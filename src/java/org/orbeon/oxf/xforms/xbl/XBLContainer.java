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
package org.orbeon.oxf.xforms.xbl;

import org.dom4j.Document;
import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsRootControl;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.XFormsModelDestructEvent;
import org.orbeon.oxf.xforms.event.events.XFormsUIEvent;
import org.orbeon.oxf.xforms.event.events.XXFormsValueChangeWithFocusChangeEvent;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represent an XBL container of models and controls.
 *
 * Notes:
 *
 * o This is used at the top-level (XFormsContainingDocument) and by component instances.
 * o For now there is no nested component tree. There is a single components tree in XFormsControls.
 *
 * There is a double purpose for this class, which we should correct:
 *
 * o as a container for models
 * o as a boundary for components
 *
 * In the future we want flexible model placement, so models should get out of this class.
 */
public class XBLContainer implements XFormsEventTarget, XFormsEventObserver, XFormsObjectResolver {

    // PipelineContext attribute used during instance restoration
    public static final String XFORMS_DYNAMIC_STATE_RESTORE_INSTANCES = "xforms-dynamic-state-instances";
    protected static final String XFORMS_DYNAMIC_STATE_RESTORE_CONTROLS = "xforms-dynamic-state-controls";

    // Static id of the control containing this container, e.g. "#document" for root container, "my-foo-bar", etc.
    private String staticId;
    // Effective id of the control containing this container, e.g. "#document" for root container, "my-stuff$my-foo-bar.1-2", etc.
    private String effectiveId;
    // Prefixed id of the control containing this container, e.g. "#document" for root container, "my-stuff$my-foo-bar", etc.
    private String prefixedId;
    // Prefix of controls and models within this container, e.g. "" for the root container, "my-stuff$my-foo-bar$", etc.
    private String fullPrefix;

    private LocationData locationData;

    // Hierarchy of containers
    private final XBLContainer parentXBLContainer;
    private LinkedHashMap<String, XBLContainer> childrenXBLContainers;  // Map<String, XBLContainer> of static id to container

    // Binding context for this container (may be null for top-level)
    // This is the binding context of the containing XFormsComponentControl for nested XBL containers
    private XFormsContextStack.BindingContext bindingContext;

    private XFormsContainingDocument containingDocument;
    private XBLBindings xblBindings;
    private final XFormsContextStack contextStack;  // for controls under this container

    private List<XFormsModel> models = new ArrayList<XFormsModel>();
    private Map<String, XFormsModel> modelsMap = new HashMap<String, XFormsModel>();    // Map<String, XFormsModel> of effective model id to model

    /**
     * Create a new container child of the control with id effectiveId.
     *
     * @param effectiveId   effective id of the containing control
     * @return              new XFormsContainer
     */
    public XBLContainer createChildContainer(String effectiveId) {
        return new XBLContainer(effectiveId, this);
    }

    protected XBLContainer(String effectiveId, XBLContainer parentXBLContainer) {
        this(XFormsUtils.getStaticIdFromId(effectiveId), effectiveId, XFormsUtils.getPrefixedId(effectiveId),
                XFormsUtils.getPrefixedId(effectiveId) + XFormsConstants.COMPONENT_SEPARATOR, parentXBLContainer);
    }

    protected XBLContainer(String staticId, String effectiveId, String prefixedId, String fullPrefix, XBLContainer parentXBLContainer) {
        this.staticId = staticId;
        this.effectiveId = effectiveId;
        this.prefixedId = prefixedId;
        this.fullPrefix = fullPrefix;
        this.parentXBLContainer = parentXBLContainer;

        if (parentXBLContainer != null) {
            // Tell parent it has a child
            parentXBLContainer.addChild(this);
        }

        // Search for containing document
        XBLContainer tempContainer = this;
        while (tempContainer != null) {
            if (tempContainer instanceof XFormsContainingDocument) {
                containingDocument = (XFormsContainingDocument) tempContainer;
                break;
            }
            tempContainer = tempContainer.getParentXBLContainer();
        }

        this.contextStack = new XFormsContextStack(this);
    }

    public XBLBindings.Scope getResolutionScope() {
        return getXBLBindings().getResolutionScopeByPrefix(fullPrefix);
    }

    protected XBLBindings getXBLBindings() {
        if (xblBindings == null)
            xblBindings = containingDocument.getStaticState().getXBLBindings();
        return xblBindings;
    }

    /**
     * Update the effective id when repeat iterations change.
     *
     * @param effectiveId   effective id of the containing control
     */
    public void updateEffectiveId(String effectiveId) {

        // Remove from parent before updating id
        if (parentXBLContainer != null) {
            parentXBLContainer.removeChild(this);
        }

        // Update all ids
        this.staticId = XFormsUtils.getStaticIdFromId(effectiveId);
        this.effectiveId = effectiveId;
        this.prefixedId = XFormsUtils.getPrefixedId(effectiveId);
        this.fullPrefix = this.prefixedId + XFormsConstants.COMPONENT_SEPARATOR;

        // Add back to parent after updating id
        if (parentXBLContainer != null) {
            // TODO: document order may not be kept anymore
            parentXBLContainer.addChild(this);
        }

        // Clear map
        modelsMap.clear();

        // Update effective ids of all nested models
        for (XFormsModel currentModel: models) {
            // E.g. foo$bar$my-model.1-2 => foo$bar$my-model.1-3
            final String newModelEffectiveId = XFormsUtils.getPrefixedId(currentModel.getEffectiveId()) + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);
            currentModel.updateEffectiveId(newModelEffectiveId);

            // Put in map
            modelsMap.put(currentModel.getEffectiveId(), currentModel);
        }
    }

    /**
     * Remove container and destroy models when a repeat iteration is removed.
     *
     * @param propertyContext   current context
     */
    public void destroy(PropertyContext propertyContext) {
        // Tell parent about it
        if (parentXBLContainer != null) {
            parentXBLContainer.removeChild(this);
        }

        // Dispatch destruction event to all models
        for (XFormsModel currentModel: models) {
            dispatchEvent(propertyContext, new XFormsModelDestructEvent(containingDocument, currentModel));
        }
    }

    public String getFullPrefix() {
        return fullPrefix;
    }

    public XBLContainer getParentXBLContainer() {
        return parentXBLContainer;
    }

    private void addChild(XBLContainer container) {
        if (childrenXBLContainers == null)
            childrenXBLContainers = new LinkedHashMap<String, XBLContainer>();
        childrenXBLContainers.put(container.getEffectiveId(), container);
    }

    private void removeChild(XBLContainer container) {

        final String effectiveId = container.getEffectiveId();
        final Object containerForEffectiveId = childrenXBLContainers.get(effectiveId);

        // Only remove if the object matches, in case another object with same effective id was added in the meanwhile.
        // Possible with repeat iteration updates.
        if (containerForEffectiveId == container)
            childrenXBLContainers.remove(effectiveId);
    }

    /**
     * Return the namespace mappings associated with the given element. The element must be part of this container.
     *
     * @param element       Element to get namespace mapping for
     * @return              Map<String prefix, String uri>
     */
    public Map<String, String> getNamespaceMappings(Element element) {
        return containingDocument.getStaticState().getNamespaceMappings(fullPrefix, element);
    }

    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        this.bindingContext = bindingContext;
        this.contextStack.setParentBindingContext(bindingContext);
    }

    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    public XFormsContextStack.BindingContext getBindingContext(PropertyContext propertyContext, XFormsContainingDocument containingDocument) {
        return getBindingContext();
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public XFormsContextStack getContextStack() {
        return contextStack;
    }

    /**
     * Find the resolution scope of a given prefixed id. The id must belong to the current container.
     *
     * @param prefixedId    prefixed id of XForms element
     * @return              container corresponding to the scope
     */
    public XBLContainer findResolutionScope(String prefixedId) {
        final String xblScopeIdFullPrefix; {
            final XBLBindings.Scope xblScope = getXBLBindings().getResolutionScopeByPrefixedId(prefixedId);
            xblScopeIdFullPrefix = xblScope.getFullPrefix();// e.g. "" or "my-tab$my-component" => "" or "my-tab$my-component$"
        }

        XBLContainer currentContainer = this;
        do {
            if (currentContainer.getFullPrefix().equals(xblScopeIdFullPrefix))
                return currentContainer;

            currentContainer = currentContainer.getParentXBLContainer();
        } while (currentContainer != null);

        throw new OXFException("XBL resolution scope not found for id: " + prefixedId);
    }

    public XBLContainer findResolutionScope(XBLBindings.Scope scope) {
        XBLContainer currentContainer = this;
        do {
            if (currentContainer.getResolutionScope() == scope)
                return currentContainer;

            currentContainer = currentContainer.getParentXBLContainer();
        } while (currentContainer != null);

        throw new OXFException("XBL resolution scope not found for scope id: " + scope.scopeId);
    }

    /**
     * Create and index models corresponding to this container's scope.
     */
    public void addAllModels() {
        // Iterate through all models and finds the one that apply to this container
        for (Map.Entry<String,Document> currentEntry: containingDocument.getStaticState().getModelDocuments().entrySet()) {
            final String modelPrefixedId = currentEntry.getKey();
            final Document modelDocument = currentEntry.getValue();

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
        initializeModels(pipelineContext, new String[] {
                XFormsEvents.XFORMS_MODEL_CONSTRUCT,
                XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE,
                XFormsEvents.XFORMS_READY
        });
    }

    public void initializeModels(PropertyContext propertyContext, String[] eventsToDispatch) {
        for (int i = 0; i < eventsToDispatch.length; i++) {
            if (i == 2) {// before dispatching xforms-ready
                // Initialize controls after all the xforms-model-construct-done events have been sent
                initializeNestedControls(propertyContext);

                // Make sure there is at least one refresh
                requireRefresh();
            }

            // Iterate over all the models
            for (XFormsModel currentModel: models) {
                dispatchEvent(propertyContext, XFormsEventFactory.createEvent(containingDocument, eventsToDispatch[i], currentModel));
            }
        }
    }

    protected void initializeNestedControls(PropertyContext propertyContext) {
        // NOP by default
    }

    /**
     * Return model within this container, with the specified static id, null if not found. If the id is the empty
     * string, return the default model, i.e. the first model.
     */
    public XFormsModel findModelByStaticId(String modelStaticId) {
        return "".equals(modelStaticId)
                ? getDefaultModel()
                : modelsMap.get(fullPrefix + modelStaticId + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId));
    }

    protected void addModel(XFormsModel model) {// move to private once legacy caller is gone
        this.models.add(model);
        this.modelsMap.put(model.getEffectiveId(), model);
    }

    public XFormsModel getDefaultModel() {
        if (models != null && models.size() > 0)
            return models.get(0);
        else
            return null;
    }

    /**
     * Get a list of all the models in this container.
     */
    public List<XFormsModel> getModels() {
        return models;
    }

    /**
     * Get a list of all the models in this container and all sub-containers.
     */
    public List<XFormsModel> getAllModels() {
        final List<XFormsModel> result = new ArrayList<XFormsModel>(models);

        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
                result.addAll(currentContainer.getAllModels());
            }
        }

        return result;
    }

    /**
     * Get object with the effective id specified.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {

        // Search in models
        for (XFormsModel model: models) {
            final Object resultObject = model.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Search in children
        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
                final Object resultObject = currentContainer.getObjectByEffectiveId(effectiveId);
                if (resultObject != null)
                    return resultObject;
            }
        }

        return null;
    }

    /**
     * Return the current repeat index for the given xforms:repeat id, -1 if the id is not found.
     *
     * The repeat must be in the scope of this container.
     *
     * @param sourceEffectiveId effective id of the source (control, model, instance, submission, ...), or null
     * @param repeatStaticId    static id of the target
     * @return                  repeat index, -1 if repeat is not found
     */
    public int getRepeatIndex(String sourceEffectiveId, String repeatStaticId) {
        final XFormsRepeatControl repeatControl = (XFormsRepeatControl) resolveObjectByIdInScope(sourceEffectiveId, repeatStaticId, null);
        if (repeatControl != null) {
            // 1. Found concrete control
            return repeatControl.getIndex();
        } else {

            final String sourcePrefixedId = XFormsUtils.getPrefixedId(sourceEffectiveId);
            final XBLBindings.Scope scope = getXBLBindings().getResolutionScopeByPrefixedId(sourcePrefixedId);
            final String repeatPrefixedId = scope.getPrefixedIdForStaticId(repeatStaticId);

            if (containingDocument.getStaticState().getControlPosition(repeatPrefixedId) >= 0) {
                // 2. Found static control

                // NOTE: above we make sure to use prefixed id, e.g. my-stuff$my-foo-bar$my-repeat
                return 0;
            } else {
                // 3. No repeat element exists
                return -1;
            }
        }
    }

    public Object resolveObjectByIdInScope(String sourceEffectiveId, String targetStaticId, Item contextItem) {
        final String sourcePrefixedId = XFormsUtils.getPrefixedId(sourceEffectiveId);
        final XBLContainer resolutionScopeContainer = findResolutionScope(sourcePrefixedId);
        return resolutionScopeContainer.resolveObjectById(sourceEffectiveId, targetStaticId, contextItem);
    }

    /**
     * Resolve an object in the scope of this container.
     *
     * @param sourceEffectiveId effective id of the source (control, model, instance, submission, ...), or null
     * @param targetStaticId    static id of the target
     * @param contextItem       context item, or null (used for bind resolution only)
     * @return                  object, or null if not found
     */
    public Object resolveObjectById(String sourceEffectiveId, String targetStaticId, Item contextItem) {

        // Make sure the static id passed is actually a static id
        if (!XFormsUtils.isStaticId(targetStaticId))
            throw new OXFException("Target id must be static id: " + targetStaticId);

        if (sourceEffectiveId == null)
            throw new OXFException("Source id must be specified.");

        // 1. Check if requesting the binding id. If so, we interpret this as requesting the bound element
        //    and return the control associated with the bound element.
        // TODO: should this use sourceControlEffectiveId?
        final String bindingId = getXBLBindings().getBindingId(prefixedId);
        if (targetStaticId.equals(bindingId))
            return containingDocument.getControls().getObjectByEffectiveId(effectiveId);

        // 2. Search in directly contained models
        final Object resultModelObject = searchContainedModels(sourceEffectiveId, targetStaticId, contextItem);
        if (resultModelObject != null)
            return resultModelObject;

        // Check that source is resolvable within this container
        if (!isEffectiveIdResolvableByThisContainer(sourceEffectiveId))
            throw new OXFException("Source not resolvable in container: " + sourceEffectiveId);

        // 3. Search in controls

        // NOTE: in the future, sub-tree of components might be rooted in this class

        // Find closest control
        final String sourceControlEffectiveId;
        {
            final Object tempModelObject = searchContainedModels(null, XFormsUtils.getStaticIdFromId(sourceEffectiveId), contextItem);
            if (tempModelObject != null) {
                // Source is a model object, so get first control instead
                sourceControlEffectiveId = getFirstControlEffectiveId();
                if (sourceControlEffectiveId == null)
                    return null;
            } else {
                // Assume the source is a control
                sourceControlEffectiveId = sourceEffectiveId;
            }
        }

        // Resolve on controls
        final XFormsControls controls = containingDocument.getControls();
        final XFormsControl result = (XFormsControl) controls.resolveObjectById(sourceControlEffectiveId, targetStaticId, contextItem);

        // If result is provided, make sure it is within the resolution scope of this container
        if (result != null && !isEffectiveIdResolvableByThisContainer(result.getEffectiveId())) {
            // This should not happen!
            throw new OXFException("Resulting control is no in proper scope: " + result.getEffectiveId());
        } else {
            return result;
        }
    }

    public String getFirstControlEffectiveId() {
        final List<XFormsControl> children = getChildrenControls(containingDocument.getControls());
        String sourceControlEffectiveId;
        if (children != null && children.size() > 0) {
            // We currently don't have a real notion of a "root" control, so we resolve against the first control if any
            final XFormsControl firstControl = children.get(0);
            sourceControlEffectiveId = firstControl.getEffectiveId();
        } else {
            // There are no controls, therefore resolution will not find anything
            sourceControlEffectiveId = null;
        }
        return sourceControlEffectiveId;
    }

    protected List<XFormsControl> getChildrenControls(XFormsControls controls) {
        // We are a nested container so there must be an associated XFormsComponentControl
        final XFormsComponentControl componentControl = (XFormsComponentControl) controls.getCurrentControlTree().getControl(effectiveId);
        return componentControl.getChildren();
    }

    private Object searchContainedModels(String sourceEffectiveId, String targetStaticId, Item contextItem) {
        for (XFormsModel model: models) {
            final Object resultObject = model.resolveObjectById(sourceEffectiveId, targetStaticId, contextItem);
            if (resultObject != null)
                return resultObject;
        }
        return null;
    }

//    private boolean isEffectiveIdWithinThisContainer(String effectiveId) {
//        if (fullPrefix.equals("")) {
//            // Case of root container
//            return XFormsUtils.getEffectiveIdPrefixNoSeparator(effectiveId).equals("");
//        } else {
//            // Nested container
//            // This matches elements within this container, but also the container itself
//            return effectiveId.equals(this.effectiveId) ||  fullPrefix.equals(XFormsUtils.getEffectiveIdPrefix(effectiveId));
//        }
//    }

    private boolean isEffectiveIdResolvableByThisContainer(String effectiveId) {
        return this == findResolutionScope(XFormsUtils.getPrefixedId(effectiveId));
    }

    /**
     * Find the instance containing the specified node, in any model.
     *
     * @param nodeInfo  node contained in an instance
     * @return      instance containing the node
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {

        // Search in models
        for (XFormsModel currentModel: models) {
            final XFormsInstance currentInstance = currentModel.getInstanceForNode(nodeInfo);
            if (currentInstance != null)
                return currentInstance;
        }

        // Search in children
        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
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
        for (XFormsModel currentModel: models) {
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
    protected void serializeInstances(Element instancesElement) {

        // Serialize this container's model's
        for (XFormsModel currentModel: models) {
            currentModel.serializeInstances(instancesElement);
        }

        // Recurse into children containers
        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
                currentContainer.serializeInstances(instancesElement);
            }
        }
    }

    public void restoreModelsState(PropertyContext propertyContext) {
        // Handle this container only
        for (XFormsModel currentModel: models) {
            currentModel.restoreState(propertyContext);
        }
    }

    public void startOutermostActionHandler() {
        // Handle this container
        for (XFormsModel currentModel: models) {
            currentModel.startOutermostActionHandler();
        }
        // Recurse into children containers
        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
                currentContainer.startOutermostActionHandler();
            }
        }
    }

    public void endOutermostActionHandler(PropertyContext propertyContext) {
        synchronizeAndRefresh(propertyContext);
    }

    public void synchronizeAndRefresh(PropertyContext propertyContext) {
        // Below we split RRR and Refresh in order to reduce the number of refreshes performed

        // This is fun. Say you have a single model requiring RRRR and you get here the first time:
        //
        // * Model's rebuildRecalculateRevalidateIfNeeded() runs
        // * Its rebuild runs
        // * That dispatches xforms-rebuild as an outer event
        // * The current method is then called again recursively
        // * So RRR runs, recursively, then comes back here
        //
        // We do not want to run refresh just after coming back from rebuildRecalculateRevalidateIfNeeded(), otherwise
        // because of recursion you might have RRR, then refresh, refresh again, instead of RRR, refresh, RRR, refresh,
        // etc. So:
        //
        // * We first exhaust the possibility for RRR globally
        // * Then we run refresh if possible
        // * Then we check again, as refresh events might have changed things
        //
        // TODO: We might want to implement some code to detect excessive loops/recursion

        while (needRebuildRecalculateRevalidate() || containingDocument.getControls().isRequireRefresh()) {
            while (needRebuildRecalculateRevalidate())
                rebuildRecalculateRevalidateIfNeeded(propertyContext);

            refreshIfNeeded(propertyContext);
        }
    }

    private boolean needRebuildRecalculateRevalidate() {
        for (XFormsModel currentModel: models) {
            if (currentModel.needRebuildRecalculateRevalidate())
                return true;
        }
        // Recurse into children containers
        if (childrenXBLContainers != null) {
            for (XBLContainer currentContainer: childrenXBLContainers.values()) {
                if (currentContainer.needRebuildRecalculateRevalidate())
                    return true;
            }
        }
        return false;
    }

    private void rebuildRecalculateRevalidateIfNeeded(PropertyContext propertyContext) {
        // Handle this container
        for (XFormsModel currentModel: models) {
            currentModel.rebuildRecalculateRevalidateIfNeeded(propertyContext);
        }
        // Recurse into children containers
        if (childrenXBLContainers != null) {
            // NOTE: childrenContainers might be modified down the line and cause a ConcurrentModificationException
            // so make a copy here before processing.
            // TODO: The exact situation is not entirely clear and there might be other places in this class where this
            // might happen!
            final Map<String, XBLContainer> tempMap = new LinkedHashMap<String, XBLContainer>(childrenXBLContainers);
            for (XBLContainer currentContainer: tempMap.values()) {
                currentContainer.rebuildRecalculateRevalidateIfNeeded(propertyContext);
            }
        }
    }

    public void requireRefresh() {
        // Delegate to controls
        // Note that we don't recurse as for now refresh is global
        containingDocument.getControls().requireRefresh();
    }

    public void refreshIfNeeded(PropertyContext propertyContext) {
        // Delegate to controls
        containingDocument.getControls().refreshIfNeeded(propertyContext, this);
    }

    public void rebuildRecalculateIfNeeded(PropertyContext propertyContext) {
        // Handle this container
        for (XFormsModel currentModel: models) {
            currentModel.rebuildRecalculateIfNeeded(propertyContext);
        }
        // Recurse into children containers
        if (childrenXBLContainers != null) {
            // NOTE: childrenContainers might be modified down the line and cause a ConcurrentModificationException
            // so make a copy here before processing.
            // TODO: The exact situation is not entirely clear and there might be other places in this class where this
            // might happen!
            final Map<String, XBLContainer> tempMap = new LinkedHashMap<String, XBLContainer>(childrenXBLContainers);
            for (XBLContainer currentContainer: tempMap.values()) {
                currentContainer.rebuildRecalculateIfNeeded(propertyContext);
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

    public XFormsEventObserver getParentEventObserver(XBLContainer container) {
        // There is no point for events to propagate beyond the container
        return null;
    }

    public void performDefaultAction(PropertyContext propertyContext, XFormsEvent event) {

        // NOTE: This event must be dispatched to elements that support bindings, but at the moment XFormsContextStack
        // doesn't know how to perform such dispatches so it dispatches to the container. This is of minor consequence
        // since the event is fatal, but this should be fixed in the future.
        final String eventName = event.getName();
        if (XFormsEvents.XFORMS_BINDING_EXCEPTION.equals(eventName)) {
            // The default action for this event results in the following: Fatal error.
            throw new ValidationException("Binding exception for target: " + event.getTargetObject().getEffectiveId(), event.getTargetObject().getLocationData());
        }
    }

    public void performTargetAction(PropertyContext propertyContext, XBLContainer container, XFormsEvent event) {
        // NOP
    }

    public List getEventHandlers(XBLContainer container) {
        return null;
    }

    public XBLContainer getXBLContainer(XFormsContainingDocument containingDocument) {
        return this;
    }

    /**
     * Main event dispatching entry.
     */
    public void dispatchEvent(PropertyContext propertyContext, XFormsEvent originalEvent) {

        // XXFormsValueChangeWithFocusChangeEvent is always transformed into DOMFocusIn/Out
        assert !(originalEvent instanceof XXFormsValueChangeWithFocusChangeEvent);

        final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY);

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.startHandleOperation("dispatchEvent", "dispatching", "name", originalEvent.getName(), "id", originalEvent.getTargetObject().getEffectiveId(), "location",
                    originalEvent.getLocationData() != null ? originalEvent.getLocationData().toString() : null);
        }

        final XFormsEventTarget targetObject = originalEvent.getTargetObject();
        try {
            if (targetObject == null)
                throw new ValidationException("Target object null for event: " + originalEvent.getName(), getLocationData());

            // Find all event handler containers
            final List<XFormsEventObserver> boundaries = new ArrayList<XFormsEventObserver>();
            final Map<String, XFormsEvent> eventsForBoundaries = new LinkedHashMap<String, XFormsEvent>();// Map<String effectiveId, XFormsEvent event>
            final List<XFormsEventObserver> eventObservers = new ArrayList<XFormsEventObserver>();
            {
                XFormsEventObserver eventObserver
                        = (targetObject instanceof XFormsEventObserver) ? (XFormsEventObserver) targetObject : targetObject.getParentEventObserver(this);
                while (eventObserver != null) {
                    if (!((eventObserver instanceof XFormsRepeatControl && targetObject != eventObserver) || eventObserver instanceof XXFormsRootControl)) {
                        // Repeat is not an observer (repeat iterations are) UNLESS it is the direct target of the event

                        if (eventObserver instanceof XFormsComponentControl && targetObject != eventObserver) {
                            // Either retarget, or stop propagation if the event is trying to go through the component boundary
                            if (originalEvent instanceof XFormsUIEvent) {
                                // UI events need to be retargeted
                                boundaries.add(eventObserver);
                                eventsForBoundaries.put(eventObserver.getEffectiveId(), null);
                            } else {
                                // Stop propagation on model container or component boundary for all non-UI events
                                break;
                            }
                        }
                        // Add the observer
                        eventObservers.add(eventObserver);
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
            XFormsEvent retargetedEvent;

            // Handle event retargeting
            if (boundaries.size() == 0) {
                // Original event all the way
                nextBoundaryEffectiveId = null;
                retargetedEvent = originalEvent;
            } else {
                // Start with retargeted event
                final XFormsEventObserver observer = boundaries.get(nextBoundaryIndex);
                nextBoundaryEffectiveId = observer.getEffectiveId();
                retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                nextBoundaryIndex++;
            }

            // Capture phase
            for (XFormsEventObserver currentEventObserver: eventObservers) {
                final List currentEventHandlers = currentEventObserver.getEventHandlers(this);
                if (currentEventHandlers != null) {
                    if (currentEventObserver != targetObject) {
                        // Event listeners on the target are handled separately

                        // Process event handlers
                        for (Object currentEventHandler: currentEventHandlers) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) currentEventHandler;
                            if (eventHandler.isCapturePhaseOnly() && eventHandler.isMatch(retargetedEvent)) {
                                // Capture phase match on event name and target is specified
                                indentedLogger.startHandleOperation("dispatchEvent", "capture handler");
                                containingDocument.startHandleEvent(retargetedEvent);
                                try {
                                    retargetedEvent.setCurrentPhase(XFormsEvent.Phase.capture);
                                    eventHandler.handleEvent(propertyContext, currentEventObserver.getXBLContainer(containingDocument), currentEventObserver, retargetedEvent);
                                } finally {
                                    containingDocument.endHandleEvent();
                                    indentedLogger.endHandleOperation();
                                }
                                propagate &= eventHandler.isPropagate();
                                performDefaultAction &= eventHandler.isPerformDefaultAction();
                            }
                        }
                        // Cancel propagation if requested and if authorized by event
                        if (!propagate && retargetedEvent.isCancelable())
                            break;
                    }
                }

                // Handle event retargeting
                if (nextBoundaryEffectiveId != null && currentEventObserver.getEffectiveId().equals(nextBoundaryEffectiveId)) {

                    if (nextBoundaryIndex == boundaries.size()) {
                        // Original event
                        nextBoundaryEffectiveId = null;
                        retargetedEvent = originalEvent;
                    } else {
                        // Retargeted event
                        final XFormsEventObserver observer = boundaries.get(nextBoundaryIndex);
                        nextBoundaryEffectiveId = observer.getEffectiveId();
                        retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                        nextBoundaryIndex++;
                    }

                    if (indentedLogger.isDebugEnabled()) {
                        indentedLogger.logDebug("dispatchEvent", "retargeting",
                                "name", originalEvent.getName(),
                                "original id", originalEvent.getTargetObject().getEffectiveId(),
                                "new id", retargetedEvent.getTargetObject().getEffectiveId()
                        );
                    }
                }
            }

            // Target and bubbling phases
            if (propagate) {

                // Go from leaf to root
                Collections.reverse(eventObservers);
                Collections.reverse(boundaries);

                // Handle event retargeting
                if (boundaries.size() > 0) {
                    nextBoundaryIndex--;
                    final XFormsEventObserver observer = boundaries.get(nextBoundaryIndex);
                    nextBoundaryEffectiveId = observer.getEffectiveId();
                }

                for (XFormsEventObserver currentEventObserver: eventObservers) {
                    final List currentEventHandlers = currentEventObserver.getEventHandlers(this);

                    // Handle event retargeting
                    if (nextBoundaryEffectiveId != null && currentEventObserver.getEffectiveId().equals(nextBoundaryEffectiveId)) {

                        // Retargeted event
                        final XFormsEventObserver observer = boundaries.get(nextBoundaryIndex);
                        nextBoundaryEffectiveId = observer.getEffectiveId();
                        retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundaryEffectiveId, observer, originalEvent);
                        nextBoundaryIndex--;

                        if (indentedLogger.isDebugEnabled()) {
                            indentedLogger.logDebug("dispatchEvent", "retargeting",
                                    "name", originalEvent.getName(),
                                    "original id", originalEvent.getTargetObject().getEffectiveId(),
                                    "new id", retargetedEvent.getTargetObject().getEffectiveId()
                            );
                        }
                    }

                    // Process "action at target"
                    // NOTE: This is used XFormsInstance for xforms-insert/xforms-delete processing
                    final boolean isAtTarget = currentEventObserver == targetObject;
                    if (isAtTarget) {
                        currentEventObserver.performTargetAction(propertyContext, currentEventObserver.getXBLContainer(containingDocument), retargetedEvent);
                    }

                    // Process event handlers
                    if (currentEventHandlers != null) {
                        for (Object currentEventHandler: currentEventHandlers) {
                            final XFormsEventHandler eventHandler = (XFormsEventHandler) currentEventHandler;
                            if ((eventHandler.isTargetPhase() && isAtTarget || eventHandler.isBubblingPhase() && !isAtTarget && originalEvent.isBubbles())
                                    && eventHandler.isMatch(retargetedEvent)) {
                                // Bubbling phase match on event name and target is specified
                                indentedLogger.startHandleOperation("dispatchEvent", isAtTarget ? "target handler" : "bubble handler");
                                containingDocument.startHandleEvent(retargetedEvent);
                                try {
                                    retargetedEvent.setCurrentPhase(isAtTarget ? XFormsEvent.Phase.target : XFormsEvent.Phase.bubbling);
                                    eventHandler.handleEvent(propertyContext, currentEventObserver.getXBLContainer(containingDocument), currentEventObserver, retargetedEvent);
                                } finally {
                                    containingDocument.endHandleEvent();
                                    indentedLogger.endHandleOperation();
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
                indentedLogger.startHandleOperation("dispatchEvent", "default action handler");
                containingDocument.startHandleEvent(originalEvent);
                try {
                    targetObject.performDefaultAction(propertyContext, originalEvent);
                } finally {
                    containingDocument.endHandleEvent();
                    indentedLogger.endHandleOperation();
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
                    "event", originalEvent.getName(), "target id", targetObject.getEffectiveId()));
        }

        if (indentedLogger.isDebugEnabled()) {
            indentedLogger.endHandleOperation("name", originalEvent.getName(), "id", originalEvent.getTargetObject().getEffectiveId(), "location",
                    originalEvent.getLocationData() != null ? originalEvent.getLocationData().toString() : null);
        }
    }

    private XFormsEvent getRetargetedEvent(Map<String, XFormsEvent> eventsForBoundaries, String boundaryId, XFormsEventTarget newEventTarget, XFormsEvent originalEvent) {
        XFormsEvent retargetedEvent = eventsForBoundaries.get(boundaryId);

        // Event already created, just return it
        if (retargetedEvent != null)
            return retargetedEvent;

        // Clone original event, retarget it, and remember it
        retargetedEvent = originalEvent.retarget(newEventTarget);
        eventsForBoundaries.put(boundaryId, retargetedEvent);

        return retargetedEvent;
    }

    public void setDeferredFlagsForSetindex() {
        // XForms 1.1: "This action affects deferred updates by performing deferred update in its initialization and by
        // setting the deferred update flags for recalculate, revalidate and refresh."
        for (XFormsModel currentModel: models) {
            // NOTE: We used to do this, following XForms 1.0, but XForms 1.1 has changed the behavior
            //currentModel.getBinds().rebuild(pipelineContext);

            final XFormsModel.DeferredActionContext deferredActionContext = currentModel.getDeferredActionContext();
            deferredActionContext.recalculate = true;
            deferredActionContext.revalidate = true;
        }
        // This container requires refresh
        requireRefresh();
    }

    public boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName) {
        return false;
    }
}
