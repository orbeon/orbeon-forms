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
package org.orbeon.oxf.fr

import FormRunner._
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{IndentedLogger, Logging}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import scala.annotation.tailrec
import scala.collection.breakOut
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}
import scala.util.control.{ControlThrowable, Breaks}
import org.orbeon.oxf.xforms.XFormsProperties

// All the logic associated with the universal detail page button. This button is able to run customizable processes.
// A button has a name, and the name translates into the definition of a process defined in a property. Each process is
// defined by a series of actions separated by combinators. The actions are predefined, but some of them are
// configurable.
object ProcessButton extends Logging {

    private val Then    = "then"
    private val Recover = "recover"
    private val AllowedCombinators = Set(Then, Recover)
    
    type ActionParams = Map[Option[String], String]

    private val AllowedActions = Map[String, ActionParams ⇒ Try[Any]](
        "done"                        → tryDone,
        "validate"                    → tryValidate,
        "maybe-validate"              → tryMaybeValidate,
        "save"                        → trySaveAttachmentsAndData,
        "success-message"             → trySuccessMessage,
        "error-message"               → tryErrorMessage,
        "pdf"                         → tryCreatePDF,
        "email"                       → trySendEmail,
        "send"                        → trySend,
        "navigate"                    → tryNavigate,
        "alfresco"                    → trySendAlfresco,
        "review"                      → tryNavigateToReview,
        "edit"                        → tryNavigateToEdit,
        "summary"                     → tryNavigateToSummary,
        "home"                        → tryNavigateToHome,
        "collapse-all"                → tryCollapseSections,
        "expand-all"                  → tryExpandSections,
        "result-dialog"               → tryShowSubmitDialog
    )

    private val processBreaks = new Breaks
    import processBreaks.{breakable, break}

    private def formRunnerProperty(prefix: String, local: String)(implicit p: FormRunnerParams) =
        Option(properties.getObject(buildPropertyName(prefix + "." + local))) map (_.toString)

    // Main entry point for starting the process associated with a named button
    def runProcessByName(buttonName: String): Unit = {

        implicit val logger = containingDocument.getIndentedLogger("send")

        implicit val formRunnerParams = FormRunnerParams()

        // Read properties
        val rawProcess = (
            formRunnerProperty("oxf.fr.detail.send.process", buttonName) orElse
            buildProcessFromLegacyProperties(buttonName)
            getOrElse ""
        )

        runProcess(rawProcess)
    }

    private object ProcessParser {

        sealed abstract class ProcessAst
        case class ProcessNode(action: Option[ActionAst], actions: List[PairAst]) extends ProcessAst
        case class PairAst(combinator: CombinatorAst, action: ActionAst) extends ProcessAst
        case class ActionAst(name: String, params: Map[Option[String], String]) extends ProcessAst
        case class CombinatorAst(name: String) extends ProcessAst

        // Match actions of the form:
        // - foo
        // - foo("bar")
        // Later we also want to handle:
        // - foo(p1 = "v1", p2 = "v2")
        val ActionWithSingleAnonymousParam = """^([^(]+)(\("([^(^)^"]*)"\))?$""".r
    
        def parseProcess(process: String): ProcessNode = {
            val tokens: List[String] = split(process)(breakOut)
    
            if (tokens.isEmpty)
                // Empty process
                ProcessNode(None, Nil)
            else {
                // Non-empty process
    
                def actionUsage(action: String)         = s"action '$action' is not supported, must be one of: ${AllowedActions.keys mkString ", "}"
                def combinatorUsage(combinator: String) = s"combinator '$combinator' is not supported, must be one of: ${AllowedCombinators mkString ", "}"
    
                def checkAction(action: String)         = require(AllowedActions.contains(action), actionUsage(action))
                def checkCombinator(combinator: String) = require(AllowedCombinators(combinator) , combinatorUsage(combinator))
    
                def parseAction(rawAction: String) = {
    
                    rawAction match {
                        case ActionWithSingleAnonymousParam(actionName, _, null) ⇒
                            checkAction(actionName)
                            ActionAst(actionName, Map())
                        case ActionWithSingleAnonymousParam(actionName, _, param) ⇒
                            checkAction(actionName)
                            ActionAst(actionName, Map(None → param))
                        case _ ⇒
                            throw new IllegalArgumentException(s"invalid action syntax for '$rawAction'")
                    }
                }
    
                require(tokens.size % 2 == 1, "process must have an odd number of steps")
    
                val firstAction = parseAction(tokens(0))
    
                val actions =
                    tokens.tail grouped 2 map {
                        case List(combinator, action) ⇒
                            checkCombinator(combinator)
    
                            PairAst(CombinatorAst(combinator), parseAction(action))
    
                    }
    
                ProcessNode(Some(firstAction), actions.toList)
            }
        }
    }

    def runProcess(process: String)(implicit logger: IndentedLogger): Unit = {

        import ProcessParser._

        // Parse
        val parsedProcess = ProcessParser.parseProcess(process)

        if (parsedProcess.action.isEmpty) {
            debug("empty process, canceling process")
            return
        }

        // This is required before all in any case
        if (tryCheckUploads().get) {
            debug("uploads in progress, canceling process")
            return
        }

        // Run process
        withDebug("running process", Seq("process" → process)) {

            // Actions which must cause an early termination, like `validate`, call `break()`
            breakable {

                def runAction(action: ActionAst) =
                    withDebug("running action", Seq("action" → action.toString)) {
                        AllowedActions(action.name).apply(action.params)
                    }

                // Interpret process recursively
                @tailrec def nextGroup(tried: Try[Any], groups: Iterator[PairAst]): Try[Any] =
                    if (groups.hasNext) {
                        val PairAst(nextCombinator, nextAction) = groups.next()

                        val newTried =
                            nextCombinator.name match {
                                case Then ⇒
                                    debug("combining with then", Seq("action" → nextAction.toString))
                                    tried flatMap (_ ⇒ runAction(nextAction))
                                case Recover ⇒
                                    debug("combining with recover", Seq("action" → nextAction.toString))
                                    tried recoverWith {
                                        case t: ControlThrowable ⇒
                                            debug("rethrowing ControlThrowable")
                                            throw t
                                        case t ⇒
                                            debug("recovering", Seq("throwable" → OrbeonFormatter.format(t)))
                                            runAction(nextAction)
                                    }
                            }

                        nextGroup(newTried, groups)
                    } else
                        tried

                // Run first action and recurse
                val processIterator = parsedProcess.actions.iterator
                val processResult = nextGroup(runAction(parsedProcess.action.get), processIterator)

                // Send a final error if there is one
                processResult recoverWith { case _ ⇒
                    tryErrorMessage(Map(Some("message") → "process-error"))
                }
            }
        }
    }

    private def buildProcessFromLegacyProperties(buttonName: String)(implicit logger: IndentedLogger, p: FormRunnerParams) = {

        def booleanPropertySet(prefix: String, name: String) = formRunnerProperty(prefix, name) exists (_ == "true")
        def stringPropertySet (prefix: String, name: String) = formRunnerProperty(prefix, name) flatMap nonEmptyOrNone isDefined

        buttonName match {
            case "workflow-send" ⇒
                val isLegacySendAlfresco    = booleanPropertySet("oxf.fr.detail.send", "alfresco")
                val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send", "email")
                val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send", "success.uri")
                val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send", "error.uri")

                def isLegacyCreatePDF =
                    isLegacySendEmail       && booleanPropertySet("oxf.fr.email", "attach-pdf")  ||
                    isLegacySendAlfresco    && booleanPropertySet("oxf.fr.alfresco", "send-pdf") ||
                    isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send", "pdf")

                val buffer = ListBuffer[String]()

                buffer += "validate"
                buffer += Then
                buffer += "save"
                buffer += Then
                buffer += """success-message("save-success")"""

                if (isLegacyCreatePDF) {
                    buffer += Then
                    buffer += "pdf"
                }

                if (isLegacySendAlfresco) {
                    buffer += Then
                    buffer += "alfresco"
                }

                if (isLegacySendEmail) {
                    buffer += Then
                    buffer += "email"
                }

                if (isLegacyNavigateSuccess) {
                    buffer += Then
                    buffer += """send("oxf.fr.detail.send.success")"""
                }

                if (isLegacyNavigateError) {
                    buffer += Recover
                    buffer += """send("oxf.fr.detail.send.error")"""
                }

                Some(buffer mkString " ")
            case _ ⇒
                None
        }
    }

    // Check whether there are pending uploads
    def tryCheckUploads() =
        Try {
            val hasPendingUploads = containingDocument.countPendingUploads > 0

            if (hasPendingUploads) {
                // Open error dialog
                dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map(
                    "message" → Some(currentFRResources \ "detail" \ "messages" \ "upload-in-progress")
                ))
            }

            hasPendingUploads
        }

    // Running this action will interrupt the process
    // We will rethrow this as we explicitly check for ControlThrowable above
    def tryDone(params: ActionParams): Try[Unit] = Try(break())

    // Validate form data
    // Interrupt the process if the data is not valid
    def tryValidate(params: ActionParams): Try[Unit] =
        Try {
            // We use instance('fr-error-summary-instance')/valid and not xxf:valid() because the instance validity may
            // not be reflected with the use of XBL components.
            val isValid = errorSummaryInstance.rootElement \ "valid" === "true"

            if (! isValid) {
                // Mark all controls as visited
                dispatch(name = "fr-visit-all", targetId = "fr-error-summary-model")

                // Open all sections
                tryExpandSections(Map()).get

                // Open error dialog
                dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map(
                    "message" → Some(currentFRResources \ "detail" \ "messages" \ "form-validation-error")
                ))

                // It makes sense at this point for this to break right away.
                // One ideas was "validate recover done", but it's unclear that this buys much.
                // OTOH if you had parentheses, you could write:
                // "validate recover (visit-alerts then expand-sections then validation-error-dialog then done) then ..."
                break()
            }
        }

    // For backward compatibility: if "oxf.fr.detail.save" is false, consider validation successful, otherwise perform
    // regular validation
    def tryMaybeValidate(params: ActionParams): Try[Unit] =
        if (formRunnerProperty("oxf.fr.detail.save", "validate")(FormRunnerParams()) == "false")
            Success(())
        else
            tryValidate(params)

    def trySaveAttachmentsAndData(params: ActionParams): Try[Unit] =
        Try {
            val FormRunnerParams(app, form, document, _) = FormRunnerParams()

            require(document.isDefined)

            // Notify that the data is about to be saved
            dispatch(name = "fr-data-save-prepare", targetId = "fr-form-model")

            // Find all instance nodes containing upload file URLs
            val uploadHolders = (
                formInstance.root \\ Node
                filter (n ⇒ isAttribute(n) || isElement(n) && ! hasChildElement(n))
                filter (n ⇒ isUploadedFileURL(n.stringValue.trim))
            )

            val beforeURLs = uploadHolders map (_.stringValue.trim)

            // For each one compute the persistence resource name
            val afterURLs  = beforeURLs map (url ⇒ createAttachmentPath(app, form, document.get, url))

            // Save all attachments
            def saveAttachments() =
                uploadHolders zip afterURLs map { case pair @ (holder, resource) ⇒
                    sendThrowOnError("fr-create-update-attachment-submission", Map("holder" → Some(holder), "resource" → Some(resource)))
                    pair
                }

            // Update the paths on success
            def updatePaths(s: Seq[(NodeInfo, String)]) =
                s foreach { case (holder, resource) ⇒ setvalue(holder, resource) }

            // Save XML document
            // We always store form data as "data.xml"
            def saveData() =
                sendThrowOnError("fr-create-update-submission", Map("holder" → Some(formInstance.rootElement), "resource" → Some("data.xml")))

            // Do things in order
            val attachments = saveAttachments()
            updatePaths(attachments)
            saveData()

            // If we were in new mode, now we must be in edit mode
            setvalue(parametersInstance.rootElement \ "mode", "edit")

            // HACK: Force this before cleaning the status because we do a setvalue just before calling the submission
            recalculate("fr-persistence-model")
            refresh("fr-persistence-model")

            setvalue(topLevelInstance("fr-persistence-model", "fr-persistence-instance").get.rootElement \ "data-status", "clean")

            // Notify that the data is saved
            dispatch(name = "fr-data-save-done", targetId = "fr-form-model", properties = Map(
                "before-urls" → Some(beforeURLs),
                "after-urls"  → Some(afterURLs)
            ))
        }

    def trySuccessMessage(params: ActionParams): Try[Unit] =
        Try {
            val resourceKey = params.get(Some("message")) getOrElse params(None)
            setvalue(persistenceInstance.rootElement \ "message", currentFRResources \ "detail" \ "messages" \ resourceKey)
            toggle("fr-message-success")
        }

    def tryErrorMessage(params: ActionParams): Try[Unit] =
        Try {
            val resourceKey = params.get(Some("message")) getOrElse params(None)
            dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map(
                "message" → Some(currentFRResources \ "detail" \ "messages" \ resourceKey)
            ))
        }

    def tryShowSubmitDialog(params: ActionParams): Try[Unit] =
        Try {
            show("fr-submission-result-dialog", Map(
                "fr-content" → Some(topLevelInstance("fr-persistence-model", "fr-create-update-submission-response").get.rootElement)
            ))
        }

    def tryCreatePDF(params: ActionParams): Try[Unit] =
        Try(sendThrowOnError("fr-pdf-service-submission"))

    def trySendEmail(params: ActionParams): Try[Unit] =
        Try(sendThrowOnError("fr-email-service-submission"))

    def trySend(params: ActionParams): Try[Unit] =
        Try {
            val prefix = params.get(Some("properties")) orElse params.get(None) getOrElse "oxf.fr.detail.send.success"

            implicit val formRunnerParams = FormRunnerParams()

            val eventProperties =
                Seq("uri", "method", "prune", "replace") map (key ⇒ key → formRunnerProperty(prefix, key))

            sendThrowOnError("fr-send-submission", eventProperties.toMap)
        }

    private def tryNavigateTo(path: String): Try[Unit] =
        Try(load(path, progress = false))

    // Navigate to a URL specified in parameters or indirectly in properties
    // If no URL is specified, the action fails
    def tryNavigate(params: ActionParams): Try[Unit] =
        Try {
            implicit val formRunnerParams = FormRunnerParams()

            def fromParams = params.get(Some("uri")) flatMap nonEmptyOrNone

            def fromProperties = {
                val prefix =  params.get(Some("properties")) orElse params.get(None) getOrElse "oxf.fr.detail.close"
                formRunnerProperty(prefix, "uri") flatMap nonEmptyOrNone
            }

            fromParams orElse fromProperties get
        } flatMap
            tryNavigateTo

    def tryNavigateToReview(params: ActionParams): Try[Unit] =
        Try(sendThrowOnError("fr-workflow-review-submission"))

    def tryNavigateToEdit(params: ActionParams): Try[Unit] =
        Try(sendThrowOnError("fr-workflow-edit-submission"))

    private def appendNoscriptIfNeeded(path: String) =
        if (XFormsProperties.isNoscript(containingDocument))
            appendQueryString(path, "fr-noscript=true")
        else
            path

    def tryNavigateToSummary(params: ActionParams): Try[Unit]  =
        Try {
            val FormRunnerParams(app, form, _, _) = FormRunnerParams()
            appendNoscriptIfNeeded(s"/fr/$app/$form/summary")
        } flatMap
            tryNavigateTo

    def tryNavigateToHome(params: ActionParams): Try[Unit] =
        Try {
            appendNoscriptIfNeeded("/fr/")
        } flatMap
            tryNavigateTo

    // Collapse/expand sections
    def tryCollapseSections(params: ActionParams): Try[Unit] = Try(dispatch(name = "fr-collapse-all", targetId = "fr-sections-model"))
    def tryExpandSections(params: ActionParams)  : Try[Unit] = Try(dispatch(name = "fr-expand-all",   targetId = "fr-sections-model"))

    def trySendAlfresco(params: ActionParams): Try[Unit] =
        Try {
            ???
//                    <!-- Pass metadata with current language, or first language if current language is not found -->
//                    <xf:var name="form-titles" value="xxf:instance('fr-form-metadata')/title" as="xs:string"/>
//                    <xf:var name="form-descriptions" value="xxf:instance('fr-form-metadata')/description" as="xs:string"/>
//                    <xf:var name="form-title" value="($form-titles[@xml:lang = xxf:instance('fr-language-instance')], $form-titles[1])[1]" as="xs:string"/>
//                    <xf:var name="form-description" value="($form-descriptions[@xml:lang = xxf:instance('fr-language-instance')], $form-descriptions[1])[1]" as="xs:string"/>
//
//                    <!-- Send PDF data if requested -->
//                    <xf:action if="xxf:property(string-join(('oxf.fr.alfresco.send-pdf', $app, $form), '.'))">
//
//                        <xf:message level="xxf:log-debug">Sending PDF to Alfresco...</xf:message>
//
//                        <!-- Get URI of PDF data -->
//                        <xf:var name="pdf-uri" value="xpl:rewriteServiceURI(instance('fr-workflow-send-instance'), true())" as="xs:anyURI"/>
//
//                        <!-- Send everything to Alfresco -->
//                        <xf:dispatch targetid="fr-alfresco-model" name="alfresco-send-document">
//                            <xf:property name="fr:name" value="concat($form-title, ' (#', $document, ').pdf')"/>
//                            <xf:property name="fr:title" value="$form-title"/>
//                            <xf:property name="fr:description" value="$form-description"/>
//                            <xf:property name="fr:mimetype" value="'application/pdf'"/>
//                            <!-- Content as Base64 -->
//                            <xf:property name="fr:content" value="xxf:doc-base64($pdf-uri)"/>
//                        </xf:dispatch>
//                    </xf:action>
//                    <!-- Send XML data if requested -->
//                    <xf:action if="xxf:property(string-join(('oxf.fr.alfresco.send-xml', $app, $form), '.'))">
//
//                        <xf:message level="xxf:log-debug">Sending XML to Alfresco...</xf:message>
//
//                        <!-- Send data to Alfresco -->
//                        <xf:dispatch targetid="fr-alfresco-model" name="alfresco-send-document">
//                            <xf:property name="fr:name" value="concat($form-title, ' (#', $document, ').xml')"/>
//                            <xf:property name="fr:title" value="$form-title"/>
//                            <xf:property name="fr:description" value="$form-description"/>
//                            <xf:property name="fr:mimetype" value="'application/xml'"/>
//                            <!-- XML data -> string -> Base64 -->
//                            <xf:property name="fr:content" value="saxon:string-to-base64Binary(saxon:serialize(xxf:instance('fr-form-instance'), 'xml'), 'UTF-8')"/>
//                        </xf:dispatch>
//                    </xf:action>
    }
}
