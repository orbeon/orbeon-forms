/**
 * Copyright (C) 2010 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor;

import org.orbeon.dom.Attribute;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.VisitorSupport;
import org.orbeon.dom.saxon.DocumentWrapper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.PooledXPathExpression;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xml.EncodeDecode;
import org.orbeon.xml.NamespaceMapping;
import org.orbeon.oxf.xml.SAXUtils;
import org.orbeon.oxf.xml.XMLReceiver;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Convert an XForms instance into a list of parameters.
 *
 * A filter can be provided. It contains XPath references to nodes that should not be included in
 * the result. The format of the filter comes directly from the native document created in the WAC,
 * for example:
 *
 * <params xmlns="http://www.orbeon.com/oxf/controller">
 *    <param ref="/form/x"/>
 *    <param ref="/form/y"/>
 *    <param ref="/form/z"/>
 * </params>
 */
public class InstanceToParametersProcessor extends ProcessorImpl {

    public static final String PARAMETERS_ELEMENT = "parameters";
    public static final String PARAMETER_ELEMENT = "parameter";
    public static final String NAME_ELEMENT = "name";
    public static final String VALUE_ELEMENT = "value";

    private static final String INPUT_INSTANCE = "instance";
    private static final String INPUT_FILTER = "filter";

    protected InstanceToParametersProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        final ProcessorOutput output = new ProcessorOutputImpl(InstanceToParametersProcessor.this, name) {
            public void readImpl(PipelineContext pipelineContext, final XMLReceiver xmlReceiver) {
                try {
                    final Element filterElement = readInputAsOrbeonDom(pipelineContext, INPUT_FILTER).getRootElement();
                    final Document instance = ( Document ) readInputAsOrbeonDom( pipelineContext, INPUT_INSTANCE ).deepCopy();

                    final LocationData locationData = ((LocationData) instance.getRootElement().getData());

                    final DocumentWrapper instanceWrapper =
                        new DocumentWrapper(
                            instance,
                            (locationData != null) ? locationData.file() : null,
                            org.orbeon.oxf.util.XPath.GlobalConfiguration()
                        );

                    // Mark all nodes referenced by XPath expressions
                    final Set<Object> markedNodes = new HashSet<Object>();
                    for (Iterator i = filterElement.jElements().iterator(); i.hasNext();) {
                        final Element paramElement   = (Element) i.next();
                        final Attribute refAttribute = paramElement.attribute("ref");
                        final String excludeRef      = refAttribute.getValue();

                        final PooledXPathExpression xpath =
                            XPathCache.getXPathExpression(
                                instanceWrapper.getConfiguration(),
                                instanceWrapper.wrap(instance),
                                excludeRef,
                                NamespaceMapping.apply(Dom4jUtils.getNamespaceContextNoDefault(paramElement)),
                                getLocationData()
                            );

                        markedNodes.add(xpath.evaluateSingleToJavaReturnToPoolOrNull());
                    }

                    // See if all nodes are marked
                    final boolean[] allMarked = { true };
                    instance.accept(new VisitorSupport() {
                        public void visit(Element node) {
                            super.visit(node);
                            if (node.jElements().size() == 0 && !markedNodes.contains(node))
                                allMarked[0] = false;
                        }

                        public void visit(Attribute node) {
                            super.visit(node);
                            if (!markedNodes.contains(node))
                                allMarked[0] = false;
                        }
                    });

                    // Output as SAX
                    xmlReceiver.startDocument();
                    xmlReceiver.startElement("", PARAMETERS_ELEMENT, PARAMETERS_ELEMENT, SAXUtils.EMPTY_ATTRIBUTES);
                    if (! allMarked[0]) {
                        // If all the nodes of the instance map to parameters, we don't output the instance parameter
                        outputParameter("$instance", EncodeDecode.encodeXML(instance, true, true, false), xmlReceiver);
                    }
                    xmlReceiver.endElement("", PARAMETERS_ELEMENT, PARAMETERS_ELEMENT);
                    xmlReceiver.endDocument();

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(OUTPUT_DATA, output);
        return output;
    }

    private static void outputParameter(String name, String value, ContentHandler contentHandler) throws SAXException {
        contentHandler.startElement("", PARAMETER_ELEMENT, PARAMETER_ELEMENT, SAXUtils.EMPTY_ATTRIBUTES);
        outputElement(NAME_ELEMENT, name, contentHandler);
        outputElement(VALUE_ELEMENT, value, contentHandler);
        contentHandler.endElement("", PARAMETER_ELEMENT, PARAMETER_ELEMENT);
    }

    private static void outputElement(String name, String content, ContentHandler contentHandler) throws SAXException {
        contentHandler.startElement("", name, name, SAXUtils.EMPTY_ATTRIBUTES);
        contentHandler.characters(content.toCharArray(), 0, content.length());
        contentHandler.endElement("", name, name);
    }
}
