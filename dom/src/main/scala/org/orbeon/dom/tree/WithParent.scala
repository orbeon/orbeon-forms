package org.orbeon.dom.tree

import org.orbeon.dom.{Element, Node}

trait WithParent extends Node {
  private var _parent: Element = _
  override def getParent: Element = _parent
  override def setParent(parent: Element): Unit = _parent = parent
}
