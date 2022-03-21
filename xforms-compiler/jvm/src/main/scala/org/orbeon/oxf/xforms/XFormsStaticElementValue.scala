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

import org.orbeon.dom
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.{StaticXPath, Whitespace}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.analysis.controls.LHHAAnalysis
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.xforms.{Constants, XFormsNames}

import java.{lang => jl}
import scala.collection.mutable.ListBuffer


object XFormsStaticElementValue {

  type XPathExpressionString = String

  def findElementExpressionOrConstantDirectOrNested(
    outerElem       : dom.Element,
    containerPrefix : String,
    isWithinRepeat  : Boolean,
    acceptHTML      : Boolean,
    makeString      : String => String
  ): (Either[XPathExpressionString, String], Boolean) =
    findElemBindingOrValueExpression(outerElem) map
    makeString                                  map
    (Left(_) -> LHHAAnalysis.isHTML(outerElem)) getOrElse
    getElementExpressionOrConstant(
      outerElem       = outerElem,
      containerPrefix = containerPrefix,
      isWithinRepeat  = isWithinRepeat,
      acceptHTML      = acceptHTML
    )

  private def findElemBindingOrValueExpression(elem: dom.Element): Option[String] = {

    def fromValue =
      elem.attributeValueOpt(XFormsNames.VALUE_QNAME)

    def fromRef =
      ElementAnalysis.getBindingExpression(elem)

    def fromBind =
      elem.attributeValueOpt(XFormsNames.BIND_QNAME) map (v => s"""bind('$v')""")

   fromValue orElse fromRef orElse fromBind
  }

  // Only for the case where there is no `bind`, `ref`, or `value` on the element.
  // We construct either:
  //
  // - a static value
  // - or an XPath expression to compute the value of the content, including
  //   nested `<xf:output>` elements.
  //
  // NOTE: The XBL scope cannot be changed on nested `<xf:output>`.
  //
  private def getElementExpressionOrConstant(
    outerElem       : dom.Element,
    containerPrefix : String,
    isWithinRepeat  : Boolean,
    acceptHTML      : Boolean
  ): (Either[XPathExpressionString, String], Boolean) = {

    val outerIsHTML = LHHAAnalysis.isHTML(outerElem)

    // Compute `containsHTML` in a separate pass
    val containsHtml = {

      var localContainsHtml = false

      outerElem.visitDescendants(
        new VisitorListener {
          def startElement(elem: dom.Element): Unit = {

            val isHTMLMediatype =
              if ((elem eq outerElem) || elem.getQName == XFormsNames.XFORMS_OUTPUT_QNAME)
                ! outerIsHTML && LHHAAnalysis.isHTML(elem) || outerIsHTML && ! LHHAAnalysis.isPlainText(elem)
              else // nested XHTML element
                true

            if (isHTMLMediatype && ! acceptHTML)
              throw new OXFException(s"HTML element or mediatype not allowed for element: `${outerElem.getName}`")

            localContainsHtml ||= isHTMLMediatype
          }

          def endElement(elem: dom.Element): Unit = ()
          def text(text: dom.Text): Unit = ()
        },
        mutable     = false,
        includeSelf = true
      )

      localContainsHtml
    }

    var containsExpr = false

    val builder        = new ListBuffer[String]()
    val literalBuilder = new jl.StringBuilder

    def commitLiteralBuilderIfNeeded(): Unit =
      if (literalBuilder.length > 0) {
        builder += "'" + literalBuilder.toString.replace("'", "''") + "'"
        literalBuilder.setLength(0)
      }

    def addExpr(expr: String, isHtml: Boolean): Unit = {
      commitLiteralBuilderIfNeeded()
      // NOTE: No need to wrap `$expr` as it's already an `xs:string?` expression and safe for embedding.
      builder += (if (containsHtml && ! isHtml) s"""replace(replace(replace($expr, '&', '&amp;'), '<', '&lt;'), '>', '&gt;')""" else expr)
      containsExpr = true
    }

    if (false && outerElem.hasAttribute(XFormsNames.VALUE_QNAME)) {
      // LATER: Optimize case where we don't need to check the content.
    } else {
      outerElem.visitDescendants(
        new VisitorListener {
          def startElement(elem: dom.Element): Unit =
            if ((elem eq outerElem) || elem.getQName == XFormsNames.XFORMS_OUTPUT_QNAME) {
              // This is either the outer element or a nested `<xf:output>`

              val isHTMLMediatype = ! outerIsHTML && LHHAAnalysis.isHTML(elem) || outerIsHTML && ! LHHAAnalysis.isPlainText(elem)

              // For nested `<xf:output>`, we don't support a combination of `value`/`ref` or `value`/`bind`.
              // Instead, we take `value`, then `ref`, then `bind`.
              // For the outer element, `value` will evaluated relative to `ref` or `bind` if present.
              // TODO: Throw an error for invalid conditions.
              // 2021-12-07: `elem eq outerElem` should no longer cause the following to call `addExpr()` as we
              // check `findElemBindingOrValueExpression()` before calling this function.
              findElemBindingOrValueExpression(elem) map StaticXPath.makeStringExpression foreach { v =>
                addExpr(v, isHTMLMediatype)
              }
            } else {
              // This is a regular element, just serialize the start tag to no namespace
              // If HTML is not allowed here, better tell the user

              literalBuilder.append('<')
              literalBuilder.append(elem.getName)
              for (attribute <- elem.attributeIterator) {

                val currentAttributeName  = attribute.getName
                val currentAttributeValue = attribute.getValue

                // Only consider attributes in no namespace
                if (attribute.getNamespaceURI.isEmpty) {

                  literalBuilder.append(' ')
                  literalBuilder.append(currentAttributeName)
                  literalBuilder.append("=\"")

                  if (XMLUtils.maybeAVT(currentAttributeValue)) {
                    addExpr(s"""xxf:evaluate-avt('${currentAttributeValue.replace("'", "''")}')""", isHtml = true)
                  } else if (currentAttributeName == "id") {
                    // This is an id, prefix if needed, but also add suffix
                    // https://github.com/orbeon/orbeon-forms/issues/4782

                    // Always append the prefixed id
                    literalBuilder.append((containerPrefix + currentAttributeValue).escapeXmlForAttribute)

                    // Append repeat suffix if needed
                    // https://github.com/orbeon/orbeon-forms/issues/4782
                    if (isWithinRepeat) {
                      literalBuilder.append(Constants.RepeatSeparatorString)
                      addExpr(s"""string-join(for $$p in xxf:repeat-positions() return string($$p), '${Constants.RepeatIndexSeparatorString}')""", isHtml = true)
                    }
                  } else {
                    literalBuilder.append(currentAttributeValue.escapeXmlForAttribute)
                  }

                  literalBuilder.append('"')
                }
              }
              literalBuilder.append('>')
            }

          def endElement(elem: dom.Element): Unit =
            if ((elem ne outerElem) && (! VoidElements(elem.getName)) && elem.getQName != XFormsNames.XFORMS_OUTPUT_QNAME) {
              // This is a regular element, just serialize the end tag to no namespace
              // UNLESS the element was just opened. This means we output `<br>`, not `<br></br>`, etc.
              literalBuilder.append("</")
              literalBuilder.append(elem.getName)
              literalBuilder.append('>')
            }

          def text(text: dom.Text): Unit =
            literalBuilder.append(Whitespace.applyPolicy(text.getText, Whitespace.Policy.Collapse))
        },
        mutable     = false,
        includeSelf = true
      )
    }

    val expressionOrConstant =
      if (containsExpr) {
        commitLiteralBuilderIfNeeded()
        if (builder.size > 1)
          Left(builder.mkString("concat(", ", ", ")"))
        else
          Left(builder.head)
      } else {
        Right(literalBuilder.toString)
      }

    (expressionOrConstant, containsHtml)
  }

  /**
   * Get the value of a child element known to have only static content.
   *
   * @param childElement element to evaluate (xf:label, etc.)
   * @param acceptHTML   whether the result may contain HTML
   * @param containsHTML whether the result actually contains HTML (null allowed)
   * @return string containing the result of the evaluation
   */
  def getStaticChildElementValue(prefix: String, childElement: dom.Element, acceptHTML: Boolean, containsHTML: Array[Boolean]): String = {

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
    childElement : dom.Element
  ) extends VisitorListener {

    private var lastIsStart = false

    def startElement(element: dom.Element): Unit = {
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

    def endElement(element: dom.Element): Unit = {
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

    def text(text: dom.Text): Unit = {
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