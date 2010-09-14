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
import org.orbeon.oxf.xforms.analysis.model.Model;
import org.orbeon.oxf.xforms.xbl.XBLBindings;

import java.util.*;

public class ViewAnalysis extends SimpleAnalysis {

    private final Map<String, SimpleAnalysis> viewVariables;

    private Map<String, SimpleAnalysis> inScopeVariables;

    public ViewAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentAnalysis, Map<String, SimpleAnalysis> inScopeVariables, boolean canHoldValue) {
        super(staticState, scope, element, parentAnalysis, canHoldValue, findContainingModel(staticState, element, parentAnalysis, scope));
        this.viewVariables = inScopeVariables;
    }

    // Constructor for root
    protected ViewAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope) {
        super(staticState, scope, null, null, false, staticState.getDefaultModelForScope(scope));
        this.viewVariables = Collections.emptyMap();
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
}
