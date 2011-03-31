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
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.ReadonlyXFormsInstance;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.Callable;

/**
 * Cacheable remote submission going through a protocol handler.
 *
 * NOTE: This could possibly be made to work as well for optimized submissions, but currently this is not the case.
 */
public class CacheableSubmission extends BaseSubmission {

    public CacheableSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "cacheable";
    }

    public boolean isMatch(XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        // Match if the submission has replace="instance" and xxforms:cache="true"
        return p.isReplaceInstance && p2.isCache;
    }

    public SubmissionResult connect(final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {
        // Get the instance from shared instance cache
        // This can only happen is method="get" and replace="instance" and xxforms:cache="true"

        // Convert URL to string
        final String absoluteResolvedURLString = getAbsoluteSubmissionURL(p2.actionOrResource, sp.queryString, submission.isURLNorewrite());

        // Compute a hash of the body if needed
        final String requestBodyHash;
        if (sp.messageBody != null) {
            requestBodyHash = SecureUtils.digestBytes(sp.messageBody, "MD5", "hex");
        } else {
            requestBodyHash = null;
        }

        final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

        // Parameters to callable
        final String submissionEffectiveId = submission.getEffectiveId();
        final String instanceStaticId;
        final String modelEffectiveId;
        final String validation;
        {
            // Find and check replacement location
            final XFormsInstance updatedInstance = checkInstanceToUpdate(detailsLogger, p);

            instanceStaticId = updatedInstance.getId();
            modelEffectiveId = updatedInstance.getEffectiveModelId();
            validation = updatedInstance.getValidation();
        }
        final boolean isReadonly = p2.isReadonly;
        final boolean handleXInclude = p2.isHandleXInclude;
        final long timeToLive = p2.timeToLive;

        // Obtain replacer
        // Pass a pseudo connection result which contains information used by getReplacer()
        // We know that we will get an InstanceReplacer
        final ConnectionResult connectionResult = createPseudoConnectionResult(absoluteResolvedURLString);
        final InstanceReplacer replacer = (InstanceReplacer) submission.getReplacer(connectionResult, p);

        // Try from cache first
        final XFormsInstance cacheResult = XFormsServerSharedInstancesCache.instance().findConvertNoLoad(
                detailsLogger, instanceStaticId, modelEffectiveId, absoluteResolvedURLString, requestBodyHash, isReadonly,
                handleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument));

        if (cacheResult != null) {
            // Result was immediately available, so return it right away
            // The purpose of this is to avoid starting a new thread in asynchronous mode if the instance is already in cache

            // Here we cheat a bit: instead of calling generically deserialize(), we directly set the instance document
            replacer.setInstance(cacheResult);

            // Return result
            return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
        } else {

            // NOTE: technically, somebody else could put an instance in cache between now and the Callable execution
            if (detailsLogger.isDebugEnabled())
                detailsLogger.logDebug("", "did not find instance in cache",
                        "id", instanceStaticId, "URI", absoluteResolvedURLString, "request hash", requestBodyHash);

            final IndentedLogger timingLogger = getTimingLogger(p, p2);

            // Create callable for synchronous or asynchronous loading
            final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
                public SubmissionResult call() {

                    if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                        timingLogger.startHandleOperation("", "running asynchronous submission", "id", submission.getEffectiveId(), "cacheable", "true");

                    final boolean[] status = { false , false};
                    try {
                        final XFormsInstance newInstance = XFormsServerSharedInstancesCache.instance().findConvert(
                                detailsLogger, instanceStaticId, modelEffectiveId, absoluteResolvedURLString, requestBodyHash, isReadonly,
                                handleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument), timeToLive, validation,
                            new XFormsServerSharedInstancesCache.Loader() {
                                public ReadonlyXFormsInstance load(String instanceStaticId,
                                                                   String modelEffectiveId, String instanceSourceURI,
                                                                   boolean handleXInclude, long timeToLive, String validation) {

                                    // Update status
                                    status[0] = true;

                                    // Call regular submission
                                    SubmissionResult submissionResult = null;
                                    try {
                                        // Run regular submission but force synchronous execution and readonly result
                                        final XFormsModelSubmission.SecondPassParameters updatedP2 = p2.amend(false, true);
                                        submissionResult = new RegularSubmission(submission).connect(p, updatedP2, sp);

                                        // Check if the connection returned a throwable
                                        final Throwable throwable = submissionResult.getThrowable();
                                        if (throwable != null) {
                                            // Propagate
                                            throw new ThrowableWrapper(throwable, submissionResult.getConnectionResult());
                                        } else {
                                            // There was no throwable
                                            // We know that RegularSubmission returns a Replacer with an instance document
                                            final DocumentInfo documentInfo = (DocumentInfo) ((InstanceReplacer) submissionResult.getReplacer()).getResultingDocument();

                                            // Update status
                                            status[1] = true;

                                            // Create new shared instance
                                            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, documentInfo, instanceSourceURI,
                                                    requestBodyHash, updatedP2.username, updatedP2.password, updatedP2.domain, true, timeToLive, validation, handleXInclude,
                                                    XFormsProperties.isExposeXPathTypes(containingDocument));
                                        }
                                    } catch (ThrowableWrapper throwableWrapper) {
                                        // In case we just threw it above, just propagate
                                        throw throwableWrapper;
                                    } catch (Throwable throwable) {
                                        // Exceptions are handled further down
                                        throw new ThrowableWrapper(throwable, (submissionResult != null) ? submissionResult.getConnectionResult() : null);
                                    }
                                }
                            });

                        // Here we cheat a bit: instead of calling generically deserialize(), we directly set the DocumentInfo
                        replacer.setInstance(newInstance);

                        // Return result
                        return new SubmissionResult(submissionEffectiveId, replacer, connectionResult);
                    } catch (ThrowableWrapper throwableWrapper) {
                        // The ThrowableWrapper was thrown within the inner load() method above
                        return new SubmissionResult(submissionEffectiveId, throwableWrapper.getThrowable(), throwableWrapper.getConnectionResult());
                    } catch (Throwable throwable) {
                        // Any other throwable
                        return new SubmissionResult(submissionEffectiveId, throwable, null);
                    } finally {
                        if (p2.isAsynchronous && timingLogger.isDebugEnabled())
                            timingLogger.endHandleOperation("id", submission.getEffectiveId(), "asynchronous", Boolean.toString(p2.isAsynchronous),
                                    "loading attempted", Boolean.toString(status[0]), "deserialized", Boolean.toString(status[1]));
                    }
                }
            };

            // Submit the callable
            // This returns null if the execution is deferred
            return submitCallable(p, p2, callable);
        }
    }

    private static class ThrowableWrapper extends RuntimeException {
        final Throwable throwable;
        final ConnectionResult connectionResult;

        private ThrowableWrapper(Throwable throwable, ConnectionResult connectionResult) {
            this.throwable = throwable;
            this.connectionResult = connectionResult;
        }

        public Throwable getThrowable() {
            return throwable;
        }

        public ConnectionResult getConnectionResult() {
            return connectionResult;
        }
    }

    private XFormsInstance checkInstanceToUpdate(IndentedLogger indentedLogger, XFormsModelSubmission.SubmissionParameters p) {
        XFormsInstance updatedInstance;
        final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(p.xpathContext,
                submission.findReplaceInstanceNoTargetref(p.refInstance), p.submissionElementContextItem);

        if (destinationNodeInfo == null) {
            // Throw target-error

            // XForms 1.1: "If the processing of the targetref attribute fails,
            // then submission processing ends after dispatching the event
            // xforms-submit-error with an error-type of target-error."

            throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                    new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
        }

        updatedInstance = submission.getContainingDocument().getInstanceForNode(destinationNodeInfo);
        if (updatedInstance == null || !updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo)) {
            // Only support replacing the root element of an instance
            // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
            throw new XFormsSubmissionException(submission, "targetref attribute must point to an instance root element when using cached/shared instance replacement.", "processing targetref attribute",
                    new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
        }

        if (indentedLogger.isDebugEnabled())
            indentedLogger.logDebug("", "using instance from application shared instance cache",
                    "instance", updatedInstance.getEffectiveId());
        return updatedInstance;
    }

    private ConnectionResult createPseudoConnectionResult(String resourceURI) {
        final ConnectionResult connectionResult = new ConnectionResult(resourceURI) {
            @Override
            public boolean hasContent() {
                return true;
            }
        };
        connectionResult.statusCode = 200;
        connectionResult.responseHeaders = ConnectionResult.EMPTY_HEADERS_MAP;
        connectionResult.setLastModified(null);
        connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);
        connectionResult.dontHandleResponse = false;
        try {
            connectionResult.setResponseInputStream(new ByteArrayInputStream(new byte[] {}));
        } catch (IOException e) {
            // Should not happen
            throw new OXFException(e);
        }
        return connectionResult;
    }
}
