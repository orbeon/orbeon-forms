/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.dom.{Attribute, Document, Element, Node}
import org.orbeon.oxf.xml.Dom4j

object InstanceDataOps {

  // Remove all instance data starting at the given node.
  // The node can be a Document, Element or Attribute. If a Document, start at the root element if any.
  def removeRecursively[A <: Node](node: A): A =
    setDataRecursively(
      node               = node,
      updateInstanceData = (n, _) => InstanceData.removeInstanceData(n)
    )

  // Annotate instance data with the `requireDefaultValue` flag set to `true`, starting at the given node.
  // For Element nodes with children elements, remove instance data instead.
  // The node can be a Document, Element or Attribute. If a Document, start at the root element if any.
  def setRequireDefaultValueRecursively[A <: Node](node: A): A =
    setDataRecursively(
      node               = node,
      updateInstanceData = (n, hasChildrenElems) =>
        if (hasChildrenElems)
          InstanceData.removeInstanceData(n)
        else
          InstanceData.setRequireDefaultValue(n)
    )

  def clearRequireDefaultValueRecursively[A <: Node](node: A): A =
    setDataRecursively(
      node               = node,
      updateInstanceData = (n, _) => InstanceData.clearRequireDefaultValue(n)
      // NOTE: Don't check `hasChildrenElems` because there could have been subsequent element insertions since the
      // moment the flags were set. So instead check all elements.
    )

  private def setDataRecursively[A <: Node](node: A, updateInstanceData: (Node, Boolean) => Unit): A = {

    // We can't store data on the Document object. Use root element instead.
    val adjustedNode =
      node match {
        case document: Document => document.getRootElement
        case node               => node
      }

    adjustedNode match {
      case elem: Element =>
        val childrenElems = Dom4j.elements(elem)

        updateInstanceData(elem, childrenElems.nonEmpty)

        Dom4j.attributes(elem) foreach (setDataRecursively(_, updateInstanceData))
        childrenElems          foreach (setDataRecursively(_, updateInstanceData))
      case attribute: Attribute =>
        updateInstanceData(attribute, false)
      case _ =>
    }

    node
  }
}
