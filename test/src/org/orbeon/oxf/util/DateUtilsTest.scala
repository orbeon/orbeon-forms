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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test


class DateUtilsTest extends AssertionsForJUnit {
    @Test def parse(): Unit = {

        val utcTimezones = Seq("+00:00", "-00:00", "Z")
        def appendUTCTimezones(value: (String, Long)) = utcTimezones map (suffix ⇒ (value._1 + suffix, value._2))

        val valuesNoExplicitTz = Seq(
            "1970-01-01T00:00:00.000" → 0L,
            "1970-01-01T00:00:00"     → 0L,
            "1970-01-01"              → 0L,
            "2012-05-29T19:35:04.123" → 1338320104123L,
            "2012-05-29T19:35:04"     → 1338320104000L,
            "2012-05-29"              → 1338249600000L
        )

        // Values with an explicit timezone
        val valuesWithExplicitTz = valuesNoExplicitTz flatMap appendUTCTimezones

        // How many minutes to add to UTC time to get the local time
        val defaultTzOffsetMs = DateUtils.DefaultOffsetMinutes * 60 * 1000

        for ((value, expected) ← valuesNoExplicitTz)
            assert(expected - defaultTzOffsetMs === DateUtils.parse(value))

        for ((value, expected) ← valuesWithExplicitTz)
            assert(expected === DateUtils.parse(value))

        assert(1338320104000L === DateUtils.parseRFC1123("Tue, 29 May 2012 19:35:04 GMT"))
    }

    @Test def format(): Unit = {
        assert("1970-01-01T00:00:00.000Z"       === DateUtils.DateTime.print(0L))
        assert("Thu, 01 Jan 1970 00:00:00 GMT"  === DateUtils.RFC1123Date.print(0L))

        assert("2012-05-29T19:35:04.123Z"       === DateUtils.DateTime.print(1338320104123L))
        assert("Tue, 29 May 2012 19:35:04 GMT"  === DateUtils.RFC1123Date.print(1338320104123L))
    }
}
