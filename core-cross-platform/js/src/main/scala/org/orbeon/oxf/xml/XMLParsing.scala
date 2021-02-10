 package org.orbeon.oxf.xml

import java.io.{InputStream, Reader}

import javax.xml.parsers.SAXParser
import org.xml.sax.XMLReader


object XMLParsing {

  def newSAXParser(parserConfiguration: ParserConfiguration): SAXParser = throw new NotImplementedError("newSAXParser")
  def newXMLReader(parserConfiguration: ParserConfiguration): XMLReader = throw new NotImplementedError("newXMLReader")

  def isWellFormedXML(xmlString: String): Boolean = throw new NotImplementedError("isWellFormedXML")

  def getReaderFromXMLInputStream(inputStream: InputStream): Reader = throw new NotImplementedError("getReaderFromXMLInputStream")
}
