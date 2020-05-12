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

import java.{lang => jl, util => ju}

import enumeratum.EnumEntry.Lowercase
import enumeratum._
import org.orbeon.oxf.util.CoreUtils._


// ORBEON: Moved subset of original Apache POI class to Scala and made lots of changes, including:
//
// - Scala-ified a little
// - removed `ThreadLocal` cache
// - removed methods we don't need
// - added determination of whether a format is for a date, time or a dateTime
//
object ExcelDateUtils {

  sealed trait FormatType extends EnumEntry with Lowercase
  object FormatType extends Enum[FormatType] {

    val values = findValues

    case object Date     extends FormatType
    case object Time     extends FormatType
    case object DateTime extends FormatType
    case object Other    extends FormatType
  }

  import Private._

  def getJavaCalendar(
    date             : Double,
    use1904windowing : Boolean,
    timeZone         : ju.TimeZone,
    locale           : ju.Locale,
    roundSeconds     : Boolean
  ): Option[ju.Calendar] =
    isInRangeExcelDate(date) option {
      val wholeDays = Math.floor(date).toInt
      val calendar  = getLocaleCalendar(timeZone, locale)

      def setCalendar(
        calendar          : ju.Calendar,
        wholeDays         : Int,
        millisecondsInDay : Int,
        use1904windowing  : Boolean,
        roundSeconds      : Boolean
      ): Unit = {

        val (startYear, dayAdjust) =
          if (use1904windowing)
            (1904, 1) // 1904 date windowing uses 1/2/1904 as the first day
          else if (wholeDays < 61)
            (1900, 0) // prior to 3/1/1900 so adjust because Excel thinks 2/29/1900 exists. If Excel date == 2/29/1900, will become 3/1/1900 in Java representation
          else
            (1900, -1) // Excel thinks 2/29/1900 is a valid date, which it isn't

        calendar.set(startYear, 0, wholeDays + dayAdjust, 0, 0, 0)
        calendar.set(ju.Calendar.MILLISECOND, millisecondsInDay)
        if (calendar.get(ju.Calendar.MILLISECOND) == 0)
          calendar.clear(ju.Calendar.MILLISECOND)
        if (roundSeconds) {
          calendar.add(ju.Calendar.MILLISECOND, 500)
          calendar.clear(ju.Calendar.MILLISECOND)
        }
      }

      setCalendar(
        calendar          = calendar,
        wholeDays         = wholeDays,
        millisecondsInDay = ((date - wholeDays) * MillisecondsPerDay + 0.5).toInt,
        use1904windowing  = use1904windowing,
        roundSeconds      = roundSeconds
      )

      calendar
    }

  def analyzeFormatType(formatIndex: Int, formatString: String): FormatType = {

    def removeSimpleSequences(s: String): String = {

      val length = formatString.length
      val sb = new jl.StringBuilder(length)

      var i = 0
      while (i < length) {
        val c = formatString.charAt(i)

        var append = true
        if (i < length - 1) {
          val nc = formatString.charAt(i + 1)
          if (c == '\\')
            nc match {
              case  '-' | ',' | '.' | ' ' | '\\' =>
                append = false // skip  '\'
              case _ =>
            }
          else if (c == ';' && nc == '@') {
            i += 1
            append = false // skip ";@"
          }
        }

        if (append)
          sb.append(c)

        i += 1
      }

      sb.toString
    }

    def removePatternSequences(s: String): String =
      RemovePatterns.foldLeft(s)((v, p) => p.matcher(v).replaceAll(""))

    def removeSecondFormat(s: String): String = {
      // You're allowed something like `dd/mm/yy;[red]dd/mm/yy` which would place dates before 1900/1904 in red.
      // For now, only consider the first one.
      val separatorIndex = s.indexOf(';')
      if (separatorIndex > 0)
        s.substring(0, separatorIndex)
      else
        s
    }

    findInternalFormat(formatIndex) match {
      case Some((formatType, _)) => formatType
      case None if formatString.isEmpty => FormatType.Other
      case None =>

        val firstPass = removeSimpleSequences(formatString)

        if (ElapsedTimePattern.matcher(firstPass).matches) {
          FormatType.Time
        } else {

          val secondPass = removeSecondFormat(removePatternSequences(firstPass))

          if (! BasicPattern.matcher(secondPass).find || ! FullPattern.matcher(secondPass).matches) {
            FormatType.Other
          } else {
            // If we get here, check it's only made up, in any case, of:
            //
            //     y m d h s - \ / , . : [ ] T
            //
            // optionally followed by `AM/PM`

            // ORBEON: We *try* to tell things apart, but it's just a heuristic.
            //
            // See also this: https://www.brendanlong.com/the-minimum-viable-xlsx-reader.html
            //
            // Essentially, the POI regexes might not do everything they can. For example, date formats can also contain `w` and `q`,
            // "reverse-engineered from the output of OpenOffice". There is also `n`, the day of the week.
            //
            // The presence of `m` is not detected perfectly either, "since m means minutes only if it immediately follows h, and otherwise
            // means months".

            val isTime = TimeFilterPattern.matcher(secondPass).find
            val isDate = DateFilterPattern.matcher(secondPass).find

            if (isTime && isDate)
              FormatType.DateTime
            else if (isDate)
              FormatType.Date
            else
              FormatType.Time
          }
        }
    }
  }

  def getJavaDate(
    date             : Double,
    use1904windowing : Boolean,
    tz               : ju.TimeZone,
    locale           : ju.Locale,
    roundSeconds     : Boolean
  ): Option[ju.Date] =
    getJavaCalendar(date, use1904windowing, tz, locale, roundSeconds) map (_.getTime)

  def getLocaleCalendar(timeZone: ju.TimeZone, locale: ju.Locale): ju.Calendar =
    ju.Calendar.getInstance(timeZone, locale)

  def getExcelDate(
    date             : ju.Date,
    use1904windowing : Boolean,
    timeZone         : ju.TimeZone,
    locale           : ju.Locale
  ): Option[Double] = {
    val cal = getLocaleCalendar(timeZone, locale) |!> (_.setTime(date))
    internalGetExcelDate(
      year             = cal.get(ju.Calendar.YEAR),
      dayOfYear        = cal.get(ju.Calendar.DAY_OF_YEAR),
      hour             = cal.get(ju.Calendar.HOUR_OF_DAY),
      minute           = cal.get(ju.Calendar.MINUTE),
      second           = cal.get(ju.Calendar.SECOND),
      milliSecond      = cal.get(ju.Calendar.MILLISECOND),
      use1904windowing = use1904windowing
    )
  }

  def findInternalFormat(formatIndex: Int): Option[(FormatType, String)] =
    BuiltinFormats.get(formatIndex)

  private object Private {

    import java.util.regex.Pattern

    val TimezoneUtc: ju.TimeZone = ju.TimeZone.getTimeZone("UTC")

    val SecondsPerMinute = 60
    val MinutesPerHour   = 60
    val HoursPerDay      = 24

    val SecondsPerDay     : Int  = HoursPerDay * MinutesPerHour * SecondsPerMinute
    val MillisecondsPerDay: Long = SecondsPerDay * 1000L

    val ElapsedTimePattern: Pattern = Pattern.compile("""^\[([hH]+|[mM]+|[sS]+)\]""")
    val BasicPattern      : Pattern = Pattern.compile("""[yYmMdDhHsS]""")
    val FullPattern       : Pattern = Pattern.compile("""^[\[\]yYmMdDhHsS\-T/年月日,. :"\\]+0*[ampAMP/]*$""")

    val TimeFilterPattern : Pattern = Pattern.compile("""[hHsS]|(AM?/PM?)""")
    val DateFilterPattern : Pattern = Pattern.compile("""[yYdDqQnNwW年月日]|mmm""")

    val RemovePatterns: List[Pattern] =
      List(
        """^\[DBNum(1|2|3)\]""", // could be a Chinese date
        """^\[\$\-.*?\]""",
        """^\[[a-zA-Z]+\]"""
      ) map
        Pattern.compile

    val BuiltinFormats: Map[Int, (FormatType, String)] = {

      val first =
        0 -> List(
          FormatType.Other    -> "General",
          FormatType.Other    -> "0",
          FormatType.Other    -> "0.00",
          FormatType.Other    -> "#,##0",
          FormatType.Other    -> "#,##0.00",
          FormatType.Other    -> "\"$\"#,##0_);(\"$\"#,##0)",
          FormatType.Other    -> "\"$\"#,##0_);[Red](\"$\"#,##0)",
          FormatType.Other    -> "\"$\"#,##0.00_);(\"$\"#,##0.00)",
          FormatType.Other    -> "\"$\"#,##0.00_);[Red](\"$\"#,##0.00)",
          FormatType.Other    -> "0%",
          FormatType.Other    -> "0.00%",
          FormatType.Other    -> "0.00E+00",
          FormatType.Other    -> "# ?/?",
          FormatType.Other    -> "# ??/??",
          FormatType.Date     -> "m/d/yy",
          FormatType.Date     -> "d-mmm-yy",
          FormatType.Date     -> "d-mmm",
          FormatType.Date     -> "mmm-yy",
          FormatType.Time     -> "h:mm AM/PM",
          FormatType.Time     -> "h:mm:ss AM/PM",
          FormatType.Time     -> "h:mm",
          FormatType.Time     -> "h:mm:ss",
          FormatType.DateTime -> "m/d/yy h:mm"
        )

        val second =
          0x25 -> List(
            FormatType.Other  -> "#,##0_);(#,##0)",
            FormatType.Other  -> "#,##0_);[Red](#,##0)",
            FormatType.Other  -> "#,##0.00_);(#,##0.00)",
            FormatType.Other  -> "#,##0.00_);[Red](#,##0.00)",
            FormatType.Other  -> "_(* #,##0_);_(* (#,##0);_(* \"-\"_);_(@_)",
            FormatType.Other  -> "_(\"$\"* #,##0_);_(\"$\"* (#,##0);_(\"$\"* \"-\"_);_(@_)",
            FormatType.Other  -> "_(* #,##0.00_);_(* (#,##0.00);_(* \"-\"??_);_(@_)",
            FormatType.Other  -> "_(\"$\"* #,##0.00_);_(\"$\"* (#,##0.00);_(\"$\"* \"-\"??_);_(@_)",
            FormatType.Time   -> "mm:ss",
            FormatType.Time   -> "[h]:mm:ss",
            FormatType.Time   -> "mm:ss.0",
            FormatType.Other  -> "##0.0E+0",
            FormatType.Other  -> "@"
          )

      val all =
        for {
          (offset, items) <- List(first, second)
          (typeFormat, i) <- items.zipWithIndex
        } yield
          (offset + i, typeFormat)

      all.toMap
    }

    def isInRangeExcelDate(value: Double): Boolean =
      value > -Double.MinPositiveValue

    def absoluteDay(year: Int, dayOfYear: Int, use1904windowing: Boolean): Int =
      dayOfYear + daysInPriorYears(year, use1904windowing)

    def daysInPriorYears(yr: Int, use1904windowing: Boolean): Int = {

      if ((! use1904windowing && yr < 1900) || (use1904windowing && yr < 1904))
        throw new IllegalArgumentException("'year' must be 1900 or greater")

      // plus julian leap days in prior years - yr1 / 100
      // minus prior century years + yr1 / 400
      // plus years divisible by 400 - 460
      // leap days in previous 1900 years

      val yr1 = yr - 1
      val leapDays = yr1 / 4
      365 * (yr - (if (use1904windowing) 1904 else 1900)) + leapDays
    }

    def internalGetExcelDate(
      year             : Int,
      dayOfYear        : Int,
      hour             : Int,
      minute           : Int,
      second           : Int,
      milliSecond      : Int,
      use1904windowing : Boolean
    ): Option[Double] =
      (! use1904windowing && year >= 1900) || (use1904windowing && year >= 1904) option {

      // Because of daylight time saving we cannot use
      //
      //     date.getTime() - calStart.getTimeInMillis()
      //
      // as the difference in milliseconds between 00:00 and 04:00
      // can be 3, 4 or 5 hours but Excel expects it to always
      // be 4 hours.
      //
      // E.g. 2004-03-28 04:00 CEST - 2004-03-28 00:00 CET is 3 hours
      // and 2004-10-31 04:00 CET - 2004-10-31 00:00 CEST is 5 hours

      val fraction = (((hour * 60.0 + minute) * 60.0 + second) * 1000.0 + milliSecond) / MillisecondsPerDay
      var value = fraction + absoluteDay(year, dayOfYear, use1904windowing)
      if (! use1904windowing && value >= 60)
        value += 1
      else if (use1904windowing)
        value -= 1
      value
    }
  }
}
