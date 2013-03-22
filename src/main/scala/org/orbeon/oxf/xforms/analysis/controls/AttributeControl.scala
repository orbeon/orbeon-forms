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

import org.dom4j.Element
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.xbl.Scope

class AttributeControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
        extends CoreControl(staticStateContext, element, parent, preceding, scope)
        with ValueTrait
        with OptionalSingleNode {

    // Attribute control uses an AVT
    // TODO: Add support for analyzing AVT
    override def computeValueAnalysis = Some(NegativeAnalysis(value.get)) // we must have a value

    val forStaticId = element.attributeValue(FOR_QNAME)
    val forPrefixedId = XFormsUtils.getRelatedEffectiveId(prefixedId, forStaticId)

    val attributeName = element.attributeValue(NAME_QNAME)
    val attributeValue = element.attributeValue(VALUE_QNAME)

    val forName = element.attributeValue("for-name")
    val urlType = element.attributeValue("url-type")
    val portletMode = element.attributeValue("portlet-mode")
    val windowState = element.attributeValue("window-state")
}