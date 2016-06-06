package org.dom4j.io

import java.io._
import java.{lang ⇒ jl, util ⇒ ju}

import org.dom4j._
import org.dom4j.io.XMLWriter._
import org.dom4j.tree.NamespaceStack
import org.xml.sax._
import org.xml.sax.ext.LexicalHandler
import org.xml.sax.helpers.XMLFilterImpl

private object XMLWriter {

  private val LexicalHandlerNames = Array(
    "http://xml.org/sax/properties/lexical-handler"
  )

  val DefaultFormat = OutputFormat(indent = false, newlines = false, trimText = false)
}

/**
 * `XMLWriter` takes a DOM4J tree and formats it to a stream as
 * XML. It can also take SAX events too so can be used by SAX clients as this
 * object implements the and  interfaces. as well. This formatter performs typical document
 * formatting. The XML declaration and processing instructions are always on
 * their own lines. An object can be used to define how
 * whitespace is handled when printing and allows various configuration options,
 * such as to allow suppression of the XML declaration, the encoding declaration
 * or whether empty documents are collapsed.
 */
class XMLWriter(protected var writer: Writer, val format: OutputFormat) extends XMLFilterImpl with LexicalHandler {

  // Should entityRefs by resolved when writing ?
  private val resolveEntityRefs = true

  // last type of node written so algorithms can refer to the previous node type
  private var lastOutputNodeType: Int = _

  // last written element node was a closing tag or an opening tag
  private var lastElementClosed = false

  // xml:space attribute value of preserve for whitespace flag
  protected var preserve = false

  private val namespaceStack = new NamespaceStack

  private val escapeText = true

  private var indentLevel = 0

  // Buffer used when escaping strings
  private val buffer = new jl.StringBuilder

  // Whether we have added characters before from the same chunk of characters
  private var charsAdded = false

  private var lastChar: Char = _

  // Whether a flush should occur after writing a document
  private var autoFlush = false

  var lexicalHandler: LexicalHandler = _
  def getLexicalHandler = lexicalHandler

  private var inDTD = false

  private var namespacesMap: ju.Map[String, String] = _

  namespaceStack.push(Namespace.EmptyNamespace)

  def this(writer: Writer) =
    this(writer, DefaultFormat)

  def setWriter(writer: Writer): Unit = {
    this.writer = writer
    this.autoFlush = false
  }

  def setOutputStream(out: OutputStream): Unit = {
    this.writer = createWriter(out, OutputFormat.StandardEncoding)
    this.autoFlush = true
  }

  def flush(): Unit =
    writer.flush()

  def close(): Unit = {
    writer.close()
  }

  def println(): Unit =
    writer.write(OutputFormat.LineSeparator)

  def write(attribute: Attribute): Unit = {
    writeAttribute(attribute)
    if (autoFlush) {
      flush()
    }
  }

  /**
   * This will print the `Document` to the current Writer.
   *
   * Warning: using your own Writer may cause the writer's preferred character
   * encoding to be ignored. If you use encodings other than UTF8, we
   * recommend using the method that takes an OutputStream instead.
   *
   * Note: as with all Writers, you may need to flush() yours after this
   * method returns.
   */
  def write(doc: Document): Unit = {
    writeDeclaration()
    for (i ← 0 until doc.nodeCount) {
      val node = doc.node(i)
      writeNode(node)
    }
    writePrintln()
    if (autoFlush) {
      flush()
    }
  }

  def write(element: Element): Unit = {
    writeElement(element)
    if (autoFlush) {
      flush()
    }
  }

  def write(cdata: CDATA): Unit = {
    writeCDATA(cdata.getText)
    if (autoFlush) {
      flush()
    }
  }

  def write(comment: Comment): Unit = {
    writeComment(comment.getText)
    if (autoFlush) {
      flush()
    }
  }

  def write(entity: Entity): Unit = {
    writeEntity(entity)
    if (autoFlush) {
      flush()
    }
  }

  def write(namespace: Namespace): Unit = {
    writeNamespace(namespace)
    if (autoFlush) {
      flush()
    }
  }

  def write(processingInstruction: ProcessingInstruction): Unit = {
    writeProcessingInstruction(processingInstruction)
    if (autoFlush) {
      flush()
    }
  }

  /**
   * Perform the necessary entity escaping and whitespace stripping.
   */
  def write(text: String): Unit = {
    writeString(text)
    if (autoFlush) {
      flush()
    }
  }

  def write(text: Text): Unit = {
    writeString(text.getText)
    if (autoFlush) {
      flush()
    }
  }

  def write(node: Node): Unit = {
    writeNode(node)
    if (autoFlush) {
      flush()
    }
  }

  override def parse(source: InputSource): Unit = {
    installLexicalHandler()
    super.parse(source)
  }

  override def setProperty(name: String, value: AnyRef): Unit =
    if (LexicalHandlerNames contains name)
      setLexicalHandler(value.asInstanceOf[LexicalHandler])
    else
      super.setProperty(name, value)

  override def getProperty(name: String): AnyRef =
    if (LexicalHandlerNames contains name)
      getLexicalHandler
    else
      super.getProperty(name)

  private def setLexicalHandler(handler: LexicalHandler): Unit = {
    if (handler eq null) {
      throw new NullPointerException("Null lexical handler")
    } else {
      this.lexicalHandler = handler
    }
  }

  override def setDocumentLocator(locator: Locator): Unit = {
    super.setDocumentLocator(locator)
  }

  override def startDocument(): Unit = {
    try {
      writeDeclaration()
      super.startDocument()
    } catch {
      case e: IOException ⇒ handleException(e)
    }
  }

  override def endDocument(): Unit = {
    super.endDocument()
    if (autoFlush) {
      try {
        flush()
      } catch {
        case e: IOException ⇒
      }
    }
  }

  override def startPrefixMapping(prefix: String, uri: String): Unit = {
    if (namespacesMap eq null) {
      namespacesMap = new ju.HashMap[String, String]()
    }
    namespacesMap.put(prefix, uri)
    super.startPrefixMapping(prefix, uri)
  }

  override def endPrefixMapping(prefix: String): Unit =
    super.endPrefixMapping(prefix)

  override def startElement(namespaceURI: String, localName: String, qName: String, attributes: Attributes): Unit =
    try {
      charsAdded = false
      writePrintln()
      indent()
      writer.write("<")
      writer.write(qName)
      writeNamespaces()
      writeAttributes(attributes)
      writer.write(">")
      indentLevel += 1
      lastOutputNodeType = Node.ELEMENT_NODE
      lastElementClosed = false
      super.startElement(namespaceURI, localName, qName, attributes)
    } catch {
      case e: IOException ⇒ handleException(e)
    }

  override def endElement(namespaceURI: String, localName: String, qName: String): Unit = {
    try {
      charsAdded = false
      indentLevel -= 1
      if (lastElementClosed) {
        writePrintln()
        indent()
      }
      val hadContent = true
      if (hadContent) {
        writeClose(qName)
      } else {
        writeEmptyElementClose(qName)
      }
      lastOutputNodeType = Node.ELEMENT_NODE
      lastElementClosed = true
      super.endElement(namespaceURI, localName, qName)
    } catch {
      case e: IOException ⇒ handleException(e)
    }
  }

  override def characters(ch: Array[Char], start: Int, length: Int): Unit = {
    if ((ch eq null) || (ch.length == 0) || (length <= 0)) {
      return
    }
    try {
      var string = String.valueOf(ch, start, length)
      if (escapeText) {
        string = escapeElementEntities(string)
      }
      if (format.trimText) {
        if ((lastOutputNodeType == Node.TEXT_NODE) && !charsAdded) {
          writer.write(' ')
        } else if (charsAdded && Character.isWhitespace(lastChar)) {
          writer.write(' ')
        }
        var delim = ""
        val tokens = new ju.StringTokenizer(string)
        while (tokens.hasMoreTokens) {
          writer.write(delim)
          writer.write(tokens.nextToken())
          delim = " "
        }
      } else {
        writer.write(string)
      }
      charsAdded = true
      lastChar = ch((start + length) - 1)
      lastOutputNodeType = Node.TEXT_NODE
      super.characters(ch, start, length)
    } catch {
      case e: IOException ⇒ handleException(e)
    }
  }

  override def ignorableWhitespace(ch: Array[Char], start: Int, length: Int): Unit =
    super.ignorableWhitespace(ch, start, length)

  override def processingInstruction(target: String, data: String): Unit =
    try {
      indent()
      writer.write("<?")
      writer.write(target)
      writer.write(" ")
      writer.write(data)
      writer.write("?>")
      writePrintln()
      lastOutputNodeType = Node.PROCESSING_INSTRUCTION_NODE
      super.processingInstruction(target, data)
    } catch {
      case e: IOException ⇒ handleException(e)
    }

  override def notationDecl(name: String, publicID: String, systemID: String): Unit =
    super.notationDecl(name, publicID, systemID)

  override def unparsedEntityDecl(name: String, publicID: String, systemID: String, notationName: String): Unit =
    super.unparsedEntityDecl(name, publicID, systemID, notationName)

  def startDTD(name: String, publicID: String, systemID: String): Unit = {
    inDTD = true
    try {
      writeDocType(name, publicID, systemID)
    } catch {
      case e: IOException ⇒ handleException(e)
    }
    if (lexicalHandler ne null)
      lexicalHandler.startDTD(name, publicID, systemID)
  }

  def endDTD(): Unit = {
    inDTD = false
    if (lexicalHandler ne null)
      lexicalHandler.endDTD()
  }

  def startCDATA(): Unit = {
    try {
      writer.write("<![CDATA[")
    } catch {
      case e: IOException ⇒ handleException(e)
    }
    if (lexicalHandler ne null)
      lexicalHandler.startCDATA()
  }

  def endCDATA(): Unit = {
    try {
      writer.write("]]>")
    } catch {
      case e: IOException ⇒ handleException(e)
    }
    if (lexicalHandler ne null) {
      lexicalHandler.endCDATA()
    }
  }

  def startEntity(name: String): Unit = {
    try {
      writeEntityRef(name)
    } catch {
      case e: IOException ⇒ handleException(e)
    }
    if (lexicalHandler ne null)
      lexicalHandler.startEntity(name)
  }

  def endEntity(name: String): Unit =
    if (lexicalHandler ne null)
      lexicalHandler.endEntity(name)

  def comment(ch: Array[Char], start: Int, length: Int): Unit = {
    if (!inDTD) {
      try {
        charsAdded = false
        writeComment(new String(ch, start, length))
      } catch {
        case e: IOException ⇒ handleException(e)
      }
    }
    if (lexicalHandler ne null) {
      lexicalHandler.comment(ch, start, length)
    }
  }

  private def writeElement(element: Element): Unit = {
    val size = element.nodeCount
    val qualifiedName = element.getQualifiedName
    writePrintln()
    indent()
    writer.write("<")
    writer.write(qualifiedName)
    val previouslyDeclaredNamespaces = namespaceStack.size
    val ns = element.getNamespace
    if (isNamespaceDeclaration(ns)) {
      namespaceStack.push(ns)
      writeNamespace(ns)
    }
    var textOnly = true
    for (i ← 0 until size) {
      val node = element.node(i)
      node match {
        case additional: Namespace ⇒
          if (isNamespaceDeclaration(additional)) {
            namespaceStack.push(additional)
            writeNamespace(additional)
          }
        case _: Element ⇒
          textOnly = false
        case _: Comment ⇒
          textOnly = false
        case _ ⇒
      }
    }
    writeAttributes(element)
    lastOutputNodeType = Node.ELEMENT_NODE
    if (size <= 0) {
      writeEmptyElementClose(qualifiedName)
    } else {
      writer.write(">")
      if (textOnly) {
        writeElementContent(element)
      } else {
        indentLevel += 1
        writeElementContent(element)
        indentLevel -= 1
        writePrintln()
        indent()
      }
      writer.write("</")
      writer.write(qualifiedName)
      writer.write(">")
    }
    while (namespaceStack.size > previouslyDeclaredNamespaces) {
      namespaceStack.pop()
    }
    lastOutputNodeType = Node.ELEMENT_NODE
  }

  /**
   * Determines if element is a special case of XML elements where it contains
   * an xml:space attribute of "preserve". If it does, then retain whitespace.
   */
  private def isElementSpacePreserved(element: Element): Boolean = {
    val attr = element.attribute("space")
    var preserveFound = preserve
    if (attr ne null) {
      preserveFound = if ("xml" == attr.getNamespacePrefix && "preserve" == attr.getText) true else false
    }
    preserveFound
  }

  /**
   * Outputs the content of the given element. If whitespace trimming is
   * enabled then all adjacent text nodes are appended together before the
   * whitespace trimming occurs to avoid problems with multiple text nodes
   * being created due to text content that spans parser buffers in a SAX
   * parser.
   */
  private def writeElementContent(element: Element): Unit = {
    var trim = format.trimText
    val oldPreserve = preserve
    if (trim) {
      preserve = isElementSpacePreserved(element)
      trim = !preserve
    }
    if (trim) {
      var lastTextNode: Text = null
      var buff: jl.StringBuilder = null
      var textOnly = true
      for (i ← 0 until element.nodeCount) {
        val node = element.node(i)
        node match {
          case textNode: Text ⇒
            if (lastTextNode eq null) {
              lastTextNode = textNode
            } else {
              if (buff eq null) {
                buff = new jl.StringBuilder(lastTextNode.getText)
              }
              buff.append(textNode.getText)
            }
          case _ ⇒
            if (lastTextNode ne null) {
              if (buff ne null) {
                writeString(buff.toString)
                buff = null
              } else {
                writeString(lastTextNode.getText)
              }
              lastTextNode = null
            }
            textOnly = false
            writeNode(node)
        }
      }
      if (lastTextNode ne null) {
        if (buff ne null) {
          writeString(buff.toString)
          buff = null
        } else {
          writeString(lastTextNode.getText)
        }
        lastTextNode = null
      }
    } else {
      var lastTextNode: Node = null
      for (i ← 0 until element.nodeCount) {
        val node = element.node(i)
        if (node.isInstanceOf[Text]) {
          writeNode(node)
          lastTextNode = node
        } else {
          writeNode(node)
          lastTextNode = null
        }
      }
    }
    preserve = oldPreserve
  }

  private def writeCDATA(text: String): Unit = {
    writer.write("<![CDATA[")
    if (text ne null) {
      writer.write(text)
    }
    writer.write("]]>")
    lastOutputNodeType = Node.CDATA_SECTION_NODE
  }

  private def writeNamespace(namespace: Namespace): Unit = {
    if (namespace ne null) {
      writeNamespace(namespace.prefix, namespace.uri)
    }
  }

  private def writeNamespaces(): Unit = {
    if (namespacesMap ne null) {
      val iter = namespacesMap.entrySet().iterator()
      while (iter.hasNext) {
        val entry = iter.next()
        val prefix = entry.getKey
        val uri = entry.getValue
        writeNamespace(prefix, uri)
      }
      namespacesMap = null
    }
  }

  private def writeNamespace(prefix: String, uri: String): Unit = {
    if ((prefix ne null) && (prefix.length > 0)) {
      writer.write(" xmlns:")
      writer.write(prefix)
      writer.write("=\"")
    } else {
      writer.write(" xmlns=\"")
    }
    writer.write(uri)
    writer.write("\"")
  }

  private def writeProcessingInstruction(pi: ProcessingInstruction): Unit = {
    writer.write("<?")
    writer.write(pi.getName)
    writer.write(" ")
    writer.write(pi.getText)
    writer.write("?>")
    writePrintln()
    lastOutputNodeType = Node.PROCESSING_INSTRUCTION_NODE
  }

  private def writeString(_text: String): Unit = {
    var text = _text
    if ((text ne null) && (text.length > 0)) {
      if (escapeText) {
        text = escapeElementEntities(text)
      }
      if (format.trimText) {
        var first = true
        val tokenizer = new ju.StringTokenizer(text)
        while (tokenizer.hasMoreTokens) {
          val token = tokenizer.nextToken()
          if (first) {
            first = false
            if (lastOutputNodeType == Node.TEXT_NODE) {
              writer.write(" ")
            }
          } else {
            writer.write(" ")
          }
          writer.write(token)
          lastOutputNodeType = Node.TEXT_NODE
          lastChar = token.charAt(token.length - 1)
        }
      } else {
        lastOutputNodeType = Node.TEXT_NODE
        writer.write(text)
        lastChar = text.charAt(text.length - 1)
      }
    }
  }

  /**
   * This method is used to write out Nodes that contain text and still allow
   * for xml:space to be handled properly.
   */
  private def writeNodeText(node: Node): Unit = {
    var text = node.getText
    if ((text ne null) && (text.length > 0)) {
      if (escapeText) {
        text = escapeElementEntities(text)
      }
      lastOutputNodeType = Node.TEXT_NODE
      writer.write(text)
      lastChar = text.charAt(text.length - 1)
    }
  }

  // ORBEON: match on trait
  private def writeNode(node: Node): Unit = {
    val nodeType = node.getNodeType
    nodeType match {
      case Node.ELEMENT_NODE                ⇒ writeElement(node.asInstanceOf[Element])
      case Node.ATTRIBUTE_NODE              ⇒ writeAttribute(node.asInstanceOf[Attribute])
      case Node.TEXT_NODE                   ⇒ writeNodeText(node)
      case Node.CDATA_SECTION_NODE          ⇒ writeCDATA(node.getText)
      case Node.ENTITY_REFERENCE_NODE       ⇒ writeEntity(node.asInstanceOf[Entity])
      case Node.PROCESSING_INSTRUCTION_NODE ⇒ writeProcessingInstruction(node.asInstanceOf[ProcessingInstruction])
      case Node.COMMENT_NODE                ⇒ writeComment(node.getText)
      case Node.DOCUMENT_NODE               ⇒ write(node.asInstanceOf[Document])
      case Node.NAMESPACE_NODE              ⇒
      case _ ⇒ throw new IOException("Invalid node type: " + node)
    }
  }

  private def installLexicalHandler(): Unit = {
    val parent = getParent
    if (parent eq null)
      throw new NullPointerException("No parent for filter")

    for (i ← LexicalHandlerNames.indices) {
      try {
        parent.setProperty(LexicalHandlerNames(i), this)
        return
      } catch {
        case ex: SAXNotRecognizedException ⇒
        case ex: SAXNotSupportedException ⇒
      }
    }
    // NOTE: No lexical handler installed.
  }

  private def writeDocType(name: String, publicID: String, systemID: String): Unit = {
    var hasPublic = false
    writer.write("<!DOCTYPE ")
    writer.write(name)
    if ((publicID ne null) && (publicID != "")) {
      writer.write(" PUBLIC \"")
      writer.write(publicID)
      writer.write("\"")
      hasPublic = true
    }
    if ((systemID ne null) && (systemID != "")) {
      if (!hasPublic) {
        writer.write(" SYSTEM")
      }
      writer.write(" \"")
      writer.write(systemID)
      writer.write("\"")
    }
    writer.write(">")
    writePrintln()
  }

  private def writeEntity(entity: Entity): Unit =
    if (!resolveEntityRefs) {
      writeEntityRef(entity.getName)
    } else {
      writer.write(entity.getText)
    }

  private def writeEntityRef(name: String): Unit = {
    writer.write("&")
    writer.write(name)
    writer.write(";")
    lastOutputNodeType = Node.ENTITY_REFERENCE_NODE
  }

  private def writeComment(text: String): Unit = {
    if (format.newlines) {
      println()
      indent()
    }
    writer.write("<!--")
    writer.write(text)
    writer.write("-->")
    lastOutputNodeType = Node.COMMENT_NODE
  }

  private def writeAttributes(element: Element): Unit = {
    for (i ← 0 until element.attributeCount) {
      val attribute = element.attribute(i)
      val ns = attribute.getNamespace
      if ((ns ne null) && (ns != Namespace.EmptyNamespace) && (ns != Namespace.XMLNamespace)) {
        val prefix = ns.prefix
        val uri = namespaceStack.getURI(prefix)
        if (ns.uri != uri) {
          writeNamespace(ns)
          namespaceStack.push(ns)
        }
      }
      val attName = attribute.getName
      if (attName.startsWith("xmlns:")) {
        val prefix = attName.substring(6)
        if (namespaceStack.getNamespaceForPrefix(prefix) eq null) {
          val uri = attribute.getValue
          namespaceStack.push(prefix, uri)
          writeNamespace(prefix, uri)
        }
      } else if (attName == "xmlns") {
        if (namespaceStack.getDefaultNamespace eq null) {
          val uri = attribute.getValue
          namespaceStack.push(null, uri)
          writeNamespace(null, uri)
        }
      } else {
        val quote = OutputFormat.AttributeQuoteCharacter
        writer.write(" ")
        writer.write(attribute.getQualifiedName)
        writer.write("=")
        writer.write(quote)
        writeEscapeAttributeEntities(attribute.getValue)
        writer.write(quote)
      }
    }
  }

  private def writeAttribute(attribute: Attribute): Unit = {
    writer.write(" ")
    writer.write(attribute.getQualifiedName)
    writer.write("=")
    val quote = OutputFormat.AttributeQuoteCharacter
    writer.write(quote)
    writeEscapeAttributeEntities(attribute.getValue)
    writer.write(quote)
    lastOutputNodeType = Node.ATTRIBUTE_NODE
  }

  private def writeAttributes(attributes: Attributes): Unit =
    for (i ← 0 until attributes.getLength)
      writeAttribute(attributes, i)

  private def writeAttribute(attributes: Attributes, index: Int): Unit = {
    val quote = OutputFormat.AttributeQuoteCharacter
    writer.write(" ")
    writer.write(attributes.getQName(index))
    writer.write("=")
    writer.write(quote)
    writeEscapeAttributeEntities(attributes.getValue(index))
    writer.write(quote)
  }

  private def indent(): Unit = {
    val indent = format.getIndent
    if (indent.nonEmpty) {
      for (i ← 0 until indentLevel) {
        writer.write(indent)
      }
    }
  }

  /**
   * This will print a new line only if the newlines flag was set to true
   */
  private def writePrintln(): Unit =
    if (format.newlines) {
      val separator = OutputFormat.LineSeparator
      if (lastChar != separator.charAt(separator.length - 1)) {
        writer.write(OutputFormat.LineSeparator)
      }
    }

  /**
   * Get an OutputStreamWriter, use preferred encoding.
   */
  private def createWriter(outStream: OutputStream, encoding: String): Writer =
    new BufferedWriter(new OutputStreamWriter(outStream, encoding))

  private def writeDeclaration(): Unit = {
    writer.write("<?xml version=\"1.0\"")
    writer.write(" encoding=\"" + OutputFormat.StandardEncoding + "\"")
    writer.write("?>")
    println()
  }

  private def writeClose(qualifiedName: String): Unit = {
    writer.write("</")
    writer.write(qualifiedName)
    writer.write(">")
  }

  private def writeEmptyElementClose(qualifiedName: String): Unit =
    writer.write("/>")

  /**
   * This will take the pre-defined entities in XML 1.0 and convert their
   * character representation to the appropriate entity reference, suitable
   * for XML attributes.
   */
  private def escapeElementEntities(text: String): String = {
    var block: Array[Char] = null
    var i = 0
    var last = 0
    val size = text.length
    i = 0
    while (i < size) {
      var entity: String = null
      val c = text.charAt(i)
      c match {
        case '<' ⇒ entity = "&lt;"
        case '>' ⇒ entity = "&gt;"
        case '&' ⇒ entity = "&amp;"
        case '\t' | '\n' | '\r' ⇒ if (preserve) {
          entity = String.valueOf(c)
        }
        case _ ⇒
          if (c < 32) {
            entity = "&#" + c.toInt + ";"
        }
      }
      if (entity ne null) {
        if (block eq null) {
          block = text.toCharArray
        }
        buffer.append(block, last, i - last)
        buffer.append(entity)
        last = i + 1
      }
      i += 1
    }
    if (last == 0) {
      return text
    }
    if (last < size) {
      if (block eq null) {
        block = text.toCharArray
      }
      buffer.append(block, last, i - last)
    }
    val answer = buffer.toString
    buffer.setLength(0)
    answer
  }

  private def writeEscapeAttributeEntities(txt: String): Unit =
    if (txt ne null)
      writer.write(escapeAttributeEntities(txt))

  /**
   * This will take the pre-defined entities in XML 1.0 and convert their
   * character representation to the appropriate entity reference, suitable
   * for XML attributes.
   */
  private def escapeAttributeEntities(text: String): String = {
    val quote = OutputFormat.AttributeQuoteCharacter
    var block: Array[Char] = null
    var i = 0
    var last = 0
    val size = text.length
    i = 0
    while (i < size) {
      var entity: String = null
      val c = text.charAt(i)
      c match {
        case '<' ⇒ entity = "&lt;"
        case '>' ⇒ entity = "&gt;"
        case '\'' ⇒ if (quote == '\'') {
          entity = "&apos;"
        }
        case '\"' ⇒ if (quote == '\"') {
          entity = "&quot;"
        }
        case '&' ⇒ entity = "&amp;"
        case '\t' | '\n' | '\r' ⇒ // don't encode standard whitespace characters
        case _ ⇒ if (c < 32) {
          entity = "&#" + c.toInt + ";"
        }
      }
      if (entity ne null) {
        if (block eq null) {
          block = text.toCharArray
        }
        buffer.append(block, last, i - last)
        buffer.append(entity)
        last = i + 1
      }
      i += 1
    }
    if (last == 0) {
      return text
    }
    if (last < size) {
      if (block eq null) {
        block = text.toCharArray
      }
      buffer.append(block, last, i - last)
    }
    val answer = buffer.toString
    buffer.setLength(0)
    answer
  }

  private def isNamespaceDeclaration(ns: Namespace): Boolean = {
    if ((ns ne null) && (ns != Namespace.XMLNamespace)) {
      val uri = ns.uri
      if (uri ne null) {
        if (!namespaceStack.contains(ns)) {
          return true
        }
      }
    }
    false
  }

  private def handleException(e: IOException): Unit = throw new SAXException(e)
}
