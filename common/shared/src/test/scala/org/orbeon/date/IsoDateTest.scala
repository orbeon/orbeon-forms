package org.orbeon.date

import org.scalatest.funspec.AnyFunSpec

class IsoDateTest extends AnyFunSpec {

  case class DateTestCase(format: DateFormat, input: String, expected: Option[IsoDate])

  describe("Magic date parsing") {

    val testCurrentDate = IsoDate(2024, 1, 15)

    def assertDateParsing(testCases: List[DateTestCase]): Unit = {
      for (DateTestCase(format, input, expected) <- testCases) {
        val result = IsoDate.findMagicDateAsIsoDateWithNow(format, input, testCurrentDate)
        expected match {
          case Some(expectedDate) =>
            assert(result.contains(expectedDate), s"Failed for format: ${format.generateFormatString}, input: $input")
          case None =>
            assert(result.isEmpty, s"Should have failed for invalid input: $input")
        }
      }
    }

    it("must parse most usual date formats") {
      assertDateParsing(List(
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = false), "today",      Some(testCurrentDate)),
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = false), "1/15/2024",  Some(IsoDate(2024, 1, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = false), "01/15/2024", Some(IsoDate(2024, 1, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = false), "15/1/2024",  Some(IsoDate(2024, 1, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = false), "15/01/2024", Some(IsoDate(2024, 1, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Year,  "-", isPadDayMonthDigits = true),  "2024-01-15", Some(IsoDate(2024, 1, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Day,   ".", isPadDayMonthDigits = false), "15.1.2024",  Some(IsoDate(2024, 1, 15)))
      ))
    }

    it("must parse dates without separators") {
      assertDateParsing(List(
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "01012000",   Some(IsoDate(2000, 1, 1))),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "31122023",   Some(IsoDate(2023, 12, 31))),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "15062024",   Some(IsoDate(2024, 6, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = true),  "01012000",   Some(IsoDate(2000, 1, 1))),
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = true),  "12312023",   Some(IsoDate(2023, 12, 31))),
        DateTestCase(DateFormat(DateFormatComponent.Month, "/", isPadDayMonthDigits = true),  "06152024",   Some(IsoDate(2024, 6, 15))),
        DateTestCase(DateFormat(DateFormatComponent.Year,  "-", isPadDayMonthDigits = true),  "20000101",   Some(IsoDate(2000, 1, 1))),
        DateTestCase(DateFormat(DateFormatComponent.Year,  "-", isPadDayMonthDigits = true),  "20231231",   Some(IsoDate(2023, 12, 31))),
        DateTestCase(DateFormat(DateFormatComponent.Year,  "-", isPadDayMonthDigits = true),  "20240615",   Some(IsoDate(2024, 6, 15)))
      ))
    }

    it("must handle invalid dates") {
      assertDateParsing(List(
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "00012000",   None), // day cannot be 0
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "32012000",   None), // day cannot be 32
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "01132000",   None), // month cannot be 13
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "29022023",   None), // February 29, 2023 doesn't exist (not a leap year)
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = false), "01012000",   None),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = false), "31122023",   None),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = false), "15062024",   None),
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "0101200",    None), // 7 digits
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "010120001",  None), // 9 digits
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "0101",       None), // 4 digits
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "abcdefgh",   None), // not digits
        DateTestCase(DateFormat(DateFormatComponent.Day,   "/", isPadDayMonthDigits = true),  "0101200a",   None)  // contains letter
      ))
    }
  }
}