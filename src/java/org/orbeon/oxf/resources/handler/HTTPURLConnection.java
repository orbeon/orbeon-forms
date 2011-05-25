/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler;

import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.*;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.*;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParamBean;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.properties.Properties;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.StringConversions;

import javax.net.ssl.SSLContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class HTTPURLConnection extends URLConnection {

    public static String STALE_CHECKING_ENABLED_PROPERTY = "oxf.http.stale-checking-enabled";
    public static String SO_TIMEOUT_PROPERTY = "oxf.http.so-timeout";
    public static String PROXY_HOST_PROPERTY = "oxf.http.proxy.host";
    public static String PROXY_PORT_PROPERTY = "oxf.http.proxy.port";
    public static String SSL_HOSTNAME_VERIFIER = "oxf.http.ssl.hostname-verifier";
    public static String PROXY_SSL_PROPERTY = "oxf.http.proxy.use-ssl";
	public static String PROXY_USERNAME_PROPERTY = "oxf.http.proxy.username";
	public static String PROXY_PASSWORD_PROPERTY = "oxf.http.proxy.password";
	public static String PROXY_NTLM_HOST_PROPERTY = "oxf.http.proxy.ntlm.host";
	public static String PROXY_NTLM_DOMAIN_PROPERTY = "oxf.http.proxy.ntlm.domain";

    // Use a single shared connection manager so we can have efficient connection pooling
    private static ClientConnectionManager connectionManager;
    private static HttpParams httpParams;
    private static PreemptiveAuthHttpRequestInterceptor preemptiveAuthHttpRequestInterceptor = new PreemptiveAuthHttpRequestInterceptor();
    private static AuthState proxyAuthState = null;

    static {
        final BasicHttpParams basicHttpParams = new BasicHttpParams();
        // Remove limit on the number of connections per host
        ConnManagerParams.setMaxConnectionsPerRoute(basicHttpParams, new ConnPerRouteBean(Integer.MAX_VALUE));
        // Remove limit on the number of max connections
        ConnManagerParams.setMaxTotalConnections(basicHttpParams, Integer.MAX_VALUE);

        // Set parameters per as configured in the properties
        final HttpConnectionParamBean paramBean = new HttpConnectionParamBean(basicHttpParams);
        final PropertySet propertySet = Properties.instance().getPropertySet();
        paramBean.setStaleCheckingEnabled(propertySet.getBoolean(STALE_CHECKING_ENABLED_PROPERTY, true));
        paramBean.setSocketBufferSize(propertySet.getInteger(SO_TIMEOUT_PROPERTY, 0));

        // Create SSL hostname verifier
        String hostnameVerifierProperty = propertySet.getString(SSL_HOSTNAME_VERIFIER, "strict");
        X509HostnameVerifier hostnameVerifier =
                  "browser-compatible".equals(hostnameVerifierProperty) ? SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER
                : "allow-all".equals(hostnameVerifierProperty) ? SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER
                : SSLSocketFactory.STRICT_HOSTNAME_VERIFIER;

        // Declare schemes (though having to declare common schemes like HTTP and HTTPS seems wasteful)
        final SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        SSLSocketFactory sslSocketFactory = SSLSocketFactory.getSocketFactory();
        sslSocketFactory.setHostnameVerifier(hostnameVerifier);
        schemeRegistry.register(new Scheme("https", sslSocketFactory, 443));
        connectionManager = new ThreadSafeClientConnManager(basicHttpParams, schemeRegistry);

        // Set proxy if defined in properties
        final String proxyHost = Properties.instance().getPropertySet().getString(PROXY_HOST_PROPERTY);
        final Integer proxyPort = Properties.instance().getPropertySet().getInteger(PROXY_PORT_PROPERTY);
        if (proxyHost != null && proxyPort != null) {
            final boolean useTLS = Properties.instance().getPropertySet().getBoolean(PROXY_SSL_PROPERTY, false);
            basicHttpParams.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, proxyPort, useTLS ? "https" : "http"));

            // Proxy authentication
            final String proxyUsername = Properties.instance().getPropertySet().getString(PROXY_USERNAME_PROPERTY);
            final String proxyPassword = Properties.instance().getPropertySet().getString(PROXY_PASSWORD_PROPERTY);
            if (proxyUsername != null && proxyPassword != null) {
                final String ntlmHost = Properties.instance().getPropertySet().getString(PROXY_NTLM_HOST_PROPERTY);
                final String ntlmDomain = Properties.instance().getPropertySet().getString(PROXY_NTLM_DOMAIN_PROPERTY);
                final Credentials proxyCredentials = ntlmHost != null && ntlmDomain != null
                        ? new NTCredentials(proxyUsername, proxyPassword,ntlmHost,ntlmDomain)
                        : new UsernamePasswordCredentials(proxyUsername, proxyPassword);
                proxyAuthState = new AuthState();
                proxyAuthState.setCredentials(proxyCredentials);
            }
        }

        // Save HTTP parameters which we'll need when instantiating an HttpClient (even though it could get the
        // parameters from the connection manager)
        httpParams = basicHttpParams;
    }

    private CookieStore cookieStore;

    private URL url;
    private boolean connected = false;
    private HttpUriRequest method;
    private HttpResponse httpResponse = null;
    private byte[] requestBody;
    private Map<String, String[]> requestProperties = new LinkedHashMap<String, String[]>();    // LinkedHashMap<String lowercaseHeaderName, String[] headerValues>
    private HashMap<String, List<String>> responseHeaders;

    private String username;
    private String password;
    private String domain;

    public HTTPURLConnection(URL url) {
        super(url);
        this.url = url;
    }

    public void setRequestMethod(String methodName) throws ProtocolException {
        if (connected)
            throw new ProtocolException("Can't reset method: already connected");
        if ("GET".equals(methodName)) method = new HttpGet(url.toString());
        else if ("POST".equals(methodName)) method = new HttpPost(url.toString());
        else if ("HEAD".equals(methodName)) method = new HttpHead(url.toString());
        else if ("OPTIONS".equals(methodName)) method = new HttpOptions(url.toString());
        else if ("PUT".equals(methodName)) method = new HttpPut(url.toString());
        else if ("DELETE".equals(methodName)) method = new HttpDelete(url.toString());
        else if ("TRACE".equals(methodName)) method = new HttpTrace(url.toString());
        else throw new ProtocolException("Method " + methodName + " not supported");
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

    public void connect() throws IOException {
        if (!connected) {
            final String userInfo = url.getUserInfo();
            final boolean isAuthenticationRequestedWithUsername = username != null && !username.equals("");

            // Create the HTTP client and HTTP context for the client (we expect this to be fairly lightweight)
            final DefaultHttpClient httpClient = new DefaultHttpClient(connectionManager, httpParams);
            final HttpContext httpContext = new BasicHttpContext();

            // Set cookie store, creating a new one if none was provided to us
            if (cookieStore == null) cookieStore = new BasicCookieStore();
            httpClient.setCookieStore(cookieStore);

            // Set proxy and host authentication
            if (proxyAuthState != null) httpContext.setAttribute(ClientContext.PROXY_AUTH_STATE, proxyAuthState);
            if (userInfo != null || isAuthenticationRequestedWithUsername) {

                // Make authentication preemptive; interceptor is added first, as the Authentication header is added
                // by HttpClient's RequestTargetAuthentication which is itself an interceptor, so our interceptor
                // needs to run before RequestTargetAuthentication, otherwise RequestTargetAuthentication won't find
                // the appropriate AuthState/AuthScheme/Credentials in the HttpContext
                httpClient.addRequestInterceptor(preemptiveAuthHttpRequestInterceptor, 0);

                CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                httpContext.setAttribute(ClientContext.CREDS_PROVIDER, credentialsProvider);
                final AuthScope authScope = new AuthScope(url.getHost(), url.getPort());
                final Credentials credentials;
                if (userInfo != null) {
                    // Set username and optional password specified on URL
                    final int separatorPosition = userInfo.indexOf(":");
                    String username = separatorPosition == -1 ? userInfo : userInfo.substring(0, separatorPosition);
                    String password = separatorPosition == -1 ? "" : userInfo.substring(separatorPosition + 1);
                    // If the username/password contain special character, those character will be encoded, since we
                    // are getting this from a URL. Now do the decoding.
                    username = URLDecoder.decode(username, "utf-8");
                    password = URLDecoder.decode(password, "utf-8");
                    credentials = new UsernamePasswordCredentials(username, password);
                } else {
                    // Set username and password specified externally
                    credentials = domain == null
                            ? new UsernamePasswordCredentials(username, password == null ? "" : password)
                            : new NTCredentials(username, password, url.getHost(), domain);
                }
                credentialsProvider.setCredentials(authScope, credentials);
            }

            // If method has not been set, use GET
            // This can happen e.g. when this connection handler is used from URLFactory
            if (method == null)
                setRequestMethod("GET");

            // Set all headers,
            final boolean skipAuthorizationHeader = userInfo != null || username != null;
            for (final Map.Entry<String, String[]> currentEntry: requestProperties.entrySet()) {
                final String currentHeaderName = currentEntry.getKey();
                final String[] currentHeaderValues = currentEntry.getValue();
                for (final String currentHeaderValue: currentHeaderValues) {
                    // Skip over Authorization header if user authentication specified
                    if (skipAuthorizationHeader && currentHeaderName.toLowerCase().equals(Connection.AUTHORIZATION_HEADER.toLowerCase()))
                        continue;
                    method.addHeader(currentHeaderName, currentHeaderValue);
                }
            }

            // Create request entity with body
            if (method instanceof HttpEntityEnclosingRequest) {

                // Use the body that was set directly, or the result of writing to the OutputStream
                final byte[] body = (requestBody != null) ? requestBody : (os != null) ? os.toByteArray() : null;

                if (body != null) {
                    final Header contentTypeHeader = method.getFirstHeader("Content-Type"); // Header names are case-insensitive for comparison
                    if (contentTypeHeader == null)
                        throw new ProtocolException("Can't set request entity: Content-Type header is missing");
                    final ByteArrayEntity byteArrayEntity = new ByteArrayEntity(body);
                    byteArrayEntity.setContentType(contentTypeHeader);
                    ((HttpEntityEnclosingRequest) method).setEntity(byteArrayEntity);
                }
            }

            // Make request
            httpResponse = httpClient.execute(method, httpContext);
            connected = true;
        }
    }

    public InputStream getInputStream() throws IOException {
        if (!connected) connect();
        return httpResponse.getEntity().getContent();
    }

    public void setRequestBody(byte[] requestBody) throws IOException {
        this.requestBody = requestBody;
    }

    private ByteArrayOutputStream os = null;

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (os == null)
            os = new ByteArrayOutputStream();

        return os;
    }

    private void initResponseHeaders() {
        try {
            if (!connected) connect();
            if (responseHeaders == null) {
                responseHeaders = new HashMap<String, List<String>>();
                for (Header header: httpResponse.getAllHeaders())
                    responseHeaders.put(header.getName().toLowerCase(), Collections.singletonList(header.getValue()));
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * This method will be called by URLConnection.getLastModified(), URLConnection.getContentLength(), etc.
     */
    @Override
    public String getHeaderField(String name) {
        initResponseHeaders();
        // We return the first header value only. This is not really right, is it? But it will work for the few calls
        // done by URLConnection.
        final List<String> values = responseHeaders.get(name);
        return (values != null) ? values.get(0) : null;
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        initResponseHeaders();
        return responseHeaders;
    }

    @Override
    public void setRequestProperty(String key, String value) {
        super.setRequestProperty(key, value);
        requestProperties.put(key, new String[] { value });
    }

    @Override
    public void addRequestProperty(String key, String value) {
        super.addRequestProperty(key, value);
        StringConversions.addValueToStringArrayMap(requestProperties, key, value);
    }

    @Override
    public String getRequestProperty(String key) {
        // Not sure what should be returned so return the first value if any. But likely nobody is calling this method.
        final String[] values = requestProperties.get(key);
        return (values == null) ? null : values[0];
    }

    @Override
    public Map<String,List<String>> getRequestProperties() {
        return super.getRequestProperties();
    }

    public int getResponseCode() {
        return httpResponse.getStatusLine().getStatusCode();
    }

    public void disconnect() {
        try {
            // According to the HTTP Client tutorial, calling consumeContent() is the easiest way to ensure that all
            // content has been fully consumed, so that the connection could be safely returned to the connection pool.
            httpResponse.getEntity().consumeContent();
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    public void setUsername(String username) {
        this.username = username.trim();
    }

    public void setPassword(String password) {
        this.password = password.trim();
    }

    public void setDomain(String domain) {
		this.domain = domain.trim();
	}

    @Override
    public long getLastModified() {
        // Default implementation throws an exception if the header is not present, so optimize on calling side
        final String field = getHeaderField("last-modified");
        return (field != null) ? super.getLastModified() : 0;
    }
}
