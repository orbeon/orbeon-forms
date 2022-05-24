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

import org.orbeon.date.JSDateUtils._
import org.scalatest.funspec.AnyFunSpec

import scala.scalajs.js

class JSDateUtilsTest extends AnyFunSpec {

  // In the list of strings, the first strings is the canonical form, that is the one we expect when
  // the `js.Date` is converted to a string.
  val StringsToDates = List(
    List("2032-05-29"                    ) -> Some(new js.Date(2032,  4, 29, 0, 0, 0, 0)),
    List("2032-12-31"                    ) -> Some(new js.Date(2032, 11, 31, 0, 0, 0, 0)),
    List("2019-01-01", "2019-01-01-08:00") -> Some(new js.Date(2019,  0,  1, 0, 0, 0, 0)),
    List("2021-13-01"                    ) -> None, // There is no month 13
    List("2021-11-32"                    ) -> None, // There is no day 32
    List("2021-11-31"                    ) -> None  // November has 30 days
  )

  describe("ISO string conversion to JavaScript date") {
    for {
      (isoStrings, expectedDate) <- StringsToDates
      isoString                  <- isoStrings
    }
      it(s"must pass for `$isoString`") {
        val actual   = parseIsoDateUsingLocalTimezone(isoString).map(_.getTime)
        val expected = expectedDate.map(_.getTime)
        assert(actual === expected)
      }
  }

  describe("JavaScript date conversion to ISO string") {
    for {
      (isoStrings, dateOpt) <- StringsToDates
      expectedString        = isoStrings.head
      date                  <- dateOpt
    }
      it(s"must pass for `$expectedString`") {
        assert(dateToIsoStringUsingLocalTimezone(date) === expectedString)
      }
  }

  describe("Roundtrip with current date") {
    it(s"must return the same result") {

      val current = new js.Date()

      current.setHours(0)
      current.setMinutes(0)
      current.setSeconds(0)
      current.setMilliseconds(0)

      assert(parseIsoDateUsingLocalTimezone(dateToIsoStringUsingLocalTimezone(current)) map (_.getTime) contains current.getTime)
    }
  }
}
