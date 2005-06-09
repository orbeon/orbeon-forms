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

import org.dom4j.Document;
import org.orbeon.oxf.transformer.xupdate.statement.Utils;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;
import org.orbeon.oxf.xml.dom4j.LocationSAXWriter;
import org.xml.sax.SAXException;

import javax.xml.transform.*;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.util.Properties;

public class TransformerImpl extends Transformer {

    private TemplatesImpl templates;
    private URIResolver uriResolver;

    public TransformerImpl(TemplatesImpl templates) {
        this.templates = templates;
    }

    public void setURIResolver(URIResolver uriResolver) {
        this.uriResolver = uriResolver;
    }

    public URIResolver getURIResolver() {
        return null;
    }

    public void setOutputProperties(Properties oformat)
            throws IllegalArgumentException {
    }

    public Properties getOutputProperties() {
        return null;
    }

    public void setOutputProperty(String name, String value)
            throws IllegalArgumentException {
    }

    public String getOutputProperty(String name)
            throws IllegalArgumentException {
        return null;
    }

    public void setErrorListener(ErrorListener listener)
            throws IllegalArgumentException {
    }

    public ErrorListener getErrorListener() {
        return null;
    }

    public void transform(Source xmlSource, Result outputTarget) throws TransformerException {

        try {
            // Check input parameters
            if (! (xmlSource instanceof SAXSource))
                throw new TransformerException("XUpdate transformer only supports SAXSource");
            if (! (outputTarget instanceof SAXResult))
                throw new TransformerException("XUpdate transformer only supports SAXResult");
            SAXSource saxSource = (SAXSource) xmlSource;
            SAXResult saxResult = (SAXResult) outputTarget;

            // Read input document
            LocationSAXContentHandler documentContentHandler = new LocationSAXContentHandler();
            saxSource.getXMLReader().setContentHandler(documentContentHandler);
            saxSource.getXMLReader().parse(saxSource.getInputSource());
            Document document = documentContentHandler.getDocument();

            // Execute operations
            Utils.execute(uriResolver, document, new VariableContextImpl(), new DocumentContext(), templates.getStatements());

            // Send document to output
            LocationSAXWriter locationSAXWriter = new LocationSAXWriter();
            locationSAXWriter.setContentHandler(saxResult.getHandler());
            locationSAXWriter.write(document);

        } catch (IOException e) {
            throw new TransformerException(e);
        } catch (SAXException e) {
            throw new TransformerException(e);
        }
    }

    public void setParameter(String name, Object value) {
    }

    public Object getParameter(String name) {
        return null;
    }

    public void clearParameters() {
    }
}
