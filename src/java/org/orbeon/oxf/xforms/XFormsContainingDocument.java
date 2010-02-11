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
import org.apache.commons.pool.ObjectPool;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.submission.AsynchronousSubmissionManager;
import org.orbeon.oxf.xforms.submission.XFormsModelSubmission;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.SequenceExtent;

import java.io.IOException;
import java.util.*;

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
    public static final String CONTAINING_DOCUMENT_PSEUDO_ID = "#document";

    // Per-document current logging indentation
    private final IndentedLogger.Indentation indentation = new IndentedLogger.Indentation();

    private static final String LOGGING_CATEGORY = "document";
    private static final Logger logger = LoggerFactory.createLogger(XFormsContainingDocument.class);

    public static final String EVENT_LOG_TYPE = "executeExternalEvent";

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

    private String uuid;
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

    /**
     * Return the global function library.
     */
    public static XFormsFunctionLibrary getFunctionLibrary() {
        return functionLibrary;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsEngineStaticState object.
     *
     * @param pipelineContext           current context
     * @param xformsStaticState         static state object
     * @param uriResolver               optional URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsStaticState xformsStaticState, XFormsURIResolver uriResolver) {
        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null);

        // Remember location data
        setLocationData(xformsStaticState.getLocationData());

        // Create UUID for this document instance
        this.uuid = UUIDUtils.createPseudoUUID();

        indentedLogger.startHandleOperation("initialization", "creating new ContainingDocument (static state object provided).", "uuid", this.uuid);
        {
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
        }
        indentedLogger.endHandleOperation();
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState and XFormsStaticState.
     *
     * @param pipelineContext         current context
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
        {
            // Make sure there is location data
            setLocationData(this.xformsStaticState.getLocationData());

            // Restore the containing document's dynamic state
            final String encodedDynamicState = xformsState.getDynamicState();
            try {
                if (StringUtils.isEmpty(encodedDynamicState)) {
                    // Just for tests, we allow the dynamic state to be empty
                    initialize(pipelineContext);
                } else {
                    // Regular case
                    restoreDynamicState(pipelineContext, encodedDynamicState);
                }
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
            }
        }
        indentedLogger.endHandleOperation();
    }

    /**
     * Restore an XFormsContainingDocument from XFormsState only.
     *
     * @param pipelineContext   current context
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

    public String getUUID() {
        return uuid;
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
    private Set<String> getExternalEventsMap() {
        return xformsStaticState.getAllowedExternalEvents();
    }

    /**
     * Return whether an external event name is explicitly allowed by the configuration.
     *
     * @param eventName event name to check
     * @return          true if allowed, false otherwise
     */
    private boolean isExplicitlyAllowedExternalEvent(String eventName) {
        return !XFormsEventFactory.isBuiltInEvent(eventName) && getExternalEventsMap().contains(eventName);
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

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;

        this.goingOffline = false;
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

        public String getEncodedDocument(PropertyContext propertyContext) {
            final Document eventsDocument = Dom4jUtils.createDocument();
            final Element eventsElement = eventsDocument.addElement(XFormsConstants.XXFORMS_EVENTS_QNAME);

            final Element eventElement = eventsElement.addElement(XFormsConstants.XXFORMS_EVENT_QNAME);
            eventElement.addAttribute("name", eventName);
            eventElement.addAttribute("source-control-id", targetStaticId);
            eventElement.addAttribute("bubbles", Boolean.toString(bubbles));
            eventElement.addAttribute("cancelable", Boolean.toString(cancelable));

            return XFormsUtils.encodeXML(propertyContext, eventsDocument, false);
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

        public void toSAX(PropertyContext propertyContext, ContentHandlerHelper ch, long currentTime) {
            ch.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "server-events",
                    new String[] {
                            "delay", Long.toString(getTime() - currentTime),
                            "discardable", isMaxDelay() ? "true" : null,
                            "show-progress", Boolean.toString(isShowProgress()),
                            "progress-message", isShowProgress() ? getProgressMessage() : null
                    });
            ch.text(getEncodedDocument(propertyContext));
            ch.endElement();
        }

        public void toJSON(PropertyContext propertyContext, StringBuilder sb, long currentTime) {
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
            sb.append(getEncodedDocument(propertyContext));
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

//        if (activeSubmission != null && StringUtils.isBlank(activeSubmission.getResolvedXXForms Target())) {
//            // Scripts occurring after a submission without a target takes place should not run
//            indentedLogger.logWarning("", "xxforms:script will be ignored because two-pass submission started", "script id", scriptId);
//            return;
//        }

        // Warn that scripts won't run in noscript mode (duh)
        if (XFormsProperties.isNoscript(this))
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
        // Only keep script with:
        // o still existing target/observer
        // o relevant target/observer
        if (scriptsToRun != null && scriptsToRun.size() > 0) {
            for (final Iterator<Script> i = scriptsToRun.iterator(); i.hasNext();) {
                final Script script = i.next();

                if (!script.getEvent().getName().equals(XFormsEvents.XFORMS_DISABLED)) { // allow xforms-disabled on removed controls

                    // Check target
                    final XFormsEventTarget scriptEventTarget = script.getEvent().getTargetObject();
                    final Object currentEventTarget = getObjectByEffectiveId(scriptEventTarget.getEffectiveId());
                    if (scriptEventTarget != currentEventTarget
                            || (currentEventTarget instanceof XFormsControl && !((XFormsControl) currentEventTarget).isRelevant())) {
                        i.remove();
                        continue;
                    }

                    // Check observer
                    final XFormsEventObserver scriptEventObserver = script.getEventObserver();
                    final Object currentEventObserver = getObjectByEffectiveId(scriptEventObserver.getEffectiveId());
                    if (scriptEventObserver != currentEventObserver
                            || (currentEventObserver instanceof XFormsControl && !((XFormsControl) currentEventObserver).isRelevant())) {
                        i.remove();
                    }
                }
            }
        }
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
        private final String resource;
        private final String target;
        private final String urlType;
        private final boolean isReplace;
        private final boolean isPortletLoad;
        private final boolean isShowProgress;

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
     * @param pipelineContext   current context
     * @param event             event to dispatch
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     */
    public void handleExternalEvent(PipelineContext pipelineContext, XFormsEvent event, boolean handleGoingOnline) {

        final IndentedLogger indentedLogger = getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);

        final XFormsEventTarget eventTarget = event.getTargetObject();
        final String eventTargetEffectiveId = eventTarget.getEffectiveId();
        final String eventName = event.getName();

        try {

            indentedLogger.startHandleOperation(EVENT_LOG_TYPE, "handling external event", "target id", eventTargetEffectiveId, "event name", eventName);

            // TODO: Is this check still needed?
            if (handleGoingOnline && eventTarget instanceof XFormsSingleNodeControl) {
                final XFormsSingleNodeControl xformsControl = (XFormsSingleNodeControl) eventTarget;
                // When going online, ensure rebuild/revalidate before each event
                rebuildRecalculateIfNeeded(pipelineContext);

                // Mark the control as dirty, because we may have done a rebuild/recalculate earlier, and this means
                // the MIPs need to be re-evaluated before being checked below
                getControls().cloneInitialStateIfNeeded();
                xformsControl.markDirty();
            }

            if (!handleGoingOnline) {
                // When not going online, each event is within its own start/end outermost action handler
                startOutermostActionHandler();
            }
            {
                // Check if the value to set will be different from the current value
                if (eventTarget instanceof XFormsValueControl && event instanceof XXFormsValueChangeWithFocusChangeEvent) {
                    final XXFormsValueChangeWithFocusChangeEvent valueChangeWithFocusChangeEvent = (XXFormsValueChangeWithFocusChangeEvent) event;
                    if (valueChangeWithFocusChangeEvent.getOtherTargetObject() == null) {
                        // We only get a value change with this event
                        final String currentExternalValue = ((XFormsValueControl) eventTarget).getExternalValue(pipelineContext);
                        if (currentExternalValue != null) {
                            // We completely ignore the event if the value in the instance is the same. This also saves dispatching xxforms-repeat-focus below.
                            final boolean isIgnoreValueChangeEvent = currentExternalValue.equals(valueChangeWithFocusChangeEvent.getNewValue());
                            if (isIgnoreValueChangeEvent) {
                                indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring value change event as value is the same",
                                        "control id", eventTargetEffectiveId, "event name", eventName, "value", currentExternalValue);

                                // Ensure deferred event handling
                                // NOTE: Here this will do nothing, but out of consistency we better have matching startOutermostActionHandler/endOutermostActionHandler
                                endOutermostActionHandler(pipelineContext);
                                return;
                            }
                        } else {
                            // shouldn't happen really, but just in case let's log this
                            indentedLogger.logDebug(EVENT_LOG_TYPE, "got null currentExternalValue", "control id", eventTargetEffectiveId, "event name", eventName);
                        }
                    } else {
                        // There will be a focus event too, so don't ignore the event!
                    }
                }

                // Handle repeat focus. Don't dispatch event on DOMFocusOut however.
                if (eventTargetEffectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1
                        && !(event instanceof DOMFocusOutEvent)) {

                    // The event target is in a repeated structure, so make sure it gets repeat focus
                    dispatchEventCheckTarget(pipelineContext, new XXFormsRepeatFocusEvent(this, eventTarget));
                }

                // Interpret event
                if (eventTarget instanceof XFormsOutputControl) {
                    // Special xforms:output case

                    final XFormsOutputControl xformsOutputControl = (XFormsOutputControl) eventTarget;
                    if (event instanceof DOMFocusInEvent) {

                        // First, dispatch DOMFocusIn
                        dispatchEventCheckTarget(pipelineContext, event);

                        // Then, dispatch DOMActivate unless the control is read-only
                        if (!xformsOutputControl.isReadonly()) {
                            dispatchEventCheckTarget(pipelineContext, new DOMActivateEvent(this, xformsOutputControl));
                        }
                    } else if (!xformsOutputControl.isIgnoredExternalEvent(eventName)) {
                        // Dispatch other event
                        dispatchEventCheckTarget(pipelineContext, event);
                    }
                } else if (event instanceof XXFormsValueChangeWithFocusChangeEvent) {
                    // 4.6.7 Sequence: Value Change

                    // TODO: Not sure if this comment makes sense anymore.
                    // What we want to do here is set the value on the initial controls tree, as the value has already been
                    // changed on the client. This means that this event(s) must be the first to come!

                    final XXFormsValueChangeWithFocusChangeEvent valueChangeWithFocusChangeEvent = (XXFormsValueChangeWithFocusChangeEvent) event;
                    if (checkEventTarget(event)) {
                        // Store value into instance data through the control
                        final XFormsValueControl valueXFormsControl = (XFormsValueControl) eventTarget;
                        // NOTE: filesElement is only used by the upload control at the moment
                        valueXFormsControl.storeExternalValue(pipelineContext, valueChangeWithFocusChangeEvent.getNewValue(), null, valueChangeWithFocusChangeEvent.getFilesElement());
                    }

                    {
                        // NOTE: Recalculate and revalidate are done with the automatic deferred updates

                        // Handle focus change DOMFocusOut / DOMFocusIn
                        if (valueChangeWithFocusChangeEvent.getOtherTargetObject() != null) {

                            // We have a focus change (otherwise, the focus is assumed to remain the same)

                            // Dispatch DOMFocusOut
                            // NOTE: setExternalValue() above may cause e.g. xforms-select / xforms-deselect events to be
                            // dispatched, so we get the control again to have a fresh reference
                            dispatchEventCheckTarget(pipelineContext, new DOMFocusOutEvent(this, eventTarget));

                            // Dispatch DOMFocusIn
                            dispatchEventCheckTarget(pipelineContext, new DOMFocusInEvent(this, valueChangeWithFocusChangeEvent.getOtherTargetObject()));
                        }

                        // NOTE: Refresh is done with the automatic deferred updates
                    }

                } else {
                    // Dispatch any other allowed event
                    dispatchEventCheckTarget(pipelineContext, event);
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

    /**
     * Dispatch the event and check its target first.
     *
     * @param pipelineContext   current context
     * @param event             event to dispatch
     */
    private void dispatchEventCheckTarget(PipelineContext pipelineContext, XFormsEvent event) {
        if (checkEventTarget(event)) {
            dispatchEvent(pipelineContext, event);
        }
    }

    /**
     * Check whether an event can be be dispatched to the given object. This only checks:
     *
     * o the the target is still live
     * o that the target is not a non-relevant or readonly control
     *
     * @param event         event
     * @return              true iif the event target is allowed
     */
    private boolean checkEventTarget(XFormsEvent event) {
        final XFormsEventTarget eventTarget = event.getTargetObject();
        final Object newReference = getObjectByEffectiveId(eventTarget.getEffectiveId());
        if (eventTarget != newReference) {

            // Here, we check that the event's target is still a valid object. For example, a couple of events from the
            // UI could target controls. The first event is processed, which causes a change in the controls tree. The
            // second event would then refer to a control which no longer exist. In this case, we don't dispatch it.

            // We used to check simply by effective id, but this is not enough in some cases. We want to handle
            // controls that just "move" in a repeat. Scenario:
            //
            // o repeat with 2 iterations has xforms:input and xforms:trigger
            // o assume repeat is sorted on input value
            // o use changes value in input and clicks trigger
            // o client sends 2 events to server
            // o client processes value change and sets new value
            // o refresh takes place and causes reordering of rows
            // o client processes DOMActivate on trigger, which now has moved position, e.g. row 2 to row 1
            // o DOMActivate is dispatched to proper control (i.e. same as input was on)
            //
            // On the other hand, if the repeat iteration has disappeared, or was removed and recreated, the event is
            // not dispatched.

            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on ghost target",
                        "control id", eventTarget.getEffectiveId(), "event name", event.getName());
            }
            return false;
        }

        if (eventTarget instanceof XFormsControl) {
            final XFormsControl xformsControl = (XFormsControl) eventTarget;
            if (!xformsControl.isRelevant()) {
                // Controls accept event only if they are relevant
                if (indentedLogger.isDebugEnabled()) {
                    indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on non-relevant control",
                            "control id", eventTarget.getEffectiveId(), "event name", event.getName());
                }
                return false;
            }
            if (eventTarget instanceof XFormsSingleNodeControl) {
                final XFormsSingleNodeControl xformsSingleNodeControl = (XFormsSingleNodeControl) eventTarget;
                if (xformsSingleNodeControl.isReadonly() && !(xformsSingleNodeControl instanceof XFormsOutputControl)) {
                    // Controls accept event only if they are not readonly, except for xforms:output which may be readonly
                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on read-only control",
                                "control id", eventTarget.getEffectiveId(), "event name", event.getName());
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void dispatchEvent(PropertyContext propertyContext, XFormsEvent event) {
        // Ensure that the event uses the proper container to dispatch the event
        final XBLContainer targetContainer = event.getTargetObject().getXBLContainer(this);
        if (targetContainer == this) {
            super.dispatchEvent(propertyContext, event);
        } else {
            targetContainer.dispatchEvent(propertyContext, event);
        }
    }

    /**
     * Check whether the external event is allowed on the given target.
     *
     * @param indentedLogger    logger
     * @param eventName         event name
     * @param eventTarget       event target
     * @return                  true iif the event is allowed
     */
    public boolean checkAllowedExternalEvents(IndentedLogger indentedLogger, String eventName, XFormsEventTarget eventTarget) {
        // This is also a security measure that also ensures that somebody is not able to change values in an instance
        // by hacking external events.

        if (!isExplicitlyAllowedExternalEvent(eventName) && !eventTarget.allowExternalEvent(indentedLogger, EVENT_LOG_TYPE, eventName))
            return false;

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

        final String eventName = event.getName();
        if (XFormsEvents.XXFORMS_LOAD.equals(eventName)) {
            // Internal load event
            final XXFormsLoadEvent xxformsLoadEvent = (XXFormsLoadEvent) event;
            final ExternalContext externalContext = XFormsUtils.getExternalContext(propertyContext);
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
        } else if (XFormsEvents.XXFORMS_POLL.equals(eventName)) {
            // Poll event for submissions
            // NOP, as we check for async submission in the client event loop
        } else if (XFormsEvents.XXFORMS_ONLINE.equals(eventName)) {
            // Internal event for going online
            goOnline(propertyContext);
        } else if (XFormsEvents.XXFORMS_OFFLINE.equals(eventName)) {
            // Internal event for going offline
            goOffline(propertyContext);
        } else {
            super.performDefaultAction(propertyContext, event);
        }
    }

    public void goOnline(PropertyContext propertyContext) {
        // Dispatch to all models
        for (XFormsModel currentModel: getModels()) {
            // TODO: Dispatch to children containers?
            dispatchEvent(propertyContext, new XXFormsOnlineEvent(this, currentModel));
        }
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
                    final XFormsEvent event = new DOMActivateEvent(this, trigger);
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
        this.goingOffline = true;
    }

    public boolean goingOffline() {
        return goingOffline;
    }

    /**
     * Create an encoded dynamic state that represents the dynamic state of this XFormsContainingDocument.
     *
     * @param propertyContext       current context
     * @param isForceEncryption     whether to force encryption or not
     * @return                      encoded dynamic state
     */
    public String createEncodedDynamicState(PropertyContext propertyContext, boolean isForceEncryption) {
        return XFormsUtils.encodeXML(propertyContext, createDynamicStateDocument(),
            (isForceEncryption || XFormsProperties.isClientStateHandling(this)) ? XFormsProperties.getXFormsPassword() : null, false);
    }

    private Document createDynamicStateDocument() {

        final Document dynamicStateDocument;
        indentedLogger.startHandleOperation("", "encoding state");
        {
            dynamicStateDocument = Dom4jUtils.createDocument();
            final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
            // Add UUID
            dynamicStateElement.addAttribute("uuid", uuid);

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

        // Restore UUID
        this.uuid = dynamicStateDocument.getRootElement().attributeValue("uuid");
        indentedLogger.logDebug("initialization", "restoring UUID", "uuid", this.uuid);

        // Restore models state
        {
            // Store instances state in PipelineContext for use down the line
            final Element instancesElement = dynamicStateDocument.getRootElement().element("instances");
            pipelineContext.setAttribute(XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES, instancesElement);

            // Create XForms controls and models
            createControlsAndModels(pipelineContext);

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
        // This is called upon the first creation of the XForms engine or for testing only

        // Create XForms controls and models
        createControlsAndModels(pipelineContext);

        // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
        // order to optimize events
        // Perform deferred updates only for xforms-ready
        startOutermostActionHandler();
        {
            // Initialize models
            initializeModels(pipelineContext);

            // After initialization, some async submissions might be running
            processCompletedAsynchronousSubmissions(pipelineContext, true, true);
        }
        // End deferred behavior
        endOutermostActionHandler(pipelineContext);
    }

    private AsynchronousSubmissionManager asynchronousSubmissionManager;

    public AsynchronousSubmissionManager getAsynchronousSubmissionManager(boolean create) {
        if (asynchronousSubmissionManager == null && create)
            asynchronousSubmissionManager = new AsynchronousSubmissionManager(this);
        return asynchronousSubmissionManager;
    }

    public void processCompletedAsynchronousSubmissions(PropertyContext propertyContext, boolean skipDeferredEventHandling, boolean addPollEvent) {
        if (asynchronousSubmissionManager != null && asynchronousSubmissionManager.hasPendingAsynchronousSubmissions(propertyContext)) {
            if (!skipDeferredEventHandling)
                startOutermostActionHandler();
            asynchronousSubmissionManager.processCompletedAsynchronousSubmissions(propertyContext);
            if (!skipDeferredEventHandling)
                endOutermostActionHandler(propertyContext);

            // Remember to send a poll event if needed
            if (addPollEvent)
                asynchronousSubmissionManager.addClientDelayEventIfNeeded(propertyContext);
        }
    }

    private void createControlsAndModels(PipelineContext pipelineContext) {

        // Gather static analysis information
        xformsStaticState.analyzeIfNecessary(pipelineContext);

        // Create XForms controls
        xformsControls = new XFormsControls(this);

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

    private static final Set<String> ALLOWED_EXTERNAL_EVENTS = new HashSet<String>();
    static {
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.KEYPRESS);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_LOAD);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_OFFLINE);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_ONLINE);
        ALLOWED_EXTERNAL_EVENTS.add(XFormsEvents.XXFORMS_POLL);
    }

    public boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName) {
        return ALLOWED_EXTERNAL_EVENTS.contains(eventName);
    }
}
