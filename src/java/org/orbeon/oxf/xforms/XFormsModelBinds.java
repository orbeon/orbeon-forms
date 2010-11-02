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

import org.apache.commons.collections.map.CompositeMap;
import org.dom4j.*;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.*;
import org.orbeon.oxf.xforms.action.actions.XFormsSetvalueAction;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.event.events.XFormsComputeExceptionEvent;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.*;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.*;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.*;
import org.orbeon.saxon.sxpath.IndependentContext;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.*;
import org.orbeon.saxon.value.*;
import org.orbeon.saxon.value.StringValue;

import java.util.*;

/**
 * Represent a given model's binds.
 */
public class XFormsModelBinds {
    
    private final XFormsModel model;                            // model to which we belong
    private final Model staticModel;

    private final IndentedLogger indentedLogger;
    private final XBLContainer container;
    private final XFormsContainingDocument containingDocument;  // current containing document
    private final XPathDependencies dependencies;

    private List<Bind> topLevelBinds = new ArrayList<Bind>();
    private Map<String, Bind> singleNodeContextBinds = new HashMap<String, Bind>();
    private Map<Item, List<BindIteration>> iterationsForContextNodeInfo = new HashMap<Item, List<BindIteration>>();
    private List<Bind> offlineBinds = new ArrayList<Bind>();

    private XFormsModelSchemaValidator xformsValidator;         // validator for standard XForms schema types

    private boolean isFirstCalculate;                           // whether this is the first recalculate for the associated XForms model

    private static final Set<String> BUILTIN_XFORMS_SCHEMA_TYPES = new HashSet<String>();

    static {
        BUILTIN_XFORMS_SCHEMA_TYPES.add("dayTimeDuration");
        BUILTIN_XFORMS_SCHEMA_TYPES.add("yearMonthDuration");
        BUILTIN_XFORMS_SCHEMA_TYPES.add("email");
        BUILTIN_XFORMS_SCHEMA_TYPES.add("card-number");
    }

    /**
     * Create an instance of XFormsModelBinds if the given model has xforms:bind elements.
     *
     * @param model XFormsModel
     * @return      XFormsModelBinds or null if the model doesn't have xforms:bind elements
     */
    public static XFormsModelBinds create(XFormsModel model) {
        return model.getStaticModel().hasBinds() ? new XFormsModelBinds(model) : null;
    }

    private XFormsModelBinds(XFormsModel model) {
        this.model = model;

        this.indentedLogger = model.getIndentedLogger();
        this.container = model.getXBLContainer();
        this.containingDocument = model.getContainingDocument();
        this.dependencies = this.containingDocument.getXPathDependencies();

        this.staticModel = model.getStaticModel();

        // For the lifecycle of an XForms document, new XFormsModelBinds() may be created multiple times, e.g. if the
        // state is deserialized, but we know that new XFormsModelBinds() will occur only once during document
        // initialization. So the assignation below is ok.
        this.isFirstCalculate = containingDocument.isInitializing();
    }

    /**
     * Rebuild all binds, computing all bind nodesets (but not computing the MIPs)
     *
     * @param propertyContext   current context
     */
    public void rebuild(PropertyContext propertyContext) {

        if (indentedLogger.isDebugEnabled())
            indentedLogger.startHandleOperation("model", "performing rebuild", "model id", model.getEffectiveId());

        // Reset everything
        model.getContextStack().resetBindingContext(propertyContext, model);
        topLevelBinds.clear();
        singleNodeContextBinds.clear();
        iterationsForContextNodeInfo.clear();
        offlineBinds.clear();

        // Iterate through all top-level bind elements
        for (final Element currentBindElement: staticModel.bindElements()) {
            // Create and remember as top-level bind
            final Bind currentBind = new Bind(propertyContext, currentBindElement, true);
            topLevelBinds.add(currentBind);
        }

        // Clear state
        final List<XFormsInstance> instances = model.getInstances();
        if (instances != null) {
            for (final XFormsInstance instance: instances) {
                // Only clear instances that are impacted by xf:bind/(@ref|@nodeset), assuming we were able to figure out the dependencies
                // The reason is that clearing this state can take quite some time
                if (dependencies.hasAnyCalculationBind(staticModel, instance.getPrefixedId())) {
                    XFormsUtils.iterateInstanceData(instance, new XFormsUtils.InstanceWalker() {
                        public void walk(NodeInfo nodeInfo) {
                            InstanceData.clearOtherState(nodeInfo);
                        }
                    }, true);
                }

                // If a schema is in use, we don't know at this point if it will touch a particular instance unless the
                // instance excludes validation, either because it is readonly or because it is marked as "skip".
                // If there is no schema, then we clear the instance only if some binds are known to touch this instance
                // or we couldn't figure out dependencies.
//                final boolean mustSchemaValidateInstance = model.isMustSchemaValidate() && instance.isSchemaValidation();
//                if (mustSchemaValidateInstance || dependencies.requireBindValidation(staticModel, instance.getPrefixedId())) {
//                    XFormsUtils.iterateInstanceData(instance, new XFormsUtils.InstanceWalker() {
//                        public void walk(NodeInfo nodeInfo) {
//                            InstanceData.clearValidationState(nodeInfo);
//                        }
//                    }, true);
//                }
            }
        }

        if (indentedLogger.isDebugEnabled())
            indentedLogger.endHandleOperation();
    }

    /**
     * Apply calculate binds.
     *
     * @param propertyContext       current context
     * @param applyInitialValues    whether to apply initial values (@xxforms:default="...")
     */
    public void applyCalculateBinds(final PropertyContext propertyContext, boolean applyInitialValues) {

        if (!dependencies.hasAnyCalculationBind(staticModel)) {
            // Dependencies say we can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping recalculate", "model id", model.getEffectiveId(), "reason", "no recalculation binds");
        } else {
            // This model may have calculation binds

            if (indentedLogger.isDebugEnabled())
                indentedLogger.startHandleOperation("model", "performing recalculate", "model id", model.getEffectiveId());

    //        PROFILING
    //        final int iterations = Properties.instance().getPropertySet().getInteger("oxf.xforms.profiling.iterations", 1);
    //        for (int i = 0; i < iterations; i++)
            {

                // Reset context stack just to re-evaluate the variables
                model.getContextStack().resetBindingContext(propertyContext, model);

                if (isFirstCalculate || applyInitialValues) {
                    // Handle default values first
                    iterateBinds(propertyContext, new BindRunner() {
                        public void applyBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
                            if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.INITIAL_VALUE()))
                                handleInitialValueDefaultBind(propertyContext, bind, nodeset, position);
                        }
                    });
                    // This will be false from now on as we have done our first handling of calculate binds
                    isFirstCalculate = false;
                }

                // Handle calculations
                // NOTE: we do not correctly handle computational dependencies, but it helps to evaluate "calculate"
                // binds before the rest.
                iterateBinds(propertyContext, new BindRunner() {
                    public void applyBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
                        if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.CALCULATE()))
                            handleCalculateBind(propertyContext, bind, nodeset, position);
                    }
                });

                // Update computed expression binds if requested (done here according to XForms 1.1)
                applyComputedExpressionBinds(propertyContext);
            }

            if (indentedLogger.isDebugEnabled())
                indentedLogger.endHandleOperation();
        }
    }

    /**
     * Apply required, relevant and readonly binds.
     *
     * @param propertyContext   current PropertyContext
     */
    public void applyComputedExpressionBinds(final PropertyContext propertyContext) {

        // Reset context stack just to re-evaluate the variables as instance values may have changed with @calculate
        model.getContextStack().resetBindingContext(propertyContext, model);

        // Apply
        final List<XFormsInstance> instances = model.getInstances();
        if (instances != null) {
            iterateBinds(propertyContext, new BindRunner() {
                public void applyBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
                    handleComputedExpressionBind(propertyContext, bind, nodeset, position);
                }
            });
        }
    }

    /**
     * Apply validation binds
     *
     * @param propertyContext   current PropertyContext
     * @param invalidInstances  will contain a
     */
    public void applyValidationBinds(final PropertyContext propertyContext, final Set<String> invalidInstances) {

        if (!dependencies.hasAnyValidationBind(staticModel)) {
            // Dependencies say we can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping bind revalidate", "model id", model.getEffectiveId(), "reason", "no validation binds");
        } else {
            // This model may have validation binds

            // Reset context stack just to re-evaluate the variables
            model.getContextStack().resetBindingContext(propertyContext, model);

            // Apply
            iterateBinds(propertyContext, new BindRunner() {
                public void applyBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
                    handleValidationBind(propertyContext, bind, nodeset, position, invalidInstances);
                }
            });
        }
    }

    /**
     * Return the nodeset for a given bind and context item, as per "4.7.2 References to Elements within a bind
     * Element".
     *
     * @param bindId        id of the bind to handle
     * @param contextItem   context item if necessary
     * @return              bind nodeset
     */
    public List<Item> getBindNodeset(String bindId, Item contextItem) {

        final Bind bind = resolveBind(bindId, contextItem);
        return (bind != null) ? bind.getNodeset() : XFormsConstants.EMPTY_ITEM_LIST;
    }

    public Bind resolveBind(String bindId, Item contextItem) {

        final Bind singleNodeContextBind = singleNodeContextBinds.get(bindId);
        if (singleNodeContextBind != null) {
            // This bind has a single-node context (incl. top-level bind), so ignore context item and just return the bind nodeset
            return singleNodeContextBind;
        } else {
            // Nested bind, context item will be used

            // This requires a context node, not just any item
            if (contextItem instanceof NodeInfo) {
                final List<BindIteration> iterationsForContextNode = iterationsForContextNodeInfo.get(contextItem);
                if (iterationsForContextNode != null) {
                    for (final BindIteration currentIteration: iterationsForContextNode) {
                        final Bind currentBind = currentIteration.getBind(bindId);
                        if (currentBind != null) {
                            // Found
                            return currentBind;
                        }
                    }
                }
            }
            // "From among the bind objects associated with the target bind element, if there exists a bind object
            // created with the same in-scope evaluation context node as the source object, then that bind object is the
            // desired target bind object. Otherwise, the IDREF resolution produced a null search result."
        }

        // Nothing found
        return null;
    }

    public Item evaluateBindByType(final PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, QName mipType) throws XPathException {

        final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
        final Map<String, ValueRepresentation> currentVariables = getVariables(currentNodeInfo);

        if (mipType.equals(XFormsConstants.RELEVANT_QNAME)) {
            // Relevant
            final Boolean relevant = evaluateRelevantMIP(propertyContext, bind, nodeset, position, currentVariables);
            return (relevant != null) ? BooleanValue.get(relevant) : null;
        } else if (mipType.equals(XFormsConstants.READONLY_QNAME)) {
            // Readonly
            final Boolean readonly = evaluateReadonlyMIP(propertyContext, bind, nodeset, position, currentVariables);
            return (readonly != null) ? BooleanValue.get(readonly) : null;
        } else if (mipType.equals(XFormsConstants.REQUIRED_QNAME)) {
            // Required
            final Boolean required = evaluateRequiredMIP(propertyContext, bind, nodeset, position, currentVariables);
            return (required != null) ? BooleanValue.get(required) : null;
        } else if (mipType.equals(XFormsConstants.TYPE_QNAME)) {
            // Type
            final NamespaceMapping namespaceMapping = container.getNamespaceMappings(bind.getBindElement());
            final QName type = evaluateTypeQName(bind, namespaceMapping.mapping);
            return (type != null) ? new QNameValue(type.getNamespacePrefix(), type.getNamespaceURI(), type.getName(), null) : null;
        } else if (mipType.equals(XFormsConstants.CONSTRAINT_QNAME)) {
            // Constraint
            final Boolean constraint = evaluateConstraintMIP(propertyContext, bind, nodeset, position, currentNodeInfo);
            return (constraint != null) ? BooleanValue.get(constraint) : null;
        } else if (mipType.equals(XFormsConstants.CALCULATE_QNAME)) {
            // Calculate
            final String result = evaluateCalculateBind(propertyContext, bind, nodeset, position);
            return (result != null) ? new StringValue(result) : null;
        } else if (mipType.equals(XFormsConstants.XXFORMS_DEFAULT_QNAME)) {
            // xxforms:default
            final String result = evaluateXXFormsDefaultBind(propertyContext, bind, nodeset, position);
            return (result != null) ? new StringValue(result) : null;
        } else {
            // Try custom MIPs
            final String result = evaluateCustomMIP(propertyContext, bind, Model.buildCustomMIPName(mipType.getQualifiedName()), nodeset, position, currentVariables);
            return (result != null) ? new StringValue(result) : null;
        }
    }

    /**
     * Iterate over all binds and for each one do the callback.
     *
     * @param propertyContext   current context
     * @param bindRunner        bind runner
     */
    private void iterateBinds(PropertyContext propertyContext, BindRunner bindRunner) {
        // Iterate over top-level binds
        for (final Bind currentBind: topLevelBinds) {
            try {
                currentBind.applyBinds(propertyContext, bindRunner);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(currentBind.getLocationData(), "evaluating XForms binds", currentBind.getBindElement()));
            }
        }
    }

    private String evaluateXXFormsDefaultBind(final PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
        // Handle xxforms:default MIP
        if (bind.getXXFormsDefault() != null) {
            // Compute default value
            try {
                final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
                return evaluateStringExpression(propertyContext, nodeset, position, bind, bind.getXXFormsDefault(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms default bind",
                        bind.getBindElement(), "expression", bind.getCalculate()));

                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private void handleInitialValueDefaultBind(final PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {

        final String stringResult = evaluateXXFormsDefaultBind(propertyContext, bind, nodeset, position);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and dispatch xforms-binding-exception
            // TODO: doSetValue may dispatch an xforms-binding-exception. It should reach the bind, but we don't support that yet so pass the model.
            final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
            XFormsSetvalueAction.doSetValue(propertyContext, containingDocument, indentedLogger, model, currentNodeInfo, stringResult, null, "default", true);
        }
    }

    public void handleCalculateBind(final PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
        final String stringResult = evaluateCalculateBind(propertyContext, bind, nodeset, position);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and dispatch xforms-binding-exception
            // TODO: doSetValue may dispatch an xforms-binding-exception. It should reach the bind, but we don't support that yet so pass the model.
            final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
            XFormsSetvalueAction.doSetValue(propertyContext, containingDocument, indentedLogger, model, currentNodeInfo, stringResult, null, "calculate", true);
        }
    }

    public String evaluateCalculateBind(final PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {
        // Handle calculate MIP
        if (bind.getCalculate() != null) {
            // Compute calculated value
            try {
                final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
                return evaluateStringExpression(propertyContext, nodeset, position, bind, bind.getCalculate(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms calculate bind",
                        bind.getBindElement(), "expression", bind.getCalculate()));

                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private Map<String, ValueRepresentation> getVariables(NodeInfo contextNodeInfo) {

        if (staticModel.bindNamesToIds().size() > 0) {
            final Map<String, ValueRepresentation> bindVariablesValues = new HashMap<String, ValueRepresentation>();

            // Add bind variables
            for (Map.Entry<String, String> currentEntry : staticModel.bindNamesToIds().entrySet()) {
                final String currentVariableName = currentEntry.getKey();
                final String currentBindId = currentEntry.getValue();

                final List<Item> currentBindNodeset = getBindNodeset(currentBindId, contextNodeInfo);
                bindVariablesValues.put(currentVariableName, new SequenceExtent(currentBindNodeset));
            }

            // Combine bind variables with model variables
            return new CompositeMap(bindVariablesValues, model.getContextStack().getCurrentVariables());
        } else {
            // Just return the regular variables in scope
            return model.getContextStack().getCurrentVariables();
        }
    }

    private void handleComputedExpressionBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position) {

        final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
        final Map<String, ValueRepresentation> currentVariables = getVariables(currentNodeInfo);

        // Handle required, relevant, readonly, and custom MIPs
        if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.REQUIRED()))
            handleRequiredMIP(propertyContext, bind, nodeset, position, currentNodeInfo, currentVariables);
        if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.RELEVANT()))
            handleRelevantMIP(propertyContext, bind, nodeset, position, currentNodeInfo, currentVariables);
        if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.READONLY()))
            handleReadonlyMIP(propertyContext, bind, nodeset, position, currentNodeInfo, currentVariables);

        // TODO: optimize those as well
        handleCustomMIPs(propertyContext, bind, nodeset, position, currentNodeInfo, currentVariables);
    }

    private void handleCustomMIPs(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo, Map<String, ValueRepresentation> currentVariables) {
        final Map<String, Model.Bind.MIP> customMips = bind.getCustomMips();// NOTE: IntelliJ marks Model.Bind as an error, but it compiles fine
        if (customMips != null && customMips.size() > 0) {
            for (final String propertyName: customMips.keySet()) {
                final String stringResult = evaluateCustomMIP(propertyContext, bind, propertyName, nodeset, position, currentVariables);
                InstanceData.setCustom(currentNodeInfo, propertyName, stringResult);
            }
        }
    }

    private String evaluateCustomMIP(PropertyContext propertyContext, Bind bind, String propertyName, List<Item> nodeset, int position, Map<String, ValueRepresentation> currentVariables) {
        final Map<String, Model.Bind.MIP> customMips = bind.getCustomMips();// NOTE: IntelliJ marks Model.Bind as an error, but it compiles fine
        if (customMips != null && customMips.size() > 0) {
            final String expression = customMips.get(propertyName).expression();
            if (expression != null) {
                try {
                    return evaluateStringExpression(propertyContext, nodeset, position, bind, expression, currentVariables);
                } catch (Exception e) {
                    final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms custom bind",
                            bind.getBindElement(), "name", propertyName, "expression", expression));

                    container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                    throw new IllegalStateException(); // event above throw an exception anyway
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void handleRequiredMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo, Map<String, ValueRepresentation> currentVariables) {
        final Boolean required = evaluateRequiredMIP(propertyContext, bind, nodeset, position, currentVariables);
        if (required != null) {
            // Update node with MIP value
            InstanceData.setRequired(currentNodeInfo, required);
        }
    }

    private Boolean evaluateRequiredMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.getRequired() != null) {
            // Evaluate "required" XPath expression on this node
            try {
                // Get MIP value
                return evaluateBooleanExpression1(propertyContext, nodeset, position, bind, bind.getRequired(), currentVariables);
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms required bind",
                        bind.getBindElement(), "expression", bind.getRequired()));

                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private void handleReadonlyMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo, Map<String, ValueRepresentation> currentVariables) {
        final Boolean readonly = evaluateReadonlyMIP(propertyContext, bind, nodeset, position, currentVariables);
        if (readonly != null) {
            // Mark node
            InstanceData.setReadonly(currentNodeInfo, readonly);
        } else if (bind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            InstanceData.setReadonly(currentNodeInfo, true);
        }
//
//        final Boolean readonly = evaluateReadonlyMIP(propertyContext, bind, nodeset, position, currentVariables);
//        final boolean oldValue = InstanceData.getLocalReadonly(currentNodeInfo);
//        final boolean newValue;
//        if (readonly != null) {
//            // Set new value
//            newValue = readonly;
//        } else if (bind.getCalculate() != null) {
//            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
//            newValue = true;
//        } else {
//            // No change
//            newValue = oldValue;
//        }
//
//        if (oldValue != newValue) {
//            // Mark node
//            InstanceData.setReadonly(currentNodeInfo, newValue);
//            model.markMipChange(currentNodeInfo);
//        }
    }

    private Boolean evaluateReadonlyMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.getReadonly() != null) {
            // The bind has a readonly attribute
            // Evaluate "readonly" XPath expression on this node
            try {
                return evaluateBooleanExpression1(propertyContext, nodeset, position, bind, bind.getReadonly(), currentVariables);
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms readonly bind",
                        bind.getBindElement(), "expression", bind.getReadonly()));


                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private void handleRelevantMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo, Map<String, ValueRepresentation> currentVariables) {
        final Boolean relevant = evaluateRelevantMIP(propertyContext, bind, nodeset, position, currentVariables);
        if (relevant != null) {
            // Mark node
            InstanceData.setRelevant(currentNodeInfo, relevant);
        }
    }

    private Boolean evaluateRelevantMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.getRelevant() != null) {
            // Evaluate "relevant" XPath expression on this node
            try {
                return evaluateBooleanExpression1(propertyContext, nodeset, position, bind, bind.getRelevant(), currentVariables);
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms relevant bind",
                        bind.getBindElement(), "expression", bind.getRelevant()));

                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private String evaluateStringExpression(PropertyContext propertyContext, List<Item> nodeset, int position, Bind bind,
                                            String xpathExpression, Map<String, ValueRepresentation> currentVariables) {

        // Setup function context
         // TODO: when binds are able to receive events, source should be bind id
        final XFormsFunction.Context functionContext = model.getContextStack().getFunctionContext(model.getEffectiveId());

        final String result = XPathCache.evaluateAsString(propertyContext, nodeset, position, xpathExpression,
                        container.getNamespaceMappings(bind.getBindElement()), currentVariables,
                        XFormsContainingDocument.getFunctionLibrary(), functionContext,
                        bind.getLocationData().getSystemID(), bind.getLocationData());

        // Restore function context
        model.getContextStack().returnFunctionContext();

        return result;
    }

    private boolean evaluateBooleanExpression1(PropertyContext propertyContext, List<Item> nodeset, int position, Bind bind,
                                               String xpathExpression, Map<String, ValueRepresentation> currentVariables) {

        // Setup function context
        // TODO: when binds are able to receive events, source should be bind id
        final XFormsFunction.Context functionContext = model.getContextStack().getFunctionContext(model.getEffectiveId());

        final String xpath = "boolean(" + xpathExpression + ")";
        final boolean result = (Boolean) XPathCache.evaluateSingle(propertyContext,
                nodeset, position, xpath, container.getNamespaceMappings(bind.getBindElement()), currentVariables,
                XFormsContainingDocument.getFunctionLibrary(), functionContext, bind.getLocationData().getSystemID(), bind.getLocationData());

        // Restore function context
        model.getContextStack().returnFunctionContext();

        return result;
    }

//    private boolean evaluateBooleanExpression2(PropertyContext propertyContext, Bind bind, String xpathExpression, List<Item> nodeset, int position, Map currentVariables) {
//        return XPathCache.evaluateAsBoolean(propertyContext,
//            nodeset, position, xpathExpression, containingDocument.getNamespaceMappings(bind.getBindElement()), currentVariables,
//            XFormsContainingDocument.getFunctionLibrary(), model.getContextStack().getFunctionContext(), bind.getLocationData().getSystemID(), bind.getLocationData());
//    }

    private void handleValidationBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, Set<String> invalidInstances) {

        final NodeInfo currentNodeInfo = (NodeInfo) nodeset.get(position - 1);
        final NamespaceMapping namespaceMapping = container.getNamespaceMappings(bind.getBindElement());

        // Current validity value
        // NOTE: This may have been set by schema validation earlier in the validation process
        boolean isValid = InstanceData.getValid(currentNodeInfo);

        // Current required value (computed during preceding recalculate)
        final boolean isRequired = InstanceData.getRequired(currentNodeInfo);

        // Node value, retrieved if needed
        String nodeValue = null;

        // Handle type MIP
        // NOTE: We must always handle the type first because types are not only used by validation, but by controls
        if (bind.getType() != null) {

            // "The type model item property is not applied to instance nodes that contain child elements"
            if (currentNodeInfo.getNodeKind() != org.w3c.dom.Document.ELEMENT_NODE || !XFormsUtils.hasChildrenElements(currentNodeInfo)) {

                // Need an evaluator to check and convert type below
                final XPathEvaluator xpathEvaluator;
                try {
                    xpathEvaluator = new XPathEvaluator();
                    // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                    final IndependentContext context = (IndependentContext) xpathEvaluator.getStaticContext();
                    for (final Map.Entry<String, String> entry : namespaceMapping.mapping.entrySet()) {
                        context.declareNamespace(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, bind.getLocationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

                {
                    // Get type namespace and local name
                    final QName typeQName = evaluateTypeQName(bind, namespaceMapping.mapping);

                    final String typeNamespaceURI = typeQName.getNamespaceURI();
                    final String typeLocalname = typeQName.getName();

                    // Get value to validate if not already computed above
                    if (nodeValue == null)
                        nodeValue = XFormsInstance.getValueForNodeInfo(currentNodeInfo);

                    // TODO: "[...] these datatypes can be used in the type model item property without the addition of the
                    // XForms namespace qualifier if the namespace context has the XForms namespace as the default
                    // namespace."

                    final boolean isBuiltInSchemaType = XMLConstants.XSD_URI.equals(typeNamespaceURI);
                    final boolean isBuiltInXFormsType = XFormsConstants.XFORMS_NAMESPACE_URI.equals(typeNamespaceURI);
                    final boolean isBuiltInXXFormsType = XFormsConstants.XXFORMS_NAMESPACE_URI.equals(typeNamespaceURI);

                    final boolean isBuiltInXFormsSchemaType = isBuiltInXFormsType && BUILTIN_XFORMS_SCHEMA_TYPES.contains(typeLocalname);

                    if (isBuiltInXFormsSchemaType) {
                        // xforms:dayTimeDuration, xforms:yearMonthDuration, xforms:email, xforms:card-number
                        if (xformsValidator == null) {
                            xformsValidator = new XFormsModelSchemaValidator("oxf:/org/orbeon/oxf/xforms/xforms-types.xsd");
                            xformsValidator.loadSchemas(propertyContext, containingDocument);
                        }

                        final String validationError =
                            xformsValidator.validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(),
                                    bind.getLocationData(), bind.getId());

                        if (validationError != null) {
                            isValid = false;
                            InstanceData.addSchemaError(currentNodeInfo, validationError, nodeValue, bind.getId());
                        }

                    } else if (isBuiltInXFormsType && nodeValue.length() == 0) {
                        // Don't consider the node invalid if the string is empty with xforms:* types
                    } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
                        // Built-in schema or XForms type

                        // Use XML Schema namespace URI as Saxon doesn't know anything about XForms types
                        final String newTypeNamespaceURI = XMLConstants.XSD_URI;

                        // Get type information
                        final int requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname);
                        if (requiredTypeFingerprint == -1) {
                            throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());

                            // TODO: xxx check what XForms event must be dispatched
                        }

                        // Try to perform casting
                        // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens. Is that expected?
                        final StringValue stringValue = new StringValue(nodeValue);
                        final XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getExecutable());
                        final ConversionResult result = stringValue.convertPrimitive((BuiltInAtomicType) BuiltInType.getSchemaType(requiredTypeFingerprint), true, xpContext);

                        // Set error on node if necessary
                        if (result instanceof ValidationFailure) {
                            isValid = false;
                            InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());
                        }
                    } else if (isBuiltInXXFormsType) {
                        // Built-in extension types
                        final boolean isOptionalAndEmpty = !isRequired && "".equals(nodeValue);
                        if (typeLocalname.equals("xml")) {
                            // xxforms:xml type
                            if (!isOptionalAndEmpty && !XMLUtils.isWellFormedXML(nodeValue)) {
                                isValid = false;
                                InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());
                            }
                        } else if (typeLocalname.equals("xpath2")) {
                            // xxforms:xpath2 type
                            if (!isOptionalAndEmpty && !XFormsUtils.isXPath2Expression(containingDocument.getStaticState().getXPathConfiguration(), nodeValue, namespaceMapping)) {
                                isValid = false;
                                InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());
                            }

                        } else {
                            throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());

                            // TODO: xxx check what XForms event must be dispatched
                        }

                    } else if (model.getSchemaValidator() != null && model.getSchemaValidator().hasSchema()) {
                        // Other type and there is a schema

                        // There are possibly types defined in the schema
                        final String validationError
                                = model.getSchemaValidator().validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(), bind.getLocationData(), bind.getId());

                        // Set error on node if necessary
                        if (validationError != null) {
                            isValid = false;
                            InstanceData.addSchemaError(currentNodeInfo, validationError, nodeValue, bind.getId());
                        }
                    } else {
                        throw new ValidationException("Invalid schema type '" + bind.getType() + "'", bind.getLocationData());

                        // TODO: xxx check what XForms event must be dispatched
                    }

                    // Set type on node
                    InstanceData.setType(currentNodeInfo, XMLUtils.buildExplodedQName(typeNamespaceURI, typeLocalname));
                }
            }
        }

        // Bail is we are already invalid
        if (!isValid) {
            // Remember invalid instances
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            invalidInstances.add(instanceForNodeInfo.getEffectiveId());
            return;
        }

        // Handle required MIP
        if (isRequired && bind.getRequired() != null) {// this assumes that the required MIP has been already computed (during recalculate)
            // Current node is required...
            if (nodeValue == null)
                nodeValue = XFormsInstance.getValueForNodeInfo(currentNodeInfo);

            if (isEmptyValue(nodeValue)) {
                // ...and empty
                isValid = false;
                InstanceData.updateValueValid(currentNodeInfo, false, bind.getId());
            }
        }

        // Bail is we are already invalid
        if (!isValid) {
            // Remember invalid instances
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            invalidInstances.add(instanceForNodeInfo.getEffectiveId());
            return;
        }

        // Handle XPath constraint MIP

        // NOTE: We evaluate the constraint here, so that if the type is not respected the constraint is not evaluated.
        // This can also prevent XPath errors, e.g.: <xforms:bind nodeset="foobar" type="xs:integer" constraint=". > 10"/>
        if (dependencies.requireModelMIPUpdate(staticModel, bind.getId(), Model.CONSTRAINT()))
            isValid &= handleConstraintMIP(propertyContext, bind, nodeset, position, currentNodeInfo);

        // Remember invalid instances
        if (!isValid) {
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            invalidInstances.add(instanceForNodeInfo.getEffectiveId());
        }
    }

    public static boolean isEmptyValue(String value) {
        // TODO: configurable notion of "empty" through property (trimming vs. strict)
        return "".equals(value);
    }

    private Boolean handleConstraintMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo) {
        final Boolean constraint = evaluateConstraintMIP(propertyContext, bind, nodeset, position, currentNodeInfo);
        if (constraint != null) {
            // Update node with MIP value
            // TODO: why do we allow calling this with valid == true?
            InstanceData.updateValueValid(currentNodeInfo, constraint, bind.getId());
            return constraint;
        } else {
            return true;
        }
    }

    private Boolean evaluateConstraintMIP(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position, NodeInfo currentNodeInfo) {
        if (bind.getConstraint() != null) {
            // Evaluate constraint
            try {
                // Get MIP value
                return evaluateBooleanExpression1(propertyContext, nodeset, position, bind, bind.getConstraint(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.getLocationData(), "evaluating XForms constraint bind",
                        bind.getBindElement(), "expression", bind.getConstraint()));

                container.dispatchEvent(propertyContext, new XFormsComputeExceptionEvent(containingDocument, model, ve.getMessage(), ve));
                throw new IllegalStateException(); // event above throw an exception anyway
            }
        } else {
            return null;
        }
    }

    private QName evaluateTypeQName(Bind bind, Map<String, String> namespaceMap) {
        final String typeQName = bind.getType();
        if (typeQName != null) {
            final String typeNamespacePrefix;
            final String typeNamespaceURI;
            final String typeLocalname;

            final int prefixPosition = typeQName.indexOf(':');
            if (prefixPosition > 0) {
                typeNamespacePrefix = typeQName.substring(0, prefixPosition);
                typeNamespaceURI = namespaceMap.get(typeNamespacePrefix);
                if (typeNamespaceURI == null)
                    throw new ValidationException("Namespace not declared for prefix '" + typeNamespacePrefix + "'",
                            bind.getLocationData());

                // TODO: xxx check what XForms event must be dispatched

                typeLocalname = typeQName.substring(prefixPosition + 1);
            } else {
                typeNamespacePrefix = "";
                typeNamespaceURI = "";
                typeLocalname = typeQName;
            }

            return new QName(typeLocalname, new Namespace(typeNamespacePrefix, typeNamespaceURI));
        } else {
            return null;
        }
    }

    private static interface BindRunner {
        public void applyBind(PropertyContext propertyContext, Bind bind, List<Item> nodeset, int position);
    }

    public class Bind {

        private Model.Bind staticBind;
        private List<Item> nodeset;       // actual nodeset for this bind

        private List<BindIteration> childrenIterations; // List<BindIteration>

        public Bind(PropertyContext propertyContext, Element bindElement, boolean isSingleNodeContext) {
            this.staticBind = staticModel.bindsById().get(XFormsUtils.getElementStaticId(bindElement));

            // If this bind is marked for offline handling, remember it
            if ("true".equals(bindElement.attributeValue(XFormsConstants.XXFORMS_OFFLINE_QNAME)))
                offlineBinds.add(this);

            // Compute nodeset for this bind
            model.getContextStack().pushBinding(propertyContext, bindElement, model.getEffectiveId(), model.getResolutionScope());
            {
                // NOTE: This should probably go into XFormsContextStack
                if (model.getContextStack().getCurrentBindingContext().isNewBind()) {
                    // Case where a @nodeset or @ref attribute is present -> a current nodeset is therefore available
                    // NOTE: @ref is not supported by XForms 1.1, but it probably should!
                    this.nodeset = model.getContextStack().getCurrentNodeset();
                } else {
                    // Case where of missing @nodeset attribute (it is optional in XForms 1.1 and defaults to the context item)
                    final Item contextItem = model.getContextStack().getContextItem();
                    this.nodeset = (contextItem == null) ? XFormsConstants.EMPTY_ITEM_LIST : Collections.singletonList(contextItem);
                }
                final int nodesetSize = this.nodeset.size();

                // "4.7.2 References to Elements within a bind Element [...] If a target bind element is outermost, or if
                // all of its ancestor bind elements have nodeset attributes that select only one node, then the target bind
                // only has one associated bind object, so this is the desired target bind object whose nodeset is used in
                // the Single Node Binding or Node Set Binding"
                if (isSingleNodeContext)
                    singleNodeContextBinds.put(staticBind.staticId(), this);

                final List<Element> childElements = Dom4jUtils.elements(bindElement, XFormsConstants.XFORMS_BIND_QNAME);
                if (childElements.size() > 0) {
                    // There are children binds
                    childrenIterations = new ArrayList<BindIteration>();

                    // Iterate over nodeset and produce child iterations
                    for (int currentPosition = 1; currentPosition <= nodesetSize; currentPosition++) {
                        model.getContextStack().pushIteration(currentPosition);
                        {
                            // Create iteration and remember it
                            final boolean isNewSingleNodeContext = isSingleNodeContext && nodesetSize == 1;
                            final BindIteration currentBindIteration = new BindIteration(propertyContext, isNewSingleNodeContext, childElements);
                            childrenIterations.add(currentBindIteration);

                            // Create mapping context node -> iteration
                            final NodeInfo iterationNodeInfo = (NodeInfo) nodeset.get(currentPosition - 1);
                            List<BindIteration> iterations = iterationsForContextNodeInfo.get(iterationNodeInfo);
                            if (iterations == null) {
                                iterations = new ArrayList<BindIteration>();
                                iterationsForContextNodeInfo.put(iterationNodeInfo, iterations);
                            }
                            iterations.add(currentBindIteration);
                        }
                        model.getContextStack().popBinding();
                    }
                }
            }
            model.getContextStack().popBinding();
        }

        public void applyBinds(PropertyContext propertyContext, BindRunner bindRunner) {
            if (nodeset != null && nodeset.size() > 0) {
                // Handle each node in this node-set
                final Iterator<BindIteration> j = (childrenIterations != null) ? childrenIterations.iterator() : null;

                for (int index = 1; index <= nodeset.size(); index++) {
                    final BindIteration currentBindIteration = (j != null) ? j.next() : null;

                    // Handle current node
                    bindRunner.applyBind(propertyContext, this, nodeset, index);

                    // Handle children iterations if any
                    if (currentBindIteration != null) {
                        currentBindIteration.applyBinds(propertyContext, bindRunner);
                    }
                }
            }
        }

        public String getId() {
            return staticBind.staticId();
        }

        public String getName() {
            return staticBind.name();
        }

        public List<Item> getNodeset() {
            return nodeset;
        }

        public LocationData getLocationData() {
            return staticBind.locationData();
        }

        public Element getBindElement() {
            return staticBind.element();
        }

        public String getRelevant() {
            return staticBind.getRelevant();
        }

        public String getCalculate() {
            return staticBind.getCalculate();
        }

        public String getType() {
            return staticBind.getType();
        }

        public String getConstraint() {
            return staticBind.getConstraint();
        }

        public String getRequired() {
            return staticBind.getRequired();
        }

        public String getReadonly() {
            return staticBind.getReadonly();
        }

        public String getXXFormsDefault() {
            return staticBind.getInitialValue();
        }

        public Map<String, Model.Bind.MIP> getCustomMips() {// NOTE: IntelliJ marks Model.Bind as an error, but it compiles fine
            return staticModel.customMIPs().get(staticBind.staticId());
        }
    }

    private class BindIteration {

        private List<Bind> childrenBinds;

        public BindIteration(PropertyContext propertyContext, boolean isSingleNodeContext, List<Element> childElements) {

            if (childElements.size() > 0) {
                // There are child elements
                childrenBinds = new ArrayList<Bind>();

                // Iterate over child elements and create children binds
                for (Element currentBindElement: childElements) {
                    final Bind currentBind = new Bind(propertyContext, currentBindElement, isSingleNodeContext);
                    childrenBinds.add(currentBind);
                }
            }
        }

        public void applyBinds(PropertyContext propertyContext, BindRunner bindRunner) {
            for (final Bind currentBind: childrenBinds) {
                currentBind.applyBinds(propertyContext, bindRunner);
            }
        }

        public Bind getBind(String bindId) {
            for (final Bind currentBind: childrenBinds) {
                if (currentBind.getId().equals(bindId))
                    return currentBind;
            }
            return null;
        }
    }
}
