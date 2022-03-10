/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.fr.process.SimpleProcess._
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, Mediatypes}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.XFormsNames._

import scala.language.postfixOps
import scala.util.Try


trait FormRunnerActions extends FormRunnerActionsCommon {

  self =>

  import FormRunnerRenderedFormat._

  val AllowedFormRunnerActions: Map[String, Action] =
    CommonAllowedFormRunnerActions                        +
      ("email"                  -> trySendEmail _)        +
      ("send"                   -> trySend _)             +
      ("review"                 -> tryNavigateToReview _) +
      ("edit"                   -> tryNavigateToEdit _)   +
      ("open-rendered-format"   -> tryOpenRenderedFormat)

  def trySendEmail(params: ActionParams): Try[Any] =
    Try {
      implicit val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val selectedRenderFormats =
        RenderedFormat.values filter { format =>
          booleanFormRunnerProperty(s"oxf.fr.email.attach-${format.entryName}")
        }

      selectedRenderFormats foreach
        (tryCreatePdfOrTiffIfNeeded(params, _).get)

      val currentFormLang = FormRunner.currentLang

      val pdfTiffParams =
        for {
          format    <- RenderedFormat.values.toList
          (path, _) <- pdfOrTiffPathOpt(
              urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
              format               = format,
              pdfTemplateOpt       = findPdfTemplate(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, Some(currentFormLang)),
              defaultLang          = currentFormLang
            )
        } yield
          format.entryName -> path

      recombineQuery(
        s"/fr/service/$app/$form/email/$document",
        pdfTiffParams ::: createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)
      )
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_NONE)

  def trySend(params: ActionParams): Try[Any] =
    Try {

      ensureDataCalculationsAreUpToDate()

      implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

      val propertyPrefixOpt = paramByNameOrDefault(params, "property")

      def findParamValue(name: String): Option[String] = {

        def fromParam    = paramByName(params, name)
        def fromProperty = propertyPrefixOpt flatMap (prefix => formRunnerProperty(prefix + "." + name))
        def fromDefault  = DefaultSendParameters.get(name)

        fromParam orElse fromProperty orElse fromDefault
      }

      object SendProcessParams extends ProcessParams {
        def runningProcessId  : String  = self.runningProcessId.get
        def app               : String  = formRunnerParams.app
        def form              : String  = formRunnerParams.form
        def formVersion       : Int     = formRunnerParams.formVersion
        def document          : String  = formRunnerParams.document.get
        def valid             : Boolean = FormRunner.dataValid
        def language          : String  = FormRunner.currentLang
        def dataFormatVersion : String  = findParamValue(DataFormatVersionName) map evaluateValueTemplate get
        def workflowStage     : String  = FormRunner.documentWorkflowStage.getOrElse("")
      }

      // Append query parameters to the URL and evaluate XVTs
      val evaluatedPropertiesAsMap = {

        def updateUri(uri: String) =
          FormRunnerActionsSupport.updateUriWithParams(
            processParams       = SendProcessParams,
            uri                 = uri,
            requestedParamNames = findParamValue("parameters").toList flatMap (_.splitTo[List]())
          )

        // `ProcessParser` should handle all the escapes, but it doesn't right now. So, specifically for
        // `headers`, we handle line breaks here.
        // https://github.com/orbeon/orbeon-forms/issues/4295
        def updateLineBreaks(s: String) =
          s.replace("\\r\\n", "\n").replace("\\n", "\n")

        val propertiesAsPairs =
          SendParameterKeys map (key => key -> findParamValue(key))

        propertiesAsPairs map {
          case (n @ "uri",     s @ Some(_)) => n -> (s map evaluateValueTemplate map updateUri)
          case (n @ "method",  s @ Some(_)) => n -> (s map evaluateValueTemplate map (_.toLowerCase))
          case (n @ "headers", s @ Some(_)) => n -> (s map updateLineBreaks map evaluateValueTemplate)
          case (n,             s @ Some(_)) => n -> (s map evaluateValueTemplate)
          case other                       => other
        } toMap
      }

      // The token can be `xml`, `metadata`, `pdf`, `tiff`, `pdf-url`, `tiff-url`,
      // `excel-with-named-ranges`, `xml-form-structure-and-data`.
      val contentToken = evaluatedPropertiesAsMap("content").get.trimAllToEmpty
      val renderedFormatContentToken = RenderedFormat.withNameOption(contentToken)

      // Handle defaults which depend on other properties
      val evaluatedSendProperties = {

        def findDefaultSerialization(method: String) = method match {
          case "post" | "put" if renderedFormatContentToken.isDefined => "application/octet-stream"
          case "post" | "put"                                         => ContentTypes.XmlContentType
          case _                                                      => "none"
        }

        def findDefaultContentType =
          renderedFormatContentToken                                      flatMap
            FormRunnerRenderedFormat.SupportedRenderFormatsMediatypes.get orElse
            Some(ContentTypes.XmlContentType)

        def findDefaultPruneMetadata(dataFormatVersion: String) = dataFormatVersion match {
          case "edge" => "false"
          case _      => "true"
        }

        val effectiveSerialization =
          evaluatedPropertiesAsMap.get("serialization").flatten orElse
            (evaluatedPropertiesAsMap.get("method").flatten map findDefaultSerialization)

        val effectiveContentType =
          evaluatedPropertiesAsMap.get(Headers.ContentTypeLower).flatten orElse
            findDefaultContentType

        val effectivePruneMetadata =
          evaluatedPropertiesAsMap.get(PruneMetadataName).flatten orElse
            (evaluatedPropertiesAsMap.get(DataFormatVersionName).flatten map findDefaultPruneMetadata)

        // Allow `prune` to override `nonrelevant` for backward compatibility

        val effectiveNonRelevant =
          evaluatedPropertiesAsMap.get("prune").flatten collect {
            case "false" => RelevanceHandling.Keep.entryName.toLowerCase
            case _       => RelevanceHandling.Remove.entryName.toLowerCase
          } orElse
            evaluatedPropertiesAsMap.get(NonRelevantName).flatten

        evaluatedPropertiesAsMap +
          ("serialization"   -> effectiveSerialization)  +
          ("mediatype"       -> effectiveContentType  )  + // `<xf:submission>` uses `mediatype`
          (PruneMetadataName -> effectivePruneMetadata)  +
          (NonRelevantName   -> effectiveNonRelevant)
      }

      // Create rendered format if needed
      val selectedRenderFormatOpt =
        RenderedFormat.values find { format =>
          val formatString = format.entryName
          Set(formatString, s"$formatString-url")(contentToken)
        }

      val formatKeyOpt =
        selectedRenderFormatOpt map
          (tryCreatePdfOrTiffIfNeeded(params, _).get)

      // Set data-safe-override as we know we are not losing data upon navigation. This happens:
      // - with changing mode (tryChangeMode)
      // - when navigating away using the "send" action
      if (evaluatedSendProperties.get("replace").flatten.contains(XFORMS_SUBMIT_REPLACE_ALL))
        setvalue(persistenceInstance.rootElement / "data-safe-override", "true")

      val evaluatedSendPropertiesWithKey =
        evaluatedSendProperties + ("binary-content-key" -> formatKeyOpt)

      debug(s"`send` action sending submission", evaluatedSendPropertiesWithKey.iterator collect { case (k, Some(v)) => k -> v } toList)

      sendThrowOnError(s"fr-send-submission", evaluatedSendPropertiesWithKey)
    }

  private def tryChangeMode(
    replace            : String,
    formTargetOpt      : Option[String] = None,
    showProgress       : Boolean        = true,
    responseIsResource : Boolean        = false)(
    path               : String
  ): Try[Any] =
    Try {
      val params: List[Option[(Option[String], String)]] =
        List(
          Some(             Some("uri")                   -> prependUserParamsForModeChange(prependCommonFormRunnerParameters(path, forNavigate = false))),
          Some(             Some("method")                -> HttpMethod.POST.entryName.toLowerCase),
          Some(             Some(NonRelevantName)         -> RelevanceHandling.Keep.entryName.toLowerCase),
          Some(             Some("replace")               -> replace),
          Some(             Some(ShowProgressName)        -> showProgress.toString),
          Some(             Some("content")               -> "xml"),
          Some(             Some(DataFormatVersionName)   -> DataFormatVersion.Edge.entryName),
          Some(             Some(PruneMetadataName)       -> false.toString),
          Some(             Some("parameters")            -> s"$FormVersionParam $DataFormatVersionName"),
          formTargetOpt.map(Some(FormTargetName)          -> _),
          Some(             Some("response-is-resource")  -> responseIsResource.toString)
        )
      params.flatten.toMap
    } flatMap
      trySend

  def tryNavigateToReview(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      s"/fr/$app/$form/view/$document"
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL)

  def tryNavigateToEdit(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      s"/fr/$app/$form/edit/$document"
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL)

  def tryOpenRenderedFormat(params: ActionParams): Try[Any] =
    Try {
      implicit val frParams: FormRunnerParams = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val renderedFormat = (
        paramByName(params, "format")
        flatMap   trimAllToOpt
        flatMap   RenderedFormat.withNameOption
        getOrElse RenderedFormat.Pdf
      )

      def extensionForRenderedFormat(renderedFormat: RenderedFormat): String =
        Mediatypes.getExtensionForMediatypeOrThrow(FormRunnerRenderedFormat.SupportedRenderFormatsMediatypes(renderedFormat))

      // TODO: Use namespaces from appropriate scope.
      val fullFilename = {
        val filenameProperty            = s"oxf.fr.detail.${renderedFormat.entryName}.filename"
        val filenamePropertyValue       = formRunnerProperty(filenameProperty).flatMap(trimAllToOpt)
        val filenameFromProperty        = filenamePropertyValue.map(evaluateString(_, xpathContext)).flatMap(trimAllToOpt)
        val escapedFilenameFromProperty = filenameFromProperty.map(EscapeURI.escape(_, "-_.~").toString)
        val filename                    = escapedFilenameFromProperty.getOrElse(currentXFormsDocumentId)

        s"$filename.${extensionForRenderedFormat(renderedFormat)}"
      }

      val path =
        buildRenderedFormatPath(
          params          = params,
          renderedFormat  = renderedFormat,
          fullFilename    = Some(fullFilename),
          currentFormLang = FormRunner.currentLang
        )

      val formTargetOpt =
        renderedFormat match {
          case RenderedFormat.Pdf | RenderedFormat.Tiff                                     => Some("_blank")
          case RenderedFormat.ExcelWithNamedRanges | RenderedFormat.XmlFormStructureAndData => None
        }

      (path, formTargetOpt)
    } flatMap { case (path, formTargetOpt) =>
      tryChangeMode(
        replace            = XFORMS_SUBMIT_REPLACE_ALL,
        showProgress       = false,
        formTargetOpt      = formTargetOpt,
        responseIsResource = true
      )(
        path               = path
      )
    }

  def clearRenderedFormatsResources(): Try[Any] = Try {

    val childElems = FormRunnerActionsCommon.findUrlsInstanceRootElem.toList child *

    // Remove resource and temporary file if any
    childElems map (_.stringValue) flatMap trimAllToOpt foreach { path =>
      XFormsAssetServer.tryToRemoveDynamicResource(path, removeFile = true)
    }

    // Clear stored paths
    delete(childElems)
  }

  private val ParamsToExcludeUponModeChange = StateParamNames + DataFormatVersionName

  // Defaults except for `uri`, `serialization` and `prune-metadata` (latter two's defaults depend on other params)
  private val DefaultSendParameters = Map(
    "method"              -> HttpMethod.POST.entryName.toLowerCase,
    NonRelevantName       -> RelevanceHandling.Remove.entryName.toLowerCase,
    "annotate"            -> "",
    "replace"             -> XFORMS_SUBMIT_REPLACE_NONE,
    "content"             -> "xml",
    DataFormatVersionName -> DataFormatVersion.V400.entryName,
    "parameters"          -> s"app form form-version document valid language process $DataFormatVersionName"
  )

  private val SendParameterKeys = List(
    "uri",
    "serialization",
    PruneMetadataName,
    Headers.ContentTypeLower,
    "headers",
    ShowProgressName,
    FormTargetName,
    "prune", // for backward compatibility,
    "response-is-resource",
    "binary-content-key"
  ) ++ DefaultSendParameters.keys

  private def prependUserParamsForModeChange(pathQuery: String) = {

    val (path, params) = splitQueryDecodeParams(pathQuery)

    val newParams =
      for {
        (name, values) <- inScopeContainingDocument.getRequestParameters.toList
        if ! ParamsToExcludeUponModeChange(name)
        value          <- values
      } yield
        name -> value

    recombineQuery(path, newParams ::: params)
  }

  private def buildRenderedFormatPath(
    params          : ActionParams,
    renderedFormat  : RenderedFormat,
    fullFilename    : Option[String],
    currentFormLang : String)(implicit
    frParams        : FormRunnerParams
  ): String = {

    val FormRunnerParams(app, form, _, Some(document), _, _) = frParams

    val urlParams =
      fullFilename.toList.map("fr-rendered-filename" ->) :::
        createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)

    renderedFormat match {
      case RenderedFormat.Pdf | RenderedFormat.Tiff =>
        recombineQuery(
          s"/fr/${if (fullFilename.isDefined) "" else "service/"}$app/$form/${renderedFormat.entryName}/$document",
          urlParams
        )
      case RenderedFormat.ExcelWithNamedRanges | RenderedFormat.XmlFormStructureAndData =>
        recombineQuery(
          s"/fr/service/$app/$form/export/$document?export-format=${renderedFormat.entryName}",
          urlParams
        )
    }
  }

  // Create if needed and return the element key name
  private def tryCreatePdfOrTiffIfNeeded(
    params   : ActionParams,
    format   : RenderedFormat)(implicit
    frParams : FormRunnerParams
  ): Try[String] =
    Try {

      val currentFormLang = FormRunner.currentLang
      val pdfTemplateOpt  = findPdfTemplate(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, Some(currentFormLang))

      pdfOrTiffPathOpt(
        urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
        format               = format,
        pdfTemplateOpt       = pdfTemplateOpt,
        defaultLang          = currentFormLang
      ) match {
        case Some((_, key)) => key
        case None =>

          val path =
            buildRenderedFormatPath(
              params          = params,
              renderedFormat  = format,
              fullFilename    = None,
              currentFormLang = currentFormLang
            )

          def processSuccessResponse() = {

            val response = topLevelInstance(FormModel, "fr-send-submission-response").get

            val node =
              getOrCreatePdfTiffPathElemOpt(
                urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
                format               = format,
                pdfTemplateOpt       = pdfTemplateOpt,
                defaultLang          = currentFormLang,
                create               = true
              ).get

            response.rootElement.stringValue.trimAllToOpt foreach { pathToTmpFile =>
              setvalue(
                ref   = node,
                value = pathToTmpFile
              )
            }

            node.localname
          }

          tryChangeMode(XFORMS_SUBMIT_REPLACE_INSTANCE)(path).get
          processSuccessResponse()
      }
    }
}
