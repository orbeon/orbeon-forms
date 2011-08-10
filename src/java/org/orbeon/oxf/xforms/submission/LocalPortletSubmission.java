/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.apache.commons.lang.StringUtils;
import org.orbeon.oxf.externalcontext.AsyncExternalContext;
import org.orbeon.oxf.externalcontext.ExternalContextWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.portlet.OrbeonPortlet2Delegate;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsConstants;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsUtils;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * This submission directly uses the Orbeon portlet to run a request/response as a response to a submission.
 */
public class LocalPortletSubmission extends BaseSubmission {

    private static final String SKIPPING_SUBMISSION_DEBUG_MESSAGE = "skipping local portlet submission";

    public LocalPortletSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "local portlet";
    }

    /**
     * Check whether submission is allowed.
     */
    public boolean isMatch(XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext.Request request = NetUtils.getExternalContext().getRequest();
        final IndentedLogger indentedLogger = getDetailsLogger(p, p2);

        // Log a lot of stuff for development, as it is not always obvious why we pick this type of submission.

        final boolean isDebugEnabled = indentedLogger.isDebugEnabled();
        if (isDebugEnabled) {
            indentedLogger.logDebug("", "checking whether " + getType() + " submission is allowed",
                "resource", p2.actionOrResource,
                "container type", request.getContainerType(),
                "deployment type", containingDocument.getDeploymentType().name()
            );
        }

        // Absolute URL implies a regular submission
        if (NetUtils.urlHasProtocol(p2.actionOrResource)) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "resource URL has protocol", "resource", p2.actionOrResource);
            return false;
        }

        // Only for portlet
        if (!request.getContainerType().equals("portlet")) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "container type is not portlet");
            return false;
        }

        // Separate deployment not supported for portlet local submission as callee is a servlet, not a portlet!
        if (containingDocument.getDeploymentType() == XFormsConstants.DeploymentType.separate) {
            if (isDebugEnabled)
                indentedLogger.logDebug("", SKIPPING_SUBMISSION_DEBUG_MESSAGE,
                        "reason", "deployment type is separate");
            return false;
        }

        if (isDebugEnabled)
            indentedLogger.logDebug("", "enabling " + getType() + " submission");

        return true;
    }

    public SubmissionResult connect(final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        final IndentedLogger timingLogger = getTimingLogger(p, p2);
        final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

        // URI with xml:base resolution
        final URI resolvedURI = XFormsUtils.resolveXMLBase(containingDocument, submission.getSubmissionElement(), p2.actionOrResource);

        // Headers
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(p.contextStack);
        final String[] headersToForward = StringUtils.split(XFormsProperties.getForwardSubmissionHeaders(containingDocument, p.isReplaceAll));

        final String submissionEffectiveId = submission.getEffectiveId();

        // If async, use a "safe" copy of the context
        final OrbeonPortlet2Delegate currentPortlet = (OrbeonPortlet2Delegate) OrbeonPortlet2Delegate.currentPortlet().value();

        final ExternalContext.Response response = containingDocument.getResponse() != null ? containingDocument.getResponse() : NetUtils.getExternalContext().getResponse();
        final ExternalContext asyncExternalContext = p2.isAsynchronous
                ? new AsyncExternalContext(NetUtils.getExternalContext().getRequest(), response)
                : NetUtils.getExternalContext();

        // Pack external call into a Runnable so it can be run synchronously or asynchronously.
        final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
            public SubmissionResult call() throws Exception {

                if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                    timingLogger.startHandleOperation("", "running asynchronous local portlet submission submission", "id", submission.getEffectiveId());

                // TODO: This refers to XFormsContainingDocument, and Submission. FIXME!

                // Open the connection
                final boolean[] status = { false , false };
                ConnectionResult connectionResult = null;
                try {
                    connectionResult = openLocalConnection(asyncExternalContext, response,
                        detailsLogger, p.isDeferredSubmissionSecondPassReplaceAll ? null : submission,
                        p.actualHttpMethod, resolvedURI.toString(), sp.actualRequestMediatype, sp.messageBody,
                        sp.queryString, p.isReplaceAll, headersToForward, customHeaderNameValues, new SubmissionProcess() {
                            public void process(final ExternalContext.Request request, final ExternalContext.Response response) {
                                // Delegate to portlet
                                currentPortlet.getProcessorService().service(new ExternalContextWrapper(asyncExternalContext) {
                                    @Override
                                    public ExternalContext.Request getRequest() {
                                        return request;
                                    }

                                    @Override
                                    public ExternalContext.Response getResponse() {
                                        return response;
                                    }
                                }, new PipelineContext());
                            }
                        }, true, false);

                    // Update status
                    status[0] = true;

                    // TODO: can we put this in the Replacer?
                    if (connectionResult.dontHandleResponse)
                        containingDocument.setGotSubmissionReplaceAll();

                    // Obtain replacer, deserialize and update status
                    final Replacer replacer = submission.getReplacer(connectionResult, p);
                    replacer.deserialize(connectionResult, p, p2);
                    status[1] = true;

                    // Return result
                    return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
                } catch (Throwable throwable) {
                    // Exceptions are handled further down
                    return new SubmissionResult(submissionEffectiveId, throwable, connectionResult);
                } finally {
                    if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                        timingLogger.endHandleOperation("id", submissionEffectiveId, "asynchronous", Boolean.toString(p2.isAsynchronous),
                                "connected", Boolean.toString(status[0]), "deserialized", Boolean.toString(status[1]));
                }
            }
        };

        // Submit the callable
        // This returns null if the execution is deferred
        return submitCallable(p, p2, callable);
    }
}
