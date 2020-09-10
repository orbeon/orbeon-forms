package org.orbeon.dom.io

import java.io._
import java.{lang => jl, util => ju}

import org.orbeon.dom._
import org.orbeon.dom.tree.NamespaceStack

object XMLWriter {
  val DefaultFormat = OutputFormat(indent = false, newlines = false, trimText = false)
  val CompactFormat = OutputFormat(indent = false, newlines = false, trimText = true)
  val PrettyFormat  = OutputFormat(indent = true, newlines = true, trimText = true)
}

/**
  * `XMLWriter` takes a tree and formats it to a stream of characters.
  */
class XMLWriter(writer: Writer, format: OutputFormat) {

  private val namespaceStack = new NamespaceStack
  namespaceStack.push(Namespace.EmptyNamespace)

  // State
  private var isLastOutputNodeTypeText = false
  private var preserve                 = false
  private val escapeText               = true
  private var indentLevel              = 0
  private val buffer                   = new jl.StringBuilder
  private var lastChar: Char           = 0

  private def writeNewLine(): Unit =
    writer.write(OutputFormat.LineSeparator)

  /**
    * This will print the `Document` to the current Writer.
    *
    * Note: as with all Writers, you may need to flush() yours after this
    * method returns.
    */
  def write(doc: Document): Unit = {
    writeDeclaration()
    for (i <- 0 until doc.nodeCount)
      writeNode(doc.node(i))
    writeNewLineIfNeeded()
  }

  def write(node: Node): Unit =
    writeNode(node)

  private def writeElement(element: Element): Unit = {
    val size = element.nodeCount
    val qualifiedName = element.getQualifiedName
    writeNewLineIfNeeded()
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
    for (i <- 0 until size) {
      val node = element.node(i)
      node match {
        case additional: Namespace =>
          if (isNamespaceDeclaration(additional)) {
            namespaceStack.push(additional)
            writeNamespace(additional)
          }
        case _: Element =>
          textOnly = false
        case _: Comment =>
          textOnly = false
        case _ =>
      }
    }
    writeAttributes(element)
    isLastOutputNodeTypeText = false
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
        writeNewLineIfNeeded()
        indent()
      }
      writer.write("</")
      writer.write(qualifiedName)
      writer.write(">")
    }
    while (namespaceStack.size > previouslyDeclaredNamespaces) {
      namespaceStack.pop()
    }
    isLastOutputNodeTypeText = false
  }

  /**
    * Determines if element is a special case of XML elements where it contains
    * an xml:space attribute of "preserve". If it does, then retain whitespace.
    */
  private def isElementSpacePreserved(element: Element): Boolean = {
    val attr = element.attribute("space")
    var preserveFound = preserve
    if (attr ne null) {
      preserveFound = if ("xml" == attr.getNamespacePrefix && "preserve" == attr.getValue) true else false
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
      for (i <- 0 until element.nodeCount) {
        val node = element.node(i)
        node match {
          case textNode: Text =>
            if (lastTextNode eq null) {
              lastTextNode = textNode
            } else {
              if (buff eq null) {
                buff = new jl.StringBuilder(lastTextNode.getText)
              }
              buff.append(textNode.getText)
            }
          case _ =>
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
      for (i <- 0 until element.nodeCount) {
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

  private def writeNamespace(namespace: Namespace): Unit =
    if (namespace ne null)
      writeNamespace(namespace.prefix, namespace.uri)

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
    writeNewLineIfNeeded()
    isLastOutputNodeTypeText = false
  }

  private def writeString(_text: String): Unit = {
    var text = _text
    if ((text ne null) && (text.length > 0)) {

      if (escapeText)
        text = escapeElementEntities(text)

      if (format.trimText) {
        var first = true
        val tokenizer = new ju.StringTokenizer(text)
        while (tokenizer.hasMoreTokens) {
          val token = tokenizer.nextToken()
          if (first) {
            first = false

            if (isLastOutputNodeTypeText)
              writer.write(" ")

          } else {
            writer.write(" ")
          }
          writer.write(token)
          isLastOutputNodeTypeText = true
          lastChar = token.charAt(token.length - 1)
        }
      } else {
        isLastOutputNodeTypeText = true
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

      if (escapeText)
        text = escapeElementEntities(text)

      isLastOutputNodeTypeText = true
      writer.write(text)
      lastChar = text.charAt(text.length - 1)
    }
  }

  private def writeNode(node: Node): Unit =
    node match {
      case n: Element               => writeElement(n)
      case n: Attribute             => writeAttribute(n)
      case n: Text                  => writeNodeText(n)
      case n: ProcessingInstruction => writeProcessingInstruction(n)
      case n: Comment               => writeComment(n.getText)
      case n: Document              => write(n)
      case _: Namespace             =>
      case _                        => throw new IllegalStateException
    }

  private def writeComment(text: String): Unit = {
    if (format.newlines) {
      writeNewLine()
      indent()
    }
    writer.write("<!--")
    writer.write(text)
    writer.write("-->")
    isLastOutputNodeTypeText = false
  }

  private def writeAttributes(element: Element): Unit = {
    for (i <- 0 until element.attributeCount) {
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
    isLastOutputNodeTypeText = false
  }

  private def indent(): Unit = {
    val indent = format.getIndent
    if (indent.nonEmpty) {
      for (i <- 0 until indentLevel) {
        writer.write(indent)
      }
    }
  }

  private def writeNewLineIfNeeded(): Unit =
    if (format.newlines) {
      val separator = OutputFormat.LineSeparator
      if (lastChar != separator.charAt(separator.length - 1)) {
        writer.write(OutputFormat.LineSeparator)
      }
    }

  private def writeDeclaration(): Unit = {
    writer.write("<?xml version=\"1.0\"")
    writer.write(" encoding=\"" + OutputFormat.StandardEncoding + "\"")
    writer.write("?>")
    writeNewLine()
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
        case '<' => entity = "&lt;"
        case '>' => entity = "&gt;"
        case '&' => entity = "&amp;"
        case '\t' | '\n' | '\r' => if (preserve) {
          entity = String.valueOf(c)
        }
        case _ =>
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
        case '<' => entity = "&lt;"
        case '>' => entity = "&gt;"
        case '\'' => if (quote == '\'') {
          entity = "&apos;"
        }
        case '\"' => if (quote == '\"') {
          entity = "&quot;"
        }
        case '&' => entity = "&amp;"
        case '\t' | '\n' | '\r' => // don't encode standard whitespace characters
        case _ => if (c < 32) {
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
}
