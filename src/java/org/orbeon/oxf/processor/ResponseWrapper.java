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
package org.orbeon.oxf.processor;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.generator.TidyConfig;
import org.orbeon.oxf.util.NetUtils;
import org.xml.sax.ContentHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;

public class ResponseWrapper implements ExternalContext.Response {
    private ExternalContext.Response _response;

    private PrintWriter printWriter;
    private StreamInterceptor streamInterceptor = new StreamInterceptor();

    public ResponseWrapper(ExternalContext.Response response) {
        this._response = response;
    }

    public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
        return _response.checkIfModifiedSince(lastModified, allowOverride);
    }

    public String getCharacterEncoding() {
        return _response.getCharacterEncoding();
    }

    public String getNamespacePrefix() {
        return _response.getNamespacePrefix();
    }

    public OutputStream getOutputStream() throws IOException {
        return streamInterceptor.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        if (printWriter == null)
            printWriter = new PrintWriter(streamInterceptor.getWriter());
        return printWriter;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public String rewriteActionURL(String urlString) {
        return _response.rewriteActionURL(urlString);
    }

    public String rewriteRenderURL(String urlString) {
        return _response.rewriteRenderURL(urlString);
    }

    public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
        return _response.rewriteResourceURL(urlString, generateAbsoluteURL);
    }

    public void sendError(int len) throws IOException {
        // NOTE: Should do something?
    }

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    public void setContentLength(int len) {
    }

    public void setContentType(String contentType) {
        streamInterceptor.setEncoding(NetUtils.getContentTypeCharset(contentType));
        streamInterceptor.setContentType(NetUtils.getContentTypeContentType(contentType));
    }

    public void setHeader(String name, String value) {
    }

    public void addHeader(String name, String value) {
    }

    public void setStatus(int status) {
    }

    public void setTitle(String title) {
    }

    public void parse(ContentHandler contentHandler) {
        parse(contentHandler, null);
    }

    public void parse(ContentHandler contentHandler, TidyConfig tidyConfig) {
        streamInterceptor.parse(contentHandler, tidyConfig, false);
    }
}
