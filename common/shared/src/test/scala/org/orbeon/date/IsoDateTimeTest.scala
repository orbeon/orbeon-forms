package org.orbeon.date

import org.scalatest.funspec.AnyFunSpec

class IsoDateTimeTest extends AnyFunSpec {

  describe("DateTimeFormat parsing") {

    it("should parse a valid date and time format") {
      val formatString = "[M01]/[D01]/[Y] [h]:[m]:[s] [PN,*-2] [ZN]"
      val expected = DateTimeFormat(
        dateFormat     = DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = true),
        timeFormat     = TimeFormat(is24Hour = false, isPadHourDigits = false, hasSeconds = true, AmPmFormat.Upper),
        separator      = " ",
        dateFirst      = true,
        timezoneFormat = Some(TimezoneFormat.ShortName)
      )

      val result = IsoDateTime.parseFormat(formatString)
      assert(result == expected)
    }

    it("should parse time first then date") {
      val formatString = "[H]:[m]:[s] at [D].[M].[Y] [z]"
      val expected = DateTimeFormat(
        dateFormat     = DateFormat(DateFormatComponent.Day, ".", isPadDayMonthDigits = false),
        timeFormat     = TimeFormat(is24Hour = true, isPadHourDigits = false, hasSeconds = true, AmPmFormat.None),
        separator      = " at ",
        dateFirst      = false,
        timezoneFormat = Some(TimezoneFormat.OffsetWithGmt)
      )

      val result = IsoDateTime.parseFormat(formatString)
      assert(result == expected)
    }

    it("should parse format without timezone") {
      val formatString = "[D]-[M]-[Y] [H01]:[m]"
      val expected = DateTimeFormat(
        dateFormat     = DateFormat(DateFormatComponent.Day, "-", isPadDayMonthDigits = false),
        timeFormat     = TimeFormat(is24Hour = true, isPadHourDigits = true, hasSeconds = false, AmPmFormat.None),
        separator      = " ",
        dateFirst      = true,
        timezoneFormat = None
      )

      val result = IsoDateTime.parseFormat(formatString)
      assert(result == expected)
    }
  }

  describe("IsoDateTime formatting") {

    it("should format date time with UpperZ timezone") {
      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(120))
      val fmt = IsoDateTime.parseFormat("[Y]-[M01]-[D01] [H01]:[m]:[s] [Z]")
      assert(IsoDateTime.formatDateTime(dt, fmt) == "2023-11-05 14:30:45 +02:00")
    }

    it("should format date time with UpperZ timezone at UTC") {
      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(0))
      val fmt = IsoDateTime.parseFormat("[Y]-[M01]-[D01] [H01]:[m]:[s] [Z]")
      assert(IsoDateTime.formatDateTime(dt, fmt) == "2023-11-05 14:30:45 Z")
    }

//    it("should format date time with UpperZN timezone") {
//      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(-330))
//      val fmt = IsoDateTime.parseFormat("[Y]-[M01]-[D01] [H01]:[m]:[s] [ZN]")
//      assert(IsoDateTime.formatDateTime(dt, fmt) == "2023-11-05 14:30:45 -0530")
//    }

    it("should format date time with LowerZ timezone") {
      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(120))
      val fmt = IsoDateTime.parseFormat("[Y]-[M01]-[D01] [H01]:[m]:[s] [z]")
      assert(IsoDateTime.formatDateTime(dt, fmt) == "2023-11-05 14:30:45 GMT+02:00")
    }

    it("should format date time with LowerZ timezone at UTC") {
      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(0))
      val fmt = IsoDateTime.parseFormat("[Y]-[M01]-[D01] [H01]:[m]:[s] [z]")
      assert(IsoDateTime.formatDateTime(dt, fmt) == "2023-11-05 14:30:45 UTC")
    }

    it("should format time first without timezone") {
      val dt = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, None), None)
      val fmt = IsoDateTime.parseFormat("[h]:[m] [P,*-2] on [M01]/[D01]/[Y]")
      assert(IsoDateTime.formatDateTime(dt, fmt) == "2:30 pm on 11/05/2023")
    }
  }

  describe("IsoDateTime.tryParseIsoDateTime") {

    it("should parse a valid ISO date time with timezone offset") {
      val parsed = IsoDateTime.tryParseIsoDateTime("2023-11-05T14:30:45+02:00")
      assert(parsed.isSuccess)
      assert(parsed.get == IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(120)))
    }

    it("should parse a valid ISO date time without timezone offset") {
      val parsed = IsoDateTime.tryParseIsoDateTime("2023-11-05T14:30:00")
      assert(parsed.isSuccess)
      assert(parsed.get == IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(0)), None))
    }

    it("should parse a valid ISO date time with UTC timezone (Z)") {
      val parsed = IsoDateTime.tryParseIsoDateTime("2023-11-05T14:30:45Z")
      assert(parsed.isSuccess)
      assert(parsed.get == IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(0)))
    }

    it("should fail to parse an invalid ISO date time") {
      val parsed = IsoDateTime.tryParseIsoDateTime("invalid")
      assert(parsed.isFailure)
    }
  }

  describe("IsoDateTime.adjustTo") {

    it("should adjust time correctly when changing timezone offsets") {
      // Original: 2023-11-05T14:30:45+02:00 (12:30:45 UTC)
      val original = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(120))
      
      // Adjust to: -05:00 (-300 mins) (07:30:45 -05:00)
      val expected = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(7, 30, Some(45)), Some(-300))
      assert(original.adjustTo(-300) == expected)
    }

    it("should adjust date and time when the adjustment crosses the day boundary backwards") {
      // Original: 2023-11-05T01:30:00+02:00 (Nov 4th 23:30:00 UTC)
      val original = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(1, 30, Some(0)), Some(120))
      
      // Adjust to: -05:00 (-300 mins) (Nov 4th 18:30:00 -05:00)
      val expected = IsoDateTime(IsoDate(2023, 11, 4), IsoTime(18, 30, Some(0)), Some(-300))
      assert(original.adjustTo(-300) == expected)
    }

    it("should adjust date and time when the adjustment crosses the day boundary forwards") {
      // Original: 2023-11-05T23:30:00-02:00 (Nov 6th 01:30:00 UTC)
      val original = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(23, 30, Some(0)), Some(-120))
      
      // Adjust to: +05:00 (300 mins) (Nov 6th 06:30:00 +05:00)
      val expected = IsoDateTime(IsoDate(2023, 11, 6), IsoTime(6, 30, Some(0)), Some(300))
      assert(original.adjustTo(300) == expected)
    }

    it("should simply assign the new offset when the original datetime has no offset") {
      // Original: 2023-11-05T14:30:45 without an offset
      val original = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), None)
      
      // Adjust to: -05:00 (-300 mins) -> should retain the same date/time and add offset
      val expected = IsoDateTime(IsoDate(2023, 11, 5), IsoTime(14, 30, Some(45)), Some(-300))
      assert(original.adjustTo(-300) == expected)
    }
  }
}
