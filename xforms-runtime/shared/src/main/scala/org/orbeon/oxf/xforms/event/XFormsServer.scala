package org.orbeon.oxf.xforms.event

import java.{util => ju}
import cats.Eval
import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.{SessionExpiredException, StatusCode}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.{debug, debugResults, error, info, withDebug}
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport.{withLock, withUpdateResponse}
import org.orbeon.oxf.xforms.{ScriptInvocation, XFormsContainingDocument, XFormsError, XFormsGlobalProperties}
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.{Focus, XFormsControl}
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl
import org.orbeon.oxf.xforms.processor.ControlsComparator
import org.orbeon.oxf.xforms.state.{RequestParameters, XFormsStateManager}
import org.orbeon.oxf.xforms.submission.{ConnectResult, XFormsModelSubmissionSupport}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml.{SAXStore, TeeXMLReceiver, XMLReceiver, XMLReceiverHelper}
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.xforms.{DelayedEvent, EventNames, Load, Message, XFormsCrossPlatformSupport}
import org.orbeon.xforms.XFormsNames.{XXFORMS_NAMESPACE_URI, XXFORMS_SHORT_PREFIX}
import org.orbeon.xforms.rpc.{WireAjaxEvent, WireAjaxEventWithTarget, WireAjaxEventWithoutTarget}

import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal


object XFormsServer {

  import Private._

  def processEvents(
    logRequestResponse      : Boolean,
    requestParameters       : RequestParameters,
    requestParametersForAll : => RequestParameters,
    extractedEvents         : List[WireAjaxEvent],
    xmlReceiverOpt          : Option[XMLReceiver],
    responseForReplaceAll   : ExternalContext.Response,
    beforeProcessRequest    : XFormsContainingDocument => Unit,
    extractWireEvents       : String => List[WireAjaxEvent]
  )(implicit
    indentedLogger          : IndentedLogger,
    externalContext         : ExternalContext
  ): Unit = {

    val remainingClientEvents = {

      xmlReceiverOpt match {
        case Some(xmlReceiver) =>

          val remainingClientEvents =
            ClientEvents.handleQuickReturnEvents(
              xmlReceiver,
              requestParameters.uuid,
              logRequestResponse,
              extractedEvents
            )

          if (remainingClientEvents.isEmpty)
            return

          remainingClientEvents
        case None =>
          extractedEvents collect { case e: WireAjaxEventWithTarget => e }
      }
    }

    // Was the following, but this is not true in tests right now:
    // `request.getMethod == HttpMethod.POST && ContentTypes.isXMLContentType(request.getContentType)`
    val isAjaxRequest = xmlReceiverOpt.isDefined

    // We don't wait on the lock for an Ajax request. But for a simulated request on GET, we do wait. See:
    // - https://github.com/orbeon/orbeon-forms/issues/2071
    // - https://github.com/orbeon/orbeon-forms/issues/1984
    // This throws if the lock is not found (UUID is not in the session OR the session doesn't exist)
    val lockResult: Try[Try[Option[Eval[ConnectResult]]]] =
      withLock(requestParameters, if (isAjaxRequest) 0L else XFormsGlobalProperties.getAjaxTimeout) {
        case Some(containingDocument) =>

          val ignoreSequenceNumber   = ! isAjaxRequest
          val expectedSequenceNumber = containingDocument.sequence

          if (ignoreSequenceNumber || (requestParameters.sequenceOpt contains expectedSequenceNumber)) {
            // We are good: process request and produce new sequence number
            try {

              beforeProcessRequest(containingDocument)

              // NOTE: As of 2010-12, background uploads in script mode are handled in xforms-server.xpl. In
              // most cases should get files here only in noscript mode, but there is a chance in script mode in
              // a 2-pass submission that some files could make it here as well.
              val beforeFocusedControlIdOpt = containingDocument.controls.getFocusedControl map (_.effectiveId)

              val beforeRepeatHierarchyOpt =
                containingDocument.staticOps.controlsByName("dynamic").nonEmpty option
                  containingDocument.staticOps.getRepeatHierarchyString(containingDocument.getContainerNamespace)

              // Run events if any
              val eventsFindingsOpt = {

                val eventsIndentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)

                if (isAjaxRequest) {
                  remainingClientEvents.nonEmpty option {
                    // Scope the containing document for the XForms API
                    XFormsAPI.withContainingDocument(containingDocument) {

                      withDebug("handling external events") {

                        // Start external events
                        containingDocument.beforeExternalEvents(responseForReplaceAll , isAjaxRequest)

                        // Dispatch the events

                        // Flatten client and server events
                        // NOTE: Only the upload now requires server events
                        val allClientAndServerEvents =
                          remainingClientEvents flatMap {
                            case e @ WireAjaxEventWithoutTarget(EventNames.XXFormsServerEvents, _) =>
                              e.valueOpt.toList flatMap (v =>
                                extractWireEvents(v)
                              ) collect {
                                case e: WireAjaxEventWithTarget => e -> true
                              }
                            case e: WireAjaxEventWithTarget =>
                              List(e -> false)
                            case _ =>
                              Nil
                          }

                        val result =
                          ClientEvents.processEvents(containingDocument, allClientAndServerEvents)

                        // End external events
                        // The following will process async submission and delayed events
                        containingDocument.afterExternalEvents(isAjaxRequest)

                        result
                      } (eventsIndentedLogger)
                    }
                  }
                } else {
                  XFormsAPI.withContainingDocument(containingDocument) {
                    withDebug("handling two-pass submission event") {
                      containingDocument.beforeExternalEvents(responseForReplaceAll , isAjaxRequest)
                      containingDocument.afterExternalEvents(isAjaxRequest)
                      Some(ClientEvents.EmptyEventsFindings)
                    } (eventsIndentedLogger)
                  }
                }
              }

              Success(
                withUpdateResponse(containingDocument, ignoreSequenceNumber) {
                  containingDocument.getReplaceAllEval match {
                    case None =>
                      xmlReceiverOpt match {
                        case Some(xmlReceiver) =>

                          // Create resulting document if there is a receiver
                          if (containingDocument.isGotSubmissionRedirect) {
                            // Redirect already sent
                            // Output null document so that rest of pipeline doesn't fail and no further processing takes place
                            debug("handling submission with `replace=\"all\"` with redirect")
                            XMLReceiverHelper.streamNullDocument(xmlReceiver)
                          } else {
                            // This is an Ajax response
                            withDebug("handling regular Ajax response") {
                              // Hook-up debug content handler if we must log the response document
                              // Buffer for retries
                              val responseStore = new SAXStore
                              // Two receivers possible
                              val receivers = new ju.ArrayList[XMLReceiver]
                              receivers.add(responseStore)

                              // Debug output
                              val debugContentHandlerOpt =
                                logRequestResponse option {
                                  val result = new LocationSAXContentHandler
                                  receivers.add(result)
                                  result
                                }

                              val responseReceiver = new TeeXMLReceiver(receivers)

                              // Whether we got a request for all events
                              val allEvents = remainingClientEvents exists
                                (_.eventName == EventNames.XXFormsAllEventsRequired)

                              // Prepare and/or output response
                              outputAjaxResponse(
                                containingDocument        = containingDocument,
                                eventFindings             = eventsFindingsOpt getOrElse ClientEvents.EmptyEventsFindings,
                                allEvents                 = allEvents,
                                beforeFocusedControlIdOpt = beforeFocusedControlIdOpt,
                                repeatHierarchyOpt        = beforeRepeatHierarchyOpt,
                                requestParametersForAll   = requestParametersForAll,
                                testOutputAllActions      = false)(
                                xmlReceiver               = responseReceiver,
                                indentedLogger            = indentedLogger
                              )

                              // Store response in to document
                              containingDocument.rememberLastAjaxResponse(responseStore)

                              // Actually output response
                              try {
                                responseStore.replay(xmlReceiver)
                              } catch {
                                case NonFatal(t) =>
                                  debug("retry: got exception while sending response; ignoring and expecting client to retry") // `t: Throwable`
                              }

                              debugContentHandlerOpt foreach { debugContentHandler =>
                                debugResults(List("ajax response" -> debugContentHandler.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat)))
                              }
                            }
                          }
                        case None =>
                          // This is the second pass of a submission with replace="all". We ensure that the document is
                          // not modified.
                          debug("handling NOP response for submission with `replace=\"all\"`")
                      }
                      None
                    case evalOpt =>
                      // Check if there is a submission with `replace="all"` that needs processing
                      evalOpt
                  }
                }
              )
            } catch {
              case NonFatal(t) =>
                // Log body of Ajax request if needed
                if (XFormsGlobalProperties.getErrorLogging.contains("server-body"))
                  error("error processing Ajax update", List("parameters" -> requestParameters.toString, "events" -> extractedEvents.toString))

                // Don't keep the document around
                throw new OXFException(t)
            }
          } else if (requestParameters.sequenceOpt contains (expectedSequenceNumber - 1)) {
            // This is a request for the previous response
            // Whatever happens when replaying, keep the document around so return a value
            Try {
              assert(containingDocument.lastAjaxResponse.isDefined)

              val xmlReceiver = xmlReceiverOpt getOrElse (throw new IllegalStateException)

              LifecycleLogger.eventAssumingRequest("xforms", "replay response", List("uuid" -> requestParameters.uuid))
              withDebug("replaying previous Ajax response") {
                try {
                  containingDocument.lastAjaxResponse foreach (_.replay(xmlReceiver))
                  debugResults(List("success" -> "true"))
                } finally {
                  debugResults(List("success" -> "false"))
                }
              }
              None
            }
          } else {
            // This is not allowed to happen
            // Keep the document around but return an `Failure`
            Failure(throw new OXFException("Got unexpected request sequence number"))
          }
        case None =>
          // This is most likely the case of a retry if the initial request was long-running
          // See https://github.com/orbeon/orbeon-forms/issues/1984
          info("Ajax update lock timeout exceeded, returning error to client")

          // Using 503 based on http://stackoverflow.com/questions/17862015/http-statuscode-to-retry-same-request
          val xmlReceiver = xmlReceiverOpt getOrElse (throw new IllegalStateException)
          ClientEvents.errorResponse(StatusCode.ServiceUnavailable)(xmlReceiver)

          Success(None)
      }

    // Throw the exception if there was any
    lockResult match {
      case Failure(e: SessionExpiredException) => // from downstream `acquireDocumentLock`
        // See also `XFormsAssetServer`
        info(s"session not found while processing client events")
        // TODO: Unclear is this can happen for replace="all" where `xmlReceiverOpt == None`.
        val xmlReceiver = xmlReceiverOpt getOrElse (throw new IllegalStateException)
        ClientEvents.errorResponse(e.code)(xmlReceiver)
      case Failure(e) => // from downstream `acquireDocumentLock`
        // See also `XFormsAssetServer`
        info(s"error while processing client events: ${e.getMessage}")
        // TODO: Unclear is this can happen for replace="all" where `xmlReceiverOpt == None`.
        val xmlReceiver = xmlReceiverOpt getOrElse (throw new IllegalStateException)
        ClientEvents.errorResponse(StatusCode.InternalServerError)(xmlReceiver)
      case Success(Success(Some(replaceAllEval))) =>
        // Check and run submission with `replace="all"`
        // - Do this outside the synchronized block, so that if this takes time, subsequent Ajax requests can still
        //   hit the document.
        // - No need to output a null document here, `xmlReceiver` is absent anyway.
        XFormsModelSubmissionSupport.runDeferredSubmission(replaceAllEval, responseForReplaceAll)
      case Success(Success(None)) =>
      case Success(Failure(t))    => throw t
    }
  }

  // Output an Ajax response for the regular Ajax mode.
  def outputAjaxResponse(
    containingDocument        : XFormsContainingDocument,
    eventFindings             : ClientEvents.EventsFindings,
    allEvents                 : Boolean,
    beforeFocusedControlIdOpt : Option[String],
    repeatHierarchyOpt        : Option[String],
    requestParametersForAll   : => RequestParameters,
    testOutputAllActions      : Boolean)(implicit
    xmlReceiver               : XMLReceiver,
    indentedLogger            : IndentedLogger
  ): Unit = {

    // Create a containing document in the initial state if required
    allEvents option XFormsStateManager.createInitialDocumentFromStore(requestParametersForAll) match {
      case Some(None) =>
        info(s"document not found in store while computing all initialization events")
        ClientEvents.errorResponse(StatusCode.Forbidden) // status code debatable
      case initialContainingDocumentOptOpt =>
        withDocument {
          xmlReceiver.startPrefixMapping(XXFORMS_SHORT_PREFIX, XXFORMS_NAMESPACE_URI)
          withElement(localName = "event-response", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {

            // Output dynamic state if using client state handling
            XFormsStateManager.getClientEncodedDynamicState(containingDocument) foreach { dynamicState =>
              element(
                localName = "dynamic-state",
                prefix    = XXFORMS_SHORT_PREFIX,
                uri       = XXFORMS_NAMESPACE_URI,
                text      = dynamicState
              )
            }

            // Output action
            locally {
              withElement(localName = "action", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {

                val initialContainingDocumentOpt = initialContainingDocumentOptOpt.flatten
                val controls                     = containingDocument.controls

                // Output new controls values and associated information
                withElement(localName = "control-values", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {
                  initialContainingDocumentOpt match {
                    case Some(initialContainingDocument) =>
                      // All events
                      // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                      val currentControlTree = controls.getCurrentControlTree
                      val initialControlTree = initialContainingDocument.controls.getCurrentControlTree
                      // Make sure all xxf:dynamic will send full updates during control comparison
                      // Usually, xxf:dynamic records structural changes at each update. Here, we don't really
                      // know whether there were any, so we safely force structural changes. This ensures that the
                      // client will have all the necessary markup, and also prevents the comparator from choking when
                      // comparing incompatible trees.
                      for (e <- containingDocument.staticOps.controlsByName("dynamic"))
                        containingDocument.addControlStructuralChange(e.prefixedId)

                      diffControls(
                        containingDocument             = containingDocument,
                        state1                         = initialControlTree.children,
                        state2                         = currentControlTree.children,
                        valueChangeControlIdsAndValues = Map.empty,
                        isTestMode                     = testOutputAllActions
                      )

                    case None if testOutputAllActions || containingDocument.isDirtySinceLastRequest =>
                      diffControls(
                        containingDocument             = containingDocument,
                        state1                         = controls.getInitialControlTree.children,
                        state2                         = controls.getCurrentControlTree.children,
                        valueChangeControlIdsAndValues = eventFindings.valueChangeControlIdsAndValues,
                        isTestMode                     = testOutputAllActions
                      )
                    case _ => // NOP
                  }
                }

                // Add repeat hierarchy update if needed
                // https://github.com/orbeon/orbeon-forms/issues/2891
                repeatHierarchyOpt foreach { repeatHierarchy =>
                  val newRepeatHierarchy = containingDocument.staticOps.getRepeatHierarchyString(containingDocument.getContainerNamespace)
                  if (repeatHierarchy != newRepeatHierarchy) {
                    element(
                      localName = "repeat-hierarchy",
                      prefix    = XXFORMS_SHORT_PREFIX,
                      uri       = XXFORMS_NAMESPACE_URI,
                      text      = newRepeatHierarchy.escapeJavaScript
                    )
                  }
                }

                // Output index updates
                val ns = containingDocument.getContainerNamespace
                initialContainingDocumentOpt match {
                  case Some(initialContainingDocument) =>
                    // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                    diffIndexState(
                      ns,
                      XFormsRepeatControl.initialIndexes(initialContainingDocument),
                      XFormsRepeatControl.currentIndexes(containingDocument)
                    )
                  case None if testOutputAllActions || containingDocument.isDirtySinceLastRequest =>
                    diffIndexState(
                      ns,
                      controls.getInitialControlTree.initialRepeatIndexes,
                      XFormsRepeatControl.currentIndexes(containingDocument)
                    )
                  case _ => // NOP
                }

                // Issue an `xxforms-poll` if there are pending delayed events
                // In case there is a submission AND a poll needed, send both, except if we guess that there
                // is navigation within the same browsing context. It's just a heuristic to prevent sending
                // an Ajax request whose response might not succeed because the browsing context has navigated.
                if (! (containingDocument.findTwoPassSubmitEvent exists (_.browserTarget.isEmpty))) {
                  containingDocument.findEarliestPendingDelayedEvent foreach { event =>
                    outputPoll(event, System.currentTimeMillis)
                  }
                }

                // TODO: the following should be ordered in the order they were requested
                // Output messages to display
                val messages = containingDocument.getMessagesToRun
                if (messages.nonEmpty)
                  outputMessagesInfo(messages)

                // `javascript:` loads only and regular scripts
                containingDocument.getScriptsToRun foreach {
                  case Left(load)              => outputLoad(containingDocument, load)
                  case Right(scriptInvocation) => outputScriptInvocation(containingDocument, scriptInvocation)
                }

                // Output focus instruction
                locally {
                  val afterFocusedControlOpt = containingDocument.controls.getFocusedControl

                  // The focus as known by the client, as far as we know: either the focus sent by the client in the
                  // current request, or the focus information we kept since the previous request.
                  val beforeFocusEffectiveIdOpt =
                    eventFindings.clientFocusControlIdOpt match {
                      case Some(clientFocusControlIdOpt) => clientFocusControlIdOpt
                      case None                          => beforeFocusedControlIdOpt
                    }

                  val afterFocusEffectiveIdOpt =
                    afterFocusedControlOpt map (_.getEffectiveId)

                  (beforeFocusEffectiveIdOpt, afterFocusEffectiveIdOpt) match {
                    case (Some(beforeFocusEffectiveId), None) =>
                      // Focus removed: notify the client only if the control still exists AND is visible
                      // See https://github.com/orbeon/orbeon-forms/issues/4113
                      if (containingDocument.controls.getCurrentControlTree.findControl(beforeFocusEffectiveId) exists (c => ! Focus.isHidden(c)))
                        outputFocusInfo(containingDocument, focus = false, beforeFocusEffectiveId)
                    case (_, Some(afterFocusEffectiveId)) if afterFocusEffectiveIdOpt != beforeFocusEffectiveIdOpt =>
                      // There is a focused control and it is different from the focus as known by the client
                      outputFocusInfo(containingDocument, focus = true, afterFocusEffectiveId)
                    case _ =>
                      // Nothing to notify
                  }
                }

                containingDocument.getClientHelpControlEffectiveId foreach { helpControlEffectiveId =>
                  outputHelpInfo(containingDocument, helpControlEffectiveId)
                }

                containingDocument.findTwoPassSubmitEvent foreach { twoPassSubmitEvent =>
                  outputSubmissionInfo(twoPassSubmitEvent)
                }

                containingDocument.getNonJavaScriptLoadsToRun foreach { load =>
                  outputLoad(containingDocument, load)
                }
              }
            }

            // Output errors
            val errors = containingDocument.getServerErrors
            if (errors.nonEmpty)
              XFormsError.outputAjaxErrors(errors)
          }
          xmlReceiver.endPrefixMapping(XXFORMS_SHORT_PREFIX)
        }
    }
  }

  private object Private {

    def diffControls(
      containingDocument             : XFormsContainingDocument,
      state1                         : Iterable[XFormsControl],
      state2                         : Iterable[XFormsControl],
      valueChangeControlIdsAndValues : Map[String, String],
      isTestMode                     : Boolean)(implicit
      xmlReceiver                    : XMLReceiver,
      indentedLogger                 : IndentedLogger
    ): Unit =
      withDebug("computing differences") {
        XFormsAPI.withContainingDocument(containingDocument) { // scope because dynamic properties can cause lazy XPath evaluations

          val comparator = new ControlsComparator(
            containingDocument,
            valueChangeControlIdsAndValues,
            isTestMode
          )

          comparator.diffChildren(
            left             = if (isTestMode) Nil else state1, // in test mode, ignore first tree
            right            = state2,
            fullUpdateBuffer = None
          )
        }
      }

    def diffIndexState(
      ns                     : String,
      initialRepeatIdToIndex : collection.Map[String, Int],
      currentRepeatIdToIndex : collection.Map[String, Int])(implicit
      xmlReceiver            : XMLReceiver
    ): Unit =
      if (currentRepeatIdToIndex.nonEmpty) {
        var found = false
        for {
          (repeatId, newIndex) <- currentRepeatIdToIndex
          oldIndex             <- initialRepeatIdToIndex.get(repeatId) // may be None if there was no iteration
          if newIndex != oldIndex
        } locally {
            if (! found) {
              openElement(
                localName = "repeat-indexes",
                prefix    = XXFORMS_SHORT_PREFIX,
                uri       = XXFORMS_NAMESPACE_URI
              )
              found = true
            }
            // Make sure to namespace the id
            element(
              localName = "repeat-index",
              prefix    = XXFORMS_SHORT_PREFIX,
              uri       = XXFORMS_NAMESPACE_URI,
              atts      = List("id" -> (ns + repeatId), "new-index" -> newIndex.toString)
            )
        }

        if (found)
          closeElement(
            localName = "repeat-indexes",
            prefix    = XXFORMS_SHORT_PREFIX,
            uri       = XXFORMS_NAMESPACE_URI
          )
      }

    def outputSubmissionInfo(
      twoPassSubmitEvent : DelayedEvent)(implicit
      receiver           : XMLReceiver
    ): Unit = {

      val showProgressAtt =
        ! twoPassSubmitEvent.showProgress list ("show-progress" -> "false")

      val targetAtt =
         twoPassSubmitEvent.browserTarget.toList map ("target" -> _)

      val urlTypeAtt =
        "url-type" -> (
          if (twoPassSubmitEvent.isResponseResourceType)
            "resource"
          else
            "action"
        )

      element(
        localName = "submission",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      = showProgressAtt ::: targetAtt ::: urlTypeAtt :: Nil
      )
    }

    def outputPoll(
      delayedEvent : DelayedEvent,
      currentTime  : Long)(implicit
      xmlReceiver  : XMLReceiver
    ): Unit =
      element(
        localName = "poll",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      = delayedEvent.time.toList map (time => "delay" -> (time - currentTime).toString)
      )

    def outputMessagesInfo(
      messages    : Iterable[Message])(implicit
      xmlReceiver : XMLReceiver
    ): Unit =
      for (message <- messages)
        element(
          localName = "message",
          prefix    = XXFORMS_SHORT_PREFIX,
          uri       = XXFORMS_NAMESPACE_URI,
          atts      = List("level" -> message.level),
          text      = message.message
        )

    def outputLoad(
      doc         : XFormsContainingDocument,
      load        : Load)(implicit
      xmlReceiver : XMLReceiver)
    : Unit =
      element(
        localName = "load",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      =
          ("resource" -> load.resource)                          ::
          (load.target.toList map ("target" -> _))               :::
          ("show" -> (if (load.isReplace) "replace" else "new")) ::
          (! load.isShowProgress list ("show-progress" -> "false"))
      )

    def outputScriptInvocation(
      doc              : XFormsContainingDocument,
      scriptInvocation : ScriptInvocation)(implicit
      receiver         : XMLReceiver
    ): Unit =
      withElement(
        "script",
        prefix = XXFORMS_SHORT_PREFIX,
        uri    = XXFORMS_NAMESPACE_URI,
        atts   = List(
          "name"        -> scriptInvocation.script.shared.clientName,
          "target-id"   -> doc.namespaceId(scriptInvocation.targetEffectiveId),
          "observer-id" -> doc.namespaceId(scriptInvocation.observerEffectiveId)
        )
      ) {

        for (value <- scriptInvocation.paramValues) {
          element(
            "param",
            prefix = XXFORMS_SHORT_PREFIX,
            uri    = XXFORMS_NAMESPACE_URI,
            atts   = Nil,
            text   = value
          )
        }
      }

    def outputFocusInfo(
      containingDocument      : XFormsContainingDocument,
      focus                   : Boolean,
      focusControlEffectiveId : String)(implicit
      xmlReceiver             : XMLReceiver
    ): Unit =
      element(
        localName = if (focus) "focus" else "blur",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      = List("control-id" -> containingDocument.namespaceId(focusControlEffectiveId))
      )

    def outputHelpInfo(
      containingDocument     : XFormsContainingDocument,
      helpControlEffectiveId : String)(implicit
      xmlReceiver            : XMLReceiver
    ): Unit =
      element(
        localName = "help",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      = List("control-id" -> containingDocument.namespaceId(helpControlEffectiveId))
      )
  }
}
