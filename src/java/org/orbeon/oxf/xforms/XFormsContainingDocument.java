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
import org.apache.log4j.Level;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.state.XFormsState;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.FastStringBuffer;

import java.io.IOException;
import java.util.*;

/**
 * Represents an XForms containing document.
 *
 * The containing document includes:
 *
 * o XForms models (including multiple instances)
 * o XForms controls
 * o Event handlers hierarchy
 */
public class XFormsContainingDocument implements XFormsEventTarget, XFormsEventHandlerContainer {

    public static final String CONTAINING_DOCUMENT_PSEUDO_ID = "$containing-document$";

    // Global XForms function library
    private static XFormsFunctionLibrary functionLibrary = new XFormsFunctionLibrary();

    // Object pool this object must be returned to, if any
    private ObjectPool sourceObjectPool;

    // URI resolver
    private XFormsURIResolver uriResolver;

    // Whether this document is currently being initialized
    private boolean isInitializing;

    // A document contains models and controls
    private XFormsStaticState xformsStaticState;
    private List models = new ArrayList();
    private Map modelsMap = new HashMap();
    private XFormsControls xformsControls;

    // Client state
    private boolean dirtySinceLastRequest;
    private XFormsModelSubmission activeSubmission;
    private boolean gotSubmission;
    private boolean gotSubmissionSecondPass;
    private List messagesToRun;
    private List loadsToRun;
    private List scriptsToRun;
    private String focusEffectiveControlId;
    private String helpEffectiveControlId;

    // Global flag used during initialization only
    private boolean mustPerformInitializationFirstRefresh;

    // Legacy information
    private String legacyContainerType;
    private String legacyContainerNamespace;

    // Event information
    private static final Map ignoredXFormsOutputExternalEvents = new HashMap();
    private static final Map allowedXFormsOutputExternalEvents = new HashMap();
    private static final Map allowedXFormsUploadExternalEvents = new HashMap();
    private static final Map allowedExternalEvents = new HashMap();
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
        allowedExternalEvents.putAll(allowedXFormsOutputExternalEvents);
        allowedExternalEvents.put(XFormsEvents.XFORMS_DOM_ACTIVATE, "");
        allowedExternalEvents.put(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE, "");

        // External events allowed on xforms:submission
        allowedXFormsSubmissionExternalEvents.put(XFormsEvents.XXFORMS_SUBMIT, "");

        // External events allowed on containing document
        allowedXFormsContainingDocumentExternalEvents.put(XFormsEvents.XXFORMS_LOAD, "");

        // External events allowed on xxforms:dialog
        allowedXXFormsDialogExternalEvents.put(XFormsEvents.XXFORMS_DIALOG_CLOSE, "");
    }

    // For testing only
    private static int testAjaxToggleValue = 0;

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
     * @param xformsStaticState   XFormsEngineStaticState
     * @param uriResolver               optional URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsStaticState xformsStaticState,
                                    XFormsURIResolver uriResolver) {

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
     * Create an XFormsContainingDocument from an XFormsState object.
     *
     * @param pipelineContext   current pipeline context
     * @param xformsState       XFormsState containing static and dynamic state
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsState xformsState) {

        logDebug("containing document", "creating new ContainingDocument (static state object not provided).");

        // Create static state object
        // TODO: Handle caching of XFormsStaticState object
        xformsStaticState = new XFormsStaticState(pipelineContext, xformsState.getStaticState());

        // Restore the containing document's dynamic state
        final String encodedDynamicState = xformsState.getDynamicState();

        try {
            if (encodedDynamicState == null || encodedDynamicState.equals("")) {
                // Just for tests, we allow the dynamic state to be empty
                initialize(pipelineContext);
                xformsControls.evaluateAllControlsIfNeeded(pipelineContext);
            } else {
                // Regular case
                restoreDynamicState(pipelineContext, encodedDynamicState);
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData(getLocationData(), "re-initializing XForms containing document"));
        }
    }

    /**
     * Legacy constructor for XForms Classic.
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsModel xformsModel) {
        this.models = Collections.singletonList(xformsModel);
        this.xformsControls = new XFormsControls(this, null, null);

        if (xformsModel.getEffectiveId() != null)
            modelsMap.put(xformsModel.getEffectiveId(), xformsModel);
        xformsModel.setContainingDocument(this);

        final ExternalContext externalContext = (ExternalContext) pipelineContext.getAttribute(PipelineContext.EXTERNAL_CONTEXT);
        this.legacyContainerType = externalContext.getRequest().getContainerType();
        this.legacyContainerNamespace = externalContext.getRequest().getContainerNamespace();

        initialize(pipelineContext);
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
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModel(String modelId) {
        return (XFormsModel) ("".equals(modelId) ? models.get(0) : modelsMap.get(modelId));
    }

    /**
     * Get a list of all the models in this document.
     */
    public List getModels() {
        return models;
    }

    /**
     * Return the XForms controls.
     */
    public XFormsControls getXFormsControls() {
        return xformsControls;
    }

    /**
     * Whether the document is dirty since the last request.
     *
     * @return  whether the document is dirty since the last request
     */
    public boolean isDirtySinceLastRequest() {
        return dirtySinceLastRequest || xformsControls == null || xformsControls.isDirtySinceLastRequest();
    }

    public void markCleanSinceLastRequest() {
        this.dirtySinceLastRequest = false;
    }

    public void markDirtySinceLastRequest() {
        this.dirtySinceLastRequest = true;
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
        return (xformsStaticState == null) ? null : xformsStaticState.getScripts();
    }

    /**
     * Return the document base URI.
     */
    public String getBaseURI() {
        return (xformsStaticState == null) ? null : xformsStaticState.getBaseURI();
    }

    /**
     * Return the container type that generate the XForms page, either "servlet" or "portlet".
     */
    public String getContainerType() {
        return (xformsStaticState == null) ? legacyContainerType : xformsStaticState.getContainerType();
    }

    /**
     * Return the container namespace that generate the XForms page. Always "" for servlets.
     */
    public String getContainerNamespace() {
        return (xformsStaticState == null) ? legacyContainerNamespace : xformsStaticState.getContainerNamespace();
    }

    public Map getNamespaceMappings(Element element) {
        if (xformsStaticState != null)
            return xformsStaticState.getNamespaceMappings(element);
        else // should happen only with the legacy XForms engine
            return Dom4jUtils.getNamespaceContextNoDefault(element);
    }

    /**
     * Return external-events configuration attribute.
     */
    private Map getExternalEventsMap() {
        return (xformsStaticState == null) ? null : xformsStaticState.getExternalEventsMap();
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
     * Get object with the id specified.
     */
    public Object getObjectById(String id) {

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.getObjectByid(id);
            if (resultObject != null)
                return resultObject;
        }

        // Search in controls
        {
            final Object resultObject = xformsControls.getObjectById(id);
            if (resultObject != null)
                return resultObject;
        }

        // Check containing document
        if (id.equals(getEffectiveId()))
            return this;

        return null;
    }

    /**
     * Find the instance containing the specified node, in any model.
     *
     * @param nodeInfo  node contained in an instance
     * @return      instance containing the node
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            final XFormsInstance currentInstance = currentModel.getInstanceForNode(nodeInfo);
            if (currentInstance != null)
                return currentInstance;
        }
        // This should not happen if the node is currently in an instance!
        return null;
    }

    /**
     * Find the instance with the specified id, searching in any model.
     *
     * @param instanceId id of the instance to find
     * @return      instance containing the node
     */
    public XFormsInstance findInstance(String instanceId) {
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            final XFormsInstance currentInstance = currentModel.getInstance(instanceId);
            if (currentInstance != null)
                return currentInstance;
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

        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
        this.helpEffectiveControlId = null;
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

    public void addScriptToRun(String scriptId, String eventTargetId, String eventHandlerContainerId) {

        if (activeSubmission != null)
            throw new ValidationException("Unable to run a two-pass submission and xxforms:script within a same action sequence.", activeSubmission.getLocationData());

        if (scriptsToRun == null)
            scriptsToRun = new ArrayList();
        scriptsToRun.add(new Script(XFormsUtils.scriptIdToScriptName(scriptId), eventTargetId, eventHandlerContainerId));
    }

    public static class Script {
        private String functionName;
        private String eventTargetId;
        private String eventHandlerContainerId;

        public Script(String functionName, String eventTargetId, String eventHandlerContainerId) {
            this.functionName = functionName;
            this.eventTargetId = eventTargetId;
            this.eventHandlerContainerId = eventHandlerContainerId;
        }

        public String getFunctionName() {
            return functionName;
        }

        public String getEventTargetId() {
            return eventTargetId;
        }

        public String getEventHandlerContainerId() {
            return eventHandlerContainerId;
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

        final XFormsControl xformsControl = (XFormsControl) getObjectById(focusEffectiveControlId);
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

        final XFormsControl xformsControl = (XFormsControl) getObjectById(helpEffectiveControlId);
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
     * Execute an external event on element with id targetElementId and event eventName.
     */
    public void executeExternalEvent(PipelineContext pipelineContext, String eventName, String controlId, String otherControlId, String contextString, Element filesElement) {

        // Get event target object
        final XFormsEventTarget eventTarget;
        {
            final Object eventTargetObject = getObjectById(controlId);
            if (!(eventTargetObject instanceof XFormsEventTarget)) {
                if (XFormsProperties.isExceptionOnInvalidClientControlId(this)) {
                    throw new ValidationException("Event target id '" + controlId + "' is not an XFormsEventTarget.", getLocationData());
                } else {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring client event with invalid control id", new String[] { "control id", controlId, "event name", eventName });
                    }
                    return;
                }
            }
            eventTarget = (XFormsEventTarget) eventTargetObject;
        }

        // Don't allow for events on non-relevant, readonly or xforms:output controls (we accept focus events on
        // xforms:output though).
        // This is also a security measures that also ensures that somebody is not able to change values in an instance
        // by hacking external events.
        if (eventTarget instanceof XFormsControl) {
            // Target is a control

            if (eventTarget instanceof XXFormsDialogControl) {
                // Target is a dialog
                // Check for implicitly allowed events
                if (allowedXXFormsDialogExternalEvents.get(eventName) == null) {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on xxforms:dialog", new String[] { "control id", controlId, "event name", eventName });
                    }
                    return;
                }
            } else {
                // Target is a regular control

                // Only single-node controls accept events from the client
                if (!(eventTarget instanceof XFormsSingleNodeControl)) {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on non-single-node control", new String[] { "control id", controlId, "event name", eventName });
                    }
                    return;
                }

                final XFormsSingleNodeControl xformsControl = (XFormsSingleNodeControl) eventTarget;

                if (!xformsControl.isRelevant() || (xformsControl.isReadonly() && !(xformsControl instanceof XFormsOutputControl))) {
                    // Controls accept event only if they are relevant and not readonly, except for xforms:output which may be readonly
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event on non-relevant or read-only control", new String[] { "control id", controlId, "event name", eventName });
                    }
                    return;
                }

                if (!isExplicitlyAllowedExternalEvent(eventName)) {
                    // The event is not explicitly allowed: check for implicitly allowed events
                    if (xformsControl instanceof XFormsOutputControl) {
                        if (allowedXFormsOutputExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event on xforms:output", new String[] { "control id", controlId, "event name", eventName });
                            }
                            return;
                        }
                    } else if (xformsControl instanceof XFormsUploadControl) {
                        if (allowedXFormsUploadExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event on xforms:upload", new String[] { "control id", controlId, "event name", eventName });
                            }
                            return;
                        }
                    } else {
                        if (allowedExternalEvents.get(eventName) == null) {
                            if (XFormsServer.logger.isDebugEnabled()) {
                                logDebug("containing document", "ignoring invalid client event", new String[] { "control id", controlId, "event name", eventName });
                            }
                            return;
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
                        logDebug("containing document", "ignoring invalid client event on xforms:submission", new String[] { "control id", controlId, "event name", eventName });
                    }
                    return;
                }
            }
        } else if (eventTarget instanceof XFormsContainingDocument) {
            // Target is the containing document
            // Check for implicitly allowed events
            if (allowedXFormsContainingDocumentExternalEvents.get(eventName) == null) {
                if (XFormsServer.logger.isDebugEnabled()) {
                    logDebug("containing document", "ignoring invalid client event on containing document", new String[] { "control id", controlId, "event name", eventName });
                }
                return;
            }
        } else {
            // Target is not a control
            if (!isExplicitlyAllowedExternalEvent(eventName)) {
                // The event is not explicitly allowed
                if (XFormsServer.logger.isDebugEnabled()) {
                    logDebug("containing document", "ignoring invalid client event", new String[] { "control id", controlId, "event name", eventName });
                }
                return;
            }
        }

        // Get other event target
        final XFormsEventTarget otherEventTarget;
        {
            final Object otherEventTargetObject = (otherControlId == null) ? null : getObjectById(otherControlId);
            if (otherEventTargetObject == null) {
                otherEventTarget = null;
            } else if (!(otherEventTargetObject instanceof XFormsEventTarget)) {
                if (XFormsProperties.isExceptionOnInvalidClientControlId(this)) {
                    throw new ValidationException("Other event target id '" + otherControlId + "' is not an XFormsEventTarget.", getLocationData());
                } else {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        logDebug("containing document", "ignoring invalid client event with invalid second control id", new String[] { "control id", controlId, "event name", eventName, "second control id", otherControlId });
                    }
                    return;
                }
            } else {
                otherEventTarget = (XFormsEventTarget) otherEventTargetObject;
            }
        }

        // Handle repeat focus. Don't dispatch event on DOMFocusOut however.
        if (controlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1 && !XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)) {
            // The event target is in a repeated structure, so make sure it gets repeat focus
            dispatchEvent(pipelineContext, new XXFormsRepeatFocusEvent(eventTarget));
        }

        // Handle xforms:output
        if (eventTarget instanceof XFormsOutputControl) {

            // Note that repeat focus may have been dispatched already

            if (XFormsEvents.XFORMS_DOM_FOCUS_IN.equals(eventName)) {
                // We convert the focus event into a DOMActivate unless the control is read-only
                final XFormsOutputControl xformsOutputControl = (XFormsOutputControl) eventTarget;
                if (xformsOutputControl.isReadonly()) {
                    return;
                } else {
                    eventName = XFormsEvents.XFORMS_DOM_ACTIVATE;
                }
            } else if (ignoredXFormsOutputExternalEvents.equals(eventName)) {
                return;
            }
        }

        // Create event
        if (XFormsProperties.isAjaxTest()) {
            if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {
                if ("category-select1".equals(controlId)) {
                    if (testAjaxToggleValue == 0) {
                        testAjaxToggleValue = 1;
                        contextString = "supplier";
                    } else {
                        testAjaxToggleValue = 0;
                        contextString = "customer";
                    }
                } else if (("xforms-element-287" + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + "1").equals(controlId)) {
                    contextString = "value" + System.currentTimeMillis();
                }
            }
        }
        
        final XFormsEvent xformsEvent = XFormsEventFactory.createEvent(eventName, eventTarget, otherEventTarget, true, true, true, contextString, null, null, filesElement);

        // Interpret event
        if (xformsEvent instanceof XXFormsValueChangeWithFocusChangeEvent) {
            // 4.6.7 Sequence: Value Change

            // What we want to do here is set the value on the initial controls state, as the value
            // has already been changed on the client. This means that this event(s) must be the
            // first to come!

            final XXFormsValueChangeWithFocusChangeEvent concreteEvent = (XXFormsValueChangeWithFocusChangeEvent) xformsEvent;

            // 1. xforms-recalculate
            // 2. xforms-revalidate
            // 3. xforms-refresh performs reevaluation of UI binding expressions then dispatches
            // these events according to value changes, model item property changes and validity
            // changes
            // [n] xforms-value-changed, [n] xforms-valid or xforms-invalid, [n] xforms-enabled or
            // xforms-disabled, [n] xforms-optional or xforms-required, [n] xforms-readonly or
            // xforms-readwrite, [n] xforms-out-of-range or xforms-in-range

            final String targetControlEffectiveId;
            {
                final XFormsValueControl valueXFormsControl = (XFormsValueControl) concreteEvent.getTargetObject();
                targetControlEffectiveId = valueXFormsControl.getEffectiveId();

                // Notify the control of the value change
                final String eventValue = concreteEvent.getNewValue();
                valueXFormsControl.storeExternalValue(pipelineContext, eventValue, null);
            }

            {
                // NOTE: Recalculate and revalidate are done with the automatic deferred updates

                // Handle focus change DOMFocusOut / DOMFocusIn
                if (concreteEvent.getOtherTargetObject() != null) {

                    // NOTE: setExternalValue() above may cause e.g. xforms-select / xforms-deselect events to be
                    // dispatched, so we get the control again to have a fresh reference
                    final XFormsControl sourceXFormsControl = (XFormsControl) getObjectById(targetControlEffectiveId);

                    final XFormsControl otherTargetXFormsControl
                        = (XFormsControl) getObjectById(((XFormsControl) concreteEvent.getOtherTargetObject()).getEffectiveId());

                    // We have a focus change (otherwise, the focus is assumed to remain the same)
                    if (sourceXFormsControl != null)
                        dispatchEvent(pipelineContext, new XFormsDOMFocusOutEvent(sourceXFormsControl));
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

    /**
     * Prepare the ContainingDocumentg for a sequence of external events.
     */
    public void prepareForExternalEventsSequence(PipelineContext pipelineContext) {
        // Clear containing document state
        clearClientState();

        // Initialize controls
        xformsControls.initialize(pipelineContext);
    }

    public void startOutermostActionHandler() {
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.startOutermostActionHandler();
        }
    }

    public void endOutermostActionHandler(PipelineContext pipelineContext) {
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.endOutermostActionHandler(pipelineContext);
        }
    }

    public void synchronizeInstanceDataEventState() {
        for (Iterator i = getModels().iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.synchronizeInstanceDataEventState();
        }
    }

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return null;
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return null;
    }

    public String getId() {
        return CONTAINING_DOCUMENT_PSEUDO_ID;
    }

    public String getEffectiveId() {
        return getId();
    }

    public LocationData getLocationData() {
        return (xformsStaticState != null) ? xformsStaticState.getLocationData() : null;
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
        }
    }

    /**
     * Main event dispatching entry.
     */
    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent event) {

        if (XFormsServer.logger.isDebugEnabled()) {
            logDebug("event", "dispatching", new String[] { "name", event.getEventName(), "id", event.getTargetObject().getEffectiveId(), "location", event.getLocationData().toString() });
        }

        final XFormsEventTarget targetObject = event.getTargetObject();
        try {
            if (targetObject == null)
                throw new ValidationException("Target object null for event: " + event.getEventName(), getLocationData());

            // Find all event handler containers
            final List containers = new ArrayList();
            {
                XFormsEventHandlerContainer container
                        = (targetObject instanceof XFormsEventHandlerContainer) ? (XFormsEventHandlerContainer) targetObject : targetObject.getParentContainer(this);
                while (container != null) {
                    containers.add(container);
                    container = container.getParentContainer(this);
                }
            }

            boolean propagate = true;
            boolean performDefaultAction = true;

            // Go from root to leaf
            Collections.reverse(containers);

            // Capture phase
            for (Iterator i = containers.iterator(); i.hasNext();) {
                final XFormsEventHandlerContainer container = (XFormsEventHandlerContainer) i.next();
                final List eventHandlers = container.getEventHandlers(this);

                if (eventHandlers != null) {
                    if (container != targetObject) {
                        // Event listeners on the target which are in capture mode are not called

                        for (Iterator j = eventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) j.next();

                            if (!eventHandler.isBubblingPhase()
                                    && eventHandler.isMatchEventName(event.getEventName())
                                    && eventHandler.isMatchTarget(event.getTargetObject().getId())) {
                                // Capture phase match on event name and target is specified
                                startHandleEvent(event);
                                try {
                                    eventHandler.handleEvent(pipelineContext, XFormsContainingDocument.this, container, event);
                                } finally {
                                    endHandleEvent();
                                }
                                propagate &= eventHandler.isPropagate();
                                performDefaultAction &= eventHandler.isPerformDefaultAction();
                            }
                        }
                        // Cancel propagation if requested and if authorized by event
                        if (!propagate && event.isCancelable())
                            break;
                    }
                }
            }

            // Go from leaf to root
            Collections.reverse(containers);

            // Bubbling phase
            if (propagate && event.isBubbles()) {
                for (Iterator i = containers.iterator(); i.hasNext();) {
                    final XFormsEventHandlerContainer container = (XFormsEventHandlerContainer) i.next();
                    final List eventHandlers = container.getEventHandlers(this);

                    if (eventHandlers != null) {
                        for (Iterator j = eventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) j.next();

                            if (eventHandler.isBubblingPhase()
                                    && eventHandler.isMatchEventName(event.getEventName())
                                    && eventHandler.isMatchTarget(event.getTargetObject().getId())) {
                                // Bubbling phase match on event name and target is specified
                                startHandleEvent(event);
                                try {
                                    eventHandler.handleEvent(pipelineContext, XFormsContainingDocument.this, container, event);
                                } finally {
                                    endHandleEvent();
                                }
                                propagate &= eventHandler.isPropagate();
                                performDefaultAction &= eventHandler.isPerformDefaultAction();
                            }
                        }
                        // Cancel propagation if requested and if authorized by event
                        if (!propagate)
                            break;
                    }
                }
            }

            // Perform default action is allowed to
            if (performDefaultAction || !event.isCancelable()) {
                startHandleEvent(event);
                try {
                    targetObject.performDefaultAction(pipelineContext, event);
                } finally {
                    endHandleEvent();
                }
            }
        } catch (Exception e) {
            // Add location information if possible
            final LocationData locationData = (targetObject != null)
                    ? ((targetObject.getLocationData() != null)
                        ? targetObject.getLocationData()
                        : getLocationData())
                    : null;

            throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "dispatching XForms event",
                    new String[] { "event", event.getEventName(), "target id", targetObject.getEffectiveId() }));
        }
    }

    private int logIndentLevel = 0;
    private Stack eventStack = new Stack();

    private void startHandleEvent(XFormsEvent event) {
        eventStack.push(event);
        logIndentLevel++;
    }

    private void endHandleEvent() {
        eventStack.pop();
        logIndentLevel--;
    }

    public void startHandleOperation() {
        logIndentLevel++;
    }

    public void endHandleOperation() {
        logIndentLevel--;
    }

    private static String getLogIndentSpaces(int level) {
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < level; i++)
            sb.append("  ");
        return sb.toString();
    }

    public void logDebug(String type, String message) {
        log(Level.DEBUG, logIndentLevel, type, message, null);
    }

    public void logDebug(String type, String message, String[] parameters) {
        log(Level.DEBUG, logIndentLevel, type, message, parameters);
    }

    public static void logDebugStatic(String type, String message) {
        logDebugStatic(null, type, message);
    }

    public static void logDebugStatic(String type, String message, String[] parameters) {
        logDebugStatic(null, type, message, parameters);
    }

    public static void logDebugStatic(XFormsContainingDocument containingDocument, String type, String message) {
        log(Level.DEBUG, (containingDocument != null) ? containingDocument.logIndentLevel : 0, type, message, null);
    }

    public static void logDebugStatic(XFormsContainingDocument containingDocument, String type, String message, String[] parameters) {
        log(Level.DEBUG, (containingDocument != null) ? containingDocument.logIndentLevel : 0, type, message, parameters);
    }

    public void logWarning(String type, String message, String[] parameters) {
        log(Level.WARN, logIndentLevel, type, message, parameters);
    }

    private static void log(Level level, int indentLevel, String type, String message, String[] parameters) {
        final String parametersString;
        if (parameters != null) {
            final FastStringBuffer sb = new FastStringBuffer(" {");
            if (parameters != null) {
                boolean first = true;
                for (int i = 0; i < parameters.length; i += 2) {
                    final String paramName = parameters[i];
                    final String paramValue = parameters[i + 1];

                    if (paramValue != null) {
                        if (!first)
                            sb.append(", ");

                        sb.append(paramName);
                        sb.append(": \"");
                        sb.append(paramValue);
                        sb.append('\"');

                        first = false;
                    }
                }
            }
            sb.append('}');
            parametersString = sb.toString();
        } else {
            parametersString = "";
        }

        XFormsServer.logger.log(level, "XForms - " + getLogIndentSpaces(indentLevel) + type + " - " + message + parametersString);
    }

    /**
     * Return the event being processed by the current event handler, null if no event is being processed.
     */
    public XFormsEvent getCurrentEvent() {
        return (eventStack.size() == 0) ? null : (XFormsEvent) eventStack.peek();
    }

    /**
     * Create an encoded dynamic state that represents the dynamic state of this XFormsContainingDocument.
     *
     * @param pipelineContext       current PipelineContext
     * @return                      encoded dynamic state
     */
    public String createEncodedDynamicState(PipelineContext pipelineContext) {
        return XFormsUtils.encodeXML(pipelineContext, createDynamicStateDocument(),
            XFormsProperties.isClientStateHandling(this) ? XFormsProperties.getXFormsPassword() : null, false);
    }

    private Document createDynamicStateDocument() {

        final XFormsControls.ControlsState currentControlsState = getXFormsControls().getCurrentControlsState();

        final Document dynamicStateDocument = Dom4jUtils.createDocument();
        final Element dynamicStateElement = dynamicStateDocument.addElement("dynamic-state");
        // Output instances
        {
            final Element instancesElement = dynamicStateElement.addElement("instances");
            for (Iterator i = getModels().iterator(); i.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) i.next();

                if (currentModel.getInstances() != null) {
                    for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                        final XFormsInstance currentInstance = (XFormsInstance) j.next();

                        // TODO: can we avoid storing the instance in the dynamic state if it has not changed from static state?

                        if (currentInstance.isReplaced() || !(currentInstance instanceof SharedXFormsInstance)) {
                            // Instance has been replaced, or it is not shared, so it has to go in the dynamic state
                            instancesElement.add(currentInstance.createContainerElement(!currentInstance.isApplicationShared()));

                            // Log instance if needed
                            currentInstance.logIfNeeded(this, "storing instance to dynamic state");
                        }
                    }
                }
            }
        }

        // Output divs information
        {
            final Element divsElement = Dom4jUtils.createElement("divs");
            outputSwitchesDialogs(divsElement, getXFormsControls());

            if (divsElement.hasContent())
                dynamicStateElement.add(divsElement);
        }

        // Output repeat index information
        {
            final Map repeatIdToIndex = currentControlsState.getRepeatIdToIndex();
            if (repeatIdToIndex.size() != 0) {
                final Element repeatIndexesElement = dynamicStateElement.addElement("repeat-indexes");
                for (Iterator i = repeatIdToIndex.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String repeatId = (String) currentEntry.getKey();
                    final Integer index = (Integer) currentEntry.getValue();
                    final Element newElement = repeatIndexesElement.addElement("repeat-index");
                    newElement.addAttribute("id", repeatId);
                    newElement.addAttribute("index", index.toString());
                }
            }
        }
        return dynamicStateDocument;
    }

    public static void outputSwitchesDialogs(Element divsElement, XFormsControls xformsControls) {
        {
            final Map switchIdToSelectedCaseIdMap = xformsControls.getCurrentSwitchState().getSwitchIdToSelectedCaseIdMap();
            if (switchIdToSelectedCaseIdMap != null) {
                // There are some xforms:switch/xforms:case controls

                for (Iterator i = switchIdToSelectedCaseIdMap.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String switchId = (String) currentEntry.getKey();
                    final String selectedCaseId = (String) currentEntry.getValue();

                    // Output selected ids
                    {
                        final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        divElement.addAttribute("switch-id", switchId);
                        divElement.addAttribute("case-id", selectedCaseId);
                        divElement.addAttribute("visibility", "visible");
                    }

                    // Output deselected ids
                    final XFormsControl switchXFormsControl = (XFormsControl) xformsControls.getObjectById(switchId);
                    final List children = switchXFormsControl.getChildren();
                    if (children != null && children.size() > 0) {
                        for (Iterator j = children.iterator(); j.hasNext();) {
                            final XFormsControl caseXFormsControl = (XFormsControl) j.next();

                            if (!caseXFormsControl.getEffectiveId().equals(selectedCaseId)) {
                                final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                                divElement.addAttribute("switch-id", switchId);
                                divElement.addAttribute("case-id", caseXFormsControl.getEffectiveId());
                                divElement.addAttribute("visibility", "hidden");
                            }
                        }
                    }
                }
            }
        }
        {
            final Map dialogIdToVisibleMap = xformsControls.getCurrentDialogState().getDialogIdToVisibleMap();
            if (dialogIdToVisibleMap != null) {
                // There are some xxforms:dialog controls
                for (Iterator i = dialogIdToVisibleMap.entrySet().iterator(); i.hasNext();) {
                    final Map.Entry currentEntry = (Map.Entry) i.next();
                    final String dialogId = (String) currentEntry.getKey();

                    final XFormsControls.DialogState.DialogInfo dialogInfo
                            = (XFormsControls.DialogState.DialogInfo) currentEntry.getValue();

                    // Output element and attributes
                    {
                        final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        divElement.addAttribute("dialog-id", dialogId);
                        divElement.addAttribute("visibility", dialogInfo.isShow() ? "visible" : "hidden");
                        if (dialogInfo.isShow()) {
                            if (dialogInfo.getNeighbor() != null)
                                divElement.addAttribute("neighbor", dialogInfo.getNeighbor());
                            if (dialogInfo.isConstrainToViewport())
                                divElement.addAttribute("constrain", Boolean.toString(dialogInfo.isConstrainToViewport()));
                        }
                    }
                }
            }
        }
    }


    private void restoreDynamicState(PipelineContext pipelineContext, String encodedDynamicState) {

        // Get dynamic state document
        final Document dynamicStateDocument = XFormsUtils.decodeXML(pipelineContext, encodedDynamicState);

        // Get repeat indexes from dynamic state
        final Element repeatIndexesElement = dynamicStateDocument.getRootElement().element("repeat-indexes");

        // Create XForms controls and models
        createControlAndModel(pipelineContext, repeatIndexesElement);

        // Extract and restore instances
        {
            // Get instances from dynamic state first
            final Element instancesElement = dynamicStateDocument.getRootElement().element("instances");
            if (instancesElement != null) {
                for (Iterator i = instancesElement.elements().iterator(); i.hasNext();) {
                    final Element instanceElement = (Element) i.next();

                    // Create and set instance document on current model
                    final XFormsInstance newInstance = new XFormsInstance(instanceElement);

                    if (newInstance.getDocumentInfo() == null) {
                        // Instance is not initialized yet

                        // This means that the instance was application shared
                        if (!newInstance.isApplicationShared())
                            throw new ValidationException("Non-initialized instance has to be application shared for id: " + newInstance.getEffectiveId(), getLocationData());

                        final SharedXFormsInstance sharedInstance
                                = XFormsServerSharedInstancesCache.instance().find(pipelineContext, this, newInstance.getEffectiveId(), newInstance.getModelId(), newInstance.getSourceURI(), newInstance.getTimeToLive(), newInstance.getValidation());
                        getModel(sharedInstance.getModelId()).setInstance(sharedInstance, false);

                    } else {
                        // Instance is initialized, just use it
                        getModel(newInstance.getModelId()).setInstance(newInstance, newInstance.isReplaced());
                    }

                    // Log instance if needed
                    newInstance.logIfNeeded(this, "restoring instance from dynamic state");
                }
            }

            // Then get instances from static state if necessary
            final Map staticInstancesMap = xformsStaticState.getInstancesMap();
            if (staticInstancesMap != null && staticInstancesMap.size() > 0) {
                for (Iterator instancesIterator = staticInstancesMap.values().iterator(); instancesIterator.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) instancesIterator.next();

                    if (findInstance(currentInstance.getEffectiveId()) == null) {
                        // Instance was not set from dynamic state

                        if (currentInstance.getDocumentInfo() == null) {
                            // Instance is not initialized yet

                            // This means that the instance was application shared
                            if (!currentInstance.isApplicationShared())
                                throw new ValidationException("Non-initialized instance has to be application shared for id: " + currentInstance.getEffectiveId(), getLocationData());

                            final SharedXFormsInstance sharedInstance
                                    = XFormsServerSharedInstancesCache.instance().find(pipelineContext, this, currentInstance.getEffectiveId(), currentInstance.getModelId(), currentInstance.getSourceURI(), currentInstance.getTimeToLive(), currentInstance.getValidation());
                            getModel(sharedInstance.getModelId()).setInstance(sharedInstance, false);

                        } else {
                            // Instance is initialized, just use it
                            getModel(currentInstance.getModelId()).setInstance(currentInstance, false);
                        }
                    }
                }
            }
        }

        // Restore models state
        for (Iterator j = getModels().iterator(); j.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) j.next();
            currentModel.initializeState(pipelineContext);
        }

        // Restore controls
        final Element divsElement = dynamicStateDocument.getRootElement().element("divs");
        xformsControls.initializeState(pipelineContext, divsElement, repeatIndexesElement, true);
        xformsControls.evaluateAllControlsIfNeeded(pipelineContext);
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
        createControlAndModel(pipelineContext, null);

        // 4.2 Initialization Events

        // 1. Dispatch xforms-model-construct to all models
        // 2. Dispatch xforms-model-construct-done to all models
        // 3. Dispatch xforms-ready to all models

        // Before dispaching initialization events, remember that first refresh must be performed
        this.mustPerformInitializationFirstRefresh = true;

        final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, XFormsEvents.XFORMS_READY, XFormsEvents.XXFORMS_READY };
        for (int i = 0; i < eventsToDispatch.length; i++) {
            if (i == 2) {
                // Initialize controls after all the xforms-model-construct-done events have been sent
                xformsControls.initialize(pipelineContext);
            }

            // Group all xforms-model-construct-done and xforms-ready events within a single outermost action handler in
            // order to optimize events
            if (i == 1) {
                // Performed deferred updates only for xforms-ready
                startOutermostActionHandler();
            }

            // Iterate over all the models
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) j.next();

                // Make sure there is at least one refresh
                final XFormsModel.DeferredActionContext deferredActionContext = currentModel.getDeferredActionContext();
                if (deferredActionContext != null) {
                    deferredActionContext.refresh = true;
                }

                dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(eventsToDispatch[i], currentModel));
            }

            if (i == 2) {
                // Performed deferred updates only for xforms-ready
                endOutermostActionHandler(pipelineContext);
            }
        }

        // In case there is no model or no controls, make sure the flag is cleared as it is only relevant during
        // initialization
        this.mustPerformInitializationFirstRefresh = false;
    }

    private void createControlAndModel(PipelineContext pipelineContext, Element repeatIndexesElement) {

        if (xformsStaticState != null) {

            // Gather static analysis information
            final long startTime = XFormsServer.logger.isDebugEnabled() ? System.currentTimeMillis() : 0;
            final boolean analyzed = xformsStaticState.analyzeIfNecessary(pipelineContext);
            if (XFormsServer.logger.isDebugEnabled()) {
                if (analyzed)
                    logDebug("containing document", "performed static analysis", new String[] { "time", Long.toString(System.currentTimeMillis() - startTime) });
                else
                    logDebug("containing document", "static analysis already available");
            }

            // Create XForms controls
            xformsControls = new XFormsControls(this, xformsStaticState, repeatIndexesElement);

            // Create and index models
            for (Iterator i = xformsStaticState.getModelDocuments().iterator(); i.hasNext();) {
                final Document modelDocument = (Document) i.next();
                final XFormsModel model = new XFormsModel(modelDocument);
                model.setContainingDocument(this); // NOTE: This requires the XFormsControls to be set on XFormsContainingDocument

                this.models.add(model);
                if (model.getEffectiveId() != null)
                    this.modelsMap.put(model.getEffectiveId(), model);
            }
        }
    }
}
