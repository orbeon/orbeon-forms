/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.PageFlowControllerProcessor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.state.XFormsDocumentCache;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.state.XFormsStateManager;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;
import java.io.StringWriter;
import java.util.*;

/**
 * The XForms Server processor handles client requests, including events, and either returns an XML
 * response, or returns a response through the ExternalContext.
 */
public class XFormsServer extends ProcessorImpl {

    static public Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    //private static final String OUTPUT_RESPONSE = "response"; // optional

    public static final Map XFORMS_NAMESPACES = new HashMap();

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
    public ProcessorOutput createOutput(final String outputName) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), outputName) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler);
            }
        };
        addOutput(outputName, output);
        return output;
    }

    /**
     * Case where the response is generated throug the ExternalContext (submission with replace="all").
     */
    public void start(PipelineContext pipelineContext) {
        doIt(pipelineContext, null);
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler) {

        final Element filesElement;
        final Element actionElement;
        final Element serverEventsElement;

        // Use request input provided by client
        final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

        // Get action
        actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

        // Get files if any (those come from xforms-server-submit.xpl upon submission)
        filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

        // Get server events if any
        serverEventsElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_SERVER_EVENTS_QNAME);

        // Get events requested by the client
        final List eventElements = new ArrayList();

        // Gather automatic events if any
        if (serverEventsElement != null) {
            final Document serverEventsDocument = XFormsUtils.decodeXML(pipelineContext, serverEventsElement.getStringValue());
            eventElements.addAll(serverEventsDocument.getRootElement().elements(XFormsConstants.XXFORMS_EVENT_QNAME));
        }

        // Gather client events if any
        if (actionElement != null) {
            eventElements.addAll(actionElement.elements(XFormsConstants.XXFORMS_EVENT_QNAME));
        }

        // Check for heartbeat event
        if (eventElements.size() > 0) {
            for (Iterator i = eventElements.iterator(); i.hasNext();) {
                final Element eventElement = (Element) i.next();
                final String eventName = eventElement.attributeValue("name");
                if (eventName.equals(XFormsEvents.XXFORMS_SESSION_HEARTBEAT)) {

                    // This event must be sent on its own
                    if (eventElements.size() > 1)
                        throw new OXFException("Got xxforms-session-heartbeat event along with other events.");

                    // Hit session if it exists (it's probably not even necessary to do so)
                    final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                    final ExternalContext.Session session = externalContext.getSession(false);

                    if (logger.isDebugEnabled()) {
                        if (session != null)
                            logger.debug("XForms - received heartbeat from client for session: " + session.getId());
                        else
                            logger.debug("XForms - received heartbeat from client (no session available).");
                    }

                    // Output simple resulting document
                    try {
                        final ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                        ch.startDocument();
                        contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");
                        ch.endElement();
                        contentHandler.endPrefixMapping("xxf");
                        ch.endDocument();
                    } catch (SAXException e) {
                        throw new OXFException(e);
                    }

                    // Don't do anything else
                    return;
                }
            }
        }

        // Get static state
        final String encodedClientStaticStateString;
        {
            final Element staticStateElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_STATIC_STATE_QNAME);
            encodedClientStaticStateString = staticStateElement.getTextTrim();
        }

        // Get dynamic state
        final String encodedClientDynamicStateString;
        {
            final Element dynamicStateElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_DYNAMIC_STATE_QNAME);
            encodedClientDynamicStateString = dynamicStateElement.getTextTrim();
        }

        // Decode state
        final XFormsStateManager.XFormsDecodedClientState xformsDecodedClientState
                = XFormsStateManager.decodeClientState(pipelineContext, encodedClientStaticStateString, encodedClientDynamicStateString);

        // Get initial dynamic state for "all events" event if needed
        final XFormsStateManager.XFormsDecodedClientState xformsDecodedInitialClientState;
        {
            final Element initialDynamicStateElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_INITIAL_DYNAMIC_STATE_QNAME);
            if (initialDynamicStateElement != null) {
                final String encodedClientInitialDynamicStateString = initialDynamicStateElement.getTextTrim();

                xformsDecodedInitialClientState
                    = XFormsStateManager.decodeClientState(pipelineContext, encodedClientStaticStateString, encodedClientInitialDynamicStateString);
            } else {
                xformsDecodedInitialClientState = null;
            }
        }

        // Get or create containing document
        final XFormsContainingDocument containingDocument;
        if (XFormsProperties.isCacheDocument()) {
            // Obtain containing document through cache
            containingDocument = XFormsDocumentCache.instance().find(pipelineContext, xformsDecodedClientState.getXFormsState());
        } else {
            // Otherwise we recreate the containing document from scratch
            containingDocument = new XFormsContainingDocument(pipelineContext, xformsDecodedClientState.getXFormsState());
        }

        // The synchronization is tricky: certainly, once we get a document, we don't want to allow multiple threads to
        // it at the same time as a document is clearly not thread-safe. What could happen though in theory is two Ajax
        // requests for the same document, one found in the cache, the other one not (because just expired due to other
        // thread's activity). This would create a new document and would produce unexpected results. However, the
        // client is meant to send only one Ajax request at a time, which should make this case very unlikely.

        // Another situation is that if the ContentHandler is null, then the event is the second pass of a submission
        // with replace="all" and does not modify the Containing Document. The event should in fact go straight to the
        // Submission object. It should therefore be safe not to synchronize in this case. But do we want to take the
        // risk?

//        final Object documentSyncronizationObject = (contentHandler != null) ? containingDocument : new Object();
        synchronized (containingDocument) {
            try {

                // Run events if any
                final Map valueChangeControlIds = new HashMap();
                boolean allEvents = false;
                if (eventElements.size() > 0) {
                    // NOTE: We store here the last xxforms-value-change-with-focus-change event so
                    // we can coalesce values in case several such events are sent for the same
                    // control. The client should not send us such series of events, but currently
                    // it may happen.
                    String lastSourceControlId = null;
                    String lastValueChangeEventValue = null;

                    containingDocument.prepareForExternalEventsSequence(pipelineContext);

                    for (Iterator i = eventElements.iterator(); i.hasNext();) {
                        final Element eventElement = (Element) i.next();
                        final String sourceControlId = eventElement.attributeValue("source-control-id");
                        final String otherControlId = eventElement.attributeValue("other-control-id");
                        final String eventName = eventElement.attributeValue("name");

                        final String dndStart = eventElement.attributeValue("dnd-start");
                        final String dndEnd = eventElement.attributeValue("dnd-end");

                        final String value = eventElement.getText();

                        if (XFormsEvents.XXFORMS_ALL_EVENTS_REQUIRED.equals(eventName)) {
                            // Special event telling us to resend the client all the events since initialization
                            if (xformsDecodedInitialClientState == null)
                                throw new OXFException("Got xxforms-all-events-required event without initial dynamic state.");

                            // Remember that we got this event
                            allEvents = true;

                        } else if (sourceControlId != null && eventName != null) {
                            // An event is passed
                            if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE) && otherControlId == null) {
                                // xxforms-value-change-with-focus-change event
                                if (lastSourceControlId == null) {
                                    // Rember event
                                    lastSourceControlId = sourceControlId;
                                    lastValueChangeEventValue = value;
                                } else if (lastSourceControlId.equals(sourceControlId)) {
                                    // Update event
                                    lastValueChangeEventValue = value;
                                } else {
                                    // Send old event
                                    executeExternalEventHandleDeferredEvents(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement, null, null);
                                    // Remember new event
                                    lastSourceControlId = sourceControlId;
                                    lastValueChangeEventValue = value;
                                }
                            } else {

                                // xxforms-offline requires initial dynamic state
                                if (eventName.equals(XFormsEvents.XXFORMS_OFFLINE) && xformsDecodedInitialClientState == null)
                                    throw new OXFException("Got xxforms-offline event without initial dynamic state.");

                                if (lastSourceControlId != null) {
                                    // Send old event
                                    executeExternalEventHandleDeferredEvents(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement, null, null);
                                    lastSourceControlId = null;
                                    lastValueChangeEventValue = null;
                                }
                                // Send new event
                                executeExternalEventHandleDeferredEvents(pipelineContext, containingDocument, eventName, sourceControlId, otherControlId, value, filesElement, dndStart, dndEnd);
                            }

                            if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
                                // Remember id of control of which value changed
                                valueChangeControlIds.put(sourceControlId, "");
                            }
                        } else if (!(sourceControlId == null && eventName == null)) {
                            throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                        }
                    }
                    // Flush stored event if needed
                    if (lastSourceControlId != null) {
                        // Send old event
                        executeExternalEventHandleDeferredEvents(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement, null, null);
                    }
                }

                if (contentHandler != null) {
                    // Create resulting document if there is a ContentHandler
                    outputResponse(containingDocument, valueChangeControlIds, pipelineContext, contentHandler, xformsDecodedClientState, xformsDecodedInitialClientState, allEvents, false, false, false);
                } else {
                    // This is the second pass of a submission with replace="all". We make it so that the document is
                    // not modified. However, we must then return it to its pool.

                    if (XFormsProperties.isCacheDocument()) {
                        XFormsDocumentCache.instance().add(pipelineContext, xformsDecodedClientState.getXFormsState(), containingDocument);
                    }
                }
            } catch (Throwable e) {
                // If an exception is caught, we need to discard the object as its state may be inconsistent
                final ObjectPool sourceObjectPool = containingDocument.getSourceObjectPool();
                if (sourceObjectPool != null) {
                    logger.debug("XForms - containing document cache: throwable caught, discarding document from pool.");
                    try {
                        sourceObjectPool.invalidateObject(containingDocument);
                        containingDocument.setSourceObjectPool(null);
                    } catch (Exception e1) {
                        throw new OXFException(e1);
                    }
                }
                throw new OXFException(e);
            }
        }
    }

    /**
     * Execute an external event and ensure deferred event handling.
     *
     * @param pipelineContext       current PipelineContext
     * @param containingDocument    XFormsContainingDocument to which events must be dispatched
     * @param eventName             name of the event
     * @param controlId             effective control id to dispatch to
     * @param otherControlId        other effective control id if any
     * @param valueString           optional context string
     * @param filesElement          optional files elements for upload
     */
    private void executeExternalEventHandleDeferredEvents(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String eventName, String controlId, String otherControlId, String valueString, Element filesElement, String dndStart, String dndEnd) {
        containingDocument.startOutermostActionHandler();
        containingDocument.executeExternalEvent(pipelineContext, eventName, controlId, otherControlId, valueString, filesElement, dndStart, dndEnd);
        containingDocument.endOutermostActionHandler(pipelineContext);
    }

    public static void outputResponse(XFormsContainingDocument containingDocument, Map valueChangeControlIds,
                                PipelineContext pipelineContext, ContentHandler contentHandler, XFormsStateManager.XFormsDecodedClientState xformsDecodedClientState,
                                XFormsStateManager.XFormsDecodedClientState xformsDecodedInitialClientState,
                                boolean allEvents, boolean isOfflineEvents, boolean testOutputStaticState, boolean testOutputAllActions) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        try {
            final ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
            ch.startDocument();
            contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

            // Compute automatic events
            boolean requireClientSubmission = false;
            String serverEvents = null;
            {
                final XFormsModelSubmission activeSubmission = containingDocument.getClientActiveSubmission();
                final List loads = containingDocument.getLoadsToRun();
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
                    // Check for xxforms-load event
                    {
                        if (loads != null && loads.size() > 0) {
                            for (Iterator i = loads.iterator(); i.hasNext();) {
                                final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) i.next();

                                if (load.isReplace() && load.isPortletLoad() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType())) {
                                    // We need to submit the event so that the portlet can load the new path
                                    final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
                                    eventElement.addAttribute("source-control-id", XFormsContainingDocument.CONTAINING_DOCUMENT_PSEUDO_ID);
                                    eventElement.addAttribute("resource", load.getResource());
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
                        serverEvents = XFormsUtils.encodeXML(pipelineContext, eventsDocument, false);
                }
            }

            // Rebuild and evaluate controls if needed, before we compute the dynamic state
            // NOTE: This is in case rebuilding controls modifies repeat indexes. We want the indexes to be included in the state further down.that
            if (allEvents || xformsControls.isDirtySinceLastRequest() || testOutputAllActions) {// TODO: Why do we rebuild anyway in case of allEvents?
                xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);
                xformsControls.evaluateAllControlsIfNeeded(pipelineContext);
            }

            // Get encoded state to send to the client (unless computing the list of offline events)
            if (!isOfflineEvents) {
                // State ready to send to the client
                final XFormsState encodedClientState;
                // Whether the incoming state handling mode is different from the outgoing state handling mode
                final boolean isMustChangeStateHandling;
                if (containingDocument.goingOffline()) {
                    // We force client state if we are going offline, and do not cache as it is likely that the result won't be used soon
                    encodedClientState = XFormsStateManager.getEncryptedSerializedClientState(containingDocument, pipelineContext, xformsDecodedClientState);
                    // Outgoing mode is always "client", so in effect we are changing when the regular mode is not client
                    isMustChangeStateHandling = !xformsDecodedClientState.isClientStateHandling();
                } else {
                    // This will also cache the containing document if needed
                    encodedClientState = XFormsStateManager.getEncodedClientStateDoCache(containingDocument, pipelineContext, xformsDecodedClientState, allEvents);
                    isMustChangeStateHandling = xformsDecodedClientState.isClientStateHandling() != XFormsProperties.isClientStateHandling(containingDocument);
                }

                // Output static state when changing state handling mode, or when testing
                if (isMustChangeStateHandling || testOutputStaticState) {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "static-state", new String[] { "container-type", externalContext.getRequest().getContainerType() });
                    // NOTE: Should output static state the same way as XFormsToXHTML does, but it's just for tests for for now it's ok
                    ch.text(encodedClientState.getStaticState());
                    ch.endElement();
                }

                // Output dynamic state
                {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic-state");
                    ch.text(encodedClientState.getDynamicState());
                    ch.endElement();
                }
            }

            // Output action
            {
                final XFormsContainingDocument initialContainingDocument;
                if (xformsDecodedInitialClientState == null) {
                    initialContainingDocument = null;
                } else {
                    initialContainingDocument = new XFormsContainingDocument(pipelineContext, xformsDecodedInitialClientState.getXFormsState());
                }

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");

                // Output new controls values and associated information
                final Map itemsetsFull1 = new HashMap();
                final Map itemsetsFull2 = new HashMap();
                {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                    if (!allEvents) {
                        // Common case

                        // Only output changes if needed
                        if (xformsControls.isDirtySinceLastRequest() || testOutputAllActions) {
                            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();
                            diffControls(pipelineContext, ch, containingDocument, testOutputAllActions ? null : xformsControls.getInitialControlsState().getChildren(), currentControlsState.getChildren(), itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                        }
                    } else {
                        // Reload / back case: diff between current state and initial state as obtained from initial dynamic state
                        final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();
                        final XFormsControls.ControlsState initialControlsState = initialContainingDocument.getXFormsControls().getCurrentControlsState();
                        diffControls(pipelineContext, ch, containingDocument, initialControlsState.getChildren(), currentControlsState.getChildren(), itemsetsFull1, itemsetsFull2, null);
                    }

                    ch.endElement();
                }

                // Output divs information
                {
                    if (!allEvents) {
                        if (containingDocument.isDirtySinceLastRequest()) {
                            // Only diff if we are dirty
                            diffDivs(ch, xformsControls, testOutputAllActions ? null : xformsControls.getInitialSwitchState(), xformsControls.getCurrentSwitchState(), xformsControls.getInitialDialogState(), xformsControls.getCurrentDialogState());
                        }
                    } else {
                        diffDivs(ch, xformsControls, initialContainingDocument.getXFormsControls().getCurrentSwitchState(), xformsControls.getCurrentSwitchState(),
                                initialContainingDocument.getXFormsControls().getCurrentDialogState(), xformsControls.getCurrentDialogState());
                    }
                }

                // Output repeat indexes information
                {
                    // Output index updates
                    // TODO: move index state out of ControlsState + handle diffs
                    if (!allEvents) {
                        if (!testOutputAllActions) {
                            if (xformsControls.isDirtySinceLastRequest()) {
                                // Only diff if controls are dirty
                                diffIndexState(ch, xformsControls.getInitialControlsState().getRepeatIdToIndex(), xformsControls.getCurrentControlsState().getRepeatIdToIndex());
                            }
                        } else {
                            testOutputInitialRepeatInfo(ch, xformsControls.getCurrentControlsState());
                        }
                    } else {
                        final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();
                        final XFormsControls.ControlsState initialControlsState = initialContainingDocument.getXFormsControls().getCurrentControlsState();
                        diffIndexState(ch, initialControlsState.getRepeatIdToIndex(), currentControlsState.getRepeatIdToIndex());
                    }
                }

                // Output itemset information
                if (allEvents || xformsControls.isDirtySinceLastRequest()) {
                    // Diff itemset information
                    final Map itemsetUpdate = diffItemsets(itemsetsFull1, itemsetsFull2);
                    // TODO: handle allEvents case
                    outputItemsets(ch, itemsetUpdate);
                }

                // Output automatic events
                if (serverEvents != null) {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events");
                    ch.text(serverEvents);
                    ch.endElement();
                }

                // Check if we want to require the client to perform a form submission
                if (requireClientSubmission) {
                    outputSubmissionInfo(externalContext, ch, containingDocument.getClientActiveSubmission());
                }

                // TODO: the following should be correctly ordered in the order they were requested
                // Output messages to display
                {
                    final List messages = containingDocument.getMessagesToRun();
                    if (messages != null) {
                        outputMessagesInfo(ch, messages);
                    }
                }

                // Output loads
                {
                    final List loads = containingDocument.getLoadsToRun();
                    if (loads != null) {
                        outputLoadsInfo(ch, loads);
                    }
                }

                // Output scripts
                {
                    final List scripts = containingDocument.getScriptsToRun();
                    if (scripts != null) {
                        outputScriptsInfo(ch, scripts);
                    }
                }

                // Output focus instruction
                {
                    final String focusEffectiveControlId = containingDocument.getClientFocusEffectiveControlId();
                    if (focusEffectiveControlId != null) {
                        outputFocusInfo(ch, focusEffectiveControlId);
                    }
                }

                // Output help instruction
                {
                    final String helpEffectiveControlId = containingDocument.getClientHelpEffectiveControlId();
                    if (helpEffectiveControlId != null) {
                        outputHelpInfo(ch, helpEffectiveControlId);
                    }
                }

                // Output go offline instruction (unless computing the list of offline events)
                {
                    if (containingDocument.goingOffline() && !isOfflineEvents) {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "offline");

                        // We send all the changes between the initial state and the time the form go offline, so when
                        // reloading the page the client can apply those changes when the page is reloaded from the
                        // client-side database.

                        final StringWriter writer = new StringWriter();
                        final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                        identity.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");// no XML decl.
                        identity.setResult(new StreamResult(writer));

                        // Output response by asking for all events, and passing a flag telling that we are processing
                        // offline events so as to avoid recursion
                        outputResponse(containingDocument, valueChangeControlIds, pipelineContext, identity, xformsDecodedClientState, xformsDecodedInitialClientState, true, true, false, false);

                        // Output serialized list of events
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "events");
                        ch.text(writer.toString());
                        ch.endElement();

                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "mappings");
                        final String offlineBindMappings = XFormsModelBinds.getOfflineBindMappings(containingDocument);
                        ch.text(offlineBindMappings);
                        ch.endElement();

                        ch.endElement();
                    }
                }

                ch.endElement();
            }

            ch.endElement();
            contentHandler.endPrefixMapping("xxf");
            ch.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    private static void diffIndexState(ContentHandlerHelper ch, Map initialRepeatIdToIndex, Map currentRepeatIdToIndex) {
        if (currentRepeatIdToIndex.size() != 0) {
            boolean found = false;
            for (Iterator i = currentRepeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String repeatId = (String) currentEntry.getKey();
                final Integer newIndex = (Integer) currentEntry.getValue();

                // Output information if there is a difference
                final Integer oldIndex = (Integer) initialRepeatIdToIndex.get(repeatId);
                if (!newIndex.equals(oldIndex)) {

                    if (!found) {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-indexes");
                        found = true;
                    }

                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-index",
                            new String[] {"id", repeatId, "old-index", oldIndex.toString(), "new-index", newIndex.toString()});
                }
            }
            if (found)
                ch.endElement();
        }
    }

    private static void testOutputInitialRepeatInfo(ContentHandlerHelper ch, XFormsControls.ControlsState controlsState) {


        final Map initialRepeatIdToIndex = controlsState.getRepeatIdToIndex();
        final Map effectiveRepeatIdToIterations = controlsState.getEffectiveRepeatIdToIterations();

        if (initialRepeatIdToIndex.size() != 0 || effectiveRepeatIdToIterations != null) {

            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeats");

            // Output repeat index information

            if (initialRepeatIdToIndex != null) {
                for (Iterator i = initialRepeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String repeatId = (String) currentEntry.getKey();
                    final Integer index = (Integer) currentEntry.getValue();

                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-index",
                        new String[]{"id", repeatId, "index", index.toString()});
                }
            }

            // Output repeat iteration information

            if (effectiveRepeatIdToIterations != null) {
                for (Iterator i = effectiveRepeatIdToIterations.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String effectiveRepeatId = (String) currentEntry.getKey();
                    final Integer iterations = (Integer) currentEntry.getValue();

                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration",
                        new String[]{"id", effectiveRepeatId, "occurs", iterations.toString()});
                }
            }

            ch.endElement();
        }
    }

    public static Map diffItemsets(Map itemsetsFull1, Map itemsetsFull2) {
        Map itemsetUpdate;
        if (itemsetsFull2 == null) {
            // There is no update in the first place
            itemsetUpdate = null;
        } else if (itemsetsFull1 == null) {
            // There was nothing before, return update
            itemsetUpdate = itemsetsFull2;
        } else {
            // Merge differences
            itemsetUpdate = new HashMap();

            for (Iterator i = itemsetsFull2.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List newItems = (List) currentEntry.getValue();

                final List existingItems = (List) itemsetsFull1.get(itemsetId);
                if (existingItems == null || !existingItems.equals(newItems)) {
                    // No existing items or new items are different from existing items
                    itemsetUpdate.put(itemsetId, newItems);
                }
            }
        }
        return itemsetUpdate;
    }

    public static void diffControls(PipelineContext pipelineContext, ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List state1, List state2, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        if (XFormsProperties.isOptimizeRelevance(containingDocument)) {
            new NewControlsComparator(pipelineContext, ch, containingDocument, itemsetsFull1, itemsetsFull2, valueChangeControlIds).diff(state1, state2);
        } else {
            new OldControlsComparator(pipelineContext, ch, containingDocument, itemsetsFull1, itemsetsFull2, valueChangeControlIds).diff(state1, state2);
        }
    }

    private static void outputSubmissionInfo(ExternalContext externalContext, ContentHandlerHelper ch, XFormsModelSubmission activeSubmission) {
        final String clientSubmisssionURL;
        final String target;
        if ("all".equals(activeSubmission.getReplace())) {
            // Replace all

            // The submission path is actually defined by the oxf:page-flow processor and its configuration
            OXFProperties.PropertySet propertySet = OXFProperties.instance().getPropertySet(XMLConstants.PAGE_FLOW_PROCESSOR_QNAME);
            final String submissionPath = propertySet.getString(PageFlowControllerProcessor.XFORMS_SUBMISSION_PATH_PROPERTY_NAME,
                    PageFlowControllerProcessor.XFORMS_SUBMISSION_PATH_DEFAULT_VALUE);

            clientSubmisssionURL = externalContext.getResponse().rewriteResourceURL(submissionPath, false);
            target = activeSubmission.getResolvedXXFormsTarget();
        } else {
            // Replace instance
            clientSubmisssionURL = externalContext.getRequest().getRequestURL();
            target = null;
        }

        // Signal that we want a POST to the XForms Server
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "submission",
                new String[]{"action", clientSubmisssionURL, "method", "POST",
                        "show-progress", (activeSubmission == null || activeSubmission.isXxfShowProgress()) ? null : "false",
                        "replace", activeSubmission.getReplace(),
                        (target != null) ? "target" : null, target
                });
    }

    private static void outputMessagesInfo(ContentHandlerHelper ch, List messages) {
        for (Iterator i = messages.iterator(); i.hasNext();) {
            final XFormsContainingDocument.Message message = (XFormsContainingDocument.Message) i.next();
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "message",
                    new String[]{"level", message.getLevel()});
            ch.text(message.getMessage());
            ch.endElement();
        }
    }

    public static void outputLoadsInfo(ContentHandlerHelper ch, List loads) {
        for (Iterator i = loads.iterator(); i.hasNext();) {
            final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) i.next();

            if (!(load.isReplace() && load.isPortletLoad() && !NetUtils.urlHasProtocol(load.getResource()) && !"resource".equals(load.getUrlType()))) {
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "load",
                        new String[]{ "resource", load.getResource(), (load.getTarget() != null) ? "target" : null, load.getTarget(), "show", load.isReplace() ? "replace" : "new", "show-progress", load.isShowProgress() ? null : "false" });
            }
        }
    }

    public static void outputScriptsInfo(ContentHandlerHelper ch, List scripts) {
        for (Iterator i = scripts.iterator(); i.hasNext();) {
            final XFormsContainingDocument.Script script = (XFormsContainingDocument.Script) i.next();
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "script",
                    new String[]{ "name", script.getFunctionName(), "target-id", script.getEventTargetId(), "observer-id", script.getEventHandlerContainerId() });
        }
    }

    private static void outputFocusInfo(ContentHandlerHelper ch, String focusEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "setfocus",
                new String[]{"control-id", focusEffectiveControlId});
    }

    private static void outputHelpInfo(ContentHandlerHelper ch, String helpEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "help",
                new String[]{"control-id", helpEffectiveControlId});
    }

    private static void outputItemsets(ContentHandlerHelper ch, Map itemsetIdToItemsetInfoMap) {
        if (itemsetIdToItemsetInfoMap != null && itemsetIdToItemsetInfoMap.size() > 0) {
            // There are some xforms:itemset controls

            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemsets");
            for (Iterator i = itemsetIdToItemsetInfoMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List items = (List) currentEntry.getValue();

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[]{"id", itemsetId});
                final String result = XFormsItemUtils.getJSONTreeInfo(items, null);// TODO: pass LocationData
                if (result.length() > 0)
                    ch.text(result);
                ch.endElement();
            }
            ch.endElement();
        }
    }

    public static void diffDivs(ContentHandlerHelper ch, XFormsControls xformsControls, XFormsControls.SwitchState switchState1, XFormsControls.SwitchState switchState2,
                                XFormsControls.DialogState dialogState1, XFormsControls.DialogState dialogState2) {

        boolean found = false;
        {
            final Map switchIdToSelectedCaseIdMap2 = switchState2.getSwitchIdToSelectedCaseIdMap();
            if (switchIdToSelectedCaseIdMap2 != null) {
                // There are some xforms:switch/xforms:case controls

                // Obtain previous state
                final Map switchIdToSelectedCaseIdMap1 = (switchState1 == null) ? new HashMap(): switchState1.getSwitchIdToSelectedCaseIdMap();

                // Iterate over all the switches
                for (Iterator i = switchIdToSelectedCaseIdMap2.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String switchId = (String) currentEntry.getKey();
                    final String selectedCaseId = (String) currentEntry.getValue();

                    // Only output the information if it has changed
                    final String previousSelectedCaseId = (String) switchIdToSelectedCaseIdMap1.get(switchId);
                    if (!selectedCaseId.equals(previousSelectedCaseId)) {

                        if (!found) {
                            // Open xxf:divs element
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                            found = true;
                        }

                        // Output selected case id
                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                "id", selectedCaseId,
                                "visibility", "visible"
                        });

                        if (previousSelectedCaseId != null) {
                            // Output deselected case ids
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                    "id", previousSelectedCaseId,
                                    "visibility", "hidden"}
                            );
                        } else {
                            // This is a new switch (can happen with repeat), send all deselected to be sure
                            final XFormsControl switchXFormsControl = (XFormsControl) xformsControls.getObjectById(switchId);
                            final List children = switchXFormsControl.getChildren();
                            if (children != null && children.size() > 0) {
                                for (Iterator j = children.iterator(); j.hasNext();) {
                                    final XFormsControl caseXFormsControl = (XFormsControl) j.next();

                                    if (!caseXFormsControl.getEffectiveId().equals(selectedCaseId)) {
                                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{
                                                "id", caseXFormsControl.getEffectiveId(),
                                                "visibility", "hidden"
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        {
            final Map dialogIdToVisibleMap2 = dialogState2.getDialogIdToVisibleMap();
            if (dialogIdToVisibleMap2 != null) {
                // There are some xxforms:dialog controls

                // Obtain previous state
                final Map dialogIdToVisibleMap1 = dialogState1.getDialogIdToVisibleMap();

                // Iterate over all the dialogs
                for (Iterator i = dialogIdToVisibleMap2.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String dialogId = (String) currentEntry.getKey();

                    final XFormsControls.DialogState.DialogInfo newDialogInfo
                            = (XFormsControls.DialogState.DialogInfo) currentEntry.getValue();

                    // Only output the information if it has changed
                    final XFormsControls.DialogState.DialogInfo previousDialogInfo
                            = (XFormsControls.DialogState.DialogInfo) dialogIdToVisibleMap1.get(dialogId);

                    if (newDialogInfo.isShow() != previousDialogInfo.isShow()) {// NOTE: We only compare on show as we con't support just changing the neighbor
                        // There is a difference

                        if (!found) {
                            // Open xxf:divs element
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                            found = true;
                        }

                        // Find neighbor if any, first on xxforms:show, then on xxforms:dialog
                        final XXFormsDialogControl dialogControl = newDialogInfo.isShow() ? (XXFormsDialogControl) xformsControls.getObjectById(dialogId) : null;
                        final String neighbor = !newDialogInfo.isShow()
                                ? null : (newDialogInfo.getNeighbor() != null)
                                ? newDialogInfo.getNeighbor() : (dialogControl != null) ? dialogControl.getNeighborControlId() : null;
                        final boolean constrainToViewport = newDialogInfo.isConstrainToViewport();

                        // Output element
                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] {
                                "id", dialogId,
                                "visibility", newDialogInfo.isShow() ? "visible" : "hidden",
                                (neighbor != null) ? "neighbor" : null, neighbor,
                                "constrain", Boolean.toString(constrainToViewport)
                        });
                    }
                }
            }
        }

        // Close xxf:divs element if needed
        if (found)
            ch.endElement();
    }
}
