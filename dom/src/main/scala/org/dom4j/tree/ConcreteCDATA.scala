package org.dom4j.tree

import org.dom4j.{CDATA, Node, Visitor}

class ConcreteCDATA(var text: String) extends WithCharacterData with CDATA {

  override def getNodeType: Short = Node.CDATA_SECTION_NODE

  override def getText               = text
  override def setText(text: String) = this.text = text

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
  }

  override def toString: String = {
    super.toString + " [CDATA: \"" + getText + "\"]"
  }
}
