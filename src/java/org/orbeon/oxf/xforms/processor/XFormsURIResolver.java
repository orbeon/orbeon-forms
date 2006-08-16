/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.processor;

import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.URIProcessorOutputImpl;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.tree.TreeBuilder;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.TransformerHandler;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * URI resolver used during XForms initialization.
 *
 * This URI resolver is able to use a username and password for HTTP and HTTPS, and works in conjunction with
 * URIProcessorOutputImpl.
 */
public class XFormsURIResolver extends TransformerURIResolver {

    private URIProcessorOutputImpl processorOutput;

    public XFormsURIResolver(ProcessorImpl processor, URIProcessorOutputImpl processorOutput, PipelineContext pipelineContext, String prohibitedInput, boolean handleXInclude) {
        super(processor, pipelineContext, prohibitedInput, handleXInclude);
        this.processorOutput = processorOutput;
    }

    public Source resolve(String href, String base) throws TransformerException {
        return resolve(href, base, null, null);
    }

    public Source resolve(String href, String base, final String username, final String password) throws TransformerException {

        final String inputName = ProcessorImpl.getProcessorInputSchemeInputName(href);
        if (inputName != null) {
            // Use parent resolver if accessing a processor input
            return super.resolve(href, base);
        } else {
            // This is a regular URL
            final URL url;
            try {
                url = URLFactory.createURL(base, href);
            } catch (MalformedURLException e) {
                throw new OXFException(e);
            }

            final String protocol = url.getProtocol();
            final boolean isHttpProtocol = protocol.equals("http") || protocol.equals("https");

            if (isHttpProtocol) {
                // Override the behavior to read into the state
                final String urlString = url.toExternalForm();
                final URIProcessorOutputImpl.URIReferencesState state = (URIProcessorOutputImpl.URIReferencesState) getProcessor().getState(getPipelineContext());

                // First, put in state if necessary
                processorOutput.readURLToStateIfNeeded(getPipelineContext(), url, state, username, password);

                // Then try to read from state
                if (state.isDocumentSet(urlString, username, password)) {// not sure why this would not be the case
                    // This means the document requested is already available. We use the cached document.
                    final XMLReader xmlReader = new XMLFilterImpl() {
                        public void parse(String systemId) throws SAXException {
                            state.getDocument(urlString, username, password).replay(getContentHandler());
                        }

                        public void setFeature(String name, boolean state) {
                            // Not sure if this is necessary
                        }
                    };

                    if (XFormsToXHTML.logger.isDebugEnabled())
                        XFormsToXHTML.logger.debug("XForms - resolving resource through initialization resolver for URI: " + urlString);

                    return new SAXSource(xmlReader, new InputSource(urlString));
                } else {
                    throw new OXFException("Cannot find document in state for URI: " + urlString);
                }
            } else {
                // Use parent resolver for other protocols
                return super.resolve(href, base);
            }
        }
    }

    public Document readURLAsDocument(String urlString, String username, String password) {
        try {
            final SAXSource source = (SAXSource) resolve(urlString, null, username, password);

            final LocationDocumentResult documentResult = new LocationDocumentResult();
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.setResult(documentResult);

            final XMLReader xmlReader = source.getXMLReader();
            xmlReader.setContentHandler(identity);
            xmlReader.parse(urlString);

            return documentResult.getDocument();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public DocumentInfo readURLAsDocumentInfo(String urlString, String username, String password) {
        try {
            final SAXSource source = (SAXSource) resolve(urlString, null, username, password);

            final TreeBuilder treeBuilder = new TreeBuilder();
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.setResult(treeBuilder);

            final XMLReader xmlReader = source.getXMLReader();
            xmlReader.setContentHandler(identity);
            xmlReader.parse(urlString);

            return (DocumentInfo) treeBuilder.getCurrentRoot();

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public void readURLAsSAX(String urlString, String username, String password, ContentHandler contentHandler) {
        try {
            final SAXSource source = (SAXSource) resolve(urlString, null, username, password);

            final SAXResult saxResult = new SAXResult(contentHandler);
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
            identity.setResult(saxResult);

            final XMLReader xmlReader = source.getXMLReader();
            xmlReader.setContentHandler(identity);
            xmlReader.parse(urlString);

        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}