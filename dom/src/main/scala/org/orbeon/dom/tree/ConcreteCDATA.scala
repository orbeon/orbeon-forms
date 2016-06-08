package org.orbeon.dom.tree

import org.orbeon.dom.{CDATA, Visitor}

class ConcreteCDATA(var text: String) extends WithCharacterData with CDATA {

  override def getText               = text
  override def setText(text: String) = this.text = text

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
  }

  override def toString: String = {
    super.toString + " [CDATA: \"" + getText + "\"]"
  }
}
