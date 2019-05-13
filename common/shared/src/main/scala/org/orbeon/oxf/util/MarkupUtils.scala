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

import java.{lang ⇒ jl}

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("ORBEON.common.MarkupUtils")
object MarkupUtils {

  @JSExport
  def escapeXMLMinimal(s: String): String =
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

  @JSExport
  def escapeXMLForAttribute(s: String): String =
    replace(
      escapeXMLMinimal(s),
      "\"",
      "&quot;"
    )

  def unescapeXMLMinimal(s: String): String =
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

  private val CrRegex = """\r""".r

  @JSExport
  def normalizeSerializedHTML(s: String): String =
    CrRegex.replaceAllIn(s, "")

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
