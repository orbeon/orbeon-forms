package org.orbeon.xforms

import org.orbeon.web.DomSupport.*
import org.scalajs.dom.html

import scala.collection.mutable



/**
 * For nested groups:
 *
 *      <td id="group-begin-outer-group-flat" class="xforms-group-begin-end">
 *          ...
 *          <td id="group-begin-inner-group-flat" class="xforms-group-begin-end">
 *              ...
 *          <td id="group-end-inner-group-flat" class="xforms-group-begin-end">
 *          ...
 *      <td id="group-end-outer-group-flat" class="xforms-group-begin-end">
 *
 * For nested repeats (specific iteration of the outer repeat):
 *
 *      <span class="xforms-repeat-delimiter">
 *          ...
 *          <span class="xforms-repeat-begin-end" id="repeat-begin-inner-repeat⊙1">
 *          <span class="xforms-repeat-delimiter">
 *              ...
 *          <span class="xforms-repeat-begin-end" id="repeat-end-inner-repeat⊙1"></span>
 *          ...
 *      <span class="xforms-repeat-delimiter">
 */
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
    startNode   : html.Element,
    startValue  : A,
    foldFunction: (html.Element, A) => A,
    stopValue   : A
  ): A = {
    val isGroup  = isGroupBegin(startNode)
    val isRepeat = isRepeatDelimiter(startNode)

    var depth        = 0
    var currentNode  = startNode.previousElementSiblingT
    var currentValue = startValue

    while (currentNode ne null) {
      if (isEnd(currentNode))   depth += 1
      if (isBegin(currentNode)) depth -= 1
      if (depth < 0 && ((isGroup && isGroupEnd(currentNode)) || (isRepeat && isRepeatBegin(currentNode)))) {
        currentValue = foldFunction(currentNode, currentValue)
        if (currentValue == stopValue)
          return stopValue
      }
      currentNode = currentNode.previousElementSiblingT
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
    var currentNode  = startNode.nextElementSiblingT
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
      currentNode = currentNode.nextElementSiblingT
    }
    currentValue
  }

  private def hasAncestor(startNode: html.Element, conditionFunction: html.Element => Boolean): Boolean =
    foldAncestors(startNode, false, (node, _: Boolean) => conditionFunction(node), true)

  def setRelevant(node: html.Element, isRelevant: Boolean): Unit = {

    node.toggleClass("xforms-disabled", ! isRelevant)

    // If this group/iteration becomes relevant, but has a parent that is non-relevant, we should not
    // remove xforms-disabled otherwise it will incorrectly show, so our job stops here
    if (isRelevant && hasAncestor(node, _.classList.contains("xforms-disabled")))
      return

    foldDescendants(node, false, (node, _: Boolean) => {
      // Skip sub-tree if we are enabling and this sub-tree is disabled
      if (isRelevant && isBegin(node) && node.classList.contains("xforms-disabled"))
        true
      else {
        node.toggleClass("xforms-disabled", ! isRelevant)
        false
      }
    }, true)
  }
}
