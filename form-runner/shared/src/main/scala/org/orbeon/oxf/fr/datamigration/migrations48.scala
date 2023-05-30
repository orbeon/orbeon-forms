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

import org.orbeon.dom.saxon.{DocumentWrapper, NodeWrapper}
import org.orbeon.oxf.fr.DataFormatVersion.MigrationVersion
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.datamigration.MigrationSupport._
import org.orbeon.oxf.fr.{DataFormatVersion, FormRunnerDocContext, InDocFormRunnerDocContext}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory.{attributeInfo, elementInfo}
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, insert}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.BasicNamespaceMapping
import org.orbeon.xforms.XFormsNames.{XFBindQName, XFORMS_BIND_QNAME, XFORMS_SHORT_PREFIX}

import scala.collection.compat._


case class Migration48(
  containerPath : List[PathElem],
  iterationElem : PathElem
) {
  require(containerPath.nonEmpty)
}

case class MigrationSet48(migrations: List[Migration48]) extends MigrationSet {
  val version: MigrationVersion = DataFormatVersion.V480
}

object MigrationOps48 extends MigrationOps {

  import Private._

  type M = MigrationSet48
  val version: MigrationVersion = DataFormatVersion.V480

  def buildMigrationSet(
    outerDocument        : DocumentNodeInfoType,
    availableXBLBindings : Option[DocumentNodeInfoType],
    legacyGridsOnly      : Boolean
  ): Option[MigrationSet48] = {

    implicit val ctx = new InDocFormRunnerDocContext(outerDocument)

    def updateWithBindingPath(migration: Migration48, bindingPath: List[PathElem]): Migration48 =
      migration.copy(containerPath = bindingPath ::: migration.containerPath)

    val migrations =
      MigrationSupport.buildGridMigrations(
        outerDocument         = outerDocument,
        availableXBLBindings  = availableXBLBindings,
        migrationsForBinding  = (doc, _) => migrationsForBinding(legacyGridsOnly)(new InDocFormRunnerDocContext(doc)),
        updateWithBindingPath = updateWithBindingPath
      )

    migrations.nonEmpty option MigrationSet48(migrations.to(List))
  }

  def encodeMigrationsToJson(
    migrationSet : MigrationSet48
  ): String = {

    def encodeOne(m: Migration48) =
      s"""{ "path": "${m.containerPath map (_.value) mkString "/"}", "iteration-name": "${m.iterationElem.value}" }"""

    migrationSet.migrations map encodeOne mkString ("[", ",", "]")
  }

  def decodeMigrationSetFromJson(
    jsonString : String
  ): MigrationSet48 = {

    import io.circe.generic.auto._
    import io.circe.parser

    case class PathAndName(path: String, `iteration-name`: String)

    MigrationSet48(
      for {
        pathAnNames                      <- parser.decode[Iterable[PathAndName]](jsonString).toOption.toList
        PathAndName(path, iterationName) <- pathAnNames
      } yield
        Migration48(
          path.splitTo[List]("/") map {
            case PathElem.TrimPathElementRE(p) => PathElem(p)
            case name                          => throw new IllegalArgumentException(s"invalid migration name: `$name`")
          },
          PathElem(iterationName)
        )
    )
  }

  def migrateDataDown(
    dataRootElem : NodeWrapper,
    migrationSet : MigrationSet48
  ): MigrationResult = {

    var result: MigrationResult = MigrationResult.None

    partitionNodes(dataRootElem, migrationSet.migrations) foreach {
      case (_, Nil, _, _) =>
        // This can happen if data is pruned
        // https://github.com/orbeon/orbeon-forms/issues/3172
      case (_, container :: tail, _, _) =>
        //assert(tail.isEmpty)

        result = MigrationResult.Some

        val contentForEachIteration =
          (container / * toList) map (iteration => (iteration /@ @*) ++ (iteration / Node) toList) // force

        insert(
          after                             = container,
          origin                            = contentForEachIteration map (elementInfo(container.name, _, removeInstanceDataFromClonedNodes = false)),
          doDispatch                        = false,
          removeInstanceDataFromClonedNodes = false // https://github.com/orbeon/orbeon-forms/issues/4519
        )

        delete(
          ref        = container,
          doDispatch = false
        )
    }

    result
  }

  def migrateDataUp(
    dataRootElem : NodeWrapper,
    migrationSet : MigrationSet48
  ): MigrationResult =
  partitionNodes(dataRootElem, migrationSet.migrations) match {
    case Nil => MigrationResult.None
    case partitioned =>
      partitioned foreach {
        case (parentNode, iterations, repeatName, iterationElem) =>

          iterations match {
            case Nil =>
              // Issue: we don't know, based just on the migration map, where to insert container elements to
              // follow bind order. This is not a new problem as we don't enforce order, see:
              //
              //     https://github.com/orbeon/orbeon-forms/issues/443.
              //
              // For now we choose to add after the last element.
              //
              // BTW at runtime `fr:grid[@repeat = 'true']` inserts iterations before the first element.
              insert(
                into       = parentNode,
                after      = parentNode / *,
                origin     = elementInfo(repeatName, Nil),
                doDispatch = false
              )
            case its =>

              val contentForEachIteration =
                its map (iteration => (iteration /@ @*) ++ (iteration / Node) toList) // force

              delete(its.head /@ @*,  doDispatch = false)
              delete(its.head / Node, doDispatch = false)
              delete(its.tail,        doDispatch = false)

              insert(
                into       = its.head,
                origin     = contentForEachIteration map (elementInfo(iterationElem.value, _)),
                doDispatch = false
              )
          }
      }

    MigrationResult.Some
  }

  def migrateOthersDown(
    outerDocument : DocumentWrapper,
    migrationSet  : M
  ): MigrationResult =
    ???

  def migrateOthersUp(
    outerDocument : DocumentWrapper,
    migrationSet  : M
  ): MigrationResult = {

    implicit val ctx = new InDocFormRunnerDocContext(outerDocument)

    // NOTE: We don't use the `migrationSet` set here because we need to only check the top-level migrations, and
    // our migration format doesn't keep that information. We can't update that format without breaking
    // backward compatibility. We could migrate the format but we should still handle the old format if we do.
    // We do reuse the code that creates the migration, however.

    // TODO: We could check that the binds map existing ancestors, and still use `migrationSet`.

    val migrations = migrationsForBinding(legacyGridsOnly = true)

    migrations foreach {
      case Migration48(containerPath, iterationElem) =>

        val containerName = containerPath.last.value // `.last` as is never empty (`NEL`)
        val iterationName = iterationElem.value

        val containerBindElem = frc.findBindByName(containerName).toList

        val existingGridBindContent = (containerBindElem child *).toList

        val useShortPrefix =
          containerBindElem.flatMap(_.prefixesForURI(XFBindQName.namespace.uri)).contains(XFORMS_SHORT_PREFIX)

        insert(
          into = containerBindElem,
          origin =
            elementInfo(
              if (useShortPrefix) XFBindQName else XFORMS_BIND_QNAME,
              attributeInfo("id",   frc.bindId(iterationName)) ::
                attributeInfo("ref",  iterationName)           ::
                attributeInfo("name", iterationName)           ::
                existingGridBindContent
            ),
          doDispatch = false
        )

        delete(
          ref        = existingGridBindContent,
          doDispatch = false
        )
    }

    // Q: Should we migrate `repeat="true"` -> `repeat="content"` here?

    MigrationResult(migrations.nonEmpty)
  }

  // Adjust the search paths if needed by dropping the repeated grid iteration element. We know that a grid
  // iteration can only contain a leaf control. Example:
  //
  // - bind refs                : "my-section" :: "my-grid" :: "my-grid-iteration" :: "my-text" :: Nil
  // - migration container path : "my-section" :: "my-grid" :: Nil
  // - migrated path            : "my-section" :: "my-grid" :: "my-text" :: Nil
  def adjustPathTo40(
    migrationSet : MigrationSet48,
    path         : List[PathElem]
  ): Option[List[PathElem]] =
    (migrationSet.migrations exists (m => path.startsWith(m.containerPath))) option {
      path.dropRight(2) ::: path.last :: Nil
    }

  private object Private {

    def gridRepeatIterationName(grid: NodeInfo)(implicit ctx: FormRunnerDocContext): String = {
      val controlName = frc.getControlName(grid)
      if (frc.isLegacyRepeat(grid))
        frc.defaultIterationName(controlName)
      else
        frc.findRepeatIterationName(controlName).get
    }

    def migrationsForBinding(legacyGridsOnly: Boolean)(implicit ctx: FormRunnerDocContext): Seq[Migration48] =
      for {
        gridElem               <- if (legacyGridsOnly) findLegacyRepeatedGrids else findAllGrids(repeat = true)
        gridName               = frc.getControlName(gridElem)
        iterationName          = gridRepeatIterationName(gridElem)
        BindPath(_, pathElems) <- frc.findBindAndPathStatically(gridName)
      } yield
        Migration48(pathElems, PathElem(iterationName))

    def partitionNodes(
      dataRootElem : NodeWrapper,
      migration    : List[Migration48]
    ): List[(NodeInfo, List[NodeInfo], String, PathElem)] =
      migration flatMap {
        case Migration48(containerPath, iterationElem) =>

          val (pathToParentNodes, pathToChildNodes) =
            (containerPath.init map (_.value) mkString "/", containerPath.last.value)

          // NOTE: Use collect, but we know they are nodes if the JSON is correct and contains paths
          val parentNodes =
            scaxon.XPath.eval(dataRootElem, pathToParentNodes, BasicNamespaceMapping.Mapping) collect {
            case node: NodeInfo => node
          }

          parentNodes map { parentNode =>

            val nodes = scaxon.XPath.eval(parentNode, pathToChildNodes) collect {
              case node: NodeInfo => node
            }

            // NOTE: Should ideally test on uriQualifiedName instead. The data in practice has elements which
            // in no namespaces, and if they were in a namespace, the prefixes would likely be unique.
            (parentNode, nodes.to(List), pathToChildNodes, iterationElem)
          }
      }
  }
}