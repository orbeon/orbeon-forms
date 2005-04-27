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
import org.orbeon.oxf.processor.xforms.Model;
import org.orbeon.oxf.processor.xforms.input.Instance;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.EmbeddedDocumentContentHandler;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;

import java.util.Iterator;
import java.util.Map;

/**
 * Implements XForms event handling.
 */
public class XFormsEvent extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(XFormsEvent.class);

    final static private String INPUT_INSTANCE = "instance";
    final static private String INPUT_MODEL = "model";
    final static private String INPUT_CONTROLS = "controls";
    final static private String INPUT_ACTION = "action";

    final static private String OUTPUT_RESPONSE = "response";

    public XFormsEvent() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MODEL));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONTROLS));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_ACTION));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_RESPONSE));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(final PipelineContext pipelineContext, ContentHandler contentHandler) {

                // Extract information from XForms model
//                Model model = (Model) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_MODEL), new CacheableInputReader(){
//                    public Object read(PipelineContext context, ProcessorInput input) {
//                        return new Model(pipelineContext, readInputAsDOM4J(context, input));
//                    }
//                });

                // Get XForms instance
//                Instance instance = (Instance) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_INSTANCE), new CacheableInputReader(){
//                    public Object read(PipelineContext context, ProcessorInput input) {
//                        return new Instance(pipelineContext, readInputAsDOM4J(context, input));
//                    }
//                });

                Model model = new Model(pipelineContext, readInputAsDOM4J(pipelineContext, INPUT_MODEL));
                Instance instance = new Instance(pipelineContext, readInputAsDOM4J(pipelineContext, INPUT_INSTANCE));
                Document controlsDocument = readInputAsDOM4J(pipelineContext, INPUT_CONTROLS);

                // Get action document
                Document actionDocument = readInputAsDOM4J(pipelineContext, getInputByName(INPUT_ACTION));
                Element actionElement = actionDocument.getRootElement();

                // Interpret actions
                interpretAction(pipelineContext, actionElement, instance);

                // Create resulting document
                ContentHandlerHelper ch = new ContentHandlerHelper(contentHandler);
                ch.startDocument();
                ch.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response");

                // Output new controls values
                ch.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values");
                for (Iterator i = XPathUtils.selectIterator(controlsDocument, "/*/*[@ref]"); i.hasNext();) {//TODO: correctly identify children controls
                    Element controlElement = (Element) i.next();

                    String controlId = controlElement.attributeValue(new QName("id", XFormsConstants.XXFORMS_NAMESPACE));
                    String controlValue = getControlValue(pipelineContext, controlElement, instance);

                    ch.element(XFormsConstants.XXFORMS_NAMESPACE_URI, "control", new String[] {"id", controlId, "value", controlValue });
                }
                ch.endElement();

                // Output updated instances
                ch.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "instances");
                ch.startElement(XFormsConstants.XXFORMS_NAMESPACE_URI, "instance");
                instance.read(new EmbeddedDocumentContentHandler(contentHandler));
                ch.endElement();
                ch.endElement();

                ch.endElement();
                ch.endDocument();
            }
        };
        addOutput(name, output);
        return output;
    }

    private void interpretAction(final PipelineContext pipelineContext, Element actionElement, Instance instance) {

        String actionNamespaceURI = actionElement.getNamespaceURI();
        if (!XFormsConstants.XFORMS_NAMESPACE_URI.equals(actionNamespaceURI)) {
            throw new OXFException("Invalid action namespace: " + actionNamespaceURI);
        }

        String actionName = actionElement.getName();
        if ("setvalue".equals(actionName)) {
            // xforms:setvalue

            String ref = actionElement.attributeValue("ref");// TODO: support relative refs
            String value = actionElement.attributeValue("value");
            String content = actionElement.getStringValue();

            Map namespaceContext = Dom4jUtils.getNamespaceContext(actionElement);

            String valueToSet;
            if (value != null) {
                // Value to set is computed with an XPath expression
                valueToSet = instance.evaluateXPath(pipelineContext, value, namespaceContext);
            } else {
                // Value to set is static content
                valueToSet = content;
            }

            instance.setValueForParam(pipelineContext, ref, namespaceContext, valueToSet);
        } else if ("action".equals(actionName)) {
            // xforms:action

            for (Iterator i = actionElement.elementIterator(); i.hasNext();) {
                Element embeddedActionElement = (Element) i.next();
                interpretAction(pipelineContext, embeddedActionElement, instance);
            }

        } else {
            throw new OXFException("Invalid action requested: " + actionName);
        }
    }

    private String getControlValue(final PipelineContext pipelineContext, Element controlElement, Instance instance) {

        String ref = controlElement.attributeValue("ref");// TODO: support relative refs

        Map namespaceContext = Dom4jUtils.getNamespaceContext(controlElement);
        return instance.evaluateXPath(pipelineContext, ref, namespaceContext);
    }
}
