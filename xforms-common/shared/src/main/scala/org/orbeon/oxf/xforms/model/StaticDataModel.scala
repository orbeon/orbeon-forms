/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.model

import org.orbeon.oxf.util.StaticXPath.*
import org.orbeon.saxon.om.*
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.{BindingErrorReason, NodeBindingErrorReason}


object StaticDataModel {

  /**
   * Whether the given item is acceptable as a bound item.
   *
   * NOTE: As of 2012-04-26, we allow binding non-value controls to any node type.
   */
  def isAllowedBoundItem(item: Item): Boolean = item ne null

  /**
   * Whether the given item is acceptable as a bound item storing a value.
   *
   * The thinking as of 2012-04-26 is that it is ok for value controls to bind to text, PI and comment nodes, which is
   * not disallowed by per XForms, however doing so might lead to funny results when writing values back. In
   * particular, writing an empty value to a text node causes the control to become non-relevant.
   */
  def isAllowedValueBoundItem(item: Item): Boolean = item match {
    case _: AtomicValue                                                                     => true
    case node: NodeInfo if node.isAttribute || node.isElement && node.supportsSimpleContent => true
    case node: NodeInfo if node self (Text || PI || Comment) effectiveBooleanValue          => true
    case _                                                                                  => false
  }

  /**
   * If it is possible to write to the given item, return a VirtualNode, otherwise return a reason why not.
   *
   * It's not possible to write to:
   *
   * - atomic values (which are read-only)
   * - document nodes (even mutable ones)
   * - element nodes containing other elements
   * - items not backed by a mutable node (which are read-only)
   */
  def isWritableItem(item: Item): NodeBindingErrorReason Either VirtualNodeType = item match {
    case _: AtomicValue                                => Left(BindingErrorReason.ReadonlyNode)
    case node: VirtualNodeType if node.hasChildElement => Left(BindingErrorReason.DisallowedNode)
    case node: VirtualNodeType                         => Right(node)
    case _: DocumentNodeInfoType                       => Left(BindingErrorReason.DisallowedNode) // TODO: review this test
    case _                                             => Left(BindingErrorReason.ReadonlyNode)
  }
}
