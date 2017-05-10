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
package org.orbeon.oxf.xforms.submission;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.orbeon.dom.*;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.http.Credentials;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.model.Instance;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.model.XFormsInstance;
import org.orbeon.oxf.xforms.model.XFormsModel;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Represents an XForms model submission instance.
 *
 * TODO: Refactor handling of serialization to separate classes.
 */
public class XFormsModelSubmission extends XFormsModelSubmissionBase {

    public static final String LOGGING_CATEGORY = "submission";
	public final static Logger logger = LoggerFactory.createLogger(XFormsModelSubmission.class);

    public final org.orbeon.oxf.xforms.analysis.model.Submission staticSubmission;
    private final String id;
    private final Element submissionElement;

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;

    private final XFormsModel model;

    private String resolvedXXFormsTarget;

    // All the submission types in the order they must be checked
    private final Submission[] submissions;

    public XFormsModelSubmission(XBLContainer container, org.orbeon.oxf.xforms.analysis.model.Submission staticSubmission, XFormsModel model) {
        this.staticSubmission = staticSubmission;

        this.id = staticSubmission.staticId();
        this.submissionElement = staticSubmission.element();

        this.container = container;
        this.containingDocument = container.getContainingDocument();

        this.model = model;

        this.submissions = new Submission[] {
            new EchoSubmission(this),
            new ClientGetAllSubmission(this),
            new CacheableSubmission(this),
            new RequestDispatcherSubmission(this),
            new RegularSubmission(this)
        };
    }

    public XFormsContainingDocument containingDocument() {
        return containingDocument;
    }

    public Element getSubmissionElement() {
        return submissionElement;
    }

    public boolean isShowProgress() {
        return staticSubmission.xxfShowProgress();
    }

    public boolean isURLNorewrite() {
        return staticSubmission.fURLNorewrite();
    }

    public String getUrlType() {
        return staticSubmission.urlType();
    }

    // Only set for replace="all" at the end of he first pass of the submission
    public String getResolvedXXFormsTarget() {
        return resolvedXXFormsTarget;
    }

    public String getId() {
        return id;
    }

    public String getPrefixedId() {
        return XFormsUtils.getPrefixedId(getEffectiveId());
    }

    public Scope scope() {
        return staticSubmission.scope();
    }

    public String getEffectiveId() {
        return XFormsUtils.getRelatedEffectiveId(model.getEffectiveId(), getId());
    }

    public XBLContainer container() {
        return getModel().container();
    }

    public LocationData getLocationData() {
        return (LocationData) submissionElement.getData();
    }

    public XFormsEventObserver parentEventObserver() {
        return model;
    }

    public XFormsModel getModel() {
        return model;
    }

    public void performDefaultAction(XFormsEvent event) {
        final String eventName = event.name();

        if (XFormsEvents.XFORMS_SUBMIT.equals(eventName) || XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            doSubmit(event);
        } else if (XFormsEvents.XXFORMS_ACTION_ERROR.equals(eventName)) {
            final XXFormsActionErrorEvent ev = (XXFormsActionErrorEvent) event;
            XFormsError.handleNonFatalActionError(this, ev.throwable());
        }
    }

    private void doSubmit(XFormsEvent event) {
        containingDocument.setGotSubmission();

        final IndentedLogger indentedLogger = getIndentedLogger();

        // Variables declared here as they are used in a catch/finally block
        SubmissionParameters p = null;
        String resolvedActionOrResource = null;

        Runnable submitDoneOrErrorRunnable = null;
        try {
            try {
                // Big bag of initial runtime parameters
                p = new SubmissionParameters(this, event.name());

                if (indentedLogger.isDebugEnabled()) {
                    final String message = p.isDeferredSubmissionFirstPass() ? "submission first pass" : p.isDeferredSubmissionSecondPass() ? "submission second pass" : "submission";
                    indentedLogger.startHandleOperation("", message, "id", getEffectiveId());
                }

                // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
                // issue a warning
                {
                    final XFormsModelSubmission existingSubmission = containingDocument.getClientActiveSubmissionFirstPass();
                    if (p.isDeferredSubmission() && existingSubmission != null) {
                        indentedLogger.logWarning("", "another submission requiring a second pass already exists",
                                "existing submission", existingSubmission.getEffectiveId(),
                                "new submission", this.getEffectiveId());
                        return;
                    }
                }

                /* ***** Check for pending uploads ********************************************************************** */

                // We can do this first, because the check just depends on the controls, instance to submit, and pending
                // submissions if any. This does not depend on the actual state of the instance.
                if (p.serialize() && p.resolvedXXFormsUploads() && SubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, p.refInstanceOpt())) {
                    throw new XFormsSubmissionException(this, "xf:submission: instance to submit has at least one pending upload.",
                        "checking pending uploads",
                        new XFormsSubmitErrorEvent(XFormsModelSubmission.this, XFormsSubmitErrorEvent.XXFORMS_PENDING_UPLOADS(), null));
                }

                /* ***** Update data model ****************************************************************************** */

                final RelevanceHandling relevanceHandling = RelevanceHandling.withNameAdjustForTrueAndFalse(p.resolvedRelevant());

                // "The data model is updated"
                if (p.refInstanceOpt().isDefined()) {
                    final XFormsModel modelForInstance = p.refInstanceOpt().get().model();
                    // NOTE: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
                    // Here, we rebuild/recalculate instead the model containing the submission's single-node binding.
                    // This can be different than the model containing the submission if using e.g. xxf:instance().

                    // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
                    // the instance is not serialized and if no instance data is otherwise used for the submission,
                    // this seems however unneeded so we optimize out.
                    if (p.resolvedValidate() || ! relevanceHandling.equals(RelevanceHandling.Keep$.MODULE$)  || p.resolvedXXFormsCalculate()) {
                        // Rebuild impacts validation, relevance and calculated values (set by recalculate)
                        modelForInstance.doRebuild();
                    }
                    if (! relevanceHandling.equals(RelevanceHandling.Keep$.MODULE$) || p.resolvedXXFormsCalculate()) {
                        // Recalculate impacts relevance and calculated values
                        modelForInstance.doRecalculateRevalidate();
                    }
                }

                /* ***** Handle deferred submission ********************************************************************* */


                // Deferred submission: end of the first pass
                if (p.isDeferredSubmissionFirstPass()) {

                    // Create (but abandon) document to submit here because in case of error, an Ajax response will still be produced
                    if (p.serialize()) {
                        createDocumentToSubmit(
                            p.refNodeInfo(),
                            p.refInstanceOpt(),
                            p.resolvedValidate(),
                            relevanceHandling,
                            p.resolvedXXFormsAnnotate(),
                            indentedLogger
                        );
                    }

                    // Resolve the target AVT because XFormsServer requires it for deferred submission
                    resolvedXXFormsTarget =
                        XFormsUtils.resolveAttributeValueTemplates(
                            containingDocument,
                            p.xpathContext(),
                            p.refNodeInfo(),
                            staticSubmission.avtXXFormsTarget()
                        );

                    // When replace="all", we wait for the submission of an XXFormsSubmissionEvent from the client
                    containingDocument.setActiveSubmissionFirstPass(this);
                    return;
                }

                /* ***** Submission second pass ************************************************************************* */

                // Compute parameters only needed during second pass
                final SecondPassParameters p2 = new SecondPassParameters(p);
                resolvedActionOrResource = p2.actionOrResource; // in case of exception

                /* ***** Serialization ********************************************************************************** */

                // Get serialization requested from @method and @serialization attributes
                final String requestedSerialization = getRequestedSerializationOrNull(p.serialization(), p.resolvedMethod());
                if (requestedSerialization == null)
                    throw new XFormsSubmissionException(this, "xf:submission: invalid submission method requested: " + p.resolvedMethod(), "serializing instance");

                final Document documentToSubmit;
                if (p.serialize()) {

                    // Check if a submission requires file upload information
                    if (requestedSerialization.startsWith("multipart/") && p.refInstanceOpt().isDefined()) {
                        // Annotate before re-rooting/pruning
                        XFormsSubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, p.refInstanceOpt().get());
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(
                        p.refNodeInfo(),
                        p.refInstanceOpt(),
                        p.resolvedValidate(),
                        relevanceHandling,
                        p.resolvedXXFormsAnnotate(),
                        indentedLogger
                    );

                } else {
                    // Don't recreate document
                    documentToSubmit = null;
                }

                final String overriddenSerializedData;
                if (!p.isDeferredSubmissionSecondPass()) {
                    if (p.serialize()) {
                        // Fire xforms-submit-serialize

                        // "The event xforms-submit-serialize is dispatched. If the submission-body property of the event
                        // is changed from the initial value of empty string, then the content of the submission-body
                        // property string is used as the submission serialization. Otherwise, the submission serialization
                        // consists of a serialization of the selected instance data according to the rules stated at 11.9
                        // Submission Options."

                        final XFormsSubmitSerializeEvent serializeEvent = new XFormsSubmitSerializeEvent(XFormsModelSubmission.this, p.refNodeInfo(), requestedSerialization);
                        Dispatch.dispatchEvent(serializeEvent);

                        // TODO: rest of submission should happen upon default action of event

                        overriddenSerializedData = serializeEvent.submissionBodyAsString();
                    } else {
                        overriddenSerializedData = null;
                    }
                } else {
                    // Two reasons: 1. We don't want to modify the document state 2. This can be called outside of the document
                    // lock, see XFormsServer.
                    overriddenSerializedData = null;
                }

                // Serialize
                final SerializationParameters sp =
                    SerializationParameters.apply(this, p, p2, requestedSerialization, documentToSubmit, overriddenSerializedData);

                /* ***** Submission connection ************************************************************************** */

                // Result information
                SubmissionResult submissionResult = null;

                // Iterate through submissions and run the first match
                for (final Submission submission : submissions) {
                    if (submission.isMatch(p, p2, sp)) {
                        if (indentedLogger.isDebugEnabled())
                            indentedLogger.startHandleOperation("", "connecting", "type", submission.getType());
                        try {
                            submissionResult = submission.connect(p, p2, sp);
                            break;
                        } finally {
                            if (indentedLogger.isDebugEnabled())
                                indentedLogger.endHandleOperation();
                        }
                    }
                }

                /* ***** Submission result processing ******************************************************************* */

                // NOTE: handleSubmissionResult() catches Throwable and returns a Runnable
                if (submissionResult != null)// submissionResult is null in case the submission is running asynchronously, AND when ???
                    submitDoneOrErrorRunnable = handleSubmissionResult(p, p2, submissionResult, true); // true because function context might have changed

            } catch (final Throwable throwable) {
                /* ***** Handle errors ********************************************************************************** */
                final SubmissionParameters pVal = p;
                final String resolvedActionOrResourceVal = resolvedActionOrResource;
                submitDoneOrErrorRunnable = new Runnable() {
                    public void run() {
                        if (pVal != null && pVal.isDeferredSubmissionSecondPass() && containingDocument.isLocalSubmissionForward()) {
                            // It doesn't serve any purpose here to dispatch an event, so we just propagate the exception
                            throw new XFormsSubmissionException(XFormsModelSubmission.this, throwable, "Error while processing xf:submission", "processing submission");
                        } else {
                            // Any exception will cause an error event to be dispatched
                            sendSubmitError(throwable, resolvedActionOrResourceVal);
                        }
                    }
                };
            }
        } finally {
            // Log total time spent in submission
            if (p != null && indentedLogger.isDebugEnabled()) {
                indentedLogger.endHandleOperation();
            }
        }

        // Execute post-submission code if any
        // This typically dispatches xforms-submit-done/xforms-submit-error, or may throw another exception
        if (submitDoneOrErrorRunnable != null) {
            // We do this outside the above catch block so that if a problem occurs during dispatching xforms-submit-done
            // or xforms-submit-error we don't dispatch xforms-submit-error (which would be illegal).
            // This will also close the connection result if needed.
            submitDoneOrErrorRunnable.run();
        }
    }

    /*
     * Process the response of an asynchronous submission.
     */
    public void doSubmitReplace(SubmissionResult submissionResult) {

        assert submissionResult != null;

        // Big bag of initial runtime parameters
        final SubmissionParameters p = new SubmissionParameters(this, null);
        final SecondPassParameters p2 = new SecondPassParameters(p);

        final Runnable submitDoneRunnable = handleSubmissionResult(p, p2, submissionResult, false);

        // Execute submit done runnable if any
        if (submitDoneRunnable != null) {
            // Do this outside the handleSubmissionResult catch block so that if a problem occurs during dispatching
            // xforms-submit-done we don't dispatch xforms-submit-error (which would be illegal)
            submitDoneRunnable.run();
        }
    }

    private Runnable handleSubmissionResult(SubmissionParameters p, SecondPassParameters p2, final SubmissionResult submissionResult, boolean initializeXPathContext) {

        assert p != null;
        assert p2 != null;
        assert submissionResult != null;

        Runnable submitDoneOrErrorRunnable = null;
        try {
            final IndentedLogger indentedLogger = getIndentedLogger();
            if (indentedLogger.isDebugEnabled())
                indentedLogger.startHandleOperation("", "handling result");
            try {
                // Get fresh XPath context if requested
                if (initializeXPathContext)
                    p.initializeXPathContext(XFormsModelSubmission.this);
                // Process the different types of response
                if (submissionResult.getThrowable() != null) {
                    // Propagate throwable, which might have come from a separate thread
                    submitDoneOrErrorRunnable = new Runnable() {
                        public void run() { sendSubmitError(submissionResult.getThrowable(), submissionResult); }
                    };
                } else {
                    // Replacer provided, perform replacement
                    assert submissionResult.getReplacer() != null;
                    submitDoneOrErrorRunnable = submissionResult.getReplacer().replace(submissionResult.getConnectionResult(), p, p2);
                }
            } finally {
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.endHandleOperation();
            }
        } catch (final Throwable throwable) {
            // Any exception will cause an error event to be dispatched
            submitDoneOrErrorRunnable = new Runnable() {
                public void run() { sendSubmitError(throwable, submissionResult); }
            };
        }

        // Create wrapping runnable to make sure the submission result is closed
        final Runnable finalSubmitDoneOrErrorRunnable = submitDoneOrErrorRunnable;
        return new Runnable() {
            public void run() {
                try {
                    if (finalSubmitDoneOrErrorRunnable != null)
                        finalSubmitDoneOrErrorRunnable.run();
                } finally {
                    // Close only after the submission result has run
                    submissionResult.close();
                }
            }
        };
    }

    /**
     * Run the given submission callable. This must be a callable for a replace="all" submission.
     *
     * @param callable          callable run
     * @param response          response to write to if needed
     */
    public static void runDeferredSubmission(Callable<SubmissionResult> callable, ExternalContext.Response response) {
        // Run submission
        try {
            final SubmissionResult result = callable.call();
            if (result != null) {
                // Callable did not do all the work, completed it here
                try {
                    if (result.getReplacer() != null) {
                        // Replacer provided, perform replacement
                        if (result.getReplacer() instanceof AllReplacer)
                            AllReplacer.forwardResultToResponse(result.getConnectionResult(), response);
                        else if (result.getReplacer() instanceof RedirectReplacer)
                            RedirectReplacer.replace(result.getConnectionResult(), response);
                        else
                            assert result.getReplacer() instanceof NoneReplacer;
                    } else if (result.getThrowable() != null) {
                        // Propagate throwable, which might have come from a separate thread
                        throw new OXFException(result.getThrowable());
                    } else {
                        // Should not happen
                    }
                } finally {
                    result.close();
                }
            }
        } catch (Exception e) {
            // Something bad happened
            throw new OXFException(e);
        }
    }

    public Runnable sendSubmitDone(final ConnectionResult connectionResult) {
        return new Runnable() {
            public void run() {
                // After a submission, the context might have changed
                model.resetAndEvaluateVariables();

                Dispatch.dispatchEvent(new XFormsSubmitDoneEvent(XFormsModelSubmission.this, connectionResult));
            }
        };
    }

    public Replacer getReplacer(ConnectionResult connectionResult, SubmissionParameters p) throws IOException {

        // NOTE: This can be called from other threads so it must NOT modify the XFCD or submission

        if (connectionResult != null) {
            // Handle response
            final Replacer replacer;
            if (connectionResult.dontHandleResponse()) {
                // Always return a replacer even if it does nothing, this way we don't have to deal with null
                replacer = new NoneReplacer(this, containingDocument);
            } else if (NetUtils.isSuccessCode(connectionResult.statusCode())) {
                // Successful response
                if (connectionResult.hasContent()) {
                    // There is a body

                    // Get replacer
                    if (p.isReplaceAll()) {
                        replacer = new AllReplacer(this, containingDocument);
                    } else if (p.isReplaceInstance()) {
                        replacer = new InstanceReplacer(this, containingDocument);
                    } else if (p.isReplaceText()) {
                        replacer = new TextReplacer(this, containingDocument);
                    } else if (p.isReplaceNone()) {
                        replacer = new NoneReplacer(this, containingDocument);
                    } else {
                        throw new XFormsSubmissionException(this, "xf:submission: invalid replace attribute: " + p.resolvedReplace(), "processing instance replacement",
                                new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.XXFORMS_INTERNAL_ERROR(), connectionResult));
                    }
                } else {
                    // There is no body, notify that processing is terminated
                    if (p.isReplaceInstance() || p.isReplaceText()) {
                        // XForms 1.1 says it is fine not to have a body, but in most cases you will want to know that
                        // no instance replacement took place
                        final IndentedLogger indentedLogger = getIndentedLogger();
                        indentedLogger.logWarning("", "instance or text replacement did not take place upon successful response because no body was provided.",
                                "submission id", getEffectiveId());
                    }

                    // "For a success response not including a body, submission processing concludes after dispatching
                    // xforms-submit-done"
                    replacer = new NoneReplacer(this, containingDocument);
                }
            } else if (NetUtils.isRedirectCode(connectionResult.statusCode())) {
                // Got a redirect

                // Currently we don't know how to handle a redirect for replace != "all"
                if (!p.isReplaceAll())
                    throw new XFormsSubmissionException(this, "xf:submission for submission id: " + id + ", redirect code received with replace=\"" + p.resolvedReplace() + "\"", "processing submission response",
                            new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.RESOURCE_ERROR(), connectionResult));

                replacer = new RedirectReplacer(this, containingDocument);

            } else {
                // Error code received
                throw new XFormsSubmissionException(this, "xf:submission for submission id: " + id + ", error code received when submitting instance: " + connectionResult.statusCode(), "processing submission response",
                        new XFormsSubmitErrorEvent(this, XFormsSubmitErrorEvent.RESOURCE_ERROR(), connectionResult));
            }

            return replacer;
        } else {
            return null;
        }
    }

    public class SecondPassParameters {

        // This mostly consists of AVTs that can be evaluated only during the second pass of the submission

        final String actionOrResource;
        final String mode;
        final String version;
        final String encoding;
        final String separator;
        final boolean indent;
        final boolean omitxmldeclaration;
        final Boolean standalone;
        final Credentials credentials;
        final boolean isReadonly;
        final boolean applyDefaults;
        final boolean isCache;
        final long timeToLive;
        final boolean isHandleXInclude;

        final boolean isAsynchronous;

        public SecondPassParameters(SubmissionParameters p) {
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtActionOrResource());
                if (temp == null) {
                    // This can be null if, e.g. you have an AVT like resource="{()}"
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: mandatory resource or action evaluated to an empty sequence for attribute value: " + staticSubmission.avtActionOrResource(),
                            "resolving resource URI");
                }
                actionOrResource = NetUtils.encodeHRRI(temp, true);
                // TODO: see if we can resolve xml:base early to detect absolute URLs early as well
//                actionOrResource = XFormsUtils.resolveXMLBase(containingDocument, getSubmissionElement(), NetUtils.encodeHRRI(temp, true)).toString();
            }

            mode = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtMode());
            version = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtVersion());
            separator = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtSeparator());

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtEncoding());
                encoding = (temp != null) ? temp : "UTF-8";
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtIndent());
                indent = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtOmitxmldeclaration());
                omitxmldeclaration = Boolean.valueOf(temp);
            }
            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtStandalone());
                standalone = (temp != null) ? Boolean.valueOf(temp) : null;
            }

            final String username = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsUsername());
            final String password = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsPassword());
            final String preemptiveAuth = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsPreemptiveAuth());
            final String domain = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsDomain());

            if (StringUtils.isEmpty(username))
                credentials = null;
            else
                credentials = Credentials.apply(username, password, preemptiveAuth, domain);

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsReadonly());
                isReadonly = (temp != null) ? Boolean.valueOf(temp) : false;
            }

            {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsDefaults());
                applyDefaults = (temp != null) ? Boolean.valueOf(temp) : false;
            }

            if (staticSubmission.avtXXFormsCache() != null) {
                final String temp = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsCache());
                // New attribute
                isCache = Boolean.valueOf(temp);
            } else {
                // For backward compatibility
                isCache = "application".equals(XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsShared()));
            }

            timeToLive = Instance.timeToLiveOrDefault(getSubmissionElement());

            // Default is "false" for security reasons
            final String tempHandleXInclude = XFormsUtils.resolveAttributeValueTemplates(containingDocument,  p.xpathContext(), p.refNodeInfo(), staticSubmission.avtXXFormsHandleXInclude());
            isHandleXInclude = Boolean.valueOf(tempHandleXInclude);

            // Check read-only and cache hints
            if (isCache) {
                if (!(p.actualHttpMethod().equals("GET") || p.actualHttpMethod().equals("POST") || p.actualHttpMethod().equals("PUT")))
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:cache=\"true\" or xxf:shared=\"application\" can be set only with method=\"get|post|put\".",
                            "checking read-only and shared hints");
                if (!p.isReplaceInstance())
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:cache=\"true\" or xxf:shared=\"application\" can be set only with replace=\"instance\".",
                            "checking read-only and shared hints");
            } else if (isReadonly) {
                if (!p.isReplaceInstance())
                    throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: xxf:readonly=\"true\" can be \"true\" only with replace=\"instance\".",
                            "checking read-only and shared hints");
            }

            // Get async/sync
            // NOTE: XForms 1.1 default to async, but we don't fully support async so we default to sync instead
            final boolean isRequestedAsynchronousMode = "asynchronous".equals(mode);
            isAsynchronous = !p.isReplaceAll() && isRequestedAsynchronousMode;
            if (isRequestedAsynchronousMode && p.isReplaceAll()) {
                // For now we don't support replace="all"
                throw new XFormsSubmissionException(XFormsModelSubmission.this, "xf:submission: mode=\"asynchronous\" cannot be \"true\" with replace=\"all\".", "checking asynchronous mode");
            }
        }

        protected SecondPassParameters(SecondPassParameters other, boolean isAsynchronous, boolean isReadonly) {
            this.actionOrResource = other.actionOrResource;
            this.version = other.version;
            this.encoding = other.encoding;
            this.separator = other.separator;
            this.indent = other.indent;
            this.omitxmldeclaration = other.omitxmldeclaration;
            this.standalone = other.standalone;
            this.credentials = other.credentials;
            this.isCache = other.isCache;
            this.timeToLive = other.timeToLive;
            this.isHandleXInclude = other.isHandleXInclude;

            this.mode = isAsynchronous ? "asynchronous" : "synchronous";
            this.isAsynchronous = isAsynchronous;
            this.isReadonly = isReadonly;
            this.applyDefaults = other.applyDefaults;
        }

        public SecondPassParameters amend(boolean isAsynchronous, boolean isReadonly){
            return new SecondPassParameters(this, isAsynchronous, isReadonly);
        }
    }

    public XFormsInstance findReplaceInstanceNoTargetref(scala.Option<XFormsInstance> refInstance) {
        final XFormsInstance replaceInstance;
        if (staticSubmission.xxfReplaceInstanceId() != null)
            replaceInstance = container.findInstanceOrNull(staticSubmission.xxfReplaceInstanceId());
        else if (staticSubmission.replaceInstanceId() != null)
            replaceInstance = model.getInstance(staticSubmission.replaceInstanceId());
        else if (refInstance.isEmpty())
            replaceInstance = model.getDefaultInstance();
        else
            replaceInstance = refInstance.get();
        return replaceInstance;
    }

    public NodeInfo evaluateTargetRef(XPathCache.XPathContext xpathContext,
                                      XFormsInstance defaultReplaceInstance, Item submissionElementContextItem) {
        final Object destinationObject;
        if (staticSubmission.targetref().isEmpty()) {
            // There is no explicit @targetref, so the target is implicitly the root element of either the instance
            // pointed to by @ref, or the instance specified by @instance or @xxf:instance.
            destinationObject = defaultReplaceInstance.rootElement();
        } else {
            // There is an explicit @targetref, which must be evaluated.

            // "The in-scope evaluation context of the submission element is used to evaluate the expression." BUT ALSO "The
            // evaluation context for this attribute is the in-scope evaluation context for the submission element, except
            // the context node is modified to be the document element of the instance identified by the instance attribute
            // if it is specified."
            final boolean hasInstanceAttribute = staticSubmission.xxfReplaceInstanceId() != null || staticSubmission.replaceInstanceId() != null;
            final Item targetRefContextItem = hasInstanceAttribute
                    ? defaultReplaceInstance.rootElement() : submissionElementContextItem;

            // Evaluate destination node
            // "This attribute is evaluated only once a successful submission response has been received and if the replace
            // attribute value is "instance" or "text". The first node rule is applied to the result."
            destinationObject = XPathCache.evaluateSingleWithContext(xpathContext, targetRefContextItem, staticSubmission.targetref().get(), containingDocument().getRequestStats().getReporter());
        }

        // TODO: Also detect readonly node/ancestor situation
        if (destinationObject instanceof NodeInfo && ((NodeInfo) destinationObject).getNodeKind() == org.w3c.dom.Node.ELEMENT_NODE)
            return (NodeInfo) destinationObject;
        else
            return null;
    }

    public void performTargetAction(XFormsEvent event) {
        // NOP
    }

    public IndentedLogger getIndentedLogger() {
        return containingDocument.getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
    }

    public IndentedLogger getDetailsLogger(final SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        return getNewLogger(p, p2, getIndentedLogger(), isLogDetails());
    }

    public IndentedLogger getTimingLogger(final SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2) {
        final IndentedLogger indentedLogger = getIndentedLogger();
        return getNewLogger(p, p2, indentedLogger, indentedLogger.isDebugEnabled());
    }

    private static IndentedLogger getNewLogger(final SubmissionParameters p, final XFormsModelSubmission.SecondPassParameters p2,
                                        IndentedLogger indentedLogger, boolean newDebugEnabled) {
        if (p2.isAsynchronous && !p.isReplaceNone()) {
            // Background asynchronous submission creates a new logger with its own independent indentation
            final IndentedLogger.Indentation newIndentation = new IndentedLogger.Indentation(indentedLogger.getIndentation().indentation);
            return new IndentedLogger(indentedLogger, newIndentation, newDebugEnabled);
        } else if (indentedLogger.isDebugEnabled() != newDebugEnabled) {
            // Keep shared indentation but use new debug setting
            return new IndentedLogger(indentedLogger, indentedLogger.getIndentation(), newDebugEnabled);
        } else {
            // Synchronous submission or foreground asynchronous submission uses current logger
            return indentedLogger;
        }
    }

    private static boolean isLogDetails() {
        return XFormsProperties.getDebugLogging().contains("submission-details");
    }

    // Only allow xxforms-submit from client
    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_SUBMIT);
    }

    public boolean allowExternalEvent(String eventName) {
        return ALLOWED_EXTERNAL_EVENTS.contains(eventName);
    }
}
