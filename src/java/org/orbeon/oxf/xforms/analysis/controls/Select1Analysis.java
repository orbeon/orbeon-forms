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
import org.orbeon.oxf.xforms.XFormsStaticState;
import org.orbeon.oxf.xml.dom4j.LocationData;
import org.orbeon.saxon.dom4j.DocumentWrapper;
import org.orbeon.saxon.om.NodeInfo;

import java.util.Map;

public class Select1Analysis extends ControlAnalysis {

    public final XFormsStaticState.ItemsInfo itemsInfo;

    public Select1Analysis(PropertyContext propertyContext, XFormsStaticState staticState, DocumentWrapper controlsDocumentInfo, String prefixedId, Element element, LocationData locationData, int index, boolean hasNodeBinding, boolean isValueControl, ControlAnalysis parentControlAnalysis, ControlAnalysis ancestorRepeat, Map<String, ControlAnalysis> inScopeVariables) {
        super(propertyContext, staticState, controlsDocumentInfo, prefixedId, element, locationData, index, hasNodeBinding, isValueControl, parentControlAnalysis, ancestorRepeat, inScopeVariables);

        // Gather itemset information
        final NodeInfo controlNodeInfo = controlsDocumentInfo.wrap(element);

        // Try to figure out if we have dynamic items. This attempts to cover all cases, including
        // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
        // don't check things like event handlers. Also check for AVTs ion @class and @style.
        final boolean hasNonStaticItem = (Boolean) XPathCache.evaluateSingle(propertyContext, controlNodeInfo,
                "exists(./(xforms:choices | xforms:item | xforms:itemset)//xforms:*[@ref or @nodeset or @bind or @value or (@class, @style)[contains(., '{')]])",
                XFormsStaticState.BASIC_NAMESPACE_MAPPINGS, null, null, null, null, locationData);

        // Remember information
        itemsInfo = new XFormsStaticState.ItemsInfo(element.getName().equals("select"), hasNonStaticItem);
    }
}
