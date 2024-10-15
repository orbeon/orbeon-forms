package org.orbeon.oxf.xml

import org.orbeon.oxf.util.MarkupUtils.{MarkupStringOps, VoidElements}
import org.xml.sax.Attributes


class SimpleHtmlSerializer(
  sb                 : java.lang.StringBuilder,
  filterOutElemByName: String => Boolean           = _      => false,
  filterOutAtt       : (String, String) => Boolean = (_, _) => false,

) extends XMLReceiverAdapter {

  private var isStartElement = false

  override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
    sb.append(new String(chars, start, length).escapeXmlMinimal) // NOTE: not efficient to create a new String here
    isStartElement = false
  }

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit =
    if (! filterOutElemByName(localname)) {

      sb.append('<')
      sb.append(localname)
      val attCount = attributes.getLength

      for (i <- 0 until attCount) {
        val currentName = attributes.getLocalName(i)
        val currentValue = attributes.getValue(i)

        if (! filterOutAtt(currentName, currentValue)) {
          sb.append(' ')
          sb.append(currentName)
          sb.append("=\"")
          sb.append(currentValue)
          sb.append('"')
        }
      }

      sb.append('>')
      isStartElement = true
    }

  override def endElement(uri: String, localname: String, qName: String): Unit =
    if (! filterOutElemByName(localname)) {
      if (! isStartElement || ! VoidElements(localname)) {
        sb.append("</")
        sb.append(localname)
        sb.append('>')
      }
      isStartElement = false
    }
}