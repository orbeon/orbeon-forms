package org.orbeon.oxf.processor.test;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xml.XPathUtils;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.apache.log4j.Logger;

import java.io.*;
import java.security.Principal;
import java.util.*;

/**
 * ExternalContext used by the TestProcessor. It is configurable with an XML document representing
 * the request.
 */
public class TestExternalContext implements ExternalContext  {

    private static final Logger logger = LoggerFactory.createLogger(TestExternalContext.class);

    private Document requestDocument;

    private Request request;
    private Response response;

    public TestExternalContext(Document requestDocument) {
        this.requestDocument = requestDocument;
    }

    private class Request implements ExternalContext.Request {

        private Map attributesMap;
        private Map parameterMap;
        private Map headerMap;
        private Map headerValuesMap;

        public Map getAttributesMap() {
            if (attributesMap == null) {
                attributesMap = new HashMap();
            }
            return attributesMap;
        }

        public String getAuthType() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/auth-type");
        }

        public String getCharacterEncoding() {
            // NIY
            return null;
        }

        public String getContainerType() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/container-type");
        }

        public int getContentLength() {
            // NIY
            return 0;
        }

        public String getContentType() {
            // NIY
            return null;
        }

        public String getContextPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/context-path");
        }

        public Map getHeaderMap() {
            if (headerMap == null) {
                Map map = new HashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/headers/header"); i.hasNext();) {
                    Element e = (Element) i.next();
                    String name = XPathUtils.selectStringValueNormalize(e, "name");
                    String value = XPathUtils.selectStringValueNormalize(e, "value[1]");
                    map.put(name, value);
                }
                headerMap = Collections.unmodifiableMap(map);
            }
            return headerMap;
        }

        public Map getHeaderValuesMap() {
            if (headerValuesMap == null) {
                Map map = new HashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/headers/header"); i.hasNext();) {
                    Element e = (Element) i.next();
                    String name = XPathUtils.selectStringValueNormalize(e, "name");
                    for (Iterator j = XPathUtils.selectIterator(e, "value"); j.hasNext();) {
                        Element valueElement = (Element) j.next();
                        String value = XPathUtils.selectStringValueNormalize(valueElement, ".");
                        NetUtils.addValueToStringArrayMap(map, name, value);
                    }
                }
                headerValuesMap = Collections.unmodifiableMap(map);
            }
            return headerValuesMap;
        }

        public InputStream getInputStream() throws IOException {
            // NIY
            return null;
        }

        public Locale getLocale() {
            // NIY
            return null;
        }

        public Enumeration getLocales() {
            // NIY
            return null;
        }

        public String getMethod() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/method");
        }

        public Map getParameterMap() {
            if (parameterMap == null) {
                Map map = new HashMap();
                for (Iterator i = XPathUtils.selectIterator(requestDocument, "/*/parameters/parameter"); i.hasNext();) {
                    Element e = (Element) i.next();
                    String name = XPathUtils.selectStringValueNormalize(e, "name");
                    for (Iterator j = XPathUtils.selectIterator(e, "value"); j.hasNext();) {
                        Element valueElement = (Element) j.next();
                        String value = XPathUtils.selectStringValueNormalize(valueElement, ".");
                        NetUtils.addValueToStringArrayMap(map, name, value);
                    }
                }
                parameterMap = Collections.unmodifiableMap(map);
            }
            return parameterMap;
        }

        public String getPathInfo() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-info");
        }

        public String getPathTranslated() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/path-translated");
        }

        public String getProtocol() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/protocol");
        }

        public String getQueryString() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/query-string");
        }

        public Reader getReader() throws IOException {
            // NIY
            return null;
        }

        public String getRemoteAddr() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-addr");
        }

        public String getRemoteHost() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-host");
        }

        public String getRemoteUser() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/remote-user");
        }

        public String getRequestedSessionId() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/requested-session-id");
        }

        public String getRequestPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-path");
        }

        public String getRequestURI() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/request-uri");
        }

        public String getScheme() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/scheme");
        }

        public String getServerName() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/server-name");
        }

        public int getServerPort() {
            return ProcessorUtils.selectIntValue(requestDocument, "/*/server-port", 80);
        }

        public String getServletPath() {
            return XPathUtils.selectStringValueNormalize(requestDocument, "/*/servlet-path");
        }

        public Principal getUserPrincipal() {
            // NIY
            return null;
        }

        public boolean isRequestedSessionIdValid() {
            // NIY
            return false;
        }

        public boolean isSecure() {
            return ProcessorUtils.selectBooleanValue(requestDocument, "/*/is-secure", false);
        }

        public boolean isUserInRole(String role) {
            // NIY
            return false;
        }

        public void sessionInvalidate() {
            // NIY
        }
    }

    private class Response implements ExternalContext.Response {
        public void addHeader(String name, String value) {
        }

        public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
            return false;
        }

        public String getCharacterEncoding() {
            return null;
        }

        public String getNamespacePrefix() {
            return null;
        }

        public OutputStream getOutputStream() throws IOException {
            return null;
        }

        public PrintWriter getWriter() throws IOException {
            return null;
        }

        public boolean isCommitted() {
            return false;
        }

        public void reset() {
        }

        public String rewriteActionURL(String urlString) {
            return null;
        }

        public String rewriteRenderURL(String urlString) {
            return null;
        }

        public String rewriteResourceURL(String urlString, boolean absolute) {
            return null;
        }

        public void sendError(int len) throws IOException {
        }

        public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
        }

        public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
        }

        public void setContentLength(int len) {
        }

        public void setContentType(String contentType) {
        }

        public void setHeader(String name, String value) {
        }

        public void setStatus(int status) {
        }

        public void setTitle(String title) {
        }
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
        // NIY
        return null;
    }

    public RequestDispatcher getRequestDispatcher(String path) {
        // NIY
        return null;
    }

    public RequestDispatcher getNamedDispatcher(String name) {
        // NIY
        return null;
    }

    public String getStartLoggerString() {
        return "Running test processor";
    }

    public String getEndLoggerString() {
        return "Done running test processor";
    }

    public Object getNativeRequest() {
        return null;
    }

    public Object getNativeResponse() {
        return null;
    }

    public Object getNativeSession(boolean flag) {
        return null;
    }

    public Map getAttributesMap() {
        // NIY
        return null;
    }

    public Map getInitAttributesMap() {
        // NIY
        return null;
    }

    public Object getNativeContext() {
        return null;
    }

    public String getRealPath(String path) {
        // NIY
        return null;
    }

    public void log(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    public void log(String msg) {
        logger.info(msg);
    }
}
