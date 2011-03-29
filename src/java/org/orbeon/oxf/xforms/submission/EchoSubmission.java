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

import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.Connection;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xml.XMLUtils;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;

/**
 * Test submission which just echoes the incoming document.
 */
public class EchoSubmission extends BaseSubmission {

    public EchoSubmission(XFormsModelSubmission submission) {
        super(submission);
    }

    public String getType() {
        return "echo";
    }

    public boolean isMatch(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p,
                           XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) {

        // Match for replace="instance|none|all" and the submission resource starts with "test:" or "echo:"
        return (p.isReplaceInstance || p.isReplaceNone || p.isReplaceAll) && (p2.actionOrResource.startsWith("test:") || p2.actionOrResource.startsWith("echo:"));
    }

    public SubmissionResult connect(PropertyContext propertyContext, XFormsModelSubmission.SubmissionParameters p,
                                    XFormsModelSubmission.SecondPassParameters p2, XFormsModelSubmission.SerializationParameters sp) throws Exception {
        if (sp.messageBody == null) {
            // Not sure when this can happen, but it can't be good
            throw new XFormsSubmissionException(submission, "Action 'test:': no message body.", "processing submission response");
        } else {
            // Log message body for debugging purposes
            final IndentedLogger indentedLogger = getDetailsLogger(p, p2);
            if (indentedLogger.isDebugEnabled() && isLogBody()) {
                try {
                    Connection.logRequestBody(indentedLogger, sp.actualRequestMediatype, sp.messageBody);
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e);
                }
            }
        }

        // Do as if we are receiving a regular XML response
        final ConnectionResult connectionResult = new ConnectionResult(null);
        connectionResult.statusCode = 200;
        connectionResult.responseHeaders = ConnectionResult.EMPTY_HEADERS_MAP;
        connectionResult.setLastModified(null);
        connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);// should we use actualRequestMediatype instead?
        connectionResult.dontHandleResponse = false;
        connectionResult.setResponseInputStream(new ByteArrayInputStream(sp.messageBody));

        // Obtain replacer
        final Replacer replacer = submission.getReplacer(propertyContext, connectionResult, p);

        // Deserialize here so it can run in parallel
        replacer.deserialize(propertyContext, connectionResult, p, p2);

        return new SubmissionResult(submission.getEffectiveId(), replacer, connectionResult);
    }
}
