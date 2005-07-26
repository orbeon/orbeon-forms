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
import org.orbeon.oxf.externalcontext.ForwardExternalContextRequestWrapper;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.resources.URLFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XFormsRevalidateEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitDoneEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsSubmissionEvent;
import org.orbeon.oxf.xforms.mip.BooleanModelItemProperty;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.oxf.xml.dom4j.LocationSAXContentHandler;

import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an XForms model submission instance.
 */
public class XFormsModelSubmission implements XFormsEventTarget, XFormsEventHandlerContainer {

    private XFormsContainingDocument containingDocument;
    private String id;
    private XFormsModel model;
    private Element submissionElement;
    private boolean submissionElementExtracted = false;

    // Event handlers
    private List eventHandlers;

    private String action; // required
    private String method; // required

    private String version;
    private boolean indent;
    private String mediatype;
    private String encoding;
    private boolean omitxmldeclaration;
    private Boolean standalone;
    private String cdatasectionelements;

    private String replace = XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL;
    private String separator = ";";
    private String includenamespaceprefixes;

    public XFormsModelSubmission(XFormsContainingDocument containingDocument, String id, Element submissionElement, XFormsModel model) {
        this.containingDocument = containingDocument;
        this.id = id;
        this.submissionElement = submissionElement;
        this.model = model;

        // Extract event handlers
        eventHandlers = XFormsEventHandlerImpl.extractEventHandlers(containingDocument, this, submissionElement);
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }

    private void extractSubmissionElement() {
        if (!submissionElementExtracted) {

            action = submissionElement.attributeValue("action");
            method = submissionElement.attributeValue("method");
            version = submissionElement.attributeValue("version");

            if (submissionElement.attributeValue("indent") != null) {
                indent = Boolean.valueOf(submissionElement.attributeValue("indent")).booleanValue();
            }
            mediatype = submissionElement.attributeValue("mediatype");
            encoding = submissionElement.attributeValue("encoding");
            if (submissionElement.attributeValue("omitxmldeclaration") != null) {
                omitxmldeclaration = Boolean.valueOf(submissionElement.attributeValue("omit-xml-declaration")).booleanValue();
            }
            if (submissionElement.attributeValue("standalone") != null) {
                standalone = new Boolean(submissionElement.attributeValue("standalone"));
            }

            cdatasectionelements = submissionElement.attributeValue("cdata-section-elements");
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

    public String getId() {
        return id;
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return model;
    }

    public List getEventHandlers() {
        return eventHandlers;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        final String eventName = event.getEventName();

        if (XFormsEvents.XFORMS_SUBMIT.equals(eventName) || XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            final boolean isDeferredSubmission = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);
            final boolean isDeferredSubmissionFirstPass = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT.equals(eventName);
            final boolean isDeferredSubmissionSecondPass = isDeferredSubmission && !XFormsEvents.XFORMS_SUBMIT.equals(eventName);

            try {
                // Make sure submission element info is extracted
                extractSubmissionElement();

                // Select node based on ref or bind
                final XFormsControls xformsControls = containingDocument.getXFormsControls();
                xformsControls.setBinding(pipelineContext, submissionElement); // FIXME: the submission element is not a control...

                final Node currentNode = xformsControls.getCurrentSingleNode();

                if (!(currentNode instanceof Document || currentNode instanceof Element)) {
                    throw new OXFException("xforms:submission: single-node binding must refer to a document node or an element.");
                }

                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();

                // Revalidate instance
                // TODO: we should only revalidate relevant parts
                containingDocument.dispatchEvent(pipelineContext,  new XFormsRevalidateEvent(model, false));

                final Document initialDocumentToSubmit;
                if (!isDeferredSubmissionSecondPass) {
                    // Create document to submit
                    initialDocumentToSubmit = createDocumentToSubmit(currentNode, currentInstance);

                    // Check that there are no validation errors
                    final boolean instanceValid = isDocumentValid(initialDocumentToSubmit);
                    if (!instanceValid) {
                        LocationSAXContentHandler ch = new LocationSAXContentHandler();
                        currentInstance.read(ch);
                        System.out.println(Dom4jUtils.domToString(ch.getDocument()));
                        throw new OXFException("xforms:submission: instance is not valid.");
                    }
                } else {
                    initialDocumentToSubmit = null;
                }

                // Deferred submission
                if (isDeferredSubmissionFirstPass) {
                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setActiveSubmission(this);
                    return;
                }

                // Handle uploaded files if any
                final Element filesElement = ((XXFormsSubmissionEvent) event).getFilesElement();
                if (filesElement != null) {
                    for (Iterator i = filesElement.elements().iterator(); i.hasNext();) {
                        final Element parameterElement = (Element) i.next();
                        final String name = parameterElement.element("name").getTextTrim();

                        final Element valueElement = parameterElement.element("value");
                        final String value = valueElement.getTextTrim();
                        final String paramValueType = valueElement.attributeValue(XMLConstants.XSI_TYPE_QNAME);//TODO: get actual QName for attribute value

                        final String filename = parameterElement.element("filename").getTextTrim();
                        final String mediatype = parameterElement.element("content-type").getTextTrim();
                        final String size = parameterElement.element("content-length").getTextTrim();

                        final XFormsControls.UploadControlInfo uploadControl
                                = (XFormsControls.UploadControlInfo) containingDocument.getObjectById(pipelineContext, name);

                        // Set value into the instance
                        xformsControls.setBinding(pipelineContext, uploadControl);
                        {
                            final Node currentSingleNode = xformsControls.getCurrentSingleNode();
                            XFormsInstance.setValueForNode(pipelineContext, currentSingleNode, value, paramValueType);
                        }

                        // Handle filename if any
                        if (uploadControl.getFilenameElement() != null) {
                            xformsControls.pushBinding(pipelineContext, uploadControl.getFilenameElement());
                            final Node currentSingleNode = xformsControls.getCurrentSingleNode();
                            XFormsInstance.setValueForNode(pipelineContext, currentSingleNode, filename, null);
                            xformsControls.popBinding();
                        }

                        // Handle mediatype if any
                        if (uploadControl.getMediatypeElement() != null) {
                            xformsControls.pushBinding(pipelineContext, uploadControl.getMediatypeElement());
                            final Node currentSingleNode = xformsControls.getCurrentSingleNode();
                            XFormsInstance.setValueForNode(pipelineContext, currentSingleNode, mediatype, null);
                            xformsControls.popBinding();
                        }
                        
                        // Handle file size if any
                        if (uploadControl.getSizeElement() != null) {
                            xformsControls.pushBinding(pipelineContext, uploadControl.getSizeElement());
                            final Node currentSingleNode = xformsControls.getCurrentSingleNode();
                            XFormsInstance.setValueForNode(pipelineContext, currentSingleNode, size, null);
                            xformsControls.popBinding();
                        }
                    }
                }

                final Document documentToSubmit;
                if (isDeferredSubmissionSecondPass) {
                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(currentNode, currentInstance);

                    // Check that there are no validation errors
                    final boolean instanceValid = isDocumentValid(documentToSubmit);
                    if (!instanceValid) {
                        LocationSAXContentHandler ch = new LocationSAXContentHandler();
                        currentInstance.read(ch);
                        System.out.println(Dom4jUtils.domToString(ch.getDocument()));
                        throw new OXFException("xforms:submission: instance is not valid.");
                    }
                } else {
                    documentToSubmit = initialDocumentToSubmit;
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

                            // TODO: use cdata-section-elements

                            final ByteArrayOutputStream os = new ByteArrayOutputStream();
                            identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                            serializedInstance = os.toByteArray();
                        } catch (Exception e) {
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

                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);

                // Result information
                int resultCode = 0;
                String resultMediaType = null;
                Map resultHeaders = null;
                InputStream resultInputStream = null;

                HttpURLConnection urlConnection = null;
                OutputStream os = null;
                boolean forwarded = false;

                try {
                    if (action.startsWith("/")) {

                        // TODO
                        //  && !externalContext.getRequest().getContainerType().equals("portlet")

                        // This is a "local" submission, i.e. in the same web application
                        // The submission can be optimized.

                        // NOTE: It is not possible to use include() with portlets, because it is
                        // not possible to intercept the response, unlike with servlets. Also, there
                        // is no concept of forward() with portlets. In the case of portlets, we can
                        // therefore not optimize.

                        // FIXME: How to deal with absolute paths on the same server, but not in
                        // the web application? Just use absolute URLs?

                        ExternalContext.RequestDispatcher requestDispatcher = externalContext.getRequestDispatcher(action);
                        try {
                            if (method.equals("post") || method.equals("put")) {

                                final ForwardExternalContextRequestWrapper requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                                        action, method.toUpperCase(), (mediatype != null) ? mediatype : "application/xml", serializedInstance);

                                if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL)) {
                                    // Just forward the reply
                                    // TODO: xforms-submit-done must be sent before the body is forwarded
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                                    requestDispatcher.forward(requestAdapter, externalContext.getResponse());
                                    forwarded = true;
                                } else {
                                    // We must intercept the reply
                                    final ResponseAdapter responseAdapter = new ResponseAdapter();
                                    requestDispatcher.include(requestAdapter, responseAdapter);

                                    // Get response information that needs to be forwarded
                                    resultCode = responseAdapter.getResponseCode();
    //                                resultMediaType = NetUtils.getContentTypeMediaType(responseAdapter.getContentType());
    //                                resultHeaders = responseAdapter.getHeaders();
                                    resultMediaType = ProcessorUtils.XML_CONTENT_TYPE;
                                    resultInputStream = responseAdapter.getInputStream();
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
                        } catch (IOException e) {
                            throw new OXFException(e);
                        }

                    } else {
                        // This is a "remote" submission, i.e. using an absolute URL, OR a "local" submission from a portlet

                        // Compute submission URL
                        final URL submissionURL;
                        try {
                            if (action.startsWith("/")) {
                                // Case of "local" portlet submission
                                final String requestURL = externalContext.getRequest().getRequestURL();
                                submissionURL = URLFactory.createURL(requestURL, externalContext.getRequest().getContextPath() + action);
                            } else if (NetUtils.urlHasProtocol(action)) {
                                // Case of absolute URL
                                submissionURL = URLFactory.createURL(action);
                            } else {
                                throw new OXFException("xforms:submission: invalid action: " + action);
//                                final String requestURL = externalContext.getRequest().getRequestURL();
//
//                                if (requestURL != null) {
//                                    submissionURL = URLFactory.createURL(requestURL, action);
//                                } else {
//                                    throw new OXFException("xforms:submission: cannot resolve relative action: " + action);
//                                }
                            }
                        } catch (MalformedURLException e) {
                            throw new OXFException("xforms:submission: invalid action: " + action, e);
                        }

                        // Perform submission
                        final String scheme = submissionURL.getProtocol();
                        if (scheme.equals("http") || scheme.equals("https")) {
                            // http MUST be supported
                            // https SHOULD be supported

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

                                // Get response information that needs to be forwarded
                                resultCode = urlConnection.getResponseCode();
                                final String contentType = urlConnection.getContentType();
                                resultMediaType = NetUtils.getContentTypeMediaType(contentType);
                                resultHeaders = urlConnection.getHeaderFields();
                                resultInputStream = urlConnection.getInputStream();

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

                    }

                    if (!forwarded) {
                        // Handle response
                        if (resultCode == 200) {
                            // Sucessful response

                            final boolean hasContent;
                            {
                                if (resultInputStream == null) {
                                    hasContent = false;
                                } else {
                                    if (!resultInputStream.markSupported())
                                        resultInputStream = new BufferedInputStream(resultInputStream);

                                    resultInputStream.mark(1);
                                    hasContent = resultInputStream.read() != -1;
                                    resultInputStream.reset();
                                }
                            }

                            if (hasContent) {
                                // There is a body

                                if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL)) {
                                    // When we get here, we are in a mode where we need to send the reply
                                    // directly to an external context, if any.

                                    // "the event xforms-submit-done is dispatched"
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));

                                    final ExternalContext.Response response = externalContext.getResponse();

                                    // Forward headers to response
                                    if (resultHeaders != null) {
                                        for (Iterator i = resultHeaders.entrySet().iterator(); i.hasNext();) {
                                            final Map.Entry currentEntry = (Map.Entry) i.next();
                                            final String headerName = (String) currentEntry.getKey();
                                            final List headerValues = (List) currentEntry.getValue();

                                            if (headerName != null && headerValues != null) {
                                                for (Iterator j = headerValues.iterator(); j.hasNext();) {
                                                    response.addHeader(headerName, (String) j.next());
                                                }
                                            }
                                        }
                                    }

                                    // Forward content to response
                                    NetUtils.copyStream(resultInputStream, response.getOutputStream());

                                } else if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_INSTANCE)) {

                                    final ByteArrayOutputStream resultByteArrayOutputStream = new ByteArrayOutputStream();
                                    NetUtils.copyStream(resultInputStream, resultByteArrayOutputStream);
                                    byte[] submissionResponse = resultByteArrayOutputStream.toByteArray();

                                    if (ProcessorUtils.isXMLContentType(resultMediaType)) {
                                        // Handling of XML media type
                                        try {
                                            final Transformer identity = TransformerUtils.getIdentityTransformer();
                                            final LocationDocumentResult documentResult = new LocationDocumentResult();
                                            identity.transform(new StreamSource(new ByteArrayInputStream(submissionResponse)), documentResult);
                                            final Document resultingInstanceDocument = documentResult.getDocument();

                                            // Set new instance document to replace the one submitted
                                            currentInstance.setInstanceDocument(resultingInstanceDocument);

                                            // Dispatch events
                                            containingDocument.dispatchEvent(pipelineContext, new org.orbeon.oxf.xforms.event.events.XFormsModelConstructEvent(model));
                                            containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                                        } catch (Exception e) {
                                            throw new OXFException("xforms:submission: exception while serializing XML to instance.", e);
                                        }
                                    } else {
                                        // Other media type
                                        throw new OXFException("Body received with non-XML media type for replace=\"instance\": " + resultMediaType);
                                    }
                                } else if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_NONE)) {
                                    // Just notify that processing is terminated
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                                } else {
                                    throw new OXFException("xforms:submission: invalid replace attribute: " + replace);
                                }

                            } else {
                                // There is no body, notify that processing is terminated
                                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                            }
                        } else if (resultCode == 302 || resultCode == 301) {
                            // Got a redirect

                            final ExternalContext.Response response = externalContext.getResponse();

                            // Forward headers to response
                            // TODO: this is duplicated from above
                            if (resultHeaders != null) {
                                for (Iterator i = resultHeaders.entrySet().iterator(); i.hasNext();) {
                                    final Map.Entry currentEntry = (Map.Entry) i.next();
                                    final String headerName = (String) currentEntry.getKey();
                                    final List headerValues = (List) currentEntry.getValue();

                                    if (headerName != null && headerValues != null) {
                                        for (Iterator j = headerValues.iterator(); j.hasNext();) {
                                            response.addHeader(headerName, (String) j.next());
                                        }
                                    }
                                }
                            }

                            // Forward redirect
                            response.setStatus(resultCode);

                        } else {
                            // Error code received
                            throw new OXFException("Error code received when submitting instance: " + resultCode);
                        }
                    }
                } finally {
                    // Clean-up
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException e) {
                            throw new OXFException("Exception while closing output stream for action: " + action);
                        }
                    }
                    if (resultInputStream != null) {
                        try {
                            resultInputStream.close();
                        } catch (IOException e) {
                            throw new OXFException("Exception while closing input stream for action: " + action);
                        }
                    }
                    if (urlConnection != null)
                        urlConnection.disconnect();
                }
            } catch (Exception e) {
                // Any exception will cause an error event to be dispatched
                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitErrorEvent(XFormsModelSubmission.this, action, e));
            }

        }
    }

    private Document createDocumentToSubmit(final Node currentNode, final XFormsInstance currentInstance) {
        Document documentToSubmit;
        if (currentNode instanceof Element) {
            // Create subset of document
            documentToSubmit = Dom4jUtils.createDocument((Element) currentNode);
        } else {
            // Use entire instance document
            documentToSubmit = currentInstance.getDocument();
        }
        // TODO: handle includenamespaceprefixes + handle non-relevant nodes
        return documentToSubmit;
    }

    private boolean isDocumentValid(final Document documentToSubmit) {
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
        return instanceValid[0];
    }
}

class ResponseAdapter implements ExternalContext.Response {

    private int status = 200;
    private String contentType;

    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private LocalByteArrayOutputStream byteStream;

    private InputStream inputStream;

    public int getResponseCode() {
        return status;
    }

    public String getContentType() {
        return contentType;
    }

    public Map getHeaders() {
        return null;
    }

    public InputStream getInputStream() {
        if (inputStream == null) {
            if (stringWriter != null) {

            } else if (byteStream != null) {
                inputStream = new ByteArrayInputStream(byteStream.getByteArray(), 0, byteStream.size());
            }
        }

        return inputStream;
    }

    public void addHeader(String name, String value) {
    }

    public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
        return false;
    }

    public String getCharacterEncoding() {
        return null;
    }

    public String getNamespacePrefix() {
        return null;
    }

    public OutputStream getOutputStream() throws IOException {
        if (byteStream == null)
            byteStream = new LocalByteArrayOutputStream();
        return byteStream;
    }

    public PrintWriter getWriter() throws IOException {
        if (stringWriter == null) {
            stringWriter = new StringWriter();
            printWriter = new PrintWriter(stringWriter);
        }
        return printWriter;
    }

    public boolean isCommitted() {
        return false;
    }

    public void reset() {
    }

    public String rewriteActionURL(String urlString) {
        return null;
    }

    public String rewriteRenderURL(String urlString) {
        return null;
    }

    public String rewriteResourceURL(String urlString, boolean absolute) {
        return null;
    }

    public void sendError(int sc) throws IOException {
        this.status = sc;
    }

    public void sendRedirect(String pathInfo, Map parameters, boolean isServerSide, boolean isExitPortal) throws IOException {
    }

    public void setCaching(long lastModified, boolean revalidate, boolean allowOverride) {
    }

    public void setContentLength(int len) {
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setHeader(String name, String value) {
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setTitle(String title) {
    }

    private static class LocalByteArrayOutputStream extends ByteArrayOutputStream {
        public byte[] getByteArray() {
            return buf;
        }
    }

    public Object getNativeResponse() {
        return null;
    }
}
