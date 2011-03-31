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
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XXFormsSubmitReplaceEvent;
import org.orbeon.oxf.xforms.xbl.XBLContainer;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Handle asynchronous submissions.
 *
 * The CompletionService is stored in the session, indexed by document UUID.
 *
 * See http://wiki.orbeon.com/forms/doc/developer-guide/asynchronous-submissions
 * See http://java.sun.com/j2se/1.5.0/docs/api/java/util/concurrent/ExecutorCompletionService.html
 */
public class AsynchronousSubmissionManager {

    private static final String ASYNC_SUBMISSIONS_SESSION_KEY_PREFIX = "oxf.xforms.state.async-submissions.";

    // Global thread pool
    private static final ExecutorService threadPool = Executors.newCachedThreadPool();

    private final XFormsContainingDocument containingDocument;

    public AsynchronousSubmissionManager(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
    }

    /**
     * Add a special delay event to the containing document if there are pending submissions.
     *
     * This should be called just before sending an Ajax response.
     *
     */
    public void addClientDelayEventIfNeeded() {
        if (hasPendingAsynchronousSubmissions()) {
            // NOTE: Could get isShowProgress() from submission, but default must be false
            containingDocument.addDelayedEvent(XFormsEvents.XXFORMS_POLL, containingDocument.getEffectiveId(),
                        false, false, XFormsProperties.getSubmissionPollDelay(containingDocument), true, false, null);
        }
    }

    private static String getSessionKey(XFormsContainingDocument containingDocument) {
        return getSessionKey(containingDocument.getUUID());
    }

    private static String getSessionKey(String documentUUID) {
        return ASYNC_SUBMISSIONS_SESSION_KEY_PREFIX + documentUUID;
    }

    private static AsynchronousSubmissions getAsynchronousSubmissions(boolean create, String sessionKey) {
        final Map<String, Object> sessionMap = NetUtils.getExternalContext().getSession(true).getAttributesMap();
        final AsynchronousSubmissions existingAsynchronousSubmissions = (AsynchronousSubmissions) sessionMap.get(sessionKey);
        if (existingAsynchronousSubmissions != null) {
            return existingAsynchronousSubmissions;
        } else if (create) {
            final AsynchronousSubmissions asynchronousSubmissions = new AsynchronousSubmissions();
            sessionMap.put(sessionKey, asynchronousSubmissions);
            return asynchronousSubmissions;
        } else {
            return null;
        }
    }

    public void addAsynchronousSubmission(final Callable<SubmissionResult> callable) {

        final AsynchronousSubmissions asynchronousSubmissions = getAsynchronousSubmissions(true, getSessionKey(containingDocument));

        // NOTE: If we want to re-enable foreground async submissions, we must:
        // o do a better detection: !(xf-submit-done/xf-submit-error listener) && replace="none"
        // o OR provide an explicit hint on xf:submission
        asynchronousSubmissions.submit(callable);
    }

    public boolean hasPendingAsynchronousSubmissions() {
        final AsynchronousSubmissions asynchronousSubmissions = getAsynchronousSubmissions(false, getSessionKey(containingDocument));
        return asynchronousSubmissions != null && asynchronousSubmissions.getPendingCount() > 0;
    }

    /**
     * Process all pending asynchronous submissions if any. If processing of a particular submission causes new
     * asynchronous submissions to be started, also wait for the completion of those.
     *
     * Submissions are processed in the order in which they are made available upon termination by the completion
     * service.
     *
     */
    public void processAllAsynchronousSubmissions() {
        final AsynchronousSubmissions asynchronousSubmissions = getAsynchronousSubmissions(false, getSessionKey(containingDocument));
        if (asynchronousSubmissions != null && asynchronousSubmissions.getPendingCount() > 0) {

            final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
            indentedLogger.startHandleOperation("", "processing all background asynchronous submissions");
            int processedCount = 0;
            try {
                while (asynchronousSubmissions.getPendingCount() > 0) {
                    try {
                        // Handle next completed task
                        final Future<SubmissionResult> future = asynchronousSubmissions.take();
                        final SubmissionResult result = future.get();

                        // Process response by dispatching an event to the submission
                        final XFormsModelSubmission submission = (XFormsModelSubmission) containingDocument.getObjectByEffectiveId(result.getSubmissionEffectiveId());
                        final XBLContainer container = submission.getXBLContainer(containingDocument);
                        // NOTE: not clear whether we should use an event for this as there doesn't seem to be a benefit
                        container.dispatchEvent(new XXFormsSubmitReplaceEvent(containingDocument, submission, result));

                    } catch (Throwable throwable) {
                        // Something bad happened
                        throw new OXFException(throwable);
                    }

                    processedCount++;
                }
            } finally {
                indentedLogger.endHandleOperation("processed", Integer.toString(processedCount));
            }
        }
    }

    /**
     * Process all completed asynchronous submissions if any. This method returns as soon as no completed submission is
     * available.
     *
     * Submissions are processed in the order in which they are made available upon termination by the completion
     * service.
     *
     */
    public void processCompletedAsynchronousSubmissions() {
        final AsynchronousSubmissions asynchronousSubmissions = getAsynchronousSubmissions(false, getSessionKey(containingDocument));
        if (asynchronousSubmissions != null && asynchronousSubmissions.getPendingCount() > 0) {
            final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
            indentedLogger.startHandleOperation("", "processing completed background asynchronous submissions");

            int processedCount = 0;
            try {
                Future<SubmissionResult> future = asynchronousSubmissions.poll();
                while (future != null) {
                    try {
                        // Handle next completed task
                        final SubmissionResult result = future.get();

                        // Process response by dispatching an event to the submission
                        final XFormsModelSubmission submission = (XFormsModelSubmission) containingDocument.getObjectByEffectiveId(result.getSubmissionEffectiveId());
                        final XBLContainer container = submission.getXBLContainer(containingDocument);
                        // NOTE: not clear whether we should use an event for this as there doesn't seem to be a benefit
                        container.dispatchEvent(new XXFormsSubmitReplaceEvent(containingDocument, submission, result));
                    } catch (Throwable throwable) {
                        // Something bad happened
                        throw new OXFException(throwable);
                    }

                    processedCount++;

                    future = asynchronousSubmissions.poll();
                }
            } finally {
                indentedLogger.endHandleOperation("processed", Integer.toString(processedCount),
                        "pending", Integer.toString(asynchronousSubmissions.getPendingCount()));
            }
        }
    }

    private static class AsynchronousSubmissions {
        private final CompletionService<SubmissionResult> completionService = new ExecutorCompletionService<SubmissionResult>(threadPool);
        private int pendingCount = 0;

        public Future<SubmissionResult> submit(Callable<SubmissionResult> task) {
            final Future<SubmissionResult> future = completionService.submit(task);
            pendingCount++;
            return future;
        }

        public Future<SubmissionResult> poll() {
            final Future<SubmissionResult> future = completionService.poll();
            if (future != null)
                pendingCount--;
            return future;
        }

        public Future<SubmissionResult> take() throws InterruptedException {
            final Future<SubmissionResult> future = completionService.take();
            pendingCount--;
            return future;
        }

        public int getPendingCount() {
            return pendingCount;
        }
    }
}
