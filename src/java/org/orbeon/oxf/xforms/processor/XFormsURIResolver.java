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
package org.orbeon.oxf.xforms.processor;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.URIProcessorOutputImpl;
import org.orbeon.oxf.processor.transformer.TransformerURIResolver;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLReaderToReceiver;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.Configuration;
import org.orbeon.saxon.om.DocumentInfo;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXSource;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * URI resolver used during XForms initialization.
 *
 * This URI resolver is able to use a username and password for HTTP and HTTPS, and works in conjunction with
 * URIProcessorOutputImpl.
 */
public class XFormsURIResolver extends TransformerURIResolver {

    private static final String LOGGING_CATEGORY = "resolver";
    private static final Logger logger = LoggerFactory.createLogger(XFormsURIResolver.class);
    private static final IndentedLogger indentedLogger = XFormsContainingDocument.getIndentedLogger(logger, XFormsServer.getLogger(), LOGGING_CATEGORY);

    private URIProcessorOutputImpl processorOutput;

    public XFormsURIResolver(ProcessorImpl processor, URIProcessorOutputImpl processorOutput, PipelineContext pipelineContext,
                             String prohibitedInput, XMLUtils.ParserConfiguration parserConfiguration) {
        super(processor, pipelineContext, prohibitedInput, parserConfiguration);
        this.processorOutput = processorOutput;
    }

    @Override
    public Source resolve(String href, String base) throws TransformerException {
        // Use global definition for headers to forward
        return resolve(href, base, null, null, null, Connection.getForwardHeaders());
    }

    public Source resolve(String href, String base, final String username, final String password,
    		final String domain, final String headersToForward) throws TransformerException {

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
                processorOutput.readURLToStateIfNeeded(getPipelineContext(), url, state, username, password, domain, headersToForward);

                // Then try to read from state
                if (state.isDocumentSet(urlString, username, password)) {// not sure why this would not be the case
                    // This means the document requested is already available. We use the cached document.
                    final XMLReader xmlReader = new XMLReaderToReceiver() {
                        public void parse(String systemId) throws SAXException {
                            state.getDocument(urlString, username, password).replay(createXMLReceiver());
                        }
                    };

                    if (indentedLogger.isDebugEnabled())
                        indentedLogger.logDebug("", "resolving resource through initialization resolver", "uri", urlString);

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

    public Document readAsDom4j(String urlString, String username, String password, String domain, String headersToForward) {
        try {
            final SAXSource source = (SAXSource) resolve(urlString, null, username, password, domain, headersToForward);
            // XInclude handled by source if needed
            return TransformerUtils.readDom4j(source, false);
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(urlString, -1, -1));
        }
    }

    public DocumentInfo readAsTinyTree(Configuration configuration, String urlString, String username, String password,
    		String domain, String headersToForward) {
        try {
            final SAXSource source = (SAXSource) resolve(urlString, null, username, password, domain, headersToForward);
            // XInclude handled by source if needed
            return TransformerUtils.readTinyTree(configuration, source, false);
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new LocationData(urlString, -1, -1));
        }
    }
}