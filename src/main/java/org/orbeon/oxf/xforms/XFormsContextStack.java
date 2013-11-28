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
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.analysis.VariableAnalysisTrait;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.TransformerUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.tinytree.TinyBuilder;
import org.xml.sax.SAXException;

import javax.xml.transform.sax.TransformerHandler;
import java.util.*;

/**
 * Handle a stack of XPath evaluation context information. This is used by controls (with one stack rooted at each
 * XBLContainer), models, and actions.
 *
 * TODO: This has to go, and instead we will just use BindingContext.
 */
public class XFormsContextStack {

    private static final NodeInfo DUMMY_CONTEXT;
    static {
        try {
            final TinyBuilder treeBuilder = new TinyBuilder();
            final TransformerHandler identity = TransformerUtils.getIdentityTransformerHandler(XPath.GlobalConfiguration());
            identity.setResult(treeBuilder);

            identity.startDocument();
            identity.endDocument();

            DUMMY_CONTEXT = treeBuilder.getCurrentRoot();
        } catch (SAXException e) {
            throw new OXFException(e);
        }
    }

    // If there is no XPath context defined at the root (in the case there is no default XForms model/instance
    // available), we should use an empty context. However, currently for non-relevance in particular we must not run
    // expressions with an empty context. To allow running expressions at the root of a container without models, we
    // create instead a context with an empty document node instead. This way there is a context for evaluation. In the
    // future, we should allow running expressions with no context, possibly after statically checking that they do not
    // depend on the context, as well as prevent evaluations within non-relevant content by other means.
//    final List<Item> DEFAULT_CONTEXT = XFormsConstants.EMPTY_ITEM_LIST;
    private static final List<Item> DEFAULT_CONTEXT = Collections.singletonList((Item) DUMMY_CONTEXT);

    private final boolean keepLocationData = XFormsProperties.isKeepLocation();

    public final XBLContainer container;
    public final XFormsContainingDocument containingDocument;

    private BindingContext parentBindingContext;

    private BindingContext head = null;

    // Constructor for XFormsModel and XBLContainer
    public XFormsContextStack(XBLContainer container) {
        this.container = container;
        this.containingDocument = container.getContainingDocument();
    }

    // Constructor for XFormsActionInterpreter
    public XFormsContextStack(XBLContainer container, BindingContext parentBindingContext) {
        this.container = container;
        this.containingDocument = this.container.getContainingDocument();
        this.parentBindingContext = parentBindingContext;

        // Push a copy of the parent binding
        this.head = pushCopy(parentBindingContext);
    }

    // Push a copy of the current binding
    public BindingContext pushCopy() {
        return pushCopy(this.head);
    }

    private BindingContext pushCopy(BindingContext parent) {
        this.head = new BindingContext(parent, parent.model(), parent.bind(), parent.nodeset(),
                parent.position(), parent.elementId(), false, parent.controlElement(),
                parent.locationData(), false, parent.contextItem(), parent.scope());

        return this.head;
    }

    // For XBL/xxf:dynamic
    public void setParentBindingContext(BindingContext parentBindingContext) {
        this.parentBindingContext = parentBindingContext;
    }

    public XFormsFunction.Context getFunctionContext(String sourceEffectiveId) {
        return getFunctionContext(sourceEffectiveId, this.head);
    }

    public XFormsFunction.Context getFunctionContext(String sourceEffectiveId, BindingContext binding) {
        return new XFormsFunction.Context(container, binding, sourceEffectiveId, binding.model());
    }

    public void returnFunctionContext() {
        // NOP XXX
    }

    /**
     * Reset the binding context to the root of the first model's first instance, or to the parent binding context.
     */
    public BindingContext resetBindingContext() {
        // Reset to default model (can be null)
        resetBindingContext(container.getDefaultModel());
        return this.head;
    }

    /**
     * Reset the binding context to the root of the given model's first instance.
     */
    public void resetBindingContext(XFormsModel model) {

        if (model != null && model.getDefaultInstance() != null) {
            // Push the default context if there is a model with an instance
            final Item defaultNode = model.getDefaultInstance().rootElement();
            final List<Item> defaultNodeset = Arrays.asList(defaultNode);
            this.head = new BindingContext(parentBindingContext, model, null, defaultNodeset, 1, null, true, null,
                    model.getDefaultInstance().getLocationData(), false, defaultNode, container.innerScope());
        } else {
            // Push empty context
            this.head = defaultContext(parentBindingContext, container, model);
        }

        // Add model variables for default model
        if (model != null)
            model.setTopLevelVariables(evaluateModelVariables(model));
    }

    /**
     * Return an empty context for the given model (which can be null).
     */
    public static BindingContext defaultContext(BindingContext parentBindingContext, XBLContainer container, XFormsModel model) {
        final List<Item> defaultContext = DEFAULT_CONTEXT;
        return new BindingContext(parentBindingContext, model, null, defaultContext, defaultContext.size(), null, true, null,
                (model != null) ? model.getLocationData() : null, false, null, container.innerScope());
    }

    // NOTE: This only scopes top-level model variables, but not binds-as-variables.
    private Map<String, ValueRepresentation> evaluateModelVariables(XFormsModel model) {
        // TODO: Check dirty flag to prevent needless re-evaluation
        // All variables in the model are in scope for the nested binds and actions.
        final List<VariableAnalysisTrait> variables = model.getStaticModel().jVariablesSeq();
        if (! variables.isEmpty()) {

            final Map<String, ValueRepresentation> variableInfos = new HashMap<String, ValueRepresentation>();

            for (final VariableAnalysisTrait variable : variables)
                variableInfos.put(variable.name(), scopeVariable(variable, model.getEffectiveId(), true).value());

            final IndentedLogger indentedLogger = containingDocument.getIndentedLogger(XFormsModel.LOGGING_CATEGORY);
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("", "evaluated model variables", "count", Integer.toString(variableInfos.size()));

            // Remove extra bindings added and set all variables on the current binding context so that things are cleaner
            for (int i = 0; i < variableInfos.size(); i++)
                popBinding();

            return variableInfos;
        } else
            return Collections.emptyMap();
    }

    public VariableNameValue scopeVariable(VariableAnalysisTrait staticVariable, String sourceEffectiveId, boolean handleNonFatal) {
        
        // Create variable object
        final Variable variable = new Variable(staticVariable, containingDocument);

        // Find variable scope
        final Scope newScope = ((ElementAnalysis) staticVariable).scope();

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
        this.head = this.head.pushVariable((ElementAnalysis) staticVariable, variable.getVariableName(), variable.getVariableValue(this, sourceEffectiveId, true, handleNonFatal), newScope);

        return this.head.variable().get();
    }

    public BindingContext setBinding(BindingContext bindingContext) {
        this.head = bindingContext;
        return this.head;
    }

    /**
     * Push an element containing either single-node or nodeset binding attributes.
     *
     * @param bindingElement    current element containing node binding attributes
     * @param sourceEffectiveId effective id of source control for id resolution of models and binds
     * @param scope             XBL scope
     */
    public void pushBinding(Element bindingElement, String sourceEffectiveId, Scope scope) {
        pushBinding(bindingElement, sourceEffectiveId, scope, true);
    }
    
    public void pushBinding(Element bindingElement, String sourceEffectiveId, Scope scope, boolean handleNonFatal) {
        // TODO: move away from element and use static analysis information
        final String ref = bindingElement.attributeValue(XFormsConstants.REF_QNAME);
        final String context = bindingElement.attributeValue(XFormsConstants.CONTEXT_QNAME);
        final String nodeset = bindingElement.attributeValue(XFormsConstants.NODESET_QNAME);
        final String model = bindingElement.attributeValue(XFormsConstants.MODEL_QNAME);
        final String bind = bindingElement.attributeValue(XFormsConstants.BIND_QNAME);

        final NamespaceMapping bindingElementNamespaceMapping = container.getNamespaceMappings(bindingElement);
        pushBinding(ref, context, nodeset, model, bind, bindingElement, bindingElementNamespaceMapping, sourceEffectiveId, scope, handleNonFatal);
    }

    private BindingContext getBindingContext(Scope scope) {
        BindingContext bindingContext = this.head;
        while (bindingContext.scope() != scope) {
            bindingContext = bindingContext.parent();
            // There must be a matching scope down the line
            assert bindingContext != null;
        }
        return bindingContext;
    }

    public void pushBinding(String ref, String context, String nodeset, String modelId, String bindId,
                            Element bindingElement, NamespaceMapping bindingElementNamespaceMapping, String sourceEffectiveId, Scope scope, boolean handleNonFatal) {

        assert scope != null;

        final LocationData locationData; {
            if (keepLocationData)
                locationData = (bindingElement == null)
                    ? container.getLocationData()
                    : new ExtendedLocationData((LocationData) bindingElement.getData(), "pushing XForms control binding", bindingElement);
            else
                locationData = null;
        }

        try {
            // Handle scope
            // The new binding evaluates against a base binding context which must be in the same scope
            final BindingContext baseBindingContext = getBindingContext(scope);

            // Handle model
            final XFormsModel newModel;
            final boolean isNewModel;
            if (modelId != null) {
                final XBLContainer resolutionScopeContainer = container.findScopeRoot(scope);
                final XFormsObject o = resolutionScopeContainer.resolveObjectById(sourceEffectiveId, modelId, null);
                if (!(o instanceof XFormsModel)) {
                    // Invalid model id

                    // NOTE: We used to dispatch xforms-binding-exception, but we want to be able to recover
                    if (!handleNonFatal)
                        throw new ValidationException("Reference to non-existing model id: " + modelId, locationData);

                    // Default to not changing the model
                    newModel = baseBindingContext.model();
                    isNewModel = false;
                } else {
                    newModel = (XFormsModel) o;
                    isNewModel = newModel != baseBindingContext.model();// don't say it's a new model unless it has really changed
                }
            } else {
                newModel = baseBindingContext.model();
                isNewModel = false;
            }

            // Handle nodeset
            final boolean isNewBind;
            final RuntimeBind bind;
            final int newPosition;
            final List<Item> newNodeset;
            final boolean hasOverriddenContext;
            final Item contextItem;
            {
                if (bindId != null) {
                    // Resolve the bind id to a nodeset

                    // NOTE: For now, only the top-level models in a resolution scope are considered
                    final XBLContainer resolutionScopeContainer = container.findScopeRoot(scope);
                    final XFormsObject o = resolutionScopeContainer.resolveObjectById(sourceEffectiveId, bindId, baseBindingContext.getSingleItem());
                    if (o == null && resolutionScopeContainer.containsBind(bindId)) {
                        // The bind attribute was valid for this scope, but no runtime object was found for the bind
                        // This can happen e.g. if a nested bind is within a bind with an empty nodeset

                        bind = null;
                        newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                        hasOverriddenContext = false;
                        contextItem = null;
                        isNewBind = true;
                        newPosition = 0;
                    } else if (!(o instanceof RuntimeBind)) {
                        // The bind attribute did not resolve to a bind

                        // NOTE: We used to dispatch xforms-binding-exception, but we want to be able to recover
                        if (!handleNonFatal)
                            throw new ValidationException("Reference to non-existing bind id: " + bindId, locationData);

                        // Default to an empty binding
                        bind = null;
                        newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                        hasOverriddenContext = false;
                        contextItem = null;
                        isNewBind = true;
                        newPosition = 0;
                    } else {
                        bind = (RuntimeBind) o;
                        newNodeset = bind.nodeset;
                        hasOverriddenContext = false;
                        contextItem = baseBindingContext.getSingleItem();
                        isNewBind = true;
                        newPosition = Math.min(newNodeset.size(), 1);
                    }
                } else if (ref != null || nodeset != null) {

                    bind = null;

                    // Check whether there is an optional context (XForms 1.1, likely generalized in XForms 1.2)
                    final BindingContext evaluationContextBinding;
                    if (context != null) {
                        // Push model and context
                        pushTemporaryContext(this.head, baseBindingContext, baseBindingContext.getSingleItem());// provide context information for the context() function
                        pushBinding(null, null, context, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope, handleNonFatal);
                        hasOverriddenContext = true;
                        final BindingContext newBindingContext = this.head;
                        contextItem = newBindingContext.getSingleItem();
                        evaluationContextBinding = newBindingContext;
                    } else if (isNewModel) {
                        // Push model only
                        pushBinding(null, null, null, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope, handleNonFatal);
                        hasOverriddenContext = false;
                        final BindingContext newBindingContext = this.head;
                        contextItem = newBindingContext.getSingleItem();
                        evaluationContextBinding = newBindingContext;
                    } else {
                        hasOverriddenContext = false;
                        contextItem = baseBindingContext.getSingleItem();
                        evaluationContextBinding = baseBindingContext;
                    }

//                    if (false) {
//                        // NOTE: This is an attempt at allowing evaluating a binding even if no context is present.
//                        // But this doesn't work properly. E.g.:
//                        //
//                        // <xf:group ref="()">
//                        //   <xf:input ref="."/>
//                        //
//                        // Above must end up with an empty binding for xf:input, while:
//                        //
//                        // <xf:group ref="()">
//                        //   <xf:input ref="instance('foobar')"/>
//                        //
//                        // Above must end up with a non-empty binding IF it was to be evaluated.
//                        //
//                        // Now the second condition above should not happen anyway, because the content of the group
//                        // is non-relevant anyway. But we do have cases now where this happens, so we can't enable
//                        // the code below naively.
//                        //
//                        // We could enable it if we knew statically that the expression did not depend on the
//                        // context though, but right now we don't.
//
//                        final boolean isDefaultContext;
//                        final List<Item> evaluationNodeset;
//                        final int evaluationPosition;
//                        if (evaluationContextBinding.getNodeset().size() > 0) {
//                            isDefaultContext = false;
//                            evaluationNodeset = evaluationContextBinding.getNodeset();
//                            evaluationPosition = evaluationContextBinding.getPosition();
//                        } else {
//                            isDefaultContext = true;
//                            evaluationNodeset = DEFAULT_CONTEXT;
//                            evaluationPosition = 1;
//                        }
//
//                        if (!isDefaultContext) {
//                            // Provide context information for the context() function
//                            pushTemporaryContext(this.head, evaluationContextBinding, evaluationContextBinding.getSingleItem());
//                        }
//
//                        // Use updated binding context to set model
//                        functionContext.setModel(evaluationContextBinding.model());
//
//                        List<Item> result;
//                            try {
//                                result = XPathCache.evaluateKeepItems(evaluationNodeset, evaluationPosition,
//                                        ref != null ? ref : nodeset, bindingElementNamespaceMapping, evaluationContextBinding.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
//                                        functionContext, null, locationData, containingDocument.getRequestStats().getReporter());
//                            } catch (Exception e) {
//                                if (handleNonFatal) {
//                                    XFormsError.handleNonFatalXPathError(container, e);
//                                    result = XFormsConstants.EMPTY_ITEM_LIST;
//                                } else {
//                                    throw e;
//                                }
//                            }
//                        newNodeset = result;
//
//
//                        if (!isDefaultContext) {
//                            popBinding();
//                        }
//                    } else {
                        if (evaluationContextBinding.nodeset().size() > 0) {
                            // Evaluate new XPath in context if the current context is not empty

                            // TODO: in the future, we should allow null context for expressions that do not depend on the context
                            // NOTE: We prevent evaluation if the context was empty. However there are cases where this
                            // should be allowed, if the expression does not depend on the context. Ideally, we would know
                            // statically whether an expression depends on the context or not, and take separate action if
                            // that's the case. Currently, such an expression will produce an XPathException.

                            // It might be the case that when we implement non-evaluation of relevant subtrees, this won't
                            // be an issue anymore, and we can simply allow evaluation of such expressions. Otherwise,
                            // static analysis of expressions might provide enough information to handle the two situations.

                            pushTemporaryContext(this.head, evaluationContextBinding, evaluationContextBinding.getSingleItem());// provide context information for the context() function

                            // Use updated binding context to set model
                            final XFormsFunction.Context functionContext = getFunctionContext(sourceEffectiveId, evaluationContextBinding);

                            List<Item> result;
                                try {
                                    result = XPathCache.evaluateKeepItems(evaluationContextBinding.nodeset(), evaluationContextBinding.position(),
                                            ref != null ? ref : nodeset, bindingElementNamespaceMapping, evaluationContextBinding.getInScopeVariables(), XFormsContainingDocument.getFunctionLibrary(),
                                            functionContext, null, locationData, containingDocument.getRequestStats().getReporter());
                                } catch (Exception e) {
                                    if (handleNonFatal) {
                                        XFormsError.handleNonFatalXPathError(container, e);
                                        result = XFormsConstants.EMPTY_ITEM_LIST;
                                    } else {
                                        throw e;
                                    }

                                }
                            newNodeset = result;

                            popBinding();
                        } else {
                            // Otherwise we consider we can't evaluate
                            newNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                        }
//                    }

                    // Restore optional context
                    if (context != null || isNewModel) {
                        popBinding();
                        if (context != null)
                            popBinding();
                    }
                    isNewBind = true;
                    newPosition = 1;
                } else if (isNewModel && context == null) {
                    // Only the model has changed

                    bind = null;

                    final BindingContext modelBindingContext = this.head.currentBindingContextForModel(newModel);
                    if (modelBindingContext != null) {
                        newNodeset = modelBindingContext.nodeset();
                        newPosition = modelBindingContext.position();
                    } else {
                        newNodeset = this.head.currentNodeset(newModel);
                        newPosition = 1;
                    }

                    hasOverriddenContext = false;
                    contextItem = baseBindingContext.getSingleItem();
                    isNewBind = false;

                } else if (context != null) {

                    bind = null;

                    // Only the context has changed, and possibly the model
                    pushBinding(null, null, context, modelId, null, null, bindingElementNamespaceMapping, sourceEffectiveId, scope, handleNonFatal);
                    {
                        newNodeset = this.head.nodeset();
                        newPosition = this.head.position();
                        isNewBind = false;
                        hasOverriddenContext = true;
                        contextItem = this.head.getSingleItem();
                    }
                    popBinding();

                } else {
                    // No change to anything
                    bind = null;
                    isNewBind = false;
                    newNodeset = baseBindingContext.nodeset();
                    newPosition = baseBindingContext.position();

                    // We set a new context item as the context into which other attributes must be evaluated. E.g.:
                    //
                    // <xf:select1 ref="type">
                    //   <xf:action ev:event="xforms-value-changed" if="context() = 'foobar'">
                    //
                    // In this case, you expect context() to be updated as follows.
                    //
                    hasOverriddenContext = false;
                    contextItem = baseBindingContext.getSingleItem();
                }
            }

            // Push new context
            final String bindingElementId = (bindingElement == null) ? null : XFormsUtils.getElementId(bindingElement);
            this.head = new BindingContext(this.head, newModel, bind, newNodeset, newPosition, bindingElementId, isNewBind,
                    bindingElement, locationData, hasOverriddenContext, contextItem, scope);
        } catch (Exception e) {
            if (bindingElement != null) {
                throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(locationData, "evaluating binding expression", bindingElement));
            } else {
                throw OrbeonLocationException.wrapException(e, new ExtendedLocationData(locationData, "evaluating binding expression",
                    bindingElement, new String[] { "ref", ref, "context", context, "nodeset", nodeset, "modelId", modelId, "bindId", bindId }));
            }
        }
    }

    private void pushTemporaryContext(BindingContext parent, BindingContext base, Item contextItem) {
        this.head = new BindingContext(parent, base.model(), null, base.nodeset(), base.position(), base.elementId(),
                false, base.controlElement(), base.locationData(), false, contextItem, base.scope());
    }

    /**
     * Push an iteration of the current node-set. Used for example by xf:repeat, xf:bind, iterate.
     *
     * @param currentPosition   1-based iteration index
     */
    public BindingContext pushIteration(int currentPosition) {
        final BindingContext currentBindingContext = this.head;
        final List<Item> currentNodeset = currentBindingContext.nodeset();

        // Set a new context item, although the context() function is never called on the iteration itself
        final Item newContextItem;
        if (currentNodeset.size() == 0)
            newContextItem = null;
        else
            newContextItem = currentNodeset.get(currentPosition - 1);

        this.head = new BindingContext(currentBindingContext, currentBindingContext.model(), null,
                currentNodeset, currentPosition, currentBindingContext.elementId(), true, null, currentBindingContext.locationData(),
                false, newContextItem, currentBindingContext.scope());

        return this.head;
    }

    public BindingContext getCurrentBindingContext() {
        return head;
   }

    public BindingContext popBinding() {
        if (this.head.parent() == null)
            throw new OXFException("Attempt to clear context stack.");
        final BindingContext popped = this.head;
        this.head = this.head.parent();
        return popped;
    }
}
