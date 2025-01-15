package org.orbeon.oxf.xml

import org.orbeon.oxf.util.MarkupUtils.{MarkupStringOps, VoidElements}
import org.xml.sax.Attributes
import org.xml.sax.helpers.AttributesImpl


sealed trait ElemFilter {
  def combine(that: ElemFilter): ElemFilter =
    if (this == ElemFilter.RemoveWithContent || that == ElemFilter.RemoveWithContent) {
      ElemFilter.RemoveWithContent
    } else if (this == ElemFilter.Remove || that == ElemFilter.Remove) {
      ElemFilter.Remove
    } else {
      ElemFilter.Keep
    }
}
object ElemFilter {
  case object Keep              extends ElemFilter
  case object Remove            extends ElemFilter
  case object RemoveWithContent extends ElemFilter
}

class SimpleHtmlFilter(
  elemFilter  : String => ElemFilter        = _      => ElemFilter.Keep,
  filterOutAtt: (String, String) => Boolean = (_, _) => false,
)(
  xmlReceiver: XMLReceiver
) extends ForwardingXMLReceiver(xmlReceiver) {

  private var level = 0
  private var removeSubtreeLevel: Option[Int] = None

  setForward(true)

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    level += 1
    if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.Remove) {
      // Just ignore
    } else if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.RemoveWithContent) {
      removeSubtreeLevel = Some(level)
      setForward(false)
    } else {

      val attCount = attributes.getLength
      var newAtts: AttributesImpl = null // attributes will be lazily copied if needed

      for (i <- 0 until attCount) {
        val currentName  = attributes.getLocalName(i)
        val currentValue = attributes.getValue(i)

        if (filterOutAtt(currentName, currentValue)) {
          if (newAtts == null) {
            newAtts = new AttributesImpl
            for (j <- 0 until i)
              newAtts.addAttribute(attributes.getURI(j), attributes.getLocalName(j), attributes.getQName(j), attributes.getType(j), attributes.getValue(j))
          }
        } else if (newAtts != null) {
          newAtts.addAttribute(attributes.getURI(i), currentName, attributes.getQName(i), attributes.getType(i), currentValue)
        }
      }

      super.startElement(uri, localname, qName, if (newAtts != null) newAtts else attributes)
    }
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.Remove) {
      // Just ignore
    } else if (removeSubtreeLevel.contains(level)) {
      removeSubtreeLevel = None
      setForward(true)
    } else {
      super.endElement(uri, localname, qName)
    }
    level -= 1
  }
}

class SimpleHtmlSerializer(sb: java.lang.StringBuilder)
  extends XMLReceiverAdapter {

  private var isStartElement = false

  override def characters(chars: Array[Char], start: Int, length: Int): Unit = {
    sb.append(new String(chars, start, length).escapeXmlMinimal) // NOTE: not efficient to create a new String here
    isStartElement = false
  }

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    sb.append('<')
    sb.append(localname)
    val attCount = attributes.getLength

    for (i <- 0 until attCount) {
      sb.append(' ')
      sb.append(attributes.getLocalName(i))
      sb.append("=\"")
      sb.append(attributes.getValue(i))
      sb.append('"')
    }

    sb.append('>')
    isStartElement = true
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    if (! isStartElement || ! VoidElements(localname)) {
      sb.append("</")
      sb.append(localname)
      sb.append('>')
    }
    isStartElement = false
  }
}