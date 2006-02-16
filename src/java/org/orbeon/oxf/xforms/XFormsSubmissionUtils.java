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
package org.orbeon.oxf.xforms;

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xml.XMLUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URLConnection;

/**
 * Utilities for XForms submission processing.
 */
public class XFormsSubmissionUtils {

    public static XFormsModelSubmission.ConnectionResult doOptimized(PipelineContext pipelineContext, ExternalContext externalContext,
                                                                     XFormsModelSubmission xformsModelSubmission, String method, final String action, String mediatype, boolean doReplace,
                                                                     byte[] serializedInstance, String serializedInstanceString) {
        try {
            if (isPost(method) || isPut(method) || isGet(method)) {

                // Create requestAdapter depending on method
                final ForwardExternalContextRequestWrapper requestAdapter;
                final String effectiveResourceURI;
                {
                    if (isPost(method) || isPut(method)) {
                        // Simulate a POST or PUT
                        effectiveResourceURI = action;
                        requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                                effectiveResourceURI, method.toUpperCase(), (mediatype != null) ? mediatype : "application/xml", serializedInstance);
                    } else {
                        // Simulate a GET
                        {
                            final StringBuffer updatedActionStringBuffer = new StringBuffer(action);
                            if (serializedInstanceString != null) {
                                if (action.indexOf('?') == -1)
                                    updatedActionStringBuffer.append('?');
                                else
                                    updatedActionStringBuffer.append('&');
                                updatedActionStringBuffer.append(serializedInstanceString);
                            }
                            effectiveResourceURI = updatedActionStringBuffer.toString();
                        }
                        requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                                effectiveResourceURI, method.toUpperCase());
                    }
                }

                final ExternalContext.RequestDispatcher requestDispatcher = externalContext.getRequestDispatcher(action);
                final XFormsModelSubmission.ConnectionResult connectionResult = new XFormsModelSubmission.ConnectionResult(effectiveResourceURI) {
                    public void close() {
                        if (resultInputStream != null) {
                            try {
                                resultInputStream.close();
                            } catch (IOException e) {
                                throw new OXFException("Exception while closing input stream for action: " + action);
                            }
                        }
                    }
                };
                if (doReplace) {
                    // "the event xforms-submit-done is dispatched"
                    if (xformsModelSubmission != null)
                        xformsModelSubmission.getContainingDocument().dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(xformsModelSubmission));
                    // Just forward the reply
                    requestDispatcher.forward(requestAdapter, externalContext.getResponse());
                    connectionResult.dontHandleResponse = true;
                } else {
                    // We must intercept the reply
                    final ResponseAdapter responseAdapter = new ResponseAdapter();
                    requestDispatcher.include(requestAdapter, responseAdapter);

                    // Get response information that needs to be forwarded

                    // NOTE: Here, the resultCode is not propagated from the included resource
                    // when including Servlets. Similarly, it is not possible to obtain the
                    // included resource's content type or headers. Because of this we should not
                    // use an optimized submission from within a servlet.
                    connectionResult.resultCode = responseAdapter.getResponseCode();
                    connectionResult.resultMediaType = ProcessorUtils.XML_CONTENT_TYPE;
                    connectionResult.resultInputStream = responseAdapter.getInputStream();
                }

                return connectionResult;
            } else if (method.equals("multipart-post")) {
                // TODO
                throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
            } else if (method.equals("form-data-post")) {
                // TODO
                throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
            } else if (method.equals("urlencoded-post")) {
                throw new OXFException("xforms:submission: deprecated submission method requested: " + method);
            } else {
                throw new OXFException("xforms:submission: invalid submission method requested: " + method);
            }
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }

    /**
     * @param action absolute URL or absolute path (which must include the context path)
     */
    public static XFormsModelSubmission.ConnectionResult doRegular(PipelineContext pipelineContext, ExternalContext externalContext,
                                                                   String method, final String action, String mediatype, boolean doReplace,
                                                                   byte[] serializedInstance, String serializedInstanceString) {

        // Compute submission URL
        final URL submissionURL;
        submissionURL = createURL(action, serializedInstanceString, externalContext);

        // Perform submission
        final String scheme = submissionURL.getProtocol();
        if (scheme.equals("http") || scheme.equals("https") || (isGet(method) && (scheme.equals("file") || scheme.equals("oxf")))) {
            // http MUST be supported
            // https SHOULD be supported
            // file SHOULD be supported
            try {
                final URLConnection urlConnection = submissionURL.openConnection();
                final HttpURLConnection httpURLConnection = (urlConnection instanceof HttpURLConnection) ? (HttpURLConnection) urlConnection : null;
                if (isPost(method) || isPut(method) || isGet(method)) {
                    urlConnection.setDoInput(true);
                    urlConnection.setDoOutput(!isGet(method)); // Only if POST / PUT

                    if (httpURLConnection != null) {
                        httpURLConnection.setRequestMethod(method.toUpperCase());
                    }
                    if (!isGet(method))
                        urlConnection.setRequestProperty("content-type", (mediatype != null) ? mediatype : "application/xml");

                    // Forward cookies for session handling
                    // TODO: The Servlet spec mandates JSESSIONID as cookie name; we should only forward this cookie
                    final String[] cookies = (String[]) externalContext.getRequest().getHeaderValuesMap().get("cookie");
                    if (cookies != null) {
                        for (int i = 0; i < cookies.length; i++) {
                            final String cookie = cookies[i];
                            urlConnection.setRequestProperty("cookie", cookie);
                        }
                    }

                    // Forward authorization header
                    // TODO: This should probably not be done automatically
                    final String authorizationHeader = (String) externalContext.getRequest().getHeaderMap().get("Authorization");
                    if (authorizationHeader != null)
                        httpURLConnection.setRequestProperty("Authorization", authorizationHeader);

                    urlConnection.connect();

                    // Write request body if needed
                    if (!isGet(method)) {
                        final OutputStream os = urlConnection.getOutputStream();
                        try {
                            os.write(serializedInstance);
                        } finally {
                            if (os != null) {
                                try {
                                    os.close();
                                } catch (IOException e) {
                                    throw new OXFException("Exception while closing output stream for action: " + action);
                                }
                            }
                        }
                    }

                    // Create result
                    final XFormsModelSubmission.ConnectionResult connectionResult = new XFormsModelSubmission.ConnectionResult(submissionURL.toExternalForm()) {
                        public void close() {
                            if (resultInputStream != null) {
                                try {
                                    resultInputStream.close();
                                } catch (IOException e) {
                                    throw new OXFException("Exception while closing input stream for action: " + action);
                                }
                            }

                            if (httpURLConnection != null)
                                httpURLConnection.disconnect();
                        }
                    };

                    // Get response information that needs to be forwarded
                    connectionResult.resultCode = (httpURLConnection != null) ? httpURLConnection.getResponseCode() : 200;
                    final String contentType = urlConnection.getContentType();
                    connectionResult.resultMediaType = NetUtils.getContentTypeMediaType(contentType);
                    connectionResult.resultHeaders = urlConnection.getHeaderFields();
                    connectionResult.resultInputStream = urlConnection.getInputStream();

                    return connectionResult;

                } else if (method.equals("multipart-post")) {
                    // TODO
                    throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                } else if (method.equals("form-data-post")) {
                    // TODO
                    throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                } else if (method.equals("urlencoded-post")) {
                    throw new OXFException("xforms:submission: deprecated submission method requested: " + method);
                } else {
                    throw new OXFException("xforms:submission: invalid submission method requested: " + method);
                }
            } catch (IOException e) {
                throw new OXFException(e);
            }
        } else if (!isGet(method) && (scheme.equals("file") || scheme.equals("oxf"))) {
            // TODO
            // SHOULD be supported (should probably support oxf: as well)
            throw new OXFException("xforms:submission: submission URL scheme not yet implemented: " + scheme);
        } else if (scheme.equals("mailto")) {
            // TODO
            // MAY be supported
            throw new OXFException("xforms:submission: submission URL scheme not yet implemented: " + scheme);
        } else {
            throw new OXFException("xforms:submission: submission URL scheme not supported: " + scheme);
        }
    }

    public static URL createURL(String action, String searchString, ExternalContext externalContext) {
        URL resultURL;
        try {
            final String actionString;
            {
                final StringBuffer updatedActionStringBuffer = new StringBuffer(action);
                if (searchString != null) {
                    if (action.indexOf('?') == -1)
                        updatedActionStringBuffer.append('?');
                    else
                        updatedActionStringBuffer.append('&');
                    updatedActionStringBuffer.append(searchString);
                }
                actionString = updatedActionStringBuffer.toString();
            }

            if (actionString.startsWith("/")) {
                // Case of path absolute
                final String requestURL = externalContext.getRequest().getRequestURL();
                resultURL = URLFactory.createURL(requestURL, actionString);
            } else if (NetUtils.urlHasProtocol(actionString)) {
                // Case of absolute URL
                resultURL = URLFactory.createURL(actionString);
            } else {
                throw new OXFException("Invalid URL: " + actionString);
            }
        } catch (MalformedURLException e) {
            throw new OXFException("Invalid URL: " + action, e);
        }
        return resultURL;
    }

    public static boolean isGet(String method) {
        return method.equals("get") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "get"));
    }

    public static boolean isPost(String method) {
        return method.equals("post") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "post"));
    }

    public static boolean isPut(String method) {
        return method.equals("put") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "put"));
    }
}
