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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.ReadonlyXFormsInstance;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;

/**
 * Cacheable remote submission going through a protocol handler.
 *
 * TODO: This should be made to work as well for optimized submissions.
 */
public class CacheableSubmission extends BaseSubmission {

    private static final ConnectionResult CONSTANT_CONNECTION_RESULT;
    static {
        CONSTANT_CONNECTION_RESULT = new ConnectionResult(null) {
            @Override
            public boolean hasContent() {
                return true;
            }
        };
        CONSTANT_CONNECTION_RESULT.statusCode = 200;
        CONSTANT_CONNECTION_RESULT.responseHeaders = ConnectionResult.EMPTY_HEADERS_MAP;
        CONSTANT_CONNECTION_RESULT.setLastModified(null);
        CONSTANT_CONNECTION_RESULT.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);
        CONSTANT_CONNECTION_RESULT.dontHandleResponse = false;
        try {
            CONSTANT_CONNECTION_RESULT.setResponseInputStream(new ByteArrayInputStream(new byte[] {}));
        } catch (IOException e) {
            // Should not happen
            throw new OXFException(e);
        }
    }

    public CacheableSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        // Match if the submission has replace="instance" and xxforms:cache="true"
        return p.isReplaceInstance && p2.resolvedXXFormsCache;
    }

    public SubmissionResult connect(final PropertyContext propertyContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) throws Exception {
        // Get the instance from shared instance cache
        // This can only happen is method="get" and replace="instance" and xxforms:cache="true" or xxforms:shared="application"


        // Convert URL to string
        final String absoluteResolvedURLString;
        {
            final ExternalContext externalContext = getExternalContext(propertyContext);
            final URL absoluteResolvedURL = getResolvedSubmissionURL(propertyContext, externalContext, p2.resolvedActionOrResource, sp.queryString);
            absoluteResolvedURLString = absoluteResolvedURL.toExternalForm();
        }

        // Compute a hash of the body if needed
        final String requestBodyHash;
        if (sp.messageBody != null) {
            requestBodyHash = SecureUtils.digestBytes(sp.messageBody, "MD5", "hex");
        } else {
            requestBodyHash = null;
        }

        // Parameters to callable
        final String submissionEffectiveId = submission.getEffectiveId();
        final String instanceStaticId;
        final String modelEffectiveId;
        final String validation;
        {
            // Find and check replacement location
            final XFormsInstance updatedInstance = checkInstanceToUpdate(propertyContext, p);

            instanceStaticId = updatedInstance.getId();
            modelEffectiveId = updatedInstance.getEffectiveModelId();
            validation = updatedInstance.getValidation();
        }
        final boolean isReadonly = p2.resolvedXXFormsReadonly;
        final boolean handleXInclude = p2.resolvedXXFormsHandleXInclude;
        final long timeToLive = XFormsInstance.getTimeToLive(submission.getSubmissionElement());

        // Create new logger as the submission might be asynchronous
        final IndentedLogger submissionLogger = new IndentedLogger(containingDocument.getIndentedLogger());

        // Obtain replacer
        // Pass a pseudo connection result which contains information used by getReplacer()
        // We know that we will get an InstanceReplacer
        final InstanceReplacer replacer = (InstanceReplacer) submission.getReplacer(propertyContext, CONSTANT_CONNECTION_RESULT, p);

        // Try from cache first
        final XFormsInstance cacheResult = XFormsServerSharedInstancesCache.instance().findConvertNoLoad(propertyContext,
                submissionLogger, instanceStaticId, modelEffectiveId, absoluteResolvedURLString, requestBodyHash, isReadonly,
                handleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument));

        if (cacheResult != null) {
            // Result was immediately available, so return it right away
            // The purpose of this is to avoid starting a new thread in asynchronous mode if the instance is already in cache

            // Here we cheat a bit: instead of calling generically deserialize(), we directly set the instance document
            replacer.setInstance(cacheResult);

            // Return result
            return new SubmissionResult(submissionEffectiveId, replacer, CONSTANT_CONNECTION_RESULT);
        } else {
            // Create callable for synchronous or asynchronous loading
            final Callable<SubmissionResult> callable = new Callable<SubmissionResult>() {
                public SubmissionResult call() {
                    try {
                        final XFormsInstance newInstance = XFormsServerSharedInstancesCache.instance().findConvert(propertyContext,
                                submissionLogger, instanceStaticId, modelEffectiveId, absoluteResolvedURLString, requestBodyHash, isReadonly,
                                handleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument), timeToLive, validation,
                            new XFormsServerSharedInstancesCache.Loader() {
                                public ReadonlyXFormsInstance load(PropertyContext propertyContext, String instanceStaticId,
                                                                   String modelEffectiveId, String instanceSourceURI,
                                                                   boolean handleXInclude, long timeToLive, String validation) {

                                    // Call regular submission
                                    SubmissionResult submissionResult = null;
                                    try {
                                        // Run regular submission but force synchronous execution and readonly result
                                        final XFormsModelSubmission.SecondPassParameters updatedP2 = p2.amend(false, true);
                                        submissionResult = new RegularSubmission(submission).connect(propertyContext, p, updatedP2, sp);

                                        // Check if the connection returned a throwable
                                        final Throwable throwable = submissionResult.getThrowable();
                                        if (throwable != null) {
                                            // Propagate
                                            throw new ThrowableWrapper(throwable, submissionResult.getConnectionResult());
                                        } else {
                                            // There was no throwable
                                            // We know that RegularSubmission returns a Replacer with an instance document
                                            final DocumentInfo documentInfo = (DocumentInfo) ((InstanceReplacer) submissionResult.getReplacer()).getResultingDocument();

                                            // Create new shared instance
                                            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, documentInfo, instanceSourceURI,
                                                    updatedP2.resolvedXXFormsUsername, updatedP2.resolvedXXFormsPassword, true, timeToLive, validation, handleXInclude,
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
                        return new SubmissionResult(submissionEffectiveId, replacer, CONSTANT_CONNECTION_RESULT);
                    } catch (ThrowableWrapper throwableWrapper) {
                        // The ThrowableWrapper was thrown within the inner load() method above
                        return new SubmissionResult(submissionEffectiveId, throwableWrapper.getThrowable(), throwableWrapper.getConnectionResult());
                    } catch (Throwable throwable) {
                        // Any other throwable
                        return new SubmissionResult(submissionEffectiveId, throwable, null);
                    }
                }
            };

            // Submit the callable
            // This returns null if the execution is asynchronous
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

    private XFormsInstance checkInstanceToUpdate(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p) {
        XFormsInstance updatedInstance;
        final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(propertyContext, p.xpathContext,
                submission.findReplaceInstanceNoTargetref(p.refInstance), p.submissionElementContextItem);

        if (destinationNodeInfo == null) {
            // Throw target-error

            // XForms 1.1: "If the processing of the targetref attribute fails,
            // then submission processing ends after dispatching the event
            // xforms-submit-error with an error-type of target-error."

            throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                    new XFormsSubmitErrorEvent(propertyContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
        }

        updatedInstance = submission.getContainingDocument().getInstanceForNode(destinationNodeInfo);
        if (updatedInstance == null || !updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo)) {
            // Only support replacing the root element of an instance
            // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
            throw new XFormsSubmissionException(submission, "targetref attribute must point to an instance root element when using cached/shared instance replacement.", "processing targetref attribute",
                    new XFormsSubmitErrorEvent(propertyContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
        }

        if (XFormsServer.logger.isDebugEnabled())
            submission.getContainingDocument().logDebug("submission", "using instance from application shared instance cache",
                    "instance", updatedInstance.getEffectiveId());
        return updatedInstance;
    }
}
