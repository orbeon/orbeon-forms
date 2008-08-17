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

import org.dom4j.Document;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.xforms.event.*;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.NodeInfo;

import java.util.*;

/**
 * Represent a container of models and controls.
 *
 * This is used at the top-level (XFormsContainingDocument) and by component instances (XFormsComponentControl).
 *
 * For now there is no nested component tree. There is a single components tree in XFormsControls.
 *
 * We may use this for nested repeat iterations as well.
 */
public class XFormsContainer implements XFormsEventTarget, XFormsEventHandlerContainer {

    // Static id of the control using this container, e.g. my-foo-bar
    private final String staticId;
    // Effective id of the control using this container, e.g. my-foo-bar.2
    private final String effectiveId;
    // "" for the root container, "my-foo-bar$", etc.
    private final String prefix;

    private LocationData locationData;
    private final XFormsContainer parentContainer;

    private XFormsContextStack.BindingContext bindingContext; 

    private XFormsContainingDocument containingDocument;
    private final XFormsContextStack contextStack;

    private List models = new ArrayList();
    private Map modelsMap = new HashMap();

    public XFormsContainer(String staticId, String effectiveId, String prefix, LocationData locationData, XFormsContainer parentContainer) {
        this.staticId = staticId;
        this.effectiveId = effectiveId;
        this.prefix = prefix;
        this.locationData = locationData;
        this.parentContainer = parentContainer;

        // Get container binding context
        if (parentContainer != null) {
            bindingContext = parentContainer.getContextStack().getCurrentBindingContext();
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

    public String getPrefix() {
        return prefix;
    }

    public XFormsContainer getParentContainer() {
        return parentContainer;
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

    /**
     * Create and index models corresponding to this container's scope.
     */
    public void addAllModels() {
        for (Iterator i = containingDocument.getStaticState().getModelDocuments().entrySet().iterator(); i.hasNext();) {
            final Map.Entry currentEntry = (Map.Entry) i.next();
            final String prefixedId = (String) currentEntry.getKey();
            final Document modelDocument = (Document) currentEntry.getValue();

            final String currentPrefix = XFormsUtils.getEffectiveIdPrefix(prefixedId);
            if (prefix.equals(currentPrefix)) {
                final XFormsModel model = new XFormsModel(prefixedId, modelDocument);
                model.setContainer(this); // NOTE: This requires the XFormsControls to be set on XFormsContainingDocument (really? should explain why)

                // Add model
                addModel(model);
            }
        }
    }

    protected void initializeModels(PipelineContext pipelineContext) {

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
            for (Iterator j = getModels().iterator(); j.hasNext();) {
                final XFormsModel currentModel = (XFormsModel) j.next();

                // Make sure there is at least one refresh
                final XFormsModel.DeferredActionContext deferredActionContext = currentModel.getDeferredActionContext();
                if (deferredActionContext != null) {
                    deferredActionContext.refresh = true;
                }

                containingDocument.dispatchEvent(pipelineContext, XFormsEventFactory.createEvent(eventsToDispatch[i], currentModel));
            }
        }
    }

    protected void initializeNestedControls(PipelineContext pipelineContext) {
        // NOP by default
    }

    /**
     * Return model with the specified id, null if not found. If the id is the empty string, return
     * the default model, i.e. the first model.
     */
    public XFormsModel getModelByEffectiveId(String modelEffectiveId) {
        return (XFormsModel) ("".equals(modelEffectiveId) ? getDefaultModel() : modelsMap.get(modelEffectiveId));
    }

    protected void addModel(XFormsModel model) {
        this.models.add(model);
        if (model.getEffectiveId() != null)
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
            XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.getObjectByEffectiveId(effectiveId);
            if (resultObject != null)
                return resultObject;
        }

        // Check containing document
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

        // Search in models
        for (Iterator i = models.iterator(); i.hasNext();) {
            XFormsModel model = (XFormsModel) i.next();
            final Object resultObject = model.resolveObjectById(effectiveSourceId, targetId);
            if (resultObject != null)
                return resultObject;
        }

        // Check containing document
        if (targetId.equals(getEffectiveId()))
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

    public String getId() {
        return staticId;
    }

    public String getEffectiveId() {
        return effectiveId;
    }

    public LocationData getLocationData() {
        return locationData;
    }

    protected void setLocationData(LocationData locationData) {
        this.locationData = locationData;
    }

    public XFormsEventHandlerContainer getParentEventHandlerContainer(XFormsContainingDocument containingDocument) {
        return parentContainer;
    }

    public void performDefaultAction(PipelineContext pipelineContext, XFormsEvent event) {
        // NOP
    }

    public List getEventHandlers(XFormsContainingDocument containingDocument) {
        return null;
    }
}
