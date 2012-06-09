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
package org.orbeon.oxf.xforms;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.cache.Cacheable;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.common.Version;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.processor.PageFlowControllerProcessor;
import org.orbeon.oxf.servlet.OrbeonXFormsFilter;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.util.URLRewriterUtils;
import org.orbeon.oxf.util.UUIDUtils;
import org.orbeon.oxf.xforms.action.XFormsAPI;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.library.XFormsFunctionLibrary;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.script.ScriptInterpreter;
import org.orbeon.oxf.xforms.state.*;
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager;
import org.orbeon.oxf.xforms.submission.SubmissionResult;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.SAXStore;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.functions.FunctionLibrary;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.Lock;

/**
 * Represents an XForms containing document.
 *
 * The containing document:
 *
 * o Is the container for root XForms models (including multiple instances)
 * o Contains XForms controls
 * o Handles event handlers hierarchy
 */
public class XFormsContainingDocument extends XBLContainer implements XFormsDocumentLifecycle, Cacheable {

    // Special id name for the top-level containing document
    public static final String CONTAINING_DOCUMENT_PSEUDO_ID = "#document";

    // Per-document current logging indentation
    private final IndentedLogger.Indentation indentation = new IndentedLogger.Indentation();

    private final Map<String, IndentedLogger> loggersMap = new HashMap<String, IndentedLogger>();

    private void registerLogger(Logger globalLogger, Set<String> debugConfig, String category) {
        loggersMap.put(category, new IndentedLogger(globalLogger, globalLogger.isDebugEnabled() && debugConfig.contains(category), indentation, category));
    }

    /**
     * Return a logger given a category.
     */
    public IndentedLogger getIndentedLogger(String loggingCategory) {
        if (! loggersMap.containsKey(loggingCategory))
            registerLogger(XFormsServer.logger, XFormsProperties.getDebugLogging(), loggingCategory);

        return loggersMap.get(loggingCategory);
    }

    private String uuid;        // UUID of this document
    private long sequence = 1;  // sequence number of changes to this document

    private SAXStore lastAjaxResponse; // last Ajax response for retry feature

    private final IndentedLogger indentedLogger = getIndentedLogger("document");

    // Global XForms function library
    private static FunctionLibrary functionLibrary = XFormsFunctionLibrary.instance();

    // Whether the document supports updates
    private final boolean supportUpdates;

    // Whether this document is currently being initialized
    private boolean initializing;

    // Transient URI resolver for initialization
    private XFormsURIResolver uriResolver;

    // Transient OutputStream for xforms:submission[@replace = 'all'], or null if not available
    private ExternalContext.Response response;

    // Asynchronous submission manager
    private AsynchronousSubmissionManager asynchronousSubmissionManager;

    // Interpreter for JavaScript, etc.
    private ScriptInterpreter scriptInterpreter;

    // A document refers to the static state and controls
    private final XFormsStaticState staticState;
    private final StaticStateGlobalOps staticOps;
    private XFormsControls xformsControls;

    // Request information
    private XFormsConstants.DeploymentType deploymentType;
    private String requestContextPath;
    private String requestPath;
    private String containerType;
    private String containerNamespace;
    private List<URLRewriterUtils.PathMatcher> versionedPathMatchers;

    // Other state
    private Set<String> pendingUploads;

    // Client state
    private XFormsModelSubmission activeSubmissionFirstPass;
    private Callable<SubmissionResult> replaceAllCallable;
    private boolean gotSubmissionReplaceAll;
    private boolean gotSubmissionRedirect;
    private List<Message> messagesToRun;
    private List<Load> loadsToRun;
    private List<Script> scriptsToRun;
    private String helpEffectiveControlId;
    private List<DelayedEvent> delayedEvents;
    private List<XFormsError.ServerError> serverErrors;
    private Set<String> controlsStructuralChanges;

    // Page template for noscript mode if stored in dynamic state (otherwise stored in static state)
    private AnnotatedTemplate template;

    private final XPathDependencies xpathDependencies;

    /**
     * Return the global function library.
     */
    public static FunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsStaticState object.
     *
     * Used by XFormsToXHTML.
     *
     * @param staticState         static state object
     * @param uriResolver               optional URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     * @param response                  optional response for handling replace="all" during initialization
     */
    public XFormsContainingDocument(XFormsStaticState staticState, AnnotatedTemplate template,
                                    XFormsURIResolver uriResolver, ExternalContext.Response response) {
        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null, null, null);

        // Remember location data
        setLocationData(staticState.locationData());

        // Create UUID for this document instance
        this.uuid = UUIDUtils.createPseudoUUID();

        // Initialize request information
        {
            initializeRequestInformation();
            this.versionedPathMatchers = (List<URLRewriterUtils.PathMatcher>) PipelineContext.get().getAttribute(PageFlowControllerProcessor.PATH_MATCHERS);
            if (this.versionedPathMatchers == null)
                this.versionedPathMatchers = Collections.emptyList();
        }

        indentedLogger.startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).", "uuid", this.uuid);
        {
            // Remember static state
            this.staticState = staticState;
            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());

            // Remember annotated page template if needed
            {
                this.template = staticState.isDynamicNoscriptTemplate() ? template : null;

                if (this.template != null && indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug("", "keeping XHTML tree", "approximate size (bytes)", Long.toString(this.template.saxStore().getApproximateSize()));
                }
            }

            this.xpathDependencies = Version.instance().createUIDependencies(this);

            // Whether we support updates
            // NOTE: Reading the property requires the static state set above
            this.supportUpdates = ! XFormsProperties.isNoUpdates(this);

            // Remember parameters used during initialization
            this.uriResolver = uriResolver;
            this.response = response;
            this.initializing = true;

            // Initialize the containing document
            try {
                initialize();
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "initializing XForms containing document"));
            }
        }
        indentedLogger.endHandleOperation();
    }

    // This is called upon the first creation of the XForms engine
    private void initialize() {

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {
                // Create XForms controls and models
                createControlsAndModels();

                // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
                // order to optimize events
                // Perform deferred updates only for xforms-ready
                startOutermostActionHandler();
                {
                    // Initialize models
                    initializeModels();

                    // After initialization, some async submissions might be running
                    processCompletedAsynchronousSubmissions(true, true);
                }
                // End deferred behavior
                endOutermostActionHandler();
            }
        });
    }

    private void initializeRequestInformation() {
        final ExternalContext externalContext = NetUtils.getExternalContext();
        final ExternalContext.Request request = externalContext.getRequest();

        // Remember if filter provided separate deployment information
        final String rendererDeploymentType = (String) request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_DEPLOYMENT_ATTRIBUTE_NAME);
        this.deploymentType = "separate".equals(rendererDeploymentType) ? XFormsConstants.DeploymentType.separate
                    : "integrated".equals(rendererDeploymentType) ? XFormsConstants.DeploymentType.integrated
                    : XFormsConstants.DeploymentType.standalone;

        // Try to get request context path
        this.requestContextPath = request.getClientContextPath("/");

        // Base URI for path resolution
        {
            // It is possible to override the base URI by setting a request attribute. This is used by OrbeonXFormsFilter.
            final String rendererBaseURI = (String) request.getAttributesMap().get(OrbeonXFormsFilter.RENDERER_BASE_URI_ATTRIBUTE_NAME);
            // NOTE: We used to have response.rewriteRenderURL() on this, but why?
            if (rendererBaseURI != null)
                this.requestPath = rendererBaseURI;
            else
                this.requestPath = request.getRequestPath();
        }

        this.containerType = request.getContainerType();
        this.containerNamespace = StringUtils.defaultIfEmpty(externalContext.getRequest().getContainerNamespace(), "");
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * Used by XFormsStateManager.
     *
     * @param xformsState       XFormsState containing static and dynamic state
     * @param disableUpdates    whether to disable updates (for recreating initial document upon browser back)
     */
    public XFormsContainingDocument(XFormsState xformsState, boolean disableUpdates) {
        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null, null, null);

        // 1. Restore the static state
        {
            final scala.Option<String> staticStateDigest = xformsState.staticStateDigest();

            if (staticStateDigest.isDefined()) {
                final XFormsStaticState cachedState = XFormsStaticStateCache.instance().getDocument(staticStateDigest.get());
                if (cachedState != null) {
                    // Found static state in cache
                    indentedLogger.logDebug("", "found static state by digest in cache");
                    this.staticState = cachedState;
                } else {
                    // Not found static state in cache, create static state from input
                    indentedLogger.logDebug("", "did not find static state by digest in cache");
                    this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState());

                    // Store in cache
                    XFormsStaticStateCache.instance().storeDocument(this.staticState);
                }

                assert this.staticState.isServerStateHandling();
            } else {
                // Not digest provided, create static state from input
                indentedLogger.logDebug("", "did not find static state by digest in cache");
                this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.staticState());

                assert this.staticState.isClientStateHandling();
            }

            setLocationData(this.staticState.locationData());
            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());
            this.xpathDependencies = Version.instance().createUIDependencies(this);

            this.supportUpdates = ! disableUpdates && ! XFormsProperties.isNoUpdates(this);
        }

        // 2. Restore the dynamic state
        indentedLogger.startHandleOperation("initialization", "restoring containing document");
        try {
            restoreDynamicState(xformsState.dynamicState());
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
        }
        indentedLogger.endHandleOperation();
    }

    private void restoreDynamicState(final DynamicState dynamicState) {

        this.uuid = dynamicState.uuid();
        this.sequence = dynamicState.sequence();

        indentedLogger.logDebug("initialization", "restoring UUID", "UUID", this.uuid, "sequence", Long.toString(this.sequence));

        // Restore request information
        if (dynamicState.decodeDeploymentTypeJava() != null) {
            // Normal case where information below was previously serialized
            this.deploymentType = XFormsConstants.DeploymentType.valueOf(dynamicState.decodeDeploymentTypeJava());
            this.requestContextPath = dynamicState.decodeRequestContextPathJava();
            this.requestPath = dynamicState.decodeRequestPathJava();
            this.containerType = dynamicState.decodeContainerTypeJava();
            this.containerNamespace = dynamicState.decodeContainerNamespaceJava();
        } else {
            // Use information from the request
            // This is relied upon by oxf:xforms-submission and unit tests and shouldn't be relied on in other cases
            initializeRequestInformation();
        }

        // Restore other encoded objects
        this.versionedPathMatchers = dynamicState.decodePathMatchersJava();
        this.pendingUploads = new HashSet<String>(dynamicState.decodePendingUploadsJava()); // make copy as must be mutable
        this.template = dynamicState.decodeAnnotatedTemplateJava();
        this.lastAjaxResponse = dynamicState.decodeLastAjaxResponseJava();

        // Scope the containing document for the XForms API
        XFormsAPI.withContainingDocumentJava(this, new Runnable() {
            public void run() {

                // TODO: don't use PipelineContext: use other ThreadLocal
                final PipelineContext pipelineContext = PipelineContext.get();

                // Restore models state
                {
                    // Store instances state in PipelineContext for use down the line
                    pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, dynamicState.decodeInstancesJava());

                    // Create XForms controls and models
                    createControlsAndModels();

                    // Restore top-level models state, including instances
                    restoreModelsState();
                }

                // Restore controls state
                {
                    // Store serialized control state for retrieval later
                    pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, dynamicState.decodeControlsJava());
                    xformsControls.restoreControls();
                    pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, null);

                    // Once the control tree is rebuilt, restore focus if needed
                    if (dynamicState.decodeFocusedControlJava() != null)
                        xformsControls.setFocusedControl(xformsControls.getCurrentControlTree().getControl(dynamicState.decodeFocusedControlJava()));
                }

                // Indicate that instance restoration process is over
                pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, null);
            }
        });
    }

    // Whether the containing document is in a phase of restoring the dynamic state.
    public boolean isRestoringDynamicState() {
        // TODO: don't use PipelineContext: use other ThreadLocal
        return PipelineContext.get().getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES) != null;
    }

    public Map<String, Map<String, String>> getSerializedControlStatesMap() {
        // TODO: don't use PipelineContext: use other ThreadLocal
        return (Map<String, Map<String, String>>) PipelineContext.get().getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS);
    }

    public PartAnalysis getPartAnalysis() {
        return staticState.topLevelPart();
    }

    public XFormsURIResolver getURIResolver() {
        return uriResolver;
    }

    public String getUUID() {
        return uuid;
    }

    public void updateChangeSequence() {
        sequence++;
    }

    public SAXStore getLastAjaxResponse() {
        return lastAjaxResponse;
    }

    public boolean isInitializing() {
        return initializing;
    }

    /**
     * Whether the document is currently in a mode where it must remember differences. This is the case when:
     *
     * - the document is currently handling an update (as opposed to initialization)
     * - the property "no-updates" is false (the default)
     * - the document is
     *
     * @return  true iif the document must handle differences
     */
    public boolean isHandleDifferences() {
        return ! initializing && supportUpdates;
    }

    /**
     * Return the controls.
     */
    public XFormsControls getControls() {
        return xformsControls;
    }

    public XFormsConstants.DeploymentType getDeploymentType() {
        return deploymentType;
    }

    /**
     * Return the context path of the request that generated the XForms page.
     */
    public String getRequestContextPath() {
        return requestContextPath;
    }

    /**
     * Return the path of the request that generated the XForms page.
     */
    public String getRequestPath() {
        return requestPath;
    }

    /**
     * Return the container type that generated the XForms page, either "servlet" or "portlet".
     */
    public String getContainerType() {
        return containerType;
    }

    public boolean isPortletContainer() {
        return "portlet".equals(containerType);
    }

    /**
     * Return the container namespace that generated the XForms page. Always "" for servlets.
     */
    public String getContainerNamespace() {
        return containerNamespace;
    }

    /**
     * Return path matchers for versioned resources mode.
     *
     * @return  List of PathMatcher
     */
    public List<URLRewriterUtils.PathMatcher> getVersionedPathMatchers() {
        return versionedPathMatchers;
    }

    /**
     * Return dependencies implementation.
     */
    public final XPathDependencies getXPathDependencies() {
        return xpathDependencies;
    }

    /**
     * Return the page template if available. Only for noscript mode.
     */
    public AnnotatedTemplate getTemplate() {
        return template;
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
     * Return the static state of this document.
     */
    public XFormsStaticState getStaticState() {
        return staticState;
    }


    public StaticStateGlobalOps getStaticOps() {
        return staticOps;
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
    public XFormsModelSubmission getClientActiveSubmissionFirstPass() {
        return activeSubmissionFirstPass;
    }

    public Callable<SubmissionResult> getReplaceAllCallable() {
        return replaceAllCallable;
    }

    /**
     * Clear current client state.
     */
    private void clearClientState() {

        assert !initializing;
        assert response == null;
        assert uriResolver == null;

        this.activeSubmissionFirstPass = null;
        this.replaceAllCallable = null;
        this.gotSubmissionReplaceAll = false;
        this.gotSubmissionRedirect = false;

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;
        
        this.serverErrors = null;

        if (this.controlsStructuralChanges != null)
            this.controlsStructuralChanges.clear();
    }

    /**
     * Add a two-pass submission.
     *
     * This can be called with a non-null value at most once.
     */
    public void setActiveSubmissionFirstPass(XFormsModelSubmission submission) {
        if (this.activeSubmissionFirstPass != null)
            throw new ValidationException("There is already an active submission.", submission.getLocationData());

        if (loadsToRun != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:load within a same action sequence.", submission.getLocationData());

        // NOTE: It seems reasonable to run scripts, messages, focus, and help up to the point where the submission takes place.

        // Remember submission
        this.activeSubmissionFirstPass = submission;
    }

    public void setReplaceAllCallable(Callable<SubmissionResult> callable) {
        this.replaceAllCallable = callable;
    }

    public void setGotSubmission() {}

    public void setGotSubmissionReplaceAll() {
        if (this.gotSubmissionReplaceAll)
            throw new ValidationException("Unable to run a second submission with replace=\"all\" within a same action sequence.", getLocationData());

        this.gotSubmissionReplaceAll = true;
    }

    public boolean isGotSubmissionReplaceAll() {
        return gotSubmissionReplaceAll;
    }

    public void setGotSubmissionRedirect() {
        if (this.gotSubmissionRedirect)
            throw new ValidationException("Unable to run a second submission with replace=\"all\" redirection within a same action sequence.", getLocationData());

        this.gotSubmissionRedirect = true;
    }

    public boolean isGotSubmissionRedirect() {
        return gotSubmissionRedirect;
    }

    /**
     * Add an XForms message to send to the client.
     */
    public void addMessageToRun(String message, String level) {
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
     * @param isMaxDelay        whether the delay indicates a maximum delay
     * @param showProgress      whether to show the progress indicator when submitting the event
     * @param progressMessage   message to show if the progress indicator is visible
     */
    public void addDelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, int delay,
                                boolean isMaxDelay, boolean showProgress, String progressMessage) {
        if (delayedEvents == null)
            delayedEvents = new ArrayList<DelayedEvent>();

        delayedEvents.add(new DelayedEvent(eventName, targetStaticId, bubbles, cancelable, System.currentTimeMillis() + delay,
                isMaxDelay, showProgress, progressMessage));
    }

    public List<DelayedEvent> getDelayedEvents() {
        return delayedEvents;
    }

    public static class DelayedEvent {
        // Event information
        private final String eventName;
        private final String targetStaticId;
        private final boolean bubbles;
        private final boolean cancelable;
        // Meta information
        private final long time;
        private final boolean isMaxDelay;
        private final boolean showProgress;
        private final String progressMessage;

        public DelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, long time,
                            boolean isMaxDelay, boolean showProgress, String progressMessage) {
            this.eventName = eventName;
            this.targetStaticId = targetStaticId;
            this.bubbles = bubbles;
            this.cancelable = cancelable;
            this.time = time;
            this.isMaxDelay = isMaxDelay;
            this.showProgress = showProgress;
            this.progressMessage = progressMessage;
        }

        public String getEncodedDocument() {
            final Document eventsDocument = Dom4jUtils.createDocument();
            final Element eventsElement = eventsDocument.addElement(XFormsConstants.XXFORMS_EVENTS_QNAME);

            final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
            eventElement.addAttribute("name", eventName);
            eventElement.addAttribute("source-control-id", targetStaticId);
            eventElement.addAttribute("bubbles", Boolean.toString(bubbles));
            eventElement.addAttribute("cancelable", Boolean.toString(cancelable));

            return XFormsUtils.encodeXML(eventsDocument, false);
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

        public boolean isMaxDelay() {
            return isMaxDelay;
        }

        public void toSAX(ContentHandlerHelper ch, long currentTime) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events",
                    new String[] {
                            "delay", Long.toString(getTime() - currentTime),
                            "discardable", isMaxDelay() ? "true" : null,
                            "show-progress", Boolean.toString(isShowProgress()),
                            "progress-message", isShowProgress() ? getProgressMessage() : null
                    });
            ch.text(getEncodedDocument());
            ch.endElement();
        }

        public void toJSON(StringBuilder sb, long currentTime) {
            sb.append('{');
            sb.append("\"delay\":");
            sb.append(getTime() - currentTime);
            if (isMaxDelay()) {
                sb.append(",\"discardable\":true");
            }
            sb.append(",\"show-progress\":");
            sb.append(isShowProgress());
            if (isShowProgress()) {
                sb.append(",\"progress-message\":\"");
                XFormsUtils.escapeJavaScript(getProgressMessage());
                sb.append('"');
            }
            sb.append(",\"event\":\"");
            sb.append(getEncodedDocument());
            sb.append('"');

            sb.append("}");
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

    public void addScriptToRun(org.orbeon.oxf.xforms.Script script, XFormsEvent event, XFormsEventObserver eventObserver) {

        if (activeSubmissionFirstPass != null && StringUtils.isBlank(activeSubmissionFirstPass.getResolvedXXFormsTarget())) {
            // Scripts occurring after a submission without a target takes place should not run
            // TODO: Should we allow scripts anyway? Don't we allow value changes updates on the client anyway?
            indentedLogger.logWarning("", "xxforms:script will be ignored because two-pass submission started", "script id", script.prefixedId());
            return;
        }

        // Warn that scripts won't run in noscript mode (duh)
        if (staticState.isNoscript())
            indentedLogger.logWarning("noscript", "script won't run in noscript mode", "script id", script.prefixedId());

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList<Script>();
        scriptsToRun.add(new Script(script.clientName(), event, eventObserver));
    }

    public static class Script {
        public final String functionName;
        public final String targetEffectiveId;
        public final String observerEffectiveId;

        public Script(String functionName, XFormsEvent event, XFormsEventObserver eventObserver) {
            this.functionName = functionName;
            this.targetEffectiveId = event.targetObject().getEffectiveId();
            this.observerEffectiveId = eventObserver.getEffectiveId();
        }
    }

    public List<Script> getScriptsToRun() {
        return scriptsToRun;
    }

    /**
     * Add an XForms load to send to the client.
     */
    public void addLoadToRun(String resource, String target, String urlType, boolean isReplace, boolean isShowProgress) {

        if (activeSubmissionFirstPass != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:load within a same action sequence.", activeSubmissionFirstPass.getLocationData());

        if (loadsToRun == null)
            loadsToRun = new ArrayList<Load>();
        loadsToRun.add(new Load(resource, target, urlType, isReplace, isShowProgress));
    }

    /**
     * Return the list of loads to send to the client, null if none.
     */
    public List<Load> getLoadsToRun() {
        return loadsToRun;
    }

    public static class Load {
        private final String resource;
        private final String target;
        private final String urlType;
        private final boolean isReplace;
        private final boolean isShowProgress;

        public Load(String resource, String target, String urlType, boolean isReplace, boolean isShowProgress) {
            this.resource = resource;
            this.target = target;
            this.urlType = urlType;
            this.isReplace = isReplace;
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

        public boolean isShowProgress() {
            return isShowProgress;
        }
    }

    /**
     * Tell the client that help must be shown for the given effective control id.
     *
     * This can be called several times, but only the last control id is remembered.
     *
     * @param effectiveControlId
     */
    public void setClientHelpEffectiveControlId(String effectiveControlId) {
        this.helpEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to help for, or null.
     */
    public String getClientHelpControlEffectiveId() {

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
    
    public void addServerError(XFormsError.ServerError serverError) {
        final int maxErrors = XFormsProperties.getShowMaxRecoverableErrors(this);
        if (maxErrors > 0) {
            if (serverErrors == null)
                serverErrors = new ArrayList<XFormsError.ServerError>();

            if (serverErrors.size() < maxErrors)
                serverErrors.add(serverError);
        }
    }
    
    public List<XFormsError.ServerError> getServerErrors() {
        return serverErrors != null ? serverErrors : Collections.<XFormsError.ServerError>emptyList();
    }

    public Set<String> getControlsStructuralChanges() {
        return controlsStructuralChanges != null ? controlsStructuralChanges : Collections.<String>emptySet();
    }

    public void addControlStructuralChange(String prefixedId) {
        if (this.controlsStructuralChanges == null)
            this.controlsStructuralChanges = new HashSet<String>();

        this.controlsStructuralChanges.add(prefixedId);
    }

    @Override
    public Scope innerScope() {
        // Do it here because at construction time, we don't yet have access to the static state!
        return staticState.topLevelPart().startScope();
    }

    /**
     * Check if there is a pending two-pass submission and run it if needed.
     *
     * This must NOT synchronize on this document and must NOT modify the document, because further Ajax request might
     * run concurrently on this document.
     *
     * @param callable          callable to run or null
     * @param response          response to write to if needed
     * @return                  true if calling the callable was successful
     */
    public static boolean checkAndRunDeferredSubmission(Callable<SubmissionResult> callable, ExternalContext.Response response) {
        if (callable != null) {
            XFormsModelSubmission.runDeferredSubmission(callable, response);
            return true;
        } else {
            return false;
        }
    }

    public void afterInitialResponse() {

        this.uriResolver = null;        // URI resolver is of no use after initialization and it may keep dangerous references (PipelineContext)
        this.response = null;           // same as above
        this.initializing = false;

        clearClientState(); // client state can contain e.g. focus information, etc. set during initialization

        // Tell dependencies
        xpathDependencies.afterInitialResponse();
    }

    /**
     * Prepare the document for a sequence of external events.
     *
     * @param response          ExternalContext.Response for xforms:submission[@replace = 'all'], or null
     */
    public void beforeExternalEvents(ExternalContext.Response response) {

        // Tell dependencies
        xpathDependencies.beforeUpdateResponse();

        // Remember OutputStream
        this.response = response;

        // Process completed asynchronous submissions if any
        processCompletedAsynchronousSubmissions(false, false);
    }

    /**
     * End a sequence of external events.
     *
     */
    public void afterExternalEvents() {

        // Process completed asynchronous submissions if any
        processCompletedAsynchronousSubmissions(false, true);

        this.response = null;
    }

    /**
     * Called after sending a successful update response.
     */
    public void afterUpdateResponse() {
        clearClientState();
        xformsControls.afterUpdateResponse();
        // Tell dependencies
        xpathDependencies.afterUpdateResponse();
    }

    public void rememberLastAjaxResponse(SAXStore response) {
        lastAjaxResponse = response;
    }

    public long getSequence() {
        return sequence;
    }

    /**
     * Return an OutputStream for xforms:submission[@replace = 'all']. Used by submission.
     *
     * @return OutputStream
     */
    public ExternalContext.Response getResponse() {
        return response;
    }

    public AsynchronousSubmissionManager getAsynchronousSubmissionManager(boolean create) {
        if (asynchronousSubmissionManager == null && create)
            asynchronousSubmissionManager = new AsynchronousSubmissionManager(this);
        return asynchronousSubmissionManager;
    }

    private void processCompletedAsynchronousSubmissions(boolean skipDeferredEventHandling, boolean addPollEvent) {
        final AsynchronousSubmissionManager manager = getAsynchronousSubmissionManager(false);
        if (manager != null && manager.hasPendingAsynchronousSubmissions()) {
            if (!skipDeferredEventHandling)
                startOutermostActionHandler();
            manager.processCompletedAsynchronousSubmissions();
            if (!skipDeferredEventHandling)
                endOutermostActionHandler();

            // Remember to send a poll event if needed
            if (addPollEvent)
                manager.addClientDelayEventIfNeeded();
        }
    }

    public ScriptInterpreter getScriptInterpreter() {
        if (scriptInterpreter == null)
            scriptInterpreter = new ScriptInterpreter(this);
        return scriptInterpreter;
    }

    private void createControlsAndModels() {

        // Create XForms controls
        xformsControls = new XFormsControls(this);

        // Add models
        addAllModels();
    }

    protected void initializeNestedControls() {
        // Call-back from super class models initialization

        // This is important because if controls use binds, those must be up to date. In addition, MIP values will be up
        // to date. Finally, upon receiving xforms-ready after initialization, it is better if calculations and
        // validations are up to date.
        rebuildRecalculateRevalidateIfNeeded();

        // Initialize controls
        xformsControls.initialize();
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

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    @Override
    protected List<XFormsControl> getChildrenControls(XFormsControls controls) {
        return controls.getCurrentControlTree().getChildren();
    }

    /**
     * Register that an upload has started.
     */
    public void startUpload(String uploadId) {
        if (pendingUploads == null)
            pendingUploads = new HashSet<String>();
        pendingUploads.add(uploadId);
    }

    /**
     * Register that an upload has ended.
     */
    public void endUpload(String uploadId) {
        // NOTE: Don't enforce existence of upload, as this is also called if upload control becomes non-relevant, and
        // also because asynchronously if the client notifies us to end an upload after a control has become non-relevant,
        // we don't want to fail.
        if (pendingUploads != null)
            pendingUploads.remove(uploadId);
    }

    public Set<String> getPendingUploads() {
        if (pendingUploads == null)
            return Collections.emptySet();
        else
            return pendingUploads;
    }

    /**
     * Return the number of pending uploads.
     */
    public int countPendingUploads() {
        return (pendingUploads == null) ? 0 : pendingUploads.size();
    }

    /**
     * Whether an upload is pending for the given upload control.
     */
    public boolean isUploadPendingFor(XFormsUploadControl uploadControl) {
        return (pendingUploads != null) && pendingUploads.contains(uploadControl.getUploadUniqueId());
    }

    /**
     * Called when this document is added to the document cache.
     */
    public void added() {
        XFormsStateManager.instance().onAddedToCache(getUUID());
    }

    /**
     * Called when somebody explicitly removes this document from the document cache.
     */
    public void removed() {
        // WARNING: This can be called while another threads owns this document lock
        XFormsStateManager.instance().onRemovedFromCache(getUUID());
    }

    /**
     * Called by the cache to check that we are ready to be evicted from cache.
     *
     * @return lock or null in case session just expired
     */
    public Lock getEvictionLock() {
        return XFormsStateManager.getDocumentLock(getUUID());
    }

    /**
     * Called when cache expires this document from the document cache.
     */
    public void evicted() {
        // WARNING: This could have been called while another threads owns this document lock, but the cache now obtains
        // the lock on the document first and will not evict us if we have the lock. This means that this will be called
        // only if no thread is dealing with this document.
        XFormsStateManager.instance().onEvictedFromCache(this);
    }
}
