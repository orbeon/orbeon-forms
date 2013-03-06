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

import org.orbeon.oxf.pipeline.api.XMLReceiver;
import org.orbeon.oxf.processor.generator.TidyConfig;
import org.orbeon.oxf.util.NetUtils;

import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.*;
import java.io.*;
import java.util.Locale;

public class ServletResponseWrapper extends HttpServletResponseWrapper {

    private PrintWriter printWriter;
    private ServletOutputStream servletOutputStream;

    private StreamInterceptor streamInterceptor = new StreamInterceptor();

    public ServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    public PrintWriter getWriter() throws IOException {
        if (printWriter == null)
            printWriter = new PrintWriter(streamInterceptor.getWriter());
        return printWriter;
    }

    public ServletOutputStream getOutputStream() throws IOException {
        if (servletOutputStream == null)
            servletOutputStream = new ByteArrayServletOutputStream(streamInterceptor.getOutputStream());
        return servletOutputStream;
    }

    public void setContentType(String contentType) {
        streamInterceptor.setEncoding(NetUtils.getContentTypeCharset(contentType));
        streamInterceptor.setContentType(NetUtils.getContentTypeMediaType(contentType));
    }

    public void addCookie(Cookie cookie) {
    }

    public void addDateHeader(String s, long l) {
    }

    public void addHeader(String s, String s1) {
    }

    public void addIntHeader(String s, int i) {
    }

    public boolean containsHeader(String s) {
        return false;
    }

    public String encodeRedirectURL(String s) {
        return super.encodeRedirectURL(s);
    }

    public String encodeRedirectUrl(String s) {
        return super.encodeRedirectUrl(s);
    }

    public String encodeURL(String s) {
        return super.encodeURL(s);
    }

    public String encodeUrl(String s) {
        return super.encodeUrl(s);
    }

    public void sendError(int i) throws IOException {
        // NOTE: Should do something?
    }

    public void sendError(int i, String s) throws IOException {
        // NOTE: Should do something?
    }

    public void sendRedirect(String s) throws IOException {
    }

    public void setDateHeader(String s, long l) {
    }

    public void setHeader(String s, String s1) {
    }

    public void setIntHeader(String s, int i) {
    }

    public void setStatus(int i) {
    }

    public void setStatus(int i, String s) {
    }

    public void flushBuffer() throws IOException {
    }

    public int getBufferSize() {
        // NOTE: What makes sense here?
        return super.getBufferSize();
    }

    public String getCharacterEncoding() {
        // NOTE: What makes sense here?
        return super.getCharacterEncoding();
    }

    public Locale getLocale() {
        // NOTE: What makes sense here?
        return super.getLocale();
    }

    public boolean isCommitted() {
        // NOTE: What makes sense here?
        return false;
    }

    public void reset() {
    }

    public void resetBuffer() {
    }

    public void setBufferSize(int i) {
    }

    public void setContentLength(int i) {
    }

    public void setLocale(Locale locale) {
    }

    public void setResponse(ServletResponse servletResponse) {
    }

    public void parse(XMLReceiver xmlReceiver) {
        parse(xmlReceiver, null);
    }

    public void parse(XMLReceiver xmlReceiver, TidyConfig tidyConfig) {
        streamInterceptor.parse(xmlReceiver, tidyConfig, false);
    }

    public static class ByteArrayServletOutputStream extends ServletOutputStream {
        private final OutputStream byteOutput;

        public ByteArrayServletOutputStream(OutputStream output) {
            this.byteOutput = output;
        }

        public void write(int b) throws IOException {
            byteOutput.write(b);
        }
    }
}
