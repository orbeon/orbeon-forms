/**
 *  Copyright (C) 2004 Orbeon, Inc.
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
package org.orbeon.oxf.processor;

import org.dom4j.Attribute;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.xforms.XFormsUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.*;

/**
 * Convert an XForms instance into a list of parameters.
 *
 * A filter can be provided. It contains XPath references to nodes that should not be included in
 * the result. The format of the filter comes directly from the native document created in the WAC,
 * for example:
 *
 * <params>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/x"/>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/y"/>
 *    <param xmlns="http://www.orbeon.com/oxf/controller" ref="/form/z"/>
 * </params>
 */
public class InstanceToParametersProcessor extends ProcessorImpl {

    public static final String PARAMETERS_ELEMENT = "parameters";
    public static final String PARAMETER_ELEMENT = "parameter";
    public static final String NAME_ELEMENT = "name";
    public static final String VALUE_ELEMENT = "value";

    private static final String INPUT_INSTANCE = "instance";
    private static final String INPUT_FILTER = "filter";

    public InstanceToParametersProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_INSTANCE));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_FILTER));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.ProcessorOutputImpl(getClass(), name) {
            public void readImpl(PipelineContext pipelineContext, final ContentHandler contentHandler) {
                try {
                    // Get parameters from instance
                    List mapping = (List) readCacheInputAsObject(pipelineContext, getInputByName(INPUT_INSTANCE), new CacheableInputReader() {
                        public Object read(PipelineContext context, ProcessorInput input) {
                            try {
                                Element instanceElement = readInputAsDOM4J(context, INPUT_INSTANCE).getRootElement();
                                Element filterElement = readInputAsDOM4J(context, INPUT_FILTER).getRootElement();

                                Map excludes = null;
                                for (Iterator i = XPathUtils.selectIterator(filterElement, "/*/*/@ref"); i.hasNext();) {
                                    Attribute attribute = (Attribute) i.next();
                                    String excludeRef = attribute.getValue();
                                    // FIXME: Argh, we don't handle namespaces correctly here!
                                    // If the param element in the WAC contains namespaces, this won't work!
                                    Node excludedNode = XPathUtils.selectSingleNode(instanceElement, excludeRef);
                                    if (excludedNode != null) {
                                        if (excludes == null)
                                            excludes = new HashMap();
                                        excludes.put(excludedNode, excludeRef);
                                    }
                                }

                                final String hidden;
                                {
                                    // Get names/values from instance
                                    List instanceMapping = new ArrayList();
                                    if (instanceElement != null)
                                        handleElement(instanceElement, excludes, instanceMapping);

                                    // Encode names/values in a string
                                    StringBuffer hiddenBuffer = new StringBuffer();
                                    boolean first = true;
                                    for (Iterator i = instanceMapping.iterator(); i.hasNext();) {
                                        NameValue nameValue = (NameValue) i.next();
                                        if (first) first = false; else hiddenBuffer.append('^');
                                        hiddenBuffer.append(XFormsUtils.isNameEncryptionEnabled() && !XFormsUtils.isHiddenEncryptionEnabled()
                                            ? XFormsUtils.encrypt(nameValue.getName()): nameValue.getName());
                                        hiddenBuffer.append('^');
                                        hiddenBuffer.append(URLEncoder.encode(nameValue.getValue(), NetUtils.DEFAULT_URL_ENCODING));
                                    }
                                    hidden = XFormsUtils.isHiddenEncryptionEnabled() 
                                            ? XFormsUtils.encrypt(hiddenBuffer.toString()) : hiddenBuffer.toString();
                                }

                                List mapping = new ArrayList();
                                mapping.add(new NameValue("$submitted", "true"));
                                mapping.add(new NameValue("$hidden", hidden));
                                return mapping;
                            } catch (UnsupportedEncodingException e) {
                                throw new OXFException(e);
                            }
                        }
                    });

                    // Output as SAX
                    contentHandler.startDocument();
                    contentHandler.startElement("", PARAMETERS_ELEMENT, PARAMETERS_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);
                    for (Iterator i = mapping.iterator(); i.hasNext();) {
                        NameValue nameValue = (NameValue) i.next();
                        outputParameter(nameValue.getName(), nameValue.getValue(), contentHandler);
                    }
                    contentHandler.endElement("", PARAMETERS_ELEMENT, PARAMETERS_ELEMENT);
                    contentHandler.endDocument();

                } catch (Exception e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(OUTPUT_DATA, output);
        return output;
    }

    private static void handleElement(Element element, Map excludes, List mapping) {

        // Handle attributes
        for (Iterator i = element.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            if (excludes == null || excludes.get(attribute) == null)
                mapping.add(new NameValue(XFormsUtils.getNameForNode(attribute, false), attribute.getValue()));
        }

        List children = element.elements();
        if (children.isEmpty()) {
            // Add parameter for text
            if (excludes == null || excludes.get(element) == null)
                mapping.add(new NameValue(XFormsUtils.getNameForNode(element, false), element.getText()));
        } else {

            // Handle children
            for (Iterator i = children.iterator(); i.hasNext();) {
                Element child = (Element) i.next();
                handleElement(child, excludes, mapping);
            }
        }
    }

    private static void outputParameter(String name, String value, ContentHandler contentHandler) throws SAXException {
        contentHandler.startElement("", PARAMETER_ELEMENT, PARAMETER_ELEMENT, XMLUtils.EMPTY_ATTRIBUTES);
        outputElement(NAME_ELEMENT, name, contentHandler);
        outputElement(VALUE_ELEMENT, value, contentHandler);
        contentHandler.endElement("", PARAMETER_ELEMENT, PARAMETER_ELEMENT);
    }

    private static void outputElement(String name, String content, ContentHandler contentHandler) throws SAXException {
        contentHandler.startElement("", name, name, XMLUtils.EMPTY_ATTRIBUTES);
        contentHandler.characters(content.toCharArray(), 0, content.length());
        contentHandler.endElement("", name, name);
    }

    private static class NameValue {
        private String name;
        private String value;

        public NameValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}

