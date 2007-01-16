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
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * The XForms Server processor handles client requests, including events, and either returns an XML
 * response, or returns a response through the ExternalContext.
 */
public class XFormsServer extends ProcessorImpl {

    static public Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    //private static final String OUTPUT_RESPONSE = "response"; // optional

    public static final String APPLICATION_STATE_PREFIX = "application:";
    public static final String SESSION_STATE_PREFIX = "session:";

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
     * Case where the response is generated throug the ExternalContext.
     */
    public void start(PipelineContext pipelineContext) {
        doIt(pipelineContext, null);
    }

    private void doIt(final PipelineContext pipelineContext, ContentHandler contentHandler) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        final Element filesElement;
        final Element actionElement;
        final Element serverEventsElement;
        final XFormsContainingDocument containingDocument;
        final XFormsState xformsState;
        final String staticStateUUID;

        // Use request input provided by client
        final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

        // Get action
        actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

        // Get files if any (those come from xforms-server-submit.xpl upon submission)
        filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

        // Get server events if any
        serverEventsElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_SERVER_EVENTS_QNAME);

        // Retrieve state
        {
            // Get static state
            final String staticStateString;
            {
                final Element staticStateElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_STATIC_STATE_QNAME);
                staticStateString = staticStateElement.getTextTrim();
            }

            // Get dynamic state
            final String dynamicStateString;
            {
                final Element dynamicStateElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_DYNAMIC_STATE_QNAME);
                dynamicStateString = dynamicStateElement.getTextTrim();
            }
            
            if (dynamicStateString.startsWith(APPLICATION_STATE_PREFIX)) {
                //  State is currently stored in the application scope
                final String dynamicStateUUID = dynamicStateString.substring(APPLICATION_STATE_PREFIX.length());

                // Extract page generation id
                staticStateUUID = staticStateString.substring(APPLICATION_STATE_PREFIX.length());

                // We don't create the session cache at this point as it may not be necessary
                final XFormsServerApplicationStateCache applicationStateCache = XFormsServerApplicationStateCache.instance(externalContext, false);
                final XFormsState applicationFormsState = applicationStateCache.find(staticStateUUID, dynamicStateUUID);

                // This is not going to be good when it happens, and we must create a caching heuristic that minimizes this
                if (applicationFormsState == null)
                    throw new OXFException("Unable to retrieve XForms engine state from application cache.");

                xformsState = applicationFormsState;

            } else if (dynamicStateString.startsWith(SESSION_STATE_PREFIX)) {
                // State doesn't come with the request, we should look it up in the repository
                final String dynamicStateUUID = dynamicStateString.substring(SESSION_STATE_PREFIX.length());

                // Extract page generation id
                staticStateUUID = staticStateString.startsWith(SESSION_STATE_PREFIX)
                        ? staticStateString.substring(SESSION_STATE_PREFIX.length())
                        : staticStateString.substring(APPLICATION_STATE_PREFIX.length());

                // We don't create the session cache at this point as it may not be necessary
                final XFormsServerSessionStateCache sessionStateCache = XFormsServerSessionStateCache.instance(externalContext.getSession(false), false);
                final XFormsState sessionFormsState = (sessionStateCache == null) ? null : sessionStateCache.find(staticStateUUID, dynamicStateUUID);

                // This is not going to be good when it happens, and we must create a caching heuristic that minimizes this
                if (sessionFormsState == null)
                    throw new OXFException("Unable to retrieve XForms engine state from session cache.");

                xformsState = sessionFormsState;
            } else {
                // State comes with request
                staticStateUUID = null;
                xformsState = new XFormsState(staticStateString, dynamicStateString);
            }
        }

        if (XFormsUtils.isCacheDocument()) {
            // Try to obtain containing document from cache
            containingDocument = XFormsServerDocumentCache.instance().find(pipelineContext, xformsState);
        } else {
            // Otherwise we recreate the containing document from scratch
            containingDocument = new XFormsContainingDocument(pipelineContext, xformsState);
        }

        try {
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

            // Run events if any
            boolean allEvents = false;
            final Map valueChangeControlIds = new HashMap();
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
                    final String value = eventElement.getText();

                    if (XFormsEvents.XXFORMS_ALL_EVENTS_REQUIRED.equals(eventName)) {
                        // Special event telling us to resend the client all the events since initialization
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
                                executeExternalEventPrepareIfNecessary(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement);
                                // Remember new event
                                lastSourceControlId = sourceControlId;
                                lastValueChangeEventValue = value;
                            }
                        } else {
                            if (lastSourceControlId != null) {
                                // Send old event
                                executeExternalEventPrepareIfNecessary(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement);
                                lastSourceControlId = null;
                                lastValueChangeEventValue = null;
                            }
                            // Send new event
                            executeExternalEventPrepareIfNecessary(pipelineContext, containingDocument, eventName, sourceControlId, otherControlId, value, filesElement);
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
                    executeExternalEventPrepareIfNecessary(pipelineContext, containingDocument, XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, lastSourceControlId, null, lastValueChangeEventValue, filesElement);
                }
            }

            // Create resulting document if there is a ContentHandler
            if (contentHandler != null) {
                outputResponse(containingDocument, allEvents, valueChangeControlIds, pipelineContext, contentHandler,
                        staticStateUUID, externalContext, xformsState);
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

    /**
     * Execute an external event while preparing containing document and controls state if an event
     * was already executed.
     */
    private void executeExternalEventPrepareIfNecessary(PipelineContext pipelineContext, XFormsContainingDocument containingDocument, String eventName, String controlId, String otherControlId, String contextString, Element filesElement) {
        containingDocument.startOutermostActionHandler();
        containingDocument.executeExternalEvent(pipelineContext, eventName, controlId, otherControlId, contextString, filesElement);
        containingDocument.endOutermostActionHandler(pipelineContext);
    }

    private void outputResponse(XFormsContainingDocument containingDocument, boolean allEvents, Map valueChangeControlIds,
                                PipelineContext pipelineContext, ContentHandler contentHandler, String requestPageGenerationId,
                                ExternalContext externalContext, XFormsState xformsState) {

        final XFormsControls xformsControls = containingDocument.getXFormsControls();

        try {
            final ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
            ch.startDocument();
            contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

            // Output state
            {
                // Produce static state if needed
                final String currentPageGenerationId = (requestPageGenerationId != null) ? requestPageGenerationId : UUIDUtils.createPseudoUUID();

                // Create and encode dynamic state
                final String newEncodedDynamicState = containingDocument.createEncodedDynamicState(pipelineContext);
                final XFormsState newXFormsState = new XFormsState(xformsState.getStaticState(), newEncodedDynamicState);

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic-state");
                if (containingDocument.isSessionStateHandling()) {
                    // Produce dynamic state key
                    final String newRequestId = UUIDUtils.createPseudoUUID();
                    final XFormsServerSessionStateCache sessionStateCache = XFormsServerSessionStateCache.instance(externalContext.getSession(true), true);
                    sessionStateCache.add(currentPageGenerationId, newRequestId, newXFormsState);
                    ch.text(SESSION_STATE_PREFIX + newRequestId);
                } else {
                    // Send state to the client
                    ch.text(newEncodedDynamicState);
                }
                ch.endElement();

                // Cache document if requested and possible
                if (XFormsUtils.isCacheDocument()) {
                    XFormsServerDocumentCache.instance().add(pipelineContext, newXFormsState, containingDocument);
                }
            }

            // Output action
            {
                final XFormsContainingDocument initialContainingDocument;
                if (!allEvents) {
                    initialContainingDocument = null;
                } else {
                    // TODO: use cached static state if possible
                    initialContainingDocument = new XFormsContainingDocument(pipelineContext, new XFormsState(xformsState.getStaticState(), null));
                    initialContainingDocument.getXFormsControls().rebuildCurrentControlsStateIfNeeded(pipelineContext);
                }

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");

                // Output new controls values and associated information
                final Map itemsetsFull1 = new HashMap();
                final Map itemsetsFull2 = new HashMap();
                {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                    if (!allEvents) {
                        // Common case

                        if (xformsControls.isDirty()) {
                            // Only output changes if needed
                            xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);
                            final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

                            diffControlsState(ch, containingDocument,  xformsControls.getInitialControlsState().getChildren(), currentControlsState.getChildren(), itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                        }
                    } else {
                        // Reload / back case
                        xformsControls.rebuildCurrentControlsStateIfNeeded(pipelineContext);
                        final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();
                        final XFormsControls.ControlsState initialControlsState = initialContainingDocument.getXFormsControls().getCurrentControlsState();

                        // Output diffs
                        diffControlsState(ch, containingDocument, initialControlsState.getChildren(), currentControlsState.getChildren(), itemsetsFull1, itemsetsFull2, null);
                    }

                    ch.endElement();
                }

                // Output divs information
                {
                    if (!allEvents) {
                        diffDivs(ch, xformsControls, xformsControls.getInitialSwitchState(), xformsControls.getCurrentSwitchState(), xformsControls.getInitialDialogState(), xformsControls.getCurrentDialogState());
                    } else {
                        diffDivs(ch, xformsControls, initialContainingDocument.getXFormsControls().getCurrentSwitchState(), xformsControls.getCurrentSwitchState(),
                                initialContainingDocument.getXFormsControls().getCurrentDialogState(), xformsControls.getCurrentDialogState());
                    }
                }

                // Output repeats information
                {
                    // Output index updates
                    // TODO: move index state out of ControlsState + handle diffs

                    if (!allEvents) {
                        diffIndexState(ch, xformsControls.getInitialControlsState().getRepeatIdToIndex(), xformsControls.getCurrentControlsState().getRepeatIdToIndex());
                    } else {
                        final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();
                        final XFormsControls.ControlsState initialControlsState = initialContainingDocument.getXFormsControls().getCurrentControlsState();
                        diffIndexState(ch, initialControlsState.getRepeatIdToIndex(), currentControlsState.getRepeatIdToIndex());
                    }
                }

                // Output itemset information
                {
                    // Diff itemset information
                    final Map itemsetUpdate = diffItemsets(itemsetsFull1, itemsetsFull2);
                    // TODO: handle allEvents case
                    outputItemsets(ch, itemsetUpdate);
                }

                boolean requireClientSubmission = false;
                // Output automatic events
                {
                    final XFormsModelSubmission activeSubmission = containingDocument.getActiveSubmission();
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
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events");
                        ch.text(XFormsUtils.encodeXML(pipelineContext, eventsDocument));
                        ch.endElement();

                    }
                }

                // Check if we want to require the client to perform a form submission
                {
                    if (requireClientSubmission)
                        outputSubmissionInfo(externalContext, ch, containingDocument.getActiveSubmission());
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

                // Output focus instructions
                {
                    final String focusEffectiveControlId = containingDocument.getClientFocusEffectiveControlId(pipelineContext);
                    if (focusEffectiveControlId != null) {
                        outputFocusInfo(ch, focusEffectiveControlId);
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

    private void diffIndexState(ContentHandlerHelper ch, Map initialRepeatIdToIndex, Map currentRepeatIdToIndex) {
        if (currentRepeatIdToIndex.size() != 0) {
            boolean found = false;
            for (Iterator i = initialRepeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String repeatId = (String) currentEntry.getKey();
                final Integer index = (Integer) currentEntry.getValue();

                // Output information if there is a difference
                final Integer newIndex = (Integer) currentRepeatIdToIndex.get(repeatId);
                if (!index.equals(newIndex)) {

                    if (!found) {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-indexes");
                        found = true;
                    }

                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-index",
                            new String[] {"id", repeatId, "old-index", index.toString(), "new-index", newIndex.toString()});
                }
            }
            if (found)
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

    public static void diffControlsState(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List state1, List state2, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {
        if (XFormsUtils.isOptimizeRelevance())
            newDiffControlsState(ch, containingDocument, state1, state2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
        else
            oldDiffControlsState(ch, containingDocument, state1, state2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
    }

    public static void newDiffControlsState(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List state1, List state2, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {

        // Normalize
        if (state1 != null && state1.size() == 0)
            state1 = null;
        if (state2 != null && state2.size() == 0)
            state2 = null;

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Only state1 can be null
//        if (state2 == null) {
//            throw new IllegalStateException("Illegal state when comparing controls.");
//        }
        // Both lists should have the same size if present, except when grouping controls become relevant/non-relevant,
        // in which case one of the containing controls may contain 0 children
        if (state1 != null && state2 != null && state1.size() != state2.size()) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        {
            final boolean isStaticReadonly = containingDocument.getReadonlyAppearance().equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE);
            final AttributesImpl attributesImpl = new AttributesImpl();
            
            final Iterator leftIterator = (state1 == null) ? null : state1.iterator();
            final Iterator rightIterator = (state2 == null) ? null : state2.iterator();
            final Iterator leadingIterator = (rightIterator != null) ? rightIterator : leftIterator;

            while (leadingIterator.hasNext()) {
                final XFormsControl xformsControl1 = (leftIterator == null) ? null : (XFormsControl) leftIterator.next();
                final XFormsControl xformsControl2 = (rightIterator == null) ? null : (XFormsControl) rightIterator.next();

                final XFormsControl leadingControl = (xformsControl2 != null) ? xformsControl2 : xformsControl1;

                // 1: Check current control
                if (!(leadingControl instanceof XFormsRepeatControl || leadingControl instanceof XFormsCaseControl)) {
                    // xforms:repeat doesn't need to be handled independently, iterations do it

                    // Output diffs between controlInfo1 and controlInfo2

                    // Control id
                    attributesImpl.clear();
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, leadingControl.getEffectiveId());

                    final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.get(leadingControl.getEffectiveId()) != null;
                    if (xformsControl2 != null) {

                        // Notify the client that this control must be static readonly in case it just appeared
                        if (xformsControl1 == null && xformsControl2.isStaticReadonly() && xformsControl2.isRelevant())
                            attributesImpl.addAttribute("", "static", "static", ContentHandlerHelper.CDATA, "true");

                        if ((!xformsControl2.equals(xformsControl1) || isValueChangeControl) && !(isStaticReadonly && xformsControl2.isReadonly() && xformsControl2 instanceof XFormsTriggerControl)) {
                            // Don't send anything if nothing has changed
                            // But we force a change for controls whose values changed in the request
                            // Also, we don't output anything for triggers in static readonly mode

                            // Control children values
                            if (!(xformsControl2 instanceof RepeatIterationControl)) {
                                {
                                    final String labelValue1 = (xformsControl1 == null) ? null : xformsControl1.getLabel();
                                    final String labelValue2 = xformsControl2.getLabel();

                                    if (!((labelValue1 == null && labelValue2 == null) || (labelValue1 != null && labelValue2 != null && labelValue1.equals(labelValue2)))) {
                                        attributesImpl.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, labelValue2 != null ? labelValue2 : "");
                                    }
                                }

                                {
                                    final String helpValue1 = (xformsControl1 == null) ? null : xformsControl1.getHelp();
                                    final String helpValue2 = xformsControl2.getHelp();

                                    if (!((helpValue1 == null && helpValue2 == null) || (helpValue1 != null && helpValue2 != null && helpValue1.equals(helpValue2)))) {
                                        attributesImpl.addAttribute("", "help", "help", ContentHandlerHelper.CDATA, helpValue2 != null ? helpValue2 : "");
                                    }
                                }

                                {
                                    final String hintValue1 = (xformsControl1 == null) ? null : xformsControl1.getHint();
                                    final String hintValue2 = xformsControl2.getHint();

                                    if (!((hintValue1 == null && hintValue2 == null) || (hintValue1 != null && hintValue2 != null && hintValue1.equals(hintValue2)))) {
                                        attributesImpl.addAttribute("", "hint", "hint", ContentHandlerHelper.CDATA, hintValue2 != null ? hintValue2 : "");
                                    }
                                }

                                {
                                    final String alertValue1 = (xformsControl1 == null) ? null : xformsControl1.getAlert();
                                    final String alertValue2 = xformsControl2.getAlert();

                                    if (!((alertValue1 == null && alertValue2 == null) || (alertValue1 != null && alertValue2 != null && alertValue1.equals(alertValue2)))) {
                                        attributesImpl.addAttribute("", "alert", "alert", ContentHandlerHelper.CDATA, alertValue2 != null ? alertValue2 : "");
                                    }
                                }

                                // Output xforms:output-specific information
                                if (xformsControl2 instanceof XFormsOutputControl) {
                                    final XFormsOutputControl outputControlInfo1 = (XFormsOutputControl) xformsControl1;
                                    final XFormsOutputControl outputControlInfo2 = (XFormsOutputControl) xformsControl2;

                                    final String mediaTypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediatypeAttribute();
                                    final String mediaTypeValue2 = outputControlInfo2.getMediatypeAttribute();

                                    if (!((mediaTypeValue1 == null && mediaTypeValue2 == null) || (mediaTypeValue1 != null && mediaTypeValue2 != null && mediaTypeValue1.equals(mediaTypeValue2)))) {
                                        attributesImpl.addAttribute("", "mediatype", "mediatype", ContentHandlerHelper.CDATA, mediaTypeValue2 != null ? mediaTypeValue2 : "");
                                    }
                                }
                                
                                // Output xforms:upload-specific information
                                if (xformsControl2 instanceof XFormsUploadControl) {
                                    final XFormsUploadControl uploadControlInfo1 = (XFormsUploadControl) xformsControl1;
                                    final XFormsUploadControl uploadControlInfo2 = (XFormsUploadControl) xformsControl2;

                                    {
                                        // State
                                        final String stateValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getState();
                                        final String stateValue2 = uploadControlInfo2.getState();

                                        if (!((stateValue1 == null && stateValue2 == null) || (stateValue1 != null && stateValue2 != null && stateValue1.equals(stateValue2)))) {
                                            attributesImpl.addAttribute("", "state", "state", ContentHandlerHelper.CDATA, stateValue2 != null ? stateValue2 : "");
                                        }
                                    }
                                    {
                                        // Mediatype
                                        final String mediatypeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getMediatype();
                                        final String mediatypeValue2 = uploadControlInfo2.getMediatype();

                                        if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
                                            attributesImpl.addAttribute("", "mediatype", "mediatype", ContentHandlerHelper.CDATA, mediatypeValue2 != null ? mediatypeValue2 : "");
                                        }
                                    }
                                    {
                                        // Filename
                                        final String filenameValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getFilename();
                                        final String filenameValue2 = uploadControlInfo2.getFilename();

                                        if (!((filenameValue1 == null && filenameValue2 == null) || (filenameValue1 != null && filenameValue2 != null && filenameValue1.equals(filenameValue2)))) {
                                            attributesImpl.addAttribute("", "filename", "filename", ContentHandlerHelper.CDATA, filenameValue2 != null ? filenameValue2 : "");
                                        }
                                    }
                                    {
                                        // Size
                                        final String sizeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getSize();
                                        final String sizeValue2 = uploadControlInfo2.getSize();

                                        if (!((sizeValue1 == null && sizeValue2 == null) || (sizeValue1 != null && sizeValue2 != null && sizeValue1.equals(sizeValue2)))) {
                                            attributesImpl.addAttribute("", "size", "size", ContentHandlerHelper.CDATA, sizeValue2 != null ? sizeValue2 : "");
                                        }
                                    }

                                }
                            }

                            // Model item properties
                            if (xformsControl1 == null || xformsControl1.isReadonly() != xformsControl2.isReadonly()) {
                                attributesImpl.addAttribute("", XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isReadonly()));
                            }
                            if (xformsControl1 == null || xformsControl1.isRequired() != xformsControl2.isRequired()) {
                                attributesImpl.addAttribute("", XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isRequired()));
                            }
                            if (xformsControl1 == null || xformsControl1.isRelevant() != xformsControl2.isRelevant()) {
                                attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isRelevant()));
                            }
                            if (xformsControl1 == null || xformsControl1.isValid() != xformsControl2.isValid()) {
                                attributesImpl.addAttribute("", XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isValid()));
                            }

                            final boolean isOutputControlWithValueAttribute = xformsControl2 instanceof XFormsOutputControl && ((XFormsOutputControl) xformsControl2).getValueAttribute() != null;
                            if (!(xformsControl2 instanceof RepeatIterationControl) && !isOutputControlWithValueAttribute) {

                                final String typeValue1 = (xformsControl1 == null) ? null : xformsControl1.getType();
                                final String typeValue2 = xformsControl2.getType();

                                if (xformsControl1 == null || !((typeValue1 == null && typeValue2 == null) || (typeValue1 != null && typeValue2 != null && typeValue1.equals(typeValue2)))) {
                                    attributesImpl.addAttribute("", XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, typeValue2 != null ? typeValue2 : "");
                                }
                            }

                            if (!(xformsControl2 instanceof RepeatIterationControl)) {
                                // Regular control

                                // Get current value if possible for this control
                                // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
                                // client not to update the value, unlike with attributes which can be missing
                                if (xformsControl2 instanceof XFormsValueControl && !(xformsControl2 instanceof XFormsUploadControl)) {

                                    final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsControl2;

                                    // Check if a "display-value" attribute must be added
                                    if (!isOutputControlWithValueAttribute) {
                                        final String displayValue = xformsValueControl.getDisplayValue();
                                        if (displayValue != null)
                                            attributesImpl.addAttribute("", "display-value", "display-value", ContentHandlerHelper.CDATA, displayValue);
                                    }

                                    // Create element with text value
                                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                    ch.text(xformsValueControl.convertToExternalValue(xformsValueControl.getValue()));
                                    ch.endElement();
                                } else if (!(xformsControl2 instanceof XFormsCaseControl)) {
                                    // No value, just output element with no content
                                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                }
                            } else {
                                // Repeat iteration
                                final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) xformsControl2;
                                attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));

                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                            }
                        }

                        // Handle itemsets
                        if (xformsControl2 instanceof XFormsSelect1Control || xformsControl2 instanceof XFormsSelectControl) {
                            final XFormsSelect1Control xformsSelect1Control1 = (XFormsSelect1Control) xformsControl1;
                            final XFormsSelect1Control xformsSelect1Control2 = (XFormsSelect1Control) xformsControl2;

                            if (itemsetsFull1 != null && xformsSelect1Control1 != null) {
                                final Object items = xformsSelect1Control1.getItemset();
                                if (items != null)
                                    itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), items);
                            }

                            if (itemsetsFull2 != null && xformsSelect1Control2 != null) {
                                final Object items = xformsSelect1Control2.getItemset();
                                if (items != null)
                                    itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), items);
                            }
                        }
                    } else {
                        // xformsControl2 == null (&& xformsControl1 != null)
                        // We went from an existing control to a non-relevant control

                        // The only information we send is the non-relevance of the control if needed
                        if (xformsControl1.isRelevant()) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                        ContentHandlerHelper.CDATA, Boolean.toString(false));

                            if (!(xformsControl1 instanceof RepeatIterationControl)) {
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                            } else {
                                final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) xformsControl1;
                                attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                            }
                        }
                    }
                }

                // 2: Check children if any
                if (XFormsControls.isGroupingControl(leadingControl.getName()) || leadingControl instanceof RepeatIterationControl) {

                    final boolean isRepeatControl = leadingControl instanceof XFormsRepeatControl;
//                    if (xformsControl2 != null) {
//
//
//
//                    } else {
//                        // xformsControl2 == null (&& xformsControl1 != null)
//                        // We went from an existing control to a non-relevant control
//
//                        final List children1 = xformsControl1.getChildren();
//                        final int size1 = (children1 == null) ? 0 : children1.size();
//
//                        if (size1 != 0) {
//                            // Size is decreasing, notify the client
//                            final String repeatControlId = xformsControl1.getEffectiveId();
//                            final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
//                            final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
//                            final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);
//                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
//                                    new String[]{"id", templateId, "parent-indexes", parentIndexes, "count", "" + (size1)});
//                        }
//                    }

                    final List children1 = (xformsControl1 == null) ? null : (xformsControl1.getChildren() != null && xformsControl1.getChildren().size() == 0) ? null : xformsControl1.getChildren();
                    final List children2 = (xformsControl2 == null) ? null : (xformsControl2.getChildren() != null && xformsControl2.getChildren().size() == 0) ? null : xformsControl2.getChildren();

                    if (isRepeatControl) {

                        // Repeat update

                        final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) leadingControl;

                        final int size1 = (children1 == null) ? 0 : children1.size();
                        final int size2 = (children2 == null) ? 0 : children2.size();

                        if (size1 == size2) {
                            // No add or remove of children

                            // Delete first template if needed
                            if (size2 == 0 && xformsControl1 == null) {
                                outputDeleteRepeatTemplate(ch, xformsControl2, 1);
                            }

                            // Diff children
                            newDiffControlsState(ch, containingDocument, children1, children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                        } else if (size2 > size1) {
                            // Size has grown

                            // Copy template instructions
                            for (int k = size1 + 1; k <= size2; k++) {
                                outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                            }

                            // Diff the common subset
                            newDiffControlsState(ch, containingDocument, children1, children2.subList(0, size1), itemsetsFull1, itemsetsFull2, valueChangeControlIds);

                            // Issue new values for new iterations
                            newDiffControlsState(ch, containingDocument, null, children2.subList(size1, size2), itemsetsFull1, itemsetsFull2, valueChangeControlIds);

                        } else if (size2 < size1) {
                            // Size has shrunk

                            outputDeleteRepeatTemplate(ch, xformsControl2, size1 - size2);

                            // Diff the remaining subset
                            newDiffControlsState(ch, containingDocument, children1.subList(0, size2), children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                        }
                    } else {
                        // Other grouping controls
                        newDiffControlsState(ch, containingDocument, children1, children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                    }
                }
            }
        }
    }

    public static void oldDiffControlsState(ContentHandlerHelper ch, XFormsContainingDocument containingDocument, List state1, List state2, Map itemsetsFull1, Map itemsetsFull2, Map valueChangeControlIds) {

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Both lists must have the same size if present; state1 can be null
        if ((state1 != null && state2 != null && state1.size() != state2.size()) || (state2 == null)) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final boolean isStaticReadonly = containingDocument.getReadonlyAppearance().equals(XFormsConstants.XXFORMS_READONLY_APPEARANCE_STATIC_VALUE);
        final AttributesImpl attributesImpl = new AttributesImpl();
        final Iterator j = (state1 == null) ? null : state1.iterator();
        for (Iterator i = state2.iterator(); i.hasNext();) {
            final XFormsControl xformsControl1 = (state1 == null) ? null : (XFormsControl) j.next();
            final XFormsControl xformsControl2 = (XFormsControl) i.next();

            // 1: Check current control
            if (!(xformsControl2 instanceof XFormsRepeatControl || xformsControl2 instanceof XFormsCaseControl)) {
                // xforms:repeat doesn't need to be handled independently, iterations do it

                // Output diffs between controlInfo1 and controlInfo2

                final boolean isValueChangeControl = valueChangeControlIds != null && valueChangeControlIds.get(xformsControl2.getEffectiveId()) != null;
                if ((!xformsControl2.equals(xformsControl1) || isValueChangeControl) && !(isStaticReadonly && xformsControl2.isReadonly() && xformsControl2 instanceof XFormsTriggerControl)) {
                    // Don't send anything if nothing has changed
                    // But we force a change for controls whose values changed in the request
                    // Also, we don't output anything for triggers in static readonly mode

                    attributesImpl.clear();

                    // Control id
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, xformsControl2.getEffectiveId());

                    // Control children values
                    if (!(xformsControl2 instanceof RepeatIterationControl)) {
                        {
                            final String labelValue1 = (xformsControl1 == null) ? null : xformsControl1.getLabel();
                            final String labelValue2 = xformsControl2.getLabel();

                            if (!((labelValue1 == null && labelValue2 == null) || (labelValue1 != null && labelValue2 != null && labelValue1.equals(labelValue2)))) {
                                attributesImpl.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, labelValue2 != null ? labelValue2 : "");
                            }
                        }

                        {
                            final String helpValue1 = (xformsControl1 == null) ? null : xformsControl1.getHelp();
                            final String helpValue2 = xformsControl2.getHelp();

                            if (!((helpValue1 == null && helpValue2 == null) || (helpValue1 != null && helpValue2 != null && helpValue1.equals(helpValue2)))) {
                                attributesImpl.addAttribute("", "help", "help", ContentHandlerHelper.CDATA, helpValue2 != null ? helpValue2 : "");
                            }
                        }

                        {
                            final String hintValue1 = (xformsControl1 == null) ? null : xformsControl1.getHint();
                            final String hintValue2 = xformsControl2.getHint();

                            if (!((hintValue1 == null && hintValue2 == null) || (hintValue1 != null && hintValue2 != null && hintValue1.equals(hintValue2)))) {
                                attributesImpl.addAttribute("", "hint", "hint", ContentHandlerHelper.CDATA, hintValue2 != null ? hintValue2 : "");
                            }
                        }

                        {
                            final String alertValue1 = (xformsControl1 == null) ? null : xformsControl1.getAlert();
                            final String alertValue2 = xformsControl2.getAlert();

                            if (!((alertValue1 == null && alertValue2 == null) || (alertValue1 != null && alertValue2 != null && alertValue1.equals(alertValue2)))) {
                                attributesImpl.addAttribute("", "alert", "alert", ContentHandlerHelper.CDATA, alertValue2 != null ? alertValue2 : "");
                            }
                        }

                        // Output xforms:output-specific information
                        if (xformsControl2 instanceof XFormsOutputControl) {
                            final XFormsOutputControl outputControlInfo1 = (XFormsOutputControl) xformsControl1;
                            final XFormsOutputControl outputControlInfo2 = (XFormsOutputControl) xformsControl2;

                            {
                                // Mediatype
                                final String mediatypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediatypeAttribute();
                                final String mediatypeValue2 = outputControlInfo2.getMediatypeAttribute();

                                if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
                                    attributesImpl.addAttribute("", "mediatype", "mediatype", ContentHandlerHelper.CDATA, mediatypeValue2 != null ? mediatypeValue2 : "");
                                }
                            }
                        }

                        // Output xforms:upload-specific information
                        if (xformsControl2 instanceof XFormsUploadControl) {
                            final XFormsUploadControl uploadControlInfo1 = (XFormsUploadControl) xformsControl1;
                            final XFormsUploadControl uploadControlInfo2 = (XFormsUploadControl) xformsControl2;

                            {
                                // State
                                final String stateValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getState();
                                final String stateValue2 = uploadControlInfo2.getState();

                                if (!((stateValue1 == null && stateValue2 == null) || (stateValue1 != null && stateValue2 != null && stateValue1.equals(stateValue2)))) {
                                    attributesImpl.addAttribute("", "state", "state", ContentHandlerHelper.CDATA, stateValue2 != null ? stateValue2 : "");
                                }
                            }
                            {
                                // Mediatype
                                final String mediatypeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getMediatype();
                                final String mediatypeValue2 = uploadControlInfo2.getMediatype();

                                if (!((mediatypeValue1 == null && mediatypeValue2 == null) || (mediatypeValue1 != null && mediatypeValue2 != null && mediatypeValue1.equals(mediatypeValue2)))) {
                                    attributesImpl.addAttribute("", "mediatype", "mediatype", ContentHandlerHelper.CDATA, mediatypeValue2 != null ? mediatypeValue2 : "");
                                }
                            }
                            {
                                // Filename
                                final String filenameValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getFilename();
                                final String filenameValue2 = uploadControlInfo2.getFilename();

                                if (!((filenameValue1 == null && filenameValue2 == null) || (filenameValue1 != null && filenameValue2 != null && filenameValue1.equals(filenameValue2)))) {
                                    attributesImpl.addAttribute("", "filename", "filename", ContentHandlerHelper.CDATA, filenameValue2 != null ? filenameValue2 : "");
                                }
                            }
                            {
                                // Size
                                final String sizeValue1 = (uploadControlInfo1 == null) ? null : uploadControlInfo1.getSize();
                                final String sizeValue2 = uploadControlInfo2.getSize();

                                if (!((sizeValue1 == null && sizeValue2 == null) || (sizeValue1 != null && sizeValue2 != null && sizeValue1.equals(sizeValue2)))) {
                                    attributesImpl.addAttribute("", "size", "size", ContentHandlerHelper.CDATA, sizeValue2 != null ? sizeValue2 : "");
                                }
                            }

                        }
                    }

                    // Model item properties
                    if (xformsControl1 == null || xformsControl1.isReadonly() != xformsControl2.isReadonly()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isReadonly()));
                    }
                    if (xformsControl1 == null || xformsControl1.isRequired() != xformsControl2.isRequired()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isRequired()));
                    }
                    if (xformsControl1 == null || xformsControl1.isRelevant() != xformsControl2.isRelevant()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isRelevant()));
                    }
                    if (xformsControl1 == null || xformsControl1.isValid() != xformsControl2.isValid()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(xformsControl2.isValid()));
                    }

                    final boolean isOutputControlWithValueAttribute = xformsControl2 instanceof XFormsOutputControl && ((XFormsOutputControl) xformsControl2).getValueAttribute() != null;
                    if (!(xformsControl2 instanceof RepeatIterationControl) && !isOutputControlWithValueAttribute) {

                        final String typeValue1 = (xformsControl1 == null) ? null : xformsControl1.getType();
                        final String typeValue2 = xformsControl2.getType();

                        if (xformsControl1 == null || !((typeValue1 == null && typeValue2 == null) || (typeValue1 != null && typeValue2 != null && typeValue1.equals(typeValue2)))) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, typeValue2 != null ? typeValue2 : "");
                        }
                    }

                    if (!(xformsControl2 instanceof RepeatIterationControl)) {
                        // Regular control

                        // Get current value if possible for this control
                        // NOTE: We issue the new value in all cases because we don't have yet a mechanism to tell the
                        // client not to update the value, unlike with attributes which can be missing
                        if (xformsControl2 instanceof XFormsValueControl && !(xformsControl2 instanceof XFormsUploadControl)) {

                            final XFormsValueControl xformsValueControl = (XFormsValueControl) xformsControl2;

                            // Check if a "display-value" attribute must be added
                            if (!isOutputControlWithValueAttribute) {
                                final String displayValue = xformsValueControl.getDisplayValue();
                                if (displayValue != null)
                                    attributesImpl.addAttribute("", "display-value", "display-value", ContentHandlerHelper.CDATA, displayValue);
                            }

                            // Create element with text value
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                            ch.text(xformsValueControl.convertToExternalValue(xformsValueControl.getValue()));
                            ch.endElement();
                        } else if (!(xformsControl2 instanceof XFormsCaseControl)) {
                            // No value, just output element with no content
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                        }
                    } else {
                        // Repeat iteration
                        final RepeatIterationControl repeatIterationInfo = (RepeatIterationControl) xformsControl2;
                        attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));

                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                    }
                }

                // Handle itemsets
                if (xformsControl2 instanceof XFormsSelect1Control || xformsControl2 instanceof XFormsSelectControl) {
                    final XFormsSelect1Control xformsSelect1Control1 = (XFormsSelect1Control) xformsControl1;
                    final XFormsSelect1Control xformsSelect1Control2 = (XFormsSelect1Control) xformsControl2;

                    if (itemsetsFull1 != null && xformsSelect1Control1 != null) {
                        final Object items = xformsSelect1Control1.getItemset();
                        if (items != null)
                            itemsetsFull1.put(xformsSelect1Control1.getEffectiveId(), items);
                    }

                    if (itemsetsFull2 != null && xformsSelect1Control2 != null) {
                        final Object items = xformsSelect1Control2.getItemset();
                        if (items != null)
                            itemsetsFull2.put(xformsSelect1Control2.getEffectiveId(), items);
                    }
                }
            }

            // 2: Check children if any
            if (XFormsControls.isGroupingControl(xformsControl2.getName()) || xformsControl2 instanceof RepeatIterationControl) {

                final List children1 = (xformsControl1 == null) ? null : xformsControl1.getChildren();
                final List children2 = (xformsControl2.getChildren() == null) ? Collections.EMPTY_LIST : xformsControl2.getChildren();

                // Repeat grouping control
                if (xformsControl2 instanceof XFormsRepeatControl && children1 != null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Special case of repeat update

                    final int size1 = children1.size();
                    final int size2 = children2.size();

                    if (size1 == size2) {
                        // No add or remove of children
                        oldDiffControlsState(ch, containingDocument, children1, xformsControl2.getChildren(), itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                    } else if (size2 > size1) {
                        // Size has grown

                        // Copy template instructions
                        for (int k = size1 + 1; k <= size2; k++) {
                            outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                        }

                        // Diff the common subset
                        oldDiffControlsState(ch, containingDocument, children1, children2.subList(0, size1), itemsetsFull1, itemsetsFull2, valueChangeControlIds);

                        // Issue new values for new iterations
                        oldDiffControlsState(ch, containingDocument, null, children2.subList(size1, size2), itemsetsFull1, itemsetsFull2, valueChangeControlIds);

                    } else if (size2 < size1) {
                        // Size has shrunk
                        outputDeleteRepeatTemplate(ch, xformsControl2, size1 - size2);

                        // Diff the remaining subset
                        oldDiffControlsState(ch, containingDocument, children1.subList(0, size2), children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                    }
                } else if (xformsControl2 instanceof XFormsRepeatControl && xformsControl1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle new sub-xforms:repeat

                    // Copy template instructions
                    final int size2 = children2.size();
                    if (size2 > 1) {
                        for (int k = 2; k <= size2; k++) { // don't copy the first template, which is already copied when the parent is copied
                            outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                        }
                    } else if (size2 == 1) {
                        // NOP, the client already has the template copied
                    } else if (size2 == 0) {
                        // Delete first template
                        outputDeleteRepeatTemplate(ch, xformsControl2, 1);
                    }

                    // Issue new values for the children
                    oldDiffControlsState(ch, containingDocument, null, children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);

                } else if (xformsControl2 instanceof XFormsRepeatControl && children1 == null) {

                    final XFormsRepeatControl repeatControlInfo = (XFormsRepeatControl) xformsControl2;

                    // Handle repeat growing from size 0 (case of instance replacement, for example)

                    // Copy template instructions
                    final int size2 = children2.size();
                    for (int k = 1; k <= size2; k++) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                    }

                    // Issue new values for the children
                    oldDiffControlsState(ch, containingDocument, null, children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                } else {
                    // Other grouping controls
                    oldDiffControlsState(ch, containingDocument, children1, children2, itemsetsFull1, itemsetsFull2, valueChangeControlIds);
                }
            }
        }
    }

    private static void outputDeleteRepeatTemplate(ContentHandlerHelper ch, XFormsControl xformsControl2, int count) {
        final String repeatControlId = xformsControl2.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String templateId = (indexOfRepeatHierarchySeparator == -1) ? repeatControlId : repeatControlId.substring(0, indexOfRepeatHierarchySeparator);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                new String[]{"id", templateId, "parent-indexes", parentIndexes, "count", "" + count});
    }

    private static void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsRepeatControl repeatControlInfo, int idSuffix) {

        final String repeatControlId = repeatControlInfo.getEffectiveId();
        final int indexOfRepeatHierarchySeparator = repeatControlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1);
        final String parentIndexes = (indexOfRepeatHierarchySeparator == -1) ? "" : repeatControlId.substring(indexOfRepeatHierarchySeparator + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                new String[]{"id", repeatControlInfo.getRepeatId(), "parent-indexes", parentIndexes,  "id-suffix", Integer.toString(idSuffix) });
    }

    private void outputSubmissionInfo(ExternalContext externalContext, ContentHandlerHelper ch, XFormsModelSubmission activeSubmission) {
        final String requestURL = externalContext.getRequest().getRequestURL();
        // Signal that we want a POST to the XForms Server
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "submission",
                new String[]{"action", requestURL, "method", "POST",
                        "show-progress", (activeSubmission == null || activeSubmission.isXxfShowProgress()) ? null : "false",
                        "replace", activeSubmission.getReplace()
                });
    }

    private void outputMessagesInfo(ContentHandlerHelper ch, List messages) {
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

    private void outputFocusInfo(ContentHandlerHelper ch, String focusEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "setfocus",
                new String[]{"control-id", focusEffectiveControlId});
    }

    private void outputItemsets(ContentHandlerHelper ch, Map itemsetIdToItemsetInfoMap) {
        if (itemsetIdToItemsetInfoMap != null && itemsetIdToItemsetInfoMap.size() > 0) {
            // There are some xforms:itemset controls

            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemsets");
            for (Iterator i = itemsetIdToItemsetInfoMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List items = (List) currentEntry.getValue();

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[]{"id", itemsetId});
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final XFormsSelect1Control.Item itemsetInfo = (XFormsSelect1Control.Item) j.next();

                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "item",
                            new String[]{"label", itemsetInfo.getLabel(), "value", itemsetInfo.getValue()});
                    ch.endElement();
                }
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
                final Map switchIdToSelectedCaseIdMap1 = switchState1.getSwitchIdToSelectedCaseIdMap();

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
                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", selectedCaseId, "visibility", "visible"});

                        if (previousSelectedCaseId != null) {
                            // Output deselected case ids
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", previousSelectedCaseId, "visibility", "hidden"});
                        } else {
                            // This is a new switch (can happen with repeat), send all deselected to be sure
                            final XFormsControl switchXFormsControl = (XFormsControl) xformsControls.getObjectById(switchId);
                            final List children = switchXFormsControl.getChildren();
                            if (children != null && children.size() > 0) {
                                for (Iterator j = children.iterator(); j.hasNext();) {
                                    final XFormsControl caseXFormsControl = (XFormsControl) j.next();

                                    if (!caseXFormsControl.getEffectiveId().equals(selectedCaseId)) {
                                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", caseXFormsControl.getEffectiveId(), "visibility", "hidden"});
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
                    final Boolean visible = (Boolean) currentEntry.getValue();

                    // Only output the information if it has changed
                    final Boolean previousVisible = (Boolean) dialogIdToVisibleMap1.get(dialogId);
                    if (!visible.equals(previousVisible)) {

                        if (!found) {
                            // Open xxf:divs element
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                            found = true;
                        }

                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", dialogId, "visibility", visible.booleanValue() ? "visible" : "hidden"});
                    }
                }
            }
        }

        // Close xxf:divs element if needed
        if (found)
            ch.endElement();
    }
}
