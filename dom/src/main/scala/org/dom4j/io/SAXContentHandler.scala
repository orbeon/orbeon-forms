package org.dom4j.io

import java.{lang ⇒ jl}

import org.dom4j._
import org.dom4j.tree.{NamespaceStack, ConcreteElement}
import org.xml.sax._
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.DefaultHandler

/**
 * `SAXContentHandler` builds a dom4j tree via SAX events.
 */
class SAXContentHandler(
  systemIdOpt         : Option[String],
  mergeAdjacentText   : Boolean,
  stripWhitespaceText : Boolean,
  ignoreComments      : Boolean
) extends DefaultHandler with LexicalHandler {

  protected val elementStack = new ElementStack(50)
  private val namespaceStack = new NamespaceStack

  private lazy val document = createDocument
  def getDocument = document

  // State
  private var locator: Locator = _
  private var entity: String = _
  private var insideCDATASection = false
  private var cdataText: jl.StringBuilder = _
  private var declaredNamespaceIndex = 0
  private var currentElement: Element = _
  private var textInTextBuffer = false
  private var textBuffer: jl.StringBuilder = _

  override def setDocumentLocator(documentLocator: Locator): Unit =
    this.locator = documentLocator

  override def processingInstruction(target: String, data: String): Unit = {
    if (mergeAdjacentText && textInTextBuffer) {
      completeCurrentTextNode()
    }
    if (currentElement != null) {
      currentElement.addProcessingInstruction(target, data)
    } else {
      getDocument.addProcessingInstruction(target, data)
    }
  }

  override def startPrefixMapping(prefix: String, uri: String): Unit =
    namespaceStack.push(prefix, uri)

  override def endPrefixMapping(prefix: String): Unit = {
    namespaceStack.pop(prefix)
    declaredNamespaceIndex = namespaceStack.size
  }

  override def startDocument(): Unit = {
//    document = null
    currentElement = null
    elementStack.clear()
    namespaceStack.clear()
    declaredNamespaceIndex = 0
    if (mergeAdjacentText && (textBuffer == null)) {
      textBuffer = new jl.StringBuilder
    }
    textInTextBuffer = false
  }

  override def endDocument(): Unit = {
    namespaceStack.clear()
    elementStack.clear()
    currentElement = null
    textBuffer = null
  }

  override def startElement(
    namespaceURI  : String,
    localName     : String,
    qualifiedName : String,
    attributes    : Attributes
  ): Unit = {
    if (mergeAdjacentText && textInTextBuffer)
      completeCurrentTextNode()

    val qName = namespaceStack.getQName(namespaceURI, localName, qualifiedName)

    val branch = Option(currentElement) getOrElse getDocument
    val element = branch.addElement(qName)
    addDeclaredNamespaces(element)
    addAttributes(element, attributes)
    elementStack.pushElement(element)
    currentElement = element
    entity = null
  }

  override def endElement(
    namespaceURI : String,
    localName    : String,
    qName        : String
  ): Unit = {
    if (mergeAdjacentText && textInTextBuffer)
      completeCurrentTextNode()

    elementStack.popElement()
    currentElement = elementStack.peekElement
  }

  override def characters(ch: Array[Char], start: Int, end: Int): Unit = {
    if (end == 0)
      return

    if (currentElement != null) {
      if (entity != null) {
        if (mergeAdjacentText && textInTextBuffer)
          completeCurrentTextNode()

        currentElement.addEntity(entity, new String(ch, start, end))
        entity = null
      } else if (insideCDATASection) {
        if (mergeAdjacentText && textInTextBuffer)
          completeCurrentTextNode()

        cdataText.append(new String(ch, start, end))
      } else {
        if (mergeAdjacentText) {
          textBuffer.append(ch, start, end)
          textInTextBuffer = true
        } else {
          currentElement.addText(new String(ch, start, end))
        }
      }
    }
  }

  override def warning(exception: SAXParseException)   : Unit = ()
  override def error(exception: SAXParseException)     : Unit = throw exception
  override def fatalError(exception: SAXParseException): Unit = throw exception

  def startDTD(name: String, publicId: String, systemId: String) = ()
  def endDTD()                                                   = ()
  def startEntity(name: String)                                  = ()
  def endEntity(name: String)                                    = ()

  def startCDATA(): Unit = {
    insideCDATASection = true
    cdataText = new jl.StringBuilder
  }

  def endCDATA(): Unit = {
    insideCDATASection = false
    currentElement.addCDATA(cdataText.toString)
  }

  def comment(ch: Array[Char], start: Int, end: Int): Unit = {
    if (!ignoreComments) {
      if (mergeAdjacentText && textInTextBuffer) {
        completeCurrentTextNode()
      }
      val text = new String(ch, start, end)
      if (text.length > 0) {
        if (currentElement != null) {
          currentElement.addComment(text)
        } else {
          getDocument.addComment(text)
        }
      }
    }
  }

  override def notationDecl(name: String, publicId: String, systemId: String)                             = ()
  override def unparsedEntityDecl(name: String, publicId: String, systemId: String, notationName: String) = ()

  /**
   * If the current text buffer contains any text then create a new text node
   * with it and add it to the current element.
   */
  private def completeCurrentTextNode(): Unit = {
    if (stripWhitespaceText) {
      var whitespace = true

      val breaks = new scala.util.control.Breaks
      import breaks._

      breakable {
        for (i ← 0 until textBuffer.length if ! Character.isWhitespace(textBuffer.charAt(i))) {
          whitespace = false
          break()
        }
      }
      if (!whitespace) {
        currentElement.addText(textBuffer.toString)
      }
    } else {
      currentElement.addText(textBuffer.toString)
    }
    textBuffer.setLength(0)
    textInTextBuffer = false
  }

  private def createDocument: Document = {
    val doc = DocumentFactory.createDocument
    systemIdOpt foreach doc.setName
    doc
  }

  /**
   * Add all namespaces declared before the startElement() SAX event to the
   * current element so that they are available to child elements and
   * attributes.
   */
  private def addDeclaredNamespaces(element: Element): Unit = {

    val size = namespaceStack.size

    while (declaredNamespaceIndex < size) {
      element.add(namespaceStack.getNamespace(declaredNamespaceIndex))
      declaredNamespaceIndex += 1
    }
  }

  private def addAttributes(element: Element, attributes: Attributes): Unit = {
    val noNamespaceAttributes = false
    element match {
      case baseElement: ConcreteElement ⇒
        baseElement.setAttributes(attributes, namespaceStack, noNamespaceAttributes)
      case _ ⇒
        for (i ← 0 until attributes.getLength) {
          val attributeQName = attributes.getQName(i)
          if (noNamespaceAttributes || !attributeQName.startsWith("xmlns")) {
            val attributeURI = attributes.getURI(i)
            val attributeLocalName = attributes.getLocalName(i)
            val attributeValue = attributes.getValue(i)
            val qName = namespaceStack.getAttributeQName(attributeURI, attributeLocalName, attributeQName)
            element.addAttribute(qName, attributeValue)
          }
        }
    }
  }
}
