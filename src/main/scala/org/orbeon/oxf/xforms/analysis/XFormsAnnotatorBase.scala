/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.orbeon.oxf.xml.XMLReceiverAdapter
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xml.XMLConstants._

abstract class XFormsAnnotatorBase extends XMLReceiverAdapter {

    // Name of container elements that require the use of separators for handling visibility
    private val SeparatorAppearanceElements = Set(
        "table",
        "tbody",
        "thead",
        "tfoot",
        "tr",
        "ol",
        "ul",
        "dl"
    )
    
    case class StackElement(parent: Option[StackElement], uri: String, localname: String) {
        val isXForms            = uri == XFORMS_NAMESPACE_URI
        val isXXForms           = uri == XXFORMS_NAMESPACE_URI
        val isEXForms           = uri == EXFORMS_NAMESPACE_URI
        val isXBL               = uri == XBL_NAMESPACE_URI
        val isXFormsOrExtension = isXForms || isXXForms || isEXForms

        def isXHTML             = uri == XHTML_NAMESPACE_URI
    }

    private var stack: List[StackElement] = Nil

    def currentStackElement = stack.head

    def startElement(uri: String, localname: String): StackElement = {

        val parentOpt = stack.headOption

        val newStackElement =
            StackElement(
                parentOpt,
                uri,
                localname
            )

        stack ::= newStackElement
        newStackElement
    }

    def endElement(): StackElement = {
        val stackElement = currentStackElement
        stack = stack.tail
        stackElement
    }
    
    def doesParentRequireSeparatorAppearance =
        currentStackElement.parent exists (p ⇒ p.isXHTML && SeparatorAppearanceElements(p.localname))
}
