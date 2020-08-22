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

import org.orbeon.dom.Element
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.xforms.XFormsId

class AttributeControl(staticStateContext: StaticStateContext, element: Element, parent: Option[ElementAnalysis], preceding: Option[ElementAnalysis], scope: Scope)
    extends CoreControl(staticStateContext, element, parent, preceding, scope)
    with ValueTrait
    with OptionalSingleNode {

  // Attribute control uses an AVT
  override def computeValueAnalysis = Some(analyzeXPath(getChildrenContext, attributeValue, avt = true))

  val forStaticId = element.attributeValue(FOR_QNAME)
  val forPrefixedId = XFormsId.getRelatedEffectiveId(prefixedId, forStaticId)

  val attributeName = element.attributeValue(NAME_QNAME)
  val attributeValue = element.attributeValue(VALUE_QNAME)

  val forName = element.attributeValue("for-name")
  val urlType = element.attributeValue("url-type")
  val portletMode = element.attributeValue("portlet-mode")
  val windowState = element.attributeValue("window-state")
}