package org.dom4j.io

import java.{util ⇒ ju}

import org.dom4j._
import org.dom4j.io.SAXWriter._
import org.dom4j.tree.NamespaceStack
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
 * `SAXWriter` writes a DOM4J tree to a SAX ContentHandler.
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

  // ORBEON TODO: match on traits
  def write(node: Node): Unit =
    node.getNodeType match {
      case Node.ELEMENT_NODE                ⇒ write(node.asInstanceOf[Element])
      case Node.ATTRIBUTE_NODE              ⇒ write(node.asInstanceOf[Attribute])
      case Node.TEXT_NODE                   ⇒ write(node.getText)
      case Node.CDATA_SECTION_NODE          ⇒ write(node.asInstanceOf[CDATA])
      case Node.ENTITY_REFERENCE_NODE       ⇒ write(node.asInstanceOf[Entity])
      case Node.PROCESSING_INSTRUCTION_NODE ⇒ write(node.asInstanceOf[ProcessingInstruction])
      case Node.COMMENT_NODE                ⇒ write(node.asInstanceOf[Comment])
      case Node.DOCUMENT_NODE               ⇒ write(node.asInstanceOf[Document])
      case Node.NAMESPACE_NODE              ⇒ // NOP
      case _                                ⇒ throw new SAXException("Invalid node type: " + node)
    }

  private def write(document: Document): Unit =
    if (document ne null) {
      createDocumentLocator(document) foreach contentHandler.setDocumentLocator
      startDocument()
      writeContent(document, new NamespaceStack)
      endDocument()
    }

  private def write(element: Element): Unit =
    write(element, new NamespaceStack)

  private def write(text: String): Unit =
    if (text ne null) {
      val chars = text.toCharArray
      contentHandler.characters(chars, 0, chars.length)
    }

  private def write(cdata: CDATA): Unit = {
    val text = cdata.getText
    if (lexicalHandler ne null) {
      lexicalHandler.startCDATA()
      write(text)
      lexicalHandler.endCDATA()
    } else {
      write(text)
    }
  }

  private def write(comment: Comment): Unit = {
    if (lexicalHandler ne null) {
      val text = comment.getText
      val chars = text.toCharArray
      lexicalHandler.comment(chars, 0, chars.length)
    }
  }

  private def write(entity: Entity): Unit = {
    val text = entity.getText
    if (lexicalHandler ne null) {
      val name = entity.getName
      lexicalHandler.startEntity(name)
      write(text)
      lexicalHandler.endEntity(name)
    } else {
      write(text)
    }
  }

  private def write(pi: ProcessingInstruction): Unit =
    contentHandler.processingInstruction(pi.getTarget, pi.getText)

  def getDTDHandler: DTDHandler = dtdHandler
  def setDTDHandler(handler: DTDHandler): Unit =
    this.dtdHandler = handler

//  def setXMLReader(xmlReader: XMLReader): Unit = {
//    setContentHandler(xmlReader.getContentHandler)
//    setDTDHandler(xmlReader.getDTDHandler)
//    setEntityResolver(xmlReader.getEntityResolver)
//    setErrorHandler(xmlReader.getErrorHandler)
//  }

  def getFeature(name: String): Boolean = {
    val answer = features.get(name)
    (answer ne null) && answer.booleanValue
  }

  def setFeature(name: String, value: Boolean): Unit =
    name match {
      case FeatureNamespacePrefixes if value ⇒
        throw new SAXNotSupportedException("Namespace prefixes feature is never supported in dom4j")
      case FeatureNamespaces if ! value ⇒
        throw new SAXNotSupportedException("Namespace feature is always supported in dom4j")
      case FeatureNamespaces ⇒
      case _ ⇒
        features.put(name, value)
    }

  def setProperty(name: String, value: AnyRef): Unit =
    LexicalHandlerNames find (_ == name) match {
      case Some(handlerName) ⇒ setLexicalHandler(value.asInstanceOf[LexicalHandler])
      case None              ⇒ properties.put(name, value)
    }

  def getProperty(name: String): AnyRef =
    LexicalHandlerNames find (_ == name) match {
      case Some(handlerName) ⇒ getLexicalHandler
      case None              ⇒ properties.get(name)
    }

  def parse(systemId: String): Unit =
    throw new SAXNotSupportedException("This XMLReader can only accept <dom4j> InputSource objects")

  def parse(input: InputSource): Unit =
    input match {
      case documentInput: DocumentInputSource ⇒
        val document = documentInput.getDocument
        write(document)
      case _ ⇒
        throw new SAXNotSupportedException("This XMLReader can only accept " + "<dom4j> InputSource objects")
    }

  private def writeContent(branch: Branch, namespaceStack: NamespaceStack): Unit = {
    val iter = branch.nodeIterator
    while (iter.hasNext) {
      val obj = iter.next()
      obj match {
        case element: Element ⇒
          write(element, namespaceStack)
        case _: CharacterData ⇒
          obj match {
            case text: Text ⇒
              write(text.getText)
            case cdata: CDATA ⇒
              write(cdata)
            case comment: Comment ⇒
              write(comment)
            case _ ⇒
              throw new SAXException("Invalid Node in DOM4J content: " + obj + " of type: " + obj.getClass)
          }
        case entity: Entity ⇒
          write(entity)
        case pi: ProcessingInstruction ⇒
          write(pi)
        case node: Namespace ⇒
          write(node)
        case _ ⇒
          throw new SAXException("Invalid Node in DOM4J content: " + obj)
      }
    }
  }

  protected def createDocumentLocator(document: Document): Option[Locator] = {
    val locator = new LocatorImpl
    locator.setLineNumber(-1)
    locator.setColumnNumber(-1)
    Some(locator)
  }

  private def startDocument(): Unit = contentHandler.startDocument()
  private def endDocument(): Unit   = contentHandler.endDocument()

  private def write(element: Element, namespaceStack: NamespaceStack): Unit = {
    val stackSize = namespaceStack.size
    startPrefixMapping(element, namespaceStack)
    startElement(element)
    writeContent(element, namespaceStack)
    endElement(element)
    endPrefixMapping(namespaceStack, stackSize)
  }

  private def startPrefixMapping(element: Element, namespaceStack: NamespaceStack): Unit = {
    val elementNamespace = element.getNamespace
    if ((elementNamespace ne null) && !isIgnoreableNamespace(elementNamespace, namespaceStack)) {
      namespaceStack.push(elementNamespace)
      contentHandler.startPrefixMapping(elementNamespace.prefix, elementNamespace.uri)
    }
    val declaredNamespaces = element.declaredNamespaces
    for (i ← 0 until declaredNamespaces.size) {
      val namespace = declaredNamespaces.get(i)
      if (!isIgnoreableNamespace(namespace, namespaceStack)) {
        namespaceStack.push(namespace)
        contentHandler.startPrefixMapping(namespace.prefix, namespace.uri)
      }
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
  private def isIgnoreableNamespace(namespace: Namespace, namespaceStack: NamespaceStack): Boolean =
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
