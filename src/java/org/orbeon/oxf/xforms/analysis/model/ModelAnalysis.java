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
package org.orbeon.oxf.xforms.analysis.model;

import org.dom4j.Element;
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;

import java.util.Map;

public class ModelAnalysis extends SimpleAnalysis {

    private final String modelPrefixedId;
    private final String defaultInstancePrefixedId;
    private final Map<String, SimpleAnalysis> inScopeVariables;

    public ModelAnalysis(XFormsStaticState staticState, XBLBindings.Scope scope, Element element, SimpleAnalysis parentControlAnalysis,
                         Map<String, SimpleAnalysis> inScopeVariables, boolean canHoldValue, String modelPrefixedId, String defaultInstancePrefixedId) {
        super(staticState, scope, element, parentControlAnalysis, canHoldValue);
        this.modelPrefixedId = modelPrefixedId;
        this.defaultInstancePrefixedId = defaultInstancePrefixedId;
        this.inScopeVariables = inScopeVariables;
    }

    @Override
    public String getModelPrefixedId() {
        return modelPrefixedId;
    }

    @Override
    public String getDefaultInstancePrefixedId() {
        return defaultInstancePrefixedId;
    }

    @Override
    public Map<String, SimpleAnalysis> getInScopeVariables() {
        return inScopeVariables;
    }
}
