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
import org.orbeon.oxf.xforms.analysis._
import org.orbeon.oxf.xml.dom.Extensions
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames
import org.orbeon.xforms.xbl.Scope
import org.orbeon.xml.NamespaceMapping

// TODO: only for `xbl:control`, then make abstract
class ContainerControl(
  index            : Int,
  element          : Element,
  parent           : Option[ElementAnalysis],
  preceding        : Option[ElementAnalysis],
  staticId         : String,
  prefixedId       : String,
  namespaceMapping : NamespaceMapping,
  scope            : Scope,
  containerScope   : Scope
) extends ElementAnalysis(
  index,
  element,
  parent,
  preceding,
  staticId,
  prefixedId,
  namespaceMapping,
  scope,
  containerScope
) with ViewTrait
  with WithChildrenTrait {

  // For `<xf:group xxf:element="xh:div">`
  // TODO: serialize/builder
  val elementQName: Option[QName] =
    Extensions.resolveQName(
      namespaceMapping.mapping,
      element.attributeValue(XFormsNames.XXFORMS_ELEMENT_QNAME),
      unprefixedIsNoNamespace = true
    )
}
