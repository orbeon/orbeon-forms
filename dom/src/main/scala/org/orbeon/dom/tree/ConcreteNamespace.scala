package org.orbeon.dom.tree

import org.orbeon.dom.{Namespace, Visitor}

case class ConcreteNamespace(prefix: String, uri: String) extends AbstractNode with Namespace {

  override def getText: String = uri
  override def getStringValue: String = uri

  def accept(visitor: Visitor) = visitor.visit(this)
}
