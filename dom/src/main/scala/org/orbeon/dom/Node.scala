package org.orbeon.dom

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
trait Text    extends Node