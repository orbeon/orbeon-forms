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

import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.style.StandardNames;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;

/**
 * The XForms Server processor handles client requests, including events.
 */
public class XFormsServer extends ProcessorImpl {

    //static private Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_REQUEST = "request";
    private static final String OUTPUT_RESPONSE = "response";

    public static final Map XFORMS_NAMESPACES = new HashMap();

    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public XFormsServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_REQUEST));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESPONSE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

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

                // Create and initialize XForms engine from encoded data
                XFormsContainingDocument containingDocument
                        = createXFormsEngine(pipelineContext, staticStateString, dynamicStateString);

                // Run event if any
                final boolean isInitializationRun;
                {
                    final Element eventElement = actionElement.element(XFormsConstants.XXFORMS_EVENT_QNAME);
                    if (eventElement != null) {
                        String controlId = eventElement.attributeValue("source-control-id");
                        String eventName = eventElement.attributeValue("name");
                        String value = eventElement.getText();

                        if (controlId != null && eventName != null) {
                            // An event is passed
                            containingDocument.executeExternalEvent(pipelineContext, controlId, eventName, value);
                            isInitializationRun = false;
                        } else if (!(controlId == null && eventName == null)) {
                            throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                        } else {
                            isInitializationRun = true;
                        }
                    } else {
                        isInitializationRun = true;
                    }
                }

                // Create resulting document
                final XFormsControls xFormsControls = containingDocument.getXFormsControls();
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
                                    instancesElement.add(((XFormsInstance) j.next()).getDocument().getRootElement().createCopy());
                                }
                            }
                        }

                        // Output divs information
                        {
                            final Element divsElement = dynamicStateElement.addElement("divs");
                            outputSwitchDivs(divsElement, xFormsControls);
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

                            final AttributesImpl attributesImpl = new AttributesImpl();
                            xFormsControls.visitAllControls(pipelineContext, new XFormsControls.ControlVisitorListener() {
                                public boolean startVisitControl(Element controlElement, String effectiveControlId) {

                                    if (effectiveControlId == null)
                                        throw new OXFException("Control element doesn't have an id: " + controlElement.getQualifiedName());

                                    // Set current binding for control element
                                    //xFormsControls.setBinding(pipelineContext, controlElement);
                                    Node currentNode = xFormsControls.getCurrentSingleNode();

                                    attributesImpl.clear();

                                    // Control id
                                    attributesImpl.addAttribute("", "id", "id", ContentHandlerHelper.CDATA, effectiveControlId);

                                    // Get control children values
                                    String labelValue = xFormsControls.getLabelValue(pipelineContext);
                                    String helpValue = xFormsControls.getHelpValue(pipelineContext);
                                    String hintValue = xFormsControls.getHintValue(pipelineContext);
                                    String alertValue = xFormsControls.getAlertValue(pipelineContext);

                                    if (labelValue != null) {
                                        attributesImpl.addAttribute("", "label", "label", ContentHandlerHelper.CDATA, labelValue);
                                    }

                                    if (helpValue != null) {
                                        attributesImpl.addAttribute("", "help", "help", ContentHandlerHelper.CDATA, helpValue);
                                    }

                                    if (hintValue != null) {
                                        attributesImpl.addAttribute("", "hint", "hint", ContentHandlerHelper.CDATA, hintValue);
                                    }

                                    if (alertValue != null) {
                                        attributesImpl.addAttribute("", "alert", "alert", ContentHandlerHelper.CDATA, alertValue);
                                    }

                                    // Get model item properties
                                    InstanceData instanceData = XFormsUtils.getLocalInstanceData(currentNode);
                                    if (instanceData != null) {
                                        BooleanModelItemProperty readonly = instanceData.getReadonly();
                                        if (readonly.isSet()) {
                                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                                    XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_NAME,
                                                    ContentHandlerHelper.CDATA, Boolean.toString(readonly.get()));
                                        }
                                        BooleanModelItemProperty required = instanceData.getRequired();
                                        if (required.isSet()) {
                                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                                    XFormsConstants.XXFORMS_REQUIRED_ATTRIBUTE_NAME,
                                                    ContentHandlerHelper.CDATA, Boolean.toString(required.get()));
                                        }
                                        BooleanModelItemProperty relevant = instanceData.getRelevant();
                                        if (relevant.isSet()) {
                                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                                    XFormsConstants.XXFORMS_RELEVANT_ATTRIBUTE_NAME,
                                                    ContentHandlerHelper.CDATA, Boolean.toString(relevant.get()));
                                        }
                                        BooleanModelItemProperty valid = instanceData.getValid();
                                        if (valid.isSet()) {
                                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                                    XFormsConstants.XXFORMS_VALID_ATTRIBUTE_NAME,
                                                    ContentHandlerHelper.CDATA, Boolean.toString(valid.get()));
                                        }
                                        int typeCode = instanceData.getType().get();
                                        if (typeCode != 0) {
                                            attributesImpl.addAttribute("", XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME,
                                                    XFormsConstants.XXFORMS_TYPE_ATTRIBUTE_NAME, ContentHandlerHelper.CDATA,
                                                    StandardNames.getPrefix(typeCode) + ":" + StandardNames.getLocalName(typeCode));
                                        }
                                    }

                                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);

                                    // Get current value if possible for this control
                                    if (xFormsControls.isValueControl(controlElement.getName())) {
                                        String controlValue = XFormsInstance.getValueForNode(currentNode);
                                        ch.text(controlValue);
                                        //attributesImpl.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, );
                                    }

                                    ch.endElement();


                                    return true;
                                }
                                public boolean endVisitControl(Element controlElement, String effectiveControlId) { return true; }
                            });

                            ch.endElement();
                        }

                        // Output divs information
                        {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                            outputSwitchDivs(ch, xFormsControls);
                            ch.endElement();
                        }

                        // Output repeats information
                        {
                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeats");
                            if (isInitializationRun) {
                                outputInitialRepeats(ch, xFormsControls.getRepeatInfo());
                            } else {
                                outputRepeatsUpdates(ch, xFormsControls.getRepeatInfo());
                            }
                            ch.endElement();
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

                        ch.endElement();
                    }

                    ch.endElement();
                    contentHandler.endPrefixMapping("xxf");
                    ch.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    private void outputItemsets(ContentHandlerHelper ch, Map itemsetIdToItemsetInfoMap) {
        if (itemsetIdToItemsetInfoMap != null) {
            // There are some xforms:itemset controls

            for (Iterator i = itemsetIdToItemsetInfoMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String itemsetId = (String) currentEntry.getKey();
                final List items = (List) currentEntry.getValue();

                ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "itemset", new String[] { "id", itemsetId });
                for (Iterator j = items.iterator(); j.hasNext();) {
                    final XFormsControls.ItemsetInfo itemsetInfo = (XFormsControls.ItemsetInfo) j.next();

                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "item",
                            new String[] { "label", itemsetInfo.getLabel(), "value", itemsetInfo.getValue() });
                    ch.endElement();
                }
                ch.endElement();
            }
        }
    }

    private void outputSwitchDivs(Element divsElement, XFormsControls xFormsControls) {
        final Map switchInfoMap = xFormsControls.getSwitchIdToToSwitchInfoMap();
        if (switchInfoMap != null) {
            // There are some xforms:switch/xforms:case controls

            for (Iterator i = switchInfoMap.entrySet().iterator(); i.hasNext();) {
                final XFormsControls.SwitchInfo switchInfo = (XFormsControls.SwitchInfo) ((Map.Entry) i.next()).getValue();

                // Output selected ids
                {
                    final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    divElement.addAttribute("id", switchInfo.getSelectedCaseId());
                    divElement.addAttribute("visibility", "visible");
                }

                // Output deselected ids
                if (switchInfo.getDeselectedCaseIds() != null) {
                    for (Iterator j = switchInfo.getDeselectedCaseIds().iterator(); j.hasNext();) {
                        final String caseId = (String) j.next();

                        final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        divElement.addAttribute("id", caseId);
                        divElement.addAttribute("visibility", "hidden");
                    }
                }
            }
        }
    }

    private void outputSwitchDivs(ContentHandlerHelper ch, XFormsControls xFormsControls) {

        Map switchInfoMap = xFormsControls.getSwitchIdToToSwitchInfoMap();
        if (switchInfoMap != null) {
            // There are some xforms:switch/xforms:case controls

            for (Iterator i = switchInfoMap.entrySet().iterator(); i.hasNext();) {
                XFormsControls.SwitchInfo switchInfo = (XFormsControls.SwitchInfo) ((Map.Entry) i.next()).getValue();

                // Output selected ids
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", switchInfo.getSelectedCaseId(), "visibility", "visible"});

                // Output deselected ids
                if (switchInfo.getDeselectedCaseIds() != null) {
                    for (Iterator j = switchInfo.getDeselectedCaseIds().iterator(); j.hasNext();) {
                        String caseId = (String) j.next();
                        ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", caseId, "visibility", "hidden"});
                    }
                }
            }
        }
    }

    private void outputInitialRepeats(ContentHandlerHelper ch, XFormsControls.RepeatInfo repeatInfo) {
        if (repeatInfo != null) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeat",
                    new String[]{ "id", repeatInfo.getId(), "occurs", Integer.toString(repeatInfo.getOccurs()), "index", Integer.toString(repeatInfo.getIndex()) });
            if (repeatInfo.getChildren() != null) {
                for (Iterator i = repeatInfo.getChildren().iterator(); i.hasNext();) {
                    XFormsControls.RepeatInfo childRepeatInfo = (XFormsControls.RepeatInfo) i.next();
                    outputInitialRepeats(ch, childRepeatInfo);
                }
            }
            ch.endElement();
        }
    }

    private void outputRepeatsUpdates(ContentHandlerHelper ch, XFormsControls.RepeatInfo repeatInfo) {
        outputInitialRepeats(ch, repeatInfo);
    }

    public static XFormsContainingDocument createXFormsEngine(PipelineContext pipelineContext, String staticStateString,
                        String dynamicStateString) {

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
        XFormsContainingDocument containingDocument = new XFormsContainingDocument(models, controlsDocument);

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
                        currentModelInstancesCount = currentModel.getInstances().size();
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
            containingDocument.dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XXFORMS_INITIALIZE));
        else
            containingDocument.dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XXFORMS_INITIALIZE_STATE));

        // Set switch state
        // TODO: send this info with XFormsEvents.XXFORMS_INITIALIZE_STATE event?
        final XFormsControls xFormsControls = containingDocument.getXFormsControls();
        if (divsElement != null) {
            for (Iterator i = divsElement.elements().iterator(); i.hasNext();) {
                final Element divElement = (Element) i.next();

                final String caseId = divElement.attributeValue("id");
                final String visibility = divElement.attributeValue("visibility");

                xFormsControls.updateSwitchInfo(caseId, "visible".equals(visibility));
            }
        }

        // TODO: update repeat indexes from request if needed

        return containingDocument;
    }
}
