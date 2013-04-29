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
import org.orbeon.oxf.util.{NetUtils, Logging}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import scala.annotation.tailrec
import scala.collection.breakOut
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}
import scala.util.control.{ControlThrowable, Breaks}
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.common.OXFException

// Implementation of simple processes
//
// - A process is usually associated with a Form Runner button.
// - A process can have a name which translates into a definition defined in a property.
// - The property specifies a sequence of actions separated by combinators.
// - Actions are predefined, but some of them are configurable.
//
object SimpleProcess extends Actions with Logging {

    private val ProcessPropertyPrefix = "oxf.fr.detail.process"
    private val ProcessPropertyTokens = ProcessPropertyPrefix split """\.""" size
    
    type ActionParams = Map[Option[String], String]
    type Action       = ActionParams ⇒ Try[Any]

    private val StandardActions = Map[String, Action](
        "success" → trySuccess,
        "failure" → tryFailure,
        "process" → tryProcess
    )

    private val AllAllowedActions = StandardActions ++ AllowedActions

    private val processBreaks = new Breaks
    import processBreaks._

    import ProcessParser._

    private object ProcessParser {

        sealed abstract class Combinator(val name: String)
        case object ThenCombinator    extends Combinator("then")
        case object RecoverCombinator extends Combinator("recover")

        val CombinatorsByName = Seq(ThenCombinator, RecoverCombinator) map (c ⇒ c.name → c) toMap

        sealed abstract class ProcessAst
        case class ProcessNode(action: Option[ActionAst], actions: List[PairAst]) extends ProcessAst
        case class PairAst(combinator: CombinatorAst, action: ActionAst) extends ProcessAst
        case class ActionAst(name: String, params: Map[Option[String], String]) extends ProcessAst
        case class CombinatorAst(combinator: Combinator) extends ProcessAst

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

                // Allowed actions are either built-in actions or other processes
                val allowedActions = AllAllowedActions.keySet ++ (
                    for {
                        property ← properties.propertiesStartsWith(ProcessPropertyPrefix)
                        tokens   = property split """\."""
                    } yield
                        tokens(ProcessPropertyTokens)
                )
    
                def actionUsage(action: String)         = s"action '$action' is not supported, must be one of: ${allowedActions mkString ", "}"
                def combinatorUsage(combinator: String) = s"combinator '$combinator' is not supported, must be one of: ${CombinatorsByName.keys mkString ", "}"
    
                def checkAction(action: String)         = require(allowedActions(action), actionUsage(action))
                def checkCombinator(combinator: String) = require(CombinatorsByName.contains(combinator) , combinatorUsage(combinator))
    
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
    
                            PairAst(CombinatorAst(CombinatorsByName(combinator)), parseAction(action))
    
                    }
    
                ProcessNode(Some(firstAction), actions.toList)
            }
        }
    }

    // Main entry point for starting the process associated with a named button
    def runProcessByName(name: String): Unit =
        runProcess(rawProcessByName(name))

    private def rawProcessByName(name: String) = {
        implicit val formRunnerParams = FormRunnerParams()

        formRunnerProperty(ProcessPropertyPrefix + '.' + name) flatMap
        nonEmptyOrNone orElse // don't accept an existing but blank property
        buildProcessFromLegacyProperties(name) getOrElse ""
    }

    def runProcess(process: String): Try[Any] = {
        implicit val logger = containingDocument.getIndentedLogger("process")
        withDebug("running process", Seq("process" → process)) {
            tryBreakable {
                runSubProcess(process) recoverWith { case _ ⇒
                    // Send a final error if there is one
                    tryErrorMessage(Map(Some("message") → "process-error"))
                }
            } catchBreak {
                Success(())
            }
        }
    }

    private def runSubProcess(process: String): Try[Any] = {

        implicit val logger = containingDocument.getIndentedLogger("process")

        // Parse
        val parsedProcess = ProcessParser.parseProcess(process)

        if (parsedProcess.action.isEmpty) {
            debug("empty process, canceling process")
            Success(())
        } else {

            def runAction(action: ActionAst) =
                withDebug("running action", Seq("action" → action.toString)) {
                    AllAllowedActions.get(action.name) getOrElse ((_: ActionParams) ⇒ tryProcess(Map(Some("name") → action.name))) apply action.params
                }

            // Interpret process recursively
            @tailrec def nextGroup(tried: Try[Any], groups: Iterator[PairAst]): Try[Any] =
                if (groups.hasNext) {
                    val PairAst(nextCombinator, nextAction) = groups.next()

                    val newTried =
                        nextCombinator.combinator match {
                            case ThenCombinator ⇒
                                debug("combining with then", Seq("action" → nextAction.toString))
                                tried flatMap (_ ⇒ runAction(nextAction))
                            case RecoverCombinator ⇒
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
            nextGroup(runAction(parsedProcess.action.get), processIterator)
        }
    }

    // Interrupt the process and complete with a success
    // We will rethrow this as we explicitly check for ControlThrowable above
    def trySuccess(params: ActionParams): Try[Any] = Try(break())

    // Interrupt the process and complete with a failure
    def tryFailure(params: ActionParams): Try[Any] = ???

    // Run a sub-process
    def tryProcess(params: ActionParams): Try[Any] =
        Try(params.get(Some("name")) getOrElse params(None)) map rawProcessByName flatMap runSubProcess

    // Legacy: build "workflow-send" process based on properties
    private def buildProcessFromLegacyProperties(buttonName: String)(implicit p: FormRunnerParams) = {

        implicit val logger = containingDocument.getIndentedLogger("process")

        def booleanPropertySet(name: String) = formRunnerProperty(name) exists (_ == "true")
        def stringPropertySet (name: String) = formRunnerProperty(name) flatMap nonEmptyOrNone isDefined

        buttonName match {
            case "workflow-send" ⇒
                val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send.email")
                val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send.success.uri")
                val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send.error.uri")

                def isLegacyCreatePDF =
                    isLegacySendEmail       && booleanPropertySet("oxf.fr.email.attach-pdf")  ||
                    isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send.pdf")

                val buffer = ListBuffer[String]()

                buffer += "require-uploads"
                buffer += ThenCombinator.name
                buffer += "require-valid"
                buffer += ThenCombinator.name
                buffer += "save"
                buffer += ThenCombinator.name
                buffer += """success-message("save-success")"""

                if (isLegacyCreatePDF) {
                    buffer += ThenCombinator.name
                    buffer += "pdf"
                }

                if (isLegacySendEmail) {
                    buffer += ThenCombinator.name
                    buffer += "email"
                }

                if (isLegacyNavigateSuccess) {
                    buffer += ThenCombinator.name
                    buffer += """send("oxf.fr.detail.send.success")"""
                }

                if (isLegacyNavigateError) {
                    buffer += RecoverCombinator.name
                    buffer += """send("oxf.fr.detail.send.error")"""
                }

                Some(buffer mkString " ")
            case _ ⇒
                None
        }
    }
}

trait Actions {

    import SimpleProcess._

    def AllowedActions = Map[String, Action](
        "validate"                    → tryValidate,
        "pending-uploads"             → tryPendingUploads,
        "save"                        → trySaveAttachmentsAndData,
        "success-message"             → trySuccessMessage,
        "error-message"               → tryErrorMessage,
        "pdf"                         → tryCreatePDF,
        "email"                       → trySendEmail,
        "send"                        → trySend,
        "navigate"                    → tryNavigate,
        "review"                      → tryNavigateToReview,
        "edit"                        → tryNavigateToEdit,
        "summary"                     → tryNavigateToSummary,
        "visit-all"                   → tryVisitAll,
        "unvisit-all"                 → tryUnvisitAll,
        "expand-all"                  → tryExpandSections,
        "collapse-all"                → tryCollapseSections,
        "result-dialog"               → tryShowResultDialog,
        "captcha"                     → tryCaptcha,
        "wizard-prev"                 → tryWizardPrev,
        "wizard-next"                 → tryWizardNext
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
            val property = params.get(Some("property")) orElse params.get(None)

            implicit val formRunnerParams = FormRunnerParams()
            def ignore = property flatMap formRunnerProperty exists (_ == "false")

            if (! ignore && ! dataValid)
                throw new OXFException("Data is invalid")
        }

    def trySaveAttachmentsAndData(params: ActionParams): Try[Any] =
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

            val commonQueryString = s"valid=$dataValid"

            // Save all attachments
            // - also pass a "valid" argument with whether the data was valid
            def saveAttachments() =
                uploadHolders zip afterURLs map { case pair @ (holder, resource) ⇒
                    sendThrowOnError("fr-create-update-attachment-submission", Map(
                        "holder"   → Some(holder),
                        "resource" → Some(appendQueryString(resource, commonQueryString)))
                    )
                    pair
                }

            // Update the paths on success
            def updatePaths(s: Seq[(NodeInfo, String)]) =
                s foreach { case (holder, resource) ⇒ setvalue(holder, resource) }

            // Save XML document
            // - always store form data as "data.xml"
            // - also pass a "valid" argument with whether the data was valid
            def saveData() =
                sendThrowOnError("fr-create-update-submission", Map(
                    "holder"   → Some(formInstance.rootElement),
                    "resource" → Some(appendQueryString("data.xml", commonQueryString)))
                )

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

    def trySuccessMessage(params: ActionParams): Try[Any] =
        Try {
            val resourceKey = params.get(Some("message")) getOrElse params(None)
            setvalue(persistenceInstance.rootElement \ "message", currentFRResources \ "detail" \ "messages" \ resourceKey)
            toggle("fr-message-success")
        }

    def tryErrorMessage(params: ActionParams): Try[Any] =
        Try {
            val resourceKey = params.get(Some("message")) getOrElse params(None)
            dispatch(name = "fr-show", targetId = "fr-error-dialog", properties = Map(
                "message" → Some(currentFRResources \ "detail" \ "messages" \ resourceKey)
            ))
        }

    def tryShowResultDialog(params: ActionParams): Try[Any] =
        Try {
            show("fr-submission-result-dialog", Map(
                "fr-content" → Some(topLevelInstance("fr-persistence-model", "fr-create-update-submission-response").get.rootElement)
            ))
        }
    
    def tryCaptcha(params: ActionParams): Try[Any] =
        Try {
            if (hasCaptcha && (persistenceInstance.rootElement \ "captcha" === "false"))
                dispatch(name = "fr-verify", targetId = "captcha")
        }

    def tryCreatePDF(params: ActionParams): Try[Any] =
        Try(sendThrowOnError("fr-pdf-service-submission"))

    def trySendEmail(params: ActionParams): Try[Any] =
        Try(sendThrowOnError("fr-email-service-submission"))

    def trySend(params: ActionParams): Try[Any] =
        Try {
            val prefix = params.get(Some("property")) getOrElse params(None)

            implicit val formRunnerParams @ FormRunnerParams(app, form, document, _) = FormRunnerParams()

            // Defaults except for "uri"
            val Defaults = Map(
                "method"  → "post",
                "prune"   → "true",
                "replace" → "none"
            )

            val propertiesAsPairs =
                Seq("uri", "method", "prune", "replace") map (key ⇒ key → (formRunnerProperty(prefix + "." + key) orElse Defaults.get(key)))

            // Append query parameters to the URL
            val withUpdatedURI =
                propertiesAsPairs map {
                    case ("uri", Some(uri)) ⇒ "uri" → Some(appendQueryString(uri, s"app=$app&form=$form&document=${document.get}&valid=$dataValid"))
                    case other              ⇒ other
                }

            val propertiesAsMap = withUpdatedURI.toMap

            // TODO: Remove duplication once @replace is an AVT
            val replace = if (propertiesAsMap.get("replace") exists (_ == Some("all"))) "all" else "none"
            sendThrowOnError(s"fr-send-submission-$replace", propertiesAsMap)
        }

    private def tryNavigateTo(path: String): Try[Any] =
        Try(load(path, progress = false))

    // Navigate to a URL specified in parameters or indirectly in properties
    // If no URL is specified, the action fails
    def tryNavigate(params: ActionParams): Try[Any] =
        Try {
            implicit val formRunnerParams = FormRunnerParams()

            // Heuristic: If the parameter is anonymous, we take it as a URL or path if it looks like one. Otherwise, we
            // consider it is a property. We could also look at whether the value looks like a property. To resolve
            // ambiguity, we'll need to parse named parameters.
            def isAbsolute(s: String) = s.startsWith("/") || NetUtils.urlHasProtocol(s)

            def fromParams = params.get(Some("uri")) orElse (params.get(None) filter isAbsolute)

            def fromProperties = {
                val property =  params.get(Some("property")) orElse (params.get(None) filterNot isAbsolute) getOrElse "oxf.fr.detail.close.uri"
                formRunnerProperty(property)
            }

            // Try to automatically append fr-noscript
            // Heuristic: We append it only if the URL doesn't have a protocol. This might not always be a right guess.
            def appendNoscriptIfNeeded(s: String) =
                if (XFormsProperties.isNoscript(containingDocument) && ! NetUtils.urlHasProtocol(s))
                    appendQueryString(s, "fr-noscript=true")
                else
                    s

            fromParams orElse fromProperties flatMap nonEmptyOrNone map appendNoscriptIfNeeded get
        } flatMap
            tryNavigateTo

    def tryNavigateToReview(params: ActionParams): Try[Any] =
        Try(sendThrowOnError("fr-workflow-review-submission"))

    def tryNavigateToEdit(params: ActionParams): Try[Any] =
        Try(sendThrowOnError("fr-workflow-edit-submission"))

    def tryNavigateToSummary(params: ActionParams): Try[Any]  =
        Try {
            val FormRunnerParams(app, form, _, _) = FormRunnerParams()
            s"/fr/$app/$form/summary"
        } flatMap
            tryNavigateTo

    // Visit/unvisit controls
    def tryVisitAll(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-visit-all",   targetId = "fr-error-summary-model"))
    def tryUnvisitAll(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-unvisit-all", targetId = "fr-error-summary-model"))

    // Collapse/expand sections
    def tryCollapseSections(params: ActionParams): Try[Any] = Try(dispatch(name = "fr-collapse-all", targetId = "fr-sections-model"))
    def tryExpandSections(params: ActionParams)  : Try[Any] = Try(dispatch(name = "fr-expand-all",   targetId = "fr-sections-model"))

    def tryWizardPrev(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-prev", targetId = "fr-view-wizard"))

    def tryWizardNext(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-next", targetId = "fr-view-wizard"))
}
