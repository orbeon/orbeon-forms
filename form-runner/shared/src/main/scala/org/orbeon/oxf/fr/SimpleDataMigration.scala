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

import cats.syntax.option._
import enumeratum.EnumEntry.Lowercase
import org.log4s
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.SimpleDataMigration.FormOps
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.http.StatusCode
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI.{delete, inScopeContainingDocument, insert}
import org.orbeon.oxf.xforms.analysis.model.StaticBind
import org.orbeon.oxf.xforms.model.{DataModel, XFormsModel}
import org.orbeon.oxf.xforms.{XFormsContainingDocument, XFormsStaticState}
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId
import shapeless.syntax.typeable._


object SimpleDataMigration {

  import Private._
  import enumeratum._

  sealed trait DataMigrationBehavior extends EnumEntry with Lowercase

  object DataMigrationBehavior extends Enum[DataMigrationBehavior] {

    val values = findValues

    case object Enabled   extends DataMigrationBehavior
    case object Disabled  extends DataMigrationBehavior
    case object HolesOnly extends DataMigrationBehavior
    case object Error     extends DataMigrationBehavior
  }

  sealed trait DataMigrationOp

  object DataMigrationOp {
    case class Insert(parentElem: om.NodeInfo, after: Option[String], template: Option[om.NodeInfo]) extends DataMigrationOp
    case class Delete(elem: om.NodeInfo)                                                             extends DataMigrationOp
  }

  trait FormOps {

    type DocType
    type BindType

    def findFormBindsRoot: Option[BindType]
    def templateIterationNamesToRootElems: Map[String, om.NodeInfo]
    def bindChildren(bind: BindType): List[BindType]
    def bindNameOpt(bind: BindType): Option[String]
  }

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
    templateInstanceRootElem : om.NodeInfo,
    dataToMigrateRootElem    : om.NodeInfo
  ): Option[om.NodeInfo] =
    dataMaybeWithSimpleMigrationWithBehavior(
      enclosingModelAbsoluteId,
      templateInstanceRootElem,
      dataToMigrateRootElem,
      getConfiguredDataMigrationBehavior(inScopeContainingDocument.staticState).entryName
    )

  //@XPathFunction
  def dataMaybeWithSimpleMigrationWithBehavior(
    enclosingModelAbsoluteId : String,
    templateInstanceRootElem : om.NodeInfo,
    dataToMigrateRootElem    : om.NodeInfo,
    dataMigrationBehavior    : String
  ): Option[om.NodeInfo] = {

    val maybeMigrated =
      dataMaybeWithSimpleMigrationUseOps(
        enclosingModelAbsoluteId = enclosingModelAbsoluteId,
        templateInstanceRootElem = templateInstanceRootElem,
        dataToMigrateRootElem    = dataToMigrateRootElem,
        dataMigrationBehavior    = DataMigrationBehavior.withName(dataMigrationBehavior))(
        formOps                  = new ContainingDocumentOps(inScopeContainingDocument, enclosingModelAbsoluteId)
      )

    maybeMigrated map {
      case Left(_)  => frc.sendError(StatusCode.InternalServerError) // TODO: Which error is best?
      case Right(v) => v
    }
  }

  def dataMaybeWithSimpleMigrationUseOps(
    enclosingModelAbsoluteId : String,
    templateInstanceRootElem : om.NodeInfo,
    dataToMigrateRootElem    : om.NodeInfo,
    dataMigrationBehavior    : DataMigrationBehavior)(implicit
    formOps                  : FormOps
  ): Option[List[DataMigrationOp] Either om.NodeInfo] = {

    require(XFormsId.isAbsoluteId(enclosingModelAbsoluteId))
    require(templateInstanceRootElem.isElement)
    require(dataToMigrateRootElem.isElement)

    dataMigrationBehavior match {
      case DataMigrationBehavior.Disabled =>
        None
      case DataMigrationBehavior.Enabled | DataMigrationBehavior.HolesOnly =>

        val dataToMigrateRootElemMutable =
          MigrationSupport.copyDocumentKeepInstanceData(dataToMigrateRootElem.root).rootElement

        val ops =
          gatherMigrationOps(
            enclosingModelAbsoluteId = enclosingModelAbsoluteId,
            templateInstanceRootElem = templateInstanceRootElem,
            dataToMigrateRootElem    = dataToMigrateRootElemMutable
          )

        lazy val deleteOps =
          ops collect {
            case delete: DataMigrationOp.Delete => delete
          }

        val mustMigrate =
          ops.nonEmpty && (
            dataMigrationBehavior == DataMigrationBehavior.Enabled ||
            dataMigrationBehavior == DataMigrationBehavior.HolesOnly && deleteOps.isEmpty
          )

        val mustRaiseError =
          ops.nonEmpty && dataMigrationBehavior == DataMigrationBehavior.HolesOnly && deleteOps.nonEmpty

        if (mustMigrate) {
          performMigrationOps(ops)
          Some(Right(dataToMigrateRootElemMutable))
        } else if (mustRaiseError) {
          Left(deleteOps).some
        } else {
          None
        }

      case DataMigrationBehavior.Error =>

        val ops =
          gatherMigrationOps(
            enclosingModelAbsoluteId = enclosingModelAbsoluteId,
            templateInstanceRootElem = templateInstanceRootElem,
            dataToMigrateRootElem    = dataToMigrateRootElem
          )

        if (ops.nonEmpty)
          Left(ops).some
        else
          None
    }
  }

  // This is used in `form-to-xbl.xsl`, see:
  // https://github.com/orbeon/orbeon-forms/issues/3829
  //@XPathFunction
  def iterateBinds(
    enclosingModelAbsoluteId : String,
    dataRootElem             : om.NodeInfo
  ): om.SequenceIterator = {

    val ops = new ContainingDocumentOps(inScopeContainingDocument, enclosingModelAbsoluteId)

    def processLevel(
      parents          : List[om.NodeInfo],
      binds            : Seq[StaticBind],
      path             : List[String]
    ): List[om.NodeInfo] = {

      def findOps(prevBindOpt: Option[StaticBind], bind: StaticBind, bindName: String): List[om.NodeInfo] =
        parents flatMap { parent =>

          val nestedElems =
            parent / bindName toList

          nestedElems ::: processLevel(
            parents = nestedElems,
            binds   = bind.childrenBinds,
            path    = bindName :: path
          )
        }

      scanBinds(ops)(binds, findOps)
    }

    // The root bind has id `fr-form-binds` at the top-level as well as within section templates
    ops.findFormBindsRoot.toList flatMap { bind =>
      processLevel(
        parents = List(dataRootElem),
        binds   = bind.childrenBinds,
        path    = Nil
      )
    }
  }

  // Merge two XML documents based on binds.
  // See https://github.com/orbeon/orbeon-forms/issues/4980
  // This doesn't handle attributes at all. This is probably ok for now because we don't have the use case
  // of being able to update file attachments metadata, in particular. But in the future we should support
  // that.
  def mergeXmlFromBindSchema(
    srcDocRootElem          : om.NodeInfo,
    dstDocRootElem          : om.NodeInfo,
    isElementReadonly       : om.NodeInfo => Boolean,
    ignoreBlankData         : Boolean,
    allowMissingElemInSource: Boolean)(
    formOps        : FormOps
  ): (Int, Int) = {

    val templateIterationNamesToRootElems = formOps.templateIterationNamesToRootElems

    var allValuesCount = 0
    var setValuesCount = 0

    def processLevel(
      parentBind            : formOps.BindType,
      leftElem              : om.NodeInfo,
      rightElem             : om.NodeInfo,
      currentIgnoreBlankData: Boolean
    ): Unit = {

      formOps.bindChildren(parentBind) match {
        case Nil =>
          // Leaf node

          // Data can be copied if there are no nested element. Specific case are:
          //
          // - section template content
          // - multiple attachment using nested array (`<_>`)
          if (! rightElem.hasChildElement && ! leftElem.hasChildElement) {

            allValuesCount += 1

            val value = leftElem.getStringValue

            if ((! currentIgnoreBlankData || value.nonAllBlank) && ! isElementReadonly(rightElem)) {
              DataModel.setValue(rightElem, value, onError = r => throw new IllegalArgumentException(r.message))
              setValuesCount += 1
            }
          }

        case childrenBinds =>

          childrenBinds foreach { childBind =>

            formOps.bindNameOpt(childBind) foreach { bindName =>

              templateIterationNamesToRootElems.get(bindName) match {
                case Some(repeatTemplateInstanceRootElem) =>
                  // We are a repeated container element (that is, an iteration)

                  // Adjust iterations
                  val leftSize  = (leftElem / bindName).size
                  val rightSize = (rightElem / bindName).size

                  // Two different ways to handle new iterations:
                  //
                  // - `false`: insert repeat templates then recurse down and copy values only
                  // - `true`: copy incoming data for new iterations as is and don't recurse
                  //
                  // `true` would be more efficient, but there is a risk that unwanted attributes could be copied,
                  // in particular.
                  val CopyNewIterations = false

                  if (CopyNewIterations) {

                    if (leftSize > rightSize)
                      insert(
                        into   = List(rightElem),
                        after  = rightElem / *,
                        origin = leftElem / bindName drop rightSize
                      )

                    // Recurse into each iteration
                    (leftElem / bindName take rightSize).zip(rightElem / bindName) foreach { case (childLeft, childRight) =>
                      processLevel(
                        parentBind             = childBind,
                        leftElem               = childLeft,
                        rightElem              = childRight,
                        currentIgnoreBlankData = currentIgnoreBlankData
                      )
                    }

                  } else {

                    if (leftSize > rightSize)
                      insert(
                        into   = List(rightElem),
                        after  = rightElem / *,
                        origin = (1 to (leftSize - rightSize)).map(_ => repeatTemplateInstanceRootElem)
                      )

                    // Recurse into each iteration
                    (leftElem / bindName).zip(rightElem / bindName).zipWithIndex foreach { case ((childLeft, childRight), index) =>
                      processLevel(
                        parentBind             = childBind,
                        leftElem               = childLeft,
                        rightElem              = childRight,
                        currentIgnoreBlankData = if (index < rightSize) currentIgnoreBlankData else false // data in new iterations always wins
                      )
                    }
                  }
                case None =>
                  // We are a non-repeated container element: just recurse

                  leftElem firstChildOpt bindName match {
                    case Some(leftChildElem) =>
                      processLevel(
                        parentBind             = childBind,
                        leftElem               = leftChildElem,
                        rightElem              = (rightElem firstChildOpt bindName).getOrElse(throw new IllegalArgumentException(s"missing element in destination: `${bindName}`")),
                        currentIgnoreBlankData = currentIgnoreBlankData
                      )
                    case None if ! allowMissingElemInSource =>
                        (leftElem firstChildOpt bindName).getOrElse(throw new IllegalArgumentException(s"missing element in source: `${bindName}`"))
                    case None =>
                  }
              }
            }
          }
      }
    }

    formOps.findFormBindsRoot foreach { bind =>
      processLevel(
        parentBind             = bind,
        leftElem               = srcDocRootElem,
        rightElem              = dstDocRootElem,
        currentIgnoreBlankData = ignoreBlankData
      )
    }

    (allValuesCount, setValuesCount)
  }

  private object Private {

    val logger: log4s.Logger = LoggerFactory.createLogger("org.orbeon.fr.data-migration")

    val DataMigrationFeatureName  = "data-migration"
    val DataMigrationPropertyName = s"oxf.fr.detail.$DataMigrationFeatureName"

    def getConfiguredDataMigrationBehavior(staticState: XFormsStaticState): DataMigrationBehavior = {

      implicit val formRunnerParams = FormRunnerParams()

      val behavior =
        if (frc.isDesignTime)
          DataMigrationBehavior.Disabled
        else
          frc.metadataInstance map (_.rootElement)                          flatMap
          (frc.optionFromMetadataOrProperties(_, DataMigrationFeatureName)) flatMap
          DataMigrationBehavior.withNameOption                              getOrElse
          DataMigrationBehavior.Disabled

      def isFeatureEnabled =
        staticState.isPEFeatureEnabled(
          featureRequested = true,
          "Form Runner simple data migration"
        )

      if ((behavior == DataMigrationBehavior.Enabled || behavior == DataMigrationBehavior.HolesOnly) && ! isFeatureEnabled)
        DataMigrationBehavior.Error
      else
        behavior
    }

    def scanBinds[T](
      ops   : FormOps)(
      binds : Seq[ops.BindType],
      find  : (Option[ops.BindType], ops.BindType, String) => List[T]
    ): List[T] = {

      var result: List[T] = Nil

      binds.scanLeft(None: Option[ops.BindType]) { case (prevBindOpt, bind) =>
        ops.bindNameOpt(bind) foreach { bindName =>
          result = find(prevBindOpt, bind, bindName) ::: result
        }
        Some(bind)
      }

      result
    }

    def gatherMigrationOps(
      enclosingModelAbsoluteId : String,
      templateInstanceRootElem : om.NodeInfo,
      dataToMigrateRootElem    : om.NodeInfo)(implicit
      formOps                  : FormOps
    ): List[DataMigrationOp] = {

      val templateIterationNamesToRootElems = formOps.templateIterationNamesToRootElems

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

      def findElementTemplate(templateRootElem: om.NodeInfo, path: List[String]): Option[om.NodeInfo] =
        path.foldRight(Option(templateRootElem)) {
          case (_, None)          => None
          case (name, Some(node)) => node firstChildOpt name
        }

      // NOTE: We work with `List`, which is probably the most optimal thing. Tried with `Iterator` but
      // it is messy and harder to get right.
      def processLevel(
        parents          : List[om.NodeInfo],
        binds            : List[formOps.BindType], // use `List` to ensure eager evaluation
        templateRootElem : om.NodeInfo,
        path             : List[String]
      ): List[DataMigrationOp] = {

        val allBindNames = binds.flatMap(formOps.bindNameOpt).toSet

        def findOps(prevBindOpt: Option[formOps.BindType], bind: formOps.BindType, bindName: String): List[DataMigrationOp] = {

          parents flatMap { parent =>
            parent / bindName toList match {
              case Nil =>
                List(
                  DataMigrationOp.Insert(
                    parentElem = parent,
                    after      = prevBindOpt.flatMap(formOps.bindNameOpt),
                    template   = findElementTemplate(templateRootElem, bindName :: path)
                  )
                )
              case nodes =>

                // If we get a `Some(_)` it means that this bind is for a repeat iteration
                val newTemplateRootElem =
                  templateIterationNamesToRootElems.get(bindName)

                // Recurse
                processLevel(
                  parents          = nodes,
                  binds            = formOps.bindChildren(bind),
                  templateRootElem = newTemplateRootElem getOrElse templateRootElem,
                  path             = if (newTemplateRootElem.isDefined) Nil else bindName :: path
                )
            }
          }
        }

        // https://github.com/orbeon/orbeon-forms/issues/5041
        // Only delete if there are no nested bind, for backward compatibility first. But is this wrong? If we don't do
        // this, we will delete `<_>` for multiple attachments as well as the nested grids in section template data.
        // However, this probably also means that we'll not delete extra elements nested within other leaves.
        val deleteOps =
          if (binds.nonEmpty)
            parents / * filter (e => e.namespaceURI == "" && ! allBindNames(e.localname)) map { e =>
              DataMigrationOp.Delete(e)
            }
          else
            Nil

        deleteOps ++: scanBinds(formOps)(binds, findOps)
      }

      // The root bind has id `fr-form-binds` at the top-level as well as within section templates
      formOps.findFormBindsRoot.toList flatMap { bind =>
        processLevel(
          parents          = List(dataToMigrateRootElem),
          binds            = formOps.bindChildren(bind),
          templateRootElem = templateInstanceRootElem,
          path             = Nil
        )
      }
    }

    def performMigrationOps(migrationOps: List[DataMigrationOp]): Unit =
      migrationOps foreach {
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

class ContainingDocumentOps(doc: XFormsContainingDocument, enclosingModelAbsoluteId: String) extends FormOps {

  type DocType = XFormsContainingDocument
  type BindType = StaticBind

  private val enclosingModel =
    doc.findObjectByEffectiveId(XFormsId.absoluteIdToEffectiveId(enclosingModelAbsoluteId)) flatMap
      (_.narrowTo[XFormsModel])                                                             getOrElse
      (throw new IllegalStateException)

  def findFormBindsRoot: Option[StaticBind] =
    enclosingModel.staticModel.bindsById.get(Names.FormBinds)

  def templateIterationNamesToRootElems: Map[String, om.NodeInfo] =
    (
      for {
        instance   <- enclosingModel.instancesIterator
        instanceId = instance.getId
        if frc.isTemplateId(instanceId)
      } yield
        instance.rootElement.localname -> instance.rootElement
    ).toMap

  def bindChildren(bind: StaticBind): List[StaticBind] =
    bind.childrenBinds

  def bindNameOpt(bind: StaticBind): Option[String] =
    bind.nameOpt
}
