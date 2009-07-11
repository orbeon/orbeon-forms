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
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsProperties;

import java.net.URL;
import java.util.Map;

/**
 * Regular remote submission going through a protocol handler.
 */
public class RegularSubmission extends SubmissionBase {
    
    public RegularSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return true;
    }

    public ConnectionResult connect(PipelineContext pipelineContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) {

        final ExternalContext externalContext = getExternalContext(pipelineContext);
        final ExternalContext.Request request = externalContext.getRequest();
        final URL absoluteResolvedURL = getResolvedSubmissionURL(pipelineContext, externalContext, request, p2.resolvedActionOrResource, sp.queryString);

        // Gather remaining information to process the request
        final String forwardSubmissionHeaders = XFormsProperties.getForwardSubmissionHeaders(containingDocument);

        // NOTE about headers forwarding: forward user-agent header for replace="all", since that *usually*
        // simulates a request from the browser! Useful in particular when the target URL renders XForms
        // in noscript mode, where some browser sniffing takes place for handling the <button> vs. <submit>
        // element.
        final String newForwardSubmissionHeaders = p.isReplaceAll ? forwardSubmissionHeaders + " user-agent" : forwardSubmissionHeaders;

        final IndentedLogger connectionLogger;
        final boolean logBody;
        if (XFormsModelSubmission.logger.isDebugEnabled()) {
            // Create new indented logger just for the Connection object. This will log more stuff.
            connectionLogger = new IndentedLogger(XFormsModelSubmission.logger, "XForms submission " + (p2.isAsyncSubmission ? "(asynchronous)" : "(synchronous)"),
                    containingDocument.getIndentedLogger().getLogIndentLevel());
            logBody = true;
        } else {
            // Use regular logger
            connectionLogger = containingDocument.getIndentedLogger();
            logBody = false;
        }

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(pipelineContext, p.contextStack);

        // Open connection
        if (p2.isAsyncSubmission) {

            // Pack call into a Runnable
            final Runnable runnable = new Runnable() {

                public void run() {

                    // Here we just want to run the submission and not touch the XFCD. Remember,
                    // we can't change XFCD because it may get out of the caches and not be picked
                    // up by further incoming Ajax requests.

                    // NOTE: If the submission was truly asynchronous, we should not touch
                    // ExternalContext either. But currently, since the submission actually runs
                    // at the end of a request, we do have access to ExternalContext, so we still
                    // use it.
                    new Connection().open(externalContext, connectionLogger, logBody,
                            p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                            sp.actualRequestMediatype, sp.messageBody,
                            customHeaderNameValues, newForwardSubmissionHeaders);

                    // NOTE: In this very basic level of support, we don't support
                    // xforms-submit-done / xforms-submit-error handlers

                    // TODO: Do something with result, e.g. log?
                    // final ConnectionResult connectionResult = ...
                }
            };

            // Tell XFCD that we have one more Runnable
            containingDocument.addAsynchronousSubmission(runnable);

            // Tell caller he doesn't need to do anything
            return null;
        } else {
            // Just run it now
            return new Connection().open(externalContext, connectionLogger, logBody,
                    p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                    sp.actualRequestMediatype, sp.messageBody,
                    customHeaderNameValues, newForwardSubmissionHeaders);
        }
    }

}
