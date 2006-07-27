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
package org.orbeon.oxf.xforms;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The XForms Server processor handles client requests, including events, and either returns an XML
 * response, or returns a response through the ExternalContext.
 *
 * TODO: Get rid of this. But this is still used in some unit tests.
 */
public class OldXFormsServer extends ProcessorImpl {

    static public Logger logger = LoggerFactory.createLogger(OldXFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    private static final String INPUT_STATIC_STATE = "static-state";
    //private static final String OUTPUT_RESPONSE = "response"; // optional

    private static final String SESSION_STATE_PREFIX = "session:";

    public static final Map XFORMS_NAMESPACES = new HashMap();

    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public OldXFormsServer() {
        //addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST)); // optional
        //addInputInfo(new ProcessorInputOutputInfo(INPUT_STATIC_STATE)); // optional
        //addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESPONSE)); // optional
    }

    /**
     * Case where an XML response must be generated.
     */
    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {
                doIt(pipelineContext, contentHandler);
            }
        };
        addOutput(name, output);
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

        boolean hasRequestInput = getConnectedInputs().get(INPUT_REQUEST) != null;

        final Element filesElement;
        final Element actionElement;
        final XFormsContainingDocument containingDocument;
        final org.orbeon.oxf.xforms.processor.XFormsServer.XFormsState xformsState;
        final String requestPageGenerationId;
        final boolean isInitializationRun;
        if (hasRequestInput) {
            // Use request input provided by client
            final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

            // Get action
            actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

            // Get files if any (those come from xforms-server-submit.xpl upon submission)
            filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

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

                if (dynamicStateString.startsWith(SESSION_STATE_PREFIX)) {
                    // State doesn't come with the request, we should look it up in the repository
                    final String requestId = dynamicStateString.substring(SESSION_STATE_PREFIX.length());

                    // Extract page generation id
                    requestPageGenerationId = staticStateString.substring(SESSION_STATE_PREFIX.length());

                    // We don't create the cache at this point as it may not be necessary
                    final XFormsServerSessionStateCache sessionStateCache = XFormsServerSessionStateCache.instance(externalContext.getSession(false), false);
                    final org.orbeon.oxf.xforms.processor.XFormsServer.XFormsState sessionFormsState = (sessionStateCache == null) ? null : sessionStateCache.find(requestPageGenerationId, requestId);

                    // This is not going to be good when it happens, and we must create a caching heuristic that minimizes this
                    if (sessionFormsState == null)
                        throw new OXFException("Unable to retrieve XForms engine state.");

                    xformsState = sessionFormsState;
                } else {
                    // State comes with request
                    requestPageGenerationId = null;
                    xformsState = new org.orbeon.oxf.xforms.processor.XFormsServer.XFormsState(staticStateString, dynamicStateString);
                }
            }

            if (XFormsUtils.isCacheDocument()) {
                // Try to obtain containing document from cache
                if (filesElement == null) {
                    // No fileElements, this may have been cached
                    containingDocument = XFormsServerDocumentCache.instance().find(pipelineContext, xformsState);
                } else  {
                    // If there are filesElement, then we know this was not cached
                    logger.debug("XForms - containing document cache (getContainingDocument): fileElements present.");
                    containingDocument = org.orbeon.oxf.xforms.processor.XFormsServer.createXFormsContainingDocument(pipelineContext, xformsState, filesElement);
                }
            } else {
                // Otherwise we recreate the containing document from scratch
                containingDocument = org.orbeon.oxf.xforms.processor.XFormsServer.createXFormsContainingDocument(pipelineContext, xformsState, filesElement);
            }
            isInitializationRun = false;
        } else {
            // Use static-state input provided during initialization run

            final Document staticStateDocument = readInputAsDOM4J(pipelineContext, INPUT_STATIC_STATE);
            xformsState = new XFormsServer.XFormsState(XFormsUtils.encodeXML(pipelineContext, staticStateDocument, XFormsUtils.getEncryptionKey()), "");
            final XFormsEngineStaticState xformsEngineStaticState = new XFormsEngineStaticState(pipelineContext, staticStateDocument);

            containingDocument = XFormsServer.createXFormsContainingDocument(pipelineContext, xformsState, null, xformsEngineStaticState, null);

            filesElement = null;
            actionElement = null;
            requestPageGenerationId = null;
            isInitializationRun = true;
        }

        try {
            // Run event if any
            if (actionElement != null) {
                final List eventElements = actionElement.elements(XFormsConstants.XXFORMS_EVENT_QNAME);
                if (eventElements != null && eventElements.size() > 0) {
                    for (Iterator i = eventElements.iterator(); i.hasNext();) {
                        final Element eventElement = (Element) i.next();
                        final String sourceControlId = eventElement.attributeValue("source-control-id");
                        final String otherControlId = eventElement.attributeValue("other-control-id");
                        final String eventName = eventElement.attributeValue("name");
                        final String value = eventElement.getText();

                        if (sourceControlId != null && eventName != null) {
                            // An event is passed
                            containingDocument.executeExternalEvent(pipelineContext, eventName, sourceControlId, otherControlId, value, null);
                        } else if (!(sourceControlId == null && eventName == null)) {
                            // Do we have a case where no event name is passed?
                            // Otherwise this test could just become: eventName == null
                            //throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                        }
                    }
                }
            }

            // Create resulting document if there is a ContentHandler
            if (contentHandler != null) {
                final XFormsControls xFormsControls = containingDocument.getXFormsControls();
                xFormsControls.rebuildCurrentControlsState(pipelineContext);
                final XFormsControls.ControlsState currentControlsState = xFormsControls.getCurrentControlsState();
                try {
                    final ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                    ch.startDocument();
                    contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

                    // Output state
                    boolean requireClientSubmission = false;
                    {
                        final Document dynamicStateDocument = Dom4jUtils.createDocument();
                        final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
                        // Output updated instances
                        {
                            final Element instancesElement = dynamicStateElement.addElement("instances");

                            for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                                final XFormsModel currentModel = (XFormsModel) i.next();

                                for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                                    final XFormsInstance currentInstance = (XFormsInstance) j.next();
                                    instancesElement.add((currentInstance).getInstanceDocument().getRootElement().createCopy());
                                    // Log instance if needed
                                    if (logger.isDebugEnabled()) {
                                        logger.debug("XForms - resulting instance: model id='" + currentModel.getEffectiveId() +  "', instance id= '" + currentInstance.getEffectiveId() + "'\n"
                                                + Dom4jUtils.domToString(currentInstance.getInstanceDocument()));
                                    }
                                }
                            }
                        }

                        // Output divs information
                        {
                            final Element divsElement = dynamicStateElement.addElement("divs");
                            outputSwitchDivs(divsElement, currentControlsState);
                        }

                        // Output repeat index information
                        {
                            final Map repeatIdToIndex = currentControlsState.getRepeatIdToIndex();
                            if (repeatIdToIndex.size() != 0) {
                                final Element repeatIndexesElement = dynamicStateElement.addElement("repeat-indexes");
                                for (Iterator i = repeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                                    final Map.Entry currentEntry = (Map.Entry) i.next();
                                    final String repeatId = (String) currentEntry.getKey();
                                    final Integer index = (Integer) currentEntry.getValue();
                                    final Element newElement = repeatIndexesElement.addElement("repeat-index");
                                    newElement.addAttribute("id", repeatId);
                                    newElement.addAttribute("index", index.toString());
                                }
                            }
                        }

                        // Submission automatic event if needed
                        {
                            // Check for xxforms-submit event
                            {
                                final XFormsModelSubmission activeSubmission = containingDocument.getActiveSubmission();
                                if (activeSubmission != null) {
                                    final Element eventElement = dynamicStateElement.addElement("event");
                                    eventElement.addAttribute("source-control-id", activeSubmission.getEffectiveId());
                                    eventElement.addAttribute("name", XFormsEvents.XXFORMS_SUBMIT);
                                    requireClientSubmission = true;
                                }
                            }
                            // Check for xxforms-load event
                            {
                                final List loads = containingDocument.getLoadsToRun();
                                if (loads != null) {
                                    for (Iterator i = loads.iterator(); i.hasNext();) {
                                        final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) i.next();

                                        if (load.isReplace() && load.isPortletLoad() && !NetUtils.urlHasProtocol(load.getResource())) {
                                            // We need to submit the event so that the portlet can load the new path
                                            final Element eventElement = dynamicStateElement.addElement("event");
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
                        }

                        // Produce static state if needed
                        final String currentPageGenerationId = (requestPageGenerationId != null) ? requestPageGenerationId : UUIDUtils.createPseudoUUID();
                        if (isInitializationRun) {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "static-state", new String[] { "container-type", externalContext.getRequest().getContainerType() });
                            if (containingDocument.isSessionStateHandling()) {
                                // Produce static state key, in fact a page generation id
                                ch.text(SESSION_STATE_PREFIX + currentPageGenerationId);
                            } else {
                                // Produce encoded static state
                                ch.text(xformsState.getStaticState());
                            }
                            ch.endElement();
                        }

                        // Encode dynamic state
                        final String newEncodedDynamicState = XFormsUtils.encodeXML(pipelineContext, dynamicStateDocument);
                        final org.orbeon.oxf.xforms.processor.XFormsServer.XFormsState newXFormsState = new org.orbeon.oxf.xforms.processor.XFormsServer.XFormsState(xformsState.getStaticState(), newEncodedDynamicState);

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
                            if (!requireClientSubmission) {
                                // NOTE: We check on requireClientSubmission because the event is encoded
                                // in the dynamic state. But if we stored the event separately, then we
                                // could still cache the containing document.
                                XFormsServerDocumentCache.instance().add(pipelineContext, newXFormsState, containingDocument);
                            } else {
                                // Since we cannot cache the result, we have to get the object out of its current pool
                                final ObjectPool objectPool = containingDocument.getSourceObjectPool();
                                if (objectPool != null) {
                                    logger.debug("XForms - containing document cache: discarding non-cacheable document from pool.");
                                    try {
                                        objectPool.invalidateObject(containingDocument);
                                        containingDocument.setSourceObjectPool(null);
                                    } catch (Exception e1) {
                                        throw new OXFException(e1);
                                    }
                                }
                            }
                        }
                    }

                    // Output action
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");

                        // Output new controls values and associated information
                        final Map itemsetsFull1 = new HashMap();
                        final Map itemsetsFull2 = new HashMap();
                        {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                            XFormsServer.diffControlsState(ch, isInitializationRun ? null : xFormsControls.getInitialControlsState().getChildren(),
                                    currentControlsState.getChildren(), itemsetsFull1, itemsetsFull2, null);

                            ch.endElement();
                        }

                        // Output divs information
                        {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                            outputSwitchDivs(ch, currentControlsState);
                            ch.endElement();
                        }

                        // Output repeats information
                        {
                            if (isInitializationRun) {
                                // Output initial repeat information
                                outputInitialRepeatInfo(ch, currentControlsState);
                            } else {
                                // Output index updates
                                final Map initialRepeatIdToIndex = xFormsControls.getInitialControlsState().getRepeatIdToIndex();
                                final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
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
                        }

                        // Output itemset information
                        {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemsets");
                            if (isInitializationRun) {
                                outputItemsets(ch, itemsetsFull2);
                            } else {
                                outputItemsets(ch, XFormsServer.diffItemsets(itemsetsFull1, itemsetsFull2));
                            }
                            ch.endElement();
                        }

                        // Check if we want to require the client to perform a form submission
                        {
                            if (requireClientSubmission)
                                outputSubmissionInfo(externalContext, ch);
                        }

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
                                org.orbeon.oxf.xforms.processor.XFormsServer.outputLoadsInfo(ch, loads);
                            }
                        }

                        // Output focus instructions
                        {
                            final String focusEffectiveControlId = containingDocument.getClientFocusEffectiveControlId();
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

    private void outputSubmissionInfo(ExternalContext externalContext, ContentHandlerHelper ch) {
        final String requestURL = externalContext.getRequest().getRequestURL();
        // Signal that we want a POST to the XForms Server
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "submission",
                new String[]{"action", requestURL, "method", "POST"});
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

    private void outputFocusInfo(ContentHandlerHelper ch, String focusEffectiveControlId) {
        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "setfocus",
                new String[]{"control-id", focusEffectiveControlId});
    }

    private void outputItemsets(ContentHandlerHelper ch, Map itemsetIdToItemsetInfoMap) {
        if (itemsetIdToItemsetInfoMap != null) {
            // There are some xforms:itemset controls

            for (Iterator i = itemsetIdToItemsetInfoMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List items = (List) currentEntry.getValue();

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[]{"id", itemsetId});
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final XFormsSelect1Control.ItemsetInfo itemsetInfo = (XFormsSelect1Control.ItemsetInfo) j.next();

                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "item",
                            new String[]{"label", itemsetInfo.getLabel(), "value", itemsetInfo.getValue()});
                    ch.endElement();
                }
                ch.endElement();
            }
        }
    }

    private void outputSwitchDivs(Element divsElement, XFormsControls.ControlsState controlsState) {
        final Map switchIdToSelectedCaseIdMap = controlsState.getSwitchIdToSelectedCaseIdMap();
        if (switchIdToSelectedCaseIdMap != null) {
            // There are some xforms:switch/xforms:case controls

            for (Iterator i = switchIdToSelectedCaseIdMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String switchId = (String) currentEntry.getKey();
                final String selectedCaseId = (String) currentEntry.getValue();

                // Output selected ids
                {
                    final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    divElement.addAttribute("id", selectedCaseId);
                    divElement.addAttribute("visibility", "visible");
                }

                // Output deselected ids
                final XFormsControl switchXFormsControl = (XFormsControl) controlsState.getIdToControl().get(switchId);
                final List children = switchXFormsControl.getChildren();
                if (children != null && children.size() > 0) {
                    for (Iterator j = children.iterator(); j.hasNext();) {
                        final XFormsControl caseXFormsControl = (XFormsControl) j.next();

                        if (!caseXFormsControl.getEffectiveId().equals(selectedCaseId)) {
                            final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                            divElement.addAttribute("id", caseXFormsControl.getEffectiveId());
                            divElement.addAttribute("visibility", "hidden");
                        }
                    }
                }
            }
        }
    }

    private void outputSwitchDivs(ContentHandlerHelper ch, XFormsControls.ControlsState controlsState) {
        final Map switchIdToSelectedCaseIdMap = controlsState.getSwitchIdToSelectedCaseIdMap();
        if (switchIdToSelectedCaseIdMap != null) {
            // There are some xforms:switch/xforms:case controls

            for (Iterator i = switchIdToSelectedCaseIdMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String switchId = (String) currentEntry.getKey();
                final String selectedCaseId = (String) currentEntry.getValue();

                // Output selected ids
                {
                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", selectedCaseId, "visibility", "visible"});
                }

                // Output deselected ids
                final XFormsControl switchXFormsControl = (XFormsControl) controlsState.getIdToControl().get(switchId);
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

    private void outputInitialRepeatInfo(ContentHandlerHelper ch, XFormsControls.ControlsState controlsState) {


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
}
