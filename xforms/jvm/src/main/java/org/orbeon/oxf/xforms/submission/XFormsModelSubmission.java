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

import org.apache.log4j.Logger;
import org.orbeon.dom.Document;
import org.orbeon.dom.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.externalcontext.ExternalContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsError;
import org.orbeon.oxf.xforms.XFormsProperties;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.model.XFormsInstance;
import org.orbeon.oxf.xforms.model.XFormsModel;
import org.orbeon.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.xforms.DelayedEvent;
import org.orbeon.xforms.RelevanceHandling;
import org.orbeon.xforms.XFormsId;
import scala.Option;

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

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;

    private final XFormsModel model;

    // All the submission types in the order they must be checked
    private final Submission[] submissions;

    public XFormsModelSubmission(XBLContainer container, org.orbeon.oxf.xforms.analysis.model.Submission staticSubmission, XFormsModel model) {
        this.staticSubmission = staticSubmission;

        this.container = container;
        this.containingDocument = container.getContainingDocument();

        this.model = model;

        this.submissions = new Submission[] {
            new EchoSubmission(this),
            new ClientGetAllSubmission(this),
            new CacheableSubmission(this),
            new RegularSubmission(this)
        };
    }

    public XFormsContainingDocument containingDocument() {
        return containingDocument;
    }

    public Element getSubmissionElement() {
        return staticSubmission.element();
    }

    public String getId() {
        return staticSubmission.staticId();
    }

    public String getPrefixedId() {
        return XFormsId.getPrefixedId(getEffectiveId());
    }

    public Scope scope() {
        return staticSubmission.scope();
    }

    public String getEffectiveId() {
        return XFormsId.getRelatedEffectiveId(model.getEffectiveId(), getId());
    }

    public XBLContainer container() {
        return getModel().container();
    }

    public LocationData getLocationData() {
        return staticSubmission.locationData();
    }

    public XFormsEventTarget parentEventObserver() {
        return model;
    }

    public XFormsModel getModel() {
        return model;
    }

    public void performDefaultAction(XFormsEvent event) {
        final String eventName = event.name();

        if (XFormsEvents.XFORMS_SUBMIT().equals(eventName) || XFormsEvents.XXFORMS_SUBMIT().equals(eventName)) {
            // 11.1 The xforms-submit Event
            // Bubbles: Yes / Cancelable: Yes / Context Info: None
            doSubmit(event);
        } else if (XFormsEvents.XXFORMS_ACTION_ERROR().equals(eventName)) {
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
                p = SubmissionParameters.apply(event.name(), this);

                if (indentedLogger.isDebugEnabled()) {
                    final String message = p.isDeferredSubmissionFirstPass() ? "submission first pass" : p.isDeferredSubmissionSecondPass() ? "submission second pass" : "submission";
                    indentedLogger.startHandleOperation("", message, "id", getEffectiveId());
                }

                // If a submission requiring a second pass was already set, then we ignore a subsequent submission but
                // issue a warning
                {
                    final Option<DelayedEvent> twoPassParams = containingDocument.findTwoPassSubmitEvent();
                    if (p.isDeferredSubmission() && twoPassParams.isDefined()) {
                        indentedLogger.logWarning("", "another submission requiring a second pass already exists",
                                "existing submission", twoPassParams.get().targetEffectiveId(),
                                "new submission", this.getEffectiveId());
                        return;
                    }
                }

                /* ***** Check for pending uploads ********************************************************************** */

                // We can do this first, because the check just depends on the controls, instance to submit, and pending
                // submissions if any. This does not depend on the actual state of the instance.
                if (p.serialize() && p.xxfUploads() && SubmissionUtils.hasBoundRelevantPendingUploadControls(containingDocument, p.refContext().refInstanceOpt())) {
                    throw new XFormsSubmissionException(
                        this,
                        "xf:submission: instance to submit has at least one pending upload.",
                        "checking pending uploads",
                        null,
                        new XFormsSubmitErrorEvent(XFormsModelSubmission.this, ErrorType$.MODULE$.XXFORMS_PENDING_UPLOADS(), null)
                    );
                }

                /* ***** Update data model ****************************************************************************** */

                final RelevanceHandling relevanceHandling = p.relevanceHandling();

                // "The data model is updated"
                if (p.refContext().refInstanceOpt().isDefined()) {
                    final XFormsModel modelForInstance = p.refContext().refInstanceOpt().get().model();
                    // NOTE: XForms 1.1 says that we should rebuild/recalculate the "model containing this submission".
                    // Here, we rebuild/recalculate instead the model containing the submission's single-node binding.
                    // This can be different than the model containing the submission if using e.g. xxf:instance().

                    // NOTE: XForms 1.1 seems to say this should happen regardless of whether we serialize or not. If
                    // the instance is not serialized and if no instance data is otherwise used for the submission,
                    // this seems however unneeded so we optimize out.
                    if (p.validate() || ! relevanceHandling.equals(RelevanceHandling.Keep$.MODULE$) || p.xxfCalculate()) {
                        // Rebuild impacts validation, relevance and calculated values (set by recalculate)
                        modelForInstance.doRebuild();
                    }
                    if (! relevanceHandling.equals(RelevanceHandling.Keep$.MODULE$) || p.xxfCalculate()) {
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
                            p.refContext().refNodeInfo(),
                            p.refContext().refInstanceOpt(),
                            p.validate(),
                            relevanceHandling,
                            p.xxfAnnotate(),
                            p.xxfRelevantAttOpt(),
                            indentedLogger
                        );
                    }

                    containingDocument.addTwoPassSubmitEvent(TwoPassSubmissionParameters.apply(getEffectiveId(), p));
                    return;
                }

                /* ***** Submission second pass ************************************************************************* */

                // Compute parameters only needed during second pass
                final SecondPassParameters p2 = SecondPassParameters.apply(this, p);
                resolvedActionOrResource = p2.actionOrResource(); // in case of exception

                /* ***** Serialization ********************************************************************************** */

                // Get serialization requested from @method and @serialization attributes
                final String requestedSerialization = getRequestedSerializationOrNull(p.serializationOpt(), p.xformsMethod(), p.httpMethod());
                if (requestedSerialization == null)
                    throw new XFormsSubmissionException(
                        this,
                        "xf:submission: invalid submission method requested: " + p.xformsMethod(),
                        "serializing instance",
                        null,
                        null
                    );

                final Document documentToSubmit;
                if (p.serialize()) {

                    // Check if a submission requires file upload information
                    if (requestedSerialization.startsWith("multipart/") && p.refContext().refInstanceOpt().isDefined()) {
                        // Annotate before re-rooting/pruning
                        XFormsSubmissionUtils.annotateBoundRelevantUploadControls(containingDocument, p.refContext().refInstanceOpt().get());
                    }

                    // Create document to submit
                    documentToSubmit = createDocumentToSubmit(
                        p.refContext().refNodeInfo(),
                        p.refContext().refInstanceOpt(),
                        p.validate(),
                        relevanceHandling,
                        p.xxfAnnotate(),
                        p.xxfRelevantAttOpt(),
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

                        final XFormsSubmitSerializeEvent serializeEvent = new XFormsSubmitSerializeEvent(XFormsModelSubmission.this, p.refContext().refNodeInfo(), requestedSerialization);
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
                            throw new XFormsSubmissionException(
                                XFormsModelSubmission.this,
                                "Error while processing xf:submission",
                                "processing submission",
                                throwable,
                                null
                            );
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
    void doSubmitReplace(SubmissionResult submissionResult) {

        assert submissionResult != null;

        // Big bag of initial runtime parameters
        final SubmissionParameters p = SubmissionParameters.apply(null, this);
        final SecondPassParameters p2 = SecondPassParameters.apply(this, p);

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
                final SubmissionParameters updatedP =
                    initializeXPathContext ? SubmissionParameters.withUpdatedRefContext(p, XFormsModelSubmission.this) : p;

                // Process the different types of response
                if (submissionResult.getThrowable() != null) {
                    // Propagate throwable, which might have come from a separate thread
                    submitDoneOrErrorRunnable = new Runnable() {
                        public void run() { sendSubmitError(submissionResult.getThrowable(), submissionResult); }
                    };
                } else {
                    // Replacer provided, perform replacement
                    assert submissionResult.getReplacer() != null;
                    submitDoneOrErrorRunnable = submissionResult.getReplacer().replace(submissionResult.connectionResult(), updatedP, p2);
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
                            AllReplacer.forwardResultToResponse(result.connectionResult(), response);
                        else if (result.getReplacer() instanceof RedirectReplacer)
                            RedirectReplacer.doReplace(result.connectionResult(), response);
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

    public Replacer getReplacer(ConnectionResult connectionResult, SubmissionParameters p) {

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
                    if (ReplaceType.isReplaceAll(p.replaceType())) {
                        replacer = new AllReplacer(this, containingDocument);
                    } else if (ReplaceType.isReplaceInstance(p.replaceType())) {
                        replacer = new InstanceReplacer(this, containingDocument);
                    } else if (ReplaceType.isReplaceText(p.replaceType())) {
                        replacer = new TextReplacer(this, containingDocument);
                    } else if (ReplaceType.isReplaceNone(p.replaceType())) {
                        replacer = new NoneReplacer(this, containingDocument);
                    } else if (ReplaceType.isReplaceBinary(p.replaceType())) {
                        replacer = new BinaryReplacer(this, containingDocument);
                    } else {
                        throw new XFormsSubmissionException(
                            this,
                            "xf:submission: invalid replace attribute: " + p.replaceType(),
                            "processing instance replacement",
                            null,
                            new XFormsSubmitErrorEvent(this, ErrorType$.MODULE$.XXFORMS_INTERNAL_ERROR(), connectionResult)
                        );
                    }
                } else {
                    // There is no body, notify that processing is terminated
                    if (ReplaceType.isReplaceInstance(p.replaceType()) || ReplaceType.isReplaceText(p.replaceType())) {
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
                if (! ReplaceType.isReplaceAll(p.replaceType()))
                    throw new XFormsSubmissionException(
                        this,
                        "xf:submission for submission id: " + getId() + ", redirect code received with replace=\"" + p.replaceType() + "\"",
                        "processing submission response",
                        null,
                        new XFormsSubmitErrorEvent(this, ErrorType$.MODULE$.RESOURCE_ERROR(), connectionResult)
                    );

                replacer = new RedirectReplacer(this, containingDocument);

            } else {
                // Error code received
                throw new XFormsSubmissionException(
                    this,
                    "xf:submission for submission id: " + getId() + ", error code received when submitting instance: " + connectionResult.statusCode(),
                    "processing submission response",
                    null,
                    new XFormsSubmitErrorEvent(this, ErrorType$.MODULE$.RESOURCE_ERROR(), connectionResult)
                );
            }

            return replacer;
        } else {
            return null;
        }
    }

    public XFormsInstance findReplaceInstanceNoTargetref(scala.Option<XFormsInstance> refInstance) {
        final XFormsInstance replaceInstance;
        if (staticSubmission.xxfReplaceInstanceIdOrNull() != null)
            replaceInstance = container.findInstanceOrNull(staticSubmission.xxfReplaceInstanceIdOrNull());
        else if (staticSubmission.replaceInstanceIdOrNull() != null)
            replaceInstance = model.getInstance(staticSubmission.replaceInstanceIdOrNull());
        else if (refInstance.isEmpty())
            replaceInstance = model.getDefaultInstance();
        else
            replaceInstance = refInstance.get();
        return replaceInstance;
    }

    public NodeInfo evaluateTargetRef(XPathCache.XPathContext xpathContext,
                                      XFormsInstance defaultReplaceInstance, Item submissionElementContextItem) {
        final Object destinationObject;
        if (staticSubmission.targetrefOpt().isEmpty()) {
            // There is no explicit @targetref, so the target is implicitly the root element of either the instance
            // pointed to by @ref, or the instance specified by @instance or @xxf:instance.
            destinationObject = defaultReplaceInstance.rootElement();
        } else {
            // There is an explicit @targetref, which must be evaluated.

            // "The in-scope evaluation context of the submission element is used to evaluate the expression." BUT ALSO "The
            // evaluation context for this attribute is the in-scope evaluation context for the submission element, except
            // the context node is modified to be the document element of the instance identified by the instance attribute
            // if it is specified."
            final boolean hasInstanceAttribute = staticSubmission.xxfReplaceInstanceIdOrNull() != null || staticSubmission.replaceInstanceIdOrNull() != null;
            final Item targetRefContextItem = hasInstanceAttribute
                    ? defaultReplaceInstance.rootElement() : submissionElementContextItem;

            // Evaluate destination node
            // "This attribute is evaluated only once a successful submission response has been received and if the replace
            // attribute value is "instance" or "text". The first node rule is applied to the result."
            destinationObject = XPathCache.evaluateSingleWithContext(xpathContext, targetRefContextItem, staticSubmission.targetrefOpt().get(), containingDocument().getRequestStats().getReporter());
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

    public IndentedLogger getDetailsLogger(final SubmissionParameters p, final SecondPassParameters p2) {
        return getNewLogger(p, p2, getIndentedLogger(), isLogDetails());
    }

    public IndentedLogger getTimingLogger(final SubmissionParameters p, final SecondPassParameters p2) {
        final IndentedLogger indentedLogger = getIndentedLogger();
        return getNewLogger(p, p2, indentedLogger, indentedLogger.isDebugEnabled());
    }

    private static IndentedLogger getNewLogger(final SubmissionParameters p, final SecondPassParameters p2,
                                        IndentedLogger indentedLogger, boolean newDebugEnabled) {
        if (p2.isAsynchronous() && ! ReplaceType.isReplaceNone(p.replaceType())) {
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
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_SUBMIT());
    }

    public boolean allowExternalEvent(String eventName) {
        return ALLOWED_EXTERNAL_EVENTS.contains(eventName);
    }
}
