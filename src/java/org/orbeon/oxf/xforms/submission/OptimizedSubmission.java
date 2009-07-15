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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.processor.XFormsServer;

import java.net.URI;
import java.util.Map;

/**
 * Optimized submission doesn't issue HTTP requests but goes through the Servlet API.
 */
public class OptimizedSubmission extends BaseSubmission {

    public OptimizedSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    /**
     * Check whether optimized submission is allowed, depending on a series of conditions.
     *
     * Log a lot of stuff for development, as it is not always obvious why we pick an optimized vs. regular submission.
     */
    public boolean isMatch(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext.Request request = getExternalContext(pipelineContext).getRequest();

        final boolean isDebugEnabled = XFormsServer.logger.isDebugEnabled();
        if (isDebugEnabled) {
            containingDocument.logDebug("submission", "checking whether optimized submission is allowed",
                "resource", p2.resolvedActionOrResource, "noscript", Boolean.toString(p.isNoscript),
                "is ajax portlet", Boolean.toString(XFormsProperties.isAjaxPortlet(containingDocument)),
                "is asynchronous", Boolean.toString(p2.isAsyncSubmission),
                "container type", request.getContainerType(), "norewrite", Boolean.toString(submission.isURLNorewrite()),
                "url type", submission.getUrlType(),
                "local-submission-forward", Boolean.toString(XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)),
                "local-submission-include", Boolean.toString(XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument))
            );
        }

        // Absolute URL is not optimized
        if (NetUtils.urlHasProtocol(p2.resolvedActionOrResource)) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "resource URL has protocol", "resource", p2.resolvedActionOrResource);
            return false;
        }

        // TODO: why is this condition here?
        if (p.isNoscript && !XFormsProperties.isAjaxPortlet(containingDocument)) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "noscript mode enabled and not in ajax portlet mode");
            return false;
        }

        // For now, we don't handle optimized async; could be optimized in the future
        if (p2.isAsyncSubmission) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "asynchronous mode is not supported yet");
            return false;
        }

        if (request.getContainerType().equals("portlet")) {
            // Portlet

            if (submission.isURLNorewrite()) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "norewrite is specified");
                return false;
            }

            // NOTE: we could optimize for resource URLs:
            // o JSR-268 local resource handling, can access porlet resources the same way as with render/action
            // o In include mode, Servlet resources can be accessed using request dispatcher to servlet

            if ("resource".equals(submission.getUrlType())) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "resource URL type is specified");
                return false;
            }
        } else if (p.isReplaceAll) {
            // Servlet, replace all
            if (!XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "forward submissions are disallowed in properties");
                return false;
            }
        } else {
            // Servlet, other
            if (!XFormsProperties.isOptimizeLocalSubmissionInclude(containingDocument)) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "include submissions are disallowed in properties");
                return false;
            }
        }

        if (isDebugEnabled)
            containingDocument.logDebug("submission", "enabling optimized submission");

        return true;
    }

    public ConnectionResult connect(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        // This is an "optimized" submission, i.e. one that does not use an actual protocol handler to
        // access the resource, but instead uses servlet forward/include for servlets, or a local
        // mechanism for portlets.

        // NOTE: Optimizing with include() for servlets doesn't allow detecting errors caused by the
        // included resource. [As of 2009-02-13, not sure if this is the case.]

        // NOTE: For portlets, paths are served directly by the portlet, NOT as resources.

        // f:url-norewrite="true" with an absolute path allows accessing other servlet contexts.

        // Current limitations:
        // o Portlets cannot access resources outside the portlet except by using absolute URLs (unless f:url-type="resource")

        // URI with xml:base resolution
        final URI resolvedURI = XFormsUtils.resolveXMLBase(submission.getSubmissionElement(), p2.resolvedActionOrResource);

        // NOTE: We don't want any changes to happen to the document upon xxforms-submit when producing
        // a new document so we don't dispatch xforms-submit-done and pass a null XFormsModelSubmission
        // in that case

        if (XFormsServer.logger.isDebugEnabled())
            containingDocument.logDebug("submission", "starting optimized submission", "id", submission.getEffectiveId());

        // NOTE about headers forwarding: forward user-agent header for replace="all", since that *usually*
        // simulates a request from the browser! Useful in particular when the target URL renders XForms
        // in noscript mode, where some browser sniffing takes place for handling the <button> vs. <submit>
        // element.
        final String[] headersToForward = p.isReplaceAll ? XFormsSubmissionUtils.STANDARD_HEADERS_TO_FORWARD : XFormsSubmissionUtils.MINIMAL_HEADERS_TO_FORWARD;
        // TODO: Harmonize with HTTP submission handling of headers

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(pipelineContext, p.contextStack);

        final ConnectionResult connectionResult
                = XFormsSubmissionUtils.openOptimizedConnection(pipelineContext, getExternalContext(pipelineContext),
                containingDocument, p.isDeferredSubmissionSecondPassReplaceAll ? null : submission, p.actualHttpMethod,
                resolvedURI.toString(), submission.isURLNorewrite(), sp.actualRequestMediatype, sp.messageBody,
                sp.queryString, p.isReplaceAll, headersToForward, customHeaderNameValues);

        // This means we got a submission with replace="all"
        if (connectionResult.dontHandleResponse)
            containingDocument.setGotSubmissionReplaceAll();

        return connectionResult;
    }
}
