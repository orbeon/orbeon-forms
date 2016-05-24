package org.dom4j.io

import org.dom4j.{Element, ElementPath}

/**
  * `ElementStack` is used internally inside the  to maintain a stack of instances. It opens
  * an integration possibility allowing derivations to prune the tree when a node
  * is complete.
  */
class ElementStack(defaultCapacity: Int) extends ElementPath {

  private var stack = new Array[Element](defaultCapacity)

  // Index of the item at the top of the stack or -1 if the stack is empty
  private var lastElementIndex = -1

  def clear(): Unit = lastElementIndex = -1

  def peekElement: Element =
    if (lastElementIndex < 0)
      null
    else
      stack(lastElementIndex)

  def popElement(): Element =
    if (lastElementIndex < 0) {
      null
    } else {

      val result = stack(lastElementIndex)
      lastElementIndex -= 1
      result
    }

  def pushElement(element: Element): Unit = {
    val length = stack.length
    lastElementIndex += 1
    if (lastElementIndex >= length) {
      reallocate(length * 2)
    }
    stack(lastElementIndex) = element
  }

  private def reallocate(size: Int): Unit = {
    val oldStack = stack
    stack = Array.ofDim[Element](size)
    System.arraycopy(oldStack, 0, stack, 0, oldStack.length)
  }

  def size: Int = lastElementIndex + 1
  def getCurrent: Element = peekElement
}
