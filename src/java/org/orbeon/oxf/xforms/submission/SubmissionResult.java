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

import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.xforms.XFormsInstance;

public class SubmissionResult {
    private String submissionEffectiveId;
    private ConnectionResult connectionResult;
    private XFormsInstance instance;

    public SubmissionResult(String submissionEffectiveId, ConnectionResult connectionResult) {
        this.submissionEffectiveId = submissionEffectiveId;
        this.connectionResult = connectionResult;
    }

    public SubmissionResult(String submissionEffectiveId, XFormsInstance instance) {
        this.submissionEffectiveId = submissionEffectiveId;
        this.instance = instance;
    }

    public String getSubmissionEffectiveId() {
        return submissionEffectiveId;
    }

    public ConnectionResult getConnectionResult() {
        return connectionResult;
    }

    public XFormsInstance getInstance() {
        return instance;
    }

    public void close() {
        if (connectionResult != null) {
            connectionResult.close();
        }
    }
}
