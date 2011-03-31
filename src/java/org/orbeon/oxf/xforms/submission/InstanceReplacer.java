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

import org.dom4j.Document;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.*;

import java.util.Collections;
import java.util.List;

/**
 * Handle replace="instance".
 */
public class InstanceReplacer extends BaseReplacer {

    private Object resultingDocument;           // Document or DocumentInfo
    private String resultingRequestBodyHash;    // can be null

    public InstanceReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void deserialize(ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws Exception {
        // Deserialize here so it can run in parallel
        if (XMLUtils.isXMLMediatype(connectionResult.getResponseMediaType())) {
            // XML media type
            final IndentedLogger detailsLogger = getDetailsLogger(p, p2);
            resultingDocument = deserializeInstance(detailsLogger, p2.isReadonly, p2.isHandleXInclude, connectionResult);
        } else {
            // Other media type is not allowed
            throw new XFormsSubmissionException(submission, "Body received with non-XML media type for replace=\"instance\": " + connectionResult.getResponseMediaType(), "processing instance replacement",
                    new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
        }
    }

    private Object deserializeInstance(IndentedLogger indentedLogger, boolean isReadonly, boolean isHandleXInclude, ConnectionResult connectionResult) throws Exception {
        final Object resultingDocument;

        // Create resulting instance whether entire instance is replaced or not, because this:
        // 1. Wraps a Document within a DocumentInfo if needed
        // 2. Performs text nodes adjustments if needed
        try {
            if (!isReadonly) {
                // Resulting instance must not be read-only

                // TODO: What about configuring validation? And what default to choose?
                resultingDocument = TransformerUtils.readDom4j(connectionResult.getResponseInputStream(), connectionResult.resourceURI, isHandleXInclude, true);

                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("", "deserializing to mutable instance");
            } else {
                // Resulting instance must be read-only

                // TODO: What about configuring validation? And what default to choose?
                // NOTE: isApplicationSharedHint is always false when get get here. isApplicationSharedHint="true" is handled above.
                resultingDocument = TransformerUtils.readTinyTree(containingDocument.getStaticState().getXPathConfiguration(),
                        connectionResult.getResponseInputStream(), connectionResult.resourceURI, isHandleXInclude, true);

                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("", "deserializing to read-only instance");
            }
        } catch (Exception e) {
            throw new XFormsSubmissionException(submission, e, "xforms:submission: exception while reading XML response.", "processing instance replacement",
                    new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.PARSE_ERROR, connectionResult));
        }

        return resultingDocument;
    }

    public Runnable replace(final ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) {

        // Set new instance document to replace the one submitted

        final XFormsInstance replaceInstanceNoTargetref = submission.findReplaceInstanceNoTargetref(p.refInstance);
        if (replaceInstanceNoTargetref == null) {

            // Replacement instance or node was specified but not found
            //
            // Not sure what's the right thing to do with 1.1, but this could be done
            // as part of the model's static analysis if the instance value is not
            // obtained through AVT, and dynamically otherwise. However, in the dynamic
            // case, I think that this should be a (currently non-specified by XForms)
            // xforms-binding-error.

            submission.getXBLContainer(containingDocument).dispatchEvent(new XFormsBindingExceptionEvent(containingDocument, submission));
            return null;
        } else {

            final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(
                    p.xpathContext, replaceInstanceNoTargetref, p.submissionElementContextItem);

            if (destinationNodeInfo == null) {
                // Throw target-error

                // XForms 1.1: "If the processing of the targetref attribute fails,
                // then submission processing ends after dispatching the event
                // xforms-submit-error with an error-type of target-error."

                throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
            }

            // This is the instance which is effectively going to be updated
            final XFormsInstance updatedInstance = containingDocument.getInstanceForNode(destinationNodeInfo);
            if (updatedInstance == null) {
                throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element in an existing instance for replace=\"instance\".", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
            }

            // Whether the destination node is the root element of an instance
            final boolean isDestinationRootElement = updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo);
            if (p2.isReadonly && !isDestinationRootElement) {
                // Only support replacing the root element of an instance when using a shared instance
                throw new XFormsSubmissionException(submission, "targetref attribute must point to instance root element when using read-only instance replacement.", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
            }

            final IndentedLogger detailsLogger = getDetailsLogger(p, p2);

            // Obtain root element to insert
            final NodeInfo newDocumentRootElement;
            final XFormsInstance newInstance;
            {
                // Create resulting instance whether entire instance is replaced or not, because this:
                // 1. Wraps a Document within a DocumentInfo if needed
                // 2. Performs text nodes adjustments if needed
                if (!p2.isReadonly) {
                    // Resulting instance must not be read-only

                    if (detailsLogger.isDebugEnabled())
                        detailsLogger.logDebug("", "replacing instance with mutable instance",
                            "instance", updatedInstance.getEffectiveId());

                    newInstance = new XFormsInstance(containingDocument.getStaticState().getXPathConfiguration(), updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                            (Document) resultingDocument, connectionResult.resourceURI, resultingRequestBodyHash, p2.username, p2.password, p2.domain,
                            p2.isCache, p2.timeToLive, updatedInstance.getValidation(), p2.isHandleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument));
                } else {
                    // Resulting instance must be read-only

                    if (detailsLogger.isDebugEnabled())
                        detailsLogger.logDebug("", "replacing instance with read-only instance",
                            "instance", updatedInstance.getEffectiveId());

                    newInstance = new ReadonlyXFormsInstance(updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                            (DocumentInfo) resultingDocument, connectionResult.resourceURI, resultingRequestBodyHash, p2.username, p2.password, p2.domain,
                            p2.isCache, p2.timeToLive, updatedInstance.getValidation(), p2.isHandleXInclude, XFormsProperties.isExposeXPathTypes(containingDocument));
                }
                newDocumentRootElement = newInstance.getInstanceRootElementInfo();
            }

            // Perform insert/delete. This will dispatch xforms-insert/xforms-delete events.
            // "the replacement is performed by an XForms action that performs some
            // combination of node insertion and deletion operations that are
            // performed by the insert action (10.3 The insert Element) and the
            // delete action"

            // NOTE: As of 2009-03-18 decision, XForms 1.1 specifies that deferred event handling flags are set instead of
            // performing RRRR directly.

            if (isDestinationRootElement) {
                // Optimized insertion for instance root element replacement

                // Update model instance
                final XFormsModel replaceModel = newInstance.getModel(containingDocument);
                replaceModel.setInstance(newInstance, true);

                // Call this directly, since we are not using insert/delete here
                replaceModel.markStructuralChange(newInstance);

                // Dispatch xforms-delete event
                // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
                // (see below). In the future, we might want to dispatch this, especially if
                // XFormsInsertAction dispatches xforms-delete when removing the root element
                //updatedInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(updatedInstance, Collections.singletonList(destinationNodeInfo), 1));

                // Dispatch xforms-insert event
                // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
                newInstance.getXBLContainer(containingDocument).dispatchEvent(
                        new XFormsInsertEvent(containingDocument, newInstance, Collections.singletonList((Item) newDocumentRootElement), null, newDocumentRootElement.getDocumentRoot(),
                            "after", null, null));

            } else {
                // Generic insertion

                final List<NodeInfo> destinationCollection = Collections.singletonList(destinationNodeInfo);

                // Perform the insertion

                // Insert before the target node, so that the position of the inserted node
                // wrt its parent does not change after the target node is removed
                XFormsInsertAction.doInsert(containingDocument, detailsLogger, "before",
                        destinationCollection, destinationNodeInfo.getParent(),
                        Collections.singletonList(newDocumentRootElement), 1, false, true);

                if (!destinationNodeInfo.getParent().isSameNodeInfo(destinationNodeInfo.getDocumentRoot())) {
                    // The node to replace is NOT a root element

                    // Perform the deletion of the selected node
                    XFormsDeleteAction.doDelete(containingDocument, detailsLogger, destinationCollection, 1, true);
                }

                // Update model instance
                // NOTE: The inserted node NodeWrapper.index might be out of date at this point because:
                // * doInsert() dispatches an event which might itself change the instance
                // * doDelete() does as well
                // Does this mean that we should check that the node is still where it should be?
                final XFormsModel updatedModel = updatedInstance.getModel(containingDocument);
                updatedModel.setInstance(updatedInstance, true);
            }

            // Dispatch xforms-submit-done
            return dispatchSubmitDone(connectionResult);
        }
    }

    public void setInstance(XFormsInstance instance) {
        final Document instanceDocument = instance.getDocument();
        this.resultingRequestBodyHash = instance.getRequestBodyHash();
        this.resultingDocument = (instanceDocument != null) ? instanceDocument : instance.getDocumentInfo();
    }

    public Object getResultingDocument() {
        return resultingDocument;
    }
}
