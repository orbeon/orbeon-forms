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
package org.orbeon.oxf.xforms.event.events;

import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.EmptyIterator;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.value.Int64Value;
import org.orbeon.saxon.value.StringValue;

import java.util.*;


/**
 * Base class for xforms-submit-done and xforms-submit-error events.
 */
public abstract class XFormsSubmitResponseEvent extends XFormsEvent {

    private final String resourceURI;
    private final Map<String, List<String>>  headers;
    private final int statusCode;

    public XFormsSubmitResponseEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, ConnectionResult connectionResult) {
        super(containingDocument, eventName, targetObject, true, false);
        if (connectionResult != null) {
            this.resourceURI = connectionResult.resourceURI;
            this.headers = connectionResult.responseHeaders;
            this.statusCode = connectionResult.statusCode;
        } else {
            this.resourceURI = null;
            this.headers = null;
            this.statusCode = 0;
        }
    }

    public XFormsSubmitResponseEvent(XFormsContainingDocument containingDocument, String eventName, XFormsEventTarget targetObject, String resourceURI, int statusCode) {
        super(containingDocument, eventName, targetObject, true, false);
        this.resourceURI = resourceURI;
        this.headers = null;
        this.statusCode = statusCode;
    }

    @Override
    public SequenceIterator getAttribute(String name) {

        if ("resource-uri".equals(name)) {
            // "The submission resource URI that succeeded/failed (xsd:anyURI)"
            return SingletonIterator.makeIterator(new StringValue(resourceURI));
        } else if ("response-status-code".equals(name)) {
            // "The protocol return code of the error response, or NaN if the failed submission did not receive an error
            // response."
            if (statusCode > 0)
                return SingletonIterator.makeIterator(new Int64Value(statusCode));
            else
                return EmptyIterator.getInstance();// instead of returning NaN, we return an empty
        } else if ("response-headers".equals(name)) {
            // "Zero or more elements, each one representing a content header in the error response received by a
            // failed submission. The returned node-set is empty if the failed submission did not receive an error
            // response or if there were no headers. Each element has a local name of header with no namespace URI and
            // two child elements, name and value, whose string contents are the name and value of the header,
            // respectively."

            if (headers != null && headers.size() > 0) {
                // Create and return sequence of <header> elements
                final StringBuilder sb = new StringBuilder(100);
                sb.append("<headers>");
                for (Map.Entry<String, List<String>> currentEntry: headers.entrySet()) {
                    sb.append("<header><name>");
                    sb.append(XMLUtils.escapeXMLMinimal(currentEntry.getKey()));
                    sb.append("</name>");
                    for (String headerValue: currentEntry.getValue()) {
                        sb.append("<value>");
                        sb.append(XMLUtils.escapeXMLMinimal(headerValue));
                        sb.append("</value>");
                    }
                    sb.append("</header>");
                }

                sb.append("</headers>");

                final Item headersDocument = TransformerUtils.stringToTinyTree(getContainingDocument().getStaticState().xpathConfiguration(),
                        sb.toString(), false, false);

                return XPathCache.evaluateAsExtent(Collections.singletonList(headersDocument), 1,
                        "/headers/header", NamespaceMapping.EMPTY_MAPPING, null, null, null, null, getLocationData()).iterate();
            } else {
                // No headers
                return EmptyIterator.getInstance();
            }
        } else if ("response-reason-phrase".equals(name)) {
            // "The protocol response reason phrase of the error response. The string is empty if the failed submission
            // did not receive an error response or if the error response did not contain a reason phrase."
            throw new ValidationException("Property Not implemented yet: " + name, getLocationData());
            // TODO
        } else {
            return super.getAttribute(name);
        }
    }

    @Override
    protected IndentedLogger getIndentedLogger () {
        return getContainingDocument().getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
    }
}
