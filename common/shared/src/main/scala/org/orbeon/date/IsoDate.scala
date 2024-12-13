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
  firstComponent     : DateFormatComponent,
  separator          : Char, // typically `/`, `.`, `-`, ` `
  isPadDayMonthDigits: Boolean
) {
  require(! separator.isDigit)

  // Use a `java.time` format so we don't have to write our own parser. Currently, this is used only for parsing, not
  // formatting. For parsing, we support non-padded day and month digits, as those are entered by the user and there is
  // no point, currently to enforce padding there. But for printing, we will want to pad if requested.
  // Q: Should we cache these formats, given that they are likely to be always the same?
  lazy val ParseFormat: DateTimeFormatter =
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

  def generateFormatString: String =
    generateFormat(
      dayComponent =
        if (isPadDayMonthDigits)
          "[D01]"
        else
          "[D]",
      monthComponent =
        if (isPadDayMonthDigits)
          "[M01]"
        else
          "[M]",
      yearComponent = "[Y]"
    )

  def generatePlaceholderString(ymdEn: String, ymd: String): String =
    generateFormat(
      dayComponent   = "DD",
      monthComponent = "MM",
      yearComponent  = "YYYY"
    ).translate(ymdEn, ymd)

  // Orbeon Forms format:         https://doc.orbeon.com/configuration/properties/xforms#for-xf-input
  // bootstrap-datepicker format: https://bootstrap-datepicker.readthedocs.io/en/latest/options.html#format
  def generateBootstrapFormatString: String =
    generateFormat(
      dayComponent =
        if (isPadDayMonthDigits)
          "dd"
        else
          "d",
      monthComponent =
        if (isPadDayMonthDigits)
          "mm"
        else
          "m",
      yearComponent = "yyyy"
    )

  private def generateFormat(dayComponent: String, monthComponent: String, yearComponent: String): String = {
    firstComponent match {
      case DateFormatComponent.Day   => s"$dayComponent$separator$monthComponent$separator$yearComponent"
      case DateFormatComponent.Month => s"$monthComponent$separator$dayComponent$separator$yearComponent"
      case DateFormatComponent.Year  => s"$yearComponent$separator$monthComponent$separator$dayComponent"
    }
  }
}

object IsoDate {

  val IsoDateFormat = DateFormat(DateFormatComponent.Year, '-', isPadDayMonthDigits = true)

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

  def formatDate(date: IsoDate, dateFormat: DateFormat): String = {

    val dayString =
      if (dateFormat.isPadDayMonthDigits)
        IsoTime.pad2(date.day)
      else
        date.day.toString

    val monthString =
      if (dateFormat.isPadDayMonthDigits)
        IsoTime.pad2(date.month)
      else
        date.month.toString

    val yearString = date.year.toString

    dateFormat.firstComponent match {
      case DateFormatComponent.Day   => s"$dayString${dateFormat.separator}$monthString${dateFormat.separator}$yearString"
      case DateFormatComponent.Month => s"$monthString${dateFormat.separator}$dayString${dateFormat.separator}$yearString"
      case DateFormatComponent.Year  => s"$yearString${dateFormat.separator}$monthString${dateFormat.separator}$dayString"
    }
  }

  def parseFormat(formatInputDate: String): DateFormat =
    DateFormat(
      firstComponent      =
        if (formatInputDate.startsWith("[D"))
          DateFormatComponent.Day
        else if (formatInputDate.startsWith("[M"))
          DateFormatComponent.Month
        else if (formatInputDate.startsWith("[Y"))
          DateFormatComponent.Year
        else
          throw new IllegalArgumentException(s"Invalid format: `$formatInputDate`"),
      separator           = formatInputDate.dropWhile(_ != ']').drop(1).head, // first character after the first `]`
      isPadDayMonthDigits = formatInputDate.contains("[D01]") || formatInputDate.contains("[M01]")
    )

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
    Try(dateFormat.ParseFormat.parse(dateValue))
      .toOption
      .map { accessor =>
        IsoDate(
          accessor.get(ChronoField.YEAR),
          accessor.get(ChronoField.MONTH_OF_YEAR),
          accessor.get(ChronoField.DAY_OF_MONTH)
        )
      }

  def parseIsoDate(isoDate: String): Option[IsoDate] =
    Try(LocalDate.parse(isoDate))
      .toOption
      .map { accessor =>
        IsoDate(
          accessor.get(ChronoField.YEAR),
          accessor.get(ChronoField.MONTH_OF_YEAR),
          accessor.get(ChronoField.DAY_OF_MONTH)
        )
      }
}
