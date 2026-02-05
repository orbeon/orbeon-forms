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
import org.orbeon.oxf.externalcontext.ExternalContext.EmbeddableParam
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.frc
import org.orbeon.oxf.fr.FormRunnerPersistence.*
import org.orbeon.oxf.fr.Names.*
import org.orbeon.oxf.fr.SimpleDataMigration.DataMigrationBehavior
import org.orbeon.oxf.fr.definitions.{FormRunnerDetailMode, ModeType}
import org.orbeon.oxf.fr.email.EmailMetadata.TemplateMatch
import org.orbeon.oxf.fr.email.{EmailContent, EmailTransport}
import org.orbeon.oxf.fr.permission.Operations
import org.orbeon.oxf.fr.persistence.api.PersistenceApi
import org.orbeon.oxf.fr.process.FormRunnerExternalMode.PrivateModeMetadata
import org.orbeon.oxf.fr.process.ProcessInterpreter.*
import org.orbeon.oxf.fr.s3.{S3, S3Config}
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.properties.PropertySet
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.event.XFormsEvent.PropertyValue
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent
import org.orbeon.oxf.xforms.submission.ReplaceType
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.route.XFormsAssetServerRoute
import org.orbeon.xml.NamespaceMapping

import java.net.URI
import scala.language.postfixOps
import scala.util.Try


trait FormRunnerActions
  extends FormRunnerActionsCommon
     with SimpleProcessCommon {

  self: XFormsActions => // for `tryCallback`

  import FormRunnerRenderedFormat.*

  val AllowedFormRunnerActions: Map[String, Action] =
    CommonAllowedFormRunnerActions                      +
      ("email"                  -> trySendEmail          _) +
      ("send"                   -> trySend               _) +
      ("review"                 -> tryNavigateToReview   _) +
      ("edit"                   -> tryNavigateToEdit     _) +
      ("change-mode"            -> tryChangeMode         _) +
      ("open-rendered-format"   -> tryOpenRenderedFormat _)

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

      implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()

      // https://github.com/orbeon/orbeon-forms/issues/5911
      val emailDataFormatVersion =
        paramByNameUseAvt(params, DataFormatVersionName)
          .map(DataFormatVersion.withName)
          .getOrElse(DataFormatVersion.V400)

      // Match first or all templates
      val templateMatch =
        paramByNameUseAvt(params, "match")
          .map(TemplateMatch.withName)
          .getOrElse(TemplateMatch.First)

      val emailContentsFromTemplates = emailsToSend(
        emailDataFormatVersion = emailDataFormatVersion,
        templateMatch          = templateMatch,
        language               = paramByNameUseAvt(params, "lang").getOrElse(FormRunner.currentLang),
        templateNameOpt        = paramByNameUseAvt(params, "template"),
        pdfParams              = params // TODO: it would be cleaner to have a proper case class here instead of a Map
      )

      // S3 tests disable the actual sending of email (this parameter is not documented)
      val sendEmail = booleanParamByNameUseAvt(params, "send-email", default = true)

      if (sendEmail)
        emailContentsFromTemplates.headOption.foreach(EmailTransport.send(_).get)

      val s3Store = booleanParamByNameUseAvt(params, "s3-store", default = false)

      // Only store email contents to S3 if enabled by parameter
      if (s3Store)
        storeEmailContentsToS3(params, emailContentsFromTemplates).get
    }

  def emailsToSend(
    emailDataFormatVersion  : DataFormatVersion,
    templateMatch           : TemplateMatch,
    language                : String,
    templateNameOpt         : Option[String],
    pdfParams               : ActionParams
  )(implicit
    formRunnerParams        : FormRunnerParams
  ): List[EmailContent] = {

    implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport

    ensureDataCalculationsAreUpToDate()

    val formDefinition = PersistenceApi.readPublishedFormDefinition(
      appName  = formRunnerParams.app,
      formName = formRunnerParams.form,
      version  = FormDefinitionVersion.Specific(formRunnerParams.formVersion)
    ).get._1._2

    implicit val ctx: InDocFormRunnerDocContext = new InDocFormRunnerDocContext(formDefinition)

    val emailMetadataNodeOpt  = frc.metadataInstanceRootOpt(formDefinition).flatMap(metadata => (metadata / "email").headOption)
    val emailMetadata         = parseEmailMetadata(emailMetadataNodeOpt, formDefinition)
    val pdfRequiredByTemplate = emailMetadata.templates.exists(_.attachPdf.contains(true))

    val selectedRenderFormats =
      RenderedFormat.values filter { format =>
        booleanFormRunnerProperty(s"oxf.fr.email.attach-${format.entryName}") ||
        (format == RenderedFormat.Pdf && pdfRequiredByTemplate)
      }

    selectedRenderFormats foreach
      (tryCreateRenderedFormatIfNeeded(pdfParams, _).get)

    val currentFormLang = FormRunner.currentLang

    val urisByRenderedFormat =
      (for {
        renderedFormat <- RenderedFormat.values.toList
        (uri, _)       <- renderedFormatPathOpt(
            urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
            renderedFormat       = renderedFormat,
            pdfTemplateOpt       = findPdfTemplate(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, pdfParams, Some(currentFormLang)),
            defaultLang          = currentFormLang
          )
      } yield
        renderedFormat -> uri).toMap

    EmailContent.emailContents(
      emailMetadata          = emailMetadata,
      urisByRenderedFormat   = urisByRenderedFormat,
      emailDataFormatVersion = emailDataFormatVersion,
      templateMatch          = templateMatch,
      language               = language,
      templateNameOpt        = templateNameOpt
    )
  }

  private def storeEmailContentsToS3(
    params                    : ActionParams,
    emailContentsFromTemplates: List[EmailContent],
  )(implicit
    formRunnerParams          : FormRunnerParams
  ): Try[Unit] = {

    val formData = frc.formInstance.root

    // Retrieve S3 parameters either from action parameters or from properties
    def fromParamsOrProperties(
      name             : String,
      default          : String,
      defaultNamespaces: NamespaceMapping = ProcessInterpreter.StandardNamespaceMapping
    ): (String, NamespaceMapping) =
      paramByNameUseAvt(params, name)
        .map((_, defaultNamespaces))
        .orElse(formRunnerPropertyWithNs(s"oxf.fr.email.$name"))
        .getOrElse((default, defaultNamespaces))

    val (s3ConfigName, _               ) = fromParamsOrProperties("s3-config", default = "default")
    val (s3Path      , s3PathNamespaces) = fromParamsOrProperties("s3-path"  , default = "")

    def s3PathPrefixTry: Try[String] = Try {
      // Evaluate S3 path prefix
      s3Path
        .trimAllToOpt
        .flatMap(process.SimpleProcess.evaluateString(_, formData, s3PathNamespaces).trimAllToOpt)
        .map(_.appendSlash)
        .getOrElse("")
    }

    for {
      s3Config      <- S3Config.fromProperties(s3ConfigName)
      _             <- {
        // Implicits in for comprehensions supported in Scala 3 only
        implicit val _s3Config: S3Config = s3Config

        S3.withS3Client { implicit s3Client =>
          TryUtils.sequenceLazily(emailContentsFromTemplates) { emailContent =>
            for {
              // Evaluate the s3-path parameter/property for each email, as the evaluation might return a different
              // value for each call (e.g. date/time function)
              s3PathPrefix <- s3PathPrefixTry
              _            <- emailContent.storeToS3(s3PathPrefix)
            } yield ()
          }
        }
      }
    } yield ()
  }

  def trySend(params: ActionParams): ActionResult = {

    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    val FormRunnerParams(currentApp, currentForm, _, currentDocumentOpt, currentIsDraft, _) = formRunnerParams
    val currentFormVersion = formRunnerParams.formVersion

    implicit val xfcd       : XFormsContainingDocument = inScopeContainingDocument
    implicit val propertySet: PropertySet              = CoreCrossPlatformSupport.properties

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

    // Set `data-safe-override` as we know we are not losing data upon navigation. This happens:
    // - with changing mode (`tryChangeMode()`)
    // - when navigating away using the `send` action
    if (params.replace == ReplaceType.All)
      setvalue(persistenceInstance.rootElement / "data-safe-override", "true")

    sendThrowOnError(
      s"fr-send-submission",
      params.toPropertyValues
    )
  }

  private def tryChangeModeImpl(
    replace            : ReplaceType,
    pathQuery          : PathWithParams, // may only include rendered format params
    sourceModeType     : ModeType,
    formTargetOpt      : Option[String] = None,
    showProgress       : Boolean        = true,
    responseIsResource : Boolean        = false
  )(implicit
    formRunnerParams   : FormRunnerParams
  ): Option[XFormsSubmitDoneEvent] = {

    val currentDataFormatVersion = getOrGuessFormDataFormatVersion(frc.metadataInstance.map(_.rootElement))

    val (path, maybeRenderFormatParams) = pathQuery

    val queryParams =
      buildPublicStateParamsFromCurrent :::
      buildUserAndStandardParamsForModeChangeFromCurrent(currentDataFormatVersion) :::
      maybeRenderFormatParams

    trySendImpl(
      SendActionParams(
        uri                 = PathUtils.recombineQuery(path, queryParams),
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
    tryChangeMode(Map(Some("mode") -> "view"))

  def tryNavigateToEdit(params: ActionParams): ActionResult =
    tryChangeMode(Map(Some("mode") -> "edit"))

  def tryChangeMode(params: ActionParams): ActionResult =
    ActionResult.trySync {
      implicit val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _, _) = FormRunnerParams()

      val modePublicNameString = requiredParamByNameUseAvt(params, "change-mode", "mode")

      // For now, we don't exclude particular modes, but note:
      // - `new` -> `view` -> `new` should make sense
      // - `tiff` is excluded as it is a secondary mode
      // - `pdf` is explicitly excluded by symmetry with `tiff`

      if (! isSupportedDetailMode(modePublicNameString, excludeSecondaryModes = true) || modePublicNameString == FormRunnerDetailMode.Pdf.publicName)
        throw new IllegalArgumentException(s"Unsupported detail mode navigation for moe: `$modePublicNameString`")

      tryChangeModeImpl(ReplaceType.All, s"/fr/$app/$form/$modePublicNameString/$document" -> Nil, formRunnerParams.modeType(frc.customModes))
    }

  def tryOpenRenderedFormat(params: ActionParams): ActionResult =
    ActionResult.trySync {
      implicit val frParams: FormRunnerParams = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val renderedFormat =
        paramByNameUseAvt(params, "format")
          .flatMap(trimAllToOpt)
          .flatMap(RenderedFormat.withNameOption)
          .getOrElse(RenderedFormat.Pdf)

      // Q: Noticing we use `EscapeURI`, while `buildContentDispositionHeader` uses `URLEncoder.encode`. Any good
      // reason for this?
      val filename =
        EscapeURI.escape(FormRunnerActionsSupport.filenameForRenderedFormat(renderedFormat), "-_.~").toString

      val pathQuery =
        buildRenderedFormatPathWithParams(
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

      tryChangeModeImpl(
        replace            = ReplaceType.All,
        pathQuery          = pathQuery,
        sourceModeType     = frParams.modeType(frc.customModes),
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

  private val ParamsToExcludeUponModeChange: Set[String] =
    StateParamNames                   +
    DataMigrationBehaviorName         +
    DataFormatVersionName             +
    InternalStateParam

  private def buildPublicStateParamsFromCurrent(implicit frParams : FormRunnerParams): List[(String, String)] =
    buildPublicStateParams(
      lang        = frc.currentLang,
      embeddable  = inScopeContainingDocument.isEmbeddedFromUrlParam,
      formVersion = frParams.formVersion,
    )

  // Propagating these parameters is essential when switching modes and navigating between Form Runner pages, as they
  // are part of the state the user expects to be kept.
  //
  // We didn't use to propagate `fr-language`, as the current language is kept in the session. But this caused an issue,
  // see https://github.com/orbeon/orbeon-forms/issues/2110. So now we keep it when switching mode only.
  def buildPublicStateParams(
    lang       : String,
    embeddable : Boolean,
    formVersion: Int
  ): List[(String, String)] =
    List(
      frc.LanguageParam    -> lang,
      EmbeddableParam      -> embeddable.toString,
      frc.FormVersionParam -> formVersion.toString
    )

  private def buildUserAndStandardParamsForModeChangeFromCurrent(dataFormatVersion: DataFormatVersion): List[(String, String)] =
    buildUserAndStandardParamsForModeChange(
      userParams          = inScopeContainingDocument.getRequestParameters,
      dataFormatVersion   = dataFormatVersion,
      privateModeMetadata = privateModeMetadataFromCurrent
    )

  def privateModeMetadataFromCurrent =
    PrivateModeMetadata(
      authorizedOperations = Operations.parseFromString(FormRunner.authorizedOperationsString),
      workflowStage        = FormRunner.documentWorkflowStage,
      created              = FormRunner.documentCreatedDateAsInstant,
      lastModified         = FormRunner.documentModifiedDateAsInstant,
      eTag                 = FormRunner.documentEtag,
      dataStatus           = if (FormRunner.isFormDataSaved) DataStatus.Clean else DataStatus.Dirty,
      renderedFormats      = renderedFormatsMap
    )

  def filterParamsToExcludeUponModeChange[T](p: Iterable[(String, T)]): Iterable[(String, T)] =
    p filterNot { case (name, _) => ParamsToExcludeUponModeChange(name) }

  def buildUserAndStandardParamsForModeChange(
    userParams           : Iterable[(String, List[String])],
    dataFormatVersion    : DataFormatVersion,
    privateModeMetadata  : PrivateModeMetadata,
  ): List[(String, String)] = {

    val standardParams =
      buildStandardParamsForModeChange(
        dataFormatVersion   = dataFormatVersion,
        privateModeMetadata = privateModeMetadata,
      )

    val filteredUserParams =
      for {
        (name, values) <- filterParamsToExcludeUponModeChange(userParams)
        value          <- values
      } yield
        name -> value

    standardParams ::: filteredUserParams.toList
  }

  // https://github.com/orbeon/orbeon-forms/issues/2999
  // https://github.com/orbeon/orbeon-forms/issues/5437
  // https://github.com/orbeon/orbeon-forms/issues/7157
  def buildStandardParamsForModeChange(
    dataFormatVersion    : DataFormatVersion,
    privateModeMetadata  : PrivateModeMetadata,
  ): List[(String, String)] =
    (DataMigrationBehaviorName -> DataMigrationBehavior.Disabled.entryName)                               ::
    (DataFormatVersionName     -> dataFormatVersion.entryName)                                            ::
    (InternalStateParam        -> FormRunnerExternalMode.encryptPrivateModeMetadata(privateModeMetadata)) ::
    Nil

  private def buildRenderedFormatPathWithParams(
    params         : ActionParams,
    renderedFormat : RenderedFormat,
    fullFilename   : Option[String],
    currentFormLang: String
  )(implicit
    frParams       : FormRunnerParams
  ): PathWithParams = {

    val FormRunnerParams(app, form, _, Some(document), _, _) = frParams

    val urlParams =
      fullFilename.toList.map("fr-rendered-filename" ->) :::
        createPdfOrTiffParams(FormRunnerActionsCommon.findFrFormAttachmentsRootElem, params, currentFormLang)

    def pathPrefix(usePagePath: Boolean) =
      s"/fr/${if (usePagePath) "" else "service/"}$app/$form"

    renderedFormat match {
      case RenderedFormat.Pdf | RenderedFormat.Tiff =>
        s"${pathPrefix(usePagePath = fullFilename.isDefined)}/${renderedFormat.entryName}/$document" -> urlParams
      case RenderedFormat.ExcelWithNamedRanges | RenderedFormat.XmlFormStructureAndData =>
          s"${pathPrefix(usePagePath = false)}/export/$document?export-format=${renderedFormat.entryName}" -> urlParams
    }
  }

  private def renderedFormatsMap: Map[String, URI] =
    FormRunnerActionsCommon
      .findUrlsInstanceRootElem
      .toList
      .child(*)
      .collect { case elem if elem.stringValue.nonAllBlank => elem.localname -> URI.create(elem.stringValue)}
      .toMap

  // Create if needed and return the element key name
  def tryCreateRenderedFormatIfNeeded(
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

          val pathQuery =
            buildRenderedFormatPathWithParams(
              params          = params,
              renderedFormat  = renderedFormat,
              fullFilename    = None,
              currentFormLang = currentFormLang
            )

          tryChangeModeImpl(ReplaceType.Instance, pathQuery, sourceModeType = frParams.modeType(frc.customModes)).get

          locally {

            val responseInstance = topLevelInstance(FormModel, "fr-send-submission-response").get

            val node =
              getOrCreateRenderedFormatPathElemOpt(
                urlsInstanceRootElem = FormRunnerActionsCommon.findUrlsInstanceRootElem.get,
                format               = renderedFormat,
                pdfTemplateOpt       = pdfTemplateOpt,
                defaultLang          = currentFormLang,
                create               = true
              ).get

            responseInstance.rootElement.stringValue.trimAllToOpt foreach { pathToTmpFile =>
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
