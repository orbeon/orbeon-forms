/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
import org.xml.sax.SAXException;

/**
 * Allows filtering sub-trees by element.
 */
public abstract class ElementFilterContentHandler extends SimpleForwardingContentHandler {

    private int level = 0;
    private int filterLevel = -1;

    protected abstract boolean isFilterElement(String uri, String localname, String qName, Attributes attributes);

    public ElementFilterContentHandler(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {

        if (filterLevel == -1) {
            if (isFilterElement(uri, localname, qName, attributes)) {
                filterLevel = level;
            } else {
                super.startElement(uri, localname, qName, attributes);
            }
        }

        level++;
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        level--;

        if (filterLevel == level) {
            filterLevel = -1;
        } else if (filterLevel == -1) {
            super.endElement(uri, localname, qName);
        }
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        if (filterLevel == -1)
            super.startPrefixMapping(s, s1);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (filterLevel == -1)
            super.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        if (filterLevel == -1)
            super.ignorableWhitespace(chars, start, length);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (filterLevel == -1)
            super.characters(chars, start, length);
    }

    public void skippedEntity(String s) throws SAXException {
        if (filterLevel == -1)
            super.skippedEntity(s);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        if (filterLevel == -1)
            super.processingInstruction(s, s1);
    }
}
