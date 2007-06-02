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
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.NetUtils;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsValueControl;
import org.orbeon.oxf.xforms.control.controls.XFormsOutputControl;
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsDialogControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xforms.processor.XFormsState;
import org.orbeon.oxf.xforms.processor.XFormsURIResolver;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

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

//    private static int xxxToggleValue = 0;

//    private LocationData locationData; // At some point we need to be able to store this

    // Object pool this object must be returned to, if any
    private ObjectPool sourceObjectPool;

    // URI resolver
    private XFormsURIResolver uriResolver;

    // XPath evaluator
    private DocumentXPathEvaluator documentXPathEvaluator = new DocumentXPathEvaluator();

    // A document contains models and controls
    private XFormsStaticState xformsStaticState;
    private List models = new ArrayList();
    private Map modelsMap = new HashMap();
    private XFormsControls xformsControls;

    // Client state
    private XFormsModelSubmission activeSubmission;
    private boolean gotSubmission;
    private List messagesToRun;
    private List loadsToRun;
    private List scriptsToRun;
    private String focusEffectiveControlId;

    private XFormsActionInterpreter actionInterpreter;

    // Legacy information
    private String legacyContainerType;
    private String legacyContainerNamespace;

    // Event information
    private static final Map allowedXFormsOutputExternalEvents = new HashMap();
    private static final Map allowedXFormsUploadExternalEvents = new HashMap();
    private static final Map allowedExternalEvents = new HashMap();
    private static final Map allowedXFormsSubmissionExternalEvents = new HashMap();
    private static final Map allowedXFormsContainingDocumentExternalEvents = new HashMap();
    private static final Map allowedXXFormsDialogExternalEvents = new HashMap();
    static {

        // External events allowed on xforms:output
        allowedXFormsOutputExternalEvents.put(XFormsEvents.XFORMS_DOM_FOCUS_IN, "");
        allowedXFormsOutputExternalEvents.put(XFormsEvents.XFORMS_DOM_FOCUS_OUT, "");

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

    /**
     * Create an XFormsContainingDocument from an XFormsEngineStaticState object.
     *
     * @param pipelineContext           current pipeline context
     * @param xformsStaticState   XFormsEngineStaticState
     * @param uriResolver               optional URIResolver for loading instances during initialization (and possibly more, such as schemas and "GET" submissions upon initialization)
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsStaticState xformsStaticState,
                                    XFormsURIResolver uriResolver) {

        XFormsServer.logger.debug("XForms - creating new ContainingDocument (static state object provided).");

        // Remember static state
        this.xformsStaticState = xformsStaticState;

        // Remember URI resolver for initialization
        this.uriResolver = uriResolver;

        // Initialize the containing document
        initialize(pipelineContext);

        // Clear URI resolver, since it is of no use after initialization
        this.uriResolver = null;
    }

    /**
     * Create an XFormsContainingDocument from an XFormsState object.
     *
     * @param pipelineContext   current pipeline context
     * @param xformsState       XFormsState containing static and dynamic state
     */
    public XFormsContainingDocument(PipelineContext pipelineContext, XFormsState xformsState) {

        XFormsServer.logger.debug("XForms - creating new ContainingDocument (static state object not provided).");

        // Create static state object
        // TODO: Handle caching of XFormsEngineStaticState object
        xformsStaticState = new XFormsStaticState(pipelineContext, xformsState.getStaticState());

        // Restore the containing document's dynamic state
        final String encodedDynamicState = xformsState.getDynamicState();

        if (encodedDynamicState == null || encodedDynamicState.equals("")) {
            // Just for tests, we allow the dynamic state to be empty
            initialize(pipelineContext);
        } else {
            // Regular case
            restoreDynamicState(pipelineContext, encodedDynamicState);
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

    public DocumentXPathEvaluator getEvaluator() {
        return documentXPathEvaluator;
    }

    public XFormsURIResolver getURIResolver() {
        return uriResolver;
    }

    public void setURIResolver(XFormsURIResolver uriResolver) {
        this.uriResolver = uriResolver;
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

    /**
     * Return the state handling strategy for this document, either "client" or "session".
     */
    public String getStateHandling() {
        return (xformsStaticState == null) ? null : xformsStaticState.getStateHandling();
    }

    public boolean isSessionStateHandling() {
        return (xformsStaticState != null) && xformsStaticState.getStateHandling().equals(XFormsConstants.XXFORMS_STATE_HANDLING_SESSION_VALUE);
    }

    /**
     * Return whether this form is read-only or not.
     */
    public boolean isReadonly() {
        return (xformsStaticState != null) && xformsStaticState.isReadonly();
    }

    /**
     * Return read-only appearance configuration attribute.
     */
    public String getReadonlyAppearance() {
        return (xformsStaticState == null) ? null : xformsStaticState.getReadonlyAppearance();
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
    public Object getObjectById(PipelineContext pipelineContext, String id) {

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.getObjectByid(pipelineContext, id);
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
    public XFormsModelSubmission getActiveSubmission() {
        return activeSubmission;
    }

    /**
     * Clear current client state.
     */
    private void clearClientState() {
        this.activeSubmission = null;
        this.gotSubmission = false;
        this.messagesToRun = null;
        this.loadsToRun = null;
        this.scriptsToRun = null;
        this.focusEffectiveControlId = null;
    }

    /**
     * Set the active submission.
     *
     * This can be called with a non-null value at most once.
     */
    public void setClientActiveSubmission(XFormsModelSubmission activeSubmission) {
        if (this.activeSubmission != null)
            throw new OXFException("There is already an active submission.");
        this.activeSubmission = activeSubmission;
    }

    public boolean isGotSubmission() {
        return gotSubmission;
    }

    public void setGotSubmission(boolean gotSubmission) {
        this.gotSubmission = gotSubmission;
    }

    /**
     * Add an XForms message to send to the client.
     */
    public void addMessageToRun(String message, String level) {
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
        this.focusEffectiveControlId = effectiveControlId;
    }

    /**
     * Return the effective control id of the control to set the focus to, or null.
     */
    public String getClientFocusEffectiveControlId(PipelineContext pipelineContext) {

        if (focusEffectiveControlId == null)
            return null;

        final XFormsControl xformsControl = (XFormsControl) getObjectById(pipelineContext, focusEffectiveControlId);
        // It doesn't make sense to tell the client to set the focus to an element that is non-relevant or readonly  
        if (xformsControl != null && xformsControl.isRelevant() && !xformsControl.isReadonly())
            return focusEffectiveControlId;
        else
            return null;
    }

    /**
     * Execute an external event on element with id targetElementId and event eventName.
     */
    public void executeExternalEvent(PipelineContext pipelineContext, String eventName, String controlId, String otherControlId, String contextString, Element filesElement) {

        // Get event target object
        final XFormsEventTarget eventTarget;
        {
            final Object eventTargetObject = getObjectById(pipelineContext, controlId);
            if (!(eventTargetObject instanceof XFormsEventTarget)) {
                if (XFormsUtils.isExceptionOnInvalidClientControlId()) {
                    throw new OXFException("Event target id '" + controlId + "' is not an XFormsEventTarget.");
                } else {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        XFormsServer.logger.debug("XForms - ignoring client event with invalid control id: " + controlId);
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
                    return;
                }
            } else {
                // Target is a regular control
                final XFormsControl xformsControl = (XFormsControl) eventTarget;

                if (!xformsControl.isRelevant() || (xformsControl.isReadonly() && !(xformsControl instanceof XFormsOutputControl))) {
                    // Controls accept event only if they are relevant and not readonly, except for xforms:output which may be readonly
                    return;
                }

                if (!isExplicitlyAllowedExternalEvent(eventName)) {
                    // The event is not explicitly allowed: check for implicitly allowed events
                    if (xformsControl instanceof XFormsOutputControl) {
                        if (allowedXFormsOutputExternalEvents.get(eventName) == null) {
                            return;
                        }
                    } else if (xformsControl instanceof XFormsUploadControl) {
                        if (allowedXFormsUploadExternalEvents.get(eventName) == null) {
                            return;
                        }
                    } else {
                        if (allowedExternalEvents.get(eventName) == null) {
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
                    return;
                }
            }
        } else if (eventTarget instanceof XFormsContainingDocument) {
            // Target is the containing document
            // Check for implicitly allowed events
            if (allowedXFormsContainingDocumentExternalEvents.get(eventName) == null) {
                return;
            }
        } else {
            // Target is not a control
            if (!isExplicitlyAllowedExternalEvent(eventName)) {
                // The event is not explicitly allowed
                return;
            }
        }

        // Get other event target
        final XFormsEventTarget otherEventTarget;
        {
            final Object otherEventTargetObject = (otherControlId == null) ? null : getObjectById(pipelineContext, otherControlId);
            if (otherEventTargetObject == null) {
                otherEventTarget = null;
            } else if (!(otherEventTargetObject instanceof XFormsEventTarget)) {
                if (XFormsUtils.isExceptionOnInvalidClientControlId()) {
                    throw new OXFException("Other event target id '" + otherControlId + "' is not an XFormsEventTarget.");
                } else {
                    if (XFormsServer.logger.isDebugEnabled()) {
                        XFormsServer.logger.debug("XForms - ignoring client event with invalid second control id: " + otherControlId);
                    }
                    return;
                }
            } else {
                otherEventTarget = (XFormsEventTarget) otherEventTargetObject;
            }
        }

        // Handle repeat focus
        if (controlId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1) {
            // The event target is in a repeated structure, so make sure it gets repeat focus
            dispatchEvent(pipelineContext, new XXFormsRepeatFocusEvent(eventTarget));
        }

        // Don't actually dispatch event to xforms:output (but repeat focus may have been dispatched)
        if (eventTarget instanceof XFormsOutputControl) {
            return;

            // NOTE: We always receive DOMFocusIn from the client on xforms:output. Would it make sense to turn this
            // into a DOMActivate if the control is not read-only?

//            final XFormsOutputControl xformsOutputControl = (XFormsOutputControl) eventTarget;
//            if (xformsOutputControl.isReadonly()) ...
        }

        // Create event

//        if (eventName.equals(XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)) {// XXX HACK FOR PERF TEST
//            if ("category-select1".equals(controlId)) {
//                if (xxxToggleValue == 0) {
//                    xxxToggleValue = 1;
//                    contextString = "supplier";
//                } else {
//                    xxxToggleValue = 0;
//                    contextString = "customer";
//                }
//            } else if ("name".equals(controlId)) {
//                contextString = "value" + System.currentTimeMillis();
//            }
//        }
        
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
                // Set current context to control
                final XFormsValueControl valueXFormsControl = (XFormsValueControl) concreteEvent.getTargetObject();
                targetControlEffectiveId = valueXFormsControl.getEffectiveId();

                // Notify the control of the value change
                final String eventValue = concreteEvent.getNewValue();
                if (valueXFormsControl instanceof XFormsUploadControl) {
                    final XFormsUploadControl uploadControl = (XFormsUploadControl) valueXFormsControl;
                    uploadControl.setExternalValue(pipelineContext, eventValue, null, true);

                    // If the value is being cleared, also clear the metadata
                    if (eventValue.equals("")) {
                        uploadControl.setFilename(pipelineContext, "");
                        uploadControl.setMediatype(pipelineContext, "");
                        uploadControl.setSize(pipelineContext, "");
                    }

                } else {
                    valueXFormsControl.setExternalValue(pipelineContext, eventValue, null);
                }
            }

            {
                // NOTE: Recalculate and revalidate are done with the automatic deferred updates

                // Handle focus change DOMFocusOut / DOMFocusIn
                if (concreteEvent.getOtherTargetObject() != null) {

                    final XFormsControl sourceXFormsControl = (XFormsControl) getObjectById(pipelineContext, targetControlEffectiveId);

                    final XFormsControl otherTargetXFormsControl
                        = (XFormsControl) getObjectById(pipelineContext,
                                ((XFormsControl) concreteEvent.getOtherTargetObject()).getEffectiveId());

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

    public XFormsEventHandlerContainer getParentContainer(XFormsContainingDocument containingDocument) {
        return null;
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return null;
    }

    public String getEffectiveId() {
        return CONTAINING_DOCUMENT_PSEUDO_ID;
    }

    public LocationData getLocationData() {
        return null;
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
                throw new OXFException(e);
            }
        }
    }

    /**
     * Main event dispatching entry.
     */
    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent event) {

        if (XFormsServer.logger.isDebugEnabled()) {
            XFormsServer.logger.debug("XForms - dispatching event: " + getEventLogSpaces() + event.getEventName() + " - " + event.getTargetObject().getEffectiveId() + " - at " + event.getLocationData());
        }

        final XFormsEventTarget targetObject = (XFormsEventTarget) event.getTargetObject();
        try {
            if (targetObject == null)
                throw new OXFException("Target object null for event: " + event.getEventName());

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
                            final XFormsEventHandler eventHandlerImpl = (XFormsEventHandler) j.next();

                            if (!eventHandlerImpl.isPhase() && eventHandlerImpl.getEventName().equals(event.getEventName())) {
                                // Capture phase match
                                startHandleEvent(event);
                                try {
                                    eventHandlerImpl.handleEvent(pipelineContext, event);
                                } finally {
                                    endHandleEvent();
                                }
                                propagate &= eventHandlerImpl.isPropagate();
                                performDefaultAction &= eventHandlerImpl.isDefaultAction();
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
                            final XFormsEventHandler eventHandlerImpl = (XFormsEventHandler) j.next();

                            if (eventHandlerImpl.isPhase() && eventHandlerImpl.getEventName().equals(event.getEventName())) {
                                // Bubbling phase match
                                startHandleEvent(event);
                                try {
                                    eventHandlerImpl.handleEvent(pipelineContext, event);
                                } finally {
                                    endHandleEvent();
                                }
                                propagate &= eventHandlerImpl.isPropagate();
                                performDefaultAction &= eventHandlerImpl.isDefaultAction();
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
            // Add OPS trace information if possible
            final LocationData locationData = (targetObject != null) ? targetObject.getLocationData() : null;
            if (locationData != null)
                throw ValidationException.wrapException(e, locationData);
            else if (e instanceof OXFException)
                throw (OXFException) e;
            else
                throw new OXFException(e);
        }
    }

    private Stack eventStack = new Stack();

    private void startHandleEvent(XFormsEvent event) {
        eventStack.push(event);
    }

    private void endHandleEvent() {
        eventStack.pop();
    }

    private String getEventLogSpaces() {
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < eventStack.size(); i++)
            sb.append("  ");
        return sb.toString();
    }

    /**
     * Return the event being processed by the current event handler, null if no event is being processed.
     */
    public XFormsEvent getCurrentEvent() {
        return (eventStack.size() == 0) ? null : (XFormsEvent) eventStack.peek();
    }

    /**
     * Execute an XForms action.
     *
     * @param pipelineContext       current PipelineContext
     * @param targetId              id of the target control
     * @param eventHandlerContainer event handler containe this action is running in
     * @param actionElement         Element specifying the action to execute
     */
    public void runAction(final PipelineContext pipelineContext, String targetId, XFormsEventHandlerContainer eventHandlerContainer, Element actionElement) {
        if (actionInterpreter == null)
            actionInterpreter = new XFormsActionInterpreter(this);
        actionInterpreter.runAction(pipelineContext, targetId, eventHandlerContainer, actionElement);
    }

    /**
     * Create an encoded dynamic state that represents the dynamic state of this XFormsContainingDocument.
     *
     * @param pipelineContext       current PipelineContext
     * @return                      encoded dynamic state
     */
    public String createEncodedDynamicState(PipelineContext pipelineContext) {
        return XFormsUtils.encodeXML(pipelineContext, createDynamicStateDocument(),
            isSessionStateHandling() ? null : XFormsUtils.getEncryptionKey());
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

                for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) j.next();

                    // TODO: can we avoid storing the instance in the dynamic state if it has not changed from static state?

                    if (currentInstance.isReplaced() || !(currentInstance instanceof SharedXFormsInstance)) {
                        // Instance has been replaced, or it is not shared, so it has to go in the dynamic state
                        instancesElement.add(currentInstance.createContainerElement(!currentInstance.isApplicationShared()));
                    }

                    // Log instance if needed
                    if (XFormsServer.logger.isDebugEnabled()) {
                        XFormsServer.logger.debug("XForms - resulting instance: model id='" + currentModel.getEffectiveId() +  "', instance id= '" + currentInstance.getEffectiveId() + "'\n"
                                + TransformerUtils.tinyTreeToString(currentInstance.getInstanceRootElementInfo()));
                    }
                }
            }
        }

        // Output divs information
        {
            final Element divsElement = dynamicStateElement.addElement("divs");
            outputSwitchesDialogs(divsElement, getXFormsControls());
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
                    final Boolean visible = (Boolean) currentEntry.getValue();

                    {
                        final Element divElement = divsElement.addElement("xxf:div", XFormsConstants.XXFORMS_NAMESPACE_URI);
                        divElement.addAttribute("dialog-id", dialogId);
                        divElement.addAttribute("visibility", visible.booleanValue() ? "visible" : "hidden");
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
        createControlAndModel(repeatIndexesElement);

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
                            throw new OXFException("Non-initialized instance has to be application shared for id: " + newInstance.getEffectiveId());

                        final SharedXFormsInstance sharedInstance
                                = XFormsServerSharedInstancesCache.instance().find(pipelineContext, newInstance.getEffectiveId(), newInstance.getModelId(), newInstance.getSourceURI(), newInstance.getValidation());
                        getModel(sharedInstance.getModelId()).setInstance(sharedInstance, false);

                    } else {
                        // Instance is initialized, just use it
                        getModel(newInstance.getModelId()).setInstance(newInstance, false);
                    }
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
                                throw new OXFException("Non-initialized instance has to be application shared for id: " + currentInstance.getEffectiveId());

                            final SharedXFormsInstance sharedInstance
                                    = XFormsServerSharedInstancesCache.instance().find(pipelineContext, currentInstance.getEffectiveId(), currentInstance.getModelId(), currentInstance.getSourceURI(), currentInstance.getValidation());
                            getModel(sharedInstance.getModelId()).setInstance(sharedInstance, false);

                        } else {
                            // Instance is initialized, just use it
                            getModel(currentInstance.getModelId()).setInstance(currentInstance, false);
                        }
                    }
                }
            }

            // Number of instances must be zero or match number of models
//            if (expectedInstancesCount != foundInstancesCount)
//                throw new OXFException("Number of instances (" + foundInstancesCount
//                        + ") doesn't match number of instances in models (" + expectedInstancesCount + ").");
        }

        // Restore models state
        for (Iterator j = getModels().iterator(); j.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) j.next();
            currentModel.initializeState(pipelineContext);
        }

        // Restore controls
        final Element divsElement = dynamicStateDocument.getRootElement().element("divs");
        xformsControls.initializeState(pipelineContext, divsElement, repeatIndexesElement);
    }

    private void initialize(PipelineContext pipelineContext) {
        // This is called upon the first creation of the XForms engine only

        // Create XForms controls and models
        createControlAndModel(null);

        // 4.2 Initialization Events

        // 1. Dispatch xforms-model-construct to all models
        // 2. Dispatch xforms-model-construct-done to all models
        // 3. Dispatch xforms-ready to all models

        final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, XFormsEvents.XFORMS_READY, XFormsEvents.XXFORMS_READY };
        for (int i = 0; i < eventsToDispatch.length; i++) {
            final boolean isXFormsModelConstructDone = i == 1;
            final boolean isXFormsReady = i == 2;

            if (isXFormsModelConstructDone) {
                // Initialize controls after all the xforms-model-construct events have been sent
                xformsControls.initialize(pipelineContext);
            }

            // Iterate over all the models
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) j.next();

                if (isXFormsReady) {
                    // Performed deferred updates only for xforms-ready
                    startOutermostActionHandler();
                }
                dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(eventsToDispatch[i], currentModel));
                if (isXFormsReady) {
                    // Performed deferred updates only for xforms-ready
                    endOutermostActionHandler(pipelineContext);
                }
            }
        }
    }

    private void createControlAndModel(Element repeatIndexesElement) {

        if (xformsStaticState != null) {
            // Create XForms controls
            xformsControls = new XFormsControls(this, xformsStaticState.getControlsDocument(), repeatIndexesElement);

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
