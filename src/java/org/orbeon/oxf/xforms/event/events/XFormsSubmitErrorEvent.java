/**
 *  Copyright (C) 2005 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.ListIterator;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.value.StringValue;

import java.io.CharArrayWriter;
import java.io.PrintWriter;
import java.util.Collections;


/**
 * 4.4.19 The xforms-submit-error Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: The submit method URI that failed (xsd:anyURI)
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsSubmitErrorEvent extends XFormsEvent {

    private Throwable throwable;
    private String urlString;
    private DocumentInfo bodyDocument;
    private String bodyString;
    private final ErrorType errorType;

    public XFormsSubmitErrorEvent(XFormsEventTarget targetObject, String urlString, ErrorType errorType) {
        super(XFormsEvents.XFORMS_SUBMIT_ERROR, targetObject, true, false);
        this.urlString = urlString;
        this.errorType = errorType;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;

        // Log exception at error level
        final CharArrayWriter writer = new CharArrayWriter();
        OXFException.getRootThrowable(throwable).printStackTrace(new PrintWriter(writer));
        XFormsServer.logger.error("XForms - submission - xforms-submit-error throwable: " + writer.toString());
    }

    public String getUrlString() {
        return urlString;
    }

    public DocumentInfo getBodyDocument() {
        return bodyDocument;
    }

    public void setBodyDocument(DocumentInfo bodyDocument) {
        this.bodyDocument = bodyDocument;
    }

    public String getBodyString() {
        return bodyString;
    }

    public void setBodyString(String bodyString) {
        this.bodyString = bodyString;
    }

    public SequenceIterator getAttribute(String name) {

        if ("response-body".equals(name) || "body".equals(name)) {// NOTE: "body" for backward compatibility
            // Return the body of the response if possible

            if ("body".equals(name)) {
                XFormsServer.logger.warn("event('body') on xforms-submit-error is deprecated. Use event('response-body') instead.");
            }

            // "When the error response specifies an XML media type as defined by [RFC 3023], the response body is
            // parsed into an XML document and the root element of the document is returned. If the parse fails, or if
            // the error response specifies a text media type (starting with text/), then the response body is returned
            // as a string. Otherwise, an empty string is returned."

            if (getBodyDocument() != null)
                return new ListIterator(Collections.singletonList(getBodyDocument()));
            else if (getBodyString() != null)
                return new ListIterator(Collections.singletonList(new StringValue(getBodyString())));
            else
                return new ListIterator(Collections.singletonList(StringValue.EMPTY_STRING));
        } else if ("resource-uri".equals(name)) {
            // "The submission resource URI that failed (xsd:anyURI)"
            return new ListIterator(Collections.singletonList(new StringValue(getUrlString())));
        } else if ("error-type".equals(name)) {
            // "One of the following: submission-in-progress, no-data, validation-error, parse-error, resource-error,
            // target-error."
            return new ListIterator(Collections.singletonList(new StringValue(String.valueOf(errorType))));
        } else if ("response-status-code".equals(name)) {
            // "The protocol return code of the error response, or NaN if the failed submission did not receive an error
            // response."
            throw new ValidationException("Property Not implemented yet: " + name, getLocationData());
            // TODO
        } else if ("response-headers".equals(name)) {
            // "Zero or more elements, each one representing a content header in the error response received by a
            // failed submission. The returned node-set is empty if the failed submission did not receive an error
            // response or if there were no headers. Each element has a local name of header with no namespace URI and
            // two child elements, name and value, whose string contents are the name and value of the header,
            // respectively."
            throw new ValidationException("Property Not implemented yet: " + name, getLocationData());
            // TODO
        } else if ("response-reason-phrase".equals(name)) {
            // "The protocol response reason phrase of the error response. The string is empty if the failed submission
            // did not receive an error response or if the error response did not contain a reason phrase."
            throw new ValidationException("Property Not implemented yet: " + name, getLocationData());
            // TODO
        } else {
            return super.getAttribute(name);
        }
    }

    /** Enumeration of possible error-type values. */
    public static class ErrorType {

    	public static final ErrorType SUBMISSION_IN_PROGRESS = new ErrorType("submission-in-progress");
    	public static final ErrorType NO_DATA = new ErrorType("no-data");
    	public static final ErrorType VALIDATION_ERROR = new ErrorType("validation-error");
    	public static final ErrorType RESOURCE_ERROR = new ErrorType("resource-error");
    	public static final ErrorType PARSE_ERROR = new ErrorType("parse-error");
        public static final ErrorType TARGET_ERROR = new ErrorType("target-error");
        public static final ErrorType XXFORMS_INTERNAL_ERROR = new ErrorType("xxforms-internal-error");

        private final String errorType;

    	private ErrorType(final String errorType) {
    		this.errorType = errorType;
        }

		public String toString() {
			return errorType;
		}
    }
}