package org.dom4j

import org.dom4j.tree._

object DocumentFactory {

  private val cache = new QNameCache

  def createDocument                                                          : Document              = new ConcreteDocument
  def createDocument             (rootElementName: String)                    : Document              = ConcreteDocument(createElement(createQName(rootElementName)))
  def createDocument             (rootElement: Element)                       : Document              = ConcreteDocument(rootElement)
  def createElement              (qName: QName)                               : Element               = new ConcreteElement(qName)
  def createElement              (name: String)                               : Element               = createElement(createQName(name))
  def createElement              (qualifiedName: String, namespaceURI: String): Element               = createElement(createQName(qualifiedName, namespaceURI))
  def createAttribute            (owner: Element, name: String, value: String): Attribute             = createAttribute(owner, createQName(name), value)
  def createAttribute            (owner: Element, qName: QName, value: String): Attribute             = new ConcreteAttribute(qName, value)
  def createCDATA                (text: String)                               : CDATA                 = new ConcreteCDATA(text)
  def createComment              (text: String)                               : Comment               = new ConcreteComment(text)
  def createText                 (text: String)                               : Text                  = new ConcreteText(text ensuring (_ ne null))
  def createEntity               (name: String, text: String)                 : Entity                = new ConcreteEntity(name, text)
  def createProcessingInstruction(target: String, data: String)               : ProcessingInstruction = new ConcreteProcessingInstruction(target, data)

  def createElementWithText(name: String, text: String): Element = {
    val e = createElement(name)
    e.setText(text)
    e
  }

  def createNamespace(prefix: String, uri: String)         = Namespace(prefix, uri)

  def createQName(localName: String, namespace: Namespace) = cache.get(localName, namespace)
  def createQName(localName: String)                       = cache.get(localName)
  def createQName(qualifiedName: String, uri: String)      = cache.get(qualifiedName, uri)
}
