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
package org.orbeon.oxf.xml;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Stack;

/**
 * Wrapper to an XML receiver. Provides more high-level methods to send events to a XML receiver.
 */
public class ContentHandlerHelper {

    public static final String CDATA = "CDATA";
    private Stack<ElementInfo> elements = new Stack<ElementInfo>();
    private XMLReceiver xmlReceiver;
    private AttributesImpl attributesImpl = new AttributesImpl();

    private static class ElementInfo {
        public final String uri;
        public final String name;
        public final String qName;

        private ElementInfo(String uri, String name, String qName) {
            this.uri = uri;
            this.name = name;
            this.qName = qName;
        }
    }

    public ContentHandlerHelper(XMLReceiver xmlReceiver) {
        this.xmlReceiver = xmlReceiver;
    }

    /**
     * ContentHandler to write to. 
     *
     * @param xmlReceiver       receiver to write to
     * @param validateStream    true if the stream must be validated by InspectingContentHandler
     */
    public ContentHandlerHelper(XMLReceiver xmlReceiver, boolean validateStream) {
        if (validateStream)
            this.xmlReceiver = new InspectingContentHandler(xmlReceiver);
        else
            this.xmlReceiver = xmlReceiver;
    }

    public XMLReceiver getXmlReceiver() {
        return xmlReceiver;
    }

    public void startElement(String name) {
        startElement("", name);
    }

    public void startElement(String namespaceURI, String name) {
        startElement("", namespaceURI, name);
    }

    public void startElement(String prefix, String namespaceURI, String name) {
        attributesImpl.clear();
        startElement(prefix, namespaceURI, name, attributesImpl);
    }

    public void startElement(String name, Attributes attributes) {
        startElement("", "", name, attributes);
    }

    public void startElement(String prefix, String namespaceURI, String name, Attributes attributes) {
        try {
            final String qName = XMLUtils.buildQName(prefix, name);
            xmlReceiver.startElement(namespaceURI, name, qName, attributes);
            elements.add(new ElementInfo(namespaceURI, name, qName));
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void startElement(String name, String[] attributes) {
        startElement("", name, attributes);
    }

    public void startElement(String namespaceURI, String name, String[] attributes) {
        startElement("", namespaceURI, name, attributes);
    }

    public void startElement(String prefix, String namespaceURI, String name, String[] attributes) {
        attributesImpl.clear();
        populateAttributes(attributesImpl, attributes);
        startElement(prefix, namespaceURI, name, attributesImpl);
    }

    public void endElement() {
        try {
            final ElementInfo elementInfo = elements.pop();
            xmlReceiver.endElement(elementInfo.uri, elementInfo.name, elementInfo.qName);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void element(String prefix, String namespaceURI, String name, Attributes attributes) {
        startElement(prefix, namespaceURI, name, attributes);
        endElement();
    }

    public void element(String namespaceURI, String name, String[] attributes) {
        startElement("", namespaceURI, name, attributes);
        endElement();
    }

    public void element(String name, String[] attributes) {
        startElement("", "", name, attributes);
        endElement();
    }

    public void element(String prefix, String namespaceURI, String name, String[] attributes) {
        startElement(prefix, namespaceURI, name, attributes);
        endElement();
    }

    public void element(String name, String text) {
        element("", name, text);
    }

    public void element(String namespaceURI, String name, String text) {
        element("", namespaceURI, name, text);
    }

    public void element(String prefix, String namespaceURI, String name, String text) {
        startElement(prefix, namespaceURI, name);
        text(text);
        endElement();
    }

    public void element(String name, long number) {
        element("", name, number);
    }

    public void element(String namespaceURI, String name, long number) {
        element("", namespaceURI, name, number);
    }

    public void element(String prefix, String namespaceURI, String name, long number) {
        attributesImpl.clear();
        startElement(prefix, namespaceURI, name);
        text(Long.toString(number));
        endElement();
    }

    public void element(String name, double number) {
        element("", name, number);
    }

    public void element(String namespaceURI, String name, double number) {
        element("", namespaceURI,  name, number);
    }

    public void element(String prefix, String namespaceURI, String name, double number) {
        attributesImpl.clear();
        startElement(prefix, namespaceURI, name);
        text(XMLUtils.removeScientificNotation(number));
        endElement();
    }

    public void text(String text)  {
        try {
            if (text != null)
                xmlReceiver.characters(text.toCharArray(), 0, text.length());
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void startDocument() {
        try {
            xmlReceiver.startDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void endDocument() {
        try {
            if (!elements.isEmpty()) {
                throw new OXFException("Element '" + elements.peek() + "' not closed");
            }
            xmlReceiver.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void startPrefixMapping (String prefix, String uri) {
        try {
            xmlReceiver.startPrefixMapping(prefix, uri);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void endPrefixMapping (String prefix) {
        try {
            xmlReceiver.endPrefixMapping(prefix);
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public static void populateAttributes(AttributesImpl attributesImpl, String[] attributes) {
        if (attributes != null) {
            for (int i = 0; i < attributes.length / 2; i++) {
                final String attributeName = attributes[i * 2];
                final String attributeValue = attributes[i * 2 + 1];
                if (attributeName != null && attributeValue != null)
                    attributesImpl.addAttribute("", attributeName, attributeName, CDATA, attributeValue);
            }
        }
    }
}
