package org.orbeon.oxf.util

import org.ccil.cowan.tagsoup.HTMLSchema
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml.XMLReceiver
import org.xml.sax.InputSource

import java.io.StringReader
import scala.util.control.NonFatal


trait HtmlParsingPlatform {

  private val TagSoupHtmlSchema = new HTMLSchema

  def parseHtmlString(value: String, xmlReceiver: XMLReceiver): Unit =
    try {
      val xmlReader = new org.ccil.cowan.tagsoup.Parser
      xmlReader.setProperty(org.ccil.cowan.tagsoup.Parser.schemaProperty, TagSoupHtmlSchema)
      xmlReader.setFeature(org.ccil.cowan.tagsoup.Parser.ignoreBogonsFeature, true)
      xmlReader.setContentHandler(xmlReceiver)
      val inputSource = new InputSource
      inputSource.setCharacterStream(new StringReader(value))
      xmlReader.parse(inputSource)
    } catch {
      case NonFatal(_) =>
        throw new OXFException(s"Cannot parse value as text/html for value: `$value`")
    }
}
