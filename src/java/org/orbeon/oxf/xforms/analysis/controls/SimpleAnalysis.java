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
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.XFormsUtils;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.oxf.xml.dom4j.ExtendedLocationData;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.Map;

/**
 * Hold the static analysis for an XForms control.
 */
public abstract class SimpleAnalysis {

    public final XFormsStaticState staticState;

    public final XBLBindings.Scope scope;
    public final String prefixedId;
    public final Element element;
    public final LocationData locationData;
    
    public final boolean hasNodeBinding;
    public final boolean canHoldValue;

    public final SimpleAnalysis parentAnalysis;

    private boolean bindingAnalyzed = false;
    private boolean valueAnalyzed = false;
    private XPathAnalysis bindingAnalysis;
    private XPathAnalysis valueAnalysis;

    public SimpleAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentAnalysis, boolean canHoldValue) {

        this.staticState = staticState;
        this.scope = scope;
        this.prefixedId = (element != null) ? scope.getPrefixedIdForStaticId(XFormsUtils.getElementStaticId(element)) : "#controls";
        this.element = element;
        this.locationData = (element != null) ? new ExtendedLocationData((LocationData) element.getData(), "gathering static XPath information", element) : null;
        this.parentAnalysis = parentAnalysis;
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

        if (!staticState.isXPathAnalysis()) {
            // Don't perform analysis because it's not enabled
            this.bindingAnalyzed = this.valueAnalyzed = true;
            this.bindingAnalysis = null;
            this.valueAnalysis = null;
        }
    }

    public XPathAnalysis getBindingAnalysis() {
        if (!bindingAnalyzed) {
            bindingAnalysis = computeBindingAnalysis(element);
            bindingAnalyzed = true;
        }
        return bindingAnalysis;
    }

    public XPathAnalysis getValueAnalysis() {
        if (!valueAnalyzed) {
            valueAnalysis = computeValueAnalysis();
            valueAnalyzed = true;
        }
        return valueAnalysis;
    }

    public abstract String getModelPrefixedId();
    public abstract String getDefaultInstancePrefixedId();
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

            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(false);
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
                // Regular value analysis
                final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(true);
                return analyzeValueXPath(baseAnalysis, element, prefixedId);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    protected XPathAnalysis findOrCreateBaseAnalysis(boolean useSelf) {
        final XPathAnalysis baseAnalysis;
        final XPathAnalysis ancestorOrSelf = getAncestorOrSelfBindingAnalysis(useSelf);
        if (ancestorOrSelf != null) {
            // There is an ancestor in the same scope with same model, use its analysis as base
            baseAnalysis = ancestorOrSelf;
        } else {
            // We are top-level in a scope/model combination, create analysis
            if (getModelPrefixedId() != null) {
                final Model model = staticState.getModel(getModelPrefixedId());
                if (model.defaultInstancePrefixedId != null) {
                    // Start with instance('defaultInstanceId')
                    baseAnalysis = new XPathAnalysis(staticState, XPathAnalysis.buildInstanceString(model.defaultInstancePrefixedId),
                            null, null, null, scope, model.prefixedId, model.defaultInstancePrefixedId, locationData, element);
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

    private XPathAnalysis getAncestorOrSelfBindingAnalysis(boolean useSelf) {
        SimpleAnalysis currentAnalysis = useSelf ? this : parentAnalysis;
        while (currentAnalysis != null) {

            if (currentAnalysis.getBindingAnalysis() != null
                    && XFormsUtils.compareStrings(currentAnalysis.getModelPrefixedId(), getModelPrefixedId()) // support null model
                    && currentAnalysis.scope.equals(scope)) {
                return currentAnalysis.getBindingAnalysis();
            }

            currentAnalysis = currentAnalysis.parentAnalysis;
        }

        return null;
    }

    protected XPathAnalysis analyzeValueXPath(XPathAnalysis baseAnalysis, Element element, String prefixedId) {
            final String valueAttribute = element.attributeValue("value");
            if (valueAttribute != null) {
                // E.g. xforms:output/@value
                return analyzeXPath(staticState, baseAnalysis, prefixedId, "xs:string((" + valueAttribute + ")[1])");
            } else {
                // Value is considered the string value
                return analyzeXPath(staticState, baseAnalysis, prefixedId, "xs:string(.[1])");
            }
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
}