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
package org.orbeon.oxf.xforms.submission;

import org.dom4j.Element;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.StringUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsContextStack;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.om.Item;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSubmission implements Submission {

    protected final XFormsModelSubmission submission;
    protected final XFormsContainingDocument containingDocument;

    protected BaseSubmission(XFormsModelSubmission submission) {
        this.submission = submission;
        this.containingDocument = submission.getContainingDocument();
    }

    protected ExternalContext getExternalContext(PipelineContext pipelineContext) {
        return (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
    }

    protected URL getResolvedSubmissionURL(PipelineContext pipelineContext, ExternalContext externalContext, ExternalContext.Request request, String resolvedActionOrResource, String queryString) {
        // Absolute URLs or absolute paths are allowed to a local servlet
        String resolvedURL;

        if (NetUtils.urlHasProtocol(resolvedActionOrResource) || submission.isURLNorewrite()) {
            // Don't touch the URL if it is absolute or if f:url-norewrite="true"
            resolvedURL = resolvedActionOrResource;
        } else {
            // Rewrite URL
            resolvedURL = XFormsUtils.resolveServiceURL(pipelineContext, submission.getSubmissionElement(), resolvedActionOrResource,
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);

            if (request.getContainerType().equals("portlet") && "resource".equals(submission.getUrlType()) && !NetUtils.urlHasProtocol(resolvedURL)) {
                // In this case, we have to prepend the complete server path
                resolvedURL = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "") + resolvedURL;
            }
        }

        // Compute absolute submission URL
        return NetUtils.createAbsoluteURL(resolvedURL, queryString, externalContext);
    }

    /**
     * Evaluate the <xforms:header> elements children of <xforms:submission>.
     *
     * @param pipelineContext   pipeline context
     * @param contextStack      context stack set to enclosing <xforms:submission>
     * @return                  LinkedHashMap<String headerName, String[] headerValues>, or null if no header elements
     */
    protected Map<String, String[]> evaluateHeaders(PipelineContext pipelineContext, XFormsContextStack contextStack) {
        final List<Element> headerElements = Dom4jUtils.elements(submission.getSubmissionElement(), XFormsConstants.XFORMS_HEADER_QNAME);
        if (headerElements.size() > 0) {
            final Map<String, String[]> headerNameValues = new LinkedHashMap<String, String[]>();

            // Iterate over all <xforms:header> elements
            for (Element currentHeaderElement: headerElements) {
                contextStack.pushBinding(pipelineContext, currentHeaderElement);
                final XFormsContextStack.BindingContext currentHeaderBindingContext = contextStack.getCurrentBindingContext();
                if (currentHeaderBindingContext.isNewBind()) {
                    // This means there was @nodeset or @bind so we must iterate
                    final List<Item> currentNodeset = contextStack.getCurrentNodeset();
                    final int currentSize = currentNodeset.size();
                    if (currentSize > 0) {
                        // Push all iterations in turn
                        for (int position = 1; position <= currentSize; position++) {
                            contextStack.pushIteration(position);
                            handleHeaderElement(pipelineContext, contextStack, headerNameValues, currentHeaderElement);
                            contextStack.popBinding();
                        }
                    }
                } else {
                    // This means there is just a single header
                    handleHeaderElement(pipelineContext, contextStack, headerNameValues, currentHeaderElement);
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
     * @param pipelineContext       pipeline context
     * @param contextStack          context stack set to <xforms:header> (or element iteration)
     * @param headerNameValues      LinkedHashMap<String headerName, String[] headerValues> to update
     * @param currentHeaderElement  <xforms:header> element to evaluate
     */
    private void handleHeaderElement(PipelineContext pipelineContext, XFormsContextStack contextStack, Map<String, String[]> headerNameValues, Element currentHeaderElement) {
        final String headerName;
        {
            final Element headerNameElement = currentHeaderElement.element("name");
            if (headerNameElement == null)
                throw new XFormsSubmissionException(submission, "Missing <name> child element of <header> element", "processing <header> elements");

            contextStack.pushBinding(pipelineContext, headerNameElement);
            headerName = XFormsUtils.getElementValue(pipelineContext, containingDocument, contextStack, headerNameElement, false, null);
            contextStack.popBinding();
        }

        final String headerValue;
        {
            final Element headerValueElement = currentHeaderElement.element("value");
            if (headerValueElement == null)
                throw new XFormsSubmissionException(submission, "Missing <value> child element of <header> element", "processing <header> elements");
            contextStack.pushBinding(pipelineContext, headerValueElement);
            headerValue = XFormsUtils.getElementValue(pipelineContext, containingDocument, contextStack, headerValueElement, false, null);
            contextStack.popBinding();
        }

        StringUtils.addValueToStringArrayMap(headerNameValues, headerName, headerValue);
    }
}
