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
import org.orbeon.scaxon.XML._
import annotation.tailrec
import collection.breakOut
import collection.mutable.ListBuffer
import util.{Success, Try}
import scala.util.control.{NonFatal, ControlThrowable, Breaks}
import org.orbeon.oxf.xforms.XFormsProperties
import org.orbeon.oxf.common.OXFException
import org.apache.commons.lang3.StringUtils
import org.orbeon.oxf.xforms.analysis.model.StaticBind._

// Implementation of simple processes
//
// - A process is usually associated with a Form Runner button.
// - A process can have a name which translates into a definition defined in a property.
// - The property specifies a sequence of actions separated by combinators.
// - Actions are predefined, but some of them are configurable.
//
object SimpleProcess extends FormRunnerActions with XFormsActions with Logging {

    import ProcessParser._
    import ProcessRuntime._

    private def processPropertyPrefix(scope: String) = scope
    private def processPropertyTokens(scope: String) = processPropertyPrefix(scope) split """\.""" size

    type ActionParams = Map[Option[String], String]
    type Action       = ActionParams ⇒ Try[Any]

    private val StandardActions = Map[String, Action](
        "success" → trySuccess,
        "failure" → tryFailure,
        "process" → tryProcess,
        "suspend" → trySuspend,
        "resume"  → tryResume,
        "abort"   → tryAbort,
        "nop"     → tryNOP
    )

    private val AllAllowedActions = StandardActions ++ AllowedFormRunnerActions ++ AllowedXFormsActions

    private object ProcessRuntime {

        import org.orbeon.oxf.util.DynamicVariable

        // Keep stack frames for the execution of action. They can nest with sub-processes.
        val processStackDyn = new DynamicVariable[Process]
        val processBreaks   = new Breaks

        // Scope an empty stack around a process execution
        def withEmptyStack[T](scope: String)(body: ⇒ T): T = {
            processStackDyn.withValue(Process(scope, Nil)) {
                body
            }
        }

        // Push a stack frame, run the body, and pop the frame
        def withStackFrame[T](process: String, programCounter: Int)(body: ⇒ T): T = {
            processStackDyn.value.get.frames = StackFrame(process, programCounter) :: processStackDyn.value.get.frames
            try body
            finally processStackDyn.value.get.frames = processStackDyn.value.get.frames.tail
        }

        // Return a process string which contains the continuation of the process after the current action
        def serializeContinuation = {
            val stack = processStackDyn.value.get.frames

            // Find the continuation, which is the concatenation of the continuation of all the sub-processes up to the
            // top-level process.
            val continuation =
                stack flatMap {
                    case StackFrame(process, actionCounter) ⇒
                        val tokens      = splitProcess(process)
                        val actionCount = (tokens.size + 1) / 2
                        val isLast      = actionCounter == actionCount - 1

                        // - If we are the last action, there is nothing to keep.
                        // - Otherwise we start with the following combinator.
                        if (isLast)
                            Nil
                        else
                            tokens.drop(actionCounter * 2 + 1)
                }

            // Continuation is either empty or starts with a combinator. We prepend the (always successful) "nop".
            "nop" :: continuation mkString " "
        }

        // Save a process into Form Runner so it can be read later
        def saveProcess(process: String) =
            setvalue(topLevelInstance("fr-persistence-model", "fr-processes-instance").get.rootElement, process)

        // Read a saved process
        def readSavedProcess =
            topLevelInstance("fr-persistence-model", "fr-processes-instance").get.rootElement.stringValue

        case class Process(scope: String, var frames: List[StackFrame])
        case class StackFrame(process: String, actionCounter: Int)
    }

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

        def splitProcess(process: String): List[String] = split(process)(breakOut)

        def parseProcess(scope: String, process: String): ProcessNode = {

            val tokens = splitProcess(process)

            if (tokens.isEmpty)
                // Empty process
                ProcessNode(None, Nil)
            else {
                // Non-empty process

                // Allowed actions are either built-in actions or other processes
                val allowedActions = AllAllowedActions.keySet ++ (
                    for {
                        property ← properties.propertiesStartsWith(processPropertyPrefix(scope))
                        tokens   = property split """\."""
                    } yield
                        tokens(processPropertyTokens(scope))
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

    import processBreaks._

    // Main entry point for starting a process associated with a named button
    def runProcessByName(scope: String, name: String): Unit =
        runProcess(scope, rawProcessByName(scope, name))

    private def rawProcessByName(scope: String, name: String) = {
        implicit val formRunnerParams = FormRunnerParams()

        formRunnerProperty(processPropertyPrefix(scope) + '.' + name) flatMap
        nonEmptyOrNone orElse // don't accept an existing but blank property
        buildProcessFromLegacyProperties(name) getOrElse ""
    }

    // Main entry point for starting a literal process
    def runProcess(scope: String, process: String): Try[Any] = {
        implicit val logger = containingDocument.getIndentedLogger("process")
        withDebug("running process", Seq("process" → process)) {
            // Scope the process (for suspend/resume)
            withEmptyStack(scope) {
                beforeProcess() flatMap { _ ⇒
                    tryBreakable {
                        runSubProcess(process)
                    } catchBreak {
                        Success(()) // to change once `tryFailure` is supported
                    }
                } doEitherWay {
                    afterProcess()
                } recoverWith { case t ⇒
                    // Log and send a user error if there is one
                    // NOTE: In the future, it would be good to provide the user with an error id.
                    error(OrbeonFormatter.format(t))
                    tryErrorMessage(Map(Some("resource") → "process-error"))
                }
            }
        }
    }

    private def runSubProcess(process: String): Try[Any] = {

        implicit val logger = containingDocument.getIndentedLogger("process")

        // Parse
        val parsedProcess = ProcessParser.parseProcess(processStackDyn.value.get.scope, process)

        if (parsedProcess.action.isEmpty) {
            debug("empty process, canceling process")
            Success(())
        } else {

            // Position of the action in the process (ignoring the combinators)
            var programCounter = 0

            def runAction(action: ActionAst) =
                withDebug("running action", Seq("action" → action.toString)) {
                    // Push and pop the stack frame (for suspend/resume)
                    withStackFrame(process, programCounter) {
                        AllAllowedActions.get(action.name) getOrElse ((_: ActionParams) ⇒ tryProcess(Map(Some("name") → action.name))) apply action.params
                    }
                }

            // Interpret process recursively
            @tailrec def nextGroup(tried: Try[Any], groups: Iterator[PairAst]): Try[Any] =
                if (groups.hasNext) {
                    val PairAst(nextCombinator, nextAction) = groups.next()
                    programCounter += 1

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
                                    case NonFatal(t) ⇒
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
        Try(params.get(Some("name")) getOrElse params(None)) map (rawProcessByName(processStackDyn.value.get.scope, _)) flatMap runSubProcess

    // Suspend the process
    def trySuspend(params: ActionParams): Try[Any] = Try {
        saveProcess(serializeContinuation)
        Success()
    } flatMap
        (_ ⇒ trySuccess(Map()))

    // Resume a process
    def tryResume(params: ActionParams): Try[Any] = {
        val process = readSavedProcess
        saveProcess("")
        runSubProcess(process)
    }

    // Abort a suspended process
    def tryAbort(params: ActionParams): Try[Any] =
        Try(saveProcess(""))

    // Don't do anything
    def tryNOP(params: ActionParams): Try[Any] =
        Success()

    // NOTE: Clear the PDF URL *before* the process, because if we clear it after, it will be already cleared during the
    // second pass of a two-pass submission.
    // TODO: Delete temp file if any.
    def beforeProcess(): Try[Any] = Try(setvalue(pdfURLInstanceRootElement, ""))
    def afterProcess():  Try[Any] = Try(())

    // Legacy: build "workflow-send" process based on properties
    private def buildProcessFromLegacyProperties(buttonName: String)(implicit p: FormRunnerParams) = {

        implicit val logger = containingDocument.getIndentedLogger("process")

        def booleanPropertySet(name: String) = booleanFormRunnerProperty(name)
        def stringPropertySet (name: String) = formRunnerProperty(name) flatMap nonEmptyOrNone isDefined

        buttonName match {
            case "workflow-send" ⇒
                val isLegacySendEmail       = booleanPropertySet("oxf.fr.detail.send.email")
                val isLegacyNavigateSuccess = stringPropertySet("oxf.fr.detail.send.success.uri")
                val isLegacyNavigateError   = stringPropertySet("oxf.fr.detail.send.error.uri")

                val buffer = ListBuffer[String]()

                buffer += "require-uploads"
                buffer += ThenCombinator.name
                buffer += "require-valid"
                buffer += ThenCombinator.name
                buffer += "save"
                buffer += ThenCombinator.name
                buffer += """success-message("save-success")"""

                if (isLegacySendEmail) {
                    buffer += ThenCombinator.name
                    buffer += "email"
                }

                // TODO: Pass `content = "pdf-url"` if isLegacyCreatePDF. Requires better parsing of process arguments.
                //def isLegacyCreatePDF = isLegacyNavigateSuccess && booleanPropertySet("oxf.fr.detail.send.pdf")

                // Workaround is to change config from oxf.fr.detail.send.pdf = true to oxf.fr.detail.send.success.content = "pdf-url"
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

trait XFormsActions {
    import SimpleProcess._

    def AllowedXFormsActions = Map[String, Action](
        "xf:send"     → tryXFormsSend,
        "xf:dispatch" → tryXFormsDispatch,
        "xf:show"     → tryShowDialog
    )

    def tryXFormsSend(params: ActionParams): Try[Any] =
        Try {
            val submission = params.get(Some("submission")) orElse params.get(None)
            submission foreach (sendThrowOnError(_))
        }

    def tryXFormsDispatch(params: ActionParams): Try[Any] =
        Try {
            val eventName = params.get(Some("name")) orElse params.get(None)
            eventName foreach (dispatch(_, "fr-form-model"))
        }

    def tryShowDialog(params: ActionParams): Try[Any] =
        Try {
            val dialogName = params.get(Some("dialog")) orElse params.get(None)
            dialogName foreach (show(_))
        }
}

trait FormRunnerActions {

    import SimpleProcess._

    def AllowedFormRunnerActions = Map[String, Action](
        "pending-uploads"  → tryPendingUploads,
        "validate"         → tryValidate,
        "save"             → trySaveAttachmentsAndData,
        "success-message"  → trySuccessMessage,
        "error-message"    → tryErrorMessage,
        "email"            → trySendEmail,
        "send"             → trySend,
        "navigate"         → tryNavigate,
        "review"           → tryNavigateToReview,
        "edit"             → tryNavigateToEdit,
        "summary"          → tryNavigateToSummary,
        "visit-all"        → tryVisitAll,
        "unvisit-all"      → tryUnvisitAll,
        "expand-all"       → tryExpandSections,
        "collapse-all"     → tryCollapseSections,
        "result-dialog"    → tryShowResultDialog,
        "captcha"          → tryCaptcha,
        "wizard-prev"      → tryWizardPrev,
        "wizard-next"      → tryWizardNext
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
            val level = params.get(Some("level")) orElse params.get(None) map LevelByName getOrElse ErrorLevel

            if (countValidationsByLevel(level) > 0)
                throw new OXFException(s"Data has failed constraints for level ${level.name}")
        }

    def trySaveAttachmentsAndData(params: ActionParams): Try[Any] =
        Try {
            val FormRunnerParams(app, form, Some(document), _) = FormRunnerParams()
            val isDraft = params.get(None).exists(_ == "draft")

            // Notify that the data is about to be saved
            dispatch(name = "fr-data-save-prepare", targetId = "fr-form-model")

            // Save
            val (beforeURLs, afterURLs) = putWithAttachments(
                data              = formInstance.root,
                toBaseURI         = "", // local save
                fromBasePath      = createFormDataBasePath(app, form, ! isDraft, document),
                toBasePath        = createFormDataBasePath(app, form, isDraft, document),
                filename          = "data.xml",
                commonQueryString = s"valid=$dataValid",
                forceAttachments  = false
            )

            // If we were in new mode, now we must be in edit mode
            setvalue(parametersInstance.rootElement \ "mode", "edit")

            // HACK: Force this before cleaning the status because we do a setvalue just before calling the submission
            recalculate("fr-persistence-model")
            refresh("fr-persistence-model")

            // Mark data clean
            val persistenceInstanceRoot = topLevelInstance("fr-persistence-model", "fr-persistence-instance").get.rootElement
            val saveStatus = if (isDraft) Seq() else persistenceInstanceRoot \ "data-status"
            val autoSaveStatus = persistenceInstanceRoot \ "autosave" \ "status"
            (saveStatus ++ autoSaveStatus) foreach (setvalue(_, "clean"))

            // Notify that the data is saved
            dispatch(name = "fr-data-save-done", targetId = "fr-form-model", properties = Map(
                "before-urls" → Some(beforeURLs),
                "after-urls"  → Some(afterURLs)
            ))
        }

    private def messageFromResourceOrInline(params: ActionParams) = {
        def resourceKey  = params.get(Some("resource")) orElse params.get(None)
        def fromResource = resourceKey map (k ⇒ currentFRResources \ "detail" \ "messages" \ k stringValue)
        def fromInline   = params.get(Some("message"))

        fromResource orElse fromInline get
    }

    def trySuccessMessage(params: ActionParams): Try[Any] =
        Try(FormRunner.successMessage(messageFromResourceOrInline(params)))

    def tryErrorMessage(params: ActionParams): Try[Any] =
        Try(FormRunner.errorMessage(messageFromResourceOrInline(params)))

    // TODO: Use xf:show("fr-submission-result-dialog")
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

    def trySendEmail(params: ActionParams): Try[Any] =
        Try {
            // NOTE: As of 2013-05-15, email-form.xpl recreates the PDF anyway, which is wasteful
//            implicit val formRunnerParams = FormRunnerParams()
//            if (booleanFormRunnerProperty("oxf.fr.email.attach-pdf"))
//                tryCreatePDFIfNeeded(Map()).get

            sendThrowOnError("fr-email-service-submission")
        }

    def trySend(params: ActionParams): Try[Any] =
        Try {
            val prefix = params.get(Some("property")) getOrElse params(None)

            implicit val formRunnerParams @ FormRunnerParams(app, form, document, _) = FormRunnerParams()

            // Defaults except for "uri"
            val Defaults = Map(
                "method"   → "post",
                "prune"    → "true",
                "annotate" → "",
                "replace"  → "none",
                "content"  → "xml"
            )

            val propertiesAsPairs =
                Seq("uri") ++ Defaults.keys map (key ⇒ key → (formRunnerProperty(prefix + "." + key) orElse Defaults.get(key)))

            // Append query parameters to the URL
            val withUpdatedURI =
                propertiesAsPairs map {
                    case ("uri", Some(uri)) ⇒ "uri" → Some(appendQueryString(uri, s"app=$app&form=$form&document=${document.get}&valid=$dataValid"))
                    case other              ⇒ other
                }

            val propertiesAsMap = withUpdatedURI.toMap

            // Create PDF if needed
            if (stringOptionToSet(propertiesAsMap("content")) exists (x ⇒  Set("pdf", "pdf-url")(x)))
                tryCreatePDFIfNeeded(Map()).get

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

    // Navigate the wizard to the previous page
    def tryWizardPrev(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-prev", targetId = "fr-view-wizard"))

    // Navigate the wizard to the next page
    def tryWizardNext(params: ActionParams): Try[Any] =
        Try (dispatch(name = "fr-next", targetId = "fr-view-wizard"))

    def pdfURLInstanceRootElement = topLevelInstance("fr-persistence-model", "fr-pdf-url-instance").get.rootElement

    def tryCreatePDFIfNeeded(params: ActionParams): Try[Any] =
        Try{
            // Only create if not available yet
            if (StringUtils.isBlank(pdfURLInstanceRootElement.stringValue))
                sendThrowOnError("fr-pdf-service-submission")
        }
}
