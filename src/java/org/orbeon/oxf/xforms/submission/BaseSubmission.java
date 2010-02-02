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
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

            return XFormsUtils.resolveResourceURL(propertyContext, submission.getSubmissionElement(),
                    NetUtils.appendQueryString(resolvedActionOrResource, queryString),
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE);
        } else {
            // Regular case of service URL
            return XFormsUtils.resolveServiceURL(propertyContext, submission.getSubmissionElement(),
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

            // This is probably a temporary setting: we run replace="none" in the foreground later, and
            // replace="instance|text" in the background.
            final boolean isRunInBackground = !p.isReplaceNone;

            // Tell XFCD that we have one more async submission
            containingDocument.getAsynchronousSubmissionManager(true).addAsynchronousSubmission(propertyContext, callable, isRunInBackground);

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
}
