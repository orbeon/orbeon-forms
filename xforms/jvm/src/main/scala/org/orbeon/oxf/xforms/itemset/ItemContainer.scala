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

  var level: Int = 0
  def isTopLevel: Boolean = level == 0

  var parent: ItemContainer = null

  private var _children: List[Item] = Nil

  def addChildItem(childItem: Item): Unit = {
    childItem.level = level
    childItem.parent = this

    _children ::= childItem
  }

  def hasChildren: Boolean = _children.nonEmpty
  def children: List[Item] = _children.reverse
  def lastChild: Item = _children.head

  // Visit the entire itemset
  def visit[T](o: T, listener: ItemsetListener[T]): Unit = {
    if (hasChildren) {
      listener.startLevel(o, selfItem) // Item is used only by menu, not ideal!
      var first = true
      for (item <- children) {
        listener.startItem(o, item, first)
        item.visit(o, listener)
        listener.endItem(o, item)
        first = false
      }
      listener.endLevel(o)
    }
  }

  // Depth-first Iterator over all the items of this and children
  def allItemsIterator: Iterator[Item] =
    selfIterator ++ (children.iterator flatMap (_.allItemsIterator))

  // Same as `allItemsIterator` but in reverse order
  def allItemsReverseIterator: Iterator[Item] =
    (_children.iterator flatMap (_.allItemsReverseIterator)) ++ selfIterator

  def allItemsWithValueIterator(reverse: Boolean): Iterator[(Item, Item.ItemValue[om.Item])] =
    for {
      currentItem      <- if (reverse) allItemsReverseIterator else allItemsIterator
      currentItemValue <- currentItem.value.iterator // TODO: `value` should not be an `Option`?
    } yield
      currentItem -> currentItemValue

  override def equals(other: Any): Boolean = other match {
    case c: ItemContainer => _children == c._children
    case _                => false
  }

  private def selfIterator: Iterator[Item] =
    this match {
      case item: Item => Iterator.single(item)
      case _          => Iterator.empty
    }

  private def selfItem = this match {
    case item: Item => item
    case _          => null
  }
}