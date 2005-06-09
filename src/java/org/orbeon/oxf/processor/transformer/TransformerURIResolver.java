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
package org.orbeon.oxf.processor.transformer;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.Processor;
import org.orbeon.oxf.processor.ProcessorImpl;
import org.orbeon.oxf.processor.generator.URLGenerator;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.xml.ForwardingXMLReader;
import org.orbeon.oxf.xml.ProcessorOutputXMLReader;
import org.orbeon.oxf.xml.TeeContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.URIResolver;
import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TransformerURIResolver implements URIResolver {

    private Processor processor;
    private PipelineContext pipelineContext;

    public TransformerURIResolver(Processor processor, PipelineContext pipelineContext) {
        this.processor = processor;
        this.pipelineContext = pipelineContext;
    }

    public Source resolve(String href, String base) throws TransformerException {
        try {
            // Create XML reader for URI
            XMLReader xmlReader;
            {
                String inputName = ProcessorImpl.getProcessorInputSchemeInputName(href);
                if (inputName != null) {
                    // Resolve to input of current processor
                    xmlReader = new ProcessorOutputXMLReader
                            (pipelineContext, processor.getInputByName(inputName).getOutput());
                } else {
                    // Resolve to regular URI
                    Processor urlGenerator = new URLGenerator(URLFactory.createURL(base, href));
                    xmlReader = new ProcessorOutputXMLReader(pipelineContext, urlGenerator.createOutput(ProcessorImpl.OUTPUT_DATA));
                }
            }

            // Also send data to listener, if there is one
            final URIResolverListener uriResolverListener = (URIResolverListener)
                    pipelineContext.getAttribute(PipelineContext.XSLT_STYLESHEET_URI_LISTENER);
            if (uriResolverListener != null) {
                xmlReader = new ForwardingXMLReader(xmlReader) {

                    private ContentHandler originalHandler;

                    public void setContentHandler(ContentHandler handler) {
                        originalHandler = handler;
                        List contentHandlers = Arrays.asList(new Object[]{uriResolverListener.getContentHandler(), handler});
                        super.setContentHandler(new TeeContentHandler(contentHandlers));
                    }

                    public ContentHandler getContentHandler() {
                        return originalHandler;
                    }
                };
            }

            // Create SAX Source based on XML Reader
            SAXSource saxSource = new SAXSource(xmlReader, new InputSource());
            saxSource.setSystemId(href);
            return saxSource;

        } catch (IOException e) {
            throw new OXFException(e);
        }
    }
}
