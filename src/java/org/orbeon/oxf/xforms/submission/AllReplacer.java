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

import org.orbeon.oxf.externalcontext.ResponseWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Handle replace="all".
 */
public class AllReplacer extends BaseReplacer {

    public AllReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void deserialize(ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) {
        // NOP
    }

    public Runnable replace(ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws IOException {

        // When we get here, we are in a mode where we need to send the reply directly to an external context, if any.

        // Remember that we got a submission producing output
        containingDocument.setGotSubmissionReplaceAll();

        final ReplaceAllResponse replaceAllResponse = new ReplaceAllResponse(containingDocument.getResponse());
        replace(connectionResult, replaceAllResponse);
        // Update status code if it was updated
        if (replaceAllResponse.getStatus() > 0)
            connectionResult.statusCode = replaceAllResponse.getStatus();

        // Success: "the event xforms-submit-done may be dispatched with appropriate context information"
        // Error: "either the document is replaced with an implementation-specific indication of an error or submission
        // processing concludes after dispatching xforms-submit-error with appropriate context information, including an
        // error-type of resource-error"
        if (! p.isDeferredSubmissionSecondPassReplaceAll) {
            if (XFormsSubmissionUtils.isSuccessCode(connectionResult.statusCode))
                return dispatchSubmitDone(connectionResult);
            else
                // Here we dispatch xforms-submit-error upon getting a non-success error code, even though the response has
                // already been written out. This gives the form author a chance to do something in cases the response is
                // buffered, for example do a sendError().
                throw new XFormsSubmissionException(submission, "xforms:submission for submission id: " + submission.getId() + ", error code received when submitting instance: " + connectionResult.statusCode, "processing submission response",
                        new XFormsSubmitErrorEvent(containingDocument, submission, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
        } else {
            // We don't want any changes to happen to the document upon xxforms-submit when producing a new document, so
            // we don't dispatch success/error events.
            return null;
        }
    }

    public static void replace(ConnectionResult connectionResult, final ExternalContext.Response response) throws IOException {

        if (response == null)
            return; // can be null for some unit tests

        // Set content-type
        response.setContentType(connectionResult.getResponseContentType());

        // Forward headers to response
        connectionResult.forwardHeaders(response);

        // Forward content to response
        final OutputStream outputStream = response.getOutputStream();
        NetUtils.copyStream(connectionResult.getResponseInputStream(), outputStream);

        // End document and close
        outputStream.flush();
        outputStream.close();
    }

    public static class ReplaceAllResponse extends ResponseWrapper {

        private int status = -1; // indicate that status was not set

        public ReplaceAllResponse(ExternalContext.Response response) {
            super(response);
        }

        @Override
        public void setStatus(int status) {
            assert status > 0;

            this.status = status;
            super.setStatus(status);
        }

        public int getStatus() {
            return status;
        }
    }
}
