package org.orbeon.dom.tree

import java.io.Serializable

import org.orbeon.dom.{Document, Element, Node}

abstract class AbstractNode extends Node with Serializable { // TODO: `Serializable` needed?

  def getDocument: Document = {
    val element = getParent
    if (element ne null) element.getDocument else null
  }

  def setDocument(document: Document) = ()

  def getParent: Element = null
  def setParent(parent: Element) = ()

  def deepCopy: Node = {
    val clone = super.clone().asInstanceOf[Node]
    clone.setParent(null)
    clone.setDocument(null)
    clone
  }

  def createCopy: Node = deepCopy

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

  def getText: String = null
  def setText(text: String): Unit =
    throw new UnsupportedOperationException("This node cannot be modified")

  def getStringValue: String = getText
}
