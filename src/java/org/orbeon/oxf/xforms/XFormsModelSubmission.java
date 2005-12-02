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
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.mip.BooleanModelItemProperty;
import org.orbeon.oxf.xforms.mip.ValidModelItemProperty;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Represents an XForms model submission instance.
 *
 * TODO: This badly needs to be modularized instead of being a soup of "ifs"!
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
    private String replaceInstanceId;
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
            method = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(submissionElement, "method"));

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

                if (replace.equals("instance")) {
                    replaceInstanceId = submissionElement.attributeValue("instance");
                }
            }
            if (submissionElement.attributeValue("separator") != null) {
                separator = submissionElement.attributeValue("separator");
            }
            includenamespaceprefixes = submissionElement.attributeValue("includenamespaceprefixes");

            submissionElementExtracted = true;
        }
    }

    private boolean isGet() {
        return method.equals("get") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "get"));
    }

    private boolean isPost() {
        return method.equals("post") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "post"));
    }

    private boolean isPut() {
        return method.equals("put") || method.equals(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, "put"));
    }

    private boolean isMethodOptimizedLocalSubmission() {
        return method.startsWith(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, ""))
                && (isGet() || isPost() || isPut());
    }

    public String getId() {
        return id;
    }

    public LocationData getLocationData() {
        return (LocationData) submissionElement.getData();
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

            boolean isDeferredSubmissionSecondPass = false;
            try {
                // Make sure submission element info is extracted
                extractSubmissionElement();

                final boolean isHandlingOptimizedGet = XFormsUtils.isOptimizeGetAllSubmission() && isGet() && replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);

                final boolean isDeferredSubmission = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);
                final boolean isDeferredSubmissionFirstPass = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT.equals(eventName) && !isHandlingOptimizedGet;
                isDeferredSubmissionSecondPass = isDeferredSubmission && !isDeferredSubmissionFirstPass;

                // Select node based on ref or bind
                final XFormsControls xformsControls = containingDocument.getXFormsControls();
                xformsControls.setBinding(pipelineContext, submissionElement); // FIXME: the submission element is not a control...

                final Node currentNode = xformsControls.getCurrentSingleNode();

                if (!(currentNode instanceof Document || currentNode instanceof Element)) {
                    throw new OXFException("xforms:submission: single-node binding must refer to a document node or an element.");
                }

                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();

                final Document initialDocumentToSubmit;
                if (!isDeferredSubmissionSecondPass) {
                    // Create document to submit
                    initialDocumentToSubmit = createDocumentToSubmit(currentNode, currentInstance);

                    // Revalidate instance
                    containingDocument.dispatchEvent(pipelineContext,  new XFormsRevalidateEvent(model, false));
                    // TODO: The "false" attribute is no longer used. The above will cause events to be
                    // sent out. Check if the validation state can really change. If so, find a
                    // solution.
                    // "no notification events are marked for dispatching due to this operation"

                    // Check that there are no validation errors
                    final boolean instanceSatisfiesValidRequired = isDocumentSatisfiesValidRequired(initialDocumentToSubmit);
                    if (!instanceSatisfiesValidRequired) {
                        if (XFormsServer.logger.isDebugEnabled()) {
                            final LocationDocumentResult documentResult = new LocationDocumentResult();
                            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                            identity.setResult(documentResult);
                            currentInstance.read(identity);
                            final String documentString = Dom4jUtils.domToString(documentResult.getDocument());

                            XFormsServer.logger.debug("XForms - instance document or subset thereof cannot be submitted:\n" + documentString);
                        }
                        throw new OXFException("xforms:submission: instance to submit does not satisfy valid and/or required model item properties.");
                    }
                } else {
                    initialDocumentToSubmit = null;
                }

                // Deferred submission: end of the first pass
                if (isDeferredSubmissionFirstPass) {
                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setClientActiveSubmission(this);
                    return;
                }

                final Document documentToSubmit;
                if (isDeferredSubmissionSecondPass) {
                    // Handle uploaded files if any
                    final Element filesElement = (event instanceof XXFormsSubmissionEvent) ? ((XXFormsSubmissionEvent) event).getFilesElement() : null;
                    if (filesElement != null) {
                        for (Iterator i = filesElement.elements().iterator(); i.hasNext();) {
                            final Element parameterElement = (Element) i.next();
                            final String name = parameterElement.element("name").getTextTrim();

                            final Element valueElement = parameterElement.element("value");
                            final String value = valueElement.getTextTrim();
                            final String paramValueType = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(valueElement, XMLConstants.XSI_TYPE_QNAME));

                            final String filename = parameterElement.element("filename").getTextTrim();
                            final String mediatype = parameterElement.element("content-type").getTextTrim();
                            final String size = parameterElement.element("content-length").getTextTrim();

                            final XFormsControls.UploadControlInfo uploadControl
                                    = (XFormsControls.UploadControlInfo) containingDocument.getObjectById(pipelineContext, name);

                            if (uploadControl != null) { // in case of xforms:repeat, the name of the template will not match an existing control
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
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(currentNode, currentInstance);

                    // Revalidate instance
                    containingDocument.dispatchEvent(pipelineContext,  new XFormsRevalidateEvent(model, false));
                    // TODO: The "false" attribute is no longer used. The above will cause events to be
                    // sent out. Check if the validation state can really change. If so, find a
                    // solution.
                    // "no notification events are marked for dispatching due to this operation"

                    // Check that there are no validation errors
                    final boolean instanceSatisfiesValidRequired = isDocumentSatisfiesValidRequired(documentToSubmit);
                    if (!instanceSatisfiesValidRequired) {
//                        currentInstance.readOut();// FIXME: DEBUG
                        throw new OXFException("xforms:submission: instance to submit does not satisfy valid and/or required model item properties.");
                    }
                } else {
                    // Don't recreate document
                    documentToSubmit = initialDocumentToSubmit;
                }

                // Serialize
                // To support: application/xml, application/x-www-form-urlencoded, multipart/related, multipart/form-data
                final byte[] serializedInstance;
                final String serializedInstanceString;
                {
                    if (isPost() || isPut()) {

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
                        serializedInstanceString = null;

                    } else if (isGet()) {

                        // Perform "application/x-www-form-urlencoded" serialization
                        serializedInstanceString = createWwwFormUrlEncoded(documentToSubmit);
                        serializedInstance = null;

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
                boolean dontHandleResponse = false;

                try {
                    if (isHandlingOptimizedGet) {
                        // GET with replace="all"

                        // We can optimize and tell the client to load the URL
                        final String actionString = action + ((action.indexOf('?') == -1) ? "?" : "") + serializedInstanceString;
                        XFormsActionInterpreter.resolveLoadValue(containingDocument, pipelineContext, submissionElement, true, actionString, null);
                        dontHandleResponse = true;
                    } else if ((XFormsUtils.isOptimizeLocalSubmission() || isMethodOptimizedLocalSubmission()) && action.startsWith("/")) {

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
                            if (isPost() || isPut() || isGet()) {

                                // Create requestAdapter depending on method
                                final ForwardExternalContextRequestWrapper requestAdapter;
                                {
                                    if (isPost() || isPut())
                                        requestAdapter= new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                                            action, method.toUpperCase(), (mediatype != null) ? mediatype : "application/xml", serializedInstance);
                                    else {
                                        final String actionString = action + ((action.indexOf('?') == -1) ? "?" : "") + serializedInstanceString;
                                        requestAdapter = new ForwardExternalContextRequestWrapper(externalContext.getRequest(),
                                            actionString, method.toUpperCase());
                                    }
                                }

                                if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL)) {
                                    // Just forward the reply
                                    // TODO: xforms-submit-done must be sent before the body is forwarded
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                                    requestDispatcher.forward(requestAdapter, externalContext.getResponse());
                                    dontHandleResponse = true;
                                } else {
                                    // We must intercept the reply
                                    final ResponseAdapter responseAdapter = new ResponseAdapter();
                                    requestDispatcher.include(requestAdapter, responseAdapter);

                                    // Get response information that needs to be forwarded
                                    resultCode = responseAdapter.getResponseCode();// TODO: this is bad, because the code is actually not propagated from the other side. We should probably not use "optimized" submission here. 
    //                                resultMediaType = NetUtils.getContentTypeMediaType(responseAdapter.getContentType());
    //                                resultHeaders = responseAdapter.getHeaders();
                                    resultMediaType = ProcessorUtils.XML_CONTENT_TYPE;
                                    resultInputStream = responseAdapter.getInputStream();
                                }
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
                            final String actionString = (serializedInstanceString == null) ? action : action + ((action.indexOf('?') == -1) ? "?" : "") + serializedInstanceString;

                            if (action.startsWith("/")) {
                                // Case of "local" portlet submission
                                final String requestURL = externalContext.getRequest().getRequestURL();
                                submissionURL = URLFactory.createURL(requestURL, externalContext.getRequest().getContextPath() + actionString);//TODO: should be relative to actual page's request URL, and use xml:base as well!
                            } else if (NetUtils.urlHasProtocol(actionString)) {
                                // Case of absolute URL
                                submissionURL = URLFactory.createURL(actionString);
                            } else {
                                throw new OXFException("xforms:submission: invalid action: " + actionString);
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

                            if (isPost() || isPut() || isGet()) {
                                urlConnection.setDoInput(true);
                                urlConnection.setDoOutput(!isGet()); // Only if POST / PUT

                                urlConnection.setRequestMethod(method.toUpperCase());
                                urlConnection.setRequestProperty("content-type", (mediatype != null) ? mediatype : "application/xml");

                                urlConnection.connect();

                                // Write request body
                                if (!isGet()) {
                                    os = urlConnection.getOutputStream();
                                    os.write(serializedInstance);
                                }

                                // Get response information that needs to be forwarded
                                resultCode = urlConnection.getResponseCode();
                                final String contentType = urlConnection.getContentType();
                                resultMediaType = NetUtils.getContentTypeMediaType(contentType);
                                resultHeaders = urlConnection.getHeaderFields();
                                resultInputStream = urlConnection.getInputStream();

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

                    if (!dontHandleResponse) {
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
                                            final XFormsInstance replaceInstance = (replaceInstanceId == null) ? currentInstance : model.getInstance(replaceInstanceId);
                                            if (replaceInstance == null) {
                                                containingDocument.dispatchEvent(pipelineContext, new XFormsBindingExceptionEvent(XFormsModelSubmission.this));
                                            } else {
                                                // Get repeat index information just before insertion
                                                final Map previousRepeatIdToIndex; {
                                                    final Map map = xformsControls.getCurrentControlsState().getRepeatIdToIndex();
                                                    previousRepeatIdToIndex = (map == null) ? null : new HashMap(map);
                                                }

                                                // Set new instance
                                                replaceInstance.setInstanceDocument(resultingInstanceDocument);

                                                // Mark all values as changed so that refresh sends appropriate events
                                                XFormsUtils.markAllValuesChanged(replaceInstance.getDocument());

                                                // Rebuild ControlsState
                                                xformsControls.rebuildCurrentControlsState(pipelineContext);

                                                // "Once the XML instance data has been replaced,
                                                // the rebuild, recalculate, revalidate and refresh
                                                // operations are performed on the model, without
                                                // dispatching events to invoke those four
                                                // operations."

                                                model.doRebuild(pipelineContext);
                                                model.doRecalculate(pipelineContext);
                                                model.doRevalidate(pipelineContext);
                                                model.doRefresh(pipelineContext);

                                                // Update repeat indexes if necessary

                                                // The idea is that if a repeat index was set to 0
                                                // (which can only happen when a repeat node-set is
                                                // empty) and instance replacement causes the
                                                // node-set to be non-empty, then the repeat index
                                                // must be set to the initial repeat index for that
                                                // repeat.
                                                if (previousRepeatIdToIndex != null) {
                                                    final XFormsControls.ControlsState currentControlsState = xformsControls.getCurrentControlsState();

                                                    final Map currentRepeatIdToIndex = currentControlsState.getRepeatIdToIndex();
                                                    final Map intialRepeatIdToIndex = currentControlsState.getDefaultRepeatIdToIndex();
                                                    final Map effectiveRepeatIdToIterations = currentControlsState.getEffectiveRepeatIdToIterations();
                                                    if (currentRepeatIdToIndex != null && currentRepeatIdToIndex.size() != 0) {
                                                        for (Iterator i = previousRepeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                                                            final Map.Entry currentEntry = (Map.Entry) i.next();
                                                            final String repeatId = (String) currentEntry.getKey();
                                                            final Integer previouslIndex = (Integer) currentEntry.getValue();

//                                                            final Integer newIndex = (Integer) currentRepeatIdToIndex.get(repeatId);
                                                            final Integer newIterations = (Integer) effectiveRepeatIdToIterations.get(repeatId);

                                                            if (previouslIndex.intValue() == 0 && newIterations.intValue() > 0) {
                                                                // Set index to defaul value
                                                                final Integer initialRepeatIndex = (Integer) intialRepeatIdToIndex.get(repeatId);
//                                                                XFormsActionInterpreter.executeSetindexAction(pipelineContext, containingDocument, repeatId, initialRepeatIndex.toString());
                                                                // TODO: Here we need to check that the index is within bounds and to send the appropriate events
                                                                currentControlsState.updateRepeatIndex(repeatId, initialRepeatIndex.intValue());
                                                            } else {
                                                                // Just reset index and make sure it is within bounds
//                                                                XFormsActionInterpreter.executeSetindexAction(pipelineContext, containingDocument, repeatId, previousRepeatIndex.toString());
                                                                // TODO: Here we need to check that the index is within bounds and to send the appropriate events
//                                                                final Integer previousRepeatIndex = (Integer) previousRepeatIdToIndex.get(repeatId);
//                                                                currentControlsState.updateRepeatIndex(repeatId, previousRepeatIndex.intValue());
                                                                final Integer initialRepeatIndex = (Integer) intialRepeatIdToIndex.get(repeatId);
                                                                currentControlsState.updateRepeatIndex(repeatId, initialRepeatIndex.intValue());
                                                            }
                                                        }
                                                    }
                                                }

                                                // Notify that submission is done
                                                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
                                            }
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
            } catch (Throwable e) {
                if (isDeferredSubmissionSecondPass && XFormsUtils.isOptimizePostAllSubmission()) {
                    // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                    throw new OXFException(e);
                } else {
                    // Any exception will cause an error event to be dispatched
                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitErrorEvent(XFormsModelSubmission.this, action, e));
                }
            }

        } else if (XFormsEvents.XFORMS_BINDING_EXCEPTION.equals(eventName)) {
            // The default action for this event results in the following: Fatal error.
            throw new OXFException("Binding exception.");
        }
    }

    private Document createDocumentToSubmit(final Node currentNode, final XFormsInstance currentInstance) {

        // "A node from the instance data is selected, based on attributes on the submission
        // element. The indicated node and all nodes for which it is an ancestor are considered for
        // the remainder of the submit process. "
        final Document documentToSubmit;
        if (currentNode instanceof Element) {
            // Create subset of document
            documentToSubmit = Dom4jUtils.createDocument((Element) currentNode);
        } else {
            // Use entire instance document
            documentToSubmit = currentInstance.getDocument();
        }

        // "Any node which is considered not relevant as defined in 6.1.4 is removed."
        final Node[] nodeToDetach = new Node[1];
        do {
            // NOTE: This is not very efficient, but at least we avoid NPEs that we would get by
            // detaching elements within accept(). Should implement a more efficient algorithm to
            // prune non-relevant nodes.
            nodeToDetach[0] = null;
            documentToSubmit.accept(new VisitorSupport() {

                public final void visit(Element element) {
                    checkInstanceData(element);
                }

                public final void visit(Attribute attribute) {
                    checkInstanceData(attribute);
                }

                private final void checkInstanceData(Node node) {
                    if (nodeToDetach[0] == null) {
                        final InstanceData instanceData = XFormsUtils.getLocalInstanceData(node);
                        // Check "relevant" MIP and remove non-relevant nodes
                        {
                            final BooleanModelItemProperty relevantMIP = instanceData.getRelevant();
                            if (relevantMIP != null && !relevantMIP.get())
                                nodeToDetach[0] = node;
                        }
                    }
                }
            });
            if (nodeToDetach[0] != null)
                nodeToDetach[0].detach();

        } while(nodeToDetach[0] != null);

        // TODO: handle includenamespaceprefixes
        return documentToSubmit;
    }

    private String createWwwFormUrlEncoded(final Document document) {

        final StringBuffer sb = new StringBuffer();
        document.accept(new VisitorSupport() {
            public final void visit(Element element) {
                // We only care about elements

                final List children = element.elements();
                if (children == null || children.size() == 0) {
                    // Only consider leaves
                    final String text = element.getText();
                    if (text != null && text.length() > 0) {
                        // Got one!
                        final String localName = element.getName();

                        if (sb.length() > 0)
                            sb.append(separator);

                        try {
                            sb.append(URLEncoder.encode(localName, "utf-8"));
                            sb.append('=');
                            sb.append(URLEncoder.encode(text, "utf-8"));
                            // TODO: check if line breaks will be correcly encoded as "%0D%0A"
                        } catch (UnsupportedEncodingException e) {
                            // Should not happen: utf-8 must be supported
                            throw new OXFException(e);
                        }
                    }
                }
            }
        });

        return sb.toString();
    }

    private boolean isDocumentSatisfiesValidRequired(final Document documentToSubmit) {
        final boolean[] instanceSatisfiesValidRequired = new boolean[] { true } ;
        documentToSubmit.accept(new VisitorSupport() {

            public final void visit(Element element) {
                final InstanceData instanceData = XFormsUtils.getLocalInstanceData(element);
                checkInstanceData(instanceData);
            }

            public final void visit(Attribute attribute) {
                final InstanceData instanceData = XFormsUtils.getLocalInstanceData(attribute);
                checkInstanceData(instanceData);
            }

            private final void checkInstanceData(InstanceData instanceData) {
                // Check "valid" MIP
                {
                    final BooleanModelItemProperty validMIP = instanceData.getValid();
                    if (validMIP != null && !validMIP.get())
                        instanceSatisfiesValidRequired[0] = false;
                }
                // Check "required" MIP
                {
                    final ValidModelItemProperty requiredMIP = instanceData.getRequired();
                    if (requiredMIP != null && requiredMIP.get() && requiredMIP.getStringValue().length() == 0) {
                        // Required and empty
                        instanceSatisfiesValidRequired[0] = false;
                    }
                }
            }
        });
        return instanceSatisfiesValidRequired[0];
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
