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
import org.orbeon.oxf.xml.EmbeddedDocumentContentHandler;
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

    private static final String INPUT_INSTANCES = "instances";
    private static final String INPUT_MODELS = "models";
    private static final String INPUT_CONTROLS = "controls";
    private static final String INPUT_EVENT = "event";

    private static final String OUTPUT_RESPONSE = "response";

    public static final Map XFORMS_NAMESPACES = new HashMap();

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
                    }

                    // Output updated instances
                    {
                        ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instances");

                        for (Iterator i = containingDocument.getModels().iterator(); i.hasNext();) {
                            XFormsModel model = (XFormsModel) i.next();

                            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "instance");
                            model.getInstance().read(new EmbeddedDocumentContentHandler(contentHandler));
                            ch.endElement();
                        }
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
