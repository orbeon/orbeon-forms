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

    public XFormsSubmitErrorEvent(XFormsEventTarget targetObject, String urlString) {
        super(XFormsEvents.XFORMS_SUBMIT_ERROR, targetObject, true, false);
        this.urlString = urlString;
    }

    public Throwable getThrowable() {
        return throwable;
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;

        // Log exception
        if (XFormsServer.logger.isDebugEnabled()) {
            CharArrayWriter writer = new CharArrayWriter();
            OXFException.getRootThrowable(throwable).printStackTrace(new PrintWriter(writer));
            XFormsServer.logger.debug("XForms - submit error throwable: " + writer.toString());
        }
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

        if ("body".equals(name)) {
            // Return the body of the response if possible
            if (getBodyDocument() != null)
                return new ListIterator(Collections.singletonList(getBodyDocument()));
            else if (getBodyString() != null)
                return new ListIterator(Collections.singletonList(new StringValue(getBodyString())));
            else
                return super.getAttribute(name);
        } else if ("resource-uri".equals(name)) {
            return new ListIterator(Collections.singletonList(new StringValue(getUrlString())));
        } else {
            return super.getAttribute(name);
        }
    }
}
