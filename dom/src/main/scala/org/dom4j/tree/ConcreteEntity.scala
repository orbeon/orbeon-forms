package org.dom4j.tree

import org.dom4j.{Entity, Node, Visitor}

class ConcreteEntity(var name: String, var text: String) extends AbstractNode with Entity with WithParent {

  override def getNodeType: Short = Node.ENTITY_REFERENCE_NODE

  override def getName = name
  override def setName(name: String) = this.name = name

  override def getText = text
  override def setText(text: String) = this.text = text

  override def getStringValue: String = "&" + getName + ";"

  def accept(visitor: Visitor): Unit = {
    visitor.visit(this)
  }

  override def toString: String = {
    super.toString + " [Entity: &" + getName + ";]"
  }
}
