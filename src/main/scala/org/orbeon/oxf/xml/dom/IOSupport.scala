/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.oxf.xml.dom

import java.io.{InputStream, Reader, StringReader}

import org.orbeon.dom.Document
import org.orbeon.dom.io.{SAXReader, XMLWriter}
import org.orbeon.oxf.xml.XMLParsing


object IOSupport {

  import Private._

  def prettyfy(xmlString: String): String =
    readDom4j(xmlString).getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToPrettyStringJava(doc: Document): String =
    doc.getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToCompactStringJava(doc: Document): String =
    doc.getRootElement.serializeToString(XMLWriter.CompactFormat)

  def domToStringJava(elem: Document): String =
    elem.getRootElement.serializeToString(XMLWriter.DefaultFormat)

  def readDom4j(reader: Reader): Document =
    createSAXReader.read(reader)

  def readDom4j(reader: Reader, uriString: String): Document =
    createSAXReader.read(reader, uriString)

  def readDom4j(xmlString: String): Document =
    createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(new StringReader(xmlString))

  def readDom4j(is: InputStream, uri: String, parserConfiguration: XMLParsing.ParserConfiguration): Document =
    createSAXReader(parserConfiguration).read(is, uri)

  def readDom4j(is: InputStream): Document =
    createSAXReader(XMLParsing.ParserConfiguration.PLAIN).read(is)

  private object Private {

    def createSAXReader(parserConfiguration: XMLParsing.ParserConfiguration): SAXReader =
      new SAXReader(XMLParsing.newXMLReader(parserConfiguration))

    def createSAXReader: SAXReader =
      createSAXReader(XMLParsing.ParserConfiguration.XINCLUDE_ONLY)
  }
}
