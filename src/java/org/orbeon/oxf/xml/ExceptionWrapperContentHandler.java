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
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * ContentHandler which wraps exceptions with location information if possible.
 */
public class ExceptionWrapperContentHandler extends SimpleForwardingContentHandler {

    private Locator locator;
    private String message;

    public ExceptionWrapperContentHandler(ContentHandler contentHandler, String message) {
        super(contentHandler);
        this.message = message;
    }

    public void setDocumentLocator(Locator locator) {
        super.setDocumentLocator(locator);
        this.locator = locator;
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        try {
            super.startElement(uri, localname, qName, attributes);
        } catch (RuntimeException e) {
            wrapException(e, uri, qName, attributes);
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        try {
            super.endElement(uri, localname, qName);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        try {
            super.characters(chars, start, length);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        try {
            super.startPrefixMapping(s, s1);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void endPrefixMapping(String s) throws SAXException {
        try {
            super.endPrefixMapping(s);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        try {
            super.ignorableWhitespace(chars, start, length);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void skippedEntity(String s) throws SAXException {
        try {
            super.skippedEntity(s);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        try {
            super.processingInstruction(s, s1);
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void endDocument() throws SAXException {
        try {
            super.endDocument();
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    public void startDocument() throws SAXException {
        try {
            super.startDocument();
        } catch (RuntimeException e) {
            wrapException(e);
        }
    }

    private void wrapException(Exception e) throws SAXException {
        if (locator != null)
            throw ValidationException.wrapException(e, new ExtendedLocationData(locator, message));
        else if (e instanceof SAXException)
            throw (SAXException) e;
        else if (e instanceof RuntimeException)
            throw (RuntimeException) e;
        else
            throw new OXFException(e);// this should not happen
    }

    private void wrapException(Exception e, String uri, String qName, Attributes attributes) throws SAXException {
        if (locator != null)
            throw ValidationException.wrapException(e, new ExtendedLocationData(new LocationData(locator), message, "element", XMLUtils.saxElementToDebugString(uri, qName, attributes)));
        else if (e instanceof SAXException)
            throw (SAXException) e;
        else if (e instanceof RuntimeException)
            throw (RuntimeException) e;
        else
            throw new OXFException(e);// this should not happen
    }
}
