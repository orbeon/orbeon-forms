package org.orbeon.dom

import org.orbeon.dom.tree.ConcreteAttribute

/**
 * `Attribute` defines an XML attribute. An attribute may have a
 * name, an optional namespace and a value.
 */
trait Attribute extends Node {
  def getQName: QName
  def getNamespace: Namespace
  def getNamespacePrefix: String
  def getNamespaceURI: String
  def getQualifiedName: String
  def getValue: String
  def setValue(value: String): Unit
  def getData: AnyRef
  def setData(data: AnyRef): Unit

  def toDebugString: String = s"""$getQualifiedName="$getValue""""
}

object Attribute {
  def apply(qName: QName, value: String): Attribute = new ConcreteAttribute(qName, value)
  def apply(name: String, value: String): Attribute = Attribute(QName(name), value)

  // An ordering for attributes, which takes into account the namespace URI and the local name
  implicit object AttributeOrdering extends Ordering[Attribute] {
    def compare(x: Attribute, y: Attribute): Int =
      x.getQName.uriQualifiedName compare y.getQName.uriQualifiedName
  }
}