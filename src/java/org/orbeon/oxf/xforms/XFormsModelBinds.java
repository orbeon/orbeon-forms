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
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.event.events.XXFormsXPathErrorEvent;
import org.orbeon.oxf.xforms.function.XFormsFunction;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.xbl.XBLContainer;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.StandardNames;
import org.orbeon.saxon.om.ValueRepresentation;
import org.orbeon.saxon.sxpath.IndependentContext;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.BuiltInType;
import org.orbeon.saxon.type.ConversionResult;
import org.orbeon.saxon.type.ValidationFailure;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.QNameValue;
import org.orbeon.saxon.value.SequenceExtent;
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
    private Map<Item, List<Bind.BindIteration>> iterationsForContextNodeInfo = new HashMap<Item, List<Bind.BindIteration>>();

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
     */
    public void rebuild() {

        if (indentedLogger.isDebugEnabled())
            indentedLogger.startHandleOperation("model", "performing rebuild", "model id", model.getEffectiveId());

        // Reset everything
        model.getContextStack().resetBindingContext(model);
        topLevelBinds.clear();
        singleNodeContextBinds.clear();
        iterationsForContextNodeInfo.clear();

        // Clear all instances that might have InstanceData
        for (final XFormsInstance instance : model.getInstances()) {
            // Only clear instances that are impacted by xf:bind/(@ref|@nodeset), assuming we were able to figure out the dependencies
            // The reason is that clearing this state can take quite some time
            final boolean instanceMightBeSchemaValidated = model.hasSchema() && instance.isSchemaValidation();
            final boolean instanceMightHaveMips =
                    dependencies.hasAnyCalculationBind(staticModel, instance.getPrefixedId()) ||
                    dependencies.hasAnyValidationBind(staticModel, instance.getPrefixedId());

            if (instanceMightBeSchemaValidated || instanceMightHaveMips) {
                XFormsUtils.iterateInstanceData(instance, new XFormsUtils.InstanceWalker() {
                    public void walk(NodeInfo nodeInfo) {
                        InstanceData.clearState(nodeInfo);
                    }
                }, true);
            }
        }

        // Iterate through all top-level bind elements to create new bind tree
        // TODO: In the future, XPath dependencies must allow for partial rebuild of the tree as is the case with controls
        // Even before that, the bind tree could be modified more dynamically as is the case with controls
        for (final Model.Bind staticBind : staticModel.topLevelBindsJava())
            topLevelBinds.add(new Bind(staticBind, true)); // remember as top-level bind

        if (indentedLogger.isDebugEnabled())
            indentedLogger.endHandleOperation();
    }

    /**
     * Apply calculate binds.
     *
     * @param applyInitialValues    whether to apply initial values (@xxforms:default="...")
     */
    public void applyCalculateBinds(boolean applyInitialValues) {

        if (!staticModel.hasCalculateComputedCustomBind()) {
            // We can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping bind recalculate", "model id", model.getEffectiveId(), "reason", "no recalculation binds");
        } else {
            // This model may have calculation binds

            if (indentedLogger.isDebugEnabled())
                indentedLogger.startHandleOperation("model", "performing bind recalculate", "model id", model.getEffectiveId());
            {

                // Reset context stack just to re-evaluate the variables
                model.getContextStack().resetBindingContext(model);

                // 1. Evaluate initial values and calculate before the rest

                if (isFirstCalculate || applyInitialValues) {
                    // Handle default values first
                    if (staticModel.hasInitialValueBind())
                        iterateBinds(new BindRunner() {
                            public void applyBind(Bind bind, int position) {
                                if (bind.staticBind.getInitialValue() != null &&  dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.INITIAL_VALUE()))
                                    handleInitialValueDefaultBind(bind, position);
                            }
                        });
                    // This will be false from now on as we have done our first handling of calculate binds
                    isFirstCalculate = false;
                }

                // Handle calculations
                if (staticModel.hasCalculateBind())
                    iterateBinds(new BindRunner() {
                        public void applyBind(Bind bind, int position) {
                            if (bind.staticBind.getCalculate() != null && dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.CALCULATE()))
                                handleCalculateBind(bind, position);
                        }
                    });

                // 2. Update computed expression binds if requested
                applyComputedExpressionBinds();
            }

            if (indentedLogger.isDebugEnabled())
                indentedLogger.endHandleOperation();
        }
    }

    /**
     * Apply required, relevant and readonly binds.
     *
     */
    public void applyComputedExpressionBinds() {

        // Reset context stack just to re-evaluate the variables as instance values may have changed with @calculate
        model.getContextStack().resetBindingContext(model);

        // Apply
        iterateBinds(new BindRunner() {
            public void applyBind(Bind bind, int position) {
                if (bind.staticBind.hasCalculateComputedMIPs() || bind.staticBind.hasCustomMIPs()) // don't bother if not
                    handleComputedExpressionBind(bind, position);
            }
        });
    }

    /**
     * Apply validation binds
     *
     * @param invalidInstances  will contain a
     */
    public void applyValidationBinds(final Set<String> invalidInstances) {

        if (!staticModel.hasValidateBind()) {
            // We can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping bind revalidate", "model id", model.getEffectiveId(), "reason", "no validation binds");
        } else {
            // This model may have validation binds

            // Reset context stack just to re-evaluate the variables
            model.getContextStack().resetBindingContext(model);

            // 1. Validate based on type and requiredness
            if (staticModel.hasTypeBind() || staticModel.hasRequiredBind())
                iterateBinds(new BindRunner() {
                    public void applyBind(Bind bind, int position) {
                        if (bind.staticBind.getType() != null || bind.staticBind.getRequired() != null) // don't bother if not
                            validateTypeAndRequired(bind, position, invalidInstances);
                    }
                });

            // 2. Validate constraints
            if (staticModel.hasConstraintBind())
                iterateBinds(new BindRunner() {
                    public void applyBind(Bind bind, int position) {
                        if (bind.staticBind.getConstraint() != null) // don't bother if not
                            validateConstraint(bind, position, invalidInstances);
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
        return (bind != null) ? bind.nodeset : XFormsConstants.EMPTY_ITEM_LIST;
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
                final List<Bind.BindIteration> iterationsForContextNode = iterationsForContextNodeInfo.get(contextItem);
                if (iterationsForContextNode != null) {
                    for (final Bind.BindIteration currentIteration: iterationsForContextNode) {
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

    public Item evaluateBindByType(Bind bind, int position, QName mipType) throws XPathException {

        final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
        final Map<String, ValueRepresentation> currentVariables = getVariables(currentNodeInfo);

        if (mipType.equals(XFormsConstants.RELEVANT_QNAME)) {
            // Relevant
            final Boolean relevant = evaluateRelevantMIP(bind, position, currentVariables);
            return (relevant != null) ? BooleanValue.get(relevant) : null;
        } else if (mipType.equals(XFormsConstants.READONLY_QNAME)) {
            // Readonly
            final Boolean readonly = evaluateReadonlyMIP(bind, position, currentVariables);
            return (readonly != null) ? BooleanValue.get(readonly) : null;
        } else if (mipType.equals(XFormsConstants.REQUIRED_QNAME)) {
            // Required
            final Boolean required = evaluateRequiredMIP(bind, position, currentVariables);
            return (required != null) ? BooleanValue.get(required) : null;
        } else if (mipType.equals(XFormsConstants.TYPE_QNAME)) {
            // Type
            final NamespaceMapping namespaceMapping = bind.staticBind.namespaceMapping();
            final QName type = bind.evaluateTypeQName(namespaceMapping.mapping);
            return (type != null) ? new QNameValue(type.getNamespacePrefix(), type.getNamespaceURI(), type.getName(), null) : null;
        } else if (mipType.equals(XFormsConstants.CONSTRAINT_QNAME)) {
            // Constraint
            final Boolean constraint = evaluateConstraintMIP(bind, position, currentNodeInfo);
            return (constraint != null) ? BooleanValue.get(constraint) : null;
        } else if (mipType.equals(XFormsConstants.CALCULATE_QNAME)) {
            // Calculate
            final String result = evaluateCalculateBind(bind, position);
            return (result != null) ? new StringValue(result) : null;
        } else if (mipType.equals(XFormsConstants.XXFORMS_DEFAULT_QNAME)) {
            // xxforms:default
            final String result = evaluateXXFormsDefaultBind(bind, position);
            return (result != null) ? new StringValue(result) : null;
        } else {
            // Try custom MIPs
            final String result = evaluateCustomMIP(bind, Model.buildCustomMIPName(mipType.getQualifiedName()), position, currentVariables);
            return (result != null) ? new StringValue(result) : null;
        }
    }

    /**
     * Iterate over all binds and for each one do the callback.
     *
     * @param bindRunner        bind runner
     */
    private void iterateBinds(BindRunner bindRunner) {
        // Iterate over top-level binds
        for (final Bind currentBind : topLevelBinds) {
            try {
                currentBind.applyBinds(bindRunner);
            } catch (Exception e) {
                throw ValidationException.wrapException(e, new ExtendedLocationData(currentBind.staticBind.locationData(), "evaluating XForms binds", currentBind.staticBind.element()));
            }
        }
    }
    
    private void handleMIPXPathException(Exception e, Bind bind, String expression, String message) {
        final ValidationException ve = ValidationException.wrapException(e, new ExtendedLocationData(bind.staticBind.locationData(), message,
            bind.staticBind.element(), "expression", expression));

        container.dispatchEvent(new XXFormsXPathErrorEvent(containingDocument, model, ve.getMessage(), ve));
    }

    private String evaluateXXFormsDefaultBind(Bind bind, int position) {
        // Handle xxforms:default MIP
        if (bind.staticBind.getInitialValue() != null) {
            // Compute default value
            try {
                final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
                return evaluateStringExpression(bind.nodeset, position, bind, bind.staticBind.getInitialValue(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getInitialValue(), "evaluating XForms default bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private void handleInitialValueDefaultBind(Bind bind, int position) {

        final String stringResult = evaluateXXFormsDefaultBind(bind, position);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and handle this error
            final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
            DataModel.jSetValueIfChanged(containingDocument, indentedLogger, model, bind.staticBind.locationData(), currentNodeInfo, stringResult, "default", true);
        }
    }

    public void handleCalculateBind(Bind bind, int position) {
        final String stringResult = evaluateCalculateBind(bind, position);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and handle this error
            final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
            DataModel.jSetValueIfChanged(containingDocument, indentedLogger, model, bind.staticBind.locationData(), currentNodeInfo, stringResult, "calculate", true);
        }
    }

    public String evaluateCalculateBind(Bind bind, int position) {
        // Handle calculate MIP
        if (bind.staticBind.getCalculate() != null) {
            // Compute calculated value
            try {
                final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
                return evaluateStringExpression(bind.nodeset, position, bind, bind.staticBind.getCalculate(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getCalculate(), "evaluating XForms calculate bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private Map<String, ValueRepresentation> getVariables(NodeInfo contextNodeInfo) {

        if (staticModel.jBindsByName().size() > 0) {
            final Map<String, ValueRepresentation> bindVariablesValues = new HashMap<String, ValueRepresentation>();

            // Add bind variables
            for (Map.Entry<String, Model.Bind> currentEntry : staticModel.jBindsByName().entrySet()) {
                final String currentVariableName = currentEntry.getKey();
                final String currentBindId = currentEntry.getValue().staticId();

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

    private void handleComputedExpressionBind(Bind bind, int position) {

        final NodeInfo currentNodeInfo = (NodeInfo) bind.nodeset.get(position - 1);
        final Map<String, ValueRepresentation> currentVariables = getVariables(currentNodeInfo);

        // Handle relevant, readonly, required, and custom MIPs
        if (bind.staticBind.getRelevant() != null && dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.RELEVANT()))
            evaluateAndSetRelevantMIP(bind, position, currentVariables);
        if (bind.staticBind.getReadonly() != null && dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.READONLY()) || bind.staticBind.getCalculate() != null)
            evaluateAndSetReadonlyMIP(bind, position, currentVariables);
        if (bind.staticBind.getRequired() != null && dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.REQUIRED()))
            evaluateAndSetRequiredMIP(bind, position, currentVariables);

        // TODO: optimize those as well
        evaluateAndSetCustomMIPs(bind, position, currentVariables);
    }

    private void evaluateAndSetCustomMIPs(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        final Map<String, Model.Bind.XPathMIP> customMips = bind.staticBind.customMIPs();
        if (customMips != null && customMips.size() > 0) {
            for (final String propertyName: customMips.keySet()) {
                final String stringResult = evaluateCustomMIP(bind, propertyName, position, currentVariables);
                bind.setCustom(position, propertyName, stringResult);
            }
        }
    }

    private String evaluateCustomMIP(Bind bind, String propertyName, int position, Map<String, ValueRepresentation> currentVariables) {
        final Map<String, Model.Bind.XPathMIP> customMips = bind.staticBind.customMIPs();
        if (customMips != null && customMips.size() > 0) {
            final String expression = customMips.get(propertyName).expression();
            if (expression != null) {
                try {
                    return evaluateStringExpression(bind.nodeset, position, bind, expression, currentVariables);
                } catch (Exception e) {
                    handleMIPXPathException(e, bind, bind.staticBind.getCalculate(), "evaluating XForms custom bind");// xxx "name", propertyName
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private void evaluateAndSetRequiredMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        final Boolean required = evaluateRequiredMIP(bind, position, currentVariables);
        if (required != null) {
            // Update node with MIP value
            bind.setRequired(position, required);
        }
    }

    private Boolean evaluateRequiredMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.staticBind.getRequired() != null) {
            // Evaluate "required" XPath expression on this node
            try {
                // Get MIP value
                return evaluateBooleanExpression1(bind.nodeset, position, bind, bind.staticBind.getRequired(), currentVariables);
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getRequired(), "evaluating XForms required bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private void evaluateAndSetReadonlyMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        final Boolean readonly = evaluateReadonlyMIP(bind, position, currentVariables);
        if (readonly != null) {
            // Mark node
            bind.setReadonly(position, readonly);
        } else if (bind.staticBind.getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has` a calculate: set readonly to true()
            bind.setReadonly(position, true);
        }
//
//        final Boolean readonly = evaluateReadonlyMIP(propertyContext, bind, nodeset, position, currentVariables);
//        final boolean oldValue = InstanceDataXxx.getLocalReadonly(currentNodeInfo);
//        final boolean newValue;
//        if (readonly != null) {
//            // Set new value
//            newValue = readonly;
//        } else if (bind.staticBind.getCalculate() != null) {
//            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
//            newValue = true;
//        } else {
//            // No change
//            newValue = oldValue;
//        }
//
//        if (oldValue != newValue) {
//            // Mark node
//            InstanceDataXxx.setReadonly(currentNodeInfo, newValue);
//            model.markMipChange(currentNodeInfo);
//        }
    }

    private Boolean evaluateReadonlyMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.staticBind.getReadonly() != null) {
            // The bind has a readonly attribute
            // Evaluate "readonly" XPath expression on this node
            try {
                return evaluateBooleanExpression1(bind.nodeset, position, bind, bind.staticBind.getReadonly(), currentVariables);
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getReadonly(), "evaluating XForms readonly bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private void evaluateAndSetRelevantMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        final Boolean relevant = evaluateRelevantMIP(bind, position, currentVariables);
        if (relevant != null) {
            // Mark node
            bind.setRelevant(position, relevant);
        }
    }

    private Boolean evaluateRelevantMIP(Bind bind, int position, Map<String, ValueRepresentation> currentVariables) {
        if (bind.staticBind.getRelevant() != null) {
            // Evaluate "relevant" XPath expression on this node
            try {
                return evaluateBooleanExpression1(bind.nodeset, position, bind, bind.staticBind.getRelevant(), currentVariables);
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getRelevant(), "evaluating XForms relevant bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private String evaluateStringExpression(List<Item> nodeset, int position, Bind bind,
                                            String xpathExpression, Map<String, ValueRepresentation> currentVariables) {

        // Setup function context
         // TODO: when binds are able to receive events, source should be bind id
        final XFormsFunction.Context functionContext = model.getContextStack().getFunctionContext(model.getEffectiveId());
        final String result;
        try {
            result = XPathCache.evaluateAsString(nodeset, position, xpathExpression,
                        bind.staticBind.namespaceMapping(), currentVariables,
                        XFormsContainingDocument.getFunctionLibrary(), functionContext,
                        bind.staticBind.locationData().getSystemID(), bind.staticBind.locationData());
        } finally {
            // Restore function context
            model.getContextStack().returnFunctionContext();
        }

        return result;
    }

    private boolean evaluateBooleanExpression1(List<Item> nodeset, int position, Bind bind,
                                               String xpathExpression, Map<String, ValueRepresentation> currentVariables) {

        // Setup function context
        // TODO: when binds are able to receive events, source should be bind id
        final XFormsFunction.Context functionContext = model.getContextStack().getFunctionContext(model.getEffectiveId());

        final String xpath = "boolean(" + xpathExpression + ")";
        final boolean result = (Boolean) XPathCache.evaluateSingle(
                nodeset, position, xpath, bind.staticBind.namespaceMapping(), currentVariables,
                XFormsContainingDocument.getFunctionLibrary(), functionContext, bind.staticBind.locationData().getSystemID(), bind.staticBind.locationData());

        // Restore function context
        model.getContextStack().returnFunctionContext();

        return result;
    }

//    private boolean evaluateBooleanExpression2(PropertyContext propertyContext, Bind bind, String xpathExpression, List<Item> nodeset, int position, Map currentVariables) {
//        return XPathCache.evaluateAsBoolean(propertyContext,
//            nodeset, position, xpathExpression, containingDocument.getNamespaceMappings(bind.staticBind.element()), currentVariables,
//            XFormsContainingDocument.getFunctionLibrary(), model.getContextStack().getFunctionContext(), bind.staticBind.locationData().getSystemID(), bind.staticBind.locationData());
//    }

    private void validateTypeAndRequired(Bind bind, int position, Set<String> invalidInstances) {

        assert bind.staticBind.getType() != null || bind.staticBind.getRequired() != null;

        // Don't try to apply validity to a node if it has children nodes or if it's not a node
        // "The type model item property is not applied to instance nodes that contain child elements"
        final Bind.BindNode bindNode = bind.getBindNode(position);
        final NodeInfo currentNodeInfo = bindNode.nodeInfo;
        if (currentNodeInfo == null || bindNode.hasChildrenElements)
            return;

        // NOTE: 2011-02-03: Decided to also apply this to required validation.
        // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

        // Current required value (computed during previous recalculate)
        final boolean isRequired = InstanceData.getRequired(currentNodeInfo);

        // 1. Check type validity

        // Type MIP @type attribute is special:
        //
        // o it is not an XPath expression
        // o but because type validation can be expensive, we want to optimize that if we can
        // o so requireModelMIPUpdate(Model.TYPE) actually means "do we need to update type validity"
        //
        // xxforms:xml and xxforms:xpath2 also depend on requiredness, which is probably not a good idea. To handle
        // this condition (partially), if the same bind has @type and @required, we also reevaluate type validity if
        // requiredness has changed. Ideally:
        //
        // o we would not depend on requiredness
        // o but if we did, we should handle also the case where another bind is setting requiredness on the node
        //
        final boolean typeValidity;
        if (bind.typeQName != null) {
             if (dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.TYPE())
                     || bind.staticBind.getRequired() != null && dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.REQUIRED())) {
                 // Compute new type validity if the value of the node might have changed OR the value of requiredness
                 // might have changed
                typeValidity = validateType(bind, currentNodeInfo, isRequired);
                bind.setTypeValidity(position, typeValidity);
             } else {
                 // Keep current value
                typeValidity = bindNode.isTypeValid();
             }
        } else {
            // Keep current value (defaults to true when no type attribute)
            typeValidity = bindNode.isTypeValid();
        }

        // 2. Check required validity
        // We compute required validity every time
        final boolean requiredValidity;
        if (isRequired) {
            // Required
            final String nodeValue = DataModel.getValue(currentNodeInfo);
            requiredValidity = !isEmptyValue(nodeValue); // not valid if value is empty
        } else {
            // Not required, so any value passes including empty as far as required is
            // concerned
            requiredValidity = true;
        }
        bind.setRequiredValidity(position, requiredValidity);

        // Remember invalid instances
        if (!typeValidity || !requiredValidity) {
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            invalidInstances.add(instanceForNodeInfo.getEffectiveId());
        }
    }

    private void validateConstraint(Bind bind, int position, Set<String> invalidInstances) {

        assert bind.staticBind.getConstraint() != null;

        // Don't try to apply validity to a node if it's not a node
        final Bind.BindNode bindNode = bind.getBindNode(position);
        final NodeInfo currentNodeInfo = bindNode.nodeInfo;
        if (currentNodeInfo == null)
            return;

        // NOTE: 2011-02-03: Decided to allow setting a constraint on an element with children. Handles the case of
        // assigning validity to an enclosing element.
        // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

        final boolean typeValidity = InstanceData.getTypeValid(currentNodeInfo); // all type validity has been computed now
        final boolean constraintValidity;
        if (typeValidity) {
            // Then bother checking @constraint
            if (dependencies.requireModelMIPUpdate(staticModel, bind.staticBind, Model.CONSTRAINT())) {
                // Re-evaluate and set
                constraintValidity = evaluateConstraintMIP(bind, position, currentNodeInfo);
                bind.setConstraintValidity(position, constraintValidity);
            } else
                // Keep current value
                constraintValidity = bindNode.isConstraintValidity();
        } else {
            // Type is invalid and we don't want to risk running an XPath expression against an invalid node type
            // This is a common scenario, e.g. <xf:bind type="xs:integer" constraint=". > 0"/>
            // Force constraint to false in this case
            constraintValidity = false;
            bind.setConstraintValidity(position, false);
        }

        // Remember invalid instances
        if (!constraintValidity) {
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            invalidInstances.add(instanceForNodeInfo.getEffectiveId());
        }
    }

    private boolean validateType(Bind bind, NodeInfo currentNodeInfo, boolean required) {

        final boolean typeValid;
        {
            // NOTE: xf:bind/@type is a literal type value, and it is the same that applies to all nodes pointed to by xf:bind/@ref
            final QName typeQName = bind.typeQName;

            final String typeNamespaceURI = typeQName.getNamespaceURI();
            final String typeLocalname = typeQName.getName();

            // Get value to validate if not already computed above

            final String nodeValue = DataModel.getValue(currentNodeInfo);

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
                    xformsValidator.loadSchemas(containingDocument);
                }

                final String validationError =
                    xformsValidator.validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(),
                            bind.staticBind.locationData());

                typeValid = validationError == null;

            } else if (isBuiltInXFormsType && nodeValue.length() == 0) {
                // Don't consider the node invalid if the string is empty with xforms:* types
                typeValid = true;
            } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
                // Built-in schema or XForms type

                // Use XML Schema namespace URI as Saxon doesn't know anything about XForms types
                final String newTypeNamespaceURI = XMLConstants.XSD_URI;

                // Get type information
                final int requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname);
                if (requiredTypeFingerprint == -1) {
                    throw new ValidationException("Invalid schema type '" + bind.staticBind.getType() + "'", bind.staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

                // Need an evaluator to check and convert type below
                final XPathEvaluator xpathEvaluator;
                try {
                    xpathEvaluator = new XPathEvaluator();
                    // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                    final IndependentContext context = (IndependentContext) xpathEvaluator.getStaticContext();
                    for (final Map.Entry<String, String> entry : bind.staticBind.namespaceMapping().mapping.entrySet()) {
                        context.declareNamespace(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    throw ValidationException.wrapException(e, bind.staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

                // Try to perform casting
                // TODO: Should we actually perform casting? This for example removes leading and trailing space around tokens. Is that expected?
                final StringValue stringValue = new StringValue(nodeValue);
                final XPathContext xpContext = new XPathContextMajor(stringValue, xpathEvaluator.getExecutable());
                final ConversionResult result = stringValue.convertPrimitive((BuiltInAtomicType) BuiltInType.getSchemaType(requiredTypeFingerprint), true, xpContext);

                // Set error on node if necessary
                typeValid = !(result instanceof ValidationFailure);
            } else if (isBuiltInXXFormsType) {
                // Built-in extension types
                final boolean isOptionalAndEmpty = !required && "".equals(nodeValue);
                if (typeLocalname.equals("xml")) {
                    // xxforms:xml type
                    typeValid = isOptionalAndEmpty || XMLUtils.isWellFormedXML(nodeValue);
                } else if (typeLocalname.equals("xpath2")) {
                    // xxforms:xpath2 type
                    typeValid = isOptionalAndEmpty || XFormsUtils.isXPath2Expression(containingDocument.getStaticState().xpathConfiguration(), nodeValue, bind.staticBind.namespaceMapping());
                } else {
                    throw new ValidationException("Invalid schema type '" + bind.staticBind.getType() + "'", bind.staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

            } else if (model.hasSchema()) {
                // Other type and there is a schema

                // There are possibly types defined in the schema
                final String validationError
                        = model.getSchemaValidator().validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(), bind.staticBind.locationData());

                typeValid = validationError == null;
            } else {
                throw new ValidationException("Invalid schema type '" + bind.staticBind.getType() + "'", bind.staticBind.locationData());

                // TODO: xxx check what XForms event must be dispatched
            }
        }
        return typeValid;
    }

    public static boolean isEmptyValue(String value) {
        // TODO: configurable notion of "empty" through property (trimming vs. strict)
        return "".equals(value);
    }

    private Boolean evaluateConstraintMIP(Bind bind, int position, NodeInfo currentNodeInfo) {
        if (bind.staticBind.getConstraint() != null) {
            // Evaluate constraint
            try {
                // Get MIP value
                return evaluateBooleanExpression1(bind.nodeset, position, bind, bind.staticBind.getConstraint(), getVariables(currentNodeInfo));
            } catch (Exception e) {
                handleMIPXPathException(e, bind, bind.staticBind.getConstraint(), "evaluating XForms constraint bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private static interface BindRunner {
        public void applyBind(Bind bind, int position);
    }

    public class Bind {

        public final Model.Bind staticBind;
        public final List<Item> nodeset;       // actual nodeset for this bind

        public final QName typeQName;

        private List<BindNode> bindNodes; // List<BindIteration>

        public Bind(Model.Bind staticBind, boolean isSingleNodeContext) {
            this.staticBind = staticBind;

            // Compute nodeset for this bind
            model.getContextStack().pushBinding(staticBind.element(), model.getEffectiveId(), model.getResolutionScope());
            {
                // NOTE: This should probably go into XFormsContextStack
                if (model.getContextStack().getCurrentBindingContext().isNewBind()) {
                    // Case where a @nodeset or @ref attribute is present -> a current nodeset is therefore available
                    // NOTE: @ref is not supported by XForms 1.1, but it probably should!
                    nodeset = model.getContextStack().getCurrentNodeset();
                } else {
                    // Case where of missing @nodeset attribute (it is optional in XForms 1.1 and defaults to the context item)
                    final Item contextItem = model.getContextStack().getContextItem();
                    nodeset = (contextItem == null) ? XFormsConstants.EMPTY_ITEM_LIST : Collections.singletonList(contextItem);
                }

                assert nodeset != null;

                // "4.7.2 References to Elements within a bind Element [...] If a target bind element is outermost, or if
                // all of its ancestor bind elements have nodeset attributes that select only one node, then the target bind
                // only has one associated bind object, so this is the desired target bind object whose nodeset is used in
                // the Single Node Binding or Node Set Binding"
                if (isSingleNodeContext)
                    singleNodeContextBinds.put(staticBind.staticId(), this);

                // Set type on node
                // Get type namespace and local name
                typeQName = evaluateTypeQName(staticBind.namespaceMapping().mapping);

                final int nodesetSize = nodeset.size();
                if (nodesetSize > 0) {
                    // Only then does it make sense to create BindNodes
                    
                    final List<Model.Bind> childrenStaticBinds = staticBind.childrenJava();
                    if (childrenStaticBinds.size() > 0) {
                        // There are children binds (and maybe MIPs)
                        bindNodes = new ArrayList<BindNode>(nodesetSize);
    
                        // Iterate over nodeset and produce child iterations
                        int currentPosition = 1;
                        for (final Item item : nodeset) {
                            model.getContextStack().pushIteration(currentPosition);
                            {
                                // Create iteration and remember it
                                final boolean isNewSingleNodeContext = isSingleNodeContext && nodesetSize == 1;
                                final BindIteration currentBindIteration = new BindIteration(isNewSingleNodeContext, item, childrenStaticBinds);
                                bindNodes.add(currentBindIteration);
    
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
    
                            currentPosition++;
                        }
                    } else if (staticBind.hasMIPs()) {
                        // No children binds, but we have MIPs, so create holders anyway
                        bindNodes = new ArrayList<BindNode>(nodesetSize);
    
                        for (final Item item : nodeset)
                            bindNodes.add(new BindNode(item));
                    }
                }

            }
            model.getContextStack().popBinding();
        }

        public void applyBinds(BindRunner bindRunner) {
            if (nodeset.size() > 0) {
                // Handle each node in this node-set
                final Iterator<BindNode> j = (bindNodes != null) ? bindNodes.iterator() : null;

                for (int index = 1; index <= nodeset.size(); index++) {
                    final BindNode currentBindIteration = (j != null) ? j.next() : null;

                    // Handle current node
                    bindRunner.applyBind(this, index);

                    // Handle children binds if any
                    if (currentBindIteration instanceof BindIteration)
                        ((BindIteration) currentBindIteration).applyBinds(bindRunner);
                }
            }
        }

        public String getStaticId() {
            return staticBind.staticId();
        }

        private QName evaluateTypeQName(Map<String, String> namespaceMap) {
            final String typeQNameString = staticBind.getType();
            if (typeQNameString != null) {
                final String typeNamespacePrefix;
                final String typeNamespaceURI;
                final String typeLocalname;

                final int prefixPosition = typeQNameString.indexOf(':');
                if (prefixPosition > 0) {
                    typeNamespacePrefix = typeQNameString.substring(0, prefixPosition);
                    typeNamespaceURI = namespaceMap.get(typeNamespacePrefix);
                    if (typeNamespaceURI == null)
                        throw new ValidationException("Namespace not declared for prefix '" + typeNamespacePrefix + "'", staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched

                    typeLocalname = typeQNameString.substring(prefixPosition + 1);
                } else {
                    typeNamespacePrefix = "";
                    typeNamespaceURI = "";
                    typeLocalname = typeQNameString;
                }

                return QName.get(typeLocalname, new Namespace(typeNamespacePrefix, typeNamespaceURI));
            } else {
                return null;
            }
        }

        private BindNode getBindNode(int position) {
            return (bindNodes != null) ? bindNodes.get(position - 1) : null;
        }

        // Delegate to BindNode
        public void setRelevant(int position, boolean value) {
            getBindNode(position).setRelevant(value);
        }

        public void setReadonly(int position, boolean value) {
            getBindNode(position).setReadonly(value);
        }

        public void setRequired(int position, boolean value) {
            getBindNode(position).setRequired(value);
        }

        public void setCustom(int position, String name, String value) {
            getBindNode(position).setCustom(name, value);
        }

        public void setTypeValidity(int position, boolean value) {
            getBindNode(position).setTypeValidity(value);
        }

        public void setRequiredValidity(int position, boolean value) {
            getBindNode(position).setRequiredValidity(value);
        }

        public void setConstraintValidity(int position, boolean value) {
            getBindNode(position).setConstraintValidity(value);
        }

        public boolean isValid(int position) {
            return getBindNode(position).isValid();
        }

        // BindNode holds MIP values for a given bind node
        public class BindNode {

            // Current MIP state
            private boolean relevant = Model.DEFAULT_RELEVANT();
            protected boolean readonly = Model.DEFAULT_READONLY();
            private boolean required = Model.DEFAULT_REQUIRED();
            private Map<String, String> customMips = null;

            private boolean typeValidity = Model.DEFAULT_VALID();
            private boolean requiredValidity = Model.DEFAULT_VALID();
            private boolean constraintValidity = Model.DEFAULT_VALID();

            public final NodeInfo nodeInfo;
            public final boolean hasChildrenElements;

            private BindNode(Item item) {
                if (item instanceof NodeInfo) {
                    nodeInfo = (NodeInfo) item;
                    hasChildrenElements = nodeInfo.getNodeKind() == org.w3c.dom.Document.ELEMENT_NODE && XFormsUtils.hasChildrenElements(nodeInfo);

                    // Add us to the node
                    InstanceData.addBindNode(nodeInfo, this);
                    if (Bind.this.typeQName != null)
                        InstanceData.setBindType(nodeInfo, Bind.this.typeQName);
                } else {
                    nodeInfo = null;
                    hasChildrenElements = false;
                }
            }

            public String getBindStaticId() {
                return Bind.this.getStaticId();
            }

            public void setRelevant(boolean value) {
                this.relevant = value;
            }

            public void setReadonly(boolean value) {
                this.readonly = value;
            }

            public void setRequired(boolean value) {
                this.required = value;
            }

            public void setCustom(String name, String value) {
                if (customMips == null)
                    customMips = new HashMap<String, String>(); // maybe should be LinkedHashMap for reproducibility
                customMips.put(name, value);
            }

            public void setTypeValidity(boolean value) {
                this.typeValidity = value;
            }

            public void setRequiredValidity(boolean value) {
                this.requiredValidity = value;
            }

            public void setConstraintValidity(boolean value) {
                this.constraintValidity = value;
            }

            public boolean isRelevant() {
                return relevant;
            }

            public boolean isReadonly() {
                return readonly;
            }

            public boolean isRequired() {
                return required;
            }

            public boolean isValid() {
                return typeValidity && requiredValidity && constraintValidity;
            }

            public boolean isTypeValid() {
                return typeValidity;
            }

            public boolean isConstraintValidity() {
                return constraintValidity;
            }

            public Map<String, String> getCustomMips() {
                return customMips == null ? null : Collections.unmodifiableMap(customMips);
            }
        }

        // Bind node that also contains nested binds
        private class BindIteration extends BindNode {// TODO: if bind doesn't have MIPs, BindNode storage is not needed

            private List<Bind> childrenBinds;

            public BindIteration(boolean isSingleNodeContext, Item item, List<Model.Bind> childrenStaticBinds) {

                super(item);

                assert childrenStaticBinds.size() > 0;

                // Iterate over children and create children binds
                childrenBinds = new ArrayList<Bind>(childrenStaticBinds.size());
                for (final Model.Bind staticBind : childrenStaticBinds)
                    childrenBinds.add(new Bind(staticBind, isSingleNodeContext));
            }

            public void applyBinds(BindRunner bindRunner) {
                for (final Bind currentBind : childrenBinds)
                    currentBind.applyBinds(bindRunner);
            }

            public Bind getBind(String bindId) {
                for (final Bind currentBind : childrenBinds)
                    if (currentBind.staticBind.staticId().equals(bindId))
                        return currentBind;
                return null;
            }
        }
    }
}
