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
                Document requestDocument = readInputAsDOM4J(pipelineContext, INPUT_REQUEST);
                Document eventDocument = Dom4jUtils.createDocument(requestDocument.getRootElement().element(XFormsConstants.XXFORMS_EVENT_QNAME));

                String encodedControlsString = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_CONTROLS_QNAME).getTextTrim();

                Element encodedModelsElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_MODELS_QNAME);
                String encodedModelsString = encodedModelsElement.getTextTrim();

                Element encodedInstancesElement = requestDocument.getRootElement().element(XFormsConstants.XXFORMS_INSTANCES_QNAME);
                String encodedInstancesString = encodedInstancesElement.getTextTrim();

                // Create and initialize XForms engine from encoded data
                XFormsContainingDocument containingDocument = createXFormsEngine(pipelineContext, encodedControlsString,
                        encodedModelsString, encodedInstancesString);

                // Run event if any
                XFormsGenericEvent xformsEvent = null;
                {
                    final Element eventElement = eventDocument.getRootElement();
                    String controlId = eventElement.attributeValue("source-control-id");
                    String eventName = eventElement.attributeValue("name");
                    String value = eventElement.attributeValue("value");

                    if (controlId != null && eventName != null) {
                        // An event is passed
                        xformsEvent = containingDocument.executeEvent(pipelineContext, controlId, eventName, value);
                    } else if (!(controlId == null && eventName == null)) {
                        throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                    }
                }

                // Create resulting document
                final XFormsControls xFormsControls = containingDocument.getXFormsControls();
                try {
                    final ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                    ch.startDocument();
                    contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

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

                                // Get current value if possible for this control
                                if (xFormsControls.isValueControl(controlElement.getName())) {
                                    String controlValue = XFormsInstance.getValueForNode(currentNode);
                                    attributesImpl.addAttribute("", "value", "value", ContentHandlerHelper.CDATA, controlValue);
                                }

                                // TODO: for xforms:case, must provide whether it is selected or not

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
                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", attributesImpl);
                                return true;
                            }
                            public boolean endVisitControl(Element controlElement, String effectiveControlId) { return true; }
                        });

                        ch.endElement();
                    }

                    // Output updated instances
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instances");

                        Document instancesDocument = Dom4jUtils.createDocument();

                        // Move all instances of all models in sequence under a single root element
                        {
                            instancesDocument.addElement("instances");
                            Element instancesElement = instancesDocument.getRootElement();

                            for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                                XFormsModel currentModel = (XFormsModel) i.next();

                                for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                                    instancesElement.add(((XFormsInstance) j.next()).getDocument().getRootElement().detach());
                                }
                            }
                        }

                        // Encode all instances
                        String encodedInstance = XFormsUtils.encodeXMLAsDOM(pipelineContext, instancesDocument);
                        ch.text(encodedInstance);

                        ch.endElement();
                    }

                    // Output divs information
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");
                        if (xformsEvent == null) {
                            outputInitialDivs();
                        } else {
                            outputDivsUpdates(ch, xformsEvent);
                        }
                        ch.endElement();
                    }

                    // Output repeats information
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "repeats");
                        if (xformsEvent == null) {
                            outputInitialRepeats(ch, xFormsControls.getRepeatInfo());
                        } else {
                            outputInitialRepeats(ch, xFormsControls.getRepeatInfo());
                            //outputRepeatsUpdates();
                            // TODO
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

    private void outputInitialDivs() {
        // TODO
    }

    private void outputDivsUpdates(ContentHandlerHelper ch, XFormsGenericEvent xformsEvent) {
        if (xformsEvent.getDivsToHide() != null) {
            for (Iterator i = xformsEvent.getDivsToHide().iterator(); i.hasNext();) {
                String caseId = (String) i.next();
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", caseId, "visibility", "hidden"});
            }
        }
        if (xformsEvent.getDivsToShow() != null) {
            for (Iterator i = xformsEvent.getDivsToShow().iterator(); i.hasNext();) {
                String caseId = (String) i.next();
                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[]{"id", caseId, "visibility", "visible"});
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

    private void outputRepeatsUpdates() {
        // TODO
    }

    public static XFormsContainingDocument createXFormsEngine(PipelineContext pipelineContext, String encodedControlsString,
                                                              String encodedModelsString, String encodedInstancesString) {
        // Get controls
        Document controlsDocument = XFormsUtils.decodeXML(pipelineContext, encodedControlsString);

        // Get models
        final List models = new ArrayList();
        {
            Document modelsDocument = XFormsUtils.decodeXML(pipelineContext, encodedModelsString);
            // FIXME: we don't get a System ID here. Is there a simple solution?

            for (Iterator i = modelsDocument.getRootElement().elements().iterator(); i.hasNext();) {
                Element modelElement = (Element) i.next();

                Document modelDocument = Dom4jUtils.createDocumentCopyParentNamespaces(modelElement);
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
            if (!"".equals(encodedInstancesString)) {
                // Aggregate instances document containing all instances in sequence
                Document instancesDocument = XFormsUtils.decodeXML(pipelineContext, encodedInstancesString);

                // Iterator over all the models
                Iterator modelIterator = containingDocument.getModels().iterator();

                XFormsModel currentModel = null;
                int currentModelInstancesCount = 0;
                int currentCount = 0;

                for (Iterator i = instancesDocument.getRootElement().elements().iterator(); i.hasNext();) {
                    Element instanceElement = (Element) i.next();

                    // Go to next model if needed
                    if (currentCount == currentModelInstancesCount) {
                        currentModel = (XFormsModel) modelIterator.next();
                        currentModelInstancesCount = currentModel.getInstanceCount();
                        currentCount = 0;

                        expectedInstancesCount += currentModelInstancesCount;
                    }

                    // Create and set instance document on current model
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
            containingDocument.dispatchEvent(pipelineContext, new XFormsGenericEvent(), XFormsEvents.XXFORMS_INITIALIZE);
        else
            containingDocument.dispatchEvent(pipelineContext, new XFormsGenericEvent(), XFormsEvents.XXFORMS_INITIALIZE_CONTROLS);

        // TODO: update repeat indexes from request if needed

        return containingDocument;
    }
}
