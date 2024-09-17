package org.orbeon.dom.tree

import org.orbeon.dom.{Element, Node}

import scala.compiletime.uninitialized


trait WithParent extends Node {
  private var _parent: Element = uninitialized
  override def getParent: Element = _parent
  override def setParent(parent: Element): Unit = _parent = parent
}
