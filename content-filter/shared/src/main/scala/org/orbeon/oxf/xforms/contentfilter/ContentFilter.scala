package org.orbeon.oxf.xforms.contentfilter

import org.orbeon.oxf.util.MarkupUtils.*
import org.orbeon.oxf.util.StringUtils.OrbeonStringOps


object ContentFilter {

  private lazy val defaultFilter: ProfanityFilter = ProfanityFilter.defaultFilter()

  def isContentClean(
    text     : String,
    language : String = "",
    extension: Map[String, AnyRef] = Map.empty
  ): Boolean =
    ! defaultFilter.containsProfanity(text)

  def checkContent(
    text     : String,
    language : String = "",
    extension: Map[String, AnyRef] = Map.empty
  ): List[ProfanityMatch] =
    defaultFilter.findAllMatches(text)

  def highlightContent(
    text     : String,
    language : String = "",
    extension: Map[String, AnyRef] = Map.empty
  ): String = {
    if (text.isAllBlank) {
      text
    } else {
      val matches = defaultFilter.findAllMatches(text)
      if (matches.nonEmpty) {
        val sortedMatches = matches.sortBy(_.start)
        val sb = new java.lang.StringBuilder
        var lastPos = 0

        for (m <- sortedMatches) {
          if (m.start >= lastPos && m.start <= text.length && m.end <= text.length && m.end >= m.start) {
            if (m.start > lastPos)
              sb.append(text.substring(lastPos, m.start).escapeXmlMinimal)
            sb.append("<mark class=\"fr-content-filter-highlight\">")
            sb.append(text.substring(m.start, m.end).escapeXmlMinimal)
            sb.append("</mark>")
            lastPos = m.end
          }
        }
        if (lastPos < text.length)
          sb.append(text.substring(lastPos).escapeXmlMinimal)
        if (text.endsWith("\n"))
          sb.append(" ")
        sb.toString
      } else {
        val escaped = text.escapeXmlMinimal
        if (text.endsWith("\n")) escaped + " " else escaped
      }
    }
  }
}
