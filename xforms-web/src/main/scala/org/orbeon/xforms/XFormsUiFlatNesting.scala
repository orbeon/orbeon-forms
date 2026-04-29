/**
 * Copyright (C) 2020 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.xforms

import org.scalajs.dom.html

import scala.collection.mutable
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}


@JSExportTopLevel("OrbeonXFormsUiFlatNesting")
object XFormsUiFlatNesting {

  private def isGroupBeginEnd(node: html.Element): Boolean =
    node.classList.contains("xforms-group-begin-end")

  private def isGroupBegin(node: html.Element): Boolean =
    isGroupBeginEnd(node) && node.id.startsWith("group-begin-")

  private def isGroupEnd(node: html.Element): Boolean =
    isGroupBeginEnd(node) && node.id.startsWith("group-end-")

  private def isRepeatBeginEnd(node: html.Element): Boolean =
    node.classList.contains("xforms-repeat-begin-end")

  private def isRepeatBegin(node: html.Element): Boolean =
    isRepeatBeginEnd(node) && node.id.startsWith("repeat-begin-")

  private def isRepeatEnd(node: html.Element): Boolean =
    isRepeatBeginEnd(node) && node.id.startsWith("repeat-end-")

  private def isRepeatDelimiter(node: html.Element): Boolean =
    node.classList.contains("xforms-repeat-delimiter")

  private def isBegin(node: html.Element): Boolean =
    isGroupBegin(node) || isRepeatBegin(node)

  private def isEnd(node: html.Element): Boolean =
    isGroupEnd(node) || isRepeatEnd(node)

  // Folds over ancestors of `startNode` (group begin or repeat delimiter), stopping if `foldFunction` returns `stopValue`.
  private def foldAncestors[A](
    startNode    : html.Element,
    startValue   : A,
    foldFunction : (html.Element, A) => A,
    stopValue    : A
  ): A = {
    val isGroup  = isGroupBegin(startNode)
    val isRepeat = isRepeatDelimiter(startNode)

    var depth        = 0
    var currentNode  = startNode.previousElementSibling.asInstanceOf[html.Element]
    var currentValue = startValue

    while (currentNode ne null) {
      if (isEnd(currentNode))   depth += 1
      if (isBegin(currentNode)) depth -= 1
      if (depth < 0 && ((isGroup && isGroupEnd(currentNode)) || (isRepeat && isRepeatBegin(currentNode)))) {
        currentValue = foldFunction(currentNode, currentValue)
        if (currentValue == stopValue) return stopValue
      }
      currentNode = currentNode.previousElementSibling.asInstanceOf[html.Element]
    }
    currentValue
  }

  // Folds over descendants of `startNode`, stopping a sub-tree if `foldFunction` returns `stopValue`.
  private def foldDescendants[A](
    startNode    : html.Element,
    startValue   : A,
    foldFunction : (html.Element, A) => A,
    stopValue    : A
  ): A = {
    val isRepeat = isRepeatDelimiter(startNode)

    var depth        = 0
    var stopDepth    = 0
    var currentNode  = startNode.nextElementSibling.asInstanceOf[html.Element]
    val valueStack   = mutable.Stack[A]()
    var currentValue = startValue

    while (currentNode ne null) {
      if (isBegin(currentNode)) {
        depth += 1
        if (stopDepth > 0) {
          stopDepth += 1
        } else {
          valueStack.push(currentValue)
          currentValue = foldFunction(currentNode, currentValue)
          if (currentValue == stopValue) stopDepth += 1
        }
      } else if (isEnd(currentNode)) {
        depth -= 1
        if (depth < 0) return currentValue
        if (stopDepth > 0)
          stopDepth -= 1
        else
          currentValue = valueStack.pop()
      } else if (isRepeat && depth == 0 && isRepeatDelimiter(currentNode)) {
        return currentValue
      } else {
        if (stopDepth == 0) currentValue = foldFunction(currentNode, currentValue)
      }
      currentNode = currentNode.nextElementSibling.asInstanceOf[html.Element]
    }
    currentValue
  }

  private def hasAncestor(startNode: html.Element, conditionFunction: html.Element => Boolean): Boolean =
    foldAncestors(startNode, false, (node, _) => conditionFunction(node), true)

  @JSExport
  def setRelevant(node: html.Element, isRelevant: Boolean): Unit = {

    if (isRelevant)
      node.classList.remove("xforms-disabled")
    else
      node.classList.add("xforms-disabled")

    // If this group/iteration becomes relevant, but has a parent that is non-relevant, we should not
    // remove xforms-disabled otherwise it will incorrectly show, so our job stops here
    if (isRelevant && hasAncestor(node, _.classList.contains("xforms-disabled")))
      return

    foldDescendants(node, false, (node, _) => {
      // Skip sub-tree if we are enabling and this sub-tree is disabled
      if (isRelevant && isBegin(node) && node.classList.contains("xforms-disabled"))
        true
      else {
        if (isRelevant)
          node.classList.remove("xforms-disabled")
        else
          node.classList.add("xforms-disabled")
        false
      }
    }, true)
  }
}
