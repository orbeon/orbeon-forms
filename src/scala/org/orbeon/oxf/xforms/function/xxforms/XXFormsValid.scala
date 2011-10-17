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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.saxon.value.BooleanValue
import org.orbeon.saxon.expr.{Expression, XPathContext, ExpressionTool}
import org.orbeon.oxf.xforms.InstanceData
import org.w3c.dom.Node._
import org.orbeon.saxon.om._
import org.orbeon.oxf.xml.AttributesAndElementsIterator

/**
 * xxforms:valid() as xs:boolean
 * xxforms:valid($item as xs:item*) as xs:boolean
 * xxforms:valid($item as xs:item*, $recurse as xs:boolean) as xs:boolean
 * xxforms:valid($item as xs:item*, $recurse as xs:boolean, $relevant) as xs:boolean
 */
class XXFormsValid extends XXFormsMIPFunction {

    override def evaluateItem(xpathContext: XPathContext) = {

        def effectiveBooleanValue(e: Expression) = ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext))

        // First item or context node if any
        val item = argument.lift(0) map (e ⇒ Option(e.iterate(xpathContext).next())) getOrElse Option(xpathContext.getContextItem)
        
        // Whether to recursively test the subtree
        val recurse = argument.lift(1) map (effectiveBooleanValue(_)) getOrElse false

        // Whether to ignore non-relevant nodes
        val ignoreNonRelevant = argument.lift(2) map (effectiveBooleanValue(_)) getOrElse false

        // Item is valid unless it is a relevant (unless relevance is ignored) element/attribute and marked as invalid
        def isItemValid(item: Item) = item match {
            case nodeInfo: NodeInfo if Set[Int](ELEMENT_NODE, ATTRIBUTE_NODE)(nodeInfo.getNodeKind) ⇒
                (ignoreNonRelevant && ! InstanceData.getInheritedRelevant(nodeInfo)) || InstanceData.getValid(nodeInfo)
            case _ ⇒
                true
        }

        // Tree is valid unless one of its descendant-or-self nodes is invalid
        def isTreeValid(nodeInfo: NodeInfo) =
            ! (new AttributesAndElementsIterator(nodeInfo) exists (! isItemValid(_)))

        // Compute validity and return a Saxon BooleanValue
        BooleanValue get {
            item match {
                case Some(nodeInfo: NodeInfo) if recurse ⇒ isTreeValid(nodeInfo)
                case Some(nodeInfo: NodeInfo) ⇒ isItemValid(nodeInfo)
                case _ ⇒ true // atomic or empty is valid (NOTE: changed from earlier behavior where empty was not valid)
            }
        }
    }
}
