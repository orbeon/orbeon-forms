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

        assert(0L === DateUtils.parse("1970-01-01T00:00:00.000"))
        assert(0L === DateUtils.parse("1970-01-01T00:00:00"))
        assert(0L === DateUtils.parse("1970-01-01"))

        assert(1338320104123L === DateUtils.parse("2012-05-29T19:35:04.123"))
        assert(1338320104000L === DateUtils.parse("2012-05-29T19:35:04"))
        assert(1338249600000L === DateUtils.parse("2012-05-29"))

        assert(1338320104000L === DateUtils.parseRFC1123("Tue, 29 May 2012 19:35:04 GMT"))
    }

    @Test def format(): Unit = {
        assert("1970-01-01T00:00:00.000"        === DateUtils.format(0L, DateUtils.XsDateTimeLong))
        assert("1970-01-01T00:00:00"            === DateUtils.format(0L, DateUtils.XsDateTime))
        assert("1970-01-01"                     === DateUtils.format(0L, DateUtils.XsDate))
        assert("Thu, 01 Jan 1970 00:00:00 GMT"  === DateUtils.format(0L, DateUtils.RFC1123Date))

        assert("2012-05-29T19:35:04.123"        === DateUtils.format(1338320104123L, DateUtils.XsDateTimeLong))
        assert("2012-05-29T19:35:04"            === DateUtils.format(1338320104123L, DateUtils.XsDateTime))
        assert("2012-05-29"                     === DateUtils.format(1338320104123L, DateUtils.XsDate))
        assert("Tue, 29 May 2012 19:35:04 GMT"  === DateUtils.format(1338320104123L, DateUtils.RFC1123Date))
    }
}
