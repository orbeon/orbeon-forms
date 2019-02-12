package org.orbeon.dom

import org.orbeon.dom.tree.{AbstractNode, WithParent}

object Node {
  def nodeTypeName(node: Node): String = node match {
    case _: Element               ⇒ "Element"
    case _: Attribute             ⇒ "Attribute"
    case _: Text                  ⇒ "Text"
    case _: Document              ⇒ "Document"
    case _: Comment               ⇒ "Comment"
    case _: ProcessingInstruction ⇒ "ProcessingInstruction"
    case _: Namespace             ⇒ "Namespace"
    case _                        ⇒ throw new IllegalStateException
  }
}

trait Node extends Cloneable {

  def getParent: Element
  def setParent(parent: Element): Unit

  def getDocument: Document
  def setDocument(document: Document): Unit

  def getName: String
  def getText: String
  def setText(text: String): Unit

  def getStringValue: String

  def detach(): Node

  def accept(visitor: Visitor): Unit

  // Maybe check this: https://tpolecat.github.io/2015/04/29/f-bounds.html
  def deepCopy: Node
}

object Text {
  def apply(text: String): Text = new Text(text ensuring (_ ne null))
}

class Text(var text: String) extends AbstractNode with WithParent {

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""Text("$text")"""
}

object Comment {
  def apply(text: String): Comment = new Comment(text ensuring (_ ne null))
}

class Comment(var text: String) extends AbstractNode with WithParent {

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""Comment("$text")"""
}

object ProcessingInstruction {
  def apply(target: String, data: String): ProcessingInstruction =
    new ProcessingInstruction(target, data)
}

class ProcessingInstruction(var target: String, var text: String)
  extends AbstractNode with WithParent {

  override def getName: String = getTarget

  def getTarget: String = target
  def setTarget(target: String): Unit = this.target = target

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""ProcessingInstruction("$target", "$text")"""
}
