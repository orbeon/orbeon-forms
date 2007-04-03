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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.util.Locale;

/**
 * This filter allows forwarding requests from your web app to an separate Orbeon Forms context.
 *
 * This filter must remain very simple and have as little dependencies as possible, because it must be easily deployable
 * into any web application.
 */
public class OPSRendererFilter implements Filter {

    private static final String OPS_CONTEXT_PATH_PARAMETER_NAME = "oxf.xforms.renderer.context";
    private static final String OPS_RENDER_SERVLET_PATH = "/xforms-renderer";
    private static final String DEFAULT_ENCODING = "utf-8";

    private ServletContext servletContext;
    private String opsContextPath;

    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
        opsContextPath = filterConfig.getInitParameter(OPS_CONTEXT_PATH_PARAMETER_NAME);
        if (opsContextPath == null)
            throw new ServletException("Filter initialization parameter '" + OPS_CONTEXT_PATH_PARAMETER_NAME + "' is required for filter: " + filterConfig.getFilterName());

        // TODO: check opsContextPath format: starts with /, doesn't end with one, etc.
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        final HttpServletRequest request = (HttpServletRequest) servletRequest;
        final HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (isOPSResourceRequest(request)) {
            // Directly forward all requests meant for Orbeon Forms
            final String subRequestPath = getRequestPathInfo(request).substring(opsContextPath.length());
            getOPSDispatcher(subRequestPath).forward(request, response);
        } else {
            // Forward the request to the Orbeon Forms renderer
            final HttpServletResponseWrapper responseWrapper = new HttpServletResponseWrapper(response) {
                // TODO: capture output

                private ByteArrayOutputStream byteArrayOutputStream;
                private ServletOutputStream servletOutputStream;
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
                    System.out.println();
//                    super.sendError(i, string);
                }

                public void sendError(int i) throws IOException {
                    System.out.println();
//                    super.sendError(i);
                }

                public void sendRedirect(String string) throws IOException {
                    System.out.println();
//                    super.sendRedirect(string);
                }

                public void setDateHeader(String string, long l) {
                    System.out.println();
//                    super.setDateHeader(string, l);
                }

                public void addDateHeader(String string, long l) {
                    System.out.println();
//                    super.addDateHeader(string, l);
                }

                public void setHeader(String string, String string1) {
                    System.out.println();
//                    super.setHeader(string, string1);
                }

                public void addHeader(String string, String string1) {
                    System.out.println();
//                    super.addHeader(string, string1);
                }

                public void setIntHeader(String string, int i) {
                    System.out.println();
//                    super.setIntHeader(string, i);
                }

                public void addIntHeader(String string, int i) {
                    System.out.println();
//                    super.addIntHeader(string, i);
                }

                public void setStatus(int i) {
                    System.out.println();
//                    super.setStatus(i);
                }

                public void setStatus(int i, String string) {
                    System.out.println();
//                    super.setStatus(i, string);
                }


                public ServletResponse getResponse() {
                    return super.getResponse();
                }

                public void setResponse(ServletResponse servletResponse) {
                    super.setResponse(servletResponse);
                }

                public String getCharacterEncoding() {
                    return super.getCharacterEncoding();
                }

                public ServletOutputStream getOutputStream() throws IOException {
                    System.out.println();
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
                    System.out.println();
                    if (printWriter == null) {
                        final String actualEncoding = (encoding == null) ? DEFAULT_ENCODING : encoding;
                        printWriter = new PrintWriter(new OutputStreamWriter(getOutputStream(), actualEncoding));
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
                    System.out.println();
//                    super.setBufferSize(i);
                }

                public int getBufferSize() {
                    System.out.println();
                    return super.getBufferSize();
                }

                public void flushBuffer() throws IOException {
                    System.out.println();
//                    super.flushBuffer();
                }

                public boolean isCommitted() {
                    return super.isCommitted();
                }

                public void reset() {
                    System.out.println();
//                    super.reset();
                }

                public void resetBuffer() {
                    System.out.println();
//                    super.resetBuffer();
                }

                public void setLocale(Locale locale) {
                    System.out.println();
//                    super.setLocale(locale);
                }

                public Locale getLocale() {
                    return super.getLocale();
                }
            };

            // Execute filter
            filterChain.doFilter(servletRequest, responseWrapper);

            // Override Orbeon Forms context
            request.setAttribute("oxf.servlet.context", request.getContextPath() + opsContextPath);

            // Forward to Orbeon Forms for rendering
            getOPSDispatcher(OPS_RENDER_SERVLET_PATH).forward(request, response);
        }
    }

    public void destroy() {
    }

    private RequestDispatcher getOPSDispatcher(String path) throws ServletException {
        final ServletContext opsContext = servletContext.getContext(opsContextPath);
        if (opsContext == null)
            throw new ServletException("Can't find Orbeon Forms context called '" + opsContextPath + "'. Check the '" + OPS_CONTEXT_PATH_PARAMETER_NAME + "' filter initialization parameter and the <Context crossContext=\"true\"/> attribute.");
        final RequestDispatcher dispatcher = opsContext.getRequestDispatcher(path);
        if (dispatcher == null)
            throw new ServletException("Can't find Orbeon Forms request dispatcher.");

        return dispatcher;
    }

    private boolean isOPSResourceRequest(HttpServletRequest request) {
        final String pathInfo = getRequestPathInfo(request);
        return (pathInfo != null && pathInfo.startsWith(opsContextPath + "/"));
    }

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

    public static String getContentTypeMediaType(String contentType) {
        if (contentType == null || contentType.equalsIgnoreCase("content/unknown"))
            return null;
        int semicolumnIndex = contentType.indexOf(";");
        if (semicolumnIndex == -1)
            return contentType;
        return contentType.substring(0, semicolumnIndex).trim();
    }
}
