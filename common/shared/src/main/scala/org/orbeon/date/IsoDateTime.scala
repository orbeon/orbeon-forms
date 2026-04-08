package org.orbeon.date

import org.orbeon.oxf.util.CoreUtils.BooleanOps
import org.parboiled2.*

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoField
import scala.util.Try


sealed trait TimezoneFormat
object TimezoneFormat {
  case object Offset        extends TimezoneFormat
  case object OffsetWithGmt extends TimezoneFormat
  case object ShortName     extends TimezoneFormat
}

case class IsoDateTime(
  date         : IsoDate,
  time         : IsoTime,
  offsetMinutes: Option[Int],
) {

  def adjustTo(newOffsetMinutes: Int): IsoDateTime =
    offsetMinutes match {
      case Some(_) =>

        val newOffsetDateTime =
          Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(toIsoString)) // could probably do this more efficiently...
            .atOffset(java.time.ZoneOffset.ofTotalSeconds(newOffsetMinutes * 60))

        IsoDateTime(
          date = IsoDate(
            year  = newOffsetDateTime.getYear,
            month = newOffsetDateTime.getMonthValue,
            day   = newOffsetDateTime.getDayOfMonth
          ),
          time = IsoTime(
            hour   = newOffsetDateTime.getHour,
            minute = newOffsetDateTime.getMinute,
            second = time.second.isDefined.option(newOffsetDateTime.getSecond)
          ),
          offsetMinutes = Some(newOffsetMinutes)
        )
      case None =>
        IsoDateTime(
          date = date,
          time = time,
          offsetMinutes = Some(newOffsetMinutes)
        )
    }

  def toIsoString: String =
    s"${date.toIsoString}T${time.toIsoString}$offsetToString"

  def offsetToString: String =
    offsetMinutes.map { offset =>
      val sign = if (offset < 0) "-" else "+"
      val absOffset = math.abs(offset)
      val h = absOffset / 60
      val m = absOffset % 60
      s"$sign${IsoTime.pad2(h)}:${IsoTime.pad2(m)}"
    }.getOrElse("")

  def toInstant: Option[Instant] =
    offsetMinutes.map(_ => Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(toIsoString)))
}

case class DateTimeFormat(
  dateFormat    : DateFormat,
  timeFormat    : TimeFormat,
  separator     : String,
  dateFirst     : Boolean,
  timezoneFormat: Option[TimezoneFormat] = None,
)

object IsoDateTime {

  def formatDateTime(
    dateTime    : IsoDateTime,
    format      : DateTimeFormat,
    timezoneName: Option[String] = None
  ): String = {

    val dateString = IsoDate.formatDate(dateTime.date, format.dateFormat)
    val timeString = IsoTime.formatTime(dateTime.time, format.timeFormat)

    val formattedDateTime =
      if (format.dateFirst)
        s"$dateString${format.separator}$timeString"
      else
        s"$timeString${format.separator}$dateString"

    val timezoneString = format.timezoneFormat match {
      case Some(tzFormat) =>
        dateTime.offsetMinutes match {
          case Some(offset) =>
            tzFormat match {
              case TimezoneFormat.Offset =>
                if (offset == 0) "Z" else dateTime.offsetToString
              case TimezoneFormat.ShortName =>
                timezoneName.getOrElse(dateTime.offsetToString)
              case TimezoneFormat.OffsetWithGmt =>
                if (offset == 0) "UTC" else s"GMT${dateTime.offsetToString}"
            }
          case None => ""
        }
      case None => ""
    }

    if (timezoneString.nonEmpty)
      s"$formattedDateTime $timezoneString"
    else
      formattedDateTime
  }

  class DateTimeFormatParser(val input: ParserInput) extends Parser {

    def InputLine = rule { FormatRule ~ EOI }

    def FormatRule: Rule1[DateTimeFormat] = rule {
      DateFirstRule | TimeFirstRule
    }

    def DateFirstRule: Rule1[DateTimeFormat] = rule {
      runSubParser(new IsoDate.DateFormatParser(_).FormatRule) ~
      capture(zeroOrMore(noneOf("["))) ~
      runSubParser(new IsoTime.TimeFormatParser(_).FormatRule) ~
      OptionalTimezoneRule ~> { (d: DateFormat, sep: String, t: TimeFormat, z: Option[TimezoneFormat]) =>
        DateTimeFormat(d, t, sep, true, z)
      }
    }

    def TimeFirstRule: Rule1[DateTimeFormat] = rule {
      runSubParser(new IsoTime.TimeFormatParser(_).FormatRule) ~
      capture(zeroOrMore(noneOf("["))) ~
      runSubParser(new IsoDate.DateFormatParser(_).FormatRule) ~
      OptionalTimezoneRule ~> { (t: TimeFormat, sep: String, d: DateFormat, z: Option[TimezoneFormat]) =>
        DateTimeFormat(d, t, sep, false, z)
      }
    }

    def OptionalTimezoneRule: Rule1[Option[TimezoneFormat]] = rule {
      optional(
        zeroOrMore(' ') ~ (
          "[z]" ~ push(TimezoneFormat.OffsetWithGmt) |
          "[ZN]" ~ push(TimezoneFormat.ShortName) |
          "[Z]" ~ push(TimezoneFormat.Offset)
        )
      )
    }
  }

  def parseFormat(dateTimeFormat: String): DateTimeFormat =
    new DateTimeFormatParser(dateTimeFormat).InputLine.run().getOrElse {
      throw new IllegalArgumentException(s"Invalid format: `$dateTimeFormat`")
    }

  def tryParseIsoDateTime(value: String): Try[IsoDateTime] =
    Try {
      val accessor = DateTimeFormatter.ISO_DATE_TIME.parse(value)
      IsoDateTime(
        IsoDate(
          year  = accessor.get(ChronoField.YEAR),
          month = accessor.get(ChronoField.MONTH_OF_YEAR),
          day   = accessor.get(ChronoField.DAY_OF_MONTH)
        ),
        IsoTime(
          hour   = accessor.get(ChronoField.HOUR_OF_DAY),
          minute = accessor.get(ChronoField.MINUTE_OF_HOUR),
          second = accessor.isSupported(ChronoField.SECOND_OF_MINUTE).option(accessor.get(ChronoField.SECOND_OF_MINUTE))
        ),
        offsetMinutes = accessor.isSupported(ChronoField.OFFSET_SECONDS).option(accessor.get(ChronoField.OFFSET_SECONDS) / 60)
      )
    }

  def unapply(s: String): Option[IsoDateTime] =
    tryParseIsoDateTime(s).toOption
}