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
import java.util.concurrent.*;

/**
 * Regular remote submission going through a protocol handler.
 */
public class RegularSubmission extends BaseSubmission {

    // Global pool
    private static ExecutorService threadPool = Executors.newCachedThreadPool();
    
    public RegularSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return true;
    }

    public ConnectionResult connect(PipelineContext pipelineContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

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

        // Pack external call into a Runnable so it can be run:
        // o now and synchronously
        // o now and asynchronously
        // o later as a "foreground" asynchronious submission
        final Callable<ConnectionResult> callable = new Callable<ConnectionResult>() {
            public ConnectionResult call() {
                // Here we just want to run the submission and not touch the XFCD. Remember, we can't change XFCD
                // because it may get out of the caches and not be picked up by further incoming Ajax requests.

                // NOTE: If the submission was truly asynchronous, we should not touch ExternalContext either.
                // But currently, since the submission actually runs at the end of a request, we do have access to
                // ExternalContext, so we still use it.
                return new Connection().open(externalContext, connectionLogger, logBody,
                        p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                        sp.actualRequestMediatype, sp.messageBody,
                        customHeaderNameValues, newForwardSubmissionHeaders);
            }
        };

        // Open connection
        if (p2.isAsyncSubmission) {

            // This is probabaly a temporary setting: we run replace="none" in the foreground later, and
            // replace="instance|text" in the background.
            final boolean isRunInBackground = !p.isReplaceNone;

            if (isRunInBackground) {
                // Submission runs immediately in a separate thread

                final Future<ConnectionResult> future = threadPool.submit(callable);

                // Tell XFCD that we have one more async submission
                containingDocument.addAsynchronousSubmission(future, submission.getEffectiveId(), true);

            } else {
                // Submission will run after response is sent to the client

                // Tell XFCD that we have one more async submission
                final Future<ConnectionResult> future = new Future<ConnectionResult>() {

                    private boolean isDone;
                    private boolean isCanceled;

                    private ConnectionResult result;

                    public boolean cancel(boolean b) {
                        if (isDone)
                            return false;
                        isCanceled = true;
                        return true;
                    }

                    public boolean isCancelled() {
                        return isCanceled;
                    }

                    public boolean isDone() {
                        return isDone;
                    }

                    public ConnectionResult get() throws InterruptedException, ExecutionException {
                        if (isCanceled)
                            throw new CancellationException();

                        if (!isDone) {
                            try {
                                result = callable.call();
                            } catch (Exception e) {
                                throw new ExecutionException(e);
                            }

                            isDone = true;
                        }

                        return result;
                    }

                    public ConnectionResult get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                        return get();
                    }
                };

                containingDocument.addAsynchronousSubmission(future, submission.getEffectiveId(), false);

                // NOTE: In this very basic level of support, we don't support
                // xforms-submit-done / xforms-submit-error handlers

                // TODO: Do something with result, e.g. log?
                // final ConnectionResult connectionResult = ...
            }

            // Tell caller he doesn't need to do anything
            return null;
        } else {
            // Just run it now
            return callable.call();
        }
    }
}
