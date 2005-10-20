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
import org.orbeon.oxf.xforms.event.events.XXFormsInitializeEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsInitializeStateEvent;
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

    public static final Map XFORMS_NAMESPACES = new HashMap();

    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public XFormsServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
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
        // Extract information from request
        final Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);

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

        // Get action
        final Element actionElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_ACTION_QNAME);

        // Get files if any
        final Element filesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_FILES_QNAME);

        // Create and initialize XForms engine from encoded data
        XFormsContainingDocument containingDocument
                = createXFormsEngine(pipelineContext, staticStateString, dynamicStateString, filesElement);

        // Run event if any
        boolean isInitializationRun = true;
        {
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
                        isInitializationRun = false;
                    } else if (!(sourceControlId == null && eventName == null)) {
                        throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                    }
                }
            } else {
                isInitializationRun = true;
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

                // NOTE: Static state is produced externally during initialization

                // Output dynamic state
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
                                instancesElement.add((currentInstance).getDocument().getRootElement().createCopy());
                                // Log instance if needed
                                if (logger.isDebugEnabled()) {
                                    logger.debug("XForms - resulting instance: model id='" + currentModel.getId() +  "', instance id= '" + currentInstance.getId() + "'\n"
                                            + Dom4jUtils.domToString(currentInstance.getDocument()));
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
                        if (repeatIdToIndex != null && repeatIdToIndex.size() != 0) {
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
                        XFormsModelSubmission activeSubmission = containingDocument.getActiveSubmission();
                        if (activeSubmission != null) {
                            final Element eventElement = dynamicStateElement.addElement("event");
                            eventElement.addAttribute("source-control-id", activeSubmission.getId());
                            eventElement.addAttribute("name", "xxforms-submit");
                        }
                    }

                    // Encode dynamic state
                    final String encodedDynamicState = XFormsUtils.encodeXMLAsDOM(pipelineContext, dynamicStateDocument);

                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "dynamic-state");
                    ch.text(encodedDynamicState);
                    ch.endElement();
                }

                // Output action
                {
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action");

                    // Output new controls values and associated information
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                        diffControlsState(ch, isInitializationRun ? null : xFormsControls.getInitialControlsState().getChildren(),
                                currentControlsState.getChildren());

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
                            if (currentRepeatIdToIndex != null && currentRepeatIdToIndex.size() != 0) {
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
                            outputItemsets(ch, xFormsControls.getItemsetFull());
                        } else {
                            outputItemsets(ch, xFormsControls.getItemsetUpdate());
                        }
                        ch.endElement();
                    }

                    // Output submit information
                    {
                        final XFormsModelSubmission submission = containingDocument.getActiveSubmission();
                        if (submission != null)
                            outputSubmissionInfo(pipelineContext, ch);
                    }

                    // Output messages to display
                    {
                        final List messages = containingDocument.getMessages();
                        if (messages != null) {
                            outputMessagesInfo(ch, messages);
                        }
                    }

                    // Output loads
                    {
                        final List loads = containingDocument.getLoads();
                        if (loads != null) {
                            outputLoadsInfo(ch, loads);
                        }
                    }

                    // Output focus instructions
                    {
                        final String focusEffectiveControlId = containingDocument.getFocusEffectiveControlId();
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
    }

    private void diffControlsState(ContentHandlerHelper ch, List state1, List state2) {

        // Trivial case
        if (state1 == null && state2 == null)
            return;

        // Both lists must have the same size if present; state1 can be null
        if ((state1 != null && state2 != null && state1.size() != state2.size()) || (state2 == null)) {
            throw new IllegalStateException("Illegal state when comparing controls.");
        }

        final AttributesImpl attributesImpl = new AttributesImpl();
        final Iterator j = (state1 == null) ? null : state1.iterator();
        for (Iterator i = state2.iterator(); i.hasNext();) {
            final XFormsControls.ControlInfo controlInfo1 = (state1 == null) ? null : (XFormsControls.ControlInfo) j.next();
            final XFormsControls.ControlInfo controlInfo2 = (XFormsControls.ControlInfo) i.next();

            // 1: Check current control
            if (!(controlInfo2 instanceof XFormsControls.RepeatControlInfo)) {
                // xforms:repeat doesn't need to be handled independently, iterations do it

                // Output diffs between controlInfo1 and controlInfo2\
                if (!controlInfo2.equals(controlInfo1)) { // don't send anything if nothing has changed

                    attributesImpl.clear();

                    // Control id
                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, controlInfo2.getId());

                    // Control children values
                    if (!(controlInfo2 instanceof XFormsControls.RepeatIterationInfo)) {
                        {
                            final String labelValue1 = (controlInfo1 == null) ? null : controlInfo1.getLabel();
                            final String labelValue2 = controlInfo2.getLabel();

                            if (!((labelValue1 == null && labelValue2 == null) || (labelValue1 != null && labelValue2 != null && labelValue1.equals(labelValue2)))) {
                                attributesImpl.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, labelValue2 != null ? labelValue2 : "");
                            }
                        }

                        {
                            final String helpValue1 = (controlInfo1 == null) ? null : controlInfo1.getHelp();
                            final String helpValue2 = controlInfo2.getHelp();

                            if (!((helpValue1 == null && helpValue2 == null) || (helpValue1 != null && helpValue2 != null && helpValue1.equals(helpValue2)))) {
                                attributesImpl.addAttribute("", "help", "help", ContentHandlerHelper.CDATA, helpValue2 != null ? helpValue2 : "");
                            }
                        }

                        {
                            final String hintValue1 = (controlInfo1 == null) ? null : controlInfo1.getHint();
                            final String hintValue2 = controlInfo2.getHint();

                            if (!((hintValue1 == null && hintValue2 == null) || (hintValue1 != null && hintValue2 != null && hintValue1.equals(hintValue2)))) {
                                attributesImpl.addAttribute("", "hint", "hint", ContentHandlerHelper.CDATA, hintValue2 != null ? hintValue2 : "");
                            }
                        }

                        {
                            final String alertValue1 = (controlInfo1 == null) ? null : controlInfo1.getAlert();
                            final String alertValue2 = controlInfo2.getAlert();

                            if (!((alertValue1 == null && alertValue2 == null) || (alertValue1 != null && alertValue2 != null && alertValue1.equals(alertValue2)))) {
                                attributesImpl.addAttribute("", "alert", "alert", ContentHandlerHelper.CDATA, alertValue2 != null ? alertValue2 : "");
                            }
                        }

                        // Output xforms:output-specific information
                        if (controlInfo2 instanceof XFormsControls.OutputControlInfo) {
                            final XFormsControls.OutputControlInfo outputControlInfo1 = (XFormsControls.OutputControlInfo) controlInfo1;
                            final XFormsControls.OutputControlInfo outputControlInfo2 = (XFormsControls.OutputControlInfo) controlInfo2;

                            final String mediaTypeValue1 = (outputControlInfo1 == null) ? null : outputControlInfo1.getMediaTypeAttribute();
                            final String mediaTypeValue2 = outputControlInfo2.getMediaTypeAttribute();

                            if (!((mediaTypeValue1 == null && mediaTypeValue2 == null) || (mediaTypeValue1 != null && mediaTypeValue2 != null && mediaTypeValue1.equals(mediaTypeValue2)))) {
                                attributesImpl.addAttribute("", "mediatype", "mediatype", ContentHandlerHelper.CDATA, mediaTypeValue2 != null ? mediaTypeValue2 : "");
                            }
                        }
                    }

                    // Model item properties
                    if (controlInfo1 == null || controlInfo1.isReadonly() != controlInfo2.isReadonly()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(controlInfo2.isReadonly()));
                    }
                    if (controlInfo1 == null || controlInfo1.isRequired() != controlInfo2.isRequired()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(controlInfo2.isRequired()));
                    }
                    if (controlInfo1 == null || controlInfo1.isRelevant() != controlInfo2.isRelevant()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(controlInfo2.isRelevant()));
                    }
                    if (controlInfo1 == null || controlInfo1.isValid() != controlInfo2.isValid()) {
                        attributesImpl.addAttribute("", XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, Boolean.toString(controlInfo2.isValid()));
                    }

                    final boolean isOutputControlWithValueAttribute = controlInfo2 instanceof XFormsControls.OutputControlInfo && ((XFormsControls.OutputControlInfo) controlInfo2).getValueAttribute() != null;
                    if (!(controlInfo2 instanceof XFormsControls.RepeatIterationInfo) && !isOutputControlWithValueAttribute) {

                        final String typeValue1 = (controlInfo1 == null) ? null : controlInfo1.getType();
                        final String typeValue2 = controlInfo2.getType();

                        if (controlInfo1 == null || !((typeValue1 == null && typeValue2 == null) || (typeValue1 != null && typeValue2 != null && typeValue1.equals(typeValue2)))) {
                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME, XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME,
                                ContentHandlerHelper.CDATA, typeValue2 != null ? typeValue2 : "");
                        }
                    }

                    if (!(controlInfo2 instanceof XFormsControls.RepeatIterationInfo)) {
                        // Regular control

                        // Get current value if possible for this control
                        // NOTE: We issue the new value anyway because we don't have yet a mechanism
                        // to tell the client not to update the value, unlike with attributes which can
                        // be missing
                        if (XFormsControls.isValueControl(controlInfo2.getName())) {

                            // Check if a "display-value" attribute must be added
                            if (!isOutputControlWithValueAttribute) {
                                final String displayValue = controlInfo2.getDisplayValue();
                                if (displayValue != null)
                                    attributesImpl.addAttribute("", "display-value", "display-value", ContentHandlerHelper.CDATA, displayValue);
                            }

                            // Create element with text value
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                            ch.text(controlInfo2.getValue());
                            ch.endElement();
                        } else {
                            // No value, just output element with no content
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                        }
                    } else {
                        // Repeat iteration
                        final XFormsControls.RepeatIterationInfo repeatIterationInfo = (XFormsControls.RepeatIterationInfo) controlInfo2;
                        attributesImpl.addAttribute("", "iteration", "iteration", ContentHandlerHelper.CDATA, Integer.toString(repeatIterationInfo.getIteration()));

                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat-iteration", attributesImpl);
                    }
                }
            }

            // 2: Check children if any
            if (XFormsControls.isGroupingControl(controlInfo2.getName()) || controlInfo2 instanceof XFormsControls.RepeatIterationInfo) {

                final List children1 = (controlInfo1 == null) ? null : controlInfo1.getChildren();
                final List children2 = (controlInfo2.getChildren() == null) ? Collections.EMPTY_LIST : controlInfo2.getChildren();

                if (controlInfo2 instanceof XFormsControls.RepeatControlInfo && children1 != null) {

                    final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) controlInfo2;

                    // Special case of repeat update

                    final int size1 = children1.size();
                    final int size2 = children2.size();

                    if (size1 == size2) {
                        // No add or remove of children
                        diffControlsState(ch, children1, controlInfo2.getChildren());
                    } else if (size2 > size1) {
                        // Size has grown

                        // Copy template instructions
                        for (int k = size1 + 1; k <= size2; k++) {
                            outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                        }

                        // Diff the common subset
                        diffControlsState(ch, children1, children2.subList(0, size1));

                        // Issue new values for new iterations
                        diffControlsState(ch, null, children2.subList(size1, size2));

                    } else if (size2 < size1) {
                        // Size has shrunk

                        // TODO: Fix this splitting algorithm. For example, document-info-repeat is
                        // split into "document" and "info-repeat", when it should not be split at
                        // all.
                        final String repeatControlId = controlInfo2.getId();
//                        final StringTokenizer st = new StringTokenizer(repeatControlId, "-");
//                        final StringBuffer templateIdBuffer = new StringBuffer();
//                        final StringBuffer parentIndexesBuffer = new StringBuffer();
//                        boolean hasInteger = false;
//                        while (st.hasMoreTokens()) {
//                            final String nextToken = st.nextToken();
//                            try {
//                                Integer.parseInt(nextToken);
//                                // Integer
//                                hasInteger = true;
//                                if (parentIndexesBuffer.length() > 0)
//                                    parentIndexesBuffer.append('-');
//                                parentIndexesBuffer.append(nextToken);
//                            } catch (NumberFormatException e) {
//                                // Not an integer
//                                if (templateIdBuffer.length() > 0)
//                                    templateIdBuffer.append('-');
//                                templateIdBuffer.append(nextToken);
//                            }
//                        }

                        final int indexOfDash = repeatControlId.indexOf('-');
                        final String templateId = (indexOfDash == -1) ? repeatControlId : repeatControlId.substring(0, indexOfDash);
                        final String parentIndexes = (indexOfDash == -1) ? "" : repeatControlId.substring(indexOfDash + 1);

                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "delete-repeat-elements",
                                new String[]{"id", templateId, "parent-indexes", parentIndexes, "count", "" + (size1 - size2)});

                        // Diff the remaining subset
                        diffControlsState(ch, children1.subList(0, size2), children2);
                    }
                } else if ((controlInfo2 instanceof XFormsControls.RepeatControlInfo) && controlInfo1 == null) {

                    final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) controlInfo2;

                    // Handle new sub-xforms:repeat

                    // Copy template instructions
                    final int size2 = children2.size();
                    for (int k = 2; k <= size2; k++) { // don't copy the first template, which is already copied when the parent is copied
                        outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                    }

                    // Issue new values for the children
                    diffControlsState(ch, null, children2);

                } else if ((controlInfo2 instanceof XFormsControls.RepeatControlInfo) && children1 == null) {

                    final XFormsControls.RepeatControlInfo repeatControlInfo = (XFormsControls.RepeatControlInfo) controlInfo2;

                    // Handle repeat growing from size 0 (case of instance replacement, for example)

                    // Copy template instructions
                    final int size2 = children2.size();
                    for (int k = 1; k <= size2; k++) {
                        outputCopyRepeatTemplate(ch, repeatControlInfo, k);
                    }

                    // Issue new values for the children
                    diffControlsState(ch, null, children2);

                } else {
                    // Other grouping controls
                    diffControlsState(ch, children1, children2);
                }
            }
        }
    }

    private void outputCopyRepeatTemplate(ContentHandlerHelper ch, XFormsControls.RepeatControlInfo repeatControlInfo, int idSuffix) {

        final String repeatControlId = repeatControlInfo.getId();
        final int indexOfDash = repeatControlId.indexOf('-');
        final String parentIndexes = (indexOfDash == -1) ? "" : repeatControlId.substring(indexOfDash + 1);

        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "copy-repeat-template",
                new String[]{"id", repeatControlInfo.getRepeatId(), "parent-indexes", parentIndexes,  "id-suffix", "-" + idSuffix});
    }

    private void outputSubmissionInfo(PipelineContext pipelineContext, ContentHandlerHelper ch) {
        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
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

    private void outputLoadsInfo(ContentHandlerHelper ch, List loads) {
        for (Iterator i = loads.iterator(); i.hasNext();) {
            final XFormsContainingDocument.Load load = (XFormsContainingDocument.Load) i.next();
            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "load",
                    new String[]{ "resource", load.getResource(), "show", load.getShow() });
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
                    final XFormsControls.ItemsetInfo itemsetInfo = (XFormsControls.ItemsetInfo) j.next();

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
                final XFormsControls.ControlInfo switchControlInfo = (XFormsControls.ControlInfo) controlsState.getIdsToControlInfo().get(switchId);
                final List children = switchControlInfo.getChildren();
                if (children != null && children.size() > 0) {
                    for (Iterator j = children.iterator(); j.hasNext();) {
                        final XFormsControls.ControlInfo caseControlInfo = (XFormsControls.ControlInfo) j.next();

                        if (!caseControlInfo.getId().equals(selectedCaseId)) {
                            final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                            divElement.addAttribute("id", caseControlInfo.getId());
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
                final XFormsControls.ControlInfo switchControlInfo = (XFormsControls.ControlInfo) controlsState.getIdsToControlInfo().get(switchId);
                final List children = switchControlInfo.getChildren();
                if (children != null && children.size() > 0) {
                    for (Iterator j = children.iterator(); j.hasNext();) {
                        final XFormsControls.ControlInfo caseControlInfo = (XFormsControls.ControlInfo) j.next();

                        if (!caseControlInfo.getId().equals(selectedCaseId)) {
                            ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", caseControlInfo.getId(), "visibility", "hidden"});
                        }
                    }
                }
            }
        }
    }

    private void outputInitialRepeatInfo(ContentHandlerHelper ch, XFormsControls.ControlsState controlsState) {


        final Map initialRepeatIdToIndex = controlsState.getRepeatIdToIndex();
        final Map effectiveRepeatIdToIterations = controlsState.getEffectiveRepeatIdToIterations();

        if (initialRepeatIdToIndex != null || effectiveRepeatIdToIterations != null) {

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

    private static XFormsContainingDocument createXFormsEngine(PipelineContext pipelineContext, String staticStateString,
                                                              String dynamicStateString, Element filesElement) {

        final Document staticStateDocument = XFormsUtils.decodeXML(pipelineContext, staticStateString);
        final Document dynamicStateDocument = (dynamicStateString == null || "".equals(dynamicStateString)) ? null : XFormsUtils.decodeXML(pipelineContext, dynamicStateString);

        // Get controls from static state
//        final Document controlsDocument = Dom4jUtils.createDocument();
//        controlsDocument.add(staticStateDocument.getRootElement().element("controls").detach());
        final Document controlsDocument = Dom4jUtils.createDocumentCopyParentNamespaces(staticStateDocument.getRootElement().element("controls"));

        // Get models from static state
        final Element modelsElement = staticStateDocument.getRootElement().element("models");

        // Get instances from dynamic state
        final Element instancesElement = (dynamicStateDocument == null) ? null : dynamicStateDocument.getRootElement().element("instances");

        // Get divs from dynamic state
        final Element divsElement = (dynamicStateDocument == null) ? null : dynamicStateDocument.getRootElement().element("divs");

        // Get repeat indexes from dynamic state
        final Element repeatIndexesElement = (dynamicStateDocument == null) ? null : dynamicStateDocument.getRootElement().element("repeat-indexes");

        // Get automatic event from dynamic state
        final Element eventElement = (dynamicStateDocument == null) ? null : dynamicStateDocument.getRootElement().element("event");

        // Get all models
        final List models = new ArrayList();
        {
            // FIXME: we don't get a System ID here. Is there a simple solution?
            for (Iterator i = modelsElement.elements().iterator(); i.hasNext();) {
                Element modelElement = (Element) i.next();

//                final Document modelDocument = Dom4jUtils.createDocument();
//                modelDocument.add(modelElement.detach());
                final Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);

                XFormsModel model = new XFormsModel(modelDocument);
                models.add(model);
            }
        }

        // Create XForms Engine
        XFormsContainingDocument containingDocument = new XFormsContainingDocument(models, controlsDocument, repeatIndexesElement);

        // Get instances
        boolean isInitializeEvent;
        {
            int foundInstancesCount = 0;
            int expectedInstancesCount = 0;
            if (instancesElement != null) {

                // Iterator over all the models
                Iterator modelIterator = containingDocument.getModels().iterator();

                XFormsModel currentModel = null;
                int currentModelInstancesCount = 0;
                int currentCount = 0;

                for (Iterator i = instancesElement.elements().iterator(); i.hasNext();) {
                    Element instanceElement = (Element) i.next();

                    // Go to next model if needed
                    if (currentCount == currentModelInstancesCount) {
                        currentModel = (XFormsModel) modelIterator.next();
                        currentModelInstancesCount = currentModel.getInstanceCount();
                        currentCount = 0;

                        expectedInstancesCount += currentModelInstancesCount;
                    }

                    // Create and set instance document on current model

//                    final Document instanceDocument = Dom4jUtils.createDocument();
//                    instanceDocument.add(instanceElement.detach());
                    Document instanceDocument = Dom4jUtils.createDocumentCopyParentNamespaces(instanceElement);
                    currentModel.setInstanceDocument(pipelineContext, currentCount, instanceDocument);

                    currentCount++;
                    foundInstancesCount++;
                }
                // Number of instances must be zero or match number of models
                if (foundInstancesCount != 0 && expectedInstancesCount != foundInstancesCount)
                    throw new OXFException("Number of instances (" + foundInstancesCount + ") doesn't match number of instances in models (" + expectedInstancesCount + ").");
            }
            // Initialization will take place if no instances are provided
            isInitializeEvent = foundInstancesCount == 0;
        }

        // Initialize XForms Engine
        containingDocument.initialize(pipelineContext);
        if (isInitializeEvent)
            containingDocument.dispatchExternalEvent(pipelineContext, new XXFormsInitializeEvent(containingDocument));
        else
            containingDocument.dispatchExternalEvent(pipelineContext, new XXFormsInitializeStateEvent(containingDocument, divsElement, repeatIndexesElement));

        // Run automatic event if present
        if (eventElement != null) {
            final String controlId = eventElement.attributeValue("source-control-id");
            final String eventName = eventElement.attributeValue("name");

            containingDocument.executeExternalEvent(pipelineContext, eventName, controlId, null, null, filesElement);
        }

        return containingDocument;
    }
}
