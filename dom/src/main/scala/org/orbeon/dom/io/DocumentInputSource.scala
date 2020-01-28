package org.orbeon.dom.io

import java.io.{IOException, Reader, StringReader, StringWriter}

import org.orbeon.dom.Document
import org.xml.sax.InputSource

class DocumentInputSource(var document: Document) extends InputSource {

  setSystemId(document.getName)

  def getDocument = document

  def setDocument(document: Document): Unit = {
    this.document = document
    setSystemId(document.getName)
  }

  // This method is not supported as this source is always a linkDocument instance.
  override def setCharacterStream(characterStream: Reader) =
    throw new UnsupportedOperationException

  /**
   * Note this method is quite inefficient, it turns the in memory XML tree
   * object model into a single block of text which can then be read by other
   * XML parsers. Should only be used with care.
   */
  override def getCharacterStream: Reader =
    try {
      val writer = new StringWriter
      val xmlWriter = new XMLWriter(writer, XMLWriter.DefaultFormat)
      xmlWriter.write(document)
      writer.flush()
      new StringReader(writer.toString)
    } catch {
      case e: IOException => new Reader {
        def read(ch: Array[Char], offset: Int, length: Int): Int = throw e
        def close() = ()
      }
    }
}
