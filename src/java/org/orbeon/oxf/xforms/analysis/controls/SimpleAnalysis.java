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
package org.orbeon.oxf.xforms.analysis.controls;

import org.dom4j.Element;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.Map;

/**
 * Hold the static analysis for an XForms object.
 */
public abstract class SimpleAnalysis {

    public final XFormsStaticState staticState;

    public final Element element;
    public final LocationData locationData;

    public final XBLBindings.Scope scope;
    public final String prefixedId;

    public final boolean canHoldValue;
    public final Model containingModel;

    public final boolean hasNodeBinding;

    public final SimpleAnalysis parentAnalysis;

    private XPathAnalysis bindingAnalysis;
    private XPathAnalysis valueAnalysis;

    public SimpleAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentAnalysis, boolean canHoldValue, Model containingModel) {

        this.staticState = staticState;

        this.parentAnalysis = parentAnalysis;

        this.element = element;
        this.locationData = createLocationData(element);

        this.scope = scope;
        this.prefixedId = (element != null) ? scope.getPrefixedIdForStaticId(XFormsUtils.getElementStaticId(element)) : "#controls";

        this.canHoldValue = canHoldValue;
        this.containingModel = containingModel;

        if (element != null) {
            final boolean hasBind = element.attribute("bind") != null;
            final boolean hasRef = element.attribute("ref") != null;
            final boolean hasNodeset = element.attribute("nodeset") != null;

            // TODO: check for mandatory bindings
//            if (XFormsConstants.XFORMS_NAMESPACE_URI.equals(controlURI)) {
//                        if (XFormsControlFactory.MANDATORY_SINGLE_NODE_CONTROLS.get(controlName) != null && !(hasRef || hasBind)) {
//                            throw new ValidationException("Missing mandatory single node binding for element: " + controlElement.getQualifiedName(), locationData);
//                        }
//                        if (XFormsControlFactory.NO_SINGLE_NODE_CONTROLS.get(controlName) != null && (hasRef || hasBind)) {
//                            throw new ValidationException("Single node binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
//                        }
//                        if (XFormsControlFactory.MANDATORY_NODESET_CONTROLS.get(controlName) != null && !(hasRef || hasNodeset || hasBind)) {
//                            throw new ValidationException("Missing mandatory nodeset binding for element: " + controlElement.getQualifiedName(), locationData);
//                        }
//                        if (XFormsControlFactory.NO_NODESET_CONTROLS.get(controlName) != null && hasNodeset) {
//                            throw new ValidationException("Node-set binding is prohibited for element: " + controlElement.getQualifiedName(), locationData);
//                        }
//                        if (XFormsControlFactory.SINGLE_NODE_OR_VALUE_CONTROLS.get(controlName) != null && !(hasRef || hasBind || controlElement.attribute("value") != null)) {
//                            throw new ValidationException("Missing mandatory single node binding or value attribute for element: " + controlElement.getQualifiedName(), locationData);
//                        }
//            }

            this.hasNodeBinding = hasBind || hasRef || hasNodeset;
        } else {
            this.hasNodeBinding = false;
        }
    }

    public final void analyzeXPath() {
        bindingAnalysis = computeBindingAnalysis(element);
        valueAnalysis = computeValueAnalysis();
    }

    public final XPathAnalysis getBindingAnalysis() {
        return bindingAnalysis;
    }

    public final XPathAnalysis getValueAnalysis() {
        return valueAnalysis;
    }

    public final String getModelPrefixedId() {
        return (containingModel != null) ? containingModel.prefixedId : null;
    }
    public final String getDefaultInstancePrefixedId() {
        return (containingModel != null) ? containingModel.defaultInstancePrefixedId : null;
    }

    // TODO: can we just pass this at construction?
    public abstract Map<String, SimpleAnalysis> getInScopeVariables();

    protected XPathAnalysis computeBindingAnalysis(Element element) {
        if (element != null) {
            // TODO: handle @context
            if (element.attributeValue("context") != null) {
                return XPathAnalysis.CONSTANT_NEGATIVE_ANALYSIS;
            }

            // TODO: handle @bind
            if (element.attributeValue("bind") != null) {
                return XPathAnalysis.CONSTANT_NEGATIVE_ANALYSIS;
            }

            final String bindingExpression = getBindingExpression(element);

            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(parentAnalysis);
            if ((bindingExpression != null)) {
                // New binding expression
                return analyzeXPath(staticState, baseAnalysis, prefixedId, bindingExpression);
            } else {
                // TODO: TEMP: Control does not have a binding. But return one anyway so that controls w/o their own binding also get updated.
                return baseAnalysis;
            }
        } else {
            return null;
        }
    }


    protected XPathAnalysis computeValueAnalysis() {
        if (element != null) {
            if (canHoldValue) {
                // Regular value analysis
                final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(this);
                return analyzeValueXPath(baseAnalysis, element, prefixedId);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected XPathAnalysis findOrCreateBaseAnalysis(SimpleAnalysis startAnalysis) {
        return findOrCreateBaseAnalysis(startAnalysis, scope, containingModel);
    }

    protected XPathAnalysis findOrCreateBaseAnalysis(SimpleAnalysis startAnalysis, XBLBindings.Scope scope, Model containingModel) {
        final XPathAnalysis baseAnalysis;
        final XPathAnalysis ancestorOrSelf = getAncestorOrSelfBindingAnalysis(startAnalysis, scope, containingModel);
        if (ancestorOrSelf != null) {
            // There is an ancestor in the same scope with same model, use its analysis as base
            baseAnalysis = ancestorOrSelf;
        } else {
            // We are top-level in a scope/model combination, create analysis
            if (containingModel != null) {
                if (containingModel.defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    baseAnalysis = new XPathAnalysis(staticState, XPathAnalysis.buildInstanceString(containingModel.defaultInstancePrefixedId),
                            null, null, null, scope, containingModel.prefixedId, containingModel.defaultInstancePrefixedId, locationData, element);
                } else {
                    // No instance
                    baseAnalysis = null;
                }
            } else {
                // No model
                baseAnalysis = null;
            }
        }
        return baseAnalysis;
    }

    private XPathAnalysis getAncestorOrSelfBindingAnalysis(SimpleAnalysis startAnalysis, XBLBindings.Scope scope, Model containingModel) {
        SimpleAnalysis currentAnalysis = startAnalysis;
        while (currentAnalysis != null) {

            if (currentAnalysis.getBindingAnalysis() != null
                    && XFormsUtils.compareStrings(currentAnalysis.getModelPrefixedId(), (containingModel != null) ? containingModel.prefixedId : null) // support null model
                    && currentAnalysis.scope.equals(scope)) {
                return currentAnalysis.getBindingAnalysis();
            }

            currentAnalysis = currentAnalysis.parentAnalysis;
        }

        return null;
    }

    protected XPathAnalysis analyzeValueXPath(XPathAnalysis baseAnalysis, Element element, String prefixedId) {
        // Two cases: e.g. xforms:output/@value, or the current item
        final String valueAttribute = element.attributeValue("value");
        final String subExpression = (valueAttribute != null) ? ("(" + valueAttribute + ")") : ".";
        return analyzeXPath(staticState, baseAnalysis, prefixedId, "xs:string(" + subExpression + "[1])");
    }

    protected XPathAnalysis analyzeXPath(XFormsStaticState staticState, XPathAnalysis baseAnalysis, String prefixedId, String xpathString) {
        return new XPathAnalysis(staticState, xpathString, staticState.getMetadata().getNamespaceMapping(prefixedId),
                baseAnalysis, getInScopeVariables(), scope, getModelPrefixedId(), getDefaultInstancePrefixedId(), locationData, element);
    }

    public int getLevel() {
        if (parentAnalysis == null)
            return 0;
        else
            return parentAnalysis.getLevel() + 1;
    }

    public void freeTransientState() {
        if (getBindingAnalysis() != null)
            getBindingAnalysis().freeTransientState();
        if (getValueAnalysis() != null)
            getValueAnalysis().freeTransientState();
    }

    /* Companion object methods */

    protected static ExtendedLocationData createLocationData(Element element) {
        return (element != null) ? new ExtendedLocationData((LocationData) element.getData(), "gathering static XPath information", element) : null;
    }

    public static String getBindingExpression(Element element) {
        final String bindingExpression;
        final String ref = element.attributeValue("ref");
        if (ref != null) {
            bindingExpression = ref;
        } else {
            bindingExpression = element.attributeValue("nodeset");
        }
        return bindingExpression;
    }

    protected static SimpleAnalysis getAncestorAnalysisInScope(SimpleAnalysis parentAnalysis, XBLBindings.Scope scope) {
        SimpleAnalysis currentControlAnalysis = parentAnalysis;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentAnalysis;
        }

        return null;
    }

    protected static Model findContainingModel(XFormsStaticState staticState, Element element, SimpleAnalysis parentAnalysis, XBLBindings.Scope scope) {
        if (element != null) {

            final Model newContainingModel;

            // Find inherited model
            final Model inheritedContainingModel; {
                final SimpleAnalysis ancestor = getAncestorAnalysisInScope(parentAnalysis, scope);
                if (ancestor != null) {
                    // There is an ancestor control in the same scope, use its model id
                    inheritedContainingModel = ancestor.containingModel;
                } else {
                    // Top-level control in a new scope, use default model id for scope
                    inheritedContainingModel = staticState.getDefaultModelForScope(scope);
                }
            }

            // Check for @model attribute
            final String localModelId = element.attributeValue("model");
            if (localModelId != null) {
                // Get model prefixed id and verify it belongs to this scope
                final String localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelId);
                if (staticState.getModel(localModelPrefixedId) == null)
                    throw new ValidationException("Reference to non-existing model id: " + localModelId, createLocationData(element));
                newContainingModel = staticState.getModel(localModelPrefixedId);
            } else {
                // Just use inherited model
                newContainingModel = inheritedContainingModel;
            }
            return newContainingModel;
        } else {
            return null;
        }
    }
}