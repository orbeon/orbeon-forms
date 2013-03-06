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

import org.xml.sax.*;

import java.io.IOException;

public class ForwardingXMLReader implements XMLReader {

    XMLReader xmlReader;

    public ForwardingXMLReader(XMLReader xmlReader) {
        this.xmlReader = xmlReader;
    }

    public boolean getFeature(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        return xmlReader.getFeature(name);
    }

    public void setFeature(String name, boolean value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        xmlReader.setFeature(name, value);
    }

    public Object getProperty(String name)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        return xmlReader.getProperty(name);
    }

    public void setProperty(String name, Object value)
            throws SAXNotRecognizedException, SAXNotSupportedException {
        xmlReader.setProperty(name, value);
    }

    public void setEntityResolver(EntityResolver resolver) {
        xmlReader.setEntityResolver(resolver);
    }

    public EntityResolver getEntityResolver() {
        return xmlReader.getEntityResolver();
    }

    public void setDTDHandler(DTDHandler handler) {
        xmlReader.setDTDHandler(handler);
    }

    public DTDHandler getDTDHandler() {
        return xmlReader.getDTDHandler();
    }

    public void setContentHandler(ContentHandler handler) {
        xmlReader.setContentHandler(handler);
    }

    public ContentHandler getContentHandler() {
        return xmlReader.getContentHandler();
    }

    public void setErrorHandler(ErrorHandler handler) {
        xmlReader.setErrorHandler(handler);
    }

    public ErrorHandler getErrorHandler() {
        return xmlReader.getErrorHandler();
    }

    public void parse(InputSource input)
            throws IOException, SAXException {
        xmlReader.parse(input);
    }

    public void parse(String systemId)
            throws IOException, SAXException {
        xmlReader.parse(systemId);
    }
}
