/**
 * Copyright (C) 2025 Orbeon, Inc.
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
package org.orbeon.oxf.fr.persistence.api

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.findControlByName
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.SimpleDataMigration.{FormDiff, diffSimilarXmlData}
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.importexport.FormDefinitionOps
import org.orbeon.oxf.http.{HttpStatusCode, StatusCode}
import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{ContentTypes, CoreCrossPlatformSupportTrait, IndentedLogger, StaticXPath, StringUtils, TryUtils}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport
import org.orbeon.oxf.xml.XMLReceiverSupport.*
import org.orbeon.oxf.xml.{SaxonUtils, XMLReceiver}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

import java.time.Instant
import scala.util.{Failure, Success, Try}
import scala.xml.Elem


object HistoryDiff {

  val IncludeDiffsParam   = "include-diffs"
  val LanguageParam       = "lang"
  val TruncationSizeParam = "truncation-size"

  case class FormDefinition(formDefinition: NodeInfo, metadataRootElem: NodeInfo, resourceRootElem: NodeInfo)

  case class HttpStatusCodeWithDescription(code: Int, error: String) extends HttpStatusCode

  def formDefinition(
    appFormVersion          : AppFormVersion,
    requestedLangOpt        : Option[String]
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[FormDefinition] =
    PersistenceApi.readPublishedFormDefinition(
      appName  = appFormVersion._1.app,
      formName = appFormVersion._1.form,
      version  = FormDefinitionVersion.Specific(appFormVersion._2)
    ).flatMap { case ((_, formDefinition), _) =>

      val ctx               = new InDocFormRunnerDocContext(formDefinition)
      val metadataRootElem  = ctx.metadataRootElem
      val resourcesRootElem = ctx.resourcesRootElem
      val availableLangs    = (resourcesRootElem / "resource" /@ "lang").map(_.getStringValue).toList
      val selectedLangOpt   = FormRunner.selectLangUseDefault(appFormVersion._1.some, requestedLangOpt, availableLangs)

      selectedLangOpt match {
        case Some(selectedLang) =>
          XXFormsResourceSupport.findResourceElementForLang(resourcesRootElem, selectedLang) match {
            case Some(resourceRootElem) =>
              Success(
                FormDefinition(
                  formDefinition   = formDefinition,
                  metadataRootElem = metadataRootElem,
                  resourceRootElem = resourceRootElem
                )
              )

            case None =>
              Failure(
                HttpStatusCodeWithDescription(
                  StatusCode.NotFound,
                  s"Missing resources element for lang `$selectedLang`"
                )
              )
          }

        case None =>
          Failure(
            HttpStatusCodeWithDescription(
              StatusCode.NotFound,
              s"No language found for app `${appFormVersion._1.app}` and form `${appFormVersion._1.form}`"
            )
          )
      }
    }

  def formDiffs(
    appFormVersion          : AppFormVersion,
    documentId              : String,
    modifiedTimes           : List[Instant],
    formDefinition          : FormDefinition
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Map[(Instant, Instant), Option[Diffs]]] = {

    def readFormData(lastModifiedTime: Instant): Try[DocumentNodeInfoType] =
      PersistenceApi.readFormData(
        appFormVersion      = appFormVersion,
        documentId          = documentId,
        lastModifiedTime    = lastModifiedTime.some,
        isInternalAdminUser = false
      ).map(_._1._2)

    def dataMigratedToEdge(data: DocumentNodeInfoType): Try[DocumentNodeInfoType] = Try {
      MigrationSupport.dataMigratedToEdge(
        appForm              = appFormVersion._1,
        data                 = data,
        metadataOpt          = formDefinition.metadataRootElem.some,
        srcDataFormatVersion = DataFormatVersion.V400
      )
    }

    val isFormBuilder = appFormVersion._1 == AppForm.FormBuilder

    def dataMigratedIfNeeded(data: DocumentNodeInfoType): Try[DocumentNodeInfoType] =
      if (isFormBuilder) Success(data) else dataMigratedToEdge(data)

    // Read and migrate form data for all modified times
    val migratedFormDataByModifiedTimeTry =
      TryUtils.sequenceLazily(modifiedTimes) { modifiedTime =>
        for {
          formData         <- readFormData(modifiedTime)
          migratedFormData <- dataMigratedIfNeeded(formData)
        } yield modifiedTime -> migratedFormData.rootElement
      } map {
        _.toMap
      }

    val sortedModifiedTimes = modifiedTimes.sorted

    // Compare all consecutive pairs of form data
    migratedFormDataByModifiedTimeTry.map { migratedFormDataByModifiedTime =>
      for {
        (olderModifiedTime, newerModifiedTime) <- sortedModifiedTimes.zip(sortedModifiedTimes.tail)
      } yield {
        val olderDataMigrated = migratedFormDataByModifiedTime(olderModifiedTime)
        val newerDataMigrated = migratedFormDataByModifiedTime(newerModifiedTime)

        val diffsOpt =
          if (isFormBuilder) formDefinitionDiffs(olderDataMigrated, newerDataMigrated)
          else               formDataDiffs      (olderDataMigrated, newerDataMigrated, formDefinition.formDefinition)

        (olderModifiedTime, newerModifiedTime) -> diffsOpt
      }
    } map {
      _.toMap
    }
  }

  def formDiffs(
    appFormVersion          : AppFormVersion,
    documentId              : String,
    olderModifiedTime       : Instant,
    newerModifiedTime       : Instant,
    formDefinition          : FormDefinition
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Option[Diffs]] =
    formDiffs(
      appFormVersion = appFormVersion,
      documentId     = documentId,
      modifiedTimes  = List(olderModifiedTime, newerModifiedTime),
      formDefinition = formDefinition
    ).map(_.head._2)

  private def formDefinitionDiffs(d1: NodeInfo, d2: NodeInfo): Option[Diffs] = {

    val same =
      SaxonUtils.deepCompare(
        StaticXPath.GlobalConfiguration,
        Iterator(d1),
        Iterator(d2),
        excludeWhitespaceTextNodes = false
      )

    // At the moment, we just compute if there's a difference or not
    if (same) None else Some(OtherDiffs)
  }

  private def formDataDiffs(d1: NodeInfo, d2: NodeInfo, formDefinition: NodeInfo): Option[Diffs] = {

    val formDefinitionOps = new FormDefinitionOps(formDefinition)

    val formDiffs =
      diffSimilarXmlData(
        srcDocRootElem    = d1,
        dstDocRootElem    = d2,
        isElementReadonly = _ => false,
        formOps           = formDefinitionOps
      )(
        mapBind           = formDefinitionOps.bindNameOpt
      )

    if (formDiffs.isEmpty) None else FormDataDiffs(formDiffs).some
  }
}

sealed trait Diffs
case class  FormDataDiffs(formDiffs: List[FormDiff[Option[String]]]) extends Diffs
case object OtherDiffs                                               extends Diffs

object Diffs {
  def fromXml(elem: Elem): Diffs = ??? // TODO

  def serializeToSAX(
    diffsOpt            : Option[Diffs],
    olderModifiedTime   : Instant,
    newerModifiedTimeOpt: Option[Instant],
    formDefinition      : HistoryDiff.FormDefinition,
    truncationSizeOpt   : Option[Int]
  )(implicit
    receiver            : XMLReceiver
  ): Unit = {

    // TODO: in some cases, we truncate the part of the string which contains the difference(s); there are probably
    //  better ways to shorten the strings, e.g. around the differences, but it would be more difficult to implement
    def truncate(s: String) = truncationSizeOpt.map(StringUtils.truncateWithEllipsis(s, _, 1)).getOrElse(s)

    withElement(
      "diffs",
      atts =
        List(
          "older-modified-time" -> olderModifiedTime.toString
        ) :::
          newerModifiedTimeOpt.toList.map { newerModifiedTime =>
            "newer-modified-time" -> newerModifiedTime.toString
          }
    ) {
      diffsOpt match {
        case Some(FormDataDiffs(formDiffs)) =>
          formDiffs.distinct map { formDiff =>
            import FormDiff.*

            // Determine difference type, as well as specific attributes and sub-elements
            val (diffType, atts, subElems) =
              formDiff match {
                case ValueChanged    (_, from, to) => ("value-changed"    , Nil                            , List("from" -> truncate(from), "to" -> truncate(to)))
                case IterationAdded  (_, count)    => ("iteration-added"  , List("count" -> count.toString), Nil)
                case IterationRemoved(_, count)    => ("iteration-removed", List("count" -> count.toString), Nil)
                case ElementAdded    (_)           => ("element-added"    , Nil                            , Nil)
                case ElementRemoved  (_)           => ("element-removed"  , Nil                            , Nil)
              }

            implicit val ctx: InDocFormRunnerDocContext = new InDocFormRunnerDocContext(formDefinition.formDefinition.rootElement)

            // TODO: support for section template
            def labelOpt(name: String) = (formDefinition.resourceRootElem / name / "label").headOption.map(_.stringValue)
            def isHTML(name: String)   = (findControlByName(name).toList / "label" /@ "mediatype").headOption.exists(_.getStringValue == ContentTypes.HtmlContentType)

            withElement("diff", atts = List("type" -> diffType) ::: formDiff.bind.toList.map("name " -> _) :::  atts) {
              for {
                name  <- formDiff.bind
                label <- labelOpt(name)
              } {
                element("label", text = label, atts = isHTML(name).list("mediatype" -> ContentTypes.HtmlContentType))
              }

              subElems.foreach { case (localName, text) =>
                element(localName, text = text)
              }
            }
          }

        case Some(OtherDiffs) =>
          element("diff", atts = List("type" -> "other"))

        case None =>
      }
    }
  }
}
