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
import org.xml.sax.*;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.DefaultHandler;

public abstract class XMLReaderToReceiver implements XMLReader {
    
    private static final DefaultHandler IMMUTABLE_DEFAULT_HANDLER = new DefaultHandler();

    private ContentHandler contentHandler = IMMUTABLE_DEFAULT_HANDLER;
    private LexicalHandler lexicalHandler;

    private DTDHandler dtdHandler = IMMUTABLE_DEFAULT_HANDLER;
    private EntityResolver entityResolver = IMMUTABLE_DEFAULT_HANDLER;
    private ErrorHandler errorHandler = IMMUTABLE_DEFAULT_HANDLER;

    public XMLReaderToReceiver() {
    }

    public ContentHandler getContentHandler() { return contentHandler; }
    public DTDHandler getDTDHandler() { return dtdHandler; }
    public EntityResolver getEntityResolver() { return entityResolver; }
    public ErrorHandler getErrorHandler() { return errorHandler; }

    /**
     * Create an XMLReceiver which handles a ContentHandler and an optional LexicalHandler.
     *
     * @return  receiver
     */
    protected XMLReceiver createXMLReceiver() {
        if (lexicalHandler != null) {
            // We have a lexical handler
            return new ForwardingXMLReceiver(contentHandler, lexicalHandler);
        } else {
            // We don't have a lexical handler, just use ContentHandler
            return new ForwardingXMLReceiver(contentHandler);
        }
    }

    /**
     * Subclass must implement this and call createXMLReceiver().
     *
     * @param systemId  id of document
     */
    public abstract void parse(String systemId) throws SAXException;

    public void parse(InputSource source) throws SAXException {
        parse(source.getSystemId());
    }

    public void setContentHandler(ContentHandler handler) {
        this.contentHandler = handler;
    }

    public void setDTDHandler(DTDHandler handler) {
        this.dtdHandler = handler;
    }

    public void setEntityResolver(EntityResolver resolver) {
        this.entityResolver = resolver;
    }

    public void setErrorHandler(ErrorHandler handler) {
        this.errorHandler = handler;
    }

    public void setFeature(String name, boolean state) throws SAXNotRecognizedException, SAXNotSupportedException {
        // We allow setting these features but assume they are the default
        if (name.equals("http://xml.org/sax/features/namespaces") && state)
            return;
        else if (name.equals("http://xml.org/sax/features/namespace-prefixes") && !state)
            return;
        else if (name.equals("http://xml.org/sax/features/validation"))
            return;

        // Otherwise throw
        throw new SAXNotSupportedException(name);
    }

    public void setProperty(String name, Object value) throws SAXNotSupportedException {

        // We allow setting these properties
        if (name.equals(XMLConstants.SAX_LEXICAL_HANDLER) && value instanceof LexicalHandler) {
            this.lexicalHandler = (LexicalHandler) value;
            return;
        }

        throw new SAXNotSupportedException(name);
    }

    public boolean getFeature(String name) {
        if (name.equals("http://xml.org/sax/features/namespaces"))
            return true;
        else if (name.equals("http://xml.org/sax/features/namespace-prefixes"))
            return false;
        else if (name.equals("http://xml.org/sax/features/validation"))
            return true;

        return false;
    }

    public Object getProperty(String name) {

        if (name.equals(XMLConstants.SAX_LEXICAL_HANDLER)) {
            return lexicalHandler;
        }

        return null;
    }
}
