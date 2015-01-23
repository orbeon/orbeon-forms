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

import org.orbeon.oxf.fr.{DataMigration, FormRunner}
import SimpleProcess._
import FormRunner.{splitQueryDecodeParams ⇒ _, recombineQuery ⇒ _, _}
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels._
import org.orbeon.scaxon.XML._
import scala.util.Try

trait FormRunnerActions {

    def runningProcessId: Option[String]

    def AllowedFormRunnerActions = Map[String, Action](
        "pending-uploads"  → tryPendingUploads,
        "validate"         → tryValidate,
        "save"             → trySaveAttachmentsAndData,
        "success-message"  → trySuccessMessage,
        "error-message"    → tryErrorMessage,
        "confirm"          → tryConfirm,
        "email"            → trySendEmail,
        "send"             → trySend,
        "navigate"         → tryNavigate,
        "review"           → tryNavigateToReview,
        "edit"             → tryNavigateToEdit,
        "open-pdf"         → tryOpenPDF,
        "toggle-noscript"  → tryToggleNoscript,
        "visit-all"        → tryVisitAll,
        "unvisit-all"      → tryUnvisitAll,
        "expand-all"       → tryExpandSections,
        "collapse-all"     → tryCollapseSections,
        "result-dialog"    → tryShowResultDialog,
        "captcha"          → tryCaptcha,
        "wizard-prev"      → tryWizardPrev,
        "wizard-next"      → tryWizardNext,
        "set-data-status"  → trySetDataStatus
    )

    // Check whether there are pending uploads
    def tryPendingUploads(params: ActionParams): Try[Any] =
        Try {
            if (containingDocument.countPendingUploads > 0)
                throw new OXFException("Pending uploads")
        }

    // Validate form data and fail if invalid
    def tryValidate(params: ActionParams): Try[Any] =
        Try {
            val level = paramByNameOrDefault(params, "level") map LevelByName getOrElse ErrorLevel

            if (countValidationsByLevel(level) > 0)
                throw new OXFException(s"Data has failed validations for level ${level.name}")
        }

    def trySaveAttachmentsAndData(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, formVersion, Some(document), _) = FormRunnerParams()

            val isDraft     = booleanParamByName(params, "draft", default = false)
            val queryXVT    = paramByName(params, "query")
            val querySuffix = queryXVT map evaluateValueTemplate map ('&' +) getOrElse ""

            // Notify that the data is about to be saved
            dispatch(name = "fr-data-save-prepare", targetId = FormModel)

            val modeElement = parametersInstance.rootElement \ "mode"
            val isNew       = modeElement.stringValue == "new"

            import DataMigration._

            val dataMaybeMigrated =
                dataMaybeMigratedFrom(formInstance.root, metadataInstance map (_.root)) getOrElse formInstance.root

            // Save
            val (beforeURLs, afterURLs, _) = putWithAttachments(
                data              = dataMaybeMigrated,
                toBaseURI         = "", // local save
                fromBasePath      = createFormDataBasePath(app, form, ! isDraft, document),
                toBasePath        = createFormDataBasePath(app, form,   isDraft, document),
                filename          = "data.xml",
                commonQueryString = s"valid=$dataValid" + querySuffix,
                forceAttachments  = false,
                formVersion       = Some(formVersion)
            )

            // If we were in new mode, now we must be in edit mode
            if (isNew && ! isDraft && supportsUpdate)
                setvalue(modeElement, "edit")

            // Manual dependency HACK: RR fr-persistence-model before updating the status because we do a setvalue just
            // before calling the submission
            recalculate(PersistenceModel)
            revalidate (PersistenceModel)
            refresh    (PersistenceModel)

            if (isNew) {
                // Manual dependency HACK: RR fr-form-model as we have changed mode
                recalculate(FormModel)
                revalidate(FormModel)
            }

            (beforeURLs, afterURLs, isDraft)
        } map {
            case result @ (beforeURLs, afterURLs, isDraft) ⇒
                // Mark data clean
                trySetDataStatus(Map(Some("status") → "safe", Some("draft") → isDraft.toString))
                result
        } map {
            case (beforeURLs, afterURLs, _) ⇒
                // Notify that the data is saved (2014-07-07: used by FB only)
                dispatch(name = "fr-data-save-done", targetId = FormModel, properties = Map(
                    "before-urls" → Some(beforeURLs),
                    "after-urls"  → Some(afterURLs)
                ))
        } onFailure {
            case _ ⇒
                dispatch(name = "fr-data-save-error", targetId = FormModel)
        }

    def trySetDataStatus(params: ActionParams): Try[Any] = Try {
        val isSafe  = paramByName(params, "status") map (_ == "safe") getOrElse true
        val isDraft = booleanParamByName(params, "draft", default = false)

        val saveStatus     = if (isDraft) Seq.empty else persistenceInstance.rootElement \ "data-status"
        val autoSaveStatus = persistenceInstance.rootElement \ "autosave" \ "status"

        (saveStatus ++ autoSaveStatus) foreach (setvalue(_, if (isSafe) "clean" else "dirty"))
    }

    private def messageFromResourceOrParam(params: ActionParams) = {
        def fromResource = paramByNameOrDefault(params, "resource") map (k ⇒ currentFRResources \ "detail" \ "messages" \ k stringValue)
        def fromParam    = paramByName(params, "message")

        fromResource orElse fromParam
    }

    def trySuccessMessage(params: ActionParams): Try[Any] =
        Try(FormRunner.successMessage(messageFromResourceOrParam(params).map(evaluateValueTemplate).get))

    def tryErrorMessage(params: ActionParams): Try[Any] =
        Try(FormRunner.errorMessage(messageFromResourceOrParam(params).map(evaluateValueTemplate).get))

    def tryConfirm(params: ActionParams): Try[Any] =
        Try {
            def defaultMessage = currentFRResources \ "detail" \ "messages" \ "confirmation-dialog-message" stringValue
            def message        = messageFromResourceOrParam(params) getOrElse defaultMessage

            show("fr-confirmation-dialog", Map("message" → Some(message)))
        }

    def tryShowResultDialog(params: ActionParams): Try[Any] =
        Try {
            show("fr-submission-result-dialog", Map(
                "fr-content" → Some(topLevelInstance(FormModel, "fr-create-update-submission-response").get.rootElement)
            ))
        }

    def tryCaptcha(params: ActionParams): Try[Any] =
        Try {
            if (showCaptcha)
                dispatch(name = "fr-verify", targetId = "captcha")
        }

    def trySendEmail(params: ActionParams): Try[Any] =
        Try {
            // NOTE: As of 2013-05-15, email-form.xpl recreates the PDF anyway, which is wasteful
//            implicit val formRunnerParams = FormRunnerParams()
//            if (booleanFormRunnerProperty("oxf.fr.email.attach-pdf"))
//                tryCreatePDFIfNeeded(EmptyActionParams).get

            sendThrowOnError("fr-email-service-submission")
        }

    // Defaults except for `uri` and `serialization`
    private val DefaultSendParameters = Map(
        "method"              → "post",
        "prune"               → "true",
        "annotate"            → "",
        "replace"             → "none",
        "content"             → "xml",
        "data-format-version" → "4.0.0",
        "parameters"          → "app form form-version document valid language process data-format-version"
    )

    private val SendParameterKeys = List("uri", "serialization") ++ DefaultSendParameters.keys

    def trySend(params: ActionParams): Try[Any] =
        Try {

            implicit val formRunnerParams @ FormRunnerParams(app, form, formVersion, document, _) = FormRunnerParams()

            val propertyPrefixOpt = paramByNameOrDefault(params, "property")

            def findParamValue(name: String) = {

                def fromParam    = paramByName(params, name)
                def fromProperty = propertyPrefixOpt flatMap (prefix ⇒ formRunnerProperty(prefix + "." + name))
                def fromDefault  = DefaultSendParameters.get(name)

                fromParam orElse fromProperty orElse fromDefault
            }

            // This is used both as URL parameter and as submission parameter
            val dataVersion =
                findParamValue("data-format-version") map evaluateValueTemplate get

            val paramsToAppend =
                stringOptionToSet(findParamValue("parameters")).to[List]

            val paramValuesToAppend = paramsToAppend collect {
                case name @ "process"             ⇒ name → runningProcessId.get
                case name @ "app"                 ⇒ name → app
                case name @ "form"                ⇒ name → form
                case name @ "form-version"        ⇒ name → formVersion
                case name @ "document"            ⇒ name → document.get
                case name @ "valid"               ⇒ name → dataValid.toString
                case name @ "language"            ⇒ name → currentLang.stringValue
                case name @ "noscript"            ⇒ name → isNoscript.toString
                case name @ "data-format-version" ⇒ name → dataVersion
            }

            val propertiesAsPairs =
                SendParameterKeys map (key ⇒ key → findParamValue(key))

            // Append query parameters to the URL and evaluate XVTs
            val evaluatedPropertiesAsMap =
                propertiesAsPairs map {
                    case (n @ "uri",    s @ Some(_)) ⇒ n → (s map evaluateValueTemplate map (recombineQuery(_, paramValuesToAppend)))
                    case (n @ "method", s @ Some(_)) ⇒ n → (s map evaluateValueTemplate map (_.toLowerCase))
                    case (n,            s @ Some(_)) ⇒ n → (s map evaluateValueTemplate)
                    case other                       ⇒ other
                } toMap

            def findDefaultSerialization(method: String) = method match {
                case "post" | "put" ⇒ "application/xml"
                case _              ⇒ "none"
            }

            val effectiveSerialization =
                evaluatedPropertiesAsMap.get("serialization").flatten orElse
                    (evaluatedPropertiesAsMap.get("method").flatten map findDefaultSerialization)

            val evaluatedSendProperties =
                evaluatedPropertiesAsMap + ("serialization" → effectiveSerialization)

            // Create PDF if needed
            if (stringOptionToSet(evaluatedSendProperties("content")) exists Set("pdf", "pdf-url"))
                tryCreatePDFIfNeeded(EmptyActionParams).get

            // TODO: Remove duplication once @replace is an AVT
            val replace = if (evaluatedSendProperties.get("replace") exists (_ == Some("all"))) "all" else "none"

            // Set data-safe-override as we know we are not losing data upon navigation. This happens:
            // - with changing mode (tryChangeMode)
            // - when navigating away using the "send" action
            if (replace == "all")
                setvalue(persistenceInstance.rootElement \ "data-safe-override", "true")

            sendThrowOnError(s"fr-send-submission-$replace", evaluatedSendProperties)
        }

    private val TestCommonParams = List[(String, () ⇒ Boolean)](
        NoscriptParam   → (() ⇒ isNoscript),
        EmbeddableParam → (() ⇒ isEmbeddable)
    )

    private val CommonParamNames = (TestCommonParams map (_._1) toSet) + "form-version"

    // Automatically prepend fr-noscript and orbeon-embeddable when needed, unless they are already specified
    // NOTE: We don't need to pass fr-language to most submissions, as the current language is kept in the session.
    // Heuristic: We append only if the URL doesn't have a protocol. This might not always be a right guess.
    private def prependCommonFormRunnerParameters(pathQuery: String) =
        if (! NetUtils.urlHasProtocol(pathQuery)) {

            val (path, params) = splitQueryDecodeParams(pathQuery)

            val newParams =
                for {
                    (name, currentValue) ← TestCommonParams
                    valueFromPath        = params collectFirst { case (`name`, v) ⇒ v == "true" }
                    effectiveValue       = valueFromPath getOrElse currentValue.apply
                    if effectiveValue
                } yield
                    name → "true"

            recombineQuery(path, newParams ::: (params filterNot (p ⇒ CommonParamNames(p._1))))
        } else
            pathQuery

    private def prependUserParameters(pathQuery: String) = {

        val (path, params) = splitQueryDecodeParams(pathQuery)

        val newParams =
            for {
                (name, values) ← containingDocument.getRequestParameters.to[List]
                if ! CommonParamNames(name) // built-in parameters win
                value          ← values
            } yield
                name → value

        recombineQuery(path, newParams ::: params)
    }

    private def tryNavigateTo(path: String): Try[Any] =
        Try(load(prependCommonFormRunnerParameters(path), progress = false))

    private def tryChangeMode(path: String): Try[Any] =
        Try {
            Map[Option[String], String](
                Some("uri")                 → prependUserParameters(prependCommonFormRunnerParameters(path)),
                Some("method")              → "post",
                Some("prune")               → "false",
                Some("replace")             → "all",
                Some("content")             → "xml",
                Some("data-format-version") → "edge",
                Some("parameters")          → "form-version data-format-version"
            )
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
            def isAbsolute(s: String) = s.startsWith("/") || NetUtils.urlHasProtocol(s)

            def fromParams = params.get(Some("uri")) orElse (params.get(None) filter isAbsolute)

            def fromProperties = {
                val property =  params.get(Some("property")) orElse (params.get(None) filterNot isAbsolute) getOrElse "oxf.fr.detail.close.uri"
                formRunnerProperty(property)
            }

            fromParams orElse fromProperties map evaluateValueTemplate flatMap nonEmptyOrNone get
        } flatMap
            tryNavigateTo

    def tryNavigateToReview(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()
            s"/fr/$app/$form/view/$document"
        } flatMap
            tryChangeMode

    def tryNavigateToEdit(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()
            s"/fr/$app/$form/edit/$document"
        } flatMap
            tryChangeMode

    def tryOpenPDF(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, _, Some(document), _) = FormRunnerParams()

            val requestedLangQuery = (
                paramByName(params, "lang")
                flatMap nonEmptyOrNone
                map     (lang ⇒ List("fr-remember-language" → "false", "fr-language" → lang))
            )

            recombineQuery(s"/fr/$app/$form/pdf/$document", requestedLangQuery getOrElse Nil)
        } flatMap
            tryChangeMode

    def tryToggleNoscript(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, _, Some(document), mode) = FormRunnerParams()
            s"/fr/$app/$form/$mode/$document?$NoscriptParam=${(! isNoscript).toString}"
        } flatMap
            tryChangeMode

    // Visit/unvisit controls
    def tryVisitAll(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-visit-all",   targetId = ErrorSummaryModel))
    def tryUnvisitAll(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-unvisit-all", targetId = ErrorSummaryModel))

    // Collapse/expand sections
    def tryCollapseSections(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-collapse-all", targetId = SectionsModel))
    def tryExpandSections(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-expand-all",   targetId = SectionsModel))

    // Navigate the wizard to the previous page
    def tryWizardPrev(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-prev", targetId = "fr-view-wizard"))

    // Navigate the wizard to the next page
    def tryWizardNext(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-next", targetId = "fr-view-wizard"))

    def pdfURLInstanceRootElementOpt = topLevelInstance(PersistenceModel, "fr-pdf-url-instance") map (_.rootElement)

    def tryCreatePDFIfNeeded(params: ActionParams): Try[Any] =
        Try {
            pdfURLInstanceRootElementOpt foreach { pdfURLInstanceRootElement ⇒
                // Only create if not available yet
                if (StringUtils.isBlank(pdfURLInstanceRootElement.stringValue))
                    sendThrowOnError("fr-pdf-service-submission")
            }
        }
}
