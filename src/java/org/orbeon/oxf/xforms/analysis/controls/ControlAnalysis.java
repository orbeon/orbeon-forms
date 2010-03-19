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
                this.bindingAnalysis = (bindingExpression != null) ? analyzeXPath(staticState, parentControlAnalysis.bindingAnalysis,
                        prefixedId, bindingExpression) : null;

                final boolean isVariable = this instanceof VariableAnalysis;
                // TODO: TEMP: later other controls will do value analysis
                this.valueAnalysis = isVariable ? analyzeVariableValue(staticState, prefixedId, element.attributeValue("select")) : null;
            } else {
                bindingAnalysis = null;
                valueAnalysis = null;
            }
        } else {
            bindingAnalysis = null;
            valueAnalysis = null;
        }
    }

    private XPathAnalysis analyzeXPath(XFormsStaticState staticState, XPathAnalysis baseAnalysis, String prefixedId, String xpathString) {
        // Create new expression
        // TODO: get expression from pool and pass in-scope variables (probably more efficient)
        final Expression expression = XPathCache.createExpression(staticState.getXPathConfiguration(), xpathString,
                staticState.getMetadata().namespaceMappings.get(prefixedId), XFormsContainingDocument.getFunctionLibrary());
        // Analyse it
        return new XPathAnalysis(staticState, expression, xpathString, baseAnalysis, inScopeVariables);

    }

    public XPathAnalysis analyzeVariableValue(XFormsStaticState staticState, String prefixedId, String xpathString) {
        // Get analysis for variable value
        // TODO: handle xxf:sequence child of variable
        final XPathAnalysis baseAnalysis = (bindingAnalysis != null) ? bindingAnalysis : parentControlAnalysis.bindingAnalysis;
        return analyzeXPath(staticState, baseAnalysis, prefixedId, xpathString);
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

    public int getLevel() {
        if (parentControlAnalysis == null)
            return 0;
        else
            return parentControlAnalysis.getLevel() + 1;
    }
}