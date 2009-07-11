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
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.SecureUtils;
import org.orbeon.oxf.xforms.ReadonlyXFormsInstance;
import org.orbeon.oxf.xforms.XFormsInstance;
import org.orbeon.oxf.xforms.XFormsModel;
import org.orbeon.oxf.xforms.XFormsServerSharedInstancesCache;
import org.orbeon.oxf.xforms.event.events.XFormsInsertEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.net.URL;
import java.util.Collections;

/**
 * Cacheable remote submission going through a protocol handler.
 */
public class CacheableSubmission extends SubmissionBase {

    public CacheableSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return p2.resolvedXXFormsCache;
    }

    public ConnectionResult connect(PipelineContext pipelineContext, final XFormsModelSubmission.SubmissionParameters p,
                                    final XFormsModelSubmission.SecondPassParameters p2, final XFormsModelSubmission.SerializationParameters sp) {
        // Get the instance from shared instance cache
        // This can only happen is method="get" and replace="instance" and xxforms:cache="true" or xxforms:shared="application"

        final ExternalContext externalContext = getExternalContext(pipelineContext);
        final ExternalContext.Request request = externalContext.getRequest();
        final URL absoluteResolvedURL = getResolvedSubmissionURL(pipelineContext, externalContext, request, p2.resolvedActionOrResource, sp.queryString);
        // Convert URL to string
        final String absoluteResolvedURLString = absoluteResolvedURL.toExternalForm();

        // Find and check replacement location
        final XFormsInstance updatedInstance;
        {
            final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(pipelineContext, p.xpathContext,
                    submission.findReplaceInstanceNoTargetref(p.refInstance), p.submissionElementContextItem);

            if (destinationNodeInfo == null) {
                // Throw target-error

                // XForms 1.1: "If the processing of the targetref attribute fails,
                // then submission processing ends after dispatching the event
                // xforms-submit-error with an error-type of target-error."

                throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
            }

            updatedInstance = submission.getContainingDocument().getInstanceForNode(destinationNodeInfo);
            if (updatedInstance == null || !updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo)) {
                // Only support replacing the root element of an instance
                // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
                throw new XFormsSubmissionException(submission, "targetref attribute must point to an instance root element when using cached/shared instance replacement.", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
            }

            if (XFormsServer.logger.isDebugEnabled())
                submission.getContainingDocument().logDebug("submission", "using instance from application shared instance cache",
                        "instance", updatedInstance.getEffectiveId());
        }


        final long timeToLive = XFormsInstance.getTimeToLive(submission.getSubmissionElement());

        // Compute a hash of the body if possible
        final String requestBodyHash;
        if (sp.messageBody != null) {
            requestBodyHash = SecureUtils.digestBytes(sp.messageBody, "MD5", "hex");
        } else {
            requestBodyHash = null;
        }

        final XFormsInstance newInstance
                = XFormsServerSharedInstancesCache.instance().findConvert(pipelineContext, submission.getContainingDocument(), updatedInstance.getId(), updatedInstance.getEffectiveModelId(),
                    absoluteResolvedURLString, requestBodyHash, p2.resolvedXXFormsReadonly, p2.resolvedXXFormsHandleXInclude, timeToLive, updatedInstance.getValidation(), new XFormsServerSharedInstancesCache.Loader() {
                    public ReadonlyXFormsInstance load(PipelineContext pipelineContext, String instanceStaticId, String modelEffectiveId, String instanceSourceURI, boolean handleXInclude, long timeToLive, String validation) {

                        // Call regular submission
                        final ConnectionResult connectionResult = new RegularSubmission(submission).connect(pipelineContext, p, p2, sp);
                        try {
                            // Handle connection errors
                            if (connectionResult.statusCode != 200) {
                                connectionResult.close();
                                throw new OXFException("Got invalid return code while loading instance from URI: " + instanceSourceURI + ", " + connectionResult.statusCode);
                            }
                            // Read into TinyTree
                            // TODO: Handle validating?
                            final DocumentInfo documentInfo = TransformerUtils.readTinyTree(connectionResult.getResponseInputStream(), connectionResult.resourceURI, handleXInclude);

                            // Create new shared instance
                            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, documentInfo, instanceSourceURI,
                                    p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword, true, timeToLive, validation, handleXInclude);
                        } catch (Exception e) {
                            throw new OXFException("Got exception while loading instance from URI: " + instanceSourceURI, e);
                        } finally {
                            // Clean-up
                            connectionResult.close();
                        }
                    }
                });

        if (XFormsServer.logger.isDebugEnabled()) {
            submission.getContainingDocument().logDebug("submission", "replacing instance with " + (p2.resolvedXXFormsReadonly ? "read-only" : "read-write") +  " cached instance",
                        "instance", newInstance.getEffectiveId());
        }

        final XFormsModel replaceModel = newInstance.getModel(submission.getContainingDocument());

        // Dispatch xforms-delete event
        // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
        // (see below). In the future, we might want to dispatch this, especially if
        // XFormsInsertAction dispatches xforms-delete when removing the root element
        //updatedInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(updatedInstance, Collections.singletonList(destinationNodeInfo), 1));

        // Handle new instance and associated event markings
        final NodeInfo newRootElementInfo = newInstance.getInstanceRootElementInfo();
        replaceModel.handleUpdatedInstance(pipelineContext, newInstance, newRootElementInfo);

        // Dispatch xforms-insert event
        // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
        newInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext,
                            new XFormsInsertEvent(newInstance, Collections.singletonList((Item) newRootElementInfo), null, newRootElementInfo.getDocumentRoot(),
                    "after", null, null, true));


        // If no exception, submission is done here: just dispatch the event
        submission.getXBLContainer(containingDocument).dispatchEvent(pipelineContext,
                            new XFormsSubmitDoneEvent(submission, absoluteResolvedURLString, 200));

        // Return minimal information to caller
        final ConnectionResult connectionResult = new ConnectionResult(absoluteResolvedURLString);
        connectionResult.dontHandleResponse = true;
        connectionResult.statusCode = 200;

        return connectionResult;
    }
}
