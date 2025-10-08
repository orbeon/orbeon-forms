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
package org.orbeon.oxf.fr.persistence.relational.rest

import cats.implicits.catsSyntaxOptionId
import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.SimpleDataMigration.{FormDiff, diffSimilarXmlData}
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.importexport.FormDefinitionOps
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.rest.HistoryDiff.HttpStatusCodeWithDescription
import org.orbeon.oxf.fr.persistence.relational.rest.HistoryDiffRoute.getResponseXmlReceiverSetContentType
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCode, HttpStatusCodeException, StatusCode}
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, IndentedLogger, StaticXPath, StringUtils}
import org.orbeon.oxf.xforms.function.xxforms.XXFormsResourceSupport
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.SimplePath.*

import java.time.Instant
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import scala.xml.Elem


object HistoryDiffRoute extends XmlNativeRoute {

  import HistoryDiff.*

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    val httpRequest  = ec.getRequest
    val httpResponse = ec.getResponse

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    require(httpRequest.getMethod == HttpMethod.GET)

    httpRequest.getRequestPath match {
      case ServicePathRe(_, app, form, documentId) => HistoryDiff.process(httpRequest, httpResponse, AppForm(app, form), documentId)
      case _                                       => httpResponse.setStatus(StatusCode.NotFound)
    }
  }
}

private object HistoryDiff {
  val ServicePathRe: Regex = "/fr/service/([^/]+)/history/([^/]+)/([^/]+)/([^/^.]+)/diff".r

  case class HttpStatusCodeWithDescription(code: Int, error: String) extends HttpStatusCode

  def process(
    httpRequest   : ExternalContext.Request,
    httpResponse  : ExternalContext.Response,
    appForm       : AppForm,
    documentId    : String
  )(implicit
    ec            : ExternalContext,
    indentedLogger: IndentedLogger
  ): Unit = {

    def paramValue[T](paramName: String, parser: String => T, defaultOpt: => Option[T] = None): Try[T] =
      httpRequest.getFirstParamAsString(paramName) match {
        case Some(s) => Try(parser(s)).recoverWith { case _ => Failure(HttpStatusCodeWithDescription(StatusCode.BadRequest, s"Invalid `$paramName` parameter"))}
        case None    => Try(defaultOpt) match {
          case Success(Some(default)) => Success(default)
          case Success(None)          => Failure(HttpStatusCodeWithDescription(StatusCode.BadRequest, s"Missing `$paramName` parameter"))
          case Failure(_)             => Failure(HttpStatusCodeWithDescription(StatusCode.InternalServerError, s"Could not determine default value for `$paramName` parameter"))
        }
      }

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    (for {
      formVersion       <- paramValue("form-version", _.toInt)
      olderModifiedTime <- paramValue("older-modified-time", RelationalUtils.instantFromString)
      newerModifiedTime <- paramValue("newer-modified-time", RelationalUtils.instantFromString)
      truncationSizeOpt <- paramValue("truncation-size", _.toInt.some, Some(None))
      appFormVersion    = (appForm, formVersion)
      formDefinition    <- formDefinition(appFormVersion)
      resourcesRootElem = new InDocFormRunnerDocContext(formDefinition).resourcesRootElem
      language          <- paramValue("lang", identity, (resourcesRootElem / "resource" /@ "lang").headOption.map(_.getStringValue))
      diffsOpt          <- formDiffs(
        appFormVersion    = appFormVersion,
        documentId        = documentId,
        olderModifiedTime = olderModifiedTime,
        newerModifiedTime = newerModifiedTime,
        formDefinition    = formDefinition
      )
      diffsXml          <- Diffs.toXml(diffsOpt, olderModifiedTime, newerModifiedTime, resourcesRootElem, language, truncationSizeOpt)
    } yield {
      NodeConversions.elemToSAX(diffsXml, getResponseXmlReceiverSetContentType)
    }) match {
      case Failure(t) =>
        val (statusCode, error) = t match {
          case HttpStatusCodeWithDescription(statusCode, error) => (statusCode,                     error)
          case HttpStatusCodeException(statusCode, _, _)        => (statusCode,                     t.getMessage)
          case _                                                => (StatusCode.InternalServerError, t.getMessage)
        }

        // TODO: the API should return detailed error messages in the body itself
        indentedLogger.logError("", s"Revision History API error: $error")
        httpResponse.setStatus(statusCode)

      case Success(_) =>
    }
  }

  def formDefinition(
    appFormVersion          : AppFormVersion
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[DocumentNodeInfoType] =
    PersistenceApi.readPublishedFormDefinition(
      appName  = appFormVersion._1.app,
      formName = appFormVersion._1.form,
      version  = FormDefinitionVersion.Specific(appFormVersion._2)
    ).map(_._1._2)

  private def formDiffs(
    appFormVersion          : AppFormVersion,
    documentId              : String,
    olderModifiedTime       : Instant,
    newerModifiedTime       : Instant,
    formDefinition          : NodeInfo
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Try[Option[Diffs]] = {

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
        metadataOpt          = new InDocFormRunnerDocContext(formDefinition).metadataRootElem.some,
        srcDataFormatVersion = DataFormatVersion.V400
      )
    }

    val isFormBuilder = appFormVersion._1 == AppForm.FormBuilder

    def dataMigratedIfNeeded(data: DocumentNodeInfoType): Try[DocumentNodeInfoType] =
      if (isFormBuilder) Success(data) else dataMigratedToEdge(data)

    for {
      olderData         <- readFormData(olderModifiedTime)
      newerData         <- readFormData(newerModifiedTime)
      olderDataMigrated <- dataMigratedIfNeeded(olderData)
      newerDataMigrated <- dataMigratedIfNeeded(newerData)
    } yield {
      if (isFormBuilder) {
        formDefinitionDiffs(olderDataMigrated.rootElement, newerDataMigrated.rootElement)
      } else {
        formDataDiffs      (olderDataMigrated.rootElement, newerDataMigrated.rootElement, formDefinition)
      }
    }
  }

  private def formDefinitionDiffs(d1: NodeInfo, d2: NodeInfo): Option[Diffs] = {

    val same =
      SaxonUtils.deepCompare(
        StaticXPath.GlobalConfiguration,
        Iterator(d1),
        Iterator(d2),
        excludeWhitespaceTextNodes = false
      )

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

  def toXml(
    diffsOpt         : Option[Diffs],
    olderModifiedTime: Instant,
    newerModifiedTime: Instant,
    resourcesRootElem: NodeInfo,
    lang             : String,
    truncationSizeOpt: Option[Int]
  ): Try[Elem] = {

    val resourcesRootElemTry: Try[NodeInfo] = {
      XXFormsResourceSupport.findResourceElementForLang(resourcesRootElem, lang) match {
        case Some(resourceElement) => Success(resourceElement)
        case None                  => Failure(HttpStatusCodeWithDescription(StatusCode.NotFound, s"Missing resources element for lang `$lang`"))
      }
    }

    resourcesRootElemTry.map { resourcesRootElem =>

      def labelOrNull(name: String) = (resourcesRootElem / name / "label").headOption.map(_.stringValue).orNull

      // TODO: in some cases, we truncate the part of the string which contains the difference(s); there are probably
      //  better ways to shorten the strings, e.g. around the differences, but it would be more difficult to implement
      def truncate(s: String) = truncationSizeOpt.map(StringUtils.truncateWithEllipsis(s, _, 1)).getOrElse(s)

      <diffs older-modified-time={olderModifiedTime.toString} newer-modified-time={newerModifiedTime.toString}>{
        diffsOpt match {
          case Some(FormDataDiffs(formDiffs)) =>
            formDiffs.distinct map { formDiff =>
              import FormDiff.*

              val baseElem =
                formDiff match {
                  case ValueChanged    (_, from, to) => <diff type="value-changed"><from>{truncate(from)}</from><to>{truncate(to)}</to></diff>
                  case IterationAdded  (_, count)    => <diff type="iteration-added" count={count.toString}/>
                  case IterationRemoved(_, count)    => <diff type="iteration-removed" count={count.toString}/>
                  case ElementAdded    (_)           => <diff type="element-added"/>
                  case ElementRemoved  (_)           => <diff type="element-removed"/>
                }

              formDiff.bind match {
                case Some(name) => (baseElem % new xml.UnprefixedAttribute("name", name, xml.Null)).copy(child = <label>{labelOrNull(name)}</label> +: baseElem.child)
                case None       => baseElem
              }
          }

          case Some(OtherDiffs) =>
            List(<diff type="other"/>)

          case None =>
            Nil
        }
      }</diffs>
    }
  }
}
