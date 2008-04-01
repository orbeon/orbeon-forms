/**
 *  Copyright (C) 2004 Orbeon, Inc.
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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.RequestContext;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContextToHttpServletResponseWrapper;
import org.orbeon.oxf.externalcontext.ForwardHttpServletRequestWrapper;
import org.orbeon.oxf.externalcontext.ServletToExternalContextRequestDispatcherWrapper;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.RequestGenerator;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.util.URLRewriter;
import org.orbeon.oxf.webapp.ProcessorService;

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
public class ServletExternalContext extends ServletWebAppExternalContext implements ExternalContext  {

    public static Logger logger = LoggerFactory.createLogger(ServletExternalContext.class);

    public static final String DEFAULT_HEADER_ENCODING = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_DEFAULT = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_PROPERTY = "oxf.servlet.default-form-charset";

    public static final String EXTERNALIZE_FORM_VALUES_PREFIX_PROPERTY = "oxf.servlet.externalize-form-values-prefix";
    public static final String SESSION_LISTENERS = "oxf.servlet.session-listeners";
    public static final String APPLICATION_LISTENERS = "oxf.servlet.application-listeners";

    private static final String DEFAULT_FORM_CHARSET = OXFProperties.instance().getPropertySet().getString(DEFAULT_FORM_CHARSET_PROPERTY, DEFAULT_FORM_CHARSET_DEFAULT);

    private class Request implements ExternalContext.Request {

        private String contextPath;

        private Map attributesMap;
        private Map headerMap;
        private Map headerValuesMap;
        private Map sessionMap;
        private Map parameterMap;

        private boolean getParameterMapMultipartFormDataCalled;
        private boolean getInputStreamCalled;
        private String inputStreamCharset;

        public String getContainerType() {
            return "servlet";
        }

        public String getContainerNamespace() {
            return "";
        }

        public String getContextPath() {
            if (contextPath == null) {
                // This attribute allows overriding the context path, for example when Orbeon Forms is deployed as a separate WAR
                final String overriddenServletContext = (String) nativeRequest.getAttribute(OPSXFormsFilter.OPS_SERVLET_CONTEXT_ATTRIBUTE_NAME);
                if (overriddenServletContext == null)
                    contextPath = nativeRequest.getContextPath(); // use regular context
                else
                    contextPath = overriddenServletContext; // use overridden context
            }
            return contextPath;
        }

        public String getPathInfo() {
            return nativeRequest.getPathInfo();
        }

        public String getRemoteAddr() {
            return nativeRequest.getRemoteAddr();
        }

        public synchronized Map getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new InitUtils.RequestMap(nativeRequest);
            }
            return attributesMap;
        }

        public synchronized Map getHeaderMap() {
            if (headerMap == null) {
                headerMap = new HashMap();
                for (Enumeration e = nativeRequest.getHeaderNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerMap.put(name.toLowerCase(), nativeRequest.getHeader(name));
                }
            }
            return headerMap;
        }

        public synchronized Map getHeaderValuesMap() {
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap();
                for (Enumeration e = nativeRequest.getHeaderNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerValuesMap.put(name.toLowerCase(), NetUtils.stringEnumerationToArray(nativeRequest.getHeaders(name)));
                }
            }
            return headerValuesMap;
        }

        public synchronized Map getParameterMap() {
            if (parameterMap == null) {
                // Two conditions: file upload ("multipart/form-data") or not
                if (getContentType() != null && getContentType().startsWith("multipart/form-data")) {
                    // Special handling for multipart/form-data

                    if (getInputStreamCalled)
                        throw new OXFException("Cannot call getParameterMap() after getInputStream() when a form was posted with multipart/form-data");

                    // Decode the multipart data
                    parameterMap = getParameterMapMultipart(pipelineContext, request, DEFAULT_HEADER_ENCODING);

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
                    parameterMap = new HashMap();
                    for (Enumeration e = nativeRequest.getParameterNames(); e.hasMoreElements();) {
                        String name = (String) e.nextElement();
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

        public void sessionInvalidate() {
            HttpSession session = nativeRequest.getSession(false);
            if (session != null)
                session.invalidate();
        }

        public synchronized Map getSessionMap() {
            if (sessionMap == null) {
                HttpSession session = nativeRequest.getSession(false);
                if (session != null)
                    sessionMap = new InitUtils.SessionMap(session);
            }
            return sessionMap;
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
            return nativeRequest.getQueryString();
        }

        public String getRequestedSessionId() {
            return nativeRequest.getRequestedSessionId();
        }

        public String getRequestPath() {
            return NetUtils.getRequestPathInfo(nativeRequest);
        }

        public String getRequestURI() {
            return nativeRequest.getRequestURI();
        }

        public String getRequestURL() {
            return nativeRequest.getRequestURL().toString();
        }

        public String getServletPath() {
            return nativeRequest.getServletPath();
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

        public ServletExternalContext getServletExternalContext() {
            return ServletExternalContext.this;
        }

        public String getPortletMode() {
            return null;
        }

        public String getWindowState() {
            return null;
        }

        public Object getNativeRequest() {
            return ServletExternalContext.this.getNativeRequest();
        }

        private void handleInputEncoding() throws UnsupportedEncodingException {
            if (!getInputStreamCalled) {
                final String requestCharacterEncoding = nativeRequest.getCharacterEncoding();
                if (requestCharacterEncoding == null) {
                    nativeRequest.setCharacterEncoding(DEFAULT_FORM_CHARSET);
                    inputStreamCharset = DEFAULT_FORM_CHARSET;
                } else  {
                    inputStreamCharset = requestCharacterEncoding;
                }
            }
        }
    }

    /**
     * Utility method to decode a multipart/fomr-data stream and return a Map of parameters of type
     * String[] or FileData.
     *
     * NOTE: This is used also by PortletExternalContext. Should probably remove this dependency
     * at some point.
     */
    public static Map getParameterMapMultipart(PipelineContext pipelineContext, final ExternalContext.Request request, String headerEncoding) {

        final Map uploadParameterMap = new HashMap();
        try {
            // Setup commons upload

            // Read properties
            // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
            int maxSize = RequestGenerator.getMaxSizeProperty();
            int maxMemorySize = RequestGenerator.getMaxMemorySizeProperty();

            final DiskFileItemFactory diskFileItemFactory = new DiskFileItemFactory(maxMemorySize, SystemUtils.getTemporaryDirectory());

            final ServletFileUpload upload = new ServletFileUpload(diskFileItemFactory) {
                protected FileItem createItem(Map headers, boolean isFormField) throws FileUploadException {
                    if (isFormField) {
                        // Handle externalized values
                        final String externalizeFormValuesPrefix = OXFProperties.instance().getPropertySet().getString(EXTERNALIZE_FORM_VALUES_PREFIX_PROPERTY);
                        final String fieldName = getFieldName(headers);
                        if (externalizeFormValuesPrefix != null && fieldName.startsWith(externalizeFormValuesPrefix)) {
                            // In this case, we do as if the value content is an uploaded file so that it can be externalized
                            return super.createItem(headers, false);
                        } else {
                            // Just create the FileItem using the default way
                            return super.createItem(headers, isFormField);
                        }
                    } else {
                        // Just create the FileItem using the default way
                        return super.createItem(headers, isFormField);
                    }
                }
            };
            upload.setHeaderEncoding(headerEncoding);
            upload.setSizeMax(maxSize);

            // Add a listener to destroy file items when the pipeline context is destroyed
            pipelineContext.addContextListener(new PipelineContext.ContextListenerAdapter() {
                public void contextDestroyed(boolean success) {
                    if (uploadParameterMap != null) {
                        for (Iterator i = uploadParameterMap.keySet().iterator(); i.hasNext();) {
                            String name = (String) i.next();
                            Object value = uploadParameterMap.get(name);
                            if (value instanceof FileItem) {
                                FileItem fileItem = (FileItem) value;
                                fileItem.delete();
                            }
                        }
                    }
                }
            });

            // Wrap and implement just the required methods for the upload code
            final InputStream inputStream;
            try {
                inputStream = request.getInputStream();
            } catch (IOException e) {
                throw new OXFException(e);
            }

            final RequestContext requestContext = new RequestContext() {

                public int getContentLength() {
                    return request.getContentLength();
                }

                public InputStream getInputStream() {
                    // NOTE: The upload code does not actually check that it doesn't read more than the content-length
                    // sent by the client! Maybe here would be a good place to put an interceptor and make sure we
                    // don't read too much.
                    return new InputStream() {
                        public int read() throws IOException {
                            return inputStream.read();
                        }
                    };
                }

                public String getContentType() {
                    return request.getContentType();
                }

                public String getCharacterEncoding() {
                    return request.getCharacterEncoding();
                }
            };

            // Parse the request and add file information
            try {
                for (Iterator i = upload.parseRequest(requestContext).iterator(); i.hasNext();) {
                    FileItem fileItem = (FileItem) i.next();
                    if (fileItem.isFormField()) {
                        // Simple form filled: add value to existing values, if any
                        NetUtils.addValueToStringArrayMap(uploadParameterMap, fileItem.getFieldName(), fileItem.getString());// FIXME: FORM_ENCODING getString() should use an encoding
                    } else {
                        // It is a file, store the FileItem object
                        uploadParameterMap.put(fileItem.getFieldName(), fileItem);
                    }
                }
            } catch (FileUploadBase.SizeLimitExceededException e) {
                // Should we do something smart so we can use the Presentation
                // Server error page anyway? Right now, this is going to fail
                // miserably with an error.
                throw e;
            } finally {
                // Close the input stream; if we don't nobody does, and if this stream is
                // associated with a temporary file, that file may resist deletion
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }
                }
            }

            return uploadParameterMap;
        } catch (FileUploadException e) {
            throw new OXFException(e);
        }
    }

    private class Response implements ExternalContext.Response {
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
            nativeResponse.setStatus(status);
        }


        public void setHeader(String name, String value) {
            nativeResponse.setHeader(name, value);
        }

        public void addHeader(String name, String value) {
            nativeResponse.addHeader(name, value);
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
            // Create URL
            if (isServerSide) {
                // Server-side redirect: do a forward
                javax.servlet.RequestDispatcher requestDispatcher = nativeRequest.getRequestDispatcher(pathInfo);
                try {
                    // Destroy the pipeline context before doing the forward. Nothing significant
                    // should be allowed on "this side" of the forward after the forward return.
                    pipelineContext.destroy(true);
                    // Execute the forward
                    ForwardHttpServletRequestWrapper wrappedRequest = new ForwardHttpServletRequestWrapper(nativeRequest, pathInfo, parameters);
                    requestDispatcher.forward(wrappedRequest, nativeResponse);
                } catch (ServletException e) {
                    throw new OXFException(e);
                }
            } else {
                // Client-side redirect: send the redirect to the client
                String redirectURLString = NetUtils.pathInfoParametersToPathInfoQueryString(pathInfo, parameters);
                if (redirectURLString.startsWith("/") && !(nativeResponse instanceof ExternalContextToHttpServletResponseWrapper))
                    nativeResponse.sendRedirect(request.getContextPath() + redirectURLString);
                else
                    nativeResponse.sendRedirect(redirectURLString);
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

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {

            // NOTE: Only revalidate = true and allowOverride = true OR revalidate = false and allowOverride = false
            // are supported. Make sure this code is checked before allowing other configurations.
            if (revalidate != allowOverride)
                throw new OXFException("Unsupported flags: revalidate = " + revalidate + ", allowOverride = " + allowOverride);

            // Check a special mode to make all pages appear static, unless the user is logged in (HACK)
            if (allowOverride) {
                Date forceLastModified = OXFProperties.instance().getPropertySet().getDateTime(ProcessorService.HTTP_FORCE_LAST_MODIFIED_PROPERTY);
                if (forceLastModified != null) {
                    // The properties tell that we should override
                    if (request.getRemoteUser() == null) {
                        // If the user is not logged in, just used the specified properties
                        lastModified = forceLastModified.getTime();
                        revalidate = OXFProperties.instance().getPropertySet().getBoolean(ProcessorService.HTTP_FORCE_MUST_REVALIDATE_PROPERTY, false).booleanValue();
                    } else {
                        // If the user is logged in, make sure the correct lastModified is used
                        lastModified = 0;
                        revalidate = true;
                    }
                }
            }

            // Get current time and adjust lastModified
            final long now = System.currentTimeMillis();
            if (lastModified <= 0) lastModified = now;

            // Set last-modified
            nativeResponse.setDateHeader("Last-Modified", lastModified);

            if (revalidate) {
                // Make sure the client does not load from cache
                nativeResponse.setDateHeader("Expires", now);

                // IMPORTANT NOTE #1: We set a public here because with IE 6/7, when BASIC auth is enabled, form
                // fields are not restored upon browser history navigation. Is there a better way?
                nativeResponse.setHeader("Cache-Control", "public");

                // IMPORTANT NOTE #2: We do not set "must-revalidate" because is so with IE 6/7 form fields are not
                // restored upon browser history navigation.
            } else {
                // Regular expiration strategy. We use the HTTP spec heuristic
                // to calculate the "Expires" header value (10% of the
                // difference between the current time and the last modified
                // time)
                nativeResponse.setDateHeader("Expires", now + (now - lastModified) / 10);
                nativeResponse.setHeader("Cache-Control", "public");
            }

            /*
             * HACK: Tomcat adds "Pragma", "Expires" and "Cache-Control"
             * headers when resources are constrained, disabling caching if
             * they are not set. We must re-set them to allow caching.
             */
            nativeResponse.setHeader("Pragma", "");
        }

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
            // Check a special mode to make all pages appear static, unless the user is logged in (HACK)
            if (allowOverride) {
                Date forceLastModified = OXFProperties.instance().getPropertySet().getDateTime(ProcessorService.HTTP_FORCE_LAST_MODIFIED_PROPERTY);
                if (forceLastModified != null) {
                    if (request.getRemoteUser() == null)
                        lastModified = forceLastModified.getTime();
                    else
                        return true;
                }
            }
            // Check whether user is logged-in
            return NetUtils.checkIfModifiedSince(nativeRequest, lastModified);
        }

        public String rewriteActionURL(String urlString) {
            return URLRewriter.rewriteURL(getRequest(), urlString, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteRenderURL(String urlString) {
            return URLRewriter.rewriteURL(getRequest(), urlString, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return URLRewriter.rewriteURL(getRequest(), urlString, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return URLRewriter.rewriteURL(getRequest(), urlString, REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return URLRewriter.rewriteURL(getRequest(), urlString, generateAbsoluteURL ? REWRITE_MODE_ABSOLUTE : REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            return URLRewriter.rewriteURL(getRequest(), urlString, rewriteMode);
        }

        public String getNamespacePrefix() {
            return "";
        }

        public void setTitle(String title) {
            // Nothing to do
        }

        public ServletExternalContext getServletExternalContext() {
            return ServletExternalContext.this;
        }

        public Object getNativeResponse() {
            return ServletExternalContext.this.getNativeResponse();
        }
    }

    private class Session implements ExternalContext.Session {

        private HttpSession httpSession;
        private Map sessionAttributesMap;

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

        public Map getAttributesMap() {
            if (sessionAttributesMap == null) {
                sessionAttributesMap = new InitUtils.SessionMap(httpSession);
            }
            return sessionAttributesMap;
        }

        public Map getAttributesMap(int scope) {
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

    private class Application implements ExternalContext.Application {
        private ServletContext servletContext;

        public Application(ServletContext servletContext) {
          this.servletContext = servletContext;
        }

        public void addListener(ApplicationListener applicationListener) {

            ApplicationListeners listeners = (ApplicationListeners) servletContext.getAttribute(APPLICATION_LISTENERS);
            if (listeners == null) {
                listeners = new ApplicationListeners();
                servletContext.setAttribute(APPLICATION_LISTENERS, listeners);
            }
            listeners.addListener(applicationListener);
        }

        public void removeListener(ApplicationListener applicationListener) {
            final ApplicationListeners listeners = (ApplicationListeners) servletContext.getAttribute(APPLICATION_LISTENERS);
            if (listeners != null)
                listeners.removeListener(applicationListener);
        }
    }

    public static class SessionListeners implements Serializable {
        // Store this class instead of the List directly, so we can have a transient member
        private transient List listeners;
        public void addListener(ExternalContext.Session.SessionListener sessionListener) {
            if (listeners == null) {
                listeners = new ArrayList();
            }
            listeners.add(sessionListener);
        }

        public void removeListener(ExternalContext.Session.SessionListener sessionListener) {
            if (listeners != null)
                listeners.remove(sessionListener);
        }

        public Iterator iterator() {
            return (listeners != null) ? listeners.iterator() : Collections.EMPTY_LIST.iterator();
        }
    }

    public static class ApplicationListeners implements Serializable {
        private transient List listeners;
        public void addListener(ExternalContext.Application.ApplicationListener applicationListener) {
            if (listeners == null) {
                listeners = new ArrayList();
            }
            listeners.add(applicationListener);
        }

        public void removeListener(ExternalContext.Application.ApplicationListener applicationListener) {
            if (listeners != null)
                listeners.remove(applicationListener);
        }

        public Iterator iterator() {
            return (listeners != null) ? listeners.iterator() : Collections.EMPTY_LIST.iterator();
        }
    }

    private Request request;
    private Response response;
    private Session session;
    private Application application;

    private PipelineContext pipelineContext;
    private HttpServletRequest nativeRequest;
    private HttpServletResponse nativeResponse;

    public ServletExternalContext(ServletContext servletContext, PipelineContext pipelineContext, Map initAttributesMap, HttpServletRequest request, HttpServletResponse response) {
        super(servletContext, initAttributesMap);

        this.pipelineContext = pipelineContext;
        this.nativeRequest = request;
        this.nativeResponse = response;
    }

    public Object getNativeRequest() {
        return nativeRequest;
    }

    public Object getNativeResponse() {
        return nativeResponse;
    }

    public Object getNativeSession(boolean create) {
        return nativeRequest.getSession(create);
    }

    public ExternalContext.Request getRequest() {
        if (request == null)
            request = new Request();
        return request;
    }

    public ExternalContext.Response getResponse() {
        if (response == null)
            response = new Response();
        return response;
    }

    public ExternalContext.Session getSession(boolean create) {
        if (session == null) {
            // Force creation if whoever forwarded to us did have a session
            // This is to work around a Tomcat issue whereby a session is newly created in the original servlet, but
            // somehow we can't know about it when the request is forwarded to us.
            if (!create && "true".equals(getRequest().getAttributesMap().get(OPSXFormsFilter.OPS_XFORMS_RENDERER_HAS_SESSION_ATTRIBUTE_NAME)))
                create = true;

            final HttpSession nativeSession = nativeRequest.getSession(create);
            if (nativeSession != null)
                session = new Session(nativeSession);
        }
        return session;
    }

    public ExternalContext.Application getApplication() {
        if (application == null)
                application = new Application(super.servletContext);

        return application;
    }
    public String getStartLoggerString() {
        return getRequest().getRequestPath() + " - Received request";
    }

    public String getEndLoggerString() {
        return getRequest().getRequestPath();
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        return new ServletToExternalContextRequestDispatcherWrapper(servletContext.getNamedDispatcher(name));
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        return new ServletToExternalContextRequestDispatcherWrapper(servletContext.getRequestDispatcher(path));
    }
}
