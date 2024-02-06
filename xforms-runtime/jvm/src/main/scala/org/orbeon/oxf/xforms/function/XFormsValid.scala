/**
 *  Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.function.xxforms.XXFormsMIPFunction
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.AttributesAndElementsIterator
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

/**
 * xf:valid() as xs:boolean
 * xf:valid($items as item()*) as xs:boolean
 * xf:valid($items as item()*, $relevant as xs:boolean) as xs:boolean
 * xf:valid($items as item()*, $relevant as xs:boolean, $recurse as xs:boolean) as xs:boolean
 */
class XFormsValid extends XXFormsMIPFunction with ValidSupport {

  override def evaluateItem(xpathContext: XPathContext) = {

    implicit val xpc = xpathContext

    val pruneNonRelevant = booleanArgument(1, default = true)
    val recurse          = booleanArgument(2, default = true)

    isValid(pruneNonRelevant, recurse)
  }
}

/**
 * xxf:valid() as xs:boolean
 * xxf:valid($item as item()*) as xs:boolean
 * xxf:valid($item as item()*, $recurse as xs:boolean) as xs:boolean
 * xxf:valid($item as item()*, $recurse as xs:boolean, $relevant as xs:boolean) as xs:boolean
 */
class XXFormsValid extends XXFormsMIPFunction with ValidSupport {

  override def evaluateItem(xpathContext: XPathContext) = {

    implicit val xpc = xpathContext

    val recurse          = booleanArgument(1, default = false)
    val pruneNonRelevant = booleanArgument(2, default = false)

    isValid(pruneNonRelevant, recurse)
  }
}

trait ValidSupport extends XFormsFunction {

  def isValid(pruneNonRelevant: Boolean, recurse: Boolean)(implicit xpathContext: XPathContext): Boolean = {

    val items = itemsArgumentOrContextOpt(0)

    if (recurse)
      ! (asScalaIterator(items) exists (i => ! isTreeValid(i, pruneNonRelevant)))
    else
      ! (asScalaIterator(items) exists (i => ! isItemValid(i, pruneNonRelevant)))
  }

  // Item is valid unless it is a relevant (unless relevance is ignored) element/attribute and marked as invalid
  private def isItemValid(item: Item, pruneNonRelevant: Boolean): Boolean = item match {
    case nodeInfo: NodeInfo if nodeInfo.isElementOrAttribute =>
      pruneNonRelevant && ! InstanceData.getInheritedRelevant(nodeInfo) || InstanceData.getValid(nodeInfo)
    case _ =>
      true
  }

  // Tree is valid unless one of its descendant-or-self nodes is invalid
  private def isTreeValid(item: Item, pruneNonRelevant: Boolean): Boolean = item match {
    case nodeInfo: NodeInfo if nodeInfo.isElementOrAttribute =>
      ! (AttributesAndElementsIterator(nodeInfo) exists (! isItemValid(_, pruneNonRelevant)))
    case _ =>
      true
  }
}
