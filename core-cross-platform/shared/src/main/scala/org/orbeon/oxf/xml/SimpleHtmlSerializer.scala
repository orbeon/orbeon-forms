package org.orbeon.oxf.xml

import org.orbeon.oxf.util.MarkupUtils.{MarkupStringOps, VoidElements}
import org.xml.sax.Attributes


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

class SimpleHtmlSerializer(
  sb                  : java.lang.StringBuilder,
  elemFilter          : String => ElemFilter        = _      => ElemFilter.Keep,
  filterOutAtt        : (String, String) => Boolean = (_, _) => false,
) extends XMLReceiverAdapter {

  private var isStartElement = false
  private var level = 0
  private var removeSubtreeLevel: Option[Int] = None

  override def characters(chars: Array[Char], start: Int, length: Int): Unit =
    if (removeSubtreeLevel.isEmpty) {
      sb.append(new String(chars, start, length).escapeXmlMinimal) // NOTE: not efficient to create a new String here
      isStartElement = false
    }

  override def startElement(uri: String, localname: String, qName: String, attributes: Attributes): Unit = {
    level += 1
    if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.Remove) {
      // Just ignore
    } else if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.RemoveWithContent) {
      removeSubtreeLevel = Some(level)
    } else if (removeSubtreeLevel.isEmpty) {
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
  }

  override def endElement(uri: String, localname: String, qName: String): Unit = {
    if (removeSubtreeLevel.isEmpty && elemFilter(localname) == ElemFilter.Remove) {
      // Just ignore
    } else if (removeSubtreeLevel.contains(level)) {
      removeSubtreeLevel = None
    } else if (removeSubtreeLevel.isEmpty) {
      if (! isStartElement || ! VoidElements(localname)) {
        sb.append("</")
        sb.append(localname)
        sb.append('>')
      }
      isStartElement = false
    }
    level -= 1
  }
}