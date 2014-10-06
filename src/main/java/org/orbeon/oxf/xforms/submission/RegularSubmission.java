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

import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;

import java.net.URI;
import java.util.concurrent.Callable;

/**
 * Regular remote submission going through a protocol handler.
 */
public class RegularSubmission extends BaseSubmission {

    public RegularSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "regular";
    }

    public boolean isMatch(XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return true;
    }

    public SubmissionResult connect(final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {

        final URI absoluteResolvedURL =
            new URI(getAbsoluteSubmissionURL(p2.actionOrResource, sp.queryString, submission().isURLNorewrite()));

        final IndentedLogger timingLogger = getTimingLogger(p, p2);
        final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

        // Headers
        final scala.collection.immutable.Map<String, scala.collection.immutable.List<String>> customHeaderNameValues = SubmissionUtils.evaluateHeaders(submission(), p.isReplaceAll);
        final String headersToForward = containingDocument().getForwardSubmissionHeaders();

        final scala.collection.immutable.Map<String, scala.collection.immutable.List<String>> headers =
            Connection.buildConnectionHeadersLowerWithSOAPIfNeeded(
                absoluteResolvedURL.getScheme(),
                p.actualHttpMethod,
                p2.credentials,
                sp.actualRequestMediatype,
                p2.encoding,
                customHeaderNameValues,
                headersToForward,
                detailsLogger
            );

        final String submissionEffectiveId = submission().getEffectiveId();

        // Prepare Connection in this thread as async submission can't access the request object
        final Connection connection =
            Connection.jApply(p.actualHttpMethod, absoluteResolvedURL,
                p2.credentials, sp.messageBody, headers, true, isLogBody(), detailsLogger);

        // Pack external call into a Callable so it can be run:
        // - now and synchronously
        // - now and asynchronously
        // - later as a "foreground" asynchronous submission
        final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
            public SubmissionResult call() throws Exception {

                // Here we just want to run the submission and not touch the XFCD. Remember, we can't change XFCD
                // because it may get out of the caches and not be picked up by further incoming Ajax requests.

                if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                    timingLogger.startHandleOperation("", "running asynchronous submission", "id", submissionEffectiveId);

                // Open the connection
                final boolean[] status = { false , false};
                ConnectionResult connectionResult = null;
                try {
                    detailsLogger.startHandleOperation("", "opening connection");
                    try {
                        // Connect, and cleanup
                        // TODO: Consider how the state could be saved. Maybe do this before connect() in the initiating thread?
                        // Or make sure it's ok to touch app/session (but not request) from other thread (ExternalContext
                        // in scope + synchronization)
                        connectionResult = connection.connect(! p2.isAsynchronous);
                    } finally {
                        // In case an exception is thrown in the body, still do adjust the logs
                        detailsLogger.endHandleOperation();
                    }

                    // Update status
                    status[0] = true;
                    
                    // Obtain replacer
                    // TODO: This refers to Submission.
                    final Replacer replacer = submission().getReplacer(connectionResult, p);
                    if (replacer != null) {
                        // Deserialize here so it can run in parallel
                        replacer.deserialize(connectionResult, p, p2);

                        // Update status
                        status[1] = true;
                    }

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
