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

import org.dom4j.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

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
            Element inputElement = (Element) j.next();
            String name = XPathUtils.selectStringValue(inputElement, "@name");
            if (XPathUtils.selectStringValue(inputElement, "@href") == null) {
                // Case of embedded XML
                Element originalElement = (Element) ((Element) inputElement).elementIterator().next();
                if (originalElement == null)
                    throw new OXFException("Input content is mandatory");
                Element copiedElement = originalElement.createCopy();
                addNeededNamespaceDeclarations(originalElement, copiedElement, new HashSet());
                DOMGenerator domGenerator = new DOMGenerator(copiedElement);
                PipelineUtils.connect(domGenerator, "data", processor, name);
            } else {
                // Href
                LocationData locationData = (LocationData) inputElement.getData();
                URL fullURL = createRelativeURL(locationData, XPathUtils.selectStringValue(inputElement, "@href"));

                URLGenerator urlGenerator = new URLGenerator(fullURL);
                urlGenerator.setLocationData(locationData);
                PipelineUtils.connect(urlGenerator, "data", processor, name);
            }
        }
        return processor;
    }

    public static URL createRelativeURL(LocationData locationData, String urlString) {
        try {
            return (locationData != null && locationData.getSystemID() != null)
                    ? URLFactory.createURL(locationData.getSystemID(), urlString)
                    : URLFactory.createURL(urlString);
        } catch (MalformedURLException e) {
            throw new ValidationException(e, locationData);
        }
    }

    public static Document createDocumentFromEmbeddedOrHref(Element element, String urlString) {
        Document result;
        if (urlString == null) {
            // Case of embedded XML
            Element originalElement = (Element) ((Element) element).elementIterator().next();
            if (originalElement == null)
                throw new OXFException("Content for element '" + element.getName() + "' is mandatory");
            Element copiedElement = originalElement.createCopy();
            addNeededNamespaceDeclarations(originalElement, copiedElement, new HashSet());
            result = XMLUtils.createDOM4JDocument();
            result.add(copiedElement);
        } else {
            // Href
            LocationData locationData = (LocationData) element.getData();
            URL url = createRelativeURL(locationData, urlString);

            URLGenerator urlGenerator = new URLGenerator(url);
            urlGenerator.setLocationData(locationData);
            DOMSerializer domSerializer = new DOMSerializer();
            PipelineUtils.connect(urlGenerator, "data", domSerializer, "data");

            PipelineContext domSerializerPipelineContext = new PipelineContext();
            domSerializer.start(domSerializerPipelineContext);
            result = domSerializer.getDocument(domSerializerPipelineContext);
        }
        return result;
    }

    private static void addNeededNamespaceDeclarations(Element originalElement, Element copyElement, Set alreadyDeclaredPrefixes) {
        Set newAlreadyDeclaredPrefixes = new HashSet(alreadyDeclaredPrefixes);

        // Add namespaces declared on this element
        for (Iterator i = copyElement.declaredNamespaces().iterator(); i.hasNext();) {
            Namespace namespace = (Namespace) i.next();
            newAlreadyDeclaredPrefixes.add(namespace.getPrefix());
        }

        // Add element prefix if needed
        String elementPrefix = copyElement.getNamespace().getPrefix();
        if (elementPrefix != null && !newAlreadyDeclaredPrefixes.contains(elementPrefix)) {
            copyElement.addNamespace(elementPrefix, originalElement.getNamespaceForPrefix(elementPrefix).getURI());;
            newAlreadyDeclaredPrefixes.add(elementPrefix);
        }

        // Add attribute prefixes if needed
        for (Iterator i = copyElement.attributes().iterator(); i.hasNext();) {
            Attribute attribute = (Attribute) i.next();
            String attributePrefix = attribute.getNamespace().getPrefix();
            if (attributePrefix != null && !newAlreadyDeclaredPrefixes.contains(attribute.getNamespace().getPrefix())) {
                copyElement.addNamespace(attributePrefix, originalElement.getNamespaceForPrefix(attributePrefix).getURI());
                newAlreadyDeclaredPrefixes.add(attributePrefix);
            }
        }

        // Get needed namespace declarations for children
        for (Iterator i = copyElement.elements().iterator(); i.hasNext();) {
            Element child = (Element) i.next();
            addNeededNamespaceDeclarations(originalElement, child, newAlreadyDeclaredPrefixes);
        }
    }
}
