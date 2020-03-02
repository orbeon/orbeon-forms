/**
 * Copyright (C) 2009 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.itemset

import org.orbeon.saxon.om

trait ItemContainer {

  protected var _parent: ItemContainer = null
  def parent: ItemContainer = _parent

  private var _childrenReversed: List[ItemNode] = Nil

  def addChildItem(childItem: ItemNode): Unit = {
    childItem._parent = this
    _childrenReversed ::= childItem
  }

  def hasChildren: Boolean = _childrenReversed.nonEmpty
  def children: List[ItemNode] = _childrenReversed.reverse
  def lastChild: ItemNode = _childrenReversed.head

  // Visit the entire itemset
  def visit[T](listener: ItemsetListener): Unit =
    if (hasChildren) {
      listener.startLevel(selfItem) // Item is used only by menu, not ideal!
      var first = true
      for (item <- children) {
        listener.startItem(item, first)
        item.visit(listener)
        listener.endItem(item)
        first = false
      }
      listener.endLevel()
    }

  // Depth-first Iterator over all the items of this and children
  def allItemsIterator: Iterator[ItemNode] =
    selfIterator ++ (children.iterator flatMap (_.allItemsIterator))

  // Same as `allItemsIterator` but in reverse order
  def allItemsReverseIterator: Iterator[ItemNode] =
    (_childrenReversed.iterator flatMap (_.allItemsReverseIterator)) ++ selfIterator

  def allItemsWithValueIterator(reverse: Boolean): Iterator[(Item.ValueNode, Item.Value[om.Item])] =
    (if (reverse) allItemsReverseIterator else allItemsIterator) collect {
      case l: Item.ValueNode => l -> l.value
    }

  override def equals(other: Any): Boolean = other match {
    case c: ItemContainer => _childrenReversed == c._childrenReversed
    case _                => false
  }

  private def selfIterator: Iterator[ItemNode] =
    this match {
      case item: ItemNode => Iterator.single(item)
      case _              => Iterator.empty
    }

  private def selfItem: ItemNode = this match {
    case item: ItemNode => item
    case _              => null
  }
}