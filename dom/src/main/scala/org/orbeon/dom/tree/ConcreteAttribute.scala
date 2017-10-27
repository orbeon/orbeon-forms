package org.orbeon.dom.tree

import org.orbeon.dom._

class ConcreteAttribute(qname: QName, var value: String)
  extends AbstractNode with Attribute with WithParent with WithData {

  def getQName: QName = qname

  def getValue: String = value
  def setValue(value: String): Unit = this.value = value

  def setNamespace(namespace: Namespace): Unit = {
    val msg = "This Attribute is read only and cannot be changed"
    throw new UnsupportedOperationException(msg)
  }

  override def getText: String = getValue
  override def setText(text: String): Unit = setValue(text)

  override def toString: String = {
    super.toString + " [Attribute: name " + getQualifiedName +
      " value \"" +
      getValue +
      "\"]"
  }

  def accept(visitor: Visitor): Unit =
    visitor.visit(this)

  def getNamespace       = getQName.namespace
  def getNamespacePrefix = getQName.namespace.prefix
  def getNamespaceURI    = getQName.namespace.uri

  override def getName   = getQName.name
  def getQualifiedName   = getQName.qualifiedName
}
