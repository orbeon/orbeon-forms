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
package org.orbeon.oxf.portlet;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.processor.serializer.CachedSerializer;
import org.orbeon.oxf.util.AttributesToMap;
import org.orbeon.oxf.util.NetUtils;

import javax.portlet.*;
import java.io.*;
import java.net.URL;
import java.security.Principal;
import java.util.*;

/*
 * Portlet-specific implementation of ExternalContext.
 */
public class PortletExternalContext implements ExternalContext {

    public static final String PATH_PARAMETER_NAME = "oxf.path";

    private class Request implements ExternalContext.Request {
        private Map attributesMap;
        private Map headerMap;
        private Map headerValuesMap;
        private Map parameterMap;

        public String getContainerType() {
            return "portlet";
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

        public synchronized Map getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new RequestMap(portletRequest);
            }
            return attributesMap;
        }

        public synchronized Map getHeaderMap() {
            // NOTE: The container may or may not make HTTP headers available through the properties API
            if (headerMap == null) {
                headerMap = new HashMap();
                for (Enumeration e = portletRequest.getPropertyNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    headerMap.put(name, portletRequest.getProperty(name));
                }
            }
            return headerMap;
        }

        public synchronized Map getHeaderValuesMap() {
            // NOTE: The container may or may not make HTTP headers available through the properties API
            if (headerValuesMap == null) {
                headerValuesMap = new HashMap();
                for (Enumeration e = portletRequest.getPropertyNames(); e.hasMoreElements();) {
                    String name = (String) e.nextElement();
                    headerValuesMap.put(name, NetUtils.stringEnumerationToArray(portletRequest.getProperties(name)));
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
                parameterMap = new HashMap(portletRequest.getParameterMap());
                parameterMap.remove(PATH_PARAMETER_NAME);
                parameterMap = Collections.unmodifiableMap(parameterMap);
            }
            return parameterMap;
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
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_BLOCKING_ACTION);
        }

        public String rewriteRenderURL(String urlString) {
            return rewritePortletURL(urlString, WSRPUtils.URL_TYPE_RENDER);
        }

        private String rewritePortletURL(String urlString, int urlType) {
            // Case where a protocol is specified: the URL is left untouched (is
            // this a correct way of detecting the situation?)
            if (urlString.indexOf(":") != -1) return urlString;

            try {
                // Parse URL
                URL baseURL = new URL("http", "example.org", getRequest().getRequestPath());
                URL u = new URL(baseURL, urlString);
                // Decode query string
                Map parameters = NetUtils.decodeQueryString(u.getQuery(), true);
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
                String navigationalState = NetUtils.encodeQueryString(parameters);

                // TODO: portlet modes and window states

                // Encode the URL a la WSRP
                return WSRPUtils.encodePortletURL(urlType, navigationalState, null, null, u.getRef(), false);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }

        public String rewriteResourceURL(String urlString, boolean generateAbsoluteURL) {

            // NOTE: We just ignore generateAbsoluteURL here

            // Case where a protocol is specified: the URL is left untouched
            // We consider that a protocol consists only of ASCII letters
            if (NetUtils.urlHasProtocol(urlString))
                return urlString;

            try {
                ExternalContext.Request request = getRequest();

                // Return absolute path URI with query string and fragment identifier if needed
                String finalResult;
                if (urlString.startsWith("?")) {
                    // This is a special case that appears to be implemented
                    // in Web browsers as a convenience. Users may use it.
                    finalResult = request.getContextPath() + request.getRequestPath() + urlString;
                } else if (!urlString.startsWith("/") && !generateAbsoluteURL && !"".equals(urlString)) {
                    // Don't change the URL if it is a relative path and we don't force absolute URLs
                    finalResult = urlString;
                } else {
                    // Regular case, parse the URL
                    URL baseURLWithPath = new URL("http", "example.org", request.getRequestPath());
                    URL u = new URL(baseURLWithPath, urlString);

                    String tempResult = u.getFile();
                    if (u.getRef() != null)
                        tempResult += "#" + u.getRef();

                    finalResult = request.getContextPath() + tempResult;
                }

                // Encode the URL a la WSRP
                return WSRPUtils.encodeResourceURL(finalResult, false);
            } catch (Exception e) {
                throw new OXFException(e);
            }
        }

        public String getNamespacePrefix() {
            return WSRPUtils.encodeNamespacePrefix();
        }

        public PortletExternalContext getServletExternalContext() {
            return PortletExternalContext.this;
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
        private StringWriter stringWriter;
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
            if (stringWriter == null) {
                stringWriter = new StringWriter();
                printWriter = new PrintWriter(stringWriter);
            }
            return printWriter;
        }

        public OutputStream getOutputStream() throws IOException {
            if (byteStream == null)
                byteStream = new LocalByteArrayOutputStream();
            return byteStream;
        }

        public void setContentType(String contentType) {
            this.contentType = NetUtils.getContentTypeContentType(contentType);
        }

        /**
         *
         * @param pathInfo      path to redirect to
         * @param parameters    parameters to redirect to
         * @param isServerSide  this is ignored for portlets
         * @param isExitPortal  if this is true, the redirect will exit the portal
         */
        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) {
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
            if (NetUtils.getContentTypeContentType(contentType).startsWith("text/")) {
                // We are dealing with text content that may need rewriting
                // CHECK: Is this check on the content-type going to cover the relevant cases?
                if (stringWriter != null) {
                    // Write directly
                    WSRPUtils.write(response, stringWriter.toString());
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
                if (stringWriter != null) {
                    // Write directly
                    response.getWriter().write(stringWriter.toString());
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
            return byteStream != null || stringWriter != null;
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

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) {
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
            renderResponse.setContentType(NetUtils.getContentTypeContentType(contentType));
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) {
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

    private PortletRequest portletRequest;
    private ActionRequest actionRequest;
    private RenderResponse renderResponse;

    private Request request;
    private Response response;
    private Session session;

    private PortletContext portletContext;
    private Map attributesMap;
    private Map initAttributesMap;

    PortletExternalContext(PortletContext context, Map initAttributesMap, PortletRequest request) {
        this(context, initAttributesMap, request, null);
    }

    PortletExternalContext(PortletContext portletContext, Map initAttributesMap, PortletRequest portletRequest, RenderResponse renderResponse) {
        this.portletContext = portletContext;
        this.initAttributesMap = initAttributesMap;
        this.portletRequest = portletRequest;
        if (portletRequest instanceof ActionRequest)
            this.actionRequest = (ActionRequest) portletRequest;
        this.renderResponse = renderResponse;
    }

    public Map getAttributesMap() {
        if (attributesMap == null) {
            attributesMap = new PortletContextMap(portletContext);
        }
        return attributesMap;
    }

    public synchronized Map getInitAttributesMap() {
        return initAttributesMap;
    }

    public Object getNativeContext() {
        return portletContext;
    }

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

    public String getRealPath(String path) {
        return portletContext.getRealPath(path);
    }

    public String getStartLoggerString() {
        return getRequest().getPathInfo() + " - Received request";
    }

    public String getEndLoggerString() {
        return getRequest().getPathInfo();
    }

    public void log(String message, Throwable throwable) {
        portletContext.log(message, throwable);
    }

    public void log(String msg) {
        portletContext.log(msg);
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        return new RequestDispatcherWrapper(portletContext.getNamedDispatcher(name));
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        return new RequestDispatcherWrapper(portletContext.getRequestDispatcher(path));
    }

    /*
     * Wrap a PortletRequestDispatcher.
     */
    private static class RequestDispatcherWrapper implements ExternalContext.RequestDispatcher {
        private PortletRequestDispatcher _dispatcher;

        public RequestDispatcherWrapper(PortletRequestDispatcher _dispatcher) {
            this._dispatcher = _dispatcher;
        }

        public void forward(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
            throw new UnsupportedOperationException("RequestDispatcher.forward() is not supported within portlets.");
        }

        public void include(ExternalContext.Request request, ExternalContext.Response response) throws IOException {
            PortletExternalContext portletExternalContext = ((Request) request).getServletExternalContext();
            try {
                // FIXME: This is incorrect, must wrap request and responses
                _dispatcher.include((RenderRequest) portletExternalContext.portletRequest, portletExternalContext.renderResponse);
            } catch (PortletException e) {
                throw new OXFException(e);
            }
        }
    }

    /**
     * Present a view of the ServletContext properties as a Map.
     */
    public static class PortletContextMap extends AttributesToMap {
        public PortletContextMap(final PortletContext portletContext) {
            super(new Attributeable() {
                public Object getAttribute(String s) {
                    return portletContext.getAttribute(s);
                }

                public Enumeration getAttributeNames() {
                    return portletContext.getAttributeNames();
                }

                public void removeAttribute(String s) {
                    portletContext.removeAttribute(s);
                }

                public void setAttribute(String s, Object o) {
                    portletContext.setAttribute(s, o);
                }
            });
        }
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
}
