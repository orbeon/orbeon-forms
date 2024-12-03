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

import cats.syntax.option.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerPersistence.*
import org.orbeon.oxf.fr.Names.*
import org.orbeon.oxf.fr.SimpleDataMigration.DataMigrationBehavior
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.permission.ModeType
import org.orbeon.oxf.fr.process.ProcessInterpreter.*
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.event.XFormsEvent.PropertyValue
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent
import org.orbeon.oxf.xforms.processor.XFormsAssetServerRoute
import org.orbeon.oxf.xforms.submission.ReplaceType
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.XFormsNames.*

import java.net.URI
import scala.language.postfixOps
import scala.util.Try


trait FormRunnerActions
  extends FormRunnerActionsCommon
     with SimpleProcessCommon {

  self: XFormsActions => // for `tryCallback`

  import FormRunnerRenderedFormat._

  val AllowedFormRunnerActions: Map[String, Action] =
    CommonAllowedFormRunnerActions                        +
      ("email"                  -> trySendEmail _)        +
      ("send"                   -> trySend _)             +
      ("review"                 -> tryNavigateToReview _) +
      ("edit"                   -> tryNavigateToEdit _)   +
      ("open-rendered-format"   -> tryOpenRenderedFormat)

  case class SendActionParams(
    uri                : String,
    method             : HttpMethod,
    relevanceHandling  : RelevanceHandling,
    annotateWith       : Set[String],
    showProgress       : Boolean,
    responseIsResource : Boolean, // TODO: Should this be a parameter?
    formTargetOpt      : Option[String],
    contentToSend      : ContentToSend,
    dataFormatVersion  : DataFormatVersion,
    pruneMetadata      : Boolean,
    pruneTmpAttMetadata: Boolean,
    replace            : ReplaceType,
    headersOpt         : Option[String],
    serialization      : String, // TODO: type
    binaryContentUrlOpt: Option[URI],
    contentTypeOpt     : Option[String],
    isModeChange       : Boolean
  ) {
     def toPropertyValues: List[PropertyValue] =
      List(
        PropertyValue("uri"                   , uri.some),
        PropertyValue("method"                , method.entryName.toUpperCase.some),
        PropertyValue(NonRelevantName         , relevanceHandling.entryName.toLowerCase.some),
        PropertyValue("annotate"              , annotateWith.mkString(" ").some),
        PropertyValue("show-progress"         , showProgress.toString.some),
        PropertyValue("response-is-resource"  , responseIsResource.toString.some),
        PropertyValue("formtarget"            , formTargetOpt.getOrElse("").some),
        PropertyValue("content"               , ContentToSend.toStringOpt(contentToSend)),
        PropertyValue(DataFormatVersionName   , dataFormatVersion.entryName.some),
        PropertyValue(PruneMetadataName       , pruneMetadata.toString.some),
        PropertyValue("prune-tmp-att-metadata", pruneTmpAttMetadata.toString.some),
        PropertyValue("replace"               , replace.entryName.some),
        PropertyValue("serialization"         , serialization.some),
        PropertyValue("binary-content-url"    , binaryContentUrlOpt.map(_.toString)),
        PropertyValue("mediatype"             , contentTypeOpt),
        PropertyValue("headers"               , headersOpt),
        PropertyValue("is-mode-change"        , isModeChange.toString.some),
      )
  }

  def trySendEmail(params: ActionParams): ActionResult =
    ActionResult.trySync {
      implicit val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val selectedRenderFormats =
        RenderedFormat.values filter { format =>
          booleanFormRunnerProperty(s"oxf.fr.email.attach-${format.entryName}")
        }

      selectedRenderFormats foreach
        (tryCreateRenderedFormatIfNeeded(params, _).get)

      val currentFormLang = FormRunner.currentLang

      val templateParams      = paramByNameUseAvt(params, "template").map("fr-template" -> _).toList
      val templateMatchParam  = paramByNameUseAvt(params, "match").getOrElse("first")
      val templateMatchParams = Seq("fr-match" -> templateMatchParam).toList
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
        paramByNameUseAvt(params, DataFormatVersionName).map(DataFormatVersion.withName).getOrElse(DataFormatVersion.V400)

      val emailParam =
        (s"email-$DataFormatVersionName" -> emailDataFormatVersion.entryName)

      val path =
        recombineQuery(
          s"/fr/service/$app/$form/email/$document",
          emailParam          ::
          templateParams      :::
          templateMatchParams :::
          pdfTiffParams       :::
          createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)
        )

      tryChangeMode(ReplaceType.None, path, sourceModeType = formRunnerParams.modeType)
    }

  def trySend(params: ActionParams): ActionResult = {

    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    val FormRunnerParams(currentApp, currentForm, currentFormVersion, currentDocumentOpt, currentIsDraft, _) = formRunnerParams

    implicit val xfcd: XFormsContainingDocument = inScopeContainingDocument

    val propertyPrefixOpt = paramByNameOrDefaultUseAvt(params, "property")

    def findParamValue(name: String): Option[String] = {

      def fromParam    = paramByNameUseAvt(params, name)
      def fromProperty = propertyPrefixOpt.flatMap(prefix => formRunnerProperty(prefix + "." + name))

      fromParam.orElse(fromProperty).map(evaluateValueTemplate)
    }

    def updateUri(uri: String, parameters: List[String], dataFormatVersionNoneIfEdge: Option[DataFormatVersion]) = {

      object SendProcessParams extends ProcessParams {
        def runningProcessId  : String  = self.runningProcessId.get
        def app               : String  = currentApp
        def form              : String  = currentForm
        def formVersion       : Int     = currentFormVersion
        def document          : String  = currentDocumentOpt.get
        def valid             : Boolean = FormRunner.dataValid
        def language          : String  = FormRunner.currentLang
        def dataFormatVersion : String  = dataFormatVersionNoneIfEdge.map(_.entryName).getOrElse("edge")
        def workflowStage     : String  = FormRunner.documentWorkflowStage.getOrElse("")
      }

      FormRunnerActionsSupport.updateUriWithParams(
        processParams       = SendProcessParams,
        uri                 = uri,
        requestedParamNames = parameters
      )
    }

    // `ProcessParser` should handle all the escapes, but it doesn't right now. So, specifically for
    // `headers`, we handle line breaks here.
    // https://github.com/orbeon/orbeon-forms/issues/4295
    def updateLineBreaks(s: String) =
      s.replace("\\r\\n", "\n").replace("\\n", "\n")

    val dataFormatVersionNoneIfEdge = findParamValue(DataFormatVersionName).map(DataFormatVersion.withNameNoneIfEdge)            .getOrElse(DataFormatVersion.V400.some)
    val parameters                  = findParamValue("parameters").map(_.splitTo[List]())                                        .getOrElse(List("app", "form", "form-version", "document", "valid", "language", "process", DataFormatVersionName))

    val uri                         = findParamValue("uri").map(updateUri(_, parameters, dataFormatVersionNoneIfEdge))           .getOrElse(throw new IllegalArgumentException("uri"))
    val method                      = findParamValue("method").map(HttpMethod.withNameInsensitive)                               .getOrElse(HttpMethod.POST)
    val headersOpt                  = findParamValue("headers").map(updateLineBreaks)
    val serializationOpt            = findParamValue("serialization")
    val pruneMetadataOpt            = findParamValue(PruneMetadataName).map(_.toBoolean)
    val contentTypeOpt              = findParamValue(Headers.ContentTypeLower)
    val showProgress                = findParamValue(ShowProgressName).map(_.toBoolean)                                          .getOrElse(true)
    val formTargetOpt               = findParamValue(FormTargetName)
    val pruneOpt                    = findParamValue("prune").map(_.toBoolean) // for backward compatibility
    val relevanceHandling           = findParamValue(NonRelevantName).map(RelevanceHandling.withNameInsensitive)                 .getOrElse(RelevanceHandling.Remove)
    val annotateWith                = findParamValue("annotate").map(_.splitTo[Set]())                                           .getOrElse(Set.empty)
    val replace                     = findParamValue("replace")                                                                  .getOrElse(XFORMS_SUBMIT_REPLACE_NONE)
    val contentToSend               = findParamValue("content").map(ContentToken.fromString).map(ContentToSend.fromContentTokens).getOrElse(ContentToSend.Single(ContentToken.Xml))

    val distinctRenderedFormats = contentToSend match {
      case ContentToSend.Single(ContentToken.Rendered(format, _)) => Set(format)
      case ContentToSend.Multipart(parts)                         => parts collect { case ContentToken.Rendered(format, _) => format } toSet
      case ContentToSend.NoContent | ContentToSend.Single(_)      => Set.empty[RenderedFormat]
    }

    // Handle defaults which depend on other properties
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

    val effectiveSerialization =
      serializationOpt.getOrElse(getDefaultSerialization(method))

    val effectiveContentTypeOpt =
      contentTypeOpt.orElse(findDefaultContentType(method))

    val effectivePruneMetadata =
      pruneMetadataOpt.getOrElse(dataFormatVersionNoneIfEdge.nonEmpty) // default: `edge` -> `None` -> `false`, else `true`

    // Allow `prune` to override `nonrelevant` for backward compatibility
    val effectiveRelevanceHandling =
      pruneOpt match {
        case Some(false) => RelevanceHandling.Keep
        case Some(true)  => RelevanceHandling.Remove
        case None        => relevanceHandling
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
              app                        = currentApp,
              form                       = currentForm,
              data                       = originalData,
              metadataOpt                = frc.metadataInstance.map(_.root),
              dstDataFormatVersionString = FormRunnerPersistence.providerDataFormatVersionOrThrow(formRunnerParams.appForm).entryName,
              pruneMetadata              = effectivePruneMetadata,
              pruneTmpAttMetadata        = true
            )

          val basePath =
            frc.createFormDataBasePath(
              app               = currentApp,
              form              = currentForm,
              isDraft           = currentIsDraft.contains(true),
              documentIdOrEmpty = currentDocumentOpt.getOrElse("")
            )

          Some(
            FormRunnerActionsSupport.buildMultipartEntity(
              dataMaybeLiveMaybeMigrated = maybeMigrateData(frc.formInstance.root),
              parts                      = parts,
              renderedFormatTmpFileUris  = renderedFormatTmpFileUris,
              dataBasePaths              = List(basePath -> currentFormVersion),
              relevanceHandling          = effectiveRelevanceHandling,
              annotateWith               = annotateWith,
              headersGetter              = xfcd.headersGetter
            )
          )
        case _ => None
      }

    // Set `data-safe-override` as we know we are not losing data upon navigation. This happens:
    // - with changing mode (`tryChangeMode()`)
    // - when navigating away using the `send` action
    if (replace == XFORMS_SUBMIT_REPLACE_ALL)
      setvalue(persistenceInstance.rootElement / "data-safe-override", "true")

    // If submitting binary (from the `xf:submission`'s point of view) find the URL
    // For rendered formats, if there are multiple of them, we fall under the multipart case
    def binaryContentUrlOpt =
      multipartTmpFileUriOpt.orElse(renderedFormatTmpFileUris.headOption).map(_._1)

    // Add the `boundary` parameter to the mediatype if needed
    def multipartContentTypeOpt =
      multipartTmpFileUriOpt.flatMap { case (_, boundary) => effectiveContentTypeOpt.map(ct => s"$ct; boundary=$boundary") }

    val sendActionParams =
      SendActionParams(
        uri                 = uri,
        method              = method,
        relevanceHandling   = effectiveRelevanceHandling,
        annotateWith        = annotateWith,
        showProgress        = showProgress,
        responseIsResource  = false,
        formTargetOpt       = formTargetOpt,
        contentToSend       = contentToSend,
        dataFormatVersion   = dataFormatVersionNoneIfEdge.getOrElse(DataFormatVersion.Edge),
        pruneMetadata       = effectivePruneMetadata,
        pruneTmpAttMetadata = true,
        replace             = ReplaceType.withName(replace),
        headersOpt          = headersOpt,
        serialization       = effectiveSerialization,
        binaryContentUrlOpt = binaryContentUrlOpt,
        contentTypeOpt      = multipartContentTypeOpt.orElse(effectiveContentTypeOpt),
        isModeChange        = false,
      )

    debug(s"`send` action sending submission", List("params" -> sendActionParams.toString))
    ActionResult.trySync(trySendImpl(sendActionParams))
  }

  def trySendImpl(params: SendActionParams): Option[XFormsSubmitDoneEvent] = {
      ensureDataCalculationsAreUpToDate()
      sendThrowOnError(
        s"fr-send-submission",
        params.toPropertyValues
      )
    }

  private def tryChangeMode(
    replace            : ReplaceType,
    path               : String,
    sourceModeType     : ModeType,
    formTargetOpt      : Option[String] = None,
    showProgress       : Boolean        = true,
    responseIsResource : Boolean        = false
  ): Option[XFormsSubmitDoneEvent] = {
    val currentDataFormatVersion = getOrGuessFormDataFormatVersion(frc.metadataInstance.map(_.rootElement))
    trySendImpl(
      SendActionParams(
        uri                 = prependCommonFormRunnerParameters(prependUserAndStandardParamsForModeChange(path, currentDataFormatVersion), forNavigate = false),
        method              = HttpMethod.POST,
        relevanceHandling   = RelevanceHandling.Keep,
        annotateWith        = Set.empty,
        showProgress        = showProgress,
        responseIsResource  = responseIsResource,
        formTargetOpt       = formTargetOpt,
        contentToSend       = ContentToSend.Single(ContentToken.Xml),
        dataFormatVersion   = currentDataFormatVersion,
        pruneMetadata       = false,
        pruneTmpAttMetadata = false,
        replace             = replace,
        headersOpt          = None,
        serialization       = ContentTypes.XmlContentType,
        binaryContentUrlOpt = None,
        contentTypeOpt      = ContentTypes.XmlContentType.some,
        isModeChange        = true,
      )
    )
  }

  def tryNavigateToReview(params: ActionParams): ActionResult =
    ActionResult.trySync {
      val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      tryChangeMode(ReplaceType.All, s"/fr/$app/$form/view/$document", formRunnerParams.modeType)
    }

  def tryNavigateToEdit(params: ActionParams): ActionResult =
    ActionResult.trySync {
      val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()
      tryChangeMode(ReplaceType.All, s"/fr/$app/$form/edit/$document", formRunnerParams.modeType)
    }

  def tryOpenRenderedFormat(params: ActionParams): ActionResult =
    ActionResult.trySync {
      implicit val frParams: FormRunnerParams = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val renderedFormat = (
        paramByNameUseAvt(params, "format")
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

      tryChangeMode(
        replace            = ReplaceType.All,
        path               = path,
        sourceModeType     = frParams.modeType,
        showProgress       = false,
        formTargetOpt      = formTargetOpt,
        responseIsResource = true
      )

    }

  def clearRenderedFormatsResources(): Try[Any] =
    Try {

      val childElems = FormRunnerActionsCommon.findUrlsInstanceRootElem.toList child *

      // Remove resource and temporary file if any
      childElems map (_.stringValue) flatMap trimAllToOpt foreach { path =>
        XFormsAssetServerRoute.tryToRemoveDynamicResource(path, removeFile = true)
      }

      // Clear stored paths
      delete(childElems)
    }

  private val ParamsToExcludeUponModeChange =
    StateParamNames                   +
    DataMigrationBehaviorName         +
    DataFormatVersionName             +
    InternalAuthorizedOperationsParam +
    InternalWorkflowStageParam

  private def prependUserAndStandardParamsForModeChange(pathQuery: String, dataFormatVersion: DataFormatVersion): String = {

    val (originalPath, originalParams) = splitQueryDecodeParams(pathQuery)

    val dataMigrationParams =
      (DataMigrationBehaviorName -> DataMigrationBehavior.Disabled.entryName) ::
      (DataFormatVersionName     -> dataFormatVersion.entryName)              ::
      Nil

    // https://github.com/orbeon/orbeon-forms/issues/2999
    // https://github.com/orbeon/orbeon-forms/issues/5437
    val internalParams = {
        val ops = frc.authorizedOperations
        (ops.nonEmpty list (InternalAuthorizedOperationsParam -> FormRunnerOperationsEncryption.encryptOperations(ops))) :::
        FormRunner.documentWorkflowStage.toList.map(stage => InternalWorkflowStageParam -> FormRunnerOperationsEncryption.encryptString(stage))
      }

    def filterParams[T](p: List[(String, T)]): List[(String, T)] =
      p filterNot { case (name, _) => ParamsToExcludeUponModeChange(name) }

    val userParams =
      for {
        (name, values) <- filterParams(inScopeContainingDocument.getRequestParameters.toList)
        value          <- values
      } yield
        name -> value

    recombineQuery(originalPath, dataMigrationParams ::: internalParams ::: userParams ::: filterParams(originalParams))
  }

  private def buildRenderedFormatPath(
    params         : ActionParams,
    renderedFormat : RenderedFormat,
    fullFilename   : Option[String],
    currentFormLang: String
  )(implicit
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
    renderedFormat: RenderedFormat
  )(implicit
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

          tryChangeMode(ReplaceType.Instance, path, sourceModeType = frParams.modeType).get

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
