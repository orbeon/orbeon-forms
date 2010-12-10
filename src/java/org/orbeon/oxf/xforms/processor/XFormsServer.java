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
import org.orbeon.oxf.pipeline.api.*;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xforms.submission.SubmissionResult;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * The XForms Server processor handles client requests, including events, and either returns an XML
 * response, or returns a response through the ExternalContext.
 */
public class XFormsServer extends ProcessorImpl {

    public static final boolean USE_SEPARATE_LOGGERS = false;
    public static final String LOGGING_CATEGORY = "server";
    private static final Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    //private static final String OUTPUT_RESPONSE = "response"; // optional

    public static final Map<String, String> XFORMS_NAMESPACES = new HashMap<String, String>();
    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public static Logger getLogger() {
        return logger;
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

        final ExternalContext externalContext = NetUtils.getExternalContext(pipelineContext);
        final ExternalContext.Request request = externalContext.getRequest();

        // Use request input provided by client
        final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

        // Request retry details
        final boolean isRetries = true;
        final long requestSequenceNumber = !isRetries ? 0 : XFormsStateManager.getRequestSequence(requestDocument);

        final boolean isAjaxRequest = request.getMethod().equalsIgnoreCase("post") && XMLUtils.isXMLMediatype(NetUtils.getContentTypeMediaType(request.getContentType()));

        final boolean isIgnoreSequenceNumber = !isRetries || !isAjaxRequest;

        // Logger used for heartbeat and request/response
        final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(XFormsServer.getLogger(), XFormsServer.getLogger(), LOGGING_CATEGORY);

        final boolean logRequestResponse = XFormsProperties.getDebugLogging().contains("server-body");
        if (logRequestResponse) {
            indentedLogger.logDebug("", "ajax request", "body", Dom4jUtils.domToPrettyString(requestDocument));
        }

        // Get action
        final Element actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

        // Get files if any (those come from xforms-server-submit.xpl upon submission)
        final Element filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

        // Get server events if any
        final List<Element> serverEventsElements = Dom4jUtils.elements(requestDocument.getRootElement(), XFormsConstants.XXFORMS_SERVER_EVENTS_QNAME);

        // Get events requested by the client
        final List<Element> eventElements = new ArrayList<Element>();

        // Gather server events first if any
        int serverEventsCount = 0;
        if (serverEventsElements != null && serverEventsElements.size() > 0) {
            for (final Element element: serverEventsElements) {
                final Document serverEventsDocument = XFormsUtils.decodeXML(pipelineContext, element.getStringValue());
                final List<Element> xxformsEventElements = Dom4jUtils.elements(serverEventsDocument.getRootElement(), XFormsConstants.XXFORMS_EVENT_QNAME);
                serverEventsCount += xxformsEventElements.size();
                eventElements.addAll(xxformsEventElements);
            }
        }

        // Gather client events if any
        if (actionElement != null) {
            eventElements.addAll(Dom4jUtils.elements(actionElement, XFormsConstants.XXFORMS_EVENT_QNAME));
        }

        // Hit session if it exists (it's probably not even necessary to do so)
        final ExternalContext.Session session = externalContext.getSession(false);

        // Check for message where there is only the heartbeat event
        if (eventElements.size() == 1) {
            final Element eventElement = eventElements.get(0);
            final String eventName = eventElement.attributeValue("name");
            if (eventName.equals(XFormsEvents.XXFORMS_SESSION_HEARTBEAT)) {

                if (indentedLogger.isDebugEnabled()) {
                    if (session != null)
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client for session: " + session.getId());
                    else
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client (no session available).");
                }

                // Output simple resulting document
                try {
                    final ContentHandlerHelper helper = new ContentHandlerHelper(xmlReceiver);
                    helper.startDocument();
                    xmlReceiver.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");
                    helper.endElement();
                    xmlReceiver.endPrefixMapping("xxf");
                    helper.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }

                // Don't do anything else
                return;
            }
        }

        // Find an output stream for xforms:submission[@replace = 'all']
        final ExternalContext.Response response = XFormsToXHTML.getResponse(xmlReceiver, externalContext);

        // Find or restore containing document from the incoming request
        final XFormsContainingDocument containingDocument
                = XFormsStateManager.instance().findOrRestoreDocument(pipelineContext, requestDocument, session, false);

        // The synchronization is tricky: certainly, once we get a document, we don't want to allow multiple threads to
        // it at the same time as a document is clearly not thread-safe. What could happen though in theory is two Ajax
        // requests for the same document, one found in the cache, the other one not (because just expired due to other
        // thread's activity). This would create a new document and would produce unexpected results. However, the
        // client is meant to send only one Ajax request at a time, which should make this case very unlikely.

        // Another situation is that if the ContentHandler is null, then the event is the second pass of a submission
        // with replace="all" and does not modify the Containing Document. The event should in fact go straight to the
        // Submission object. It should therefore be safe not to synchronize in this case. But do we want to take the
        // risk?

//        final Object documentSynchronizationObject = (contentHandler != null) ? containingDocument : new Object();
        Callable<SubmissionResult> replaceAllCallable = null;
        synchronized (containingDocument) {
            final IndentedLogger eventsIndentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);

            final long expectedSequenceNumber = !isRetries ? 0 : containingDocument.getSequence();

            if (isIgnoreSequenceNumber || requestSequenceNumber == expectedSequenceNumber) {
                // We are good: process request and produce new sequence number

                try {
                    // Run events if any
                    final boolean isNoscript = containingDocument.getStaticState().isNoscript();

                    // Set URL rewriter resource path information based on information in static state
                    if (containingDocument.getVersionedPathMatchers() != null) {
                        // Don't override existing matchers if any (e.g. case of oxf:xforms-to-xhtml and oxf:xforms-submission processor running in same pipeline)
                        pipelineContext.setAttribute(PageFlowControllerProcessor.PATH_MATCHERS, containingDocument.getVersionedPathMatchers());
                    }
                    // Set XPath configuration
                    pipelineContext.setAttribute(XPathCache.XPATH_CACHE_CONFIGURATION_PROPERTY, containingDocument.getStaticState().getXPathConfiguration());

                    // Set deployment mode into request (useful for epilogue)
                    request.getAttributesMap().put(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME, containingDocument.getDeploymentType().name());

                    final boolean hasEvents = eventElements.size() > 0;
                    // Whether there are uploaded files to handle
                    final boolean hasFiles = XFormsUploadControl.hasUploadedFiles(filesElement);

                    // NOTE: As of 2010-12, background uploads in script mode are handled in xforms-server.xpl. In
                    // most cases should get files here only in noscript mode, but there is a chance in script mode in
                    // a 2-pass submission that some files could make it here as well.

                    final boolean allEvents;
                    final Set<String> valueChangeControlIds = new HashSet<String>();
                    if (hasEvents || hasFiles) {

                        // Reorder events in noscript mode
                        if (hasEvents && isNoscript)
                            processEventsForNoscript(eventElements, containingDocument);

                        eventsIndentedLogger.startHandleOperation("", "handling external events and/or uploaded files");
                        {
                            // Start external events
                            containingDocument.beforeExternalEvents(pipelineContext, response);

                            // Handle uploaded files for noscript if any
                            if (hasFiles) {
                                eventsIndentedLogger.logDebug("", "handling uploaded files");
                                XFormsUploadControl.handleUploadedFiles(pipelineContext, containingDocument, filesElement, true);
                            }

                            // Dispatch the events
                            allEvents = hasEvents && createAndDispatchEvents(pipelineContext, containingDocument, eventElements,
                                    serverEventsCount, valueChangeControlIds);

                            // End external events
                            containingDocument.afterExternalEvents(pipelineContext);
                        }
                        eventsIndentedLogger.endHandleOperation();
                    } else {
                        allEvents = false;
                    }

                    // Check if there is a submission with replace="all" that needs processing
                    replaceAllCallable = containingDocument.getReplaceAllCallable();

                    // TODO: UI-DEPENDENCIES TEMP
    //                containingDocument.getStaticState().dumpAnalysis();

                    // Notify the state manager that we will send the response
                    XFormsStateManager.instance().beforeUpdateResponse(pipelineContext, containingDocument, isIgnoreSequenceNumber);

                    if (replaceAllCallable == null) {
                        // Handle response here (if not null, is handled after synchronized block)
                        if (xmlReceiver != null) {
                            // Create resulting document if there is a receiver
                            if (containingDocument.isGotSubmissionReplaceAll() && (isNoscript || XFormsProperties.isAjaxPortlet(containingDocument))) {
                                // NOP: Response already sent out by a submission
                                indentedLogger.logDebug("response", "handling noscript or Ajax portlet response for submission with replace=\"all\"");
                            } else if (!isNoscript) {
                                // This is an Ajax response
                                indentedLogger.startHandleOperation("response", "handling regular Ajax response");

                                // Hook-up debug content handler if we must log the response document
                                final XMLReceiver responseReceiver;
                                final LocationSAXContentHandler debugContentHandler;
                                final SAXStore responseStore;
                                if (logRequestResponse || isRetries) {
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

                                } else {
                                    // Just one receiver
                                    debugContentHandler = null;
                                    responseStore = null;
                                    responseReceiver = xmlReceiver;
                                }

                                // Prepare and/or output response
                                outputAjaxResponse(containingDocument, indentedLogger, valueChangeControlIds, pipelineContext,
                                        requestDocument, responseReceiver, allEvents, false);

                                if (isRetries) {
                                    // Store response in to document
                                    containingDocument.rememberLastAjaxResponse(responseStore);

                                    // Actually output response
                                    // If there is an error, we do not
                                    try {
                                        responseStore.replay(xmlReceiver);
                                    } catch (Throwable t) {
                                        indentedLogger.logDebug("retry", "got exception while sending response; ignoring and expecting client to retry", t);
                                    }
                                }

                                indentedLogger.endHandleOperation("ajax response", (debugContentHandler != null) ? Dom4jUtils.domToPrettyString(debugContentHandler.getDocument()) : null);
                            } else {
                                // Noscript mode
                                indentedLogger.startHandleOperation("response", "handling noscript response");
                                outputNoscriptResponse(containingDocument, indentedLogger, pipelineContext, xmlReceiver, externalContext);
                                indentedLogger.endHandleOperation();
                            }
                        } else {
                            // This is the second pass of a submission with replace="all". We ensure that the document is
                            // not modified.

                            indentedLogger.logDebug("response", "handling NOP response for submission with replace=\"all\"");
                        }
                    }

                    // Notify state manager that we are done sending the response
                    XFormsStateManager.instance().afterUpdateResponse(pipelineContext, containingDocument);

                } catch (Throwable e) {
                    // Notify state manager that an error occurred
                    XFormsStateManager.instance().onUpdateError(pipelineContext, containingDocument);

                    // Log body of Ajax request if needed
                    if (XFormsProperties.getErrorLogging().contains("server-body"))
                        indentedLogger.logError("", "error processing Ajax update", "request", Dom4jUtils.domToPrettyString(requestDocument));

                    throw new OXFException(e);
                }

            } else if (requestSequenceNumber == expectedSequenceNumber - 1) {
                // This is a request for the previous response

                assert containingDocument.getLastAjaxResponse() != null;

                indentedLogger.startHandleOperation("retry", "replaying previous Ajax response");
                boolean success = false;
                try {
                    // Write last response
                    containingDocument.getLastAjaxResponse().replay(xmlReceiver);
                    success = true;
                } catch (Exception e) {
                    throw new OXFException(e);
                } finally {
                    indentedLogger.endHandleOperation("success", Boolean.toString(success));
                }


                // We are done here
                return;

            } else {
                // This is not allowed to happen
                throw new OXFException("Got unexpected request sequence number");
            }
        }

        // Check and run submission with replace="all"
        // NOTE: Do this outside the synchronized block, so that if this takes time, subsequent Ajax requests can still
        // hit the document
        XFormsContainingDocument.checkAndRunDeferredSubmission(replaceAllCallable, response);
    }

    // All supported event parameters
    private static String[] EVENT_PARAMETERS = new String[] { "dnd-start", "dnd-end", "modifiers", "text", "file", "filename", "content-type", "content-length" };

    private boolean createAndDispatchEvents(PipelineContext pipelineContext, XFormsContainingDocument containingDocument,
                                            List<Element> eventElements, int serverEventsCount, Set<String> valueChangeControlIds) {

        // NOTE: We store here the last xxforms-value-change-with-focus-change event so
        // we can coalesce values in case several such events are sent for the same
        // control. The client should not send us such series of events, but currently
        // it may happen.
        String lastSourceControlId = null;
        String lastValueChangeEventValue = null;

        boolean hasAllEvents = false;

        final List<XFormsEvent> events = new ArrayList<XFormsEvent>();

        // Iterate through all events to dispatch them
        final Map<String, String> parameters = new HashMap<String, String>();
        int eventElementIndex = 0;
        for (Iterator i = eventElements.iterator(); i.hasNext(); eventElementIndex++) {
            final Element eventElement = (Element) i.next();

            // Whether this event is trusted, that is whether this event was a server event. Server events
            // are processed first, so are at the beginning of eventElements.
            boolean isTrustedEvent = eventElementIndex < serverEventsCount;

            final String eventName = eventElement.attributeValue("name");
            final String sourceTargetId = eventElement.attributeValue("source-control-id");
            final boolean bubbles = !"false".equals(eventElement.attributeValue("bubbles"));// default is true
            final boolean cancelable = !"false".equals(eventElement.attributeValue("cancelable"));// default is true

            final String otherControlId = eventElement.attributeValue("other-control-id");

            // Gather parameters corresponding to special event attributes
            parameters.clear();
            for (final String attributeName: EVENT_PARAMETERS) {
                final String attributeValue = eventElement.attributeValue(attributeName);
                if (attributeValue != null)
                    parameters.put(attributeName, attributeValue);
            }

            // Content of the element (the value of a control)
            final String value = eventElement.getText();

            if (XFormsEvents.XXFORMS_ALL_EVENTS_REQUIRED.equals(eventName)) {
                // Special event telling us to resend the client all the events since initialization

                // Remember that we got this event
                hasAllEvents = true;

            } else if (sourceTargetId != null && eventName != null) {
                // A normal event is passed
                if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE) && otherControlId == null) {
                    // xxforms-value-change-with-focus-change event

                    // The goal of the code below is to coalesce multiple sequential value changes for the
                    // same control. Not sure if this is still needed.
                    if (lastSourceControlId == null) {
                        // Remember event
                        lastSourceControlId = sourceTargetId;
                        lastValueChangeEventValue = value;
                    } else if (lastSourceControlId.equals(sourceTargetId)) {
                        // Update event
                        lastValueChangeEventValue = value;
                    } else {
                        // Send old event
                        createCheckEvent(containingDocument, false, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE,
                                lastSourceControlId, true, true, null, lastValueChangeEventValue, events, null);
                        // Remember new event
                        lastSourceControlId = sourceTargetId;
                        lastValueChangeEventValue = value;
                    }
                } else {
                    // Other normal events

                    if (lastSourceControlId != null) {
                        // Send old event
                        createCheckEvent(containingDocument, false, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE,
                                lastSourceControlId, true, true, null, lastValueChangeEventValue, events, null);
                        lastSourceControlId = null;
                        lastValueChangeEventValue = null;
                    }
                    // Send new event
                    createCheckEvent(containingDocument, isTrustedEvent, eventName, sourceTargetId, bubbles, cancelable,
                            otherControlId, value, events, parameters);
                }

                if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
                    // Remember id of controls for which value changed
                    valueChangeControlIds.add(sourceTargetId);
                }
            } else if (!(sourceTargetId == null && eventName == null)) {
                // Error case
                throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
            }
        }
        // Flush stored event if needed
        if (lastSourceControlId != null) {
            // Send old event
            createCheckEvent(containingDocument, false, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE,
                    lastSourceControlId, true, true, null, lastValueChangeEventValue, events, null);
        }

        // Iterate and dispatch the events
        for (final XFormsEvent event: events) {
            containingDocument.handleExternalEvent(pipelineContext, event);
        }

        return hasAllEvents;
    }

    private void createCheckEvent(XFormsContainingDocument containingDocument, boolean isTrustedEvent,
                                  String eventName, String targetEffectiveId, boolean bubbles, boolean cancelable,
                                  String otherControlEffectiveId, String valueString, List<XFormsEvent> events,
                                  Map<String, String> parameters) {

        final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);

        // Get event target
        final XFormsEventTarget eventTarget;
        {
            final Object eventTargetObject = containingDocument.getObjectByEffectiveId(XFormsUtils.deNamespaceId(containingDocument, targetEffectiveId));
            if (!(eventTargetObject instanceof XFormsEventTarget)) {
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "ignoring client event with invalid target id", "target id", targetEffectiveId, "event name", eventName);
                }
                return;
            }
            eventTarget = (XFormsEventTarget) eventTargetObject;
        }

        // Rewrite event type. This is special handling of xxforms-value-or-activate for noscript mode.
        // NOTE: We do this here, because we need to know the actual type of the target. Could do this statically if
        // the static state kept type information for each control.
        if (XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE.equals(eventName)) {
            // In this case, we translate the event depending on the control type
            if (eventTarget instanceof XFormsTriggerControl) {
                // Triggers get a DOM activation
                if ("".equals(valueString)) {
                    // Handler produces:
                    //   <button type="submit" name="foobar" value="activate">...
                    //   <input type="submit" name="foobar" value="Hi There">...
                    //   <input type="image" name="foobar" value="Hi There" src="...">...

                    // IE 6/7 are terminally broken: they don't send the value back, but the contents of the label. So
                    // we must test for any empty content here instead of "!activate".equals(valueString). (Note that
                    // this means that empty labels won't work.) Further, with IE 6, all buttons are present when
                    // using <button>, so we use <input> instead, either with type="submit" or type="image". Bleh.

                    return;
                }
                eventName = XFormsEvents.DOM_ACTIVATE;
            } else {
                // Other controls get a value change
                eventName = XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE;
            }
        }

        // For testing only
        if (XFormsProperties.isAjaxTest()) {
            if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
                valueString = "value" + System.currentTimeMillis();
            }
        }

        // Check the event is allowed on target
        if (isTrustedEvent) {
            // Event is trusted, don't check if it is allowed
            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "processing trusted event", "target id", eventTarget.getEffectiveId(), "event name", eventName);
            }
        } else if (!containingDocument.checkAllowedExternalEvents(indentedLogger, eventName, eventTarget)) {
            // Event is not trusted and is not allowed
            return;
        }

        // Get other event target
        final XFormsEventTarget otherEventTarget;
        {
            final Object otherEventTargetObject = (otherControlEffectiveId == null) ? null : containingDocument.getObjectByEffectiveId(XFormsUtils.deNamespaceId(containingDocument, otherControlEffectiveId));
            if (otherEventTargetObject == null) {
                otherEventTarget = null;
            } else if (!(otherEventTargetObject instanceof XFormsEventTarget)) {
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "ignoring invalid client event with invalid second control id", "target id", targetEffectiveId, "event name", eventName, "second control id", otherControlEffectiveId);
                }
                return;
            } else {
                otherEventTarget = (XFormsEventTarget) otherEventTargetObject;
            }
        }

        // Create event
        events.add(XFormsEventFactory.createEvent(containingDocument, eventName, eventTarget, otherEventTarget, true,
                bubbles, cancelable, valueString, parameters));
    }

    private void processEventsForNoscript(List<Element> eventElements, XFormsContainingDocument containingDocument) {

        final XFormsStaticState staticState = containingDocument.getStaticState();

        List<Element> noscriptActivateEvents = null;
        Map<String, String> noscriptValueIds = null;

        for (Iterator i = eventElements.iterator(); i.hasNext();) {
            final Element eventElement = (Element) i.next();
            final String eventName = eventElement.attributeValue("name");

            if (XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE.equals(eventName)) {
                // Special event for noscript mode
                final String sourceControlId = eventElement.attributeValue("source-control-id");
                if (!staticState.isValueControl(sourceControlId)) {
                    // This is most likely a trigger or submit which will translate into a DOMActivate,
                    // so we move it to the end so that value change events are committed to the
                    // instance before that.
                    i.remove();
                    if (noscriptActivateEvents == null)
                        noscriptActivateEvents = new ArrayList<Element>();
                    noscriptActivateEvents.add(eventElement);
                } else {
                    // This is a value event, just remember the id
                    if (noscriptValueIds == null)
                        noscriptValueIds = new HashMap<String, String>();
                    noscriptValueIds.put(sourceControlId, "");
                }
            }
        }

        // Special handling of checkboxes blanking in noscript mode
        final Map<String, XFormsControl> selectFullControls = containingDocument.getControls().getCurrentControlTree().getSelectFullControls();

        if (selectFullControls != null) {

            for (Map.Entry<String, XFormsControl> currentEntry: selectFullControls.entrySet()) {
                final String currentEffectiveId = currentEntry.getKey();
                final XFormsSelectControl currentControl = (XFormsSelectControl) currentEntry.getValue();

                if (currentControl != null
                        && (noscriptValueIds == null || noscriptValueIds.get(currentEffectiveId) == null) // control did not have a value set by other events
                        && currentControl.isRelevant() && !currentControl.isReadonly()) {                 // control is relevant and not readonly

                    // <xxforms:event name="xxforms-value-or-activate" source-control-id="my-effective-id"/>
                    final Element newEventElement = Dom4jUtils.createElement(XFormsConstants.XXFORMS_EVENT_QNAME.getQualifiedName(), XFormsConstants.XXFORMS_EVENT_QNAME.getNamespaceURI());
                    newEventElement.addAttribute("name", XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE);
                    newEventElement.addAttribute("source-control-id", currentEffectiveId);

                    // Append the blanking event
                    eventElements.add(newEventElement);
                }
            }
        }

        // Append all noscript activation events
        if (noscriptActivateEvents != null) {
            eventElements.addAll(noscriptActivateEvents);
        }
    }

    /**
     * Output an XHTML response for the noscript mode.
     *
     * @param containingDocument            containing document
     * @param pipelineContext               pipeline context
     * @param xmlReceiver                   handler for the XHTML result
     * @param externalContext               external context
     * @throws IOException
     * @throws SAXException
     */
    private void outputNoscriptResponse(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger, PipelineContext pipelineContext,
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
            // Response will be Ajax response or XHTML document
            final SAXStore xhtmlDocument = containingDocument.getAnnotatedTemplate();
            if (xhtmlDocument == null)
                throw new OXFException("Missing XHTML document in static state for noscript mode.");// shouldn't happen!

            indentedLogger.logDebug("response", "handling noscript response for XHTML output");
            XFormsToXHTML.outputResponseDocument(pipelineContext, externalContext, indentedLogger, xhtmlDocument,
                    containingDocument, xmlReceiver);
        }
    }

    /**
     * Output an Ajax response for the regular Ajax mode.
     *
     * @param containingDocument                containing document
     * @param indentedLogger                    logger
     * @param valueChangeControlIds             control ids for which the client sent a value change
     * @param pipelineContext                   current context
     * @param requestDocument                   incoming request document (for all events mode)
     * @param xmlReceiver                       handler for the Ajax result
     * @param allEvents                         whether to handle all events
     * @param testOutputAllActions              for testing purposes
     */
    public static void outputAjaxResponse(XFormsContainingDocument containingDocument, IndentedLogger indentedLogger,
                                          Set<String> valueChangeControlIds, PipelineContext pipelineContext,
                                          Document requestDocument, XMLReceiver xmlReceiver, boolean allEvents,
                                          boolean testOutputAllActions) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final XFormsControls xformsControls = containingDocument.getControls();

        final boolean testOutputStaticState = false;

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
                                if (load.isReplace() && load.isPortletLoad() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType())) {
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
                        submissionServerEvents = XFormsUtils.encodeXML(pipelineContext, eventsDocument, false);
                }
            }

            // Output static state when testing
            if (testOutputStaticState) {
                final String staticState = XFormsStateManager.instance().getClientEncodedStaticState(pipelineContext, containingDocument);
                if (staticState != null) {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "static-state", new String[] {
                            "container-type", externalContext.getRequest().getContainerType()
                    });
                    ch.text(staticState);
                    ch.endElement();
                }
            }

            // Output dynamic state
            {
                final String dynamicState = XFormsStateManager.instance().getClientEncodedDynamicState(pipelineContext, containingDocument);
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
                    final ExternalContext.Session session = externalContext.getSession(false);
                    initialContainingDocument
                            = XFormsStateManager.instance().findOrRestoreDocument(pipelineContext, requestDocument, session, true);
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
                        diffControls(pipelineContext, ch, containingDocument, indentedLogger, initialControlTree.getChildren(),
                                currentControlTree.getChildren(), null, testOutputAllActions);
                    } else if (testOutputAllActions || containingDocument.isDirtySinceLastRequest()) {
                        // Only output changes if needed
                        final ControlTree currentControlTree = xformsControls.getCurrentControlTree();
                        diffControls(pipelineContext, ch, containingDocument, indentedLogger,
                                xformsControls.getInitialControlTree().getChildren(),
                                currentControlTree.getChildren(), valueChangeControlIds, testOutputAllActions);
                    }

                    ch.endElement();
                }

                // Output repeat indexes information
                {
                    // Output index updates
                    final XFormsStaticState staticState = containingDocument.getStaticState();
                    if (allEvents) {
                        // All events
                        // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                        final ControlTree currentControlTree = xformsControls.getCurrentControlTree();
                        final ControlTree initialControlTree = initialContainingDocument.getControls().getCurrentControlTree();
                        diffIndexState(ch, containingDocument, initialControlTree.getInitialMinimalRepeatIdToIndex(staticState),
                                currentControlTree.getMinimalRepeatIdToIndex(staticState));
                    } else if (!testOutputAllActions && containingDocument.isDirtySinceLastRequest()) {
                        // Only output changes if needed
                        diffIndexState(ch, containingDocument, xformsControls.getInitialControlTree().getInitialMinimalRepeatIdToIndex(staticState),
                                xformsControls.getCurrentControlTree().getMinimalRepeatIdToIndex(staticState));
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
                            delayedEvent.toSAX(pipelineContext, ch, currentTime);
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
                        outputLoadsInfo(ch, loads);
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
                    final String focusEffectiveControlId = containingDocument.getClientFocusEffectiveControlId();
                    if (focusEffectiveControlId != null) {
                        outputFocusInfo(ch, containingDocument, focusEffectiveControlId);
                    }
                }

                // Output help instruction
                {
                    final String helpEffectiveControlId = containingDocument.getClientHelpEffectiveControlId();
                    if (helpEffectiveControlId != null) {
                        outputHelpInfo(ch, containingDocument, helpEffectiveControlId);
                    }
                }

                ch.endElement();
            }

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
                            new String[] {"id", XFormsUtils.namespaceId(containingDocument, repeatId), "old-index", (oldIndex != null) ? oldIndex.toString() : "0", "new-index", newIndex.toString()});
                }
            }
            if (found)
                ch.endElement();
        }
    }

    public static void diffControls(PipelineContext pipelineContext, ContentHandlerHelper ch,
                                    XFormsContainingDocument containingDocument, IndentedLogger indentedLogger,
                                    List<XFormsControl> state1, List<XFormsControl> state2,
                                    Set<String> valueChangeControlIds, boolean isTestMode) {

        // In test mode, ignore first tree
        if (isTestMode)
            state1 = null;

        indentedLogger.startHandleOperation("", "computing differences");
        {
            new ControlsComparator(pipelineContext, ch, containingDocument, valueChangeControlIds, isTestMode).diff(state1, state2);
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
                        "replace", activeSubmissionReplace,
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

    public static void outputLoadsInfo(ContentHandlerHelper ch, List<XFormsContainingDocument.Load> loads) {
        for (XFormsContainingDocument.Load load: loads) {
            if (!(load.isReplace() && load.isPortletLoad() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType()))) {
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "load",
                        new String[]{ "resource", load.getResource(), (load.getTarget() != null) ? "target" : null, load.getTarget(), "show", load.isReplace() ? "replace" : "new", "show-progress", load.isShowProgress() ? null : "false" });
            }
        }
    }

    public static void outputScriptsInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List<XFormsContainingDocument.Script> scripts) {
        for (XFormsContainingDocument.Script script: scripts) {
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "script",
                    new String[]{
                            "name", script.getFunctionName(),
                            "target-id", XFormsUtils.namespaceId(containingDocument, script.getEvent().getTargetObject().getEffectiveId()),
                            "observer-id", XFormsUtils.namespaceId(containingDocument, script.getEventObserver().getEffectiveId())
                    });
        }
    }

    private static void outputFocusInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, String focusEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "setfocus",
                new String[]{"control-id", XFormsUtils.namespaceId(containingDocument, focusEffectiveControlId)});
    }

    private static void outputHelpInfo(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, String helpEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "help",
                new String[]{"control-id", XFormsUtils.namespaceId(containingDocument, helpEffectiveControlId)});
    }
}
