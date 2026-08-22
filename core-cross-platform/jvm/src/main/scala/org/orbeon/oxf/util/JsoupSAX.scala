package org.orbeon.oxf.util

import org.jsoup.Jsoup
import org.jsoup.nodes.{Comment, Document, Element, Node, TextNode}
import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.helpers.AttributesImpl

import scala.jdk.CollectionConverters.*


object JsoupSAX {

  def parseHtmlToReceiver(html: String, xmlReceiver: XMLReceiver): Unit = {
    val doc = Jsoup.parse(html)
    xmlReceiver.startDocument()
    traverseNode(doc, xmlReceiver)
    xmlReceiver.endDocument()
  }

  private def traverseNode(node: Node, xmlReceiver: XMLReceiver): Unit =
    node match {
      case doc: Document =>
        doc
          .childNodes()
          .iterator()
          .asScala
          .foreach(traverseNode(_, xmlReceiver))
      case elem: Element =>
        val localName = elem.tagName()
        val qName     = localName
        val atts = new AttributesImpl
        elem
          .attributes()
          .iterator()
          .asScala
          .filterNot { attr =>
            // Allowed in HTML, but for backward compatibility with earlier Tagsoup behavior, we reject such attributes
            attr.getKey.contains(":")
          }
          .foreach { attr =>
            atts.addAttribute("", attr.getKey, attr.getKey, "CDATA", attr.getValue)
          }
        xmlReceiver.startElement("", localName, qName, atts)
        elem.childNodes().iterator().asScala.foreach(traverseNode(_, xmlReceiver))
        xmlReceiver.endElement("", localName, qName)
      case text: TextNode =>
        val data = text.getWholeText
        if (data.nonEmpty)
          xmlReceiver.characters(data.toCharArray, 0, data.length)
      case comment: Comment =>
        val data = comment.getData
        if (data.nonEmpty)
          xmlReceiver.comment(data.toCharArray, 0, data.length)
      case _ =>
    }
}
