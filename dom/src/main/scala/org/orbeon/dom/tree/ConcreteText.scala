package org.orbeon.dom.tree

import org.orbeon.dom.{Text, Visitor}

class ConcreteText(var text: String) extends WithCharacterData with Text {

  override def getText               = text
  override def setText(text: String) = { this.text = text }

  def accept(visitor: Visitor)       = visitor.visit(this)

  override def toString: String = {
    super.toString + " [Text: \"" + getText + "\"]"
  }
}
