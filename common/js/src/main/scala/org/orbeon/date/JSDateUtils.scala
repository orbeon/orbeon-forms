/**
  * Copyright (C) 2019 Orbeon, Inc.
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
package org.orbeon.date

import cats.syntax.option._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._

import scala.scalajs.js
import scala.util.Try


object JSDateUtils {

  case class IsoTime(
    hour   : Int,
    minute : Int,
    second : Option[Int],
//    millis : Option[Int]
  ) {
    def toIsoString: String =
      formatTime(
        this,
        "[H]:[m]:[s]"
      )
  }

  // Parse as a local date (see https://stackoverflow.com/a/33909265/5295)
  def parseIsoDateUsingLocalTimezone(dateString: String): Option[js.Date] = {

    // Use `substring` to trim potential timezone
    val beforeChrist = dateString.startsWith("-")
    val dateLength   = if (beforeChrist) 11 else 10
    val dateTrimmed  = dateString.substring(0, dateLength)

    Try {
      val List(yearPart, monthPart, dayPart) = dateTrimmed.splitTo[List]("-")
      new js.Date(
        year    = yearPart  .toInt * (if (beforeChrist) -1 else 1),
        month   = monthPart .toInt - 1,
        date    = dayPart   .toInt,
        hours   = 0,
        minutes = 0,
        seconds = 0,
        ms      = 0
      )
    }.toOption.filter(parsedDate =>
      // We want return `None` for "2021-11-31", as November has 30 days, but instead of failing,
      // `new Date(2021, 10, 31)` returns December 1. To detect this case, we can convert the parsed
      // date back to a string and check that string is the same our input.
      dateToIsoStringUsingLocalTimezone(parsedDate) == dateTrimmed
    )
  }

  def findMagicTimeAsIsoTime(magicTime: String): Option[IsoTime] =
    magicTime.some.map(_.trimAllToEmpty) collect {
      case "now"              =>
        jsDateToIsoTimeUsingLocalTimezone(new js.Date(), millis = false)
      case MagicTimeRe(h, m, s, amPm)
        if h.toInt >=0 && h.toInt < 24  &&
          ((m eq null) || m.toInt >= 0 && m.toInt <= 59) &&
          ((s eq null) || s.toInt >= 0 && s.toInt <= 59) =>

        // TODO: handle 24:00:00?

        val hInt = h.toInt
        val mInt = if (m eq null) 0 else m.toInt
        val isAm = Option(amPm).exists(_.startsWith("a"))
        val isPm = Option(amPm).exists(_.startsWith("p"))

        val hh =
          if (isAm)
            hInt % 12
          else if (isPm && hInt < 12)
            hInt + 12
          else
            hInt

        IsoTime(hh, mInt, Option(s).map(_.toInt))
    }

  def dateToIsoStringUsingLocalTimezone(date: js.Date): String =
    date.getFullYear.toString + '-' + pad2(date.getMonth + 1) + '-' + pad2(date.getDate)

  def jsDateToIsoTimeUsingLocalTimezone(time: js.Date, millis: Boolean): IsoTime =
    IsoTime(time.getHours.toInt, time.getMinutes.toInt, time.getSeconds.toInt.some) // , millis option time.getMilliseconds.toInt

  // Supported format only:
  //
  // - `[h]:[m]:[s] [P]`    : e.g. 2:05:12 p.m. with dots in a.m. and p.m.
  // - `[h]:[m] [P]`        : e.g. 2:05 p.m.    [SINCE Orbeon Forms 2020.1]
  // - `[h]:[m]:[s] [P,2-2]`: e.g. 2:05:12 pm   without dots in am and pm
  // - `[h]:[m] [P,2-2]`    : e.g. 2:05 pm      [SINCE Orbeon Forms 2020.1]
  // - `[H]:[m]:[s]`        : e.g. 14:05:12
  // - `[H]:[m]`            : e.g. 14:05        (without seconds)
  //
  // TODO: Would be nice to:
  //
  // 1. validate the format
  // 2. support richer formatting, including milliseconds
  //
  def formatTime(time: IsoTime, formatInputTime: String): String = {

    val hours =
      if (formatInputTime.contains("[H]"))
        pad2(time.hour)
      else
        time.hour match {
          case 0 | 12 => 12
          case other  => other % 12
        }

    val minutes =
      pad2(time.minute)

    val secondsOpt =
      formatInputTime.contains("[s]") option pad2(time.second.getOrElse(0): Int)

    val amPmOpt =
      formatInputTime.contains("[P") option {
        if (formatInputTime.endsWith("-2]"))
          if (time.hour < 12) "am" else "pm"
        else
          if (time.hour < 12) "a.m." else "p.m."
      }

    ((hours.toString :: minutes :: secondsOpt.toList).mkString(":") :: amPmOpt.toList).mkString(" ")
  }

  private val MagicTimeRe = """(\d{1,2})(?::(\d{1,2})(?::(\d{1,2}))?)?(?:\s*(p|pm|p\.m\.|a|am|a\.m\.))?""".r

  private def pad2(n: Double): String =
    if (n < 10)
      "0" + n
    else
      n.toString
}
