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
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;

import javax.portlet.*;
import javax.servlet.http.Cookie;
import java.io.*;
import java.util.Collection;
import java.util.Locale;

/**
 * Wrap an ExternalContext.Request into a RenderResponse.
 *
 * Methods with counterparts in ExternalContext.Response use the wrapped
 * ExternalContext.Response object and can be overridden using ResponseWrapper. Other methods
 * directly forward to the native response.
 *
 * TODO: Review this.
 */
public class ExternalContextToPortletRenderResponseWrapper implements RenderResponse {

    private ExternalContext.Response response;
    private RenderResponse nativeResponse;

    public ExternalContextToPortletRenderResponseWrapper(ExternalContext.Response response) {
        this.response = response;
        if (response.getNativeResponse() instanceof RenderResponse)
            this.nativeResponse = (RenderResponse) response.getNativeResponse();

        // TODO: Throw this until we implement this properly
        throw new UnsupportedOperationException();
    }

    public PortletURL createActionURL() {
        if (nativeResponse != null)
            return nativeResponse.createActionURL();
        else
            return null;
    }

    public PortletURL createRenderURL() {
        if (nativeResponse != null)
            return nativeResponse.createRenderURL();
        else
            return null;
    }

    public void flushBuffer() throws IOException {
        // TODO
    }

    public int getBufferSize() {
        return 0;// TODO
    }

    public String getCharacterEncoding() {
        return response.getCharacterEncoding();
    }

    public String getContentType() {
        if (nativeResponse != null)
            return nativeResponse.getContentType();
        else
            return null;
    }

    public Locale getLocale() {
        if (nativeResponse != null)
            return nativeResponse.getLocale();
        else
            return null;
    }

    public String getNamespace() {
        if (nativeResponse != null)
            return nativeResponse.getNamespace();
        else
            return null;
    }

    public OutputStream getPortletOutputStream() throws IOException {
        return response.getOutputStream();
    }

    public PrintWriter getWriter() throws IOException {
        return response.getWriter();
    }

    public boolean isCommitted() {
        return response.isCommitted();
    }

    public void reset() {
        response.reset();
    }

    public void resetBuffer() {
        // TODO
    }

    public void setBufferSize(int i) {
        // TODO
    }

    public void setContentType(String clazz) {
        response.setContentType(clazz);
    }

    public void setTitle(String clazz) {
        if (nativeResponse != null)
            nativeResponse.setTitle(clazz);
    }

    public void addProperty(String clazz, String clazz1) {
        // TODO
    }

    public String encodeURL(String clazz) {
        if (nativeResponse != null)
            return nativeResponse.encodeURL(clazz);
        else
            return null;
    }

    public void setProperty(String clazz, String clazz1) {
        // TODO
    }

    // JSR-286 methods

    public ResourceURL createResourceURL() {
        if (nativeResponse != null)
            return nativeResponse.createResourceURL();
        else
            return null;
    }

    public CacheControl getCacheControl() {
        if (nativeResponse != null)
            return nativeResponse.getCacheControl();
        else
            return null;
    }

    public void setNextPossiblePortletModes(Collection collection) {
        // TODO
    }

    public void addProperty(Cookie cookie) {
        // TODO
    }

    public void addProperty(String s, Element element) {
        // TODO
    }

    public Element createElement(String s) throws DOMException {
        // TODO
        return null;
    }
}
