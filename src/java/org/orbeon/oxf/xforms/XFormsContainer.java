/**
 *  Copyright (C) 2008 Orbeon, Inc.
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

import org.apache.commons.collections.map.LinkedMap;
import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XFormsModelDestructEvent;
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represent a container of models and controls.
 *
 * Notes:
 *
 * o This is used at the top-level (XFormsContainingDocument) and by component instances.
 * o For now there is no nested component tree. There is a single components tree in XFormsControls.
 * o We are not 100% happy with this, because models should be allowed within any container, not only components.
 *
 * In the future, we should see how we can refactor this to handle flexible model placement. Also, there is a double
 * purpose for this class, which we should correct:
 *
 * o as a container for models
 * o as a boundary for components
 */
public class XFormsContainer implements XFormsEventTarget, XFormsEventObserver {

    // PipelineContext attribute used during instance restoration
    protected static final String XFORMS_DYNAMIC_STATE_RESTORE = "xforms-dynamic-state-instances";

    // Static id of the control containing this container, e.g. my-foo-bar
    private String staticId;
    // Effective id of the control containing this container, e.g. my-stuff$my-foo-bar.1-2
    private String effectiveId;
    // Prefix of controls and models within this container, e.g. "" for the root container, "my-stuff$my-foo-bar$", etc.
    private String fullPrefix;

    private LocationData locationData;

    // Hierarchy of containers
    private final XFormsContainer parentContainer;
    private LinkedHashMap childrenContainers;  // Map<String, XFormsContainer> of static id to container

    // Binding context for this container (may be null)
    private XFormsContextStack.BindingContext bindingContext; 

    private XFormsContainingDocument containingDocument;
    private final XFormsContextStack contextStack;  // for controls under this container

    private List models = new ArrayList();  // List<XFormsModel>
    private Map modelsMap = new HashMap();  // Map<String, XFormsModel> of effective model id to model

    /**
     * Create a new container child of the control with id effectiveId.
     *
     * @param effectiveId   effective id of the containing control
     * @return              new XFormsContainer
     */
    public XFormsContainer createChildContainer(String effectiveId) {
        return new XFormsContainer(effectiveId, this);
    }

    protected XFormsContainer(String effectiveId, XFormsContainer parentContainer) {
        this(XFormsUtils.getStaticIdFromId(effectiveId), effectiveId, XFormsUtils.getEffectiveIdNoSuffix(effectiveId) + XFormsConstants.COMPONENT_SEPARATOR, parentContainer);
    }

    protected XFormsContainer(String staticId, String effectiveId, String fullPrefix, XFormsContainer parentContainer) {
        this.staticId = staticId;
        this.effectiveId = effectiveId;
        this.fullPrefix = fullPrefix;
        this.parentContainer = parentContainer;

        if (parentContainer != null) {
            // Tell parent it has a child
            parentContainer.addChild(this);
        }

        // Search for containing document
        XFormsContainer tempContainer = this;
        while (tempContainer != null) {
            if (tempContainer instanceof XFormsContainingDocument) {
                containingDocument = (XFormsContainingDocument) tempContainer;
                break;
            }
            tempContainer = tempContainer.getParentContainer();
        }

        this.contextStack = new XFormsContextStack(this);
    }

    /**
     * Update the effective id when repeat iterations change.
     *
     * @param effectiveId   effective id of the containing control
     */
    public void updateEffectiveId(String effectiveId) {

        // Remove from parent before updating id
        if (parentContainer != null) {
            parentContainer.removeChild(this);
        }

        // Update all ids
        this.staticId = XFormsUtils.getStaticIdFromId(effectiveId);
        this.effectiveId = effectiveId;
        this.fullPrefix = XFormsUtils.getEffectiveIdNoSuffix(effectiveId) + XFormsConstants.COMPONENT_SEPARATOR;

        // Add back to parent after updating id
        if (parentContainer != null) {
            // TODO: document order may not be kept anymore
            parentContainer.addChild(this);
        }

        // Clear map
        modelsMap.clear();

        // Update effective ids of all nested models
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) iterator.next();

            // E.g. foo$bar$my-model.1-2 => foo$bar$my-model.1-3
            final String newModelEffectiveId = XFormsUtils.getEffectiveIdNoSuffix(currentModel.getEffectiveId()) + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);
            currentModel.updateEffectiveId(newModelEffectiveId);

            // Put in map
            modelsMap.put(currentModel.getEffectiveId(), currentModel);
        }
    }

    /**
     * Remove container and destroy models when a repeat iteration is removed.
     *
     * @param pipelineContext   current PipelineContext
     */
    public void destroy(PipelineContext pipelineContext) {
        // Tell parent about it
        if (parentContainer != null) {
            parentContainer.removeChild(this);
        }

        // Dispatch destruction event to all models
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) iterator.next();
            dispatchEvent(pipelineContext, new XFormsModelDestructEvent(currentModel));
        }
    }

    public String getFullPrefix() {
        return fullPrefix;
    }

    public XFormsContainer getParentContainer() {
        return parentContainer;
    }

    private void addChild(XFormsContainer container) {
        if (childrenContainers == null)
            childrenContainers = new LinkedHashMap();
        childrenContainers.put(container.getEffectiveId(), container);
    }

    private void removeChild(XFormsContainer container) {

        final String effectiveId = container.getEffectiveId();
        final Object containerForEffectiveId = childrenContainers.get(effectiveId);

        // Only remove if the object matches, in case another object with same effective id was added in the meanwhile.
        // Possible with repeat iteration updates.
        if (containerForEffectiveId == container)
            childrenContainers.remove(effectiveId);
    }

    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        this.bindingContext = bindingContext;
        this.contextStack.setParentBindingContext(bindingContext);
    }

    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public XFormsContextStack getContextStack() {
        return contextStack;
    }

    public XFormsContainer getChildByEffectiveId(String staticId) {
        return (XFormsContainer) ((childrenContainers != null) ? childrenContainers.get(staticId) : null);
    }

    /**
     * Create and index models corresponding to this container's scope.
     */
    public void addAllModels() {
        // Iterate through all models and finds the one that apply to this container
        for (Iterator i = containingDocument.getStaticState().getModelDocuments().entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final String modelPrefixedId = (String) currentEntry.getKey();
            final Document modelDocument = (Document) currentEntry.getValue();

            final String modelPrefix = XFormsUtils.getEffectiveIdPrefix(modelPrefixedId);
            if (fullPrefix.equals(modelPrefix)) {
                // This model belongs to this container

                // Find model's effective id, e.g. if container's effective id is foo$bar.1-2 and models static id is
                // my-model => foo$bar$my-model.1-2
                final String modelEffectiveId = modelPrefixedId + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);

                // Create and add model
                addModel(new XFormsModel(this, modelEffectiveId, modelDocument));
            }
        }
    }

    public void initializeModels(PipelineContext pipelineContext) {

        // 4.2 Initialization Events

        // 1. Dispatch xforms-model-construct to all models
        // 2. Dispatch xforms-model-construct-done to all models
        // 3. Dispatch xforms-ready to all models

        final String[] eventsToDispatch = { XFormsEvents.XFORMS_MODEL_CONSTRUCT, XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE, XFormsEvents.XFORMS_READY, XFormsEvents.XXFORMS_READY };
        for (int i = 0; i < eventsToDispatch.length; i++) {
            if (i == 2) {
                // Initialize controls after all the xforms-model-construct-done events have been sent
                 initializeNestedControls(pipelineContext);
            }

            // Iterate over all the models
            for (Iterator j = models.iterator(); j.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) j.next();

                // Make sure there is at least one refresh
                currentModel.getDeferredActionContext().refresh = true;

                dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(eventsToDispatch[i], currentModel));
            }
        }
    }

    protected void initializeNestedControls(PipelineContext pipelineContext) {
        // NOP by default
    }

    /**
     * Return model within this container, with the specified static id, null if not found. If the id is the empty
     * string, return the default model, i.e. the first model.
     */
    public XFormsModel findModelByStaticId(String modelStaticId) {
        return (XFormsModel) ("".equals(modelStaticId)
                ? getDefaultModel()
                : modelsMap.get(fullPrefix + modelStaticId + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId)));
    }

    protected void addModel(XFormsModel model) {// move to private once legacy caller is gone
        this.models.add(model);
        if (model.getEffectiveId() != null)// TODO: how can this be null?
            this.modelsMap.put(model.getEffectiveId(), model);
    }

    public XFormsModel getDefaultModel() {
        if (models != null && models.size() > 0)
            return (XFormsModel) models.get(0);
        else
            return null;
    }

    /**
     * Get a list of all the models in this document.
     */
    public List getModels() {
        return models;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in children
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                final Object resultObject = currentContainer.getObjectByEffectiveId(effectiveId);
                if (resultObject != null)
                    return resultObject;
            }
        }

        // Check container id
        if (effectiveId.equals(getEffectiveId()))
            return this;

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

        // Check this id
        if (targetId.equals(getEffectiveId()))
            return this;

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.resolveObjectById(effectiveSourceId, targetId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in children
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                final Object resultObject = currentContainer.resolveObjectById(effectiveSourceId, targetId);
                if (resultObject != null)
                    return resultObject;
            }
        }

        return null;
    }

    /**
     * Find the instance containing the specified node, in any model.
     *
     * @param nodeInfo  node contained in an instance
     * @return      instance containing the node
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            final XFormsInstance currentInstance = currentModel.getInstanceForNode(nodeInfo);
            if (currentInstance != null)
                return currentInstance;
        }

        // Search in children
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                final XFormsInstance currentInstance = currentContainer.getInstanceForNode(nodeInfo);
                if (currentInstance != null)
                    return currentInstance;
            }
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
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            final XFormsInstance currentInstance = currentModel.getInstance(instanceId);
            if (currentInstance != null)
                return currentInstance;
        }
        return null;
    }

    /**
     * Serialize all the instances of this container and children containers.
     *
     * @param instancesElement  container element
     */
    // TODO: xxx move to XFormsModel
    protected void serializeInstances(Element instancesElement) {

        // Serialize this container's model's
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();

            if (currentModel.getInstances() != null) {
                for (Iterator j = currentModel.getInstances().iterator(); j.hasNext();) {
                    final XFormsInstance currentInstance = (XFormsInstance) j.next();

                    // TODO: can we avoid storing the instance in the dynamic state if it has not changed from static state?

                    if (currentInstance.isReplaced() || !(currentInstance instanceof SharedXFormsInstance)) {
                        // Instance has been replaced, or it is not shared, so it has to go in the dynamic state
                        instancesElement.add(currentInstance.createContainerElement(!currentInstance.isApplicationShared()));

                        // Log instance if needed
                        currentInstance.logIfNeeded(getContainingDocument(), "storing instance to dynamic state");
                    }
                }
            }
        }

        // Recurse into children containers
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                currentContainer.serializeInstances(instancesElement);
            }
        }
    }

    public void restoreModelsState(PipelineContext pipelineContext) {
        // Handle this container only
        for (Iterator iterator = models.iterator(); iterator.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) iterator.next();
            currentModel.restoreState(pipelineContext);
        }
    }

    public void startOutermostActionHandler() {
        // Handle this container
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.startOutermostActionHandler();
        }
        // Recurse into children containers
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                currentContainer.startOutermostActionHandler();
            }
        }
    }

    public void endOutermostActionHandler(PipelineContext pipelineContext) {
        // Handle this container
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.endOutermostActionHandler(pipelineContext);
        }
        // Recurse into children containers
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                currentContainer.endOutermostActionHandler(pipelineContext);
            }
        }
    }

    public void rebuildRecalculateIfNeeded(PipelineContext pipelineContext) {
        // Handle this container
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.rebuildRecalculateIfNeeded(pipelineContext);
        }
        // Recurse into children containers
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                currentContainer.rebuildRecalculateIfNeeded(pipelineContext);
            }
        }
    }

    public void synchronizeInstanceDataEventState() {
        // Handle this container
        for (Iterator i = models.iterator(); i.hasNext();) {
            final XFormsModel currentModel = (XFormsModel) i.next();
            currentModel.synchronizeInstanceDataEventState();
        }
        // Recurse into children containers
        if (childrenContainers != null) {
            for (Iterator i = childrenContainers.values().iterator(); i.hasNext();) {
                final XFormsContainer currentContainer = (XFormsContainer) i.next();
                currentContainer.synchronizeInstanceDataEventState();
            }
        }
    }

    public String getId() {
        return staticId;
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    public void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public XFormsEventObserver getParentEventObserver(XFormsContainer container) {
        return parentContainer;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // NOP
    }

    public void performTargetAction(PipelineContext pipelineContext, XFormsContainer container, XFormsEvent event) {
        // NOP
    }

    public List getEventHandlers(XFormsContainer container) {
        return null;
    }

    public XFormsContainer getContainer() {
        return this;
    }

    public XFormsContainer getContainer(XFormsContainingDocument containingDocument) {
        return getContainer();
    }

    /**
     * Main event dispatching entry.
     */
    public void dispatchEvent(PipelineContext pipelineContext, XFormsEvent originalEvent) {

        if (XFormsServer.logger.isDebugEnabled()) {
            containingDocument.startHandleOperation("event", "dispatching", new String[] { "name", originalEvent.getEventName(), "id", originalEvent.getTargetObject().getEffectiveId(), "location", originalEvent.getLocationData().toString() });
        }

        final XFormsEventTarget targetObject = originalEvent.getTargetObject();
        try {
            if (targetObject == null)
                throw new ValidationException("Target object null for event: " + originalEvent.getEventName(), getLocationData());

            // Find all event handler containers
            final List boundaries = new ArrayList();        // List<XFormsEventObserver>
            final Map eventsForBoundaries = new LinkedMap();// Map<String effectiveId, XFormsEvent event>
            final List eventObservers = new ArrayList();
            {
                XFormsEventObserver eventObserver
                        = (targetObject instanceof XFormsEventObserver) ? (XFormsEventObserver) targetObject : targetObject.getParentEventObserver(this);
                while (eventObserver != null) {
                    if (!(eventObserver instanceof XFormsRepeatControl)) {
                        // Add container except if it is a repeat, as we use repeat iterations instead
                        eventObservers.add(eventObserver);

                        if (eventObserver instanceof XFormsContainer && !(eventObserver instanceof XFormsContainingDocument)
                                || eventObserver instanceof XFormsComponentControl) {
                            if (originalEvent instanceof XFormsUIEvent) {
                                // UI events need to be retargetted
                                boundaries.add(eventObserver);
                                eventsForBoundaries.put(eventObserver.getEffectiveId(), null);
                            } else {
                                // Stop propagation on model container or component boundary for all non-UI events
                                break;
                            }
                        }
                    }

                    // Find parent
                    eventObserver = eventObserver.getParentEventObserver(this);
                }
            }

            boolean propagate = true;
            boolean performDefaultAction = true;

            // Go from root to leaf
            Collections.reverse(eventObservers);
            Collections.reverse(boundaries);

            // Get event according to its target
            int nextBoundaryIndex = 0;
            String nextBoundaryEffectiveId;
            XFormsEvent retargettedEvent;

            // Handle event retargetting
            if (boundaries.size() == 0) {
                // Original event all the way
                nextBoundaryEffectiveId = null;
                retargettedEvent = originalEvent;
            } else {
                // Start with retargetted event
                final XFormsEventObserver observer = (XFormsEventObserver) boundaries.get(nextBoundaryIndex);
                nextBoundaryEffectiveId = observer.getEffectiveId();
                retargettedEvent = getRetargettedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                nextBoundaryIndex++;
            }

            // Capture phase
            for (Iterator i = eventObservers.iterator(); i.hasNext();) {
                final XFormsEventObserver currentEventObserver = (XFormsEventObserver) i.next();
                final List currentEventHandlers = currentEventObserver.getEventHandlers(this);

                if (currentEventHandlers != null) {
                    if (currentEventObserver != targetObject) {
                        // Event listeners on the target which are in capture mode are not called

                        // Process event handlers
                        for (Iterator j = currentEventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) j.next();

                            if (!eventHandler.isBubblingPhase()
                                    && eventHandler.isMatchEventName(retargettedEvent.getEventName())
                                    && eventHandler.isMatchTarget(retargettedEvent.getTargetObject().getId())) {
                                // Capture phase match on event name and target is specified
                                containingDocument.startHandleEvent(retargettedEvent);
                                try {
                                    eventHandler.handleEvent(pipelineContext, currentEventObserver.getContainer(containingDocument), currentEventObserver, retargettedEvent);
                                } finally {
                                    containingDocument.endHandleEvent();
                                }
                                propagate &= eventHandler.isPropagate();
                                performDefaultAction &= eventHandler.isPerformDefaultAction();
                            }
                        }
                        // Cancel propagation if requested and if authorized by event
                        if (!propagate && retargettedEvent.isCancelable())
                            break;
                    }
                }

                // Handle event retargetting
                if (nextBoundaryEffectiveId != null && currentEventObserver.getEffectiveId().equals(nextBoundaryEffectiveId)) {

                    if (nextBoundaryIndex == boundaries.size()) {
                        // Original event
                        nextBoundaryEffectiveId = null;
                        retargettedEvent = originalEvent;
                    } else {
                        // Retargetted event
                        final XFormsEventObserver observer = (XFormsEventObserver) boundaries.get(nextBoundaryIndex);
                        nextBoundaryEffectiveId = observer.getEffectiveId();
                        retargettedEvent = getRetargettedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                        nextBoundaryIndex++;
                    }

                    if (XFormsServer.logger.isDebugEnabled()) {
                        containingDocument.logDebug("event", "retargetting", new String[] {
                                "name", originalEvent.getEventName(),
                                "original id", originalEvent.getTargetObject().getEffectiveId(),
                                "new id", retargettedEvent.getTargetObject().getEffectiveId()
                        });
                    }
                }
            }

            // Bubbling phase
            if (propagate && originalEvent.isBubbles()) {

                // Go from leaf to root
                Collections.reverse(eventObservers);
                Collections.reverse(boundaries);

                // Handle event retargetting
                if (boundaries.size() > 0) {
                    nextBoundaryIndex--;
                    final XFormsEventObserver observer = (XFormsEventObserver) boundaries.get(nextBoundaryIndex);
                    nextBoundaryEffectiveId = observer.getEffectiveId();
                }

                for (Iterator i = eventObservers.iterator(); i.hasNext();) {
                    final XFormsEventObserver currentEventObserver = (XFormsEventObserver) i.next();
                    final List currentEventHandlers = currentEventObserver.getEventHandlers(this);

                    // Handle event retargetting
                    if (nextBoundaryEffectiveId != null && currentEventObserver.getEffectiveId().equals(nextBoundaryEffectiveId)) {

                        // Retargetted event
                        final XFormsEventObserver observer = (XFormsEventObserver) boundaries.get(nextBoundaryIndex);
                        nextBoundaryEffectiveId = observer.getEffectiveId();
                        retargettedEvent = getRetargettedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                        nextBoundaryIndex--;

                        if (XFormsServer.logger.isDebugEnabled()) {
                            containingDocument.logDebug("event", "retargetting", new String[] {
                                    "name", originalEvent.getEventName(),
                                    "original id", originalEvent.getTargetObject().getEffectiveId(),
                                    "new id", retargettedEvent.getTargetObject().getEffectiveId()
                            });
                        }
                    }

                    // Process "action at target"
                    // NOTE: This is used XFormsInstance for xforms-insert/xforms-delete processing
                    if (currentEventObserver == targetObject) {
                        currentEventObserver.performTargetAction(pipelineContext, currentEventObserver.getContainer(containingDocument), retargettedEvent);
                    }

                    // Process event handlers
                    if (currentEventHandlers != null) {
                        for (Iterator j = currentEventHandlers.iterator(); j.hasNext();) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) j.next();

                            if (eventHandler.isBubblingPhase()
                                    && eventHandler.isMatchEventName(retargettedEvent.getEventName())
                                    && eventHandler.isMatchTarget(retargettedEvent.getTargetObject().getId())) {
                                // Bubbling phase match on event name and target is specified
                                containingDocument.startHandleEvent(retargettedEvent);
                                try {
                                    eventHandler.handleEvent(pipelineContext, currentEventObserver.getContainer(containingDocument), currentEventObserver, retargettedEvent);
                                } finally {
                                    containingDocument.endHandleEvent();
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
            if (performDefaultAction || !originalEvent.isCancelable()) {
                containingDocument.startHandleEvent(originalEvent);
                try {
                    targetObject.performDefaultAction(pipelineContext, originalEvent);
                } finally {
                    containingDocument.endHandleEvent();
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
                    new String[] { "event", originalEvent.getEventName(), "target id", targetObject.getEffectiveId() }));
        }

        if (XFormsServer.logger.isDebugEnabled()) {
            containingDocument.endHandleOperation();
        }
    }

    private XFormsEvent getRetargettedEvent(Map eventsForBoundaries, String boundaryId, XFormsEventTarget newEventTarget, XFormsEvent originalEvent) {
        XFormsEvent retargettedEvent = (XFormsEvent) eventsForBoundaries.get(boundaryId);

        // Event already created, just return it
        if (retargettedEvent != null)
            return retargettedEvent;

        // Clone original event, retarget it, and remember it
        retargettedEvent = originalEvent.retarget(newEventTarget);
        eventsForBoundaries.put(boundaryId, retargettedEvent);

        return retargettedEvent;
    }
}
