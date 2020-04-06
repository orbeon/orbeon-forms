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

import java.io.OutputStream;
import java.io.PrintWriter;


public class ResponseAdapter implements ExternalContext.Response {
    public PrintWriter getWriter() {
        return null;
    }

    public OutputStream getOutputStream() {
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

    public void sendError(int len) {
    }

    public String getCharacterEncoding() {
        return null;
    }

    public void sendRedirect(String location, boolean isServerSide, boolean isExitPortal) {
    }

    public void setPageCaching(long lastModified) {
    }

    public void setResourceCaching(long lastModified, long expires) {
    }

    public boolean checkIfModifiedSince(ExternalContext.Request request, long lastModified) {
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
