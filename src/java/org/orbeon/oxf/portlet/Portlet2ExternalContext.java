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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.PortletToExternalContextRequestDispatcherWrapper;
import org.orbeon.oxf.externalcontext.URLRewriter;
import org.orbeon.oxf.externalcontext.WSRPURLRewriter;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.*;

import javax.portlet.*;
import java.io.*;
import java.security.Principal;
import java.util.*;

/*
 * Portlet-specific implementation of ExternalContext.
 */
public class Portlet2ExternalContext extends PortletWebAppExternalContext implements ExternalContext {

    private static final String OPS_CONTEXT_NAMESPACE_KEY = "org.orbeon.ops.portlet.namespace";

    private static RequestFilter requestFilter;
    static {
        try {
            final Class<? extends RequestFilter> customContextClass
                    = (Class<? extends RequestFilter>) Class.forName("org.orbeon.oxf.portlet.FormRunnerRequestFilter");
            requestFilter = customContextClass.newInstance();
        } catch (Exception e) {
            // Silently ignore as this typically means that we are not in Liferay
        }
    }

    private ExternalContext.Request request;
    private ExternalContext.Response response;
    private ExternalContext.Session session;
    private ExternalContext.Application application;

    private PipelineContext pipelineContext;
    private PortletRequest portletRequest;
    private ClientDataRequest clientDataRequest;
    private MimeResponse mimeResponse;

    Portlet2ExternalContext(PipelineContext pipelineContext, PortletContext portletContext, Map<String, String> initAttributesMap, PortletRequest portletRequest) {
        this(portletContext, initAttributesMap);
        this.pipelineContext = pipelineContext;

        // Wrap request if needed
        if (requestFilter != null) {
            try {
                this.portletRequest = requestFilter.amendRequest(portletRequest);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        } else {
            this.portletRequest = portletRequest;
        }

        if (portletRequest instanceof ClientDataRequest)
            this.clientDataRequest = (ClientDataRequest) portletRequest;
    }

    Portlet2ExternalContext(PipelineContext pipelineContext, PortletContext portletContext, Map<String, String> initAttributesMap, PortletRequest portletRequest, MimeResponse mimeResponse) {
        this(pipelineContext, portletContext, initAttributesMap, portletRequest);
        this.mimeResponse = mimeResponse;
    }

    private Portlet2ExternalContext(PortletContext portletContext, Map<String, String> initAttributesMap) {
        super(portletContext, initAttributesMap);
    }

    private class Request implements ExternalContext.Request {

        private Request() {}

        private String namespace = null;

        private Map<String, Object> attributesMap;
        private Map<String, String[]> headerValuesMap;
        private Map<String, Object[]> parameterMap;

        public String getContainerType() {
            return "portlet";
        }

        public String getContainerNamespace() {
            return getResponse().getNamespacePrefix();
        }

        public String getContextPath() {
            return portletRequest.getContextPath();
        }

        public String getPathInfo() {
            // Use the resource id if we are a ResourceRequest
            // In that case, remove the query string part of the resource id, that's handled by getParameterMap()
            String result = (portletRequest instanceof ResourceRequest) ? NetUtils.removeQueryString(((ResourceRequest) portletRequest).getResourceID()) : portletRequest.getParameter(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME);
            if (result == null) result = "";
            return (result.startsWith("/")) ? result : "/" + result;
        }

        public String getRemoteAddr() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public synchronized Map<String, String[]> getHeaderValuesMap() {
            // NOTE: The container may or may not make HTTP headers available through the properties API
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap<String, String[]>();

                // NOTE: Not sure we should even pass these properties as "headers"
                // Example of property: javax.portlet.markup.head.element.support = true
                for (Enumeration<String> e = portletRequest.getPropertyNames(); e.hasMoreElements();) {
                    String name = e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerValuesMap.put(name.toLowerCase(), StringConversions.stringEnumerationToArray(portletRequest.getProperties(name)));
                }

                // PLT.11.1.5 Request Properties: "client request HTTP headers may not be always available. Portlets
                // should not rely on the presence of headers to function properly. The PortletRequest interface
                // provides specific methods to access information normally available as HTTP headers: content-length,
                // content-type, accept-language."
                // NOTE: It seems like while Liferay 5 was making headers available, Liferay 6 doesn't anymore.
                if (portletRequest instanceof ClientDataRequest) {
                    final ClientDataRequest clientDataRequest = (ClientDataRequest) portletRequest;
                    if (clientDataRequest.getContentType() != null)
                        headerValuesMap.put("content-type", new String[] { clientDataRequest.getContentType() });
                    if (clientDataRequest.getContentLength() != -1)
                        headerValuesMap.put("content-length", new String[] { Integer.toString(clientDataRequest.getContentLength()) });
                }
            }
            return headerValuesMap;
        }

        public ExternalContext.Session getSession(boolean create) {
            return Portlet2ExternalContext.this.getSession(create);
        }

        public void sessionInvalidate() {
            PortletSession session = portletRequest.getPortletSession(false);
            if (session != null)
                session.invalidate();
        }

        public String getAuthType() {
            return portletRequest.getAuthType();
        }

        public String getRemoteUser() {
            return portletRequest.getRemoteUser();
        }

        public boolean isSecure() {
            return portletRequest.isSecure();
        }

        public boolean isUserInRole(String role) {
            return portletRequest.isUserInRole(role);
        }

        public String getCharacterEncoding() {
            return (clientDataRequest != null) ? clientDataRequest.getCharacterEncoding() : null;
        }

        public int getContentLength() {
            return (clientDataRequest != null) ? clientDataRequest.getContentLength() : -1;
        }

        public String getContentType() {
            return (clientDataRequest != null) ? clientDataRequest.getContentType() : null;
        }

        public String getMethod() {
            return (clientDataRequest != null) ? clientDataRequest.getMethod() : null;
        }

        public synchronized Map<String, Object[]> getParameterMap() {
            if (parameterMap == null) {
                // Two conditions: file upload ("multipart/form-data") or not
                if (getContentType() != null && getContentType().startsWith("multipart/form-data")) {
                    // Special handling for multipart/form-data

                    // Decode the multipart data
                    parameterMap = Multipart.getParameterMapMultipart(pipelineContext, request, ServletExternalContext.DEFAULT_FORM_CHARSET_DEFAULT);
                } else if (portletRequest instanceof ResourceRequest) {
                    // We encoded query parameters directly into the resource id in this case
                    final String queryString = NetUtils.getQueryString(((ResourceRequest) portletRequest).getResourceID());
                    if (queryString != null)
                        parameterMap = Collections.unmodifiableMap(StringConversions.stringArrayMapToObjectArrayMap(NetUtils.decodeQueryString(queryString, false)));
                    else
                        parameterMap = Collections.emptyMap();
                } else {
                    // Not a resource request, so just use native request parameters
                    parameterMap = new HashMap<String, Object[]>(portletRequest.getParameterMap());
                    parameterMap.remove(OrbeonPortletXFormsFilter.PATH_PARAMETER_NAME);
                    parameterMap = Collections.unmodifiableMap(parameterMap);
                }
            }
            return parameterMap;
        }

        public synchronized Map<String, Object> getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new Portlet2ExternalContext.RequestMap(portletRequest);
            }
            return attributesMap;
        }

        public String getPathTranslated() {
            return null;
        }

        public String getProtocol() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public String getQueryString() {
            // NOTE: We could build one from the parameters
            return null;
        }

        public Reader getReader() throws IOException {
            return (clientDataRequest != null) ? clientDataRequest.getReader() : null;
        }

        public InputStream getInputStream() throws IOException {
            return (clientDataRequest != null) ? clientDataRequest.getPortletInputStream() : null;
        }

        public String getRemoteHost() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public String getRequestedSessionId() {
            return portletRequest.getRequestedSessionId();
        }

        public String getRequestPath() {
            return getPathInfo();
        }

        public String getRequestURI() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public String getRequestURL() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public String getScheme() {
            return portletRequest.getScheme();
        }

        public String getServerName() {
            return portletRequest.getServerName();
        }

        public int getServerPort() {
            return portletRequest.getServerPort();
        }

        public String getServletPath() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        private String platformClientContextPath;
        private String applicationClientContextPath;

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

        public Locale getLocale() {
            return portletRequest.getLocale();
        }

        public Enumeration getLocales() {
            return portletRequest.getLocales();
        }

        public boolean isRequestedSessionIdValid() {
            return portletRequest.isRequestedSessionIdValid();
        }

        public Principal getUserPrincipal() {
            return portletRequest.getUserPrincipal();
        }

        public Object getNativeRequest() {
            return Portlet2ExternalContext.this.getNativeRequest();
        }

        public String getPortletMode() {
            return portletRequest.getPortletMode().toString();
        }

        public String getWindowState() {
            return portletRequest.getWindowState().toString();
        }
    }

    private class Session implements ExternalContext.Session {

        private PortletSession portletSession;
        private Map<String, Object>[] sessionAttributesMaps;

        public Session(PortletSession httpSession) {
            this.portletSession = httpSession;
        }

        public long getCreationTime() {
            return portletSession.getCreationTime();
        }

        public String getId() {
            return portletSession.getId();
        }

        public long getLastAccessedTime() {
            return portletSession.getLastAccessedTime();
        }

        public int getMaxInactiveInterval() {
            return portletSession.getMaxInactiveInterval();
        }

        public void invalidate() {
            portletSession.invalidate();
        }

        public boolean isNew() {
            return portletSession.isNew();
        }

        public void setMaxInactiveInterval(int interval) {
            portletSession.setMaxInactiveInterval(interval);
        }

        public Map<String, Object> getAttributesMap() {
            return getAttributesMap(PortletSession.PORTLET_SCOPE);
        }

        public Map<String, Object> getAttributesMap(int scope) {
            if (sessionAttributesMaps == null) {
                sessionAttributesMaps = new Map[2];
            }
            if (sessionAttributesMaps[scope - 1] == null) {
                PortletSession session = portletRequest.getPortletSession(false);
                if (session != null)
                    sessionAttributesMaps[scope - 1] = new SessionMap(session, scope);
            }
            return sessionAttributesMaps[scope - 1];
        }

        public void addListener(SessionListener sessionListener) {
            ServletExternalContext.SessionListeners listeners = (ServletExternalContext.SessionListeners) portletSession.getAttribute(ServletExternalContext.SESSION_LISTENERS, PortletSession.APPLICATION_SCOPE);
            if (listeners == null) {
                listeners = new ServletExternalContext.SessionListeners();
                portletSession.setAttribute(ServletExternalContext.SESSION_LISTENERS, listeners, PortletSession.APPLICATION_SCOPE);
            }
            listeners.addListener(sessionListener);
        }

        public void removeListener(SessionListener sessionListener) {
            final ServletExternalContext.SessionListeners listeners = (ServletExternalContext.SessionListeners) portletSession.getAttribute(ServletExternalContext.SESSION_LISTENERS, PortletSession.APPLICATION_SCOPE);
            if (listeners != null)
                listeners.removeListener(sessionListener);
        }
    }


    private class Application implements ExternalContext.Application {
        private PortletContext portletContext;

        public Application(PortletContext portletContext) {
          this.portletContext = portletContext;
        }

        public void addListener(ApplicationListener applicationListener) {

            ServletExternalContext.ApplicationListeners listeners = (ServletExternalContext.ApplicationListeners) portletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS);
            if (listeners == null) {
                listeners = new ServletExternalContext.ApplicationListeners();
                portletContext.setAttribute(ServletExternalContext.APPLICATION_LISTENERS, listeners);
            }
            listeners.addListener(applicationListener);
        }

        public void removeListener(ApplicationListener applicationListener) {
            final ServletExternalContext.ApplicationListeners listeners = (ServletExternalContext.ApplicationListeners) portletContext.getAttribute(ServletExternalContext.APPLICATION_LISTENERS);
            if (listeners != null)
                listeners.removeListener(applicationListener);
        }
    }

    public abstract static class BaseResponse implements ExternalContext.Response {

        private final URLRewriter urlRewriter;

        public BaseResponse(PipelineContext pipelineContext, ExternalContext.Request request) {
            urlRewriter = new WSRPURLRewriter(pipelineContext, request, URLRewriterUtils.isWSRPEncodeResources());
        }

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
            // NIY / FIXME
            return true;
        }

        public void setContentLength(int len) {
            // NOP
        }

        public void sendError(int code) throws IOException {
            // FIXME/XXX: What to do here?
            throw new OXFException("Error while processing request: " + code);
        }

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
            // NIY / FIXME
        }

        public void setResourceCaching(long lastModified, long expires) {
            // NIY / FIXME
        }

        public void setHeader(String name, String value) {
            // NIY / FIXME: This may not make sense here. Used only by XLS serializer as of 7/29/03.
        }

        public void addHeader(String name, String value) {
            // NIY / FIXME: This may not make sense here. Used only by XLS serializer as of 7/29/03.
        }

        public void setStatus(int status) {
            // Test error
            if (status == SC_NOT_FOUND) {
                throw new OXFException("Resource not found. HTTP status: " + status);
            } else if (status >= 400) {
                // Ignore
            }
            // FIXME: How to handle NOT_MODIFIED?
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

        public String getNamespacePrefix() {
            return urlRewriter.getNamespacePrefix();
        }

        public Object getNativeResponse() {
            throw new OXFException("NIY");
//            return Portlet2ExternalContext.this.getNativeResponse();
        }
    }

    public static class BufferedResponse extends BaseResponse {

        private String contentType;
        private String redirectPathInfo;
        private Map<String, String[]> redirectParameters;
        private boolean redirectIsExitPortal;
        private StringBuilderWriter stringBuilderWriter;
        private PrintWriter printWriter;
        private ByteArrayOutputStream byteStream;
        private String title;
        
        public BufferedResponse(PipelineContext pipelineContext, ExternalContext.Request request) {
            super(pipelineContext, request);
        }
        
        public String getContentType() {
            return contentType;
        }

        public String getRedirectPathInfo() {
            return redirectPathInfo;
        }

        public Map<String, String[]> getRedirectParameters() {
            return redirectParameters;
        }

        public StringBuilderWriter getStringBuilderWriter() {
            return stringBuilderWriter;
        }

        public ByteArrayOutputStream getByteStream() {
            return byteStream;
        }

        public boolean isRedirectIsExitPortal() {
            return redirectIsExitPortal;
        }

        public PrintWriter getWriter() throws IOException {
            if (stringBuilderWriter == null) {
                stringBuilderWriter = new StringBuilderWriter();
                printWriter = new PrintWriter(stringBuilderWriter);
            }
            return printWriter;
        }

        public OutputStream getOutputStream() throws IOException {
            if (byteStream == null)
                byteStream = new ByteArrayOutputStream();
            return byteStream;
        }

        public void setContentType(String contentType) {
            this.contentType = NetUtils.getContentTypeMediaType(contentType);
        }

        /**
         *
         * @param pathInfo      path to redirect to
         * @param parameters    parameters to redirect to
         * @param isServerSide  this is ignored for portlets
         * @param isExitPortal  if this is true, the redirect will exit the portal
         */
        public void sendRedirect(String pathInfo, Map<String, String[]> parameters, boolean isServerSide, boolean isExitPortal) {
            if (isCommitted())
                throw new IllegalStateException("Cannot call sendRedirect if response is already committed.");
            this.redirectPathInfo = pathInfo;
            this.redirectParameters = parameters;
            this.redirectIsExitPortal = isExitPortal;
        }

        public String getCharacterEncoding() {
            // NOTE: This is used only by ResultStoreWriter as of 8/12/03,
            // and used only to compute the size of the resulting byte
            // stream, size which is then set but not used when working with
            // portlets.
            return CachedSerializer.DEFAULT_ENCODING;
        }

        public boolean isCommitted() {
            return false;
        }

        public void reset() {
            // NOP
        }

        public boolean isRedirect() {
            return redirectPathInfo != null;
        }

        public boolean hasContent() {
            return byteStream != null || stringBuilderWriter != null;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public Object getNativeRequest() {
        return portletRequest;
    }

    public Object getNativeResponse() {
        return mimeResponse;
    }

    public ExternalContext.Request getRequest() {
        if (request == null)
            request = new Request();
        return request;
    }

    public Response getResponse() {
        if (response == null)
            response = new BufferedResponse(pipelineContext, request);
        return response;
    }

    public ExternalContext.Session getSession(boolean create) {
        if (session == null) {
            PortletSession nativeSession = portletRequest.getPortletSession(create);
            if (nativeSession != null)
                session = new Session(nativeSession);
        }
        return session;
    }

    public ExternalContext.Application getApplication() {
        if (portletContext == null && portletRequest.getPortletSession() != null)
          portletContext = portletRequest.getPortletSession().getPortletContext();
        if (portletContext != null)
          application = new Application(portletContext);

        return application;
    }

    public String getStartLoggerString() {
        return getRequest().getRequestPath() + " - Received request";
    }

    public String getEndLoggerString() {
        return getRequest().getRequestPath();
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        return new PortletToExternalContextRequestDispatcherWrapper(portletContext.getRequestDispatcher(path));
    }

    /**
     * Present a view of the HttpServletRequest properties as a Map.
     */
    public static class RequestMap extends AttributesToMap<Object> {
        public RequestMap(final PortletRequest portletRequest) {
            super(new Attributeable<Object>() {
                public Object getAttribute(String s) {
                    return portletRequest.getAttribute(s);
                }

                public Enumeration<String> getAttributeNames() {
                    return portletRequest.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    portletRequest.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    portletRequest.setAttribute(s, o);
                }
            });
        }
    }

    /**
     * Present a view of the HttpSession properties as a Map.
     */
    public static class SessionMap extends AttributesToMap<Object> {
        public SessionMap(final PortletSession portletSession, final int scope) {
            super(new Attributeable<Object>() {
                public Object getAttribute(String s) {
                    return portletSession.getAttribute(s, scope);
                }

                public Enumeration<String> getAttributeNames() {
                    return portletSession.getAttributeNames(scope);
                }

                public void removeAttribute(String s) {
                    portletSession.removeAttribute(s, scope);
                }

                public void setAttribute(String s, Object o) {
                    portletSession.setAttribute(s, o, scope);
                }
            });
        }
    }

    public String rewriteServiceURL(String urlString, int rewriteMode) {
        return URLRewriterUtils.rewriteServiceURL(getRequest(), urlString, rewriteMode);
    }
}
