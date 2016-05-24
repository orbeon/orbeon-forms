package org.dom4j.tree

import org.dom4j.{Element, Node}

trait WithParent extends Node {
  private var _parent: Element = _
  override def getParent = _parent
  override def setParent(parent: Element) = _parent = parent
}
