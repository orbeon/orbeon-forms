/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.processor

import org.orbeon.dom.io.DocumentSource
import org.orbeon.dom.{Document, Element, Namespace, Node, QName}
import org.orbeon.oxf.processor.generator.DOMGenerator
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xml.{DigestContentHandler, NamespaceCleanupXMLReceiver, XMLConstants, XMLParsing}
import org.orbeon.oxf.xml.dom4j.{LocationData, LocationDocumentSource, LocationSAXContentHandler, LocationSAXWriter}


object ProcessorSupport {

  // NOTE: This should be an immutable document, but we don't have support for this yet.
  val NullDocument: Document = {
    val d = Document()
    val nullElement = Element("null")
    nullElement.addAttribute(XMLConstants.XSI_NIL_QNAME, "true")
    d.setRootElement(nullElement)
    d
  }

  def makeSystemId(e: Element): String =
    Option(e.getData.asInstanceOf[LocationData]) flatMap
      (d => Option(d.file))                      getOrElse
      DOMGenerator.DefaultContext

  // 1 Java caller
  def normalizeTextNodesJava(nodeToNormalize: Node): Node =
    nodeToNormalize.normalizeTextNodes

  def qNameToExplodedQName(qName: QName): String =
    if (qName eq null) null else qName.clarkName

    /**
    * Clean-up namespaces. Some tools generate namespace "un-declarations" or
    * the form xmlns:abc="". While this is needed to keep the XML infoset
    * correct, it is illegal to generate such declarations in XML 1.0 (but it
    * is legal in XML 1.1). Technically, this cleanup is incorrect at the DOM
    * and SAX level, so this should be used only in rare occasions, when
    * serializing certain documents to XML 1.0.
    *
    * 2020-08-27: 2 legacy Java callers in `QueryInterpreter`.
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
    * Decode a String containing an exploded QName (also known as a "Clark name") into a QName.
    *
    * 2020-08-27: 1 caller.
    */
  def explodedQNameToQName(qName: String, prefix: String): QName = {

    val openIndex = qName.indexOf("{")
    if (openIndex == -1)
      return QName.apply(qName)

    val namespaceURI = qName.substring(openIndex + 1, qName.indexOf("}"))
    val localName = qName.substring(qName.indexOf("}") + 1)

    QName(localName, Namespace(prefix, namespaceURI))
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

  private def getLocationData(depth: Int, isDebug: Boolean): LocationData = {
    // Enable this with a property for debugging only, as it is time consuming
    if (
      ! isDebug &&
      ! Properties.instance.getPropertySet.getBoolean("oxf.debug.enable-java-location-data", default = false)
    ) return null

    // Compute stack trace and extract useful information
    val e = new Exception
    val stkTrc = e.getStackTrace
    val depthToUse = depth + 1
    val sysID = stkTrc(depthToUse).getFileName
    val line = stkTrc(depthToUse).getLineNumber

    new LocationData(sysID, line, -1)
  }

  def getDocumentSource(d: Document): DocumentSource = {
    val lds = new LocationDocumentSource(d)
    val rdr = lds.getXMLReader
    rdr.setErrorHandler(XMLParsing.ERROR_HANDLER)
    lds
  }

  def computeDocumentDigest(document: Document): Array[Byte] = {
    val ds = getDocumentSource(document)
    DigestContentHandler.getDigest(ds)
  }
}
