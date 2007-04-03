/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.servlet;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.http.Cookie;
import java.io.*;
import java.util.Locale;

/**
 * This filter allows forwarding requests from your web app to an separate Orbeon Forms context.
 *
 * This filter must remain very simple and have as little dependencies as possible, because it must be easily deployable
 * into any web application.
 */
public class OPSXFormsFilter implements Filter {

    public static final String OPS_XFORMS_RENDERER_DOCUMENT_PARAMETER_NAME = "oxf.xforms.renderer.document";

    public static final String OPS_SERVLET_CONTEXT_ATTRIBUTE_NAME = "oxf.servlet.context";
    public static final String OPS_RENDERER_PATH = "/xforms-renderer";

    private static final String OPS_XFORMS_RENDERER_CONTEXT_PARAMETER_NAME = "oxf.xforms.renderer.context";
    private static final String DEFAULT_ENCODING = "ISO-8859-1"; // must be this per Servlet spec

    private ServletContext servletContext;
    private String opsContextPath;

    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
        opsContextPath = filterConfig.getInitParameter(OPS_XFORMS_RENDERER_CONTEXT_PARAMETER_NAME);

        // TODO: check opsContextPath format: starts with /, doesn't end with one, etc.
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        if (isOPSResourceRequest(httpRequest)) {
            // Directly forward all requests meant for Orbeon Forms resources
            final String subRequestPath = getRequestPathInfo(httpRequest).substring(opsContextPath.length());
            getOPSDispatcher(subRequestPath).forward(httpRequest, httpResponse);
        } else {
            // Forward the request to the Orbeon Forms renderer
            final MyHttpServletResponseWrapper responseWrapper = new MyHttpServletResponseWrapper(httpResponse);

            // Execute filter
            filterChain.doFilter(servletRequest, responseWrapper);

            // Set document if not present AND output was intercepted
            if (httpRequest.getAttribute(OPS_XFORMS_RENDERER_DOCUMENT_PARAMETER_NAME) == null) {
                final String content = responseWrapper.getContent();
                if (content != null) {
                    httpRequest.setAttribute(OPS_XFORMS_RENDERER_DOCUMENT_PARAMETER_NAME, content);
                }
            }

            // Override Orbeon Forms context so that rewriting works correctly
            if (opsContextPath != null)
                httpRequest.setAttribute(OPS_SERVLET_CONTEXT_ATTRIBUTE_NAME, httpRequest.getContextPath() + opsContextPath);

            // Forward to Orbeon Forms for rendering
            getOPSDispatcher(OPS_RENDERER_PATH).forward(httpRequest, httpResponse);
        }
    }

    public void destroy() {
    }

    private RequestDispatcher getOPSDispatcher(String path) throws ServletException {
        final ServletContext opsContext = (opsContextPath != null) ? servletContext.getContext(opsContextPath) : servletContext;
        if (opsContext == null)
            throw new ServletException("Can't find Orbeon Forms context called '" + opsContextPath + "'. Check the '" + OPS_XFORMS_RENDERER_CONTEXT_PARAMETER_NAME + "' filter initialization parameter and the <Context crossContext=\"true\"/> attribute.");
        final RequestDispatcher dispatcher = opsContext.getRequestDispatcher(path);
        if (dispatcher == null)
            throw new ServletException("Can't find Orbeon Forms request dispatcher.");

        return dispatcher;
    }

    private boolean isOPSResourceRequest(HttpServletRequest request) {
        if (opsContextPath == null)
            return false;

        final String pathInfo = getRequestPathInfo(request);
        return pathInfo != null && pathInfo.startsWith(opsContextPath + "/");
    }

    // NOTE: This is borrowed from NetUtils but we don't want the dependency
    private static String getRequestPathInfo(HttpServletRequest request) {

        // Get servlet path and path info
        String servletPath = request.getServletPath();
        if (servletPath == null) servletPath = "";
        String pathInfo = request.getPathInfo();
        if (pathInfo == null) pathInfo = "";

        // Concatenate servlet path and path info, avoiding a double slash
        String requestPath = servletPath.endsWith("/") && pathInfo.startsWith("/")
                ? servletPath + pathInfo.substring(1)
                : servletPath + pathInfo;

        // Add starting slash if missing
        if (!requestPath.startsWith("/"))
            requestPath = "/" + requestPath;

        return requestPath;
    }

    // NOTE: This is borrowed from NetUtils but we don't want the dependency
    public static String getContentTypeCharset(String contentType) {
        if (contentType == null)
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return null;
        int charsetIndex = contentType.indexOf("charset=", semicolumnIndex);
        if (charsetIndex == -1)
            return null;
        // FIXME: There may be other attributes after charset, right?
        String afterCharset = contentType.substring(charsetIndex + 8);
        afterCharset = afterCharset.replace('"', ' ');
        return afterCharset.trim();
    }

    // NOTE: This is borrowed from NetUtils but we don't want the dependency
    public static String getContentTypeMediaType(String contentType) {
        if (contentType == null || contentType.equalsIgnoreCase("content/unknown"))
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return contentType;
        return contentType.substring(0, semicolumnIndex).trim();
    }

    private static class MyHttpServletResponseWrapper extends HttpServletResponseWrapper {

        public MyHttpServletResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        private ByteArrayOutputStream byteArrayOutputStream;
        private ServletOutputStream servletOutputStream;

        private StringWriter stringWriter;
        private PrintWriter printWriter;


        private String encoding;
        private String mediatype;

        public void addCookie(Cookie cookie) {
            super.addCookie(cookie);
        }

        public boolean containsHeader(String string) {
            return super.containsHeader(string);
        }

        public String encodeURL(String string) {
            return super.encodeURL(string);
        }

        public String encodeRedirectURL(String string) {
            return super.encodeRedirectURL(string);
        }

        public String encodeUrl(String string) {
            return super.encodeUrl(string);
        }

        public String encodeRedirectUrl(String string) {
            return super.encodeRedirectUrl(string);
        }

        public void sendError(int i, String string) throws IOException {
            // TODO
        }

        public void sendError(int i) throws IOException {
            // TODO
        }

        public void sendRedirect(String string) throws IOException {
            // TODO
        }

        public void setDateHeader(String string, long l) {
            // TODO
        }

        public void addDateHeader(String string, long l) {
            // TODO
        }

        public void setHeader(String string, String string1) {
            // TODO
        }

        public void addHeader(String string, String string1) {
            // TODO
        }

        public void setIntHeader(String string, int i) {
            // TODO
        }

        public void addIntHeader(String string, int i) {

            // TODO
        }

        public void setStatus(int i) {
            // TODO
        }

        public void setStatus(int i, String string) {
            // TODO
        }


        public ServletResponse getResponse() {
            return super.getResponse();
        }

        public void setResponse(ServletResponse servletResponse) {
            super.setResponse(servletResponse);
        }

        public String getCharacterEncoding() {
            // TODO: we don't support setLocale()
            return (encoding == null) ? DEFAULT_ENCODING : encoding;
        }

        public ServletOutputStream getOutputStream() throws IOException {
            if (byteArrayOutputStream == null) {
                byteArrayOutputStream = new ByteArrayOutputStream();
                servletOutputStream = new ServletOutputStream() {
                    public void write(int i) throws IOException {
                        byteArrayOutputStream.write(i);
                    }
                };
            }
            return servletOutputStream;
        }

        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                stringWriter = new StringWriter();
                printWriter = new PrintWriter(stringWriter);
            }
            return printWriter;
        }

        public void setContentLength(int i) {
            // NOP
        }

        public void setContentType(String contentType) {
            this.encoding = getContentTypeCharset(contentType);
            this.mediatype = getContentTypeMediaType(contentType);
        }

        public void setBufferSize(int i) {
            // NOP
        }

        public int getBufferSize() {
            // We have a buffer, but it is infinite
            return Integer.MAX_VALUE;
        }

        public void flushBuffer() throws IOException {
            // NOPE
        }

        public boolean isCommitted() {
            // We buffer everything so return false all the time
            return false;
        }

        public void reset() {
            resetBuffer();
        }

        public void resetBuffer() {
            if (byteArrayOutputStream != null) {
                try {
                    servletOutputStream.flush();
                } catch (IOException e) {
                    // ignore?
                }
                byteArrayOutputStream.reset();
            } else if (stringWriter != null) {
                printWriter.flush();
                final StringBuffer sb = stringWriter.getBuffer();
                sb.delete(0, sb.length());
            }
        }

        public void setLocale(Locale locale) {
            // TODO
        }

        public Locale getLocale() {
            return super.getLocale();
        }

        public String getContent() throws IOException {
            if (stringWriter != null) {
                // getWriter() was used
                printWriter.flush();
                return stringWriter.toString();
            } else if (servletOutputStream != null) {
                // getOutputStream() was used
                servletOutputStream.flush();
                return new String(byteArrayOutputStream.toByteArray(), getCharacterEncoding());
            } else {
                return null;
            }
        }
    }
}