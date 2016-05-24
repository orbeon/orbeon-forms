package org.dom4j.tree

import org.dom4j.{Comment, Node, Visitor}

class ConcreteComment(var text: String) extends WithCharacterData with Comment {

  override def getNodeType: Short = Node.COMMENT_NODE

  override def getText               = text
  override def setText(text: String) = this.text = text

  def accept(visitor: Visitor) = visitor.visit(this)

  override def toString: String = {
    super.toString + " [Comment: \"" + getText + "\"]"
  }
}
