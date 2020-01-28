/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import com.typesafe.scalalogging.Logger
import enumeratum.EnumEntry.Lowercase
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, inScopeContainingDocument, insert}
import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable._

import scala.collection.compat._

object SimpleDataMigration {

  import Private._

  // Attempt to fill/remove holes in an instance given:
  //
  // - the enclosing model
  // - the main template instance
  // - the data to update
  //
  // The function returns the root element of the updated data if there was any update, or the
  // empty sequence if there was no data to update.
  //
  //@XPathFunction
  def dataMaybeWithSimpleMigration(
    enclosingModelAbsoluteId : String,
    templateInstanceRootElem : NodeInfo,
    dataToMigrateRootElem    : NodeInfo
  ): Option[NodeInfo] =
    try {

      require(XFormsId.isAbsoluteId(enclosingModelAbsoluteId))
      require(templateInstanceRootElem.isElement)
      require(dataToMigrateRootElem.isElement)

      implicit val doc = inScopeContainingDocument

      getConfiguredDataMigrationBehavior match {
        case DataMigrationBehavior.Disabled =>
          None
        case DataMigrationBehavior.Enabled =>

          val dataToMigrateRootElemMutable =
            MigrationSupport.copyDocumentKeepInstanceData(dataToMigrateRootElem.root).rootElement

          val ops =
            gatherMigrationOps(
              enclosingModelAbsoluteId = enclosingModelAbsoluteId,
              templateInstanceRootElem = templateInstanceRootElem,
              dataToMigrateRootElem    = dataToMigrateRootElemMutable
            )

          if (ops.nonEmpty) {
            performMigrationOps(ops)
            Some(dataToMigrateRootElemMutable)
          } else
            None

        case DataMigrationBehavior.Error =>
          val ops = gatherMigrationOps(enclosingModelAbsoluteId, templateInstanceRootElem, dataToMigrateRootElem)
          if (ops.nonEmpty)
            FormRunner.sendError(StatusCode.InternalServerError) // TODO: Which error is best?
          else
            None
      }
    } catch {
      case t: Throwable =>
        logger.error(OrbeonFormatter.format(t))
        throw t
    }

  //@XPathFunction
  def iterateBinds(
    enclosingModelAbsoluteId : String,
    dataRootElem             : NodeInfo
  ): SequenceIterator = {

    implicit val doc = inScopeContainingDocument

    def processLevel(
      parents          : List[NodeInfo],
      binds            : Seq[StaticBind],
      path             : List[String]
    ): List[NodeInfo] = {

      def findOps(prevBindOpt: Option[StaticBind], bind: StaticBind, bindName: String): List[NodeInfo] =
        parents flatMap { parent =>

          val nestedElems =
            parent / bindName toList

          nestedElems ::: processLevel(
            parents = nestedElems,
            binds   = bind.children,
            path    = bindName :: path
          )
        }

      scanBinds(binds, findOps)
    }

    // The root bind has id `fr-form-binds` at the top-level as well as within section templates
    findFormBindsRoot(findEnclosingModel(enclosingModelAbsoluteId)).toList flatMap { bind =>
      processLevel(
        parents = List(dataRootElem),
        binds   = bind.children,
        path    = Nil
      )
    }
  }

  private object Private {

    val logger = Logger("org.orbeon.fr.data-migration")

    val DataMigrationFeatureName  = "data-migration"
    val DataMigrationPropertyName = s"oxf.fr.detail.$DataMigrationFeatureName"

    import enumeratum._

    sealed abstract class DataMigrationBehavior extends EnumEntry with Lowercase

    object DataMigrationBehavior extends Enum[DataMigrationBehavior] {

      val values = findValues

      case object Enabled   extends DataMigrationBehavior
      case object Disabled  extends DataMigrationBehavior
      case object Error     extends DataMigrationBehavior
    }

    sealed trait DataMigrationOp

    object DataMigrationOp {
      case class Insert(parentElem: NodeInfo, after: Option[String], template: Option[NodeInfo]) extends DataMigrationOp
      case class Delete(elem: NodeInfo)                                                          extends DataMigrationOp
    }

    def getConfiguredDataMigrationBehavior: DataMigrationBehavior = {

      implicit val formRunnerParams = FormRunnerParams()

      val behavior =
        if (FormRunner.isDesignTime)
          DataMigrationBehavior.Disabled
        else
          FormRunner.metadataInstance map (_.rootElement)                          flatMap
          (FormRunner.optionFromMetadataOrProperties(_, DataMigrationFeatureName)) flatMap
          DataMigrationBehavior.withNameOption                                     getOrElse
          DataMigrationBehavior.Disabled

      def isFeatureEnabled =
        Version.instance.isPEFeatureEnabled(
          featureRequested = true,
          "Form Runner simple data migration"
        )

      if (behavior == DataMigrationBehavior.Enabled && ! isFeatureEnabled)
        DataMigrationBehavior.Error
      else
        behavior
    }

    def findEnclosingModel(enclosingModelAbsoluteId: String)(implicit doc: XFormsContainingDocument): XFormsModel =
      doc.findObjectByEffectiveId(XFormsId.absoluteIdToEffectiveId(enclosingModelAbsoluteId)) flatMap
        (_.narrowTo[XFormsModel])                                                             getOrElse
        (throw new IllegalStateException)

    def findFormBindsRoot(enclosingModel: XFormsModel)(implicit doc: XFormsContainingDocument): Option[StaticBind] =
      enclosingModel.staticModel.bindsById.get(Names.FormBinds)

    def scanBinds[T](
      binds : Seq[StaticBind],
      find  : (Option[StaticBind], StaticBind, String) => List[T]
    ): List[T] = {

      var result: List[T] = Nil

      binds.scanLeft(None: Option[StaticBind]) { case (prevBindOpt, bind) =>
        bind.nameOpt foreach { bindName =>
          result = find(prevBindOpt, bind, bindName) ::: result
        }
        Some(bind)
      }

      result
    }

    def gatherMigrationOps(
      enclosingModelAbsoluteId : String,
      templateInstanceRootElem : NodeInfo,
      dataToMigrateRootElem    : NodeInfo)(implicit
      doc                      : XFormsContainingDocument
    ): List[DataMigrationOp] = {

      val enclosingModel = findEnclosingModel(enclosingModelAbsoluteId)

      val templateIterationNamesToRootElems =
        (
          for {
            instance   <- enclosingModel.instancesIterator
            instanceId = instance.getId
            if FormRunner.isTemplateId(instanceId)
          } yield
            instance.rootElement.localname -> instance.rootElement
        ).toMap

      // How this works:
      //
      // - The source of truth is the bind tree.
      // - We iterate binds from root to leaf.
      // - Repeated elements are identified by the existence of a template instance, so
      //   we don't need to look at the static tree of controls.
      // - Element templates are searched first in the form instance and then, as we enter
      //   repeats, the relevant template instances.
      // - We use the bind hierarchy to look for templates, instead of just searching for the first
      //   matching element, because the top-level instance can contain data from section templates,
      //   and those are not guaranteed to be unique. Se we could find an element template coming
      //   from section template data, which would be the wrong element template. By following binds,
      //   and taking paths from them, we avoid finding incorrect element templates in section template
      //   data.
      // - NOTE: We never need to identify a template for a repeat iteration, because repeat
      //   iterations are optional!

      def findElementTemplate(templateRootElem: NodeInfo, path: List[String]): Option[NodeInfo] =
        path.foldRight(Option(templateRootElem)) {
          case (_, None)          => None
          case (name, Some(node)) => node firstChildOpt name
        }

      // NOTE: We work with `List`, which is probably the most optimal thing. Tried with `Iterator` but
      // it is messy and harder to get right.
      def processLevel(
        parents          : List[NodeInfo],
        binds            : List[StaticBind], // use `List` to ensure eager evaluation
        templateRootElem : NodeInfo,
        path             : List[String]
      ): List[DataMigrationOp] = {

        val allBindNames = binds flatMap (_.nameOpt) toSet

        def findOps(prevBindOpt: Option[StaticBind], bind: StaticBind, bindName: String): List[DataMigrationOp] =
          parents flatMap { parent =>

            val deleteOps =
              parent / * filter (e => ! allBindNames(e.localname)) map { e =>
                DataMigrationOp.Delete(e)
              }

            val nestedOps =
              parent / bindName toList match {
                case Nil =>
                  List(
                    DataMigrationOp.Insert(
                      parentElem = parent,
                      after      = prevBindOpt flatMap (_.nameOpt),
                      template   = findElementTemplate(templateRootElem, bindName :: path)
                    )
                  )
                case nodes =>

                  // Recurse
                  val newTemplateRootElem =
                    templateIterationNamesToRootElems.get(bindName)

                  processLevel(
                    parents          = nodes,
                    binds            = bind.children.to(List),
                    templateRootElem = newTemplateRootElem getOrElse templateRootElem,
                    path             = if (newTemplateRootElem.isDefined) Nil else bindName :: path
                  )
              }

            deleteOps ++: nestedOps
          }

        scanBinds(binds, findOps)
      }

      // The root bind has id `fr-form-binds` at the top-level as well as within section templates
      findFormBindsRoot(enclosingModel).toList flatMap { bind =>
        processLevel(
          parents          = List(dataToMigrateRootElem),
          binds            = bind.children.to(List),
          templateRootElem = templateInstanceRootElem,
          path             = Nil
        )
      }
    }

    def performMigrationOps(ops: List[DataMigrationOp]): Unit =
      ops foreach {
        case DataMigrationOp.Delete(elem) =>

          logger.debug(s"removing element `${elem.localname}` from `${elem.getParent.localname}`")
          delete(elem)

        case DataMigrationOp.Insert(parentElem, after, Some(template)) =>

          logger.debug(s"inserting element `${template.localname}` into `${parentElem.localname}` after `$after`")

          insert(
            into   = parentElem,
            after  = after.toList flatMap (parentElem / _),
            origin = template.toList
          )

        case DataMigrationOp.Insert(_, _, None) =>

          // Template for the element was not found. Error?
      }
  }
}
