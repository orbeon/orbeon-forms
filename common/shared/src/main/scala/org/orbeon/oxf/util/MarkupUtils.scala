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

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("ORBEON.common.MarkupUtils") // AjaxServer.js/xforms.js, remove when possible
object MarkupUtils {

  @JSExport // AjaxServer.js/xforms.js, remove when possible
  def escapeXmlMinimal(s: String): String = s.escapeXmlMinimal

  @JSExport // AjaxServer.js, remove when possible
  def escapeXmlForAttribute(s: String): String = s.escapeXmlForAttribute

  @JSExport // AjaxServer.js, remove when possible
  def normalizeSerializedHtml(s: String): String = s.normalizeSerializedHtml

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
  }

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
