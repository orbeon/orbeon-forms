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

import collection.JavaConverters._

trait ItemContainer {

  var level: Int = 0
  def isTopLevel = level == 0

  var parent: ItemContainer = null

  private var _children: List[Item] = Nil

  def addChildItem(childItem: Item): Unit = {
    childItem.level = level
    childItem.parent = this

    _children ::= childItem
  }

  def hasChildren = _children.nonEmpty
  def children = _children.reverse
  def lastChild = _children.head

  def pruneNonRelevantChildren(): Unit = {
    // Prune children first
    _children foreach (_.pruneNonRelevantChildren())
    // Keep only children which have children or which have a value
    _children = _children filter (child ⇒ child.hasChildren || (child.value ne null))
  }

  // Visit the entire itemset
  def visit[T](o: T, listener: ItemsetListener[T]): Unit = {
    if (hasChildren) {
      listener.startLevel(o, selfItem) // Item is used only by menu, not ideal!
      var first = true
      for (item ← children) {
        listener.startItem(o, item, first)
        item.visit(o, listener)
        listener.endItem(o, item)
        first = false
      }
      listener.endLevel(o)
    }
  }

  // Depth-first Iterator over all the items of this and children
  def allItemsIterator: Iterator[Item] = {
    def selfIterator = new Iterator[Item] {
      var current = selfItem
      def hasNext = current ne null
      def next() = {
        val result = current
        current = null
        result
      }
    }

    def childrenIterator = children.iterator flatMap (_.allItemsIterator)

    selfIterator ++ childrenIterator
  }

  def jAllItemsIterator = allItemsIterator.asJava

  // Implement deep equals
  override def equals(other: Any) = other match {
    case other: ItemContainer ⇒ _children == other._children
    case _                    ⇒ false
  }

  private def selfItem = this match {
    case item: Item ⇒ item
    case _          ⇒ null
  }
}