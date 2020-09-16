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

import java.time.Instant

import org.scalatest.funspec.AnyFunSpec


class DateUtilsTest extends AnyFunSpec {

  describe("RFC1123 date parsing") {
    for (value <- List("Tue, 29 May 2012 19:35:04 GMT")) // "Tuesday, 29-May-12 19:35:04 GMT", "Tue May 29 19:35:04 2012"
      it(s"must parse `$value` in all formats") {
        assert(1338320104000L === DateUtils.parseRFC1123(value))
      }
  }

  describe("Date formatting") {

    val data = List(
      ("1970-01-01T00:00:00.000Z"     , "DateTime",    DateUtils.DateTime   , 0L),
      ("Thu, 01 Jan 1970 00:00:00 GMT", "RFC1123Date", DateUtils.RFC1123Date, 0L),
      ("2012-05-29T19:35:04.123Z"     , "DateTime",    DateUtils.DateTime   , 1338320104123L),
      ("Tue, 29 May 2012 19:35:04 GMT", "RFC1123Date", DateUtils.RFC1123Date, 1338320104123L)
    )

    for ((expected, formatterName, formatter, instant) <- data)
      it(s"must format instant `$instant` using formatter `$formatterName`") {
        assert(expected === formatter.format(Instant.ofEpochMilli(instant)))
      }
  }
}
