/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.pipeline.api.ExternalContext;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Wrap an ExternalContext.Response into an HttpServletResponse.
 */
public class ExternalContextToHttpServletResponseWrapper implements HttpServletResponse {

    private ExternalContext.Response response;
    private ServletOutputStream servletOutputStream;

    public ExternalContextToHttpServletResponseWrapper(ExternalContext.Response response) {
        this.response = response;
    }

    public void addCookie(Cookie cookie) {
        //TODO
        System.out.println();
    }

    public void addDateHeader(String clazz, long l) {
        //TODO
        System.out.println();
    }

    public void addHeader(String clazz, String clazz1) {
        //TODO
        System.out.println();
    }

    public void addIntHeader(String clazz, int i) {
        //TODO
        System.out.println();
    }

    public boolean containsHeader(String clazz) {
        return false;//TODO
    }

    public String encodeRedirectURL(String clazz) {
        return response.rewriteRenderURL(clazz);// CHECK
    }

    public String encodeRedirectUrl(String clazz) {
        return encodeRedirectURL(clazz);
    }

    public String encodeURL(String clazz) {
        return null;//TODO
    }

    public String encodeUrl(String clazz) {
        return encodeURL(clazz);
    }

    public void sendError(int i) throws IOException {
        response.sendError(i);
    }

    public void sendError(int i, String clazz) throws IOException {
        //TODO
        System.out.println();
    }

    public void sendRedirect(String clazz) throws IOException {
        //TODO
        System.out.println();
    }

    public void setDateHeader(String clazz, long l) {
        //TODO
        System.out.println();
    }

    public void setHeader(String clazz, String clazz1) {
        response.setHeader(clazz, clazz1);
    }

    public void setIntHeader(String clazz, int i) {
        //TODO
        System.out.println();
    }

    public void setStatus(int i) {
        response.setStatus(i);
    }

    public void setStatus(int i, String clazz) {
        //TODO
        System.out.println();
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
        return null;//TODO
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
    }
}