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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

import java.io.*;
import java.util.*;
import java.util.concurrent.Callable;

public abstract class BaseSubmission implements Submission {

    protected final XFormsModelSubmission submission;
    protected final XFormsContainingDocument containingDocument;

    protected BaseSubmission(XFormsModelSubmission submission) {
        this.submission = submission;
        this.containingDocument = submission.getContainingDocument();
    }

    protected String getAbsoluteSubmissionURL(PropertyContext propertyContext, String resolvedActionOrResource, String queryString) {
        // Absolute URLs or absolute paths are allowed to a local servlet
        assert NetUtils.urlHasProtocol(resolvedActionOrResource) || resolvedActionOrResource.startsWith("/");

        if ("resource".equals(submission.getUrlType())) {
            // In case, for some reason, author forces a resource URL

            // NOTE: Before 2009-10-08, there was some code performing custom rewriting in portlet mode. That code was
            // very unclear and was removed as it seemed like resolveResourceURL() should handle all cases.

            return XFormsUtils.resolveResourceURL(propertyContext, containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        } else {
            // Regular case of service URL
            return XFormsUtils.resolveServiceURL(propertyContext, containingDocument, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        }
    }

    /**
     * Evaluate the <xforms:header> elements children of <xforms:submission>.
     *
     * @param propertyContext   pipeline context
     * @param contextStack      context stack set to enclosing <xforms:submission>
     * @return                  LinkedHashMap<String headerName, String[] headerValues>, or null if no header elements
     */
    protected Map<String, String[]> evaluateHeaders(PropertyContext propertyContext, XFormsContextStack contextStack) {

        // Used for XBL scope resolution
        final XBLBindings bindings = containingDocument.getStaticState().getXBLBindings();
        final String fullPrefix = submission.getXBLContainer(containingDocument).getFullPrefix();

        final List<Element> headerElements = Dom4jUtils.elements(submission.getSubmissionElement(), XFormsConstants.XFORMS_HEADER_QNAME);
        if (headerElements.size() > 0) {
            final Map<String, String[]> headerNameValues = new LinkedHashMap<String, String[]>();

            // Iterate over all <xforms:header> elements
            for (Element headerElement: headerElements) {
                // Find scope of header element
                final XBLBindings.Scope headerScope = bindings.getResolutionScopeByPrefixedId(fullPrefix + headerElement.attributeValue("id"));

                contextStack.pushBinding(propertyContext, headerElement, submission.getEffectiveId(), headerScope);
                final XFormsContextStack.BindingContext currentHeaderBindingContext = contextStack.getCurrentBindingContext();
                if (currentHeaderBindingContext.isNewBind()) {
                    // This means there was @nodeset or @bind so we must iterate
                    final List<Item> currentNodeset = contextStack.getCurrentNodeset();
                    final int currentSize = currentNodeset.size();
                    if (currentSize > 0) {
                        // Push all iterations in turn
                        for (int position = 1; position <= currentSize; position++) {
                            contextStack.pushIteration(position);
                            handleHeaderElement(propertyContext, contextStack, bindings, fullPrefix, headerNameValues, headerElement);
                            contextStack.popBinding();
                        }
                    }
                } else {
                    // This means there is just a single header
                    handleHeaderElement(propertyContext, contextStack, bindings, fullPrefix, headerNameValues, headerElement);
                }
                contextStack.popBinding();
            }

            return headerNameValues;
        } else {
            return null;
        }
    }

    /**
     * Evaluate a single <xforms:header> element. It may have a node-set binding.
     *
     * @param propertyContext       current context
     * @param contextStack          context stack set to <xforms:header> (or element iteration)
     * @param bindings              XBL bindings
     * @param fullPrefix            container prefix
     * @param headerNameValues      LinkedHashMap<String headerName, String[] headerValues> to update
     * @param headerElement         <xforms:header> element to evaluate
     */
    private void handleHeaderElement(PropertyContext propertyContext, XFormsContextStack contextStack, XBLBindings bindings,
                                     String fullPrefix, Map<String, String[]> headerNameValues, Element headerElement) {
        final String headerName;
        {
            final Element headerNameElement = headerElement.element("name");
            if (headerNameElement == null)
                throw new XFormsSubmissionException(submission, "Missing <name> child element of <header> element", "processing <header> elements");

            final XBLBindings.Scope nameScope = bindings.getResolutionScopeByPrefixedId(fullPrefix + headerNameElement.attributeValue("id"));
            contextStack.pushBinding(propertyContext, headerNameElement, submission.getEffectiveId(), nameScope);
            headerName = XFormsUtils.getElementValue(propertyContext, containingDocument, contextStack, submission.getEffectiveId(), headerNameElement, false, null);
            contextStack.popBinding();
        }

        final String headerValue;
        {
            final Element headerValueElement = headerElement.element("value");
            if (headerValueElement == null)
                throw new XFormsSubmissionException(submission, "Missing <value> child element of <header> element", "processing <header> elements");
            final XBLBindings.Scope valueScope = bindings.getResolutionScopeByPrefixedId(fullPrefix + headerValueElement.attributeValue("id"));
            contextStack.pushBinding(propertyContext, headerValueElement, submission.getEffectiveId(), valueScope);
            headerValue = XFormsUtils.getElementValue(propertyContext, containingDocument, contextStack, submission.getEffectiveId(), headerValueElement, false, null);
            contextStack.popBinding();
        }
        
        final String combine;
        {
        	final String avtCombine = headerElement.attributeValue("combine", "append");
        	combine = XFormsUtils.resolveAttributeValueTemplates(propertyContext, contextStack.getCurrentBindingContext().getNodeset(),
        			contextStack.getCurrentBindingContext().getPosition(), contextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
        			contextStack.getFunctionContext(submission.getEffectiveId()), containingDocument.getNamespaceMappings(headerElement),
        			(LocationData)headerElement.getData(), avtCombine);

            contextStack.returnFunctionContext();
        	
        	if (!("append".equals(combine) || "prepend".equals(combine) || "replace".equals(combine))) {
        		throw new XFormsSubmissionException(submission, "Invalid value '" + combine + "' for attribute combine.", "processing <header> elements");
        	}
        }
        
        // add value to string Array Map respecting the optional combine attribute
        final String[] currentValue = headerNameValues.get(headerName);
        if (currentValue == null || "replace".equals(combine)) {
        	headerNameValues.put(headerName, new String[] { headerValue });
        } else {
            final String[] newValue = new String[currentValue.length + 1];
            
            if ("prepend".equals(combine)) {
            	System.arraycopy(currentValue, 0, newValue, 1, currentValue.length);
            	newValue[0] = headerValue;
            }
            else {
            	System.arraycopy(currentValue, 0, newValue, 0, currentValue.length);
            	newValue[currentValue.length] = headerValue;
            }
            headerNameValues.put(headerName, newValue);
        }
    }

    /**
     * Submit the Callable for synchronous or asynchronous execution.
     *
     * @param propertyContext   current context
     * @param p                 parameters
     * @param p2                parameters
     * @param callable          callable performing the submission
     * @return ConnectionResult or null if asynchronous
     * @throws Exception
     */
    protected SubmissionResult submitCallable(final PropertyContext propertyContext,
                                              final XFormsModelSubmission.SubmissionParameters p,
                                              final XFormsModelSubmission.SecondPassParameters p2,
                                              final Callable<SubmissionResult> callable) throws Exception {
        if (p2.isAsynchronous) {

            // Tell XFCD that we have one more async submission
            containingDocument.getAsynchronousSubmissionManager(true).addAsynchronousSubmission(propertyContext, callable);

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
    protected ConnectionResult openLocalConnection(PropertyContext propertyContext, ExternalContext externalContext,
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
                    xformsModelSubmission.getXBLContainer(containingDocument).dispatchEvent(propertyContext,
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
