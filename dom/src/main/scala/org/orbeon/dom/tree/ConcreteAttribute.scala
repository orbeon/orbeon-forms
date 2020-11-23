package org.orbeon.dom.tree

import org.orbeon.dom._

class ConcreteAttribute(qname: QName, var value: String)
  extends AbstractNode with Attribute with WithParent with WithData {

  def getQName: QName = qname

  def getValue: String = value
  def setValue(value: String): Unit = this.value = value

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

  def getNamespace       = qname.namespace
  def getNamespacePrefix = qname.namespace.prefix
  def getNamespaceURI    = qname.namespace.uri

  override def getName   = qname.localName
  def getQualifiedName   = qname.qualifiedName
}
