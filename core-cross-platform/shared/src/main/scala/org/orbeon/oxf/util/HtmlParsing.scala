package org.orbeon.oxf.util

import org.orbeon.oxf.xml.*


object HtmlParsing extends HtmlParsingPlatform {

  private val safeElementsElemFilter: String => ElemFilter =
    n => if (MarkupUtils.SafeElements(n)) ElemFilter.Keep else ElemFilter.RemoveWithContent

  private val JavaScriptSchemeRegex = "(?i)javascript:".r

  def sanitizeHtmlString(
    value          : String,
    extraElemFilter: String => ElemFilter = _ => ElemFilter.Keep
  ): String = {
    val sb = new java.lang.StringBuilder
    sanitizeHtmlStringToReceiver(
      value,
      "",
      extraElemFilter
    )(
      new SimpleHtmlSerializer(sb)
    )
    sb.toString
  }

  def sanitizeHtmlStringToReceiver(
    value          : String,
    xhtmlPrefix    : String,
    extraElemFilter: String => ElemFilter = _ => ElemFilter.Keep
  )(
    receiver       : XMLReceiver
  ): Unit =
    parseHtmlString(
      value,
      new HTMLBodyXMLReceiver(
        new SimpleHtmlFilter(
          n => safeElementsElemFilter(n).combine(extraElemFilter(n)),
          (n, v) => n.startsWith("on") || JavaScriptSchemeRegex.findFirstIn(v.filterNot("\t\n\r".contains(_))).isDefined
        )(
          receiver
        ),
        xhtmlPrefix
      )
    )
}
