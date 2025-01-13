package org.orbeon.oxf.util

import org.orbeon.oxf.xml.{ElemFilter, HTMLBodyXMLReceiver, SimpleHtmlSerializer}


object HtmlParsing extends HtmlParsingPlatform {

  private val safeElementsElemFilter: String => ElemFilter =
    n => if (MarkupUtils.SafeElements(n)) ElemFilter.Keep else ElemFilter.RemoveWithContent

  def sanitizeHtmlString(
    value          : String,
    extraElemFilter: String => ElemFilter = _  => ElemFilter.Keep
  ): String = {
    val sb = new java.lang.StringBuilder
    parseHtmlString(
      value,
      new HTMLBodyXMLReceiver(
        new SimpleHtmlSerializer(
          sb,
          n => safeElementsElemFilter(n).combine(extraElemFilter(n)),
          (n, v) => n.startsWith("on") || v.contains("javascript:")
        ),
        ""
      )
    )
    sb.toString
  }
}
