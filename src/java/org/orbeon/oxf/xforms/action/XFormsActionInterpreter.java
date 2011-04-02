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
package org.orbeon.oxf.xforms.action;

import org.apache.commons.lang.StringUtils;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.control.XFormsControl;
import org.orbeon.oxf.xforms.control.controls.XXFormsVariableControl;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.*;
import org.orbeon.saxon.om.Item;

import java.util.Collections;
import java.util.List;

/**
 * Execute a top-level XForms action and the included nested actions if any.
 */
public class XFormsActionInterpreter {

    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;

    private final IndentedLogger indentedLogger;

    private final XFormsControls xformsControls;
    private final XFormsContextStack actionBlockContextStack;

    private final String outerActionElementEffectiveId;

    public XFormsActionInterpreter(XBLContainer container, XFormsEventObserver eventObserver,
                                   Element outerActionElement, String ancestorObserverStaticId, boolean isXBLHandler) {

        this.container = container;
        this.containingDocument = container.getContainingDocument();

        this.indentedLogger = containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY);

        this.xformsControls = containingDocument.getControls();

        if (isXBLHandler) {
            // Get the effective id of the <xbl:handler> element as source
            outerActionElementEffectiveId = container.getFullPrefix() + getActionStaticId(outerActionElement) + XFormsUtils.getEffectiveIdSuffixWithSeparator(container.getEffectiveId());

            // Initialize context stack based on container context (based on local models if any)
            container.getContextStack().resetBindingContext();
            actionBlockContextStack = new XFormsContextStack(container, container.getContextStack().getCurrentBindingContext());
        } else if (containingDocument.getEffectiveId().equals(ancestorObserverStaticId)) {
            // Ancestor is the containing document itself

            // Since we are at the top-level, the effective id of the action is the same as its static id
            outerActionElementEffectiveId = getActionStaticId(outerActionElement);

            // Start with default model as context
            actionBlockContextStack = new XFormsContextStack(container, containingDocument.getDefaultModel().getBindingContext(containingDocument));

            // TODO: Does sourceEffectiveId work
            scopeVariables(containingDocument, outerActionElement, outerActionElement.getParent(), containingDocument.getDefaultModel().getEffectiveId());

        } else {
            // Set XPath context based on lexical location
            final XFormsEventObserver xpathContextObserver;
            if (eventObserver.getId().equals(ancestorObserverStaticId)) {
                // Observer is parent of action, so we have easily access to the effective context
                xpathContextObserver = eventObserver;
            } else {
                // Observer is not parent of action, must resolve effective id of parent
                xpathContextObserver = (XFormsEventObserver) container.resolveObjectByIdInScope(eventObserver.getEffectiveId(), ancestorObserverStaticId, null);
            }

            // Effective id of the XPath observer, which must be a control or model object
            outerActionElementEffectiveId = XFormsUtils.getRelatedEffectiveId(xpathContextObserver.getEffectiveId(), getActionStaticId(outerActionElement));

            actionBlockContextStack = new XFormsContextStack(container, xpathContextObserver.getBindingContext(containingDocument));

            // Check variables in scope for action handlers within controls
            if (xpathContextObserver instanceof XXFormsVariableControl) {
                // Special case of a listener within a variable control
                final XXFormsVariableControl variable = (XXFormsVariableControl) xpathContextObserver;
                actionBlockContextStack.pushVariable(variable.getControlElement(), variable.getVariableName(), variable.getValue(), variable.getResolutionScope());
            } else if (xpathContextObserver instanceof XFormsControl) {
                // NOTE: This is not right, as variable values are re-evaluated and may have values different from the ones
                // used by the controls during refresh. Contemplate handling this differently, e.g. see
                // http://wiki.orbeon.com/forms/projects/core-xforms-engine-improvements#TOC-Representation-of-outer-action-hand
                // TODO: here we can scope variables by actually asking the preceding XXFormsVariableControl
                scopeVariables(container, outerActionElement, ((XFormsControl) xpathContextObserver).getControlElement(), xpathContextObserver.getEffectiveId());
            }
        }

        // Push binding for outermost action
        actionBlockContextStack.pushBinding(outerActionElement, getSourceEffectiveId(outerActionElement), getActionScope(outerActionElement));
    }

    private void scopeVariables(XBLContainer container, Element outerActionElement, Element ancestorElement, String sourceEffectiveId) {
        final List<Element> actionPrecedingElements = Dom4jUtils.findPrecedingElements(outerActionElement, ancestorElement);
        if (actionPrecedingElements.size() > 0) {
            Collections.reverse(actionPrecedingElements);
            final List<XFormsContextStack.BindingContext.VariableInfo> variableInfos
                    = actionBlockContextStack.addAndScopeVariables(container, actionPrecedingElements, sourceEffectiveId);
            if (variableInfos != null && variableInfos.size() > 0 && indentedLogger.isDebugEnabled()) {
                indentedLogger.logDebug("interpreter", "evaluated variables for outer action",
                        "count", Integer.toString(variableInfos.size()));
            }
        }
    }

    public XBLContainer getXBLContainer() {
        return container;
    }

    public XFormsContainingDocument getContainingDocument() {
        return containingDocument;
    }

    public IndentedLogger getIndentedLogger() {
        return indentedLogger;
    }

    public XFormsContextStack getContextStack() {
        return actionBlockContextStack;
    }

    /**
     * Return the namespace mappings for the given action element.
     *
     * @param actionElement element to get namespace mapping for
     * @return              mapping
     */
    public NamespaceMapping getNamespaceMappings(Element actionElement) {
        return container.getNamespaceMappings(actionElement);
    }

    /**
     * Execute an XForms action.
     *
     * @param event                 event causing the action
     * @param eventObserver         event observer
     * @param actionElement         Element specifying the action to execute
     */
    public void runAction(XFormsEvent event, XFormsEventObserver eventObserver, Element actionElement) {

        // Check that we understand the action element
        final String actionNamespaceURI = actionElement.getNamespaceURI();
        final String actionName = actionElement.getName();
        if (!XFormsActions.isActionName(actionNamespaceURI, actionName)) {
            throw new ValidationException("Invalid action: " + XMLUtils.buildExplodedQName(actionNamespaceURI, actionName),
                    new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                            "action name", XMLUtils.buildExplodedQName(actionNamespaceURI, actionName)));
        }

        try {
            // Get action scope
            final XBLBindings.Scope actionScope = getActionScope(actionElement);

            // Extract conditional action (@if / @exf:if)
            final String ifConditionAttribute;
            {
                final String ifAttribute = actionElement.attributeValue("if");
                if (ifAttribute != null)
                    ifConditionAttribute = ifAttribute;
                else
                    ifConditionAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_IF_ATTRIBUTE_QNAME);
            }

            // Extract iterated action (@while / @exf:while)
            final String whileIterationAttribute;
            {
                final String whileAttribute = actionElement.attributeValue("while");
                if (whileAttribute != null)
                    whileIterationAttribute = whileAttribute;
                else
                    whileIterationAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_WHILE_ATTRIBUTE_QNAME);
            }

            // Extract iterated action (@xxforms:iterate / @exf:iterate)
            final String iterateIterationAttribute;
            {
                final String xxformsIterateAttribute = actionElement.attributeValue(XFormsConstants.XXFORMS_ITERATE_ATTRIBUTE_QNAME);
                if (xxformsIterateAttribute != null)
                    iterateIterationAttribute = xxformsIterateAttribute;
                else
                    iterateIterationAttribute = actionElement.attributeValue(XFormsConstants.EXFORMS_ITERATE_ATTRIBUTE_QNAME);
            }

            // NOTE: At this point, the context has already been set to the current action element
            if (iterateIterationAttribute != null) {
                // Gotta iterate

                // We have to restore the context to the in-scope evaluation context, then push @model/@context/@iterate
                // NOTE: It's not 100% how @context and @xxforms:iterate should interact here. Right now @xxforms:iterate overrides @context,
                // i.e. @context is evaluated first, and @xxforms:iterate sets a new context for each iteration
                final XFormsContextStack.BindingContext actionBindingContext = actionBlockContextStack.popBinding();
                final NamespaceMapping namespaceMapping = container.getNamespaceMappings(actionElement);
                {
                    final String contextAttribute = actionElement.attributeValue(XFormsConstants.CONTEXT_QNAME);
                    final String modelAttribute = actionElement.attributeValue(XFormsConstants.MODEL_QNAME);
                    // TODO: function context
                    actionBlockContextStack.pushBinding(null, contextAttribute, iterateIterationAttribute, modelAttribute, null, actionElement, namespaceMapping, getSourceEffectiveId(actionElement), actionScope);
                }
                {
                    final String refAttribute = actionElement.attributeValue(XFormsConstants.REF_QNAME);
                    final String nodesetAttribute = actionElement.attributeValue(XFormsConstants.NODESET_QNAME);
                    final String bindAttribute = actionElement.attributeValue(XFormsConstants.BIND_QNAME);

                    final List<Item> currentNodeset = actionBlockContextStack.getCurrentNodeset();
                    final int iterationCount = currentNodeset.size();
                    for (int index = 1; index <= iterationCount; index++) {

                        // Push iteration
                        actionBlockContextStack.pushIteration(index);

                        // Then we also need to push back binding attributes, excluding @context and @model
                        // TODO: function context
                        actionBlockContextStack.pushBinding(refAttribute, null, nodesetAttribute, null, bindAttribute, actionElement, namespaceMapping, getSourceEffectiveId(actionElement), actionScope);

                        final Item overriddenContextNodeInfo = currentNodeset.get(index - 1);
                        runSingleIteration(event, eventObserver, actionElement, actionNamespaceURI,
                                actionName, actionScope, ifConditionAttribute, whileIterationAttribute, true, overriddenContextNodeInfo);

                        // Restore context
                        actionBlockContextStack.popBinding();
                        actionBlockContextStack.popBinding();
                    }

                }
                // Restore context stack
                actionBlockContextStack.popBinding();
                actionBlockContextStack.restoreBinding(actionBindingContext);
            } else {
                // Do a single iteration run (but this may repeat over the @while condition!)

                runSingleIteration(event, eventObserver, actionElement, actionNamespaceURI,
                        actionName, actionScope, ifConditionAttribute, whileIterationAttribute, actionBlockContextStack.hasOverriddenContext(), actionBlockContextStack.getContextItem());
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                    "action name", XMLUtils.buildExplodedQName(actionNamespaceURI, actionName)));
        }
    }

    private void runSingleIteration(XFormsEvent event, XFormsEventObserver eventObserver,
                                    Element actionElement, String actionNamespaceURI, String actionName, XBLBindings.Scope actionScope,
                                    String ifConditionAttribute, String whileIterationAttribute, boolean hasOverriddenContext, Item contextItem) {

        // The context is now the overridden context
        int whileIteration = 1;
        while (true) {
            // Check if the conditionAttribute attribute exists and stop if false
            if (ifConditionAttribute != null) {
                boolean result = evaluateCondition(actionElement, actionName, ifConditionAttribute, "if", contextItem);
                if (!result)
                    break;
            }
            // Check if the iterationAttribute attribute exists and stop if false
            if (whileIterationAttribute != null) {
                boolean result = evaluateCondition(actionElement, actionName, whileIterationAttribute, "while", contextItem);
                if (!result)
                    break;
            }

            // We are executing the action
            if (indentedLogger.isDebugEnabled()) {
                if (whileIterationAttribute == null)
                    indentedLogger.startHandleOperation("interpreter", "executing", "action name", actionName);
                else
                    indentedLogger.startHandleOperation("interpreter", "executing", "action name", actionName, "while iteration", Integer.toString(whileIteration));
            }

            // Get action and execute it
            final XFormsAction xformsAction = XFormsActions.getAction(actionNamespaceURI, actionName);
            xformsAction.execute(this, event, eventObserver, actionElement, actionScope, hasOverriddenContext, contextItem);

            if (indentedLogger.isDebugEnabled()) {
                if (whileIterationAttribute == null)
                    indentedLogger.endHandleOperation("action name", actionName);
                else
                    indentedLogger.endHandleOperation("action name", actionName, "while iteration", Integer.toString(whileIteration));
            }

            // Stop if there is no iteration
            if (whileIterationAttribute == null)
                break;

            // If we repeat, we must re-evaluate the action binding.
            // For example:
            //   <xforms:delete nodeset="/*/foo[1]" while="/*/foo"/>
            // In that case, in the second iteration, xforms:repeat must find an up-to-date nodeset
            // NOTE: There is still the possibility that parent bindings will be out of date. What should be done there?
            actionBlockContextStack.popBinding();
            actionBlockContextStack.pushBinding(actionElement, getSourceEffectiveId(actionElement), actionScope);

            whileIteration++;
        }
    }

    private boolean evaluateCondition(Element actionElement,
                                      String actionName, String conditionAttribute, String conditionType,
                                      Item contextItem) {

        // Execute condition relative to the overridden context if it exists, or the in-scope context if not
        final List<Item> contextNodeset;
        final int contextPosition;
        {
            if (contextItem != null) {
                // Use provided context item
                contextNodeset = Collections.singletonList(contextItem);
                contextPosition = 1;
            } else {
                // Use empty context
                contextNodeset = XFormsConstants.EMPTY_ITEM_LIST;
                contextPosition = 0;
            }
        }

        // Don't evaluate the condition if the context has gone missing
        {
            if (contextNodeset.size() == 0) {//  || containingDocument.getInstanceForNode((NodeInfo) contextNodeset.get(contextPosition - 1)) == null
                if (indentedLogger.isDebugEnabled())
                    indentedLogger.logDebug("interpreter", "not executing", "action name", actionName, "condition type", conditionType, "reason", "missing context");
                return false;
            }
        }

        final List conditionResult = evaluateExpression(actionElement,
                contextNodeset, contextPosition, "boolean(" + conditionAttribute + ")");
        if (!(Boolean) conditionResult.get(0)) {
            // Don't execute action

            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("interpreter", "not executing", "action name", actionName, "condition type", conditionType, "reason", "condition evaluated to 'false'", "condition", conditionAttribute);

            return false;
        } else {
            // Condition is true
            return true;
        }
    }

    /**
     * Return the source against which id resolutions are made for the given action element.
     *
     * @param   actionElement           action element to resolve
     * @return  effective id of source
     */
    public String getSourceEffectiveId(Element actionElement) {
        return XFormsUtils.getRelatedEffectiveId(outerActionElementEffectiveId, getActionStaticId(actionElement));
    }

    public String evaluateStringExpression(Element actionElement,
                                           List<Item> nodeset, int position, String xpathExpression) {

        // Setup function context
        final XFormsFunction.Context functionContext = actionBlockContextStack.getFunctionContext(getSourceEffectiveId(actionElement));

        // @ref points to something
        final String result = XPathCache.evaluateAsString(
                nodeset, position,
            xpathExpression, getNamespaceMappings(actionElement), actionBlockContextStack.getCurrentVariables(),
            XFormsContainingDocument.getFunctionLibrary(), functionContext, null,
            (LocationData) actionElement.getData());

        // Restore function context
        actionBlockContextStack.returnFunctionContext();

        return result;
    }

    public List evaluateExpression(Element actionElement,
                                   List<Item> nodeset, int position, String xpathExpression) {


        // Setup function context
        final XFormsFunction.Context functionContext = actionBlockContextStack.getFunctionContext(getSourceEffectiveId(actionElement));

        // @ref points to something
        final List result = XPathCache.evaluate(
                nodeset, position,
            xpathExpression, getNamespaceMappings(actionElement), actionBlockContextStack.getCurrentVariables(),
            XFormsContainingDocument.getFunctionLibrary(), functionContext, null,
            (LocationData) actionElement.getData());

        // Restore function context
        actionBlockContextStack.returnFunctionContext();

        return result;
    }

    /**
     * Resolve a value which may be an AVT.
     *
     * @param actionElement     action element
     * @param attributeValue    raw value to resolve
     * @return                  resolved attribute value
     */
    public String resolveAVTProvideValue(Element actionElement, String attributeValue) {

        if (attributeValue == null)
            return null;

        // Whether this can't be an AVT
        final String resolvedAVTValue;
        if (XFormsUtils.maybeAVT(attributeValue)) {
            // We have to go through AVT evaluation
            final XFormsContextStack.BindingContext bindingContext = actionBlockContextStack.getCurrentBindingContext();

            // We don't have an evaluation context so return
            // CHECK: In the future we want to allow an empty evaluation context so do we really want this check?
            if (bindingContext.getSingleItem() == null)
                return null;

            final NamespaceMapping namespaceMapping = getNamespaceMappings(actionElement);
            final LocationData locationData = (LocationData) actionElement.getData();

            // Setup function context
            final XFormsFunction.Context functionContext = actionBlockContextStack.getFunctionContext(getSourceEffectiveId(actionElement));

            resolvedAVTValue = XFormsUtils.resolveAttributeValueTemplates(bindingContext.getNodeset(),
                        bindingContext.getPosition(), actionBlockContextStack.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                        functionContext, namespaceMapping, locationData, attributeValue);

            // Restore function context
            actionBlockContextStack.returnFunctionContext();
        } else {
            // We optimize as this doesn't need AVT evaluation
            resolvedAVTValue = attributeValue;
        }

        return resolvedAVTValue;
    }

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     *
     * @param actionElement     action element
     * @param attributeName     QName of the attribute containing the value
     * @return                  resolved attribute value
     */
    public String resolveAVT(Element actionElement, QName attributeName) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionElement, attributeValue);
    }

    /**
     * Resolve the value of an attribute which may be an AVT.
     *
     * @param actionElement     action element
     * @param attributeName     name of the attribute containing the value
     * @return                  resolved attribute value
     */
    public String resolveAVT(Element actionElement, String attributeName) {
        // Get raw attribute value
        final String attributeValue = actionElement.attributeValue(attributeName);
        if (attributeValue == null)
            return null;

        return resolveAVTProvideValue(actionElement, attributeValue);
    }

    /**
     * Find an effective object based on either the xxforms:repeat-indexes attribute, or on the current repeat indexes.
     *
     * @param actionElement         current action element
     * @param targetStaticId        static id of the target to resolve
     * @return                      effective control if found
     */
    public Object resolveEffectiveControl(Element actionElement, String targetStaticId) {

        final XBLContainer resolutionScopeContainer = findResolutionScopeContainer(actionElement);

        // Get indexes as space-separated list
        final String repeatIndexes = resolveAVT(actionElement, XFormsConstants.XXFORMS_REPEAT_INDEXES_QNAME);
        if (StringUtils.isBlank(repeatIndexes)) {
            // Most common case: resolve effective id based on source and target
            return resolutionScopeContainer.resolveObjectById(getSourceEffectiveId(actionElement), targetStaticId, null);
        } else {
            // Extension: effective id is provided through repeat indexes, modify appropriately and directly reach control
            final Integer[] containerParts = XFormsUtils.getEffectiveIdSuffixParts(resolutionScopeContainer.getEffectiveId());
            final String[] additionalParts = StringUtils.split(repeatIndexes);

            final String[] parts = new String[containerParts.length + additionalParts.length];
            for (int i = 0; i < containerParts.length; i++) {
                parts[i] = Integer.toString(containerParts[i]);
            }
            System.arraycopy(additionalParts, 0, parts, containerParts.length, additionalParts.length);

            return xformsControls.getObjectByEffectiveId(resolutionScopeContainer.getFullPrefix() + targetStaticId + XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 + StringUtils.join(parts, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2));
        }
    }

    public String getActionPrefixedId(Element actionElement) {
        return container.getFullPrefix() + getActionStaticId(actionElement);
    }

    public String getActionStaticId(Element actionElement) {
        assert actionElement.attributeValue(XFormsConstants.ID_QNAME) != null;
        return actionElement.attributeValue(XFormsConstants.ID_QNAME);
    }

    public XBLBindings.Scope getActionScope(Element actionElement) {
        return containingDocument.getStaticState().getXBLBindings().getResolutionScopeByPrefixedId(getActionPrefixedId(actionElement));
    }

    private XBLContainer findResolutionScopeContainer(Element actionElement) {
        return container.findResolutionScope(getActionPrefixedId(actionElement));
    }

    /**
     * Resolve an effective object.
     *
     * @param actionElement     current action element
     * @param targetStaticId    target to resolve
     * @return                  effective control if found
     */
    public Object resolveEffectiveObject(Element actionElement, String targetStaticId) {
        // First try controls as we want to check on explicit repeat indexes first
        final Object tempXFormsEventTarget = resolveEffectiveControl(actionElement, targetStaticId);
        if (tempXFormsEventTarget != null) {
            // Object with this id exists
            return tempXFormsEventTarget;
        } else {
            // Otherwise, try container
            final XBLContainer resolutionScopeContainer = findResolutionScopeContainer(actionElement);
            return resolutionScopeContainer.resolveObjectById(getSourceEffectiveId(actionElement), targetStaticId, null);
        }
    }

    /**
     * Search a model given a static id and/or the current action element.
     *
     * @param actionElement     current action element
     * @param modelStaticId     static id of the model searched, or null if current model
     * @return                  model
     */
    public XFormsModel resolveModel(Element actionElement, String modelStaticId) {
        final XFormsModel model;
        if (modelStaticId != null) {
            // Id is specified, resolve the effective object
            final Object o = resolveEffectiveObject(actionElement, modelStaticId);
            if (!(o instanceof XFormsModel))
                throw new ValidationException("Invalid model id: " + modelStaticId, (LocationData) actionElement.getData());
            model = (XFormsModel) o;
        } else {
            // Id is not specified
            model = actionBlockContextStack.getCurrentModel();
        }
        if (model == null)
            throw new ValidationException("Invalid model id: " + modelStaticId, (LocationData) actionElement.getData());
        return model;
    }
}