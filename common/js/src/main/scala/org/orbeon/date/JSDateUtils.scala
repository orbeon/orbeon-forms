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

import org.orbeon.oxf.util.StringUtils._

import scala.scalajs.js
import scala.util.Try


object JSDateUtils {

  // Parse as a local date (see https://stackoverflow.com/a/33909265/5295)
  def isoDateToStringUsingLocalTimezone(dateString: String): Option[js.Date] = {

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
      dateToISOStringUsingLocalTimezone(parsedDate) == dateTrimmed
    )
  }

  def dateToISOStringUsingLocalTimezone(date: js.Date): String = {

    def pad2(n: Double): String =
      if (n < 10)
        "0" + n
      else
        n.toString

    date.getFullYear.toString + '-' + pad2(date.getMonth + 1) + '-' + pad2(date.getDate)
  }
}
