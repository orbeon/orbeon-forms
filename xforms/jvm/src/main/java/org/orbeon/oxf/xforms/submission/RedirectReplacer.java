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

import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;

import java.io.IOException;

/**
 * Handle redirect for replace="all".
 */
public class RedirectReplacer extends BaseReplacer {

    public RedirectReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        super(submission, containingDocument);
    }

    public void deserialize(ConnectionResult connectionResult, SubmissionParameters p, SecondPassParameters p2) {
       // NOP
    }

    public Runnable replace(ConnectionResult connectionResult, SubmissionParameters p, SecondPassParameters p2) throws IOException {

        final ExternalContext.Response response = NetUtils.getExternalContext().getResponse();
        containingDocument.setGotSubmissionRedirect();
        replace(connectionResult, response);

        return null;
    }

    public static void replace(ConnectionResult connectionResult, final ExternalContext.Response response) throws IOException {
        SubmissionUtils.forwardResponseHeaders(connectionResult, response);
        response.setStatus(connectionResult.statusCode());
    }
}
