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

/*
 * This must either have a non-null Replacer or a non-null Throwable.
 */
public class SubmissionResult {

    private String submissionEffectiveId;
    private Replacer replacer;

    private Throwable throwable;
    private ConnectionResult connectionResult;

    public SubmissionResult(String submissionEffectiveId, Replacer replacer, ConnectionResult connectionResult) {
        this.submissionEffectiveId = submissionEffectiveId;
        this.replacer = replacer;
        this.connectionResult = connectionResult;
    }

    public SubmissionResult(String submissionEffectiveId, Throwable throwable, ConnectionResult connectionResult) {
        this.submissionEffectiveId = submissionEffectiveId;
        this.throwable = throwable;
        this.connectionResult = connectionResult;
    }

    public String getSubmissionEffectiveId() {
        return submissionEffectiveId;
    }

    public Replacer getReplacer() {
        return replacer;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public ConnectionResult getConnectionResult() {
        return connectionResult;
    }

    /**
     * Close the result once everybody is done with it.
     *
     * This can be overridden by specific subclasses.
     */
    public void close() {
        if (connectionResult != null) {
            connectionResult.close();
        }
    }
}
