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
import org.dom4j.QName;
import org.orbeon.oxf.util.PropertyContext;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.dom4j.DocumentWrapper;

import java.util.HashMap;
import java.util.Map;

public class ContainerAnalysis extends ControlAnalysis {

    private Map<String, VariableAnalysis> containedVariables;

    public ContainerAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo,
                             XBLBindings.Scope scope, Element element, int index, boolean isValueControl,
                             ContainerAnalysis parentControlAnalysis, Map<String, SimpleAnalysis> inScopeVariables) {
        super(propertyContext, staticState, controlsDocumentInfo, scope, element, index, isValueControl, parentControlAnalysis, inScopeVariables);
    }

    // Constructor for root
    public ContainerAnalysis(XFormsStaticState staticState, int index, XBLBindings.Scope scope) {
        super(staticState, index, scope);
    }

    @Override
    protected Element findNestedLHHAElement(PropertyContext propertyContext, DocumentWrapper controlsDocumentInfo, QName qName) {
        // For e.g. <xforms:group>, consider only nested element without @for attribute
        // NOTE: Should probably be child::xforms:label[not(exists(@for))] to get first such element, but e.g. group
        // label if any should probably be first anyway.
        final Element e = element.element(qName);
        if (e != null) {
            final String forAttribute = e.attributeValue("for");
            return (forAttribute == null) ? e : null;
        } else {
            return null;
        }
    }

    public void addContainedVariable(String variableName, VariableAnalysis variablePrefixedId) {
        if (containedVariables == null)
            containedVariables = new HashMap<String, VariableAnalysis>();
        containedVariables.put(variableName, variablePrefixedId);
    }

    public Map<String, SimpleAnalysis> getInScopeViewVariablesForContained() {

        // NOTE: We don't scope model variables as this method must only return view variables

        // Add all of parent's in-scope view variables
        final Map<String, SimpleAnalysis> result = new HashMap<String, SimpleAnalysis>();
        result.putAll(getViewVariables());

        // Add all new view variables in the container so far
        if (containedVariables != null) {
            result.putAll(containedVariables);
        }

        return result;
    }

    @Override
    public void freeTransientState() {
        // Free this since this information is only useful while building sub-controls
        containedVariables = null;
    }
}
