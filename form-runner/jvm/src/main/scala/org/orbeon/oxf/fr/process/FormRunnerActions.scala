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
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.fr.SimpleDataMigration.DataMigrationBehavior
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.permission.ModeType
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.fr.process.SimpleProcess._
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.processor.XFormsAssetServer
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.XFormsNames._

import java.net.URI
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
        (tryCreateRenderedFormatIfNeeded(params, _).get)

      val currentFormLang = FormRunner.currentLang

      val pdfTiffParams =
        for {
          renderedFormat <- RenderedFormat.values.toList
          (uri, _)       <- renderedFormatPathOpt(
              urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
              renderedFormat       = renderedFormat,
              pdfTemplateOpt       = findPdfTemplate(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, Some(currentFormLang)),
              defaultLang          = currentFormLang
            )
        } yield
          renderedFormat.entryName -> uri.toString

      // https://github.com/orbeon/orbeon-forms/issues/5911
      val emailDataFormatVersion =
        paramByName(params, DataFormatVersionName).map(DataFormatVersion.withName).getOrElse(DataFormatVersion.V400)

      val emailParam =
        (s"email-$DataFormatVersionName" -> emailDataFormatVersion.entryName)

      val path =
        recombineQuery(
          s"/fr/service/$app/$form/email/$document",
          emailParam     ::
          pdfTiffParams  :::
          createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)
        )

      (path, formRunnerParams.modeType)
    } flatMap { case (path, sourceModeType) =>
      tryChangeMode(XFORMS_SUBMIT_REPLACE_NONE, path, sourceModeType = sourceModeType)
    }

  def trySend(params: ActionParams): Try[Any] =
    Try {

      ensureDataCalculationsAreUpToDate()

      implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
      val FormRunnerParams(currentApp, currentForm, currentFormVersion, currentDocumentOpt, currentIsDraft, _) = formRunnerParams

      val propertyPrefixOpt = paramByNameOrDefault(params, "property")

      def findParamValue(name: String): Option[String] = {

        def fromParam    = paramByName(params, name)
        def fromProperty = propertyPrefixOpt flatMap (prefix => formRunnerProperty(prefix + "." + name))
        def fromDefault  = DefaultSendParameters.get(name)

        fromParam orElse fromProperty orElse fromDefault
      }

      // Append query parameters to the URL and evaluate XVTs
      val evaluatedPropertiesAsMap = {

        object SendProcessParams extends ProcessParams {
          def runningProcessId  : String  = self.runningProcessId.get
          def app               : String  = currentApp
          def form              : String  = currentForm
          def formVersion       : Int     = currentFormVersion
          def document          : String  = currentDocumentOpt.get
          def valid             : Boolean = FormRunner.dataValid
          def language          : String  = FormRunner.currentLang
          def dataFormatVersion : String  = findParamValue(DataFormatVersionName) map evaluateValueTemplate get
          def workflowStage     : String  = FormRunner.documentWorkflowStage.getOrElse("")
        }

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
          case other                        => other
        } toMap
      }

      val contentToSend =
        ContentToSend.fromContentTokens(
          ContentToken.fromString(evaluatedPropertiesAsMap("content").getOrElse(throw new IllegalArgumentException))
        )

      val distinctRenderedFormats = contentToSend match {
        case ContentToSend.Single(ContentToken.Rendered(format, _)) => Set(format)
        case ContentToSend.Multipart(parts)                         => parts collect { case ContentToken.Rendered(format, _) => format } toSet
        case ContentToSend.NoContent | ContentToSend.Single(_)      => Set.empty[RenderedFormat]
      }

      // Handle defaults which depend on other properties
      val evaluatedSendProperties = {

        def getDefaultSerialization(method: HttpMethod): String =
          (method, contentToSend) match {
            case (HttpMethod.POST | HttpMethod.PUT, sd: SerializationDefaults) => sd.defaultSerialization
            case _                                                             => "none"
          }

        def findDefaultContentType(method: HttpMethod): Option[String] =
          (method, contentToSend) match {
            case (HttpMethod.POST | HttpMethod.PUT, sd: SerializationDefaults) => Some(sd.defaultContentType)
            case _                                                             => None
          }

        def findDefaultPruneMetadata(dataFormatVersion: String): String = dataFormatVersion match {
          case "edge" => "false"
          case _      => "true"
        }

        val effectiveSerialization =
          evaluatedPropertiesAsMap.get("serialization").flatten orElse
            (evaluatedPropertiesAsMap.get("method").flatten.map(HttpMethod.withNameInsensitive) map getDefaultSerialization)

        val effectiveContentType =
          evaluatedPropertiesAsMap.get(Headers.ContentTypeLower).flatten orElse
            (evaluatedPropertiesAsMap.get("method").flatten.map(HttpMethod.withNameInsensitive) flatMap findDefaultContentType)

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
      val renderedFormatTmpFileUris =
        distinctRenderedFormats map { format =>
          tryCreateRenderedFormatIfNeeded(params, format).get._1 -> format
        }

      // Create multipart if needed
      val multipartTmpFileUriOpt =
        contentToSend match {
          case ContentToSend.Multipart(parts) =>

            def maybeMigrateData(originalData: DocumentNodeInfoType): DocumentNodeInfoType =
              GridDataMigration.dataMaybeMigratedFromEdge(
                app                     = currentApp,
                form                    = currentForm,
                data                    = originalData,
                metadataOpt             = frc.metadataInstance.map(_.root),
                dataFormatVersionString = FormRunnerPersistence.providerDataFormatVersionOrThrow(formRunnerParams.appForm).entryName,
                pruneMetadata           = evaluatedSendProperties.get(PruneMetadataName).flatten.contains(true.toString),
              )

            val basePath =
              frc.createFormDataBasePath(
                app               = currentApp,
                form              = currentForm,
                isDraft           = currentIsDraft.contains(true),
                documentIdOrEmpty = currentDocumentOpt.getOrElse("")
              )

            val relevanceHandling =
              evaluatedSendProperties.get(NonRelevantName).flatten
                .orElse(DefaultSendParameters.get(NonRelevantName))
                .map(RelevanceHandling.withNameInsensitive)
                .getOrElse(throw new IllegalStateException)

            Some(
              FormRunnerActionsSupport.buildMultipartEntity(
                dataMaybeLiveMaybeMigrated = maybeMigrateData(frc.formInstance.root),
                parts                      = parts,
                renderedFormatTmpFileUris  = renderedFormatTmpFileUris,
                dataBasePaths              = List(basePath -> currentFormVersion),
                relevanceHandling          = relevanceHandling,
                annotateWith               = evaluatedSendProperties.get("annotate").flatten.map(_.splitTo[Set]()).getOrElse(Set.empty),
                headersGetter              = inScopeContainingDocument.headersGetter
              )
            )
          case _ => None
        }

      // Set data-safe-override as we know we are not losing data upon navigation. This happens:
      // - with changing mode (tryChangeMode)
      // - when navigating away using the "send" action
      if (evaluatedSendProperties.get("replace").flatten.contains(XFORMS_SUBMIT_REPLACE_ALL))
        setvalue(persistenceInstance.rootElement / "data-safe-override", "true")

      // If submitting binary (from the `xf:submission`'s point of view) find the URL
      // For rendered formats, if there are multiple of them, we fall under the multipart case
      def binaryContentUrlProperty =
        "binary-content-url" -> multipartTmpFileUriOpt.map(_._1).orElse(renderedFormatTmpFileUris.headOption.map(_._1)).map(_.toString)

      // Add the `boundary` parameter to the mediatype if needed
      def multipartContentTypePropertyOpt =
        multipartTmpFileUriOpt map { case (_, boundary) =>
          "mediatype" -> evaluatedSendProperties.get("mediatype").flatten.map(ct => s"$ct; boundary=$boundary")}

      val evaluatedSendPropertiesWithUpdates =
        evaluatedSendProperties + binaryContentUrlProperty ++ multipartContentTypePropertyOpt

      debug(s"`send` action sending submission", evaluatedSendPropertiesWithUpdates.iterator collect { case (k, Some(v)) => k -> v } toList)

      sendThrowOnError(s"fr-send-submission", evaluatedSendPropertiesWithUpdates)
    }

  private def tryChangeMode(
    replace            : String,
    path               : String,
    sourceModeType     : ModeType,
    formTargetOpt      : Option[String] = None,
    showProgress       : Boolean        = true,
    responseIsResource : Boolean        = false
  ): Try[Any] =
    Try {
      val params: List[Option[(Option[String], String)]] =
        List(
          Some(             Some("uri")                  -> prependUserAndStandardParamsForModeChange(propagateDataPermissions = true, prependCommonFormRunnerParameters(path, forNavigate = false))),
          Some(             Some("method")               -> HttpMethod.POST.entryName.toLowerCase),
          Some(             Some(NonRelevantName)        -> RelevanceHandling.Keep.entryName.toLowerCase),
          Some(             Some("replace")              -> replace),
          Some(             Some(ShowProgressName)       -> showProgress.toString),
          Some(             Some("content")              -> "xml"),
          Some(             Some(DataFormatVersionName)  -> getOrGuessFormDataFormatVersion(frc.metadataInstance.map(_.rootElement)).entryName), // use the form's current internal data format version, not `Edge`
          Some(             Some(PruneMetadataName)      -> false.toString),
          Some(             Some("parameters")           -> s"$FormVersionParam $DataFormatVersionName"),
          formTargetOpt.map(Some(FormTargetName)         -> _),
          Some(             Some("response-is-resource") -> responseIsResource.toString)
        )
      params.flatten.toMap
    } flatMap
      trySend

  def tryNavigateToReview(params: ActionParams): Try[Any] =
    Try {
      val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      (s"/fr/$app/$form/view/$document", formRunnerParams.modeType)
    } flatMap { case (path, sourceModeType) =>
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL, path, sourceModeType)
    }

  def tryNavigateToEdit(params: ActionParams): Try[Any] =
    Try {
      val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      (s"/fr/$app/$form/edit/$document", formRunnerParams.modeType)
    } flatMap { case (path, sourceModeType) =>
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL, path, sourceModeType)
    }

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

      // Q: Noticing we use `EscapeURI`, while `buildContentDispositionHeader` uses `URLEncoder.encode`. Any good
      // reason for this?
      val filename =
        EscapeURI.escape(FormRunnerActionsSupport.filenameForRenderedFormat(renderedFormat), "-_.~").toString

      val path =
        buildRenderedFormatPath(
          params          = params,
          renderedFormat  = renderedFormat,
          fullFilename    = Some(filename),
          currentFormLang = frc.currentLang
        )

      val formTargetOpt =
        renderedFormat match {
          case RenderedFormat.Pdf | RenderedFormat.Tiff                                     => Some("_blank")
          case RenderedFormat.ExcelWithNamedRanges | RenderedFormat.XmlFormStructureAndData => None
        }

      (path, formTargetOpt, frParams.modeType)
    } flatMap { case (path, formTargetOpt, sourceModeType) =>
      tryChangeMode(
        replace            = XFORMS_SUBMIT_REPLACE_ALL,
        path               = path,
        sourceModeType     = sourceModeType,
        showProgress       = false,
        formTargetOpt      = formTargetOpt,
        responseIsResource = true
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

  private val ParamsToExcludeUponModeChange = StateParamNames + DataFormatVersionName + DataMigrationBehaviorName

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
    "prune" // for backward compatibility,
  ) ++ DefaultSendParameters.keys

  private def prependUserAndStandardParamsForModeChange(propagateDataPermissions: Boolean, pathQuery: String) = {

    val (path, params) = splitQueryDecodeParams(pathQuery)

    val dataMigrationParam =
      DataMigrationBehaviorName -> DataMigrationBehavior.Disabled.entryName

    // https://github.com/orbeon/orbeon-forms/issues/2999
    // https://github.com/orbeon/orbeon-forms/issues/5437
    val internalAuthorizedOperationsParam =
      propagateDataPermissions flatList {
        val ops = authorizedOperations
        ops.nonEmpty list (InternalAuthorizedOperationsParam -> FormRunnerOperationsEncryption.encryptOperations(ops))
      }

    val userParams =
      for {
        (name, values) <- inScopeContainingDocument.getRequestParameters.toList
        if ! ParamsToExcludeUponModeChange(name)
        value          <- values
      } yield
        name -> value

    recombineQuery(path, dataMigrationParam :: internalAuthorizedOperationsParam ::: userParams ::: params)
  }

  private def buildRenderedFormatPath(
    params         : ActionParams,
    renderedFormat : RenderedFormat,
    fullFilename   : Option[String],
    currentFormLang: String)(implicit
    frParams       : FormRunnerParams
  ): String = {

    val FormRunnerParams(app, form, _, Some(document), _, _) = frParams

    val urlParams =
      fullFilename.toList.map("fr-rendered-filename" ->) :::
        createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)

    def pathPrefix(usePagePath: Boolean) =
      s"/fr/${if (usePagePath) "" else "service/"}$app/$form"

    renderedFormat match {
      case RenderedFormat.Pdf | RenderedFormat.Tiff =>
        recombineQuery(
          s"${pathPrefix(usePagePath = fullFilename.isDefined)}/${renderedFormat.entryName}/$document",
          urlParams
        )
      case RenderedFormat.ExcelWithNamedRanges | RenderedFormat.XmlFormStructureAndData =>
        recombineQuery(
          s"${pathPrefix(usePagePath = false)}/export/$document?export-format=${renderedFormat.entryName}",
          urlParams
        )
    }
  }

  // Create if needed and return the element key name
  private def tryCreateRenderedFormatIfNeeded(
    params        : ActionParams,
    renderedFormat: RenderedFormat)(implicit
    frParams      : FormRunnerParams
  ): Try[(URI, String)] =
    Try {

      val currentFormLang = frc.currentLang
      val pdfTemplateOpt  = findPdfTemplate(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, Some(currentFormLang))

      renderedFormatPathOpt(
        urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
        renderedFormat       = renderedFormat,
        pdfTemplateOpt       = pdfTemplateOpt,
        defaultLang          = currentFormLang
      ) match {
        case Some(pathToTmpFileWithKey) => pathToTmpFileWithKey
        case None =>

          val path =
            buildRenderedFormatPath(
              params          = params,
              renderedFormat  = renderedFormat,
              fullFilename    = None,
              currentFormLang = currentFormLang
            )

          tryChangeMode(XFORMS_SUBMIT_REPLACE_INSTANCE, path, sourceModeType = frParams.modeType).get

          locally {

            val response = topLevelInstance(FormModel, "fr-send-submission-response").get

            val node =
              getOrCreateRenderedFormatPathElemOpt(
                urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
                format               = renderedFormat,
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

            URI.create(node.stringValue) -> node.localname
          }
      }
    }
}
