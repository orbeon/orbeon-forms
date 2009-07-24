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

import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.log4j.Logger;
import org.dom4j.*;
import org.dom4j.io.DocumentSource;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitErrorEvent;
import org.orbeon.oxf.xforms.event.events.XFormsSubmitSerializeEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsSubmitEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsSubmitReplaceEvent;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.NodeWrapper;
import org.orbeon.saxon.functions.FunctionLibrary;
import org.orbeon.saxon.om.DocumentInfo;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import javax.xml.transform.Transformer;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
public class XFormsModelSubmission implements XFormsEventTarget, XFormsEventObserver {

	public final static Logger logger = LoggerFactory.createLogger(XFormsModelSubmission.class);

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;
    private final String id;
    private final XFormsModel model;
    private final Element submissionElement;
    private boolean submissionElementExtracted = false;

    private String avtActionOrResource; // required unless there is a nested xforms:resource element;
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

    // All the submission types in the order they must be checked
    private final Submission[] submissions;

    public XFormsModelSubmission(XBLContainer container, String id, Element submissionElement, XFormsModel model) {
        this.container = container;
        this.containingDocument = container.getContainingDocument();
        this.id = id;
        this.submissionElement = submissionElement;
        this.model = model;

        this.submissions = new Submission[] {
            new EchoSubmission(this),
            new OptimizedGetSubmission(this),
            new OptimizedSubmission(this),
            new CacheableSubmission(this),
            new RegularSubmission(this)
        };
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }


    public boolean isShowProgress() {
        return xxfShowProgress;
    }

    public boolean isURLNorewrite() {
        return fURLNorewrite;
    }

    public String getUrlType() {
        return urlType;
    }

    public String getReplace() {
        return replace;
    }

    public String getTargetref() {
        return targetref;
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
                throw new XFormsSubmissionException(this, "xforms:submission: action attribute or resource attribute is missing.",
                        "processing xforms:submission attributes");
            }

            avtMethod = submissionElement.attributeValue("method");
            avtValidate = submissionElement.attributeValue("validate");
            avtRelevant = submissionElement.attributeValue("relevant");

            avtSerialization = submissionElement.attributeValue("serialization");
            if (avtSerialization != null) {
                serialize = !avtSerialization.equals("none");
            } else {
                // For backward compatibility only, support @serialize if there is no @serialization attribute (was in early XForms 1.1 draft)
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

    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {
        final String eventName = event.getEventName();

        if (XFormsEvents.XXFORMS_SUBMIT_REPLACE.equals(eventName)) {

            // Custom event to process the response of asynchronous submissions

            final XXFormsSubmitReplaceEvent replaceEvent = (XXFormsSubmitReplaceEvent) event;

            // Big bag of initial runtime parameters
            final SubmissionParameters p = new SubmissionParameters(propertyContext, eventName);
            final SecondPassParameters p2 = new SecondPassParameters(propertyContext, p);
            final SubmissionResult submissionResult = replaceEvent.getSubmissionResult();
            handleSubmissionResult(propertyContext, p, p2, submissionResult);

        } else if (XFormsEvents.XFORMS_SUBMIT.equals(eventName) || XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None

            containingDocument.setGotSubmission();

            // Variables declared here as they are used in a catch/finally block
            SubmissionParameters p = null;
            String resolvedActionOrResource = null;
            final long submissionStartTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;

            // Make sure submission element info is extracted
            extractSubmissionElement();

            try {
                // Big bag of initial runtime parameters
                p = new SubmissionParameters(propertyContext, eventName);

                if (p.isDeferredSubmissionSecondPass)
                    containingDocument.setGotSubmissionSecondPass();

                // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
                // issue a warning
                {
                    final XFormsModelSubmission existingSubmission = containingDocument.getClientActiveSubmission();
                    if (p.isDeferredSubmission && existingSubmission != null) {
                        containingDocument.logWarning("submission", "another submission requiring a second pass already exists",
                                "existing submission", existingSubmission.getEffectiveId(),
                                "new submission", this.getEffectiveId());
                        return;
                    }
                }

                /* ***** Update data model ************************************************************************** */

                // "The data model is updated"
                final XFormsModel modelForInstance;
                if (p.refInstance != null) {
                    modelForInstance = p.refInstance.getModel(containingDocument);
                    {
                        // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
                        // the instance is not serialized and if no instance data is otherwise used for the submission,
                        // this seems however unneeded.

                        // TODO: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
                        modelForInstance.rebuildRecalculateIfNeeded(propertyContext);
                    }
                } else {
                    // Case where no instance was found
                    modelForInstance = null;
                }

                /* ***** Handle deferred submission ***************************************************************** */

                // Resolve the target AVT because XFormsServer requires it for deferred submission
                resolvedXXFormsTarget = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsTarget);

                // Deferred submission: end of the first pass
                if (p.isDeferredSubmissionFirstPass) {

                    // Create document to submit here because in case of error, an Ajax response will still be produced
                    if (serialize) {
                        createDocumentToSubmit(propertyContext, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant);
                    }

                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setClientActiveSubmission(this);
                    return;
                }

                /* ***** Submission second pass ********************************************************************* */

                // Compute parameters only needed during second pass
                final SecondPassParameters p2 = new SecondPassParameters(propertyContext, p);
                resolvedActionOrResource = p2.actionOrResource; // in case of exception

                /* ***** Serialization ****************************************************************************** */

                // Get serialization requested from @method and @serialization attributes
                final String requestedSerialization = getRequestedSerialization(p2.serialization, p.resolvedMethod);

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
                        XFormsUploadControl.handleFileElement(propertyContext, containingDocument, filesElement, null, true);
                    }

                    // Check if a submission requires file upload information
                    if (requestedSerialization.startsWith("multipart/")) {
                        // Annotate before re-rooting/pruning
                        XFormsSubmissionUtils.annotateBoundRelevantUploadControls(propertyContext, containingDocument, p.refInstance);
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(propertyContext, p.refNodeInfo, p.refInstance, modelForInstance, p.resolvedValidate, p.resolvedRelevant);

                } else {
                    // Don't recreate document
                    documentToSubmit = null;
                }

                final String overriddenSerializedData;
                if (serialize && !p.isDeferredSubmissionSecondPassReplaceAll) { // we don't want any changes to happen to the document upon xxforms-submit when producing a new document
                    // Fire xforms-submit-serialize

                    // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
                    // is changed from the initial value of empty string, then the content of the submission-body
                    // property string is used as the submission serialization. Otherwise, the submission serialization
                    // consists of a serialization of the selected instance data according to the rules stated at 11.9
                    // Submission Options."

                    final XFormsSubmitSerializeEvent serializeEvent = new XFormsSubmitSerializeEvent(XFormsModelSubmission.this, p.refNodeInfo, requestedSerialization);
                    container.dispatchEvent(propertyContext, serializeEvent);

                    // TODO: rest of submission should happen upon default action of event

                    overriddenSerializedData = serializeEvent.getSerializedData();
                } else {
                    overriddenSerializedData = null;
                }

                // Serialize
                final SerializationParameters sp = new SerializationParameters(propertyContext, p, p2,
                        requestedSerialization, documentToSubmit, overriddenSerializedData);

                /* ***** Execute submission ************************************************************************* */

                // Result information
                SubmissionResult submissionResult = null;
                final long externalSubmissionStartTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;

                try {
                    // Iterate through submissions and run the first match
                    for (Submission submission: submissions) {
                        if (submission.isMatch(propertyContext, p, p2, sp)) {
                            submissionResult = submission.connect(propertyContext, p, p2, sp);
                            break;
                        }
                    }

                    /* ***** Submission response ******************************************************************** */
                    handleSubmissionResult(propertyContext, p, p2, submissionResult);
                } finally {
                    // Log time spent in submission if needed
                    if (XFormsServer.logger.isDebugEnabled()) {
                        final long submissionTime = System.currentTimeMillis() - externalSubmissionStartTime;
                        containingDocument.logDebug("submission", "external submission time including handling returned body",
                            "time", Long.toString(submissionTime));
                    }
                }
            } catch (Throwable throwable) {
                /* ***** Handle errors ****************************************************************************** */
                if (p != null && p.isDeferredSubmissionSecondPassReplaceAll && XFormsProperties.isOptimizeLocalSubmissionForward(containingDocument)) {
                    // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                    throw new XFormsSubmissionException(this, throwable, "Error while processing xforms:submission", "processing submission");
                } else {
                    // Any exception will cause an error event to be dispatched
                    sendSubmitError(propertyContext, resolvedActionOrResource, throwable);
                }
            } finally {
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

    private void handleSubmissionResult(PropertyContext propertyContext, SubmissionParameters p, SecondPassParameters p2, SubmissionResult submissionResult) {
        if (submissionResult != null) { // nothing to do if it is null
            try {
                try {
                    // Process the different types of response
                    final Replacer replacer;
                    if (submissionResult.getReplacer() != null) {
                        // Replacer provided
                        replacer = submissionResult.getReplacer();
                    } else if (submissionResult.getThrowable() != null) {
                        // Propagate throwable, which might have come from a separate thread
                        sendSubmitError(propertyContext, submissionResult.getThrowable(), submissionResult);
                        replacer = null;
                    } else {
                        replacer = null;
                    }

                    // Perform replacement
                    if (replacer != null)
                        replacer.replace(propertyContext, submissionResult.getConnectionResult(), p, p2);

                } finally {
                    // Clean-up result
                    submissionResult.close();
                }
            } catch (Throwable throwable) {
                // Any exception will cause an error event to be dispatched
                sendSubmitError(propertyContext, throwable, submissionResult);
            }
        }
    }

    private void sendSubmitError(PropertyContext propertyContext, Throwable throwable, SubmissionResult submissionResult) {
        // Try to get error event from exception
        XFormsSubmitErrorEvent submitErrorEvent = null;
        if (throwable instanceof XFormsSubmissionException) {
            final XFormsSubmissionException submissionException = (XFormsSubmissionException) throwable;
            submitErrorEvent = submissionException.getSubmitErrorEvent();
        }

        // If no event obtained, create default event
        if (submitErrorEvent == null) {
            submitErrorEvent = new XFormsSubmitErrorEvent(propertyContext, XFormsModelSubmission.this,
                XFormsSubmitErrorEvent.ErrorType.XXFORMS_INTERNAL_ERROR, submissionResult.getConnectionResult());
        }

        // Dispatch event
        submitErrorEvent.setThrowable(throwable);
        container.dispatchEvent(propertyContext, submitErrorEvent);
    }

    private void sendSubmitError(PropertyContext propertyContext, String resolvedActionOrResource, Throwable throwable) {
        // Try to get error event from exception
        XFormsSubmitErrorEvent submitErrorEvent = null;
        if (throwable instanceof XFormsSubmissionException) {
            final XFormsSubmissionException submissionException = (XFormsSubmissionException) throwable;
            submitErrorEvent = submissionException.getSubmitErrorEvent();
        }

        // If no event obtained, create default event
        if (submitErrorEvent == null) {
            submitErrorEvent = new XFormsSubmitErrorEvent(XFormsModelSubmission.this, resolvedActionOrResource,
                XFormsSubmitErrorEvent.ErrorType.XXFORMS_INTERNAL_ERROR, 0);
        }

        // Dispatch event
        submitErrorEvent.setThrowable(throwable);
        container.dispatchEvent(propertyContext, submitErrorEvent);
    }

    public Replacer getReplacer(PropertyContext propertyContext, ConnectionResult connectionResult, SubmissionParameters p) throws IOException {

        // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission

        if (connectionResult != null && !connectionResult.dontHandleResponse) {
            // Handle response
            final Replacer replacer;
            if (connectionResult.statusCode >= 200 && connectionResult.statusCode < 300) {// accept any success code (in particular "201 Resource Created")
                // Successful response
                if (connectionResult.hasContent()) {
                    // There is a body

                    // Get replacer
                    if (p.isReplaceAll) {
                        replacer = new AllReplacer(this, containingDocument);
                    } else if (p.isReplaceInstance) {
                        replacer = new InstanceReplacer(this, containingDocument);
                    } else if (p.isReplaceText) {
                        replacer = new TextReplacer(this, containingDocument);
                    } else if (p.isReplaceNone) {
                        replacer = new NoneReplacer(this, containingDocument);
                    } else {
                        throw new XFormsSubmissionException(this, "xforms:submission: invalid replace attribute: " + replace, "processing instance replacement",
                                new XFormsSubmitErrorEvent(propertyContext, this, XFormsSubmitErrorEvent.ErrorType.XXFORMS_INTERNAL_ERROR, connectionResult));
                    }
                } else {
                    // There is no body, notify that processing is terminated
                    if (p.isReplaceInstance || p.isReplaceText) {
                        // XForms 1.1 says it is fine not to have a body, but in most cases you will want to know that
                        // no instance replacement took place
                        XFormsServer.logger.warn("XForms - submission - instance or text replacement did not take place upon successful response because no body was provided. Submission: "
                                + getEffectiveId());
                    }

                    // "For a success response not including a body, submission processing concludes after dispatching
                    // xforms-submit-done"
                    replacer = new NoneReplacer(this, containingDocument);
                }
            } else if (connectionResult.statusCode == 302 || connectionResult.statusCode == 301) {
                // Got a redirect

                // TODO: only for replace="all", right?

                replacer = new RedirectReplacer(this, containingDocument);

            } else {
                // Error code received
                throw new XFormsSubmissionException(this, "xforms:submission for submission id: " + id + ", error code received when submitting instance: " + connectionResult.statusCode, "processing submission response",
                        new XFormsSubmitErrorEvent(propertyContext, this, XFormsSubmitErrorEvent.ErrorType.RESOURCE_ERROR, connectionResult));
            }

            return replacer;
        } else {
            return null;
        }
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
        
        public SubmissionParameters(PropertyContext propertyContext, String eventName) {
            contextStack.resetBindingContext(propertyContext);
            
            contextStack.setBinding(propertyContext, XFormsModelSubmission.this);
    
            refNodeInfo = contextStack.getCurrentSingleNode();
            functionContext = contextStack.getFunctionContext();
            submissionElementContextItem = contextStack.getContextItem();
    
            // Check that we have a current node and that it is pointing to a document or an element
            if (refNodeInfo == null)
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "Empty single-node binding on xforms:submission for submission id: " + id, "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(propertyContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.NO_DATA, null));
    
            if (!(refNodeInfo instanceof DocumentInfo || refNodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE)) {
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: single-node binding must refer to a document node or an element.", "getting submission single-node binding",
                        new XFormsSubmitErrorEvent(propertyContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.NO_DATA, null));
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
                final String resolvedMethodQName = XFormsUtils.resolveAttributeValueTemplates(propertyContext, xpathContext, refNodeInfo , avtMethod);
                resolvedMethod = Dom4jUtils.qNameToExplodedQName(Dom4jUtils.extractTextValueQName(prefixToURIMap, resolvedMethodQName, true));
        
                // Get actual method based on the method attribute
                actualHttpMethod = getActualHttpMethod(resolvedMethod);
        
                // Get mediatype
                resolvedMediatype = XFormsUtils.resolveAttributeValueTemplates(propertyContext, xpathContext, refNodeInfo , avtMediatype);
        
                // Resolve validate and relevant AVTs
                final String resolvedValidateString = XFormsUtils.resolveAttributeValueTemplates(propertyContext, xpathContext, refNodeInfo , avtValidate);
                resolvedValidate = !"false".equals(resolvedValidateString);
        
                final String resolvedRelevantString = XFormsUtils.resolveAttributeValueTemplates(propertyContext, xpathContext, refNodeInfo , avtRelevant);
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

        // This mostly consists of AVTs that can be evaluated only during the second pass of the submission

        final String actionOrResource;
        final String serialization;
        final String mode;
        final String version;
        final String encoding;
        final String separator;
        final boolean indent;
        final boolean omitxmldeclaration;
        final Boolean standalone;
        final String username;
        final String password;
        final boolean isReadonly;
        final boolean isCache;
        final long timeToLive;
        final boolean isHandleXInclude;

        final boolean isAsynchronous;

        public SecondPassParameters(PropertyContext propertyContext, SubmissionParameters p) {
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtActionOrResource);
                if (temp == null) {
                    // This can be null if, e.g. you have an AVT like resource="{()}"
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: mandatory resource or action evaluated to an empty sequence for attribute value: " + avtActionOrResource,
                            "resolving resource URI");
                }
                actionOrResource = XFormsUtils.encodeHRRI(temp, true);
            }

            serialization = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtSerialization);
            mode = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtMode);
            version = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtVersion);
            encoding = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtEncoding);
            separator = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtSeparator);

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtIndent);
                indent = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtOmitxmldeclaration);
                omitxmldeclaration = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtStandalone);
                standalone = (temp != null) ? Boolean.valueOf(temp) : null;
            }

            username = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsUsername);
            password = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsPassword);
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsReadonly);
                isReadonly = (temp != null) ? Boolean.valueOf(temp) : false;
            }

            if (avtXXFormsCache != null) {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsCache);
                // New attribute
                isCache = Boolean.valueOf(temp);
            } else {
                // For backward compatibility
                isCache = "application".equals(XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsShared));
            }

            timeToLive = XFormsInstance.getTimeToLive(getSubmissionElement());

            // Default is "false" for security reasons
            final String tempHandleXInclude = XFormsUtils.resolveAttributeValueTemplates(propertyContext, p.xpathContext, p.refNodeInfo, avtXXFormsHandleXInclude);
            isHandleXInclude = Boolean.valueOf(tempHandleXInclude);

            // Check read-only and cache hints
            if (isCache) {
                if (!(p.actualHttpMethod.equals("GET") || p.actualHttpMethod.equals("POST") || p.actualHttpMethod.equals("PUT")))
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: xxforms:cache=\"true\" or xxforms:shared=\"application\" can be set only with method=\"get|post|put\".",
                            "checking read-only and shared hints");
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: xxforms:cache=\"true\" or xxforms:shared=\"application\" can be set only with replace=\"instance\".",
                            "checking read-only and shared hints");
            } else if (isReadonly) {
                if (!p.isReplaceInstance)
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: xxforms:readonly=\"true\" can be \"true\" only with replace=\"instance\".",
                            "checking read-only and shared hints");
            }

            // Get async/sync
            // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
            final boolean isRequestedAsynchronousMode = "asynchronous".equals(mode);
            isAsynchronous = !p.isReplaceAll && isRequestedAsynchronousMode;
            if (isRequestedAsynchronousMode && p.isReplaceAll) {
                // For now we don't support replace="all"
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: mode=\"asynchronous\" cannot be \"true\" with replace=\"all\".", "checking asynchronous mode");
            }
        }

        protected SecondPassParameters(SecondPassParameters other, boolean isAsynchronous, boolean isReadonly) {
            this.actionOrResource = other.actionOrResource;
            this.serialization = other.serialization;
            this.version = other.version;
            this.encoding = other.encoding;
            this.separator = other.separator;
            this.indent = other.indent;
            this.omitxmldeclaration = other.omitxmldeclaration;
            this.standalone = other.standalone;
            this.username = other.username;
            this.password = other.password;
            this.isCache = other.isCache;
            this.timeToLive = other.timeToLive;
            this.isHandleXInclude = other.isHandleXInclude;

            this.mode = isAsynchronous ? "asynchronous" : "synchronous";
            this.isAsynchronous = isAsynchronous;
            this.isReadonly = isReadonly;
        }

        public SecondPassParameters amend(boolean isAsynchronous, boolean isReadonly){
            return new SecondPassParameters(this, isAsynchronous, isReadonly);
        }
    }

    public class SerializationParameters {
        final byte[] messageBody;// TODO: provide option for body to be a stream
        final String queryString;
        final String actualRequestMediatype;

        public SerializationParameters(PropertyContext propertyContext, SubmissionParameters p, SecondPassParameters p2, String requestedSerialization, Document documentToSubmit, String overriddenSerializedData) throws Exception {
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
                        messageBody = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator).getBytes("UTF-8");// the resulting string is already ASCII in fact
                        defaultMediatypeForSerialization = "application/x-www-form-urlencoded";
                    } else {
                        queryString = XFormsSubmissionUtils.createWwwFormUrlEncoded(documentToSubmit, p2.separator);
                        messageBody = null;
                        defaultMediatypeForSerialization = null;
                    }
                } else if (requestedSerialization.equals("application/xml")) {
                    // Serialize XML to a stream of bytes
                    try {
                        final Transformer identity = TransformerUtils.getIdentityTransformer();
                        TransformerUtils.applyOutputProperties(identity,
                                "xml", p2.version, null, null, p2.encoding, p2.omitxmldeclaration, p2.standalone, p2.indent, 4);

                        // TODO: use cdata-section-elements

                        final ByteArrayOutputStream os = new ByteArrayOutputStream();
                        identity.transform(new DocumentSource(documentToSubmit), new StreamResult(os));
                        messageBody = os.toByteArray();
                    } catch (Exception e) {
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, e, "xforms:submission: exception while serializing instance to XML.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = "application/xml";
                    queryString = null;
                } else if (requestedSerialization.equals("multipart/related")) {
                    // TODO
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: submission serialization not yet implemented: " + requestedSerialization, "serializing instance");
                } else if (requestedSerialization.equals("multipart/form-data")) {
                    // Build multipart/form-data body

                    // Create and set body
                    // TODO: cast to PipelineContext
                    final MultipartRequestEntity multipartFormData = XFormsSubmissionUtils.createMultipartFormData((PipelineContext) propertyContext, documentToSubmit);

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
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: binary serialization with base64Binary type is not yet implemented.", "serializing instance");
                    } else {
                        // TODO
                        throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: binary serialization without a type is not yet implemented.", "serializing instance");
                    }
                    defaultMediatypeForSerialization = "application/octet-stream";
                    queryString = null;
                } else if (XMLUtils.isTextContentType(requestedSerialization)) {
                    // TODO: Text serialization
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: text serialization is not yet implemented.", "serializing instance");
                } else {
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xforms:submission: invalid submission serialization requested: " + requestedSerialization, "serializing instance");
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

    public XFormsInstance findReplaceInstanceNoTargetref(XFormsInstance refInstance) {
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

    public NodeInfo evaluateTargetRef(PropertyContext propertyContext, XPathCache.XPathContext xpathContext,
                                      XFormsInstance defaultReplaceInstance, Item submissionElementContextItem) {
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
            destinationObject = XPathCache.evaluateSingle(propertyContext, xpathContext, targetRefContextItem, targetref);
        }

        // TODO: Also detect readonly node/ancestor situation
        if (destinationObject instanceof NodeInfo && ((NodeInfo) destinationObject).getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE)
            return (NodeInfo) destinationObject;
        else
            return null;
    }

    public void performTargetAction(PropertyContext propertyContext, XBLContainer container, XFormsEvent event) {
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
                throw new XFormsSubmissionException(this, "xforms:submission: invalid submission methodrequested: " + submissionMethodAttribute, "serializing instance");
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

    private Document createDocumentToSubmit(PropertyContext propertyContext, NodeInfo currentNodeInfo, XFormsInstance currentInstance, XFormsModel modelForInstance, boolean resolvedValidate, boolean resolvedRelevant) {
        final Document documentToSubmit;

        // Revalidate instance
        // NOTE: We need to do this here so that bind/@type works correctly. XForms 1.1 seems to say that this
        // must be done after pruning, but then it is not clear how XML Schema validation would work then.
        if (modelForInstance != null)
            modelForInstance.doRevalidate(propertyContext);

        // Get selected nodes (re-root and prune)
        documentToSubmit = reRootAndPrune(currentNodeInfo, resolvedRelevant);

        // Check that there are no validation errors
        // NOTE: If the instance is read-only, it can't have MIPs at the moment, and can't fail validation/requiredness, so we don't go through the process at all.
        final boolean instanceSatisfiesValidRequired
                = (currentInstance != null && currentInstance.isReadOnly())
                || !resolvedValidate
                || XFormsSubmissionUtils.isSatisfiesValidRequired(containingDocument, documentToSubmit, true, true, true);
        if (!instanceSatisfiesValidRequired) {
            if (XFormsServer.logger.isDebugEnabled()) {
                final String documentString = TransformerUtils.tinyTreeToString(currentNodeInfo);
                containingDocument.logDebug("submission", "instance document or subset thereof cannot be submitted",
                        "document", documentString);
            }
            throw new XFormsSubmissionException(this, "xforms:submission: instance to submit does not satisfy valid and/or required model item properties.",
                    "checking instance validity",
                    new XFormsSubmitErrorEvent(propertyContext, XFormsModelSubmission.this, XFormsSubmitErrorEvent.ErrorType.VALIDATION_ERROR, null));
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

                        private void checkInstanceData(Node node) {
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
}
