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
package org.orbeon.oxf.util;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;

public class ConnectionResult {

    public boolean dontHandleResponse;
    public int statusCode;
    private String responseMediaType;
    private String responseContentType;
    public Map responseHeaders;
    private Long lastModified;
    public String resourceURI;

    private InputStream responseInputStream;
    private boolean hasContent;

    public ConnectionResult(String resourceURI) {
        this.resourceURI = resourceURI;
    }

    public InputStream getResponseInputStream() {
        return responseInputStream;
    }

    public boolean hasContent() {
        return hasContent;
    }

    public void setResponseInputStream(final InputStream responseInputStream) throws IOException {
        this.responseInputStream = responseInputStream;
        setHasContentFlag();

    }

    public void setResponseContentType(String responseContentType) {
        this.responseContentType = responseContentType;
    }

    public String getResponseContentType() {
        return responseContentType;
    }

    public String getResponseMediaType() {
        if (responseMediaType == null) {
            responseMediaType = NetUtils.getContentTypeMediaType(responseContentType);
        }
        return responseMediaType;
    }

    public Long getLastModified() {
        return lastModified;
    }

    public void setLastModified(Long lastModified) {
        this.lastModified = lastModified;
    }

    private void setHasContentFlag() throws IOException {
        if (responseInputStream == null) {
            hasContent = false;
        } else {
            if (!responseInputStream.markSupported())
                this.responseInputStream = new BufferedInputStream(responseInputStream);

            responseInputStream.mark(1);
            hasContent = responseInputStream.read() != -1;
            responseInputStream.reset();
        }
    }

    public void forwardHeaders(ExternalContext.Response response) {
        if (responseHeaders != null) {
            for (Iterator i = responseHeaders.entrySet().iterator(); i.hasNext();) {
                final Map.Entry currentEntry = (Map.Entry) i.next();
                final String headerName = (String) currentEntry.getKey();

                if (headerName != null) {
                    // NOTE: As per the doc, this should always be a List, but for some unknown reason
                    // it appears to be a String sometimes
                    if (currentEntry.getValue() instanceof String) {
                        // Case of String
                        final String headerValue = (String) currentEntry.getValue();
                        forwardHeaderFilter(response, headerName, headerValue);
                    } else {
                        // Case of List
                        final List headerValues = (List) currentEntry.getValue();
                        if (headerValues != null) {
                            for (Iterator j = headerValues.iterator(); j.hasNext();) {
                                final String headerValue = (String) j.next();
                                if (headerValue != null) {
                                    forwardHeaderFilter(response, headerName, headerValue);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void forwardHeaderFilter(ExternalContext.Response response, String headerName, String headerValue) {
        /**
         * Filtering the Transfer-Encoding header
         *
         * We don't pass the Transfer-Encoding header, as the request body is
         * already decoded for us. Passing along the Transfer-Encoding causes a
         * problem if the server sends us chunked data and we send it in the
         * response not chunked but saying in the header that it is chunked.
         *
         * Non-filtering of Content-Encoding header
         *
         * The Content-Encoding has the potential of causing the same problem as
         * the Transfer-Encoding header. It could be an issue if we get data with
         * Content-Encoding: gzip, but pass it along uncompressed but still
         * include the Content-Encoding: gzip. However this does not happen, as
         * the request we send does not contain a Accept-Encoding: gzip,deflate. So
         * filtering the Content-Encoding header is safe here.
         */
        if (!"transfer-encoding".equals(headerName.toLowerCase())) {
            response.addHeader(headerName, headerValue);
        }
    }

    public void close() {}
}
