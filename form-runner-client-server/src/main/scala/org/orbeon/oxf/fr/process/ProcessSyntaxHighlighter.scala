/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.process

import org.orbeon.oxf.util.MarkupUtils.*


object ProcessSyntaxHighlighter {

  private val Keywords: Set[String] = Set("then", "recover", "if", "else")

  private def isNameStart(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-' || c == '_'

  private def isNameAfter(c: Char): Boolean =
    isNameStart(c) || (c >= '0' && c <= '9') || c == ':'

  private def isPunctuation(c: Char): Boolean =
    c == '(' || c == ')' || c == ',' || c == '='

  private def appendSpan(sb: StringBuilder, tokenClass: String, text: String): Unit = {
    sb.append("<span class=\"")
    sb.append(tokenClass)
    sb.append("\">")
    sb.append(text.escapeXmlMinimal)
    sb.append("</span>")
  }

  // Highlight a formatted process string into HTML `<span>` tokens.
  // Never throws on malformed/partial input; preserves all whitespace and newlines.
  def highlightToHtml(source: String): String = {
    if (source == null || source.isEmpty)
      return ""

    val len = source.length
    val sb  = new StringBuilder(len * 2)
    var i   = 0

    while (i < len) {
      val c = source.charAt(i)

      if (c == '"' || c == '\'') {
        val quote = c
        val start = i
        i += 1
        while (i < len && source.charAt(i) != quote) {
          if (source.charAt(i) == '\\' && i + 1 < len)
            i += 2
          else
            i += 1
        }
        if (i < len && source.charAt(i) == quote)
          i += 1
        appendSpan(sb, "fr-proc-tok-string", source.substring(start, i))
      } else if (isNameStart(c)) {
        val start = i
        i += 1
        while (i < len && isNameAfter(source.charAt(i)))
          i += 1
        val word = source.substring(start, i)

        // Look ahead past horizontal whitespace to see if this identifier is a parameter name (`param = ...`)
        var look = i
        while (look < len && (source.charAt(look) == ' ' || source.charAt(look) == '\t'))
          look += 1
        val followedByEquals = look < len && source.charAt(look) == '='

        val tokenClass =
          if (followedByEquals)
            "fr-proc-tok-param"
          else if (Keywords(word))
            "fr-proc-tok-keyword"
          else
            "fr-proc-tok-action"

        appendSpan(sb, tokenClass, word)
      } else if (isPunctuation(c)) {
        appendSpan(sb, "fr-proc-tok-punct", c.toString)
        i += 1
      } else {
        val start = i
        i += 1
        while (i < len && {
          val ch = source.charAt(i)
          ch != '"' && ch != '\'' && ! isNameStart(ch) && ! isPunctuation(ch)
        }) {
          i += 1
        }
        sb.append(source.substring(start, i).escapeXmlMinimal)
      }
    }

    sb.toString
  }
}
