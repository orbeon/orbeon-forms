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

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.pipeline.api.PipelineContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.event.XFormsEventHandlerContainer;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.processor.XFormsServer;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.ValueRepresentation;

import java.util.*;

/**
 * Handle a stack of XPath evaluation context information. This can be used by controls, models, and actions.
 */
public class XFormsContextStack {

    private XFormsContainingDocument containingDocument;
    private Stack contextStack = new Stack();
    private XFormsFunction.Context functionContext;

    public XFormsContextStack(XFormsContainingDocument containingDocument) {
        this.containingDocument = containingDocument;
        this.functionContext = new XFormsFunction.Context(containingDocument, this);
    }

    public XFormsContextStack(XFormsModel containingModel) {
        this.containingDocument = containingModel.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(containingModel, this);
    }

    public XFormsFunction.Context getFunctionContext() {
        return functionContext;
    }

    /**
     * Reset the binding context to the root of the first model's first instance.
     */
    public void resetBindingContext(PipelineContext pipelineContext) {
        resetBindingContext(pipelineContext, containingDocument.getDefaultModel());
    }

    /**
     * Reset the binding context to the root of the given model's first instance.
     */
    public void resetBindingContext(PipelineContext pipelineContext, XFormsModel xformsModel) {

        // Clear existing stack
        contextStack.clear();

        if (xformsModel != null && xformsModel.getDefaultInstance() != null) {
            // Push the default context
            final NodeInfo defaultNode = xformsModel.getDefaultInstance().getInstanceRootElementInfo();
            final List defaultNodeset = Arrays.asList(new Object[]{ defaultNode });
            contextStack.push(new BindingContext(null, xformsModel, defaultNodeset, 1, null, true, null, xformsModel.getDefaultInstance().getLocationData(), false, defaultNode));
        } else {
            // Push empty context
            contextStack.push(new BindingContext(null, xformsModel, Collections.EMPTY_LIST, 0, null, true, null, xformsModel.getLocationData(), false, null));
        }

        // Add model variables for default model
        if (xformsModel != null)
            addVariables(pipelineContext, getCurrentBindingContext(), xformsModel);
    }

    private void addVariables(PipelineContext pipelineContext, BindingContext bindingContext, XFormsModel xformsModel) {
        int variablesCount = 0;
        // TODO: Check dirty flag to prevent needless re-evaluation
        for (Iterator i = xformsModel.getModelDocument().getRootElement().elements("variable").iterator(); i.hasNext(); variablesCount++) {
            final Element currentVariableElement = (Element) i.next();

            // All variables in the model are in scope for the nested binds and actions.
            // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation in the future.
            final Variable variable = new Variable(containingDocument, this, currentVariableElement);
            bindingContext.addVariable(variable.getVariableName(), variable.getVariableValue(pipelineContext, false));
        }

        if (variablesCount > 0 && XFormsServer.logger.isDebugEnabled())
            containingDocument.logDebug("model", "evaluated variables",
                    new String[] { "count", Integer.toString(variablesCount) });
    }

    /**
     * Set the binding context to the current control.
     *
     * @param xformsControl       control to bind
     */
    public void setBinding(XFormsControl xformsControl) {

        BindingContext controlBindingContext = xformsControl.getBindingContext();

        // Don't do the work if the current context is already as requested
        if (contextStack.size() > 0 && getCurrentBindingContext() == controlBindingContext)
            return;

        // Create ancestors-or-self list
        final List ancestorsOrSelf = new ArrayList();
        while (controlBindingContext != null) {
            ancestorsOrSelf.add(controlBindingContext);
            controlBindingContext = controlBindingContext.getParent();
        }
        Collections.reverse(ancestorsOrSelf);

        // Bind up to the specified control
        contextStack.clear();
        contextStack.addAll(ancestorsOrSelf);
    }

    /**
     * Set the binding for an event handler container. Support controls, models, and submissions.
     *
     * @param pipelineContext       current PipelineContext
     * @param eventHandlerContainer container object
     */
    public void setBinding(PipelineContext pipelineContext, XFormsEventHandlerContainer eventHandlerContainer) {

        if (eventHandlerContainer == null) {
            // Odd case which can happen when a handler and control are in a removed iteration. Set an empty context.
            // NOTE: Should ideally still try to figure out the context model, for example.
            resetBindingContext(pipelineContext);
            final XFormsModel xformsModel = containingDocument.getDefaultModel();
            contextStack.push(new BindingContext(null, xformsModel, Collections.EMPTY_LIST, 0, null, true, null, xformsModel.getLocationData(), false, null));
        } else if (eventHandlerContainer instanceof XFormsControl) {
            setBinding((XFormsControl) eventHandlerContainer);
        } else if (eventHandlerContainer instanceof XFormsModel) {
            final XFormsModel xformsModel = (XFormsModel) eventHandlerContainer;
            resetBindingContext(pipelineContext, xformsModel);
        } else if (eventHandlerContainer instanceof XFormsInstance) {
            final XFormsInstance xformsInstance = (XFormsInstance) eventHandlerContainer;
            resetBindingContext(pipelineContext, xformsInstance.getModel(containingDocument));
        } else if (eventHandlerContainer instanceof XFormsModelSubmission) {
            final XFormsModelSubmission submission = (XFormsModelSubmission) eventHandlerContainer;
            final XFormsModel xformsModel = (XFormsModel) submission.getParentContainer(containingDocument);
            resetBindingContext(pipelineContext, xformsModel);
            pushBinding(pipelineContext, submission.getSubmissionElement());
        } else {
            // Should not happen
            throw new OXFException("Invalid XFormsEventHandlerContainer type: " + eventHandlerContainer.getClass());
        }

        // TODO: Some code here which attempts to set iteration context for handlers within repeats. Check if needed.
            // If in the iteration, then it may be in no context if there is no iteration.
//            if (eventHandlerContainer instanceof XFormsRepeatControl) {
//                final XFormsRepeatControl repeatControl = (XFormsRepeatControl) eventHandlerContainer;
//                final List children = repeatControl.getChildren();
//
//                final Integer repeatIndexInteger = (Integer) xformsControls.getCurrentControlsState().getRepeatIdToIndex().get(eventHandlerContainerId);
//                if (repeatIndexInteger != null && children != null && children.size() > 0) {
//                    final int index = repeatIndexInteger.intValue();
//                    final int childrenSize = children.size();
//                    if (index > 0 && index < childrenSize) {
//                        final RepeatIterationControl repeatIteration = (RepeatIterationControl) children.get(index);
//                        xformsControls.setBinding(pipelineContext, repeatIteration);
//                    } else {
//                        xformsControls.setBinding(pipelineContext, (XFormsControl) eventHandlerContainer);
//                    }
//                } else {
//                    xformsControls.setBinding(pipelineContext, (XFormsControl) eventHandlerContainer);
//                }
//            } else {
//                contextStack.setBinding((XFormsControl) eventHandlerContainer);
//            }
    }

    public void restoreBinding(BindingContext bindingContext) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        if (currentBindingContext != bindingContext.getParent())
            throw new ValidationException("Inconsistent binding context parent.", currentBindingContext.getLocationData());

        contextStack.push(bindingContext);
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement) {
        pushBinding(pipelineContext, bindingElement, null);
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     *
     * @param pipelineContext   current PipelineContext
     * @param bindingElement    current element containing node binding attributes
     * @param model             if specified, overrides a potential @model attribute on the element
     */
    public void pushBinding(PipelineContext pipelineContext, Element bindingElement, String model) {
        final String ref = bindingElement.attributeValue("ref");
        final String context = bindingElement.attributeValue("context");
        final String nodeset = bindingElement.attributeValue("nodeset");
        if (model == null)
            model = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("model"));
        final String bind = XFormsUtils.namespaceId(containingDocument, bindingElement.attributeValue("bind"));

        final Map bindingElementNamespaceContext = containingDocument.getNamespaceMappings(bindingElement);
        pushBinding(pipelineContext, ref, context, nodeset, model, bind, bindingElement, bindingElementNamespaceContext);
    }

    public void pushBinding(PipelineContext pipelineContext, String ref, String context, String nodeset, String modelId, String bindId,
                            Element bindingElement, Map bindingElementNamespaceContext) {

        // Get location data for error reporting
        final LocationData locationData = (bindingElement == null)
                ? containingDocument.getLocationData()
                : new ExtendedLocationData((LocationData) bindingElement.getData(), "pushing XForms control binding", bindingElement);

        // Determine current context
        final BindingContext currentBindingContext = getCurrentBindingContext();

        // Handle model
        final XFormsModel newModel;
        final boolean isNewModel;
        if (modelId != null) {
            newModel = containingDocument.getModel(modelId);
            if (newModel == null)
                throw new ValidationException("Invalid model id: " + modelId, locationData);
            isNewModel = newModel != currentBindingContext.getModel();// don't say it's a new model unless it has really changed
        } else {
            newModel = currentBindingContext.getModel();
            isNewModel = false;
        }

        // Handle nodeset
        final boolean isNewBind;
        final int newPosition;
        final List newNodeset;
        final boolean hasOverriddenContext;
        final Item contextItem;
        final boolean isPushModelVariables;
        final List variableInfo;
        {
            if (bindId != null) {
                // Resolve the bind id to a nodeset
                newNodeset = newModel.getBinds().getBindNodeset(bindId, currentBindingContext.getSingleItem());
                hasOverriddenContext = false;
                contextItem = currentBindingContext.getSingleItem();
                isNewBind = true;
                newPosition = 1;
                isPushModelVariables = false;
                variableInfo = null;
            } else if (ref != null || nodeset != null) {

                // Check whether there is an optional context (XForms 1.1, likely generalized in XForms 1.2)
                if (context != null) {
                    // Push model and context
                    pushTemporaryContext(currentBindingContext.getSingleItem());// provide context information for the context() function
                    pushBinding(pipelineContext, null, null, context, modelId, null, null, bindingElementNamespaceContext);
                    hasOverriddenContext = true;
                    contextItem = getCurrentSingleNode();
                    variableInfo = getCurrentBindingContext().getVariableInfo();
                } else if (isNewModel) {
                    // Push model only
                    pushBinding(pipelineContext, null, null, null, modelId, null, null, bindingElementNamespaceContext);
                    hasOverriddenContext = false;
                    contextItem = currentBindingContext.getSingleItem();
                    variableInfo = getCurrentBindingContext().getVariableInfo();
                } else {
                    hasOverriddenContext = false;
                    contextItem = currentBindingContext.getSingleItem();
                    variableInfo = null;
                }

                // Evaluate new XPath in context
                final BindingContext contextBindingContext = getCurrentBindingContext();

                if (contextBindingContext != null && contextBindingContext.getNodeset().size() > 0) {
                    pushTemporaryContext(contextBindingContext.getSingleItem());// provide context information for the context() function
                    newNodeset = XPathCache.evaluateKeepItems(pipelineContext, contextBindingContext.getNodeset(), contextBindingContext.getPosition(),
                            ref != null ? ref : nodeset, bindingElementNamespaceContext, contextBindingContext.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                            functionContext, null, locationData);
                    popBinding();
                } else {
                    newNodeset = Collections.EMPTY_LIST;
                }

                // Restore optional context
                if (context != null || isNewModel) {
                    popBinding();
                    if (context != null)
                        popBinding();
                }
                isNewBind = true;
                newPosition = 1;
                isPushModelVariables = false;
            } else if (isNewModel && context == null) {
                // Only the model has changed

                final BindingContext modelBindingContext = getCurrentBindingContextForModel(newModel.getEffectiveId());
                if (modelBindingContext != null) {
                    newNodeset = modelBindingContext.getNodeset();
                    newPosition = modelBindingContext.getPosition();
                    isPushModelVariables = false;
                } else {
                    newNodeset = getCurrentNodeset(newModel.getEffectiveId());
                    newPosition = 1;
                    // Variables for this model are not yet on the stack
                    isPushModelVariables = true;
                }

                hasOverriddenContext = false;
                contextItem = currentBindingContext.getSingleItem();
                isNewBind = false;
                variableInfo = null;

            } else if (context != null) {
                // Only the context has changed, and possibly the model
                pushBinding(pipelineContext, null, null, context, modelId, null, null, bindingElementNamespaceContext);
                {
                    newNodeset = getCurrentNodeset();
                    newPosition = getCurrentPosition();
                    isNewBind = false;
                    hasOverriddenContext = true;
                    contextItem = getCurrentSingleNode();
                    isPushModelVariables = false;
                    variableInfo = null;
                }
                popBinding();

            } else {
                // No change to anything
                isNewBind = false;
                newNodeset = currentBindingContext.getNodeset();
                newPosition = currentBindingContext.getPosition();
                isPushModelVariables = false;
                variableInfo = null;

                // We set a new context item as the context into which other attributes must be evaluated. E.g.:
                //
                // <xforms:select1 ref="type">
                //   <xforms:action ev:event="xforms-value-changed" if="context() = 'foobar'">
                //
                // In this case, you expect context() to be updated as follows.
                //
                hasOverriddenContext = false;
                contextItem = currentBindingContext.getSingleItem();
            }
        }

        // Push new context
        final String id = (bindingElement == null) ? null : bindingElement.attributeValue("id");
        contextStack.push(new BindingContext(currentBindingContext, newModel, newNodeset, newPosition, id, isNewBind, bindingElement, locationData, hasOverriddenContext, contextItem));

        // Add new model variables if needed
        if (variableInfo != null) {
            // In this case, we did a temporary context push with the new model, and we already gathered the new
            // variables in scope. We have to set them to the newly pushed BindingContext.
            getCurrentBindingContext().setVariableInfo(variableInfo);
        } else if (isPushModelVariables) {
            // In this case, the model just changed and so we gathered the variables in scope for this model.
            addVariables(pipelineContext, getCurrentBindingContext(), newModel);
        }
    }

    private void pushTemporaryContext(Item contextItem) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        contextStack.push(new BindingContext(currentBindingContext, currentBindingContext.getModel(),
                currentBindingContext.getNodeset(), currentBindingContext.getPosition(), currentBindingContext.getElementId(),
                false, currentBindingContext.getControlElement(), currentBindingContext.getLocationData(),
                false, contextItem));
    }

    /**
     * Push an iteration of the current node-set. Used for example by xforms:repeat, xforms:bind, xxforms:iterate.
     *
     * @param currentPosition   1-based iteration index
     */
    public void pushIteration(int currentPosition) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        final List currentNodeset = currentBindingContext.getNodeset();

        // Set a new context item, although the context() function is never called on the iteration itself
        final Item newContextItem;
        if (currentNodeset == null || currentNodeset.size() == 0)
            newContextItem = null;
        else
            newContextItem = (Item) currentNodeset.get(currentPosition - 1);

        contextStack.push(new BindingContext(currentBindingContext, currentBindingContext.getModel(),
                currentNodeset, currentPosition, currentBindingContext.getElementId(), true, null, currentBindingContext.getLocationData(),
                false, newContextItem));
    }

    /**
     * Push a new variable in scope, providing its name and value.
     *
     * @param name      variable name
     * @param value     variable value
     */
    public void pushVariable(Element variableElement, String name, ValueRepresentation value) {
        final LocationData locationData = new ExtendedLocationData((LocationData) variableElement.getData(), "pushing variable binding", variableElement);
        final BindingContext currentBindingContext = getCurrentBindingContext();
        contextStack.push(new BindingContext(currentBindingContext, variableElement, locationData, name, value));
    }

    public BindingContext getCurrentBindingContext() {
        return (BindingContext) contextStack.peek();
    }

    public BindingContext popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear context stack.");
        return (BindingContext) contextStack.pop();
    }

    /**
     * Get the current node-set binding for the given model id.
     */
    public BindingContext getCurrentBindingContextForModel(String modelId) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final String currentModelId = currentBindingContext.getModel().getEffectiveId();
            if ((currentModelId == null && modelId == null) || (modelId != null && modelId.equals(currentModelId)))
                return currentBindingContext;
        }

        return null;
    }

    /**
     * Get the current node-set binding for the given model id.
     */
    public List getCurrentNodeset(String modelId) {

        final BindingContext bindingContext = getCurrentBindingContextForModel(modelId);

        // If a context exists, return its node-set
        if (bindingContext != null)
            return bindingContext.getNodeset();

        // If there is no default instance, return an empty node-set
        final XFormsInstance defaultInstance = containingDocument.getModel(modelId).getDefaultInstance();
        if (defaultInstance == null)
            return Collections.EMPTY_LIST;

        // If not found, return the document element of the model's default instance
        try {
            return Collections.singletonList(defaultInstance.getInstanceRootElementInfo());
        } catch (Exception e) {
            defaultInstance.getInstanceRootElementInfo();
            throw new OXFException(e);
        }
    }

    /**
     * Get the current single node binding for the given model id.
     */
    public NodeInfo getCurrentSingleNode(String modelId) {

        final BindingContext bindingContext = getCurrentBindingContextForModel(modelId);

        // If a context exists, use it
        if (bindingContext != null)
            return bindingContext.getSingleNode();

        // If there is no default instance, return null
        final XFormsInstance defaultInstance = containingDocument.getModel(modelId).getDefaultInstance();
        if (defaultInstance == null)
            return null;

        // Otherwise return the document element of the model's default instance
        return defaultInstance.getInstanceRootElementInfo();
    }

    /**
     * Get the current single node binding, if any.
     */
    public NodeInfo getCurrentSingleNode() {
        return getCurrentBindingContext().getSingleNode();
    }

    /**
     * Get the current single node binding, if any.
     */
    public Item getCurrentSingleItem() {
        return getCurrentBindingContext().getSingleItem();
    }

    /**
     * Get the context item, whether in-scope or overridden.
     */
    public Item getContextItem() {
        return getCurrentBindingContext().getContextItem();
    }

    /**
     * Return whether there is an overridden context, whether empty or not.
     */
    public boolean hasOverriddenContext() {
        return getCurrentBindingContext().hasOverriddenContext();
    }

    public String getCurrentSingleNodeValue() {
        final NodeInfo currentSingleNode = getCurrentSingleNode();
        if (currentSingleNode != null)
            return XFormsInstance.getValueForNodeInfo(currentSingleNode);
        else
            return null;
    }

    /**
     * Get the current nodeset binding, if any.
     */
    public List getCurrentNodeset() {
        return getCurrentBindingContext().getNodeset();
    }

    /**
     * Get the current position in current nodeset binding.
     */
    public int getCurrentPosition() {
        return getCurrentBindingContext().getPosition();
    }

    /**
     * Return a Map of the current variables in scope.
     *
     * @return  Map<String, List> of variable name to value
     */
    public Map getCurrentVariables() {
        return getCurrentBindingContext().getInScopeVariables();
    }

    /**
     * Return the single node associated with the iteration of the repeat specified. If a null
     * repeat id is passed, return the single node associated with the closest enclosing repeat
     * iteration.
     *
     * NOTE: Use getContextForId() instead.
     *
     * @param repeatId  enclosing repeat id, or null
     * @return          the single node
     */
    public NodeInfo getRepeatCurrentSingleNode(String repeatId) {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);
            if (isRepeatIterationBindingContext(currentBindingContext) && (repeatId == null || currentBindingContext.getParent().getElementId().equals(repeatId))) {
                // Found binding context for relevant repeat iteration
                return currentBindingContext.getSingleNode();
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        if (repeatId == null)
            throw new ValidationException("No enclosing xforms:repeat found.", getCurrentBindingContext().getLocationData());
        else
            throw new ValidationException("No enclosing xforms:repeat found for repeat id: " + repeatId, getCurrentBindingContext().getLocationData());
    }

    private boolean isRepeatIterationBindingContext(BindingContext bindingContext) {
        // First, we need a parent
        final BindingContext parent = bindingContext.getParent();
        if (parent == null)
            return false;
        // We don't have a bound element, but the parent is bound to xforms:repeat
        final Element bindingElement = bindingContext.getControlElement();
        final Element parentBindingElement = parent.getControlElement();
        return (bindingElement == null) && (parentBindingElement != null) && parentBindingElement.getName().equals("repeat");
    }

    /**
     * Return the closest enclosing repeat id.
     *
     * @return  repeat id, throw if not found
     */
    public String getEnclosingRepeatId() {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            if (isRepeatIterationBindingContext(currentBindingContext)) {
                // Found binding context for relevant repeat iteration
                return currentBindingContext.getParent().getElementId();
            }
        }
        // It is required that there is a relevant enclosing xforms:repeat
        throw new ValidationException("Enclosing xforms:repeat not found.", getCurrentBindingContext().getLocationData());
    }

    /**
     * Obtain the single-node binding for an enclosing xforms:group, xforms:repeat, or xforms:switch. It takes one
     * mandatory string parameter containing the id of an enclosing grouping XForms control. For xforms:repeat, the
     * context returned is the context of the current iteration.
     *
     * @param contextId  enclosing context id
     * @return           the item
     */
    public Item getContextForId(String contextId) {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final Element bindingElement = currentBindingContext.getControlElement();
            if (bindingElement != null && XFormsControls.groupingControls.get(bindingElement.getName()) != null) {
                // We are a grouping control
                final String elementId = currentBindingContext.getElementId();
                if (contextId.equals(elementId)) {
                    // Found matching binding context for regular grouping control
                    return currentBindingContext.getSingleItem();
                }
            } else if (bindingElement == null) {
                // We a likely repeat iteration
                if (isRepeatIterationBindingContext(currentBindingContext) && currentBindingContext.getParent().getElementId().equals(contextId)) {
                    // Found matching repeat iteration
                    return currentBindingContext.getSingleItem();
                }
            }
        }
        throw new ValidationException("No enclosing container XForms control found for id: " + contextId, getCurrentBindingContext().getLocationData());
    }

    /**
     * Get the current node-set for the given repeat id.
     *
     * @param repeatId  existing repeat id
     * @return          node-set
     */
    public List getRepeatNodeset(String repeatId) {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);

            final Element bindingElement = currentBindingContext.getControlElement();
            final String elementId = currentBindingContext.getElementId();
            if (repeatId.equals(elementId) && bindingElement != null && bindingElement.getName().equals("repeat")) {
                // Found repeat, return associated node-set
                return currentBindingContext.getNodeset();
            }
        }
        throw new ValidationException("No enclosing xforms:repeat found for id: " + repeatId, getCurrentBindingContext().getLocationData());
    }

    /**
     * Return the currrent model for the current nodeset binding.
     */
    public XFormsModel getCurrentModel() {
        return getCurrentBindingContext().getModel();
    }

    /**
     * Return the current instance for the current nodeset binding.
     *
     * This method goes up the context stack until it finds a node, and returns the instance associated with that node.
     */
    public XFormsInstance getCurrentInstance() {
        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);
            final NodeInfo currentSingleNode = currentBindingContext.getSingleNode();

            if (currentSingleNode != null)
                return containingDocument.getInstanceForNode(currentSingleNode);
        }
        return null;
    }

    /**
     * Do not use. Only called by legacy XForms engine.
     */
    public Stack legacyGetStack() {
        return contextStack;
    }

    public static class BindingContext {
        private BindingContext parent;
        private XFormsModel model;
        private List nodeset;
        private int position = 1;
        private String elementId;
        private boolean newBind;
        private Element controlElement;
        private LocationData locationData;
        private boolean hasOverriddenContext;
        private Item contextItem;

        private List variableInfo;

        private Map variablesMap; // cached varaiable map

        public BindingContext(BindingContext parent, XFormsModel model, List nodeSet, int position, String elementId, boolean newBind, Element controlElement, LocationData locationData, boolean hasOverriddenContext, Item contextItem) {
            this.parent = parent;
            this.model = model;
            this.nodeset = nodeSet;
            this.position = position;
            this.elementId = elementId;
            this.newBind = newBind;
            this.controlElement = controlElement;
            this.locationData = (locationData != null) ? locationData : (controlElement != null) ? (LocationData) controlElement.getData() : null;

            this.hasOverriddenContext = hasOverriddenContext;
            this.contextItem = contextItem;

//            if (nodeset != null && nodeset.size() > 0) {
//                // TODO: PERF: This seems to take some significant time
//                for (Iterator i = nodeset.iterator(); i.hasNext();) {
//                    final Object currentItem = i.next();
//                    if (!(currentItem instanceof NodeInfo))
//                        throw new ValidationException("A reference to a node (such as element, attribute, or text) is required in a binding. Attempted to bind to the invalid item type: " + currentItem.getClass(), this.locationData);
//                }
//            }
        }

        public BindingContext(BindingContext parent, Element controlElement, LocationData locationData, String variableName, ValueRepresentation variableValue) {
            this(parent, parent.getModel(), parent.getNodeset(), parent.getPosition(), parent.getElementId(), false, controlElement, locationData, false, parent.getContextItem());
            variableInfo = new ArrayList();
            variableInfo.add(new VariableInfo(variableName, variableValue));
        }

        public BindingContext getParent() {
            return parent;
        }

        public XFormsModel getModel() {
            return model;
        }

        public List getNodeset() {
            return nodeset;
        }

        public int getPosition() {
            return position;
        }

        public String getElementId() {
            return elementId;
        }

        public boolean isNewBind() {
            return newBind;
        }

        public Element getControlElement() {
            return controlElement;
        }

        public Item getContextItem() {
            return contextItem;
        }

        public boolean hasOverriddenContext() {
            return hasOverriddenContext;
        }

        /**
         * Convenience method returning the location data associated with the XForms element (typically, a control)
         * associated with the binding. If location data was passed during construction, pass that, otherwise try to
         * get location data from passed element.
         *
         * @return  LocationData object, or null if not found
         */
        public LocationData getLocationData() {
            return locationData;
        }

        /**
         * Get the current single node binding, if any.
         */
        public NodeInfo getSingleNode() {
            if (nodeset == null || nodeset.size() == 0)
                return null;

            return (NodeInfo) nodeset.get(position - 1);
        }

        public Item getSingleItem() {
            if (nodeset == null || nodeset.size() == 0)
                return null;

            return (Item) nodeset.get(position - 1);
        }

        public void addVariable(String variableName, ValueRepresentation variableValue) {
            if (variableInfo == null)
                variableInfo = new ArrayList();
            variableInfo.add(new VariableInfo(variableName, variableValue));
        }

        public void setVariableInfo(List variableInfo) {
            this.variableInfo = variableInfo;
        }

        public List getVariableInfo() {
            return variableInfo;
        }

        /**
         * Return a Map of the variables in scope.
         *
         * @return  Map<String, List> of variable name to value
         */
        public Map getInScopeVariables() {
            return getInScopeVariables(true);
        }

        public Map getInScopeVariables(boolean useCache) {
            // TODO: Variables in scope in the view must not include the variables defined in another model, but must include all view variables.
            if (variablesMap == null || !useCache) {
                final Map tempVariablesMap = new HashMap();

                BindingContext currentBindingContext = this;
                do {
                    final List currentInfo = currentBindingContext.variableInfo;
                    if (currentInfo != null) {
                        for (Iterator i = currentInfo.iterator(); i.hasNext();) {
                            final VariableInfo variableInfo = (VariableInfo) i.next();
                            final String currentName = variableInfo.variableName;
                            if (currentName != null && tempVariablesMap.get(currentName) == null) {
                                // The binding defines a variable and there is not already a variable with that name
                                tempVariablesMap.put(variableInfo.variableName, variableInfo.variableValue);
                            }
                        }
                    }

                    currentBindingContext = currentBindingContext.getParent();
                } while (currentBindingContext != null);

                if (!useCache)
                    return tempVariablesMap;

                variablesMap = tempVariablesMap;
            }
            return variablesMap;
        }

        private static class VariableInfo {
            private String variableName;
            private ValueRepresentation variableValue;

            private VariableInfo(String variableName, ValueRepresentation variableValue) {
                this.variableName = variableName;
                this.variableValue = variableValue;
            }
        }
    }
}
