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
package org.orbeon.oxf.xforms.processor;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.PageFlowControllerProcessor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsAPI;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.ClientEvents;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.state.AnnotatedTemplate;
import org.orbeon.oxf.xforms.state.XFormsStateLifecycle;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.submission.SubmissionResult;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.TeeXMLReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.SAXException;
import scala.Tuple3;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The XForms Server processor handles client requests, including events, and either returns an XML
 * response, or returns a response through the ExternalContext.
 */
public class XFormsServer extends ProcessorImpl {

    public static final Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    //private static final String OUTPUT_RESPONSE = "response"; // optional

    public static final Map<String, String> XFORMS_NAMESPACES = new HashMap<String, String>();
    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public XFormsServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST)); // optional
        //addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESPONSE)); // optional
    }

    /**
     * Case where an XML response must be generated.
     */
    @Override
    public ProcessorOutput createOutput(final String outputName) {
        final ProcessorOutput output = new ProcessorOutputImpl(XFormsServer.this, outputName) {
            public void readImpl(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {
                doIt(pipelineContext, xmlReceiver);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    /**
     * Case where the response is generated through the ExternalContext (submission with replace="all").
     */
    @Override
    public void start(PipelineContext pipelineContext) {
        doIt(pipelineContext, null);
    }

    private void doIt(final PipelineContext pipelineContext, XMLReceiver xmlReceiver) {

        final ExternalContext externalContext = NetUtils.getExternalContext();
        final ExternalContext.Request request = externalContext.getRequest();

        // Use request input provided by client
        final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

        // Request retry details
        final boolean isRetries = true;
        final long requestSequenceNumber = XFormsStateManager.getRequestSequence(requestDocument);

        final boolean isAjaxRequest = request.getMethod() != null && request.getMethod().equalsIgnoreCase("post") && XMLUtils.isXMLMediatype(NetUtils.getContentTypeMediaType(request.getContentType()));

        final boolean isIgnoreSequenceNumber = !isAjaxRequest;

        // Logger used for heartbeat and request/response
        final IndentedLogger indentedLogger = Loggers.getIndentedLogger("server");

        final boolean logRequestResponse = XFormsProperties.getDebugLogging().contains("server-body");
        if (logRequestResponse) {
            indentedLogger.logDebug("", "ajax request", "body", Dom4jUtils.domToPrettyString(requestDocument));
        }

        // Get action
        final Element actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

        // Get files if any (those come from xforms-server-submit.xpl upon submission)
        final Element filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

        // Gather client events if any
        final List<Element> clientEvents = (actionElement != null) ? Dom4jUtils.elements(actionElement, XFormsConstants.XXFORMS_EVENT_QNAME) : Collections.<Element>emptyList();

        // Hit session if it exists (it's probably not even necessary to do so)
        final ExternalContext.Session session = request.getSession(false);

        // Quick return for heartbeat and upload progress if those events are alone -> we don't need to access the XForms document
        if (clientEvents.size() == 1) {
            if (ClientEvents.doQuickReturnEvents(xmlReceiver, request, requestDocument, indentedLogger, logRequestResponse, clientEvents, session))
                return;
        }

        // Gather server events containers if any
        final List<Element> serverEventsElements = Dom4jUtils.elements(requestDocument.getRootElement(), XFormsConstants.XXFORMS_SERVER_EVENTS_QNAME);

        // Find an output stream for xforms:submission[@replace = 'all']
        final ExternalContext.Response response = XFormsToXHTML.getResponse(xmlReceiver, externalContext);

        // Get containing document from the incoming request
        // IMPORTANT: We now have a lock associated with the document
        final XFormsStateLifecycle.RequestParameters parameters = XFormsStateManager.instance().extractParameters(requestDocument, false);
        final XFormsContainingDocument containingDocument = XFormsStateManager.instance().beforeUpdate(parameters);
        boolean keepDocument = false;
        Callable<SubmissionResult> replaceAllCallable = null;
        try {
            final long expectedSequenceNumber = containingDocument.getSequence();
            if (isIgnoreSequenceNumber || requestSequenceNumber == expectedSequenceNumber) {
                // We are good: process request and produce new sequence number
                try {
                    // Run events if any
                    final boolean isNoscript = containingDocument.getStaticState().isNoscript();

                    // Set URL rewriter resource path information based on information in static state
                    if (containingDocument.getVersionedPathMatchers() != null && containingDocument.getVersionedPathMatchers().size() > 0) {
                        // Don't override existing matchers if any (e.g. case of oxf:xforms-to-xhtml and oxf:xforms-submission processor running in same pipeline)
                        pipelineContext.setAttribute(PageFlowControllerProcessor.PATH_MATCHERS, containingDocument.getVersionedPathMatchers());
                    }

                    // Set deployment mode into request (useful for epilogue)
                    request.getAttributesMap().put(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME, containingDocument.getDeploymentType().name());

                    final boolean hasEvents = clientEvents.size() > 0|| serverEventsElements.size() > 0;
                    // Whether there are uploaded files to handle
                    final boolean hasFiles = XFormsUploadControl.hasSubmittedFiles(filesElement);

                    // NOTE: As of 2010-12, background uploads in script mode are handled in xforms-server.xpl. In
                    // most cases should get files here only in noscript mode, but there is a chance in script mode in
                    // a 2-pass submission that some files could make it here as well.

                    final Tuple3[] eventsFindings = new Tuple3[1];

                    final XFormsControl beforeFocusedControl = containingDocument.getControls().getFocusedControl();
                    if (hasEvents || hasFiles) {
                        // Scope the containing document for the XForms API
                        XFormsAPI.withContainingDocumentJava(containingDocument, new Runnable() {
                            public void run() {
                                
                                final IndentedLogger eventsIndentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);
                                eventsIndentedLogger.startHandleOperation("", "handling external events and/or uploaded files");
                                {
                                    // Start external events
                                    containingDocument.beforeExternalEvents(response);
        
                                    // Handle uploaded files for noscript if any
                                    if (hasFiles) {
                                        eventsIndentedLogger.logDebug("", "handling uploaded files");
                                        XFormsUploadControl.handleSubmittedFiles(containingDocument, filesElement);
                                    }
        
                                    // Dispatch the events
                                    if (hasEvents)
                                        eventsFindings[0] = ClientEvents.processEvents(containingDocument, clientEvents, serverEventsElements);
        
                                    // End external events
                                    containingDocument.afterExternalEvents();
                                }
                                eventsIndentedLogger.endHandleOperation();
                            }
                        });
                    }

                    final boolean allEvents;
                    final Set<String> valueChangeControlIds;
                    final String clientFocusControlId;
                    if (eventsFindings[0] != null) {
                        allEvents = ((Boolean) eventsFindings[0]._1());
                        valueChangeControlIds = ((Set<String>) eventsFindings[0]._2());
                        clientFocusControlId = ((String) eventsFindings[0]._3());
                    } else {
                        allEvents = false;
                        valueChangeControlIds = Collections.emptySet();
                        clientFocusControlId = null;
                    }

                    // Check if there is a submission with replace="all" that needs processing
                    replaceAllCallable = containingDocument.getReplaceAllCallable();

                    // Notify the state manager that we will send the response
                    XFormsStateManager.instance().beforeUpdateResponse(containingDocument, isIgnoreSequenceNumber);

                    if (replaceAllCallable == null) {
                        // Handle response here (if not null, is handled after synchronized block)
                        if (xmlReceiver != null) {
                            // Create resulting document if there is a receiver
                            if (containingDocument.isGotSubmissionRedirect()) {
                                // Redirect already sent, output a null document is output so that rest of pipeline doesn't fail
                                indentedLogger.logDebug("response", "handling submission with replace=\"all\" with redirect");
                                XMLUtils.streamNullDocument(xmlReceiver);
                            } else if (!isNoscript) {
                                // This is an Ajax response
                                indentedLogger.startHandleOperation("response", "handling regular Ajax response");

                                // Hook-up debug content handler if we must log the response document
                                final XMLReceiver responseReceiver;
                                final LocationSAXContentHandler debugContentHandler;
                                final SAXStore responseStore;

                                // Two receivers possible
                                final List<XMLReceiver> receivers = new ArrayList<XMLReceiver>();

                                // Buffer for retries
                                if (isRetries) {
                                    responseStore = new SAXStore();
                                    receivers.add(responseStore);
                                } else {
                                    responseStore = null;
                                    receivers.add(xmlReceiver);
                                }

                                // Debug output
                                if (logRequestResponse) {
                                    debugContentHandler = new LocationSAXContentHandler();
                                    receivers.add(debugContentHandler);
                                } else {
                                    debugContentHandler = null;
                                }

                                responseReceiver = new TeeXMLReceiver(receivers);

                                // Prepare and/or output response
                                outputAjaxResponse(containingDocument, indentedLogger, valueChangeControlIds,
                                        clientFocusControlId, beforeFocusedControl,
                                        requestDocument, responseReceiver, allEvents, false);

                                // Store response in to document
                                containingDocument.rememberLastAjaxResponse(responseStore);

                                // Actually output response
                                // If there is an error, we do not
                                try {
                                    responseStore.replay(xmlReceiver);
                                } catch (Throwable t) {
                                    indentedLogger.logDebug("retry", "got exception while sending response; ignoring and expecting client to retry", t);
                                }

                                indentedLogger.endHandleOperation("ajax response", (debugContentHandler != null) ? Dom4jUtils.domToPrettyString(debugContentHandler.getDocument()) : null);
                            } else {
                                // Noscript mode
                                indentedLogger.startHandleOperation("response", "handling noscript response");
                                outputNoscriptResponse(containingDocument, indentedLogger, xmlReceiver, externalContext);
                                indentedLogger.endHandleOperation();
                            }
                        } else {
                            // This is the second pass of a submission with replace="all". We ensure that the document is
                            // not modified.

                            indentedLogger.logDebug("response", "handling NOP response for submission with replace=\"all\"");
                        }
                    }

                    // Notify state manager that we are done sending the response
                    XFormsStateManager.instance().afterUpdateResponse(containingDocument);

                    // All is done, keep the document around
                    keepDocument = true;

                } catch (Throwable e) {

                    // Log body of Ajax request if needed
                    if (XFormsProperties.getErrorLogging().contains("server-body"))
                        indentedLogger.logError("", "error processing Ajax update", "request", Dom4jUtils.domToPrettyString(requestDocument));

                    throw new OXFException(e);
                }

            } else if (requestSequenceNumber == expectedSequenceNumber - 1) {
                // This is a request for the previous response

                // Whatever happens when replaying, keep the document around
                keepDocument = true;

                assert containingDocument.getLastAjaxResponse() != null;

                indentedLogger.startHandleOperation("retry", "replaying previous Ajax response");
                boolean replaySuccess = false;
                try {
                    // Write last response
                    containingDocument.getLastAjaxResponse().replay(xmlReceiver);
                    replaySuccess = true;
                } catch (Exception e) {
                    throw new OXFException(e);
                } finally {
                    indentedLogger.endHandleOperation("success", Boolean.toString(replaySuccess));
                }

                // We are done here
                return;

            } else {
                // This is not allowed to happen

                // Keep the document around
                keepDocument = true;

                throw new OXFException("Got unexpected request sequence number");
            }
        } finally {
            // Make sure to call this to release the lock
            XFormsStateManager.instance().afterUpdate(containingDocument, keepDocument);
        }

        // Check and run submission with replace="all"
        // NOTE: Do this outside the synchronized block, so that if this takes time, subsequent Ajax requests can still
        // hit the document
        XFormsContainingDocument.checkAndRunDeferredSubmission(replaceAllCallable, response);
    }

    /**
     * Output an XHTML response for the noscript mode.
     *
     * @param containingDocument            containing document
     * @param xmlReceiver                   handler for the XHTML result
     * @param externalContext               external context
     * @throws IOException
     * @throws SAXException
     */
    private void outputNoscriptResponse(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger,
                                        XMLReceiver xmlReceiver, ExternalContext externalContext) throws IOException, SAXException {
        // This will also cache the containing document if needed
        // QUESTION: Do we actually need to cache if a xforms:submission[@replace = 'all'] happened?

        final List loads = containingDocument.getLoadsToRun();
        if (loads != null && loads.size() > 0) {
            // Handle xforms:load response

            // Get first load only
            final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) loads.get(0);

            // Send redirect
            final String redirectResource = load.getResource();
            indentedLogger.logDebug("response", "handling noscript redirect response for xforms:load", "url", redirectResource);
            // Set isNoRewrite to true, because the resource is either a relative path or already contains the servlet context
            externalContext.getResponse().sendRedirect(redirectResource, null, false, false);

            // Still send out a null document to signal that no further processing must take place
            XMLUtils.streamNullDocument(xmlReceiver);
        } else {
            // The template is stored either in the static state or in the dynamic state
            final AnnotatedTemplate template; {
                if (containingDocument.getStaticState().template().isDefined())
                    template = containingDocument.getStaticState().template().get();
                else
                    template = containingDocument.getTemplate();
            }
            if (template == null)
                throw new OXFException("Missing template in static and dynamic state for noscript mode.");// shouldn't happen!

            indentedLogger.logDebug("response", "handling noscript response for XHTML output");
            XFormsToXHTML.outputResponseDocument(externalContext, indentedLogger, template, containingDocument, xmlReceiver);
        }
    }

    /**
     * Output an Ajax response for the regular Ajax mode.
     *
     * @param containingDocument                containing document
     * @param indentedLogger                    logger
     * @param valueChangeControlIds             control ids for which the client sent a value change
     * @param clientFocusControlId              id of the last control that received focus from client
     * @param beforeFocusedControl              control which had the focus before the updates, if any
     * @param requestDocument                   incoming request document (for all events mode)
     * @param xmlReceiver                       handler for the Ajax result
     * @param allEvents                         whether to handle all events
     * @param testOutputAllActions              for testing purposes
     */
    public static void outputAjaxResponse(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger,
                                          Set<String> valueChangeControlIds, String clientFocusControlId, XFormsControl beforeFocusedControl,
                                          Document requestDocument, XMLReceiver xmlReceiver, boolean allEvents,
                                          boolean testOutputAllActions) {

        final XFormsControls xformsControls = containingDocument.getControls();

        try {
            final ContentHandlerHelper ch = new ContentHandlerHelper(xmlReceiver);
            ch.startDocument();
            xmlReceiver.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

            // Compute server events
            boolean requireClientSubmission = false;
            String submissionServerEvents = null;
            {
                final XFormsModelSubmission activeSubmission = containingDocument.getClientActiveSubmissionFirstPass();
                final List<XFormsContainingDocument.Load> loads = containingDocument.getLoadsToRun();
                if (activeSubmission != null || (loads != null && loads.size() > 0)) {
                    final Document eventsDocument = Dom4jUtils.createDocument();
                    final Element eventsElement = eventsDocument.addElement(XFormsConstants.XXFORMS_EVENTS_QNAME);

                    // Check for xxforms-submit event
                    {
                        if (activeSubmission != null) {
                            final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
                            eventElement.addAttribute("source-control-id", activeSubmission.getEffectiveId());
                            eventElement.addAttribute("name", XFormsEvents.XXFORMS_SUBMIT);
                            requireClientSubmission = true;
                        }
                    }
                    // Check for xxforms-load event (for portlet mode only!)
                    {
                        if (loads != null && loads.size() > 0) {
                            for (XFormsContainingDocument.Load load: loads) {
                                if (load.isReplace() && containingDocument.isPortletContainer() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType())) {
                                    // We need to submit the event so that the portlet can load the new path
                                    final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
                                    eventElement.addAttribute("source-control-id", XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID);
                                    eventElement.setText(load.getResource());
                                    // NOTE: don't care about the target for portlets
                                    eventElement.addAttribute("name", XFormsEvents.XXFORMS_LOAD);
                                    requireClientSubmission = true;

                                    break;
                                }
                            }
                        }
                    }
                    // Encode events so that the client cannot send back arbitrary events
                    if (requireClientSubmission)
                        submissionServerEvents = XFormsUtils.encodeXML(eventsDocument, false);
                }
            }

            // Output dynamic state
            {
                final String dynamicState = XFormsStateManager.instance().getClientEncodedDynamicState(containingDocument);
                if (dynamicState != null) {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic-state");
                    ch.text(dynamicState);
                    ch.endElement();
                }
            }

            // Output action
            {
                // Create a containing document in the initial state
                final XFormsContainingDocument initialContainingDocument;
                if (!allEvents) {
                    initialContainingDocument = null;
                } else {
                    // NOTE: Document is removed from cache if it was found there. This may or may not be desirable.
                    // Set disableUpdates = true so that we don't needlessly try to copy the controls tree. Also addresses:
                    // #54: "Browser back causes server exception" https://github.com/orbeon/orbeon-forms/issues/54
                    initialContainingDocument = XFormsStateManager.instance().findOrRestoreDocument(
                        XFormsStateManager.instance().extractParameters(requestDocument, true),
                        true,
                        true);
                }

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");

                // Output new controls values and associated information
                {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                    if (allEvents) {
                        // All events
                        // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                        final ControlTree currentControlTree = xformsControls.getCurrentControlTree();
                        final ControlTree initialControlTree = initialContainingDocument.getControls().getCurrentControlTree();

                        // Make sure all xxforms:dynamic will send full updates during control comparison
                        // Usually, xxforms:dynamic records structural changes at each update. Here, we don't really
                        // know whether there were any, so we safely force structural changes. This ensures that the
                        // client will have all the necessary markup, and also prevents the comparator from choking when
                        // comparing incompatible trees.
                        for (final ElementAnalysis e : containingDocument.getStaticOps().jControlsByName("dynamic"))
                            containingDocument.addControlStructuralChange(e.prefixedId());

                        diffControls(ch, containingDocument, indentedLogger, initialControlTree.getChildren(),
                                currentControlTree.getChildren(), null, testOutputAllActions);
                    } else if (testOutputAllActions || containingDocument.isDirtySinceLastRequest()) {
                        // Only output changes if needed
                        final ControlTree currentControlTree = xformsControls.getCurrentControlTree();
                        diffControls(ch, containingDocument, indentedLogger,
                                xformsControls.getInitialControlTree().getChildren(),
                                currentControlTree.getChildren(), valueChangeControlIds, testOutputAllActions);
                    }

                    ch.endElement();
                }

                // Output repeat indexes information
                {
                    // Output index updates
                    if (allEvents) {
                        // All events
                        // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                        final ControlTree currentControlTree = xformsControls.getCurrentControlTree();
                        final ControlTree initialControlTree = initialContainingDocument.getControls().getCurrentControlTree();

                        diffIndexState(ch, containingDocument, XFormsRepeatControl.findInitialIndexes(containingDocument, initialControlTree),
                                XFormsRepeatControl.findCurrentIndexes(containingDocument, currentControlTree));
                    } else if (!testOutputAllActions && containingDocument.isDirtySinceLastRequest()) {
                        // Only output changes if needed
                        diffIndexState(ch, containingDocument, xformsControls.getInitialControlTree().getIndexes(),
                                XFormsRepeatControl.findCurrentIndexes(containingDocument, xformsControls.getCurrentControlTree()));
                    }
                }

                // Output server events
                if (submissionServerEvents != null) {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events");
                    ch.text(submissionServerEvents);
                    ch.endElement();
                }
                {
                    final List<XFormsContainingDocument.DelayedEvent> delayedEvents = containingDocument.getDelayedEvents();
                    if (delayedEvents != null && delayedEvents.size() > 0) {
                        final long currentTime = System.currentTimeMillis();
                        for (XFormsContainingDocument.DelayedEvent delayedEvent: delayedEvents) {
                            delayedEvent.toSAX(ch, currentTime);
                        }
                    }
                }

                // Check if we need to tell the client to perform a form submission
                if (requireClientSubmission) {
                    outputSubmissionInfo(ch, containingDocument.getClientActiveSubmissionFirstPass());
                }

                // TODO: the following should be ordered in the order they were requested
                // Output messages to display
                {
                    final List<XFormsContainingDocument.Message> messages = containingDocument.getMessagesToRun();
                    if (messages != null) {
                        outputMessagesInfo(ch, messages);
                    }
                }

                // Output loads
                {
                    final List<XFormsContainingDocument.Load> loads = containingDocument.getLoadsToRun();
                    if (loads != null && loads.size() > 0) {
                        outputLoadsInfo(ch, containingDocument, loads);
                    }
                }

                // Output scripts
                {
                    final List<XFormsContainingDocument.Script> scripts = containingDocument.getScriptsToRun();
                    if (scripts != null) {
                        outputScriptsInfo(ch, containingDocument, scripts);
                    }
                }

                // Output focus instruction
                {
                    final XFormsControl afterFocusedControl = containingDocument.getControls().getFocusedControl();

                    // The focus as known by the client, as far as we know: either the focus sent by the client in the
                    // current request, or the focus information we kept since the previous request.
                    final String beforeFocusEffectiveId = clientFocusControlId != null ? clientFocusControlId : beforeFocusedControl != null ? beforeFocusedControl.getEffectiveId() : null;
                    final String afterFocusEffectiveId = afterFocusedControl != null ? afterFocusedControl.getEffectiveId() : null;

                    if (beforeFocusEffectiveId != null && afterFocusEffectiveId == null) {
                        // Focus removed: notify the client only if the control still exists
                        if (containingDocument.getControls().getCurrentControlTree().getControl(beforeFocusEffectiveId) != null)
                            outputFocusInfo(ch, containingDocument, false, beforeFocusEffectiveId);

                    } else if (afterFocusEffectiveId != null && ! afterFocusEffectiveId.equals(beforeFocusEffectiveId)) {

                        // There is a focused control and it is different from the focus as known by the client
                        outputFocusInfo(ch, containingDocument, true, afterFocusEffectiveId);
                    }
                }

                // Output help instruction
                {
                    final String helpControlEffectiveId = containingDocument.getClientHelpControlEffectiveId();
                    if (helpControlEffectiveId != null) {
                        outputHelpInfo(ch, containingDocument, helpControlEffectiveId);
                    }
                }

                ch.endElement();
            }

            // Output errors
            final List<XFormsError.ServerError> errors = containingDocument.getServerErrors();
            if (errors.size() > 0)
                XFormsError.outputAjaxErrors(ch, errors);

            ch.endElement();
            xmlReceiver.endPrefixMapping("xxf");
            ch.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    private static void diffIndexState(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, Map<String, Integer> initialRepeatIdToIndex,
                                       Map<String, Integer> currentRepeatIdToIndex) {
        if (currentRepeatIdToIndex.size() != 0) {
            boolean found = false;
            for (Map.Entry<String, Integer> currentEntry: currentRepeatIdToIndex.entrySet()) {
                final String repeatId = currentEntry.getKey();
                final Integer newIndex = currentEntry.getValue();

                // Output information if there is a difference
                final Integer oldIndex = initialRepeatIdToIndex.get(repeatId);// may be null if there was no iteration
                if (!newIndex.equals(oldIndex)) {

                    if (!found) {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-indexes");
                        found = true;
                    }

                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-index",
                            new String[] {"id", XFormsUtils.namespaceId(containingDocument, repeatId), "new-index", newIndex.toString()});
                }
            }
            if (found)
                ch.endElement();
        }
    }

    public static void diffControls(ContentHandlerHelper ch,
                                    XFormsContainingDocument containingDocument, IndentedLogger indentedLogger,
                                    List<XFormsControl> state1, List<XFormsControl> state2,
                                    Set<String> valueChangeControlIds, boolean isTestMode) {

        // In test mode, ignore first tree
        if (isTestMode)
            state1 = null;

        indentedLogger.startHandleOperation("", "computing differences");
        {
            new ControlsComparator(ch, containingDocument, valueChangeControlIds, isTestMode).diff(state1, state2);
        }
        indentedLogger.endHandleOperation();
    }

    private static void outputSubmissionInfo(ContentHandlerHelper ch, XFormsModelSubmission activeSubmission) {
        final String target;

        // activeSubmission submission can be null when are running as a portlet and handling an <xforms:load>, which
        // when executed from within a portlet is ran as very much like the replace="all" submissions.
        final String activeSubmissionReplace = activeSubmission == null ? "all" : activeSubmission.getReplace();
        final String activeSubmissionResolvedXXFormsTarget = activeSubmission == null ? null : activeSubmission.getResolvedXXFormsTarget();
        final String activeSubmissionShowProgress = (activeSubmission == null || activeSubmission.isShowProgress()) ? null : "false";

        if ("all".equals(activeSubmissionReplace)) {
            // Replace all
            // RFE: Set action ("action", clientSubmissionURL,) to destination page for local submissions? (http://tinyurl.com/692f7r)
            target = activeSubmissionResolvedXXFormsTarget;
        } else {
            // Replace instance
            target = null;
        }

        // Signal that we want a POST to the XForms server
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "submission",
                new String[]{
                        "method", "POST",
                        "show-progress", activeSubmissionShowProgress,
                        (target != null) ? "target" : null, target
                });
    }

    private static void outputMessagesInfo(ContentHandlerHelper ch, List<XFormsContainingDocument.Message> messages) {
        for (XFormsContainingDocument.Message message: messages) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "message",
                    new String[]{"level", message.getLevel()});
            ch.text(message.getMessage());
            ch.endElement();
        }
    }

    public static void outputLoadsInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List<XFormsContainingDocument.Load> loads) {
        for (XFormsContainingDocument.Load load: loads) {
            if (!(load.isReplace() && containingDocument.isPortletContainer() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType()))) {
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "load",
                        new String[]{ "resource", load.getResource(), (load.getTarget() != null) ? "target" : null, load.getTarget(), "show", load.isReplace() ? "replace" : "new", "show-progress", load.isShowProgress() ? null : "false" });
            }
        }
    }

    public static void outputScriptsInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List<XFormsContainingDocument.Script> scripts) {
        for (XFormsContainingDocument.Script script: scripts) {
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "script",
                    new String[]{
                            "name", script.functionName,
                            "target-id", XFormsUtils.namespaceId(containingDocument, script.targetEffectiveId),
                            "observer-id", XFormsUtils.namespaceId(containingDocument, script.observerEffectiveId)
                    });
        }
    }

    private static void outputFocusInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, boolean focus, String focusControlEffectiveId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, focus ? "focus" : "blur",
                new String[]{"control-id", XFormsUtils.namespaceId(containingDocument, focusControlEffectiveId)});
    }

    private static void outputHelpInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, String helpControlEffectiveId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "help",
                new String[]{"control-id", XFormsUtils.namespaceId(containingDocument, helpControlEffectiveId)});
    }
}
