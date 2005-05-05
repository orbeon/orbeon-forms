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
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.ProcessorOutput;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
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
                System.out.println(Dom4jUtils.domToString(requestDocument));
                Document eventDocument = Dom4jUtils.createDocument(requestDocument.getRootElement().element(new QName("event", XFormsConstants.XXFORMS_NAMESPACE)));

                // Get controls
                String encodedControlsString = requestDocument.getRootElement().element(new QName("controls", XFormsConstants.XXFORMS_NAMESPACE)).getText();
                Document controlsDocument = XFormsUtils.decodeXML(pipelineContext, encodedControlsString);

                // Create XForms Engine
                XFormsContainingDocument containingDocument = new XFormsContainingDocument(controlsDocument);

                // Get models
                {
                    Element encodedModelsElement = requestDocument.getRootElement().element(new QName("models", XFormsConstants.XXFORMS_NAMESPACE));
                    String encodedModelsString = encodedModelsElement.getText();
                    Document modelsDocument = XFormsUtils.decodeXML(pipelineContext, encodedModelsString);

                    for (Iterator i = modelsDocument.getRootElement().elements().iterator(); i.hasNext();) {
                        Element modelElement = (Element) i.next();

                        Document modelDocument = Dom4jUtils.createDocument(modelElement);
                        XFormsModel model = new XFormsModel(modelDocument);
                        containingDocument.addModel(model);
                    }

                }

                // Get instances
                boolean isInitializeEvent;
                {

                    Element encodedInstancesElement = requestDocument.getRootElement().element(new QName("instances", XFormsConstants.XXFORMS_NAMESPACE));
                    String encodedInstancesString = encodedInstancesElement.getText();
                    Document instancesDocument = XFormsUtils.decodeXML(pipelineContext, encodedInstancesString);

                    int instancesCount = 0;
                    for (Iterator i = instancesDocument.getRootElement().elements().iterator(); i.hasNext();) {
                        Element instanceElement = (Element) i.next();

                        Document instanceDocument = Dom4jUtils.createDocument(instanceElement);
                        ((XFormsModel) containingDocument.getModels().get(instancesCount)).setInstanceDocument(pipelineContext, instanceDocument);

                        instancesCount++;
                    }
                    // Number of instances must be zero or match number of models
                    if (instancesCount != 0 && containingDocument.getModels().size() != instancesCount)
                        throw new OXFException("Number of instances (" + instancesCount + ") doesn't match number of models (" + containingDocument.getModels().size()  + ").");
                    // Initialization will take place if no instances are provided
                    isInitializeEvent = instancesCount == 0;
                }

                // Initialize XForms Engine
                containingDocument.initialize(pipelineContext);
                if (isInitializeEvent)
                    containingDocument.dispatchEvent(pipelineContext, XFormsEvents.XXFORMS_INITIALIZE);

                // Run action if any
                XFormsContainingDocument.ActionResult actionResult = null;
                {
                    String controlId = eventDocument.getRootElement().attributeValue("source-control-id");
                    String eventName = eventDocument.getRootElement().attributeValue("name");

                    if (controlId != null && eventName != null) {
                        // An event is passed
                        actionResult = containingDocument.executeAction(pipelineContext, controlId, eventName);
                    } else if (!(controlId == null && eventName == null)) {
                        throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.");
                    }
                }

                // Create resulting document
                try {
                    ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                    ch.startDocument();
                    contentHandler.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI);
                    ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

                    // Output new controls values
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");

                        {
                            PooledXPathExpression xpathExpression =
                                XPathCache.getXPathExpression(pipelineContext, new DocumentWrapper(controlsDocument, null).wrap(controlsDocument), "/xxf:controls//(xf:input|xf:secret|xf:textarea|xf:output|xf:upload|xf:range|xf:trigger|xf:submit|xf:select|xf:select1)[@ref]", XFORMS_NAMESPACES);
                            try {
                                for (Iterator i = xpathExpression.evaluate().iterator(); i.hasNext();) {
                                    Element controlElement = (Element) i.next();

                                    String controlId = controlElement.attributeValue(new QName("id", XFormsConstants.XXFORMS_NAMESPACE));
                                    String controlValue = XFormsUtils.getControlValue(pipelineContext, containingDocument, controlElement);

                                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control", new String[] { "id", controlId, "value", controlValue });
                                }
                            } catch (XPathException e) {
                                throw new OXFException(e);
                            } finally {
                                if (xpathExpression != null)
                                    xpathExpression.returnToPool();
                            }
                        }

                        ch.endElement();
                    }

                    // Output updated instances
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instances");

                        Document instancesDocument = Dom4jUtils.createDocument();

                        // Move all instances under a single root element
                        {
                            instancesDocument.addElement("instances");
                            Element instancesElement = instancesDocument.getRootElement();

                            for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                                XFormsModel model = (XFormsModel) i.next();

                                instancesElement.add(model.getInstance().getDocument().getRootElement().detach());
                            }
                        }

                        // Encode all instances
                        String encodedInstance = XFormsUtils.encodeXMLAsDOM(pipelineContext, instancesDocument);
                        ch.text(encodedInstance);

                        ch.endElement();
                    }

                    // Output divs
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "divs");

                        if (actionResult != null) {
                            if (actionResult.getDivsToHide() != null) {
                                for (Iterator i = actionResult.getDivsToHide().iterator(); i.hasNext();) {
                                    String caseId = (String) i.next();
                                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] { "id", caseId, "visibility", "hidden" });
                                }
                            }
                            if (actionResult.getDivsToShow() != null) {
                                for (Iterator i = actionResult.getDivsToShow().iterator(); i.hasNext();) {
                                    String caseId = (String) i.next();
                                    ch.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "div", new String[] { "id", caseId, "visibility", "visible" });
                                }
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
        };
        addOutput(name, output);
        return output;
    }

}
