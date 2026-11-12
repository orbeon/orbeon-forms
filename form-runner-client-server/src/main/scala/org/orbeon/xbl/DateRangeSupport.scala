/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.date.IsoDate
import org.orbeon.oxf.util.StringUtils.*

import java.time.LocalDate
import scala.util.Try


object DateRangeSupport {

  private def escapePart(part: String): String =
    part.replace("%", "%25").replace("/", "%2F")

  private def unescapePart(part: String): String = {
    val sb  = new java.lang.StringBuilder(part.length)
    var i   = 0
    val len = part.length
    while (i < len) {
      val c = part.charAt(i)
      if (c == '%' && i + 2 < len) {
        val hex = part.substring(i + 1, i + 3).toUpperCase
        hex match {
          case "2F" => sb.append('/'); i += 3
          case "25" => sb.append('%'); i += 3
          case _    => sb.append(c);   i += 1
        }
      } else {
        sb.append(c)
        i += 1
      }
    }
    sb.toString
  }

  def combineStartEnd(startRaw: String, endRaw: String): String = {
    val s = Option(startRaw).map(_.trimAllToEmpty).getOrElse("")
    val e = Option(endRaw).map(_.trimAllToEmpty).getOrElse("")
    if (s.isEmpty && e.isEmpty)
      ""
    else
      s"${escapePart(s)}/${escapePart(e)}"
  }

  def splitStartEnd(raw: String): (String, String) =
    Option(raw).map(_.trimAllToEmpty).getOrElse("") match {
      case "" =>
        ("", "")
      case s =>
        val slashIdx = s.indexOf('/')
        if (slashIdx < 0)
          (unescapePart(s), "")
        else
          (unescapePart(s.substring(0, slashIdx)), unescapePart(s.substring(slashIdx + 1)))
    }

  def startPart(raw: String): String = splitStartEnd(raw)._1
  def endPart(raw: String)  : String = splitStartEnd(raw)._2

  def parseDateRange(raw: String): Option[(IsoDate, IsoDate)] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      val (sPart, ePart) = splitStartEnd(trimmed)
      if (sPart.isEmpty || ePart.isEmpty)
        None
      else
        for {
          startDate <- IsoDate.tryParseLocalIsoDate(sPart).toOption
          endDate   <- IsoDate.tryParseLocalIsoDate(ePart).toOption
        } yield (startDate, endDate)
    }

  def toLocalDate(isoDate: IsoDate): LocalDate =
    LocalDate.of(isoDate.year, isoDate.month, isoDate.day)

  def startIsoDateOpt(raw: String): Option[IsoDate] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      val (sPart, _) = splitStartEnd(trimmed)
      if (sPart.isEmpty) None else IsoDate.tryParseLocalIsoDate(sPart).toOption
    }

  def endIsoDateOpt(raw: String): Option[IsoDate] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      val (_, ePart) = splitStartEnd(trimmed)
      if (ePart.isEmpty) None else IsoDate.tryParseLocalIsoDate(ePart).toOption
    }

  def daysBetweenOpt(raw: String): Option[Long] =
    parseDateRange(raw).map { case (start, end) =>
      toLocalDate(end).toEpochDay - toLocalDate(start).toEpochDay
    }

  def parseNonNegativeLongOpt(raw: String): Option[Long] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap(s => Try(s.toLong).toOption).filter(_ >= 0L)

  def isValidDateRange(raw: String, minDaysParam: String, maxDaysParam: String): Boolean =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        true
      case Some(trimmed) =>
        daysBetweenOpt(trimmed) match {
          case None =>
            false
          case Some(days) if days < 0L =>
            false
          case Some(days) =>
            val minOk = parseNonNegativeLongOpt(minDaysParam).forall(days >= _)
            val maxOk = parseNonNegativeLongOpt(maxDaysParam).forall(days <= _)
            minOk && maxOk
        }
    }

  def formatReadonlyModeDateRange(raw: String, formatString: String): String =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        ""
      case Some(trimmed) =>
        parseDateRange(trimmed) match {
          case Some((start, end)) =>
            val fmt = IsoDate.parseFormat(formatString)
            s"${IsoDate.formatDate(start, fmt)} – ${IsoDate.formatDate(end, fmt)}"
          case None =>
            trimmed
        }
    }
}

object DateRangeSupportJava {

  //@XPathFunction
  def combineStartEnd(startRaw: String, endRaw: String): String =
    DateRangeSupport.combineStartEnd(startRaw, endRaw)

  //@XPathFunction
  def startPart(raw: String): String =
    DateRangeSupport.startPart(raw)

  //@XPathFunction
  def endPart(raw: String): String =
    DateRangeSupport.endPart(raw)

  //@XPathFunction
  def isValidDateRange(raw: String, minDaysParam: String, maxDaysParam: String): Boolean =
    DateRangeSupport.isValidDateRange(raw, minDaysParam, maxDaysParam)

  //@XPathFunction
  def formatReadonlyModeDateRange(raw: String, formatString: String): String =
    DateRangeSupport.formatReadonlyModeDateRange(raw, formatString)
}
