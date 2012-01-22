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
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.ElementAnalysis;
import org.orbeon.oxf.xforms.event.XFormsEvent;
import org.orbeon.oxf.xforms.event.XFormsEventObserver;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.Scope;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.om.Item;
import scala.Option;

import java.util.Collections;
import java.util.List;

/**
 * Execute a top-level XForms action and the included nested actions if any.
 */
public class XFormsActionInterpreter {

    private final IndentedLogger _indentedLogger;

    private final XBLContainer _container;
    private final XFormsContainingDocument _containingDocument;

    private final XFormsControls xformsControls;

    private final XFormsContextStack _actionXPathContext;

    public final Element outerActionElement;
    private final String handlerEffectiveId;

    public final XFormsEvent event;
    public final XFormsEventObserver eventObserver;

    public XFormsActionInterpreter(XBLContainer container, XFormsContextStack actionXPathContext, Element outerActionElement,
                                   String handlerEffectiveId, XFormsEvent event, XFormsEventObserver eventObserver) {

        this._container = container;
        this._containingDocument = container.getContainingDocument();

        this._indentedLogger = _containingDocument.getIndentedLogger(XFormsActions.LOGGING_CATEGORY());

        this.xformsControls = _containingDocument.getControls();

        this._actionXPathContext = actionXPathContext;
        this.outerActionElement = outerActionElement;
        this.handlerEffectiveId = handlerEffectiveId;

        this.event = event;
        this.eventObserver = eventObserver;
    }

    public IndentedLogger indentedLogger() {
        return _indentedLogger;
    }

    public XBLContainer container() {
        return _container;
    }

    public XFormsContainingDocument containingDocument() {
        return _containingDocument;
    }

    public XFormsContextStack actionXPathContext() {
        return _actionXPathContext;
    }

    /**
     * Return the namespace mappings for the given action element.
     *
     * @param actionElement element to get namespace mapping for
     * @return              mapping
     */
    public NamespaceMapping getNamespaceMappings(Element actionElement) {
        return _container.getNamespaceMappings(actionElement);
    }

    /**
     * Execute an XForms action.
     */
    public void runAction(ElementAnalysis actionAnalysis) {

        final Element actionElement = actionAnalysis.element();
        
        // Check that we understand the action element
        final QName actionQName = actionElement.getQName();
        if (!XFormsActions.isAction(actionQName)) {
            throw new ValidationException("Invalid action: " + actionQName.getQualifiedName(),
                    new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                            "action name", actionQName.getQualifiedName()));
        }

        try {

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
                final XFormsContextStack.BindingContext actionBindingContext = _actionXPathContext.popBinding();
                final NamespaceMapping namespaceMapping = _container.getNamespaceMappings(actionElement);
                {
                    final String contextAttribute = actionElement.attributeValue(XFormsConstants.CONTEXT_QNAME);
                    final String modelAttribute = actionElement.attributeValue(XFormsConstants.MODEL_QNAME);
                    // TODO: function context
                    _actionXPathContext.pushBinding(null, contextAttribute, iterateIterationAttribute, modelAttribute, null, actionElement, namespaceMapping, getSourceEffectiveId(actionElement), actionAnalysis.scope(), false);
                }
                {
                    final String refAttribute = actionElement.attributeValue(XFormsConstants.REF_QNAME);
                    final String nodesetAttribute = actionElement.attributeValue(XFormsConstants.NODESET_QNAME);
                    final String bindAttribute = actionElement.attributeValue(XFormsConstants.BIND_QNAME);

                    final List<Item> currentNodeset = _actionXPathContext.getCurrentNodeset();
                    final int iterationCount = currentNodeset.size();
                    for (int index = 1; index <= iterationCount; index++) {

                        // Push iteration
                        _actionXPathContext.pushIteration(index);

                        // Then we also need to push back binding attributes, excluding @context and @model
                        // TODO: function context
                        _actionXPathContext.pushBinding(refAttribute, null, nodesetAttribute, null, bindAttribute, actionElement, namespaceMapping, getSourceEffectiveId(actionElement), actionAnalysis.scope(), false);

                        final Item overriddenContextNodeInfo = currentNodeset.get(index - 1);
                        runSingleIteration(actionAnalysis, actionQName,
                                ifConditionAttribute, whileIterationAttribute, true, overriddenContextNodeInfo);

                        // Restore context
                        _actionXPathContext.popBinding();
                        _actionXPathContext.popBinding();
                    }

                }
                // Restore context stack
                _actionXPathContext.popBinding();
                _actionXPathContext.restoreBinding(actionBindingContext);
            } else {
                // Do a single iteration run (but this may repeat over the @while condition!)

                runSingleIteration(actionAnalysis, actionQName,
                        ifConditionAttribute, whileIterationAttribute, _actionXPathContext.hasOverriddenContext(), _actionXPathContext.getContextItem());
            }
        } catch (Exception e) {
            throw ValidationException.wrapException(e, new ExtendedLocationData((LocationData) actionElement.getData(), "running XForms action", actionElement,
                    "action name", actionQName.getQualifiedName()));
        }
    }

    private void runSingleIteration(ElementAnalysis actionAnalysis, QName actionQName,
                                    String ifConditionAttribute, String whileIterationAttribute, boolean hasOverriddenContext, Item contextItem) {

        // The context is now the overridden context
        int whileIteration = 1;
        while (true) {
            // Check if the conditionAttribute attribute exists and stop if false
            if (ifConditionAttribute != null) {
                boolean result = evaluateCondition(actionAnalysis.element(), actionQName.getQualifiedName(), ifConditionAttribute, "if", contextItem);
                if (!result)
                    break;
            }
            // Check if the iterationAttribute attribute exists and stop if false
            if (whileIterationAttribute != null) {
                boolean result = evaluateCondition(actionAnalysis.element(), actionQName.getQualifiedName(), whileIterationAttribute, "while", contextItem);
                if (!result)
                    break;
            }

            // We are executing the action
            if (_indentedLogger.isDebugEnabled()) {
                if (whileIterationAttribute == null)
                    _indentedLogger.startHandleOperation("interpreter", "executing", "action name", actionQName.getQualifiedName());
                else
                    _indentedLogger.startHandleOperation("interpreter", "executing", "action name", actionQName.getQualifiedName(), "while iteration", Integer.toString(whileIteration));
            }

            // Get action and execute it
            final DynamicActionContext dynamicActionContext =
                    new DynamicActionContext(this, actionAnalysis, hasOverriddenContext ? Option.apply(contextItem) : Option.apply((Item) null));
            
            XFormsActions.getAction(actionQName).execute(dynamicActionContext);

            if (_indentedLogger.isDebugEnabled()) {
                if (whileIterationAttribute == null)
                    _indentedLogger.endHandleOperation("action name", actionQName.getQualifiedName());
                else
                    _indentedLogger.endHandleOperation("action name", actionQName.getQualifiedName(), "while iteration", Integer.toString(whileIteration));
            }

            // Stop if there is no iteration
            if (whileIterationAttribute == null)
                break;

            // If we repeat, we must re-evaluate the action binding.
            // For example:
            //   <xforms:delete nodeset="/*/foo[1]" while="/*/foo"/>
            // In that case, in the second iteration, xforms:repeat must find an up-to-date nodeset
            // NOTE: There is still the possibility that parent bindings will be out of date. What should be done there?
            _actionXPathContext.popBinding();
            _actionXPathContext.pushBinding(actionAnalysis.element(), getSourceEffectiveId(actionAnalysis.element()), actionAnalysis.scope(), false);

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
                if (_indentedLogger.isDebugEnabled())
                    _indentedLogger.logDebug("interpreter", "not executing", "action name", actionName, "condition type", conditionType, "reason", "missing context");
                return false;
            }
        }

        final List conditionResult = evaluateExpression(actionElement,
                contextNodeset, contextPosition, "boolean(" + conditionAttribute + ")");
        if (!(Boolean) conditionResult.get(0)) {
            // Don't execute action

            if (_indentedLogger.isDebugEnabled())
                _indentedLogger.logDebug("interpreter", "not executing", "action name", actionName, "condition type", conditionType, "reason", "condition evaluated to 'false'", "condition", conditionAttribute);

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
        return XFormsUtils.getRelatedEffectiveId(handlerEffectiveId, getActionStaticId(actionElement));
    }

    /**
     * Evaluate an expression as a string. This returns "" if the result is an empty sequence.
     */
    public String evaluateStringExpression(Element actionElement,
                                           List<Item> nodeset, int position, String xpathExpression) {

        // Setup function context
        final XFormsFunction.Context functionContext = _actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement));

        // @ref points to something
        final String result = XPathCache.evaluateAsString(
                nodeset, position,
                xpathExpression, getNamespaceMappings(actionElement), _actionXPathContext.getCurrentVariables(),
                XFormsContainingDocument.getFunctionLibrary(), functionContext, null,
                (LocationData) actionElement.getData());

        // Restore function context
        _actionXPathContext.returnFunctionContext();

        return result != null ? result : "";
    }

    public List evaluateExpression(Element actionElement,
                                   List<Item> nodeset, int position, String xpathExpression) {


        // Setup function context
        final XFormsFunction.Context functionContext = _actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement));

        // @ref points to something
        final List result = XPathCache.evaluate(
                nodeset, position,
                xpathExpression, getNamespaceMappings(actionElement), _actionXPathContext.getCurrentVariables(),
                XFormsContainingDocument.getFunctionLibrary(), functionContext, null,
                (LocationData) actionElement.getData());

        // Restore function context
        _actionXPathContext.returnFunctionContext();

        return result;
    }

    /**
     * Resolve a value which may be an AVT.
     *
     * @param actionElement     action element
     * @param attributeValue    raw value to resolve
     * @return                  resolved attribute value, null if the value is null or if the XPath context item is missing
     */
    public String resolveAVTProvideValue(Element actionElement, String attributeValue) {

        if (attributeValue == null)
            return null;

        // Whether this can't be an AVT
        final String resolvedAVTValue;
        if (XFormsUtils.maybeAVT(attributeValue)) {
            // We have to go through AVT evaluation
            final XFormsContextStack.BindingContext bindingContext = _actionXPathContext.getCurrentBindingContext();

            // We don't have an evaluation context so return
            // CHECK: In the future we want to allow an empty evaluation context so do we really want this check?
            if (bindingContext.getSingleItem() == null)
                return null;

            final NamespaceMapping namespaceMapping = getNamespaceMappings(actionElement);
            final LocationData locationData = (LocationData) actionElement.getData();

            // Setup function context
            final XFormsFunction.Context functionContext = _actionXPathContext.getFunctionContext(getSourceEffectiveId(actionElement));

            resolvedAVTValue = XFormsUtils.resolveAttributeValueTemplates(bindingContext.getNodeset(),
                        bindingContext.getPosition(), _actionXPathContext.getCurrentVariables(), XFormsContainingDocument.getFunctionLibrary(),
                        functionContext, namespaceMapping, locationData, attributeValue);

            // Restore function context
            _actionXPathContext.returnFunctionContext();
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

            return
                xformsControls.getObjectByEffectiveId(resolutionScopeContainer.getFullPrefix() +
                targetStaticId +
                XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1 +
                StringUtils.join(parts, XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_2));
        }
    }

    public String getActionPrefixedId(Element actionElement) {
        return _container.getFullPrefix() + getActionStaticId(actionElement);
    }

    public String getActionStaticId(Element actionElement) {
        assert actionElement.attributeValue(XFormsConstants.ID_QNAME) != null;
        return XFormsUtils.getElementStaticId(actionElement);
    }

    public Scope getActionScope(Element actionElement) {
        return _container.getPartAnalysis().getResolutionScopeByPrefixedId(getActionPrefixedId(actionElement));
    }

    private XBLContainer findResolutionScopeContainer(Element actionElement) {
        return _container.findResolutionScope(getActionPrefixedId(actionElement));
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
            model = _actionXPathContext.getCurrentModel();
        }
        if (model == null)
            throw new ValidationException("Invalid model id: " + modelStaticId, (LocationData) actionElement.getData());
        return model;
    }

    /**
     * Resolve an object by effective id if the id is not a static id, otherwise try to resolve by static id.
     */
    public Object resolveOrFindByEffectiveId(Element actionElement, String staticOrEffectiveId) {
        if (staticOrEffectiveId.indexOf(XFormsConstants.COMPONENT_SEPARATOR) != -1
            || staticOrEffectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1) {
            // We allow the use of effective ids so that e.g. a global component such as the error summary can target a specific component
            return _containingDocument.getObjectByEffectiveId(staticOrEffectiveId);
        } else {
            return resolveEffectiveObject(actionElement, staticOrEffectiveId);
        }
    }
}
