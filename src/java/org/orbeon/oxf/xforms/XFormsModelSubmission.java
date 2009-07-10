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

import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.actions.XFormsDeleteAction;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
import org.orbeon.oxf.xforms.action.actions.XFormsLoadAction;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an XForms model submission instance.
 *
 * TODO: This badly needs to be modularized instead of being a soup of "ifs"!
 */
public class XFormsModelSubmission implements XFormsEventTarget, XFormsEventObserver {

	public final static Logger logger = LoggerFactory.createLogger(XFormsModelSubmission.class);

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;
    private final String id;
    private final XFormsModel model;
    private final Element submissionElement;
    private boolean submissionElementExtracted = false;

    private String avtActionOrResource; // required unless there is a nested xforms:resource element
    private String resolvedActionOrResource;
    private String avtMethod; // required

    private String avtValidate;
    private String avtRelevant;

    private String avtSerialization;
    private boolean serialize = true;// computed from @serialization attribute or legacy @serialize attribute

    private String targetref;// this is an XPath expression when used with replace="instance|text" (other meaning possible post-XForms 1.1 for replace="all")
    private String avtMode;

    private String avtVersion;
    private String avtEncoding;
    private String avtMediatype;
    private String avtIndent;
    private String avtOmitxmldeclaration;
    private String avtStandalone;
//    private String cdatasectionelements;

    private String replace = XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL;
    private String replaceInstanceId;
    private String xxfReplaceInstanceId;
    private String avtSeparator = "&";// XForms 1.1 changes back the default to the ampersand as of February 2009
//    private String includenamespaceprefixes;

    private String avtXXFormsUsername;
    private String avtXXFormsPassword;
    private String avtXXFormsReadonly;
    private String avtXXFormsShared;
    private String avtXXFormsCache;
    private String avtXXFormsTarget;
    private String resolvedXXFormsTarget;
    private String avtXXFormsHandleXInclude;

    private boolean xxfFormsEnsureUploads;

    private boolean xxfShowProgress;

    private boolean fURLNorewrite;
    private String urlType;

    public XFormsModelSubmission(XBLContainer container, String id, Element submissionElement, XFormsModel model) {
        this.container = container;
        this.containingDocument = container.getContainingDocument();
        this.id = id;
        this.submissionElement = submissionElement;
        this.model = model;
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

    public String getReplace() {
        return replace;
    }

    public String getResolvedXXFormsTarget() {
        return resolvedXXFormsTarget;
    }

    private void extractSubmissionElement() {
        if (!submissionElementExtracted) {

            avtActionOrResource = submissionElement.attributeValue("resource");
            if (avtActionOrResource == null) // @resource has precedence over @action
                avtActionOrResource = submissionElement.attributeValue("action");
            if (avtActionOrResource == null) {
                // TODO: For XForms 1.1, support @resource and nested xforms:resource
                throw new XFormsSubmissionException("xforms:submission: action attribute or resource attribute is missing.",
                        "processing xforms:submission attributes");
            }

            avtMethod = submissionElement.attributeValue("method");
            avtValidate = submissionElement.attributeValue("validate");
            avtRelevant = submissionElement.attributeValue("relevant");

            avtSerialization = submissionElement.attributeValue("serialization");
            if (avtSerialization != null) {
                serialize = !avtSerialization.equals("none");
            } else {
                // For backward compability only, support @serialize if there is no @serialization attribute (was in early XForms 1.1 draft)
                serialize = !"false".equals(submissionElement.attributeValue("serialize"));
            }

            // @targetref is the new name as of May 2009, and @target is still supported for backward compatibility
            targetref = submissionElement.attributeValue("targetref");
            if (targetref == null)
                targetref = submissionElement.attributeValue("target");

            avtMode = submissionElement.attributeValue("mode");

            avtVersion = submissionElement.attributeValue("version");

            avtIndent = submissionElement.attributeValue("indent");
            avtMediatype = submissionElement.attributeValue("mediatype");
            avtEncoding = submissionElement.attributeValue("encoding");
            avtOmitxmldeclaration = submissionElement.attributeValue("omit-xml-declaration");
            avtStandalone = submissionElement.attributeValue("standalone");

            // TODO
//            cdatasectionelements = submissionElement.attributeValue("cdata-section-elements");
            if (submissionElement.attributeValue("replace") != null) {
                replace = submissionElement.attributeValue("replace");

                if (replace.equals("instance")) {
                    replaceInstanceId = XFormsUtils.namespaceId(containingDocument, submissionElement.attributeValue("instance"));
                    xxfReplaceInstanceId = XFormsUtils.namespaceId(containingDocument, submissionElement.attributeValue(XFormsConstants.XXFORMS_INSTANCE_QNAME));
                }
            }
            if (submissionElement.attributeValue("separator") != null) {
                avtSeparator = submissionElement.attributeValue("separator");
            }
            // TODO
//            includenamespaceprefixes = submissionElement.attributeValue("includenamespaceprefixes");

            // Extension attributes
            avtXXFormsUsername = submissionElement.attributeValue(XFormsConstants.XXFORMS_USERNAME_QNAME);
            avtXXFormsPassword = submissionElement.attributeValue(XFormsConstants.XXFORMS_PASSWORD_QNAME);

            avtXXFormsReadonly = submissionElement.attributeValue(XFormsConstants.XXFORMS_READONLY_ATTRIBUTE_QNAME);
            avtXXFormsShared = submissionElement.attributeValue(XFormsConstants.XXFORMS_SHARED_QNAME);
            avtXXFormsCache = submissionElement.attributeValue(XFormsConstants.XXFORMS_CACHE_QNAME);

            avtXXFormsTarget = submissionElement.attributeValue(XFormsConstants.XXFORMS_TARGET_QNAME);
            xxfFormsEnsureUploads = !"false".equals(submissionElement.attributeValue(XFormsConstants.XXFORMS_ENSURE_UPLOADS_QNAME));
            avtXXFormsHandleXInclude = submissionElement.attributeValue(XFormsConstants.XXFORMS_XINCLUDE);

            // Whether we must show progress or not
            xxfShowProgress = !"false".equals(submissionElement.attributeValue(XFormsConstants.XXFORMS_SHOW_PROGRESS_QNAME));

            // Whether or not to rewrite URLs
            fURLNorewrite = XFormsUtils.resolveUrlNorewrite(submissionElement);

            // URL type
            urlType = submissionElement.attributeValue(XMLConstants.FORMATTING_URL_TYPE_QNAME);

            // Remember that we did this
            submissionElementExtracted = true;
        }
    }

    public String getId() {
        return id;
    }

    public String getEffectiveId() {
        return XFormsUtils.getRelatedEffectiveId(model.getEffectiveId(), getId());
    }

    public XBLContainer getXBLContainer(XFormsContainingDocument containingDocument) {
        return getModel().getXBLContainer();
    }

    public LocationData getLocationData() {
        return (LocationData) submissionElement.getData();
    }

    public XFormsEventObserver getParentEventObserver(XBLContainer container) {
        return model;
    }

    public XFormsModel getModel() {
        return model;
    }

    public List getEventHandlers(XBLContainer container) {
        return containingDocument.getStaticState().getEventHandlers(XFormsUtils.getEffectiveIdNoSuffix(getEffectiveId()));
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        final String eventName = event.getEventName();

        if (XFormsEvents.XFORMS_SUBMIT.equals(eventName) || XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            containingDocument.setGotSubmission();

            // Variables declared here as they are used in a catch/finally block
            boolean isDeferredSubmissionSecondPassReplaceAll = false;
            XFormsSubmitDoneEvent submitDoneEvent = null;
            final long submissionStartTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;

            // Make sure submission element info is extracted
            extractSubmissionElement();

            try {
                // Big bag of initial runtime parameters
                final SubmissionParameters p = new SubmissionParameters(pipelineContext, eventName);

                if (p.isDeferredSubmissionSecondPass)
                    containingDocument.setGotSubmissionSecondPass();

                // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
                // issue a warning
                {
                    final XFormsModelSubmission existingSubmission = containingDocument.getClientActiveSubmission();
                    if (p.isDeferredSubmission && existingSubmission != null) {
                        logger.warn("XForms - submission - another submission requiring a second pass already exists (" + existingSubmission.getEffectiveId() + "). Ignoring new submission (" + this.getEffectiveId() + ").");
                        return;
                    }
                }

                // "The data model is updated"
                final XFormsModel modelForInstance;
                if (p.refInstance != null) {
                    modelForInstance = p.refInstance.getModel(containingDocument);
                    {
                        // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
                        // the instance is not serialized and if no instance data is otherwise used for the submission,
                        // this seems however unneeded.

                        // TODO: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
                        modelForInstance.rebuildRecalculateIfNeeded(pipelineContext);
                    }
                } else {
                    // Case where no instance was found
                    modelForInstance = null;
                }

                // Resolve the target AVT because XFormsServer requires it for deferred submission
                resolvedXXFormsTarget = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsTarget);

                // Deferred submission: end of the first pass
                if (p.isDeferredSubmissionFirstPass) {

                    // Create document to submit here because in case of error, an Ajax response will still be produced
                    if (serialize) {
                        createDocumentToSubmit(pipelineContext, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant);
                    }

                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setClientActiveSubmission(this);
                    return;
                }

                /* ************************************* Submission second pass ************************************* */

                // Compute parameters only needed during second pass
                final SecondPassParameters p2 = new SecondPassParameters(pipelineContext, p);

                /* ************************************* Serialization ************************************* */

                // Get serialization requested from @method and @serialization attributes
                final String requestedSerialization = getRequestedSerialization(p2.resolvedSerialization, p.resolvedMethod);

                final Document documentToSubmit;
                if (serialize) {
                    // Handle uploaded files if any
                    final Element filesElement = (event instanceof XXFormsSubmitEvent) ? ((XXFormsSubmitEvent) event).getFilesElement() : null;
                    if (filesElement != null) {
                        // Handle all file elements

                        // NOTE: We used to request handling of temp files only if NOT replace="all". Guessing the
                        // rationale was that user would be navigating to new page anyway. However, this was not a
                        // correct assumption: the page might load in another window/tab, result in an file being
                        // downloaded, or simply the file might be used by the next page.
                        XFormsUploadControl.handleFileElement(pipelineContext, containingDocument, filesElement, null, true);
                    }

                    // Check if a submission requires file upload information
                    if (requestedSerialization.startsWith("multipart/")) {
                        // Annotate before re-rooting/pruning
                        XFormsSubmissionUtils.annotateBoundRelevantUploadControls(pipelineContext, containingDocument, p.refInstance);
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(pipelineContext, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant);

                } else {
                    // Don't recreate document
                    documentToSubmit = null;
                }

                final String overriddenSerializedData;
                if (serialize && !isDeferredSubmissionSecondPassReplaceAll) { // we don't want any changes to happen to the document upon xxforms-submit when producing a new document
                    // Fire xforms-submit-serialize

                    // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
                    // is changed from the initial value of empty string, then the content of the submission-body
                    // property string is used as the submission serialization. Otherwise, the submission serialization
                    // consists of a serialization of the selected instance data according to the rules stated at 11.9
                    // Submission Options."

                    final XFormsSubmitSerializeEvent serializeEvent = new XFormsSubmitSerializeEvent(XFormsModelSubmission.this, p.refNodeInfo, requestedSerialization);
                    container.dispatchEvent(pipelineContext, serializeEvent);

                    // TODO: rest of submission should happen upon default action of event

                    overriddenSerializedData = serializeEvent.getSerializedData();
                } else {
                    overriddenSerializedData = null;
                }

                // Serialize
                final SerializationParameters sp = new SerializationParameters(pipelineContext, p, p2,
                        requestedSerialization, documentToSubmit, overriddenSerializedData);

                /* ************************************* Execute submission ************************************* */

                final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
                final ExternalContext.Request request = externalContext.getRequest();

                // Result information
                ConnectionResult connectionResult = null;
                final long externalSubmissionStartTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
                try {

                    // TODO: implement various types of submissions in different classes, and test each of those in order

                    if ((p.isReplaceInstance || p.isReplaceNone) && resolvedActionOrResource.startsWith("test:")) {
                        // Test action

                        connectionResult = testSubmission(sp);

                    } else if (p.isHandlingOptimizedGet) {
                        // GET with replace="all": we can optimize and tell the client to just load the URL

                        connectionResult = optimizedGetSubmission(pipelineContext, sp);

                    } else if (isAllowOptimizedSubmission(p.isReplaceAll, p.isNoscript, request, p2.isAsyncSubmission)) {
                        connectionResult = optimizedSubmission(pipelineContext, p, sp, externalContext);

                    } else if (p2.resolvedXXFormsCache) {
                        // This is a cacheable remote submission going through a protocol handler
                        connectionResult = cachedSubmission(pipelineContext, externalContext, request, p, p2, sp);

                        // If no exception, submission is done here: just dispatch the event
                        submitDoneEvent = new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult.resourceURI, connectionResult.statusCode);
                    } else {
                        // This is a regular remote submission going through a protocol handler

                        connectionResult = regularSubmission(pipelineContext, externalContext, request, p, p2, sp);
                    }

                    /* ************************************* Submission response ************************************* */

                    if (connectionResult != null && !connectionResult.dontHandleResponse) {
                        // Handle response
                        if (connectionResult.statusCode >= 200 && connectionResult.statusCode < 300) {// accept any success code (in particular "201 Resource Created")
                            // Sucessful response
                            if (connectionResult.hasContent()) {
                                // There is a body

                                if (p.isReplaceAll) {
                                    // When we get here, we are in a mode where we need to send the reply
                                    // directly to an external context, if any.

                                    // "the event xforms-submit-done is dispatched"
                                    if (!isDeferredSubmissionSecondPassReplaceAll) // we don't want any changes to happen to the document upon xxforms-submit when producing a new document
                                        container.dispatchEvent(pipelineContext, new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult));

                                    // Remember that we got a submission producing output
                                    containingDocument.setGotSubmissionReplaceAll();

                                    // Get response from containing document
                                    final ExternalContext.Response response = containingDocument.getResponse();

                                    // Set content-type
                                    response.setContentType(connectionResult.getResponseContentType());

                                    // Forward headers to response
                                    connectionResult.forwardHeaders(response);

                                    // Forward content to response
                                    final OutputStream outputStream = response.getOutputStream();
                                    NetUtils.copyStream(connectionResult.getResponseInputStream(), outputStream);

                                    // End document and close
                                    outputStream.flush();
                                    outputStream.close();

                                    // TODO: [#306918] RFE: Must be able to do replace="all" during initialization.
                                    // http://forge.objectweb.org/tracker/index.php?func=detail&aid=306918&group_id=168&atid=350207
                                    // Suggestion is to write either binary or XML to processor output ContentHandler,
                                    // and make sure the code which would output the XHTML+XForms is disabled.

                                } else if (p.isReplaceInstance) {

                                    if (XMLUtils.isXMLMediatype(connectionResult.getResponseMediaType())) {
                                        // Handling of XML media type
                                        // Set new instance document to replace the one submitted

                                        final XFormsInstance replaceInstanceNoTargetref = findReplaceInstanceNoTargetref(p.refInstance);
                                        if (replaceInstanceNoTargetref == null) {

                                            // Replacement instance or node was specified but not found
                                            //
                                            // Not sure what's the right thing to do with 1.1, but this could be done
                                            // as part of the model's static analysis if the instance value is not
                                            // obtained through AVT, and dynamically otherwise. However, in the dynamic
                                            // case, I think that this should be a (currently non-specified by XForms)
                                            // xforms-binding-error.
                                            container.dispatchEvent(pipelineContext, new XFormsBindingExceptionEvent(XFormsModelSubmission.this));
                                        } else {

                                            final NodeInfo destinationNodeInfo = evaluateTargetRef(pipelineContext,
                                                    replaceInstanceNoTargetref, p.submissionElementContextItem,
                                                    p.prefixToURIMap, p.contextStack, p.functionLibrary, p.functionContext);

                                            if (destinationNodeInfo == null) {
                                                // Throw target-error

                                                // XForms 1.1: "If the processing of the targetref attribute fails,
                                                // then submission processing ends after dispatching the event
                                                // xforms-submit-error with an error-type of target-error."

                                                throw new XFormsSubmissionException("targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                                                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                                            }

                                            // This is the instance which is effectively going to be updated
                                            final XFormsInstance updatedInstance = containingDocument.getInstanceForNode(destinationNodeInfo);
                                            if (updatedInstance == null) {
                                                throw new XFormsSubmissionException("targetref attribute doesn't point to an element in an existing instance for replace=\"instance\".", "processing targetref attribute",
                                                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                                            }

                                            // Whether the destination node is the root element of an instance
                                            final boolean isDestinationRootElement = updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo);
                                            if (p2.resolvedXXFormsReadonly && !isDestinationRootElement) {
                                                // Only support replacing the root element of an instance when using a shared instance
                                                throw new XFormsSubmissionException("targetref attribute must point to instance root element when using read-only instance replacement.", "processing targetref attribute",
                                                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                                            }

                                            // Obtain root element to insert
                                            final NodeInfo newDocumentRootElement;
                                            final XFormsInstance newInstance;
                                            try {
                                                // Create resulting instance whether entire instance is replaced or not, because this:
                                                // 1. Wraps a Document within a DocumentInfo if needed
                                                // 2. Performs text nodes adjustments if needed
                                                if (!p2.resolvedXXFormsReadonly) {
                                                    // Resulting instance must not be read-only

                                                    // TODO: What about configuring validation? And what default to choose?
                                                    final Document resultingInstanceDocument
                                                            = TransformerUtils.readDom4j(connectionResult.getResponseInputStream(), connectionResult.resourceURI, p2.resolvedXXFormsHandleXInclude);

                                                    if (XFormsServer.logger.isDebugEnabled())
                                                        containingDocument.logDebug("submission", "replacing instance with mutable instance",
                                                            "instance", updatedInstance.getEffectiveId());

                                                    newInstance = new XFormsInstance(updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                                                            resultingInstanceDocument, connectionResult.resourceURI, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                                                            false, -1, updatedInstance.getValidation(), p2.resolvedXXFormsHandleXInclude);
                                                } else {
                                                    // Resulting instance must be read-only

                                                    // TODO: What about configuring validation? And what default to choose?
                                                    // NOTE: isApplicationSharedHint is always false when get get here. isApplicationSharedHint="true" is handled above.
                                                    final DocumentInfo resultingInstanceDocument
                                                            = TransformerUtils.readTinyTree(connectionResult.getResponseInputStream(), connectionResult.resourceURI, p2.resolvedXXFormsHandleXInclude);

                                                    if (XFormsServer.logger.isDebugEnabled())
                                                        containingDocument.logDebug("submission", "replacing instance with read-only instance",
                                                            "instance", updatedInstance.getEffectiveId());

                                                    newInstance = new ReadonlyXFormsInstance(updatedInstance.getEffectiveModelId(), updatedInstance.getId(),
                                                            resultingInstanceDocument, connectionResult.resourceURI, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                                                            false, -1, updatedInstance.getValidation(), p2.resolvedXXFormsHandleXInclude);
                                                }
                                                newDocumentRootElement = newInstance.getInstanceRootElementInfo();
                                            } catch (Exception e) {
                                                throw new XFormsSubmissionException(e, "xforms:submission: exception while reading XML response.", "processing instance replacement",
                                                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.PARSE_ERROR, connectionResult));
                                            }

                                            // Perform insert/delete. This will dispatch xforms-insert/xforms-delete events.
                                            // "the replacement is performed by an XForms action that performs some
                                            // combination of node insertion and deletion operations that are
                                            // performed by the insert action (10.3 The insert Element) and the
                                            // delete action"

                                            if (isDestinationRootElement) {
                                                // Optimized insertion for instance root element replacement

                                                // Handle new instance and associated event markings
                                                final XFormsModel replaceModel = newInstance.getModel(containingDocument);
                                                replaceModel.handleUpdatedInstance(pipelineContext, newInstance, newDocumentRootElement);
                                                
                                                // Dispatch xforms-delete event
                                                // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
                                                // (see below). In the future, we might want to dispatch this, especially if
                                                // XFormsInsertAction dispatches xforms-delete when removing the root element
                                                //updatedInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(updatedInstance, Collections.singletonList(destinationNodeInfo), 1));

                                                // Dispatch xforms-insert event
                                                // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
                                                newInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext,
                                                    new XFormsInsertEvent(newInstance, Collections.singletonList((Item) newDocumentRootElement), null, newDocumentRootElement.getDocumentRoot(),
                                                            "after", null, null, true));

                                            } else {
                                                // Generic insertion

                                                final List<NodeInfo> destinationCollection = Collections.singletonList(destinationNodeInfo);

                                                // Perform the insertion

                                                // Insert before the target node, so that the position of the inserted node
                                                // wrt its parent does not change after the target node is removed
                                                final List insertedNode = XFormsInsertAction.doInsert(pipelineContext, containingDocument, "before",
                                                        destinationCollection, destinationNodeInfo.getParent(),
                                                        Collections.singletonList(newDocumentRootElement), 1, false, true);

                                                if (!destinationNodeInfo.getParent().isSameNodeInfo(destinationNodeInfo.getDocumentRoot())) {
                                                    // The node to replace is NOT a root element

                                                    // Perform the deletion of the selected node
                                                    XFormsDeleteAction.doDelete(pipelineContext, containingDocument, destinationCollection, 1, true);
                                                }

                                                // Perform model instance update
                                                // Handle new instance and associated event markings
                                                // NOTE: The inserted node NodeWrapper.index might be out of date at this point because:
                                                // * doInsert() dispatches an event which might itself change the instance
                                                // * doDelete() does as well
                                                // Does this mean that we should check that the node is still where it should be?
                                                final XFormsModel updatedModel = updatedInstance.getModel(containingDocument);
                                                updatedModel.handleUpdatedInstance(pipelineContext, updatedInstance, (NodeInfo) insertedNode.get(0));
                                            }

                                            // Notify that submission is done
                                            submitDoneEvent = new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult);
                                        }
                                    } else {
                                        // Other media type
                                        throw new XFormsSubmissionException("Body received with non-XML media type for replace=\"instance\": " + connectionResult.getResponseMediaType(), "processing instance replacement",
                                                new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
                                    }
                                } else if (p.isReplaceText) {

                                    // XForms 1.1: "If the replace attribute contains the value "text" and the
                                    // submission response conforms to an XML mediatype (as defined by the content type
                                    // specifiers in [RFC 3023]) or a text media type (as defined by a content type
                                    // specifier of text/*), then the response data is encoded as text and replaces the
                                    // content of the replacement target node."

                                    // Get response body
                                    final String responseBody = connectionResult.getTextResponseBody();
                                    if (responseBody == null) {
                                        // This is a binary result

                                        // Don't store anything for now as per the spec, but we could do something better by going beyond the spec
                                        // NetUtils.inputStreamToAnyURI(pipelineContext, connectionResult.resultInputStream, NetUtils.SESSION_SCOPE);

                                        // XForms 1.1: "For a success response including a body that is both a non-XML
                                        // media type (i.e. with a content type not matching any of the specifiers in
                                        // [RFC 3023]) and a non-text type (i.e. with a content type not matching
                                        // text/*), when the value of the replace attribute on element submission is
                                        // "text", nothing in the document is replaced and submission processing
                                        // concludes after dispatching xforms-submit-error with appropriate context
                                        // information, including an error-type of resource-error."
                                        throw new XFormsSubmissionException("Mediatype is neither text nor XML for replace=\"text\": " + connectionResult.getResponseMediaType(), "reading response body",
                                                new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
                                    }

                                    // Find target location
                                    final NodeInfo destinationNodeInfo;
                                    if (targetref != null) {
                                        // Evaluate destination node
                                        final Object destinationObject
                                                = XPathCache.evaluateSingle(pipelineContext, p.refNodeInfo, targetref, p.prefixToURIMap,
                                                p.contextStack.getCurrentVariables(), p.functionLibrary, p.functionContext, null, getLocationData());

                                        if (destinationObject instanceof NodeInfo) {
                                            destinationNodeInfo = (NodeInfo) destinationObject;
                                            if (destinationNodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE && destinationNodeInfo.getNodeKind() != org.w3c.dom.Document.ATTRIBUTE_NODE) {
                                                // Throw target-error

                                                // XForms 1.1: "If the processing of the targetref attribute fails,
                                                // then submission processing ends after dispatching the event
                                                // xforms-submit-error with an error-type of target-error."
                                                throw new XFormsSubmissionException("targetref attribute doesn't point to an element or attribute for replace=\"text\".", "processing targetref attribute",
                                                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                                            }
                                        } else {
                                            // Throw target-error
                                            // TODO: also do this for readonly situation

                                            // XForms 1.1: "If the processing of the targetref attribute fails, then
                                            // submission processing ends after dispatching the event
                                            // xforms-submit-error with an error-type of target-error."
                                            throw new XFormsSubmissionException("targetref attribute doesn't point to a node for replace=\"text\".", "processing targetref attribute",
                                                    new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, connectionResult));
                                        }
                                    } else {
                                        // Handle default destination
                                        destinationNodeInfo = findReplaceInstanceNoTargetref(p.refInstance).getInstanceRootElementInfo();
                                    }

                                    // Set value into the instance
                                    XFormsSetvalueAction.doSetValue(pipelineContext, containingDocument, this, destinationNodeInfo, responseBody, null, false);

                                    // Notify that processing is terminated
                                    submitDoneEvent = new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult);

                                } else if (p.isReplaceNone) {
                                    // Just notify that processing is terminated
                                    submitDoneEvent = new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult);
                                } else {
                                    throw new XFormsSubmissionException("xforms:submission: invalid replace attribute: " + replace, "processing instance replacement",
                                            new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.XXFORMS_INTERNAL_ERROR, connectionResult));
                                }

                            } else {
                                // There is no body, notify that processing is terminated

                                if (p.isReplaceInstance) {
                                    // XForms 1.1 says it is fine not to have a body, but in most cases you will want
                                    // to know that no instance replacement took place
                                    XFormsServer.logger.warn("XForms - submission - instance replacement did not take place upon successful response because no body was provided. Submission: "
                                            + getEffectiveId());
                                }

                                submitDoneEvent = new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult);
                            }
                        } else if (connectionResult.statusCode == 302 || connectionResult.statusCode == 301) {
                            // Got a redirect

                            final ExternalContext.Response response = externalContext.getResponse();

                            // Forward headers to response
                            connectionResult.forwardHeaders(response);

                            // Forward redirect
                            response.setStatus(connectionResult.statusCode);

                        } else {
                            // Error code received
                            throw new XFormsSubmissionException("xforms:submission for submission id: " + id + ", error code received when submitting instance: " + connectionResult.statusCode, "processing submission response",
                                    new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
                        }
                    }
                } finally {
                    // Clean-up
                    if (connectionResult != null) {
                        connectionResult.close();
                    }
                    // Log time spent in submission if needed
                    if (XFormsServer.logger.isDebugEnabled()) {
                        final long submissionTime = System.currentTimeMillis() - externalSubmissionStartTime;
                        containingDocument.logDebug("submission", "external submission time including handling returned body",
                            "time", Long.toString(submissionTime));
                    }
                }
            } catch (Throwable e) {
                if (isDeferredSubmissionSecondPassReplaceAll && XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)) {
                    // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                    throw new XFormsSubmissionException(e, "Error while processing xforms:submission", "processing submission");
                } else {
                    // Any exception will cause an error event to be dispatched

                    // Try to get error event from exception
                    XFormsSubmitErrorEvent submitErrorEvent = null;
                    if (e instanceof XFormsSubmissionException) {
                        final XFormsSubmissionException submissionException = (XFormsSubmissionException) e;
                        submitErrorEvent = submissionException.getSubmitErrorEvent();
                    }

                    // If no event obtained, create default event
                    if (submitErrorEvent == null) {
                        submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, resolvedActionOrResource,
                            XFormsSubmitErrorEvent.ErrorType.XXFORMS_INTERNAL_ERROR, 0);
                    }

                    // Dispatch event
                    submitErrorEvent.setThrowable(e);
                    container.dispatchEvent(pipelineContext, submitErrorEvent);
                }
            } finally {
                // If submission succeeded, dispatch success event
                if (submitDoneEvent != null && !isDeferredSubmissionSecondPassReplaceAll) { // we don't want any changes to happen to the document upon xxforms-submit when producing a new document
                    container.dispatchEvent(pipelineContext, submitDoneEvent);
                }
                // Log total time spent in submission if needed
                if (XFormsServer.logger.isDebugEnabled()) {
                    final long submissionTime = System.currentTimeMillis() - submissionStartTime;
                    containingDocument.logDebug("submission", "total submission time",
                        "time", Long.toString(submissionTime));
                }
            }

        } else if (XFormsEvents.XFORMS_BINDING_EXCEPTION.equals(eventName)) {
            // The default action for this event results in the following: Fatal error.
            throw new ValidationException("Binding exception for target: " + event.getTargetObject().getEffectiveId(), event.getTargetObject().getLocationData());
        }
    }

    private ConnectionResult cachedSubmission(final PipelineContext pipelineContext, final ExternalContext externalContext, final ExternalContext.Request request,
                                              final SubmissionParameters p, final SecondPassParameters p2, final SerializationParameters sp) {
        // Get the instance from shared instance cache
        // This can only happen is method="get" and replace="instance" and xxforms:cache="true" or xxforms:shared="application"

        final URL absoluteResolvedURL = getResolvedSubmissionURL(pipelineContext, externalContext, request, sp.queryString, urlType);
        // Convert URL to string
        final String absoluteResolvedURLString = absoluteResolvedURL.toExternalForm();

        // TODO: preparing to move to other class
        final XFormsModelSubmission submission = this;

        // Find and check replacement location
        final XFormsInstance updatedInstance;
        {
            final NodeInfo destinationNodeInfo = submission.evaluateTargetRef(pipelineContext, submission.findReplaceInstanceNoTargetref(p.refInstance),
                    p.submissionElementContextItem, p.prefixToURIMap, p.contextStack, p.functionLibrary, p.functionContext);

            if (destinationNodeInfo == null) {
                // Throw target-error

                // XForms 1.1: "If the processing of the targetref attribute fails,
                // then submission processing ends after dispatching the event
                // xforms-submit-error with an error-type of target-error."

                throw new XFormsSubmissionException("targetref attribute doesn't point to an element for replace=\"instance\".", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
            }

            updatedInstance = submission.getContainingDocument().getInstanceForNode(destinationNodeInfo);
            if (updatedInstance == null || !updatedInstance.getInstanceRootElementInfo().isSameNodeInfo(destinationNodeInfo)) {
                // Only support replacing the root element of an instance
                // TODO: in the future, check on resolvedXXFormsReadonly to implement this restriction only when using a readonly instance
                throw new XFormsSubmissionException("targetref attribute must point to an instance root element when using cached/shared instance replacement.", "processing targetref attribute",
                        new XFormsSubmitErrorEvent(pipelineContext, submission, XFormsSubmitErrorEvent.ErrorType.TARGET_ERROR, null));
            }

            if (XFormsServer.logger.isDebugEnabled())
                submission.getContainingDocument().logDebug("submission", "using instance from application shared instance cache",
                        "instance", updatedInstance.getEffectiveId());
        }


        final long timeToLive = XFormsInstance.getTimeToLive(submission.getSubmissionElement());

        // Compute a hash of the body if possible
        final String requestBodyHash;
        if (sp.messageBody != null) {
            requestBodyHash = SecureUtils.digestBytes(sp.messageBody, "MD5", "hex"); 
        } else {
            requestBodyHash = null;
        }

        final XFormsInstance newInstance
                = XFormsServerSharedInstancesCache.instance().findConvert(pipelineContext, submission.getContainingDocument(), updatedInstance.getId(), updatedInstance.getEffectiveModelId(),
                    absoluteResolvedURLString, requestBodyHash, p2.resolvedXXFormsReadonly, p2.resolvedXXFormsHandleXInclude, timeToLive, updatedInstance.getValidation(), new XFormsServerSharedInstancesCache.Loader() {
                    public ReadonlyXFormsInstance load(PipelineContext pipelineContext, String instanceStaticId, String modelEffectiveId, String instanceSourceURI, boolean handleXInclude, long timeToLive, String validation) {

                        // Call regular submission
                        final ConnectionResult connectionResult = regularSubmission(pipelineContext, externalContext, request, p, p2, sp);
                        try {
                            // Handle connection errors
                            if (connectionResult.statusCode != 200) {
                                connectionResult.close();
                                throw new OXFException("Got invalid return code while loading instance from URI: " + instanceSourceURI + ", " + connectionResult.statusCode);
                            }
                            // Read into TinyTree
                            // TODO: Handle validating?
                            final DocumentInfo documentInfo = TransformerUtils.readTinyTree(connectionResult.getResponseInputStream(), connectionResult.resourceURI, handleXInclude);

                            // Create new shared instance
                            return new ReadonlyXFormsInstance(modelEffectiveId, instanceStaticId, documentInfo, instanceSourceURI,
                                    p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword, true, timeToLive, validation, handleXInclude);
                        } catch (Exception e) {
                            throw new OXFException("Got exception while loading instance from URI: " + instanceSourceURI, e);
                        } finally {
                            // Clean-up
                            connectionResult.close();
                        }
                    }
                });

        if (XFormsServer.logger.isDebugEnabled()) {
            submission.getContainingDocument().logDebug("submission", "replacing instance with " + (p2.resolvedXXFormsReadonly ? "read-only" : "read-write") +  " cached instance",
                        "instance", newInstance.getEffectiveId());
        }

        final XFormsModel replaceModel = newInstance.getModel(submission.getContainingDocument());

        // Dispatch xforms-delete event
        // NOTE: Do NOT dispatch so we are compatible with the regular root element replacement
        // (see below). In the future, we might want to dispatch this, especially if
        // XFormsInsertAction dispatches xforms-delete when removing the root element
        //updatedInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext, new XFormsDeleteEvent(updatedInstance, Collections.singletonList(destinationNodeInfo), 1));

        // Handle new instance and associated event markings
        final NodeInfo newRootElementInfo = newInstance.getInstanceRootElementInfo();
        replaceModel.handleUpdatedInstance(pipelineContext, newInstance, newRootElementInfo);

        // Dispatch xforms-insert event
        // NOTE: use the root node as insert location as it seems to make more sense than pointing to the earlier root element
        newInstance.getXBLContainer(containingDocument).dispatchEvent(pipelineContext,
                            new XFormsInsertEvent(newInstance, Collections.singletonList((Item) newRootElementInfo), null, newRootElementInfo.getDocumentRoot(),
                    "after", null, null, true));

        // Return minimal information to caller
        final ConnectionResult connectionResult = new ConnectionResult(absoluteResolvedURLString);
        connectionResult.dontHandleResponse = true;
        connectionResult.statusCode = 200;

        return connectionResult;
    }
    
    public class SubmissionParameters {

        // @replace attribute
        final boolean isReplaceAll = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_ALL);
        final boolean isReplaceInstance = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_INSTANCE);
        final boolean isReplaceText = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_TEXT);
        final boolean isReplaceNone = replace.equals(XFormsConstants.XFORMS_SUBMIT_REPLACE_NONE);

        // Current node for xforms:submission and instance containing the node to submit
        final NodeInfo refNodeInfo;
        final XFormsInstance refInstance;

        // Context stack
        final XFormsContextStack contextStack = model.getContextStack();
        
        final Item submissionElementContextItem;
    
        final XFormsFunction.Context functionContext;
        final boolean hasBoundRelevantUploadControl;
        
        final String resolvedMethod;
        final String actualHttpMethod;
        final String resolvedMediatype;
    
        final boolean resolvedValidate;
        final boolean resolvedRelevant;
        
        final boolean isHandlingOptimizedGet;

        // XPath function library and namespace mappings
        final FunctionLibrary functionLibrary = XFormsContainingDocument.getFunctionLibrary();
        final Map<String, String> prefixToURIMap = container.getNamespaceMappings(submissionElement);

        // XPath context
        final XPathCache.XPathContext xpathContext;
        
        final boolean isNoscript;
        final boolean isAllowDeferredSubmission;
    
        final boolean isPossibleDeferredSubmission;
        final boolean isDeferredSubmission;
        final boolean isDeferredSubmissionFirstPass;
        final boolean isDeferredSubmissionSecondPass;
        
        final boolean isDeferredSubmissionSecondPassReplaceAll;
        
        public SubmissionParameters(PipelineContext pipelineContext, String eventName) {
            contextStack.resetBindingContext(pipelineContext);
            
            contextStack.setBinding(pipelineContext, XFormsModelSubmission.this);
    
            refNodeInfo = contextStack.getCurrentSingleNode();
            functionContext = contextStack.getFunctionContext();
            submissionElementContextItem = contextStack.getContextItem();
    
            // Check that we have a current node and that it is pointing to a document or an element
            if (refNodeInfo == null)
                throw new XFormsSubmissionException("Empty single-node binding on xforms:submission for submission id: " + id, "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.NO_DATA, null));
    
            if (!(refNodeInfo instanceof DocumentInfo || refNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE)) {
                throw new XFormsSubmissionException("xforms:submission: single-node binding must refer to a document node or an element.", "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.NO_DATA, null));
            }
    
            // Current instance may be null if the document submitted is not part of an instance
            refInstance = contextStack.getCurrentInstance();
    
            // Determine if the instance to submit has one or more bound and relevant upload controls
            //
            // o we don't check if we are currently initializing the document because at that point the
            //   client cannot have any files to upload yet
            //
            // o we don't check if we have already processed the second pass of a submission during this
            //   request, because it means that upload controls have been already committed
            //
            // o we don't check if we are requested not to with an attribute
            //
            // o we only check for replace="instance|none" and if serialization must take place
        
            
            if (refInstance!= null && !containingDocument.isInitializing() && !containingDocument.isGotSubmissionSecondPass() && xxfFormsEnsureUploads && !isReplaceAll && serialize) {
                hasBoundRelevantUploadControl = XFormsSubmissionUtils.hasBoundRelevantUploadControls(containingDocument, refInstance);
            } else {
                hasBoundRelevantUploadControl = false;
            }
        
            // Evaluate early AVTs
            xpathContext = new XPathCache.XPathContext(prefixToURIMap, contextStack.getCurrentVariables(), functionLibrary, functionContext, null, getLocationData());
            
            {
                // Resolved method AVT
                final String resolvedMethodQName = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xpathContext, refNodeInfo , avtMethod);
                resolvedMethod = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractTextValueQName(prefixToURIMap, resolvedMethodQName, true));
        
                // Get actual method based on the method attribute
                actualHttpMethod = getActualHttpMethod(resolvedMethod);
        
                // Get mediatype
                resolvedMediatype = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xpathContext, refNodeInfo , avtMediatype);
        
                // Resolve validate and relevant AVTs
                final String resolvedValidateString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xpathContext, refNodeInfo , avtValidate);
                resolvedValidate = !"false".equals(resolvedValidateString);
        
                final String resolvedRelevantString = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, xpathContext, refNodeInfo , avtRelevant);
                resolvedRelevant = !"false".equals(resolvedRelevantString);
            }
        
            isHandlingOptimizedGet = XFormsProperties.isOptimizeGetAllSubmission(containingDocument) && actualHttpMethod.equals("GET")
                    && isReplaceAll
                    && (resolvedMediatype == null || !resolvedMediatype.startsWith(NetUtils.APPLICATION_SOAP_XML)) // can't let SOAP requests be handled by the browser
                    && avtXXFormsUsername == null // can't optimize if there are authentication credentials
                    && avtXXFormsTarget == null;  // can't optimize if there is a target
        
            // In noscript mode, or in "Ajax portlet" mode, there is no deferred submission process
            isNoscript = XFormsProperties.isNoscript(containingDocument);
            isAllowDeferredSubmission = !isNoscript && !XFormsProperties.isAjaxPortlet(containingDocument);
        
            isPossibleDeferredSubmission = (isReplaceAll && !isHandlingOptimizedGet) || (!isReplaceAll && serialize && hasBoundRelevantUploadControl);
            isDeferredSubmission = isAllowDeferredSubmission && isPossibleDeferredSubmission;
            isDeferredSubmissionFirstPass = isDeferredSubmission && XFormsEvents.XFORMS_SUBMIT.equals(eventName);
            isDeferredSubmissionSecondPass = isDeferredSubmission && !isDeferredSubmissionFirstPass; // here we get XXFORMS_SUBMIT
            
            isDeferredSubmissionSecondPassReplaceAll = isDeferredSubmissionSecondPass && isReplaceAll;
        }
    }

    public class SecondPassParameters {

        // This mostly consits of AVTs that can be evaluated only during the second pass of the submission

        final String resolvedSerialization;
        final String resolvedMode;
        final String resolvedVersion;
        final String resolvedEncoding;
        final String resolvedSeparator;
        final boolean resolvedIndent;
        final boolean resolvedOmitxmldeclaration;
        final Boolean resolvedStandalone;
        final String resolvedXXFormsUsername;
        final String resolvedXXFormsPassword;
        final boolean resolvedXXFormsReadonly;
        final boolean resolvedXXFormsCache;
        final boolean resolvedXXFormsHandleXInclude;

        final boolean isAsyncSubmission;

        public SecondPassParameters(PipelineContext pipelineContext, SubmissionParameters p) {
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtActionOrResource);
                if (temp == null) {
                    // This can be null if, e.g. you have an AVT like resource="{()}"
                    throw new XFormsSubmissionException("xforms:submission: mandatory resource or action evaluated to an empty sequence for attribute value: " + avtActionOrResource,
                            "resolving resource URI");
                }
                resolvedActionOrResource = XFormsUtils.encodeHRRI(temp, true);
            }

            resolvedSerialization = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtSerialization);
            resolvedMode = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtMode);
            resolvedVersion = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtVersion);
            resolvedEncoding = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtEncoding);
            resolvedSeparator = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtSeparator);

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtIndent);
                resolvedIndent = Boolean.valueOf(temp).booleanValue();
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtOmitxmldeclaration);
                resolvedOmitxmldeclaration = Boolean.valueOf(temp).booleanValue();
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtStandalone);
                resolvedStandalone = (temp != null) ? Boolean.valueOf(temp) : null;
            }

            resolvedXXFormsUsername = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsUsername);
            resolvedXXFormsPassword = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsPassword);
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsReadonly);
                resolvedXXFormsReadonly = (temp != null) ? Boolean.valueOf(temp) : false;
            }

            if (avtXXFormsCache != null) {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsCache);
                // New attribute
                resolvedXXFormsCache = Boolean.valueOf(temp).booleanValue();
            } else {
                // For backward compatibility
                resolvedXXFormsCache = "application".equals(XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsShared));
            }


            // Default is "false" for security reasons
            final String tempHandleXInclude = XFormsUtils.resolveAttributeValueTemplates(pipelineContext, p.xpathContext, p.refNodeInfo, avtXXFormsHandleXInclude);
            resolvedXXFormsHandleXInclude = Boolean.valueOf(tempHandleXInclude).booleanValue();

            // Check read-only and cache hints
            if (resolvedXXFormsCache) {
                if (!(p.actualHttpMethod.equals("GET") || p.actualHttpMethod.equals("POST") || p.actualHttpMethod.equals("PUT")))
                    throw new XFormsSubmissionException("xforms:submission: xxforms:cache=\"true\" or xxforms:shared=\"application\" can be set only with method=\"get|post|put\".",
                            "checking read-only and shared hints");
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException("xforms:submission: xxforms:cache=\"true\" or xxforms:shared=\"application\" can be set only with replace=\"instance\".",
                            "checking read-only and shared hints");
            } else if (resolvedXXFormsReadonly) {
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException("xforms:submission: xxforms:readonly=\"true\" can be \"true\" only with replace=\"instance\".",
                            "checking read-only and shared hints");
            }

            // Get async/sync
            // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
            isAsyncSubmission = p.isReplaceNone && "asynchronous".equals(resolvedMode);// for now we only support this with replace="none"
        }
    }

    public class SerializationParameters {
        final byte[] messageBody;// TODO: provide option for body to be a stream
        final String queryString;
        final String actualRequestMediatype;

        public SerializationParameters(PipelineContext pipelineContext, SubmissionParameters p, SecondPassParameters p2, String requestedSerialization, Document documentToSubmit, String overriddenSerializedData) throws Exception {
            if (serialize) {
                final String defaultMediatypeForSerialization;
                if (overriddenSerializedData != null && !overriddenSerializedData.equals("")) {
                    // Form author set data to serialize
                    if (p.actualHttpMethod.equals("POST") || p.actualHttpMethod.equals("PUT")) {
                        queryString = null;
                        messageBody = overriddenSerializedData.getBytes("UTF-8");
                        defaultMediatypeForSerialization = "application/xml";
                    } else {
                        queryString = URLEncoder.encode(overriddenSerializedData, "UTF-8");
                        messageBody = null;
                        defaultMediatypeForSerialization = null;
                    }
                } else if (requestedSerialization.equals("application/x-www-form-urlencoded")) {
                    // Perform "application/x-www-form-urlencoded" serialization
                    if (p.actualHttpMethod.equals("POST") || p.actualHttpMethod.equals("PUT")) {
                        queryString = null;
                        messageBody = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.resolvedSeparator).getBytes("UTF-8");// the resulting string is already ASCII in fact
                        defaultMediatypeForSerialization = "application/x-www-form-urlencoded";
                    } else {
                        queryString = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.resolvedSeparator);
                        messageBody = null;
                        defaultMediatypeForSerialization = null;
                    }
                } else if (requestedSerialization.equals("application/xml")) {
                    // Serialize XML to a stream of bytes
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                "xml", p2.resolvedVersion, null, null, p2.resolvedEncoding, p2.resolvedOmitxmldeclaration, p2.resolvedStandalone, p2.resolvedIndent, 4);

                        // TODO: use cdata-section-elements

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        messageBody = os.toByteArray();
                    } catch (Exception e) {
                        throw new XFormsSubmissionException(e, "xforms:submission: exception while serializing instance to XML.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = "application/xml";
                    queryString = null;
                } else if (requestedSerialization.equals("multipart/related")) {
                    // TODO
                    throw new XFormsSubmissionException("xforms:submission: submission serialization not yet implemented: " + requestedSerialization, "serializing instance");
                } else if (requestedSerialization.equals("multipart/form-data")) {
                    // Build multipart/form-data body

                    // Create and set body
                    final MultipartRequestEntity multipartFormData = XFormsSubmissionUtils.createMultipartFormData(pipelineContext, documentToSubmit);

                    final ByteArrayOutputStream os = new ByteArrayOutputStream();
                    multipartFormData.writeRequest(os);

                    messageBody = os.toByteArray();
                    queryString = null;

                    // The mediatype also contains the boundary
                    defaultMediatypeForSerialization = multipartFormData.getContentType();

                } else if (requestedSerialization.equals("application/octet-stream")) {
                    // Binary serialization
                    final String nodeType = InstanceData.getType(documentToSubmit.getRootElement());

                    if (XMLConstants.XS_ANYURI_EXPLODED_QNAME.equals(nodeType)) {
                        // Interpret node as anyURI
                        // TODO: PERFORMANCE: Must pass InputStream all the way to the submission instead of storing into byte[] in memory!
                        final String uri = documentToSubmit.getRootElement().getStringValue();
                        messageBody = NetUtils.uriToByteArray(uri);
                    } else if (XMLConstants.XS_BASE64BINARY_EXPLODED_QNAME.equals(nodeType)) {
                        // TODO
                        throw new XFormsSubmissionException("xforms:submission: binary serialization with base64Binary type is not yet implemented.", "serializing instance");
                    } else {
                        // TODO
                        throw new XFormsSubmissionException("xforms:submission: binary serialization without a type is not yet implemented.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = "application/octet-stream";
                    queryString = null;
                } else if (XMLUtils.isTextContentType(requestedSerialization)) {
                    // TODO: Text serialization
                    throw new XFormsSubmissionException("xforms:submission: text serialization is not yet implemented.", "serializing instance");
                } else {
                    throw new XFormsSubmissionException("xforms:submission: invalid submission serialization requested: " + requestedSerialization, "serializing instance");
                }

                // Actual request mediatype
                actualRequestMediatype = (p.resolvedMediatype == null) ? defaultMediatypeForSerialization : p.resolvedMediatype;
            } else {
                queryString = null;
                messageBody = null;
                actualRequestMediatype = null;
            }
        }
    }

    private ConnectionResult regularSubmission(PipelineContext pipelineContext, final ExternalContext externalContext, ExternalContext.Request request,
                                               final SubmissionParameters p, final SecondPassParameters p2, final SerializationParameters sp) {

        final URL absoluteResolvedURL = getResolvedSubmissionURL(pipelineContext, externalContext, request, sp.queryString, urlType);

        // Gather remaining information to process the request
        final String forwardSubmissionHeaders = XFormsProperties.getForwardSubmissionHeaders(containingDocument);

        // NOTE about headers forwarding: forward user-agent header for replace="all", since that *usually*
        // simulates a request from the browser! Useful in particular when the target URL renders XForms
        // in noscript mode, where some browser sniffing takes place for handling the <button> vs. <submit>
        // element.
        final String newForwardSubmissionHeaders = p.isReplaceAll ? forwardSubmissionHeaders + " user-agent" : forwardSubmissionHeaders;

        final IndentedLogger connectionLogger;
        final boolean logBody;
        if (logger.isDebugEnabled()) {
            // Create new indented logger just for the Connection object. This will log more stuff.
            connectionLogger = new IndentedLogger(logger, "XForms submission " + (p2.isAsyncSubmission ? "(asynchronous)" : "(synchronous)"),
                    containingDocument.getIndentedLogger().getLogIndentLevel());
            logBody = true;
        } else {
            // Use regular logger
            connectionLogger = containingDocument.getIndentedLogger();
            logBody = false;
        }

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(pipelineContext, p.contextStack);

        // Open connection
        if (p2.isAsyncSubmission) {

            // Pack call into a Runnable
            final Runnable runnable = new Runnable() {

                public void run() {

                    // Here we just want to run the submission and not touch the XFCD. Remember,
                    // we can't change XFCD because it may get out of the caches and not be picked
                    // up by further incoming Ajax requests.

                    // NOTE: If the submission was truly asynchronous, we should not touch
                    // ExternalContext either. But currently, since the submission actually runs
                    // at the end of a request, we do have access to ExternalContext, so we still
                    // use it.
                    new Connection().open(externalContext, connectionLogger, logBody,
                            p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                            sp.actualRequestMediatype, sp.messageBody,
                            customHeaderNameValues, newForwardSubmissionHeaders);

                    // NOTE: In this very basic level of support, we don't support
                    // xforms-submit-done / xforms-submit-error handlers

                    // TODO: Do something with result, e.g. log?
                    // final ConnectionResult connectionResult = ...
                }
            };

            // Tell XFCD that we have one more Runnable
            containingDocument.addAsynchronousSubmission(runnable);

            // Tell caller he doesn't need to do anything
            return null;
        } else {
            // Just run it now
            return new Connection().open(externalContext, connectionLogger, logBody,
                    p.actualHttpMethod, absoluteResolvedURL, p2.resolvedXXFormsUsername, p2.resolvedXXFormsPassword,
                    sp.actualRequestMediatype, sp.messageBody,
                    customHeaderNameValues, newForwardSubmissionHeaders);
        }
    }

    private URL getResolvedSubmissionURL(PipelineContext pipelineContext, ExternalContext externalContext, ExternalContext.Request request, String queryString, String urlType) {
        // Absolute URLs or absolute paths are allowed to a local servlet
        String resolvedURL;

        if (NetUtils.urlHasProtocol(resolvedActionOrResource) || fURLNorewrite) {
            // Don't touch the URL if it is absolute or if f:url-norewrite="true"
            resolvedURL = resolvedActionOrResource;
        } else {
            // Rewrite URL
            resolvedURL = XFormsUtils.resolveServiceURL(pipelineContext, submissionElement, resolvedActionOrResource,
                    ExternalContext.Response.REWRITE_MODE_ABSOLUTE_PATH_OR_RELATIVE);

            if (request.getContainerType().equals("portlet") && "resource".equals(urlType) && !NetUtils.urlHasProtocol(resolvedURL)) {
                // In this case, we have to prepend the complete server path
                resolvedURL = request.getScheme() + "://" + request.getServerName() + (request.getServerPort() > 0 ? ":" + request.getServerPort() : "") + resolvedURL;
            }
        }

        // Compute absolute submission URL
        return NetUtils.createAbsoluteURL(resolvedURL, queryString, externalContext);
    }

    private ConnectionResult optimizedSubmission(PipelineContext pipelineContext, SubmissionParameters p, SerializationParameters sp, ExternalContext externalContext) {
        // This is an "optimized" submission, i.e. one that does not use an actual protocol handler to
        // access the resource, but instead uses servlet forward/include for servlets, or a local
        // mechanism for portlets.

        // NOTE: Optimizing with include() for servlets doesn't allow detecting errors caused by the
        // included resource. [As of 2009-02-13, not sure if this is the case.]

        // NOTE: For portlets, paths are served directly by the portlet, NOT as resources.

        // f:url-norewrite="true" with an absolute path allows accessing other servlet contexts.

        // Current limitations:
        // o Portlets cannot access resources outside the portlet except by using absolute URLs (unless f:url-type="resource")

        // URI with xml:base resolution
        final URI resolvedURI = XFormsUtils.resolveXMLBase(submissionElement, resolvedActionOrResource);

        // NOTE: We don't want any changes to happen to the document upon xxforms-submit when producing
        // a new document so we don't dispatch xforms-submit-done and pass a null XFormsModelSubmission
        // in that case

        if (XFormsServer.logger.isDebugEnabled())
            containingDocument.logDebug("submission", "starting optimized submission", "id", getEffectiveId());

        // NOTE about headers forwarding: forward user-agent header for replace="all", since that *usually*
        // simulates a request from the browser! Useful in particular when the target URL renders XForms
        // in noscript mode, where some browser sniffing takes place for handling the <button> vs. <submit>
        // element.
        final String[] headersToForward = p.isReplaceAll ? XFormsSubmissionUtils.STANDARD_HEADERS_TO_FORWARD : XFormsSubmissionUtils.MINIMAL_HEADERS_TO_FORWARD;
        // TODO: Harmonize with HTTP submission handling of headers

        // Evaluate headers if any
        final Map<String, String[]> customHeaderNameValues = evaluateHeaders(pipelineContext, p.contextStack);

        final ConnectionResult connectionResult = XFormsSubmissionUtils.openOptimizedConnection(pipelineContext, externalContext, containingDocument,
                p.isDeferredSubmissionSecondPassReplaceAll ? null : this, p.actualHttpMethod, resolvedURI.toString(), fURLNorewrite, sp.actualRequestMediatype,
                sp.messageBody, sp.queryString, p.isReplaceAll, headersToForward, customHeaderNameValues);

        // This means we got a submission with replace="all"
        if (connectionResult.dontHandleResponse)
            containingDocument.setGotSubmissionReplaceAll();
        return connectionResult;
    }

    private ConnectionResult optimizedGetSubmission(PipelineContext pipelineContext, SerializationParameters sp) {
        final String actionString = (sp.queryString == null) ? resolvedActionOrResource : resolvedActionOrResource + ((resolvedActionOrResource.indexOf('?') == -1) ? "?" : "") + sp.queryString;
        final String resultURL = XFormsLoadAction.resolveLoadValue(containingDocument, pipelineContext, submissionElement, true, actionString, null, null, fURLNorewrite, xxfShowProgress);

        final ConnectionResult connectionResult = new ConnectionResult(resultURL);
        connectionResult.dontHandleResponse = true;
        return connectionResult;
    }

    private ConnectionResult testSubmission(SerializationParameters sp) throws IOException {
        if (sp.messageBody == null) {
            // Not sure when this can happen, but it can't be good
            throw new XFormsSubmissionException("Action 'test:': no message body.", "processing submission response");
        } else {
            // Log message body for debugging purposes
            //xxx TODO: complete logging
            if (XFormsServer.logger.isDebugEnabled())
                Connection.logRequestBody(containingDocument.getIndentedLogger(), sp.actualRequestMediatype, sp.messageBody);
        }

        // Do as if we are receiving a regular XML response
        final ConnectionResult connectionResult = new ConnectionResult(null);
        connectionResult.statusCode = 200;
        connectionResult.responseHeaders = ConnectionResult.EMPTY_HEADERS_MAP;
        connectionResult.setLastModified(null);
        connectionResult.setResponseContentType(XMLUtils.XML_CONTENT_TYPE);// should we use actualRequestMediatype instead?
        connectionResult.dontHandleResponse = false;
        connectionResult.setResponseInputStream(new ByteArrayInputStream(sp.messageBody));
        return connectionResult;
    }

    private XFormsInstance findReplaceInstanceNoTargetref(XFormsInstance refInstance) {
        final XFormsInstance replaceInstance;
        if (xxfReplaceInstanceId != null)
            replaceInstance = containingDocument.findInstance(xxfReplaceInstanceId);
        else if (replaceInstanceId != null)
            replaceInstance = model.getInstance(replaceInstanceId);
        else if (refInstance == null)
            replaceInstance = model.getDefaultInstance();
        else
            replaceInstance = refInstance;
        return replaceInstance;
    }

    private NodeInfo evaluateTargetRef(PipelineContext pipelineContext, XFormsInstance defaultReplaceInstance, Item submissionElementContextItem,
                                       Map<String, String> prefixToURIMap, XFormsContextStack contextStack, FunctionLibrary functionLibrary,
                                       XPathCache.FunctionContext functionContext) {
        final Object destinationObject;
        if (targetref == null) {
            // There is no explicit @targetref, so the target is implicity the root element of either the instance
            // pointed to by @ref, or the instance specified by @instance or @xxforms:instance.
            destinationObject = defaultReplaceInstance.getInstanceRootElementInfo();
        } else {
            // There is an explicit @targetref, which must be evaluated.

            // "The in-scope evaluation context of the submission element is used to evaluate the expression." BUT ALSO "The
            // evaluation context for this attribute is the in-scope evaluation context for the submission element, except
            // the context node is modified to be the document element of the instance identified by the instance attribute
            // if it is specified."
            final boolean hasInstanceAttribute = xxfReplaceInstanceId != null || replaceInstanceId != null;
            final Item targetRefContextItem = hasInstanceAttribute
                    ? defaultReplaceInstance.getInstanceRootElementInfo() : submissionElementContextItem;

            // Evaluate destination node
            // "This attribute is evaluated only once a successful submission response has been received and if the replace
            // attribute value is "instance" or "text". The first node rule is applied to the result."
            destinationObject = XPathCache.evaluateSingle(pipelineContext, targetRefContextItem, targetref, prefixToURIMap,
                    contextStack.getCurrentVariables(), functionLibrary, functionContext, null, getLocationData());
        }

        // TODO: Also detect readonly node/ancestor situation
        if (destinationObject instanceof NodeInfo && ((NodeInfo) destinationObject).getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE)
            return (NodeInfo) destinationObject;
        else
            return null;
    }

    /**
     * Check whether optimized submission is allowed, depending on a series of conditions.
     *
     * Log a lot of stuff for development, as it is not always obvious why we pick an optimized vs. regular submission.
     */
    private boolean isAllowOptimizedSubmission(boolean replaceAll, boolean isNoscript, ExternalContext.Request request, boolean asyncSubmission) {

        final boolean isDebugEnabled = XFormsServer.logger.isDebugEnabled();
        if (isDebugEnabled) {
            containingDocument.logDebug("submission", "checking whether optimized submission is allowed",
                "resource", resolvedActionOrResource, "noscript", Boolean.toString(isNoscript),
                "is ajax portlet", Boolean.toString(XFormsProperties.isAjaxPortlet(containingDocument)),
                "is asynchronous", Boolean.toString(asyncSubmission),
                "container type", request.getContainerType(), "norewrite", Boolean.toString(fURLNorewrite),
                "url type", urlType,
                "local-submission-forward", Boolean.toString(XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)),
                "local-submission-include", Boolean.toString(XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument))
            );
        }

        // Absolute URL is not optimized
        if (NetUtils.urlHasProtocol(resolvedActionOrResource)) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "resource URL has protocol", "resource", resolvedActionOrResource);
            return false;
        }

        // TODO: why is this condition here?
        if (isNoscript && !XFormsProperties.isAjaxPortlet(containingDocument)) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "noscript mode enabled and not in ajax portlet mode");
            return false;
        }

        // For now, we don't handle optimized async; could be optimized in the future
        if (asyncSubmission) {
            if (isDebugEnabled)
                containingDocument.logDebug("submission", "skipping optimized submission",
                        "reason", "asynchronous mode is not supported yet");
            return false;
        }

        if (request.getContainerType().equals("portlet")) {
            // Portlet

            if (fURLNorewrite) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "norewrite is specified");
                return false;
            }

            // NOTE: we could optimize for resource URLs:
            // o JSR-268 local resource handling, can access porlet resources the same way as with render/action
            // o In include mode, Servlet resources can be accessed using request dispatcher to servlet

            if ("resource".equals(urlType)) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "resource URL type is specified");
                return false;
            }
        } else if (replaceAll) {
            // Servlet, replace all
            if (!XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "forward submissions are disallowed in properties");
                return false;
            }
        } else {
            // Servlet, other
            if (!XFormsProperties.isOptimizeLocalSubmissionInclude(containingDocument)) {
                if (isDebugEnabled)
                    containingDocument.logDebug("submission", "skipping optimized submission",
                            "reason", "include submissions are disallowed in properties");
                return false;
            }
        }

        if (isDebugEnabled)
            containingDocument.logDebug("submission", "enabling optimized submission");

        return true;
    }

    /**
     * Evaluate the <xforms:header> elements children of <xforms:submission>.
     *
     * @param pipelineContext   pipeline context
     * @param contextStack      context stack set to enclosing <xforms:submission>
     * @return                  LinkedHashMap<String headerName, String[] headerValues>, or null if no header elements
     */
    private Map<String, String[]> evaluateHeaders(PipelineContext pipelineContext, XFormsContextStack contextStack) {
        final List<Element> headerElements = Dom4jUtils.elements(submissionElement, XFormsConstants.XFORMS_HEADER_QNAME);
        if (headerElements.size() > 0) {
            final Map<String, String[]> headerNameValues = new LinkedHashMap<String, String[]>();

            // Iterate over all <xforms:header> elements
            for (Element currentHeaderElement: headerElements) {
                contextStack.pushBinding(pipelineContext, currentHeaderElement);
                final XFormsContextStack.BindingContext currentHeaderBindingContext = contextStack.getCurrentBindingContext();
                if (currentHeaderBindingContext.isNewBind()) {
                    // This means there was @nodeset or @bind so we must iterate
                    final List<Item> currentNodeset = contextStack.getCurrentNodeset();
                    final int currentSize = currentNodeset.size();
                    if (currentSize > 0) {
                        // Push all iterations in turn
                        for (int position = 1; position <= currentSize; position++) {
                            contextStack.pushIteration(position);
                            handleHeaderElement(pipelineContext, contextStack, headerNameValues, currentHeaderElement);
                            contextStack.popBinding();
                        }
                    }
                } else {
                    // This means there is just a single header
                    handleHeaderElement(pipelineContext, contextStack, headerNameValues, currentHeaderElement);
                }
                contextStack.popBinding();
            }

            return headerNameValues;
        } else {
            return null;
        }
    }

    /**
     * Evaluate a single <xforms:header> element. It may have a node-set binding.
     *
     * @param pipelineContext       pipeline context
     * @param contextStack          context stack set to <xforms:header> (or element iteration)
     * @param headerNameValues      LinkedHashMap<String headerName, String[] headerValues> to update
     * @param currentHeaderElement  <xforms:header> element to evaluate
     */
    private void handleHeaderElement(PipelineContext pipelineContext, XFormsContextStack contextStack, Map<String, String[]> headerNameValues, Element currentHeaderElement) {
        final String headerName;
        {
            final Element headerNameElement = currentHeaderElement.element("name");
            if (headerNameElement == null)
                throw new XFormsSubmissionException("Missing <name> child element of <header> element", "processing <header> elements");

            contextStack.pushBinding(pipelineContext, headerNameElement);
            headerName = XFormsUtils.getElementValue(pipelineContext, containingDocument, contextStack, headerNameElement, false, null);
            contextStack.popBinding();
        }

        final String headerValue;
        {
            final Element headerValueElement = currentHeaderElement.element("value");
            if (headerValueElement == null)
                throw new XFormsSubmissionException("Missing <value> child element of <header> element", "processing <header> elements");
            contextStack.pushBinding(pipelineContext, headerValueElement);
            headerValue = XFormsUtils.getElementValue(pipelineContext, containingDocument, contextStack, headerValueElement, false, null);
            contextStack.popBinding();
        }

        StringUtils.addValueToStringArrayMap(headerNameValues, headerName, headerValue);
    }

    public void performTargetAction(PipelineContext pipelineContext, XBLContainer container, XFormsEvent event) {
        // NOP
    }

    private String getRequestedSerialization(String submissionSerializationAttribute, String submissionMethodAttribute) {
        final String actualSerialization;
        if (submissionSerializationAttribute == null) {
            if (submissionMethodAttribute.equals("multipart-post")) {
                actualSerialization = "multipart/related";
            } else if (submissionMethodAttribute.equals("form-data-post")) {
                actualSerialization = "multipart/form-data";
            } else if (submissionMethodAttribute.equals("urlencoded-post")) {
                actualSerialization = "application/x-www-form-urlencoded";
            } else if (XFormsSubmissionUtils.isPost(submissionMethodAttribute) || XFormsSubmissionUtils.isPut(submissionMethodAttribute)) {
                actualSerialization = "application/xml";
            } else if (XFormsSubmissionUtils.isGet(submissionMethodAttribute) || XFormsSubmissionUtils.isDelete(submissionMethodAttribute)) {
                actualSerialization = "application/x-www-form-urlencoded";
            } else {
                throw new XFormsSubmissionException("xforms:submission: invalid submission methodrequested: " + submissionMethodAttribute, "serializing instance");
            }
        } else {
            actualSerialization = submissionSerializationAttribute;
        }
        return actualSerialization;
    }

    private static String getActualHttpMethod(String submissionMethodAttribute) {
        final String actualMethod;
        if (XFormsSubmissionUtils.isPost(submissionMethodAttribute)) {
            actualMethod = "POST";
        } else if (XFormsSubmissionUtils.isGet(submissionMethodAttribute)) {
            actualMethod = "GET";
        } else if (XFormsSubmissionUtils.isPut(submissionMethodAttribute)) {
            actualMethod = "PUT";
        } else if (XFormsSubmissionUtils.isDelete(submissionMethodAttribute)) {
            actualMethod = "DELETE";
        } else {
            actualMethod = submissionMethodAttribute;
        }
        return actualMethod;
    }

    private Document createDocumentToSubmit(PipelineContext pipelineContext, NodeInfo currentNodeInfo, XFormsInstance currentInstance, XFormsModel modelForInstance, boolean resolvedValidate, boolean resolvedRelevant) {
        final Document documentToSubmit;

        // Revalidate instance
        // NOTE: We need to do this here so that bind/@type works correctly. XForms 1.1 seems to say that this
        // must be done after pruning, but then it is not clear how XML Schema validation would work then.
        if (modelForInstance != null)
            modelForInstance.doRevalidate(pipelineContext);

        // Get selected nodes (re-root and prune)
        documentToSubmit = reRootAndPrune(currentNodeInfo, resolvedRelevant);

        // Check that there are no validation errors
        // NOTE: If the instance is read-only, it can't have MIPs at the moment, and can't fail validation/requiredness, so we don't go through the process at all.
        final boolean instanceSatisfiesValidRequired
                = (currentInstance != null && currentInstance.isReadOnly())
                || !resolvedValidate
                || XFormsSubmissionUtils.isSatisfiesValidRequired(containingDocument, documentToSubmit, true, true, true);
        if (!instanceSatisfiesValidRequired) {
            if (logger.isDebugEnabled()) {
                final String documentString = TransformerUtils.tinyTreeToString(currentNodeInfo);
                containingDocument.logDebug("submission", "instance document or subset thereof cannot be submitted",
                        "document", documentString);
            }
            throw new XFormsSubmissionException("xforms:submission: instance to submit does not satisfy valid and/or required model item properties.",
                    "checking instance validity",
                    new XFormsSubmitErrorEvent(pipelineContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.VALIDATION_ERROR, null));
        }

        return documentToSubmit;
    }

    private Document reRootAndPrune(final NodeInfo currentNodeInfo, boolean resolvedRelevant) {

        final Document documentToSubmit;
        if (currentNodeInfo instanceof NodeWrapper) {
            final Node currentNode = (Node) ((NodeWrapper) currentNodeInfo).getUnderlyingNode();

            // "A node from the instance data is selected, based on attributes on the submission
            // element. The indicated node and all nodes for which it is an ancestor are considered for
            // the remainder of the submit process. "
            if (currentNode instanceof Element) {
                // Create subset of document
                documentToSubmit = Dom4jUtils.createDocumentCopyParentNamespaces((Element) currentNode);
            } else {
                // Use entire instance document
                documentToSubmit = Dom4jUtils.createDocumentCopyElement(currentNode.getDocument().getRootElement());
            }

            if (resolvedRelevant) {
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
                                // Check "relevant" MIP and remove non-relevant nodes
                                    if (!InstanceData.getInheritedRelevant(node))
                                        nodeToDetach[0] = node;
                            }
                        }
                    });
                    if (nodeToDetach[0] != null)
                        nodeToDetach[0].detach();

                } while (nodeToDetach[0] != null);
            }

            // TODO: handle includenamespaceprefixes
        } else {
            // Submitting read-only instance backed by TinyTree (no MIPs to check)
            if (currentNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE) {
                documentToSubmit = TransformerUtils.tinyTreeToDom4j2(currentNodeInfo);
            } else {
                documentToSubmit = TransformerUtils.tinyTreeToDom4j2(currentNodeInfo.getRoot());
            }
        }
        return documentToSubmit;
    }

    private class XFormsSubmissionException extends ValidationException {

        private XFormsSubmitErrorEvent submitErrorEvent;

        public XFormsSubmissionException(final String message, final String description, XFormsSubmitErrorEvent submitErrorEvent) {
            this(message, description);
            this.submitErrorEvent = submitErrorEvent;
        }

        public XFormsSubmissionException(final String message, final String description) {
            super(message, new ExtendedLocationData(XFormsModelSubmission.this.getLocationData(), description,
                    XFormsModelSubmission.this.getSubmissionElement()));
        }

        public XFormsSubmissionException(final Throwable e, final String message, final String description) {
            super(message, e, new ExtendedLocationData(XFormsModelSubmission.this.getLocationData(), description,
                    XFormsModelSubmission.this.getSubmissionElement()));
        }

        public XFormsSubmissionException(final Throwable e, final String message, final String description, XFormsSubmitErrorEvent submitErrorEvent) {
            this(e, message, description);
            this.submitErrorEvent = submitErrorEvent;
        }

        public XFormsSubmitErrorEvent getSubmitErrorEvent() {
            return submitErrorEvent;
        }
    }
}