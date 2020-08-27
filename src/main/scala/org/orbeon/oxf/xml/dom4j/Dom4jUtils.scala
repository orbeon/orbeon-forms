/**
  * Copyright (C) 2010 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xml.dom4j

import java.io.{InputStream, Reader, StringReader}
import java.{util => ju}

import org.orbeon.dom._
import org.orbeon.dom.io._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.util.StringUtils
import org.orbeon.oxf.xml._
import org.xml.sax.SAXException
import org.xml.sax.helpers.AttributesImpl

import scala.collection.JavaConverters._

// TODO: move this to Scala/remove unneeded stuff
object Dom4jUtils {

    // NOTE: This should be an immutable document, but we don't have support for this yet.
    val NullDocument: Document = {
      val d = Document()
      val nullElement = DocumentFactory.createElement("null")
      nullElement.addAttribute(XMLConstants.XSI_NIL_QNAME, "true")
      d.setRootElement(nullElement)
      d
    }

  private def createSAXReader(parserConfiguration: XMLParsing.ParserConfiguration): SAXReader =
    new SAXReader(XMLParsing.newXMLReader(parserConfiguration))

  private def createSAXReader: SAXReader =
    createSAXReader(XMLParsing.ParserConfiguration.XINCLUDE_ONLY)

  /**
    * Convert an XML string to a prettified XML string.
    */
  def prettyfy(xmlString: String): String =
    readDom4j(xmlString).getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToPrettyStringJava(document: Document): String =
    document.getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToCompactStringJava(document: Document): String =
    document.getRootElement.serializeToString(XMLWriter.CompactFormat)

  def domToStringJava(elem: Document): String =
    elem.getRootElement.serializeToString(XMLWriter.DefaultFormat)

  def readDom4j(reader: Reader): Document =
    createSAXReader.read(reader)

  def readDom4j(reader: Reader, uri: String): Document =
    createSAXReader.read(reader, uri)

  /*
   * Replacement for DocumentHelper.parseText. DocumentHelper.parseText is not used since it creates work for GC
   * (because it relies on JAXP).
   */
  def readDom4j(xmlString: String, parserConfiguration: XMLParsing.ParserConfiguration): Document = {
    val stringReader = new StringReader(xmlString)
    createSAXReader(parserConfiguration).read(stringReader)
  }

  def readDom4j(xmlString: String): Document =
    readDom4j(xmlString, XMLParsing.ParserConfiguration.PLAIN)

  @throws[SAXException]
  @throws[DocumentException]
  def readDom4j(inputStream: InputStream, uri: String, parserConfiguration: XMLParsing.ParserConfiguration): Document =
    createSAXReader(parserConfiguration).read(inputStream, uri)

  @throws[SAXException]
  @throws[DocumentException]
  def readDom4j(inputStream: InputStream): Document =
    createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(inputStream)

  def makeSystemId(e: Element): String =
    Option(e.getData.asInstanceOf[LocationData]) flatMap
      (d => Option(d.file))                      getOrElse
      DOMGenerator.DefaultContext

  // 1 Java caller
  def normalizeTextNodesJava(nodeToNormalize: Node): Node =
    nodeToNormalize.normalizeTextNodes

  /*
   * Saxon's error handler is expensive for the service it provides so we just use our
   * singleton instead.
   *
   * Wrt expensive, delta in heap dump info below is amount of bytes allocated during the
   * handling of a single request to '/' in the examples app. i.e. The trace below was
   * responsible for creating 200k of garbage during the handing of a single request to '/'.
   *
   * delta: 213408 live: 853632 alloc: 4497984 trace: 380739 class: byte[]
   *
   * TRACE 380739:
   * java.nio.HeapByteBuffer.<init>(HeapByteBuffer.java:39)
   * java.nio.ByteBuffer.allocate(ByteBuffer.java:312)
   * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:310)
   * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:290)
   * sun.nio.cs.StreamEncoder$CharsetSE.<init>(StreamEncoder.java:274)
   * sun.nio.cs.StreamEncoder.forOutputStreamWriter(StreamEncoder.java:69)
   * java.io.OutputStreamWriter.<init>(OutputStreamWriter.java:93)
   * java.io.PrintWriter.<init>(PrintWriter.java:109)
   * java.io.PrintWriter.<init>(PrintWriter.java:92)
   * org.orbeon.saxon.StandardErrorHandler.<init>(StandardErrorHandler.java:22)
   * org.orbeon.saxon.event.Sender.sendSAXSource(Sender.java:165)
   * org.orbeon.saxon.event.Sender.send(Sender.java:94)
   * org.orbeon.saxon.IdentityTransformer.transform(IdentityTransformer.java:31)
   * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:453)
   * org.orbeon.oxf.xml.XMLUtils.getDigest(XMLUtils.java:423)
   * org.orbeon.oxf.processor.generator.DOMGenerator.<init>(DOMGenerator.java:93)
   *
   * Before mod
   *
   * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	10510 ms ( 150 mb ), 7124 ( 512 mb ) 	2.131312472239924 ( 150 mb ), 1.7474380872589803 ( 512 mb )
   *
   * after mod
   *
   * 1.4.2_06-b03 	P4 2.6 Ghz	/ 	50 th	tc 4.1.30	9154 ms ( 150 mb ), 6949 ( 512 mb ) 	1.7316203642295738 ( 150 mb ), 1.479365288194895 ( 512 mb )
   *
   */
  def getDocumentSource(d: Document): DocumentSource = {
    val lds = new LocationDocumentSource(d)
    val rdr = lds.getXMLReader
    rdr.setErrorHandler(XMLParsing.ERROR_HANDLER)
    lds
  }

  def getDigest(document: Document): Array[Byte] = {
    val ds = getDocumentSource(document)
    DigestContentHandler.getDigest(ds)
  }

  /**
    * Clean-up namespaces. Some tools generate namespace "un-declarations" or
    * the form xmlns:abc="". While this is needed to keep the XML infoset
    * correct, it is illegal to generate such declarations in XML 1.0 (but it
    * is legal in XML 1.1). Technically, this cleanup is incorrect at the DOM
    * and SAX level, so this should be used only in rare occasions, when
    * serializing certain documents to XML 1.0.
    */
  def adjustNamespaces(document: Document, xml11: Boolean): Document = {

    if (xml11)
      return document

    val writer = new LocationSAXWriter
    val ch = new LocationSAXContentHandler
    writer.setContentHandler(new NamespaceCleanupXMLReceiver(ch, xml11))
    writer.write(document)

    ch.getDocument
  }

  /**
    * Return a Map of namespaces in scope on the given element.
    */
  def getNamespaceContext(elem: Element): ju.Map[String, String] = {
    val namespaces = new ju.HashMap[String, String]
    var currentElem = elem
    while (currentElem ne null) {
      val currentNamespaces = currentElem.declaredNamespaces
      for (namespace <- currentNamespaces.iterator.asScala) {
        if (!namespaces.containsKey(namespace.prefix)) {
          namespaces.put(namespace.prefix, namespace.uri)
          // TODO: Intern namespace strings to save memory; should use NamePool later
          //   namespaces.put(namespace.getPrefix().intern(), namespace.getURI().intern());
        }
      }
      currentElem = currentElem.getParent
    }
    // It seems that by default this may not be declared. However, it should be: "The prefix xml is by definition
    // bound to the namespace name http://www.w3.org/XML/1998/namespace. It MAY, but need not, be declared, and MUST
    // NOT be bound to any other namespace name. Other prefixes MUST NOT be bound to this namespace name, and it
    // MUST NOT be declared as the default namespace."
    namespaces.put(XMLConstants.XML_PREFIX, XMLConstants.XML_URI)
    namespaces
  }

  /**
    * Return a Map of namespaces in scope on the given element, without the default namespace.
    */
  def getNamespaceContextNoDefault(elem: Element): ju.Map[String, String] = {
    val namespaces = getNamespaceContext(elem)
    namespaces.remove("")
    namespaces
  }

  /**
    * Extract a QName from an Element and an attribute name. The prefix of the QName must be in
    * scope. Return null if the attribute is not found.
    */
  def extractAttributeValueQName(elem: Element, attributeName: String): QName =
    extractTextValueQName(elem, elem.attributeValue(attributeName), unprefixedIsNoNamespace = true)

  /**
    * Extract a QName from an Element and an attribute QName. The prefix of the QName must be in
    * scope. Return null if the attribute is not found.
    */
  def extractAttributeValueQName(elem: Element, attributeQName: QName): QName =
    extractTextValueQName(elem, elem.attributeValue(attributeQName), unprefixedIsNoNamespace = true)

  def extractAttributeValueQName(elem: Element, attributeQName: QName, unprefixedIsNoNamespace: Boolean): QName =
    extractTextValueQName(elem, elem.attributeValue(attributeQName), unprefixedIsNoNamespace)

  /**
    * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
    * Return null if the text is empty.
    */
  def extractTextValueQName(elem: Element, unprefixedIsNoNamespace: Boolean): QName =
    extractTextValueQName(elem, elem.getStringValue, unprefixedIsNoNamespace)

  /**
    * Extract a QName from an Element's string value. The prefix of the QName must be in scope.
    * Return null if the text is empty.
    *
    * @param elem                 Element containing the attribute
    * @param qNameString             QName to analyze
    * @param unprefixedIsNoNamespace if true, an unprefixed value is in no namespace; if false, it is in the default namespace
    * @return a QName object or null if not found
    */
  def extractTextValueQName(elem: Element, qNameString: String, unprefixedIsNoNamespace: Boolean): QName =
    extractTextValueQName(getNamespaceContext(elem), qNameString, unprefixedIsNoNamespace)

  /**
    * Extract a QName from a string value, given namespace mappings. Return null if the text is empty.
    *
    * @param namespaces              prefix -> URI mappings
    * @param qNameStringOrig             QName to analyze
    * @param unprefixedIsNoNamespace if true, an unprefixed value is in no namespace; if false, it is in the default namespace
    * @return a QName object or null if not found
    */
  def extractTextValueQName(namespaces: ju.Map[String, String], qNameStringOrig: String, unprefixedIsNoNamespace: Boolean): QName = {
    if (qNameStringOrig eq null)
      return null

    val qNameString = StringUtils.trimAllToEmpty(qNameStringOrig)

    if (qNameString.length == 0)
      return null

    val colonIndex = qNameString.indexOf(':')
    var prefix: String = null
    var localName: String  = null
    var namespaceURI: String  = null
    if (colonIndex == -1) {
      prefix = ""
      localName = qNameString
      if (unprefixedIsNoNamespace)
        namespaceURI = ""
      else {
        val nsURI = namespaces.get(prefix)
        namespaceURI = if (nsURI eq null) ""
        else nsURI
      }
    } else if (colonIndex == 0) {
      throw new OXFException("Empty prefix for QName: " + qNameString)
    } else {
      prefix = qNameString.substring(0, colonIndex)
      localName = qNameString.substring(colonIndex + 1)
      namespaceURI = namespaces.get(prefix)
      if (namespaceURI eq null)
        throw new OXFException("No namespace declaration found for prefix: " + prefix)
    }
    QName(localName, Namespace(prefix, namespaceURI))
  }

  /**
    * Decode a String containing an exploded QName (also known as a "Clark name") into a QName.
    */
  def explodedQNameToQName(qName: String): QName = {

    val openIndex = qName.indexOf("{")
    if (openIndex == -1)
      return QName.apply(qName)

    val namespaceURI = qName.substring(openIndex + 1, qName.indexOf("}"))
    val localName = qName.substring(qName.indexOf("}") + 1)

    QName(localName, Namespace("p1", namespaceURI))
  }

  /**
    * Create a copy of a dom4j Node.
    *
    * @param source source Node
    * @return copy of Node
    */
  def createCopy(source: Node): Node = source match {
    case elem: Element => elem.createCopy
    case _             => source.deepCopy
  }

  /**
    * Return a new document with a copy of newRoot as its root.
    */
  def createDocumentCopyElement(newRoot: Element) =
    Document(newRoot.createCopy)

  /**
    * Return a new document with all parent namespaces copied to the new root element, assuming they are not already
    * declared on the new root element. The element passed is deep copied.
    *
    * @param newRoot element which must become the new root element of the document
    * @return new document
    */
  def createDocumentCopyParentNamespaces(newRoot: Element): Document =
    createDocumentCopyParentNamespaces(newRoot, detach = false)

  /**
    * Return a new document with all parent namespaces copied to the new root element, assuming they are not already
    * declared on the new root element.
    *
    * @param newRoot element which must become the new root element of the document
    * @param detach  if true the element is detached, otherwise it is deep copied
    * @return new document
    */
  def createDocumentCopyParentNamespaces(newRoot: Element, detach: Boolean): Document = {
    val parentElement = newRoot.getParent
    val document =
      if (detach) {
        // Detach
        val d = Document()
        d.setRootElement(newRoot.detach().asInstanceOf[Element])
        d
      } else {
        // Copy
        createDocumentCopyElement(newRoot)
      }
    copyMissingNamespaces(parentElement, document.getRootElement)
    document
  }

  def copyMissingNamespaces(sourceElem: Element, destinationElement: Element) {
    val parentNamespaceContext = getNamespaceContext(sourceElem)
    val rootElementNamespaceContext = getNamespaceContext(destinationElement)
    for (prefix <- parentNamespaceContext.keySet.asScala) {
      // NOTE: Don't use rootElement.getNamespaceForPrefix() because that will return the element prefix's
      // namespace even if there are no namespace nodes
      if (rootElementNamespaceContext.get(prefix) eq null) {
        val uri = parentNamespaceContext.get(prefix)
        destinationElement.addNamespace(prefix, uri)
      }
    }
  }

  /**
    * Return a new document with a copy of newRoot as its root and all parent namespaces copied to the new root
    * element, except those with the prefixes appearing in the Map, assuming they are not already declared on the new
    * root element.
    */
  def createDocumentCopyParentNamespaces(newRoot: Element, prefixesToFilter: ju.Set[String]): Document = {
    val document = createDocumentCopyElement(newRoot)
    val rootElement = document.getRootElement
    val parentElement = newRoot.getParent
    val parentNamespaceContext = getNamespaceContext(parentElement)
    val rootElemNamespaceContext = getNamespaceContext(rootElement)
    for (prefix <- parentNamespaceContext.keySet.asScala) {
      if ((rootElemNamespaceContext.get(prefix) eq null) && ! prefixesToFilter.contains(prefix)) {
        val uri = parentNamespaceContext.get(prefix)
        rootElement.addNamespace(prefix, uri)
      }
    }
    document
  }

  /**
    * Return a copy of the given element which includes all the namespaces in scope on the element.
    *
    * @param sourceElem element to copy
    * @return copied element
    */
  def copyElementCopyParentNamespaces(sourceElem: Element): Element = {
    val newElement = sourceElem.createCopy
    copyMissingNamespaces(sourceElem.getParent, newElement)
    newElement
  }

  /**
    * Workaround for Java's lack of an equivalent to C's __FILE__ and __LINE__ macros.  Use
    * carefully as it is not fast.
    *
    * Perhaps in 1.5 we will find a better way.
    *
    * @return LocationData of caller.
    */
  def getLocationData: LocationData =
    getLocationData(1, isDebug = false)

  def getLocationData(depth: Int, isDebug: Boolean): LocationData = { // Enable this with a property for debugging only, as it is time consuming

    if (!isDebug && !org.orbeon.oxf.properties.Properties.instance.getPropertySet.getBoolean("oxf.debug.enable-java-location-data", default = false))
      return null

    // Compute stack trace and extract useful information
    val e = new Exception
    val stkTrc = e.getStackTrace
    val depthToUse = depth + 1
    val sysID = stkTrc(depthToUse).getFileName
    val line = stkTrc(depthToUse).getLineNumber

    new LocationData(sysID, line, -1)
  }

  /**
    * Visit a subtree of a dom4j document.
    *
    * @param container       element containing the elements to visit
    * @param visitorListener listener to call back
    */
  def visitSubtree(container: Element, visitorListener: VisitorListener): Unit =
    visitSubtree(container, visitorListener, mutable = false)

  /**
    * Visit a subtree of a dom4j document.
    *
    * @param container       element containing the elements to visit
    * @param visitorListener listener to call back
    * @param mutable         whether the source tree can mutate while being visited
    */
  def visitSubtree(container: Element, visitorListener: VisitorListener, mutable: Boolean): Unit = {

    // If the source tree can mutate, copy the list first, otherwise dom4j might throw exceptions
    val content =
      if (mutable)
        new ju.ArrayList[Node](container.content)
      else
        container.content

    // Iterate over the content
    for (childNode <- content.asScala) {
      childNode match {
        case childElem: Element =>
          visitorListener.startElement(childElem)
          visitSubtree(childElem, visitorListener, mutable)
          visitorListener.endElement(childElem)
        case text: Text => visitorListener.text(text)
        case _ =>
        // Ignore as we don't need other node types for now
      }
    }
  }

  def elementToDebugString(element: Element): String = {

    // Open start tag
    val sb = new StringBuilder("<")
    sb.append(element.getQualifiedName)

    // Attributes if any
    for (currentAtt <- element.attributeIterator.asScala) {
      sb.append(' ')
      sb.append(currentAtt.getQualifiedName)
      sb.append("=\"")
      sb.append(currentAtt.getValue)
      sb.append('\"')
    }

    val isEmptyElement = element.elements.isEmpty && element.getText.length == 0
    if (isEmptyElement) {
      // Close empty element
      sb.append("/>")
    } else {
      // Close start tag
      sb.append('>')
      sb.append("[...]")

      // Close element with end tag
      sb.append("</")
      sb.append(element.getQualifiedName)
      sb.append('>')
    }
    sb.toString
  }

  def attributeToDebugString(attribute: Attribute): String =
    attribute.getQualifiedName + "=\"" + attribute.getValue + '\"'

  /**
    * Convert dom4j attributes to SAX attributes.
    *
    * @param element dom4j Element
    * @return SAX Attributes
    */
  def getSAXAttributes(element: Element): AttributesImpl = {
    val result = new AttributesImpl
    for (att <- element.attributeIterator.asScala) {
      result.addAttribute(att.getNamespaceURI, att.getName, att.getQualifiedName, XMLReceiverHelper.CDATA, att.getValue)
    }
    result
  }

  def createDocument(debugXML: DebugXML): Document = {

    val identity = TransformerUtils.getIdentityTransformerHandler
    val result = new LocationDocumentResult
    identity.setResult(result)

    val helper = new XMLReceiverHelper(
      new ForwardingXMLReceiver(identity) {
        override def startDocument(): Unit = ()
        override def endDocument(): Unit = ()
      }
    )

    identity.startDocument()
    debugXML.toXML(helper)
    identity.endDocument()

    result.getDocument
  }

  // 2019-11-14: Only used by processor/pipeline Java code
  def qNameToExplodedQName(qName: QName): String =
    if (qName eq null) null else qName.clarkName

  trait VisitorListener {
    def startElement(element: Element)
    def endElement(element: Element)
    def text(text: Text)
  }

  trait DebugXML {
    def toXML(helper: XMLReceiverHelper): Unit
  }
}