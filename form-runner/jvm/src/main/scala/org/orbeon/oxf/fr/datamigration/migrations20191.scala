/**
* Copyright (C) 2019 Orbeon, Inc.
*
* This program is free software; you can redistribute it and/or modify it under the terms of the
* GNU Lesser General Public License as published by the Free Software Foundation; either version
* 2.1 of the License, or (at your option) any later version.
*
* This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
* without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See the GNU Lesser General Public License for more details.
*
* The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
*/
package org.orbeon.oxf.fr.datamigration

import io.circe.parser
import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.DataFormatVersion.MigrationVersion
import org.orbeon.oxf.fr.FormRunner.{getControlName, precedingSiblingOrSelfContainers, _}
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.datamigration.MigrationSupport._
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunner}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.{attributeInfo, elementInfo}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.{immutable => i}
import scala.util.{Failure, Success}
import scala.collection.compat._

case class Migration20191(
  containerPath : List[PathElem],
  newGridElem   : PathElem,
  afterElem     : Option[PathElem],
  content       : List[PathElem],
  topLevel      : Boolean
) {
  require(containerPath.nonEmpty || ! topLevel)
}

case class MigrationSet20191(migrations: List[Migration20191]) extends MigrationSet {
  val version: MigrationVersion = DataFormatVersion.V20191
}

object MigrationOps20191 extends MigrationOps {

  import Private._
  import io.circe.generic.auto._
  import io.circe.syntax._

  type M = MigrationSet20191
  val version: MigrationVersion = DataFormatVersion.V20191

  def buildMigrationSet(
    outerDocument        : DocumentInfo,
    availableXBLBindings : Option[DocumentInfo],
    legacyGridsOnly      : Boolean
  ): Option[MigrationSet20191] = {

    def migrationsForBinding(doc: DocumentInfo, topLevel: Boolean): Seq[Migration20191] =
      for {
        gridElem           <- if (legacyGridsOnly) findLegacyUnrepeatedGrids(doc) else findAllGrids(doc, repeat = false)
        gridName           = getControlName(gridElem)
        afterElem          = precedingSiblingOrSelfContainers(gridElem, includeSelf = false).headOption
        containerNames     = findContainerNamesForModel(gridElem, includeSelf = false, includeIterationElements = true)
      } yield
        Migration20191(
          containerPath = containerNames map PathElem.apply,
          newGridElem   = PathElem(gridName),
          afterElem     = afterElem flatMap getControlNameOpt map PathElem.apply,
          content       = (findNestedControls(gridElem) map getControlName map PathElem.apply).to(List),
          topLevel      = topLevel
        )

    def updateWithBindingPath(migration: Migration20191, bindingPath: List[PathElem]): Migration20191 =
      migration.copy(containerPath = bindingPath ::: migration.containerPath)

    val migrations =
      buildGridMigrations(
        outerDocument         = outerDocument,
        availableXBLBindings  = availableXBLBindings,
        migrationsForBinding  = migrationsForBinding,
        updateWithBindingPath = updateWithBindingPath
      )

    migrations.nonEmpty option MigrationSet20191(migrations.to(List))
  }

  def encodeMigrationsToJson(
    migrationSet : MigrationSet20191
  ): String =
    migrationSet.asJson.noSpaces

  def decodeMigrationSetFromJson(
    jsonString : String
  ): MigrationSet20191 =
    parser.decode[MigrationSet20191](jsonString).fold(Failure.apply, Success.apply) getOrElse
      (throw new IllegalArgumentException(s"illegal format `$jsonString`"))

  def migrateDataDown(
    dataRootElem : NodeWrapper,
    migrationSet : MigrationSet20191
  ): MigrationResult = {

    var result: MigrationResult = MigrationResult.None

    migrationSet.migrations foreach { case Migration20191(containerPath, newGridElem, _, content, _) =>

      applyPath(dataRootElem, containerPath ::: newGridElem :: Nil) foreach { gridElem =>

        result = MigrationResult.Some

        val gridContent = content flatMap (p => gridElem child p.value)

        insert(
          after                             = gridElem,
          origin                            = gridContent,
          doDispatch                        = false,
          removeInstanceDataFromClonedNodes = false // https://github.com/orbeon/orbeon-forms/issues/4519
        )

        delete(
          ref        = gridElem,
          doDispatch = false
        )
      }
    }

    result
  }

  def migrateDataUp(
    dataRootElem : NodeWrapper,
    migrationSet : MigrationSet20191
  ): MigrationResult = {

    var result: MigrationResult = MigrationResult.None

    migrationSet.migrations foreach { case Migration20191(containerPath, newGridElem, afterElem, content, _) =>

      applyPath(dataRootElem, containerPath) foreach { containerElem =>

        result = MigrationResult.Some

        val gridContent = content flatMap (p => containerElem child p.value)

        insert(
          into       = containerElem,
          after      = afterElem.toList flatMap (containerElem child _.value),
          origin     = elementInfo(newGridElem.value, gridContent),
          doDispatch = false
        )

        delete(
          ref        = gridContent,
          doDispatch = false
        )
      }
    }

    result
  }

  def migrateOthersTo(
    outerDocument : DocumentWrapper,
    migrationSet  : M
  ): MigrationResult = {

    val topLevelMigrations = migrationSet.migrations filter (_.topLevel)

    topLevelMigrations foreach {
      case Migration20191(containerPath, newGridElem, afterElem, content, _) =>

        val containerName = containerPath.last.value // `.last` as is never empty (`NEL`) when at top-level
        val gridName      = newGridElem.value

        // 1. Binds

        val containerBindElem = findBindByName(outerDocument, containerName).toList

        val existingBindContent = content flatMap (p => findBindByName(outerDocument, p.value))

        val afterBindElem =
          afterElem flatMap (p => findBindByName(outerDocument, p.value)) filter (_.parentOption contains containerBindElem)

        insert(
          into  = containerBindElem,
          after = afterBindElem.toList,
          origin =
            elementInfo(
              XFormsBindQName,
              attributeInfo("id",   FormRunner.bindId(gridName)) ::
                attributeInfo("ref",  gridName) ::
                attributeInfo("name", gridName) ::
                existingBindContent
            ),
          doDispatch = false
        )

        delete(
          ref        = existingBindContent,
          doDispatch = false
        )

        // 2. Controls
        FormRunner.findControlByName(outerDocument, gridName) foreach { gridElem =>
          ensureAttribute(gridElem, BIND_QNAME, FormRunner.bindId(FormRunner.controlNameFromId(gridElem.id)))
        }
    }

    MigrationResult(topLevelMigrations.nonEmpty)
  }

  // Adjust the search paths if needed by dropping the repeated grid element. We know that a grid
  // can only contain a leaf control. Example:
  //
  // - bind refs                : "my-section" :: "my-grid" :: "my-text" :: Nil
  // - migration container path : "my-section" :: Nil
  // - migration grid element   : "my-grid"
  // - migrated path            : "my-section" :: "my-text" :: Nil
  def adjustPathTo40(
    migrationSet : MigrationSet20191,
    path         : List[PathElem]
  ): Option[List[PathElem]] =
    (migrationSet.migrations exists (m => path.startsWith(m.containerPath ::: m.newGridElem :: Nil))) option {
      path.dropRight(2) ::: path.last :: Nil
    }

  private object Private {

    def applyPath(mutableData: NodeInfo, path: i.Seq[PathElem]): Seq[NodeInfo] =
      path.foldLeft(Seq(mutableData)) { case (e, p) => e child p.value }
  }
}