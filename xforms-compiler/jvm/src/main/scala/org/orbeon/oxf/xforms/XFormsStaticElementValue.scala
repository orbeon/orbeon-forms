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
package org.orbeon.oxf.xforms

import java.{lang => jl}

import org.orbeon.dom._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.XFormsNames

object XFormsStaticElementValue {

  /**
   * Get the value of a child element known to have only static content.
   *
   * @param childElement element to evaluate (xf:label, etc.)
   * @param acceptHTML   whether the result may contain HTML
   * @param containsHTML whether the result actually contains HTML (null allowed)
   * @return string containing the result of the evaluation
   */
  def getStaticChildElementValue(prefix: String, childElement: Element, acceptHTML: Boolean, containsHTML: Array[Boolean]): String = {

    assert(childElement != null)

    // No HTML found by default
    if (containsHTML != null)
      containsHTML(0) = false

    val sb = new jl.StringBuilder(20)

    // Visit the subtree and serialize
    // NOTE: It is a little funny to do our own serialization here, but the alternative is to build a DOM and
    // serialize it, which is not trivial because of the possible interleaved xf:output's. Furthermore, we
    // perform a very simple serialization of elements and text to simple (X)HTML, not full-fledged HTML or XML
    // serialization.
    childElement.visitDescendants(
      new LHHAElementVisitorListener(prefix, acceptHTML, containsHTML, sb, childElement),
      mutable = false
    )
    if (acceptHTML && containsHTML != null && !containsHTML(0)) {
      // We went through the subtree and did not find any HTML
      // If the caller supports the information, return a non-escaped string so we can optimize output later
      sb.toString.unescapeXmlMinimal
    } else {
      // We found some HTML, just return it
      sb.toString
    }
  }

  class LHHAElementVisitorListener(
    prefix       : String,
    acceptHTML   : Boolean,
    containsHTML : Array[Boolean],
    sb           : jl.StringBuilder,
    childElement : Element
  ) extends VisitorListener {

    private var lastIsStart = false

    def startElement(element: Element): Unit = {
      // This is a regular element, just serialize the start tag to no namespace
      // If HTML is not allowed here, better tell the user
      if (! acceptHTML)
        throw new OXFException("Nested XHTML or XForms not allowed within element: " + childElement.getName)
      if (containsHTML != null)
        containsHTML(0) = true
      sb.append('<')
      sb.append(element.getName)
      for (attribute <- element.attributes) {
        val currentAttributeName = attribute.getName
        val currentAttributeValue = attribute.getValue
        val resolvedValue =
          if (currentAttributeName == "id") {
            // This is an id, prefix if needed
            prefix + currentAttributeValue
          } else {
            // Simply use control value
            currentAttributeValue
          }
        // Only consider attributes in no namespace
        if ("" == attribute.getNamespaceURI) {
          sb.append(' ')
          sb.append(currentAttributeName)
          sb.append("=\"")
          if (resolvedValue != null)
            sb.append(resolvedValue.escapeXmlMinimal)
          sb.append('"')
        }
      }
      sb.append('>')
      lastIsStart = true
    }

    def endElement(element: Element): Unit = {
      val elementName = element.getName
      if ((! lastIsStart || ! VoidElements(elementName)) && element.getQName != XFormsNames.XFORMS_OUTPUT_QNAME) {
        // This is a regular element, just serialize the end tag to no namespace
        // UNLESS the element was just opened. This means we output `<br>`, not `<br></br>`, etc.
        sb.append("</")
        sb.append(elementName)
        sb.append('>')
      }
      lastIsStart = false
    }

    def text(text: Text): Unit = {
      sb.append(
        if (acceptHTML)
          text.getStringValue.escapeXmlMinimal
        else
          text.getStringValue
      )
      lastIsStart = false
    }
  }
}