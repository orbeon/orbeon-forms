/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import java.io.*;
import java.util.Map;

public class ResponseWrapper implements ExternalContext.Response {
    private ExternalContext.Response _response;

    public ResponseWrapper(ExternalContext.Response response) {
        if (response == null)
            throw new IllegalArgumentException();
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
        return _response.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        return _response.getWriter();
    }

    public boolean isCommitted() {
        return _response.isCommitted();
    }

    public void reset() {
        _response.reset();
    }

    public String rewriteActionURL(String urlString) {
        return _response.rewriteActionURL(urlString);
    }

    public String rewriteRenderURL(String urlString) {
        return _response.rewriteRenderURL(urlString);
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return _response.rewriteActionURL(urlString, portletMode, windowState);
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return _response.rewriteRenderURL(urlString, portletMode, windowState);
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return _response.rewriteResourceURL(urlString, rewriteMode);
    }

    public void sendError(int len) throws IOException {
        _response.sendError(len);
    }

    public void sendRedirect(String pathInfo, Map<String, String[]> parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
        _response.sendRedirect(pathInfo, parameters, isServerSide, isExitPortal);
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
        _response.setCaching(lastModified, revalidate, allowOverride);
    }

    public void setResourceCaching(long lastModified, long expires) {
        _response.setResourceCaching(lastModified, expires);
    }

    public void setContentLength(int len) {
        _response.setContentLength(len);
    }

    public void setContentType(String contentType) {
        _response.setContentType(contentType);
    }

    public void setHeader(String name, String value) {
        _response.setHeader(name, value);
    }

    public void addHeader(String name, String value) {
        _response.addHeader(name, value);
    }

    public void setStatus(int status) {
        _response.setStatus(status);
    }

    public void setTitle(String title) {
        _response.setTitle(title);
    }

    public Object getNativeResponse() {
        return _response.getNativeResponse();
    }

    public ExternalContext.Response _getResponse() {
        return _response;
    }
}
