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

public class TeeContentHandler implements ContentHandler {

    // NOTE: Use an array, as List and Iterator are less efficient (profiling)
    private ContentHandler[] contentHandlers;

    public TeeContentHandler( final java.util.List hndlrs ) {
        contentHandlers = new ContentHandler[ hndlrs.size() ];
        hndlrs.toArray( contentHandlers );
    }

    public TeeContentHandler( final ContentHandler[] hndlrs  ) {
        contentHandlers = ( ContentHandler[] )hndlrs.clone();
    }

    public TeeContentHandler(ContentHandler contentHandler1, ContentHandler contentHandler2) {
        contentHandlers = new ContentHandler[2];
        contentHandlers[0] = contentHandler1;
        contentHandlers[1] = contentHandler2;
    }

    public void setDocumentLocator(Locator locator) {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.setDocumentLocator(locator);
        }
    }

    public void startDocument() throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.startDocument();
        }
    }

    public void endDocument() throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.endDocument();
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.startPrefixMapping(prefix, uri);
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.endPrefixMapping(prefix);
        }
    }

    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.startElement(namespaceURI, localName, qName, atts);
        }
    }

    public void endElement(String namespaceURI, String localName,
                           String qName) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.endElement(namespaceURI, localName, qName);
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.characters(ch, start,  length);
        }
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.ignorableWhitespace(ch, start, length);
        }
    }

    public void processingInstruction(String target, String data) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.processingInstruction(target, data);
        }
    }

    public void skippedEntity(String name) throws SAXException {
        for (int i = 0; i < contentHandlers.length; i++) {
            ContentHandler contentHandler = contentHandlers[i];
            contentHandler.skippedEntity(name);
        }
    }
}
