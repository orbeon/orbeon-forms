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
package org.orbeon.oxf.transformer.xupdate;

import org.xml.sax.XMLFilter;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;

public class TransformerFactoryImpl extends SAXTransformerFactory {
    public TransformerHandler newTransformerHandler(Source src)
            throws TransformerConfigurationException {
        return null;
    }

    public TransformerHandler newTransformerHandler(
            Templates templates) throws TransformerConfigurationException {
        return null;
    }

    public TransformerHandler newTransformerHandler()
            throws TransformerConfigurationException {
        return null;
    }

    public javax.xml.transform.sax.TemplatesHandler newTemplatesHandler() {
        return new TemplatesHandlerImpl();
    }

    public XMLFilter newXMLFilter(Source src)
            throws TransformerConfigurationException {
        return null;
    }

    public XMLFilter newXMLFilter(Templates templates)
            throws TransformerConfigurationException {
        return null;
    }

    public Transformer newTransformer(Source source)
            throws TransformerConfigurationException {
        return null;
    }

    public Transformer newTransformer()
            throws TransformerConfigurationException {
        return null;
    }

    public Templates newTemplates(Source source)
            throws TransformerConfigurationException {
        return null;
    }

    public Source getAssociatedStylesheet(
            Source source, String media, String title, String charset)
            throws TransformerConfigurationException {
        return null;
    }

    public void setURIResolver(URIResolver resolver) {
    }

    public URIResolver getURIResolver() {
        return null;
    }

    public boolean getFeature(String name) {
        return false;
    }

    public void setAttribute(String name, Object value)
            throws IllegalArgumentException {
    }

    public Object getAttribute(String name)
            throws IllegalArgumentException {
        return null;
    }

    public void setErrorListener(ErrorListener listener)
            throws IllegalArgumentException {
    }

    public ErrorListener getErrorListener() {
        return null;
    }
    public void setFeature( final String s, final boolean b ) {
    }
}
