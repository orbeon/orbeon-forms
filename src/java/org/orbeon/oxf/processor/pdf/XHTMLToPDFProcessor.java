/**
 *  Copyright (C) 2008 Orbeon, Inc.
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
package org.orbeon.oxf.processor.pdf;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.ConnectionResult;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.ImageResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * XHTML to PDF converter using the Flying Saucer library.
 */
public class XHTMLToPDFProcessor extends HttpBinarySerializer {// TODO: HttpBinarySerializer is supposedly deprecated

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";

    public XHTMLToPDFProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(final PipelineContext pipelineContext, final ProcessorInput input, Config config, OutputStream outputStream) {

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

        // Read the input as a DOM
        final Document domDocument = readInputAsDOM(pipelineContext, input);

        // Create renderer and add our own callback

        final float DEFAULT_DOTS_PER_POINT = 20f * 4f / 3f;
        final int DEFAULT_DOTS_PER_PIXEL = 14;

        final ITextRenderer renderer = new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL);
        final ITextUserAgent callback = new ITextUserAgent(renderer.getOutputDevice()) {
            public String resolveURI(String s) {
                // Our own resolver
                return externalContext.getResponse().rewriteResourceURL(s, true);
            }

            protected InputStream resolveAndOpenStream(String uri) {
                try {
                    final String resolvedURI = resolveURI(uri);
                    final ConnectionResult connectionResult
                            = NetUtils.openConnection(externalContext, indentedLogger, "GET", new URL(resolvedURI),
                                null, null, null, null, null, null);

                    if (connectionResult.statusCode != 200) {
                        connectionResult.close();
                        throw new OXFException("Got invalid return code while loading resource: " + uri + ", " + connectionResult.statusCode);
                    }

                    pipelineContext.addContextListener(new PipelineContext.ContextListener() {
                        public void contextDestroyed(boolean success) {
                            if (connectionResult != null)
                                connectionResult.close();
                        }
                    });

                    return connectionResult.getResponseInputStream();

                } catch (IOException e) {
                    throw new OXFException(e);
                }
            }

            public ImageResource getImageResource(String uri) {
                final InputStream is = resolveAndOpenStream(uri);
                final String localURI = NetUtils.inputStreamToAnyURI(pipelineContext, is, NetUtils.REQUEST_SCOPE);
                return super.getImageResource(localURI);
            }
        };
        callback.setSharedContext(renderer.getSharedContext());
        renderer.getSharedContext().setUserAgentCallback(callback);
//        renderer.getSharedContext().setDPI(150);

        // Set the document to process
        renderer.setDocument(domDocument,
            // No base URL if can't get request URL from context
            externalContext.getRequest() == null ? null : externalContext.getRequest().getRequestURL());

        // Do the layout and create the resulting PDF
        renderer.layout();
        try {
            renderer.createPDF(outputStream);
            outputStream.close();
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }
}
