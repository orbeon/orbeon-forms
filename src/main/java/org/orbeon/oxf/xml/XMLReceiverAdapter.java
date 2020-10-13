/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * Adapter for XMLReceiver. Methods don't do anything but can be overridden.
 */
public class XMLReceiverAdapter implements XMLReceiver {
    public void characters(char[] ch, int start, int length) throws SAXException {
    }

    public void endDocument() {
    }

    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
    }

    public void endPrefixMapping(String prefix) {
    }

    public void ignorableWhitespace(char[] ch, int start, int length) {
    }

    public void processingInstruction(String target, String data) {
    }

    public void setDocumentLocator(Locator locator) {
    }

    public void skippedEntity(String name) {
    }

    public void startDocument() {
    }

    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {
    }

    public void startPrefixMapping(String prefix, String uri) {
    }

    public void startDTD(String name, String publicId, String systemId) {
    }

    public void endDTD() {
    }

    public void startEntity(String name) {
    }

    public void endEntity(String name) {
    }

    public void startCDATA() {
    }

    public void endCDATA() {
    }

    public void comment(char[] ch, int start, int length) {
    }
}
