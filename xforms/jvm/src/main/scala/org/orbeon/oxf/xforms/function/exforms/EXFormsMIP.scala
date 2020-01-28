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
package org.orbeon.oxf.xforms.function.exforms

import org.orbeon.oxf.xforms.function.xxforms.XXFormsMIPFunction
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.BooleanValue

// Base class for eXForms MIP functions.
class EXFormsMIP extends XXFormsMIPFunction {
  override def evaluateItem(xpathContext: XPathContext): Item = {

    // "If the argument is omitted, it defaults to a node-set with the context node as its only member."
    val itemOption = itemArgumentOrContextOpt(0)(xpathContext)

    def getOrDefault(item: Item, getFromNode: NodeInfo => Boolean, default: Boolean) = item match {
      case nodeInfo: NodeInfo => getFromNode(nodeInfo)
      case _                  => default
    }

    def get(item: Item) = operation match {
      case 0 => getOrDefault(item, InstanceData.getInheritedRelevant, default = true)
      case 1 => getOrDefault(item, InstanceData.getInheritedReadonly, default = true)
      case 2 => getOrDefault(item, InstanceData.getRequired,          default = false)
    }

    // "If the node-set is empty then the function returns false."
    itemOption map (item => BooleanValue.get(get(item))) getOrElse BooleanValue.FALSE
  }
}
