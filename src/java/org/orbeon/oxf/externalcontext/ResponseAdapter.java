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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;


public class ResponseAdapter implements ExternalContext.Response {
    public PrintWriter getWriter() throws IOException {
        return null;
    }

    public OutputStream getOutputStream() throws IOException {
        return null;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public void setContentType(String contentType) {
    }

    public void setStatus(int status) {
    }

    public void setContentLength(int len) {
    }

    public void setHeader(String name, String value) {
    }

    public void addHeader(String name, String value) {
    }

    public void sendError(int len) throws IOException {
    }

    public String getCharacterEncoding() {
        return null;
    }

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    public void setResourceCaching(long lastModified, long expires) {
    }

    public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
        // Always indicate that the resource has been modified. If needed we could use:
        // return NetUtils.checkIfModifiedSince(request, lastModified);
        return true;
    }

    public String rewriteActionURL(String urlString) {
        return null;
    }

    public String rewriteRenderURL(String urlString) {
        return null;
    }

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return null;
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
        return null;
    }

    public String rewriteResourceURL(String urlString, int rewriteMode) {
        return null;
    }

    public String getNamespacePrefix() {
        return null;
    }

    public void setTitle(String title) {
    }

    public Object getNativeResponse() {
        return null;
    }
}
