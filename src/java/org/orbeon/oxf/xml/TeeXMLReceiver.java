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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import java.util.List;

public class TeeXMLReceiver implements XMLReceiver {

    // NOTE: Use an array, as List and Iterator are less efficient (profiling)
    private XMLReceiver[] xmlReceivers;

    public TeeXMLReceiver(final List<XMLReceiver> receivers) {
        xmlReceivers = new XMLReceiver[receivers.size()];
        receivers.toArray(xmlReceivers);
    }

    public TeeXMLReceiver(XMLReceiver xmlReceiver1, XMLReceiver xmlReceiver2) {
        xmlReceivers = new XMLReceiver[2];
        xmlReceivers[0] = xmlReceiver1;
        xmlReceivers[1] = xmlReceiver2;
    }

    public TeeXMLReceiver(XMLReceiver xmlReceiver1, XMLReceiver xmlReceiver2, XMLReceiver xmlReceiver3) {
        xmlReceivers = new XMLReceiver[3];
        xmlReceivers[0] = xmlReceiver1;
        xmlReceivers[1] = xmlReceiver2;
        xmlReceivers[2] = xmlReceiver3;
    }

    public void setDocumentLocator(Locator locator) {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.setDocumentLocator(locator);
        }
    }

    public void startDocument() throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.startDocument();
        }
    }

    public void endDocument() throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.endDocument();
        }
    }

    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.startPrefixMapping(prefix, uri);
        }
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.endPrefixMapping(prefix);
        }
    }

    public void startElement(String namespaceURI, String localName,
                             String qName, Attributes atts) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.startElement(namespaceURI, localName, qName, atts);
        }
    }

    public void endElement(String namespaceURI, String localName,
                           String qName) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.endElement(namespaceURI, localName, qName);
        }
    }

    public void characters(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.characters(ch, start, length);
        }
    }

    public void ignorableWhitespace(char ch[], int start, int length) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.ignorableWhitespace(ch, start, length);
        }
    }

    public void processingInstruction(String target, String data) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.processingInstruction(target, data);
        }
    }

    public void skippedEntity(String name) throws SAXException {
        for (int i = 0; i < xmlReceivers.length; i++) {
            ContentHandler contentHandler = xmlReceivers[i];
            contentHandler.skippedEntity(name);
        }
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.startDTD(name, publicId, systemId);
    }

    public void endDTD() throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.endDTD();
    }

    public void startEntity(String name) throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.startEntity(name);
    }

    public void endEntity(String name) throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.endEntity(name);
    }

    public void startCDATA() throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.startCDATA();
    }

    public void endCDATA() throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.endCDATA();
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        for (final XMLReceiver xmlReceiver : xmlReceivers)
            xmlReceiver.comment(ch, start, length);
    }
}
