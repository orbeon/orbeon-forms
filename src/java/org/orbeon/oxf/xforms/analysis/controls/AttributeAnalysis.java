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
import org.orbeon.oxf.xforms.xbl.XBLBindings;
import org.orbeon.saxon.dom4j.DocumentWrapper;

import java.util.Map;

public class AttributeAnalysis extends ControlAnalysis {
    public AttributeAnalysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo,
                             XBLBindings.Scope scope, Element element, int index, boolean isValueControl, ContainerAnalysis parentControlAnalysis,
                             Map<String, SimpleAnalysis> inScopeVariables) {
        super(propertyContext, staticState, controlsDocumentInfo, scope, element, index, isValueControl, parentControlAnalysis, inScopeVariables);
    }
}
