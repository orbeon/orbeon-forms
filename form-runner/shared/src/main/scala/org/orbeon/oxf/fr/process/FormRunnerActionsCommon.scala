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
import org.orbeon.oxf.externalcontext.ExternalContext.EmbeddableParam
import org.orbeon.oxf.fr.FormRunnerCommon._
import org.orbeon.oxf.fr.FormRunnerPersistence._
import org.orbeon.oxf.fr.{AppForm, DataStatus, FormRunnerBaseOps, FormRunnerParams, FormRunnerPersistence, GridDataMigration, Names}
import org.orbeon.oxf.fr.Names._
import org.orbeon.oxf.fr.process.ProcessInterpreter._
import org.orbeon.oxf.util.PathUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.TryUtils._
import org.orbeon.oxf.util.PathUtils
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.action.actions.XXFormsUpdateValidityAction
import org.orbeon.xforms.analysis.model.ValidationLevel._
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.xbl.{ErrorSummary, Wizard}
import org.orbeon.oxf.util.StaticXPath.DocumentNodeInfoType
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.language.postfixOps
import scala.util.Try
import scala.collection.compat._


trait FormRunnerActionsCommon {

  self =>

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
  protected def ensureDataCalculationsAreUpToDate(): Unit =
    frc.formInstance.model.doRecalculateRevalidate()

  def trySaveAttachmentsAndData(params: ActionParams): Try[Any] =
    Try {
      val FormRunnerParams(app, form, formVersion, Some(document), _, _) = FormRunnerParams()

      ensureDataCalculationsAreUpToDate()

      val appForm       = AppForm(app, form)
      val isDraft       = booleanParamByName(params, "draft", default = false)
      val pruneMetadata = booleanParamByName(params, "prune-metadata", default = false)
      val queryXVT      = paramByName(params, "query")
      val querySuffix   = queryXVT map spc.evaluateValueTemplate map ('&' +) getOrElse ""

      // Notify that the data is about to be saved
      dispatch(name = "fr-data-save-prepare", targetId = FormModel)

      val databaseDataFormatVersion = FormRunnerPersistence.providerDataFormatVersionOrThrow(appForm)

      def maybeMigrateData(originalData: DocumentNodeInfoType): DocumentNodeInfoType =
        GridDataMigration.dataMaybeMigratedFromEdge(
          app                     = app,
          form                    = form,
          data                    = originalData,
          metadataOpt             = frc.metadataInstance.map(_.root),
          dataFormatVersionString = databaseDataFormatVersion.entryName,
          pruneMetadata           = pruneMetadata
        )

      // Save
      val (beforeURLs, afterURLs, _) = frc.putWithAttachments(
        liveData          = frc.formInstance.root,
        migrate           = Some(maybeMigrateData),
        toBaseURI         = "", // local save
        fromBasePaths     = List(frc.createFormDataBasePath(app, form, ! isDraft, document) -> formVersion),
        toBasePath        = frc.createFormDataBasePath(app, form,   isDraft, document),
        filename          = "data.xml",
        commonQueryString = s"valid=${frc.dataValid}&$DataFormatVersionName=${databaseDataFormatVersion.entryName}" + querySuffix,
        forceAttachments  = false,
        formVersion       = Some(formVersion.toString),
        workflowStage     = frc.documentWorkflowStage
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
    val leaseState = frc.persistenceInstance.rootElement / "lease-state"
    if (leaseState.stringValue == "current-user") {
      send("fr-relinquish-lease-submission", Map.empty)(_ => ())
      setvalue(leaseState, "relinquished")
    }
  }

  def tryNewToEdit(params: ActionParams): Try[Any] = Try {

    val modeElement = frc.parametersInstance.get.rootElement / "mode"
    val isNew       = modeElement.stringValue == "new"

    if (isNew && frc.canUpdate) {
      setvalue(modeElement, "edit")
      // Manual dependency HACK: RR fr-form-model as we have changed mode
      recalculate(FormModel)
    }
  }

  def trySetDataStatus(params: ActionParams): Try[Any] = Try {

    val isSafe  = paramByName(params, "status") map (_ == "safe") getOrElse true
    val isDraft = booleanParamByName(params, "draft", default = false)

    val saveStatus     = if (isDraft) Seq.empty else frc.persistenceInstance.rootElement / "data-status"
    val autoSaveStatus = frc.persistenceInstance.rootElement / "autosave" / "status"

    (saveStatus ++ autoSaveStatus) foreach
      (setvalue(_, if (isSafe) DataStatus.Clean.entryName else DataStatus.Dirty.entryName))
  }

  def trySetWorkflowStage(params: ActionParams): Try[Any] = Try {
    val name = paramByNameOrDefault(params, "name").map(spc.evaluateValueTemplate)
    frc.documentWorkflowStage = name
    // Manual dependency HACK: RR fr-form-model, as it might use the stage that we just set
    recalculate(FormModel)
  }

  private def messageFromResourceOrParam(params: ActionParams): Option[String] = {
    def fromResource = paramByNameOrDefault(params, "resource") map (k => frc.currentFRResources / "detail" / "messages" / k stringValue)
    def fromParam    = paramByName(params, "message")

    fromResource orElse fromParam
  }

  def trySuccessMessage(params: ActionParams): Try[Any] =
    Try(frc.successMessage(messageFromResourceOrParam(params).map(spc.evaluateValueTemplate).get))

  def tryErrorMessage(params: ActionParams): Try[Any] = {
    val message    = messageFromResourceOrParam(params).map(spc.evaluateValueTemplate).get
    val appearance = paramByName(params, "appearance").map(FormRunnerBaseOps.MessageAppearance.withName).getOrElse(FormRunnerBaseOps.MessageAppearance.Dialog)
    Try(frc.errorMessage(message, appearance))
  }

  def tryConfirm(params: ActionParams): Try[Any] =
    Try {
      def defaultMessage = frc.currentFRResources / "detail" / "messages" / "confirmation-dialog-message" stringValue
      def message        = messageFromResourceOrParam(params).map(spc.evaluateValueTemplate) getOrElse defaultMessage

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
      if (frc.showCaptcha) {
        dispatch(name = "fr-verify", targetId = "fr-captcha")
        dispatch(name = "fr-verify", targetId = "fr-view-component")
      }
    }

  private val StateParams = List[(String, (() => String, String => Boolean))](
    frc.LanguageParam    -> (() => frc.currentLang,                         _ => false ),
    EmbeddableParam      -> (() => frc.isEmbeddable.toString,               _ == "true"),
    frc.FormVersionParam -> (() => FormRunnerParams().formVersion.toString, _ => false )
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

  private def tryNavigateTo(location: String, target: Option[String]): Try[Any] =
    Try(load(prependCommonFormRunnerParameters(location, forNavigate = true), target, progress = false))

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
        frc.formRunnerProperty(propertyName)
      }

      val location  = fromParams orElse fromProperties map spc.evaluateValueTemplate flatMap trimAllToOpt get
      val targetOpt = params.get(Some("target")) flatMap trimAllToOpt

      (location, targetOpt)

    } flatMap
      (tryNavigateTo _).tupled

  // Visit/unvisit controls
  def tryShowRelevantErrors(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-show-relevant-errors", targetId = ErrorSummaryModel))
  def tryUnvisitAll(params: ActionParams)        : Try[Any] = Try(dispatch(name = "fr-unvisit-all",          targetId = ErrorSummaryModel))

  // Collapse/expand sections
  def tryCollapseSections(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-collapse-all",         targetId = SectionsModel))
  def tryExpandAllSections(params: ActionParams) : Try[Any] = Try(dispatch(name = "fr-expand-all",           targetId = SectionsModel))

  def tryExpandInvalidSections(params: ActionParams)  : Try[Any] =
    Try {
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
}