package org.orbeon.dom.tree

import org.orbeon.dom.{Text, Visitor}

class ConcreteText(var text: String) extends AbstractNode with WithParent with Text {

  override def getText               = text
  override def setText(text: String) = { this.text = text }

  def accept(visitor: Visitor)       = visitor.visit(this)

  override def toString = s"""Text("$text")"""
}
