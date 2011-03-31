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
package org.orbeon.oxf.xforms.submission;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xml.XMLUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class BaseSubmission implements Submission {

    protected final XFormsModelSubmission submission;
    protected final XFormsContainingDocument containingDocument;

    protected BaseSubmission(XFormsModelSubmission submission) {
        this.submission = submission;
        this.containingDocument = submission.getContainingDocument();
    }

    protected String getAbsoluteSubmissionURL(String resolvedActionOrResource, String queryString, boolean isNorewrite) {

        if ("resource".equals(submission.getUrlType())) {
            // In case, for some reason, author forces a resource URL

            // NOTE: Before 2009-10-08, there was some code performing custom rewriting in portlet mode. That code was
            // very unclear and was removed as it seemed like resolveResourceURL() should handle all cases.

            return XFormsUtils.resolveResourceURL(containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    isNorewrite ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE_NO_CONTEXT : ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        } else {
            // Regular case of service URL

            // NOTE: If the resource or service URL does not start with a protocol or with '/', the URL is resolved against
            // the request path, then against the service base. Example in servlet environment:
            //
            // o action path: my/service
            // o request URL: http://orbeon.com/orbeon/myapp/mypage
            // o request path: /myapp/mypage
            // o service base: http://services.com/myservices/
            // o resulting service URL: http://services.com/myservices/myapp/my/service

            return XFormsUtils.resolveServiceURL(containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    isNorewrite ? ExternalContext.Response.REWRITE_MODE_ABSOLUTE_NO_CONTEXT : ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        }
    }

    protected Map<String, String[]> evaluateHeaders(XFormsContextStack contextStack) {
        try {
            return Headers.evaluateHeaders(submission.getXBLContainer(containingDocument), contextStack,
                    submission.getEffectiveId(), submission.getSubmissionElement());
        } catch (OXFException e) {
            throw new XFormsSubmissionException(submission, e, e.getMessage(), "processing <header> elements");
        }
    }

    /**
     * Submit the Callable for synchronous or asynchronous execution.
     *
     * @param p                 parameters
     * @param p2                parameters
     * @param callable          callable performing the submission
     * @return ConnectionResult or null if asynchronous
     * @throws Exception
     */
    protected SubmissionResult submitCallable(final XFormsModelSubmission.SubmissionParameters p,
                                              final XFormsModelSubmission.SecondPassParameters p2,
                                              final Callable<SubmissionResult> callable) throws Exception {
        if (p2.isAsynchronous) {

            // Tell XFCD that we have one more async submission
            containingDocument.getAsynchronousSubmissionManager(true).addAsynchronousSubmission(callable);

            // Tell caller he doesn't need to do anything
            return null;
        } else if (p.isDeferredSubmissionSecondPass && p.isReplaceAll) {
            // Tell XFCD that we have a submission replace="all" ready for a second pass
            containingDocument.setReplaceAllCallable(callable);
            // Tell caller he doesn't need to do anything
            return null;
        } else {
            // Just run it now
            return callable.call();
        }
    }

    protected IndentedLogger getDetailsLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return submission.getDetailsLogger(p, p2);
    }

    protected IndentedLogger getTimingLogger(final XFormsModelSubmission.SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return submission.getTimingLogger(p, p2);
    }

    public static boolean isLogBody() {
        return XFormsProperties.getDebugLogging().contains("submission-body");
    }

    protected interface SubmissionProcess {
        void process(ExternalContext.Request request, ExternalContext.Response response);
    }

    /**
     * Perform a local local submission.
     */
    protected ConnectionResult openLocalConnection(ExternalContext externalContext,
                                                   final IndentedLogger indentedLogger,
                                                   ExternalContext.Response response,
                                                   XFormsModelSubmission xformsModelSubmission,
                                                   String httpMethod, final String resource, String mediatype,
                                                   byte[] messageBody, String queryString,
                                                   final boolean isReplaceAll, String[] headerNames,
                                                   Map<String, String[]> customHeaderNameValues,
                                                   SubmissionProcess submissionProcess,
                                                   boolean isContextRelative, boolean isDefaultContext) {

        // Action must be an absolute path
        if (!resource.startsWith("/"))
            throw new OXFException("Action does not start with a '/': " + resource);

        final XFormsContainingDocument containingDocument = (xformsModelSubmission != null) ? xformsModelSubmission.getContainingDocument() : null;
        try {

            // Case of empty body
            if (messageBody == null)
                messageBody = new byte[0];

            // Destination context path is the context path of the current request, or the context path implied by the new URI
            final String destinationContextPath = isDefaultContext ? "" : isContextRelative ? externalContext.getRequest().getContextPath() : NetUtils.getFirstPathElement(resource);

            // Create requestAdapter depending on method
            final ForwardExternalContextRequestWrapper requestAdapter;
            final String effectiveResourceURI;
            final String rootAdjustedResourceURI;
            {
                if (httpMethod.equals("POST") || httpMethod.equals("PUT")) {
                    // Simulate a POST or PUT
                    effectiveResourceURI = resource;

                    // Log request body
                    if (indentedLogger.isDebugEnabled() && isLogBody())
                        Connection.logRequestBody(indentedLogger, mediatype, messageBody);

                    rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                    if (rootAdjustedResourceURI == null)
                        throw new OXFException("Action must start with a servlet context path: " + resource);

                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(), destinationContextPath,
                            rootAdjustedResourceURI, httpMethod, (mediatype != null) ? mediatype : XMLUtils.XML_CONTENT_TYPE, messageBody, headerNames, customHeaderNameValues);
                } else {
                    // Simulate a GET or DELETE
                    {
                        final StringBuffer updatedActionStringBuffer = new StringBuffer(resource);
                        if (queryString != null) {
                            if (resource.indexOf('?') == -1)
                                updatedActionStringBuffer.append('?');
                            else
                                updatedActionStringBuffer.append('&');
                            updatedActionStringBuffer.append(queryString);
                        }
                        effectiveResourceURI = updatedActionStringBuffer.toString();
                    }

                    rootAdjustedResourceURI = isDefaultContext || isContextRelative ? effectiveResourceURI : NetUtils.removeFirstPathElement(effectiveResourceURI);
                    if (rootAdjustedResourceURI == null)
                        throw new OXFException("Action must start with a servlet context path: " + resource);

                    requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(), destinationContextPath,
                            rootAdjustedResourceURI, httpMethod, headerNames, customHeaderNameValues);
                }
            }

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("", "dispatching request",
                            "method", httpMethod,
                            "mediatype", mediatype,
                            "context path", destinationContextPath,
                            "effective resource URI (original)", effectiveResourceURI,
                            "effective resource URI (relative to servlet root)", rootAdjustedResourceURI);

            // Reason we use a Response passed is for the case of replace="all" when XFormsContainingDocument provides a Response
            final ExternalContext.Response effectiveResponse = !isReplaceAll ? null : response != null ? response : externalContext.getResponse();

            final ConnectionResult connectionResult = new ConnectionResult(effectiveResourceURI) {
                @Override
                public void close() {
                    if (getResponseInputStream() != null) {
                        // Case of !isReplaceAll where we read from the response
                        try {
                            getResponseInputStream().close();
                        } catch (IOException e) {
                            throw new OXFException("Exception while closing input stream for resource: " + resource);
                        }
                    } else {
                        // Case of isReplaceAll where forwarded resource writes to the response directly

                        // Try to obtain, flush and close the stream to work around WebSphere issue
                        try {
                            if (effectiveResponse != null) {
                                final OutputStream os = effectiveResponse.getOutputStream();
                                os.flush();
                                os.close();
                            }
                        } catch (IllegalStateException e) {
                            indentedLogger.logDebug("", "IllegalStateException caught while closing OutputStream after forward");
                            try {
                                if (effectiveResponse != null) {
                                    final PrintWriter writer = effectiveResponse.getWriter();
                                    writer.flush();
                                    writer.close();
                                }
                            } catch (IllegalStateException f) {
                                indentedLogger.logDebug("", "IllegalStateException caught while closing Writer after forward");
                            } catch (IOException f) {
                                indentedLogger.logDebug("", "IOException caught while closing Writer after forward");
                            }
                        } catch (IOException e) {
                            indentedLogger.logDebug("", "IOException caught while closing OutputStream after forward");
                        }
                    }
                }
            };
            if (isReplaceAll) {
                // "the event xforms-submit-done is dispatched"
                if (xformsModelSubmission != null)
                    xformsModelSubmission.getXBLContainer(containingDocument).dispatchEvent(
                            new XFormsSubmitDoneEvent(containingDocument, xformsModelSubmission, connectionResult.resourceURI, connectionResult.statusCode));

                submissionProcess.process(requestAdapter, effectiveResponse);
                connectionResult.dontHandleResponse = true;
            } else {
                // We must intercept the reply
                final ResponseAdapter responseAdapter = new ResponseAdapter(externalContext.getNativeResponse());
                submissionProcess.process(requestAdapter, responseAdapter);

                // Get response information that needs to be forwarded

                // NOTE: Here, the resultCode is not propagated from the included resource
                // when including Servlets. Similarly, it is not possible to obtain the
                // included resource's content type or headers. Because of this we should not
                // use an optimized submission from within a servlet.
                connectionResult.statusCode = responseAdapter.getResponseCode();
                connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);
                connectionResult.setResponseInputStream(responseAdapter.getInputStream());
                connectionResult.responseHeaders = ConnectionResult.EMPTY_HEADERS_MAP;
                connectionResult.setLastModified(null);
            }

            return connectionResult;
        } catch (IOException e) {
            throw new OXFException(e);
        }
    }
}
