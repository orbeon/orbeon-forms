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
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


class AttributeControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends CoreControl(index, element, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with ValueTrait
     with OptionalSingleNode {

  val forStaticId   : String = element.attributeValue(FOR_QNAME)
  val forPrefixedId : String = XFormsId.getRelatedEffectiveId(prefixedId, forStaticId)

  val attributeName : String = element.attributeValue(NAME_QNAME)
  val attributeValue: String = element.attributeValue(VALUE_QNAME)

  val forName       : String = element.attributeValue("for-name")
  val urlType       : String = element.attributeValue("url-type")
  val portletMode   : String = element.attributeValue("portlet-mode")
  val windowState   : String = element.attributeValue("window-state")
}