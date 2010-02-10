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
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.Stack;

/**
 * Wrapper to a SAX ContentHandler. Provides more high-level methods to send events to a ContentHandler.
 */
public class ContentHandlerHelper {

    public static final String CDATA = "CDATA";
    private Stack<String> elementNamespaces = new Stack<String>();
    private Stack<String> elements = new Stack<String>();
    private ContentHandler contentHandler;
    private AttributesImpl attributesImpl = new AttributesImpl();

    public ContentHandlerHelper(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    /**
     * ContentHandler to write to. 
     *
     * @param contentHandler    ContentHandler to write to
     * @param validateStream    true if the stream must be validated by InspectingContentHandler
     */
    public ContentHandlerHelper(ContentHandler contentHandler, boolean validateStream) {
        if (validateStream)
            this.contentHandler = new InspectingContentHandler(contentHandler);
        else
            this.contentHandler = contentHandler;
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
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
            String qname = prefix == null || "".equals(prefix) ? name : prefix + ":" + name;
            contentHandler.startElement(namespaceURI, name, qname, attributes);
            elementNamespaces.add(namespaceURI);
            elements.add(name);
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
            String name = elements.pop();
            String namespace = elementNamespaces.pop();
            contentHandler.endElement(namespace, name, name);
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
                contentHandler.characters(text.toCharArray(), 0, text.length());
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void startDocument() {
        try {
            contentHandler.startDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public void endDocument() {
        try {
            if (!elements.isEmpty()) {
                throw new OXFException("Element '" + elements.peek() + "' not closed");
            }
            contentHandler.endDocument();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    public static void populateAttributes(AttributesImpl attributesImpl, String[] attributes) {
        for (int i = 0; i < attributes.length / 2; i++) {
            final String attributeName = attributes[i * 2];
            final String attributeValue = attributes[i * 2 + 1];
            if (attributeName != null && attributeValue != null)
                attributesImpl.addAttribute("", attributeName, attributeName, CDATA, attributeValue);
        }
    }
}
