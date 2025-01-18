package org.orbeon.oxf.util

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.XMLReceiver
import org.scalajs.dom
import org.scalajs.dom.html
import org.xml.sax.Attributes

import scala.util.control.NonFatal


trait HtmlParsingPlatform {

  def parseHtmlString(value: String, xmlReceiver: XMLReceiver): Unit =
    try {
      // Use the browser's HTML parser to parse the HTML string
      val nodes =
        (new dom.DOMParser)
          .parseFromString(s"<html><body>$value</body></html>", dom.MIMEType.`text/html`)
          .childNodes

      def processNode(node: dom.Node): Unit = node match {
        case e: html.Element =>

          val atts =
            new Attributes {
              override def getLength: Int = e.attributes.length

              override def getURI(index: Int): String =
                if (index >= 0 && index < e.attributes.length) "" else null

              override def getLocalName(index: Int): String =
                if (index >= 0 && index < e.attributes.length) e.attributes(index).name else null

              override def getQName(index: Int): String = getLocalName(index)

              override def getType(index: Int): String =
              if (index >= 0 && index < e.attributes.length) "CDATA" else null

              override def getValue(index: Int): String =
                if (index >= 0 && index < e.attributes.length) e.attributes(index).value else null

              override def getIndex(uri: String, localName: String): Int = ???
              override def getIndex(qName: String): Int = ???

              override def getType(uri: String, localName: String): String =
                if (uri !=  "")
                  null
                else
                  if (e.attributes.getNamedItem(localName) ne null)
                    "CDATA"
                  else
                    null

              override def getType(qName: String): String =
                if (qName.contains(":"))
                  null
                else if
                (e.attributes.getNamedItem(qName) ne null)
                  "CDATA"
                else
                  null

              override def getValue(uri: String, localName: String): String =
                if (uri !=  "")
                  null
                else {
                  val item = e.attributes.getNamedItem(localName)
                  if (item ne null) item.value else null
                }

              override def getValue(qName: String): String =
                if (qName.contains(":"))
                  null
                else {
                  val item = e.attributes.getNamedItem(qName)
                  if (item ne null) item.value else null
                }
            }

          xmlReceiver.startElement("", e.tagName.toLowerCase, e.tagName.toLowerCase, atts)
          e.childNodes.foreach(processNode)
          xmlReceiver.endElement("", e.tagName.toLowerCase, e.tagName.toLowerCase)
        case e: dom.Text =>
          xmlReceiver.characters(e.textContent.toCharArray, 0, e.textContent.length)
        case _ => // Comment, CDATASection, Attr, Document, DocumentFragment, DocumentType, ProcessingInstruction, Entity, EntityReference, Notation
      }

      xmlReceiver.startDocument()
      nodes.foreach(processNode)
      xmlReceiver.endDocument()
    } catch {
      case NonFatal(_) =>
        throw new OXFException(s"Cannot parse value as text/html for value: `$value`")
    }
}
