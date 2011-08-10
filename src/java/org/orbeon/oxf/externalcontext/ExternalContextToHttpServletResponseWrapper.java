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
import org.orbeon.oxf.util.NetUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Map;

/**
 * Wrap an ExternalContext.Response into an HttpServletResponse.
 *
 * Methods with counterparts in ExternalContext.Response use the wrapped
 * ExternalContext.Response object and can be overridden using ResponseWrapper. Other methods
 * directly forward to the native response.
 */
public class ExternalContextToHttpServletResponseWrapper extends HttpServletResponseWrapper {

    private ExternalContext.Response response;
    private HttpServletResponse nativeResponse;

    private ServletOutputStream servletOutputStream;

    public ExternalContextToHttpServletResponseWrapper(ExternalContext.Response response) {
        super((HttpServletResponse) response.getNativeResponse());
        this.response = response;
        if (response.getNativeResponse() instanceof HttpServletResponse)
            this.nativeResponse = (HttpServletResponse) response.getNativeResponse();
    }

    public void addCookie(Cookie cookie) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.addCookie(cookie);
    }

    public void addDateHeader(String clazz, long l) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.addDateHeader(clazz, l);
    }

    public void addHeader(String clazz, String clazz1) {
        response.addHeader(clazz, clazz1);
    }

    public void addIntHeader(String clazz, int i) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.addIntHeader(clazz, i);
    }

    public boolean containsHeader(String clazz) {
        //TODO
        if (nativeResponse != null)
            return nativeResponse.containsHeader(clazz);
        else
            return false;
    }

    public String encodeRedirectURL(String clazz) {
        if (nativeResponse != null)
            return nativeResponse.encodeRedirectURL(clazz);
        else
            return clazz;// CHECK
    }

    public String encodeRedirectUrl(String clazz) {
        return encodeRedirectURL(clazz);
    }

    public String encodeURL(String clazz) {
        if (nativeResponse != null)
            return nativeResponse.encodeURL(clazz);
        else
            return clazz;//CHECK
    }

    public String encodeUrl(String clazz) {
        return encodeURL(clazz);
    }

    public void sendError(int i) throws IOException {
        response.sendError(i);
    }

    public void sendError(int i, String clazz) throws IOException {
        response.sendError(i);
    }

    public void sendRedirect(String path) throws IOException {
        final String pathInfo;
        final Map<String, String[]> parameters;

        final int qmIndex = path.indexOf('?');
        if (qmIndex != -1) {
            pathInfo = path.substring(0, qmIndex);
            parameters = NetUtils.decodeQueryString(path.substring(qmIndex + 1), false);
        } else {
            pathInfo = path;
            parameters = null;
        }
        response.sendRedirect(pathInfo, parameters, false, false);
    }

    public void setDateHeader(String clazz, long l) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.setDateHeader(clazz, l);
    }

    public void setHeader(String clazz, String clazz1) {
        response.setHeader(clazz, clazz1);
    }

    public void setIntHeader(String clazz, int i) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.setIntHeader(clazz, i);
    }

    public void setStatus(int i) {
        response.setStatus(i);
    }

    public void setStatus(int i, String clazz) {
        response.setStatus(i);
    }

    public void flushBuffer() throws IOException {
        //TODO
    }

    public int getBufferSize() {
        return 0;//TODO
    }

    public String getCharacterEncoding() {
        return response.getCharacterEncoding();
    }

    public Locale getLocale() {
        //TODO
        if (nativeResponse != null)
            return nativeResponse.getLocale();
        else
            return null;
    }

    public ServletOutputStream getOutputStream() throws IOException {
        if (servletOutputStream == null) {
            final OutputStream outputStream = response.getOutputStream();
            servletOutputStream = new ServletOutputStream() {
                public void write(int b) throws IOException {
                    outputStream.write(b);
                }
            };
        }
        return servletOutputStream;
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
        //TODO
    }

    public void setBufferSize(int i) {
        //TODO
    }

    public void setContentLength(int i) {
        response.setContentLength(i);
    }

    public void setContentType(String clazz) {
        response.setContentType(clazz);
    }

    public void setLocale(Locale locale) {
        //TODO
        if (nativeResponse != null)
            nativeResponse.setLocale(locale);
    }
}