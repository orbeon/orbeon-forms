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
package org.orbeon.oxf.xforms;

import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.event.EventTarget;
import org.orbeon.oxf.xforms.event.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;

import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Represents an XForms model submission instance.
 */
public class XFormsModelSubmission implements EventTarget {

    private XFormsModel model;
    private Element submissionElement;
    private boolean submissionElementExtracted = false;

    private String id;

    private String action; // required
    private String method; // required

    private String version;
    private boolean indent;
    private String mediatype;
    private String encoding;
    private boolean omitxmldeclaration;
    private Boolean standalone;
    private String cdatasectionelements;

    private String replace = "all";
    private String separator = ";";
    private String includenamespaceprefixes;

    public XFormsModelSubmission(Element submissionElement, XFormsModel model) {
        this.submissionElement = submissionElement;
        this.model = model;
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }

    private void extractSubmissionElement() {
        if (!submissionElementExtracted) {
            id = submissionElement.attributeValue("id");

            action = submissionElement.attributeValue("action");
            method = submissionElement.attributeValue("method");
            version = submissionElement.attributeValue("version");

            if (submissionElement.attributeValue("indent") != null) {
                indent = Boolean.getBoolean(submissionElement.attributeValue("indent"));
            }
            mediatype = submissionElement.attributeValue("mediatype");
            encoding = submissionElement.attributeValue("encoding");
            if (submissionElement.attributeValue("omitxmldeclaration") != null) {
                omitxmldeclaration = Boolean.getBoolean(submissionElement.attributeValue("omitxmldeclaration"));
            }
            if (submissionElement.attributeValue("standalone") != null) {
                standalone = new Boolean(submissionElement.attributeValue("standalone"));
            }

            cdatasectionelements = submissionElement.attributeValue("cdatasectionelements");
            if (submissionElement.attributeValue("replace") != null) {
                replace = submissionElement.attributeValue("replace");
            }
            if (submissionElement.attributeValue("separator") != null) {
                separator = submissionElement.attributeValue("separator");
            }
            includenamespaceprefixes = submissionElement.attributeValue("includenamespaceprefixes");

            submissionElementExtracted = true;
        }
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        dispatchEvent(pipelineContext, xformsEvent, xformsEvent.getEventName());
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsGenericEvent event, String eventName) {
        if (XFormsEvents.XFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            // Make sure submission element info is extracted
            extractSubmissionElement();

            // Select node based on ref or bind
            final XFormsControls xformsControls = model.getControls();
            xformsControls.setBinding(pipelineContext, submissionElement);

            final Node currentNode = xformsControls.getCurrentSingleNode();

            if (!(currentNode instanceof Document || currentNode instanceof Element)) {
                // TODO: dispatch xforms-submit-error
                throw new OXFException("xforms:submission: single-node binding must refer to a document node or an element.");
            }

            final XFormsInstance currentInstance = xformsControls.getCurrentInstance();
            // TODO: fix getCurrentInstance() in controls

            // Revalidate instance
            // TODO: we should only revalidate relevant parts
            // TODO: this should not send valid / invalid events (probably)
            model.dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XFORMS_REVALIDATE));

            final Document documentToSubmit;
            {
                if (currentNode instanceof Element) {
                    // Create subset of document
                    documentToSubmit = Dom4jUtils.createDocument((Element) currentNode);
                } else {
                    // Use entire instance document
                    documentToSubmit = currentInstance.getDocument();
                }
                // TODO: handle includenamespaceprefixes + handle non-relevant nodes
            }

            // Check that there are no validation errors
            final boolean[] instanceValid = new boolean[] { true } ;
            documentToSubmit.accept(new VisitorSupport() {

                public void visit(Element element) {
                    final InstanceData instanceData = XFormsUtils.getLocalInstanceData(element);
                    checkInstanceData(instanceData);
                }

                public void visit(Attribute attribute) {
                    final InstanceData instanceData = XFormsUtils.getLocalInstanceData(attribute);
                    checkInstanceData(instanceData);
                }

                private void checkInstanceData(InstanceData instanceData) {
                    final BooleanModelItemProperty validMIP = instanceData.getValid();
                    if (validMIP != null)
                        instanceValid[0] &= validMIP.get();
                }
            });

            if (!instanceValid[0]) {
                // TODO: dispatch xforms-submit-error and stop processing
                throw new OXFException("xforms:submission: instance is not valid.");
            }

            // Serialize
            // To support: application/xml, application/x-www-form-urlencoded, multipart/related, multipart/form-data
            final byte[] serializedInstance;
            {
                if (method.equals("post") || method.equals("put")) {
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                "xml", version, null, null, encoding, omitxmldeclaration, standalone, indent, 4);

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        serializedInstance = os.toByteArray();
                    } catch (Exception e) {
                        // TODO: dispatch xforms-submit-error and stop processing
                        throw new OXFException("xforms:submission: exception while serializing instance to XML.", e);
                    }

                } else if (method.equals("get")) {
                    // TODO
                    throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                } else if (method.equals("multipart-post")) {
                    // TODO
                    throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                } else if (method.equals("form-data-post")) {
                    // TODO
                    throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                } else if (method.equals("urlencoded-post")) {
                    throw new OXFException("xforms:submission: deprecated submission method requested: " + method);
                } else {
                    throw new OXFException("xforms:submission: invalid submission method requested: " + method);
                }
            }

            // Submit to URL
            final URL submissionURL;
            try {
                submissionURL = URLFactory.createURL(action);
            } catch (MalformedURLException e) {
                // TODO: dispatch xforms-submit-error and stop processing
                throw new OXFException("xforms:submission: invalid action: " + action);
            }

            byte[] submissionResponse = null;
            int responseCode = 0;
            String responseMediaType = null;
            final String scheme = submissionURL.getProtocol();

            if (scheme.equals("http") || scheme.equals("https")) {
                // http MUST be supported
                // https SHOULD be supported

                HttpURLConnection urlConnection = null;
                OutputStream os = null;
                InputStream is = null;
                try {
                    urlConnection = (HttpURLConnection) submissionURL.openConnection();

                    if (method.equals("post") || method.equals("put")) {
                        urlConnection.setDoInput(true);
                        urlConnection.setDoOutput(true); // If POST / PUT

                        urlConnection.setRequestMethod(method.toUpperCase());
                        urlConnection.setRequestProperty("content-type", (mediatype != null) ? mediatype : "application/xml");

                        urlConnection.connect();

                        // Submit
                        os = urlConnection.getOutputStream();
                        os.write(serializedInstance);

                        // Get response
                        responseCode = urlConnection.getResponseCode();
                        final String contentType = urlConnection.getContentType();
                        responseMediaType = NetUtils.getContentTypeMediaType(contentType);

                        final ByteArrayOutputStream resultByteArrayOutputStream = new ByteArrayOutputStream();
                        is = urlConnection.getInputStream();
                        NetUtils.copyStream(is, resultByteArrayOutputStream);
                        submissionResponse = resultByteArrayOutputStream.toByteArray();

                    } else if (method.equals("get")) {
                        // TODO
                        throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                    } else if (method.equals("multipart-post")) {
                        // TODO
                        throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                    } else if (method.equals("form-data-post")) {
                        // TODO
                        throw new OXFException("xforms:submission: submission method not yet implemented: " + method);
                    } else if (method.equals("urlencoded-post")) {
                        throw new OXFException("xforms:submission: deprecated submission method requested: " + method);
                    } else {
                        throw new OXFException("xforms:submission: invalid submission method requested: " + method);
                    }
                } catch (IOException e) {
                    // TODO: dispatch xforms-submit-error and stop processing
                    throw new OXFException(e);
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            // TODO: dispatch xforms-submit-error and stop processing
                            throw new OXFException("Exception while closing output stream for action: " + action);
                        }
                    }
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                            // TODO: dispatch xforms-submit-error and stop processing
                            throw new OXFException("Exception while closing input stream for action: " + action);
                        }
                    }
                    if (urlConnection != null)
                        urlConnection.disconnect();
                }

                // Handle response
                if (responseCode == 200) {
                    // Sucessful response
                    if (submissionResponse != null && submissionResponse.length > 0) {
                        // There is a body

                        if (replace.equals("all")) {
                            // TODO: dispatch xforms-submit-done
                            // TODO: this will have to involve some more complex behavior, probably.
                        } else if (replace.equals("instance")) {
                            if (ProcessorUtils.isXMLContentType(responseMediaType)) {
                                // Handling of XML media type
                                try {
                                    final Transformer identity = TransformerUtils.getIdentityTransformer();
                                    final LocationDocumentResult documentResult = new LocationDocumentResult();
                                    identity.transform(new StreamSource(new ByteArrayInputStream(submissionResponse)), documentResult);
                                    final Document resultingInstanceDocument = documentResult.getDocument();

                                    // Set new instance document to replace the one submitted
                                    currentInstance.setInstanceDocument(resultingInstanceDocument);

                                    // Dispatch events
                                    model.dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XFORMS_MODEL_CONSTRUCT));
                                    dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XFORMS_SUBMIT_DONE));
                                } catch (Exception e) {
                                    // TODO: dispatch xforms-submit-error and stop processing
                                    throw new OXFException("xforms:submission: exception while serializing XML to instance.", e);
                                }
                            } else {
                                // Other media type
                                dispatchEvent(pipelineContext, new XFormsSubmitErrorEvent(action, null));
                            }
                        } else if (replace.equals("none")) {
                            dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XFORMS_SUBMIT_DONE));
                        } else {
                            // TODO: dispatch xforms-submit-error and stop processing
                            throw new OXFException("xforms:submission: invalid replace attribute: " + replace);
                        }

                    } else {
                        // There is no body
                        dispatchEvent(pipelineContext, new XFormsEvent(XFormsEvents.XFORMS_SUBMIT_DONE));
                    }
                } else {
                    // Error code received
                    dispatchEvent(pipelineContext, new XFormsSubmitErrorEvent(action, null));
                }
            } else if (scheme.equals("file")) {
                // TODO
                // SHOULD be supported
                // Question: should support oxf: as well?
                throw new OXFException("xforms:submission: submission URL scheme not yet implemented: " + scheme);
            } else if (scheme.equals("mailto")) {
                // TODO
                // MAY be supported
                throw new OXFException("xforms:submission: submission URL scheme not yet implemented: " + scheme);
            } else {
                throw new OXFException("xforms:submission: submission URL scheme not supported: " + scheme);
            }

        } else if (XFormsEvents.XFORMS_SUBMIT_DONE.equals(eventName)) {
            // 4.4.18 The xforms-submit-done Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            // The default action for this event results in the following: None; notification event only.

        } else if (XFormsEvents.XFORMS_SUBMIT_ERROR.equals(eventName)) {
            // 4.4.19 The xforms-submit-error Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: The submit method URI that failed (xsd:anyURI)
            // The default action for this event results in the following: None; notification event only.

        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }
}
