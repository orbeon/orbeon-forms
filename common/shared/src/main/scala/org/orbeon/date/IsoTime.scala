package org.orbeon.date

import cats.syntax.option._
import org.orbeon.date.IsoTime._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._


case class IsoTime(
  hour   : Int,
  minute : Int,
  second : Option[Int],
//    millis : Option[Int]
) {
  require(hour   >=0 && hour   <= 23) // with ISO, "24 then the values of the minutes element and the seconds element must be 00 and 00"
  require(minute >=0 && minute <= 59) // with ISO, "60 or more is allowed only in the case of leap seconds"
  require(second.isEmpty || second.exists(s => s >=0 && s <= 59))

  def toIsoString: String =
    formatTime(
      this,
      IsoTimeFormat
    )
}

sealed trait AmPmFormat
object AmPmFormat {
  case object None       extends AmPmFormat
  case object Lower      extends AmPmFormat
  case object Upper      extends AmPmFormat
  case object LowerDots  extends AmPmFormat
  case object LowerShort extends AmPmFormat
  case object UpperDots  extends AmPmFormat
  case object UpperShort extends AmPmFormat
}

case class TimeFormat(
  is24Hour       : Boolean,
  isPadHourDigits: Boolean,
  hasSeconds     : Boolean,
  amPmFormat     : AmPmFormat,
)
object IsoTime {

  val IsoTimeFormat = TimeFormat(is24Hour = true, isPadHourDigits = true, hasSeconds = true, AmPmFormat.None)

  // Supported format only:
  //
  // - `[h]:[m]:[s] [P]`    : e.g. 2:05:12 p.m. with dots in a.m. and p.m.
  // - `[h]:[m] [P]`        : e.g. 2:05 p.m.    [SINCE Orbeon Forms 2020.1]
  // - `[h]:[m]:[s] [P,2-2]`: e.g. 2:05:12 pm   without dots in am and pm
  // - `[h]:[m] [P,2-2]`    : e.g. 2:05 pm      [SINCE Orbeon Forms 2020.1]
  // - `[H]:[m]:[s]`        : e.g. 14:05:12
  // - `[H]:[m]`            : e.g. 14:05        (without seconds)
  //
  // TODO: Would be nice to:
  //
  // 1. validate the format
  // 2. support richer formatting, including milliseconds
  //
  def formatTime(time: IsoTime, timeFormat: TimeFormat): String = {

    val hours =
      if (timeFormat.is24Hour)
        time.hour
      else // for now assume a required `[h]`/`[h1]` for 12-hour format
        time.hour match {
          case 0 | 12 => 12
          case other  => other % 12
        }

    val hoursString =
      if (timeFormat.isPadHourDigits)
        pad2(hours)
      else
        hours.toString

    val minutesString =
      pad2(time.minute) // for now assume `[m]`/`[m01]`

    val secondsOpt =
      timeFormat.hasSeconds option pad2(time.second.getOrElse(0))

    val amPmOpt =
      timeFormat.amPmFormat match {
        case AmPmFormat.None       => None
        case AmPmFormat.Lower      => Some(if (time.hour < 12) "am"   else "pm")
        case AmPmFormat.Upper      => Some(if (time.hour < 12) "AM"   else "PM")
        case AmPmFormat.LowerDots  => Some(if (time.hour < 12) "a.m." else "p.m.")
        case AmPmFormat.LowerShort => Some(if (time.hour < 12) "a"    else "p")
        case AmPmFormat.UpperDots  => Some(if (time.hour < 12) "A.M." else "P.M.")
        case AmPmFormat.UpperShort => Some(if (time.hour < 12) "A"    else "P")
      }

    ((hoursString :: minutesString :: secondsOpt.toList).mkString(":") :: amPmOpt.toList).mkString(" ")
  }

  def parseFormat(formatInputTime: String): TimeFormat = {

    val is24Hour =
      formatInputTime.contains("[H]") || formatInputTime.contains("[H1]") || formatInputTime.contains("[H01]")

    val isPadHourDigits =
      formatInputTime.contains("[H01]") || formatInputTime.contains("[h01]")

    val hasSeconds =
      formatInputTime.contains("[s]")

    val amPmFormat =
      if (formatInputTime.contains("[PN") && formatInputTime.endsWith("-2]"))
        AmPmFormat.Upper
      else if (formatInputTime.contains("[P") && formatInputTime.endsWith("-2]"))
        AmPmFormat.Lower
      else if (formatInputTime.contains("[PN") && formatInputTime.endsWith("-1]"))
        AmPmFormat.UpperShort
      else if (formatInputTime.contains("[P") && formatInputTime.endsWith("-1]"))
        AmPmFormat.LowerShort
      else if (formatInputTime.contains("[PN"))
        AmPmFormat.UpperDots
      else if (formatInputTime.contains("[P"))
        AmPmFormat.LowerDots
      else
        AmPmFormat.None

    TimeFormat(is24Hour = is24Hour, isPadHourDigits = isPadHourDigits, hasSeconds = hasSeconds, amPmFormat = amPmFormat)
  }

  def generateFormat(timeFormat: TimeFormat): String = {

    val hours = s"[${if (timeFormat.is24Hour) "H" else "h"}${if (timeFormat.isPadHourDigits) "01" else ""}]"

    val secondsSuffix = if (timeFormat.hasSeconds) ":[s]" else ""

    val amPmSuffix = timeFormat.amPmFormat match {
      case AmPmFormat.None       => ""
      case AmPmFormat.Lower      => " [P,2-2]"  // could also produce `*-2`
      case AmPmFormat.Upper      => " [PN,2-2]" // could also produce `*-2`
      case AmPmFormat.LowerDots  => " [P]"
      case AmPmFormat.LowerShort => " [P,1-1]"  // could also produce `*-1`
      case AmPmFormat.UpperDots  => " [PN]"
      case AmPmFormat.UpperShort => " [PN,1-1]" // could also produce `*-1`
    }

    s"$hours:[m]$secondsSuffix$amPmSuffix"
  }

  def findMagicTimeAsIsoTimeWithNow(magicTime: String, currentTime: => IsoTime): Option[IsoTime] =
    magicTime.some.map(_.trimAllToEmpty) collect {
      case "now" => currentTime
    } orElse
      findMagicTimeAsIsoTime(magicTime)

  def findMagicTimeAsIsoTime(magicTime: String): Option[IsoTime] =
    magicTime.some.map(_.trimAllToEmpty) collect {
      case MagicTimeRe(h, m, s, amPm)
        if h.toInt >=0 && h.toInt < 24  &&
          ((m eq null) || m.toInt >= 0 && m.toInt <= 59) &&
          ((s eq null) || s.toInt >= 0 && s.toInt <= 59) =>

        // TODO: handle 24:00:00?

        val hInt = h.toInt
        val mInt = if (m eq null) 0 else m.toInt
        val isAm = Option(amPm).exists(_.startsWith("a"))
        val isPm = Option(amPm).exists(_.startsWith("p"))

        val hh =
          if (isAm)
            hInt % 12
          else if (isPm && hInt < 12)
            hInt + 12
          else
            hInt

        IsoTime(hh, mInt, Option(s).map(_.toInt))
    }

  def pad2(n: Int): String =
    if (n < 10)
      "0" + n
    else
      n.toString

  private val MagicTimeRe = """(\d{1,2})(?::(\d{1,2})(?::(\d{1,2}))?)?(?:\s*(p|pm|p\.m\.|a|am|a\.m\.))?""".r
}