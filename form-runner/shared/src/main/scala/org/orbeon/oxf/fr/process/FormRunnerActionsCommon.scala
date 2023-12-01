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

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.externalcontext.ExternalContext.EmbeddableParam
import org.orbeon.oxf.fr.FormRunner.updateAttachments
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{CoreCrossPlatformSupport, CoreCrossPlatformSupportTrait, MarkupUtils, PathUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.actions.XXFormsUpdateValidityAction
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xbl.{ErrorSummary, Wizard}
import org.orbeon.xforms.analysis.model.ValidationLevel._

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}


trait FormRunnerActionsCommon {

  self: XFormsActions => // for `tryCallback`

  def runningProcessId: Option[String]
  def AllowedFormRunnerActions: Map[String, Action]

  protected val CommonAllowedFormRunnerActions: Map[String, Action] = Map(
    "pending-uploads"        -> tryPendingUploads,
    "validate"               -> tryValidate,
    "save"                   -> trySaveAttachmentsAndData,
    "relinquish-lease"       -> tryRelinquishLease,
    "success-message"        -> trySuccessMessage,
    "error-message"          -> tryErrorMessage,
    "confirm"                -> tryConfirm,
    "navigate"               -> tryNavigate,
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
    "new-to-edit"            -> tryNewToEdit,
    "callback"               -> tryCallback,
  )

  // Check whether there are pending uploads
  def tryPendingUploads(params: ActionParams): ActionResult =
    ActionResult.trySync {
      if (inScopeContainingDocument.countPendingUploads > 0)
        throw new OXFException("Pending uploads")
    }

  // Validate form data and fail if invalid
  def tryValidate(params: ActionParams): ActionResult =
    ActionResult.trySync {

      ensureDataCalculationsAreUpToDate()

      val level     = paramByNameOrDefault(params, "level")   map LevelByName getOrElse ErrorLevel
      val controlId = paramByName(params, "control") getOrElse Names.ViewComponent

      // In case of explicit validation mode
      if (frc.formRunnerProperty("oxf.fr.detail.validation-mode")(FormRunnerParams()) contains "explicit") {
        inScopeContainingDocument.synchronizeAndRefresh()
        XFormsAPI.resolveAs[XFormsControl](controlId) foreach { control =>
          XXFormsUpdateValidityAction.updateValidity(control, recurse = true)
        }
      }

      inScopeContainingDocument.synchronizeAndRefresh()

      if (frc.countValidationsByLevel(level) > 0)
        throw new OXFException(s"Data has failed validations for level ${level.entryName}")
    }

  def tryUpdateCurrentWizardPageValidity(params: ActionParams): ActionResult =
    ActionResult.trySync {
      dispatch(name = "fr-update-validity", targetId = Names.ViewComponent)
    }

  def tryWizardPrev(params: ActionParams): ActionResult =
    ActionResult.trySync {
      // Still run `fr-prev` even if not allowed, as `fr-prev` does perform actions even if not moving to the previous page
      val isPrevAllowed = Wizard.isPrevAllowed
      dispatch(name = "fr-prev", targetId = Names.ViewComponent)
      if (! isPrevAllowed)
        throw new UnsupportedOperationException()
    }

  def tryWizardNext(params: ActionParams): ActionResult =
    ActionResult.trySync {
      // Still run `fr-next` even if not allowed, as `fr-next` does perform actions even if not moving to the next page
      val isNextAllowed = Wizard.isNextAllowed
      dispatch(name = "fr-next", targetId = Names.ViewComponent)
      if (! isNextAllowed)
        throw new UnsupportedOperationException()
    }

  // It makes sense to update all calculations as needed before saving data
  // https://github.com/orbeon/orbeon-forms/issues/3591
  protected def ensureDataCalculationsAreUpToDate(): Unit =
    frc.formInstance.model.doRecalculateRevalidate()

  def trySaveAttachmentsAndData(params: ActionParams): ActionResult =
    ActionResult.tryAsync {

      implicit val externalContext         : ExternalContext               = CoreCrossPlatformSupport.externalContext
      implicit val coreCrossPlatformSupport: CoreCrossPlatformSupportTrait = CoreCrossPlatformSupport
      implicit val xfcd                    : XFormsContainingDocument      = inScopeContainingDocument

      val FormRunnerParams(app, form, formVersion, Some(document), _, _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val appForm       = AppForm(app, form)
      val isDraft       = booleanParamByName(params, "draft", default = false)
      val pruneMetadata = booleanParamByName(params, "prune-metadata", default = false)
      val queryXVT      = paramByName(params, "query")
      val querySuffix   = queryXVT.map(spc.evaluateValueTemplate).map('&' + _).getOrElse("")

      // Notify that the data is about to be saved
      dispatch(name = "fr-data-save-prepare", targetId = FormModel)

      val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm)

      def maybeMigrateData(originalData: DocumentNodeInfoType): DocumentNodeInfoType =
        GridDataMigration.dataMaybeMigratedFromEdge(
          app                        = app,
          form                       = form,
          data                       = originalData,
          metadataOpt                = frc.metadataInstance.map(_.root),
          dstDataFormatVersionString = databaseDataFormatVersion.entryName,
          pruneMetadata              = pruneMetadata
        )

      // Forward the token if present so the persistence proxy can use it
      val tokenParamOpt =
        inScopeContainingDocument.getRequestParameters
          .get(frc.AccessTokenParam)
          .flatMap(_.headOption)
          .map(frc.AccessTokenParam -> _)

      val systemParams =
        PathUtils.encodeSimpleQuery(
          tokenParamOpt.toList                                           :::
          ("valid" -> frc.dataValid.toString)                            ::
          (DataFormatVersionName -> databaseDataFormatVersion.entryName) ::
          Nil
        )

      // Saving is an asynchronous operation
      val future: Future[(List[AttachmentWithEncryptedAtRest], Option[Int])] =
        frc.putWithAttachments(
          liveData          = frc.formInstance.root,
          migrate           = Some(maybeMigrateData),
          toBaseURI         = "", // local save
          fromBasePaths     = List(frc.createFormDataBasePath(app, form, ! isDraft, document) -> formVersion),
          toBasePath        = frc.createFormDataBasePath(app, form, isDraft, document),
          filename          = DataXml,
          commonQueryString = systemParams + querySuffix,
          forceAttachments  = false,
          formVersion       = Some(formVersion.toString),
          workflowStage     = frc.documentWorkflowStage
        )

      // This will be run when the future completes, but in a controlled way
      // Q: Could we make this an `IO`?
      def continuation(value: Try[(List[AttachmentWithEncryptedAtRest], Option[Int])]): Try[Unit] =
        Try {
          value match {
            case Success((attachmentWithEncryptedAtRest, _)) =>

              // Update, in this thread, the attachment paths
              updateAttachments(frc.formInstance.root, attachmentWithEncryptedAtRest)

              // Manual dependency HACK: RR `fr-persistence-model` before updating the status because we do a setvalue just
              // before calling the submission
              recalculate(PersistenceModel)
              refresh    (PersistenceModel)

              // Mark data clean
              trySetDataStatus(Map(Some("status") -> "safe", Some("draft") -> isDraft.toString))

              // Notify that the data is saved (2014-07-07: used by FB only)
              // We pass the URLs to the event so that Form Builder can update `fb-form-instance`
              dispatch(name = "fr-data-save-done", targetId = FormModel, properties = Map(
                "before-urls" -> Some(attachmentWithEncryptedAtRest.map(_.fromPath)),
                "after-urls"  -> Some(attachmentWithEncryptedAtRest.map(_.toPath))
              ))

              Success(())

            case Failure(t) =>

              dispatch(name = "fr-data-save-error", targetId = FormModel)

              Failure(t)
          }
        } .flatten

      (future, continuation _)
    }

  def tryRelinquishLease(params: ActionParams): ActionResult = ActionResult.trySync {
    val leaseState = frc.persistenceInstance.rootElement / "lease-state"
    if (leaseState.stringValue == "current-user") {
      sendThrowOnError("fr-relinquish-lease-submission", Nil)
      setvalue(leaseState, "relinquished")
    }
  }

  def tryNewToEdit(params: ActionParams): ActionResult = ActionResult.trySync {

    val modeElement = frc.parametersInstance.get.rootElement / "mode"
    val isNew       = modeElement.stringValue == "new"

    if (isNew && frc.canUpdate) {
      setvalue(modeElement, "edit")
      // Manual dependency HACK: RR fr-form-model as we have changed mode
      recalculate(FormModel)
    }
  }

  def trySetDataStatus(params: ActionParams): ActionResult = ActionResult.trySync {

    val isSafe  = paramByName(params, "status") map (_ == "safe") getOrElse true
    val isDraft = booleanParamByName(params, "draft", default = false)

    val saveStatus     = if (isDraft) Seq.empty else frc.persistenceInstance.rootElement / "data-status"
    val autoSaveStatus = frc.persistenceInstance.rootElement / "autosave" / "status"

    (saveStatus ++ autoSaveStatus) foreach
      (setvalue(_, if (isSafe) DataStatus.Clean.entryName else DataStatus.Dirty.entryName))
  }

  def trySetWorkflowStage(params: ActionParams): ActionResult = ActionResult.trySync {
    val name = paramByNameOrDefault(params, "name").map(spc.evaluateValueTemplate)
    frc.documentWorkflowStage = name
    // Manual dependency HACK: RR fr-form-model, as it might use the stage that we just set
    recalculate(FormModel)
  }

  private case class TextOrHtml(string: String, isHtml: Boolean) {
    def asHtml: String = if (isHtml) string else MarkupUtils.escapeXmlMinimal(string)
  }

  private def messageFromResourceOrParam(params: ActionParams): Option[TextOrHtml] = {
    def fromResource =
      for {
        messageName <- paramByNameOrDefault(params, "resource")
        messageNode <- (frc.currentFRResources / "detail" / "messages" / messageName).headOption
        isHtml       = FormRunnerActionsCommon.isMessageInHtml(messageNode)
      } yield TextOrHtml(spc.evaluateValueTemplate(messageNode.getStringValue), isHtml)

    def fromParam =
      for {
        message <- paramByName(params, "message")
        isHtml   = booleanParamByName(params, "html", default = false)
      } yield TextOrHtml(spc.evaluateValueTemplate(message), isHtml)

    fromResource orElse fromParam
  }

  def trySuccessMessage(params: ActionParams): ActionResult =
    ActionResult.trySync {
      val htmlMessage = messageFromResourceOrParam(params).get.asHtml
      frc.successMessage(htmlMessage)
    }

  def tryErrorMessage(params: ActionParams): ActionResult =
    ActionResult.trySync {
      val htmlMessage = messageFromResourceOrParam(params).get.asHtml
      val appearance  = paramByName(params, "appearance").map(FormRunnerBaseOps.MessageAppearance.withName).getOrElse(FormRunnerBaseOps.MessageAppearance.Dialog)

      frc.errorMessage(htmlMessage, appearance)
    }

  def tryConfirm(params: ActionParams): ActionResult =
    ActionResult.trySync {
      def defaultMessage =  messageFromResourceOrParam(Map(None -> "confirmation-dialog-message")).get
      def htmlMessage    = (messageFromResourceOrParam(params) getOrElse defaultMessage).asHtml

      show("fr-confirmation-dialog", Map("message" -> Some(htmlMessage), "message-is-html" -> Some("true")))
    }

  def tryShowResultDialog(params: ActionParams): ActionResult =
    ActionResult.trySync {
      show("fr-submission-result-dialog", Map(
        "fr-content" -> Some(topLevelInstance(FormModel, "fr-create-update-submission-response").get.rootElement)
      ))
    }

  def tryCaptcha(params: ActionParams): ActionResult =
    ActionResult.trySync {
      if (frc.showCaptcha) {
        dispatch(name = "fr-verify", targetId = "fr-captcha")
        dispatch(name = "fr-verify", targetId = "fr-view-component")
      }
    }

  private val StateParams = List[(String, (() => String, String => Boolean))](
    frc.LanguageParam    -> (() => frc.currentLang,                                           _ => false ),
    EmbeddableParam      -> (() => inScopeContainingDocument.isEmbeddedFromUrlParam.toString, _ == true.toString),
    frc.FormVersionParam -> (() => FormRunnerParams().formVersion.toString,                   _ => false )
  )

  protected val StateParamNames = StateParams map (_._1) toSet

  // Automatically prepend `fr-language`, `orbeon-embeddable` and `form-version` based on their current value unless
  // they are already specified in the given path.
  //
  // Propagating these parameters is essential when switching modes and navigating between Form Runner pages, as they
  // are part of the state the user expects to be kept.
  //
  // We didn't use to propagate `fr-language`, as the current language is kept in the session. But this caused an issue,
  // see https://github.com/orbeon/orbeon-forms/issues/2110. So now we keep it when switching mode only.
  protected def prependCommonFormRunnerParameters(pathQueryOrUrl: String, forNavigate: Boolean): String =
    if (! PathUtils.urlHasProtocol(pathQueryOrUrl)) { // heuristic, which might not always be a right guess?

      val (path, params) = splitQueryDecodeParams(pathQueryOrUrl)

      val newParams =
        for {
          (name, (valueFromCurrent, keepValue)) <- StateParams
          valueFromPath                         = params collectFirst { case (`name`, v) => v }
          effectiveValue                        = valueFromPath getOrElse valueFromCurrent()
          if ! forNavigate || forNavigate && (valueFromPath.isDefined || keepValue(effectiveValue)) // keep parameter if explicit!
        } yield
          name -> effectiveValue

      recombineQuery(path, newParams ::: (params filterNot (p => StateParamNames(p._1))))
    } else
      pathQueryOrUrl

  // Navigate to a URL specified in parameters or indirectly in properties
  // If no URL is specified, the action fails
  def tryNavigate(params: ActionParams): ActionResult =
    ActionResult.trySync {
      // Heuristic: If the parameter is anonymous, we take it as a URL or path if it looks like one. Otherwise, we
      // consider it is a property. We could also look at whether the value looks like a property. It's better to
      // be explicit and use `uri =` or `property =`.
      def isAbsolute(s: String) = s.startsWith("/") || PathUtils.urlHasProtocol(s)

      def fromParams = params.get(Some("uri")) orElse (params.get(None) filter isAbsolute)

      def fromProperties = {
        val propertyName =  params.get(Some("property")) orElse (params.get(None) filterNot isAbsolute) getOrElse "oxf.fr.detail.close.uri"
        frc.formRunnerProperty(propertyName)(FormRunnerParams())
      }

      val location     = fromParams orElse fromProperties map spc.evaluateValueTemplate flatMap trimAllToOpt get
      val targetOpt    = params.get(Some("target")) flatMap trimAllToOpt
      val showProgress = {
        val paramStringOpt    = paramByName(params, ShowProgressName)
        val paramEvaluatedOpt = paramStringOpt.map(spc.evaluateValueTemplate)
        val paramBooleanOpt   = paramEvaluatedOpt.map(_ == "true")
        paramBooleanOpt.getOrElse(false)
      }

      load(prependCommonFormRunnerParameters(location, forNavigate = true), targetOpt, showProgress = showProgress)
    }

  // Visit/unvisit controls
  def tryShowRelevantErrors(params: ActionParams): ActionResult = ActionResult.trySync(dispatch(name = "fr-show-relevant-errors", targetId = ErrorSummaryModel))
  def tryUnvisitAll(params: ActionParams)        : ActionResult = ActionResult.trySync(dispatch(name = "fr-unvisit-all",          targetId = ErrorSummaryModel))

  // Collapse/expand sections
  def tryCollapseSections(params: ActionParams)  : ActionResult = ActionResult.trySync(dispatch(name = "fr-collapse-all",         targetId = SectionsModel))
  def tryExpandAllSections(params: ActionParams) : ActionResult = ActionResult.trySync(dispatch(name = "fr-expand-all",           targetId = SectionsModel))

  def tryExpandInvalidSections(params: ActionParams): ActionResult =
    ActionResult.trySync {
      ErrorSummary.sectionsWithVisibleErrors.foreach { sectionName =>
        val sectionId = frc.sectionId(sectionName)
        dispatch(name = "fr-expand", targetId = sectionId)
      }
    }
}

object FormRunnerActionsCommon {

  def findUrlsInstanceRootElem: Option[NodeInfo] =
    frc.urlsInstance map (_.rootElement)

  def findFrFormAttachmentsRootElem: Option[NodeInfo] =
    frc.formAttachmentsInstance map (_.rootElement)

  def isMessageInHtml(nodeInfo: NodeInfo): Boolean =
    (nodeInfo /@ "html").headOption.exists(_.stringValue == "true")
}