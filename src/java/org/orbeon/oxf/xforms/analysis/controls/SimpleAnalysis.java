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
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.*;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.expr.Expression;

import java.util.Map;

/**
 * Hold the static analysis for an XForms control.
 */
public class SimpleAnalysis {

    public final XFormsStaticState staticState;

    public final XBLBindings.Scope scope;
    public final String prefixedId;
    public final Element element;
    public final LocationData locationData;
    
    public final boolean hasNodeBinding;
    public final boolean canHoldValue;

    public final SimpleAnalysis parentControlAnalysis;
    public final Map<String, ControlAnalysis> inScopeVariables; // variable name -> ControlAnalysis

    public final String modelPrefixedId;

    public final XPathAnalysis bindingAnalysis;
    public final XPathAnalysis valueAnalysis;

    public SimpleAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentControlAnalysis,
                          Map<String, ControlAnalysis> inScopeVariables, boolean canHoldValue) {

        this.staticState = staticState;
        this.scope = scope;
        this.prefixedId = (element != null) ? scope.getPrefixedIdForStaticId(XFormsUtils.getElementStaticId(element)) : "#controls";
        this.element = element;
        this.locationData = (element != null) ? new ExtendedLocationData((LocationData) element.getData(), "gathering static control information", element) : null;
        this.parentControlAnalysis = parentControlAnalysis;
        this.inScopeVariables = inScopeVariables;
        this.canHoldValue = canHoldValue;

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

        this.modelPrefixedId = computeModelPrefixedId();

        if (staticState.isXPathAnalysis()) {
            this.bindingAnalysis = computeBindingAnalysis(element);
            this.valueAnalysis = computeValueAnalysis();
        } else {
            this.bindingAnalysis = null;
            this.valueAnalysis = null;
        }
    }

    protected String computeModelPrefixedId() {
        if (element != null) {
            final String localModelId = element.attributeValue("model");
            if (localModelId != null) {
                // Get model prefixed id and verify it belongs to this scope
                final String localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelId);
                if (staticState.getModel(localModelPrefixedId) == null)
                    throw new ValidationException("Reference to non-existing model id: " + localModelId, locationData);
                return localModelPrefixedId;
            } else {
                final SimpleAnalysis ancestor = getAncestorControlAnalysisInScope();
                if (ancestor != null) {
                    // There is an ancestor control in the same scope, use its model id
                    return ancestor.modelPrefixedId;
                } else {
                    // Top-level control in a new scope, use default model id for scope
                    return staticState.getDefaultModelPrefixedIdForScope(scope);
                }
            }
        } else {
            return null;
        }
    }

    protected String getDefaultInstancePrefixedId() {
        return staticState.getModel(modelPrefixedId).defaultInstancePrefixedId;
    }

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

            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis();
            if ((bindingExpression != null)) {
                // New binding expression
                return analyzeXPath(staticState, baseAnalysis, prefixedId, bindingExpression);
            } else {
                // TODO: TEMP: just do this for now so that controls w/o their own binding also get binding updated
                return baseAnalysis;
            }
        } else {
            return null;
        }
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

    protected XPathAnalysis computeValueAnalysis() {
        if (element != null) {
            if (canHoldValue) {
                // Regular value control
                final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis();
                return analyzeValueXPath(baseAnalysis, element, prefixedId);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected XPathAnalysis findOrCreateBaseAnalysis() {
        final XPathAnalysis baseAnalysis;
        final XPathAnalysis ancestorOrSelf = getAncestorOrSelfBindingAnalysis();
        if (ancestorOrSelf != null) {
            // There is an ancestor control in the same scope with same model, use its analysis as base
            baseAnalysis = ancestorOrSelf;
        } else {
            // We are a top-level control in a scope/model combination, create analysis
            if (modelPrefixedId != null) {
                final Model model = staticState.getModel(modelPrefixedId);
                if (model.defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    baseAnalysis = analyzeXPath(staticState, null, prefixedId, XPathAnalysis.buildInstanceString(model.defaultInstancePrefixedId));
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

    private SimpleAnalysis getAncestorControlAnalysisInScope() {
        SimpleAnalysis currentControlAnalysis = parentControlAnalysis;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentControlAnalysis;
        }

        return null;
    }

    private XPathAnalysis getAncestorOrSelfBindingAnalysis() {
        SimpleAnalysis currentControlAnalysis = this;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.bindingAnalysis != null
                    && currentControlAnalysis.modelPrefixedId.equals(modelPrefixedId)
                    && currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis.bindingAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentControlAnalysis;
        }

        return null;
    }

    protected XPathAnalysis analyzeValueXPath(XPathAnalysis baseAnalysis, Element element, String prefixedId) {
            final String valueAttribute = element.attributeValue("value");
            if (valueAttribute != null) {
                // E.g. xforms:output/@value
                return analyzeXPath(staticState, baseAnalysis, prefixedId, valueAttribute);
            } else {
                // Value is considered the string value
                return analyzeXPath(staticState, baseAnalysis, prefixedId, "string()");
            }
        }

    protected XPathAnalysis analyzeXPath(XFormsStaticState staticState, XPathAnalysis baseAnalysis, String prefixedId, String xpathString) {
        // Create new expression
        // TODO: get expression from pool and pass in-scope variables (probably more efficient)
        final Expression expression = XPathCache.createExpression(staticState.getXPathConfiguration(), xpathString,
                staticState.getMetadata().getNamespaceMapping(prefixedId), XFormsContainingDocument.getFunctionLibrary());
        // Analyse it
        return new XPathAnalysis(staticState, expression, xpathString, baseAnalysis, inScopeVariables, scope, modelPrefixedId, getDefaultInstancePrefixedId());

    }

    public int getLevel() {
        if (parentControlAnalysis == null)
            return 0;
        else
            return parentControlAnalysis.getLevel() + 1;
    }
}