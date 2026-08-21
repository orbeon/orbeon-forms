package org.orbeon.web

import org.orbeon.web.JSDateUtils.*
import org.scalatest.funspec.AnyFunSpecLike

import scala.scalajs.js

class JSDateUtilsTest extends AnyFunSpecLike {

  // In the list of strings, the first strings is the canonical form, that is the one we expect when
  // the `js.Date` is converted to a string.
  val StringsToDates = List(
    List("2032-05-29"                    ) -> Some(new js.Date(js.Date.UTC(2032,  4, 29, 0, 0, 0, 0))),
    List("2032-12-31"                    ) -> Some(new js.Date(js.Date.UTC(2032, 11, 31, 0, 0, 0, 0))),
    List("2019-01-01", "2019-01-01-08:00") -> Some(new js.Date(js.Date.UTC(2019,  0,  1, 0, 0, 0, 0))),
    List("2021-13-01"                    ) -> None, // There is no month 13
    List("2021-11-32"                    ) -> None, // There is no day 32
    List("2021-11-31"                    ) -> None  // November has 30 days
  )

  describe("ISO string conversion to JavaScript date") {
    for {
      (isoStrings, expectedDate) <- StringsToDates
      isoString                  <- isoStrings
    } locally {
      it(s"must pass for `$isoString`") {
        val actual   = parseIsoDateAsUtcDate(isoString).map(_.getTime())
        val expected = expectedDate.map(_.getTime())
        assert(actual === expected)
      }
    }
  }

  describe("JavaScript date conversion to ISO string") {
    for {
      (isoStrings, dateOpt) <- StringsToDates
      expectedString        = isoStrings.head
      date                  <- dateOpt
    } locally {
      it(s"must pass for `$expectedString`") {
        assert(dateToIsoStringAsUtcDate(date) === expectedString)
      }
    }
  }

  describe("Roundtrip with current date") {
    it(s"must return the same result") {

      val current = new js.Date()
      val currentUtc = new js.Date(js.Date.UTC(current.getFullYear().toInt, current.getMonth().toInt, current.getDate().toInt, 0, 0, 0, 0))

      assert(parseIsoDateAsUtcDate(dateToIsoStringAsUtcDate(currentUtc)) map (_.getTime()) contains currentUtc.getTime())
    }
  }

  describe("findTimezoneShortName()/findDateOffsetInMinutes()") {

    // This test relies on the fact that we pass a `TZ` environment variable set to "America/Los_Angeles" to
    // `JSDOMNodeJSEnv` in build.sbt. The offsets in the date determine an instant, but do not influence the
    // short timezone name or the offset of the result.
    val Expected = List(
      ("2026-11-05T12:00:00Z",      "PST", -480),
      ("2026-06-01T12:00:00Z",      "PDT", -420),
      ("2026-01-01T11:17:46-08:00", "PST", -480),
      ("2026-06-01T11:17:46-08:00", "PDT", -420),
    )

    for ((isoDateTimeString, expectedShortName, expectedOffset) <- Expected) {
      it(s"must return the short timezone name for a given date for $isoDateTimeString") {
        val date = new js.Date(isoDateTimeString)
        val shortName = JSDateUtils.findTimezoneShortName(date)
        assert(shortName.contains(expectedShortName))

        val offsetMinutes = JSDateUtils.findDateOffsetInMinutes(date)
        assert(offsetMinutes.contains(expectedOffset))
      }
    }
  }
}
