/**
 *  Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.util;

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.resources.handler.HTTPURLConnection;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.properties.PropertySet;
import org.orbeon.saxon.om.FastStringBuffer;
import org.apache.commons.httpclient.HttpState;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Cookie;
import java.net.URL;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.*;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class Connection {

    // NOTE: Could add a distinction for portlet session scope.
    public enum StateScope {
        NONE, REQUEST, SESSION, APPLICATION
    }
    
    private static final StateScope DEFAULT_STATE_SCOPE = StateScope.SESSION;

    public static final String HTTP_CLIENT_STATE_PROPERTY = "oxf.http.state";
    private static final String HTTP_CLIENT_STATE_ATTRIBUTE = "oxf.http.state";

    private HttpState httpState;
    private final StateScope stateScope = getStateScope();

    /**
     * Perform a connection to the given URL with the given parameters.
     *
     * This handles:
     *
     * o PUTting or POSTing a body
     * o handling username and password
     * o setting HTTP heades
     * o forwarding session cookies
     * o forwarding specified HTTP headers
     * o managing SOAP POST and GET a la XForms 1.1 (should this be here?)
     */
    public ConnectionResult open(ExternalContext externalContext, IndentedLogger indentedLogger,
                                 String httpMethod, final URL connectionURL, String username, String password, String contentType,
                                 byte[] messageBody, Map<String, String[]> headerNameValues, String headersToForward) {

        final boolean isHTTPOrHTTPS = isHTTPOrHTTPS(connectionURL.getProtocol());

        // Get state if possible
        if (isHTTPOrHTTPS)
            loadHttpState(externalContext, indentedLogger);

        // Get  the headers to forward if any
        final Map<String, String[]> headersMap = (externalContext.getRequest() != null) ?
                getHeadersMap(externalContext, indentedLogger, username, headerNameValues, headersToForward) : headerNameValues;
        // Open the connection
        final ConnectionResult result = open(indentedLogger, httpMethod, connectionURL, username, password, contentType, messageBody, headersMap);

        // Save state if possible
        if (isHTTPOrHTTPS)
            saveHttpState(externalContext, indentedLogger);

        return result;
    }

    /**
     * Get header names and values to send given:
     *
     * o the incoming request
     * o a list of headers names and values to set
     * o authentication information including username
     * o a list of headers to forward
     *
     * @param headerNameValues  LinkedHashMap<String headerName, String[] headerValues>
     *
     * @return LinkedHashMap<String headerName, String[] headerValues>
     */
    private static Map<String, String[]> getHeadersMap(ExternalContext externalContext, IndentedLogger indentedLogger, String username,
                                    Map<String, String[]> headerNameValues, String headersToForward) {
        // Resulting header names and values to set
        final LinkedHashMap<String, String[]> headersMap = new LinkedHashMap<String, String[]>();

        // Get header forwarding information
        final Map<String, String> headersToForwardMap = getHeadersToForward(headersToForward);

        // Set headers if provided
        if (headerNameValues != null && headerNameValues.size() > 0) {
            for (Iterator<Map.Entry<String,String[]>> i = headerNameValues.entrySet().iterator(); i.hasNext();) {
                final Map.Entry<String,String[]> currentEntry = i.next();
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
        boolean sessionCookieSet = false;
        if (username == null) {

            // START NEW ALGORITHM

            // 1. If there is an incoming JSESSIONID cookie, use it. The reason is that there is not necessarily an
            // obvious mapping between "session id" and JSESSIONID cookie value. With Tomcat, this works, but with e.g.
            // Websphere, you get session id="foobar" and JSESSIONID=0000foobar:-1. So we must first try to get the
            // incoming JSESSIONID. To do this, we get the cookie, then serialize it as a header.

            // TODO: ExternalContext must provide direct access to cookies
            final Object nativeRequest = externalContext.getNativeRequest();
            if (nativeRequest instanceof HttpServletRequest) {
                final HttpServletRequest httpServletRequest = (HttpServletRequest) nativeRequest;
                final Cookie[] cookies = httpServletRequest.getCookies();

                StringBuffer sb = new StringBuffer();

                if (cookies != null) {
                    for (int i = 0; i < cookies.length; i++) {
                        final Cookie cookie = cookies[i];

                        // This is the standard JSESSIONID cookie
                        final boolean isJsessionId = cookie.getName().equals("JSESSIONID");
                        // Remember if we've seen JSESSIONID
                        sessionCookieSet |= isJsessionId;

                        // Forward JSESSIONID and JSESSIONIDSSO for JBoss
                        if (isJsessionId || cookie.getName().equals("JSESSIONIDSSO")) {
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
                        indentedLogger.logDebug("connection", "forwarding cookies", "cookie", cookieString);
                        StringUtils.addValueToStringArrayMap(headersMap, "Cookie", cookieString );

//                            CookieTools.getCookieHeaderValue(cookie, sb);
                    }
                }
            }

            // 2. If there is no incoming JSESSIONID cookie, try to make our own cookie. This may fail with e.g.
            // Websphere.
            if (!sessionCookieSet) {
                final ExternalContext.Session session = externalContext.getSession(false);

                if (session != null) {

                    // This will work with Tomcat, but may not work with other app servers
                    StringUtils.addValueToStringArrayMap(headersMap, "Cookie", "JSESSIONID=" + session.getId());

                    if (indentedLogger.isDebugEnabled()) {

                        String incomingSessionHeader = null;
                        final String[] cookieHeaders = (String[]) externalContext.getRequest(   ).getHeaderValuesMap().get("cookie");
                        if (cookieHeaders != null) {
                            for (int i = 0; i < cookieHeaders.length; i++) {
                                final String cookie = cookieHeaders[i];
                                if (cookie.indexOf("JSESSIONID") != -1) {
                                    incomingSessionHeader = cookie;
                                }
                            }
                        }

                        String incomingSessionCookie = null;
                        if (externalContext.getNativeRequest() instanceof HttpServletRequest) {
                            final Cookie[] cookies = ((HttpServletRequest) externalContext.getNativeRequest()).getCookies();
                            if (cookies != null) {
                                for (int i = 0; i < cookies.length; i++) {
                                    final Cookie cookie = cookies[i];
                                    if (cookie.getName().equals("JSESSIONID")) {
                                        incomingSessionCookie = cookie.getValue();
                                    }
                                }
                            }
                        }

                        indentedLogger.logDebug("connection", "setting cookie",
                                "new session", Boolean.toString(session.isNew()),
                                "session id", session.getId(),
                                "requested session id", externalContext.getRequest().getRequestedSessionId(),
                                "incoming JSESSIONID cookie", incomingSessionCookie,
                                "incoming JSESSIONID header", incomingSessionHeader);
                    }
                }
            }

            // END NEW ALGORITHM
        }

        // Forward headers if needed
        // NOTE: Forwarding the "Cookie" header may yield unpredictable results because of the above work done w/ JSESSIONID
        if (headersToForwardMap != null) {

            final Map<String, String[]> requestHeaderValuesMap = externalContext.getRequest().getHeaderValuesMap();

            for (Iterator<Map.Entry<String,String>> i = headersToForwardMap.entrySet().iterator(); i.hasNext();) {
                final Map.Entry<String,String> currentEntry = i.next();
                final String currentHeaderName = currentEntry.getValue();
                final String currentHeaderNameLowercase = currentEntry.getKey();

                // Get incoming header value (Map contains values in lowercase!)
                final String[] currentIncomingHeaderValues = requestHeaderValuesMap.get(currentHeaderNameLowercase);
                // Forward header if present
                if (currentIncomingHeaderValues != null) {
                    final boolean isAuthorizationHeader = currentHeaderNameLowercase.equals("authorization");
                    if (!isAuthorizationHeader || isAuthorizationHeader && username == null) {
                        // Only forward Authorization header if there is no username provided
                        indentedLogger.logDebug("connection", "forwarding header",
                                "name", currentHeaderName, "value", currentIncomingHeaderValues.toString());
                        StringUtils.addValuesToStringArrayMap(headersMap, currentHeaderName, currentIncomingHeaderValues);
                    } else {
                        // Just log this information
                        indentedLogger.logDebug("connection",
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
                httpState = (HttpState) externalContext.getRequest().getAttributesMap().get(HTTP_CLIENT_STATE_ATTRIBUTE);
                break;
            case SESSION:
                final ExternalContext.Session session = externalContext.getSession(false);
                if (session != null)
                    httpState = (HttpState) session.getAttributesMap().get(HTTP_CLIENT_STATE_ATTRIBUTE);
                break;
            case APPLICATION:
                httpState = (HttpState) externalContext.getAttributesMap().get(HTTP_CLIENT_STATE_ATTRIBUTE);
                break;
        }

        if (httpState != null) {
            indentedLogger.logDebug("connection", "loaded HTTP state", "scope", stateScope.toString().toLowerCase());
        } else {
            indentedLogger.logDebug("connection", "did not load HTTP state");
        }
    }

    private void saveHttpState(ExternalContext externalContext, IndentedLogger indentedLogger) {
        if (httpState != null) {
            switch (stateScope) {
                case REQUEST:
                    externalContext.getRequest().getAttributesMap().put(HTTP_CLIENT_STATE_ATTRIBUTE, httpState);
                    break;
                case SESSION:
                    final ExternalContext.Session session = externalContext.getSession(false);
                    if (session != null)
                        session.getAttributesMap().put(HTTP_CLIENT_STATE_ATTRIBUTE, httpState);
                    break;
                case APPLICATION:
                    externalContext.getAttributesMap().put(HTTP_CLIENT_STATE_ATTRIBUTE, httpState);
                    break;
            }

            if (indentedLogger.isDebugEnabled()) {
                // Log information about state
                final org.apache.commons.httpclient.Cookie[] cookies = httpState.getCookies();

                final StringBuilder sb = new StringBuilder();
                for (org.apache.commons.httpclient.Cookie cookie: cookies) {
                    if (sb.length() > 0)
                        sb.append(" | ");
                    sb.append(cookie.getName());
                }

                indentedLogger.logDebug("connection", "saved HTTP state",
                        "scope", stateScope.toString().toLowerCase(),
                        "cookie names", sb.toString());
            }
        }
    }

    /**
     *
     * @param indentedLogger
     * @param httpMethod
     * @param connectionURL
     * @param username
     * @param password
     * @param contentType
     * @param messageBody
     * @param headersMap        LinkedHashMap<String headerName, String[] headerValues>
     * @return
     */
    private ConnectionResult open(IndentedLogger indentedLogger,
                                 String httpMethod, final URL connectionURL, String username, String password,
                                 String contentType, byte[] messageBody, Map<String, String[]> headersMap) {

        // Perform connection
        final String scheme = connectionURL.getProtocol();
        if (isHTTPOrHTTPS(scheme) || (httpMethod.equals("GET") && (scheme.equals("file") || scheme.equals("oxf")))) {
            // http MUST be supported
            // https SHOULD be supported
            // file SHOULD be supported
            try {
                if (indentedLogger.isDebugEnabled()) {
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
                    indentedLogger.logDebug("connection", "opening URL connection",
                            "URL", connectionURI.toString());
                }

                final URLConnection urlConnection = connectionURL.openConnection();
                final HTTPURLConnection httpURLConnection = (urlConnection instanceof HTTPURLConnection) ? (HTTPURLConnection) urlConnection : null;

                // Whether a message body must be sent
                final boolean hasRequestBody = httpMethod.equals("POST") || httpMethod.equals("PUT");

                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(hasRequestBody);

                if (httpURLConnection != null) {
                    // Set state if possible
                    httpURLConnection.setHttpState(this.httpState);

                    // Set method
                    httpURLConnection.setRequestMethod(httpMethod);

                    // Set credentials
                    if (username != null) {

                        httpURLConnection.setUsername(username);
                        if (password != null)
                           httpURLConnection.setPassword(password);
                    }
                }
                final String contentTypeMediaType = NetUtils.getContentTypeMediaType(contentType);
                if (hasRequestBody) {
                    if (httpMethod.equals("POST") && NetUtils.APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                        // SOAP POST

                        indentedLogger.logDebug("connection", "found SOAP POST");

                        final Map<String, String> parameters = NetUtils.getContentTypeParameters(contentType);
                        final FastStringBuffer sb = new FastStringBuffer("text/xml");

                        // Extract charset parameter if present
                        // TODO: We have the body as bytes already, using the xforms:submission/@encoding attribute, so this is not right.
                        if (parameters != null) {
                            final String charsetParameter = parameters.get("charset");
                            if (charsetParameter != null) {
                                // Append charset parameter
                                sb.append("; ");
                                sb.append(charsetParameter);
                            }
                        }

                        // Set new content type
                        urlConnection.setRequestProperty("Content-Type", sb.toString());

                        // Extract action parameter if present
                        if (parameters != null) {
                            final String actionParameter = parameters.get("action");
                            if (actionParameter != null) {
                                // Set SOAPAction header
                                urlConnection.setRequestProperty("SOAPAction", actionParameter);
                                indentedLogger.logDebug("connection", "setting header", "SOAPAction", actionParameter);
                            }
                        }
                    } else {
                        urlConnection.setRequestProperty("Content-Type", (contentType != null) ? contentType : "application/xml");
                    }
                } else {
                    if (httpMethod.equals("GET") && NetUtils.APPLICATION_SOAP_XML.equals(contentTypeMediaType)) {
                        // SOAP GET
                        indentedLogger.logDebug("connection", "found SOAP GET");

                        final Map<String, String> parameters = NetUtils.getContentTypeParameters(contentType);
                        final FastStringBuffer sb = new FastStringBuffer(NetUtils.APPLICATION_SOAP_XML);

                        // Extract charset parameter if present
                        if (parameters != null) {
                            final String charsetParameter = parameters.get("charset");
                            if (charsetParameter != null) {
                                // Append charset parameter
                                sb.append("; ");
                                sb.append(charsetParameter);
                            }
                        }

                        // Set Accept header with optional charset
                        urlConnection.setRequestProperty("Accept", sb.toString());
                    }
                }

                // Set headers if provided
                if (headersMap != null && headersMap.size() > 0) {
                    for (Iterator<Map.Entry<String,String[]>> i = headersMap.entrySet().iterator(); i.hasNext();) {
                        final Map.Entry<String,String[]> currentEntry = i.next();
                        final String currentHeaderName = currentEntry.getKey();
                        final String[] currentHeaderValues = currentEntry.getValue();
                        if (currentHeaderValues != null) {
                            // Add all header values as "request properties"
                            for (int j = 0; j < currentHeaderValues.length; j++) {
                                final String currentHeaderValue = currentHeaderValues[j];
                                urlConnection.addRequestProperty(currentHeaderName, currentHeaderValue);
                            }
                        }
                    }
                }

                // Write request body if needed
                if (hasRequestBody) {

                    // Case of empty body
                    if (messageBody == null)
                        messageBody = new byte[0];

                    // Log message mody for debugging purposes
                    if (indentedLogger.isDebugEnabled())
                        logRequestBody(indentedLogger, contentType, messageBody);

                    // Set request body on connection
                    httpURLConnection.setRequestBody(messageBody);
                }

                // Connect
                urlConnection.connect();

                if (httpURLConnection != null) {
                    // Get state if possible
                    // This is either the state we set above before calling connect(), or a new state if we didn't provide any
                    this.httpState = httpURLConnection.getHttpState();
                }

                // Create result
                final ConnectionResult connectionResult = new ConnectionResult(connectionURL.toExternalForm()) {
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
                connectionResult.statusCode = (httpURLConnection != null) ? httpURLConnection.getResponseCode() : 200;
                final String receivedContentType = urlConnection.getContentType();

                connectionResult.setResponseContentType(receivedContentType != null ? receivedContentType : "application/xml");
                connectionResult.responseHeaders = urlConnection.getHeaderFields();
                connectionResult.setLastModified(NetUtils.getLastModifiedAsLong(urlConnection));
                connectionResult.setResponseInputStream(urlConnection.getInputStream());

                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug("connection", "results", "status", Integer.toString(connectionResult.statusCode),
                            "content-type", receivedContentType,
                            "used content-type", connectionResult.getResponseContentType());
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
        if (XMLUtils.isXMLMediatype(mediatype) || XMLUtils.isTextContentType(mediatype) || (mediatype != null && mediatype.equals("application/x-www-form-urlencoded"))) {
            indentedLogger.logDebug("submission", "setting request body",
                    "mediatype", mediatype, "body", new String(messageBody, "UTF-8"));
        } else {
            indentedLogger.logDebug("submission", "setting binary request body", "mediatype", mediatype);
        }
    }

    private static StateScope getStateScope() {
        // NOTE: Property values are same as enum except in lowercase
        final PropertySet propertySet = org.orbeon.oxf.properties.Properties.instance().getPropertySet();
        final String proxyHost = propertySet.getString(HTTP_CLIENT_STATE_PROPERTY, DEFAULT_STATE_SCOPE.name().toLowerCase());
        return StateScope.valueOf(proxyHost.toUpperCase());
    }
}
