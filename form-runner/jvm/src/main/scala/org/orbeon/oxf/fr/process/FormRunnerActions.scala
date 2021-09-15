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

import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext.EmbeddableParam
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.datamigration.MigrationSupport
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.fr.process.SimpleProcess._
import org.orbeon.oxf.http.{Headers, HttpMethod}
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.{ContentTypes, PathUtils, XPath}
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.actions.XXFormsUpdateValidityAction
import org.orbeon.xforms.analysis.model.ValidationLevel._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.xforms.RelevanceHandling
import org.orbeon.saxon.functions.EscapeURI
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xbl.{ErrorSummary, Wizard}

import scala.language.postfixOps
import scala.util.{Failure, Try}
import scala.collection.compat._

trait FormRunnerActions {

  self =>

  import FormRunnerRenderedFormat._

  def runningProcessId: Option[String]

  def AllowedFormRunnerActions = Map[String, Action](
    "pending-uploads"        -> tryPendingUploads,
    "validate"               -> tryValidate,
    "save"                   -> trySaveAttachmentsAndData,
    "relinquish-lease"       -> tryRelinquishLease,
    "success-message"        -> trySuccessMessage,
    "error-message"          -> tryErrorMessage,
    "confirm"                -> tryConfirm,
    "email"                  -> trySendEmail,
    "send"                   -> trySend,
    "navigate"               -> tryNavigate,
    "review"                 -> tryNavigateToReview,
    "edit"                   -> tryNavigateToEdit,
    "open-rendered-format"   -> tryOpenRenderedFormat,
    "visit-all"              -> tryShowRelevantErrors,
    "show-relevant-errors"   -> tryShowRelevantErrors,
    "unvisit-all"            -> tryUnvisitAll,
    "expand-all"             -> tryExpandAllSections,
    "expand-invalid"         -> tryExpandInvalidSections,
    "collapse-all"           -> tryCollapseSections,
    "result-dialog"          -> tryShowResultDialog,
    "captcha"                -> tryCaptcha,
    "set-data-status"        -> trySetDataStatus,
    "set-workflow-stage"     -> trySetWorkflowStage,
    "wizard-update-validity" -> tryUpdateCurrentWizardPageValidity,
    "wizard-prev"            -> tryWizardPrev,
    "wizard-next"            -> tryWizardNext,
    "new-to-edit"            -> tryNewToEdit
  )

  // Check whether there are pending uploads
  def tryPendingUploads(params: ActionParams): Try[Any] =
    Try {
      if (inScopeContainingDocument.countPendingUploads > 0)
        throw new OXFException("Pending uploads")
    }

  // Validate form data and fail if invalid
  def tryValidate(params: ActionParams): Try[Any] =
    Try {

      ensureDataCalculationsAreUpToDate()

      val level     = paramByNameOrDefault(params, "level")   map LevelByName getOrElse ErrorLevel
      val controlId = paramByName(params, "control") getOrElse Names.ViewComponent

      // In case of explicit validation mode
      if (formRunnerProperty("oxf.fr.detail.validation-mode")(FormRunnerParams()) contains "explicit") {
        inScopeContainingDocument.synchronizeAndRefresh()
        XFormsAPI.resolveAs[XFormsControl](controlId) foreach { control =>
          XXFormsUpdateValidityAction.updateValidity(control, recurse = true)
        }
      }

      inScopeContainingDocument.synchronizeAndRefresh()

      if (countValidationsByLevel(level) > 0)
        throw new OXFException(s"Data has failed validations for level ${level.entryName}")
    }

  def tryUpdateCurrentWizardPageValidity(params: ActionParams): Try[Any] =
    Try {
      dispatch(name = "fr-update-validity", targetId = Names.ViewComponent)
    }

  def tryWizardPrev(params: ActionParams): Try[Any] = {
    Try {
      // Still run `fr-prev` even if not allowed, as `fr-prev` does perform actions even if not moving to the previous page
      val isPrevAllowed = Wizard.isPrevAllowed
      dispatch(name = "fr-prev", targetId = Names.ViewComponent)
      if (! isPrevAllowed)
        throw new UnsupportedOperationException()
    }
  }

  def tryWizardNext(params: ActionParams): Try[Any] = {
    Try {
      // Still run `fr-next` even if not allowed, as `fr-next` does perform actions even if not moving to the next page
      val isNextAllowed = Wizard.isNextAllowed
      dispatch(name = "fr-next", targetId = Names.ViewComponent)
      if (! isNextAllowed)
        throw new UnsupportedOperationException()
    }
  }

  // It makes sense to update all calculations as needed before saving data
  // https://github.com/orbeon/orbeon-forms/issues/3591
  private def ensureDataCalculationsAreUpToDate(): Unit =
    formInstance.model.doRecalculateRevalidate()

  def trySaveAttachmentsAndData(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, formVersion, Some(document), _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val isDraft       = booleanParamByName(params, "draft", default = false)
      val pruneMetadata = booleanParamByName(params, "prune-metadata", default = false)
      val queryXVT      = paramByName(params, "query")
      val querySuffix   = queryXVT map evaluateValueTemplate map ('&' +) getOrElse ""

      // Notify that the data is about to be saved
      dispatch(name = "fr-data-save-prepare", targetId = FormModel)

      val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form)

      def maybeMigrateData(originalData: DocumentInfo): DocumentInfo = {

        val providerDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(app, form)

        val databaseData =
          MigrationSupport.migrateDataWithFormMetadataMigrations(
            appForm             = AppForm(app, form),
            data                = originalData,
            metadataRootElemOpt = metadataInstance.map(_.rootElement),
            srcVersion          = DataFormatVersion.Edge,
            dstVersion          = providerDataFormatVersion,
            pruneMetadata       = pruneMetadata
          ) getOrElse {
            // Make a copy as we only want to set the `fr:data-format-version` attribute on the migrated data
            val originalDataClone = new DocumentWrapper(dom.Document(), null, XPath.GlobalConfiguration)
            insert(
              into                              = originalDataClone,
              origin                            = originalData child *,
              removeInstanceDataFromClonedNodes = false // https://github.com/orbeon/orbeon-forms/issues/4911
            )
            originalDataClone
          }

        // Add `data-format-version` attribute on the root element
        insert(
          into       = databaseData / *,
          origin     = NodeInfoFactory.attributeInfo(XMLNames.FRDataFormatVersionQName, providerDataFormatVersion.entryName),
          doDispatch = false
        )

        databaseData
      }

      // Save
      val (beforeURLs, afterURLs, _) = putWithAttachments(
        liveData          = formInstance.root,
        migrate           = Some(maybeMigrateData),
        toBaseURI         = "", // local save
        fromBasePaths     = List(createFormDataBasePath(app, form, ! isDraft, document) -> 1), // data is never versioned so `1`
        toBasePath        = createFormDataBasePath(app, form,   isDraft, document),
        filename          = "data.xml",
        commonQueryString = s"valid=$dataValid&$DataFormatVersionName=${databaseDataFormatVersion.entryName}" + querySuffix,
        forceAttachments  = false,
        formVersion       = Some(formVersion.toString),
        workflowStage     = FormRunner.documentWorkflowStage
      )

      // Manual dependency HACK: RR fr-persistence-model before updating the status because we do a setvalue just
      // before calling the submission
      recalculate(PersistenceModel)
      refresh    (PersistenceModel)

      (beforeURLs, afterURLs, isDraft)
    } map {
      case result @ (_, _, isDraft) =>
        // Mark data clean
        trySetDataStatus(Map(Some("status") -> "safe", Some("draft") -> isDraft.toString))
        result
    } map {
      case (beforeURLs, afterURLs, _) =>
        // Notify that the data is saved (2014-07-07: used by FB only)
        dispatch(name = "fr-data-save-done", targetId = FormModel, properties = Map(
          "before-urls" -> Some(beforeURLs),
          "after-urls"  -> Some(afterURLs)
        ))
    } onFailure {
      case _ =>
        dispatch(name = "fr-data-save-error", targetId = FormModel)
    }

  def tryRelinquishLease(params: ActionParams): Try[Any] = Try {
    val leaseState = persistenceInstance.rootElement / "lease-state"
    if (leaseState.stringValue == "current-user") {
      send("fr-relinquish-lease-submission", Map.empty)(_ => ())
      setvalue(leaseState, "relinquished")
    }
  }

  def tryNewToEdit(params: ActionParams): Try[Any] = Try {

    val modeElement = parametersInstance.get.rootElement / "mode"
    val isNew       = modeElement.stringValue == "new"

    if (isNew && canUpdate) {
      setvalue(modeElement, "edit")
      // Manual dependency HACK: RR fr-form-model as we have changed mode
      recalculate(FormModel)
    }
  }

  def trySetDataStatus(params: ActionParams): Try[Any] = Try {

    val isSafe  = paramByName(params, "status") map (_ == "safe") getOrElse true
    val isDraft = booleanParamByName(params, "draft", default = false)

    val saveStatus     = if (isDraft) Seq.empty else persistenceInstance.rootElement / "data-status"
    val autoSaveStatus = persistenceInstance.rootElement / "autosave" / "status"

    (saveStatus ++ autoSaveStatus) foreach
      (setvalue(_, if (isSafe) DataStatus.Clean.entryName else DataStatus.Dirty.entryName))
  }

  def trySetWorkflowStage(params: ActionParams): Try[Any] = Try {
    val name = paramByNameOrDefault(params, "name").map(evaluateValueTemplate)
    FormRunner.documentWorkflowStage = name
    // Manual dependency HACK: RR fr-form-model, as it might use the stage that we just set
    recalculate(FormModel)
  }

  private def messageFromResourceOrParam(params: ActionParams) = {
    def fromResource = paramByNameOrDefault(params, "resource") map (k => currentFRResources / "detail" / "messages" / k stringValue)
    def fromParam    = paramByName(params, "message")

    fromResource orElse fromParam
  }

  def trySuccessMessage(params: ActionParams): Try[Any] =
    Try(FormRunner.successMessage(messageFromResourceOrParam(params).map(evaluateValueTemplate).get))

  def tryErrorMessage(params: ActionParams): Try[Any] = {
    val message    = messageFromResourceOrParam(params).map(evaluateValueTemplate).get
    val appearance = paramByName(params, "appearance").map(MessageAppearance.withName).getOrElse(MessageAppearance.Dialog)
    Try(FormRunner.errorMessage(message, appearance))
  }

  def tryConfirm(params: ActionParams): Try[Any] =
    Try {
      def defaultMessage = currentFRResources / "detail" / "messages" / "confirmation-dialog-message" stringValue
      def message        = messageFromResourceOrParam(params).map(evaluateValueTemplate) getOrElse defaultMessage

      show("fr-confirmation-dialog", Map("message" -> Some(message)))
    }

  def tryShowResultDialog(params: ActionParams): Try[Any] =
    Try {
      show("fr-submission-result-dialog", Map(
        "fr-content" -> Some(topLevelInstance(FormModel, "fr-create-update-submission-response").get.rootElement)
      ))
    }

  def tryCaptcha(params: ActionParams): Try[Any] =
    Try {
      if (showCaptcha) {
        dispatch(name = "fr-verify", targetId = "fr-captcha")
        dispatch(name = "fr-verify", targetId = "fr-view-component")
      }
    }

  def trySendEmail(params: ActionParams): Try[Any] =
    Try {
      implicit val formRunnerParams @ FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()

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
          format    <- RenderedFormat.values.to(List)
          (path, _) <- pdfOrTiffPathOpt(
              urlsInstanceRootElem = findUrlsInstanceRootElem.get,
              format               = format,
              pdfTemplateOpt       = findPdfTemplate(findFrFormAttachmentsRootElem, params, Some(currentFormLang)),
              defaultLang          = currentFormLang
            )
        } yield
          format.entryName -> path

      recombineQuery(
        s"/fr/service/$app/$form/email/$document",
        pdfTiffParams ::: createPdfOrTiffParams(findFrFormAttachmentsRootElem, params, currentFormLang)
      )
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_NONE)

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

      // The token can be `xml`, `metadata`, `pdf`, `tiff`, `pdf-url`, `tiff-url`
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

      // Create PDF and/or TIFF if needed
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

  private val StateParams = List[(String, (() => String, String => Boolean))](
    LanguageParam    -> (() => currentLang,                             _ => false  ),
    EmbeddableParam  -> (() => isEmbeddable.toString,                   _ == "true"),
    FormVersionParam -> (() => FormRunnerParams().formVersion.toString, _ => false  )
  )

  private val StateParamNames               = StateParams map (_._1) toSet
  private val ParamsToExcludeUponModeChange = StateParamNames + DataFormatVersionName

  // Automatically prepend `fr-language`, `orbeon-embeddable` and `form-version` based on their current value unless
  // they are already specified in the given path.
  //
  // Propagating these parameters is essential when switching modes and navigating between Form Runner pages, as they
  // are part of the state the user expects to be kept.
  //
  // We didn't use to propagate `fr-language`, as the current language is kept in the session. But this caused an issue,
  // see https://github.com/orbeon/orbeon-forms/issues/2110. So now we keep it when switching mode only.
  private def prependCommonFormRunnerParameters(pathQueryOrUrl: String, forNavigate: Boolean) =
    if (! PathUtils.urlHasProtocol(pathQueryOrUrl)) { // heuristic, which might not always be a right guess?

      val (path, params) = splitQueryDecodeParams(pathQueryOrUrl)

      val newParams =
        for {
          (name, (valueFromCurrent, keepValue)) <- StateParams
          valueFromPath                         = params collectFirst { case (`name`, v) => v }
          effectiveValue                        = valueFromPath getOrElse valueFromCurrent.apply
          if ! forNavigate || forNavigate && (valueFromPath.isDefined || keepValue(effectiveValue)) // keep parameter if explicit!
        } yield
          name -> effectiveValue

      recombineQuery(path, newParams ::: (params filterNot (p => StateParamNames(p._1))))
    } else
      pathQueryOrUrl

  private def prependUserParamsForModeChange(pathQuery: String) = {

    val (path, params) = splitQueryDecodeParams(pathQuery)

    val newParams =
      for {
        (name, values) <- inScopeContainingDocument.getRequestParameters.to(List)
        if ! ParamsToExcludeUponModeChange(name)
        value          <- values
      } yield
        name -> value

    recombineQuery(path, newParams ::: params)
  }

  private def tryNavigateTo(location: String, target: Option[String]): Try[Any] =
    Try(load(prependCommonFormRunnerParameters(location,  forNavigate = true), target, progress = false))

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

  // Navigate to a URL specified in parameters or indirectly in properties
  // If no URL is specified, the action fails
  def tryNavigate(params: ActionParams): Try[Any] =
    Try {
      implicit val formRunnerParams = FormRunnerParams()

      // Heuristic: If the parameter is anonymous, we take it as a URL or path if it looks like one. Otherwise, we
      // consider it is a property. We could also look at whether the value looks like a property. It's better to
      // be explicit and use `uri =` or `property =`.
      def isAbsolute(s: String) = s.startsWith("/") || PathUtils.urlHasProtocol(s)

      def fromParams = params.get(Some("uri")) orElse (params.get(None) filter isAbsolute)

      def fromProperties = {
        val propertyName =  params.get(Some("property")) orElse (params.get(None) filterNot isAbsolute) getOrElse "oxf.fr.detail.close.uri"
        formRunnerProperty(propertyName)
      }

      val location  = fromParams orElse fromProperties map evaluateValueTemplate flatMap trimAllToOpt get
      val targetOpt = params.get(Some("target")) flatMap trimAllToOpt

      (location, targetOpt)

    } flatMap
      (tryNavigateTo _).tupled

  def tryNavigateToReview(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()
      s"/fr/$app/$form/view/$document"
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL)

  def tryNavigateToEdit(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()
      s"/fr/$app/$form/edit/$document"
    } flatMap
      tryChangeMode(XFORMS_SUBMIT_REPLACE_ALL)

  def tryOpenRenderedFormat(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val renderedFormatString = (
        paramByName(params, "format")
        flatMap   trimAllToOpt
        flatMap   RenderedFormat.withNameOption
        getOrElse RenderedFormat.Pdf
      ).entryName

      // TODO: Use namespaces from appropriate scope.
      val fullFilename = {
        val filenameProperty            = s"oxf.fr.detail.$renderedFormatString.filename"
        val filenamePropertyValue       = formRunnerProperty(filenameProperty)(FormRunnerParams()).flatMap(trimAllToOpt)
        val filenameFromProperty        = filenamePropertyValue.map(evaluateString(_, xpathContext)).flatMap(trimAllToOpt)
        val escapedFilenameFromProperty = filenameFromProperty.map(EscapeURI.escape(_, "-_.~").toString)
        val filename                    = escapedFilenameFromProperty.getOrElse(currentXFormsDocumentId)
        s"$filename.$renderedFormatString"
      }

      val currentFormLang = FormRunner.currentLang

      recombineQuery(
        s"/fr/$app/$form/$renderedFormatString/$document",
        ("fr-rendered-filename" -> fullFilename) :: createPdfOrTiffParams(findFrFormAttachmentsRootElem, params, currentFormLang)
      )
    } flatMap
      tryChangeMode(
        replace            = XFORMS_SUBMIT_REPLACE_ALL,
        showProgress       = false,
        formTargetOpt      = Some("_blank"),
        responseIsResource = true
      )

  // Visit/unvisit controls
  def tryShowRelevantErrors(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-show-relevant-errors", targetId = ErrorSummaryModel))
  def tryUnvisitAll(params: ActionParams)        : Try[Any] = Try(dispatch(name = "fr-unvisit-all",          targetId = ErrorSummaryModel))

  // Collapse/expand sections
  def tryCollapseSections(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-collapse-all",         targetId = SectionsModel))
  def tryExpandAllSections(params: ActionParams) : Try[Any] = Try(dispatch(name = "fr-expand-all",           targetId = SectionsModel))

  def tryExpandInvalidSections(params: ActionParams)  : Try[Any] =
    Try {
      ErrorSummary.sectionsWithVisibleErrors.foreach { sectionName =>
        val sectionId = FormRunner.sectionId(sectionName)
        dispatch(name = "fr-expand", targetId = sectionId)
      }
    }

  def findUrlsInstanceRootElem: Option[NodeInfo] =
    urlsInstance map (_.rootElement)

  def findFrFormAttachmentsRootElem: Option[NodeInfo] =
    formAttachmentsInstance map (_.rootElement)

  // Create if needed and return the element key name
  private def tryCreatePdfOrTiffIfNeeded(params: ActionParams, format: RenderedFormat): Try[String] =
    Try {

      val currentFormLang = FormRunner.currentLang
      val pdfTemplateOpt  = findPdfTemplate(findFrFormAttachmentsRootElem, params, Some(currentFormLang))

      pdfOrTiffPathOpt(
        urlsInstanceRootElem = findUrlsInstanceRootElem.get,
        format               = format,
        pdfTemplateOpt       = pdfTemplateOpt,
        defaultLang          = currentFormLang
      ) match {
        case Some((_, key)) => key
        case None =>

          implicit val frParams @ FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()

          val path =
            recombineQuery(
              s"/fr/service/$app/$form/${format.entryName}/$document",
              createPdfOrTiffParams(findFrFormAttachmentsRootElem, params, currentFormLang)
            )

          def processSuccessResponse() = {

            val response = topLevelInstance(FormModel, "fr-send-submission-response").get

            val node =
              getOrCreatePdfTiffPathElemOpt(
                urlsInstanceRootElem = findUrlsInstanceRootElem.get,
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
