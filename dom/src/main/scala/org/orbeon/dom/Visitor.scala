package org.orbeon.dom

trait Visitor {
  def visit(node: Document)              : Unit
  def visit(node: Element)               : Unit
  def visit(node: Attribute)             : Unit
  def visit(node: Comment)               : Unit
  def visit(node: Namespace)             : Unit
  def visit(node: ProcessingInstruction) : Unit
  def visit(node: Text)                  : Unit
}
