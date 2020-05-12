/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.orbeon.oxf.fr.excel

import java.{util => ju}

import org.orbeon.oxf.fr.excel.ExcelDateUtils.FormatType
import org.orbeon.oxf.util.CoreUtils._
import org.scalatest.funspec.AnyFunSpecLike

// ORBEON: Moved subset of original Apache POI class to Scala.
class ExcelDateUtilsTest extends AnyFunSpecLike {

  val TimeZoneUtc   = ju.TimeZone.getTimeZone("UTC")
  val DefaultLocale = ju.Locale.getDefault

  def createCalendar(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): ju.Calendar =
    ju.Calendar.getInstance(TimeZoneUtc, DefaultLocale) |!>
      (_.set(year, month, day, hour, minute, second))   |!>
      (_.clear(ju.Calendar.MILLISECOND))

  describe("The `analyzeFormatType` function") {
    // Cell content 2016-12-8 as an example
    val Expected =
      List(
        (FormatType.Date , 14, "m/d/yy",                                                "12/8/2016"),
        (FormatType.Date ,182, "[$-F800]dddd\\,\\ mmmm\\ dd\\,\\ yyyy",                 "Thursday, December 8, 2016"),
        (FormatType.Date ,183, "m/d;@",                                                 "12/8"),
        (FormatType.Date ,184, "mm/dd/yy;@",                                            "12/08/16"),
        (FormatType.Date ,185, "[$-409]d\\-mmm\\-yy;@",                                 "8-Dec-16"),
        (FormatType.Date ,186, "[$-409]mmmmm\\-yy;@",                                   "D-16"),
        (FormatType.Date ,165, "yyyy\"\u5e74\"m\"\u6708\"d\"\u65e5\";@",                "2016年12月8日"),
        (FormatType.Date ,164, "yyyy\"\u5e74\"m\"\u6708\";@",                           "2016年12月"),
        (FormatType.Date ,168, "m\"\u6708\"d\"\u65e5\";@",                              "12月8日"),
        (FormatType.Date ,181, "[DBNum1][$-404]m\"\u6708\"d\"\u65e5\";@",               "十二月八日"),
        (FormatType.Date ,177, "[DBNum2][$-804]yyyy\"\u5e74\"m\"\u6708\"d\"\u65e5\";@", "贰零壹陆年壹拾贰月捌日"),
        (FormatType.Date , 78, "[DBNum3][$-804]yyyy\"\u5e74\"m\"\u6708\"d\"\u65e5\";@", "２０１６年１２月８日")
      )

    for ((format, formatIndex, formatString, message) <- Expected)
      it(s"`$message`") {
        assert(format == ExcelDateUtils.analyzeFormatType(formatIndex, formatString))
      }
  }

  describe("Date and dateTime formats identification") {

    describe("Builtin date formats") {

      val builtins = List(
        (FormatType.Date,     0x0e),
        (FormatType.Date,     0x0f),
        (FormatType.Date,     0x10),
        (FormatType.DateTime, 0x16),
        (FormatType.Time,     0x2d),
        (FormatType.Time,     0x2e)
      )
      for ((formatType, builtin) <- builtins) {
        it(s"`$builtin`") {
          assert(
            ExcelDateUtils.findInternalFormat(builtin) exists
              (typeFormat => formatType == ExcelDateUtils.analyzeFormatType(builtin, typeFormat._2))
          )
        }
      }
    }

    describe("Builtin non-date formats") {
      val builtins = List(0x01, 0x02, 0x17, 0x1f, 0x30)
      for (builtin <- builtins) {
        it(s"`$builtin`") {
          assert(
            ! (
              ExcelDateUtils.findInternalFormat(builtin) exists
                (typeFormat => FormatType.Other != ExcelDateUtils.analyzeFormatType(builtin, typeFormat._2))
            )
          )
        }
      }
    }

    val formatId = 60
    it(s"`$formatId` is not an internal date format") {
      assert(ExcelDateUtils.findInternalFormat(formatId).isEmpty)
    }

    describe("Valid date and time formats") {

      val formats =
        List(
          (FormatType.Date, "yyyy-mm-dd"),
          (FormatType.Date, "yyyy/mm/dd"),
          (FormatType.Date, "yy/mm/dd"),
          (FormatType.Date, "yy/mmm/dd"),
          (FormatType.Date, "dd/mm/yy"),
          (FormatType.Date, "dd/mm/yyyy"),
          (FormatType.Date, "dd/mmm/yy"),
          (FormatType.Date, "dd-mm-yy"),
          (FormatType.Date, "dd-mm-yyyy"),
          (FormatType.Date, "DD-MM-YY"),
          (FormatType.Date, "DD-mm-YYYY"),
          (FormatType.Date, "dd\\-mm\\-yy"),
          (FormatType.Date, "dd.mm.yyyy"),
          (FormatType.Date, "dd\\.mm\\.yyyy"),
          (FormatType.Date, "dd\\ mm\\.yyyy AM"),
          (FormatType.Date, "dd\\ mm\\.yyyy pm"),
          (FormatType.Date, "dd\\ mm\\.yyyy\\-dd"),
          (FormatType.Time, "[h]:mm:ss"),
          (FormatType.Date, "mm/dd/yy"),
          (FormatType.Date, "\"mm\"/\"dd\"/\"yy\""),
          (FormatType.Date, "m\\/d\\/yyyy"),
          (FormatType.Date, "yyyy-mm-dd;@"),
          (FormatType.Date, "yyyy/mm/dd;@"),
          (FormatType.Date, "dd-mm-yy;@"),
          (FormatType.Date, "dd-mm-yyyy;@"),
          (FormatType.Date, "[$-F800]dddd\\,\\ mmm\\ dd\\,\\ yyyy"),
          (FormatType.Date, "[$-F900]ddd/mm/yyy"),
          (FormatType.Date, "[BLACK]dddd/mm/yy"),
          (FormatType.Date, "[yeLLow]yyyy-mm-dd")
        )

      for ((formatType, format) <- formats)
        it(s"`$format`") {
          assert(formatType == ExcelDateUtils.analyzeFormatType(formatId, format))
        }
    }

    describe("Valid datesTime formats") {
      val formats =
        List(
          (FormatType.DateTime, "yyyy-mm-dd hh:mm:ss"),
          (FormatType.DateTime, "yyyy/mm/dd HH:MM:SS"),
          (FormatType.DateTime, "mm/dd HH:MM"),
          (FormatType.DateTime, "yy/mmm/dd SS"),
          (FormatType.DateTime, "mm/dd HH:MM AM"),
          (FormatType.DateTime, "mm/dd HH:MM am"),
          (FormatType.DateTime, "mm/dd HH:MM PM"),
          (FormatType.DateTime, "mm/dd HH:MM pm"),
          (FormatType.DateTime, "m/d/yy h:mm AM/PM"),
          (FormatType.Time, "hh:mm:ss"),
          (FormatType.Time, "hh:mm:ss.0"),
          (FormatType.Time, "mm:ss.0"), //support elapsed time [h],[m],[s]
          (FormatType.Time, "[hh]"),
          (FormatType.Time, "[mm]"),
          (FormatType.Time, "[ss]"),
          (FormatType.Time, "[SS]"),
          (FormatType.Time, "[red][hh]")
      )

      for ((formatType, format) <- formats)
        it(s"`$format`") {
          assert(formatType == ExcelDateUtils.analyzeFormatType(formatId, format))
        }
    }

    describe("Invalid dates formats") {
      for (format <- List("yyyy*mm*dd", "0.0", "0.000", "0%", "0.0%", "[]Foo", "[BLACK]0.00%", "[ms]", "[Mh]", ""))
        it(s"`$format`") {
          assert(FormatType.Other == ExcelDateUtils.analyzeFormatType(formatId, format))
        }
    }
  }

  describe("Valid and invalid dates"){

    it ("invalid date must return `None`") {
      assert(
        ExcelDateUtils.getJavaDate(
          date             = -1,
          use1904windowing = false,
          tz               = ju.TimeZone.getTimeZone("UTC"),
          locale           = ju.Locale.getDefault,
          roundSeconds     = false
        ).isEmpty
      )
    }

    it ("valid date must return `Some`") {

      val calendar = createCalendar(1900, 0, 0)

      assert(
        ExcelDateUtils.getJavaDate(
          date             = 0,
          use1904windowing = false,
          tz               = TimeZoneUtc,
          locale           = DefaultLocale,
          roundSeconds     = false
        ).contains(calendar.getTime)
      )
    }
  }

  describe("Date conversions") {

    def assertDate(date: Double, use1904windowing : Boolean, expected: ju.Date) =
      ExcelDateUtils.getJavaDate(date, use1904windowing, TimeZoneUtc, DefaultLocale, roundSeconds = false) map (_.getTime) contains expected.getTime

    describe("Iterating over hours") {
      val cal = createCalendar(2002, ju.Calendar.JANUARY, 1, 0, 1, 1)
      var hour = 0
      while (hour < 24) {
        it(s"$hour") {
          val excelDate = ExcelDateUtils.getExcelDate(cal.getTime, use1904windowing = false, TimeZoneUtc, DefaultLocale).get
          assertDate(excelDate, use1904windowing = false, cal.getTime)
        }
        cal.add(ju.Calendar.HOUR_OF_DAY, 1)
        hour += 1
      }
    }

    val excelDate = 36526.0

    it("handles 1900 windowing") {
      // With 1900 windowing, excelDate is Jan. 1, 2000
      val dateIf1900 = createCalendar(2000, ju.Calendar.JANUARY, 1).getTime
      assertDate(excelDate, use1904windowing = false, dateIf1900)
    }

    it("handles 1904 windowing") {
      // With 1904 windowing, excelDate is Jan. 2, 2004
      val dateIf1904 = (createCalendar(2000, ju.Calendar.JANUARY, 1) |!> (_.add(ju.Calendar.YEAR, 4)) |!> (_.add(ju.Calendar.DATE, 1))).getTime
      assertDate(excelDate, use1904windowing = true, dateIf1904)
    }
  }
}
