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

import org.orbeon.dom._
import org.orbeon.dom.io._
import org.orbeon.oxf.xml._

// TODO: move/remove unneeded stuff
object Dom4jUtils {

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

  def readDom4j(xmlString: String): Document = {
    val stringReader = new StringReader(xmlString)
    createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(stringReader)
  }

  def readDom4j(inputStream: InputStream, uri: String, parserConfiguration: XMLParsing.ParserConfiguration): Document =
    createSAXReader(parserConfiguration).read(inputStream, uri)

  def readDom4j(inputStream: InputStream): Document =
    createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(inputStream)

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

  trait DebugXML {
    def toXML(helper: XMLReceiverHelper): Unit
  }
}