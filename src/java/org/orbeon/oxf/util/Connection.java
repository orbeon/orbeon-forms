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
package org.orbeon.oxf.util;

import org.apache.http.client.CookieStore;
import org.apache.log4j.Level;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.oxf.resources.handler.HTTPURLConnection;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class Connection {

    // NOTE: Could add a distinction for portlet session scope.
    public enum StateScope {
        NONE, REQUEST, SESSION, APPLICATION
    }

    public enum Method {
        GET, PUT, POST
    }

    public static final String AUTHORIZATION_HEADER = "Authorization";

    private static final StateScope DEFAULT_STATE_SCOPE = StateScope.SESSION;
    private static final String LOG_TYPE = "connection";

    public static final String HTTP_FORWARD_HEADERS_PROPERTY = "oxf.http.forward-headers";

    public static final String HTTP_STATE_PROPERTY = "oxf.http.state";
    private static final String HTTP_COOKIE_STORE_ATTRIBUTE = "oxf.http.cookie-store";

    private CookieStore cookieStore;
    private final StateScope stateScope = getStateScope();

    /**
     * Perform a connection to the given URL with the given parameters.
     *
     * This handles:
     *
     * o PUTting or POSTing a body
     * o handling username and password
     * o setting HTTP headers
     * o forwarding session cookies
     * o forwarding specified HTTP headers
     * o managing SOAP POST and GET a la XForms 1.1 (should this be here?)
     */
    public ConnectionResult open(ExternalContext externalContext, IndentedLogger indentedLogger, boolean logBody,
                                 String httpMethod, final URL connectionURL, String username, String password, String domain,
                                 String contentType, byte[] messageBody, Map<String, String[]> headerNameValues,
                                 String headersToForward) {

        indentedLogger.startHandleOperation(LOG_TYPE, "opening connection");
        try {
            final boolean isHTTPOrHTTPS = isHTTPOrHTTPS(connectionURL.getProtocol());

            // Caller might pass null here
            if (headerNameValues == null)
                headerNameValues = Collections.emptyMap();

            // Get state if possible
            if (isHTTPOrHTTPS)
                loadHttpState(externalContext, indentedLogger);

            // Get  the headers to forward if any
            final Map<String, String[]> headersMap = (externalContext.getRequest() != null) ?
                    getHeadersMap(externalContext, indentedLogger, username, headerNameValues, headersToForward) : headerNameValues;

            // Open the connection
            final ConnectionResult result = connect(indentedLogger, logBody, httpMethod, connectionURL, username, password, domain, contentType, messageBody, headersMap);

            // Save state if possible
            if (isHTTPOrHTTPS)
                saveHttpState(externalContext, indentedLogger);

            return result;
        } finally {
            // In case an exception is thrown in the body, still do adjust the logs
            indentedLogger.endHandleOperation();
        }
    }

    /**
     * Get header names and values to send given:
     *
     * o the incoming request
     * o a list of headers names and values to set
     * o authentication information including username
     * o a list of headers to forward
     *
     * @param externalContext   context
     * @param indentedLogger    logger
     * @param username          username
     * @param headerNameValues  LinkedHashMap<String headerName, String[] headerValues>
     * @param headersToForward  headers to forward
     * @return LinkedHashMap<String headerName, String[] headerValues>
     */
    private static Map<String, String[]> getHeadersMap(ExternalContext externalContext, IndentedLogger indentedLogger, String username,
                                    Map<String, String[]> headerNameValues, String headersToForward) {
        // Resulting header names and values to set
        final LinkedHashMap<String, String[]> headersMap = new LinkedHashMap<String, String[]>();

        // Get header forwarding information
        final Map<String, String> headersToForwardMap = getHeadersToForward(headersToForward);

        // Set headers if provided
        if (headerNameValues.size() > 0) {
            for (final Map.Entry<String, String[]> currentEntry: headerNameValues.entrySet()) {
                final String currentHeaderName = currentEntry.getKey();
                final String[] currentHeaderValues = currentEntry.getValue();
                // Set header
                headersMap.put(currentHeaderName, currentHeaderValues);
                // Remove from list of headers to forward below
                if (headersToForwardMap != null)
                    headersToForwardMap.remove(currentHeaderName.toLowerCase());
            }
        }

        // Forward cookies for session handling
        if (username == null) {

            // NOTES 2011-01-22:
            //
            // If this is requested when a page is generated, it turns out we cannot rely on a JSESSIONID that makes
            // sense right after authentication, even in the scenario where the JSESSIONID is clean, because Tomcat
            // replays the initial request. In other words the JSESSIONID cookie can be stale.
            //
            // This means that the forwarding done below often doesn't make sense.
            //
            // We could possibly allow it only for XForms Ajax/page updates, where the probability that JSESSIONID is
            // correct is greater.
            //
            // A stronger fix might be to simply disable JSESSIONID forwarding, or support a stronger SSO option.
            //
            // See: http://forge.ow2.org/tracker/?func=detail&atid=350207&aid=315104&group_id=168
            //      https://issues.apache.org/bugzilla/show_bug.cgi?id=50633
            //

            // START "NEW" 2009 ALGORITHM

            // 1. If there is an incoming JSESSIONID cookie, use it. The reason is that there is not necessarily an
            // obvious mapping between "session id" and JSESSIONID cookie value. With Tomcat, this works, but with e.g.
            // Websphere, you get session id="foobar" and JSESSIONID=0000foobar:-1. So we must first try to get the
            // incoming JSESSIONID. To do this, we get the cookie, then serialize it as a header.

            // TODO: ExternalContext must provide direct access to cookies
            final Object nativeRequest = externalContext.getNativeRequest();
            boolean sessionCookieSet = false;
            if (nativeRequest instanceof HttpServletRequest) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) nativeRequest;
                final Cookie[] cookies = httpServletRequest.getCookies();

                final StringBuilder sb = new StringBuilder();

                if (cookies != null) {

                    // Figure out if we need to forward session cookies. We only forward if there is the requested
                    // session id is the same as the current session. Otherwise, it means that the current session is no
                    // longer valid, or that the incoming cookie is out of date.
                    boolean forwardSessionCookies = false;
                    final ExternalContext.Session session = externalContext.getSession(false);
                    if (session != null) {
                        final String requestedSessionId = httpServletRequest.getRequestedSessionId();
                        if (session.getId().equals(requestedSessionId)) {
                            forwardSessionCookies = true;
                        }
                    }

                    if (forwardSessionCookies) {
                        for (final Cookie cookie: cookies) {
                            // This is the standard JSESSIONID cookie
                            final boolean isJSessionId = cookie.getName().equals("JSESSIONID");
                            // Remember if we've seen JSESSIONID
                                sessionCookieSet |= isJSessionId;

                            // Forward JSESSIONID and JSESSIONIDSSO for JBoss
                            if (isJSessionId || cookie.getName().equals("JSESSIONIDSSO")) {
                                // Multiple cookies in the header, separated with ";"
                                if (sb.length() > 0)
                                    sb.append("; ");

                                sb.append(cookie.getName());
                                sb.append('=');
                                sb.append(cookie.getValue());
                            }
                        }

                        if (sb.length() > 0) {
                            // One or more cookies were set
                            final String cookieString = sb.toString();
                            indentedLogger.logDebug(LOG_TYPE, "forwarding cookies",
                                    "cookie", cookieString,
                                    "requested session id", externalContext.getRequest().getRequestedSessionId());
                            StringConversions.addValueToStringArrayMap(headersMap, "Cookie", cookieString );
                        }
                    }
                }
            }

            // 2. If there is no incoming JSESSIONID cookie, try to make our own cookie. This may fail with e.g.
            // WebSphere.
            if (!sessionCookieSet) {
                final ExternalContext.Session session = externalContext.getSession(false);

                if (session != null) {

                    // This will work with Tomcat, but may not work with other app servers
                    StringConversions.addValueToStringArrayMap(headersMap, "Cookie", "JSESSIONID=" + session.getId());

                    if (indentedLogger.isDebugEnabled()) {

                        String incomingSessionHeader = null;
                        final String[] cookieHeaders = externalContext.getRequest(   ).getHeaderValuesMap().get("cookie");
                        if (cookieHeaders != null) {
                            for (final String cookie: cookieHeaders) {
                                if (cookie.indexOf("JSESSIONID") != -1) {
                                    incomingSessionHeader = cookie;
                                }
                            }
                        }

                        String incomingSessionCookie = null;
                        if (externalContext.getNativeRequest() instanceof HttpServletRequest) {
                            final Cookie[] cookies = ((HttpServletRequest) externalContext.getNativeRequest()).getCookies();
                            if (cookies != null) {
                                for (final Cookie cookie: cookies) {
                                    if (cookie.getName().equals("JSESSIONID")) {
                                        incomingSessionCookie = cookie.getValue();
                                    }
                                }
                            }
                        }

                        indentedLogger.logDebug(LOG_TYPE, "setting cookie",
                                "new session", Boolean.toString(session.isNew()),
                                "session id", session.getId(),
                                "requested session id", externalContext.getRequest().getRequestedSessionId(),
                                "incoming JSESSIONID cookie", incomingSessionCookie,
                                "incoming JSESSIONID header", incomingSessionHeader);
                    }
                }
            }

            // END "NEW" 2009 ALGORITHM
        }

        // Forward headers if needed
        // NOTE: Forwarding the "Cookie" header may yield unpredictable results because of the above work done w/ JSESSIONID
        if (headersToForwardMap != null) {

            final Map<String, String[]> requestHeaderValuesMap = externalContext.getRequest().getHeaderValuesMap();

            for (final Map.Entry<String, String> currentEntry: headersToForwardMap.entrySet()) {
                final String currentHeaderName = currentEntry.getValue();
                final String currentHeaderNameLowercase = currentEntry.getKey();

                // Get incoming header value (Map contains values in lowercase!)
                final String[] currentIncomingHeaderValues = requestHeaderValuesMap.get(currentHeaderNameLowercase);
                // Forward header if present
                if (currentIncomingHeaderValues != null) {
                    final boolean isAuthorizationHeader = currentHeaderNameLowercase.equalsIgnoreCase(Connection.AUTHORIZATION_HEADER);
                    if (!isAuthorizationHeader || isAuthorizationHeader && username == null) {
                        // Only forward Authorization header if there is no username provided
                        indentedLogger.logDebug(LOG_TYPE, "forwarding header",
                                "name", currentHeaderName, "value", currentIncomingHeaderValues.toString());
                        StringConversions.addValuesToStringArrayMap(headersMap, currentHeaderName, currentIncomingHeaderValues);
                    } else {
                        // Just log this information
                        indentedLogger.logDebug(LOG_TYPE,
                                "not forwarding Authorization header because username is present");
                    }
                }
            }
        }

        return headersMap;
    }

    private void loadHttpState(ExternalContext externalContext, IndentedLogger indentedLogger) {
        switch (stateScope) {
            case REQUEST:
                cookieStore = (CookieStore) externalContext.getRequest().getAttributesMap().get(HTTP_COOKIE_STORE_ATTRIBUTE);
                break;
            case SESSION:
                final ExternalContext.Session session = externalContext.getSession(false);
                if (session != null)
                    cookieStore = (CookieStore) session.getAttributesMap().get(HTTP_COOKIE_STORE_ATTRIBUTE);
                break;
            case APPLICATION:
                cookieStore = (CookieStore) externalContext.getAttributesMap().get(HTTP_COOKIE_STORE_ATTRIBUTE);
                break;
        }

        if (cookieStore != null) {
            indentedLogger.logDebug(LOG_TYPE, "loaded HTTP state", "scope", stateScope.toString().toLowerCase());
        } else {
            indentedLogger.logDebug(LOG_TYPE, "did not load HTTP state");
        }
    }

    private void saveHttpState(ExternalContext externalContext, IndentedLogger indentedLogger) {
        if (cookieStore != null) {
            switch (stateScope) {
                case REQUEST:
                    externalContext.getRequest().getAttributesMap().put(HTTP_COOKIE_STORE_ATTRIBUTE, cookieStore);
                    break;
                case SESSION:
                    final ExternalContext.Session session = externalContext.getSession(false);
                    if (session != null)
                        session.getAttributesMap().put(HTTP_COOKIE_STORE_ATTRIBUTE, cookieStore);
                    break;
                case APPLICATION:
                    externalContext.getAttributesMap().put(HTTP_COOKIE_STORE_ATTRIBUTE, cookieStore);
                    break;
            }

            if (indentedLogger.isDebugEnabled()) {
                // Log information about state
                if (cookieStore != null) {
                    final StringBuilder sb = new StringBuilder();
                    for (org.apache.http.cookie.Cookie cookie: cookieStore.getCookies()) {
                        if (sb.length() > 0) sb.append(" | ");
                        sb.append(cookie.getName());
                    }
                    indentedLogger.logDebug(LOG_TYPE, "saved HTTP state",
                            "scope", stateScope.toString().toLowerCase(),
                            (sb.length() > 0) ? "cookie names" : null, sb.toString());
                }
            }
        }
    }

    /**
     * Open the connection. This sends request headers, request body, and reads status and response headers.
     *
     * @param indentedLogger    logger
     * @param logBody           whether the request/response body must be logged
     * @param httpMethod        method i.e. GET, etc.
     * @param connectionURL     URL to connect to
     * @param username          username or null
     * @param password          password or null
     * @param contentType       content type for POST and PUT
     * @param messageBody       request body for POST and PUT
     * @param headersMap        LinkedHashMap<String headerName, String[] headerValues> headers to set
     * @return                  connection result
     */
    private ConnectionResult connect(IndentedLogger indentedLogger, boolean logBody,
                                     String httpMethod, final URL connectionURL, String username, String password,
                                     String domain, String contentType, byte[] messageBody, Map<String, String[]> headersMap) {

        final boolean isDebugEnabled = indentedLogger.isDebugEnabled();

        // Perform connection
        final String scheme = connectionURL.getProtocol();
        if (isHTTPOrHTTPS(scheme) || (httpMethod.equals("GET") && (scheme.equals("file") || scheme.equals("oxf")))) {
            // http MUST be supported
            // https SHOULD be supported
            // file SHOULD be supported
            try {
                // Create URL connection object
                final URLConnection urlConnection = connectionURL.openConnection();
                final HTTPURLConnection httpURLConnection = (urlConnection instanceof HTTPURLConnection) ? (HTTPURLConnection) urlConnection : null;

                // Whether a message body must be sent
                final boolean hasRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT");

                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(hasRequestBody);

                // Configure HTTPURLConnection
                if (httpURLConnection != null) {
                    // Set state if possible
                    httpURLConnection.setCookieStore(this.cookieStore);

                    // Set method
                    httpURLConnection.setRequestMethod(httpMethod);

                    // Set credentials
                    if (username != null) {

                        httpURLConnection.setUsername(username);
                        if (password != null)
                           httpURLConnection.setPassword(password);
                        if (domain != null)
                        	httpURLConnection.setDomain(domain);
                    }
                }

                // Update request headers
                {
                    // Handle SOAP
                    // Set request Content-Type, SOAPAction or Accept header if needed
                    final boolean didSOAP = handleSOAP(indentedLogger, httpMethod, headersMap, contentType, hasRequestBody);

                    // Set request content type
                    if (!didSOAP && hasRequestBody) {
                        final String actualContentType = (contentType != null) ? contentType : "application/xml";
                        headersMap.put("Content-Type", new String[] { actualContentType });
                        indentedLogger.logDebug(LOG_TYPE, "setting header", "Content-Type", actualContentType);
                    }
                }

                // Set headers on connection
                final List<String> headersToLog;
                if (headersMap != null && headersMap.size() > 0) {

                    headersToLog = isDebugEnabled ? new ArrayList<String>() : null;

                    for (Map.Entry<String,String[]> currentEntry: headersMap.entrySet()) {
                        final String currentHeaderName = currentEntry.getKey();
                        final String[] currentHeaderValues = currentEntry.getValue();
                        if (currentHeaderValues != null) {
                            // Add all header values as "request properties"
                            for (String currentHeaderValue: currentHeaderValues) {
                                urlConnection.addRequestProperty(currentHeaderName, currentHeaderValue);

                                if (headersToLog != null) {
                                    headersToLog.add(currentHeaderName);
                                    headersToLog.add(currentHeaderValue);
                                }
                            }
                        }
                    }
                } else{
                    headersToLog = null;
                }

                // Log request details except body
                if (isDebugEnabled) {
                    // Basic connection information
                    final URI connectionURI;
                    try {
                        String userInfo = connectionURL.getUserInfo();
                        if (userInfo != null) {
                            final int colonIndex = userInfo.indexOf(':');
                            if (colonIndex != -1)
                                userInfo = userInfo.substring(0, colonIndex + 1) + "xxxxxxxx";// hide password in logs
                        }
                        connectionURI = new URI(connectionURL.getProtocol(), userInfo, connectionURL.getHost(),
                                connectionURL.getPort(), connectionURL.getPath(), connectionURL.getQuery(), connectionURL.getRef());
                    } catch (URISyntaxException e) {
                        throw new OXFException(e);
                    }
                    indentedLogger.logDebug(LOG_TYPE, "opening URL connection", "method", httpMethod,
                            "URL", connectionURI.toString(), "request Content-Type", contentType);

                    // Log all headers
                    if (headersToLog != null) {
                        final String[] strings = new String[headersToLog.size()];
                        indentedLogger.logDebug(LOG_TYPE, "request headers", headersToLog.toArray(strings));
                    }
                }

                // Write request body if needed
                if (hasRequestBody) {

                    // Case of empty body
                    if (messageBody == null)
                        messageBody = new byte[0];

                    // Log message body for debugging purposes
                    if (logBody)
                        logRequestBody(indentedLogger, contentType, messageBody);

                    // Set request body on connection
                    httpURLConnection.setRequestBody(messageBody);
                }

                // Connect
                urlConnection.connect();

                if (httpURLConnection != null) {
                    // Get state if possible
                    // This is either the state we set above before calling connect(), or a new state if we didn't provide any
                    this.cookieStore = httpURLConnection.getCookieStore();
                }

                // Create result
                final ConnectionResult connectionResult = new ConnectionResult(connectionURL.toExternalForm()) {
                    @Override
                    public void close() {
                        if (getResponseInputStream() != null) {
                            try {
                                getResponseInputStream().close();
                            } catch (IOException e) {
                                throw new OXFException("Exception while closing input stream for action: " + connectionURL);
                            }
                        }

                        if (httpURLConnection != null)
                            httpURLConnection.disconnect();
                    }
                };

                // Get response information that needs to be forwarded
                {
                    // Status code
                    connectionResult.statusCode = (httpURLConnection != null) ? httpURLConnection.getResponseCode() : 200;

                    // Headers
                    connectionResult.responseHeaders = urlConnection.getHeaderFields();
                    connectionResult.setLastModified(NetUtils.getLastModifiedAsLong(urlConnection));

                    // Content-Type
                    connectionResult.setResponseContentType(urlConnection.getContentType(), "application/xml");
                }

                // Log response details except body
                if (isDebugEnabled) {
                    connectionResult.logResponseDetailsIfNeeded(indentedLogger, Level.DEBUG, LOG_TYPE);
                }

                // Response stream
                connectionResult.setResponseInputStream(urlConnection.getInputStream());

                // Log response body
                if (isDebugEnabled) {
                    connectionResult.logResponseBody(indentedLogger, Level.DEBUG, LOG_TYPE, logBody);
                }

                return connectionResult;

            } catch (IOException e) {
                throw new ValidationException(e, new LocationData(connectionURL.toExternalForm(), -1, -1));
            }
        } else if (!httpMethod.equals("GET") && (scheme.equals("file") || scheme.equals("oxf"))) {
            // TODO: implement writing to file: and oxf:
            // SHOULD be supported (should probably support oxf: as well)
            throw new OXFException("submission URL scheme not yet implemented: " + scheme);
        } else if (scheme.equals("mailto")) {
            // TODO: implement sending mail
            // MAY be supported
            throw new OXFException("submission URL scheme not yet implemented: " + scheme);
        } else {
            throw new OXFException("submission URL scheme not supported: " + scheme);
        }
    }

    /**
     * Add SOAP-related headers if needed.
     *
     * TODO: check this logic against the latest XForms 1.1.
     *
     * @param indentedLogger    logger
     * @param httpMethod        method
     * @param headersMap        existing headers
     * @param contentType       content-type requested
     * @param hasRequestBody    whether there is a request body
     * @return                  true iif a SOAP request was detected and headers added
     */
    private boolean handleSOAP(IndentedLogger indentedLogger, String httpMethod, Map<String, String[]> headersMap, String contentType, boolean hasRequestBody) {
        final String contentTypeMediaType = NetUtils.getContentTypeMediaType(contentType);
        if (hasRequestBody) {
            if (httpMethod.equals("POST") && NetUtils.APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                // SOAP POST

                final Map<String, String> parameters = NetUtils.getContentTypeParameters(contentType);
                final StringBuilder sb = new StringBuilder("text/xml");

                // Extract charset parameter if present
                // TODO: We have the body as bytes already, using the xforms:submission/@encoding attribute, so this is not right.
                if (parameters != null) {
                    final String charsetParameter = parameters.get("charset");
                    if (charsetParameter != null) {
                        // Append charset parameter
                        sb.append("; charset=");
                        sb.append(charsetParameter);
                    }
                }

                // Set new content type
                final String overriddenContentType = sb.toString();
                headersMap.put("Content-Type", new String[] { overriddenContentType });

                // Extract action parameter if present
                String actionParameter = null;
                if (parameters != null) {
                    actionParameter = parameters.get("action");
                    if (actionParameter != null) {
                        // Set SOAPAction header
                        headersMap.put("SOAPAction", new String[] { actionParameter });
                    }
                }

                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(LOG_TYPE, "found SOAP POST",
                            "request Content-Type", overriddenContentType,
                            "request SOAPAction header", actionParameter);
                }

                return true;
            }
        } else {
            if (httpMethod.equals("GET") && NetUtils.APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                // SOAP GET

                final Map<String, String> parameters = NetUtils.getContentTypeParameters(contentType);
                final StringBuilder sb = new StringBuilder(NetUtils.APPLICATION_SOAP_XML);

                // Extract charset parameter if present
                if (parameters != null) {
                    final String charsetParameter = parameters.get("charset");
                    if (charsetParameter != null) {
                        // Append charset parameter
                        sb.append("; charset=");
                        sb.append(charsetParameter);
                    }
                }

                // Set Accept header with optional charset
                final String acceptHeader = sb.toString();
                headersMap.put("Accept", new String[] { acceptHeader });

                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(LOG_TYPE, "found SOAP GET", "request Accept header", acceptHeader);
                }

                return true;
            }
        }

        return false;
    }

    private boolean isHTTPOrHTTPS(String scheme) {
        return scheme.equals("http") || scheme.equals("https");
    }

    /**
     * Get user-specified list of headers to forward.
     *
     * @param headersToForward  space-separated list of headers to forward
     * @return  Map<String, String> lowercase header name to user-specified header name or null if null String passed
     */
    private static Map<String, String> getHeadersToForward(String headersToForward) {
        if (headersToForward == null)
            return null;

        final Map<String, String> result = new HashMap<String, String>();
        for (final StringTokenizer st = new StringTokenizer(headersToForward, ", "); st.hasMoreTokens();) {
            final String currentHeaderName = st.nextToken().trim();
            final String currentHeaderNameLowercase = currentHeaderName.toLowerCase();
            result.put(currentHeaderNameLowercase, currentHeaderName);
        }
        return result;
    }

    public static void logRequestBody(IndentedLogger indentedLogger, String mediatype, byte[] messageBody) throws UnsupportedEncodingException {
        if (XMLUtils.isXMLMediatype(mediatype) || XMLUtils.isTextOrJSONContentType(mediatype) || (mediatype != null && mediatype.equals("application/x-www-form-urlencoded"))) {
            indentedLogger.logDebug("submission", "setting request body", "body", new String(messageBody, "UTF-8"));
        } else {
            indentedLogger.logDebug("submission", "setting binary request body");
        }
    }

    private static StateScope getStateScope() {
        // NOTE: Property values are same as enum except in lowercase
        final PropertySet propertySet = org.orbeon.oxf.properties.Properties.instance().getPropertySet();
        final String stateScope = propertySet.getString(HTTP_STATE_PROPERTY, DEFAULT_STATE_SCOPE.name().toLowerCase());
        return StateScope.valueOf(stateScope.toUpperCase());
    }

    /**
     * Get the list of headers to forward from the configuration properties
     *
     * @return space-separated list of header names
     */
    public static String getForwardHeaders() {
        final PropertySet propertySet = org.orbeon.oxf.properties.Properties.instance().getPropertySet();
        return propertySet.getString(HTTP_FORWARD_HEADERS_PROPERTY, "");
    }
}
