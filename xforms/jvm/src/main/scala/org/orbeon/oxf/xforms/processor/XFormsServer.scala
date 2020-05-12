/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.processor

import java.util.concurrent.Callable
import java.{util => ju}

import org.orbeon.dom
import org.orbeon.dom.io.XMLWriter
import org.orbeon.dom.{Document, Element}
import org.orbeon.exception.OrbeonFormatter
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.controller.PageFlowControllerProcessor
import org.orbeon.oxf.externalcontext.ExternalContext.Response
import org.orbeon.oxf.externalcontext.URLRewriter
import org.orbeon.oxf.http.{HttpMethod, SessionExpiredException, StatusCode}
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.processor.{ProcessorImpl, ProcessorInputOutputInfo, ProcessorOutput}
import org.orbeon.oxf.servlet.OrbeonXFormsFilter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{ContentTypes, IndentedLogger, LoggerFactory, NetUtils}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.XFormsContainingDocumentSupport._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XFormsUploadControl}
import org.orbeon.oxf.xforms.control.{Focus, XFormsControl}
import org.orbeon.oxf.xforms.event.{ClientEvents, XFormsEvents}
import org.orbeon.oxf.xforms.state.{RequestParameters, XFormsStateManager}
import org.orbeon.oxf.xforms.submission.{SubmissionResult, UrlType, XFormsModelSubmission}
import org.orbeon.oxf.xml.XMLReceiverSupport._
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler
import org.orbeon.xforms.Constants

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * The XForms Server processor handles client requests, including events, and either returns an XML
  * response, or returns a response through the ExternalContext.
  */
object XFormsServer {

  private val InputRequest = "request"

  import Private._

  val logger = LoggerFactory.createLogger(classOf[XFormsServer])

  /**
    * Output an Ajax response for the regular Ajax mode.
    *
    * @param beforeFocusedControlIdOpt control which had the focus before the updates, if any
    * @param requestDocument           incoming request document (for all events mode)
    * @param xmlReceiver               handler for the Ajax result
    */
  def outputAjaxResponse(
    containingDocument        : XFormsContainingDocument,
    eventFindings             : ClientEvents.EventsFindings,
    beforeFocusedControlIdOpt : Option[String],
    repeatHierarchyOpt        : Option[String],
    requestDocument           : Document,
    testOutputAllActions      : Boolean)(implicit
    xmlReceiver               : XMLReceiver,
    indentedLogger            : IndentedLogger
  ): Unit =
    withDocument {
      xmlReceiver.startPrefixMapping(XXFORMS_SHORT_PREFIX, XXFORMS_NAMESPACE_URI)
      withElement(localName = "event-response", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {

        // Compute server events
        val submissionServerEventsOpt: Option[String] = {

          val activeSubmissionOpt = containingDocument.getClientActiveSubmissionFirstPass
          val portletLoadOpt      = containingDocument.getNonJavaScriptLoadsToRun find (isPortletLoadMatch(containingDocument, _))

          (activeSubmissionOpt.isDefined || portletLoadOpt.isDefined) option {
            val eventsDocument = dom.Document()
            val eventsElement  = eventsDocument.addElement(XXFORMS_EVENTS_QNAME)

            def newEventElem(sourceControlId: String, eventName: String): Element =
              eventsElement.addElement(XXFORMS_EVENT_QNAME)
                .addAttribute("source-control-id", sourceControlId)
                .addAttribute("name", eventName)

            // Check for `xxforms-submit` event
            activeSubmissionOpt foreach { activeSubmission =>
              newEventElem(activeSubmission.getEffectiveId, XFormsEvents.XXFORMS_SUBMIT)
            }

            // Check for `xxforms-load` event (for portlet mode only!)
            portletLoadOpt foreach { load =>
              // We need to submit the event so that the portlet can load the new path
              // NOTE: don't care about the target for portlets
              newEventElem(Constants.DocumentId, XFormsEvents.XXFORMS_LOAD)
                .addAttribute("resource", load.resource)
            }

            // Encode events so that the client cannot send back arbitrary events
            XFormsUtils.encodeXML(eventsDocument, false)
          }
        }

        // Output dynamic state
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
          // Create a containing document in the initial state
          val initialContainingDocumentOpt =
            eventFindings.allEvents option {
              // NOTE: Document is removed from cache if it was found there. This may or may not be desirable.
              // Set disableUpdates = true so that we don't needlessly try to copy the controls tree. Also addresses:
              // #54: "Browser back causes server exception" https://github.com/orbeon/orbeon-forms/issues/54
              XFormsStateManager.createDocumentFromStore(
                extractParameters(requestDocument, isInitialState = true),
                isInitialState = true,
                disableUpdates = true
              )
            }

          withElement(localName = "action", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {

            val controls = containingDocument.getControls

            // Output new controls values and associated information
            withElement(localName = "control-values", prefix = XXFORMS_SHORT_PREFIX, uri = XXFORMS_NAMESPACE_URI) {
              initialContainingDocumentOpt match {
                case Some(initialContainingDocument) =>
                  // All events
                  // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                  val currentControlTree = controls.getCurrentControlTree
                  val initialControlTree = initialContainingDocument.getControls.getCurrentControlTree
                  // Make sure all xxf:dynamic will send full updates during control comparison
                  // Usually, xxf:dynamic records structural changes at each update. Here, we don't really
                  // know whether there were any, so we safely force structural changes. This ensures that the
                  // client will have all the necessary markup, and also prevents the comparator from choking when
                  // comparing incompatible trees.
                  for (e <- containingDocument.getStaticOps.controlsByName("dynamic"))
                    containingDocument.addControlStructuralChange(e.prefixedId)

                  diffControls(
                    containingDocument             = containingDocument,
                    state1                         = initialControlTree.children,
                    state2                         = currentControlTree.children,
                    valueChangeControlIdsAndValues = Map.empty,
                    isTestMode                     = testOutputAllActions
                  )
                case None if testOutputAllActions || containingDocument.isDirtySinceLastRequest =>
                  val currentControlTree = controls.getCurrentControlTree
                  diffControls(
                    containingDocument             = containingDocument,
                    state1                         = controls.getInitialControlTree.children,
                    state2                         = currentControlTree.children,
                    valueChangeControlIdsAndValues = eventFindings.valueChangeControlIdsAndValues,
                    isTestMode                     = testOutputAllActions
                  )
                case _ => // NOP
              }
            }

            // Add repeat hierarchy update if needed
            // https://github.com/orbeon/orbeon-forms/issues/2891
            repeatHierarchyOpt foreach { repeatHierarchy =>
              val newRepeatHierarchy = containingDocument.getStaticOps.getRepeatHierarchyString(containingDocument.getContainerNamespace)
              if (repeatHierarchy != newRepeatHierarchy) {
                element(
                  localName = "repeat-hierarchy",
                  prefix    = XXFORMS_SHORT_PREFIX,
                  uri       = XXFORMS_NAMESPACE_URI,
                  text      = XFormsUtils.escapeJavaScript(newRepeatHierarchy)
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

            // Output server events

            submissionServerEventsOpt foreach { submissionServerEvents =>
              element(
                localName = XXFORMS_SERVER_EVENTS_QNAME.localName,
                prefix    = XXFORMS_SHORT_PREFIX,
                uri       = XXFORMS_NAMESPACE_URI,
                text      = submissionServerEvents
              )
            }

            val delayedEvents = containingDocument.delayedEvents
            if (delayedEvents.nonEmpty) {
              val currentTime = System.currentTimeMillis
              for (delayedEvent <- delayedEvents)
                delayedEvent.writeAsSAX(currentTime)
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
              val afterFocusedControlOpt = containingDocument.getControls.getFocusedControl

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
                  if (containingDocument.getControls.getCurrentControlTree.findControl(beforeFocusEffectiveId) exists (c => ! Focus.isHidden(c)))
                    outputFocusInfo(containingDocument, focus = false, beforeFocusEffectiveId)
                case (_, Some(afterFocusEffectiveId)) if afterFocusEffectiveIdOpt != beforeFocusEffectiveIdOpt =>
                  // There is a focused control and it is different from the focus as known by the client
                  outputFocusInfo(containingDocument, focus = true, afterFocusEffectiveId)
                case _ =>
                  // Nothing to notify
              }
            }

            // Output help instruction
            containingDocument.getClientHelpControlEffectiveId foreach { helpControlEffectiveId =>
              outputHelpInfo(containingDocument, helpControlEffectiveId)
            }

            // Check if we need to tell the client to perform a form submission
            if (submissionServerEventsOpt.isDefined)
              outputSubmissionInfo(
                containingDocument.getClientActiveSubmissionFirstPass,
                containingDocument.isPortletContainer || containingDocument.isEmbedded,
                NetUtils.getExternalContext.getResponse // would be better to pass this to `outputAjaxResponse`
              )

            // Non-`javascript:` loads only
            containingDocument.getNonJavaScriptLoadsToRun filterNot (isPortletLoadMatch(containingDocument, _)) foreach
              (load => outputLoad(containingDocument, load))
          }
        }

        // Output errors
        val errors = containingDocument.getServerErrors
        if (errors.nonEmpty)
          XFormsError.outputAjaxErrors(errors)
      }
      xmlReceiver.endPrefixMapping(XXFORMS_SHORT_PREFIX)
    }

  def extractParameters(request: Document, isInitialState: Boolean): RequestParameters = {

    val uuid = XFormsStateManager.getRequestUUID(request) ensuring (_ ne null)

    val sequenceElement =
      request.getRootElement.element(XXFORMS_SEQUENCE_QNAME) ensuring (_ ne null)

    val sequenceOpt =
      sequenceElement.getTextTrim.trimAllToOpt map (_.toLong)

    val encodedStaticStateOpt =
      Option(request.getRootElement.element(XXFORMS_STATIC_STATE_QNAME)) flatMap
        (_.getTextTrim.trimAllToOpt)

    val qName =
      if (isInitialState)
        XXFORMS_INITIAL_DYNAMIC_STATE_QNAME
    else
        XXFORMS_DYNAMIC_STATE_QNAME

    val encodedDynamicStateOpt =
      Option(request.getRootElement.element(qName)) flatMap
        (_.getTextTrim.trimAllToOpt)

    RequestParameters(uuid, sequenceOpt, encodedStaticStateOpt, encodedDynamicStateOpt)
  }

  private object Private {

    def isPortletLoadMatch(containingDocument: XFormsContainingDocument, load: Load): Boolean =
      containingDocument.isPortletContainer    &&
      load.isReplace                           &&
      ! NetUtils.urlHasProtocol(load.resource) &&
      load.urlType != UrlType.Resource

    def diffControls(
      containingDocument             : XFormsContainingDocument,
      state1                         : Seq[XFormsControl],
      state2                         : Seq[XFormsControl],
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
      activeSubmissionOpt : Option[XFormsModelSubmission],
      isPortletContainer  : Boolean,
      response            : Response)(implicit
      receiver            : XMLReceiver
    ): Unit = {
      // `activeSubmissionOpt` can be `None` when we are running as a portlet and handling an `<xf:load>`, which
      // when executed from within a portlet is ran as very much like the `replace="all"` submissions.

      val showProgressAtt =
        activeSubmissionOpt exists (!_.getActiveSubmissionParameters.xxfShowProgress) list ("show-progress" -> "false")

      val targetAtt =
        activeSubmissionOpt flatMap (_.getActiveSubmissionParameters.xxfTargetOpt) map ("target" -> _) toList

      val actionAtt =
        isPortletContainer list {

          val SubmitUrl = XFORMS_SERVER_SUBMIT

          val actionUrl =
            if (activeSubmissionOpt exists (_.getActiveSubmissionParameters.resolvedIsResponseResourceType))
              response.rewriteResourceURL(SubmitUrl, URLRewriter.REWRITE_MODE_ABSOLUTE_NO_CONTEXT) // NOTE: mode ignored in portlet mode
            else
              response.rewriteActionURL(SubmitUrl)

          "action" -> actionUrl
        }

      // Signal that we want a POST to the XForms server
      element(
        localName = "submission",
        prefix    = XXFORMS_SHORT_PREFIX,
        uri       = XXFORMS_NAMESPACE_URI,
        atts      = ("method" -> HttpMethod.POST.entryName) :: showProgressAtt ::: targetAtt ::: actionAtt
      )
    }

    def outputMessagesInfo(
      messages    : Seq[Message])(implicit
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
          "target-id"   -> XFormsUtils.namespaceId(doc, scriptInvocation.targetEffectiveId),
          "observer-id" -> XFormsUtils.namespaceId(doc, scriptInvocation.observerEffectiveId)
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
        atts      = List("control-id" -> XFormsUtils.namespaceId(containingDocument, focusControlEffectiveId))
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
        atts      = List("control-id" -> XFormsUtils.namespaceId(containingDocument, helpControlEffectiveId))
      )
  }
}

class XFormsServer extends ProcessorImpl {

  self =>

  import XFormsServer._

  addInputInfo(new ProcessorInputOutputInfo(XFormsServer.InputRequest))

  // Case where an XML response must be generated.
  override def createOutput(outputName: String): ProcessorOutput = {
    val output = new ProcessorOutputImpl(self, outputName) {
      override def readImpl(pipelineContext: PipelineContext, xmlReceiver: XMLReceiver): Unit = {
        try {
          doIt(pipelineContext, Some(xmlReceiver))
        } catch {
          case e: SessionExpiredException =>
            LifecycleLogger.eventAssumingRequest("xforms", e.message, Nil)
            // Don't log whole exception
            XFormsServer.logger.info(e.message)
            ClientEvents.errorDocument(e.message, e.code)(xmlReceiver)
          case NonFatal(t) =>
            XFormsServer.logger.error(OrbeonFormatter.format(t))
            ClientEvents.errorDocument(OrbeonFormatter.message(t), StatusCode.InternalServerError)(xmlReceiver)
        }
      }
    }
    addOutput(outputName, output)
    output
  }

  // Case where the response is generated through the ExternalContext (submission with `replace="all"`).
  override def start(pipelineContext: PipelineContext): Unit =
    doIt(pipelineContext, None)

  private def doIt(pipelineContext: PipelineContext, xmlReceiverOpt: Option[XMLReceiver]): Unit = {

    // Use request input provided by client
    val requestDocument = readInputAsOrbeonDom(pipelineContext, XFormsServer.InputRequest)
    val externalContext = NetUtils.getExternalContext
    val request = externalContext.getRequest

    // It's not possible to handle a form update without an existing session. We depend on this to check the UUID,
    // to get the lock, and (except for client state) to retrieve form state.
    //
    // NOTE: We should test this at the beginning of this method, but calling readInputAsOrbeonDom() in unit tests
    // can cause the side effect to create the session, so doing so without changing some tests doesn't work.
    ClientEvents.assertSessionExists()

    // Logger used for heartbeat and request/response
    implicit val indentedLogger = Loggers.getIndentedLogger("server")

    val logRequestResponse = XFormsProperties.getDebugLogging.contains("server-body")

    if (logRequestResponse)
      debug("ajax request", List("body" -> requestDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat)))

    // Get action
    val actionElement = requestDocument.getRootElement.element(XXFORMS_ACTION_QNAME)

    // Quick return for heartbeat and upload progress if those events are alone -> we don't need to access the XForms document
    // NOTE: If we don't have a receiver, this means that we are in the second pass of a submission with
    // replace="all". In this case, only server events are provided.
    val remainingClientEvents =
      xmlReceiverOpt match {
        case Some(xmlReceiver) =>

          val remainingClientEvents =
            ClientEvents.handleQuickReturnEvents(
              xmlReceiver,
              request,
              requestDocument,
              logRequestResponse,
              ClientEvents.extractLocalEvents(actionElement)
            )

          if (remainingClientEvents.isEmpty)
            return

          remainingClientEvents
        case None =>
          ClientEvents.extractLocalEvents(actionElement)
      }

    val isAjaxRequest =
      request.getMethod == HttpMethod.POST &&
      ContentTypes.isXMLContentType(request.getContentType)

    val ignoreSequence = ! isAjaxRequest

    // Get files if any (those come from xforms-server-submit.xpl upon submission)
    val filesElement = requestDocument.getRootElement.element(XXFORMS_FILES_QNAME)

    // Gather server events containers if any
    val serverEventsElements = ClientEvents.extractServerEventsElements(requestDocument.getRootElement)

    // Find an output stream for xf:submission[@replace = 'all']
    val response = PipelineResponse.getResponse(xmlReceiverOpt.orNull, externalContext)

    // The following throws if the session has expired
    val parameters = extractParameters(requestDocument, isInitialState = false)

    // We don't wait on the lock for an Ajax request. But for a simulated request on GET, we do wait. See:
    // - https://github.com/orbeon/orbeon-forms/issues/2071
    // - https://github.com/orbeon/orbeon-forms/issues/1984
    // This throws if the lock is not found (UUID is not in the session OR the session doesn't exist)
    val lockResult: Try[Option[Callable[SubmissionResult]]] =
      withLock(parameters, if (isAjaxRequest) 0L else XFormsProperties.getAjaxTimeout) {
        case Some(containingDocument) =>

          val expectedSequenceNumber = containingDocument.getSequence
          if (ignoreSequence || (parameters.sequenceOpt contains expectedSequenceNumber)) {
            // We are good: process request and produce new sequence number
            try {

              // State to set before running events
              locally {
                // Set URL rewriter resource path information based on information in static state
                if (containingDocument.getVersionedPathMatchers != null && containingDocument.getVersionedPathMatchers.size > 0) {
                  // Don't override existing matchers if any (e.g. case of oxf:xforms-to-xhtml and oxf:xforms-submission
                  // processor running in same pipeline)
                  pipelineContext.setAttribute(PageFlowControllerProcessor.PathMatchers, containingDocument.getVersionedPathMatchers)
                }

                // Set deployment mode into request (useful for epilogue)
                request.getAttributesMap.put(OrbeonXFormsFilter.RendererDeploymentAttributeName, containingDocument.getDeploymentType.entryName)
              }

              // NOTE: As of 2010-12, background uploads in script mode are handled in xforms-server.xpl. In
              // most cases should get files here only in noscript mode, but there is a chance in script mode in
              // a 2-pass submission that some files could make it here as well.
              val beforeFocusedControlIdOpt = containingDocument.getControls.getFocusedControl map (_.effectiveId)

              val beforeRepeatHierarchyOpt =
                containingDocument.getStaticOps.controlsByName("dynamic").nonEmpty option
                  containingDocument.getStaticOps.getRepeatHierarchyString(containingDocument.getContainerNamespace)

              // Run events if any
              val eventsFindingsOpt = {

                val hasEvents = remainingClientEvents.nonEmpty || serverEventsElements.nonEmpty
                val hasFiles  = XFormsUploadControl.hasSubmittedFiles(filesElement)

                if (hasEvents || hasFiles) {
                  // Scope the containing document for the XForms API
                  XFormsAPI.withContainingDocument(containingDocument) {

                    val eventsIndentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
                    withDebug("handling external events and/or uploaded files") {

                      // Start external events
                      containingDocument.beforeExternalEvents(response)
                      // Handle uploaded files for noscript if any
                      if (hasFiles) {
                        debug("handling uploaded files")(eventsIndentedLogger)
                        XFormsUploadControl.handleSubmittedFiles(containingDocument, filesElement)
                      }

                      // Dispatch the events
                      val result =
                        hasEvents option
                          ClientEvents.processEvents(containingDocument, remainingClientEvents, serverEventsElements)

                      // End external events
                      containingDocument.afterExternalEvents()

                      result
                    } (eventsIndentedLogger)
                  }
                } else
                  None
              }

              Success(
                withUpdateResponse(containingDocument, ignoreSequence) {
                  containingDocument.getReplaceAllCallable match {
                    case None =>
                      xmlReceiverOpt match {
                        case Some(xmlReceiver) =>

                          // Create resulting document if there is a receiver
                          if (containingDocument.isGotSubmissionRedirect) {
                            // Redirect already sent
                            // Output null document so that rest of pipeline doesn't fail and no further processing takes place
                            debug("handling submission with `replace=\"all\"` with redirect")
                            SAXUtils.streamNullDocument(xmlReceiver)
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

                              // Prepare and/or output response
                              XFormsServer.outputAjaxResponse(
                                containingDocument        = containingDocument,
                                eventFindings             = eventsFindingsOpt getOrElse ClientEvents.EmptyEventsFindings,
                                beforeFocusedControlIdOpt = beforeFocusedControlIdOpt,
                                repeatHierarchyOpt        = beforeRepeatHierarchyOpt,
                                requestDocument           = requestDocument,
                                testOutputAllActions      = false)(
                                xmlReceiver               = responseReceiver,
                                indentedLogger            = indentedLogger
                              )

                              // Store response in to document
                              containingDocument.rememberLastAjaxResponse(responseStore)

                              // Actually output response
                              // If there is an error, we do not
                              try {
                                responseStore.replay(xmlReceiver)
                              } catch {
                                case NonFatal(t) =>
                                  indentedLogger.logDebug("retry", "got exception while sending response; ignoring and expecting client to retry", t)
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
                    case someCallable =>
                      // Check if there is a submission with replace="all" that needs processing
                      someCallable
                  }
                }
              )
            } catch {
              case NonFatal(t) =>
                // Log body of Ajax request if needed
                if (XFormsProperties.getErrorLogging.contains("server-body"))
                  indentedLogger.logError("", "error processing Ajax update", "request", requestDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))

                // Don't keep the document around
                throw new OXFException(t)
            }
          } else if (parameters.sequenceOpt contains (expectedSequenceNumber - 1)) {
            // This is a request for the previous response
            // Whatever happens when replaying, keep the document around so return a value
            Try {
              assert(containingDocument.getLastAjaxResponse ne null)

              val xmlReceiver = xmlReceiverOpt getOrElse (throw new IllegalStateException)

              LifecycleLogger.eventAssumingRequest("xforms", "replay response", List("uuid" -> parameters.uuid))
              withDebug("replaying previous Ajax response") {
                try {
                  // Write last response
                  containingDocument.getLastAjaxResponse.replay(xmlReceiver)
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
      case Success(Some(replaceAllCallable)) =>
        // Check and run submission with `replace="all"`
        // - Do this outside the synchronized block, so that if this takes time, subsequent Ajax requests can still
        //   hit the document.
        // - No need to output a null document here, `xmlReceiver` is absent anyway.
        XFormsModelSubmission.runDeferredSubmission(replaceAllCallable, response)
      case Success(None) =>
      case Failure(t)    => throw t
    }
  }
}