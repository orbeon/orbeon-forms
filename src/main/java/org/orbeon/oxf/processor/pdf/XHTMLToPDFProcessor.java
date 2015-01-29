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
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Headers;
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
import java.net.URI;
import java.net.URISyntaxException;
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

        final ITextRenderer renderer = new ITextRenderer(DEFAULT_DOTS_PER_POINT, DEFAULT_DOTS_PER_PIXEL);

        // Embed fonts if needed, based on configuration properties
        embedFonts(renderer);

        try {
            final ITextUserAgent callback = new ITextUserAgent(renderer.getOutputDevice()) {

                final IndentedLogger indentedLogger = new IndentedLogger(logger, "");

                // Called for:
                //
                // - CSS URLs
                // - image URLs
                // - link clicked / form submission (not relevant for our usage)
                // - resolveAndOpenStream below
                public String resolveURI(String uri) {
                    // Our own resolver

                    // All resources we care about here are resource URLs. The PDF pipeline makes sure that the servlet
                    // URL rewriter processes the XHTML output to rewrite resource URLs to absolute paths, including
                    // the servlet context and version number if needed. In addition, CSS resources must either use
                    // relative paths when pointing to other CSS files or images, or go through the XForms CSS rewriter,
                    // which also generates absolute paths.
                    // So all we need to do here is rewrite the resulting path to an absolute URL.
                    // NOTE: We used to call rewriteResourceURL() here as the PDF pipeline did not do URL rewriting.
                    // However this caused issues, for example resources like background images referred by CSS files
                    // could be rewritten twice: once by the XForms resource rewriter, and a second time here.

                    indentedLogger.logDebug("pdf", "before resolving URL", "url", uri);

                    final String resolved =
                        URLRewriterUtils.rewriteServiceURL(
                            NetUtils.getExternalContext().getRequest(),
                            uri,
                            ExternalContext.Response.REWRITE_MODE_ABSOLUTE | ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_NO_CONTEXT
                        );

                    indentedLogger.logDebug("pdf", "after resolving URL", "url", resolved);

                    return resolved;
                }

                // Called by:
                //
                // - getCSSResource
                // - getImageResource below
                // - getBinaryResource (not sure when called)
                // - getXMLResource (not sure when called)
                protected InputStream resolveAndOpenStream(String uri) {

                    final String resolvedURI = resolveURI(uri);
                    // TODO: Use xf:submission code instead

                    // Tell callee we are loading that we are a servlet environment, as in effect we act like
                    // a browser retrieving resources directly, not like a portlet. This is the case also if we are
                    // called by the proxy portlet or if we are directly within a portlet.
                    final Map<String, String[]> explicitHeaders = new HashMap<String, String[]>();
                    explicitHeaders.put(Headers.OrbeonClient(), new String[] { "servlet" });

                    final URI url;
                    try {
                        url = new URI(resolvedURI);
                    } catch (URISyntaxException e) {
                        throw new OXFException(e);
                    }
                    final scala.collection.immutable.Map<String, scala.collection.immutable.List<String>> headers =
                        Connection.jBuildConnectionHeadersLowerIfNeeded(
                            url.getScheme(),
                            false,
                            explicitHeaders,
                            Connection.jHeadersToForward(),
                            indentedLogger
                        );

                    final ConnectionResult cxr =
                        Connection.jApply("GET", url, null, null, headers, true, false, indentedLogger).connect(true);

                    final InputStream is =
                        ConnectionResult.withSuccessConnection(cxr, false, new Function1Adapter<InputStream, InputStream>() {
                            public InputStream apply(InputStream is) {
                                return is;
                            }

                        });

                    pipelineContext.addContextListener(new PipelineContext.ContextListener() {
                        public void contextDestroyed(boolean success) {
                            cxr.close();
                        }
                    });

                    return is;
                }

                // See https://github.com/orbeon/orbeon-forms/issues/1996
                //
                // Use our own local cache (NaiveUserAgent has one too) so that we can cache against the absolute URL
                // yet pass a local URL to super.getImageResource().
                //
                // This doesn't live beyond the production of this PDF as the ITextUserAgent is created each time.
                private HashMap<String, ImageResource> localImageCache = new HashMap<String, ImageResource>();

                public ImageResource getImageResource(String uri) {
                    final String resolvedURI = resolveURI(uri);
                    final ImageResource cachedImageResource = localImageCache.get(resolvedURI);

                    if (cachedImageResource != null) {
                        return cachedImageResource;
                    } else {
                        final InputStream is = resolveAndOpenStream(resolvedURI);
                        final String localURI = NetUtils.inputStreamToAnyURI(is, NetUtils.REQUEST_SCOPE, logger);

                        indentedLogger.logDebug("pdf", "getting image resource", "url", uri, "local", localURI);

                        final ImageResource retrievedImageResource = super.getImageResource(localURI);
                        localImageCache.put(resolvedURI, retrievedImageResource);
                        return retrievedImageResource;
                    }
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
