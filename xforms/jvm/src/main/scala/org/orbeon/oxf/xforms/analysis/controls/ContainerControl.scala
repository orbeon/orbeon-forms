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
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xforms.xbl.Scope
import org.orbeon.xforms.XFormsNames

class ContainerControl(
  staticStateContext : StaticStateContext,
  element            : Element,
  parent             : Option[ElementAnalysis],
  preceding          : Option[ElementAnalysis],
  scope              : Scope
) extends SimpleElementAnalysis(staticStateContext, element, parent, preceding, scope)
     with ViewTrait {

  // For <xf:group xxf:element="xh:div">. Can be null.
  val elementQName: QName = Dom4jUtils.extractAttributeValueQName(element, XFormsNames.XXFORMS_ELEMENT_QNAME)
}
