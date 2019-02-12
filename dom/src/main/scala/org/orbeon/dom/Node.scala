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

  def hasContent: Boolean
  def getName: String
  def getText: String
  def setText(text: String): Unit

  def getStringValue: String

  def detach(): Node

  def accept(visitor: Visitor): Unit

  // Maybe check this: https://tpolecat.github.io/2015/04/29/f-bounds.html
  def deepCopy: Node
}

trait Comment extends Node

object Text {
  def apply(text: String): Text = new Text(text ensuring (_ ne null))
}

class Text(var text: String) extends AbstractNode with WithParent {

  override def getText: String = text
  override def setText(text: String): Unit = this.text = text

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString = s"""Text("$text")"""
}
