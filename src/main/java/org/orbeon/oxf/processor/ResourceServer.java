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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.StatusCode;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.resources.ResourceNotFoundException;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.Mediatypes;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PathMatcher;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.xml.XPathUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.List;

/**
 * Serve resources to the response.
 */
public class ResourceServer extends ProcessorImpl {

    public static final String RESOURCE_SERVER_NAMESPACE_URI = "http://www.orbeon.com/oxf/resource-server";
    public static final long ONE_YEAR_IN_MILLISECONDS = 365L * 24 * 60 * 60 * 1000;

    public ResourceServer() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG, RESOURCE_SERVER_NAMESPACE_URI));
    }

    public void start(PipelineContext context) {
        try {
            // Read config input into a String, cache if possible
            final org.orbeon.dom.Node configNode = readCacheInputAsOrbeonDom(context, INPUT_CONFIG);

            // Get config URL first
            String urlString = XPathUtils.selectStringValueNormalize(configNode, "url");

            // For backward compatibility, try to get path element
            if (urlString == null) {
                urlString = XPathUtils.selectStringValueNormalize(configNode, "path");

                // There must be a configuration
                if (urlString == null)
                    throw new OXFException("Missing configuration.");
            }

            final List<PathMatcher> pathMatchers = URLRewriterUtils.getPathMatchers();
            serveResource(urlString, URLRewriterUtils.isVersionedURL(urlString, pathMatchers));
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    public static void serveResource(String urlString, boolean isVersioned) throws IOException {

        final ExternalContext externalContext = NetUtils.getExternalContext();
        final ExternalContext.Response response = externalContext.getResponse();

        // Remove version from the path if it is versioned
        urlString = URLRewriterUtils.decodeResourceURI(urlString, isVersioned);

        // Use the default protocol to read the file as a resource
        if (!urlString.startsWith("oxf:"))
            urlString = "oxf:" + urlString;

        InputStream urlConnectionInputStream = null;
        try {
            // Open resource and set headers
            try {
                final URL newURL = URLFactory.createURL(urlString);

                // Open the connection
                final URLConnection urlConnection = newURL.openConnection();
                urlConnectionInputStream = urlConnection.getInputStream();

                // Get length and last modified
                final int length = urlConnection.getContentLength();
                final long lastModified = NetUtils.getLastModified(urlConnection);

                // Set Last-Modified, required for caching and conditional get
                if (isVersioned) {
                    // Use expiration far in the future
                    response.setResourceCaching(lastModified, System.currentTimeMillis() + ONE_YEAR_IN_MILLISECONDS);
                } else {
                    // Use standard expiration policy
                    response.setResourceCaching(lastModified, 0);
                }

                // Check If-Modified-Since and don't return content if condition is met
                if (!response.checkIfModifiedSince(externalContext.getRequest(), lastModified)) {
                    response.setStatus(StatusCode.NotModified());
                    return;
                }

                // Lookup and set the content type
                final String contentType = Mediatypes.findMediatypeForPathJava(urlString);
                if (contentType != null)
                    response.setContentType(contentType);

                if (length > 0)
                    response.setContentLength(length);

            } catch (IOException e) {
                response.setStatus(StatusCode.NotFound());
                return;
            } catch (ResourceNotFoundException e) {
                // Note: we should really not get this exception here, but an IOException
                // However we do actually get it, and so do the same we do for IOException.
                response.setStatus(StatusCode.NotFound());
                return;
            }
            // Copy stream to output
            NetUtils.copyStream(urlConnectionInputStream, response.getOutputStream());
        } finally {
            // Make sure the stream is closed in all cases so as to not lock the file on disk
            if (urlConnectionInputStream != null) {
                urlConnectionInputStream.close();
            }
        }
    }
}
