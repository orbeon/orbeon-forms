package org.dom4j

abstract class VisitorSupport extends Visitor {
  def visit(node: Document)              = ()
  def visit(node: Element)               = ()
  def visit(node: Attribute)             = ()
  def visit(node: CDATA)                 = ()
  def visit(node: Comment)               = ()
  def visit(node: Entity)                = ()
  def visit(node: Namespace)             = ()
  def visit(node: ProcessingInstruction) = ()
  def visit(node: Text)                  = ()
}
