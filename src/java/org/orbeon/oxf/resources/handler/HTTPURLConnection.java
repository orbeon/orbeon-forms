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

import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.DeleteMethod;
import org.apache.commons.httpclient.methods.EntityEnclosingMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.TraceMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.properties.Properties;

import java.io.IOException;
import java.io.InputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class HTTPURLConnection extends URLConnection {

    public static String PROXY_HOST_PROPERTY = "oxf.http.proxy.host";
    public static String PROXY_PORT_PROPERTY = "oxf.http.proxy.port";
	public static String PROXY_USERNAME_PROPERTY = "oxf.http.proxy.username";
	public static String PROXY_PASSWORD_PROPERTY = "oxf.http.proxy.password";
	public static String PROXY_NTLM_HOST_PROPERTY = "oxf.http.proxy.ntlm.host";
	public static String PROXY_NTLM_DOMAIN_PROPERTY = "oxf.http.proxy.ntlm.domain";

    // Use a single shared connection manager so we can have efficient connection pooling
    private static HttpConnectionManager connectionManager;
    static {
        connectionManager = new MultiThreadedHttpConnectionManager();
        final HttpConnectionManagerParams params = new HttpConnectionManagerParams();
        params.setDefaultMaxConnectionsPerHost(Integer.MAX_VALUE);
        params.setMaxTotalConnections(Integer.MAX_VALUE);
        // The code commented below isables retries. By default HttpClient will try 3 times, and it is not clear
        // if this is a good thing or not in our case.
        //DefaultHttpMethodRetryHandler retryHandler = new DefaultHttpMethodRetryHandler(0, false);
        //params.setParameter(HttpMethodParams.RETRY_HANDLER, retryHandler);
        connectionManager.setParams(params);
    }

    private URL url;
    private boolean connected = false;
    private HttpMethodBase method;
    private int responseCode;
    private byte[] requestBody;
    private HashMap requestProperties = new HashMap();
    private HashMap responseHeaders;

    private String username;
    private String password;

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
            final String userinfo = url.getUserInfo();
            final boolean isAuthenticationRequestedWithUsername = username != null && !username.equals("");

            // Create the HTTP client (this *should* be fairly lightweight)

            // NOTE: This will also reset the client's state, including cookies and authorization stuff, as currently
            // don't have the ability to keep this state for example in association with an XForms page.
            final HttpClient httpClient = new HttpClient(connectionManager);

            // Make authentification preemptive
            if (userinfo != null || isAuthenticationRequestedWithUsername)
                httpClient.getParams().setAuthenticationPreemptive(true);

            // Set proxy if defined in properties
            final String proxyHost = Properties.instance().getPropertySet().getString(PROXY_HOST_PROPERTY);
            final Integer proxyPort = Properties.instance().getPropertySet().getInteger(PROXY_PORT_PROPERTY);
			if (proxyHost != null && proxyPort != null) {
                httpClient.getHostConfiguration().setProxy(proxyHost, proxyPort.intValue());

				// Proxy authentication
				final String proxyUsername = Properties.instance().getPropertySet().getString(PROXY_USERNAME_PROPERTY);
				final String proxyPassword = Properties.instance().getPropertySet().getString(PROXY_PASSWORD_PROPERTY);
				if (proxyUsername != null && proxyPassword != null) {
					final Credentials proxyCred;
					{
						final String ntlmHost = Properties.instance().getPropertySet().getString(PROXY_NTLM_HOST_PROPERTY);
						final String ntlmDomain = Properties.instance().getPropertySet().getString(PROXY_NTLM_DOMAIN_PROPERTY);
						if(ntlmHost != null && ntlmDomain != null){
							proxyCred = new NTCredentials(proxyUsername, proxyPassword,ntlmHost,ntlmDomain);
						}else{
							proxyCred = new UsernamePasswordCredentials(proxyUsername, proxyPassword);
						}

					}
					httpClient.getState().setProxyCredentials(AuthScope.ANY, proxyCred);
				}
			}

            if (userinfo != null) {
                // Set username and optional password specified on URL
                int separatorPosition = userinfo.indexOf(":");
                String username = separatorPosition == -1 ? userinfo : userinfo.substring(0, separatorPosition);
                String password = separatorPosition == -1 ? "" : userinfo.substring(separatorPosition + 1);
                // If the username/password contain special character, those character will be encoded, since we
                // are getting this from a URL. Now do the decoding.
                username = URLDecoder.decode(username, "utf-8");
                password = URLDecoder.decode(password, "utf-8");
                httpClient.getState().setCredentials(
                    new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(username, password)
                );
            } else if (isAuthenticationRequestedWithUsername) {
                // Set username and password specified externally
                httpClient.getState().setCredentials(
                    new AuthScope(url.getHost(), url.getPort()),
                    new UsernamePasswordCredentials(username, password == null ? "" : password)
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
                if (!"authorization".equalsIgnoreCase(key) || (userinfo == null && username == null))// note that we don't forward the header if username is ""
                    method.setRequestHeader(key, (String) requestProperties.get(key));
            }
            // Handle authentication challenge
            method.setDoAuthentication(true);

            // Make request
            responseCode = httpClient.executeMethod(method);
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

    public void setUsername(String username) {
        this.username = username.trim();
    }

    public void setPassword(String password) {
        this.password = password.trim();
    }
}
