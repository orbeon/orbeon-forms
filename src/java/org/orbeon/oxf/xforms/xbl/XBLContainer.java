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
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.controls.ComponentControl;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.control.XFormsComponentControl;
import org.orbeon.oxf.xforms.control.XFormsContainerControl;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatControl;
import org.orbeon.oxf.xforms.control.controls.XFormsRepeatIterationControl;
import org.orbeon.oxf.xforms.event.Dispatch;
import org.orbeon.oxf.xforms.event.XFormsEventFactory;
import org.orbeon.oxf.xforms.event.XFormsEvents;
import org.orbeon.oxf.xforms.event.events.XFormsModelDestructEvent;
import org.orbeon.oxf.xml.NamespaceMapping;
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
public class XBLContainer implements XFormsObjectResolver {

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

    private XFormsContainingDocument containingDocument;

    private final XFormsContextStack contextStack;  // for controls under this container

    private List<XFormsModel> models = new ArrayList<XFormsModel>();

    // Containing control if any
    // NOTE: null if this instanceof XFormsContainingDocument BUT could use a root control instead!
    private final XFormsControl associatedControl;

    private final Scope _innerScope;

    public Scope innerScope() {
        return _innerScope;
    }

    /**
     * Create a new container child of the given control
     *
     * @param associatedControl  containing control
     * @return                   new XFormsContainer
     */
    public XBLContainer createChildContainer(XFormsComponentControl associatedControl) {
        return new XBLContainer(associatedControl, this, ((ComponentControl) associatedControl.staticControl()).binding().innerScope());
    }

    public XBLContainer createChildContainer(XFormsControl associatedControl, final PartAnalysis partAnalysis) {

        return new XBLContainer(associatedControl, this, partAnalysis.startScope()) {
            @Override
            public PartAnalysis getPartAnalysis() {
                // Start with specific part
                return partAnalysis;
            }
        };
    }

    protected XBLContainer(XFormsControl associatedControl, XBLContainer parentXBLContainer, Scope innerScope) {
        this(associatedControl.getEffectiveId(),
             XFormsUtils.getPrefixedId(associatedControl.getEffectiveId()),
             XFormsUtils.getPrefixedId(associatedControl.getEffectiveId()) + XFormsConstants.COMPONENT_SEPARATOR,
             parentXBLContainer,
             associatedControl,
             innerScope);
    }

    protected XBLContainer(String effectiveId, String prefixedId, String fullPrefix, XBLContainer parentXBLContainer, XFormsControl associatedControl, Scope innerScope) {

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
        this._innerScope = innerScope;
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
        this.effectiveId = effectiveId;
        this.prefixedId = XFormsUtils.getPrefixedId(effectiveId);
        this.fullPrefix = this.prefixedId + XFormsConstants.COMPONENT_SEPARATOR;

        // Add back to parent after updating id
        if (parentXBLContainer != null) {
            // TODO: document order may not be kept anymore
            parentXBLContainer.addChild(this);
        }

        // Update effective ids of all nested models
        for (final XFormsModel currentModel: models) {
            // E.g. foo$bar$my-model.1-2 => foo$bar$my-model.1-3
            final String newModelEffectiveId = XFormsUtils.getPrefixedId(currentModel.getEffectiveId()) + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);
            currentModel.updateEffectiveId(newModelEffectiveId);
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
            Dispatch.dispatchEvent(new XFormsModelDestructEvent(containingDocument, model));
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

    // Find the root container for the given prefixed id, starting with the current container.
    // This is the container which has the given scope as inner scope.
    // The prefixed id must be within the current container.
    public XBLContainer findScopeRoot(String prefixedId) {
        final Scope scope = getPartAnalysis().scopeForPrefixedId(prefixedId);
        if (scope == null)
            throw new IllegalArgumentException("Prefixed id not found in current part: " + prefixedId);

        return findScopeRoot(scope);
    }

    // Find the root container for the given scope, starting with the current container.
    // This is the container which has the given scope as inner scope.
    public XBLContainer findScopeRoot(Scope scope) {
        XBLContainer currentContainer = this;
        do {
            if (currentContainer.innerScope() == scope)
                return currentContainer;

            currentContainer = currentContainer.getParentXBLContainer();
        } while (currentContainer != null);

        throw new OXFException("XBL resolution scope not found for scope id: " + scope.scopeId());
    }

    /**
     * Check whether if the bind id can resolve in this scope
     *
     * @param bindId    bind id to check
     * @return
     */
    public boolean containsBind(String bindId) {
        for (final Model model : getPartAnalysis().jGetModelsForScope(innerScope())) {
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
        for (final Model model: getPartAnalysis().jGetModelsForScope(innerScope())) {
            // Find model's effective id, e.g. if container's effective id is foo$bar.1-2 and models static id is
            // my-model => foo$bar$my-model.1-2
            final String modelEffectiveId = model.prefixedId() + XFormsUtils.getEffectiveIdSuffixWithSeparator(effectiveId);

            // Create and add model
            addModel(new XFormsModel(this, modelEffectiveId, model));
        }
    }

    private void addModel(XFormsModel model) {
        this.models.add(model);
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
                Dispatch.dispatchEvent(XFormsEventFactory.createEvent(containingDocument, eventsToDispatch[i], model));
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
        final XFormsRepeatControl repeatControl; {
            final Object o = resolveObjectByIdInScope(sourceEffectiveId, repeatStaticId, null);
            if (o instanceof XFormsRepeatControl) {
                repeatControl = (XFormsRepeatControl) o;
            } else if (o instanceof XFormsRepeatIterationControl) {
                repeatControl = ((XFormsRepeatIterationControl) o).repeat();
            } else {
                repeatControl = null;
            }
        }

        if (repeatControl != null) {
            // 1. Found concrete control
            return repeatControl.getIndex();
        } else {

            final String sourcePrefixedId = XFormsUtils.getPrefixedId(sourceEffectiveId);
            final Scope scope = getPartAnalysis().scopeForPrefixedId(sourcePrefixedId);
            final String repeatPrefixedId = scope.prefixedIdForStaticId(repeatStaticId);

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
        final XBLContainer resolutionScopeContainer = findScopeRoot(sourcePrefixedId);
        return resolutionScopeContainer.resolveObjectById(sourceEffectiveId, targetStaticId, contextItem);
    }

    /**
     * Resolve an object in the scope of this container.
     *
     * @param sourceEffectiveId effective id of the source (control, model, instance, submission, ...) (can be null only for absolute ids)
     * @param targetStaticId    static id of the target
     * @param contextItem       context item, or null (used for bind resolution only)
     * @return                  object, or null if not found
     */
    public Object resolveObjectById(String sourceEffectiveId, String targetStaticId, Item contextItem) {

        // Handle "absolute ids" of format "/foo/bar.1-2"
        // NOTE: Experimental, definitive format TBD
        if (XFormsUtils.isAbsoluteId(targetStaticId))
            return containingDocument.getObjectByEffectiveId(XFormsUtils.absoluteIdToEffectiveId(targetStaticId));

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
        // NOTE: As of 2011-11, models don't use sourceEffectiveId
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
        if (! children.isEmpty()) {
            // We currently don't have a real notion of a "root" control, so we resolve against the first control if any
            final XFormsControl firstControl = children.get(0);
            sourceControlEffectiveId = firstControl.getEffectiveId();
        } else {
            // There are no controls, therefore resolution will not find anything
            sourceControlEffectiveId = null;
        }
        return sourceControlEffectiveId;
    }

    public XFormsControl getAssociatedControl() {
        return associatedControl;
    }

    protected List<XFormsControl> getChildrenControls(XFormsControls controls) {
        // We are a nested container so there must be an associated XFormsContainerControl
        return ((XFormsContainerControl) associatedControl).childrenJava();
    }

    private Object searchContainedModels(String sourceEffectiveId, String targetStaticId, Item contextItem) {
        for (final XFormsModel model: models) {
            final Object resultObject = model.resolveObjectById(sourceEffectiveId, targetStaticId, contextItem);
            if (resultObject != null)
                return resultObject;
        }
        return null;
    }

    private boolean isEffectiveIdResolvableByThisContainer(String effectiveId) {
        return this == findScopeRoot(XFormsUtils.getPrefixedId(effectiveId));
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

        // 1: Restore all instances
        for (final XFormsModel model: models)
            model.restoreInstances();

        // 2: Restore everything else
        // NOTE: It's important to do this as a separate step, because variables which might refer to other models' instances.
        for (final XFormsModel model: models)
            model.restoreState();
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

    protected void rebuildRecalculateRevalidateIfNeeded() {
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

    public void setDeferredFlagsForSetindex() {
        // XForms 1.1: "This action affects deferred updates by performing deferred update in its initialization and by
        // setting the deferred update flags for recalculate, revalidate and refresh."
        for (final XFormsModel model : models) {
            // NOTE: We used to do this, following XForms 1.0, but XForms 1.1 has changed the behavior
            //currentModel.getBinds().rebuild(pipelineContext);

            model.markValueChange(null, false);
        }
    }
}
