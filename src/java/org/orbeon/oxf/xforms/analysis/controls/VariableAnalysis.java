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
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xforms.analysis.XPathAnalysis;
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.dom4j.DocumentWrapper;

import java.util.Map;

public class VariableAnalysis extends ControlAnalysis {

    public final String name;

    public VariableAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo,
                            XBLBindings.Scope scope, Element element, int index, boolean isValueControl, ContainerAnalysis parentControlAnalysis,
                            Map<String, SimpleAnalysis> inScopeVariables) {
        super(propertyContext, staticState, controlsDocumentInfo, scope, element, index, isValueControl, parentControlAnalysis, inScopeVariables);

        // Gather variable information
        this.name = element.attributeValue("name");
        parentControlAnalysis.addContainedVariable(this.name, this);
    }

    @Override
    protected XPathAnalysis computeValueAnalysis() {
        // TODO: handle xxf:sequence
        final String selectAttribute = element.attributeValue("select");
        if (selectAttribute != null) {
            final XPathAnalysis baseAnalysis = findOrCreateBaseAnalysis(true);
            return analyzeXPath(staticState, baseAnalysis, prefixedId, selectAttribute);
        } else {
            // Value is constant
            return XPathAnalysis.CONSTANT_ANALYSIS;
        }
    }
}
