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

import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.saxon.om.NodeInfo;

import java.io.IOException;

/**
 * Handle replace="text".
 */
public class TextReplacer extends BaseReplacer {

    private String responseBody;

    public TextReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void deserialize(ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws IOException {
        responseBody = connectionResult.getTextResponseBody();
        if (responseBody == null) {
            // This is a binary result

            // Don't store anything for now as per the spec, but we could do something better by going beyond the spec
            // NetUtils.inputStreamToAnyURI(pipelineContext, connectionResult.resultInputStream, NetUtils.SESSION_SCOPE);

            // XForms 1.1: "For a success response including a body that is both a non-XML media type (i.e. with a
            // content type not matching any of the specifiers in [RFC 3023]) and a non-text type (i.e. with a content
            // type not matching text/*), when the value of the replace attribute on element submission is "text",
            // nothing in the document is replaced and submission processing concludes after dispatching
            // xforms-submit-error with appropriate context information, including an error-type of resource-error."
            throw new XFormsSubmissionException(submission, "Mediatype is neither text nor XML for replace=\"text\": " + connectionResult.getResponseMediaType(), "reading response body",
                    new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
        }
    }

    public Runnable replace(ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws IOException {

        // XForms 1.1: "If the replace attribute contains the value "text" and the submission response conforms to an
        // XML mediatype (as defined by the content type specifiers in [RFC 3023]) or a text media type (as defined by
        // a content type specifier of text/*), then the response data is encoded as text and replaces the content of
        // the replacement target node."

        // Find target location
        final NodeInfo destinationNodeInfo;
        if (submission.getTargetref() != null) {
            // Evaluate destination node
            final Object destinationObject
                    = XPathCache.evaluateSingle(p.xpathContext, p.refNodeInfo, submission.getTargetref());

            if (destinationObject instanceof NodeInfo) {
                destinationNodeInfo = (NodeInfo) destinationObject;
                if (destinationNodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE && destinationNodeInfo.getNodeKind() != org.w3c.dom.Document.ATTRIBUTE_NODE) {
                    // Throw target-error

                    // XForms 1.1: "If the processing of the targetref attribute fails,
                    // then submission processing ends after dispatching the event
                    // xforms-submit-error with an error-type of target-error."
                    throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to an element or attribute for replace=\"text\".", "processing targetref attribute",
                            new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                }
            } else {
                // Throw target-error
                // TODO: also do this for readonly situation

                // XForms 1.1: "If the processing of the targetref attribute fails, then
                // submission processing ends after dispatching the event
                // xforms-submit-error with an error-type of target-error."
                throw new XFormsSubmissionException(submission, "targetref attribute doesn't point to a node for replace=\"text\".", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
            }
        } else {
            // Handle default destination
            destinationNodeInfo = submission.findReplaceInstanceNoTargetref(p.refInstance).getInstanceRootElementInfo();
        }

        // Set value into the instance
        // NOTE: Here we decided to use the actions logger, by compatibility with xforms:setvalue. Anything we would like to log in "submission" mode?
        XFormsSetvalueAction.doSetValue(containingDocument, containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY), submission, destinationNodeInfo, responseBody, null, "submission", false);

        // Dispatch xforms-submit-done
        return dispatchSubmitDone(connectionResult);
    }
}
