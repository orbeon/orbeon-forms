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
package org.orbeon.oxf.xforms.function

import org.orbeon.saxon.value.AtomicValue
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.expr.XPathContext
import org.dom4j._
import XFormsElement._
import org.orbeon.saxon.om.{EmptyIterator, NodeInfo, Item}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xml.Dom4j

/**
 * xxf:element()
 */
class XFormsElement extends XFormsFunction {

    override def evaluateItem(xpathContext: XPathContext): Item = {

        // Element QName and content sequence
        val qName   = argument.lift(0) map (getQNameFromExpression(xpathContext, _)) get
        val content = argument.lift(1) map (_.iterate(xpathContext)) getOrElse EmptyIterator.getInstance

        // Create element and iterate over content
        val element = Dom4jUtils.createElement(qName)
        val createIterator = asScalaIterator(content) map (addItem(element, _))
        val hasNewText = createIterator.foldLeft(true)(_ || _)

        // Make sure we are normalized if we added text
        if (hasNewText)
            Dom4jUtils.normalizeTextNodes(element)

        context(xpathContext).containingDocument.getStaticState.documentWrapper.wrap(element)
    }
}

object XFormsElement {

    // Add an item to the element and return whether new text was added
    def addItem(element: Element, item: Item): Boolean = {
        // TODO: use more complete insert code from insert action to achieve this
        item match {
            case atomic: AtomicValue ⇒
                // Insert as text
                element.addText(item.getStringValue)
                true
            case node: NodeInfo ⇒
                // Copy node before using it
                Dom4jUtils.createCopy(XFormsUtils.getNodeFromNodeInfoConvert(node)) match {
                    case attribute: Attribute ⇒
                        // Add attribute
                        element.add(attribute)
                        false
                    case document: Document ⇒
                        // use the document root instead
                        Dom4j.content(element) += document.getRootElement
                        false
                    case text: Text ⇒
                        // use the document root instead
                        Dom4j.content(element) += text
                        true
                    case newNode ⇒
                        // Just add the node
                        Dom4j.content(element) += newNode
                        false
                }
        }
    }
}