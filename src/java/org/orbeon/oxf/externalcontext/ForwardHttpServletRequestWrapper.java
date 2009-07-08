/**
 *  Copyright (C) 2005 Orbeon, Inc.
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
package org.orbeon.oxf.externalcontext;

import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.util.*;

/**
 * Create an HttpServletRequestWrapper useful for forwarding a request while simulating a
 * server-side redirect.
 */
public class ForwardHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private HttpServletRequest httpServletRequest;
    private String pathInfo;
    private Map parameters;

    private String queryString;

    /**
     * This simulates a GET.
     *
     * @param parameters        Map<String name, String[] values>
     */
    public ForwardHttpServletRequestWrapper(HttpServletRequest httpServletRequest, String pathInfo, Map parameters) {
        super(httpServletRequest);
        this.httpServletRequest = httpServletRequest;
        this.pathInfo = pathInfo;
        this.parameters = parameters;
    }

    public Map getParameterMap() {
        return parameters;
    }

    public Enumeration getParameterNames() {
        return Collections.enumeration(parameters.keySet());
    }

    public String[] getParameterValues(String name) {
        // Convert as parameters MAY contain FileItem values
        return StringUtils.objectArrayToStringArray((Object[]) parameters.get(name));
    }

    public String getParameter(String s) {
        final String[] parameterValues = getParameterValues(s);
        if (parameterValues == null || parameterValues.length == 0)
            return null;
        else
            return parameterValues[0];
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public String getQueryString() {
        if (queryString == null) {
            queryString = NetUtils.encodeQueryString(parameters);
        }

        return queryString;
    }

    public String getServletPath() {
        return "";
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
            final InputStream is = new ByteArrayInputStream(new byte[]{});
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

    public static Map filterHeaders = new HashMap();

    static {
        filterHeaders.put("content-length", "");
        filterHeaders.put("content-type", "");
        //filterHeaders.put("referer", "");
    }

    private List headerNamesList;

    public Enumeration getHeaderNames() {
        if (headerNamesList == null) {
            headerNamesList = Collections.list(httpServletRequest.getHeaderNames());

            // Remove headers associated with body
            for (Iterator i = filterHeaders.keySet().iterator(); i.hasNext();) {
                headerNamesList.remove(i.next());
            }
        }

        return Collections.enumeration(headerNamesList);
    }

    public String getHeader(String s) {
        if (filterHeaders.get(s) != null)
            return null;
        return httpServletRequest.getHeader(s);
    }

    public Enumeration getHeaders(String s) {
        if (filterHeaders.get(s) != null)
            return null;
        return httpServletRequest.getHeaders(s);
    }

    public long getDateHeader(String s) {
        if (filterHeaders.get(s) != null)
            return -1;
        return httpServletRequest.getDateHeader(s);
    }

    public int getIntHeader(String s) {
        if (filterHeaders.get(s) != null)
            return -1;
        return httpServletRequest.getIntHeader(s);
    }
}