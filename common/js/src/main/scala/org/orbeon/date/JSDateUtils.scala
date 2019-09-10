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
  def isoDateToStringUsingLocalTimezone(dateString: String): Option[js.Date] = Try {

    // Use `substring` to trim potential timezone
    val dateParts = dateString.substring(0, 10).splitTo[List]("-")

    new js.Date(
      year    = dateParts(0).toInt,
      month   = dateParts(1).toInt - 1,
      date    = dateParts(2).toInt,
      hours   = 0,
      minutes = 0,
      seconds = 0,
      ms      = 0
    )
  } toOption

  def dateToISOStringUsingLocalTimezone(date: js.Date): String = {

    def pad2(n: Int): String =
      if (n < 10) {
        "0" + n
      } else
        n.toString

    date.getFullYear.toString + '-' + pad2(date.getMonth + 1) + '-' + pad2(date.getDate)
  }
}
