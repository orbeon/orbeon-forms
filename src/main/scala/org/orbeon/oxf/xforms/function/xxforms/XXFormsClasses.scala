/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{StaticProperty, XPathContext}
import org.orbeon.saxon.om.NodeInfo
import org.w3c.dom.Node._

class XXFormsClasses extends XFormsFunction with ClassSupport {
  override def iterate(xpathContext: XPathContext) =
    asIterator(classes(0)(xpathContext).toList)

  // Needed otherwise xpathContext.getContextItem doesn't return the correct value
  override def getIntrinsicDependencies =
    if (argument.isEmpty) StaticProperty.DEPENDS_ON_CONTEXT_ITEM else 0
}

protected trait ClassSupport extends XFormsFunction {
  def classes(i: Int)(implicit xpathContext: XPathContext): Set[String] =
    itemArgumentOrContextOpt(i) match {
      case Some(node: NodeInfo) if node.getNodeKind == ELEMENT_NODE ⇒
        val classCode = xpathContext.getNamePool.allocate("", "", "class")
        val value     = node.getAttributeValue(classCode)

        stringToSet(value)
      case _ ⇒
        Set()
    }
}