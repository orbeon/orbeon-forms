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

import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ProcessorUtils {
    public static final String XML_CONTENT_TYPE1 = "text/xml";
    public static final String XML_CONTENT_TYPE2 = "application/xml";
    public static final String XML_CONTENT_TYPE3_SUFFIX = "+xml";

    public static final String TEXT_CONTENT_TYPE_PREFIX = "text/";
    public static final String HTML_CONTENT_TYPE = "text/html";
    public static final String DEFAULT_CONTENT_TYPE = XML_CONTENT_TYPE2;
    public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";

    public static final Map supportedBinaryTypes = new HashMap();
    static {
        supportedBinaryTypes.put(XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName(), XMLConstants.XS_BASE64BINARY_QNAME.getQualifiedName());
        supportedBinaryTypes.put(XMLConstants.XS_ANYURI_QNAME.getQualifiedName(), XMLConstants.XS_ANYURI_QNAME.getQualifiedName());
    }

    public static boolean isXMLContentType(String contentType) {
        if (contentType == null)
            return false;
        return contentType.equals(XML_CONTENT_TYPE1)
                || contentType.equals(XML_CONTENT_TYPE2)
                || contentType.endsWith(XML_CONTENT_TYPE3_SUFFIX);
    }

    public static boolean isTextContentType(String contentType) {
        return contentType != null && contentType.startsWith(TEXT_CONTENT_TYPE_PREFIX);
    }

    public static boolean selectBooleanValue(Node node, String expr, boolean defaultValue) {
        String result = XPathUtils.selectStringValueNormalize(node, expr);
        return (result == null) ? defaultValue : "true".equals(result);
    }

    public static int selectIntValue(Node node, String expr, int defaultValue) {
        Integer result = XPathUtils.selectIntegerValue(node, expr);
        return (result == null) ? defaultValue : result.intValue();
    }

    public static Processor createProcessorWithInputs(Element testNode, PipelineContext pipelineContext) {
        // Create processor
        QName processorName = XMLProcessorRegistry.extractProcessorQName(testNode);
        ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);
        if (processorFactory == null)
            throw new OXFException("Cannot find processor factory with name '"
                    + processorName.getNamespacePrefix() + ":" + processorName.getName() + "'");
        Processor processor = processorFactory.createInstance(pipelineContext);

        // Connect inputs
        for (Iterator j = XPathUtils.selectIterator(testNode, "input"); j.hasNext();) {
            Node inputNode = (Node) j.next();
            String name = XPathUtils.selectStringValue(inputNode, "@name");
            if (XPathUtils.selectStringValue(inputNode, "@href") == null) {
                // Case of embedded XML
                Node data = (Node) ((Element) inputNode).elementIterator().next();
                DOMGenerator domGenerator = new DOMGenerator(Dom4jUtils.cloneNode(data));
                PipelineUtils.connect(domGenerator, "data", processor, name);
            } else {
                // Href
                URLGenerator urlGenerator = new URLGenerator(XPathUtils.selectStringValue(inputNode, "@href"));
                PipelineUtils.connect(urlGenerator, "data", processor, name);
            }
        }
        return processor;
    }
}
