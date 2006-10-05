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

import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.ProcessorUtils;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.action.actions.XFormsLoadAction;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.mip.BooleanModelItemProperty;
import org.orbeon.oxf.xforms.mip.ValidModelItemProperty;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.LocationDocumentResult;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.Transformer;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URI;
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

	public final static Logger logger = LoggerFactory.createLogger(XFormsModelSubmission.class);

    public static final String DEFAULT_TEXT_READING_ENCODING = "iso-8859-1";

    private final XFormsContainingDocument containingDocument;
    private final String id;
    private final XFormsModel model;
    private final Element submissionElement;
    private boolean submissionElementExtracted = false;

    // Event handlers
    private final List eventHandlers;

    private String avtAction; // required
    private String resolvedAction;
    private String method; // required

    private boolean validate = true;
    private boolean relevant = true;

    private String version;
    private boolean indent;
    private String mediatype;
    private String encoding;
    private boolean omitxmldeclaration;
    private Boolean standalone;
    private String cdatasectionelements;

    private String replace = XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL;
    private String replaceInstanceId;
    private String xxfReplaceInstanceId;
    private String separator = ";";
    private String includenamespaceprefixes;

    private String avtXXFormsUsername;
    private String resolvedXXFormsUsername;
    private String avtXXFormsPassword;
    private String resolvedXXFormsPassword;

    private boolean xxfShowProgress;

    public XFormsModelSubmission(XFormsContainingDocument containingDocument, String id, Element submissionElement, XFormsModel model) {
        this.containingDocument = containingDocument;
        this.id = id;
        this.submissionElement = submissionElement;
        this.model = model;

        // Extract event handlers
        eventHandlers = XFormsEventHandlerImpl.extractEventHandlers(containingDocument, this, submissionElement);
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }


    public boolean isXxfShowProgress() {
        return xxfShowProgress;
    }

    private void extractSubmissionElement() {
        if (!submissionElementExtracted) {

            avtAction = submissionElement.attributeValue("action");
            method = submissionElement.attributeValue("method");
            method = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(submissionElement, "method"));

            validate = !"false".equals(submissionElement.attributeValue("validate"));
            relevant = !"false".equals(submissionElement.attributeValue("relevant"));

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
                    replaceInstanceId = XFormsUtils.namespaceId(containingDocument, submissionElement.attributeValue("instance"));
                    xxfReplaceInstanceId = XFormsUtils.namespaceId(containingDocument, submissionElement.attributeValue(XFormsConstants.XXFORMS_INSTANCE_QNAME));
                }
            }
            if (submissionElement.attributeValue("separator") != null) {
                separator = submissionElement.attributeValue("separator");
            }
            includenamespaceprefixes = submissionElement.attributeValue("includenamespaceprefixes");

            // Extension: username and password
            avtXXFormsUsername = submissionElement.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
            avtXXFormsPassword = submissionElement.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);

            // Whether we must show progress or not
            xxfShowProgress = !"false".equals(submissionElement.attributeValue(XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME));

            // Remember that we did this
            submissionElementExtracted = true;
        }
    }

    private boolean isMethodOptimizedLocalSubmission() {
        return method.startsWith(XMLUtils.buildExplodedQName(XFormsConstants.XXFORMS_NAMESPACE_URI, ""))
                && (XFormsSubmissionUtils.isGet(method) || XFormsSubmissionUtils.isPost(method) || XFormsSubmissionUtils.isPut(method));
    }

    public String getEffectiveId() {
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

            containingDocument.setGotSubmission(true);

            boolean isDeferredSubmissionSecondPass = false;
            XFormsSubmitErrorEvent submitErrorEvent = null;
            boolean submitDone = false;
            try {
                // Make sure submission element info is extracted
                extractSubmissionElement();

                final boolean isReplaceAll = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);
                final boolean isReplaceInstance = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_INSTANCE);

                final boolean isHandlingOptimizedGet = XFormsUtils.isOptimizeGetAllSubmission() && XFormsSubmissionUtils.isGet(method) && isReplaceAll;

                //noinspection UnnecessaryLocalVariable
                final boolean isDeferredSubmission = isReplaceAll && !isHandlingOptimizedGet;
                final boolean isDeferredSubmissionFirstPass = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT.equals(eventName);
                isDeferredSubmissionSecondPass = isDeferredSubmission && !isDeferredSubmissionFirstPass; // here we get XXFORMS_SUBMIT

                final XFormsControls xformsControls = containingDocument.getXFormsControls();

                // Get node to submit
                final Node currentNode;
                {
                    // TODO FIXME: the submission element is not a control, so we shouldn't use XFormsControls.
                    // "The default value is '/'."
                    final String refAttribute = (submissionElement.attributeValue("ref") != null)
                            ? submissionElement.attributeValue("ref") : "/";
                    xformsControls.resetBindingContext();
                    xformsControls.pushBinding(pipelineContext, refAttribute, null, null, model.getEffectiveId(), null, submissionElement,
                            Dom4jUtils.getNamespaceContextNoDefault(submissionElement));

                    // Check that we have a current node and that it is pointing to a document or an element
                    final NodeInfo currentNodeInfo = xformsControls.getCurrentSingleNode();

                    if (currentNodeInfo == null)
                        throw new OXFException("Empty single-node binding on xforms:submission for submission id: " + id);

                    if (!(currentNodeInfo instanceof DocumentInfo || currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE)) {
                        throw new OXFException("xforms:submission: single-node binding must refer to a document node or an element.");
                    }

                    // For now, we can't submit a read-only instance (but we could in the future)
                    if (!(currentNodeInfo instanceof NodeWrapper))
                        throw new OXFException("xforms:submission: submitting a read-only instance is not yet implemented.");

                    currentNode = (Node) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();
                }

                // Evaluate AVTs
                // TODO FIXME: the submission element is not a control, so we shouldn't use XFormsControls.
                resolvedAction = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xformsControls, submissionElement, avtAction);
                resolvedXXFormsUsername = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xformsControls, submissionElement, avtXXFormsUsername);
                resolvedXXFormsPassword = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xformsControls, submissionElement, avtXXFormsPassword);

                final XFormsInstance currentInstance = xformsControls.getCurrentInstance();

                final Document initialDocumentToSubmit;
                if (!isDeferredSubmissionSecondPass) {
                    // Create document to submit
                    final Document backupInstanceDocument = currentInstance.getInstanceDocument();
                    try {
                        initialDocumentToSubmit = createDocumentToSubmit(currentNode, currentInstance);
                        currentInstance.setInstanceDocument(initialDocumentToSubmit, false);

                        // Revalidate instance
                        model.doRevalidate(pipelineContext);
                        // TODO: Check if the validation state can really change. If so, find a solution.
                        // "no notification events are marked for dispatching due to this operation"

                        // Check that there are no validation errors
                        final boolean instanceSatisfiesValidRequired = isDocumentSatisfiesValidRequired(initialDocumentToSubmit);
                        if (!instanceSatisfiesValidRequired) {
//                            {
//                                currentInstance.readOut();
//                            }
                            if (logger.isDebugEnabled()) {
                                final LocationDocumentResult documentResult = new LocationDocumentResult();
                                final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler();
                                identity.setResult(documentResult);
                                currentInstance.read(identity);
                                final String documentString = Dom4jUtils.domToString(documentResult.getDocument());

                                logger.debug("XForms - instance document or subset thereof cannot be submitted:\n" + documentString);
                            }
                            throw new OXFException("xforms:submission: instance to submit does not satisfy valid and/or required model item properties.");
                        }
                    } finally {
                        currentInstance.setInstanceDocument(backupInstanceDocument, false);
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
                    final Element filesElement = (event instanceof XXFormsSubmitEvent) ? ((XXFormsSubmitEvent) event).getFilesElement() : null;
                    if (filesElement != null) {
                        for (Iterator i = filesElement.elements().iterator(); i.hasNext();) {
                            final Element parameterElement = (Element) i.next();
                            final String name = parameterElement.element("name").getTextTrim();

                            final Element valueElement = parameterElement.element("value");
                            final String value = valueElement.getTextTrim();

                            // An empty value likely means that the user did not select a file. In this case, we don't
                            // want to override an existing value in the instance. Clearing the value in the instance
                            // will have to be done by other means, like a "clear file" button.
                            if (value.length() == 0)
                                continue;

                            final String paramValueType = Dom4jUtils.qNameToexplodedQName(Dom4jUtils.extractAttributeValueQName(valueElement, XMLConstants.XSI_TYPE_QNAME));

                            final String filename = parameterElement.element("filename").getTextTrim();
                            final String mediatype = parameterElement.element("content-type").getTextTrim();
                            final String size = parameterElement.element("content-length").getTextTrim();

                            final XFormsUploadControl uploadControl
                                    = (XFormsUploadControl) containingDocument.getObjectById(pipelineContext, name);

                            if (uploadControl != null)
                            { // in case of xforms:repeat, the name of the template will not match an existing control
                                // Set value into the instance
                                xformsControls.setBinding(pipelineContext, uploadControl);
                                {
                                    final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
                                    XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, value, paramValueType);
                                }

                                // Handle filename if any
                                if (uploadControl.getFilenameElement() != null) {
                                    xformsControls.pushBinding(pipelineContext, uploadControl.getFilenameElement());
                                    final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
                                    XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, filename, null);
                                    xformsControls.popBinding();
                                }

                                // Handle mediatype if any
                                if (uploadControl.getMediatypeElement() != null) {
                                    xformsControls.pushBinding(pipelineContext, uploadControl.getMediatypeElement());
                                    final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
                                    XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, mediatype, null);
                                    xformsControls.popBinding();
                                }

                                // Handle file size if any
                                if (uploadControl.getSizeElement() != null) {
                                    xformsControls.pushBinding(pipelineContext, uploadControl.getSizeElement());
                                    final NodeInfo currentSingleNode = xformsControls.getCurrentSingleNode();
                                    XFormsInstance.setValueForNodeInfo(pipelineContext, currentSingleNode, size, null);
                                    xformsControls.popBinding();
                                }
                            }
                        }
                    }

                    // Create document to submit
                    final Document backupInstanceDocument = currentInstance.getInstanceDocument();
                    try {
                        documentToSubmit = createDocumentToSubmit(currentNode, currentInstance);
                        currentInstance.setInstanceDocument(documentToSubmit, false);

                        // Revalidate instance
                        model.doRevalidate(pipelineContext);
                        // sent out. Check if the validation state can really change. If so, find a
                        // solution.
                        // "no notification events are marked for dispatching due to this operation"

                        // Check that there are no validation errors
                        final boolean instanceSatisfiesValidRequired = isDocumentSatisfiesValidRequired(documentToSubmit);
                        if (!instanceSatisfiesValidRequired) {
    //                        currentInstance.readOut();// FIXME: DEBUG
                            throw new OXFException("xforms:submission: instance to submit does not satisfy valid and/or required model item properties.");
                        }
                    } finally {
                        currentInstance.setInstanceDocument(backupInstanceDocument, false);
                    }
                } else {
                    // Don't recreate document
                    documentToSubmit = initialDocumentToSubmit;
                }

                // Fire xforms-submit-serialize
                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitSerializeEvent(XFormsModelSubmission.this));
                // TODO: what follows must be executed as the default action of xforms-submit-serialize

                // Serialize
                // To support: application/xml, application/x-www-form-urlencoded, multipart/related, multipart/form-data
                final byte[] serializedInstance;
                final String serializedInstanceString;
                {
                    if (XFormsSubmissionUtils.isPost(method) || XFormsSubmissionUtils.isPut(method)) {

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

                    } else if (XFormsSubmissionUtils.isGet(method) || XFormsSubmissionUtils.isDelete(method)) {

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

                // Get URL type
                final String urlType = submissionElement.attributeValue(new QName("url-type", new Namespace("f", XMLConstants.OPS_FORMATTING_URI)));
                final ExternalContext.Request request = externalContext.getRequest();

                // Result information
                ConnectionResult connectionResult = null;
                try {
                    if (isReplaceInstance && resolvedAction.startsWith("test:")) {
                        // Test action

                        if (serializedInstance == null)
                            throw new OXFException("Action 'test:' can only be used with POST method.");

                        connectionResult = new ConnectionResult(null);
                        connectionResult.resultCode = 200;
                        connectionResult.resultHeaders = new HashMap();
                        connectionResult.lastModified = 0;
                        connectionResult.resultMediaType = "application/xml";
                        connectionResult.dontHandleResponse = false;
                        connectionResult.setResultInputStream(new ByteArrayInputStream(serializedInstance));

                    } else if (isHandlingOptimizedGet) {
                        // GET with replace="all": we can optimize and tell the client to just load the URL
                        connectionResult = doOptimizedGet(pipelineContext, serializedInstanceString, xxfShowProgress);
                    } else if (!NetUtils.urlHasProtocol(resolvedAction)
                               && ((request.getContainerType().equals("portlet") && !"resource".equals(urlType))
                                    || (request.getContainerType().equals("servlet")
                                        && (XFormsUtils.isOptimizeLocalSubmission() || isMethodOptimizedLocalSubmission())
                                        &&  isReplaceAll))) {

                        // This is an "optimized" submission, i.e. one that does not use an actual
                        // protocol handler to access the resource

                        // NOTE: Optimizing with include() for servlets doesn't allow detecting
                        // errors caused by the included resource, so we don't allow this for now.

                        // NOTE: For portlets, paths are served directly by the portlet, NOT as
                        // resources.

                        // Current limitations:
                        // o Portlets cannot access resources outside the portlet except by using absolute URLs (unless f:url-type="resource")
                        // o Servlets cannot access resources on the same server but not in the current application
                        //   except by using absolute URLs

                        final URI resolvedURI = XFormsUtils.resolveXMLBase(submissionElement, resolvedAction);
                        connectionResult = XFormsSubmissionUtils.doOptimized(pipelineContext, externalContext,
                                this, method, resolvedURI.toString(), mediatype, isReplaceAll,
                                serializedInstance, serializedInstanceString);

                    } else {
                        // This is a regular remote submission going through a protocol handler

                        // Absolute URLs or absolute paths are allowed to a local servlet
                        String resolvedURL = XFormsUtils.resolveURL(containingDocument, pipelineContext, submissionElement, false, resolvedAction);

                        if (request.getContainerType().equals("portlet") && "resource".equals(urlType) && !NetUtils.urlHasProtocol(resolvedURL)) {
                            // In this case, we have to prepend the complete server path
                            resolvedURL = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "") + resolvedURL;
                        }

                        connectionResult = XFormsSubmissionUtils.doRegular(externalContext,
                                method, resolvedURL, resolvedXXFormsUsername, resolvedXXFormsPassword, mediatype,
                                serializedInstance, serializedInstanceString);
                    }

                    if (!connectionResult.dontHandleResponse) {
                        // Handle response
                        if (connectionResult.resultCode >= 200 && connectionResult.resultCode < 300) {// accept any success code (in particular "201 Resource Created")
                            // Sucessful response
                            if (connectionResult.hasContent()) {
                                // There is a body

                                if (isReplaceAll) {
                                    // When we get here, we are in a mode where we need to send the reply
                                    // directly to an external context, if any.

                                    // "the event xforms-submit-done is dispatched"
                                    containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));

                                    final ExternalContext.Response response = externalContext.getResponse();

                                    // Forward headers to response
                                    if (connectionResult.resultHeaders != null) {
                                        for (Iterator i = connectionResult.resultHeaders.entrySet().iterator(); i.hasNext();) {
                                            final Map.Entry currentEntry = (Map.Entry) i.next();
                                            final String headerName = (String) currentEntry.getKey();
                                            final String headerValue = (String) currentEntry.getValue();

                                            // NOTE: We only get one header value per name
                                            if (headerName != null && headerValue != null) {
                                                response.addHeader(headerName, headerValue);
                                            }
                                        }
                                    }

                                    // Forward content to response
                                    NetUtils.copyStream(connectionResult.getResultInputStream(), response.getOutputStream());

                                } else if (isReplaceInstance) {

                                    if (ProcessorUtils.isXMLContentType(connectionResult.resultMediaType)) {
                                        // Handling of XML media type
                                        try {
                                            // Read stream into Document
                                            final Document resultingInstanceDocument = Dom4jUtils.read(connectionResult.getResultInputStream());

                                            // Set new instance document to replace the one submitted
                                            final XFormsInstance replaceInstance;
                                            {
                                                if (xxfReplaceInstanceId != null)
                                                    replaceInstance = containingDocument.findInstance(xxfReplaceInstanceId);
                                                else if (replaceInstanceId != null)
                                                    replaceInstance = model.getInstance(replaceInstanceId);
                                                else
                                                    replaceInstance = currentInstance;
                                            }

                                            if (replaceInstance == null) {
                                                // Replacement instance was specified but not found
                                                containingDocument.dispatchEvent(pipelineContext, new XFormsBindingExceptionEvent(XFormsModelSubmission.this));
                                            } else {

                                                // Set new instance
                                                replaceInstance.setInstanceDocument(resultingInstanceDocument, true);

                                                // Mark all values as changed so that refresh sends appropriate events
                                                XFormsUtils.markAllValuesChanged(replaceInstance);

                                                // Handle new instance and associated events
                                                replaceInstance.getModel().handleNewInstanceDocuments(pipelineContext);

                                                // Notify that submission is done
                                                submitDone = true;
                                            }
                                        } catch (Exception e) {
                                            submitErrorEvent = createErrorEvent(connectionResult);
                                            throw new OXFException("xforms:submission: exception while serializing XML to instance.", e);
                                        }
                                    } else {
                                        // Other media type
                                        submitErrorEvent = createErrorEvent(connectionResult);
                                        throw new OXFException("Body received with non-XML media type for replace=\"instance\": " + connectionResult.resultMediaType);
                                    }
                                } else if (replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_NONE)) {
                                    // Just notify that processing is terminated
                                    submitDone = true;
                                } else {
                                    submitErrorEvent = createErrorEvent(connectionResult);
                                    throw new OXFException("xforms:submission: invalid replace attribute: " + replace);
                                }

                            } else {
                                // There is no body, notify that processing is terminated
                                submitDone = true;
                            }
                        } else if (connectionResult.resultCode == 302 || connectionResult.resultCode == 301) {
                            // Got a redirect

                            final ExternalContext.Response response = externalContext.getResponse();

                            // Forward headers to response
                            // TODO: this is duplicated from above
                            if (connectionResult.resultHeaders != null) {
                                for (Iterator i = connectionResult.resultHeaders.entrySet().iterator(); i.hasNext();) {
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
                            response.setStatus(connectionResult.resultCode);

                        } else {
                            // Error code received
                            submitErrorEvent = createErrorEvent(connectionResult);
                            throw new OXFException("Error code received when submitting instance: " + connectionResult.resultCode);
                        }
                    }
                } finally {
                    // Clean-up
                    if (connectionResult != null) {
                        connectionResult.close();
                    }
                }
            } catch (Throwable e) {
                if (isDeferredSubmissionSecondPass && XFormsUtils.isOptimizePostAllSubmission()) {
                    // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                    throw new OXFException(e);
                } else {
                    // Any exception will cause an error event to be dispatched
                    if (submitErrorEvent == null)
                        submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, resolvedAction);

                    submitErrorEvent.setThrowable(e);
                    containingDocument.dispatchEvent(pipelineContext, submitErrorEvent);
                }
            }

            // If submission succeeded, dispatch success event
            if (submitDone) {
                containingDocument.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this));
            }

        } else if (XFormsEvents.XFORMS_BINDING_EXCEPTION.equals(eventName)) {
            // The default action for this event results in the following: Fatal error.
            throw new OXFException("Binding exception.");
        }
    }

    private XFormsSubmitErrorEvent createErrorEvent(ConnectionResult connectionResult) throws IOException {
        XFormsSubmitErrorEvent submitErrorEvent = null;
        if (connectionResult.hasContent()) {
            if (ProcessorUtils.isXMLContentType(connectionResult.resultMediaType)) {
                // XML content-type
                // TODO: XForms 1.1 may mandate that we always try to parse the body as XML first
                // Read stream into Document
                final DocumentInfo responseBody
                        = TransformerUtils.readTinyTree(connectionResult.getResultInputStream(), connectionResult.resourceURI);

                submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, resolvedAction);
                submitErrorEvent.setBodyDocument(responseBody);
            } else if (ProcessorUtils.isTextContentType(connectionResult.resultMediaType)) {
                // Text content-type
                // Read stream into String
                final String charset;
                {
                    final String connectionCharset = NetUtils.getContentTypeCharset(connectionResult.resultMediaType);
                    if (connectionCharset != null)
                        charset = connectionCharset;
                    else
                        charset = DEFAULT_TEXT_READING_ENCODING;
                }
                final Reader reader = new InputStreamReader(connectionResult.getResultInputStream(), charset);
                final String responseBody = NetUtils.readStreamAsString(reader);
                submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, resolvedAction);
                submitErrorEvent.setBodyString(responseBody);
            } else {
                // This is binary
                // Don't store anything for now
            }
        }
        return submitErrorEvent;
    }

    private ConnectionResult doOptimizedGet(PipelineContext pipelineContext, String serializedInstanceString, boolean isShowProgress) {
        final String actionString = resolvedAction + ((resolvedAction.indexOf('?') == -1) ? "?" : "") + serializedInstanceString;
        final String resultURL = XFormsLoadAction.resolveLoadValue(containingDocument, pipelineContext, submissionElement, true, actionString, null, null, false, isShowProgress);
        final ConnectionResult connectionResult = new ConnectionResult(resultURL);
        connectionResult.dontHandleResponse = true;
        return connectionResult;
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
            documentToSubmit = Dom4jUtils.createDocument(currentInstance.getInstanceDocument().getRootElement());
        }

        if (relevant) {
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
                            final InstanceData instanceData = XFormsUtils.getInstanceDataUpdateInherited(node);
                            // Check "relevant" MIP and remove non-relevant nodes
                            {
                                final BooleanModelItemProperty relevantMIP = instanceData.getInheritedRelevant();
                                if (relevantMIP != null && !relevantMIP.get())
                                    nodeToDetach[0] = node;
                            }
                        }
                    }
                });
                if (nodeToDetach[0] != null)
                    nodeToDetach[0].detach();

            } while (nodeToDetach[0] != null);
        }

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
        final boolean[] instanceSatisfiesValidRequired = new boolean[]{true};
        if (validate) {
            documentToSubmit.accept(new VisitorSupport() {

                public final void visit(Element element) {
                    final InstanceData instanceData = XFormsUtils.getLocalInstanceData(element);
                    final boolean valid = checkInstanceData(instanceData);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && logger.isDebugEnabled()) {
                        logger.debug("Found invalid element: " + element.getQName() + ", value:" + element.getText());
                    }
                }

                public final void visit(Attribute attribute) {
                    final InstanceData instanceData = XFormsUtils.getLocalInstanceData(attribute);
                    final boolean valid = checkInstanceData(instanceData);

                    instanceSatisfiesValidRequired[0] &= valid;

                    if (!valid && logger.isDebugEnabled()) {
                        logger.debug("Found invalid attribute: " + attribute.getQName() + ", value:" + attribute.getValue());
                    }
                }

                private final boolean checkInstanceData(InstanceData instanceData) {
                    // Check "valid" MIP
                    {
                        final BooleanModelItemProperty validMIP = instanceData.getValid();
                        if (validMIP != null && !validMIP.get())
                            return false;
                    }
                    // Check "required" MIP
                    {
                        final ValidModelItemProperty requiredMIP = instanceData.getRequired();
                        if (requiredMIP != null && requiredMIP.get() && requiredMIP.getStringValue().length() == 0) {
                            // Required and empty
                            return false;
                        }
                    }
                    return true;
                }
            });
        }
        return instanceSatisfiesValidRequired[0];
    }

    public static class ConnectionResult {
        public boolean dontHandleResponse;
        public int resultCode;
        public String resultMediaType;
        public Map resultHeaders;
        public long lastModified;
        public String resourceURI;

        private InputStream resultInputStream;
        private boolean hasContent;

        public ConnectionResult(String resourceURI) {
            this.resourceURI = resourceURI;
        }

        public InputStream getResultInputStream() {
        	return resultInputStream;
        }

        public boolean hasContent() {
            return hasContent;
        }

        public void setResultInputStream(final InputStream resultInputStream) throws IOException {
        	this.resultInputStream = resultInputStream;
        	setHasContentFlag();

        }

        private void setHasContentFlag() throws IOException {
            if (resultInputStream == null) {
                hasContent = false;
            } else {
                if (!resultInputStream.markSupported())
                    this.resultInputStream = new BufferedInputStream(resultInputStream);

                resultInputStream.mark(1);
                hasContent = resultInputStream.read() != -1;
                resultInputStream.reset();
            }
        }

        public void close() {}
    }
}

class ResponseAdapter implements ExternalContext.Response {

    private Object nativeResponse;

    private int status = 200;
    private String contentType;

    private StringWriter stringWriter;
    private PrintWriter printWriter;
    private LocalByteArrayOutputStream byteStream;

    private InputStream inputStream;

    public ResponseAdapter(Object nativeResponse) {
        this.nativeResponse = nativeResponse;
    }

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
                final byte[] bytes;
                try {
                    bytes = stringWriter.getBuffer().toString().getBytes("utf-8");
                } catch (UnsupportedEncodingException e) {
                    throw new OXFException(e); // should not happen
                }
                inputStream = new ByteArrayInputStream(bytes, 0, bytes.length);
//                throw new OXFException("ResponseAdapter.getInputStream() does not yet support content written with getWriter().");
            } else if (byteStream != null) {
                inputStream = new ByteArrayInputStream(byteStream.getByteArray(), 0, byteStream.size());
            }
        }

        return inputStream;
    }

    public void addHeader(String name, String value) {
    }

    public boolean checkIfModifiedSince(long lastModified, boolean allowOverride) {
        return true;
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

    public String rewriteActionURL(String urlString, String portletMode, String windowState) {
        return null;
    }

    public String rewriteRenderURL(String urlString, String portletMode, String windowState) {
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
        return nativeResponse;
    }
}
