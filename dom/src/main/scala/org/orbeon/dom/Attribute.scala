package org.orbeon.dom

import org.orbeon.dom.tree.ConcreteAttribute

/**
 * `Attribute` defines an XML attribute. An attribute may have a
 * name, an optional namespace and a value.
 */
trait Attribute extends Node {
  def getQName: QName
  def getNamespace: Namespace
  def setNamespace(namespace: Namespace): Unit
  def getNamespacePrefix: String
  def getNamespaceURI: String
  def getQualifiedName: String
  def getValue: String
  def setValue(value: String): Unit
  def getData: AnyRef
  def setData(data: AnyRef): Unit
}

object Attribute {
  def apply(qName: QName, value: String): Attribute = new ConcreteAttribute(qName, value)
  def apply(name: String, value: String): Attribute = Attribute(QName(name), value)
}