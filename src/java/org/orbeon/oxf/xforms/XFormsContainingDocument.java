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
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XXFormsActionErrorEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsLoadEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.script.ScriptInterpreter;
import org.orbeon.oxf.xforms.state.*;
import org.orbeon.oxf.xforms.submission.*;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.Item;

import java.io.IOException;
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
            loggersMap.put(category, new IndentedLogger(globalLogger, globalLogger.isDebugEnabled() && debugConfig.contains(category), indentation, category));
        }
    }

    private String uuid;        // UUID of this document
    private long sequence = 1;  // sequence number of changes to this document

    private SAXStore lastAjaxResponse; // last Ajax response for retry feature

    private final IndentedLogger indentedLogger = getIndentedLogger(LOGGING_CATEGORY);

    // Global XForms function library
    private static XFormsFunctionLibrary functionLibrary = new XFormsFunctionLibrary();

    // Whether this document is currently being initialized
    private boolean isInitializing;

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
    private String focusEffectiveControlId;
    private String helpEffectiveControlId;
    private List<DelayedEvent> delayedEvents;

    // Annotated page template for noscript and full updates mode
    // NOTE: We used to keep this in the static state, but the static state must now not depend on external HTML anymore
    private SAXStore annotatedTemplate;

    private final XPathDependencies xpathDependencies;

    /**
     * Return the global function library.
     */
    public static XFormsFunctionLibrary getFunctionLibrary() {
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
    public XFormsContainingDocument(XFormsStaticState staticState, SAXStore annotatedTemplate,
                                    XFormsURIResolver uriResolver, ExternalContext.Response response) {
        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null, null);

        // Remember location data
        setLocationData(staticState.locationData());

        // Create UUID for this document instance
        this.uuid = UUIDUtils.createPseudoUUID();

        // Initialize request information
        {
            initializeRequestInformation();
            this.versionedPathMatchers = (List<URLRewriterUtils.PathMatcher>) PipelineContext.get().getAttribute(PageFlowControllerProcessor.PATH_MATCHERS);
        }

        indentedLogger.startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).", "uuid", this.uuid);
        {
            // Remember static state
            this.staticState = staticState;
            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());

            // Remember annotated page template if needed based on static state information
            {
                this.annotatedTemplate = staticState.isKeepAnnotatedTemplate() ? annotatedTemplate : null;

                if (this.annotatedTemplate != null && indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug("", "keeping XHTML tree", "approximate size (bytes)", Long.toString(this.annotatedTemplate.getApproximateSize()));
                }
            }

            this.xpathDependencies = Version.instance().createUIDependencies(this);

            // Remember parameters used during initialization
            this.uriResolver = uriResolver;
            this.response = response;
            this.isInitializing = true;

            // Initialize the containing document
            try {
                initialize();
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "initializing XForms containing document"));
            }
        }
        indentedLogger.endHandleOperation();
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
        this.containerNamespace = StringUtils.defaultIfEmpty(externalContext.getResponse().getNamespacePrefix(), "");
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * Used by XFormsStateManager.
     *
     * @param xformsState       XFormsState containing static and dynamic state
     */
    public XFormsContainingDocument(XFormsState xformsState) {
        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null, null);

        // Create static state object
        {
            final String staticStateDigest = xformsState.getStaticStateDigest();

            if (staticStateDigest != null) {
                final XFormsStaticState cachedState = XFormsStaticStateCache.instance().getDocument(staticStateDigest);
                if (cachedState != null) {
                    // Found static state in cache
                    indentedLogger.logDebug("", "found static state by digest in cache");
                    this.staticState = cachedState;
                } else {
                    // Not found static state in cache, create static state from input
                    indentedLogger.logDebug("", "did not find static state by digest in cache");
                    this.staticState = XFormsStaticStateImpl.restore(staticStateDigest, xformsState.getStaticState());

                    // Store in cache
                    XFormsStaticStateCache.instance().storeDocument(this.staticState);
                }

                assert this.staticState.isServerStateHandling();
            } else {
                // Not digest provided, create static state from input
                indentedLogger.logDebug("", "did not find static state by digest in cache");
                this.staticState = XFormsStaticStateImpl.restore(null, xformsState.getStaticState());

                assert this.staticState.isClientStateHandling();
            }

            this.staticOps = new StaticStateGlobalOps(staticState.topLevelPart());
        }

        indentedLogger.startHandleOperation("initialization", "restoring containing document");

        {
            // Make sure there is location data
            setLocationData(this.staticState.locationData());

            this.xpathDependencies = Version.instance().createUIDependencies(this);

            // Restore the containing document's dynamic state
            final String encodedDynamicState = xformsState.getDynamicState();
            try {
                if (StringUtils.isEmpty(encodedDynamicState)) {
                    // Just for tests, we allow the dynamic state to be empty
                    initialize();
                } else {
                    // Regular case
                    restoreDynamicState(encodedDynamicState);
                }
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
            }
        }
        indentedLogger.endHandleOperation();
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
     * Return the annotated page template if available. Only for noscript mode and full updates.
     *
     * @return  SAXStore containing annotated page template or null
     */
    public SAXStore getAnnotatedTemplate() {
        return annotatedTemplate;
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

        assert !isInitializing;
        assert response == null;
        assert uriResolver == null;

        this.activeSubmissionFirstPass = null;
        this.replaceAllCallable = null;
        this.gotSubmissionReplaceAll = false;
        this.gotSubmissionRedirect = false;

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;
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

    public void addScriptToRun(String scriptId, XFormsEvent event, XFormsEventObserver eventObserver) {

        if (activeSubmissionFirstPass != null && StringUtils.isBlank(activeSubmissionFirstPass.getResolvedXXFormsTarget())) {
            // Scripts occurring after a submission without a target takes place should not run
            // TODO: Should we allow scripts anyway? Don't we allow value changes updates on the client anyway?
            indentedLogger.logWarning("", "xxforms:script will be ignored because two-pass submission started", "script id", scriptId);
            return;
        }

        // Warn that scripts won't run in noscript mode (duh)
        if (staticState.isNoscript())
            indentedLogger.logWarning("noscript", "script won't run in noscript mode", "script id", scriptId);

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList<Script>();
        scriptsToRun.add(new Script(XFormsUtils.scriptIdToScriptName(scriptId), event, eventObserver));
    }

    public static class Script {
        private String functionName;
        private final XFormsEvent event;
        private final XFormsEventObserver eventObserver;

        public Script(String functionName, XFormsEvent event, XFormsEventObserver eventObserver) {
            this.functionName = functionName;
            this.event = event;
            this.eventObserver = eventObserver;
        }

        public String getFunctionName() {
            return functionName;
        }

        public XFormsEvent getEvent() {
            return event;
        }

        public XFormsEventObserver getEventObserver() {
            return eventObserver;
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
     * Tell the client that focus must be changed to the given effective control id.
     *
     * This can be called several times, but only the last control id is remembered.
     *
     * @param effectiveControlId
     */
    public void setClientFocusEffectiveControlId(String effectiveControlId) {
        this.focusEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to set the focus to, or null.
     */
    public String getClientFocusControlEffectiveId() {

        if (focusEffectiveControlId == null)
            return null;

        final XFormsControl xformsControl = (XFormsControl) getObjectByEffectiveId(focusEffectiveControlId);
        // It doesn't make sense to tell the client to set the focus to an element that is non-relevant or readonly
        if (xformsControl instanceof XFormsSingleNodeControl) {
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

    @Override
    public Object resolveObjectById(String sourceEffectiveId, String targetStaticId, Item contextItem) {
        if (targetStaticId.equals(CONTAINING_DOCUMENT_PSEUDO_ID)) {
            // Special case of containing document
            return this;
        } else {
            // All other cases
            return super.resolveObjectById(sourceEffectiveId, targetStaticId, contextItem);
        }
    }

    @Override
    public void dispatchEvent(XFormsEvent event) {
        // Ensure that the event uses the proper container to dispatch the event
        final XBLContainer targetContainer = event.getTargetObject().getXBLContainer(this);
        if (targetContainer == this) {
            super.dispatchEvent(event);
        } else {
            targetContainer.dispatchEvent(event);
        }
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
        this.isInitializing = false;

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

    public void performDefaultAction(XFormsEvent event) {

        final String eventName = event.getName();
        if (XFormsEvents.XXFORMS_LOAD.equals(eventName)) {
            // Internal load event
            final XXFormsLoadEvent xxformsLoadEvent = (XXFormsLoadEvent) event;
            final ExternalContext externalContext = NetUtils.getExternalContext();
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
                externalContext.getResponse().sendRedirect(pathInfo, parameters, false, false);
            } catch (IOException e) {
                throw new ValidationException(e, getLocationData());
            }
        } else if (XFormsEvents.XXFORMS_POLL.equals(eventName)) {
            // Poll event for submissions
            // NOP, as we check for async submission in the client event loop
        } else if (XFormsEvents.XXFORMS_ACTION_ERROR.equals(eventName)) {
            // Log error
            final XXFormsActionErrorEvent ev = (XXFormsActionErrorEvent) event;
            getIndentedLogger(XFormsActions.LOGGING_CATEGORY).logError("action", "exception while running action", ev.toStringArray());
        } else {
            super.performDefaultAction(event);
        }
    }

    /**
     * Create an encoded dynamic state that represents the dynamic state of this XFormsContainingDocument.
     *
     * @param compress              whether to compress
     * @param isForceEncryption     whether to force encryption or not
     * @return                      encoded dynamic state
     */
    public String createEncodedDynamicState(boolean compress, boolean isForceEncryption) {
        return XFormsUtils.encodeXML(createDynamicStateDocument(), compress,
            (isForceEncryption || XFormsProperties.isClientStateHandling(this)) ? XFormsProperties.getXFormsPassword() : null, false);
    }

    private Document createDynamicStateDocument() {

        final Document dynamicStateDocument;
        indentedLogger.startHandleOperation("", "encoding state");
        {
            dynamicStateDocument = Dom4jUtils.createDocument();
            final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
            // Add UUIDs
            dynamicStateElement.addAttribute("uuid", uuid);
            dynamicStateElement.addAttribute("sequence", Long.toString(sequence));

            // Add request information
            dynamicStateElement.addAttribute("deployment-type", deploymentType.name());
            dynamicStateElement.addAttribute("request-context-path", requestContextPath);
            dynamicStateElement.addAttribute("request-path", requestPath);
            dynamicStateElement.addAttribute("container-type", containerType);
            dynamicStateElement.addAttribute("container-namespace", containerNamespace);

            // Remember versioned paths
            if (versionedPathMatchers != null && versionedPathMatchers.size() > 0) {
                final Element matchersElement = dynamicStateElement.addElement("matchers");
                for (final URLRewriterUtils.PathMatcher pathMatcher: versionedPathMatchers) {
                    matchersElement.add(pathMatcher.serialize());
                }
            }

            // Add upload information
            if (pendingUploads != null && pendingUploads.size() > 0)
                dynamicStateElement.addAttribute("pending-uploads", StringUtils.join(pendingUploads, ' '));

            // Serialize instances
            {
                final Element instancesElement = dynamicStateElement.addElement("instances");
                serializeInstances(instancesElement);
            }

            // Serialize controls
            xformsControls.serializeControls(dynamicStateElement);

            // Serialize annotated page template if present
            if (annotatedTemplate != null) {
                final Element templateElement = dynamicStateElement.addElement("template");
                final Document document = TransformerUtils.saxStoreToDom4jDocument(annotatedTemplate);
                templateElement.add(document.getRootElement().detach());
            }

            // Serialize last Ajax response if present
            if (lastAjaxResponse != null) {
                final Element responseElement = dynamicStateElement.addElement("response");
                final Document document = TransformerUtils.saxStoreToDom4jDocument(lastAjaxResponse);
                responseElement.add(document.getRootElement().detach());
            }
        }
        indentedLogger.endHandleOperation();

        // DEBUG
//        System.out.println("XXX SERIALIZE: " + Dom4jUtils.domToPrettyString(dynamicStateDocument));

        return dynamicStateDocument;
    }


    /**
     * Restore the document's dynamic state given a serialized version of the dynamic state.
     *
     * @param encodedDynamicState   serialized dynamic state
     */
    private void restoreDynamicState(String encodedDynamicState) {

        // Get dynamic state document
        final Element dynamicStateElement = XFormsUtils.decodeXML(encodedDynamicState).getRootElement();

        // DEBUG
//        System.out.println("XXX RESTORE: " + Dom4jUtils.domToPrettyString(dynamicStateElement.getDocument()));

        // Restore UUIDs

        this.uuid = dynamicStateElement.attributeValue("uuid");
        this.sequence = Long.parseLong(dynamicStateElement.attributeValue("sequence"));

        indentedLogger.logDebug("initialization", "restoring UUID", "UUID", this.uuid,
                "sequence", Long.toString(this.sequence));

        // Restore request information
        if (dynamicStateElement.attribute("deployment-type") != null) {
            // Normal case where information below was previously serialized
            this.deploymentType = XFormsConstants.DeploymentType.valueOf(dynamicStateElement.attributeValue("deployment-type"));
            this.requestContextPath = dynamicStateElement.attributeValue("request-context-path");
            this.requestPath = dynamicStateElement.attributeValue("request-path");
            this.containerType = dynamicStateElement.attributeValue("container-type");
            this.containerNamespace = dynamicStateElement.attributeValue("container-namespace");
        } else {
            // Use information from the request
            // This is relied upon by oxf:xforms-submission and unit tests and shouldn't be relied on in other cases
            initializeRequestInformation();
        }

        // Restore versioned paths matchers if present
        {
            final Element matchersElement = dynamicStateElement.element("matchers");
            if (matchersElement != null) {
                final List<Element> matchersElements = Dom4jUtils.elements(matchersElement, "matcher");
                this.versionedPathMatchers = new ArrayList<URLRewriterUtils.PathMatcher>(matchersElements.size());
                for (final Element currentMatcherElement: matchersElements) {
                    this.versionedPathMatchers.add(new URLRewriterUtils.PathMatcher(currentMatcherElement));
                }
            }
        }

        // Restore upload information
        {
            final String pendingUploads = dynamicStateElement.attributeValue("pending-uploads");
            this.pendingUploads = (pendingUploads == null) ? null : new HashSet<String>(Arrays.asList(StringUtils.split(pendingUploads, ' ')));
        }

        // Restore annotated page template if present
        {
            final Element templateElement = dynamicStateElement.element("template");
            if (templateElement != null) {
                final Document templateDocument = Dom4jUtils.createDocument();
                templateDocument.setRootElement((Element) ((Element) templateElement.elements().get(0)).detach());
                this.annotatedTemplate = TransformerUtils.dom4jToSAXStore(templateDocument);
            }
        }

        // TODO: don't use PipelineContext: use other ThreadLocal
        final PipelineContext pipelineContext = PipelineContext.get();

        // Restore models state
        {
            // Store instances state in PipelineContext for use down the line
            final Element instancesElement = dynamicStateElement.element("instances");
            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, instancesElement);

            // Create XForms controls and models
            createControlsAndModels();

            // Restore top-level models state, including instances
            restoreModelsState();
        }

        // Restore controls state
        {
            // Store serialized control state for retrieval later
            final Map<String, Element> serializedControlStateMap = xformsControls.getSerializedControlStateMap(dynamicStateElement);
            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, serializedControlStateMap);

            xformsControls.restoreControls();

            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS, null);
        }

        // Restore last Ajax response if present
        {
            final Element responseElement = dynamicStateElement.element("response");
            if (responseElement != null) {
                final Document responseDocument = Dom4jUtils.createDocument();
                responseDocument.setRootElement((Element) ((Element) responseElement.elements().get(0)).detach());
                this.lastAjaxResponse = TransformerUtils.dom4jToSAXStore(responseDocument);
            }
        }

        // Indicate that instance restoration process is over
        pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, null);
    }

    /**
     * Whether the containing document is in a phase of restoring the dynamic state.
     *
     * @return                  true iif restore is in process
     */
    public boolean isRestoringDynamicState() {
        // TODO: don't use PipelineContext: use other ThreadLocal
        return PipelineContext.get().getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES) != null;
    }

    public Map<String, Element> getSerializedControlStatesMap() {
        // TODO: don't use PipelineContext: use other ThreadLocal
        return (Map) PipelineContext.get().getAttribute(XFormsContainingDocument.XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS);
    }

    private void initialize() {
        // This is called upon the first creation of the XForms engine or for testing only

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

        // This is important because if controls use binds, those must be up to date
        rebuildRecalculateIfNeeded();

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
            isDebugEnabled = logger.isDebugEnabled() && XFormsProperties.getDebugLogging().contains(category);
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

    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.KEYPRESS);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_LOAD);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_POLL);
    }

    public boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName) {
        return ALLOWED_EXTERNAL_EVENTS.contains(eventName);
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
