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
package org.orbeon.oxf.servlet;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.*;
import org.orbeon.oxf.http.Headers;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.webapp.WebAppContext;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.*;
import java.security.Principal;
import java.util.*;

/*
 * Servlet-specific implementation of ExternalContext.
 */
public class ServletExternalContext implements ExternalContext  {

    public static Logger logger = LoggerFactory.createLogger(ServletExternalContext.class);

    public static final String DEFAULT_HEADER_ENCODING = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_DEFAULT  = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_PROPERTY = "oxf.servlet.default-form-charset";

    public static final String HTTP_PAGE_CACHE_HEADERS_DEFAULT      = "Cache-Control: private, max-age=0; Pragma:";
    public static final String HTTP_PAGE_CACHE_HEADERS_PROPERTY     = "oxf.http.page.cache-headers";
    public static final String HTTP_RESOURCE_CACHE_HEADERS_DEFAULT  = "Cache-Control: public; Pragma:";
    public static final String HTTP_RESOURCE_CACHE_HEADERS_PROPERTY = "oxf.http.resource.cache-headers";
    public static final String HTTP_NOCACHE_CACHE_HEADERS_DEFAULT   = "Cache-Control: no-cache, no-store, must-revalidate; Pragma: no-cache; Expires: 0";
    public static final String HTTP_NOCACHE_CACHE_HEADERS_PROPERTY  = "oxf.http.nocache.cache-headers";

    public static final String SESSION_LISTENERS = "oxf.servlet.session-listeners";

    private final static String REWRITING_STRATEGY_DEFAULT = "servlet";

    private static RequestFilter requestFilter;
    static {
        try {
            final Class<? extends RequestFilter> customContextClass
                    = (Class<? extends RequestFilter>) Class.forName("org.orbeon.oxf.servlet.FormRunnerRequestFilter");
            requestFilter = customContextClass.newInstance();
        } catch (Exception e) {
            // Silently ignore as this typically means that we are not in Liferay
        }
    }

    private static String defaultFormCharset;
    private static Map<String, String> pageCacheHeaders;
    private static Map<String, String> resourceCacheHeaders;
    private static Map<String, String> nocacheCacheHeaders;

    public static String getDefaultFormCharset() {
        if (defaultFormCharset == null)
            defaultFormCharset = Properties.instance().getPropertySet().getString(DEFAULT_FORM_CHARSET_PROPERTY, DEFAULT_FORM_CHARSET_DEFAULT);
        return defaultFormCharset;
    }

    private static Map<String, String> decodeCacheString(String name, String defaultValue) {

        final Map<String, String> result = new LinkedHashMap<String, String>();

        final String value = Properties.instance().getPropertySet().getString(name, defaultValue);

        for (final String header : StringUtils.split(value, ';')) {
            final String[] parts = StringUtils.split(header, ':');
            if (parts.length == 2) {
                result.put(parts[0].trim(), parts[1].trim());
            }
        }

        return result;
    }

    private static void setResponseHeaders(HttpServletResponse nativeResponse, Map<String, String> headers) {
        for (final Map.Entry<String, String> entry : headers.entrySet())
            nativeResponse.setHeader(entry.getKey(), entry.getValue());
    }

    private static Map<String, String> getPageCacheHeaders() {
        if (pageCacheHeaders == null)
            pageCacheHeaders = decodeCacheString(HTTP_PAGE_CACHE_HEADERS_PROPERTY, HTTP_PAGE_CACHE_HEADERS_DEFAULT);
        return pageCacheHeaders;
    }

    private static Map<String, String> getResourceCacheHeaders() {
        if (resourceCacheHeaders == null)
            resourceCacheHeaders = decodeCacheString(HTTP_RESOURCE_CACHE_HEADERS_PROPERTY, HTTP_RESOURCE_CACHE_HEADERS_DEFAULT);
        return resourceCacheHeaders;
    }

    private static Map<String, String> getNocacheCacheHeaders() {
        if (nocacheCacheHeaders == null)
            nocacheCacheHeaders = decodeCacheString(HTTP_NOCACHE_CACHE_HEADERS_PROPERTY, HTTP_NOCACHE_CACHE_HEADERS_DEFAULT);
        return nocacheCacheHeaders;
    }

    private class Request implements ExternalContext.Request {

        private String contextPath;

        private Map<String, Object> attributesMap;
        private Map<String, String[]> headerValuesMap;
        private Map<String, Object[]> parameterMap;

        private boolean getParameterMapMultipartFormDataCalled;
        private boolean getInputStreamCalled;
        private String inputStreamCharset;

        private String platformClientContextPath;
        private String applicationClientContextPath;

        public String getContainerType() {
            return "servlet";
        }

        public String getContainerNamespace() {
            return getResponse().getNamespacePrefix();
        }

        public String getContextPath() {
            if (contextPath == null) {
                // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
                // included servlet via the getAttribute method on the request object and their values must be equal to
                // the request URI, context path, servlet path, path info, and query string of the included servlet,
                // respectively."
                // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
                // first servlet in the chain!
                final String dispatcherContext = (String) nativeRequest.getAttribute("javax.servlet.include.context_path");
                if (dispatcherContext != null) {
                    // This ensures we return the included / forwarded servlet's value
                    contextPath = dispatcherContext;
                } else {
                    contextPath = nativeRequest.getContextPath(); // use regular context
                }
            }
            return contextPath;
        }

        public String getPathInfo() {
            return nativeRequest.getPathInfo();
        }

        public String getRemoteAddr() {
            return nativeRequest.getRemoteAddr();
        }

        public synchronized Map<String, Object> getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new InitUtils.RequestMap(nativeRequest);
            }
            return attributesMap;
        }

        public synchronized Map<String, String[]> getHeaderValuesMap() {
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap<String, String[]>();
                for (Enumeration e = nativeRequest.getHeaderNames(); e.hasMoreElements();) {
                    final String name = (String) e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerValuesMap.put(name.toLowerCase(), StringConversions.stringEnumerationToArray(nativeRequest.getHeaders(name)));
                }
            }
            return headerValuesMap;
        }

        public synchronized Map<String, Object[]> getParameterMap() {
            if (parameterMap == null) {
                // Two conditions: file upload ("multipart/form-data") or not
                // NOTE: Regular form POST uses application/x-www-form-urlencoded. In this case, the servlet container
                // exposes parameters with getParameter*() methods (see SRV.4.1.1).
                if (getContentType() != null && getContentType().startsWith("multipart/form-data")) {
                    // Special handling for multipart/form-data

                    if (getInputStreamCalled)
                        throw new OXFException("Cannot call getParameterMap() after getInputStream() when a form was posted with multipart/form-data");

                    // Decode the multipart data
                    parameterMap = Multipart.jGetParameterMapMultipart(pipelineContext, request, DEFAULT_HEADER_ENCODING);

                    // Remember that we were called, so we can display a meaningful exception if getInputStream() is called after this
                    getParameterMapMultipartFormDataCalled = true;
                } else {
                    // Set the input character encoding before getting the stream as this can cause issues with Jetty
                    try {
                        handleInputEncoding();
                    } catch (UnsupportedEncodingException e) {
                        throw new OXFException(e);
                    }
                    // Just use native request parameters
                    parameterMap = new HashMap<String, Object[]>();
                    for (Enumeration<String> e = nativeRequest.getParameterNames(); e.hasMoreElements();) {
                        final String name = e.nextElement();
                        parameterMap.put(name, nativeRequest.getParameterValues(name));
                    }
                }
            }
            return parameterMap;
        }

        public String getAuthType() {
            return nativeRequest.getAuthType();
        }

        public String getRemoteUser() {
            return nativeRequest.getRemoteUser();
        }

        public boolean isSecure() {
            return nativeRequest.isSecure();
        }

        public boolean isUserInRole(String role) {
            return nativeRequest.isUserInRole(role);
        }

        public ExternalContext.Session getSession(boolean create) {
            return ServletExternalContext.this.getSession(create);
        }

        public void sessionInvalidate() {
            HttpSession session = nativeRequest.getSession(false);
            if (session != null)
                session.invalidate();
        }

        public String getCharacterEncoding() {
            if (inputStreamCharset != null)
                return inputStreamCharset;
            else
                return nativeRequest.getCharacterEncoding();
        }

        public int getContentLength() {
            return nativeRequest.getContentLength();
        }

        public String getContentType() {
            return nativeRequest.getContentType();
        }

        public String getServerName() {
            return nativeRequest.getServerName();
        }

        public int getServerPort() {
            return nativeRequest.getServerPort();
        }

        public String getMethod() {
            return nativeRequest.getMethod();
        }

        public String getProtocol() {
            return nativeRequest.getProtocol();
        }

        public String getRemoteHost() {
            return nativeRequest.getRemoteHost();
        }

        public String getScheme() {
            return nativeRequest.getScheme();
        }

        public String getPathTranslated() {
            return nativeRequest.getPathTranslated();
        }

        public String getQueryString() {
            // Use included / forwarded servlet's value
            // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
            // included servlet via the getAttribute method on the request object and their values must be equal to the
            // request URI, context path, servlet path, path info, and query string of the included servlet,
            // respectively."
            // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
            // first servlet in the chain!
            final String dispatcherQueryString = (String) nativeRequest.getAttribute("javax.servlet.include.query_string");
            return (dispatcherQueryString != null) ? dispatcherQueryString : nativeRequest.getQueryString();
        }

        public String getRequestedSessionId() {
            return nativeRequest.getRequestedSessionId();
        }

        public String getRequestPath() {
            return NetUtils.getRequestPathInfo(nativeRequest);
        }

        public String getRequestURI() {
            // Use included / forwarded servlet's value
            // NOTE: Servlet 2.4 spec says: "These attributes [javax.servlet.include.*] are accessible from the
            // included servlet via the getAttribute method on the request object and their values must be equal to the
            // request URI, context path, servlet path, path info, and query string of the included servlet,
            // respectively."
            // NOTE: This is very different from the similarly-named forward attributes, which reflect the values of the
            // first servlet in the chain!
            final String dispatcherRequestURI = (String) nativeRequest.getAttribute("javax.servlet.include.request_uri");
            return (dispatcherRequestURI != null) ? dispatcherRequestURI : nativeRequest.getRequestURI();
        }

        public String getRequestURL() {
            // NOTE: If this is included from a portlet, we may not have a request URL
            final StringBuffer requestUrl = nativeRequest.getRequestURL();
            // TODO: check if we should return null or "" or sth else
            return (requestUrl != null) ? requestUrl.toString() : null;
        }

        public String getServletPath() {
            return nativeRequest.getServletPath();
        }

        public String getClientContextPath(String urlString) {
            // Return depending on whether passed URL is a platform URL or not
            return URLRewriterUtils.isPlatformPath(urlString) ? getPlatformClientContextPath() : getApplicationClientContextPath();
        }

        private String getPlatformClientContextPath() {
            if (platformClientContextPath == null) {
                platformClientContextPath = URLRewriterUtils.getClientContextPath(this, true);
            }
            return platformClientContextPath;
        }

        private String getApplicationClientContextPath() {
            if (applicationClientContextPath == null) {
                applicationClientContextPath = URLRewriterUtils.getClientContextPath(this, false);
            }
            return applicationClientContextPath;
        }

        public Reader getReader() throws IOException {
            return nativeRequest.getReader();
        }

        public InputStream getInputStream() throws IOException {
            if (getParameterMapMultipartFormDataCalled)
                throw new OXFException("Cannot call getInputStream() after getParameterMap() when a form was posted with multipart/form-data");

            // Set the input character encoding before getting the stream as this can cause issues with Jetty
            handleInputEncoding();

            // Remember that we were called, so we can display a meaningful exception if getParameterMap() is called after this
            getInputStreamCalled = true;

            return nativeRequest.getInputStream();
        }

        public Locale getLocale() {
            return nativeRequest.getLocale();
        }

        public Enumeration getLocales() {
            return nativeRequest.getLocales();
        }

        public boolean isRequestedSessionIdValid() {
            return nativeRequest.isRequestedSessionIdValid();
        }

        public Principal getUserPrincipal() {
            return nativeRequest.getUserPrincipal();
        }

        public String getPortletMode() {
            return null;
        }

        public String getWindowState() {
            return null;
        }

        public Object getNativeRequest() {
            return nativeRequest;
        }

        private void handleInputEncoding() throws UnsupportedEncodingException {
            if (!getInputStreamCalled) {
                final String requestCharacterEncoding = nativeRequest.getCharacterEncoding();
                if (requestCharacterEncoding == null) {
                    nativeRequest.setCharacterEncoding(getDefaultFormCharset());
                    inputStreamCharset = getDefaultFormCharset();
                } else  {
                    inputStreamCharset = requestCharacterEncoding;
                }
            }
        }
    }

    private class Response implements ExternalContext.Response {

        private boolean responseCachingDisabled = false;

        private URLRewriter urlRewriter;

        private Response() {
        }

        public void setURLRewriter(URLRewriter urlRewriter) {
            this.urlRewriter = urlRewriter;
        }

        public OutputStream getOutputStream() throws IOException {
            return nativeResponse.getOutputStream();
        }

        public PrintWriter getWriter() throws IOException {
            return nativeResponse.getWriter();
        }

        public boolean isCommitted() {
            return nativeResponse.isCommitted();
        }

        public void reset() {
            nativeResponse.reset();
        }

        public void setContentType(String contentType) {
            nativeResponse.setContentType(contentType);
        }

        public void setStatus(int status) {
            // If anybody ever sets a non-success status code, we disable caching of the output. This covers the
            // following scenario:
            //
            // - request with If-Modified-Since arrives and causes PFC to run
            // - oxf:http-serializer runs and sees pipeline NOT cacheable so reads the input
            // - during execution of pipeline, HttpStatusCodeException is thrown
            // - PFC catches it and calls setStatus()
            // - error, not found, or unauthorized pipeline runs
            // - oxf:http-serializer runs and sees pipeline IS cacheable so sends a 403
            // - client sees wrong result!
            if (! NetUtils.isSuccessCode(status))
                responseCachingDisabled = true;

            nativeResponse.setStatus(status);
        }

        public void setHeader(String name, String value) {
            nativeResponse.setHeader(name, value);
        }

        public void addHeader(String name, String value) {
            nativeResponse.addHeader(name, value);
        }

        public void sendRedirect(String location, boolean isServerSide, boolean isExitPortal) throws IOException {
            // Create URL
            if (isServerSide) {
                // Server-side redirect: do a forward
                final javax.servlet.RequestDispatcher requestDispatcher = nativeRequest.getRequestDispatcher(location);
                // TODO: handle isNoRewrite like in XFormsSubmissionUtils.openOptimizedConnection(): absolute path can then be used to redirect to other servlet context
                try {
                    // Destroy the pipeline context before doing the forward. Nothing significant
                    // should be allowed on "this side" of the forward after the forward return.
                    pipelineContext.destroy(true);
                    // Execute the forward
                    final ForwardServletRequestWrapper wrappedRequest = new ForwardServletRequestWrapper(nativeRequest, location);
                    requestDispatcher.forward(wrappedRequest, nativeResponse);
                } catch (ServletException e) {
                    throw new OXFException(e);
                }
            } else {
                // Client-side redirect: send the redirect to the client
                nativeResponse.sendRedirect(location);
            }
        }

        public void setContentLength(int len) {
            nativeResponse.setContentLength(len);
        }

        public void sendError(int code) throws IOException {
            nativeResponse.sendError(code);
        }

        public String getCharacterEncoding() {
            return nativeResponse.getCharacterEncoding();
        }

        public void setPageCaching(long lastModified) {
            if (responseCachingDisabled) {
                setResponseHeaders(nativeResponse, getNocacheCacheHeaders());
            } else {
                // Get current time and adjust lastModified
                final long now = System.currentTimeMillis();
                if (lastModified <= 0) lastModified = now;

                // Set last-modified
                nativeResponse.setDateHeader(Headers.LastModified(), lastModified);

                // Make sure the client does not load from cache without revalidation
                nativeResponse.setDateHeader("Expires", now);
                setResponseHeaders(nativeResponse, getPageCacheHeaders());
            }
        }

        public void setResourceCaching(long lastModified, long expires) {
            if (responseCachingDisabled) {
                setResponseHeaders(nativeResponse, getNocacheCacheHeaders());
            } else {
                // Get current time and adjust parameters
                final long now = System.currentTimeMillis();
                if (lastModified <= 0) {
                    lastModified = now;
                    expires = now;
                } else if (expires <= 0) {
                    // Regular expiration strategy. We use the HTTP spec heuristic to calculate the "Expires" header value
                    // (10% of the difference between the current time and the last modified time)
                    expires = now + (now - lastModified) / 10;
                }

                // Set caching headers
                nativeResponse.setDateHeader(Headers.LastModified(), lastModified);
                nativeResponse.setDateHeader("Expires", expires);

                setResponseHeaders(nativeResponse, getResourceCacheHeaders());
            }
        }

        public boolean checkIfModifiedSince(long lastModified) {
            if (responseCachingDisabled) {
                return true;
            } else {
                return NetUtils.checkIfModifiedSince(request, lastModified, logger);
            }
        }

        public String getNamespacePrefix() {
            return urlRewriter.getNamespacePrefix();
        }

        public void setTitle(String title) {
            // Nothing to do
        }

        public Object getNativeResponse() {
            return nativeResponse;
        }

        public String rewriteActionURL(String urlString) {
            return urlRewriter.rewriteActionURL(urlString);
        }

        public String rewriteRenderURL(String urlString) {
            return urlRewriter.rewriteRenderURL(urlString);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return urlRewriter.rewriteActionURL(urlString, portletMode, windowState);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return urlRewriter.rewriteRenderURL(urlString, portletMode, windowState);
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return rewriteResourceURL(urlString, generateAbsoluteURL ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE : ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            return urlRewriter.rewriteResourceURL(urlString, rewriteMode);
        }
    }

    private class Session implements ExternalContext.Session {

        private HttpSession httpSession;
        private Map<String, Object> sessionAttributesMap;

        public Session(HttpSession httpSession) {
            this.httpSession = httpSession;
        }

        public long getCreationTime() {
            return httpSession.getCreationTime();
        }

        public String getId() {
            return httpSession.getId();
        }

        public long getLastAccessedTime() {
            return httpSession.getLastAccessedTime();
        }

        public int getMaxInactiveInterval() {
            return httpSession.getMaxInactiveInterval();
        }

        public void invalidate() {
            httpSession.invalidate();
        }

        public boolean isNew() {
            return httpSession.isNew();
        }

        public void setMaxInactiveInterval(int interval) {
            httpSession.setMaxInactiveInterval(interval);
        }

        public Map<String, Object> getAttributesMap() {
            if (sessionAttributesMap == null) {
                sessionAttributesMap = new InitUtils.SessionMap(httpSession);
            }
            return sessionAttributesMap;
        }

        public Map<String, Object> getAttributesMap(int scope) {
            if (scope != Session.APPLICATION_SCOPE)
                throw new OXFException("Invalid session scope scope: only the application scope is allowed in Servlets");
            return getAttributesMap();
        }


        public void addListener(SessionListener sessionListener) {
            SessionListeners listeners = (SessionListeners) httpSession.getAttribute(SESSION_LISTENERS);
            if (listeners == null) {
                listeners = new SessionListeners();
                httpSession.setAttribute(SESSION_LISTENERS, listeners);
            }
            listeners.addListener(sessionListener);
        }

        public void removeListener(SessionListener sessionListener) {
            final SessionListeners listeners = (SessionListeners) httpSession.getAttribute(SESSION_LISTENERS);
            if (listeners != null)
                listeners.removeListener(sessionListener);
        }
    }

    public static class SessionListeners implements Serializable {
        // Store this class instead of the List directly, so we can have a transient member
        private transient List<ExternalContext.Session.SessionListener> listeners;
        public void addListener(ExternalContext.Session.SessionListener sessionListener) {
            if (listeners == null) {
                listeners = new ArrayList<ExternalContext.Session.SessionListener>();
            }
            listeners.add(sessionListener);
        }

        public void removeListener(ExternalContext.Session.SessionListener sessionListener) {
            if (listeners != null)
                listeners.remove(sessionListener);
        }

        public Iterator<ExternalContext.Session.SessionListener> iterator() {
            return (listeners != null) ? listeners.iterator() : Collections.<ExternalContext.Session.SessionListener>emptyList().iterator();
        }
    }

    private Request request;
    private Response response;
    private Session session;

    private WebAppContext webAppContext;
    private PipelineContext pipelineContext;
    private HttpServletRequest nativeRequest;
    private HttpServletResponse nativeResponse;

    public ServletExternalContext(PipelineContext pipelineContext, WebAppContext webAppContext, HttpServletRequest request, HttpServletResponse response) {
        this.webAppContext = webAppContext;
        this.pipelineContext = pipelineContext;

        // Wrap request if needed
        if (requestFilter != null) {
            try {
                this.nativeRequest = requestFilter.amendRequest(request);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            this.nativeRequest = request;
        }

        this.nativeResponse = response;
    }

    public WebAppContext getWebAppContext() {
        return webAppContext;
    }

    public ExternalContext.Request getRequest() {
        if (request == null)
            request = new Request();
        return request;
    }

    public ExternalContext.Response getResponse() {
        if (response == null) {
            response = new Response();

            // NOTE: This whole logic below could be used by ServletExternalContext and PortletExternalContext

            // Check if there is an override of container type. This is currently used by the proxy portlet and by
            // XHTMLToPDF, as both require a specific type of URL rewriting to take place. Using this header means that
            // using a global property is not required anymore.

            // NOTE: use request.getHeaderValuesMap() which normalizes header names to lowercase. This is important if
            // the headers map is generated internally as in that case it might be lowercase already.
            final String override = NetUtils.getHeader(request.getHeaderValuesMap(), Headers.OrbeonClientLower());
            if ("embedded".equals(override) || "portlet".equals(override)) {
                // Always set wsrpEncodeResources to true if the client is a remote portlet
                response.setURLRewriter(new WSRPURLRewriter(URLRewriterUtils.getPathMatchersCallable(), getRequest(), true));
            } else if ("servlet".equals(override)) {
                response.setURLRewriter(new ServletURLRewriter(getRequest()));
            } else {
                // No known override
                if ("portlet2".equals(URLRewriterUtils.getRewritingStrategy("servlet", REWRITING_STRATEGY_DEFAULT)) ||
                            "wsrp".equals(URLRewriterUtils.getRewritingStrategy("servlet", REWRITING_STRATEGY_DEFAULT))) {
                    // Configuration asks to use portlet2/wsrp
                    response.setURLRewriter(new WSRPURLRewriter(URLRewriterUtils.getPathMatchersCallable(), getRequest(), URLRewriterUtils.isWSRPEncodeResources()));
                } else {
                    // Default
                    response.setURLRewriter(new ServletURLRewriter(getRequest()));
                }
            }
        }
        return response;
    }

    public ExternalContext.Session getSession(boolean create) {
        if (session == null) {
            // Force creation if whoever forwarded to us did have a session
            // This is to work around a Tomcat issue whereby a session is newly created in the original servlet, but
            // somehow we can't know about it when the request is forwarded to us.
            if (!create && "true".equals(getRequest().getAttributesMap().get(OrbeonXFormsFilter.RendererHasSessionAttributeName())))
                create = true;

            final HttpSession nativeSession = nativeRequest.getSession(create);
            if (nativeSession != null)
                session = new Session(nativeSession);
        }
        return session;
    }

    public String getStartLoggerString() {
        return getRequest().getRequestPath() + " - Received request";
    }

    public String getEndLoggerString() {
        return getRequest().getRequestPath();
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {

        final ServletContext servletContext = (ServletContext) webAppContext.getNativeContext();

        if (isContextRelative) {
            // Path is relative to the current context root
            final ServletContext slashServletContext = servletContext.getContext("/");
            return new ServletToExternalContextRequestDispatcherWrapper(servletContext.getRequestDispatcher(path), slashServletContext == servletContext);
        } else {
            // Path is relative to the server document root

            final ServletContext otherServletContext = servletContext.getContext(path);
            if (otherServletContext == null)
                return null;

            final ServletContext slashServletContext = servletContext.getContext("/");

            final String modifiedPath;
            final boolean isDefaultContext;
            if (slashServletContext != otherServletContext) {
                // Remove first path element
                modifiedPath = NetUtils.removeFirstPathElement(path);
                if (modifiedPath == null)
                    return null;
                isDefaultContext = false;
            } else {
                // No need to remove first path element because the servlet context is ""
                modifiedPath = path;
                isDefaultContext = true;
            }

            return new ServletToExternalContextRequestDispatcherWrapper(otherServletContext.getRequestDispatcher(modifiedPath), isDefaultContext);
        }
    }
}
