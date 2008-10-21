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
package org.orbeon.oxf.xml;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Simple ContentHandler able to forward to another ContentHandler.
 */
public class SimpleForwardingContentHandler implements ContentHandler {
    private ContentHandler contentHandler;

    public SimpleForwardingContentHandler(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        contentHandler.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {
        contentHandler.endDocument();
        contentHandler = null;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        contentHandler.endElement(uri, localname, qName);
    }

    public void endPrefixMapping(String s) throws SAXException {
        contentHandler.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        contentHandler.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        contentHandler.processingInstruction(s, s1);
    }

    public void setDocumentLocator(Locator locator) {
        contentHandler.setDocumentLocator(locator);
    }

    public void skippedEntity(String s) throws SAXException {
        contentHandler.skippedEntity(s);
    }

    public void startDocument() throws SAXException {
        contentHandler.startDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        contentHandler.startElement(uri, localname, qName, attributes);
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        contentHandler.startPrefixMapping(s, s1);
    }

    public ContentHandler getContentHandler() {
        return contentHandler;
    }

    public void setContentHandler(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
    }
}
