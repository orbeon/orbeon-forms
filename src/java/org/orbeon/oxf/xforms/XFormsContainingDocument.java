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
package org.orbeon.oxf.xforms;

import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.*;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEventTarget;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.submission.SubmissionResult;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.SequenceExtent;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * Represents an XForms containing document.
 *
 * The containing document:
 *
 * o Is the container for root XForms models (including multiple instances)
 * o Contains XForms controls
 * o Handles event handlers hierarchy
 */
public class XFormsContainingDocument extends XBLContainer {

    // Special id name for the top-level containing document
    public static final String CONTAINING_DOCUMENT_PSEUDO_ID = "$containing-document$";

    // Per-document current logging indentation
    private final IndentedLogger.Indentation indentation = new IndentedLogger.Indentation();

    private static final String LOGGING_CATEGORY = "document";
    private static final Logger logger = LoggerFactory.createLogger(XFormsContainingDocument.class);

    private final Map<String, IndentedLogger> loggersMap = new HashMap<String, IndentedLogger>();
    {
        final Logger globalLogger = XFormsServer.getLogger();
        final Set<String> debugConfig = XFormsProperties.getDebugLogging();

        registerLogger(XFormsContainingDocument.logger, globalLogger, debugConfig, XFormsContainingDocument.LOGGING_CATEGORY);
        registerLogger(XFormsModel.logger, globalLogger, debugConfig, XFormsModel.LOGGING_CATEGORY);
        registerLogger(XFormsModelSubmission.logger, globalLogger, debugConfig, XFormsModelSubmission.LOGGING_CATEGORY);
        registerLogger(XFormsControls.logger, globalLogger, debugConfig, XFormsControls.LOGGING_CATEGORY);
        registerLogger(XFormsEvents.logger, globalLogger, debugConfig, XFormsEvents.LOGGING_CATEGORY);
        registerLogger(XFormsActions.logger, globalLogger, debugConfig, XFormsActions.LOGGING_CATEGORY);
    }

    private void registerLogger(Logger localLogger, Logger globalLogger, Set<String> debugConfig, String category) {
        if (XFormsServer.USE_SEPARATE_LOGGERS) {
            loggersMap.put(category, new IndentedLogger(localLogger, indentation, category));
        } else {
            loggersMap.put(category, new IndentedLogger(globalLogger, debugConfig.contains(category), indentation, category));
        }
    }

    private final IndentedLogger indentedLogger = getIndentedLogger(LOGGING_CATEGORY);

    // Global XForms function library
    private static XFormsFunctionLibrary functionLibrary = new XFormsFunctionLibrary();

    // Object pool this object must be returned to, if any
    private ObjectPool sourceObjectPool;

    // Whether this document is currently being initialized
    private boolean isInitializing;

    // Transient URI resolver for initialization
    private XFormsURIResolver uriResolver;

    // Transient OutputStream for xforms:submission[@replace = 'all'], or null if not available
    private ExternalContext.Response response;

    // A document refers to the static state and controls
    private XFormsStaticState xformsStaticState;
    private XFormsControls xformsControls;

    // Client state
    private XFormsModelSubmission activeSubmission;
    private List<AsynchronousSubmission> backgroundAsynchronousSubmissions;
    private List<AsynchronousSubmission> foregroundAsynchronousSubmissions;
    private boolean gotSubmission;
    private boolean gotSubmissionSecondPass;
    private boolean gotSubmissionReplaceAll;
    private List<Message> messagesToRun;
    private List<Load> loadsToRun;
    private List<Script> scriptsToRun;
    private String focusEffectiveControlId;
    private String helpEffectiveControlId;
    private List<DelayedEvent> delayedEvents;

    private boolean goingOffline;
//    private boolean goingOnline;  // never used at the moment

    // Event information
    private static final Set<String> ignoredXFormsOutputExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXFormsOutputExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXFormsUploadExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXFormsControlsExternalEvents = new HashSet<String>();

    private static final Set<String> allowedXFormsRepeatExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXFormsSubmissionExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXFormsContainingDocumentExternalEvents = new HashSet<String>();
    private static final Set<String> allowedXXFormsDialogExternalEvents = new HashSet<String>();

    static {
        // External events ignored on xforms:output
        ignoredXFormsOutputExternalEvents.add(XFormsEvents.XFORMS_DOM_FOCUS_IN);
        ignoredXFormsOutputExternalEvents.add(XFormsEvents.XFORMS_DOM_FOCUS_OUT);

        // External events allowed on xforms:output
        allowedXFormsOutputExternalEvents.addAll(ignoredXFormsOutputExternalEvents);
        allowedXFormsOutputExternalEvents.add(XFormsEvents.XFORMS_HELP);

        // External events allowed on xforms:upload
        allowedXFormsUploadExternalEvents.addAll(allowedXFormsOutputExternalEvents);
        allowedXFormsUploadExternalEvents.add(XFormsEvents.XFORMS_SELECT);
        allowedXFormsUploadExternalEvents.add(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE);

        // External events allowed on other controls
        allowedXFormsControlsExternalEvents.addAll(allowedXFormsOutputExternalEvents);
        allowedXFormsControlsExternalEvents.add(XFormsEvents.XFORMS_DOM_ACTIVATE);
        allowedXFormsControlsExternalEvents.add(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE);
        allowedXFormsControlsExternalEvents.add(XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE);// for noscript mode

        // External events allowed on xforms:repeat
        allowedXFormsRepeatExternalEvents.add(XFormsEvents.XXFORMS_DND);

        // External events allowed on xforms:submission
        allowedXFormsSubmissionExternalEvents.add(XFormsEvents.XXFORMS_SUBMIT);

        // External events allowed on containing document
        allowedXFormsContainingDocumentExternalEvents.add(XFormsEvents.XXFORMS_LOAD);
        allowedXFormsContainingDocumentExternalEvents.add(XFormsEvents.XXFORMS_OFFLINE);
        allowedXFormsContainingDocumentExternalEvents.add(XFormsEvents.XXFORMS_ONLINE);

        // External events allowed on xxforms:dialog
        allowedXXFormsDialogExternalEvents.add(XFormsEvents.XXFORMS_DIALOG_CLOSE);
    }

    // For testing only
//    private static int testAjaxToggleValue = 0;

    /**
     * Return the global function library.
     */
    public static XFormsFunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsEngineStaticState object.
     *
     * @param pipelineContext           current pipeline context
     * @param xformsStaticState         static state object
     * @param uriResolver               optional URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsStaticState xformsStaticState, XFormsURIResolver uriResolver) {

        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null);
        setLocationData(xformsStaticState.getLocationData());

        indentedLogger.startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).");

        // Remember static state
        this.xformsStaticState = xformsStaticState;

        // Remember URI resolver for initialization
        this.uriResolver = uriResolver;
        this.isInitializing = true;

        // Initialize the containing document
        try {
            initialize(pipelineContext);
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "initializing XForms containing document"));
        }

        // Clear URI resolver, since it is of no use after initialization, and it may keep dangerous references (PipelineContext)
        this.uriResolver = null;

        // NOTE: we clear isInitializing when Ajax requests come in

        indentedLogger.endHandleOperation();
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState and XFormsStaticState.
     *
     * @param pipelineContext         current pipeline context
     * @param xformsState             static and dynamic state information
     * @param xformsStaticState       static state object, or null if not available
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsState xformsState, XFormsStaticState xformsStaticState) {

        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null);

        if (xformsStaticState != null) {
            // Use passed static state object
            indentedLogger.startHandleOperation("initialization", "restoring containing document (static state object provided).");
            this.xformsStaticState = xformsStaticState;
        } else {
            // Create static state object
            // TODO: Handle caching of XFormsStaticState object? Anything that can be done here?
            indentedLogger.startHandleOperation("initialization", "restoring containing document (static state object not provided).");
            this.xformsStaticState = new XFormsStaticState(pipelineContext, xformsState.getStaticState());
        }

        // Make sure there is location data
        setLocationData(this.xformsStaticState.getLocationData());

        // Restore the containing document's dynamic state
        final String encodedDynamicState = xformsState.getDynamicState();

        try {
            if (encodedDynamicState == null || encodedDynamicState.equals("")) {
                // Just for tests, we allow the dynamic state to be empty
                initialize(pipelineContext);
            } else {
                // Regular case
                restoreDynamicState(pipelineContext, encodedDynamicState);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
        }

        indentedLogger.endHandleOperation();
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * @param pipelineContext   current pipeline context
     * @param xformsState       XFormsState containing static and dynamic state
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsState xformsState) {
        this(pipelineContext, xformsState,  null);
    }

    public XFormsState getXFormsState(PipelineContext pipelineContext) {

        // Encode state
        return new XFormsState(xformsStaticState.getEncodedStaticState(pipelineContext), createEncodedDynamicState(pipelineContext, false));
    }

    public void setSourceObjectPool(ObjectPool sourceObjectPool) {
        this.sourceObjectPool = sourceObjectPool;
    }

    public ObjectPool getSourceObjectPool() {
        return sourceObjectPool;
    }

    public XFormsURIResolver getURIResolver() {
        return uriResolver;
    }

    public boolean isInitializing() {
        return isInitializing;
    }

    /**
     * Whether the document is currently in a mode where it must handle differences. This is the case when the document
     * is initializing and producing the initial output.
     *
     * @return  true iif the document must handle differences
     */
    public boolean isHandleDifferences() {
        return !isInitializing;
    }

    /**
     * Return the controls.
     */
    public XFormsControls getControls() {
        return xformsControls;
    }

    /**
     * Whether the document is dirty since the last request.
     *
     * @return  whether the document is dirty since the last request
     */
    public boolean isDirtySinceLastRequest() {
        return xformsControls.isDirtySinceLastRequest();
    }

    /**
     * Return the XFormsEngineStaticState.
     */
    public XFormsStaticState getStaticState() {
        return xformsStaticState;
    }

    /**
     * Return a map of script id -> script text.
     */
    public Map<String, String> getScripts() {
        return xformsStaticState.getScripts();
    }

    /**
     * Return the document base URI.
     */
    public String getBaseURI() {
        return xformsStaticState.getBaseURI();
    }

    /**
     * Return the container type that generate the XForms page, either "servlet" or "portlet".
     */
    public String getContainerType() {
        return xformsStaticState.getContainerType();
    }

    /**
     * Return the container namespace that generate the XForms page. Always "" for servlets.
     */
    public String getContainerNamespace() {
        return xformsStaticState.getContainerNamespace();
    }

    /**
     * Return external-events configuration attribute.
     */
    private Map<String, String> getExternalEventsMap() {
        return xformsStaticState.getExternalEventsMap();
    }

    /**
     * Return whether an external event name is explicitly allowed by the configuration.
     *
     * @param eventName event name to check
     * @return          true if allowed, false otherwise
     */
    private boolean isExplicitlyAllowedExternalEvent(String eventName) {
        return !XFormsEventFactory.isBuiltInEvent(eventName) && getExternalEventsMap() != null && getExternalEventsMap().get(eventName) != null;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {

        // Search in parent (models and this)
        {
            final Object resultObject = super.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in controls
        {
            final Object resultObject = xformsControls.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Check container id
        if (effectiveId.equals(getEffectiveId()))
            return this;

        return null;
    }

    /**
     * Return the active submission if any or null.
     */
    public XFormsModelSubmission getClientActiveSubmission() {
        return activeSubmission;
    }

    /**
     * Clear current client state.
     */
    private void clearClientState() {
        this.isInitializing = false;

        this.activeSubmission = null;
        this.gotSubmission = false;
        this.gotSubmissionSecondPass = false;
        this.gotSubmissionReplaceAll = false;
        this.backgroundAsynchronousSubmissions = null;
        this.foregroundAsynchronousSubmissions = null;

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;

        this.goingOffline = false;
//        this.goingOnline = false;
    }

    /**
     * Set the active submission.
     *
     * This can be called with a non-null value at most once.
     */
    public void setClientActiveSubmission(XFormsModelSubmission activeSubmission) {
        if (this.activeSubmission != null)
            throw new ValidationException("There is already an active submission.", activeSubmission.getLocationData());

        if (loadsToRun != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:load within a same action sequence.", activeSubmission.getLocationData());

        if (messagesToRun != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:message within a same action sequence.", activeSubmission.getLocationData());

        // scriptsToRun: it seems reasonable to run scripts up to the point where the submission takes place

        if (focusEffectiveControlId != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:setfocus within a same action sequence.", activeSubmission.getLocationData());

        if (helpEffectiveControlId != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms-help within a same action sequence.", activeSubmission.getLocationData());

        this.activeSubmission = activeSubmission;
    }

    public boolean isGotSubmission() {
        return gotSubmission;
    }

    public void setGotSubmission() {
        this.gotSubmission = true;
    }

    public boolean isGotSubmissionSecondPass() {
        return gotSubmissionSecondPass;
    }

    public void setGotSubmissionSecondPass() {
        this.gotSubmissionSecondPass = true;
    }

    public void setGotSubmissionReplaceAll() {
        if (this.gotSubmissionReplaceAll)
            throw new ValidationException("Unable to run a second submission with replace=\"all\" within a same action sequence.", getLocationData());

        this.gotSubmissionReplaceAll = true;
    }

    public boolean isGotSubmissionReplaceAll() {
        return gotSubmissionReplaceAll;
    }

    private static class AsynchronousSubmission {
        public Future<SubmissionResult> future;
        public String submissionEffectiveId;

        public AsynchronousSubmission(Future<SubmissionResult> future, String submissionEffectiveId) {
            this.future = future;
            this.submissionEffectiveId = submissionEffectiveId;
        }
    }

    // Global thread pool
    private static ExecutorService threadPool = Executors.newCachedThreadPool();

    // See http://java.sun.com/j2se/1.5.0/docs/api/java/util/concurrent/ExecutorCompletionService.html
    private CompletionService<SubmissionResult> completionService = new ExecutorCompletionService<SubmissionResult>(threadPool);

    public void addAsynchronousSubmission(final Callable<SubmissionResult> callable, String submissionEffectiveId, boolean isBackground) {

        // Add submission future
        if (isBackground) {
            // Background async submission
            final Future<SubmissionResult> future = completionService.submit(callable);

            if (backgroundAsynchronousSubmissions == null)
                backgroundAsynchronousSubmissions = new ArrayList<AsynchronousSubmission>();
            backgroundAsynchronousSubmissions.add(new AsynchronousSubmission(future, submissionEffectiveId));
        } else {
            // Foreground async submission
            final Future<SubmissionResult> future = new Future<SubmissionResult>() {

                private boolean isDone;
                private boolean isCanceled;

                private SubmissionResult result;

                public boolean cancel(boolean b) {
                    if (isDone)
                        return false;
                    isCanceled = true;
                    return true;
                }

                public boolean isCancelled() {
                    return isCanceled;
                }

                public boolean isDone() {
                    return isDone;
                }

                public SubmissionResult get() throws InterruptedException, ExecutionException {
                    if (isCanceled)
                        throw new CancellationException();

                    if (!isDone) {
                        try {
                            result = callable.call();
                        } catch (Exception e) {
                            throw new ExecutionException(e);
                        }

                        isDone = true;
                    }

                    return result;
                }

                public SubmissionResult get(long l, TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
                    return get();
                }
            };

            if (foregroundAsynchronousSubmissions == null)
                foregroundAsynchronousSubmissions = new ArrayList<AsynchronousSubmission>();
            foregroundAsynchronousSubmissions.add(new AsynchronousSubmission(future, submissionEffectiveId));

            // NOTE: In this very basic level of support, we don't support
            // xforms-submit-done / xforms-submit-error handlers

            // TODO: Do something with result, e.g. log?
            // final ConnectionResult connectionResult = ...
        }
    }

    public boolean hasBackgroundAsynchronousSubmissions() {
        return backgroundAsynchronousSubmissions != null && backgroundAsynchronousSubmissions.size() > 0;
    }

    private boolean hasForegroundAsynchronousSubmissions() {
        return foregroundAsynchronousSubmissions != null && foregroundAsynchronousSubmissions.size() > 0;
    }

    public void processBackgroundAsynchronousSubmissions(PropertyContext propertyContext) {

        // Processing a series of background asynchronous submission might in turn trigger the creation of new
        // background asynchronous submissions. We need to process until no submissions are left.
        while (hasBackgroundAsynchronousSubmissions())
            processBackgroundAsynchronousSubmissionsBatch(propertyContext);
    }

    /**
     * Process all current background submissions if any. Submissions are processed in the order in which they are made
     * available upon termination by the completion service.
     *
     * @param propertyContext   current context
     */
    private void processBackgroundAsynchronousSubmissionsBatch(PropertyContext propertyContext) {

        // Get then reset current batch list
        final List<AsynchronousSubmission> submissionsToProcess = backgroundAsynchronousSubmissions;
        backgroundAsynchronousSubmissions = null;

        // NOTE: See http://wiki.orbeon.com/forms/projects/asynchronous-submissions

        final IndentedLogger indentedLogger = getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
        indentedLogger.startHandleOperation("", "processing background asynchronous submissions");
        int count = 0;
        try {
            final int size = submissionsToProcess.size();
            for (; count < size; count++) {
                try {
                    // Handle next completed task
                    final SubmissionResult result = completionService.take().get();

                    // Process response by dispatching an event to the submission
                    final XFormsModelSubmission submission = (XFormsModelSubmission) getObjectByEffectiveId(result.getSubmissionEffectiveId());
                    final XBLContainer container = submission.getXBLContainer(this);
                    // NOTE: not clear whether we should use an event for this as there doesn't seem to be a benefit
                    container.dispatchEvent(propertyContext, new XXFormsSubmitReplaceEvent(this, submission, result));

                } catch (Throwable throwable) {
                    // Something bad happened
                    throw new OXFException(throwable);
                }
            }
        } finally {
            indentedLogger.endHandleOperation("count", Integer.toString(count));
        }
    }

    /**
     * Process all current foreground submissions if any. Submissions are processed in the order in which they were
     * executed.
     */
    public void processForegroundAsynchronousSubmissions() {
        // NOTE: See http://wiki.orbeon.com/forms/doc/developer-guide/asynchronous-submissions
        if (hasForegroundAsynchronousSubmissions()) {
            final IndentedLogger indentedLogger = getIndentedLogger(XFormsModelSubmission.LOGGING_CATEGORY);
            indentedLogger.startHandleOperation("", "processing foreground asynchronous submissions");
            int count = 0;
            try {
                for (Iterator<AsynchronousSubmission> i = foregroundAsynchronousSubmissions.iterator(); i.hasNext(); count++) {
                    final AsynchronousSubmission asyncSubmission = i.next();
                    try {
                        // Submission is run at this point
                        asyncSubmission.future.get();

                        // NOTE: We do not process the response at all

                    } catch (Throwable throwable) {
                        // Something happened but we swallow the exception and keep going
                        indentedLogger.logError("", "asynchronous submission: throwable caught", throwable);
                    }
                    // Remove submission from list of submission so we can gc the Runnable
                    i.remove();
                }
            } finally {
                indentedLogger.endHandleOperation("count", Integer.toString(count));
            }
        }
    }

    /**
     * Add an XForms message to send to the client.
     */
    public void addMessageToRun(String message, String level) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:message within a same action sequence.", activeSubmission.getLocationData());

        if (messagesToRun == null)
            messagesToRun = new ArrayList<Message>();
        messagesToRun.add(new Message(message, level));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List<Message> getMessagesToRun() {
        return messagesToRun;
    }

    /**
     * Schedule an event for delayed execution, following xforms:dispatch/@delay semantics.
     *
     * @param eventName         name of the event to dispatch
     * @param targetStaticId    static id of the target to dispatch to
     * @param bubbles           whether the event bubbles
     * @param cancelable        whether the event is cancelable
     * @param delay             delay after which to dispatch the event
     * @param showProgress      whether to show the progress indicator when submitting the event
     * @param progressMessage   message to show if the progress indicator is visible
     */
    public void addDelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, int delay, boolean showProgress, String progressMessage) {
        if (delayedEvents == null)
            delayedEvents = new ArrayList<DelayedEvent>();

        delayedEvents.add(new DelayedEvent(eventName, targetStaticId, bubbles, cancelable, System.currentTimeMillis() + delay, showProgress, progressMessage));
    }

    public List<DelayedEvent> getDelayedEvents() {
        return delayedEvents;
    }

    public static class DelayedEvent {
        private String eventName;
        private String targetStaticId;
        private boolean bubbles;
        private boolean cancelable;
        private long time;
        private boolean showProgress;
        private String progressMessage;

        public DelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, long time, boolean showProgress, String progressMessage) {
            this.eventName = eventName;
            this.targetStaticId = targetStaticId;
            this.bubbles = bubbles;
            this.cancelable = cancelable;
            this.time = time;
            this.showProgress = showProgress;
            this.progressMessage = progressMessage;
        }

        public String getEncodedDocument(PipelineContext pipelineContext) {
            final Document eventsDocument = Dom4jUtils.createDocument();
            final Element eventsElement = eventsDocument.addElement(XFormsConstants.XXFORMS_EVENTS_QNAME);

            final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
            eventElement.addAttribute("name", eventName);
            eventElement.addAttribute("source-control-id", targetStaticId);
            eventElement.addAttribute("bubbles", Boolean.toString(bubbles));
            eventElement.addAttribute("cancelable", Boolean.toString(cancelable));

            return XFormsUtils.encodeXML(pipelineContext, eventsDocument, false);
        }

        public boolean isShowProgress() {
            return showProgress;
        }

        public String getProgressMessage() {
            return progressMessage;
        }

        public long getTime() {
            return time;
        }
    }

    public static class Message {
        private String message;
        private String level;

        public Message(String message, String level) {
            this.message = message;
            this.level = level;
        }

        public String getMessage() {
            return message;
        }

        public String getLevel() {
            return level;
        }
    }

    public void addScriptToRun(String scriptId, String eventTargetId, String eventObserverId) {

        if (activeSubmission != null) {
            // Scripts occurring after the submission takes place should not run
            indentedLogger.logWarning("", "xxforms:script will be ignored because two-pass submission started", "script id", scriptId);
            return;
        }

        // Warn that scripts won't run in noscript mode (duh)
        if (XFormsProperties.isNoscript(this))
            indentedLogger.logWarning("noscript", "script won't run in noscript mode", "script id", scriptId);

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList<Script>();
        scriptsToRun.add(new Script(XFormsUtils.scriptIdToScriptName(scriptId), eventTargetId, eventObserverId));
    }

    public static class Script {
        private String functionName;
        private String eventTargetId;
        private String eventObserverId;

        public Script(String functionName, String eventTargetId, String eventObserverId) {
            this.functionName = functionName;
            this.eventTargetId = eventTargetId;
            this.eventObserverId = eventObserverId;
        }

        public String getFunctionName() {
            return functionName;
        }

        public String getEventTargetId() {
            return eventTargetId;
        }

        public String getEventObserverId() {
            return eventObserverId;
        }
    }

    public List<Script> getScriptsToRun() {
        return scriptsToRun;
    }

    /**
     * Add an XForms load to send to the client.
     */
    public void addLoadToRun(String resource, String target, String urlType, boolean isReplace, boolean isPortletLoad, boolean isShowProgress) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:load within a same action sequence.", activeSubmission.getLocationData());

        if (loadsToRun == null)
            loadsToRun = new ArrayList<Load>();
        loadsToRun.add(new Load(resource, target, urlType, isReplace, isPortletLoad, isShowProgress));
    }

    /**
     * Return the list of loads to send to the client, null if none.
     */
    public List<Load> getLoadsToRun() {
        return loadsToRun;
    }

    public static class Load {
        private String resource;
        private String target;
        private String urlType;
        private boolean isReplace;
        private boolean isPortletLoad;
        private boolean isShowProgress;

        public Load(String resource, String target, String urlType, boolean isReplace, boolean isPortletLoad, boolean isShowProgress) {
            this.resource = resource;
            this.target = target;
            this.urlType = urlType;
            this.isReplace = isReplace;
            this.isPortletLoad = isPortletLoad;
            this.isShowProgress = isShowProgress;
        }

        public String getResource() {
            return resource;
        }

        public String getTarget() {
            return target;
        }

        public String getUrlType() {
            return urlType;
        }

        public boolean isReplace() {
            return isReplace;
        }

        public boolean isPortletLoad() {
            return isPortletLoad;
        }

        public boolean isShowProgress() {
            return isShowProgress;
        }
    }

    /**
     * Tell the client that focus must be changed to the given effective control id.
     *
     * This can be called several times, but only the last control id is remembered.
     *
     * @param effectiveControlId
     */
    public void setClientFocusEffectiveControlId(String effectiveControlId) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:setfocus within a same action sequence.", activeSubmission.getLocationData());

        this.focusEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to set the focus to, or null.
     */
    public String getClientFocusEffectiveControlId() {

        if (focusEffectiveControlId == null)
            return null;

        final XFormsControl xformsControl = (XFormsControl) getObjectByEffectiveId(focusEffectiveControlId);
        // It doesn't make sense to tell the client to set the focus to an element that is non-relevant or readonly
        if (xformsControl != null && xformsControl instanceof XFormsSingleNodeControl) {
            final XFormsSingleNodeControl xformsSingleNodeControl = (XFormsSingleNodeControl) xformsControl;
            if (xformsSingleNodeControl.isRelevant() && !xformsSingleNodeControl.isReadonly())
                return focusEffectiveControlId;
            else
                return null;
        } else {
            return null;
        }
    }

    /**
     * Tell the client that help must be shown for the given effective control id.
     *
     * This can be called several times, but only the last controld id is remembered.
     *
     * @param effectiveControlId
     */
    public void setClientHelpEffectiveControlId(String effectiveControlId) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms-help within a same action sequence.", activeSubmission.getLocationData());

        this.helpEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to help for, or null.
     */
    public String getClientHelpEffectiveControlId() {

        if (helpEffectiveControlId == null)
            return null;

        final XFormsControl xformsControl = (XFormsControl) getObjectByEffectiveId(helpEffectiveControlId);
        // It doesn't make sense to tell the client to show help for an element that is non-relevant, but we allow readonly
        if (xformsControl != null && xformsControl instanceof XFormsSingleNodeControl) {
            final XFormsSingleNodeControl xformsSingleNodeControl = (XFormsSingleNodeControl) xformsControl;
            if (xformsSingleNodeControl.isRelevant())
                return helpEffectiveControlId;
            else
                return null;
        } else {
            return null;
        }
    }

    /**
     * Execute an external event and ensure deferred event handling.
     *
     * @param pipelineContext           current PipelineContext
     * @param isTrustedEvent            whether this event is trusted
     * @param eventName                 name of the event
     * @param targetEffectiveId         effective id of the target to dispatch to
     * @param bubbles                   whether the event bubbles (for custom events)
     * @param cancelable                whether the event is cancelable (for custom events)
     * @param otherControlEffectiveId   other effective control id if any
     * @param valueString               optional context string
     * @param filesElement              optional files elements for upload
     * @param dndStart                  optional DnD start information
     * @param dndEnd                    optional DnD end information
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     */
    public void executeExternalEvent(PipelineContext pipelineContext, boolean isTrustedEvent, String eventName, String targetEffectiveId,
                                     boolean bubbles, boolean cancelable,
                                     String otherControlEffectiveId, String valueString, Element filesElement,
                                     String dndStart, String dndEnd, boolean handleGoingOnline) {

        final IndentedLogger indentedLogger = getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);
        final String LOG_TYPE = "executeExternalEvent";
        try {
            indentedLogger.startHandleOperation(LOG_TYPE, "handling external event", "target id", targetEffectiveId, "event name", eventName);

            // Get event target object
            XFormsEventTarget eventTarget;
            {
                final Object eventTargetObject = getObjectByEffectiveId(targetEffectiveId);
                if (!(eventTargetObject instanceof XFormsEventTarget)) {
                    if (XFormsProperties.isExceptionOnInvalidClientControlId(this)) {
                        throw new ValidationException("Event target id '" + targetEffectiveId + "' is not an XFormsEventTarget.", getLocationData());
                    } else {
                        if (indentedLogger.isDebugEnabled()) {
                            indentedLogger.logDebug(LOG_TYPE, "ignoring client event with invalid target id", "target id", targetEffectiveId, "event name", eventName);
                        }
                        return;
                    }
                }
                eventTarget = (XFormsEventTarget) eventTargetObject;
            }

            if (isTrustedEvent) {
                // Event is trusted, don't check if it is allowed
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(LOG_TYPE, "processing trusted event", "target id", targetEffectiveId, "event name", eventName);
                }
            } else if (!checkForAllowedEvents(pipelineContext, indentedLogger, eventName, eventTarget, handleGoingOnline)) {
                // Event is not trusted and is not allowed
                return;
            }

            // Get other event target
            final XFormsEventTarget otherEventTarget;
            {
                final Object otherEventTargetObject = (otherControlEffectiveId == null) ? null : getObjectByEffectiveId(otherControlEffectiveId);
                if (otherEventTargetObject == null) {
                    otherEventTarget = null;
                } else if (!(otherEventTargetObject instanceof XFormsEventTarget)) {
                    if (XFormsProperties.isExceptionOnInvalidClientControlId(this)) {
                        throw new ValidationException("Other event target id '" + otherControlEffectiveId + "' is not an XFormsEventTarget.", getLocationData());
                    } else {
                        if (indentedLogger.isDebugEnabled()) {
                            indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event with invalid second control id", "target id", targetEffectiveId, "event name", eventName, "second control id", otherControlEffectiveId);
                        }
                        return;
                    }
                } else {
                    otherEventTarget = (XFormsEventTarget) otherEventTargetObject;
                }
            }

            // Rewrite event type. This is special handling of xxforms-value-or-activate for noscript mode.
            if (XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE.equals(eventName)) {
                // In this case, we translate the event depending on the control type
                if (eventTarget instanceof XFormsTriggerControl) {
                    // Triggers get a DOM activation
                    if ("".equals(valueString)) {
                        // Handler produces:
                        //   <button type="submit" name="foobar" value="activate">...
                        //   <input type="submit" name="foobar" value="Hi There">...
                        //   <input type="image" name="foobar" value="Hi There" src="...">...

                        // IE 6/7 are terminally broken: they don't send the value back, but the contents of the label. So
                        // we must test for any empty content here instead of "!activate".equals(valueString). (Note that
                        // this means that empty labels won't work.) Further, with IE 6, all buttons are present when
                        // using <button>, so we use <input> instead, either with type="submit" or type="image". Bleh.

                        return;
                    }
                    eventName = XFormsEvents.XFORMS_DOM_ACTIVATE;
                } else {
                    // Other controls get a value change
                    eventName = XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE;
                }
            }

            // For testing only
            if (XFormsProperties.isAjaxTest()) {
                if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
                    valueString = "value" + System.currentTimeMillis();
                }
            }

            if (!handleGoingOnline) {
                // When not going online, each event is within its own start/end outermost action handler
                startOutermostActionHandler();
            }
            {
                // Create event
                final XFormsEvent xformsEvent = XFormsEventFactory.createEvent(this, eventName, eventTarget, otherEventTarget,
                        true, bubbles, cancelable, valueString, filesElement, new String[] { dndStart, dndEnd} );

                // Handle repeat focus. Don't dispatch event on DOMFocusOut however.
                if (targetEffectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1
                        && !XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)) {

                    // Check if the value to set will be different from the current value
                    if (eventTarget instanceof XFormsValueControl && xformsEvent instanceof XXFormsValueChangeWithFocusChangeEvent) {
                        final XXFormsValueChangeWithFocusChangeEvent valueChangeWithFocusChangeEvent = (XXFormsValueChangeWithFocusChangeEvent) xformsEvent;
                        if (valueChangeWithFocusChangeEvent.getOtherTargetObject() == null) {
                            // We only get a value change with this event
                            final String currentExternalValue = ((XFormsValueControl) eventTarget).getExternalValue(pipelineContext);
                            if (currentExternalValue != null) {
                                // We completely ignore the event if the value in the instance is the same. This also saves dispatching xxforms-repeat-focus below.
                                final boolean isIgnoreValueChangeEvent = currentExternalValue.equals(valueChangeWithFocusChangeEvent.getNewValue());
                                if (isIgnoreValueChangeEvent) {
                                    indentedLogger.logDebug(LOG_TYPE, "ignoring value change event as value is the same",
                                            "control id", eventTarget.getEffectiveId(), "event name", eventName, "value", currentExternalValue);

                                    // Ensure deferred event handling
                                    // NOTE: Here this will do nothing, but out of consistency we better have matching startOutermostActionHandler/endOutermostActionHandler
                                    endOutermostActionHandler(pipelineContext);
                                    return;
                                }
                            } else {
                                // shouldn't happen really, but just in case let's log this
                                indentedLogger.logDebug(LOG_TYPE, "got null currentExternalValue", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                            }
                        } else {
                            // There will be a focus event too, so don't ignore
                        }
                    }

                    // Dispatch repeat focus event
                    {
                        // The event target is in a repeated structure, so make sure it gets repeat focus
                        dispatchEvent(pipelineContext, new XXFormsRepeatFocusEvent(this, eventTarget));
                        // Get a fresh reference
                        eventTarget = (XFormsControl) getObjectByEffectiveId(eventTarget.getEffectiveId());
                    }
                }

                // Interpret event
                if (eventTarget instanceof XFormsOutputControl) {
                    // Special xforms:output case

                    if (XFormsEvents.XFORMS_DOM_FOCUS_IN.equals(eventName)) {

                        // First, dispatch DOMFocusIn
                        dispatchEvent(pipelineContext, xformsEvent);

                        // Then, dispatch DOMActivate unless the control is read-only
                        final XFormsOutputControl xformsOutputControl = (XFormsOutputControl) getObjectByEffectiveId(eventTarget.getEffectiveId());
                        if (!xformsOutputControl.isReadonly()) {
                            dispatchEvent(pipelineContext, new XFormsDOMActivateEvent(this, xformsOutputControl));
                        }
                    } else if (!ignoredXFormsOutputExternalEvents.contains(eventName)) {
                        // Dispatch other event
                        dispatchEvent(pipelineContext, xformsEvent);
                    }
                } else if (xformsEvent instanceof XXFormsValueChangeWithFocusChangeEvent) {
                    // 4.6.7 Sequence: Value Change

                    // What we want to do here is set the value on the initial controls tree, as the value has already been
                    // changed on the client. This means that this event(s) must be the first to come!

                    final XXFormsValueChangeWithFocusChangeEvent valueChangeWithFocusChangeEvent = (XXFormsValueChangeWithFocusChangeEvent) xformsEvent;
                    {
                        // Store value into instance data through the control
                        final XFormsValueControl valueXFormsControl = (XFormsValueControl) eventTarget;
                        // NOTE: filesElement is only used by the upload control at the moment
                        valueXFormsControl.storeExternalValue(pipelineContext, valueChangeWithFocusChangeEvent.getNewValue(), null, filesElement);
                    }

                    {
                        // NOTE: Recalculate and revalidate are done with the automatic deferred updates

                        // Handle focus change DOMFocusOut / DOMFocusIn
                        if (valueChangeWithFocusChangeEvent.getOtherTargetObject() != null) {

                            // We have a focus change (otherwise, the focus is assumed to remain the same)

                            // Dispatch DOMFocusOut
                            // NOTE: setExternalValue() above may cause e.g. xforms-select / xforms-deselect events to be
                            // dispatched, so we get the control again to have a fresh reference
                            final XFormsControl sourceXFormsControl = (XFormsControl) getObjectByEffectiveId(eventTarget.getEffectiveId());
                            if (sourceXFormsControl != null) {
                                dispatchEvent(pipelineContext, new XFormsDOMFocusOutEvent(this, sourceXFormsControl));
                            }

                            // Dispatch DOMFocusIn
                            final XFormsControl otherTargetXFormsControl
                                = (XFormsControl) getObjectByEffectiveId(((XFormsControl) valueChangeWithFocusChangeEvent.getOtherTargetObject()).getEffectiveId());
                            if (otherTargetXFormsControl != null) {
                                dispatchEvent(pipelineContext, new XFormsDOMFocusInEvent(this, otherTargetXFormsControl));
                            }
                        }

                        // NOTE: Refresh is done with the automatic deferred updates
                    }

                } else {
                    // Dispatch any other allowed event
                    dispatchEvent(pipelineContext, xformsEvent);
                }
            }
            // When not going online, each event is within its own start/end outermost action handler
            if (!handleGoingOnline) {
                endOutermostActionHandler(pipelineContext);
            }
        } finally {
            indentedLogger.endHandleOperation();
        }
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent event) {
        // Ensure that the event uses the proper container to dispatch the event
        final XBLContainer targetContainer = event.getTargetObject().getXBLContainer(this);
        if (targetContainer == this) {
            super.dispatchEvent(pipelineContext, event);
        } else {
            targetContainer.dispatchEvent(pipelineContext, event);
        }
    }

    /**
     * Check whether the external event is allowed on the gtiven target/
     *
     * @param pipelineContext   pipeline context
     * @param indentedLogger    logger
     * @param eventName         event name
     * @param eventTarget       event target
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     * @return                  true iif the event is allowed
     */
    private boolean checkForAllowedEvents(PipelineContext pipelineContext, IndentedLogger indentedLogger, String eventName, XFormsEventTarget eventTarget, boolean handleGoingOnline) {
        // Don't allow for events on non-relevant, readonly or xforms:output controls (we accept focus events on
        // xforms:output though).
        // This is also a security measure that also ensures that somebody is not able to change values in an instance
        // by hacking external events.
        final String LOG_TYPE = "checkForAllowedEvents";
        if (eventTarget instanceof XFormsControl) {
            // Target is a control

            if (eventTarget instanceof XXFormsDialogControl) {
                // Target is a dialog
                // Check for implicitly allowed events
                if (!allowedXXFormsDialogExternalEvents.contains(eventName)) {
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on xxforms:dialog", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                    }
                    return false;
                }
            } else if (eventTarget instanceof XFormsRepeatControl) {
                // Target is a repeat
                if (!allowedXFormsRepeatExternalEvents.contains(eventName)) {
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on xforms:repeat", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                    }
                    return false;
                }

            } else {
                // Target is a regular control

                // Only single-node controls accept events from the client
                if (!(eventTarget instanceof XFormsSingleNodeControl)) {// NOTE: This includes xforms:trigger/xforms:submit
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on non-single-node control", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                    }
                    return false;
                }

                final XFormsSingleNodeControl xformsControl = (XFormsSingleNodeControl) eventTarget;

                if (handleGoingOnline) {
                    // When going online, ensure rebuild/revalidate before each event
                    rebuildRecalculateIfNeeded(pipelineContext);

                    // Mark the control as dirty, because we may have done a rebuild/recalculate earlier, and this means
                    // the MIPs need to be re-evaluated before being checked below
                    getControls().cloneInitialStateIfNeeded();
                    xformsControl.markDirty();
                }

                if (!xformsControl.isRelevant() || (xformsControl.isReadonly() && !(xformsControl instanceof XFormsOutputControl))) {
                    // Controls accept event only if they are relevant and not readonly, except for xforms:output which may be readonly
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on non-relevant or read-only control", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                    }
                    return false;
                }

                if (!isExplicitlyAllowedExternalEvent(eventName)) {
                    // The event is not explicitly allowed: check for implicitly allowed events
                    if (xformsControl instanceof XFormsOutputControl) {
                        if (!allowedXFormsOutputExternalEvents.contains(eventName)) {
                            if (indentedLogger.isDebugEnabled()) {
                                indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on xforms:output", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                            }
                            return false;
                        }
                    } else if (xformsControl instanceof XFormsUploadControl) {
                        if (!allowedXFormsUploadExternalEvents.contains(eventName)) {
                            if (indentedLogger.isDebugEnabled()) {
                                indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on xforms:upload", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                            }
                            return false;
                        }
                    } else {
                        if (!allowedXFormsControlsExternalEvents.contains(eventName)) {
                            if (indentedLogger.isDebugEnabled()) {
                                indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event", "control id", eventTarget.getEffectiveId(), "event name", eventName);
                            }
                            return false;
                        }
                    }
                }
            }
        } else if (eventTarget instanceof XFormsModelSubmission) {
            // Target is a submission
            if (!isExplicitlyAllowedExternalEvent(eventName)) {
                // The event is not explicitly allowed: check for implicitly allowed events
                if (!allowedXFormsSubmissionExternalEvents.contains(eventName)) {
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on xforms:submission", "submission id", eventTarget.getEffectiveId(), "event name", eventName);
                    }
                    return false;
                }
            }
        } else if (eventTarget instanceof XFormsContainingDocument) {
            // Target is the containing document
            // Check for implicitly allowed events
            if (!allowedXFormsContainingDocumentExternalEvents.contains(eventName)) {
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event on containing document", "target id", eventTarget.getEffectiveId(), "event name", eventName);
                }
                return false;
            }
        } else {
            // Target is not a control
            if (!isExplicitlyAllowedExternalEvent(eventName)) {
                // The event is not explicitly allowed
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(LOG_TYPE, "ignoring invalid client event", "target id", eventTarget.getEffectiveId(), "event name", eventName);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Prepare the ContainingDocument for a sequence of external events.
     *
     * @param pipelineContext   current PipelineContext
     * @param response          ExternalContext.Response for xforms:submission[@replace = 'all'], or null
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     */
    public void startExternalEventsSequence(PipelineContext pipelineContext, ExternalContext.Response response, boolean handleGoingOnline) {
        // Clear containing document state
        clearClientState();

        // Remember OutputStream
        this.response = response;

        // Initialize controls
        xformsControls.initialize(pipelineContext);

        // Start outermost action handler here if going online
        if (handleGoingOnline)
            startOutermostActionHandler();
    }

    /**
     * End a sequence of external events.
     *
     * @param pipelineContext   current PipelineContext
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     */
    public void endExternalEventsSequence(PipelineContext pipelineContext, boolean handleGoingOnline) {

        // End outermost action handler here if going online
        if (handleGoingOnline)
            endOutermostActionHandler(pipelineContext);

        this.response = null;
    }

    /**
     * Return an OutputStream for xforms:submission[@replace = 'all']. Used by submission.
     *
     * @return OutputStream
     */
    public ExternalContext.Response getResponse() {
        return response;
    }

    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {

        final String eventName = event.getEventName();
        if (XFormsEvents.XXFORMS_LOAD.equals(eventName)) {
            // Internal load event
            final XXFormsLoadEvent xxformsLoadEvent = (XXFormsLoadEvent) event;
            final ExternalContext externalContext = (ExternalContext) propertyContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            try {
                final String resource = xxformsLoadEvent.getResource();

                final String pathInfo;
                final Map<String, String[]> parameters;

                final int qmIndex = resource.indexOf('?');
                if (qmIndex != -1) {
                    pathInfo = resource.substring(0, qmIndex);
                    parameters = NetUtils.decodeQueryString(resource.substring(qmIndex + 1), false);
                } else {
                    pathInfo = resource;
                    parameters = null;
                }
                externalContext.getResponse().sendRedirect(pathInfo, parameters, false, false, false);
            } catch (IOException e) {
                throw new ValidationException(e, getLocationData());
            }
        } else if (XFormsEvents.XXFORMS_ONLINE.equals(eventName)) {
            // Internal event for going online
            goOnline(propertyContext);
        } else if (XFormsEvents.XXFORMS_OFFLINE.equals(eventName)) {
            // Internal event for going offline
            goOffline(propertyContext);
        }
    }

    public void goOnline(PropertyContext propertyContext) {
        // Dispatch to all models
        for (XFormsModel currentModel: getModels()) {
            // TODO: Dispatch to children containers?
            dispatchEvent(propertyContext, new XXFormsOnlineEvent(this, currentModel));
        }
//        this.goingOnline = true;
        this.goingOffline = false;
    }

    public void goOffline(PropertyContext propertyContext) {

        // Handle inserts of controls marked as "offline insert triggers"
        final List<String> offlineInsertTriggerPrefixedIds = getStaticState().getOfflineInsertTriggerIds();
        if (offlineInsertTriggerPrefixedIds != null) {

            for (String currentPrefixedId: offlineInsertTriggerPrefixedIds) {
                final Object o = getObjectByEffectiveId(currentPrefixedId);// NOTE: won't work for triggers within repeats
                if (o instanceof XFormsTriggerControl) {
                    final XFormsTriggerControl trigger = (XFormsTriggerControl) o;
                    final XFormsEvent event = new XFormsDOMActivateEvent(this, trigger);
                    // This attribute is a temporary HACK, used to improve performance when going offline. It causes
                    // the insert action to not rebuild controls to adjust indexes after insertion, as well as always
                    // inserting based on the last node of the insert nodes-set. This probably wouldn't be needed if
                    // insert performance was good from the get go.
                    // TODO: check above now that repeat/insert/delete has been improved
                    event.setAttribute(XFormsConstants.NO_INDEX_ADJUSTMENT, new SequenceExtent(new Item[] { BooleanValue.TRUE }));
                    // Dispatch event n times
                    final int repeatCount = XFormsProperties.getOfflineRepeatCount(this);
                    for (int j = 0; j < repeatCount; j++)
                        dispatchEvent(propertyContext, event);
                }
            }
        }

        // Dispatch xxforms-offline to all models
        for (XFormsModel currentModel: getModels()) {
            // TODO: Dispatch to children containers
            dispatchEvent(propertyContext, new XXFormsOfflineEvent(this, currentModel));
        }
//        this.goingOnline = false;
        this.goingOffline = true;
    }

    public boolean goingOffline() {
        return goingOffline;
    }

    /**
     * Create an encoded dynamic state that represents the dynamic state of this XFormsContainingDocument.
     *
     * @param pipelineContext       current PipelineContext
     * @param isForceEncryption      whether to force encryption or not
     * @return                      encoded dynamic state
     */
    public String createEncodedDynamicState(PipelineContext pipelineContext, boolean isForceEncryption) {
        return XFormsUtils.encodeXML(pipelineContext, createDynamicStateDocument(),
            (isForceEncryption || XFormsProperties.isClientStateHandling(this)) ? XFormsProperties.getXFormsPassword() : null, false);
    }

    private Document createDynamicStateDocument() {

        final Document dynamicStateDocument;
        indentedLogger.startHandleOperation("", "encoding state");
        {
            dynamicStateDocument = Dom4jUtils.createDocument();
            final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
            // Serialize instances
            {
                final Element instancesElement = dynamicStateElement.addElement("instances");
                serializeInstances(instancesElement);
            }

            // Serialize controls
            xformsControls.serializeControls(dynamicStateElement);
        }
        indentedLogger.endHandleOperation();

        return dynamicStateDocument;
    }


    /**
     * Restore the document's dynamic state given a serialized version of the dynamic state.
     *
     * @param pipelineContext       current PipelineContext
     * @param encodedDynamicState   serialized dynamic state
     */
    private void restoreDynamicState(PipelineContext pipelineContext, String encodedDynamicState) {

        // Get dynamic state document
        final Document dynamicStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedDynamicState);

        // Restore models state
        {
            // Store instances state in PipelineContext for use down the line
            final Element instancesElement = dynamicStateDocument.getRootElement().element("instances");
            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, instancesElement);

            // Create XForms controls and models
            createControlsAndModels(pipelineContext, false);

            // Restore top-level models state, including instances
            restoreModelsState(pipelineContext);
        }

        // Restore controls state
        {
            // Store serialized control state for retrieval later
            final Map serializedControlStateMap = xformsControls.getSerializedControlStateMap(dynamicStateDocument.getRootElement());
            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, serializedControlStateMap);

            xformsControls.initializeState(pipelineContext, true);

            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, null);
        }

        // Indicate that instance restoration process is over
        pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, null);
    }

    /**
     * Whether the containing document is in a phase of restoring the dynamic state.
     *
     * @param propertyContext   current context
     * @return                  true iif restore is in process
     */
    public boolean isRestoringDynamicState(PropertyContext propertyContext) {
        return propertyContext.getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES) != null;
    }

    public Map getSerializedControlStatesMap(PropertyContext propertyContext) {
        return (Map) propertyContext.getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS);
    }

    private void initialize(PipelineContext pipelineContext) {
        // This is called upon the first creation of the XForms engine only

        // Create XForms controls and models
        createControlsAndModels(pipelineContext, true);

        // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
        // order to optimize events
        // Perform deferred updates only for xforms-ready
        startOutermostActionHandler();

        // Initialize models
        initializeModels(pipelineContext);

        // Check for background asynchronous submissions
        processBackgroundAsynchronousSubmissions(pipelineContext);

        // End deferred behavior
        endOutermostActionHandler(pipelineContext);
    }

    private void createControlsAndModels(PipelineContext pipelineContext, boolean isInitialization) {

        // Gather static analysis information
        xformsStaticState.analyzeIfNecessary(pipelineContext);

        // Create XForms controls
        xformsControls = new XFormsControls(this, isInitialization);

        // Add models
        addAllModels();
    }

    protected void initializeNestedControls(PropertyContext propertyContext) {
        // Call-back from super class models initialization

        // This is important because if controls use binds, those must be up to date
        rebuildRecalculateIfNeeded(propertyContext);

        // Initialize controls
        xformsControls.initialize(propertyContext);
    }

    private Stack<XFormsEvent> eventStack = new Stack<XFormsEvent>();

    public void startHandleEvent(XFormsEvent event) {
        eventStack.push(event);
    }

    public void endHandleEvent() {
        eventStack.pop();
    }

    /**
     * Return the event being processed by the current event handler, null if no event is being processed.
     */
    public XFormsEvent getCurrentEvent() {
        return (eventStack.size() == 0) ? null : eventStack.peek();
    }

    public List getEventHandlers(XBLContainer container) {
        return getStaticState().getEventHandlers(XFormsUtils.getPrefixedId(getEffectiveId()));
    }

    public static void logWarningStatic(String type, String message, String... parameters) {
        final Logger globalLogger = XFormsServer.getLogger();
        IndentedLogger.logWarningStatic(globalLogger, "XForms", type, message, parameters);
    }

    public static void logErrorStatic(String type, String message, Throwable throwable) {
        final Logger globalLogger = XFormsServer.getLogger();
        IndentedLogger.logErrorStatic(globalLogger, "XForms", type, message, throwable);
    }

    /**
     * Return a logger given a category.
     *
     * @param loggingCategory   category
     * @return                  logger
     */
    public IndentedLogger getIndentedLogger(String loggingCategory) {
        return loggersMap.get(loggingCategory);
    }

    private static final Map<String, IndentedLogger> STATIC_LOGGERS_MAP = new HashMap<String, IndentedLogger>();

    /**
     * Return a static logger given all the details.
     *
     * @param localLogger       local logger, used only if separate loggers are configured
     * @param globalLogger      global logger, usually XFormsServer.getLogger()
     * @param category          category
     * @return                  logger
     */
    public static IndentedLogger getIndentedLogger(Logger localLogger, Logger globalLogger, String category) {

        final IndentedLogger existingIndentedLogger = STATIC_LOGGERS_MAP.get(category);
        if (existingIndentedLogger != null) {
            return existingIndentedLogger;
        }

        final IndentedLogger indentedLogger;
        final Logger logger;
        final boolean isDebugEnabled;
        if (XFormsServer.USE_SEPARATE_LOGGERS) {
            logger = localLogger;
            isDebugEnabled = logger.isDebugEnabled();
        } else {
            logger = globalLogger;
            isDebugEnabled = XFormsProperties.getDebugLogging().contains(category);
        }
        indentedLogger = new IndentedLogger(logger, isDebugEnabled, category);

        STATIC_LOGGERS_MAP.put(category, indentedLogger);

        return indentedLogger;
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    @Override
    protected List<XFormsControl> getChildrenControls(XFormsControls controls) {
        return controls.getCurrentControlTree().getChildren();
    }
}
