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

import org.apache.commons.validator.routines.EmailValidator;
import org.dom4j.Element;
import org.dom4j.QName;
import org.orbeon.oxf.common.OrbeonLocationException;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.util.IndentedLogger;
import org.orbeon.oxf.util.XPath;
import org.orbeon.oxf.xforms.analysis.XPathDependencies;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.analysis.model.StaticBind;
import org.orbeon.oxf.xforms.analysis.model.ValidationLevels;
import org.orbeon.oxf.xforms.model.BindNode;
import org.orbeon.oxf.xforms.model.DataModel;
import org.orbeon.oxf.xforms.model.RuntimeBind;
import org.orbeon.oxf.xml.NamespaceMapping;
import org.orbeon.oxf.xml.XMLConstants;
import org.orbeon.oxf.xml.XMLParsing;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.orbeon.saxon.expr.XPathContext;
import org.orbeon.saxon.expr.XPathContextMajor;
import org.orbeon.saxon.om.Item;
import org.orbeon.saxon.om.NodeInfo;
import org.orbeon.saxon.om.StandardNames;
import org.orbeon.saxon.sxpath.IndependentContext;
import org.orbeon.saxon.sxpath.XPathEvaluator;
import org.orbeon.saxon.trans.XPathException;
import org.orbeon.saxon.type.BuiltInAtomicType;
import org.orbeon.saxon.type.BuiltInType;
import org.orbeon.saxon.type.ConversionResult;
import org.orbeon.saxon.type.ValidationFailure;
import org.orbeon.saxon.value.BooleanValue;
import org.orbeon.saxon.value.QNameValue;
import org.orbeon.saxon.value.StringValue;
import org.orbeon.scaxon.XML;
import org.w3c.dom.Node;

import java.util.Map;
import java.util.Set;

/**
 * Represent a given model's binds.
 */
public class XFormsModelBinds extends XFormsModelBindsBase {

    public final XFormsModel model;                            // model to which we belong
    private final Model staticModel;

    private final IndentedLogger indentedLogger;
    public final XFormsContainingDocument containingDocument;  // current containing document
    private final XPathDependencies dependencies;

    private XFormsModelSchemaValidator xformsValidator;         // validator for standard XForms schema types

    private boolean isFirstCalculate;                           // whether this is the first recalculate for the associated XForms model

    /**
     * Create an instance of XFormsModelBinds if the given model has xf:bind elements.
     *
     * @param model XFormsModel
     * @return      XFormsModelBinds or null if the model doesn't have xf:bind elements
     */
    public static XFormsModelBinds create(XFormsModel model) {
        return model.getStaticModel().hasBinds() ? new XFormsModelBinds(model) : null;
    }

    private XFormsModelBinds(XFormsModel model) {
        super(model);
        this.model = model;

        this.indentedLogger = model.getIndentedLogger();
        this.containingDocument = model.containingDocument();
        this.dependencies = this.containingDocument.getXPathDependencies();

        this.staticModel = model.getStaticModel();
        this.isFirstCalculate = containingDocument.isInitializing();
    }

    public void resetFirstCalculate() {
        this.isFirstCalculate = true;
    }

    /**
     * Apply calculate binds.
     *
     * @param applyDefaults    whether to apply initial values (@xxf:default="...")
     */
    public void applyCalculateBinds(boolean applyDefaults) {

        if (!staticModel.hasCalculateComputedCustomBind()) {
            // We can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping bind recalculate", "model id", model.getEffectiveId(), "reason", "no recalculation binds");
        } else {
            // This model may have calculation binds

            if (indentedLogger.isDebugEnabled())
                indentedLogger.startHandleOperation("model", "performing bind recalculate", "model id", model.getEffectiveId());
            {
                // 1. Evaluate initial values and calculate before the rest

                if (isFirstCalculate || applyDefaults) {
                    // Handle default values first
                    if (staticModel.hasDefaultValueBind())
                        iterateBinds(new BindRunner() {
                            public void applyBind(BindNode bindNode) {
                                if (bindNode.staticBind().getDefaultValue() != null && dependencies.requireModelMIPUpdate(staticModel, bindNode.staticBind(), Model.DEFAULT(), null))
                                    handleDefaultValueBind(bindNode);
                            }
                        });
                    // This will be false from now on as we have done our first handling of calculate binds
                    isFirstCalculate = false;
                }

                // Handle calculations
                if (staticModel.hasCalculateBind())
                    iterateBinds(new BindRunner() {
                        public void applyBind(BindNode bindNode) {
                            if (bindNode.staticBind().getCalculate() != null && dependencies.requireModelMIPUpdate(staticModel, bindNode.staticBind(), Model.CALCULATE(), null))
                                handleCalculateBind(bindNode);
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
        model.resetAndEvaluateVariables();

        // Apply
        iterateBinds(new BindRunner() {
            public void applyBind(BindNode bindNode) {
                if (bindNode.staticBind().hasCalculateComputedMIPs() || bindNode.staticBind().hasCustomMIPs()) // don't bother if not
                    handleComputedExpressionBind(bindNode);
            }
        });
    }

    /**
     * Apply validation binds
     *
     * @param invalidInstances set filled with invalid instances if any
     */
    public void applyValidationBinds(final Set<String> invalidInstances) {

        if (!staticModel.hasValidateBind()) {
            // We can skip this
            if (indentedLogger.isDebugEnabled())
                indentedLogger.logDebug("model", "skipping bind revalidate", "model id", model.getEffectiveId(), "reason", "no validation binds");
        } else {
            // This model may have validation binds

            // Reset context stack just to re-evaluate the variables
            model.resetAndEvaluateVariables();

            // 1. Validate based on type and requiredness
            if (staticModel.hasTypeBind() || staticModel.hasRequiredBind())
                iterateBinds(new BindRunner() {
                    public void applyBind(BindNode bindNode) {
                        if (bindNode.staticBind().dataTypeOrNull() != null || bindNode.staticBind().getRequired() != null) // don't bother if not
                            validateTypeAndRequired(bindNode, invalidInstances);
                    }
                });

            // 2. Validate constraints
            if (staticModel.hasConstraintBind())
                iterateBinds(new BindRunner() {
                    public void applyBind(BindNode bindNode) {
                        if (bindNode.staticBind().constraintsByLevel().nonEmpty()) // don't bother if not
                            validateConstraint(bindNode, invalidInstances);
                    }
                });
        }
    }

    public Item evaluateBindByType(RuntimeBind bind, int position, QName mipType) throws XPathException {

        final BindNode bindNode = bind.getOrCreateBindNode(position);

        if (mipType.equals(XFormsConstants.RELEVANT_QNAME)) {
            // Relevant
            final Boolean relevant = evaluateRelevantMIP(bindNode);
            return (relevant != null) ? BooleanValue.get(relevant) : null;
        } else if (mipType.equals(XFormsConstants.READONLY_QNAME)) {
            // Readonly
            final Boolean readonly = evaluateReadonlyMIP(bindNode);
            return (readonly != null) ? BooleanValue.get(readonly) : null;
        } else if (mipType.equals(XFormsConstants.REQUIRED_QNAME)) {
            // Required
            final Boolean required = evaluateRequiredMIP(bindNode);
            return (required != null) ? BooleanValue.get(required) : null;
        } else if (mipType.equals(XFormsConstants.TYPE_QNAME)) {
            // Type
            final scala.Option<QName> type = bind.staticBind().dataType();
            return (type.isDefined()) ? new QNameValue(type.get().getNamespacePrefix(), type.get().getNamespaceURI(), type.get().getName(), null) : null;
        } else if (mipType.equals(XFormsConstants.CONSTRAINT_QNAME)) {
            // Constraint
            // TODO: Add support for other constraint levels.
            if (bind.staticBind().constraintsByLevel().nonEmpty())
                return BooleanValue.get(failedConstraintMIPs(ValidationLevels.jErrorLevel(), bindNode).isEmpty());
            else
                return null;
        } else if (mipType.equals(XFormsConstants.CALCULATE_QNAME)) {
            // Calculate
            final String result = evaluateCalculateBind(bindNode);
            return (result != null) ? new StringValue(result) : null;
        } else if (mipType.equals(XFormsConstants.XXFORMS_DEFAULT_QNAME)) {
            // xxf:default
            final String result = evaluateXXFormsDefaultBind(bindNode);
            return (result != null) ? new StringValue(result) : null;
        } else {
            // Try custom MIPs
            final String result = evaluateCustomMIPByName(bindNode, Model.buildCustomMIPName(mipType.getQualifiedName()));
            return (result != null) ? new StringValue(result) : null;
        }
    }

    private String evaluateXXFormsDefaultBind(BindNode bindNode) {
        final StaticBind.XPathMIP defaultMIP = bindNode.staticBind().getDefaultValue();
        if (defaultMIP != null) {
            try {
                return evaluateStringExpression(bindNode, defaultMIP);
            } catch (Exception e) {
                handleMIPXPathException(e, bindNode, defaultMIP, "evaluating XForms default bind");
                return null;
            }
        } else {
            return null;
        }
    }

    private void handleDefaultValueBind(BindNode bindNode) {

        final String stringResult = evaluateXXFormsDefaultBind(bindNode);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and handle this error
            final NodeInfo currentNodeInfo = bindNode.node();
            DataModel.jSetValueIfChanged(
                containingDocument,
                model,
                bindNode.locationData(),
                currentNodeInfo,
                stringResult,
                "default",
                true,
                indentedLogger
            );
        }
    }

    public void handleCalculateBind(BindNode bindNode) {
        final String stringResult = evaluateCalculateBind(bindNode);
        if (stringResult != null) {
            // TODO: Detect if we have already handled this node and handle this error
            final NodeInfo currentNodeInfo = bindNode.node();
            DataModel.jSetValueIfChanged(
                containingDocument,
                model,
                bindNode.locationData(),
                currentNodeInfo,
                stringResult,
                "calculate",
                true,
                indentedLogger
            );
        }
    }

    public String evaluateCalculateBind(BindNode bindNode) {
        // Handle calculate MIP
        final StaticBind.XPathMIP calculateMIP = bindNode.staticBind().getCalculate();
        if (calculateMIP != null) {
            try {
                return evaluateStringExpression(bindNode, calculateMIP);
            } catch (Exception e) {
                handleMIPXPathException(e, bindNode, calculateMIP, "evaluating XForms calculate bind");
                // Blank value so we don't have stale calculated values
                return "";
            }
        } else {
            return null;
        }
    }

    private void handleComputedExpressionBind(BindNode bindNode) {

        final StaticBind staticBind = bindNode.staticBind();

        // Handle relevant, readonly, required, and custom MIPs
        if (staticBind.getRelevant() != null && dependencies.requireModelMIPUpdate(staticModel, staticBind, Model.RELEVANT(), null))
            evaluateAndSetRelevantMIP(bindNode);
        if (staticBind.getReadonly() != null && dependencies.requireModelMIPUpdate(staticModel, staticBind, Model.READONLY(), null) || staticBind.getCalculate() != null)
            evaluateAndSetReadonlyMIP(bindNode);
        if (staticBind.getRequired() != null && dependencies.requireModelMIPUpdate(staticModel, staticBind, Model.REQUIRED(), null))
            evaluateAndSetRequiredMIP(bindNode);

        // TODO: optimize those as well
        evaluateAndSetCustomMIPs(bindNode);
    }

    private void evaluateAndSetRequiredMIP(BindNode bindNode) {
        final Boolean required = evaluateRequiredMIP(bindNode);
        if (required != null) {
            // Update node with MIP value
            bindNode.setRequired(required);
        }
    }

    private Boolean evaluateRequiredMIP(BindNode bindNode) {
        final StaticBind.XPathMIP requiredMIP = bindNode.staticBind().getRequired();
        if (requiredMIP != null) {
            // Evaluate "required" XPath expression on this node
            try {
                // Get MIP value
                return evaluateBooleanExpression(bindNode, requiredMIP);
            } catch (Exception e) {
                handleMIPXPathException(e, bindNode, requiredMIP, "evaluating XForms required bind");
                return ! Model.DEFAULT_REQUIRED(); // https://github.com/orbeon/orbeon-forms/issues/835
            }
        } else {
            return null;
        }
    }

    private void evaluateAndSetReadonlyMIP(BindNode bindNode) {
        final Boolean readonlyMIP = evaluateReadonlyMIP(bindNode);
        if (readonlyMIP != null) {
            bindNode.setReadonly(readonlyMIP);
        } else if (bindNode.staticBind().getCalculate() != null) {
            // The bind doesn't have a readonly attribute, but has a calculate: set readonly to true()
            bindNode.setReadonly(true);
        }
    }

    private Boolean evaluateReadonlyMIP(BindNode bindNode) {
        final StaticBind.XPathMIP readonlyMIP = bindNode.staticBind().getReadonly();
        if (readonlyMIP != null) {
            // The bind has a readonly attribute
            // Evaluate "readonly" XPath expression on this node
            try {
                return evaluateBooleanExpression(bindNode, readonlyMIP);
            } catch (Exception e) {
                handleMIPXPathException(e, bindNode, readonlyMIP, "evaluating XForms readonly bind");
                return ! Model.DEFAULT_READONLY(); // https://github.com/orbeon/orbeon-forms/issues/835
            }
        } else {
            return null;
        }
    }

    private void evaluateAndSetRelevantMIP(BindNode bindNode) {
        final Boolean relevant = evaluateRelevantMIP(bindNode);
        if (relevant != null) {
            bindNode.setRelevant(relevant);
        }
    }

    private Boolean evaluateRelevantMIP(BindNode bindNode) {
        final StaticBind.XPathMIP relevantMIP = bindNode.staticBind().getRelevant();
        if (relevantMIP != null) {
            // Evaluate "relevant" XPath expression on this node
            try {
                return evaluateBooleanExpression(bindNode, relevantMIP);
            } catch (Exception e) {
                handleMIPXPathException(e, bindNode, relevantMIP, "evaluating XForms relevant bind");
                return ! Model.DEFAULT_RELEVANT(); // https://github.com/orbeon/orbeon-forms/issues/835
            }
        } else {
            return null;
        }
    }

    private void validateTypeAndRequired(BindNode bindNode, Set<String> invalidInstances) {

        final StaticBind staticBind = bindNode.staticBind();

        assert staticBind.dataTypeOrNull() != null || staticBind.getRequired() != null;

        // Don't try to apply validity to a node if it has children nodes or if it's not a node
        // "The type model item property is not applied to instance nodes that contain child elements"
        final NodeInfo currentNodeInfo = bindNode.node();
        if (currentNodeInfo == null || bindNode.hasChildrenElements())
            return;

        // NOTE: 2011-02-03: Decided to also apply this to required validation.
        // See: http://forge.ow2.org/tracker/index.php?func=detail&aid=315821&group_id=168&atid=350207

        // Current required value (computed during previous recalculate)
        final boolean isRequired = InstanceData.getRequired(currentNodeInfo);

        // 1. Check type validity

        // Type MIP @type attribute is special:
        //
        // - it is not an XPath expression
        // - but because type validation can be expensive, we want to optimize that if we can
        // - so requireModelMIPUpdate(Model.TYPE) actually means "do we need to update type validity"
        //
        // xxf:xml and xxf:xpath2 also depend on requiredness, which is probably not a good idea. To handle
        // this condition (partially), if the same bind has @type and @required, we also reevaluate type validity if
        // requiredness has changed. Ideally:
        //
        // - we would not depend on requiredness
        // - but if we did, we should handle also the case where another bind is setting requiredness on the node
        //
        final boolean typeValidity;
        if (staticBind.dataType().isDefined()) {
             if (dependencies.requireModelMIPUpdate(staticModel, staticBind, Model.TYPE(), null)
                     || staticBind.getRequired() != null && dependencies.requireModelMIPUpdate(staticModel, staticBind, Model.REQUIRED(), null)) {
                 // Compute new type validity if the value of the node might have changed OR the value of requiredness
                 // might have changed
                typeValidity = validateType(bindNode.parentBind(), currentNodeInfo, isRequired);
                bindNode.setTypeValid(typeValidity, staticBind.typeMIPOpt().get());
             } else {
                 // Keep current value
                typeValidity = bindNode.typeValid();
             }
        } else {
            // Keep current value (defaults to true when no type attribute)
            typeValidity = bindNode.typeValid();
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
        bindNode.setRequiredValid(requiredValidity, staticBind.getRequired());

        // Remember invalid instances
        if (!typeValidity || !requiredValidity) {
            final XFormsInstance instanceForNodeInfo = containingDocument.getInstanceForNode(currentNodeInfo);
            if (instanceForNodeInfo != null)
                invalidInstances.add(instanceForNodeInfo.getEffectiveId());
        }
    }

    private boolean validateType(RuntimeBind bind, NodeInfo currentNodeInfo, boolean required) {

        final StaticBind staticBind = bind.staticBind();

        final boolean typeValid;
        {
            // NOTE: xf:bind/@type is a literal type value, and it is the same that applies to all nodes pointed to by xf:bind/@ref
            final QName typeQName = staticBind.dataType().get();

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

            if (isBuiltInXFormsType && nodeValue.length() == 0) {
                // Don't consider the node invalid if the string is empty with xf:* types
                typeValid = true;
            } else if (isBuiltInXFormsType && "email".equals(typeLocalname)) {
                typeValid = EmailValidator.getInstance(false).isValid(nodeValue);
            } else if (isBuiltInXFormsType && Model.jXFormsSchemaTypeNames().contains(typeLocalname)) {
                // xf:dayTimeDuration, xf:yearMonthDuration, xf:email, xf:card-number
                if (xformsValidator == null) {
                    xformsValidator = new XFormsModelSchemaValidator("oxf:/org/orbeon/oxf/xforms/xforms-types.xsd");
                    xformsValidator.loadSchemas(containingDocument);
                }

                final String validationError =
                    xformsValidator.validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(),
                            staticBind.locationData());

                typeValid = validationError == null;

            } else if (isBuiltInSchemaType || isBuiltInXFormsType) {
                // Built-in schema or XForms type

                // Use XML Schema namespace URI as Saxon doesn't know anything about XForms types
                final String newTypeNamespaceURI = XMLConstants.XSD_URI;

                // Get type information
                final int requiredTypeFingerprint = StandardNames.getFingerprint(newTypeNamespaceURI, typeLocalname);
                if (requiredTypeFingerprint == -1) {
                    throw new ValidationException("Invalid schema type '" + staticBind.dataTypeOrNull() + "'", staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

                // Need an evaluator to check and convert type below
                final XPathEvaluator xpathEvaluator;
                try {
                    xpathEvaluator = new XPathEvaluator(XPath.GlobalConfiguration());
                    // NOTE: Not sure declaring namespaces here is necessary just to perform the cast
                    final IndependentContext context = (IndependentContext) xpathEvaluator.getStaticContext();
                    for (final Map.Entry<String, String> entry : staticBind.namespaceMapping().mapping.entrySet()) {
                        context.declareNamespace(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    throw OrbeonLocationException.wrapException(e, staticBind.locationData());

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
                    // xxf:xml type
                    typeValid = isOptionalAndEmpty || XMLParsing.isWellFormedXML(nodeValue);
                } else if (typeLocalname.equals("xpath2")) {
                    // xxf:xpath2 type

                    // Find element which scopes namespaces
                    final NodeInfo namespaceNodeInfo;
                    if (currentNodeInfo.getNodeKind() == Node.ELEMENT_NODE)
                        namespaceNodeInfo = currentNodeInfo;
                    else
                        namespaceNodeInfo = currentNodeInfo.getParent();

                    if (namespaceNodeInfo != null && namespaceNodeInfo.getNodeKind() == Node.ELEMENT_NODE) {
                        // ASSUMPTION: Binding to dom4j-backed node (which InstanceData assumes too)
                        final Element namespaceElement = XML.unwrapElement(namespaceNodeInfo);
                        final NamespaceMapping namespaceMapping = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(namespaceElement));
                        typeValid = isOptionalAndEmpty || XPath.isXPath2Expression(nodeValue, namespaceMapping, staticBind.locationData(), indentedLogger);
                    } else {
                        // This means that we are bound to a node which is not an element and which does not have a
                        // parent element. This could be a detached attribute, or an element node, etc. Unsure if we
                        // would have made it this far anyway! We can't validate the expression so we only consider
                        // the "optional-and-empty" case.
                        typeValid = isOptionalAndEmpty;
                    }
                } else {
                    throw new ValidationException("Invalid schema type '" + staticBind.dataTypeOrNull() + "'", staticBind.locationData());

                    // TODO: xxx check what XForms event must be dispatched
                }

            } else if (model.hasSchema()) {
                // Other type and there is a schema

                // There are possibly types defined in the schema
                final String validationError
                        = model.schemaValidator().validateDatatype(nodeValue, typeNamespaceURI, typeLocalname, typeQName.getQualifiedName(), staticBind.locationData());

                typeValid = validationError == null;
            } else {
                throw new ValidationException("Invalid schema type '" + staticBind.dataTypeOrNull() + "'", staticBind.locationData());

                // TODO: xxx check what XForms event must be dispatched
            }
        }
        return typeValid;
    }

    public static boolean isEmptyValue(String value) {
        // TODO: configurable notion of "empty" through property (trimming vs. strict)
        return "".equals(value);
    }

    public static interface BindRunner {
        public void applyBind(BindNode bind);
    }
}
