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
package org.orbeon.oxf.externalcontext;

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.servlet.ServletExternalContext;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringConversions;

import java.io.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Create an ExternalContext.Request useful for forwarding a request while simulating a server-side redirect.
 */
public class ForwardExternalContextRequestWrapper extends RequestWrapper {

    private String contextPath;
    private String pathQuery;
    private String method;
    private String mediaType;
    private byte[] messageBody;

    private Map<String, String[]> headerValuesMap;

    private InputStream inputStream;
    private String path;
    private String queryString;
    private Map<String, Object[]> queryParameters;

    /**
     * This simulates a POST or a PUT.
     *
     * @param customHeaderNameValues    can be null
     */
    public ForwardExternalContextRequestWrapper(ExternalContext externalContext, IndentedLogger indentedLogger, String contextPath, String pathQuery,
                                                String method, String mediaType, byte[] messageBody, String[] namesOfHeadersToForward,
                                                Map<String, String[]> customHeaderNameValues) {
        super(externalContext.getRequest());
        this.contextPath = contextPath;
        this.pathQuery = pathQuery;
        this.method = method;
        this.mediaType = mediaType;
        this.messageBody = messageBody;

        initializeHeaders(externalContext, indentedLogger, namesOfHeadersToForward, customHeaderNameValues);
    }

    /**
     * This simulates a GET.
     *
     * @param customHeaderNameValues    can be null
     */
    public ForwardExternalContextRequestWrapper(ExternalContext externalContext, IndentedLogger indentedLogger, String contextPath, String pathQuery,
                                                String method, String[] namesOfHeadersToForward, Map<String, String[]> customHeaderNameValues) {
        super(externalContext.getRequest());
        this.contextPath = contextPath;
        this.pathQuery = pathQuery;
        this.method = method;

        initializeHeaders(externalContext, indentedLogger, namesOfHeadersToForward, customHeaderNameValues);
    }

    private void initializeHeaders(ExternalContext externalContext, IndentedLogger indentedLogger, String[] namesOfHeadersToForward, Map<String, String[]> customHeaderNameValues) {

        // Determine headers to set
        headerValuesMap = Connection.getHeadersMap(externalContext, indentedLogger, null, customHeaderNameValues, StringUtils.join(namesOfHeadersToForward, ' '));

        // Set Content-Type and Content-Length headers
        if (mediaType != null && messageBody != null) {
            headerValuesMap.put("content-type", new String[] { mediaType });
            headerValuesMap.put("content-length", new String[] { Integer.toString(messageBody.length) });
        }
    }

    /* SUPPORTED: methods called by ExternalContextToHttpServletRequestWrapper */

    public String getMethod() {
        return method.toUpperCase();
    }

    public Map<String, Object[]> getParameterMap() {
        if (queryParameters == null) {
            // Handle query string
            {
                // SRV.4.1: "Query string data is presented before post body data."
                final Map<String, String[]> queryParameters = NetUtils.decodeQueryString(getQueryString(), false);
                this.queryParameters = new TreeMap<String, Object[]>();
                for (Map.Entry<String, String[]> entry: queryParameters.entrySet()) {
                    this.queryParameters.put(entry.getKey(), StringConversions.stringArrayToObjectArray(entry.getValue()));
                }
            }
            // Handle post body form parameters
            // NOTE: Remember, the servlet container does not help us decoding the body: the "other side" will just end
            // up here when asking for parameters.
            if (method.equalsIgnoreCase("post") && "application/x-www-form-urlencoded".equals(mediaType) && messageBody != null && messageBody.length > 0) {
                // There is a body possibly containing parameters
                // SRV.4.1.1 When Parameters Are Available
                final String bodyString;
                try {
                    bodyString = new String(messageBody, ServletExternalContext.getDefaultFormCharset());
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                }
                final Map<String, String[]> bodyParameters = NetUtils.decodeQueryString(bodyString, false);
                for (Map.Entry<String, String[]> entry: bodyParameters.entrySet()) {
                    StringConversions.addValuesToObjectArrayMap(queryParameters, entry.getKey(), entry.getValue());
                }
            }
        }

        return queryParameters;
    }

    public String getQueryString() {
        if (queryString == null) {
            final int mark = pathQuery.indexOf('?');
            queryString = (mark == -1) ? null : pathQuery.substring(mark + 1);
        }

        return queryString;
    }

    public String getCharacterEncoding() {
        return null;//TODO?
    }

    public int getContentLength() {
        return (messageBody == null) ? 0 : messageBody.length;
    }

    public String getContentType() {
        return mediaType;
    }

    public InputStream getInputStream() throws IOException {
        if (inputStream == null) {
            // NOTE: Provide an empty stream if there is no body because caller might assume InputStream is non-null
            inputStream = new ByteArrayInputStream((messageBody != null) ? messageBody : new byte[] {});
        }

        return inputStream;
    }

    public Reader getReader() throws IOException {
        return null;//TODO?
    }

    public Map<String, Object> getAttributesMap() {
        // Just return super since we do not override attributes here
        return super.getAttributesMap();
    }

    public Map<String, String[]> getHeaderValuesMap() {
        return headerValuesMap;
    }

    @Override
    public String getClientContextPath(String urlString) {
        // NOTE: Assuming this is not going to be called
        return super.getClientContextPath(urlString);
    }

    /*
    * NOTE: All the path methods are handled by the request dispatcher implementation in the servlet container upon
    * forward, but upon include we must provide them.
    *
    * NOTE: Checked 2009-02-12 that none of the methods below are called when forwarding through
    * spring/JSP/filter/Orbeon in Tomcat 5.5.27. HOWEVER they are called when including.
    */

    public String getPathInfo() {
        if (path == null) {
            final int mark = pathQuery.indexOf('?');
            path = (mark == -1) ? pathQuery : pathQuery.substring(0, mark);
        }

        return path;
    }

    public String getServletPath() {
        return "";
    }

    public String getContextPath() {
        // Return the context path passed to this wrapper
        return contextPath;
    }

    public String getRequestPath() {
        // Get servlet path and path info
        String servletPath = getServletPath();
        if (servletPath == null) servletPath = "";
        String pathInfo = getPathInfo();
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

    public String getRequestURI() {
        // Must return the path including the context
        final String contextPath = getContextPath();
        return "/".equals(contextPath) ? getRequestPath() : getContextPath() + getRequestPath();
    }

    // Probably not needed because computed by ExternalContextToHttpServletRequestWrapper. 
    public String getRequestURL() {
        // Get absolute URL w/o query string e.g. http://foo.com/a/b/c
        final String incomingRequestURL = super.getRequestURL();
        // Resolving request URI against incoming absolute URL, e.g. /d/e/f -> http://foo.com/d/e/f
        return NetUtils.resolveURI(getRequestURI(), incomingRequestURL);
    }
}