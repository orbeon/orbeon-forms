package org.orbeon.dom.io

import javax.xml.transform.sax.SAXSource

import org.orbeon.dom.{Document, Node}
import org.xml.sax.{InputSource, XMLFilter, XMLReader}

class DocumentSource(document: Document) extends SAXSource {

  setDocument(document)

  def this(node: Node) = this(node.getDocument)

  def getDocument: Document =
    getInputSource.asInstanceOf[DocumentInputSource].getDocument

  def setDocument(document: Document): Unit =
    super.setInputSource(new DocumentInputSource(document))

  private var xmlReader: XMLReader = new SAXWriter
  override def getXMLReader: XMLReader = xmlReader

  override def setInputSource(inputSource: InputSource): Unit =
    inputSource match {
      case source: DocumentInputSource => super.setInputSource(source)
      case _                           => throw new UnsupportedOperationException
    }

  override def setXMLReader(reader: XMLReader): Unit =
    reader match {
      case writer: SAXWriter =>
        this.xmlReader = writer
      case xmlFilter: XMLFilter =>

        val breaks = new scala.util.control.Breaks
        import breaks._

        var currentFilter = xmlFilter
        breakable {
          while (true) {
            val parent = currentFilter.getParent
            parent match {
              case parentFilter: XMLFilter => currentFilter = parentFilter
              case _ => break()
            }
          }
        }
        currentFilter.setParent(xmlReader)
        xmlReader = currentFilter
      case _ =>
        throw new UnsupportedOperationException
    }
}
