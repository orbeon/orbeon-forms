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
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.xbl.XBLBindings;

import java.util.HashMap;
import java.util.Map;

public class ViewAnalysis extends SimpleAnalysis {

    private String modelPrefixedId;

    private final Map<String, SimpleAnalysis> viewVariables;

    private Map<String, SimpleAnalysis> inScopeVariables;

    public ViewAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentControlAnalysis, Map<String, SimpleAnalysis> inScopeVariables, boolean canHoldValue) {
        super(staticState, scope, element, parentControlAnalysis, canHoldValue);
        this.viewVariables = inScopeVariables;
    }

    public String getModelPrefixedId() {
        if (element != null) {
            if (modelPrefixedId == null) {

                // Find inherited model
                final String inheritedModelPrefixedId; {
                    final SimpleAnalysis ancestor = getAncestorControlAnalysisInScope();
                    if (ancestor != null) {
                        // There is an ancestor control in the same scope, use its model id
                        inheritedModelPrefixedId = ancestor.getModelPrefixedId();
                    } else {
                        // Top-level control in a new scope, use default model id for scope
                        inheritedModelPrefixedId = staticState.getDefaultModelPrefixedIdForScope(scope);
                    }
                }

                // Check for @model attribute
                final String localModelId = element.attributeValue("model");
                if (localModelId != null) {
                    // Get model prefixed id and verify it belongs to this scope
                    final String localModelPrefixedId = scope.getPrefixedIdForStaticId(localModelId);
                    if (staticState.getModel(localModelPrefixedId) == null)
                        throw new ValidationException("Reference to non-existing model id: " + localModelId, locationData);
                    modelPrefixedId = localModelPrefixedId;

//                    if (!modelPrefixedId.equals(inheritedModelPrefixedId)) {
//                        // Model has changed
//                    }
                } else {
                    // Just inherit
                    modelPrefixedId = inheritedModelPrefixedId;
                }
            }
            return modelPrefixedId;
        } else {
            return null;
        }
    }

    public String getDefaultInstancePrefixedId() {
        final Model model = staticState.getModel(getModelPrefixedId());
        return (model != null) ? model.defaultInstancePrefixedId : null;
    }

    @Override
    public Map<String, SimpleAnalysis> getInScopeVariables() {
        if (inScopeVariables == null) {
            final Model model = staticState.getModel(getModelPrefixedId());
            inScopeVariables = new HashMap<String, SimpleAnalysis>();
            if (model != null)
                inScopeVariables.putAll(model.variables);
            inScopeVariables.putAll(viewVariables);
        }
        return inScopeVariables;
    }

    public Map<String, SimpleAnalysis> getViewVariables() {
        return viewVariables;
    }

    private SimpleAnalysis getAncestorControlAnalysisInScope() {
        SimpleAnalysis currentControlAnalysis = parentAnalysis;
        while (currentControlAnalysis != null) {

            if (currentControlAnalysis.scope.equals(scope)) {
                return currentControlAnalysis;
            }

            currentControlAnalysis = currentControlAnalysis.parentAnalysis;
        }

        return null;
    }
}
