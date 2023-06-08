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

import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.dom.Node;
import org.orbeon.dom.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.DOMGenerator;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.ContentTypes;
import org.orbeon.oxf.util.PipelineUtils;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.xml.dom.Extensions;
import org.orbeon.datatypes.LocationData;

import java.net.URL;
import java.util.Iterator;

public class ProcessorUtils {

    public static final String HTML_CONTENT_TYPE = ContentTypes.HtmlContentType();
    public static final String DEFAULT_CONTENT_TYPE = ContentTypes.XmlContentType();
    //public static final String DEFAULT_BINARY_CONTENT_TYPE = "application/octet-stream";

    public static LocationData getElementLocationData(Element element) {
        final Object elementData = element.getData();
        return (elementData instanceof LocationData) ? (LocationData) elementData : null;
    }

    public static boolean selectBooleanValue(Node node, String expr, boolean defaultValue) {
        final String result = XPathUtils.selectStringValueNormalize(node, expr);
        return (result == null) ? defaultValue : "true".equals(result);
    }

    public static Boolean selectBooleanValueOrNull(Node node, String expr) {
        final String result = XPathUtils.selectStringValueNormalize(node, expr);
        return (result == null) ? null : "true".equals(result);
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
                    + processorName.namespace().prefix() + ":" + processorName.localName() + "'");

        Processor processor = processorFactory.createInstance();

        // Connect inputs
        for (Iterator j = XPathUtils.selectNodeIterator(testNode, "input"); j.hasNext();) {
            Element inputElement = (Element) j.next();
            String name = XPathUtils.selectStringValue(inputElement, "@name");
            if (XPathUtils.selectStringValue(inputElement, "@href") == null) {
                // Case of embedded XML
                Element originalElement = (Element) ((Element) inputElement).jElementIterator().next();
                if (originalElement == null)
                    throw new OXFException("Input content is mandatory");
                Element copiedElement = Extensions.copyAndCopyParentNamespacesJava(originalElement);
                final String sid = ProcessorSupport.makeSystemId( originalElement );
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
            final Element originalElement = (Element) ((Element) element).jElementIterator().next();
            if (originalElement == null)
                throw new OXFException("Content for element '" + element.getName() + "' is mandatory");
            Element copiedElement = Extensions.copyAndCopyParentNamespacesJava(originalElement);
            result = Document.apply();
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
}
