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

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;

public class XFormsSubmissionException extends ValidationException {

    private XFormsSubmitErrorEvent submitErrorEvent;

    public XFormsSubmissionException(XFormsModelSubmission submission, String message, String description, XFormsSubmitErrorEvent submitErrorEvent) {
        this(submission, message, description);
        this.submitErrorEvent = submitErrorEvent;
    }

    public XFormsSubmissionException(XFormsModelSubmission submission, String message, String description) {
        super(message, new ExtendedLocationData(submission.getLocationData(), description,
                submission.getSubmissionElement()));
    }

    public XFormsSubmissionException(XFormsModelSubmission submission, Throwable e, String message, String description) {
        super(message, e, new ExtendedLocationData(submission.getLocationData(), description,
                submission.getSubmissionElement()));
    }

    public XFormsSubmissionException(XFormsModelSubmission submission, Throwable e, String message, String description, XFormsSubmitErrorEvent submitErrorEvent) {
        this(submission, e, message, description);
        this.submitErrorEvent = submitErrorEvent;
    }

    public XFormsSubmitErrorEvent getSubmitErrorEvent() {
        return submitErrorEvent;
    }
}
