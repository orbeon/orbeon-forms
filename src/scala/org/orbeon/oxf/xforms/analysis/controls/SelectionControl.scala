/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.analysis.controls

import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsStaticState
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

trait SelectionControl extends SimpleElementAnalysis {

    // Try to figure out if we have dynamic items. This attempts to cover all cases, including
    // nested xforms:output controls. Check only under xforms:choices, xforms:item and xforms:itemset so that we
    // don't check things like event handlers. Also check for AVTs ion @class and @style.
    // TODO: fix this, seems incorrect: if there is an itemset, consider dynamic; also handle AVTs on any child element of label/value
    val hasNonStaticItem = XPathCache.evaluateSingle(staticStateContext.propertyContext, staticStateContext.controlsDocument.wrap(element),
            "exists(./(xforms:choices | xforms:item | xforms:itemset)//xforms:*[@ref or @nodeset or @bind or @value or (@class, @style)[contains(., '{')]])",
            XFormsStaticState.BASIC_NAMESPACE_MAPPING, null, null, null, null, locationData).asInstanceOf[Boolean]

    // Remember information
    val isMultiple = element.getName == "select"
}
