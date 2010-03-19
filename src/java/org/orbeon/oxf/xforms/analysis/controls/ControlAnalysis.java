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
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.util.XPathCache;
import org.orbeon.oxf.xforms.XFormsContainingDocument;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.expr.Expression;
import org.orbeon.saxon.expr.PathMap;

import java.util.HashMap;
import java.util.Map;

public class ControlAnalysis {

    public final XFormsStaticState staticState;

    public final String prefixedId;
    public final Element element;
    public final LocationData locationData;
    public final int index;
    public final boolean hasNodeBinding;
    public final boolean isValueControl;
    public final ControlAnalysis parentControlAnalysis;
    public final ControlAnalysis ancestorRepeat;
    public final Map<String, ControlAnalysis> inScopeVariables; // variable name -> ControlAnalysis

    public final XPathAnalysis bindingAnalysis;
    public final XPathAnalysis valueAnalysis;

    // TODO: move to ContainerAnalysis
    private Map<String, String> containedVariables; // variable name -> prefixed id

    private String classes;

    public ControlAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo, String prefixedId,
                       Element element, LocationData locationData, int index, boolean hasNodeBinding, boolean isValueControl,
                       ControlAnalysis parentControlAnalysis, ControlAnalysis ancestorRepeat, Map<String, ControlAnalysis> inScopeVariables) {

        this.staticState = staticState;
        this.prefixedId = prefixedId;
        this.element = element;
        this.locationData = locationData;
        this.index = index;
        this.hasNodeBinding = hasNodeBinding;
        this.isValueControl = isValueControl;
        this.parentControlAnalysis = parentControlAnalysis;
        this.ancestorRepeat = ancestorRepeat;
        this.inScopeVariables = inScopeVariables;

        if (element != null) {

            // XPath analysis if needed
            if (staticState.isXPathAnalysis() && element != null) {
                final String bindingExpression;
                final String ref = element.attributeValue("ref");
                if (ref != null) {
                    bindingExpression = ref;
                } else {
                    bindingExpression = element.attributeValue("nodeset");
                }
    //            final String parentPrefixedId = (parentControlAnalysis != null) ? parentControlAnalysis.prefixedId : null;
                this.bindingAnalysis = (bindingExpression != null) ? analyseXPath(staticState, prefixedId, bindingExpression) : null;

                final boolean isVariable = this instanceof VariableAnalysis;
                // TODO: TEMP: later other controls will do value analysis
                this.valueAnalysis = isVariable ? getVariableValueAnalysis(staticState, prefixedId, element.attributeValue("select")) : null;
            } else {
                bindingAnalysis = null;
                valueAnalysis = null;
            }
        } else {
            bindingAnalysis = null;
            valueAnalysis = null;
        }
    }

    private XPathAnalysis analyseXPath(XFormsStaticState staticState, String prefixedId, String xpathString) {
        // Pass null context item as we don't care about actually running the expression
        // TODO: get expression from pool and pass in-scope variables (probably more efficient)
//            final PooledXPathExpression pooledXPathExpression
//                    = XPathCache.getXPathExpression(propertyContext, null, xpathString, metadata.namespaceMappings.get(prefixedId),
//                        null, XFormsContainingDocument.getFunctionLibrary(), null, null);
//        try {
//        } finally {
//                pooledXPathExpression.returnToPool();
//        }

        final Expression expression = XPathCache.createExpression(staticState.getXPathConfiguration(), xpathString, staticState.getMetadata().namespaceMappings.get(prefixedId), XFormsContainingDocument.getFunctionLibrary());
            final XPathAnalysis localPathAnalysis = analyzeExpression(staticState, expression, xpathString);
            localPathAnalysis.rebase(parentControlAnalysis.bindingAnalysis, inScopeVariables);
            localPathAnalysis.processPaths();
            return localPathAnalysis;

    }

    public XPathAnalysis getVariableValueAnalysis(XFormsStaticState staticState, String prefixedId, String xpathString) {
        // Get analysis for variable value
        // TODO: handle xxf:sequence child of variable
//        if (bindingAnalysis != null) {
//            // Use the variable's binding
//            return analyseXPath(staticState, prefixedId, xpathString);
//        } else {
//            // Use the parent's binding
            return analyseXPath(staticState, prefixedId, xpathString);
//        }
    }

    public void addContainedVariable(String variableName, String variablePrefixedId) {
        if (containedVariables == null)
            containedVariables = new HashMap<String, String>();
        containedVariables.put(variableName, variablePrefixedId);
    }

    public void clearContainedVariables() {
        // TODO: when to call this? should call to free memory
        containedVariables = null;
    }

    public Map<String, ControlAnalysis> getInScopeVariablesForContained(Map<String, ControlAnalysis> controlAnalysisMap) {
        if (inScopeVariables == null && containedVariables == null) {
            // No variables at all
            return null;
        } else if (containedVariables == null) {
            // No contained variables
            return inScopeVariables;
        } else {
            // Contained variables
            final Map<String, ControlAnalysis> result = new HashMap<String, ControlAnalysis>();
            // Add all of parent's in-scope variables
            if (inScopeVariables != null)
                result.putAll(inScopeVariables);
            // Add all new variables so far
            for (final Map.Entry<String, String> entry: containedVariables.entrySet()) {
                result.put(entry.getKey(), controlAnalysisMap.get(entry.getValue()));
            }

            return result;
        }
    }

    private XPathAnalysis analyzeExpression(XFormsStaticState staticState, Expression expression, String xpathString) {
        try {
            final PathMap pathmap = new PathMap(expression);
            final int dependencies = expression.getDependencies();
            return new XPathAnalysis(xpathString, pathmap, dependencies);
        } catch (Exception e) {
            staticState.getIndentedLogger().logError("", "EXCEPTION WHILE ANALYZING PATHS: " + xpathString);
            return null;
        }
    }

    public void addClasses(String classes) {
        if (this.classes == null) {
            // Set
            this.classes = classes;
        } else {
            // Append
            this.classes = this.classes + ' ' + classes;
        }
    }

    public String getClasses() {
        return classes;
    }
}