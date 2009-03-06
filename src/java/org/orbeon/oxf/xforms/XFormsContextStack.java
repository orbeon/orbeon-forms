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
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
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

    private XFormsContainer container;
    private XFormsContainingDocument containingDocument;
    private XFormsFunction.Context functionContext;
    private BindingContext parentBindingContext;

    private Stack contextStack = new Stack();

    public XFormsContextStack(XFormsContainer container) {
        this.container = container;
        this.containingDocument = this.container.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(container, this);
    }

    // Constructor for binds and submissions
    public XFormsContextStack(XFormsModel containingModel) {
        this.container = containingModel.getContainer();
        this.containingDocument = this.container.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(containingModel, this);
    }

    public void setParentBindingContext(BindingContext parentBindingContext) {
        this.parentBindingContext = parentBindingContext;
    }

    public XFormsFunction.Context getFunctionContext() {
        return functionContext;
    }

    /**
     * Reset the binding context to the root of the first model's first instance, or to the parent binding context.
     */
    public void resetBindingContext(PipelineContext pipelineContext) {
        if (parentBindingContext == null) {
            // Reset to default model
            resetBindingContext(pipelineContext, container.getDefaultModel());
        } else {
            // Clear existing stack
            contextStack.clear();
            // Set initial context to parent
            contextStack.push(parentBindingContext);
        }
    }

    /**
     * Reset the binding context to the root of the given model's first instance.
     */
    public void resetBindingContext(PipelineContext pipelineContext, XFormsModel xformsModel) {

        // Clear existing stack
        contextStack.clear();

        if (xformsModel != null && xformsModel.getDefaultInstance() != null) {
            // Push the default context if there is a model with an instance
            final NodeInfo defaultNode = xformsModel.getDefaultInstance().getInstanceRootElementInfo();
            final List defaultNodeset = Arrays.asList(new Object[]{ defaultNode });
            contextStack.push(new BindingContext(null, xformsModel, defaultNodeset, 1, null, true, null, xformsModel.getDefaultInstance().getLocationData(), false, defaultNode));
        } else {
            // Push parent context
            final XFormsContextStack.BindingContext containerBindingContext = container.getBindingContext();
            if (containerBindingContext != null) {
                // This is the case where a component doesn't have local models; we decided to inherit the context of the parent
                contextStack.push(containerBindingContext);
            } else {
                // Push empty context
                contextStack.push(new BindingContext(null, xformsModel, Collections.EMPTY_LIST, 0, null, true, null, (xformsModel != null) ? xformsModel.getLocationData() : null, false, null));
            }
        }

        // Add model variables for default model
        if (xformsModel != null) {
            addModelVariables(pipelineContext, xformsModel);
        }
    }

    private void addModelVariables(PipelineContext pipelineContext, XFormsModel xformsModel) {
        // TODO: Check dirty flag to prevent needless re-evaluation

        List /* <VariableInfo> */ variableInfos = null;
        for (Iterator i = xformsModel.getModelDocument().getRootElement().elements("variable").iterator(); i.hasNext(); ) {
            final Element currentVariableElement = (Element) i.next();

            // All variables in the model are in scope for the nested binds and actions.
            // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation in the future.
            final Variable variable = new Variable(containingDocument, this, currentVariableElement);

            // NOTE: We used to simply add variables to the current bindingContext, but this could cause issues
            // because getVariableValue() can itself use variables declared previously. This would work at first, but
            // because BindingContext caches variables in scope, after a first request for in-scope variables, further
            // variables values could not be added. The method below temporarily adds more elements on the stack but it
            // is safer.
            pushVariable(currentVariableElement, variable.getVariableName(), variable.getVariableValue(pipelineContext, true));

            // Add VariableInfo created during above pushVariable(). There must be only one!
            if (variableInfos == null)
                variableInfos = new ArrayList();

            variableInfos.addAll(getCurrentBindingContext().getVariables());
        }

        if (variableInfos != null) {
            // Some variables added

            final int variableCount = variableInfos.size();

            if (XFormsServer.logger.isDebugEnabled()) {
                containingDocument.logDebug("model", "evaluated variables",
                        new String[] { "count", Integer.toString(variableCount) });
            }

            // Remove extra bindings added
            for (int i = 0; i < variableCount; i++) {
                popBinding();
            }

            getCurrentBindingContext().setVariables(variableInfos);
        }
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
     * @param eventObserver container object
     */
    public void setBinding(PipelineContext pipelineContext, XFormsEventObserver eventObserver) {

        if (eventObserver == null) {
            // Odd case which can happen when an action handler and control are in a removed iteration. Set an empty context.
            // OR, case where an action handler is within another action handler!
            // NOTE: Should ideally still try to figure out the context model, for example.
            resetBindingContext(pipelineContext);
            final XFormsModel xformsModel = container.getDefaultModel();
            contextStack.push(new BindingContext(null, xformsModel, Collections.EMPTY_LIST, 0, null, true, null, (xformsModel != null) ? xformsModel.getLocationData() : null, false, null));
        } else if (eventObserver instanceof XFormsControl) {
            setBinding((XFormsControl) eventObserver);
        } else if (eventObserver instanceof XFormsModel) {
            final XFormsModel xformsModel = (XFormsModel) eventObserver;
            resetBindingContext(pipelineContext, xformsModel);
        } else if (eventObserver instanceof XFormsInstance) {
            final XFormsInstance xformsInstance = (XFormsInstance) eventObserver;
            resetBindingContext(pipelineContext, xformsInstance.getModel(containingDocument));
        } else if (eventObserver instanceof XFormsModelSubmission) {
            final XFormsModelSubmission submission = (XFormsModelSubmission) eventObserver;
            final XFormsModel xformsModel = (XFormsModel) submission.getModel();
            resetBindingContext(pipelineContext, xformsModel);
            pushBinding(pipelineContext, submission.getSubmissionElement());
        } else {
            // Should not happen
            throw new OXFException("Invalid XFormsEventObserver type: " + eventObserver.getClass());
        }

        // TODO: Some code here which attempts to set iteration context for handlers within repeats. Check if needed.
            // If in the iteration, then it may be in no context if there is no iteration.
//            if (eventObserver instanceof XFormsRepeatControl) {
//                final XFormsRepeatControl repeatControl = (XFormsRepeatControl) eventObserver;
//                final List children = repeatControl.getChildren();
//
//                final Integer repeatIndexInteger = (Integer) xformsControls.getCurrentControlsState().getRepeatIdToIndex().get(eventObserverId);
//                if (repeatIndexInteger != null && children != null && children.size() > 0) {
//                    final int index = repeatIndexInteger.intValue();
//                    final int childrenSize = children.size();
//                    if (index > 0 && index < childrenSize) {
//                        final RepeatIterationControl repeatIteration = (RepeatIterationControl) children.get(index);
//                        xformsControls.setBinding(pipelineContext, repeatIteration);
//                    } else {
//                        xformsControls.setBinding(pipelineContext, (XFormsControl) eventObserver);
//                    }
//                } else {
//                    xformsControls.setBinding(pipelineContext, (XFormsControl) eventObserver);
//                }
//            } else {
//                contextStack.setBinding((XFormsControl) eventObserver);
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
    private void pushBinding(PipelineContext pipelineContext, Element bindingElement, String model) {
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
                ? container.getLocationData()
                : new ExtendedLocationData((LocationData) bindingElement.getData(), "pushing XForms control binding", bindingElement);

        // Determine current context
        final BindingContext currentBindingContext = getCurrentBindingContext();

//        try {

            // Handle model
            final XFormsModel newModel;
            final boolean isNewModel;
            if (modelId != null) {
                newModel = (XFormsModel) container.findModelByStaticId(modelId);
                // TODO: dispatch xforms-binding-exception
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
                    // TODO: dispatch xforms-binding-exception if no bind is found
                    final XFormsModelBinds binds = newModel.getBinds();
                    newNodeset = (binds != null) ?  binds.getBindNodeset(bindId, currentBindingContext.getSingleItem()) : Collections.EMPTY_LIST;
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
                        variableInfo = getCurrentBindingContext().getVariables();
                    } else if (isNewModel) {
                        // Push model only
                        pushBinding(pipelineContext, null, null, null, modelId, null, null, bindingElementNamespaceContext);
                        hasOverriddenContext = false;
                        contextItem = currentBindingContext.getSingleItem();
                        variableInfo = getCurrentBindingContext().getVariables();
                    } else {
                        hasOverriddenContext = false;
                        contextItem = currentBindingContext.getSingleItem();
                        variableInfo = null;
                    }

                    final BindingContext contextBindingContext = getCurrentBindingContext();
                    if (contextBindingContext != null && contextBindingContext.getNodeset().size() > 0) {
                        // Evaluate new XPath in context if the current contex is not empty

                        pushTemporaryContext(contextBindingContext.getSingleItem());// provide context information for the context() function
                        newNodeset = XPathCache.evaluateKeepItems(pipelineContext, contextBindingContext.getNodeset(), contextBindingContext.getPosition(),
                                ref != null ? ref : nodeset, bindingElementNamespaceContext, contextBindingContext.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                functionContext, null, locationData);
                        popBinding();
                    } else {
                        // Otherwise we consider we can't evaluate
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

                    final BindingContext modelBindingContext = getCurrentBindingContextForModel(newModel);
                    if (modelBindingContext != null) {
                        newNodeset = modelBindingContext.getNodeset();
                        newPosition = modelBindingContext.getPosition();
                        isPushModelVariables = false;
                    } else {
                        newNodeset = getCurrentNodeset(newModel);
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

//        DEBUG
//            {
//                if (newNodeset != null && newNodeset.size() > 0) {
//                    final Item item = (Item) newNodeset.get(0);
//
//                    if (item instanceof NodeInfo && containingDocument.getInstanceForNode((NodeInfo) item) == null) {
//                        System.out.println("Dangling node: " + ((NodeInfo) item).getDisplayName());
//                    }
//                }
//            }

            contextStack.push(new BindingContext(currentBindingContext, newModel, newNodeset, newPosition, id, isNewBind, bindingElement, locationData, hasOverriddenContext, contextItem));

            // Add new model variables if needed
            if (variableInfo != null) {
                // In this case, we did a temporary context push with the new model, and we already gathered the new
                // variables in scope. We have to set them to the newly pushed BindingContext.
                getCurrentBindingContext().setVariables(variableInfo);
            } else if (isPushModelVariables) {
                // In this case, the model just changed and so we gather the variables in scope for the new model
                addModelVariables(pipelineContext, newModel);
            }
//        } catch (Throwable e) {
            // TODO: handle dispatch of xforms-binding-exception
//            final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(locationData, "evaluating binding expression",
//                        bindingElement, new String[] { "xxx", xxxx }));
//
//            currentBindingContext.getModel().getContainer().dispatchEvent(pipelineContext, new XFormsBindingExceptionEvent(model, ve.getMessage(), ve));
//        }
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
     * Get the current node-set binding for the given model.
     */
    public BindingContext getCurrentBindingContextForModel(XFormsModel model) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = (BindingContext) contextStack.get(i);
            if (model == currentBindingContext.getModel())
                return currentBindingContext;
        }

        return null;
    }

    /**
     * Get the current node-set binding for the given model.
     */
    public List getCurrentNodeset(XFormsModel model) {

        final BindingContext bindingContext = getCurrentBindingContextForModel(model);

        // If a context exists, return its node-set
        if (bindingContext != null)
            return bindingContext.getNodeset();

        // If there is no default instance, return an empty node-set
        final XFormsInstance defaultInstance = model.getDefaultInstance();
        if (defaultInstance == null)
            return Collections.EMPTY_LIST;

        // If not found, return the document element of the model's default instance
        try {
            return Collections.singletonList(defaultInstance.getInstanceRootElementInfo());
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Get the current single node binding for the given effective model id.
     */
//    public NodeInfo getCurrentSingleNode(String effectiveModelId) {
//
//        final BindingContext bindingContext = getCurrentBindingContextForModel(effectiveModelId);
//
//        // If a context exists, use it
//        if (bindingContext != null)
//            return bindingContext.getSingleNode();
//
//        // If there is no default instance, return null
//        final XFormsInstance defaultInstance = containingDocument.getModelByEffectiveId(effectiveModelId).getDefaultInstance();
//        if (defaultInstance == null)
//            return null;
//
//        // Otherwise return the document element of the model's default instance
//        return defaultInstance.getInstanceRootElementInfo();
//    }

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
            if (bindingElement != null && XFormsControlFactory.isContainerControl(bindingElement.getNamespaceURI(), bindingElement.getName())) {
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
                return container.getInstanceForNode(currentSingleNode);
        }
        return null;
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

        private List /* <VariableInfo> */ variables;

        private Map inScopeVariablesMap; // cached variable map

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
            variables = new ArrayList();
            variables.add(new VariableInfo(variableName, variableValue));
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

        public void setVariables(List /* <VariableInfo> */ variableInfo) {
            this.variables = variableInfo;
        }

        public List /* <VariableInfo> */ getVariables() {
            return variables;
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
            if (inScopeVariablesMap == null || !useCache) {
                final Map tempVariablesMap = new HashMap();

                BindingContext currentBindingContext = this;
                do {
                    final List currentInfo = currentBindingContext.variables;
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

                inScopeVariablesMap = tempVariablesMap;
            }
            return inScopeVariablesMap;
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
