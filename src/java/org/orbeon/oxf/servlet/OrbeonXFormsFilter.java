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
package org.orbeon.oxf.servlet;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

/**
 * This filter allows forwarding requests from your web app to an separate Orbeon Forms context.
 *
 * This filter must remain very simple and have as little dependencies as possible, because it must be easily deployable
 * into any web application.
 */
public class OrbeonXFormsFilter implements Filter {

    public static final String RENDERER_DEPLOYMENT_ATTRIBUTE_NAME = "oxf.xforms.renderer.deployment";
    public static final String RENDERER_BASE_URI_ATTRIBUTE_NAME = "oxf.xforms.renderer.base-uri";
    public static final String RENDERER_DOCUMENT_ATTRIBUTE_NAME = "oxf.xforms.renderer.document";
    public static final String RENDERER_CONTENT_TYPE_ATTRIBUTE_NAME = "oxf.xforms.renderer.content-type";
    public static final String RENDERER_HAS_SESSION_ATTRIBUTE_NAME = "oxf.xforms.renderer.has-session";

    public static final String RENDERER_PATH = "/xforms-renderer";

    private static final String RENDERER_CONTEXT_PARAMETER_NAME = "oxf.xforms.renderer.context";
    private static final String DEFAULT_ENCODING = "ISO-8859-1"; // must be this per Servlet spec

    private ServletContext servletContext;
    private String orbeonContextPath;

    public void init(FilterConfig filterConfig) throws ServletException {
        servletContext = filterConfig.getServletContext();
        orbeonContextPath = filterConfig.getInitParameter(RENDERER_CONTEXT_PARAMETER_NAME);

        // TODO: check orbeonContextPath format: starts with /, doesn't end with one, etc.
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        final HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
        final HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;

        final String requestPath = getRequestPath(httpRequest);

        // Set whether deployment is integrated or separate
        // NOTE: DO this also for resources, so that e.g. /xforms-server, /xforms-server-submit can handle URLs properly
        httpRequest.setAttribute(RENDERER_DEPLOYMENT_ATTRIBUTE_NAME, (getOrbeonContext() == servletContext) ? "integrated" : "separate");

        if (isOrbeonResourceRequest(requestPath)) {
            // Directly forward all requests meant for Orbeon Forms resources (including /xforms-server)
            final String subRequestPath = requestPath.substring(orbeonContextPath.length());
            getOrbeonDispatcher(subRequestPath).forward(httpRequest, httpResponse);
        } else {
            // Forward the request to the Orbeon Forms renderer
            final FilterRequestWrapper requestWrapper = new FilterRequestWrapper(httpRequest);
            final FilterResponseWrapper responseWrapper = new FilterResponseWrapper(httpResponse);

            // Execute filter
            filterChain.doFilter(requestWrapper, responseWrapper);

            // Set document if not present AND output was intercepted
            final boolean isEmptyContent;
            if (httpRequest.getAttribute(RENDERER_DOCUMENT_ATTRIBUTE_NAME) == null) {
                final String content = responseWrapper.getContent();
                if (content != null) {
                    httpRequest.setAttribute(RENDERER_DOCUMENT_ATTRIBUTE_NAME, content);
                }
                isEmptyContent = isBlank(content);
            } else {
                // Assume content is not blank
                isEmptyContent = false;
            }

            // Tell whether there is a session
            httpRequest.setAttribute(RENDERER_HAS_SESSION_ATTRIBUTE_NAME, Boolean.toString(httpRequest.getSession(false) != null));

            // Provide media type if available
            if (responseWrapper.getMediaType() != null)
                httpRequest.setAttribute(RENDERER_CONTENT_TYPE_ATTRIBUTE_NAME, responseWrapper.getMediaType());

            // Set base URI
            httpRequest.setAttribute(RENDERER_BASE_URI_ATTRIBUTE_NAME, requestPath);

            // Forward to Orbeon Forms for rendering only of there is content to be rendered, otherwise just return and
            // let the filterChain finish its life naturally, assuming that when sendRedirect is used, no content is
            // available in the response object
            if (!isEmptyContent) {
                // The request wrapper provides an empty request body if the filtered resource already attempted to
                // read the body.
                final HandleBodyOrbeonRequestWrapper orbeonRequestWrapper
                        = new HandleBodyOrbeonRequestWrapper(httpRequest, requestWrapper.isRequestBodyRead());

                // Forward
                getOrbeonDispatcher(RENDERER_PATH).forward(orbeonRequestWrapper, httpResponse);
            }
        }
    }

    // NOTE: this method copied from Apache StringUtils 2.3 as we don't want a dependency on the JAR
    // http://www.apache.org/licenses/LICENSE-2.0
    public static boolean isBlank(String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return true;
        }
        for (int i = 0; i < strLen; i++) {
            if ((!Character.isWhitespace(str.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    public void destroy() {
    }

    private ServletContext getOrbeonContext() throws ServletException {
        final ServletContext orbeonContext = (orbeonContextPath != null) ? servletContext.getContext(orbeonContextPath) : servletContext;
        if (orbeonContext  == null)
            throw new ServletException("Can't find Orbeon Forms context called '" + orbeonContextPath + "'. Check the '" + RENDERER_CONTEXT_PARAMETER_NAME + "' filter initialization parameter and the <Context crossContext=\"true\"/> attribute.");

        return orbeonContext ;
    }

    private RequestDispatcher getOrbeonDispatcher(String path) throws ServletException {
        final RequestDispatcher dispatcher = getOrbeonContext().getRequestDispatcher(path);
        if (dispatcher == null)
            throw new ServletException("Can't find Orbeon Forms request dispatcher.");

        return dispatcher;
    }

    private boolean isOrbeonResourceRequest(String requestPath) {
        return orbeonContextPath != null && requestPath != null && requestPath.startsWith(orbeonContextPath + "/");

    }

    // NOTE: This is borrowed from NetUtils but we don't want the dependency
    private static String getRequestPath(HttpServletRequest request) {

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
        int semicolonIndex = contentType.indexOf(";");
        if (semicolonIndex == -1)
            return null;
        int charsetIndex = contentType.indexOf("charset=", semicolonIndex);
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
        int semicolonIndex = contentType.indexOf(";");
        if (semicolonIndex == -1)
            return contentType;
        return contentType.substring(0, semicolonIndex).trim();
    }

    private static class FilterRequestWrapper extends HttpServletRequestWrapper {

        private boolean requestBodyRead;

        public FilterRequestWrapper(HttpServletRequest httpServletRequest) {
            super(httpServletRequest);

        }

        @Override
        public BufferedReader getReader() throws IOException {
            // If the filtered resource attempts to read the request body, we will forward an empty body
            this.requestBodyRead = true;
            return super.getReader();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            // If the filtered resource attempts to read the request body, we will forward an empty body
            this.requestBodyRead = true;
            return super.getInputStream();
        }

        public boolean isRequestBodyRead() {
            return requestBodyRead;
        }
    }

    private static class HandleBodyOrbeonRequestWrapper extends FilterRequestWrapper {

        // If necessary, return an empty stream for the body, because the body might be read by the JSP and we don't want
        // Orbeon Forms to attempt to read a closed stream.
        private final boolean forceEmptyBody;

        private HandleBodyOrbeonRequestWrapper(HttpServletRequest httpServletRequest, boolean forceEmptyBody) {
            super(httpServletRequest);
            this.forceEmptyBody = forceEmptyBody;
        }

        @Override
        public String getContentType() {
            return forceEmptyBody ? null : super.getContentType();
        }

        @Override
        public int getContentLength() {
            return forceEmptyBody ? 0 : super.getContentLength();
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return forceEmptyBody ? new ServletInputStream() {
                @Override
                public int read() throws IOException {
                    return -1;
                }
            } : super.getInputStream();
        }

        @Override
        public BufferedReader getReader() throws IOException {
            return forceEmptyBody ? new BufferedReader(new Reader() {
                @Override
                public int read(char[] cbuf, int off, int len) throws IOException {
                    return 0;
                }

                @Override
                public void close() throws IOException {
                }
            }) : super.getReader();
        }

        // Filter all headers starting with "if-" so that Orbeon Forms doesn't attempt caching based on those.

        private Enumeration<String> headerNames;

        @Override
        public String getHeader(String s) {
            // Filter conditional get headers so that we always get content
            if (s.toLowerCase().startsWith("if-") )
                return null;
            else
                return super.getHeader(s);
        }

        @Override
        public Enumeration getHeaders(String s) {
            // Filter conditional get headers so that we always get content
            if (s.toLowerCase().startsWith("if-"))
                return null;
            else
                return super.getHeaders(s);
        }

        @Override
        public Enumeration getHeaderNames() {
            if (headerNames == null) {
                // Filter conditional get headers so that we always get content
                final List<String> newHeaderNames = new ArrayList<String>();
                for (Enumeration e = super.getHeaderNames(); e.hasMoreElements();) {
                    final String currentName = (String) e.nextElement();
                    if (!currentName.toLowerCase().startsWith("if-"))
                        newHeaderNames.add(currentName);
                }
                headerNames = Collections.enumeration(newHeaderNames);

            }
            return headerNames;
        }

        @Override
        public long getDateHeader(String s) {
            // Filter conditional get headers so that we always get content
            if (s.toLowerCase().startsWith("if-"))
                return -1;
            else
                return super.getDateHeader(s);
        }
    }

    private static class FilterResponseWrapper extends HttpServletResponseWrapper {

        public FilterResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        private ByteArrayOutputStream byteArrayOutputStream;
        private ServletOutputStream servletOutputStream;

        private StringWriter stringWriter;
        private PrintWriter printWriter;


        private String encoding;
        private String mediatype;

        @Override
        public void addCookie(Cookie cookie) {
            super.addCookie(cookie);
        }

        @Override
        public boolean containsHeader(String string) {
            return super.containsHeader(string);
        }

        @Override
        public String encodeURL(String string) {
            return super.encodeURL(string);
        }

        @Override
        public String encodeRedirectURL(String string) {
            return super.encodeRedirectURL(string);
        }

        @Override
        public String encodeUrl(String string) {
            return super.encodeUrl(string);
        }

        @Override
        public String encodeRedirectUrl(String string) {
            return super.encodeRedirectUrl(string);
        }

        @Override
        public void sendError(int i, String string) throws IOException {
            // TODO
        }

        @Override
        public void sendError(int i) throws IOException {
            // TODO
        }

        @Override
        public void sendRedirect(String string) throws IOException {
            super.sendRedirect(string); 
        }

        @Override
        public void setDateHeader(String string, long l) {
            // TODO
        }

        @Override
        public void addDateHeader(String string, long l) {
            // TODO
        }

        @Override
        public void setHeader(String string, String string1) {
            // TODO
        }

        @Override
        public void addHeader(String string, String string1) {
            // TODO
        }

        @Override
        public void setIntHeader(String string, int i) {
            // TODO
        }

        @Override
        public void addIntHeader(String string, int i) {

            // TODO
        }

        @Override
        public void setStatus(int i) {
            // TODO
        }

        @Override
        public void setStatus(int i, String string) {
            // TODO
        }

        @Override
        public ServletResponse getResponse() {
            return super.getResponse();
        }

        @Override
        public void setResponse(ServletResponse servletResponse) {
            super.setResponse(servletResponse);
        }

        @Override
        public String getCharacterEncoding() {
            // TODO: we don't support setLocale()
            return (encoding == null) ? DEFAULT_ENCODING : encoding;
        }

        public String getMediaType() {
            return mediatype;
        }

        @Override
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

        @Override
        public PrintWriter getWriter() throws IOException {
            if (printWriter == null) {
                stringWriter = new StringWriter();
                printWriter = new PrintWriter(stringWriter);
            }
            return printWriter;
        }

        @Override
        public void setContentLength(int i) {
            // NOP
        }

        @Override
        public void setContentType(String contentType) {
            this.encoding = getContentTypeCharset(contentType);
            this.mediatype = getContentTypeMediaType(contentType);
        }

        @Override
        public void setBufferSize(int i) {
            // NOP
        }

        @Override
        public int getBufferSize() {
            // We have a buffer, but it is infinite
            return Integer.MAX_VALUE;
        }

        @Override
        public void flushBuffer() throws IOException {
            // NOPE
        }

        @Override
        public boolean isCommitted() {
            // We buffer everything so return false all the time
            return false;
        }

        @Override
        public void reset() {
            resetBuffer();
        }

        @Override
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

        @Override
        public void setLocale(Locale locale) {
            // TODO
        }

        @Override
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