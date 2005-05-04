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
package org.orbeon.oxf.processor.xforms.event;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.processor.xforms.XFormsConstants;
import org.orbeon.oxf.processor.xforms.XFormsContainingDocument;
import org.orbeon.oxf.processor.xforms.XFormsModel;
import org.orbeon.oxf.processor.xforms.XFormsEvents;
import org.orbeon.oxf.processor.xforms.input.XFormsInstance;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.EmbeddedDocumentContentHandler;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.xpath.XPathException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Implements XForms event handling.
 */
public class XFormsServer extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(XFormsServer.class);

    private static final String INPUT_INSTANCES = "instances";
    private static final String INPUT_MODELS = "models";
    private static final String INPUT_CONTROLS = "controls";
    private static final String INPUT_EVENT = "event";

    private static final String OUTPUT_RESPONSE = "response";

    private final static Map XFORMS_NAMESPACES = new HashMap();

    static {
        XFORMS_NAMESPACES.put(XFormsConstants.XFORMS_SHORT_PREFIX, XFormsConstants.XFORMS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XML_EVENTS_PREFIX, XFormsConstants.XML_EVENTS_NAMESPACE_URI);
        XFORMS_NAMESPACES.put(XFormsConstants.XXFORMS_SHORT_PREFIX, XFormsConstants.XXFORMS_NAMESPACE_URI);
    }

    public XFormsServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCES));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODELS));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONTROLS));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_EVENT));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESPONSE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Get XForms model and instance, etc.
                Document instancesDocument = readInputAsDOM4J(pipelineContext, INPUT_INSTANCES);
                Document modelsDocument = readInputAsDOM4J(pipelineContext, INPUT_MODELS);
                Document controlsDocument = readInputAsDOM4J(pipelineContext, INPUT_CONTROLS);
                Document eventDocument = readInputAsDOM4J(pipelineContext, INPUT_EVENT);

                // Create XForms Engine
                XFormsContainingDocument containingDocument = new XFormsContainingDocument(controlsDocument);

                // Get models
                {
                    PooledXPathExpression xpathExpression =
                        XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(modelsDocument, null).wrap(controlsDocument),
                                "/xxf:models/*", XFORMS_NAMESPACES);
                    try {
                        for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                            Element modelElement = (Element) i.next();
                            Document modelDocument = Dom4jUtils.createDocument(modelElement);

                            XFormsModel model = new XFormsModel(modelDocument);
                            containingDocument.addModel(model);
                        }
                    } catch (XPathException e) {
                        throw new OXFException(e);
                    } finally {
                        if (xpathExpression != null)
                            xpathExpression.returnToPool();
                    }
                }

                // Get instances
                boolean isInitializeEvent;
                {
                    PooledXPathExpression xpathExpression =
                        XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(instancesDocument, null).wrap(controlsDocument),
                                "/xxf:instances/xxf:instance/*", XFORMS_NAMESPACES);
                    try {
                        int instancesCount = 0;
                        for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                            Element instanceElement = (Element) i.next();
                            Document instanceDocument = Dom4jUtils.createDocument(instanceElement);

                            // Set instance on model
                            ((XFormsModel) containingDocument.getModels().get(instancesCount)).setInstanceDocument(pipelineContext, instanceDocument);

                            instancesCount++;
                        }
                        if (instancesCount != 0 && containingDocument.getModels().size() != instancesCount)
                            throw new OXFException("Number of instances (" + instancesCount + ") doesn't match number of models (" + containingDocument.getModels().size()  + ").");
                        // Initialization will take place if no instances are provided
                        isInitializeEvent = instancesCount == 0;
                    } catch (XPathException e) {
                        throw new OXFException(e);
                    } finally {
                        if (xpathExpression != null)
                            xpathExpression.returnToPool();
                    }
                }

                // Initialize XForms Engine
                containingDocument.initialize(pipelineContext);
                if (isInitializeEvent)
                    containingDocument.dispatchEvent(pipelineContext, XFormsEvents.XXFORMS_INITIALIZE);

                // Extract action element
                Element actionElement;
                {
                    String controlId = eventDocument.getRootElement().attributeValue("source-control-id");
                    String eventName = eventDocument.getRootElement().attributeValue("name");

                    Map variables = new HashMap();
                    variables.put("control-id", controlId);
                    variables.put("control-name", eventName);

                    PooledXPathExpression xpathExpression =
                        XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument),
                                "/xxf:controls//*[@xxf:id = $control-id]/xf:*[@ev:event = $control-name]", XFORMS_NAMESPACES, variables);
                    try {
                        actionElement = (Element) xpathExpression.evaluateSingle();

                        if (actionElement == null)
                            throw new OXFException("Cannot find control with id '" + controlId + "' and event '" + eventName + "'.");
                    } catch (XPathException e) {
                        throw new OXFException(e);
                    } finally {
                        if (xpathExpression != null)
                            xpathExpression.returnToPool();
                    }
                }

                // Create resulting divs document
                Document divsDocument = Dom4jUtils.createDocument();
                divsDocument.addElement("xxf:divs", XFormsConstants.XXFORMS_NAMESPACE_URI);

                // Interpret action
                interpretAction(pipelineContext, actionElement, controlsDocument, divsDocument, containingDocument);

                // Create resulting document
                try {
                    ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                    ch.startDocument();
                    contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

                    // Output new controls values
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                    {
                        PooledXPathExpression xpathExpression =
                            XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument), "/xxf:controls//(xf:input|xf:secret|xf:textarea|xf:output|xf:upload|xf:range|xf:trigger|xf:submit|xf:select|xf:select1)[@ref]", XFORMS_NAMESPACES);
                        try {
                            for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                                Element controlElement = (Element) i.next();

                                String controlId = controlElement.attributeValue(new QName("id", XFormsConstants.XXFORMS_NAMESPACE));
                                String controlValue = getControlValue(pipelineContext, containingDocument, controlElement);

                                ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", new String[] {"id", controlId, "value", controlValue });
                            }
                        } catch (XPathException e) {
                            throw new OXFException(e);
                        } finally {
                            if (xpathExpression != null)
                                xpathExpression.returnToPool();
                        }
                    }

                    ch.endElement();

                    // Output updated instances
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instances");

                    for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                        XFormsModel model = (XFormsModel) i.next();

                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instance");
                        model.getInstance().read(new EmbeddedDocumentContentHandler(contentHandler));
                        ch.endElement();
                    }
                    ch.endElement();

                    // Output divs if needed

                    try {
                        LocationSAXWriter saxw = new LocationSAXWriter();
                        saxw.setContentHandler(new EmbeddedDocumentContentHandler(contentHandler));
                        saxw.write(divsDocument);
                    } catch (SAXException e) {
                        throw new OXFException(e);
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

    private void interpretAction(final PipelineContext pipelineContext, Element actionElement, Document controlsDocument, Document divsDocument,
                                XFormsContainingDocument containingDocument) {

        String actionNamespaceURI = actionElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        String actionEventName = actionElement.getName();

        if (XFormsEvents.XFORMS_SETVALUE_ACTION.equals(actionEventName)) {
            // 10.1.9 The setvalue Element
            // xforms:setvalue

            String ref = actionElement.attributeValue("ref");// TODO: support relative refs
            String value = actionElement.attributeValue("value");
            String content = actionElement.getStringValue();

            Map namespaceContext = Dom4jUtils.getNamespaceContext(actionElement);

            XFormsInstance instance = getInstanceFromSingleNodeBindingElement(containingDocument, actionElement);

            String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                valueToSet = instance.evaluateXPath(pipelineContext, value, namespaceContext);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            instance.setValueForParam(pipelineContext, ref, namespaceContext, valueToSet);
        } else if (XFormsEvents.XFORMS_TOGGLE_ACTION.equals(actionEventName)) {
            // 9.2.3 The toggle Element
            // xforms:toggle

            // Find case with that id and select it
            String caseId = actionElement.attributeValue("case");
            {
                Element divElement = Dom4jUtils.createElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                divElement.addAttribute("id", caseId);
                divElement.addAttribute("visibility", "visible");
                divsDocument.getRootElement().add(divElement);
            }

            // Deselect other cases in that switch
            {
                Map variables = new HashMap();
                variables.put("case-id", caseId);

                PooledXPathExpression xpathExpression =
                    XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument),
                            "/xxf:controls//xf:case[@id = $case-id]/ancestor::xf:switch[1]//xf:case[not(@id = $case-id)]", XFORMS_NAMESPACES, variables);
                try {

                    for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                        Element caseElement = (Element) i.next();

                        Element divElement = Dom4jUtils.createElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        divElement.addAttribute("id", caseElement.attributeValue(new QName("id")));
                        divElement.addAttribute("visibility", "hidden");
                        divsDocument.getRootElement().add(divElement);
                    }
                } catch (XPathException e) {
                    throw new OXFException(e);
                } finally {
                    if (xpathExpression != null)
                        xpathExpression.returnToPool();
                }
            }

            // TODO:
            // 1. Dispatching an xforms-deselect event to the currently selected case.
            // 2. Dispatching an xform-select event to the case to be selected.


        } else if (XFormsEvents.XFORMS_ACTION_ACTION.equals(actionEventName)) {
            // 10.1.1 The action Element
            // xforms:action

            for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                interpretAction(pipelineContext, embeddedActionElement, controlsDocument, divsDocument, containingDocument);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionEventName);
        }
    }

    private String getControlValue(final PipelineContext pipelineContext, XFormsContainingDocument containingDocument, Element controlElement) {

        String ref = controlElement.attributeValue("ref");// TODO: support relative refs
        XFormsInstance instance = getInstanceFromSingleNodeBindingElement(containingDocument, controlElement);

        Map namespaceContext = Dom4jUtils.getNamespaceContext(controlElement);
        return instance.evaluateXPath(pipelineContext, ref, namespaceContext);
    }

    private XFormsInstance getInstanceFromSingleNodeBindingElement(XFormsContainingDocument containingDocument, Element element) {
        String modelId = element.attributeValue("model");

        XFormsModel model = containingDocument.getModel(modelId == null ? "" : modelId);
        return model.getInstance();
    }
}
