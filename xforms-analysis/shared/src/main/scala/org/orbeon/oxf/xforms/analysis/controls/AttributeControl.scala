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

import org.orbeon.dom.{Element, QName}
import org.orbeon.oxf.xforms.analysis.*
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.XFormsNames.*
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping


class AttributeControl(
  index            : Int,
  elem             : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends CoreControl(index, elem, parent, preceding, staticId, prefixedId, namespaceMapping, scope, containerScope)
     with ValueTrait
     with OptionalSingleNode {

  val forStaticId   : String = elem.attributeValue(FOR_QNAME)
  val forPrefixedId : String = XFormsId.getRelatedEffectiveId(prefixedId, forStaticId)

  val attributeName : String = elem.attributeValue(NAME_QNAME)
  val attributeValue: String = elem.attributeValue(VALUE_QNAME)

  val forName       : String = elem.attributeValue("for-name")
  val urlType       : String = elem.attributeValue("url-type")
  val portletMode   : String = elem.attributeValue("portlet-mode")
  val windowState   : String = elem.attributeValue("window-state")

  override protected def synthesizedUnprefixedAttributeValue(local: String): String =
    local match {
      case "for"          => forStaticId
      case "name"         => attributeName
      case "for-name"     => forName
      case "url-type"     => urlType
      case "portlet-mode" => portletMode
      case "window-state" => windowState
      case _              => super.synthesizedUnprefixedAttributeValue(local)
    }

  override protected def extraSynthesizedAttributes: List[(QName, String)] = {
    val b = List.newBuilder[(QName, String)]
    b ++= super.extraSynthesizedAttributes
    if (forStaticId ne null)   b += FOR_QNAME             -> forStaticId
    if (attributeName ne null) b += NAME_QNAME            -> attributeName
    if (forName ne null)       b += QName("for-name")     -> forName
    if (urlType ne null)       b += QName("url-type")     -> urlType
    if (portletMode ne null)   b += QName("portlet-mode") -> portletMode
    if (windowState ne null)   b += QName("window-state") -> windowState
    b.result()
  }

  override protected def isPrunedAttribute(attQName: QName): Boolean =
    super.isPrunedAttribute(attQName) ||
      (attQName.namespace.uri.isEmpty && AttributeControl.PrunedLocalNames(attQName.localName))
}

private object AttributeControl {
  val PrunedLocalNames: Set[String] = Set("for", "name", "for-name", "url-type", "portlet-mode", "window-state")
}