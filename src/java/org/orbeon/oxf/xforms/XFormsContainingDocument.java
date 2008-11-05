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

import org.apache.commons.pool.ObjectPool;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.action.actions.XFormsInsertAction;
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
public class XFormsContainingDocument extends XFormsContainer {

    // Special id name for the top-level containing document
    public static final String CONTAINING_DOCUMENT_PSEUDO_ID = "$containing-document$";

    private IndentedLogger indentedLogger = new IndentedLogger(XFormsServer.logger, "XForms");

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
    private List asynchronousSubmissions;   // List<Runnable runnable>
    private boolean gotSubmission;
    private boolean gotSubmissionSecondPass;
    private boolean gotSubmissionReplaceAll;
    private List messagesToRun;
    private List loadsToRun;                // List<Load load>
    private List scriptsToRun;              // List<Script script>
    private String focusEffectiveControlId;
    private String helpEffectiveControlId;
    private List delayedEvents;             // List<DelayedEvent delayedEvents>

    private boolean goingOffline;
    private boolean goingOnline;

    // Global flag used during initialization only
    private boolean mustPerformInitializationFirstRefresh;

    // Event information
    private static final Map ignoredXFormsOutputExternalEvents = new HashMap();
    private static final Map allowedXFormsOutputExternalEvents = new HashMap();
    private static final Map allowedXFormsUploadExternalEvents = new HashMap();
    private static final Map allowedXFormsControlsExternalEvents = new HashMap();

    private static final Map allowedXFormsRepeatExternalEvents = new HashMap();
    private static final Map allowedXFormsSubmissionExternalEvents = new HashMap();
    private static final Map allowedXFormsContainingDocumentExternalEvents = new HashMap();
    private static final Map allowedXXFormsDialogExternalEvents = new HashMap();
    static {
        // External events ignored on xforms:output
        ignoredXFormsOutputExternalEvents.put(XFormsEvents.XFORMS_DOM_FOCUS_IN, "");
        ignoredXFormsOutputExternalEvents.put(XFormsEvents.XFORMS_DOM_FOCUS_OUT, "");

        // External events allowed on xforms:output
        allowedXFormsOutputExternalEvents.putAll(ignoredXFormsOutputExternalEvents);
        allowedXFormsOutputExternalEvents.put(XFormsEvents.XFORMS_HELP, "");

        // External events allowed on xforms:upload
        allowedXFormsUploadExternalEvents.putAll(allowedXFormsOutputExternalEvents);
        allowedXFormsUploadExternalEvents.put(XFormsEvents.XFORMS_SELECT, "");
        allowedXFormsUploadExternalEvents.put(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, "");

        // External events allowed on other controls
        allowedXFormsControlsExternalEvents.putAll(allowedXFormsOutputExternalEvents);
        allowedXFormsControlsExternalEvents.put(XFormsEvents.XFORMS_DOM_ACTIVATE, "");
        allowedXFormsControlsExternalEvents.put(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, "");
        allowedXFormsControlsExternalEvents.put(XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE, "");// for noscript mode

        // External events allowed on xforms:repeat
        allowedXFormsRepeatExternalEvents.put(XFormsEvents.XXFORMS_DND, "");

        // External events allowed on xforms:submission
        allowedXFormsSubmissionExternalEvents.put(XFormsEvents.XXFORMS_SUBMIT, "");

        // External events allowed on containing document
        allowedXFormsContainingDocumentExternalEvents.put(XFormsEvents.XXFORMS_LOAD, "");
        allowedXFormsContainingDocumentExternalEvents.put(XFormsEvents.XXFORMS_OFFLINE, "");
        allowedXFormsContainingDocumentExternalEvents.put(XFormsEvents.XXFORMS_ONLINE, "");

        // External events allowed on xxforms:dialog
        allowedXXFormsDialogExternalEvents.put(XFormsEvents.XXFORMS_DIALOG_CLOSE, "");
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

        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null);
        setLocationData(xformsStaticState.getLocationData());

        logDebug("containing document", "creating new ContainingDocument (static state object provided).");

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

    /**
     * Restore an XFormsContainingDocument from XFormsState and XFormsStaticState.
     *
     * @param pipelineContext         current pipeline context
     * @param xformsState             static and dynamic state information
     * @param xformsStaticState       static state object, or null if not available
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsState xformsState, XFormsStaticState xformsStaticState) {

        super(CONTAINING_DOCUMENT_PSEUDO_ID, CONTAINING_DOCUMENT_PSEUDO_ID, "", null);

        if (xformsStaticState != null) {
            // Use passed static state object
            logDebug("containing document", "restoring containing document (static state object provided).");
            this.xformsStaticState = xformsStaticState;
        } else {
            // Create static state object
            // TODO: Handle caching of XFormsStaticState object? Anything that can be done here?
            logDebug("containing document", "restoring containing document (static state object not provided).");
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
                xformsControls.evaluateControlValuesIfNeeded(pipelineContext);
            } else {
                // Regular case
                restoreDynamicState(pipelineContext, encodedDynamicState);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
        }
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
    public Map getScripts() {
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

    public Map getNamespaceMappings(Element element) {
        return xformsStaticState.getNamespaceMappings(element);
    }

    /**
     * Return external-events configuration attribute.
     */
    private Map getExternalEventsMap() {
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

        return null;
    }

    /**
     * Resolve an object. This optionally depends on a source, and involves resolving whether the source is within a
     * repeat or a component.
     *
     * @param effectiveSourceId  effective id of the source, or null
     * @param targetId           id of the target
     * @return                   object, or null if not found
     */
    public Object resolveObjectById(String effectiveSourceId, String targetId) {

        // Search in parent (models and this)
        {
            final Object resultObject = super.resolveObjectById(effectiveSourceId, targetId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in controls
        {
            final Object resultObject = xformsControls.resolveObjectById(effectiveSourceId, targetId);
            if (resultObject != null)
                return resultObject;
        }

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
        this.asynchronousSubmissions = null;

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
        this.helpEffectiveControlId = null;
        this.delayedEvents = null;

        this.goingOffline = false;
        this.goingOnline = false;
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

        if (scriptsToRun != null)
            throw new ValidationException("Unable to run a two-pass submission and xxforms:script within a same action sequence.", activeSubmission.getLocationData());

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
     * Add an asynchronous submission to run after sending the Ajax response.
     *
     * @param runnable  Runnable that will execute the submission
     */
    public void addAsynchronousSubmission(Runnable runnable) {
        if (asynchronousSubmissions == null)
            asynchronousSubmissions = new ArrayList();
        asynchronousSubmissions.add(runnable);
    }

    /**
     * Process pending asynchronous submissions.
     */
    public void processAsynchronousSubmissions() {
        // NOTE: See http://wiki.orbeon.com/forms/projects/asynchronous-submissions
        if (asynchronousSubmissions != null && asynchronousSubmissions.size() > 0) {
            for (Iterator i = asynchronousSubmissions.iterator(); i.hasNext();) {
                final Runnable currentRunnable = (Runnable) i.next();
                try {
                    // Run submission
                    currentRunnable.run();
                } catch (RuntimeException e) {
                    // Something happened but we keep going
                    XFormsServer.logger.debug("XForms (async) - asynchronous submission: throwable caught.", e);
                }
                // Remove submission from list of submission so we can gc the Runnable
                i.remove();
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
            messagesToRun = new ArrayList();
        messagesToRun.add(new Message(message, level));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List getMessagesToRun() {
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
     */
    public void addDelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, int delay, boolean showProgress) {
        if (delayedEvents == null)
            delayedEvents = new ArrayList();

        delayedEvents.add(new DelayedEvent(eventName, targetStaticId, bubbles, cancelable, System.currentTimeMillis() + delay, showProgress));
    }

    public List getDelayedEvents() {
        return delayedEvents;
    }

    public static class DelayedEvent {
        private String eventName;
        private String targetStaticId;
        private boolean bubbles;
        private boolean cancelable;
        private long time;
        private boolean showProgress;

        public DelayedEvent(String eventName, String targetStaticId, boolean bubbles, boolean cancelable, long time, boolean showProgress) {
            this.eventName = eventName;
            this.targetStaticId = targetStaticId;
            this.bubbles = bubbles;
            this.cancelable = cancelable;
            this.time = time;
            this.showProgress = showProgress;
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

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xxforms:script within a same action sequence.", activeSubmission.getLocationData());

        // Warn that scripts won't run in noscript mode (duh)
        if (XFormsProperties.isNoscript(this))
            logWarning("noscript", "script won't run in noscript mode", new String[] { "script id", scriptId });

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList();
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

    public List getScriptsToRun() {
        return scriptsToRun;
    }

    /**
     * Add an XForms load to send to the client.
     */
    public void addLoadToRun(String resource, String target, String urlType, boolean isReplace, boolean isPortletLoad, boolean isShowProgress) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xforms:load within a same action sequence.", activeSubmission.getLocationData());

        if (loadsToRun == null)
            loadsToRun = new ArrayList();
        loadsToRun.add(new Load(resource, target, urlType, isReplace, isPortletLoad, isShowProgress));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List getLoadsToRun() {
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
     * This can be called several times, but only the last controld id is remembered.
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

        // Get event target object
        XFormsEventTarget eventTarget;
        {
            final Object eventTargetObject = getObjectByEffectiveId(targetEffectiveId);
            if (!(eventTargetObject instanceof XFormsEventTarget)) {
                if (XFormsProperties.isExceptionOnInvalidClientControlId(this)) {
                    throw new ValidationException("Event target id '" + targetEffectiveId + "' is not an XFormsEventTarget.", getLocationData());
                } else {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring client event with invalid target id", new String[] { "target id", targetEffectiveId, "event name", eventName });
                    }
                    return;
                }
            }
            eventTarget = (XFormsEventTarget) eventTargetObject;
        }

        if (isTrustedEvent) {
            // Event is trusted, don't check if it is allowed
            if (XFormsServer.logger.isDebugEnabled()) {
                logDebug("containing document", "processing trusted event", new String[] { "target id", targetEffectiveId, "event name", eventName });
            }
        } else if (!checkForAllowedEvents(pipelineContext, eventName, eventTarget, handleGoingOnline)) {
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
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event with invalid second control id", new String[] { "target id", targetEffectiveId, "event name", eventName, "second control id", otherControlEffectiveId });
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
//        if (XFormsProperties.isAjaxTest()) {
//            if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
//                if ("category-select1".equals(controlId)) {
//                    if (testAjaxToggleValue == 0) {
//                        testAjaxToggleValue = 1;
//                        valueString = "supplier";
//                    } else {
//                        testAjaxToggleValue = 0;
//                        valueString = "customer";
//                    }
//                } else if (("xforms-element-287" + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + "1").equals(controlId)) {
//                    valueString = "value" + System.currentTimeMillis();
//                }
//            }
//        }

        if (!handleGoingOnline) {
            // When not going online, each event is within its own start/end outermost action handler
            startOutermostActionHandler();
        }
        {
            // Create event
            final XFormsEvent xformsEvent = XFormsEventFactory.createEvent(eventName, eventTarget, otherEventTarget,
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
                                logDebug("containing document", "ignoring value change event as value is the same",
                                        new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName, "value", currentExternalValue });

                                // Ensure deferred event handling
                                // NOTE: Here this will do nothing, but out of consistency we better have matching startOutermostActionHandler/endOutermostActionHandler
                                endOutermostActionHandler(pipelineContext);
                                return;
                            }
                        } else {
                            // shouldn't happen really, but just in case let's log this
                            logDebug("containing document", "got null currentExternalValue", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                        }
                    } else {
                        // There will be a focus event too, so don't ignore
                    }
                }

                // Dispatch repeat focus event
                {
                    // The event target is in a repeated structure, so make sure it gets repeat focus
                    dispatchEvent(pipelineContext, new XXFormsRepeatFocusEvent(eventTarget));
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
                        dispatchEvent(pipelineContext, new XFormsDOMActivateEvent(xformsOutputControl));
                    }
                } else if (!ignoredXFormsOutputExternalEvents.equals(eventName)) {
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
                    valueXFormsControl.storeExternalValue(pipelineContext, valueChangeWithFocusChangeEvent.getNewValue(), null);
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
                        if (sourceXFormsControl != null)
                            dispatchEvent(pipelineContext, new XFormsDOMFocusOutEvent(sourceXFormsControl));

                        // Dispatch DOMFocusIn
                        final XFormsControl otherTargetXFormsControl
                            = (XFormsControl) getObjectByEffectiveId(((XFormsControl) valueChangeWithFocusChangeEvent.getOtherTargetObject()).getEffectiveId());
                        if (otherTargetXFormsControl != null)
                            dispatchEvent(pipelineContext, new XFormsDOMFocusInEvent(otherTargetXFormsControl));
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
    }

    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent event) {
        // Ensure that the event uses the proper container to dispatch the event
        final XFormsContainer targetContainer = event.getTargetObject().getContainer(this);
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
     * @param eventName         event name
     * @param eventTarget       event target
     * @param handleGoingOnline whether we are going online and therefore using optimized event handling
     * @return                  true iif the event is allowed
     */
    private boolean checkForAllowedEvents(PipelineContext pipelineContext, String eventName, XFormsEventTarget eventTarget, boolean handleGoingOnline) {
        // Don't allow for events on non-relevant, readonly or xforms:output controls (we accept focus events on
        // xforms:output though).
        // This is also a security measure that also ensures that somebody is not able to change values in an instance
        // by hacking external events.
        if (eventTarget instanceof XFormsControl) {
            // Target is a control

            if (eventTarget instanceof XXFormsDialogControl) {
                // Target is a dialog
                // Check for implicitly allowed events
                if (allowedXXFormsDialogExternalEvents.get(eventName) == null) {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on xxforms:dialog", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                    }
                    return false;
                }
            } else if (eventTarget instanceof XFormsRepeatControl) {
                // Target is a repeat
                if (allowedXFormsRepeatExternalEvents.get(eventName) == null) {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on xforms:repeat", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                    }
                    return false;
                }

            } else {
                // Target is a regular control

                // Only single-node controls accept events from the client
                if (!(eventTarget instanceof XFormsSingleNodeControl)) {// NOTE: This includes xforms:trigger/xforms:submit
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on non-single-node control", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                    }
                    return false;
                }

                final XFormsSingleNodeControl xformsControl = (XFormsSingleNodeControl) eventTarget;

                if (handleGoingOnline) {
                    // When going online, ensure rebuild/revalidate before each event
                    rebuildRevalidateIfNeeded(pipelineContext);

                    // Mark the control as dirty, because we may have done a rebuild/recalculate earlier, and this means
                    // the MIPs need to be re-evaluated before being checked below
                    getControls().cloneInitialStateIfNeeded();
                    xformsControl.markDirty();
                }

                if (!xformsControl.isRelevant() || (xformsControl.isReadonly() && !(xformsControl instanceof XFormsOutputControl))) {
                    // Controls accept event only if they are relevant and not readonly, except for xforms:output which may be readonly
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on non-relevant or read-only control", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                    }
                    return false;
                }

                if (!isExplicitlyAllowedExternalEvent(eventName)) {
                    // The event is not explicitly allowed: check for implicitly allowed events
                    if (xformsControl instanceof XFormsOutputControl) {
                        if (allowedXFormsOutputExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event on xforms:output", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                            }
                            return false;
                        }
                    } else if (xformsControl instanceof XFormsUploadControl) {
                        if (allowedXFormsUploadExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event on xforms:upload", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
                            }
                            return false;
                        }
                    } else {
                        if (allowedXFormsControlsExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event", new String[] { "control id", eventTarget.getEffectiveId(), "event name", eventName });
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
                if (allowedXFormsSubmissionExternalEvents.get(eventName) == null) {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on xforms:submission", new String[] { "submission id", eventTarget.getEffectiveId(), "event name", eventName });
                    }
                    return false;
                }
            }
        } else if (eventTarget instanceof XFormsContainingDocument) {
            // Target is the containing document
            // Check for implicitly allowed events
            if (allowedXFormsContainingDocumentExternalEvents.get(eventName) == null) {
                if (XFormsServer.logger.isDebugEnabled()) {
                    logDebug("containing document", "ignoring invalid client event on containing document", new String[] { "target id", eventTarget.getEffectiveId(), "event name", eventName });
                }
                return false;
            }
        } else {
            // Target is not a control
            if (!isExplicitlyAllowedExternalEvent(eventName)) {
                // The event is not explicitly allowed
                if (XFormsServer.logger.isDebugEnabled()) {
                    logDebug("containing document", "ignoring invalid client event", new String[] { "target id", eventTarget.getEffectiveId(), "event name", eventName });
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

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {

        final String eventName = event.getEventName();
        if (XFormsEvents.XXFORMS_LOAD.equals(eventName)) {
            // Internal load event
            final XXFormsLoadEvent xxformsLoadEvent = (XXFormsLoadEvent) event;
            final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
            try {
                final String resource = xxformsLoadEvent.getResource();

                final String pathInfo;
                final Map parameters;

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
        } else if (XFormsEvents.XXFORMS_ONLINE.equals(eventName)) {
            // Internal event for going online
            goOnline(pipelineContext);
        } else if (XFormsEvents.XXFORMS_OFFLINE.equals(eventName)) {
            // Internal event for going offline
            goOffline(pipelineContext);
        }
    }

    public void goOnline(PipelineContext pipelineContext) {
        // Dispatch to all models
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            // TODO: Dispatch to children containers
            dispatchEvent(pipelineContext, new XXFormsOnlineEvent(currentModel));
        }
        this.goingOnline = true;
        this.goingOffline = false;
    }

    public void goOffline(PipelineContext pipelineContext) {

        // Handle inserts of controls marked as "offline insert triggers"
        final List offlineInsertTriggerPrefixedIds = getStaticState().getOfflineInsertTriggerIds();
        if (offlineInsertTriggerPrefixedIds != null) {
            
            for (Iterator i = offlineInsertTriggerPrefixedIds.iterator(); i.hasNext();) {
                final String currentPrefixedId = (String) i.next();
                final Object o = getObjectByEffectiveId(currentPrefixedId);// NOTE: won't work for triggers within repeats
                if (o instanceof XFormsTriggerControl) {
                    final XFormsTriggerControl trigger = (XFormsTriggerControl) o;
                    final XFormsEvent event = new XFormsDOMActivateEvent(trigger);
                    // This attribute is a temporary HACK, used to improve performance when going offline. It causes
                    // the insert action to not rebuild controls to adjust indexes after insertion, as well as always
                    // inserting based on the last node of the insert nodes-set. This probably wouldn't be needed if
                    // insert performance was good from the get go.
                    // TODO: check above now that repeat/insert/delete has been improved
                    event.setAttribute(XFormsInsertAction.NO_INDEX_ADJUSTMENT, new SequenceExtent(new Item[] { BooleanValue.TRUE }));
                    // Dispatch event n times
                    final int repeatCount = XFormsProperties.getOfflineRepeatCount(this);
                    for (int j = 0; j < repeatCount; j++)
                        dispatchEvent(pipelineContext, event);
                }
            }
        }

        // Dispatch xxforms-offline to all models
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            // TODO: Dispatch to children containers
            dispatchEvent(pipelineContext, new XXFormsOfflineEvent(currentModel));
        }
        this.goingOnline = false;
        this.goingOffline = true;
    }

    public boolean goingOffline() {
        return goingOffline;
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    public void startHandleOperation() {
        indentedLogger.startHandleOperation();
    }

    public void startHandleOperation(String type, String message) {
        indentedLogger.startHandleOperation(type, message);
    }

    public void endHandleOperation() {
        indentedLogger.endHandleOperation();
    }

    public void logDebug(String type, String message) {
        indentedLogger.logDebug(type, message);
    }

    public void logDebug(String type, String message, String[] parameters) {
        indentedLogger.logDebug(type, message, parameters);
    }

    public static void logDebugStatic(String type, String message, String[] parameters) {
        logDebugStatic(null, type, message, parameters);
    }

    public static void logDebugStatic(XFormsContainingDocument containingDocument, String type, String message, String[] parameters) {
        if (containingDocument != null)
            containingDocument.logDebug(type, message, parameters);
        else
            IndentedLogger.logDebugStatic(XFormsServer.logger, "XForms", type, message, parameters);
    }

    public void logWarning(String type, String message, String[] parameters) {
        indentedLogger.logWarning(type, message, parameters);
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

        final Document dynamicStateDocument = Dom4jUtils.createDocument();
        final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
        // Serialize instances
        {
            final Element instancesElement = dynamicStateElement.addElement("instances");
            serializeInstances(instancesElement);
        }

        // Serialize controls
        xformsControls.serializeControls(dynamicStateElement);

        return dynamicStateDocument;
    }


    private void restoreDynamicState(PipelineContext pipelineContext, String encodedDynamicState) {

        // Get dynamic state document
        final Document dynamicStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedDynamicState);

        // Create XForms controls and models
        createControlsAndModels(pipelineContext);

        // Extract and restore instances
        {
            final Element instancesElement = dynamicStateDocument.getRootElement().element("instances");
            restoreInstances(pipelineContext, instancesElement);
        }

        // Restore models state
        restoreModelsState(pipelineContext);

        // Restore controls state
        {
            xformsControls.initializeState(pipelineContext, true);
            xformsControls.deserializeControls(dynamicStateDocument.getRootElement());
            xformsControls.evaluateControlValuesIfNeeded(pipelineContext);
        }
    }

    /**
     * Whether, during initialization, this is the first refresh. The flag is automatically cleared during this call so
     * that only the first call returns true.
     *
     * @return  true if this is the first refresh, false otherwise
     */
    public boolean isInitializationFirstRefreshClear() {
        boolean result = mustPerformInitializationFirstRefresh;
        mustPerformInitializationFirstRefresh = false;
        return result;
    }

    private void initialize(PipelineContext pipelineContext) {
        // This is called upon the first creation of the XForms engine only

        // Create XForms controls and models
        createControlsAndModels(pipelineContext);

        // Before dispaching initialization events, remember that first refresh must be performed
        this.mustPerformInitializationFirstRefresh = XFormsProperties.isDispatchInitialEvents(this);

        // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
        // order to optimize events
        // Perform deferred updates only for xforms-ready
        startOutermostActionHandler();

        // Initialize models
        initializeModels(pipelineContext);

        // End deferred behavior
        endOutermostActionHandler(pipelineContext);

        // In case there is no model or no controls, make sure the flag is cleared as it is only relevant during
        // initialization
        this.mustPerformInitializationFirstRefresh = false;
    }

    private void createControlsAndModels(PipelineContext pipelineContext) {

        // Gather static analysis information
        final long startTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
        final boolean analyzed = xformsStaticState.analyzeIfNecessary(pipelineContext);
        if (XFormsServer.logger.isDebugEnabled()) {
            if (analyzed)
                logDebug("containing document", "performed static analysis",
                        new String[] {
                                "time", Long.toString(System.currentTimeMillis() - startTime),
                                "controls", Integer.toString(xformsStaticState.getControlInfoMap().size())
                        });
            else
                logDebug("containing document", "static analysis already available");
        }

        // Create XForms controls
        xformsControls = new XFormsControls(this);

        // Add models
        addAllModels();
    }

    protected void initializeNestedControls(PipelineContext pipelineContext) {
        // Call-back from super class models initialization
        xformsControls.initialize(pipelineContext);
    }
}
