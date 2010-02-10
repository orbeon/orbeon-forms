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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.webapp.ProcessorService;
import org.orbeon.oxf.xml.XMLUtils;

import javax.portlet.*;
import java.io.*;
import java.net.URL;
import java.security.Principal;
import java.util.*;

/*
 * Portlet-specific implementation of ExternalContext.
 */
public class PortletExternalContext extends PortletWebAppExternalContext implements ExternalContext {

    public static final String PATH_PARAMETER_NAME = "orbeon.path";
    private static final String OPS_CONTEXT_NAMESPACE_KEY = "org.orbeon.ops.portlet.namespace";

    private class Request implements ExternalContext.Request {
        private Map attributesMap;
        private Map<String, String> headerMap;
        private Map<String, String[]> headerValuesMap;
        private Map parameterMap;

        public PortletExternalContext getPortletExternalContext() {
            return PortletExternalContext.this;
        }

        public String getContainerType() {
            return "portlet";
        }

        public String getContainerNamespace() {
            final String namespace;
            if (getNativeResponse() instanceof RenderResponse) {
//                System.out.println("getContainerNamespace() 1: ");
                // We have a render response, so we can get the namespace directly and remember it
                namespace = ((RenderResponse) getNativeResponse()).getNamespace().replace(' ', '_');
                PortletExternalContext.this.getAttributesMap().put(OPS_CONTEXT_NAMESPACE_KEY, namespace);
            } else {
//                System.out.println("getContainerNamespace() 2: ");
                // We don't have a render response, and we hope for two things:
                // 1. For a given portlet, the namespace tends to remain constant for the lifetime of the portlet
                // 2. Even if it is not constant, we hope that it tends to be between a render and action requests
                namespace = (String) PortletExternalContext.this.getAttributesMap().get(OPS_CONTEXT_NAMESPACE_KEY);
                if (namespace == null)
                    throw new OXFException("Unable to find portlet namespace in portlet context.");
            }
//            System.out.println("  namespace: " + namespace);
            return namespace;
        }

        public String getContextPath() {
            return portletRequest.getContextPath();
        }

        public String getPathInfo() {
            String result = portletRequest.getParameter(PATH_PARAMETER_NAME);
            if (result == null) result = "";
            return (result.startsWith("/")) ? result : "/" + result;
        }

        public String getRemoteAddr() {
            // NOTE: The portlet API does not provide for this value.
            return null;
        }

        public synchronized Map getHeaderMap() {
            // NOTE: The container may or may not make HTTP headers available through the properties API
            if (headerMap == null) {
                headerMap = new HashMap<String, String>();
                for (Enumeration e = portletRequest.getPropertyNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerMap.put(name.toLowerCase(), portletRequest.getProperty(name));
                }
            }
            return headerMap;
        }

        public synchronized Map getHeaderValuesMap() {
            // NOTE: The container may or may not make HTTP headers available through the properties API
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap<String, String[]>();
                for (Enumeration e = portletRequest.getPropertyNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    // NOTE: Normalize names to lowercase to ensure consistency between servlet containers
                    headerValuesMap.put(name.toLowerCase(), StringUtils.stringEnumerationToArray(portletRequest.getProperties(name)));
                }
            }
            return headerValuesMap;
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
            return (actionRequest != null) ? actionRequest.getCharacterEncoding() : null;
        }

        public int getContentLength() {
            return (actionRequest != null) ? actionRequest.getContentLength() : -1;
        }

        public String getContentType() {
            return (actionRequest != null) ? actionRequest.getContentType() : null;
        }

        public String getMethod() {
            // NOTE: This method does not make sense within a portlet
            return null;
        }

        public synchronized Map getParameterMap() {
            if (parameterMap == null) {
                // Two conditions: file upload ("multipart/form-data") or not
                if (getContentType() != null && getContentType().startsWith("multipart/form-data")) {
                    // Special handling for multipart/form-data

                    // Decode the multipart data
                    parameterMap = NetUtils.getParameterMapMultipart(pipelineContext, request, ServletExternalContext.DEFAULT_FORM_CHARSET_DEFAULT);

                } else {
                    // Just use native request parameters
                    parameterMap = new HashMap(portletRequest.getParameterMap());
                    parameterMap.remove(PATH_PARAMETER_NAME);
                    parameterMap = Collections.unmodifiableMap(parameterMap);
                }
            }
            return parameterMap;
        }

        public synchronized Map getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new PortletExternalContext.RequestMap(portletRequest);
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
            return (actionRequest != null) ? actionRequest.getReader() : null;
        }

        public InputStream getInputStream() throws IOException {
            return (actionRequest != null) ? actionRequest.getPortletInputStream() : null;
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

        public PortletExternalContext getServletExternalContext() {
            return PortletExternalContext.this;
        }

        public Object getNativeRequest() {
            return PortletExternalContext.this.getNativeRequest();
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
        private Map[] sessionAttributesMaps;

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

        public Map getAttributesMap() {
            return getAttributesMap(PortletSession.PORTLET_SCOPE);
        }

        public Map getAttributesMap(int scope) {
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

    public abstract class BaseResponse implements Response {
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
            if (status == SC_NOT_FOUND)
                throw new OXFException("Resource not found. HTTP status: " + status);
            else if (status >= 400)
                ;// Ignore
                //throw new OXFException("Error while processing request. HTTP status: " + status);
            // FIXME: How to handle NOT_MODIFIED?
        }

        public String rewriteActionURL(String urlString) {
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_BLOCKING_ACTION, null, null);
        }

        public String rewriteRenderURL(String urlString) {
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_RENDER, null, null);
        }

        public String rewriteActionURL(String urlString, String portletMode, String windowState) {
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_BLOCKING_ACTION, portletMode,  windowState);
        }

        public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_RENDER, portletMode,  windowState);
        }

        private String rewritePortletURL(String urlString, int urlType, String portletMode, String windowState) {
            // Case where a protocol is specified OR it's just a fragment: the URL is left untouched
            if (NetUtils.urlHasProtocol(urlString) || urlString.startsWith("#")) return urlString;

            try {
                // Parse URL
                final URL baseURL = new URL("http", "example.org", getRequest().getRequestPath());
                final URL u = new URL(baseURL, urlString);
                // Decode query string
                final Map<String, String[]> parameters = NetUtils.decodeQueryString(u.getQuery(), true);
                // Add special path parameter
                if (urlString.startsWith("?")) {
                    // This is a special case that appears to be implemented
                    // in Web browsers as a convenience. Users may use it.
                    parameters.put(PortletExternalContext.PATH_PARAMETER_NAME, new String[] { getRequest().getRequestPath() });
                } else {
                    // Regular case, use parsed path
                    parameters.put(PortletExternalContext.PATH_PARAMETER_NAME, new String[] { u.getPath() });
                }
                // Encode as "navigational state"
                final String navigationalState = NetUtils.encodeQueryString(parameters);

                // Encode the URL a la WSRP
                return WSRPUtils.encodePortletURL(urlType, navigationalState, portletMode, windowState, u.getRef(), false);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {
            return rewriteResourceURL(urlString, generateAbsoluteURL ? REWRITE_MODE_ABSOLUTE : REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);
        }

        public String rewriteResourceURL(String urlString, int rewriteMode) {
            // NOTE: We could encode the URL a la WSRP, but for resources, we can always produce an URL here, so we
            // just do so!
            return URLRewriterUtils.rewriteURL(getRequest(), urlString, rewriteMode);
        }

        public String getNamespacePrefix() {
            return WSRPUtils.encodeNamespacePrefix();
        }

        public PortletExternalContext getServletExternalContext() {
            return PortletExternalContext.this;
        }

        public Object getNativeResponse() {
            return PortletExternalContext.this.getNativeResponse();
        }
    }

    private static class LocalByteArrayOutputStream extends ByteArrayOutputStream {
        public byte[] getByteArray() {
            return buf;
        }
    }

    public class BufferedResponse extends BaseResponse {

        private String contentType;
        private String redirectPathInfo;
        private Map redirectParameters;
        private boolean redirectIsExitPortal;
        private StringBuilderWriter StringBuilderWriter;
        private PrintWriter printWriter;
        private LocalByteArrayOutputStream byteStream;
        private String title;

        public String getContentType() {
            return contentType;
        }

        public String getRedirectPathInfo() {
            return redirectPathInfo;
        }

        public Map getRedirectParameters() {
            return redirectParameters;
        }

        public boolean isRedirectIsExitPortal() {
            return redirectIsExitPortal;
        }

        public PrintWriter getWriter() throws IOException {
            if (StringBuilderWriter == null) {
                StringBuilderWriter = new StringBuilderWriter();
                printWriter = new PrintWriter(StringBuilderWriter);
            }
            return printWriter;
        }

        public OutputStream getOutputStream() throws IOException {
            if (byteStream == null)
                byteStream = new LocalByteArrayOutputStream();
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
         * @param isNoRewrite
         */
        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) {
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

        public void write(RenderResponse response) throws IOException {
            if (XMLUtils.isTextOrJSONContentType(contentType) || XMLUtils.isXMLMediatype(contentType)) {
                // We are dealing with text content that may need rewriting
                // CHECK: Is this check on the content-type going to cover the relevant cases?
                if (StringBuilderWriter != null) {
                    // Write directly
                    WSRPUtils.write(response, StringBuilderWriter.toString());
                } else if (byteStream != null) {
                    // Transform to string and write
                    String encoding = NetUtils.getContentTypeCharset(contentType);
                    if (encoding == null)
                        encoding = CachedSerializer.DEFAULT_ENCODING;
                    WSRPUtils.write(response, new String(byteStream.getByteArray(), 0, byteStream.size(), encoding));
                } else {
                    throw new IllegalStateException("Processor execution did not return content.");
                }
            } else {
                // We are dealing with content that does not require rewriting
                if (StringBuilderWriter != null) {
                    // Write directly
                    response.getWriter().write(StringBuilderWriter.toString());
                } else if (byteStream != null) {
                    // Transform to string and write
                    byteStream.writeTo(response.getPortletOutputStream());
                } else {
                    throw new IllegalStateException("Processor execution did not return content.");
                }
            }
        }

        public boolean isRedirect() {
            return redirectPathInfo != null;
        }

        public boolean isContent() {
            return byteStream != null || StringBuilderWriter != null;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    public class DirectResponseTemp extends BufferedResponse {

        public DirectResponseTemp() {
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) {
            throw new IllegalStateException();
        }
    }

    public class DirectResponse extends BaseResponse {

        public DirectResponse() {
        }

        public OutputStream getOutputStream() throws IOException {
            return renderResponse.getPortletOutputStream();
        }

        public PrintWriter getWriter() throws IOException {
            return renderResponse.getWriter();
        }

        public void setContentType(String contentType) {
            renderResponse.setContentType(NetUtils.getContentTypeMediaType(contentType));
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal, boolean isNoRewrite) {
            throw new IllegalStateException();
        }

        public String getCharacterEncoding() {
            return renderResponse.getCharacterEncoding();
        }

        public boolean isCommitted() {
            return renderResponse.isCommitted();
        }

        public void reset() {
            renderResponse.reset();
        }

        public void setTitle(String title) {
            renderResponse.setTitle(title);
        }
    }

    private ExternalContext.Request request;
    private ExternalContext.Response response;
    private ExternalContext.Session session;
    private ExternalContext.Application application;

    private ProcessorService processorService;
    private PipelineContext pipelineContext;
    private PortletRequest portletRequest;
    private ActionRequest actionRequest;
    private RenderResponse renderResponse;

    private PortletExternalContext(ProcessorService processorService, PortletContext portletContext, Map<String, String> initAttributesMap) {
        super(portletContext, initAttributesMap);
        this.processorService = processorService;
    }

    PortletExternalContext(ProcessorService processorService, PipelineContext pipelineContext, PortletContext portletContext, Map<String, String> initAttributesMap, PortletRequest portletRequest) {
        this(processorService, portletContext, initAttributesMap);
        this.pipelineContext = pipelineContext;
        this.portletRequest = portletRequest;
        if (portletRequest instanceof ActionRequest)
            this.actionRequest = (ActionRequest) portletRequest;
    }

    PortletExternalContext(ProcessorService processorService, PipelineContext pipelineContext, PortletContext portletContext, Map<String, String> initAttributesMap, PortletRequest portletRequest, RenderResponse renderResponse) {
        this(processorService, pipelineContext, portletContext, initAttributesMap, portletRequest);
        this.renderResponse = renderResponse;
    }

    private PortletExternalContext(PipelineContext pipelineContext, Request unwrappedRequest) {
        this(unwrappedRequest.getPortletExternalContext().getProcessorService(),
                pipelineContext,
                unwrappedRequest.getPortletExternalContext().getPortletContext(),
                unwrappedRequest.getPortletExternalContext().getInitAttributesMap(),
                unwrappedRequest.getPortletExternalContext().getPortletRequest(),
                unwrappedRequest.getPortletExternalContext().getRenderResponse());

        // Forward portlet config to new pipeline context
        pipelineContext.setAttribute(PipelineContext.PORTLET_CONFIG,
                unwrappedRequest.getPortletExternalContext().getPipelineContext().getAttribute(PipelineContext.PORTLET_CONFIG));
    }

    PortletExternalContext(PipelineContext pipelineContext, ExternalContext.Request request, ExternalContext.Response response) {
        this(pipelineContext, unwrapRequest(request));

        this.request = request;
        this.response = response;
    }

    private static Request unwrapRequest(ExternalContext.Request request) {
        while (!(request instanceof Request)) {
            if (!(request instanceof org.orbeon.oxf.externalcontext.RequestWrapper))
                throw new OXFException("Request in forward must be original Request or instance of RequestWrapper.");
            request = ((org.orbeon.oxf.externalcontext.RequestWrapper) request)._getRequest();
        }
        return (Request) request;
    }

//    private static Response unwrapResponse(ExternalContext.Response response) {
//        while (!(response instanceof BaseResponse)) {
//            if (!(response instanceof org.orbeon.oxf.externalcontext.ResponseWrapper))
//                throw new OXFException("Response in forward must be original Response or instance of ResponseWrapper.");
//            response = ((org.orbeon.oxf.externalcontext.ResponseWrapper) response)._getResponse();
//        }
//        return (Response) response;
//    }

    public Object getNativeRequest() {
        return portletRequest;
    }

    public Object getNativeResponse() {
        return renderResponse;
    }

    public Object getNativeSession(boolean create) {
        return portletRequest.getPortletSession(create);
    }

    public ExternalContext.Request getRequest() {
        if (request == null)
            request = new Request();
        return request;
    }

    public Response getResponse() {
        if (response == null) {
            if (renderResponse == null)
                response = new BufferedResponse();
            else
                response = new DirectResponseTemp();
        }
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

    public RequestDispatcher getNamedDispatcher(String name) {
        return new PortletToExternalContextRequestDispatcherWrapper(portletContext.getNamedDispatcher(name));
    }

    public RequestDispatcher getRequestDispatcher(String path, boolean isContextRelative) {
        return new PortletToExternalContextRequestDispatcherWrapper(portletContext.getRequestDispatcher(path));
    }

    public ProcessorService getProcessorService() {
        return processorService;
    }

    public PipelineContext getPipelineContext() {
        return pipelineContext;
    }

    private PortletRequest getPortletRequest() {
        return portletRequest;
    }

    private RenderResponse getRenderResponse() {
        return renderResponse;
    }

    /**
     * Present a view of the HttpServletRequest properties as a Map.
     */
    public static class RequestMap extends AttributesToMap {
        public RequestMap(final PortletRequest portletRequest) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return portletRequest.getAttribute(s);
                }

                public Enumeration getAttributeNames() {
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
    public static class SessionMap extends AttributesToMap {
        public SessionMap(final PortletSession portletSession, final int scope) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return portletSession.getAttribute(s, scope);
                }

                public Enumeration getAttributeNames() {
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

    public String rewriteServiceURL(String urlString, boolean forceAbsolute) {
        return URLRewriterUtils.rewriteServiceURL(getRequest(), urlString, forceAbsolute);
    }
}
