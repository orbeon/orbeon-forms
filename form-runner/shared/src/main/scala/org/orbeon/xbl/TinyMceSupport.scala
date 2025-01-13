package org.orbeon.xbl

import org.orbeon.oxf.util.HtmlParsing


object TinyMceSupport {

  //@XPathFunction
  def deserializeExternalValue(
    externalValue       : String,
  ): String =
    HtmlParsing.sanitizeHtmlString(externalValue)
}
