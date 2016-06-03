package org.dom4j.tree

import org.dom4j.{Namespace, Node, Visitor}

case class ConcreteNamespace(prefix: String, uri: String) extends AbstractNode with Namespace {

  override def getNodeType: Short = Node.NAMESPACE_NODE

  override def getText: String = uri
  override def getStringValue: String = uri

  def accept(visitor: Visitor) = visitor.visit(this)
}
