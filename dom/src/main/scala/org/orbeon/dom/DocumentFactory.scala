package org.orbeon.dom

import org.orbeon.dom.tree._

object DocumentFactory {

  def createDocument                                                          : Document              = new ConcreteDocument
  def createDocument             (rootElementName: String)                    : Document              = ConcreteDocument(createElement(QName(rootElementName)))
  def createDocument             (rootElement: Element)                       : Document              = ConcreteDocument(rootElement)
  def createElement              (qName: QName)                               : Element               = new ConcreteElement(qName)
  def createElement              (name: String)                               : Element               = createElement(QName(name))
  def createAttribute            (owner: Element, name: String, value: String): Attribute             = createAttribute(owner, QName(name), value)
  def createAttribute            (owner: Element, qName: QName, value: String): Attribute             = new ConcreteAttribute(qName, value)
  def createComment              (text: String)                               : Comment               = new ConcreteComment(text)
  def createText                 (text: String)                               : Text                  = new ConcreteText(text ensuring (_ ne null))
  def createProcessingInstruction(target: String, data: String)               : ProcessingInstruction = new ConcreteProcessingInstruction(target, data)

  def createElementWithText(name: String, text: String): Element = {
    val e = createElement(name)
    e.setText(text)
    e
  }
}
