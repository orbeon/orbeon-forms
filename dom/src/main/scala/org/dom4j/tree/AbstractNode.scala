package org.dom4j.tree

import java.io.Serializable

import org.dom4j.tree.AbstractNode._
import org.dom4j.{Document, Element, Node}

private object AbstractNode {
  val NodeTypeNames = Array(
    "Node",
    "Element",
    "Attribute",
    "Text",
    "CDATA",
    "Entity",
    "Entity",
    "ProcessingInstruction",
    "Comment",
    "Document",
    "DocumentType",
    "DocumentFragment",
    "Notation",
    "Namespace",
    "Unknown"
  )
}

abstract class AbstractNode extends Node with Cloneable with Serializable {

  def getNodeType: Short = Node.UNKNOWN_NODE

  def getNodeTypeName: String = {
    val `type` = getNodeType
    if ((`type` < 0) || (`type` >= NodeTypeNames.length)) {
      return "Unknown"
    }
    NodeTypeNames(`type`)
  }

  def getDocument: Document = {
    val element = getParent
    if (element ne null) element.getDocument else null
  }

  def setDocument(document: Document) = ()

  def getParent: Element = null
  def setParent(parent: Element) = ()

  def hasContent: Boolean = false

  override def clone(): AnyRef = {
    val clone = super.clone().asInstanceOf[Node]
    clone.setParent(null)
    clone.setDocument(null)
    clone
  }

  def detach(): Node = {
    val parent = getParent
    if (parent ne null) {
      parent.remove(this)
    } else {
      val document = getDocument
      if (document ne null) {
        document.remove(this)
      }
    }
    setParent(null)
    setDocument(null)
    this
  }

  def getName: String = null
  def setName(name: String): Unit =
    throw new UnsupportedOperationException("This node cannot be modified")

  def getText: String = null
  def setText(text: String): Unit =
    throw new UnsupportedOperationException("This node cannot be modified")

  def getStringValue: String = getText
}
