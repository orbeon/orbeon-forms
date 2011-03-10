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

import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsContainingDocument;

import java.io.IOException;

/**
 * Handle redirect for replace="all".
 */
public class RedirectReplacer extends BaseReplacer {

    public RedirectReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void deserialize(PropertyContext propertyContext, ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) {
       // NOP
    }

    //private void doReplaceAll(PipelineContext pipelineContext, ConnectionResult connectionResult, boolean deferredSubmissionSecondPassReplaceAll) throws IOException {

    public Runnable replace(PropertyContext propertyContext, ConnectionResult connectionResult, XFormsModelSubmission.SubmissionParameters p, XFormsModelSubmission.SecondPassParameters p2) throws IOException {

        final ExternalContext.Response response = NetUtils.getExternalContext(propertyContext).getResponse();

        // Remember that we got a redirect
        containingDocument.setGotSubmissionRedirect();

        // Forward headers to response
        connectionResult.forwardHeaders(response);

        // Forward redirect
        response.setStatus(connectionResult.statusCode);

        return null;
    }
}
