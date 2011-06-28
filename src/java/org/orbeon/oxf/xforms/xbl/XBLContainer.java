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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.action.XFormsActions;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsRootControl;
import org.orbeon.oxf.xforms.event.EventListener;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xforms.event.events.*;
import org.orbeon.oxf.xml.NamespaceMapping;
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

    // Containing control if any
    private final XFormsControl associatedControl;

    /**
     * Create a new container child of the given control
     *
     * @param associatedControl  containing control
     * @return                   new XFormsContainer
     */
    public XBLContainer createChildContainer(XFormsControl associatedControl) {
        return new XBLContainer(associatedControl, this);
    }

    public XBLContainer createChildContainer(XFormsControl associatedControl, final PartAnalysis partAnalysis) {

        return new XBLContainer(associatedControl, this) {
            @Override
            public PartAnalysis getPartAnalysis() {
                // Start with specific part
                return partAnalysis;
            }
        };
    }

    protected XBLContainer(XFormsControl associatedControl, XBLContainer parentXBLContainer) {
        this(XFormsUtils.getStaticIdFromId(associatedControl.getEffectiveId()),
                associatedControl.getEffectiveId(),
                XFormsUtils.getPrefixedId(associatedControl.getEffectiveId()),
                XFormsUtils.getPrefixedId(associatedControl.getEffectiveId()) + XFormsConstants.COMPONENT_SEPARATOR,
                parentXBLContainer,
                associatedControl);
    }

    protected XBLContainer(String staticId, String effectiveId, String prefixedId, String fullPrefix, XBLContainer parentXBLContainer, XFormsControl associatedControl) {

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

        this.associatedControl = associatedControl;
    }

    public XBLBindingsBase.Scope getResolutionScope() {
        return containingDocument.getStaticOps().getResolutionScopeByPrefix(fullPrefix);
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
        for (final XFormsModel currentModel: models) {
            // E.g. foo$bar$my-model.1-2 => foo$bar$my-model.1-3
            final String newModelEffectiveId = XFormsUtils.getPrefixedId(currentModel.getEffectiveId()) + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);
            currentModel.updateEffectiveId(newModelEffectiveId);

            // Put in map
            modelsMap.put(currentModel.getEffectiveId(), currentModel);
        }
    }

    /**
     * Remove container and destroy models when a repeat iteration is removed.
     */
    public void destroy() {
        // Tell parent about it
        if (parentXBLContainer != null) {
            parentXBLContainer.removeChild(this);
        }

        // Dispatch destruction event to all models
        for (final XFormsModel model: models) {
            dispatchEvent(new XFormsModelDestructEvent(containingDocument, model));
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
     * @param element       element to get namespace mapping for
     * @return              mapping
     */
    public NamespaceMapping getNamespaceMappings(Element element) {
        return getPartAnalysis().getNamespaceMapping(fullPrefix, element);
    }

    public void setBindingContext(XFormsContextStack.BindingContext bindingContext) {
        this.bindingContext = bindingContext;
        this.contextStack.setParentBindingContext(bindingContext);
    }

    public XFormsContextStack.BindingContext getBindingContext() {
        return bindingContext;
    }

    public XFormsContextStack.BindingContext getBindingContext(XFormsContainingDocument containingDocument) {
        return getBindingContext();
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public PartAnalysis getPartAnalysis() {
        // By default return parent part analysis
        return (parentXBLContainer != null) ? parentXBLContainer.getPartAnalysis() : null;
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
            final XBLBindingsBase.Scope xblScope = getPartAnalysis().getResolutionScopeByPrefixedId(prefixedId);
            if (xblScope == null)
                throw new IllegalArgumentException("Prefixed id not found in current part: " + prefixedId);
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

    public XBLContainer findResolutionScope(XBLBindingsBase.Scope scope) {
        XBLContainer currentContainer = this;
        do {
            if (currentContainer.getResolutionScope() == scope)
                return currentContainer;

            currentContainer = currentContainer.getParentXBLContainer();
        } while (currentContainer != null);

        throw new OXFException("XBL resolution scope not found for scope id: " + scope.scopeId);
    }

    /**
     * Check whether if the bind id can resolve in this scope
     *
     * @param bindId    bind id to check
     * @return
     */
    public boolean containsBind(String bindId) {
        for (final Model model : getPartAnalysis().getModelsForScope(getResolutionScope())) {
            if (model.containsBind(bindId))
                return true;
        }
        return false;
    }

    /**
     * Create and index models corresponding to this container's scope.
     */
    public void addAllModels() {
        // Iterate through all models and finds the one that apply to this container
        for (final Model model: getPartAnalysis().getModelsForScope(getResolutionScope())) {
            // Find model's effective id, e.g. if container's effective id is foo$bar.1-2 and models static id is
            // my-model => foo$bar$my-model.1-2
            final String modelEffectiveId = model.prefixedId() + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);

            // Create and add model
            addModel(new XFormsModel(this, modelEffectiveId, model));
        }
    }

    private void addModel(XFormsModel model) {
        this.models.add(model);
        this.modelsMap.put(model.getEffectiveId(), model);
    }

    protected void initializeModels() {

        // 4.2 Initialization Events

        // 1. Dispatch xforms-model-construct to all models
        // 2. Dispatch xforms-model-construct-done to all models
        // 3. Dispatch xforms-ready to all models
        initializeModels(new String[] {
                XFormsEvents.XFORMS_MODEL_CONSTRUCT,
                XFormsEvents.XFORMS_MODEL_CONSTRUCT_DONE,
                XFormsEvents.XFORMS_READY
        });
    }

    public void initializeModels(String[] eventsToDispatch) {
        for (int i = 0; i < eventsToDispatch.length; i++) {
            if (i == 2) {// before dispatching xforms-ready
                // Initialize controls after all the xforms-model-construct-done events have been sent
                initializeNestedControls();

                // Make sure there is at least one refresh
                requireRefresh();
            }

            // Iterate over all the models
            for (final XFormsModel model: models) {
                dispatchEvent(XFormsEventFactory.createEvent(containingDocument, eventsToDispatch[i], model));
            }
        }
    }

    protected void initializeNestedControls() {
        // NOP by default
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
     * Get a list of all the relevant models in this container and all sub-containers.
     */
    public List<XFormsModel> getAllModels() {
        if (isRelevant()) {
            // Add local models
            final List<XFormsModel> result = new ArrayList<XFormsModel>(models);
            // Add models in children containers
            if (childrenXBLContainers != null) {
                for (final XBLContainer container: childrenXBLContainers.values()) {
                    result.addAll(container.getAllModels());
                }
            }

            return result;
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Get object with the effective id specified within this container or descendant containers.
     *
     * @param effectiveId   effective id of the target
     * @return              object, or null if not found
     */
    public Object getObjectByEffectiveId(String effectiveId) {

        if (isRelevant()) {
            // Search in models
            for (final XFormsModel model: models) {
                final Object resultObject = model.getObjectByEffectiveId(effectiveId);
                if (resultObject != null)
                    return resultObject;
            }

            // Search in children
            if (childrenXBLContainers != null) {
                for (XBLContainer container: childrenXBLContainers.values()) {
                    final Object resultObject = container.getObjectByEffectiveId(effectiveId);
                    if (resultObject != null)
                        return resultObject;
                }
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
            final XBLBindingsBase.Scope scope = getPartAnalysis().getResolutionScopeByPrefixedId(sourcePrefixedId);
            final String repeatPrefixedId = scope.getPrefixedIdForStaticId(repeatStaticId);

            if (containingDocument.getStaticOps().getControlPosition(repeatPrefixedId) >= 0) {
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
        final String bindingId = containingDocument.getStaticOps().getBindingId(prefixedId);
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
        for (final XFormsModel model: models) {
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
     * Find the instance containing the specified node, in any relevant model.
     *
     * @param nodeInfo  node contained in an instance
     * @return      instance containing the node
     */
    public XFormsInstance getInstanceForNode(NodeInfo nodeInfo) {

        if (isRelevant()) {
            // Search in models
            for (final XFormsModel model: models) {
                final XFormsInstance instance = model.getInstanceForNode(nodeInfo);
                if (instance != null)
                    return instance;
            }

            // Search in children
            if (childrenXBLContainers != null) {
                for (final XBLContainer container: childrenXBLContainers.values()) {
                    final XFormsInstance instance = container.getInstanceForNode(nodeInfo);
                    if (instance != null)
                        return instance;
                }
            }
        }

        // This should not happen if the node is currently in an instance!
        return null;
    }

    /**
     * Find the instance with the specified id, searching in any relevant model.
     *
     * @param instanceId id of the instance to find
     * @return      instance containing the node
     */
    public XFormsInstance findInstance(String instanceId) {
        if (isRelevant()) {
            for (final XFormsModel model: models) {
                final XFormsInstance instance = model.getInstance(instanceId);
                if (instance != null)
                    return instance;
            }
        }
        return null;
    }

    /**
     * Serialize all the instances of this container and children containers.
     *
     * @param instancesElement  container element
     */
    protected void serializeInstances(Element instancesElement) {
        // Only serialize if we are relevant
        if (isRelevant()) {
            // Serialize this container's model's
            for (final XFormsModel currentModel: models) {
                currentModel.serializeInstances(instancesElement);
            }

            // Recurse into children containers
            if (childrenXBLContainers != null) {
                for (final XBLContainer currentContainer: childrenXBLContainers.values()) {
                    currentContainer.serializeInstances(instancesElement);
                }
            }
        }
    }

    /**
     * Whether this container is relevant, i.e. either is a top-level container OR is within a relevant container control.
     *
     * @return  true iif container is relevant
     */
    public boolean isRelevant() {
        // componentControl will be null if we are at the top-level
        return (associatedControl == null) || associatedControl.isRelevant();
    }

    public void restoreModelsState() {
        // Handle this container only
        for (final XFormsModel model: models) {
            model.restoreState();
        }
    }

    public void startOutermostActionHandler() {
//        if (isRelevant()) {
            // Handle this container
            for (XFormsModel model: models) {
                model.startOutermostActionHandler();
            }
            // Recurse into children containers
            if (childrenXBLContainers != null) {
                for (XBLContainer container: childrenXBLContainers.values()) {
                    container.startOutermostActionHandler();
                }
            }
//        }
    }

    public void endOutermostActionHandler() {
//        if (isRelevant()) {
            synchronizeAndRefresh();
//        }
    }

    public void synchronizeAndRefresh() {
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
                rebuildRecalculateRevalidateIfNeeded();

            refreshIfNeeded();
        }
    }

    private boolean needRebuildRecalculateRevalidate() {
        if (isRelevant()) {
            for (final XFormsModel model: models) {
                if (model.needRebuildRecalculateRevalidate())
                    return true;
            }
            // Recurse into children containers
            if (childrenXBLContainers != null) {
                for (final XBLContainer container: childrenXBLContainers.values()) {
                    if (container.needRebuildRecalculateRevalidate())
                        return true;
                }
            }
        }
        return false;
    }

    private void rebuildRecalculateRevalidateIfNeeded() {
        if (isRelevant()) {
            // Handle this container
            for (final XFormsModel model: models) {
                model.rebuildRecalculateRevalidateIfNeeded();
            }
            // Recurse into children containers
            if (childrenXBLContainers != null) {
                // NOTE: childrenContainers might be modified down the line and cause a ConcurrentModificationException
                // so make a copy here before processing.
                // TODO: The exact situation is not entirely clear and there might be other places in this class where this
                // might happen!
                final Map<String, XBLContainer> tempMap = new LinkedHashMap<String, XBLContainer>(childrenXBLContainers);
                for (final XBLContainer container: tempMap.values()) {
                    container.rebuildRecalculateRevalidateIfNeeded();
                }
            }
        }
    }

    public void requireRefresh() {
        // Note that we don't recurse into children container as for now refresh is global
        final XFormsControls controls = containingDocument.getControls();
        if (controls.isInitialized()) {
            // Controls exist, otherwise there is no point in doing anything controls-related
            controls.requireRefresh();
        }
    }

    public void refreshIfNeeded() {
        // Delegate to controls
        containingDocument.getControls().refreshIfNeeded(this);
    }

    public void rebuildRecalculateIfNeeded() {
        if (isRelevant()) {
            // Handle this container
            for (final XFormsModel model: models) {
                model.rebuildRecalculateIfNeeded();
            }
            // Recurse into children containers
            if (childrenXBLContainers != null) {
                // NOTE: childrenContainers might be modified down the line and cause a ConcurrentModificationException
                // so make a copy here before processing.
                // TODO: The exact situation is not entirely clear and there might be other places in this class where this
                // might happen!
                final Map<String, XBLContainer> tempMap = new LinkedHashMap<String, XBLContainer>(childrenXBLContainers);
                for (final XBLContainer container: tempMap.values()) {
                    container.rebuildRecalculateIfNeeded();
                }
            }
        }
    }

    public String getId() {
        return staticId;
    }

    public String getPrefixedId() {
        return prefixedId;
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

    public void performDefaultAction(XFormsEvent event) {

        // NOTE: This event must be dispatched to elements that support bindings, but at the moment XFormsContextStack
        // doesn't know how to perform such dispatches so it dispatches to the container. This is of minor consequence
        // since the event is fatal, but this should be fixed in the future.
        final String eventName = event.getName();
        if (XFormsEvents.XFORMS_BINDING_EXCEPTION.equals(eventName)) {
            // The default action for this event results in the following: Fatal error.
            final XFormsBindingExceptionEvent bindingExceptionEvent = (XFormsBindingExceptionEvent) event;
            throw new ValidationException("Binding exception for target: " + event.getTargetObject().getEffectiveId(),
                    bindingExceptionEvent.getThrowable(), event.getTargetObject().getLocationData());
        } else if (XFormsEvents.XXFORMS_ACTION_ERROR.equals(eventName)) {
            // Log error
            final XXFormsActionErrorEvent ev = (XXFormsActionErrorEvent) event;
            final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY);
            indentedLogger.logError("action", "exception while running action", ev.toStringArray());
        }
    }

    public void performTargetAction(XBLContainer container, XFormsEvent event) {
        // NOP
    }

    public XBLContainer getXBLContainer(XFormsContainingDocument containingDocument) {
        return this;
    }

    /**
     * Main event dispatching entry.
     */
    public void dispatchEvent(XFormsEvent originalEvent) {

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
            XFormsEventObserver nextBoundary;
            XFormsEvent retargetedEvent;

            // Handle event retargeting
            if (boundaries.size() == 0) {
                // Original event all the way
                nextBoundary = null;
                retargetedEvent = originalEvent;
            } else {
                // Start with retargeted event
                nextBoundary = boundaries.get(nextBoundaryIndex);
                retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundary, originalEvent);
                nextBoundaryIndex++;
            }

            // Capture phase
            for (XFormsEventObserver currentEventObserver: eventObservers) {

                final List<XFormsEventHandler> currentEventHandlers =
                        currentEventObserver.getXBLContainer(containingDocument).getPartAnalysis().getEventHandlers(XFormsUtils.getPrefixedId(currentEventObserver.getEffectiveId()));
                if (currentEventHandlers != null) {
                    if (currentEventObserver != targetObject) {
                        // Event listeners on the target are handled separately

                        // Process event handlers
                        for (final XFormsEventHandler eventHandler : currentEventHandlers) {
                            if (eventHandler.isCapturePhaseOnly() && eventHandler.isMatch(retargetedEvent)) {
                                // Capture phase match on event name and target is specified
                                indentedLogger.startHandleOperation("dispatchEvent", "capture handler");
                                containingDocument.startHandleEvent(retargetedEvent);
                                try {
                                    retargetedEvent.setCurrentPhase(XFormsEvent.Phase.capture);
                                    eventHandler.handleEvent(currentEventObserver.getXBLContainer(containingDocument), currentEventObserver, retargetedEvent);
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
                if (currentEventObserver == nextBoundary) {

                    if (nextBoundaryIndex == boundaries.size()) {
                        // Original event
                        nextBoundary = null;
                        retargetedEvent = originalEvent;
                    } else {
                        // Retargeted event
                        nextBoundary = boundaries.get(nextBoundaryIndex);
                        retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundary, originalEvent);
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

                // Handle event retargeting
                Collections.reverse(boundaries);
                nextBoundaryIndex = 0;
                if (boundaries.size() > 0) {
                    nextBoundary = boundaries.get(nextBoundaryIndex);
                    nextBoundaryIndex++;
                }

                for (XFormsEventObserver currentEventObserver: eventObservers) {
                    final List<XFormsEventHandler> currentEventHandlers =
                            currentEventObserver.getXBLContainer(containingDocument).getPartAnalysis().getEventHandlers(XFormsUtils.getPrefixedId(currentEventObserver.getEffectiveId()));

                    // Handle event retargeting
                    if (currentEventObserver == nextBoundary) {

                        // Retargeted event
                        retargetedEvent = getRetargetedEvent(eventsForBoundaries, nextBoundary, originalEvent);

                        if (nextBoundaryIndex == boundaries.size()) {
                            nextBoundary = null;
                        } else {
                            nextBoundary = boundaries.get(nextBoundaryIndex);
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

                    // Process "action at target"
                    // NOTE: As of 2011-03-07, this is used XFormsInstance for xforms-insert/xforms-delete processing,
                    // and in XFormsUploadControl for upload processing.
                    // NOTE: Possibly testing on retargetedEvent.getTargetObject() might be more correct, but we should
                    // fix event retargeting to make more sense in the first place.
                    final boolean isAtTarget = currentEventObserver == targetObject;
                    if (isAtTarget) {
                        currentEventObserver.performTargetAction(currentEventObserver.getXBLContainer(containingDocument), retargetedEvent);
                    }

                    // Process event handlers
                    if (currentEventHandlers != null) {
                        for (final XFormsEventHandler eventHandler : currentEventHandlers) {
                            if ((eventHandler.isTargetPhase() && isAtTarget || eventHandler.isBubblingPhase() && !isAtTarget && originalEvent.isBubbles())
                                    && eventHandler.isMatch(retargetedEvent)) {
                                // Bubbling phase match on event name and target is specified
                                indentedLogger.startHandleOperation("dispatchEvent", isAtTarget ? "target handler" : "bubble handler");
                                containingDocument.startHandleEvent(retargetedEvent);
                                try {
                                    retargetedEvent.setCurrentPhase(isAtTarget ? XFormsEvent.Phase.target : XFormsEvent.Phase.bubbling);
                                    eventHandler.handleEvent(currentEventObserver.getXBLContainer(containingDocument), currentEventObserver, retargetedEvent);
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

                    // Custom event listeners
                    // TODO: Would be nice to have all listeners implemented this way
                    final List<EventListener> customListeners = currentEventObserver.getListeners(originalEvent.getName());
                    if (customListeners != null) {
                        for (final EventListener listener : customListeners)
                            listener.handleEvent(retargetedEvent);
                    }
                }
            }

            // Perform default action if allowed to
            if (performDefaultAction || !originalEvent.isCancelable()) {
                indentedLogger.startHandleOperation("dispatchEvent", "default action handler");
                containingDocument.startHandleEvent(originalEvent);
                try {
                    targetObject.performDefaultAction(originalEvent);
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

    private XFormsEvent getRetargetedEvent(Map<String, XFormsEvent> eventsForBoundaries, XFormsEventTarget newEventTarget, XFormsEvent originalEvent) {
        XFormsEvent retargetedEvent = eventsForBoundaries.get(newEventTarget.getEffectiveId());

        // Event already created, just return it
        if (retargetedEvent != null)
            return retargetedEvent;

        // Clone original event, retarget it, and remember it
        retargetedEvent = originalEvent.retarget(newEventTarget);
        eventsForBoundaries.put(newEventTarget.getEffectiveId(), retargetedEvent);

        return retargetedEvent;
    }

    public void setDeferredFlagsForSetindex() {
        // XForms 1.1: "This action affects deferred updates by performing deferred update in its initialization and by
        // setting the deferred update flags for recalculate, revalidate and refresh."
        for (final XFormsModel model : models) {
            // NOTE: We used to do this, following XForms 1.0, but XForms 1.1 has changed the behavior
            //currentModel.getBinds().rebuild(pipelineContext);

            model.markValueChange(null, false);
        }
    }

    public boolean allowExternalEvent(IndentedLogger indentedLogger, String logType, String eventName) {
        return false;
    }

    public void addListener(String eventName, EventListener listener) {
        throw new UnsupportedOperationException();
    }

    public void removeListener(String eventName, EventListener listener) {
        throw new UnsupportedOperationException();
    }

    public List<EventListener> getListeners(String eventName) {
        return null;
    }
}
