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

import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.XFormsContainingDocument;

public abstract class BaseReplacer implements Replacer {

    protected final XFormsModelSubmission submission;
    protected final XFormsContainingDocument containingDocument;

    public BaseReplacer(XFormsModelSubmission submission, XFormsContainingDocument containingDocument) {
        this.submission = submission;
        this.containingDocument = containingDocument;
    }

    protected IndentedLogger getDetailsLogger(final SubmissionParameters p, final SecondPassParameters p2) {
        return submission.getDetailsLogger(p, p2);
    }
}
