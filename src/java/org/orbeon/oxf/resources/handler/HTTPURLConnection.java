/**
 *  Copyright (C) 2006 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.*;
import org.orbeon.oxf.common.OXFException;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HTTPURLConnection extends URLConnection {

    private URL url;
    private boolean connected = false;
    private HttpMethodBase method;
    private int responseCode;
    private byte[] requestBody;
    private HashMap requestProperties = new HashMap();
    private HashMap responseHeaders;

    public HTTPURLConnection(URL url) {
        super(url);
        this.url = url;
    }

    public void setRequestMethod(String methodName) throws ProtocolException {
        if (connected)
            throw new ProtocolException("Can't reset method: already connected");

        if ("GET".equals(methodName)) method = new GetMethod(url.toString());
        else if ("POST".equals(methodName)) method = new PostMethod(url.toString());
        else if ("HEAD".equals(methodName)) method = new HeadMethod(url.toString());
        else if ("OPTIONS".equals(methodName)) method = new OptionsMethod(url.toString());
        else if ("PUT".equals(methodName)) method = new PutMethod(url.toString());
        else if ("DELETE".equals(methodName)) method = new DeleteMethod(url.toString());
        else if ("TRACE".equals(methodName)) method = new TraceMethod(url.toString());
        else throw new ProtocolException("Method " + methodName + " not supported");
    }

    public void connect() throws IOException {
        if (!connected) {
            String userinfo = url.getUserInfo();
            HttpClient client = new HttpClient();

            if (userinfo != null) {
                // Set username and password
                int separatorPosition = userinfo.indexOf(":");
                String username = separatorPosition == -1 ? userinfo : userinfo.substring(0, separatorPosition);
                String password = separatorPosition == -1 ? "" : userinfo.substring(separatorPosition + 1);
                client.getState().setCredentials(
                    new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(username, password)
                );
            }

            // If method has not been set, use GET
            if (method == null)
                method = new GetMethod(url.toString());
            // Create request entity with body
            if (requestBody != null && method instanceof EntityEnclosingMethod) {
                HandlerRequestEntity requestEntity = new HandlerRequestEntity(requestBody, getRequestProperty("content-type"));
                ((EntityEnclosingMethod) method).setRequestEntity(requestEntity);
            }
            // Set headers
            for (Iterator keyIteratory = requestProperties.keySet().iterator(); keyIteratory.hasNext();) {
                String key = (String) keyIteratory.next();
                method.setRequestHeader(key, (String) requestProperties.get(key));
            }
            // Handle authentication challenge
            method.setDoAuthentication(true);

            // Make request
            responseCode = client.executeMethod(method);
            connected = true;
        }
    }

    public InputStream getInputStream() throws IOException {
        if (method == null) connect();
        return method.getResponseBodyAsStream();
    }

    public void setRequestBody(byte[] requestBody) throws IOException {
        this.requestBody = requestBody;
    }

    private void initResponseHeaders() {
        try {
            if (!connected)
                connect();
            if (responseHeaders == null) {
                responseHeaders = new HashMap();
                Header[] headers = method.getResponseHeaders();
                for (int i = headers.length - 1; i >= 0; i--)
                    responseHeaders.put(headers[i].getName().toLowerCase(), headers[i].getValue());
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * This method will be called by URLConnection.getLastModified(),
     * URLConnection.getContentLength(), etc.
     */
    public String getHeaderField(String name) {
        initResponseHeaders();
        return (String) responseHeaders.get(name);
    }

    public Map getHeaderFields() {
        initResponseHeaders();
        return responseHeaders;
    }

    public void setRequestProperty(String key, String value) {
        super.setRequestProperty(key, value);
        requestProperties.put(key, value);
    }

    public String getRequestProperty(String key) {
        return (String) requestProperties.get(key);
    }

    public int getResponseCode() {
        return responseCode;
    }

    public void disconnect() {
        method.releaseConnection();
    }
}
