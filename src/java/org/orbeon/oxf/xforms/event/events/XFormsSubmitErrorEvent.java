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

import org.apache.log4j.Level;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.util.ConnectionResult;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.SequenceIterator;
import org.orbeon.saxon.om.SingletonIterator;
import org.orbeon.saxon.value.StringValue;

import java.io.*;
import java.net.URL;


/**
 * 4.4.19 The xforms-submit-error Event
 *
 * Target: model / Bubbles: Yes / Cancelable: No / Context Info: The submit method URI that failed (xsd:anyURI)
 * The default action for this event results in the following: None; notification event only.
 */
public class XFormsSubmitErrorEvent extends XFormsSubmitResponseEvent {

    private Throwable throwable;

    private DocumentInfo bodyDocument;
    private String bodyString;

    private final ErrorType errorType;

    public XFormsSubmitErrorEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject) {
        this(containingDocument, targetObject, ErrorType.XXFORMS_INTERNAL_ERROR, null);
    }

    public XFormsSubmitErrorEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, String resourceURI, ErrorType errorType, int statusCode) {
        super(containingDocument, XFormsEvents.XFORMS_SUBMIT_ERROR, targetObject, resourceURI, statusCode);
        this.errorType = errorType;
    }

    public XFormsSubmitErrorEvent(XFormsContainingDocument containingDocument, XFormsEventTarget targetObject, ErrorType errorType, ConnectionResult connectionResult) {
        super(containingDocument, XFormsEvents.XFORMS_SUBMIT_ERROR, targetObject, connectionResult);
        this.errorType = errorType;

        final IndentedLogger indentedLogger = getIndentedLogger();

        if (connectionResult != null) {

            // Log response details if not done already
            connectionResult.logResponseDetailsIfNeeded(indentedLogger, Level.ERROR, "xforms-submit-error");

            // Try to add body information if present
            if (connectionResult.hasContent()) {

                // "When the error response specifies an XML media type as defined by [RFC 3023], the response body is
                // parsed into an XML document and the root element of the document is returned. If the parse fails, or if
                // the error response specifies a text media type (starting with text/), then the response body is returned
                // as a string. Otherwise, an empty string is returned."

                // Read the whole stream to a temp URI so we can read it more than once if needed
                final String tempURI;
                try {
                    // NOTE: cast to PipelineContext not desirable. Must rework interfaces?
                    tempURI = NetUtils.inputStreamToAnyURI(connectionResult.getResponseInputStream(), NetUtils.REQUEST_SCOPE);
                    connectionResult.getResponseInputStream().close();
                } catch (Exception e) {
                    // Simply can't read the body
                    indentedLogger.logWarning("xforms-submit-error", "error while reading response body ", e);
                    return;
                }

                boolean isXMLParseFailed = false;
                if (XMLUtils.isXMLMediatype(connectionResult.getResponseMediaType())) {
                    // XML content-type
                    // Read stream into Document
                    // TODO: In case of text/xml, charset is not handled. Should modify readTinyTree() and readDom4j()
                    InputStream is = null;
                    try {
                        is = new URL(tempURI).openStream();
                        final DocumentInfo responseBody = TransformerUtils.readTinyTree(containingDocument.getStaticState().getXPathConfiguration(),
                                is, connectionResult.resourceURI, false, true);
                        setBodyDocument(responseBody);
                        return;
                    } catch (Exception e) {
                        indentedLogger.logWarning("xforms-submit-error", "error while parsing response body as XML, defaulting to plain text.", e);
                        isXMLParseFailed = true;
                    } finally {
                        try {
                            is.close();
                        } catch (Exception e) {
                            // NOP
                        }
                    }
                }

                if (isXMLParseFailed || XMLUtils.isTextOrJSONContentType(connectionResult.getResponseMediaType())) {
                    // XML parsing failed, or we got a text content-type
                    // Read stream into String
                    try {
                        final String charset = NetUtils.getTextCharsetFromContentType(connectionResult.getResponseContentType());
                        final InputStream is = new URL(tempURI).openStream();
                        final Reader reader = new InputStreamReader(is, charset);
                        try {
                            final String responseBody = NetUtils.readStreamAsString(reader);
                            setBodyString(responseBody);
                        } finally {
                            try {
                                reader.close();
                            } catch (Exception e) {
                                // NOP
                            }
                        }
                    } catch (Exception e) {
                        indentedLogger.logWarning("xforms-submit-error", "error while reading response body ", e);
                    }
                } else {
                    // This is binary
                    // Don't store anything for now
                }
            }
        }
    }

    public void setThrowable(Throwable throwable) {
        this.throwable = throwable;

        if (errorType != ErrorType.VALIDATION_ERROR) {
            // Don't log validation errors as actual errors
            getIndentedLogger().logError("xforms-submit-error", "setting throwable", "throwable", throwableToString(throwable));
        }
    }

    private static String throwableToString(Throwable throwable) {
        final CharArrayWriter writer = new CharArrayWriter();
        OXFException.getRootThrowable(throwable).printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public DocumentInfo getBodyDocument() {
        return bodyDocument;
    }

    private void setBodyDocument(DocumentInfo bodyDocument) {
        if (XFormsProperties.getErrorLogging().contains("submission-error-body"))
            getIndentedLogger().logError("xforms-submit-error", "setting body document", "body", "\n" + TransformerUtils.tinyTreeToString(bodyDocument));
        this.bodyDocument = bodyDocument;
    }

    public String getBodyString() {
        return bodyString;
    }

    private void setBodyString(String bodyString) {
        if (XFormsProperties.getErrorLogging().contains("submission-error-body"))
            getIndentedLogger().logError("xforms-submit-error", "setting body string", "body", "\n" + bodyString);
        this.bodyString = bodyString;
    }

    public SequenceIterator getAttribute(String name) {

        if ("response-body".equals(name) || "body".equals(name)) {// NOTE: "body" for backward compatibility
            // Return the body of the response if possible

            if ("body".equals(name)) {
                getIndentedLogger().logWarning("xforms-submit-error", "event('body') is deprecated. Use event('response-body') instead.");
            }

            // "When the error response specifies an XML media type as defined by [RFC 3023], the response body is
            // parsed into an XML document and the root element of the document is returned. If the parse fails, or if
            // the error response specifies a text media type (starting with text/), then the response body is returned
            // as a string. Otherwise, an empty string is returned."

            if (getBodyDocument() != null)
                return SingletonIterator.makeIterator(getBodyDocument());
            else if (getBodyString() != null)
                return SingletonIterator.makeIterator(new StringValue(getBodyString()));
            else
                return SingletonIterator.makeIterator(StringValue.EMPTY_STRING);
        } else if ("error-type".equals(name)) {
            // "One of the following: submission-in-progress, no-data, validation-error, parse-error, resource-error,
            // target-error."
            return SingletonIterator.makeIterator(new StringValue(String.valueOf(errorType)));
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
        public static final ErrorType XXFORMS_PENDING_UPLOADS = new ErrorType("xxforms-pending-uploads");
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