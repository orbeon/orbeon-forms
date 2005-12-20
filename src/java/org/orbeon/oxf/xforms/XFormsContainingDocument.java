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

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.pipeline.api.ExternalContext;
import org.orbeon.oxf.xforms.action.XFormsActionInterpreter;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.oxf.util.NetUtils;
import org.apache.commons.pool.ObjectPool;

import java.util.*;
import java.io.IOException;

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

//    private LocationData locationData; // At some point we need to be able to store this

    // Object pool this object must be returned to, if any
    private ObjectPool sourceObjectPool;

    // A document contains models and controls
    private List models;
    private Map modelsMap = new HashMap();
    private XFormsControls xformsControls;
    private String containerType;
    private String stateHandling;

    // Client state
    private XFormsModelSubmission activeSubmission;
    private List messages;
    private List loads;
    private String focusEffectiveControlId;

    private XFormsActionInterpreter actionInterpreter;

    public XFormsContainingDocument(List models, Document controlsDocument) {
        this(models, controlsDocument, null, null, null);
    }

    public XFormsContainingDocument(List models, Document controlsDocument, Element repeatIndexesElement, String containerType, String stateHandling) {

        this.models = models;
        this.xformsControls = new XFormsControls(this, controlsDocument, repeatIndexesElement);
        this.containerType = containerType;
        this.stateHandling = stateHandling;

        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            if (model.getId() != null)
                modelsMap.put(model.getId(), model);
            model.setContainingDocument(this);
        }
    }

    public void setSourceObjectPool(ObjectPool sourceObjectPool) {
        this.sourceObjectPool = sourceObjectPool;
    }

    public ObjectPool getSourceObjectPool() {
        return sourceObjectPool;
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
     * Return the container type that generate the XForms page, either "servlet" or "portlet".
     */
    public String getContainerType() {
        return containerType;
    }

    /**
     * Return the state handling strategy for this document, either "client" or "session".
     */
    public String getStateHandling() {
        return stateHandling;
    }

    /**
     * Initialize the XForms engine.
     */
    public void initialize(PipelineContext pipelineContext) {
        // NOP for now
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
        if (id.equals(getId()))
            return this;

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
    public void clearClientState() {
        this.activeSubmission = null;
        this.messages = null;
        this.loads = null;
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

    /**
     * Add an XForms message to send to the client.
     */
    public void addClientMessage(String message, String level) {
        if (messages == null)
            messages = new ArrayList();
        messages.add(new Message(message, level));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List getClientMessages() {
        return messages;
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

    /**
     * Add an XForms load to send to the client.
     */
    public void addClientLoad(String resource, String target, boolean isReplace, boolean isPortletLoad) {
        if (loads == null)
            loads = new ArrayList();
        loads.add(new Load(resource, target, isReplace, isPortletLoad));
    }

    /**
     * Return the list of messages to send to the client, null if none.
     */
    public List getClientLoads() {
        return loads;
    }

    public static class Load {
        private String resource;
        private String target;
        private boolean isReplace;
        private boolean isPortletLoad;

        public Load(String resource, String target, boolean replace, boolean portletLoad) {
            this.resource = resource;
            this.target = target;
            isReplace = replace;
            isPortletLoad = portletLoad;
        }

        public String getResource() {
            return resource;
        }

        public String getTarget() {
            return target;
        }

        public boolean isReplace() {
            return isReplace;
        }

        public boolean isPortletLoad() {
            return isPortletLoad;
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
    public String getClientFocusEffectiveControlId() {
        return focusEffectiveControlId;
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

        // Create event
        final XFormsEvent xformsEvent = XFormsEventFactory.createEvent(eventName, eventTarget, otherEventTarget, contextString, null, null, filesElement);

        // Interpret event
        interpretEvent(pipelineContext, xformsEvent);
    }

    private void interpretEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        final String eventName = xformsEvent.getEventName();
        if (XFormsEvents.XFORMS_DOM_ACTIVATE.equals(eventName)
            || XFormsEvents.XFORMS_DOM_FOCUS_OUT.equals(eventName)
            || XFormsEvents.XFORMS_DOM_FOCUS_IN.equals(eventName)
            || XFormsEvents.XFORMS_VALUE_CHANGED.equals(eventName)) { // TODO: check if xforms-value-changed is actually ever sent by client

            // These are events we allow directly from the client and actually handle

            dispatchEvent(pipelineContext, xformsEvent);

        } else if (XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE.equals(eventName)) {
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

            {
                // Set current context to control
                final XFormsControls.ControlInfo valueControlInfo = (XFormsControls.ControlInfo) concreteEvent.getTargetObject();
                xformsControls.setBinding(pipelineContext, valueControlInfo);

                // Set value into the instance
                XFormsInstance.setValueForNode(pipelineContext, xformsControls.getCurrentSingleNode(), concreteEvent.getNewValue(), null);

                // Update this particular control's value
                valueControlInfo.evaluateValue(pipelineContext);
                valueControlInfo.evaluateDisplayValue(pipelineContext);
            }

            // Make sure controls are not in the initial state before sending events
            xformsControls.rebuildCurrentControlsState(pipelineContext);

            // Recalculate and revalidate
            final XFormsModel model = xformsControls.getCurrentModel();
            dispatchEvent(pipelineContext, new XFormsRecalculateEvent(model, true));
            dispatchEvent(pipelineContext, new XFormsRevalidateEvent(model, true));

            // Handle focus change DOMFocusOut / DOMFocusIn
            if (concreteEvent.getOtherTargetObject() != null) {
                // We have a focus change (otherwise, the focus is assumed to remain the same)
                dispatchEvent(pipelineContext, new XFormsDOMFocusOutEvent(concreteEvent.getTargetObject()));
                dispatchEvent(pipelineContext, new XFormsDOMFocusInEvent(concreteEvent.getOtherTargetObject()));
            }

            // Refresh (this will send update events)
            dispatchEvent(pipelineContext, new XFormsRefreshEvent(model));

        } else if (XFormsEvents.XXFORMS_SUBMIT.equals(eventName)) {
            // Internal submission event
            dispatchEvent(pipelineContext, xformsEvent);

        } else if (XFormsEvents.XXFORMS_LOAD.equals(eventName)) {
            // Internal load event
            dispatchEvent(pipelineContext, xformsEvent);
        } else {
            throw new OXFException("Invalid event dispatched by client: " + eventName);
        }
    }

    public void dispatchExternalEvent(final PipelineContext pipelineContext, XFormsEvent xformsEvent) {
        final String eventName = xformsEvent.getEventName();
        if (XFormsEvents.XXFORMS_INITIALIZE.equals(eventName)) {

            // This is called upon the first creation of the XForms engine only

            // 4.2 Initialization Events

            // 1. Dispatch xforms-model-construct to all models
            // 2. Dispatch xforms-model-construct-done to all models
            // 3. Dispatch xforms-ready to all models

            final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, XFormsEvents.XFORMS_READY };
            for (int i = 0; i < eventsToDispatch.length; i++) {
                if (XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE.equals(eventsToDispatch[i])) {
                    dispatchExternalEvent(pipelineContext, new XXFormsInitializeControlsEvent(this, null, null));
                }
                for (Iterator j = getModels().iterator(); j.hasNext();) {
                    final XFormsModel currentModel = (XFormsModel) j.next();
                    dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(eventsToDispatch[i], currentModel));
                }
            }
        } else if (XFormsEvents.XXFORMS_INITIALIZE_STATE.equals(eventName)) {
            final XXFormsInitializeStateEvent initializeStateEvent = (XXFormsInitializeStateEvent) xformsEvent;

            // This is called whenever the state of the XForms engine needs to be rebuilt

            // Clear containing document state
            clearClientState();

            // Restore models state
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) j.next();
                dispatchEvent(pipelineContext, new XXFormsInitializeStateEvent(currentModel, initializeStateEvent.getDivsElement(), initializeStateEvent.getRepeatIndexesElement()));
            }

            dispatchExternalEvent(pipelineContext, new XXFormsInitializeControlsEvent(this, initializeStateEvent.getDivsElement(), initializeStateEvent.getRepeatIndexesElement()));

        } else if (XFormsEvents.XXFORMS_INITIALIZE_CONTROLS.equals(eventName)) {
            // Make sure controls are initialized
            final XXFormsInitializeControlsEvent initializeControlsEvent = (XXFormsInitializeControlsEvent) xformsEvent;
            xformsControls.initialize(pipelineContext, initializeControlsEvent.getDivsElement(), initializeControlsEvent.getRepeatIndexesElement());
        } else {
            throw new OXFException("Invalid event dispatched: " + eventName);
        }
    }

    public XFormsEventHandlerContainer getParentContainer() {
        return null;
    }

    public List getEventHandlers() {
        return null;
    }

    public String getId() {
        return CONTAINING_DOCUMENT_PSEUDO_ID;
    }

    public LocationData getLocationData() {
        return null;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {

        if (XFormsEvents.XXFORMS_LOAD.equals(event.getEventName())) {
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
            XFormsServer.logger.debug("XForms - dispatching event: " + event.getEventName() + " - " + event.getTargetObject().getId() + " - at " + event.getLocationData());
        }

        final XFormsEventTarget targetObject = (XFormsEventTarget) event.getTargetObject();

        try {
            // Find all event handler containers
            final List containers = new ArrayList();
            {
                XFormsEventHandlerContainer container = (targetObject instanceof XFormsEventHandlerContainer) ? (XFormsEventHandlerContainer) targetObject : targetObject.getParentContainer();
                while (container != null) {
                    containers.add(container);
                    container = container.getParentContainer();
                }
            }

            boolean propagate = true;
            boolean performDefaultAction = true;

            // Go from root to leaf
            Collections.reverse(containers);

            // Capture phase
            for (Iterator i = containers.iterator(); i.hasNext();) {
                final XFormsEventHandlerContainer container = (XFormsEventHandlerContainer) i.next();
                final List eventHandlers = container.getEventHandlers();

                if (eventHandlers != null) {
                    if (container != targetObject) {
                        // Event listeners on the target which are in capture mode are not called

                        for (Iterator j = eventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandlerImpl = (XFormsEventHandler) j.next();

                            if (!eventHandlerImpl.isPhase() && eventHandlerImpl.getEventName().equals(event.getEventName())) {
                                // Capture phase match
                                eventHandlerImpl.handleEvent(pipelineContext, event);
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
                    final List eventHandlers = container.getEventHandlers();

                    if (eventHandlers != null) {
                        for (Iterator j = eventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandlerImpl = (XFormsEventHandler) j.next();

                            if (eventHandlerImpl.isPhase() && eventHandlerImpl.getEventName().equals(event.getEventName())) {
                                // Bubbling phase match
                                eventHandlerImpl.handleEvent(pipelineContext, event);
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
                targetObject.performDefaultAction(pipelineContext, event);
            }
        } catch (Exception e) {
            // Add OPS trace information if possible
            final LocationData locationData = targetObject.getLocationData();
            if (locationData != null)
                throw ValidationException.wrapException(e, locationData);
            else if (e instanceof OXFException)
                throw (OXFException) e;
            else
                throw new OXFException(e);
        }
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
        actionInterpreter.runAction(pipelineContext, targetId, eventHandlerContainer, actionElement, null);
    }
}
