package org.orbeon.date

import cats.implicits.catsSyntaxOptionId
import org.orbeon.date.IsoDate.*
import org.orbeon.oxf.util.StringUtils.*

import java.time.LocalDate
import java.time.chrono.IsoChronology
import java.time.format.{DateTimeFormatter, DateTimeFormatterBuilder, ResolverStyle, SignStyle}
import java.time.temporal.ChronoField
import java.time.temporal.ChronoField.{DAY_OF_MONTH, MONTH_OF_YEAR, YEAR}
import scala.util.Try


sealed trait DateFormatComponent
object DateFormatComponent {
  case object Day   extends DateFormatComponent
  case object Month extends DateFormatComponent
  case object Year  extends DateFormatComponent
}

case class IsoDate(
  year : Int,
  month: Int,
  day  : Int,
) {

  // This checks that the date is valid according to the ISO chronology, so that we cannot construct invalid dates.
  LocalDate.of(year, month, day)

  def toIsoString: String =
    formatDate(
      this,
      IsoDateFormat
    )
}

// Out of all the permutations, we need to support only three formats:
//
//  | Column 1 | Column 2 | Column 3 | Supported |
//  |----------|----------|----------|-----------|
//  | DD       | MM       | YYYY     | Yes       |
//  | MM       | DD       | YYYY     | Yes       |
//  | YYYY     | MM       | DD       | Yes       |
//  | DD       | YYYY     | MM       | No        |
//  | MM       | YYYY     | DD       | No        |
//  | YYYY     | DD       | MM       | No        |
//
// This means that we just need to indicate the first component. Other permutations are not needed.
case class DateFormat(
  firstComponent  : DateFormatComponent,
  separator       : Char, // typically `/`, `.`, `-`, ` `
  isPadDayDigits  : Boolean,
  isPadMonthDigits: Boolean,
) {
  require(! separator.isDigit)

  // Use a `java.time` format so we don't have to write our own parser
  lazy val Format: DateTimeFormatter =
    firstComponent match {
      case DateFormatComponent.Day   =>
        new DateTimeFormatterBuilder()
          .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(YEAR, 4, 4, SignStyle.NOT_NEGATIVE)
          .toFormatter
          .withResolverStyle(ResolverStyle.STRICT)
          .withChronology(IsoChronology.INSTANCE)
      case DateFormatComponent.Month =>
        new DateTimeFormatterBuilder()
          .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(YEAR, 4, 4, SignStyle.NOT_NEGATIVE)
          .toFormatter
          .withResolverStyle(ResolverStyle.STRICT)
          .withChronology(IsoChronology.INSTANCE)
      case DateFormatComponent.Year  =>
        new DateTimeFormatterBuilder()
          .appendValue(YEAR, 4, 4, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(MONTH_OF_YEAR, 1, 2, SignStyle.NOT_NEGATIVE)
          .appendLiteral(separator)
          .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NOT_NEGATIVE)
          .toFormatter
          .withResolverStyle(ResolverStyle.STRICT)
          .withChronology(IsoChronology.INSTANCE)
    }
}

object IsoDate {

  val IsoDateFormat = DateFormat(DateFormatComponent.Year, '-', isPadDayDigits = true, isPadMonthDigits = true)

  // Supported format only:
  //
  //  | Format            | Example    | Description                          |
  //  |===================|============|======================================|
  //  | `[M]/[D]/[Y]`     | 11/5/2023  | also called "North American format"  |
  //  | `[D]/[M]/[Y]`     | 5/11/2023  | also called "European format"        |
  //  | `[D].[M].[Y]`     | 5.11.2023  | variation with dot separator         |
  //  | `[D]-[M]-[Y]`     | 5-11-2023  | variation with dash separator        |
  //  | `[M01]/[D01]/[Y]` | 11/05/2023 | force two digits for months and days |
  //  | `[Y]-[M01]-[D01]` | 2023-11-05 | ISO format                           |

  def formatDate(time: IsoDate, dateFormat: DateFormat): String = {

    val dayString =
      if (dateFormat.isPadDayDigits)
        IsoTime.pad2(time.day)
      else
        time.day.toString

    val monthString =
      if (dateFormat.isPadMonthDigits)
        IsoTime.pad2(time.month)
      else
        time.month.toString

    val yearString = time.year.toString

    dateFormat.firstComponent match {
      case DateFormatComponent.Day   => s"$dayString${dateFormat.separator}$monthString${dateFormat.separator}$yearString"
      case DateFormatComponent.Month => s"$monthString${dateFormat.separator}$dayString${dateFormat.separator}$yearString"
      case DateFormatComponent.Year  => s"$yearString${dateFormat.separator}$monthString${dateFormat.separator}$dayString"
    }
  }

  def parseFormat(formatInputDate: String): DateFormat = {

    val firstComponent =
      if (formatInputDate.startsWith("[D"))
        DateFormatComponent.Day
      else if (formatInputDate.startsWith("[M"))
        DateFormatComponent.Month
      else if (formatInputDate.startsWith("[Y"))
        DateFormatComponent.Year
      else
        throw new IllegalArgumentException(s"Invalid format: `$formatInputDate`")

    val isPadDayDigits = formatInputDate.contains("[D01]")
    val isPadMonthDigits = formatInputDate.contains("[M01]")

    DateFormat(
      firstComponent,
      formatInputDate.dropWhile(_ != ']').drop(1).head, // first character after the first `]`
      isPadDayDigits,
      isPadMonthDigits
    )
  }

  def generateFormat(dateFormat: DateFormat): String = {

    val dayComponent =
      if (dateFormat.isPadDayDigits)
        "[D01]"
      else
        "[D]"

    val monthComponent =
      if (dateFormat.isPadMonthDigits)
        "[M01]"
      else
        "[M]"

    val yearComponent = "[Y]"

    dateFormat.firstComponent match {
      case DateFormatComponent.Day   => s"$dayComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$yearComponent"
      case DateFormatComponent.Month => s"$monthComponent${dateFormat.separator}$dayComponent${dateFormat.separator}$yearComponent"
      case DateFormatComponent.Year  => s"$yearComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$dayComponent"
    }
  }

  def generatePlaceholder(dateFormat: DateFormat, ymdEn: String, ymd: String): String = {

    val dayComponent   = "DD"
    val monthComponent = "MM"
    val yearComponent  = "YYYY"

    val enPlaceholder =
      dateFormat.firstComponent match {
        case DateFormatComponent.Day   => s"$dayComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$yearComponent"
        case DateFormatComponent.Month => s"$monthComponent${dateFormat.separator}$dayComponent${dateFormat.separator}$yearComponent"
        case DateFormatComponent.Year  => s"$yearComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$dayComponent"
      }

    enPlaceholder.translate(ymdEn, ymd)
  }

  // Orbeon Forms format:         https://doc.orbeon.com/configuration/properties/xforms#for-xf-input
  // bootstrap-datepicker format: https://bootstrap-datepicker.readthedocs.io/en/latest/options.html#format
  def generateBootstrapFormat(dateFormat: DateFormat): String = {

    val dayComponent =
      if (dateFormat.isPadDayDigits)
        "dd"
      else
        "d"

    val monthComponent =
      if (dateFormat.isPadMonthDigits)
        "mm"
      else
        "m"

    val yearComponent = "yyyy"

    dateFormat.firstComponent match {
      case DateFormatComponent.Day   => s"$dayComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$yearComponent"
      case DateFormatComponent.Month => s"$monthComponent${dateFormat.separator}$dayComponent${dateFormat.separator}$yearComponent"
      case DateFormatComponent.Year  => s"$yearComponent${dateFormat.separator}$monthComponent${dateFormat.separator}$dayComponent"
    }
  }

  def findMagicDateAsIsoDateWithNow(dateFormat: DateFormat, magicDate: String, currentDate: => IsoDate): Option[IsoDate] =
    magicDate.some.map(_.trimAllToEmpty)
      .collect {
        case "today" => currentDate
        // TODO: implement more magic?
      }
      .orElse(parseUserDateValue(dateFormat, magicDate))

  // The `java.time` format that we create validates dates against a chronology. So for example `2023-02-29` is not
  // valid.
  def parseUserDateValue(dateFormat: DateFormat, dateValue: String): Option[IsoDate] =
    Try(dateFormat.Format.parse(dateValue))
      .toOption
      .map { accessor =>
        IsoDate(
          accessor.get(ChronoField.YEAR),
          accessor.get(ChronoField.MONTH_OF_YEAR),
          accessor.get(ChronoField.DAY_OF_MONTH)
        )
      }
}
