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
import org.orbeon.oxf.xml.XMLUtils;

import java.io.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ConnectionResult {

    public static final Map<String, List<String>> EMPTY_HEADERS_MAP = Collections.emptyMap();

    public boolean dontHandleResponse;
    public int statusCode;
    private String responseMediaType;
    private String responseContentType;
    public Map<String, List<String>> responseHeaders;
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
            for (Map.Entry<String, List<String>> currentEntry: responseHeaders.entrySet()) {
                final String headerName = (String) currentEntry.getKey();
                if (headerName != null) {
                    // NOTE: Values could be a String in the past, but that shouldn't be the case anymore!
                    final List<String> headerValues = currentEntry.getValue();
                    if (headerValues != null) {
                        for (String headerValue: headerValues) {
                            if (headerValue != null) {
                                forwardHeaderFilter(response, headerName, headerValue);
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

    /**
     * Return the response body as text, null if not a text or XML result.
     *
     * @return  response body or null
     * @throws IOException
     */
    public String getTextResponseBody() throws IOException {
        if (XMLUtils.isTextContentType(getResponseMediaType())) {
            // Text mediatype (including text/xml), read stream into String
            final String charset = NetUtils.getTextCharsetFromContentType(getResponseContentType());
            final Reader reader = new InputStreamReader(getResponseInputStream(), charset);
            try {
                return NetUtils.readStreamAsString(reader);
            } finally {
                try {
                    reader.close();
                } catch (Exception e) {
                    // NOP
                }
            }
        } else if (XMLUtils.isXMLMediatype(getResponseMediaType())) {
            // XML mediatype other than text/xml

            // TODO: What should we do if the response Content-Type includes a charset parameter?
            final Reader reader = XMLUtils.getReaderFromXMLInputStream(resourceURI, getResponseInputStream());
            try {
                return NetUtils.readStreamAsString(reader);
            } finally {
                try {
                    reader.close();
                } catch (Exception e) {
                    // NOP
                }
            }
        } else {
            // This is a binary result
            return null;
        }
    }
}
