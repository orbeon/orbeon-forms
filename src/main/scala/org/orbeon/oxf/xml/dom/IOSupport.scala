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
import org.orbeon.oxf.xml.{ParserConfiguration, XMLParsing}


object IOSupport {

  import Private._

  def prettyfy(xmlString: String): String =
    readOrbeonDom(xmlString).getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToPrettyStringJava(doc: Document): String =
    doc.getRootElement.serializeToString(XMLWriter.PrettyFormat)

  def domToCompactStringJava(doc: Document): String =
    doc.getRootElement.serializeToString(XMLWriter.CompactFormat)

  def domToStringJava(elem: Document): String =
    elem.getRootElement.serializeToString(XMLWriter.DefaultFormat)

  def readOrbeonDom(reader: Reader): Document =
    createSAXReader.read(reader)

  def readOrbeonDom(reader: Reader, uriString: String): Document =
    createSAXReader.read(reader, uriString)

  def readOrbeonDom(xmlString: String): Document =
    createSAXReader(ParserConfiguration.Plain).read(new StringReader(xmlString))

  def readOrbeonDom(is: InputStream, uri: String, parserConfiguration: ParserConfiguration): Document =
    createSAXReader(parserConfiguration).read(is, uri)

  def readOrbeonDom(is: InputStream): Document =
    createSAXReader(ParserConfiguration.Plain).read(is)

  private object Private {

    def createSAXReader(parserConfiguration: ParserConfiguration): SAXReader =
      new SAXReader(XMLParsing.newXMLReader(parserConfiguration))

    def createSAXReader: SAXReader =
      createSAXReader(ParserConfiguration.XIncludeOnly)
  }
}
