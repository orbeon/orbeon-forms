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
    
    private boolean forwardContent;
    private boolean forwardLexical;

    public ForwardingXMLReceiver() {
    }

    public ForwardingXMLReceiver(XMLReceiver xmlReceiver) {
        setXMLReceiver(xmlReceiver);
    }

    public ForwardingXMLReceiver(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
        this.contentHandler = contentHandler;
        this.lexicalHandler = lexicalHandler;

        setForward(true);
    }

    public ForwardingXMLReceiver(ContentHandler contentHandler) {
        setContentHandler(contentHandler);
    }

    public XMLReceiver getXMLReceiver() {
        return xmlReceiver;
    }

    public void setContentHandler(final ContentHandler contentHandler) {
        this.xmlReceiver = null;
        this.contentHandler = contentHandler;
        this.lexicalHandler = null;

        setForward(true);
    }

    public void setXMLReceiver(final XMLReceiver xmlReceiver) {
        this.xmlReceiver = xmlReceiver;
        this.contentHandler = xmlReceiver;
        this.lexicalHandler = xmlReceiver;

        setForward(true);
    }

    public void setForward(boolean forward) {
        this.forwardContent = forward && contentHandler != null;
        this.forwardLexical = forward && lexicalHandler != null;
    }

    // ContentHandler methods

    public void characters(char[] chars, int start, int length) throws SAXException {
        if (forwardContent)
            contentHandler.characters(chars, start, length);
    }

    public void endDocument() throws SAXException {
        if (forwardContent) {
            contentHandler.endDocument();
        }
        setXMLReceiver(null);
    }

    public void endElement(String uri, String localname, String qName) throws SAXException {
        if (forwardContent)
            contentHandler.endElement(uri, localname, qName);
    }

    public void endPrefixMapping(String s) throws SAXException {
        if (forwardContent)
            contentHandler.endPrefixMapping(s);
    }

    public void ignorableWhitespace(char[] chars, int start, int length) throws SAXException {
        if (forwardContent)
            contentHandler.ignorableWhitespace(chars, start, length);
    }

    public void processingInstruction(String s, String s1) throws SAXException {
        if (forwardContent)
            contentHandler.processingInstruction(s, s1);
    }

    public void setDocumentLocator(Locator locator) {
        if (forwardContent)
            contentHandler.setDocumentLocator(locator);
    }

    public void skippedEntity(String s) throws SAXException {
        if (forwardContent)
            contentHandler.skippedEntity(s);
    }

    public void startDocument() throws SAXException {
        if (forwardContent)
            contentHandler.startDocument();
    }

    public void startElement(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        if (forwardContent)
            contentHandler.startElement(uri, localname, qName, attributes);
    }

    public void startPrefixMapping(String s, String s1) throws SAXException {
        if (forwardContent)
            contentHandler.startPrefixMapping(s, s1);
    }

    // LexicalHandler methods

    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (forwardLexical)
            lexicalHandler.startDTD(name, publicId, systemId);
    }

    public void endDTD() throws SAXException {
        if (forwardLexical)
            lexicalHandler.endDTD();
    }

    public void startEntity(String name) throws SAXException {
        if (forwardLexical)
            lexicalHandler.startEntity(name);
    }

    public void endEntity(String name) throws SAXException {
        if (forwardLexical)
            lexicalHandler.endEntity(name);
    }

    public void startCDATA() throws SAXException {
        if (forwardLexical)
            lexicalHandler.startCDATA();
    }

    public void endCDATA() throws SAXException {
        if (forwardLexical)
            lexicalHandler.endCDATA();
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        if (forwardLexical)
            lexicalHandler.comment(ch, start, length);
    }
}