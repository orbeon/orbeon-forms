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

public class StringValueContentHandler implements ContentHandler {
    private StringBuffer value;

    public String getStringValue() {
        return (value == null) ? null : value.toString();
    }

    public void characters(char[] chars, int i, int i1) throws SAXException {
        if (value == null)
            value = new StringBuffer();
        value.append(chars, i, i1);
    }

    public void endDocument() throws SAXException {
    }

    public void endElement(String s, String s1, String s2) throws SAXException {
    }

    public void endPrefixMapping(String s) throws SAXException {
    }

    public void ignorableWhitespace(char[] chars, int i, int i1) throws SAXException {
    }

    public void processingInstruction(String s, String s1) throws SAXException {
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void skippedEntity(String s) throws SAXException {
    }

    public void startDocument() throws SAXException {
        value = null;
    }

    public void startElement(String s, String s1, String s2, Attributes attributes) throws SAXException {
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
    }
}
