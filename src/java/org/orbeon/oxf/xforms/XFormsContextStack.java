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
package org.orbeon.oxf.xforms;

import org.dom4j.Element;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.analysis.VariableAnalysis;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.XFormsControlFactory;
import org.orbeon.oxf.xforms.event.events.XFormsBindingExceptionEvent;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.ValueRepresentation;

import java.util.*;

/**
 * Handle a stack of XPath evaluation context information. This is used by controls (with one stack rooted at each
 * XBLContainer), models, and actions.
 */
public class XFormsContextStack {

    private XBLContainer container;
    private XFormsContainingDocument containingDocument;
    private XFormsFunction.Context functionContext;
    private BindingContext parentBindingContext;

    // TODO: use ArrayStack (although that is not generic)
    private Stack<BindingContext> contextStack = new Stack<BindingContext>();


    // Constructor for XBLContainer
    public XFormsContextStack(XBLContainer container) {
        this.container = container;
        this.containingDocument = this.container.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(container, this);
    }

    // Constructor for XFormsActionInterpreter
    public XFormsContextStack(XBLContainer container, BindingContext parentBindingContext) {
        this.container = container;
        this.containingDocument = this.container.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(container, this);
        this.parentBindingContext = parentBindingContext;

        // Push a copy of the parent binding context as first element of the stack (stack cannot be initially empty)
        contextStack.push(new BindingContext(parentBindingContext, parentBindingContext.model, parentBindingContext.nodeset,
                parentBindingContext.position, parentBindingContext.elementId, false, parentBindingContext.controlElement,
                parentBindingContext.locationData, false, parentBindingContext.contextItem, parentBindingContext.scope));
    }

    // Constructor for XFormsModel
    public XFormsContextStack(XFormsModel containingModel) {
        this.container = containingModel.getXBLContainer();
        this.containingDocument = this.container.getContainingDocument();
        this.functionContext = new XFormsFunction.Context(containingModel, this);
    }

    public void setParentBindingContext(BindingContext parentBindingContext) {
        this.parentBindingContext = parentBindingContext;
    }

    public XFormsFunction.Context getFunctionContext(String sourceEffectiveId) {
        functionContext.setSourceEffectiveId(sourceEffectiveId);
        functionContext.setSourceElement(getCurrentBindingContext().controlElement);
        functionContext.setModel(getCurrentBindingContext().model);
        return functionContext;
    }

    public void returnFunctionContext() {
        functionContext.setSourceEffectiveId(null);
        functionContext.setSourceElement(null);
    }

    /**
     * Reset the binding context to the root of the first model's first instance, or to the parent binding context.
     */
    public void resetBindingContext() {
        // Reset to default model (can be null)
        resetBindingContext(container.getDefaultModel());
    }

    /**
     * Reset the binding context to the root of the given model's first instance.
     */
    public void resetBindingContext(XFormsModel xformsModel) {

        // Clear existing stack
        contextStack.clear();

        if (xformsModel != null && xformsModel.getDefaultInstance() != null) {
            // Push the default context if there is a model with an instance
            final Item defaultNode = xformsModel.getDefaultInstance().getInstanceRootElementInfo();
            final List<Item> defaultNodeset = Arrays.asList(defaultNode);
            contextStack.push(new BindingContext(parentBindingContext, xformsModel, defaultNodeset, 1, null, true, null,
                    xformsModel.getDefaultInstance().getLocationData(), false, defaultNode, container.getResolutionScope()));
        } else {
            // Push empty context
            final List<Item> defaultContext = containingDocument.getStaticState().DEFAULT_CONTEXT;
            contextStack.push(new BindingContext(parentBindingContext, xformsModel, defaultContext, defaultContext.size(), null, true, null,
                    (xformsModel != null) ? xformsModel.getLocationData() : null, false, null, container.getResolutionScope()));
        }

        // Add model variables for default model
        if (xformsModel != null) {
            addModelVariables(xformsModel);
        }
    }

    private void addModelVariables(XFormsModel xformsModel) {
        // TODO: Check dirty flag to prevent needless re-evaluation

        // All variables in the model are in scope for the nested binds and actions.
        final List<Element> elements = xformsModel.getStaticModel().variableElements();
        final List<BindingContext.VariableInfo> variableInfos
                = addAndScopeVariables(xformsModel.getXBLContainer(), elements, xformsModel.getEffectiveId());

        if (variableInfos != null && variableInfos.size() > 0) {
            // Some variables added

            final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY);
            if (indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("", "evaluated model variables", "count", Integer.toString(variableInfos.size()));
            }

            // Remove extra bindings added
            for (int i = 0; i < variableInfos.size(); i++) {
                popBinding();
            }

            getCurrentBindingContext().setVariables(variableInfos);
        }
    }

    public List<BindingContext.VariableInfo> addAndScopeVariables(XBLContainer container, List<Element> elements, String sourceEffectiveId) {
        List<BindingContext.VariableInfo> variableInfos = null;
        final XBLBindings bindings = containingDocument.getStaticState().getXBLBindings();
        for (Element currentElement: elements) {
            if (VariableAnalysis.isVariableElement(currentElement)) {
                // Create variable object
                final Variable variable = new Variable(container, this, currentElement);

                // Find variable scope
                final XBLBindings.Scope newScope = bindings.getResolutionScopeByPrefixedId(container.getFullPrefix() + currentElement.attributeValue(XFormsConstants.ID_QNAME));

                // Push the variable on the context stack. Note that we do as if each variable was a "parent" of the
                // following controls and variables.

                // NOTE: The value is computed immediately. We should use Expression objects and do lazy evaluation
                // in the future.

                // NOTE: We used to simply add variables to the current bindingContext, but this could cause issues
                // because getVariableValue() can itself use variables declared previously. This would work at first,
                // but because BindingContext caches variables in scope, after a first request for in-scope variables,
                // further variables values could not be added. The method below temporarily adds more elements on the
                // stack but it is safer.
                getFunctionContext(sourceEffectiveId);
                pushVariable(currentElement, variable.getVariableName(), variable.getVariableValue(sourceEffectiveId, true), newScope);
                returnFunctionContext();

                // Add VariableInfo created during above pushVariable(). There must be only one!
                if (variableInfos == null)
                    variableInfos = new ArrayList<BindingContext.VariableInfo>();

                assert getCurrentBindingContext().getVariables().size() == 1;
                variableInfos.addAll(getCurrentBindingContext().getVariables());
            }
        }
        return variableInfos;
    }

    /**
     * Set the binding context to the current control.
     *
     * @param xformsControl       control to bind
     */
    public void setBinding(XFormsControl xformsControl) {
        setBinding(xformsControl.getBindingContext());
    }

    public void setBinding(BindingContext bindingContext) {

        // Don't do the work if the current context is already as requested
        if (contextStack.size() > 0 && getCurrentBindingContext() == bindingContext)
            return;

        // Create ancestors-or-self list
        final List<BindingContext> ancestorsOrSelf = new ArrayList<BindingContext>();
        while (bindingContext != null) {
            ancestorsOrSelf.add(bindingContext);
            bindingContext = bindingContext.parent;
        }
        Collections.reverse(ancestorsOrSelf);

        // Bind up to the specified control
        contextStack.clear();
        contextStack.addAll(ancestorsOrSelf);
    }

    public void restoreBinding(BindingContext bindingContext) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        if (currentBindingContext != bindingContext.parent)
            throw new ValidationException("Inconsistent binding context parent.", currentBindingContext.getLocationData());

        contextStack.push(bindingContext);
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     *
     * @param bindingElement    current element containing node binding attributes
     * @param sourceEffectiveId effective id of source control for id resolution of models and binds
     * @param scope             XBL scope
     */
    public void pushBinding(Element bindingElement, String sourceEffectiveId, XBLBindings.Scope scope) {
        // TODO: move away from element and use static analysis information
        final String ref = bindingElement.attributeValue(XFormsConstants.REF_QNAME);
        final String context = bindingElement.attributeValue(XFormsConstants.CONTEXT_QNAME);
        final String nodeset = bindingElement.attributeValue(XFormsConstants.NODESET_QNAME);
        final String model = bindingElement.attributeValue(XFormsConstants.MODEL_QNAME);
        final String bind = bindingElement.attributeValue(XFormsConstants.BIND_QNAME);

        final NamespaceMapping bindingElementNamespaceMapping = container.getNamespaceMappings(bindingElement);
        pushBinding(ref, context, nodeset, model, bind, bindingElement, bindingElementNamespaceMapping, sourceEffectiveId, scope);
    }

    private BindingContext getBindingContext(XBLBindings.Scope scope) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        if (scope == null) {
            // Keep existing scope
            // TODO: remove this once no caller can pass a null scope
            return currentBindingContext;
        } else {
            // Use scope passed
            BindingContext bindingContext = currentBindingContext;
            while (bindingContext.scope != scope) {
                bindingContext = bindingContext.parent;
                // There must be a matching scope down the line
                assert bindingContext != null;
            }
            return bindingContext;
        }
    }

    public void pushBinding(String ref, String context, String nodeset, String modelId, String bindId,
                            Element bindingElement, NamespaceMapping bindingElementNamespaceMapping, String sourceEffectiveId, XBLBindings.Scope scope) {

        // Get location data for error reporting
        final LocationData locationData = (bindingElement == null)
                ? container.getLocationData()
                : new ExtendedLocationData((LocationData) bindingElement.getData(), "pushing XForms control binding", bindingElement);

        try {
            try {
                // Determine current context
                final BindingContext currentBindingContext = getCurrentBindingContext();

                // Handle scope
                // The new binding evaluates against a base binding context which must be in the same scope
                final BindingContext baseBindingContext = getBindingContext(scope);
                final XBLBindings.Scope newScope;
                if (scope == null) {
                    // Keep existing scope
                    // TODO: remove this once no caller can pass a null scope
                    newScope = currentBindingContext.scope;
                } else {
                    // Use scope passed
                    newScope = scope;
                }

                // Set context
                final String tempSourceEffectiveId = functionContext.getSourceEffectiveId();
                if (sourceEffectiveId != null) {
                    functionContext.setSourceEffectiveId(sourceEffectiveId);
                }

                // Handle model
                final XFormsModel newModel;
                final boolean isNewModel;
                if (modelId != null) {
                    final XBLContainer resolutionScopeContainer = container.findResolutionScope(scope);
                    final Object o = resolutionScopeContainer.resolveObjectById(sourceEffectiveId, modelId, null);
                    if (!(o instanceof XFormsModel)) {
                        // TODO: should be dispatched to element having the @model attribute
                        // Invalid model id
                        container.dispatchEvent(new XFormsBindingExceptionEvent(containingDocument, container));
                    }
                    newModel = (XFormsModel) o;
                    isNewModel = newModel != baseBindingContext.model;// don't say it's a new model unless it has really changed
                } else {
                    newModel = baseBindingContext.model;
                    isNewModel = false;
                }

                // Handle nodeset
                final boolean isNewBind;
                final int newPosition;
                final List<Item> newNodeset;
                final boolean hasOverriddenContext;
                final Item contextItem;
                final boolean isPushModelVariables;
                final List<BindingContext.VariableInfo> variableInfo;
                {
                    if (bindId != null) {
                        // Resolve the bind id to a nodeset

                        // NOTE: For now, only the top-level models in a resolution scope are considered
                        final XBLContainer resolutionScopeContainer = container.findResolutionScope(scope);
                        final Object o = resolutionScopeContainer.resolveObjectById(sourceEffectiveId, bindId, baseBindingContext.getSingleItem());
                        if (o == null && resolutionScopeContainer.containsBind(bindId)) {
                            // The bind attribute was valid for this scope, but no runtime object was found for the bind
                            // This can happen e.g. if a nested bind is within a bind with an empty nodeset

                            newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                            hasOverriddenContext = false;
                            contextItem = null;
                            isNewBind = true;
                            newPosition = 0;
                            isPushModelVariables = false;
                            variableInfo = null;
                        } else if (!(o instanceof XFormsModelBinds.Bind)) {
                            // The bind attribute did not resolve to a bind: dispatch xforms-binding-exception

                            // TODO: should be dispatched to element having the @model attribute
                            container.dispatchEvent(new XFormsBindingExceptionEvent(containingDocument, container));

                            newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                            hasOverriddenContext = false;
                            contextItem = null;
                            isNewBind = true;
                            newPosition = 0;
                            isPushModelVariables = false;
                            variableInfo = null;
                        } else {
                            newNodeset = ((XFormsModelBinds.Bind) o).nodeset;
                            hasOverriddenContext = false;
                            contextItem = baseBindingContext.getSingleItem();
                            isNewBind = true;
                            newPosition = Math.min(newNodeset.size(), 1);
                            isPushModelVariables = false;
                            variableInfo = null;
                        }
                    } else if (ref != null || nodeset != null) {

                        // Check whether there is an optional context (XForms 1.1, likely generalized in XForms 1.2)
                        final BindingContext evaluationContextBinding;
                        if (context != null) {
                            // Push model and context
                            pushTemporaryContext(currentBindingContext, baseBindingContext, baseBindingContext.getSingleItem());// provide context information for the context() function
                            pushBinding(null, null, context, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope);
                            hasOverriddenContext = true;
                            final BindingContext newBindingContext = getCurrentBindingContext();
                            contextItem = newBindingContext.getSingleItem();
                            variableInfo = newBindingContext.getVariables();
                            evaluationContextBinding = newBindingContext;
                        } else if (isNewModel) {
                            // Push model only
                            pushBinding(null, null, null, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope);
                            hasOverriddenContext = false;
                            final BindingContext newBindingContext = getCurrentBindingContext();
                            contextItem = newBindingContext.getSingleItem();
                            variableInfo = newBindingContext.getVariables();
                            evaluationContextBinding = newBindingContext;
                        } else {
                            hasOverriddenContext = false;
                            contextItem = baseBindingContext.getSingleItem();
                            variableInfo = null;
                            evaluationContextBinding = baseBindingContext;
                        }

                        if (false) {
                            // NOTE: This is an attempt at allowing evaluating a binding even if no context is present.
                            // But this doesn't work properly. E.g.:
                            //
                            // <xf:group ref="()">
                            //   <xf:input ref="."/>
                            //
                            // Above must end up with an empty binding for xf:input, while:
                            //
                            // <xf:group ref="()">
                            //   <xf:input ref="instance('foobar')"/>
                            //
                            // Above must end up with a non-empty binding IF it was to be evaluated.
                            //
                            // Now the second condition above should not happen anyway, because the content of the group
                            // is non-relevant anyway. But we do have cases now where this happens, so we can't enable
                            // the code below naively.
                            //
                            // We could enable it if we knew statically that the expression did not depend on the
                            // context though, but right now we don't.

                            final boolean isDefaultContext;
                            final List<Item> evaluationNodeset;
                            final int evaluationPosition;
                            if (evaluationContextBinding.getNodeset().size() > 0) {
                                isDefaultContext = false;
                                evaluationNodeset = evaluationContextBinding.getNodeset();
                                evaluationPosition = evaluationContextBinding.getPosition();
                            } else {
                                isDefaultContext = true;
                                evaluationNodeset = containingDocument.getStaticState().DEFAULT_CONTEXT;
                                evaluationPosition = 1;
                            }

                            if (!isDefaultContext) {
                                // Provide context information for the context() function
                                pushTemporaryContext(currentBindingContext, evaluationContextBinding, evaluationContextBinding.getSingleItem());
                            }

                            // Use updated binding context to set model
                            functionContext.setModel(evaluationContextBinding.model);

                            newNodeset = XPathCache.evaluateKeepItems(evaluationNodeset, evaluationPosition,
                                    ref != null ? ref : nodeset, bindingElementNamespaceMapping, evaluationContextBinding.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                    functionContext, null, locationData);

                            if (!isDefaultContext) {
                                popBinding();
                            }
                        } else {
                            if (evaluationContextBinding.getNodeset().size() > 0) {
                                // Evaluate new XPath in context if the current context is not empty

                                // TODO: in the future, we should allow null context for expressions that do not depend on the context
                                // NOTE: We prevent evaluation if the context was empty. However there are cases where this
                                // should be allowed, if the expression does not depend on the context. Ideally, we would know
                                // statically whether an expression depends on the context or not, and take separate action if
                                // that's the case. Currently, such an expression will produce an XPathException.

                                // It might be the case that when we implement non-evaluation of relevant subtrees, this won't
                                // be an issue anymore, and we can simply allow evaluation of such expressions. Otherwise,
                                // static analysis of expressions might provide enough information to handle the two situations.

                                pushTemporaryContext(currentBindingContext, evaluationContextBinding, evaluationContextBinding.getSingleItem());// provide context information for the context() function

                                // Use updated binding context to set model
                                functionContext.setModel(evaluationContextBinding.model);

                                newNodeset = XPathCache.evaluateKeepItems(evaluationContextBinding.getNodeset(), evaluationContextBinding.getPosition(),
                                        ref != null ? ref : nodeset, bindingElementNamespaceMapping, evaluationContextBinding.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                        functionContext, null, locationData);

                                popBinding();
                            } else {
                                // Otherwise we consider we can't evaluate
                                newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                            }
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
                        contextItem = baseBindingContext.getSingleItem();
                        isNewBind = false;
                        variableInfo = null;

                    } else if (context != null) {
                        // Only the context has changed, and possibly the model
                        pushBinding(null, null, context, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope);
                        {
                            newNodeset = getCurrentNodeset();
                            newPosition = getCurrentPosition();
                            isNewBind = false;
                            hasOverriddenContext = true;
                            contextItem = getCurrentSingleItem();
                            isPushModelVariables = false;
                            variableInfo = null;
                        }
                        popBinding();

                    } else {
                        // No change to anything
                        isNewBind = false;
                        newNodeset = baseBindingContext.getNodeset();
                        newPosition = baseBindingContext.getPosition();
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
                        contextItem = baseBindingContext.getSingleItem();
                    }
                }

                // Push new context
                final String bindingElementStaticId = (bindingElement == null) ? null : bindingElement.attributeValue(XFormsConstants.ID_QNAME);
                contextStack.push(new BindingContext(currentBindingContext, newModel, newNodeset, newPosition, bindingElementStaticId, isNewBind,
                        bindingElement, locationData, hasOverriddenContext, contextItem, newScope));

                // Add new model variables if needed
                if (variableInfo != null) {
                    // In this case, we did a temporary context push with the new model, and we already gathered the new
                    // variables in scope. We have to set them to the newly pushed BindingContext.
                    getCurrentBindingContext().setVariables(variableInfo);
                } else if (isPushModelVariables) {
                    // In this case, only the model just changed and so we gather the variables in scope for the new model
                    addModelVariables(newModel);
                }

                // Restore context
                if (sourceEffectiveId != null) {
                    functionContext.setSourceEffectiveId(tempSourceEffectiveId);
                }

            } catch (ValidationException e) {
                // Must be due to nested XFormsBindingExceptionEvent
                throw e;
            } catch (Throwable e) {
                // TODO: should be dispatched to element having the @model attribute
                // Any other exception occurring within
                container.dispatchEvent(new XFormsBindingExceptionEvent(containingDocument, container, e));
            }
        } catch (ValidationException e) {
            if (bindingElement != null) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "evaluating binding expression",
                    bindingElement, "element", Dom4jUtils.elementToDebugString(bindingElement)));
            } else {
                throw ValidationException.wrapException(e, new ExtendedLocationData(locationData, "evaluating binding expression",
                    bindingElement, "ref", ref, "context", context, "nodeset", nodeset, "modelId", modelId, "bindId", bindId));
            }
        }
    }

    public void pushBinding(BindingContext bindingContext) {
        // Don't make a copy and just relink to parent
        bindingContext.updateParent(getCurrentBindingContext());
        contextStack.push(bindingContext);
    }

    private void pushTemporaryContext(BindingContext parent, BindingContext base, Item contextItem) {
        contextStack.push(new BindingContext(parent, base.model, base.getNodeset(), base.getPosition(), base.elementId,
                false, base.getControlElement(), base.getLocationData(), false, contextItem, base.scope));
    }

    /**
     * Push an iteration of the current node-set. Used for example by xforms:repeat, xforms:bind, xxforms:iterate.
     *
     * @param currentPosition   1-based iteration index
     */
    public void pushIteration(int currentPosition) {
        final BindingContext currentBindingContext = getCurrentBindingContext();
        final List<Item> currentNodeset = currentBindingContext.getNodeset();

        // Set a new context item, although the context() function is never called on the iteration itself
        final Item newContextItem;
        if (currentNodeset == null || currentNodeset.size() == 0)
            newContextItem = null;
        else
            newContextItem = currentNodeset.get(currentPosition - 1);

        contextStack.push(new BindingContext(currentBindingContext, currentBindingContext.model,
                currentNodeset, currentPosition, currentBindingContext.elementId, true, null, currentBindingContext.getLocationData(),
                false, newContextItem, currentBindingContext.scope));
    }

    /**
     * Push a new variable in scope, providing its name and value.
     *
     * @param variableElement   variable element
     * @param name              variable name
     * @param value             variable value
     * @param scope             XBL scope of the variable visibility
     */
    public void pushVariable(Element variableElement, String name, ValueRepresentation value, XBLBindings.Scope scope) {
        final LocationData locationData = new ExtendedLocationData((LocationData) variableElement.getData(), "pushing variable binding", variableElement);
        contextStack.push(new BindingContext(getCurrentBindingContext(), getBindingContext(scope), variableElement, locationData, name, value, scope));
    }

    public BindingContext getCurrentBindingContext() {
        return contextStack.peek();
    }

    public BindingContext popBinding() {
        if (contextStack.size() == 1)
            throw new OXFException("Attempt to clear context stack.");
        return contextStack.pop();
    }

    /**
     * Get the current node-set binding for the given model.
     */
    public BindingContext getCurrentBindingContextForModel(XFormsModel model) {

        for (int i = contextStack.size() - 1; i >= 0; i--) {
            final BindingContext currentBindingContext = contextStack.get(i);
            if (model == currentBindingContext.model)
                return currentBindingContext;
        }

        return null;
    }

    /**
     * Get the current node-set binding for the given model.
     */
    public List<Item> getCurrentNodeset(XFormsModel model) {

        final BindingContext bindingContext = getCurrentBindingContextForModel(model);

        // If a context exists, return its node-set
        if (bindingContext != null)
            return bindingContext.getNodeset();

        // If there is no default instance, return an empty node-set
        final XFormsInstance defaultInstance = model.getDefaultInstance();
        if (defaultInstance == null)
            return XFormsConstants.EMPTY_ITEM_LIST;

        // If not found, return the document element of the model's default instance
        try {
            return Collections.singletonList((Item) defaultInstance.getInstanceRootElementInfo());
        } catch (Exception e) {
            throw new OXFException(e);
        }
    }

    /**
     * Get the current single item, if any.
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

    /**
     * Get the current nodeset binding, if any.
     */
    public List<Item> getCurrentNodeset() {
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
    public Map<String, ValueRepresentation> getCurrentVariables() {
        return getCurrentBindingContext().getInScopeVariables();
    }

    /**
     * Return the single item associated with the iteration of the repeat specified. If a null
     * repeat id is passed, return the single item associated with the closest enclosing repeat
     * iteration.
     *
     * NOTE: Use getContextForId() instead.
     *
     * @param repeatId  enclosing repeat id, or null
     * @return          the single item
     */
    public Item getRepeatCurrentSingleNode(String repeatId) {
        BindingContext currentBindingContext = getCurrentBindingContext();
        do {
            if (isRepeatIterationBindingContext(currentBindingContext) && (repeatId == null || currentBindingContext.parent.elementId.equals(repeatId))) {
                // Found binding context for relevant repeat iteration
                return currentBindingContext.getSingleItem();
            }
            currentBindingContext = currentBindingContext.parent;
        } while (currentBindingContext != null);

        // It is required that there is a relevant enclosing xforms:repeat
        if (repeatId == null)
            throw new ValidationException("No enclosing xforms:repeat found.", getCurrentBindingContext().getLocationData());
        else
            throw new ValidationException("No enclosing xforms:repeat found for repeat id: " + repeatId, getCurrentBindingContext().getLocationData());
    }

    private boolean isRepeatIterationBindingContext(BindingContext bindingContext) {
        // First, we need a parent
        final BindingContext parent = bindingContext.parent;
        if (parent == null)
            return false;
        // We don't have a bound element, but the parent is bound to xforms:repeat
        final Element bindingElement = bindingContext.getControlElement();
        final Element parentBindingElement = parent.getControlElement();
        return (bindingElement == null) && (parentBindingElement != null) && parentBindingElement.getName().equals("repeat");
    }

    private boolean isRepeatBindingContext(BindingContext bindingContext) {
        final Element bindingElement = bindingContext.getControlElement();
        return bindingElement != null && bindingElement.getName().equals("repeat");
    }

    /**
     * Return the closest enclosing repeat id.
     *
     * @return  repeat id, throw if not found
     */
    public String getEnclosingRepeatId() {
        BindingContext currentBindingContext = getCurrentBindingContext();
        do {
            // Handle case where we are within a repeat iteration, as well as case where we are directly within the
            // repeat container object.
            if (isRepeatIterationBindingContext(currentBindingContext) || isRepeatBindingContext(currentBindingContext)) {
                // Found binding context for relevant repeat iteration
                return currentBindingContext.parent.elementId;
            }
            currentBindingContext = currentBindingContext.parent;
        } while (currentBindingContext != null);
        // It is required that there is an enclosing xforms:repeat
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
        BindingContext currentBindingContext = getCurrentBindingContext();
        do {
            final Element bindingElement = currentBindingContext.getControlElement();
            if (bindingElement != null && XFormsControlFactory.isContainerControl(bindingElement.getNamespaceURI(), bindingElement.getName())) {
                // We are a grouping control
                final String elementId = currentBindingContext.elementId;
                if (contextId.equals(elementId)) {
                    // Found matching binding context for regular grouping control
                    return currentBindingContext.getSingleItem();
                }
            } else if (bindingElement == null) {
                // We a likely repeat iteration
                if (isRepeatIterationBindingContext(currentBindingContext) && currentBindingContext.parent.elementId.equals(contextId)) {
                    // Found matching repeat iteration
                    return currentBindingContext.getSingleItem();
                }
            }
            currentBindingContext = currentBindingContext.parent;
        } while (currentBindingContext != null);
        // It is required that there is an enclosing container control
        throw new ValidationException("No enclosing container XForms control found for id: " + contextId, getCurrentBindingContext().getLocationData());
    }

    /**
     * Get the current node-set for the given repeat id.
     *
     * @param repeatId  existing repeat id
     * @return          node-set
     */
    public List getRepeatNodeset(String repeatId) {
        BindingContext currentBindingContext = getCurrentBindingContext();
        do {
            final Element bindingElement = currentBindingContext.getControlElement();
            final String elementId = currentBindingContext.elementId;
            if (repeatId.equals(elementId) && bindingElement != null && bindingElement.getName().equals("repeat")) {
                // Found repeat, return associated node-set
                return currentBindingContext.getNodeset();
            }
            currentBindingContext = currentBindingContext.parent;
        } while (currentBindingContext != null);
        // It is required that there is an enclosing xforms:repeat
        throw new ValidationException("No enclosing xforms:repeat found for id: " + repeatId, getCurrentBindingContext().getLocationData());
    }

    /**
     * Return the current model for the current nodeset binding.
     */
    public XFormsModel getCurrentModel() {
        return getCurrentBindingContext().model;
    }

    public static class BindingContext {
        public BindingContext parent;
        public final XFormsModel model;
        public final List<Item> nodeset;
        public final int position;
        public final String elementId;
        public final boolean newBind;
        public final Element controlElement;
        public final LocationData locationData;
        public final boolean hasOverriddenContext;
        public final Item contextItem;
        public final XBLBindings.Scope scope;

        private List<VariableInfo> variables;

        public BindingContext(BindingContext parent, XFormsModel model, List<Item> nodeSet, int position, String elementId,
                              boolean newBind, Element controlElement, LocationData locationData, boolean hasOverriddenContext,
                              Item contextItem, XBLBindings.Scope scope) {

            assert scope != null;

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

            this.scope = scope; // XBL scope of the variable visibility

//            if (nodeset != null && nodeset.size() > 0) {
//                // TODO: PERF: This seems to take some significant time
//                for (Iterator i = nodeset.iterator(); i.hasNext();) {
//                    final Object currentItem = i.next();
//                    if (!(currentItem instanceof NodeInfo))
//                        throw new ValidationException("A reference to a node (such as element, attribute, or text) is required in a binding. Attempted to bind to the invalid item type: " + currentItem.getClass(), this.locationData);
//                }
//            }
        }

        public void updateParent(BindingContext parent) {
            this.parent = parent;
        }

        public BindingContext(BindingContext parent, BindingContext base, Element controlElement, LocationData locationData, String variableName,
                              ValueRepresentation variableValue, XBLBindings.Scope scope) {
            this(parent, base.model, base.getNodeset(), base.getPosition(), base.elementId, false,
                    controlElement, locationData, false, base.getContextItem(), scope);

            variables = new ArrayList<VariableInfo>();
            variables.add(new VariableInfo(variableName, variableValue));
        }

        public List<Item> getNodeset() {
            return nodeset;
        }

        public int getPosition() {
            return position;
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
         * Get the current single item, if any.
         */
        public Item getSingleItem() {
            if (nodeset == null || nodeset.size() == 0)
                return null;

            return nodeset.get(position - 1);
        }

        public void setVariables(List<VariableInfo> variableInfo) {
            this.variables = variableInfo;
        }

        public List<VariableInfo> getVariables() {
            return variables;
        }

        /**
         * Return a Map of the variables in scope.
         *
         * @return  map of variable name to value
         */
        public Map<String, ValueRepresentation> getInScopeVariables() {
            // TODO: Variables in scope in the view must not include the variables defined in another model, but must include all view variables.
            final Map<String, ValueRepresentation> tempVariablesMap = new HashMap<String, ValueRepresentation>();

            BindingContext currentBindingContext = this; // start with current BindingContext
            do {
                if (currentBindingContext.scope == scope) { // consider only BindingContext with same scope and skip others
                    final List<VariableInfo> currentInfo = currentBindingContext.variables;
                    if (currentInfo != null) {
                        for (VariableInfo variableInfo: currentInfo) {
                            final String currentName = variableInfo.variableName;
                            if (currentName != null && tempVariablesMap.get(currentName) == null) {
                                // The binding defines a variable and there is not already a variable with that name
                                tempVariablesMap.put(variableInfo.variableName, variableInfo.variableValue);
                            }
                        }
                    }
                }

                currentBindingContext = currentBindingContext.parent;
            } while (currentBindingContext != null);

            return tempVariablesMap;
        }

        public static class VariableInfo {
            private String variableName;
            private ValueRepresentation variableValue;

            private VariableInfo(String variableName, ValueRepresentation variableValue) {
                this.variableName = variableName;
                this.variableValue = variableValue;
            }
        }

        public XFormsInstance getInstance() {
            // NOTE: This is as of 2009-09-17 used only to determine the submission instance based on a submission node.
            // May return null
            final Item currentNode = getSingleItem();
            return (currentNode instanceof NodeInfo) ? model.getContainingDocument().getInstanceForNode((NodeInfo) currentNode) : null;
        }
    }
}
