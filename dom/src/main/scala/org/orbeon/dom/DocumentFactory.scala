package org.orbeon.dom

import org.orbeon.dom.tree._

// NOTE: Should move this as `apply` functions to companion objects of `Document`, `Element`, etc.
object DocumentFactory {

  def createElement              (qName: QName)                 : Element               = new ConcreteElement(qName)
  def createElement              (name: String)                 : Element               = createElement(QName(name))
  def createAttribute            (name: String, value: String)  : Attribute             = createAttribute(QName(name), value)
  def createAttribute            (qName: QName, value: String)  : Attribute             = new ConcreteAttribute(qName, value)
  def createComment              (text: String)                 : Comment               = new ConcreteComment(text)
  def createProcessingInstruction(target: String, data: String) : ProcessingInstruction = new ConcreteProcessingInstruction(target, data)

  def createElementWithText(name: String, text: String): Element = {
    val e = createElement(name)
    e.setText(text)
    e
  }
}
