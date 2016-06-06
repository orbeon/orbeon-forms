package org.dom4j

object Node {
  val ELEMENT_NODE                : Short = 1
  val ATTRIBUTE_NODE              : Short = 2
  val TEXT_NODE                   : Short = 3
  val CDATA_SECTION_NODE          : Short = 4
  val ENTITY_REFERENCE_NODE       : Short = 5
  val PROCESSING_INSTRUCTION_NODE : Short = 7
  val COMMENT_NODE                : Short = 8
  val DOCUMENT_NODE               : Short = 9
  val NAMESPACE_NODE              : Short = 13
  val UNKNOWN_NODE                : Short = 14
}

trait Node extends Cloneable {

  def getParent: Element
  def setParent(parent: Element): Unit

  def getDocument: Document
  def setDocument(document: Document): Unit

  def hasContent: Boolean
  def getName: String
  def setName(name: String): Unit
  def getText: String
  def setText(text: String): Unit

  def getStringValue: String

  def getNodeType: Short
  def getNodeTypeName: String

  def detach(): Node

  def accept(visitor: Visitor): Unit
  override def clone(): AnyRef = super.clone()
}
