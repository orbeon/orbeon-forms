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

import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.xforms.action.actions.XFormsLoadAction;

/**
 * Optimized GET submission. It stores the resulting location directly into the containing document.
 */
public class OptimizedGetSubmission extends BaseSubmission {

    public OptimizedGetSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public boolean isMatch(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {
        return p.isHandlingOptimizedGet;
    }

    public ConnectionResult connect(PipelineContext pipelineContext, XFormsModelSubmission.SubmissionParameters p,
                                    XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        final String actionString = (sp.queryString == null) ? p2.resolvedActionOrResource : p2.resolvedActionOrResource + ((p2.resolvedActionOrResource.indexOf('?') == -1) ? "?" : "") + sp.queryString;
        final String resultURL = XFormsLoadAction.resolveLoadValue(containingDocument, pipelineContext, submission.getSubmissionElement(), true, actionString, null, null, submission.isURLNorewrite(), submission.isShowProgress());

        final ConnectionResult connectionResult = new ConnectionResult(resultURL);
        connectionResult.dontHandleResponse = true;
        return connectionResult;
    }
}
