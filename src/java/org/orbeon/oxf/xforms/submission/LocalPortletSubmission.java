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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.portlet.OrbeonPortlet2Delegate;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsConstants;
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
    public boolean isMatch(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext.Request request = NetUtils.getExternalContext(propertyContext).getRequest();
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

    public SubmissionResult connect(final PropertyContext propertyContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        // URI with xml:base resolution
        final URI resolvedURI = XFormsUtils.resolveXMLBase(containingDocument, submission.getSubmissionElement(), p2.actionOrResource);

        final String[] headersToForward = p.isReplaceAll ? RequestDispatcherSubmission.STANDARD_HEADERS_TO_FORWARD : RequestDispatcherSubmission.MINIMAL_HEADERS_TO_FORWARD;
        // TODO: Harmonize with HTTP submission handling of headers

        final IndentedLogger timingLogger = getTimingLogger(p, p2);
        final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(propertyContext, p.contextStack);

        final String submissionEffectiveId = submission.getEffectiveId();

        // Pack external call into a Runnable so it can be run:
        // o now and synchronously
        // o now and asynchronously
        // o later as a "foreground" asynchronous submission
        final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
            public SubmissionResult call() throws Exception {

                // TODO: This refers to PropertyContext, XFormsContainingDocument, and Submission. FIXME!

                // Open the connection
                final boolean[] status = { false , false };
                ConnectionResult connectionResult = null;
                try {
                    connectionResult = openLocalConnection(propertyContext, NetUtils.getExternalContext(propertyContext),
                        detailsLogger, containingDocument.getResponse(), p.isDeferredSubmissionSecondPassReplaceAll ? null : submission,
                        p.actualHttpMethod, resolvedURI.toString(), sp.actualRequestMediatype, sp.messageBody,
                        sp.queryString, p.isReplaceAll, headersToForward, customHeaderNameValues, new SubmissionProcess() {
                                public void process(ExternalContext.Request request, ExternalContext.Response response) {
                                    // Delegate to portlet
                                    OrbeonPortlet2Delegate.processPortletRequest(request, response);
                                }
                            }, false, true);

                    // Update status
                    status[0] = true;

                    if (connectionResult.dontHandleResponse) {
                        // This means we got a submission with replace="all" and above already did all the work
                        // TODO: Could this be done in a Replacer instead?
                        containingDocument.setGotSubmissionReplaceAll();

                        // Update status
                        status[1] = true;

                        // Caller has nothing to do
                        return null;
                    } else {
                        // Obtain replacer
                        final Replacer replacer = submission.getReplacer(propertyContext, connectionResult, p);

                        // Deserialize
                        replacer.deserialize(propertyContext, connectionResult, p, p2);

                        // Update status
                        status[1] = true;

                        // Return result
                        return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
                    }
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
        return submitCallable(propertyContext, p, p2, callable);
    }
}
