package org.orbeon.dom.io

import java.{util => ju}

import org.orbeon.dom._
import org.orbeon.dom.io.SAXWriter._
import org.orbeon.dom.tree.NamespaceStack
import org.xml.sax._
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.{AttributesImpl, LocatorImpl}

import scala.beans.BeanProperty

private object SAXWriter {

  val LexicalHandlerNames = List(
    "http://xml.org/sax/properties/lexical-handler"
  )

  val FeatureNamespacePrefixes = "http://xml.org/sax/features/namespace-prefixes"
  val FeatureNamespaces        = "http://xml.org/sax/features/namespaces"
}

/**
 * `SAXWriter` writes a document tree to a SAX `ContentHandler`.
 */
class SAXWriter extends XMLReader {

  @BeanProperty
  var contentHandler: ContentHandler = _

  private var dtdHandler: DTDHandler = _

  @BeanProperty
  var entityResolver: EntityResolver = _

  @BeanProperty
  var errorHandler: ErrorHandler = _

  @BeanProperty
  var lexicalHandler: LexicalHandler = _

  // Reusable attributes used by `createAttributes()` only
  private val attributes = new AttributesImpl

  private val features: ju.Map[String, java.lang.Boolean] = new ju.HashMap[String, java.lang.Boolean]()
  private val properties: ju.Map[String, AnyRef]          = new ju.HashMap[String, AnyRef]()

  properties.put(FeatureNamespacePrefixes, java.lang.Boolean.FALSE)
  properties.put(FeatureNamespacePrefixes, java.lang.Boolean.TRUE)

  def write(node: Node): Unit =
    node match {
      case n: Element               => writeElement(n)
      case n: Text                  => writeText(n.getText)
      case n: ProcessingInstruction => writeProcessingInstruction(n)
      case n: Comment               => writeComment(n)
      case n: Document              => writeDocument(n)
      case _: Namespace             => // NOP
      case n                        => throw new SAXException(s"Invalid node type: $n")
    }

  private def writeDocument(document: Document): Unit =
    if (document ne null) {
      createDocumentLocator(document) foreach contentHandler.setDocumentLocator
      contentHandler.startDocument()
      writeContent(document, new NamespaceStack)
      contentHandler.endDocument()
    }

  private def writeElement(element: Element): Unit =
    writeElement(element, new NamespaceStack)

  private def writeText(text: String): Unit =
    if (text ne null) {
      val chars = text.toCharArray
      contentHandler.characters(chars, 0, chars.length)
    }

  private def writeComment(comment: Comment): Unit =
    if (lexicalHandler ne null) {
      val text = comment.getText
      val chars = text.toCharArray
      lexicalHandler.comment(chars, 0, chars.length)
    }

  private def writeProcessingInstruction(pi: ProcessingInstruction): Unit =
    contentHandler.processingInstruction(pi.getTarget, pi.getText)

  def getDTDHandler: DTDHandler = dtdHandler
  def setDTDHandler(handler: DTDHandler): Unit =
    this.dtdHandler = handler

  def getFeature(name: String): Boolean = {
    val answer = features.get(name)
    (answer ne null) && answer.booleanValue
  }

  def setFeature(name: String, value: Boolean): Unit =
    name match {
      case FeatureNamespacePrefixes if value =>
        throw new SAXNotSupportedException("Namespace prefixes feature is never supported")
      case FeatureNamespaces if ! value =>
        throw new SAXNotSupportedException("Namespace feature is always supported")
      case FeatureNamespaces =>
      case _ =>
        features.put(name, value)
    }

  def setProperty(name: String, value: AnyRef): Unit =
    LexicalHandlerNames find (_ == name) match {
      case Some(_) => setLexicalHandler(value.asInstanceOf[LexicalHandler])
      case None    => properties.put(name, value)
    }

  def getProperty(name: String): AnyRef =
    LexicalHandlerNames find (_ == name) match {
      case Some(_) => getLexicalHandler
      case None    => properties.get(name)
    }

  def parse(systemId: String): Unit =
    throw new SAXNotSupportedException("This XMLReader can only accept a DocumentInputSource")

  def parse(input: InputSource): Unit =
    input match {
      case documentInput: DocumentInputSource => writeDocument(documentInput.getDocument)
      case _                                  => throw new SAXNotSupportedException("This XMLReader can only accept a DocumentInputSource")
    }

  private def writeContent(branch: Branch, namespaceStack: NamespaceStack): Unit = {
    val iter = branch.nodeIterator
    while (iter.hasNext) {
      iter.next() match {
        case element : Element               => writeElement(element, namespaceStack)
        case text    : Text                  => writeText(text.getText)
        case comment : Comment               => writeComment(comment)
        case pi      : ProcessingInstruction => writeProcessingInstruction(pi)
        case _       : Namespace             => // ignore
        case _                               => throw new IllegalStateException
      }
    }
  }

  protected def createDocumentLocator(document: Document): Option[Locator] = {
    val locator = new LocatorImpl
    locator.setLineNumber(-1)
    locator.setColumnNumber(-1)
    Some(locator)
  }

  private def writeElement(element: Element, namespaceStack: NamespaceStack): Unit = {
    val stackSize = namespaceStack.size
    startPrefixMapping(element, namespaceStack)
    startElement(element)
    writeContent(element, namespaceStack)
    endElement(element)
    endPrefixMapping(namespaceStack, stackSize)
  }

  private def startPrefixMapping(element: Element, namespaceStack: NamespaceStack): Unit = {
    val elementNamespace = element.getNamespace
    if ((elementNamespace ne null) && !isIgnorableNamespace(elementNamespace, namespaceStack)) {
      namespaceStack.push(elementNamespace)
      contentHandler.startPrefixMapping(elementNamespace.prefix, elementNamespace.uri)
    }

    for (namespace <- element.declaredNamespacesIterator)
      if (! isIgnorableNamespace(namespace, namespaceStack)) {
        namespaceStack.push(namespace)
        contentHandler.startPrefixMapping(namespace.prefix, namespace.uri)
      }
  }

  private def endPrefixMapping(stack: NamespaceStack, stackSize: Int): Unit = {
    while (stack.size > stackSize) {
      val namespace = stack.pop()
      if (namespace ne null)
        contentHandler.endPrefixMapping(namespace.prefix)
    }
  }

  protected def startElement(element: Element): Unit =
    contentHandler.startElement(element.getNamespaceURI, element.getName, element.getQualifiedName, createAttributes(element))

  private def endElement(element: Element): Unit =
    contentHandler.endElement(element.getNamespaceURI, element.getName, element.getQualifiedName)

  private def createAttributes(element: Element): Attributes = {
    attributes.clear()
    val iter = element.attributeIterator
    while (iter.hasNext) {
      val attribute = iter.next()
      attributes.addAttribute(attribute.getNamespaceURI, attribute.getName, attribute.getQualifiedName, "CDATA", attribute.getValue)
    }
    attributes
  }

  /**
   * @return true if the given namespace is an ignorable namespace (such as
   *         Namespace.NO_NAMESPACE or Namespace.XML_NAMESPACE) or if the
   *         namespace has already been declared in the current scope
   */
  private def isIgnorableNamespace(namespace: Namespace, namespaceStack: NamespaceStack): Boolean =
    if (namespace == Namespace.EmptyNamespace || namespace == Namespace.XMLNamespace) {
      true
    } else {
      val uri = namespace.uri
      if ((uri eq null) || (uri.length <= 0))
        true
      else
        namespaceStack.contains(namespace)
    }
}
