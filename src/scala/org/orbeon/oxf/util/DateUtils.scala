/**
 * Copyright (C) 2012 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.joda.time.format.{DateTimeFormatter, DateTimeFormat}
import java.util.Locale
import org.joda.time.DateTimeZone


object DateUtils {

    // ISO 8601 formats without timezones
    // From the doc: "DateTimeFormat is thread-safe and immutable, and the formatters it returns are as well."
    val XsDateTimeLong = withLocaleTZ(DateTimeFormat forPattern "yyyy-MM-dd'T'HH:mm:ss.SSS")
    val XsDateTime     = withLocaleTZ(DateTimeFormat forPattern "yyyy-MM-dd'T'HH:mm:ss")
    val XsDate         = withLocaleTZ(DateTimeFormat forPattern "yyyy-MM-dd")

    // RFC 1123 format
    val RFC1123Date    = withLocaleTZ(DateTimeFormat forPattern "EEE, dd MMM yyyy HH:mm:ss 'GMT'")

    private def withLocaleTZ(format: DateTimeFormatter) = format withLocale Locale.US withZone DateTimeZone.UTC

    // Parse a date in one of the 3 ISO formats above
    // Throws IllegalArgumentException if the date format is not known
    def parse(date: String): Long =
        if (date.length == 10)
            XsDate.parseDateTime(date).getMillis
        else if (date.indexOf('.') > 0)
            XsDateTimeLong.parseDateTime(date).getMillis
        else
            XsDateTime.parseDateTime(date).getMillis

    // Parse an RFC 1123 dateTime
    def parseRFC1123(date: String): Long = RFC1123Date.parseDateTime(date).getMillis

    // Format the given instant
    def format(instant: Long, format: DateTimeFormatter): String = format.print(instant)
}
