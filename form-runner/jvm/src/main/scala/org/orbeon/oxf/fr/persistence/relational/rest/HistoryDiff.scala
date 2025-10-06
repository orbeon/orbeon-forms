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
import cats.syntax.either.*
import org.orbeon.oxf.controller.XmlNativeRoute
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunnerParams.AppFormVersion
import org.orbeon.oxf.fr.SimpleDataMigration.{FormDiff, diffSimilarXmlData}
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.importexport.FormDefinitionOps
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.persistence.relational.RelationalUtils
import org.orbeon.oxf.fr.persistence.relational.rest.HistoryDiffRoute.getResponseXmlReceiverSetContentType
import org.orbeon.oxf.http.{HttpMethod, HttpStatusCodeException, StatusCode}
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
import scala.util.{Success, Try}
import scala.xml.Elem


object HistoryDiffRoute extends XmlNativeRoute {

  import HistoryDiff.*

  def process()(implicit pc: PipelineContext, ec: ExternalContext): Unit = {

    val httpRequest  = ec.getRequest
    val httpResponse = ec.getResponse

    implicit val indentedLogger: IndentedLogger = RelationalUtils.newIndentedLogger

    try {
      require(httpRequest.getMethod == HttpMethod.GET)

      httpRequest.getRequestPath match {
        case ServicePathRe(_, app, form, documentId) => HistoryDiff.process(httpRequest, httpResponse, AppForm(app, form), documentId)
        case _                                       => httpResponse.setStatus(StatusCode.NotFound)
      }
    } catch {
      case e: HttpStatusCodeException                => httpResponse.setStatus(e.code)
    }
  }
}

private object HistoryDiff {
  val ServicePathRe: Regex = "/fr/service/([^/]+)/history/([^/]+)/([^/]+)/([^/^.]+)/diff".r

  def process(
    httpRequest   : ExternalContext.Request,
    httpResponse  : ExternalContext.Response,
    appForm       : AppForm,
    documentId    : String
  )(implicit
    ec            : ExternalContext,
    indentedLogger: IndentedLogger
  ): Unit = {

    def error(paramName: String) = s"Missing or invalid `$paramName` parameter"

    def paramValue[T](paramName: String, parser: String => T): Either[String, T] =
      httpRequest.getFirstParamAsString(paramName)
        .toRight(error(paramName))
        .flatMap(s => Either.catchNonFatal(parser(s)).leftMap(_ => error(paramName)))

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    val formDiffsXmlEither =
      for {
        formVersion       <- paramValue("form-version", _.toInt)
        olderModifiedTime <- paramValue("older-modified-time", RelationalUtils.instantFromString)
        newerModifiedTime <- paramValue("newer-modified-time", RelationalUtils.instantFromString)
        language          = httpRequest.getFirstParamAsString("lang").getOrElse(FormRunner.getDefaultLang(appForm.some))
        appFormVersion    = (appForm, formVersion)
        formDefinition    <- formDefinition(appFormVersion)
        formDiffs         <- formDiffs(
          appFormVersion    = appFormVersion,
          documentId        = documentId,
          olderModifiedTime = olderModifiedTime,
          newerModifiedTime = newerModifiedTime,
          formDefinition    = formDefinition
        )
        formDiffsXml      <- formDiffs.toXml(olderModifiedTime, newerModifiedTime, formDefinition, language)
      } yield formDiffsXml

    formDiffsXmlEither match {
      case Left(error) =>
        // TODO: the API should return detailed error messages in the body itself
        indentedLogger.logError("", s"Revision History API error: $error")
        httpResponse.setStatus(StatusCode.BadRequest)

      case Right(formDiffsXml) =>
        NodeConversions.elemToSAX(formDiffsXml, getResponseXmlReceiverSetContentType)
    }
  }

  def formDefinition(
    appFormVersion          : AppFormVersion
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Either[String, DocumentNodeInfoType] =
    PersistenceApi.readPublishedFormDefinition(
      appName  = appFormVersion._1.app,
      formName = appFormVersion._1.form,
      version  = FormDefinitionVersion.Specific(appFormVersion._2)
    ).map(_._1._2).toEither.leftMap(_.getMessage)

  private def formDiffs(
    appFormVersion          : AppFormVersion,
    documentId              : String,
    olderModifiedTime       : Instant,
    newerModifiedTime       : Instant,
    formDefinition          : NodeInfo
  )(implicit
    indentedLogger          : IndentedLogger,
    coreCrossPlatformSupport: CoreCrossPlatformSupportTrait
  ): Either[String, FormDiffs] = {

    def readFormData(lastModifiedTime: Instant): Try[DocumentNodeInfoType] =
      PersistenceApi.readFormData(
        appFormVersion      = appFormVersion,
        documentId          = documentId,
        lastModifiedTime    = lastModifiedTime.some,
        isInternalAdminUser = false
      ).map(_._1._2)

    def dataMigratedToEdge(data: DocumentNodeInfoType): Try[DocumentNodeInfoType] = Try{
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

    val formDiffsTry =
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

    formDiffsTry.toEither.leftMap(_.getMessage)
  }

  private def formDefinitionDiffs(d1: NodeInfo, d2: NodeInfo): FormDiffs = {

    val same =
      SaxonUtils.deepCompare(
        StaticXPath.GlobalConfiguration,
        Iterator(d1),
        Iterator(d2),
        excludeWhitespaceTextNodes = false
      )

    FormDiffs(if (same) Nil else List(FormDiff.Unspecified()))
  }

  private def formDataDiffs(d1: NodeInfo, d2: NodeInfo, formDefinition: NodeInfo): FormDiffs = {

    val formDefinitionOps = new FormDefinitionOps(formDefinition)

    FormDiffs(
      diffSimilarXmlData(
        srcDocRootElem    = d1,
        dstDocRootElem    = d2,
        isElementReadonly = _ => false,
        formOps           = formDefinitionOps
      )(
        mapBind           = formDefinitionOps.bindNameOpt
      )
    )
  }
}

object FormDiffs {
  def fromXml(elem: Elem): FormDiffs =
    FormDiffs(Nil) // TODO
}

case class FormDiffs(diffs: List[FormDiff[Option[String]]]) {

  def toXml(
      olderModifiedTime: Instant,
      newerModifiedTime: Instant,
      formDefinition   : NodeInfo,
      lang             : String
    ): Either[String, Elem] = {

    val resourcesRootElemEither: Either[String, NodeInfo] = {
      val resourcesRootElem = new InDocFormRunnerDocContext(formDefinition).resourcesRootElem

      XXFormsResourceSupport.findResourceElementForLang(resourcesRootElem, lang) match {
        case Some(resourceElement) => resourceElement.asRight
        case None                  => s"Missing resources element for lang `$lang`".asLeft
      }
    }

    resourcesRootElemEither.map { resourcesRootElem =>

      // TODO: param?
      val MaxValueLength = 20

      def labelOrNull(name: String) = (resourcesRootElem / name / "label").headOption.map(_.stringValue).orNull
      def truncate(s: String)       = StringUtils.truncateWithEllipsis(s, MaxValueLength, 1)

      <diffs older-modified-time={olderModifiedTime.toString} newer-modified-time={newerModifiedTime.toString}>{
        diffs.distinct map {
          case FormDiff.ValueChanged    (Some(name), from, to) => <diff type="value-changed"     name={name} label={labelOrNull(name)} from={truncate(from)} to={truncate(to)}/>
          case FormDiff.IterationAdded  (Some(name), count)    => <diff type="iteration-added"   name={name} label={labelOrNull(name)} count={count.toString}/>
          case FormDiff.IterationRemoved(Some(name), count)    => <diff type="iteration-removed" name={name} label={labelOrNull(name)} count={count.toString}/>
          case FormDiff.ElementAdded    (Some(name))           => <diff type="element-added"     name={name} label={labelOrNull(name)}/>
          case FormDiff.ElementRemoved  (Some(name))           => <diff type="element-removed"   name={name} label={labelOrNull(name)}/>
          case _                                               => <diff type="unspecified"/>
        }
      }</diffs>
    }
  }
}