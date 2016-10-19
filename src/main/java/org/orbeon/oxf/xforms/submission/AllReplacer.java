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
import org.orbeon.oxf.webapp.ExternalContext;
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

    public void deserialize(ConnectionResult cxr, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) {
        // NOP
    }

    public Runnable replace(ConnectionResult cxr, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws IOException {

        // When we get here, we are in a mode where we need to send the reply directly to an external context, if any.

        // Remember that we got a submission producing output
        containingDocument.setGotSubmissionReplaceAll();

        final ReplaceAllResponse replaceAllResponse = new ReplaceAllResponse(containingDocument.getResponse());
        forwardResultToResponse(cxr, replaceAllResponse);

        // Success: "the event xforms-submit-done may be dispatched with appropriate context information"
        // Error: "either the document is replaced with an implementation-specific indication of an error or submission
        // processing concludes after dispatching xforms-submit-error with appropriate context information, including an
        // error-type of resource-error"
        if (! p.isDeferredSubmissionSecondPass) {
            if (NetUtils.isSuccessCode(cxr.statusCode()))
                return submission.sendSubmitDone(cxr);
            else
                // Here we dispatch xforms-submit-error upon getting a non-success error code, even though the response has
                // already been written out. This gives the form author a chance to do something in cases the response is
                // buffered, for example do a sendError().
                throw new XFormsSubmissionException(submission, "xf:submission for submission id: " + submission.getId() + ", error code received when submitting instance: " + cxr.statusCode(), "processing submission response",
                        new XFormsSubmitErrorEvent(submission, XFormsSubmitErrorEvent.RESOURCE_ERROR(), cxr));
        } else {
            // Two reasons: 1. We don't want to modify the document state 2. This can be called outside of the document
            // lock, see XFormsServer.
            return null;
        }
    }

    public static void forwardResultToResponse(ConnectionResult cxr, final ExternalContext.Response response) throws IOException {

        if (response == null)
            return; // can be null for some unit tests only :(

        response.setStatus(cxr.statusCode());
        if (cxr.content().contentType().isDefined())
            response.setContentType(cxr.content().contentType().get());
        SubmissionUtils.forwardResponseHeaders(cxr, response);

        // Forward content to response
        final OutputStream outputStream = response.getOutputStream();

        try {
            NetUtils.copyStream(cxr.content().inputStream(), outputStream);
        } finally {
            cxr.close();
        }

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
