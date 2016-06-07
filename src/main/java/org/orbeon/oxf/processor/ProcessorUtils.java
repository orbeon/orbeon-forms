/**
 * Copyright (C) 2009 Orbeon, Inc.
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

import org.orbeon.dom.*;
import org.orbeon.oxf.common.Defaults;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Headers;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.DateUtils;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.ContentHandler;
import org.xml.sax.helpers.AttributesImpl;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Iterator;

public class ProcessorUtils {

    public static final String HTML_CONTENT_TYPE = "text/html";
    public static final String DEFAULT_CONTENT_TYPE = XMLUtils.XML_CONTENT_TYPE2;
    //public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";

    public static final String DEFAULT_TEXT_DOCUMENT_ELEMENT = "document";
    public static final String DEFAULT_BINARY_DOCUMENT_ELEMENT = "document";

    public static LocationData getElementLocationData(Element element) {
        final Object elementData = element.getData();
        return (elementData instanceof LocationData) ? (LocationData) elementData : null;
    }

    public static boolean selectBooleanValue(Node node, String expr, boolean defaultValue) {
        final String result = XPathUtils.selectStringValueNormalize(node, expr);
        return (result == null) ? defaultValue : "true".equals(result);
    }

    public static int selectIntValue(Node node, String expr, int defaultValue) {
        Integer result = XPathUtils.selectIntegerValue(node, expr);
        return (result == null) ? defaultValue : result;
    }

    public static Processor createProcessorWithInputs(Element testNode) {
        return createProcessorWithInputs(testNode, false);
    }

    public static Processor createProcessorWithInputs(Element testNode, boolean saxDebug) {
        // Create processor
        QName processorName = XMLProcessorRegistry.extractProcessorQName(testNode);
        ProcessorFactory processorFactory = ProcessorFactoryRegistry.lookup(processorName);
        if (processorFactory == null)
            throw new OXFException("Cannot find processor factory with name '"
                    + processorName.getNamespacePrefix() + ":" + processorName.getName() + "'");

        Processor processor = processorFactory.createInstance();

        // Connect inputs
        for (Iterator j = XPathUtils.selectNodeIterator(testNode, "input"); j.hasNext();) {
            Element inputElement = (Element) j.next();
            String name = XPathUtils.selectStringValue(inputElement, "@name");
            if (XPathUtils.selectStringValue(inputElement, "@href") == null) {
                // Case of embedded XML
                Element originalElement = (Element) ((Element) inputElement).elementIterator().next();
                if (originalElement == null)
                    throw new OXFException("Input content is mandatory");
                Element copiedElement = Dom4jUtils.copyElementCopyParentNamespaces(originalElement);
                final String sid = Dom4jUtils.makeSystemId( originalElement );
                final DOMGenerator domGenerator = new DOMGenerator
                    (copiedElement, "input from pipeline utils", DOMGenerator.ZeroValidity, sid);

                if (saxDebug) {
                    final SAXLoggerProcessor loggerProcessor = new SAXLoggerProcessor();
                    PipelineUtils.connect(domGenerator, "data", loggerProcessor, "data");
                    PipelineUtils.connect(loggerProcessor, "data", processor, name);
                } else {
                    PipelineUtils.connect(domGenerator, "data", processor, name );
                }
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
        return (locationData != null && locationData.file() != null)
                ? URLFactory.createURL(locationData.file(), urlString)
                : URLFactory.createURL(urlString);
    }

    public static Document createDocumentFromEmbeddedOrHref(Element element, String urlString) {
        final Document result;
        if (urlString == null) {
            // Case of embedded XML
            final Element originalElement = (Element) ((Element) element).elementIterator().next();
            if (originalElement == null)
                throw new OXFException("Content for element '" + element.getName() + "' is mandatory");
            Element copiedElement = Dom4jUtils.copyElementCopyParentNamespaces(originalElement);
            result = DocumentFactory.createDocument();
            result.add(copiedElement);
        } else {
            // External URI
            final LocationData locationData = (LocationData) element.getData();
            result = createDocumentFromURL(urlString, locationData);
        }
        return result;
    }

    public static Document createDocumentFromURL(String urlString, LocationData locationData) {
        final URL url = createRelativeURL(locationData, urlString);

        URLGenerator urlGenerator = new URLGenerator(url);
        urlGenerator.setLocationData(locationData);
        final DOMSerializer domSerializer = new DOMSerializer();
        PipelineUtils.connect(urlGenerator, "data", domSerializer, "data");

        final PipelineContext pipelineContext = PipelineContext.get();
        return domSerializer.runGetDocument(pipelineContext);
    }

    /**
     * Generate a "standard" Orbeon text document.
     *
     * @param is                    InputStream to read from
     * @param encoding              character encoding to use, or null for default
     * @param output                output ContentHandler to write text document to
     * @param contentType           optional content type to set as attribute on the root element
     * @param lastModified          optional last modified timestamp
     */
    public static void readText(InputStream is, String encoding, ContentHandler output, String contentType, Long lastModified, int statusCode) {
        try {
            if (encoding == null)
                encoding = Defaults.DefaultEncodingForServletCompatibility();
            outputStartDocument(output, contentType, lastModified, statusCode, XMLConstants.XS_STRING_QNAME, DEFAULT_TEXT_DOCUMENT_ELEMENT);
            SAXUtils.readerToCharacters(new InputStreamReader(is, encoding), output);
            outputEndDocument(output, DEFAULT_TEXT_DOCUMENT_ELEMENT);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Generate a "standard" Orbeon text document.
     *
     * @param text                  String to read from
     * @param output                output ContentHandler to write text document to
     * @param contentType           optional content type to set as attribute on the root element
     * @param lastModified          optional last modified timestamp
     */
    public static void readText(String text, ContentHandler output, String contentType, Long lastModified) {
        try {
            outputStartDocument(output, contentType, lastModified, -1, XMLConstants.XS_STRING_QNAME, DEFAULT_TEXT_DOCUMENT_ELEMENT);
            output.characters(text.toCharArray(), 0, text.length());
            outputEndDocument(output, DEFAULT_TEXT_DOCUMENT_ELEMENT);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Generate a "standard" Orbeon binary document.
     *
     * @param is            InputStream to read from
     * @param output        output ContentHandler to write binary document to
     * @param contentType   optional content type to set as attribute on the root element
     * @param lastModified  optional last modified timestamp
     * @param statusCode    optional status code, or -1 is to ignore
     * @param fileName      optional filename
     */
    public static void readBinary(InputStream is, ContentHandler output, String contentType, Long lastModified, int statusCode, String fileName) {
        try {
            outputStartDocument(output, contentType, lastModified, statusCode, fileName, XMLConstants.XS_BASE64BINARY_QNAME, DEFAULT_BINARY_DOCUMENT_ELEMENT);
            SAXUtils.inputStreamToBase64Characters(new BufferedInputStream(is), output);
            outputEndDocument(output, DEFAULT_BINARY_DOCUMENT_ELEMENT);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Generate a "standard" Orbeon binary document.
     *
     * @param is            InputStream to read from
     * @param output        output ContentHandler to write binary document to
     * @param contentType   optional content type to set as attribute on the root element
     * @param lastModified  optional last modified timestamp
     * @param statusCode    optional status code, or -1 is to ignore
     */
    public static void readBinary(InputStream is, ContentHandler output, String contentType, Long lastModified, int statusCode) {
        readBinary(is, output, contentType, lastModified, statusCode, null);
    }

    private static void outputStartDocument(ContentHandler output, String contentType, Long lastModified, int statusCode, String fileName, QName type, String documentElement) {
        try {
            // Create attributes for root element: xsi:type, and optional content-type
            final AttributesImpl attributes = new AttributesImpl();
            attributes.addAttribute(XMLConstants.XSI_URI, "type", "xsi:type", "CDATA", type.getQualifiedName());
            if (contentType != null)
                attributes.addAttribute("", Headers.ContentTypeLower(), Headers.ContentTypeLower(), "CDATA", contentType);
            if (fileName != null)
                attributes.addAttribute("", "filename", "filename", "CDATA", fileName);
            if (lastModified != null)
                attributes.addAttribute("", Headers.LastModifiedLower(), Headers.LastModifiedLower(), "CDATA", DateUtils.RFC1123Date().print(lastModified));
            if (statusCode > 0)
                attributes.addAttribute("", "status-code", "status-code", "CDATA", Integer.toString(statusCode));

            // Write document
            output.startDocument();
            output.startPrefixMapping(XMLConstants.XSI_PREFIX, XMLConstants.XSI_URI);
            output.startPrefixMapping(XMLConstants.XSD_PREFIX, XMLConstants.XSD_URI);
            output.startElement("", documentElement, documentElement, attributes);
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    private static void outputStartDocument(ContentHandler output, String contentType, Long lastModified, int statusCode, QName type, String documentElement) {
        outputStartDocument(output, contentType, lastModified, statusCode, null, type, documentElement);
    }

    private static void outputEndDocument(ContentHandler output, String documentElement) {
        try {
            output.endElement("", documentElement, documentElement);
            output.endPrefixMapping(XMLConstants.XSD_PREFIX);
            output.endPrefixMapping(XMLConstants.XSI_PREFIX);
            output.endDocument();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
