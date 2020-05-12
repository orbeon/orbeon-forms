/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import java.{util => ju}

import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.PartAnalysisImpl
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.event.EventHandlerImpl
import org.orbeon.oxf.xforms.{PartAnalysis, XFormsUtils}
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.oxf.xml.{Dom4j, NamespaceMapping}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.NodeConversions._

import scala.collection.JavaConverters._

object XBLTransformer {

  val XBLAttrQName       = QName("attr",       XBL_NAMESPACE)
  val XBLContentQName    = QName("content",    XBL_NAMESPACE)
  val XXBLAttrQName      = QName("attr",       XXBL_NAMESPACE)
  val XXBLUseIfAttrQName = QName("use-if-attr", XXBL_NAMESPACE)

  private object DefaultXblSupport extends XBLSupport {

    def keepElement(
      partAnalysis  : PartAnalysis,
      boundElement  : Element,
      directNameOpt : Option[QName],
      elem          : Element
    ): Boolean =
      elem.attributeValueOpt(XXBLUseIfAttrQName) match {
        case Some(att) => boundElement.attributeValueOpt(att).flatMap(_.trimAllToOpt).nonEmpty
        case None      => true
      }
  }

  /**
    * Apply an XBL transformation, i.e. apply xbl:content, xbl:attr, etc.
    *
    * NOTE: This mutates shadowTreeDocument.
    */
  def transform(
    partAnalysis          : PartAnalysisImpl,
    xblSupport            : Option[XBLSupport],
    shadowTreeDocument    : Document,
    boundElement          : Element,
    abstractBindingOpt    : Option[AbstractBinding],
    excludeNestedHandlers : Boolean,
    excludeNestedLHHA     : Boolean,
    supportAVTs           : Boolean
  ): Document = {

    val documentWrapper  = new DocumentWrapper(boundElement.getDocument, null, org.orbeon.oxf.util.XPath.GlobalConfiguration)
    val boundElementInfo = documentWrapper.wrap(boundElement)
    val directNameOpt    = abstractBindingOpt flatMap (_.directName)

    def isNestedHandler(e: Element): Boolean =
      (e.getParent eq boundElement) && EventHandlerImpl.isEventHandler(e)

    def isNestedLHHA(e: Element): Boolean =
      (e.getParent eq boundElement) && LHHA.isLHHA(e)

    def mustFilterOut(e: Element): Boolean =
      excludeNestedHandlers && isNestedHandler(e) || excludeNestedLHHA && isNestedLHHA(e)

    def setAttribute(
      nodes            : ju.List[Node],
      attributeQName   : QName,
      attributeValue   : String,
      namespaceElement : Element
    ): Unit =
      if ((attributeValue ne null) && (nodes ne null) && ! nodes.isEmpty) {
        for (node <- nodes.asScala) {
          node match {
            case element: Element =>
              Dom4jUtils.copyMissingNamespaces(namespaceElement, element)
              element.addAttribute(attributeQName, attributeValue)
            case _ =>
          }
        }
      }

    def setText(
      nodes : ju.List[Node],
      value : String
    ): Unit =
      if ((value ne null) && (nodes ne null) && ! nodes.isEmpty) {
        for (node <- nodes.asScala) {
          if (node.isInstanceOf[Element]) {
            node.setText(value)
          }
        }
      }

    Dom4j.visitSubtree(shadowTreeDocument.getRootElement, currentElem => {

      val isXBLContent = currentElem.getQName == XBLContentQName
      var resultingNodes: ju.List[Node] = null

      val processChildren =
        if (isXBLContent) {
          val includesAttribute = currentElem.attributeValue(INCLUDES_QNAME)
          val scopeAttribute    = currentElem.attributeValue(XXBL_SCOPE_QNAME)

          var contentToInsert: ju.List[Node] = null

          if (includesAttribute eq null) {
            // All bound node content must be copied over (except nested handlers if requested)

            val clonedContent = new ju.ArrayList[Node]
            for (node <- Dom4j.content(boundElement)) {
              node match {
                case _: Namespace =>
                  // don't copy over namespace nodes
                case elem: Element =>
                  if (! mustFilterOut(elem))
                    clonedContent.add(Dom4jUtils.copyElementCopyParentNamespaces(elem))
                case node =>
                  clonedContent.add(Dom4jUtils.createCopy(node))
              }
            }
            contentToInsert = clonedContent
          } else {
            // Apply CSS selector

            // Convert CSS to XPath
            val xpathExpression = CSSParser.toXPath(includesAttribute)
            val boundElementInfo = documentWrapper.wrap(boundElement)

            // TODO: don't use getNamespaceContext() as this is already computed for the bound element
            val elements = XPathCache.evaluate(
              boundElementInfo,
              xpathExpression,
              NamespaceMapping(Dom4jUtils.getNamespaceContext(currentElem)),
              null,
              null,
              null,
              null,
              null,
              null
            ).asInstanceOf[ju.List[NodeInfo]].asScala

            if (elements.nonEmpty) {
              // Clone all the resulting elements
              contentToInsert = new ju.ArrayList[Node](elements.size)
              for (currentNodeInfo <- elements) {
                val currentElement = unsafeUnwrapElement(currentNodeInfo)
                if (! mustFilterOut(currentElement))
                  contentToInsert.add(Dom4jUtils.copyElementCopyParentNamespaces(currentElement))
              }
            } else {
              // Clone all the element's children if any
              // See: http://www.w3.org/TR/xbl/#the-content
              contentToInsert = new ju.ArrayList[Node](currentElem.nodeCount)
              for (currentElement <- Dom4j.elements(currentElem)) {
                if (! mustFilterOut(currentElement))
                  contentToInsert.add(Dom4jUtils.copyElementCopyParentNamespaces(currentElement))
              }
            }
          }

          // Insert content if any
          if ((contentToInsert ne null) && ! contentToInsert.isEmpty) {
            val parentContent = currentElem.getParent.content
            val elementIndex = parentContent.indexOf(currentElem)
            parentContent.addAll(elementIndex, contentToInsert)
          }

          // Remove <xbl:content> from shadow tree
          currentElem.detach()

          resultingNodes = contentToInsert
          if (scopeAttribute.nonAllBlank) {
            // If author specified scope attribute, use it
            setAttribute(resultingNodes, XXBL_SCOPE_QNAME, scopeAttribute, null)
          } else {
            // By default, set xxbl:scope="outer" on resulting elements
            setAttribute(resultingNodes, XXBL_SCOPE_QNAME, "outer", null)
          }
          true
        } else if (! DefaultXblSupport.keepElement(partAnalysis, boundElement, directNameOpt, currentElem)) {
          // Skip this element
          currentElem.detach()
          false
        } else if (xblSupport exists (! _.keepElement(partAnalysis, boundElement, directNameOpt, currentElem))) {
          // Skip this element
          currentElem.detach()
          false
        } else {
          // Element is simply kept
          resultingNodes = ju.Collections.singletonList(currentElem.asInstanceOf[Node])
          true
        }

      // Handle attribute forwarding
      val xblAttr  = currentElem.attribute(XBLAttrQName)  // standard xbl:attr (custom syntax)
      val xxblAttr = currentElem.attribute(XXBLAttrQName) // extension xxbl:attr (XPath expression)

      if (xblAttr ne null) {
        // Detach attribute (not strictly necessary?)
        xblAttr.detach()

        val xblAttrString = xblAttr.getValue

        for (currentValue <- xblAttrString.splitTo[Iterator]()) {

          val equalIndex = currentValue.indexOf('=')
          if (equalIndex == -1) {
            // No a=b pair, just a single QName
            val valueQName = Dom4jUtils.extractTextValueQName(currentElem, currentValue, true)
            if (valueQName.namespace.uri != XBL_NAMESPACE_URI) {
              // This is not xbl:text, copy the attribute
              val attributeValue = boundElement.attributeValue(valueQName)
              if (attributeValue ne null)
                setAttribute(resultingNodes, valueQName, attributeValue, boundElement)
            } else {
              // This is xbl:text
              // "The xbl:text value cannot occur by itself in the list"
            }
          } else {
            // a=b pair
            val leftSideQName = {
              val leftSide = currentValue.substring(0, equalIndex)
              Dom4jUtils.extractTextValueQName(currentElem, leftSide, true)
            }
            val rightSideQName = {
              val rightSide = currentValue.substring(equalIndex + 1)
              Dom4jUtils.extractTextValueQName(currentElem, rightSide, true)
            }

            val isLeftSideXBLText  = leftSideQName.namespace.uri  == XBL_NAMESPACE_URI
            val isRightSideXBLText = rightSideQName.namespace.uri == XBL_NAMESPACE_URI

            val (rightSideValue, namespaceElement) =
              if (! isRightSideXBLText) {
                // Get attribute value
                boundElement.attributeValue(rightSideQName) -> boundElement
              } else {
                // Get text value

                // "any text nodes (including CDATA nodes and whitespace text nodes) that are
                // explicit children of the bound element must have their data concatenated"
                boundElement.getText -> null
              }

            if (rightSideValue ne null) {
              // NOTE: XBL doesn't seem to says what should happen if the source attribute is not
              // found! We assume the rule is ignored in this case.
              if (! isLeftSideXBLText) {
                // Set attribute value
                setAttribute(resultingNodes, leftSideQName, rightSideValue, namespaceElement)
              } else {
                // Set text value

                // "value of the attribute on the right-hand side are to be represented as text
                // nodes underneath the shadow element"

                // TODO: "If the element has any child nodes in the DOM (any nodes, including
                // comment nodes, whitespace text nodes, or even empty CDATA nodes) then the pair
                // is in error and UAs must ignore it, meaning the attribute value is not forwarded"

                setText(resultingNodes, rightSideValue)
              }
            }
          }
          // TODO: handle xbl:lang?
          // TODO: handle type specifiers?
        }
      } else if (xxblAttr ne null) {
        // Detach attribute (not strictly necessary?)
        xxblAttr.detach()
        // Get attribute value
        val xxblAttrString= xxblAttr.getValue

        // TODO: don't use getNamespaceContext() as this is already computed for the bound element
        val nodeInfos = XPathCache.evaluate(
          boundElementInfo,
          xxblAttrString,
          NamespaceMapping(Dom4jUtils.getNamespaceContext(currentElem)),
          null,
          null,
          null,
          null,
          null,
          null
        )
        if (! nodeInfos.isEmpty) {
          for (nodeInfo <- nodeInfos.asScala) {
            val currentNodeInfo = nodeInfo.asInstanceOf[NodeInfo]
            if (currentNodeInfo.getNodeKind == org.w3c.dom.Node.ATTRIBUTE_NODE) {
              val currentAttribute = unsafeUnwrapAttribute(currentNodeInfo)
              setAttribute(resultingNodes, currentAttribute.getQName, currentAttribute.getValue, currentAttribute.getParent)
            }
          }
        }
      }

      // Check for AVTs
      if (supportAVTs) {
        for (att <- Dom4j.attributes(currentElem)) {
          val attValue = att.getValue
          if (XFormsUtils.maybeAVT(attValue)) {
            val newValue = XPathCache.evaluateAsAvt(
              boundElementInfo,
              attValue,
              NamespaceMapping(Dom4jUtils.getNamespaceContext(currentElem)),
              null,
              null,
              null,
              null,
              null,
              null
            )
            setAttribute(ju.Collections.singletonList[Node](currentElem), att.getQName, newValue, currentElem)
          }
        }
      }

      processChildren
    })
    shadowTreeDocument
  }
}