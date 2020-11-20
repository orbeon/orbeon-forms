 package org.orbeon.oxf.xml

import java.io.{InputStream, Reader}

import javax.xml.parsers.SAXParser
import org.xml.sax.XMLReader


object XMLParsing {

  def newSAXParser(parserConfiguration: ParserConfiguration): SAXParser = ???
  def newXMLReader(parserConfiguration: ParserConfiguration): XMLReader = ???

  def isWellFormedXML(xmlString: String): Boolean = ???

  def getReaderFromXMLInputStream(inputStream: InputStream): Reader = ???
}
