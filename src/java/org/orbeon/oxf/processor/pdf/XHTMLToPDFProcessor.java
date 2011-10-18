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
package org.orbeon.oxf.processor.pdf;

import com.lowagie.text.pdf.BaseFont;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ServletURLRewriter;
import org.orbeon.oxf.externalcontext.URLRewriter;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorInput;
import org.orbeon.oxf.processor.ProcessorInputOutputInfo;
import org.orbeon.oxf.processor.serializer.legacy.HttpBinarySerializer;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.*;
import org.w3c.dom.Document;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.xhtmlrenderer.pdf.ITextUserAgent;
import org.xhtmlrenderer.resource.ImageResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * XHTML to PDF converter using the Flying Saucer library.
 */
public class XHTMLToPDFProcessor extends HttpBinarySerializer {// TODO: HttpBinarySerializer is supposedly deprecated

    private static final Logger logger = LoggerFactory.createLogger(XHTMLToPDFProcessor.class);

    public static String DEFAULT_CONTENT_TYPE = "application/pdf";

    public XHTMLToPDFProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_DATA));
    }

    protected String getDefaultContentType() {
        return DEFAULT_CONTENT_TYPE;
    }

    protected void readInput(final PipelineContext pipelineContext, final ProcessorInput input, Config config, OutputStream outputStream) {

        final ExternalContext externalContext = NetUtils.getExternalContext();

        // Read the input as a DOM
        final Document domDocument = readInputAsDOM(pipelineContext, input);

        // Create renderer and add our own callback

        final float DEFAULT_DOTS_PER_POINT = 20f * 4f / 3f;
        final int DEFAULT_DOTS_PER_PIXEL = 14;

        // Use servlet URL rewriter for resources, even in portlet mode, so that resources are served over HTTP by
        // the servlet and not looping back through the portal.
        // NOTE: We could imagine loading resources through a local portlet submission. To do this, we need to properly
        // abstract the xforms:submission code.
        final URLRewriter servletRewriter = new ServletURLRewriter(externalContext.getRequest());

        final ITextRenderer renderer = new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL);

        // Embed fonts if needed, based on configuration properties
        embedFonts(renderer);

        try {
            final ITextUserAgent callback = new ITextUserAgent(renderer.getOutputDevice()) {
                // Called for:
                //
                // - CSS URLs
                // - image URLs
                // - link clicked / form submission (not relevant for our usage)
                // - resolveAndOpenStream below
                public String resolveURI(String uri) {
                    // Our own resolver

                    // When the browser retrieves resources, they are obviously resource URLs, including the use of the
                    // incoming host and port.
                    // In this case, things are a bit different, because when deploying with e.g. an Apache front-end,
                    // requests from this processor should not go through it. So we rewrite as a service URL instead. But in
                    // addition to that, we must still rewrite the path as a resource, so that versioned resources are
                    // handled properly.
                    final String path = servletRewriter.rewriteResourceURL(uri, ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT);
                    return externalContext.rewriteServiceURL(path, ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
                }

                // Called by:
                //
                // - getCSSResource
                // - getImageResource below
                // - getBinaryResource (not sure when called)
                // - getXMLResource (not sure when called)
                protected InputStream resolveAndOpenStream(String uri) {
                    try {
                        final String resolvedURI = resolveURI(uri);
                        // TODO: Use xforms:submission code instead

                        // Tell callee we are loading that we are a servlet environment, as in effect we act like
                        // a browser retrieving resources directly, not like a portlet. This is the case also if we are
                        // called by the proxy portlet or if we are directly within a portlet.
                        final Map<String, String[]> headers = new HashMap<String, String[]>();
                        headers.put("Orbeon-Container", new String[] { "servlet" });
                        
                        final ConnectionResult connectionResult
                            = new Connection().open(externalContext, new IndentedLogger(logger, ""), false, Connection.Method.GET.name(),
                                new URL(resolvedURI), null, null, null, null, null, headers, Connection.getForwardHeaders());

                        if (connectionResult.statusCode != 200) {
                            connectionResult.close();
                            throw new OXFException("Got invalid return code while loading resource: " + uri + ", " + connectionResult.statusCode);
                        }

                        pipelineContext.addContextListener(new PipelineContext.ContextListener() {
                            public void contextDestroyed(boolean success) {
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
                    final String localURI = NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE);
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
            final List pages = renderer.getRootBox().getLayer().getPages();
            try {
                // Page count might be zero, and if so createPDF
                if (pages != null && pages.size() > 0) {
                    renderer.createPDF(outputStream);
                } else {
                    // TODO: log?
                }
            } catch (Exception e) {
                throw new OXFException(e);
            } finally {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    // NOP
                    // TODO: log?
                }
            }
        } finally {
            // Free resources associated with the rendering context
            renderer.getSharedContext().reset();
        }
    }

    public static void embedFonts(ITextRenderer renderer) {
        final PropertySet propertySet = Properties.instance().getPropertySet();
        for (final String propertyName : propertySet.getPropertiesStartsWith("oxf.fr.pdf.font.path")) {
            final String path = StringUtils.trimToNull(propertySet.getString(propertyName));
            if (path != null) {
                try {
                    // Overriding the font family is optional
                    final String family; {
                        final String[] tokens = StringUtils.split(propertyName, '.');
                        if (tokens.length >= 6) {
                            final String id = tokens[5];
                            family = StringUtils.trimToNull(propertySet.getString("oxf.fr.pdf.font.family" + '.' + id));
                        } else {
                            family = null;
                        }
                    }

                    // Add the font
                    renderer.getFontResolver().addFont(path, family, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, null);
                } catch (Exception e) {
                    logger.warn("Failed to load font by path: '" + path + "' specified with property '"  + propertyName + "'");
                }
            }
        }
    }
}
