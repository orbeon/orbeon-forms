package org.orbeon.oxf.util

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.XMLReceiver

import scala.util.control.NonFatal


trait HtmlParsingPlatform {

  def parseHtmlString(value: String, xmlReceiver: XMLReceiver): Unit =
    try {
      JsoupSAX.parseHtmlToReceiver(value, xmlReceiver)
    } catch {
      case NonFatal(e) =>
        throw new OXFException(s"Cannot parse value as text/html for value: `$value`", e)
    }
}
