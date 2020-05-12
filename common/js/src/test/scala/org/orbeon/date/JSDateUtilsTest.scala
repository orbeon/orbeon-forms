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

  val StringsToDates = List(
    "2032-05-29" -> new js.Date(2032,  4, 29, 0, 0, 0, 0),
    "2032-12-31" -> new js.Date(2032, 11, 31, 0, 0, 0, 0),
    "2019-01-01" -> new js.Date(2019,  0,  1, 0, 0, 0, 0)
  )

  describe("ISO string conversion to JavaScript date") {
    for ((isoString, expectedDate) <- StringsToDates)
      it(s"must pass for `$isoString`") {
        assert(isoDateToStringUsingLocalTimezone(isoString) map (_.getTime) contains expectedDate.getTime)
      }
  }

  describe("JavaScript date conversion to ISO string") {
    for ((date, expectedString) <- StringsToDates map (t => t._2 -> t._1))
      it(s"must pass for `$expectedString`") {
        assert(dateToISOStringUsingLocalTimezone(date) === expectedString)
      }
  }

  describe("Roundtrip with current date") {
    it(s"must return the same result") {

      val current = new js.Date()

      current.setHours(0)
      current.setMinutes(0)
      current.setSeconds(0)
      current.setMilliseconds(0)

      assert(isoDateToStringUsingLocalTimezone(dateToISOStringUsingLocalTimezone(current)) map (_.getTime) contains current.getTime)
    }
  }
}
