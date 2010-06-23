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
import org.xml.sax.ext.LexicalHandler;

public class ForwardingXMLReceiver implements XMLReceiver {

    private transient XMLReceiver xmlReceiver;
    private transient ContentHandler contentHandler;
    private transient LexicalHandler lexicalHandler;

    private boolean forward;

    public ForwardingXMLReceiver() {
    }

    public ForwardingXMLReceiver(XMLReceiver xmlReceiver) {
        this((ContentHandler) xmlReceiver);
        this.xmlReceiver = xmlReceiver;
        this.lexicalHandler = xmlReceiver;
    }

    public ForwardingXMLReceiver(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
        this(contentHandler);
        this.lexicalHandler = lexicalHandler;
    }

    public ForwardingXMLReceiver(ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
        this.forward = this.contentHandler != null;
    }

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (forward)
            contentHandler.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {
        if (forward) {
            contentHandler.endDocument();
            contentHandler = null;
            forward = false;
        }
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        if (forward)
            contentHandler.endElement(uri, localname, qName);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (forward)
            contentHandler.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        if (forward)
            contentHandler.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        if (forward)
            contentHandler.processingInstruction(s, s1);
    }

    public void setDocumentLocator(Locator locator) {
        if (forward)
            contentHandler.setDocumentLocator(locator);
    }

    public void skippedEntity(String s) throws SAXException {
        if (forward)
            contentHandler.skippedEntity(s);
    }

    public void startDocument() throws SAXException {
        if (forward)
            contentHandler.startDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (forward)
            contentHandler.startElement(uri, localname, qName, attributes);
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        if (forward)
            contentHandler.startPrefixMapping(s, s1);
    }

    public XMLReceiver getXMLReceiver() {
        return xmlReceiver;
    }

    public void setContentHandler(final ContentHandler contentHandler) {
        this.contentHandler = contentHandler;
        if (this.contentHandler == null)
            forward = false;
    }

    public void setXMLReceiver(final XMLReceiver xmlReceiver) {
        this.xmlReceiver = xmlReceiver;
        this.contentHandler = xmlReceiver;
        this.lexicalHandler = xmlReceiver;
        if (this.contentHandler == null)
            forward = false;
    }

    protected boolean getForward() {
        return forward;
    }

    public void setForward(boolean forward) {
        this.forward = forward;
    }

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.startDTD(name, publicId, systemId);
    }

    public void endDTD() throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.endDTD();
    }

    public void startEntity(String name) throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.startEntity(name);
    }

    public void endEntity(String name) throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.endEntity(name);
    }

    public void startCDATA() throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.startCDATA();
    }

    public void endCDATA() throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.endCDATA();
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        if (forward && lexicalHandler != null)
            lexicalHandler.comment(ch, start, length);
    }
}