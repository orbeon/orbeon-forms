/**
 *  Copyright (C) 2005 Orbeon, Inc.
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

import org.orbeon.oxf.common.OXFException;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * ContentHandler with an additional method allowing for adding attributes.
 */
public class DeferredContentHandlerImpl extends ForwardingContentHandler implements DeferredContentHandler  {

    private boolean storedElement;
    private String uri;
    private String localname;
    private String qName;
    private AttributesImpl attributes;

    public DeferredContentHandlerImpl(ContentHandler contentHandler) {
        super(contentHandler);
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        flush();

        storedElement = true;
        this.uri = uri;
        this.localname = localname;
        this.qName = qName;
        this.attributes = new AttributesImpl(attributes);
    }

    public void addAttribute(String uri, String localname, String qName, String value) {
        if (!storedElement)
            throw new OXFException("addAttribute called within no element.");

        attributes.addAttribute(uri, localname, qName, "CDATA", value);
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        flush();
        super.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {
        super.endDocument();
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        flush();
        super.endElement(uri, localname, qName);
    }

    private void flush() throws SAXException {
        if (storedElement) {
            super.startElement(uri, localname, qName, attributes);
            storedElement = false;
        }
    }
}
