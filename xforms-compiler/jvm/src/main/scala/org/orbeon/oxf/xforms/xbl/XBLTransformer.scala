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
import cats.syntax.option._
import org.orbeon.dom._
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPathCache
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.{EventHandler, PartAnalysisContextForTree, PartAnalysisForXblSupport}
import org.orbeon.oxf.xml.XMLUtils
import org.orbeon.oxf.xml.dom.Extensions._
import org.orbeon.saxon.om
import org.orbeon.saxon.om.StructuredQName
import org.orbeon.scaxon.NodeInfoConversions._
import org.orbeon.xforms.Namespaces
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xml.NamespaceMapping

import scala.jdk.CollectionConverters._


object XBLTransformer {

  val XBLAttrQName       = QName("attr",       XBL_NAMESPACE)
  val XBLContentQName    = QName("content",    XBL_NAMESPACE)
  val XXBLAttrQName      = QName("attr",       XXBL_NAMESPACE)

  private val XxblUseIfAttrQName                 = QName("use-if-attr",                  XXBL_NAMESPACE)
  private val XxblKeepIfFunctionAvailableQName   = QName("keep-if-function-available",   XXBL_NAMESPACE)
  private val XxblKeepIfFunctionUnavailableQName = QName("keep-if-function-unavailable", XXBL_NAMESPACE)

  private object DefaultXblSupport extends XBLSupport {

    def keepElement(
      partAnalysisCtx : PartAnalysisForXblSupport,
      boundElement    : Element,
      directNameOpt   : Option[QName],
      elem            : Element
    ): Boolean = {

      def keepIfAttr: Boolean =
        elem.attributeValueOpt(XxblUseIfAttrQName) match {
          case Some(att) => boundElement.attributeValueOpt(att).flatMap(_.trimAllToOpt).nonEmpty
          case None      => true
        }

      def functionAvailable(fnQualifiedName: String): Boolean = {
        val fnQName = elem.resolveStringQNameOrThrow(fnQualifiedName, unprefixedIsNoNamespace = true)
        partAnalysisCtx.functionLibrary.isAvailable(new StructuredQName(fnQName.namespace.prefix, fnQName.namespace.uri, fnQName.localName), -1)
      }

      def keepIfFunctionAvailable: Boolean =
        elem.attributeValueOpt(XxblKeepIfFunctionAvailableQName) match {
          case Some(fnQualifiedName) => functionAvailable(fnQualifiedName)
          case _                     => true
        }

      def keepIfFunctionUnavailable: Boolean =
        elem.attributeValueOpt(XxblKeepIfFunctionUnavailableQName) match {
          case Some(fnQualifiedName) => ! functionAvailable(fnQualifiedName)
          case _                     => true
        }

      // TODO: Should also remove processed attributes.
      ! (! keepIfAttr || ! keepIfFunctionAvailable || ! keepIfFunctionUnavailable)
    }
  }

  /**
    * Apply an XBL transformation, i.e. apply `xbl:content`, `xbl:attr`, etc.
    *
    * NOTE: This mutates `shadowTreeDocument`.
    */
  def transform(
    partAnalysisCtx       : PartAnalysisContextForTree, // for `XblSupport`
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
    val directNameOpt    = abstractBindingOpt flatMap (_.commonBinding.directName)

    def isNestedHandler(e: Element): Boolean =
      (e.getParent eq boundElement) && EventHandler.isEventHandler(e)

    def isNestedLHHA(e: Element): Boolean =
      (e.getParent eq boundElement) && LHHA.isLHHA(e)

    def mustFilterOut(e: Element): Boolean =
      excludeNestedHandlers && isNestedHandler(e) || excludeNestedLHHA && isNestedLHHA(e)

    def setAttribute(
      nodes            : ju.List[Node],
      attributeQName   : QName,
      attributeValue   : String,
      namespaceElemOpt : Option[Element]
    ): Unit =
      if ((attributeValue ne null) && (nodes ne null) && ! nodes.isEmpty) {
        for (node <- nodes.asScala) {
          node match {
            case element: Element =>
              element.copyMissingNamespacesByPrefix(namespaceElemOpt)
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

    visitSubtree(shadowTreeDocument.getRootElement, currentElem => {

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
            for (node <- boundElement.content) {
              node match {
                case _: Namespace =>
                  // don't copy over namespace nodes
                case elem: Element =>
                  if (! mustFilterOut(elem))
                    clonedContent.add(elem.copyAndCopyParentNamespaces)
                case node =>
                  clonedContent.add(node.createCopy)
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
              NamespaceMapping(currentElem.allInScopeNamespacesAsStrings),
              null,
              null,
              null,
              null,
              null,
              null
            ).asInstanceOf[ju.List[om.NodeInfo]].asScala

            if (elements.nonEmpty) {
              // Clone all the resulting elements
              contentToInsert = new ju.ArrayList[Node](elements.size)
              for (currentNodeInfo <- elements) {
                val currentElement = unsafeUnwrapElement(currentNodeInfo)
                if (! mustFilterOut(currentElement))
                  contentToInsert.add(currentElement.copyAndCopyParentNamespaces)
              }
            } else {
              // Clone all the element's children if any
              // See: http://www.w3.org/TR/xbl/#the-content
              contentToInsert = new ju.ArrayList[Node](currentElem.nodeCount)
              for (currentElement <- currentElem.elements) {
                if (! mustFilterOut(currentElement))
                  contentToInsert.add(currentElement.copyAndCopyParentNamespaces)
              }
            }
          }

          // Insert content if any
          if ((contentToInsert ne null) && ! contentToInsert.isEmpty) {
            val parentContent = currentElem.getParent.jContent
            val elementIndex = parentContent.indexOf(currentElem)
            parentContent.addAll(elementIndex, contentToInsert)
          }

          // Remove <xbl:content> from shadow tree
          currentElem.detach()

          resultingNodes = contentToInsert
          if (scopeAttribute.nonAllBlank) {
            // If author specified scope attribute, use it
            setAttribute(resultingNodes, XXBL_SCOPE_QNAME, scopeAttribute, None)
          } else {
            // By default, set xxbl:scope="outer" on resulting elements
            setAttribute(resultingNodes, XXBL_SCOPE_QNAME, "outer", None)
          }
          true
        } else if (! DefaultXblSupport.keepElement(partAnalysisCtx, boundElement, directNameOpt, currentElem)) {
          // Skip this element
          currentElem.detach()
          false
        } else if (xblSupport exists (! _.keepElement(partAnalysisCtx, boundElement, directNameOpt, currentElem))) {
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
            val valueQName = currentElem.resolveStringQNameOrThrow(currentValue, unprefixedIsNoNamespace = true)
            if (valueQName.namespace.uri != Namespaces.XBL) {
              // This is not xbl:text, copy the attribute
              val attributeValue = boundElement.attributeValue(valueQName)
              if (attributeValue ne null)
                setAttribute(resultingNodes, valueQName, attributeValue, boundElement.some)
            } else {
              // This is xbl:text
              // "The xbl:text value cannot occur by itself in the list"
            }
          } else {
            // a=b pair
            val leftSideQName = {
              val leftSide = currentValue.substring(0, equalIndex)
              currentElem.resolveStringQNameOrThrow(leftSide, unprefixedIsNoNamespace = true)
            }
            val rightSideQName = {
              val rightSide = currentValue.substring(equalIndex + 1)
              currentElem.resolveStringQNameOrThrow(rightSide, unprefixedIsNoNamespace = true)
            }

            val isLeftSideXBLText  = leftSideQName.namespace.uri  == Namespaces.XBL
            val isRightSideXBLText = rightSideQName.namespace.uri == Namespaces.XBL

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
                setAttribute(resultingNodes, leftSideQName, rightSideValue, namespaceElement.some)
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
          NamespaceMapping(currentElem.allInScopeNamespacesAsStrings),
          null,
          null,
          null,
          null,
          null,
          null
        )
        if (! nodeInfos.isEmpty) {
          for (nodeInfo <- nodeInfos.asScala) {
            val currentNodeInfo = nodeInfo.asInstanceOf[om.NodeInfo]
            if (currentNodeInfo.getNodeKind == org.w3c.dom.Node.ATTRIBUTE_NODE) {
              val currentAttribute = unwrapAttribute(currentNodeInfo) getOrElse (throw new IllegalArgumentException)
              setAttribute(resultingNodes, currentAttribute.getQName, currentAttribute.getValue, Option(currentAttribute.getParent))
            }
          }
        }
      }

      // Check for AVTs
      if (supportAVTs) {
        for (att <- currentElem.attributes) {
          val attValue = att.getValue
          if (XMLUtils.maybeAVT(attValue)) {
            val newValue = XPathCache.evaluateAsAvt(
              boundElementInfo,
              attValue,
              NamespaceMapping(currentElem.allInScopeNamespacesAsStrings),
              null,
              null,
              null,
              null,
              null,
              null
            )
            setAttribute(ju.Collections.singletonList[Node](currentElem), att.getQName, newValue, currentElem.some)
          }
        }
      }

      processChildren
    })
    shadowTreeDocument
  }

  private def visitSubtree(container: Element, process: Element => Boolean): Unit =
    for (childNode <- container.content.toList) {
      childNode match {
        case e: Element =>
          if (process(e))
            visitSubtree(e, process)
        case _ =>
      }
    }
}