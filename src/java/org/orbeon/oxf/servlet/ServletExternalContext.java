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

import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadBase;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.log4j.Logger;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.InitUtils;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.generator.RequestGenerator;
import org.orbeon.oxf.resources.OXFProperties;
import org.orbeon.oxf.util.HttpServletRequestStub;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.SystemUtils;
import org.orbeon.oxf.webapp.ProcessorService;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.net.URL;
import java.security.Principal;
import java.util.*;

/*
 * Servlet-specific implementation of ExternalContext.
 */
public class ServletExternalContext extends ServletWebAppExternalContext implements ExternalContext  {

    static Logger logger = LoggerFactory.createLogger(ServletExternalContext.class);

    public static final String DEFAULT_FORM_CHARSET = "utf-8";
    public static final String DEFAULT_FORM_CHARSET_PROPERTY = "oxf.servlet.default-form-charset";

    private class Request implements ExternalContext.Request {

        private Map attributesMap;
        private Map headerMap;
        private Map headerValuesMap;
        private Map sessionMap;
        private Map parameterMap;
        private ServletInputStreamRepeater repeater;

        private boolean getParameterMapMultipartFormDataCalled;
        private boolean getInputStreamCalled;

        public String getContainerType() {
            return "servlet";
        }

        public String getContextPath() {
            return nativeRequest.getContextPath();
        }

        public String getPathInfo() {
            return NetUtils.getRequestPathInfo(nativeRequest);
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
                    headerMap.put(name, nativeRequest.getHeader(name));
                }
            }
            return headerMap;
        }

        public synchronized Map getHeaderValuesMap() {
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap();
                for (Enumeration e = nativeRequest.getHeaderNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    headerValuesMap.put(name, NetUtils.stringEnumerationToArray(nativeRequest.getHeaders(name)));
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

                    // If needed, instanciate the repeater that may take care of temporarily storing the input stream
                    boolean enableInputStreamSaving = false;// no longer do this; this was required for upload and portlets, now we do it differently

                    ServletInputStreamRepeater localRepeater = null;
                    try {
                        if (enableInputStreamSaving)
                            localRepeater = new ServletInputStreamRepeater(request);

                        // Decode the multipart data
                        parameterMap = getParameterMapMultipart(pipelineContext, request, localRepeater);

                        if (enableInputStreamSaving)
                            repeater = localRepeater;
                    } catch (IOException e) {
                        throw new OXFException(e);
                    }

                    // Remember that we were called, so we can display a meaningful exception if getInputStream() is called after this
                    getParameterMapMultipartFormDataCalled = true;
                } else {
                    // Try to set an appropriate encoding for forms and parameters
                    try {
//                        String acceptCharset = nativeRequest.getHeader("accept-charset");
//                        if (acceptCharset != null && acceptCharset.toLowerCase().indexOf("utf-8") != -1)
//                            nativeRequest.setCharacterEncoding("utf-8");
                        String formCharset = OXFProperties.instance().getPropertySet().getString(DEFAULT_FORM_CHARSET_PROPERTY);
                        if (formCharset == null)
                            formCharset = DEFAULT_FORM_CHARSET;
                        nativeRequest.setCharacterEncoding(formCharset);
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
            if (repeater != null)
                return repeater.getCharacterEncoding();
            else
                return nativeRequest.getCharacterEncoding();
        }

        public int getContentLength() {
            if (repeater != null)
                return repeater.getContentLength();
            else
                return nativeRequest.getContentLength();
        }

        public String getContentType() {
            if (repeater != null)
                return repeater.getContentType();
            else
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
            if (repeater != null) {
                return repeater.getSavedInputStream();
            } else {
                if (getParameterMapMultipartFormDataCalled)
                    throw new OXFException("Cannot call getInputStream() after getParameterMap() when a form was posted with multipart/form-data");
                // Remember that we were called, so we can display a meaningful exception if getParameterMap() is called after this
                getInputStreamCalled = true;
                return nativeRequest.getInputStream();
            }
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
    }

    /**
     * Utility method to decode a multipart/fomr-data stream and return a Map of parameters of type
     * String[] or FileData.
     *
     * NOTE: This is used also by PortletExternalContext. Should probably remove this dependency
     * at some point.
     */
    public static Map getParameterMapMultipart(PipelineContext pipelineContext, final ExternalContext.Request request, final ServletInputStreamRepeater repeater) {

        final Map uploadParameterMap = new HashMap();
        try {
            // Setup commons upload
            DiskFileUpload upload = new DiskFileUpload();

            // Read properties
            // NOTE: We use properties scoped in the Request generator for historical reasons. Not too good.
            int maxSize = RequestGenerator.getMaxSizeProperty();
            int maxMemorySize = RequestGenerator.getMaxMemorySizeProperty();

            if (repeater != null)
                repeater.setMaxMemorySize(maxMemorySize);

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
                    // Also cleanup repeater
                    if (repeater != null)
                        repeater.delete();
                }
            });

            // Wrap and implement just the required methods for the upload code
            final InputStream inputStream;
            try {
                inputStream = (repeater != null) ? repeater.getInputStream() : request.getInputStream();
            } catch (IOException e) {
                throw new OXFException(e);
            }

            HttpServletRequest wrapper = new HttpServletRequestStub() {

                public String getHeader(String s) {
                    if ("content-type".equalsIgnoreCase(s)) {
                        if (repeater != null)
                            return repeater.getContentType();
                        else
                            return request.getContentType();
                    }
                    return null;
                }

                public int getContentLength() {
                    if (repeater != null)
                        return repeater.getContentLength();
                    else
                        return request.getContentLength();
                }

                public ServletInputStream getInputStream() {
                    // NOTE: The upload code does not actually check that it
                    // doesn't read more than the content-length sent by the client!
                    // Maybe here would be a good place to put an interceptor and
                    // make sure we don't read too much.
                    return new ServletInputStream() {
                        public int read() throws IOException {
                            return inputStream.read();
                        }
                    };
                }
            };

            // Parse the request and add file information
            try {
                for (Iterator i = upload.parseRequest(wrapper, maxMemorySize, maxSize, SystemUtils.getTemporaryDirectory().getPath()).iterator(); i.hasNext();) {
                    FileItem fileItem = (FileItem) i.next();
                    if (fileItem.isFormField()) {
                        // Simple form filled: add value to existing values, if any
                        NetUtils.addValueToStringArrayMap(uploadParameterMap, fileItem.getFieldName(), fileItem.getString());// FIXME: FORM_ENCODING getString() should use an encoding
                    } else {
                        // It is a file, store the FileItem object
                        uploadParameterMap.put(fileItem.getFieldName(), fileItem);
                    }
                }
                // Make sure repeater finishes its job
                if (repeater != null) {
                    repeater.finishReading();
                    logger.debug("Finished reading InputStream repeater (announced content length "
                            + repeater.getContentLength() + " bytes, actual read "
                            + repeater.getActualByteCount() + " bytes)");
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
                    // should allowed on "this side" of the forward after the forward return.
                    pipelineContext.destroy(true);
                    // Execute the forward
                    RequestWrapper wrappedRequest = new RequestWrapper(nativeRequest, parameters);
                    requestDispatcher.forward(wrappedRequest, nativeResponse);
                } catch (ServletException e) {
                    throw new OXFException(e);
                }
            } else {
                // Client-side redirect: send the redirect to the client
                String redirectURLString = NetUtils.pathInfoParametersToRelativeURL(pathInfo, parameters);
                if (redirectURLString.startsWith("/"))
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
            long now = System.currentTimeMillis();
            if (lastModified <= 0) lastModified = now;

            // Set last-modified
            nativeResponse.setDateHeader("Last-Modified", lastModified);

            if (revalidate) {
                // Try to force revalidation from the client
                nativeResponse.setDateHeader("Expires", now);
                nativeResponse.setHeader("Cache-Control", "must-revalidate");
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
            return rewriteURL(urlString, false);
        }

        public String rewriteRenderURL(String urlString) {
            return rewriteURL(urlString, false);
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return rewriteURL(urlString, generateAbsoluteURL);
        }

        private String rewriteURL(String urlString, boolean generateAbsoluteURL) {
            // Case where a protocol is specified: the URL is left untouched
            // We consider that a protocol consists only of ASCII letters
            if (NetUtils.urlHasProtocol(urlString))
                return urlString;

            try {
                ExternalContext.Request request = getRequest();

                URL absoluteBaseURL = generateAbsoluteURL ? new URL(new URL(request.getRequestURL()), "/") : null;
                String baseURLString = generateAbsoluteURL ? absoluteBaseURL.toExternalForm() : "";
                if (baseURLString.endsWith("/"))
                    baseURLString = baseURLString.substring(0, baseURLString.length() - 1);

                // Return absolute path URI with query string and fragment identifier if needed
                if (urlString.startsWith("?")) {
                    // This is a special case that appears to be implemented
                    // in Web browsers as a convenience. Users may use it.
                    return baseURLString + request.getContextPath() + request.getRequestPath() + urlString;
                } else if (!urlString.startsWith("/") && !generateAbsoluteURL && !"".equals(urlString)) {
                    // Don't change the URL if it is a relative path and we don't force absolute URLs
                    return urlString;
                } else {
                    // Regular case, parse the URL
                    URL baseURLWithPath = new URL("http", "example.org", request.getRequestPath());
                    URL u = new URL(baseURLWithPath, urlString);

                    String tempResult = u.getFile();
                    if (u.getRef() != null)
                        tempResult += "#" + u.getRef();
                    return baseURLString + request.getContextPath() + tempResult;
                }
            } catch (Exception e) {
                throw new OXFException(e);
            }
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
    }

    private Request request;
    private Response response;
    private Session session;

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
            HttpSession nativeSession = nativeRequest.getSession(create);
            if (nativeSession != null)
                session = new Session(nativeSession);
        }
        return session;
    }

    public String getStartLoggerString() {
        return getRequest().getPathInfo() + " - Received request";
    }

    public String getEndLoggerString() {
        return getRequest().getPathInfo();
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        return new RequestDispatcherWrapper(servletContext.getNamedDispatcher(name));
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        return new RequestDispatcherWrapper(servletContext.getRequestDispatcher(path));
    }

    /*
     * Wrap a Servlet RequestDispatcher.
     */
    private static class RequestDispatcherWrapper implements ExternalContext.RequestDispatcher {

        private javax.servlet.RequestDispatcher dispatcher;

        public RequestDispatcherWrapper(javax.servlet.RequestDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public void forward(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
            try {
                // FIXME: This is incorrect, must wrap request and responses
                ServletExternalContext servletExternalContext = ((Request) request).getServletExternalContext();
                dispatcher.forward(servletExternalContext.nativeRequest, servletExternalContext.nativeResponse);
            } catch (ServletException e) {
                throw new OXFException(e);
            }
        }

        public void include(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
            try {
                // FIXME: This is incorrect, must wrap request and responses
                ServletExternalContext servletExternalContext = ((Request) request).getServletExternalContext();
                dispatcher.include(servletExternalContext.nativeRequest, servletExternalContext.nativeResponse);
            } catch (ServletException e) {
                throw new OXFException(e);
            }
        }
    }

    private static class RequestWrapper extends HttpServletRequestWrapper {

        private Map parameters;

        public RequestWrapper(HttpServletRequest httpServletRequest, Map parameters) {
            super(httpServletRequest);
            this.parameters = parameters;
        }

        public Map getParameterMap() {
            return parameters;
        }

        public Enumeration getParameterNames() {
            return new Vector(parameters.keySet()).elements();
        }

        public String[] getParameterValues(String s) {
            return (String[]) parameters.get(s);
        }

        public String getParameter(String s) {
            return (String) parameters.get(s);
        }

        public String getContentType() {
            // We never have a body on redirect
            return null;
        }

        public int getContentLength() {
            // We never have a body on redirect
            return -1;
        }

        private ServletInputStream servletInputStream;
        private BufferedReader bufferedReader;

        public ServletInputStream getInputStream() throws IOException {
            // We never have a body on redirect (returning something because the spec doesn't say the result can be null)
            if (servletInputStream == null) {
                final InputStream is = new ByteArrayInputStream(new byte[] {});
                servletInputStream = new ServletInputStream() {
                    public int read() throws IOException {
                        return is.read();
                    }
                };
            }
            return servletInputStream;
        }

        public BufferedReader getReader() throws IOException {
            // We never have a body on redirect (returning something because the spec doesn't say the result can be null)
            if (bufferedReader == null) {
                bufferedReader = new BufferedReader(new StringReader(""));
            }
            return bufferedReader;
        }

        public String getMethod() {
            return "GET";
        }

        private Map filterHeaders = new HashMap();
        {
            filterHeaders.put("content-length", "");
            filterHeaders.put("content-type", "");
            //filterHeaders.put("referer", "");
        }

        private List headerNamesList;
        private Enumeration headerNames;

        public Enumeration getHeaderNames() {
            if (headerNames == null) {
                headerNamesList = Collections.list(super.getHeaderNames());

                // Remove headers associated with body
                for (Iterator i = filterHeaders.keySet().iterator(); i.hasNext();) {
                    headerNamesList.remove(i.next());
                }

                headerNames = Collections.enumeration(headerNamesList);
            }

            return headerNames;
        }

        public String getHeader(String s) {
            if (filterHeaders.get(s) != null)
                return null;
            return super.getHeader(s);
        }

        public Enumeration getHeaders(String s) {
            if (filterHeaders.get(s) != null)
                return null;
            return super.getHeaders(s);
        }

        public long getDateHeader(String s) {
            if (filterHeaders.get(s) != null)
                return -1;
            return super.getDateHeader(s);
        }

        public int getIntHeader(String s) {
            if (filterHeaders.get(s) != null)
                return -1;
            return super.getIntHeader(s);
        }
    }
}
