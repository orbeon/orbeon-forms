/**
  * Copyright (C) 2007 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.util

import java.{lang => jl}

import org.orbeon.oxf.util.StringUtils._

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("OrbeonMarkupUtils") // AjaxServer.js/xforms.js, remove when possible
object MarkupUtils {

  @JSExport // xforms.js, remove when possible
  def escapeXmlMinimal(s: String): String = s.escapeXmlMinimal

  @JSExport // AjaxServer.js, remove when possible
  def escapeXmlForAttribute(s: String): String = s.escapeXmlForAttribute

  @JSExport // AjaxServer.js, remove when possible
  def normalizeSerializedHtml(s: String): String = s.normalizeSerializedHtml

  // Java callers
  def encodeHRRI(s: String, processSpace: Boolean): String = s.encodeHRRI(processSpace)

  implicit class MarkupStringOps(private val s: String) extends AnyVal {

    def escapeXmlMinimal: String =
      replace(
        replace(
          replace(
            s,
            "&", // first, so we don't replace the `&` in the subsequent character entity references
            "&amp;"
          ),
          "<",
          "&lt;"
        ),
        ">",
        "&gt;" // because the sequence `]]>` is not allowed
      )

    def escapeXmlForAttribute: String =
      replace(
        s.escapeXmlMinimal,
        "\"",
        "&quot;"
      )

    def filterOutInvalidXmlCharacters: String =
      RemoveDisallowedXmlCharactersRegex.pattern.matcher(s).replaceAll("")

    def unescapeXmlMinimal: String =
      replace(
        replace(
          replace(
            s,
            "&amp;",
            "&"
          ),
          "&lt;",
          "<"
        ),
        "&gt;",
        ">"
      )

    def normalizeSerializedHtml: String =
      CrRegex.replaceAllIn(s, "")

    def escapeJavaScript: String =
      replace(
        replace(
          replace(
            replace(
              s,
              "\\",
              "\\\\"
            ),
            "\"",
            "\\\""
          ),
          "\n",
          "\\n"
        ),
        "\t",
        "\\t"
      )

    /**
     * Encode a Human Readable Resource Identifier to a URI. Leading and trailing spaces are removed first.
     *
     * W3C note: https://www.w3.org/TR/leiri/
     *
     * @param uriString    URI to encode
     * @param processSpace whether to process the space character or leave it unchanged
     * @return encoded URI, or null if uriString was null
     */
    def encodeHRRI(processSpace: Boolean): String = {

      if (s eq null)
        return null

      // Note that the XML Schema spec says "Spaces are, in principle, allowed in the ·lexical space· of anyURI,
      // however, their use is highly discouraged (unless they are encoded by %20).".

      // We assume that we never want leading or trailing spaces. You can use %20 if you really want this.
      val trimmed = s.trimAllToEmpty

      // We try below to follow the "Human Readable Resource Identifiers" RFC, in draft as of 2007-06-06.
      // - the control characters #x0 to #x1F and #x7F to #x9F
      // - space #x20
      // - the delimiters "<" #x3C, ">" #x3E, and """ #x22
      // - the unwise characters "{" #x7B, "}" #x7D, "|" #x7C, "\" #x5C, "^" #x5E, and "`" #x60
      val sb = new jl.StringBuilder(trimmed.length * 2)

      for (c <- trimmed)
        if (
          c >= 0 && (
            c <= 0x1f                   ||
            (processSpace && c == 0x20) ||
            c == 0x22                   ||
            c == 0x3c                   ||
            c == 0x3e                   ||
            c == 0x5c                   ||
            c == 0x5e                   ||
            c == 0x60                   ||
            (c >= 0x7b && c <= 0x7d)    ||
            (c >= 0x7f && c <= 0x9f)
          )
        ) {
          sb.append('%')
          sb.append(NumberUtils.toHexString(c.toByte).toUpperCase)
        } else
          sb.append(c)

      sb.toString
    }
  }

  val VoidElements = Set(
    // HTML 5: http://www.w3.org/TR/html5/syntax.html#void-elements
    "area", "base", "br", "col", "command", "embed", "hr", "img", "input", "keygen", "link", "meta", "param", "source", "track", "wbr", // Legacy
    "basefont", "frame", "isindex"
  )

  private val CrRegex = """\r""".r
  private val RemoveDisallowedXmlCharactersRegex = """[\x00-\x08\x0B\x0C\x0E-\x1F]""".r

  private def replace(text: String, searchString: String, replacement: String): String = {

    def isEmpty(cs: String): Boolean = (cs eq null) || cs.length == 0

    if (isEmpty(text) || isEmpty(searchString) || (replacement eq null))
      return text

    var start = 0
    var end = text.indexOf(searchString, start)
    if (end == -1)
      return text

    val replLength = searchString.length
    var increase = replacement.length - replLength

    increase = if (increase < 0) 0 else increase * 16

    val buf = new jl.StringBuilder(text.length + increase)
    while (end != -1) {
      buf.append(text.substring(start, end)).append(replacement)
      start = end + replLength
      end   = text.indexOf(searchString, start)
    }
    buf.append(text.substring(start))
    buf.toString
  }
}
