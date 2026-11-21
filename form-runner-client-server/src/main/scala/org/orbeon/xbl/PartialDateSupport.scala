/**
  * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.date.{DateFormat, DateFormatComponent, IsoDate, IsoTime}
import org.orbeon.oxf.util.StringUtils.*

import java.time.{LocalDate, YearMonth => JYearMonth}
import scala.util.Try


sealed trait EdtfQualifier {
  def symbol: String
}
object EdtfQualifier {
  case object Uncertain          extends EdtfQualifier { val symbol = "?" }
  case object Approximate        extends EdtfQualifier { val symbol = "~" }
  case object UncertainAndApprox extends EdtfQualifier { val symbol = "%" }
}

sealed trait EdtfPrecision {
  def name: String
}
object EdtfPrecision {
  case object Day    extends EdtfPrecision { val name = "day"    }
  case object Month  extends EdtfPrecision { val name = "month"  }
  case object Year   extends EdtfPrecision { val name = "year"   }
  case object Decade extends EdtfPrecision { val name = "decade" }

  val DefaultPrecisions: Set[EdtfPrecision] = Set(Day, Month, Year)
  val AllPrecisions    : Set[EdtfPrecision] = Set(Day, Month, Year, Decade)

  def fromName(name: String): Option[EdtfPrecision] = name.toLowerCase match {
    case "day"    => Some(Day)
    case "month"  => Some(Month)
    case "year"   => Some(Year)
    case "decade" => Some(Decade)
    case _        => None
  }
}

sealed trait EdtfValue {
  def canonicalString: String
  def earliestIsoDate: IsoDate
  def latestIsoDate  : IsoDate
  def exactYearOpt   : Option[Int]
  def allPrecisions  : Set[EdtfPrecision]
  def hasQualifier   : Boolean
  def isInterval     : Boolean
  def minYear        : Int
  def maxYear        : Int
}

sealed trait EdtfSingle extends EdtfValue {
  def qualifierOpt: Option[EdtfQualifier]
  def precision   : EdtfPrecision
  def allPrecisions: Set[EdtfPrecision] = Set(precision)
  def hasQualifier : Boolean            = qualifierOpt.isDefined
  def isInterval   : Boolean            = false

  protected def qualifierSuffix: String = qualifierOpt.map(_.symbol).getOrElse("")
}

object EdtfSingle {

  case class ExactDate(date: IsoDate, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    val precision      : EdtfPrecision = EdtfPrecision.Day
    val canonicalString: String        = s"${date.toIsoString}$qualifierSuffix"
    val earliestIsoDate: IsoDate       = date
    val latestIsoDate  : IsoDate       = date
    val exactYearOpt   : Option[Int]   = Some(date.year)
    val minYear        : Int           = date.year
    val maxYear        : Int           = date.year
  }

  case class UnspecifiedDayDate(
    year        : Int,
    month       : Int,
    dayTensOpt  : Option[Int], // Some(0..3) for YYYY-MM-DX, None for YYYY-MM-XX
    qualifierOpt: Option[EdtfQualifier]
  ) extends EdtfSingle {
    private val daysInMonth = JYearMonth.of(year, month).lengthOfMonth()
    private val (earliestDay, latestDay) = dayTensOpt match {
      case None    => (1, daysInMonth)
      case Some(0) => (1, 9)
      case Some(1) => (10, 19)
      case Some(2) => (20, math.min(29, daysInMonth))
      case Some(3) => (30, daysInMonth)
      case Some(d) => throw new IllegalArgumentException(s"Invalid day tens digit: $d")
    }
    private val dayStr = dayTensOpt.map(d => s"${d}X").getOrElse("XX")

    val precision      : EdtfPrecision = EdtfPrecision.Decade
    val canonicalString: String        = f"$year%04d-${IsoTime.pad2(month)}-$dayStr$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(year, month, earliestDay)
    val latestIsoDate  : IsoDate       = IsoDate(year, month, latestDay)
    val exactYearOpt   : Option[Int]   = Some(year)
    val minYear        : Int           = year
    val maxYear        : Int           = year
  }

  case class YearMonth(year: Int, month: Int, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    private val daysInMonth = JYearMonth.of(year, month).lengthOfMonth()

    val precision      : EdtfPrecision = EdtfPrecision.Month
    val canonicalString: String        = f"$year%04d-${IsoTime.pad2(month)}$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(year, month, 1)
    val latestIsoDate  : IsoDate       = IsoDate(year, month, daysInMonth)
    val exactYearOpt   : Option[Int]   = Some(year)
    val minYear        : Int           = year
    val maxYear        : Int           = year
  }

  case class UnspecifiedMonthDate(year: Int, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    val precision      : EdtfPrecision = EdtfPrecision.Decade
    val canonicalString: String        = f"$year%04d-XX$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(year, 1, 1)
    val latestIsoDate  : IsoDate       = IsoDate(year, 12, 31)
    val exactYearOpt   : Option[Int]   = Some(year)
    val minYear        : Int           = year
    val maxYear        : Int           = year
  }

  case class YearOnly(year: Int, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    val precision      : EdtfPrecision = EdtfPrecision.Year
    val canonicalString: String        = f"$year%04d$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(year, 1, 1)
    val latestIsoDate  : IsoDate       = IsoDate(year, 12, 31)
    val exactYearOpt   : Option[Int]   = Some(year)
    val minYear        : Int           = year
    val maxYear        : Int           = year
  }

  case class DecadeDate(decadeStartYear: Int, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    require(decadeStartYear >= 0 && decadeStartYear <= 9990 && decadeStartYear % 10 == 0)

    val precision      : EdtfPrecision = EdtfPrecision.Decade
    val canonicalString: String        = f"${decadeStartYear / 10}%03dX$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(decadeStartYear, 1, 1)
    val latestIsoDate  : IsoDate       = IsoDate(decadeStartYear + 9, 12, 31)
    val exactYearOpt   : Option[Int]   = None
    val minYear        : Int           = decadeStartYear
    val maxYear        : Int           = decadeStartYear + 9
  }

  case class CenturyDate(centuryStartYear: Int, qualifierOpt: Option[EdtfQualifier]) extends EdtfSingle {
    require(centuryStartYear >= 0 && centuryStartYear <= 9900 && centuryStartYear % 100 == 0)

    val precision      : EdtfPrecision = EdtfPrecision.Decade
    val canonicalString: String        = f"${centuryStartYear / 100}%02dXX$qualifierSuffix"
    val earliestIsoDate: IsoDate       = IsoDate(centuryStartYear, 1, 1)
    val latestIsoDate  : IsoDate       = IsoDate(centuryStartYear + 99, 12, 31)
    val exactYearOpt   : Option[Int]   = None
    val minYear        : Int           = centuryStartYear
    val maxYear        : Int           = centuryStartYear + 99
  }
}

case class EdtfInterval(start: EdtfSingle, end: EdtfSingle) extends EdtfValue {
  val canonicalString: String             = s"${start.canonicalString}/${end.canonicalString}"
  val earliestIsoDate: IsoDate            = start.earliestIsoDate
  val latestIsoDate  : IsoDate            = end.latestIsoDate
  val exactYearOpt   : Option[Int]        = None
  val allPrecisions  : Set[EdtfPrecision] = start.allPrecisions ++ end.allPrecisions
  val hasQualifier   : Boolean            = start.hasQualifier || end.hasQualifier
  val isInterval     : Boolean            = true
  val minYear        : Int                = math.min(start.minYear, end.minYear)
  val maxYear        : Int                = math.max(start.maxYear, end.maxYear)
}

object PartialDateSupport {

  import Private.*

  val DefaultDateFormat: DateFormat = DateFormat(DateFormatComponent.Year, "-", isPadDayMonthDigits = true)

  def parsePrecisions(raw: String): Set[EdtfPrecision] = {
    val parsed = Option(raw).map(_.splitTo[Set]()).getOrElse(Set.empty).flatMap(EdtfPrecision.fromName)
    if (parsed.nonEmpty) parsed else EdtfPrecision.DefaultPrecisions
  }

  def parseBooleanParam(raw: String, default: Boolean = false): Boolean =
    Option(raw).flatMap(_.trimAllToOpt).map(_.toLowerCase) match {
      case Some("true")  => true
      case Some("false") => false
      case _             => default
    }

  def parseYearOpt(raw: String): Option[Int] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap(s => Try(s.toInt).toOption).filter(y => y >= 0 && y <= 9999)

  def parseDateFormatOrDefault(formatString: String): DateFormat =
    Option(formatString)
      .flatMap(_.trimAllToOpt)
      .flatMap(s => Try(IsoDate.parseFormat(s)).toOption)
      .getOrElse(DefaultDateFormat)

  def parseCanonical(raw: String): Option[EdtfValue] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      val slashIdx = trimmed.indexOf('/')
      if (slashIdx >= 0) {
        val left  = trimmed.substring(0, slashIdx)
        val right = trimmed.substring(slashIdx + 1)
        if (right.contains('/'))
          None
        else
          for {
            start <- parseCanonicalSingle(left)
            end   <- parseCanonicalSingle(right)
            if toEpochDay(start.earliestIsoDate) <= toEpochDay(end.latestIsoDate)
          } yield EdtfInterval(start, end)
      } else {
        parseCanonicalSingle(trimmed)
      }
    }

  def parseAndNormalize(
    raw            : String,
    dateFormat     : DateFormat,
    precisions     : Set[EdtfPrecision] = EdtfPrecision.AllPrecisions,
    allowQualifiers: Boolean            = true,
    allowIntervals : Boolean            = true
  ): Option[EdtfValue] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      splitUserInterval(trimmed, dateFormat) match {
        case Some((leftRaw, rightRaw)) =>
          if (! allowIntervals)
            None
          else
            for {
              start <- parseUserSingle(leftRaw, dateFormat)
              end   <- parseUserSingle(rightRaw, dateFormat)
              if toEpochDay(start.earliestIsoDate) <= toEpochDay(end.latestIsoDate)
              interval = EdtfInterval(start, end)
              if satisfiesConfig(interval, precisions, allowQualifiers, allowIntervals)
            } yield interval
        case None =>
          parseUserSingle(trimmed, dateFormat)
            .filter(satisfiesConfig(_, precisions, allowQualifiers, allowIntervals))
      }
    }

  def normalizeUserInput(
    raw            : String,
    dateFormat     : DateFormat,
    precisions     : Set[EdtfPrecision],
    allowQualifiers: Boolean,
    allowIntervals : Boolean
  ): String =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        ""
      case Some(trimmed) =>
        parseAndNormalize(trimmed, dateFormat, precisions, allowQualifiers, allowIntervals)
          .map(_.canonicalString)
          .getOrElse(trimmed)
    }

  def isValidPartialDate(
    raw                 : String,
    precisionsParam     : String,
    allowQualifiersParam: String,
    allowIntervalsParam : String,
    minYearParam        : String,
    maxYearParam        : String
  ): Boolean =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        true
      case Some(trimmed) =>
        val precisions      = parsePrecisions(precisionsParam)
        val allowQualifiers = parseBooleanParam(allowQualifiersParam, default = false)
        val allowIntervals  = parseBooleanParam(allowIntervalsParam, default = false)
        val minYearOpt      = parseYearOpt(minYearParam)
        val maxYearOpt      = parseYearOpt(maxYearParam)

        parseCanonical(trimmed).exists { value =>
          satisfiesConfig(value, precisions, allowQualifiers, allowIntervals) &&
            minYearOpt.forall(value.minYear >= _) &&
            maxYearOpt.forall(value.maxYear <= _)
        }
    }

  def earliestIsoDateOpt(raw: String): Option[IsoDate] =
    parseCanonical(raw).map(_.earliestIsoDate)

  def latestIsoDateOpt(raw: String): Option[IsoDate] =
    parseCanonical(raw).map(_.latestIsoDate)

  def exactYearOpt(raw: String): Option[Int] =
    parseCanonical(raw).flatMap(_.exactYearOpt)

  def formatForEdit(raw: String, dateFormat: DateFormat): String =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        ""
      case Some(trimmed) =>
        parseCanonical(trimmed) match {
          case Some(value) => formatEditValue(value, dateFormat)
          case None        => trimmed
        }
    }

  def formatReadonly(
    raw          : String,
    formatString : String,
    approxPrefix : String,
    decadePattern: String
  ): String =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None =>
        ""
      case Some(trimmed) =>
        parseCanonical(trimmed) match {
          case Some(value) =>
            val dateFormat = parseDateFormatOrDefault(formatString)
            val prefix     = Option(approxPrefix).flatMap(_.trimAllToOpt).getOrElse("c.")
            val decade     = Option(decadePattern).flatMap(_.trimAllToOpt).getOrElse("{decade}s")
            formatReadonlyValue(value, dateFormat, prefix, decade)
          case None =>
            trimmed
        }
    }

  def generateBootstrapMonthFormat(dateFormat: DateFormat): String = {
    val monthPart = if (dateFormat.isPadDayMonthDigits) "mm" else "m"
    val sep       = dateFormat.separator
    dateFormat.firstComponent match {
      case DateFormatComponent.Year => s"yyyy${sep}$monthPart"
      case _                        => s"$monthPart${sep}yyyy"
    }
  }

  def generatePlaceholder(
    formatString        : String,
    precisionsParam     : String,
    allowQualifiersParam: String,
    allowIntervalsParam : String,
    ymdEn               : String,
    ymd                 : String
  ): String = {
    val dateFormat = parseDateFormatOrDefault(formatString)
    val precisions = parsePrecisions(precisionsParam)
    val ymdEnSafe  = Option(ymdEn).filter(_.length == 3).getOrElse("MDY")
    val ymdSafe    = Option(ymd).filter(_.length == 3).getOrElse(ymdEnSafe)

    val examples = List.newBuilder[String]
    if (precisions.contains(EdtfPrecision.Day))
      examples += dateFormat.generatePlaceholderString(ymdEnSafe, ymdSafe)
    if (precisions.contains(EdtfPrecision.Month))
      examples += generateMonthYearPlaceholder(dateFormat, ymdEnSafe, ymdSafe)
    if (precisions.contains(EdtfPrecision.Year))
      examples += "YYYY".translate(ymdEnSafe, ymdSafe)
    if (precisions.contains(EdtfPrecision.Decade))
      examples += "192X"

    examples.result().take(3).mkString(", ")
  }

  private object Private {

    private val ExactDateRegex       = """^(\d{4})-(\d{2})-(\d{2})$""".r
    private val UnspecDayRegex       = """^(\d{4})-(\d{2})-([0-3])[Xx]$""".r
    private val UnspecDayFullRegex   = """^(\d{4})-(\d{2})-[Xx]{2}$""".r
    private val YearMonthRegex       = """^(\d{4})-(\d{2})$""".r
    private val UnspecMonthRegex     = """^(\d{4})-[Xx]{2}$""".r
    private val YearOnlyRegex        = """^(\d{4})$""".r
    private val DecadeEdtfRegex      = """^(\d{3})[Xx]$""".r
    private val CenturyEdtfRegex     = """^(\d{2})[Xx]{2}$""".r
    private val DecadeEnglishRegex   = """^(\d{3})0'?[sS]$""".r
    private val MonthYearNumRegex    = """^(\d{1,2})[/.\-\s](\d{4})$""".r
    private val YearMonthNumRegex    = """^(\d{4})[/.\s](\d{1,2})$""".r
    private val MonthNameYearRegex   = """^([A-Za-z]+)\.?,?\s+(\d{4})$""".r
    private val YearMonthNameRegex   = """^(\d{4})\s+([A-Za-z]+)\.?$""".r
    private val HyphenIntervalRegex  = """^(\d{4}(?:[^\s]*))\s*-\s*(\d{4}(?:[^\s]*))$""".r
    private val LeadingApproxRegex   = """^(?:(?:circa|approx\.?|ca\.?|c\.)\s+|~\s*)(.+)$""".r

    private val EnglishMonths: Map[String, Int] = Map(
      "jan" -> 1,  "january"   -> 1,
      "feb" -> 2,  "february"  -> 2,
      "mar" -> 3,  "march"     -> 3,
      "apr" -> 4,  "april"     -> 4,
      "may" -> 5,
      "jun" -> 6,  "june"      -> 6,
      "jul" -> 7,  "july"      -> 7,
      "aug" -> 8,  "august"    -> 8,
      "sep" -> 9,  "sept"      -> 9, "september" -> 9,
      "oct" -> 10, "october"   -> 10,
      "nov" -> 11, "november"  -> 11,
      "dec" -> 12, "december"  -> 12
    )

    def toEpochDay(isoDate: IsoDate): Long =
      LocalDate.of(isoDate.year, isoDate.month, isoDate.day).toEpochDay

    def satisfiesConfig(
      value          : EdtfValue,
      precisions     : Set[EdtfPrecision],
      allowQualifiers: Boolean,
      allowIntervals : Boolean
    ): Boolean =
      value.allPrecisions.subsetOf(precisions) &&
        (allowQualifiers || ! value.hasQualifier) &&
        (allowIntervals  || ! value.isInterval)

    def splitTrailingQualifier(s: String): (String, Option[EdtfQualifier]) =
      if (s.endsWith("%"))
        (s.dropRight(1), Some(EdtfQualifier.UncertainAndApprox))
      else if (s.endsWith("?"))
        (s.dropRight(1), Some(EdtfQualifier.Uncertain))
      else if (s.endsWith("~"))
        (s.dropRight(1), Some(EdtfQualifier.Approximate))
      else
        (s, None)

    def combineQualifiers(
      leadingApprox: Boolean,
      trailingOpt  : Option[EdtfQualifier]
    ): Option[EdtfQualifier] =
      if (! leadingApprox)
        trailingOpt
      else
        trailingOpt match {
          case None                                   => Some(EdtfQualifier.Approximate)
          case Some(EdtfQualifier.Approximate)        => Some(EdtfQualifier.Approximate)
          case Some(EdtfQualifier.Uncertain)          => Some(EdtfQualifier.UncertainAndApprox)
          case Some(EdtfQualifier.UncertainAndApprox) => Some(EdtfQualifier.UncertainAndApprox)
        }

    def parseCanonicalSingle(raw: String): Option[EdtfSingle] = {
      val (core, qualOpt) = splitTrailingQualifier(raw)
      parseCanonicalCore(core, qualOpt)
    }

    def parseCanonicalCore(core: String, qualOpt: Option[EdtfQualifier]): Option[EdtfSingle] =
      core match {
        case ExactDateRegex(yStr, mStr, dStr) =>
          Try(IsoDate(yStr.toInt, mStr.toInt, dStr.toInt)).toOption
            .map(EdtfSingle.ExactDate(_, qualOpt))
        case UnspecDayRegex(yStr, mStr, dTensStr) =>
          val y     = yStr.toInt
          val m     = mStr.toInt
          val dTens = dTensStr.toInt
          if (m >= 1 && m <= 12) {
            val daysInMonth = JYearMonth.of(y, m).lengthOfMonth()
            if (dTens == 3 && daysInMonth < 30) None
            else Some(EdtfSingle.UnspecifiedDayDate(y, m, Some(dTens), qualOpt))
          } else None
        case UnspecDayFullRegex(yStr, mStr) =>
          val y = yStr.toInt
          val m = mStr.toInt
          if (m >= 1 && m <= 12) Some(EdtfSingle.UnspecifiedDayDate(y, m, None, qualOpt))
          else None
        case YearMonthRegex(yStr, mStr) =>
          val y = yStr.toInt
          val m = mStr.toInt
          if (m >= 1 && m <= 12) Some(EdtfSingle.YearMonth(y, m, qualOpt))
          else None
        case UnspecMonthRegex(yStr) =>
          Some(EdtfSingle.UnspecifiedMonthDate(yStr.toInt, qualOpt))
        case YearOnlyRegex(yStr) =>
          Some(EdtfSingle.YearOnly(yStr.toInt, qualOpt))
        case DecadeEdtfRegex(yyyStr) =>
          Some(EdtfSingle.DecadeDate(yyyStr.toInt * 10, qualOpt))
        case CenturyEdtfRegex(yyStr) =>
          Some(EdtfSingle.CenturyDate(yyStr.toInt * 100, qualOpt))
        case _ =>
          None
      }

    def splitUserInterval(trimmed: String, dateFormat: DateFormat): Option[(String, String)] = {
      // 1. En-dash or em-dash always indicates an interval
      val dashIdx = {
        val en = trimmed.indexOf('–')
        if (en >= 0) en else trimmed.indexOf('—')
      }
      if (dashIdx >= 0) {
        val left  = trimmed.substring(0, dashIdx).trimAllToEmpty
        val right = trimmed.substring(dashIdx + 1).trimAllToEmpty
        if (left.nonEmpty && right.nonEmpty) Some((left, right)) else None
      } else {
        // 2. Slash: interval unless it parses as a single locale date or MM/YYYY / YYYY/MM
        val slashCount = trimmed.count(_ == '/')
        if (slashCount == 1) {
          val slashIdx = trimmed.indexOf('/')
          val left     = trimmed.substring(0, slashIdx).trimAllToEmpty
          val right    = trimmed.substring(slashIdx + 1).trimAllToEmpty
          // Disambiguate MM/YYYY (1-2 digit month) and YYYY/MM (1-2 digit month) from intervals
          val looksLikeMonthYear =
            (left.length <= 2 && left.forall(_.isDigit) && right.length == 4 && right.forall(_.isDigit)) ||
            (left.length == 4 && left.forall(_.isDigit) && right.length <= 2 && right.forall(_.isDigit))
          if (! looksLikeMonthYear && left.nonEmpty && right.nonEmpty)
            Some((left, right))
          else
            None
        } else if (slashCount > 1) {
          // Could be a 3-part locale date like 07/31/2026 OR two locale dates separated by / (unlikely);
          // only treat as single if it parses as a single date.
          if (parseUserSingle(trimmed, dateFormat).isDefined) None
          else None
        } else {
          // 3. Hyphen between two 4-digit-year expressions: e.g., 1894-1896 or 1920-05 - 1920-08
          trimmed match {
            case HyphenIntervalRegex(left, right) =>
              Some((left.trimAllToEmpty, right.trimAllToEmpty))
            case _ =>
              None
          }
        }
      }
    }

    def parseUserSingle(raw: String, dateFormat: DateFormat): Option[EdtfSingle] = {
      val trimmed = raw.trimAllToEmpty
      if (trimmed.isEmpty)
        return None

      val (withoutPrefix, leadingApprox) =
        LeadingApproxRegex.findFirstMatchIn(trimmed.toLowerCase) match {
          case Some(m) =>
            val prefixLen = trimmed.length - m.group(1).length
            (trimmed.substring(prefixLen).trimAllToEmpty, true)
          case None =>
            (trimmed, false)
        }

      val (core, trailingQualOpt) = splitTrailingQualifier(withoutPrefix)
      val coreTrimmed             = core.trimAllToEmpty
      val qualOpt                 = combineQualifiers(leadingApprox, trailingQualOpt)

      parseCanonicalCore(coreTrimmed, qualOpt)
        .orElse(parseUserShorthandCore(coreTrimmed, dateFormat, qualOpt))
    }

    def parseUserShorthandCore(
      core      : String,
      dateFormat: DateFormat,
      qualOpt   : Option[EdtfQualifier]
    ): Option[EdtfSingle] =
      core match {
        case DecadeEnglishRegex(yyyStr) =>
          Some(EdtfSingle.DecadeDate(yyyStr.toInt * 10, qualOpt))
        case MonthYearNumRegex(mStr, yStr) =>
          val m = mStr.toInt
          val y = yStr.toInt
          if (m >= 1 && m <= 12) Some(EdtfSingle.YearMonth(y, m, qualOpt))
          else None
        case YearMonthNumRegex(yStr, mStr) =>
          val y = yStr.toInt
          val m = mStr.toInt
          if (m >= 1 && m <= 12) Some(EdtfSingle.YearMonth(y, m, qualOpt))
          else None
        case MonthNameYearRegex(monthName, yStr) =>
          EnglishMonths.get(monthName.toLowerCase).map(EdtfSingle.YearMonth(yStr.toInt, _, qualOpt))
        case YearMonthNameRegex(yStr, monthName) =>
          EnglishMonths.get(monthName.toLowerCase).map(EdtfSingle.YearMonth(yStr.toInt, _, qualOpt))
        case _ =>
          // Try locale exact date format (e.g. 07/31/2026, 31.07.2026, 2026/07/31)
          Try(dateFormat.ParseFormat.parse(core)).toOption.flatMap { accessor =>
            Try(
              IsoDate(
                year  = accessor.get(java.time.temporal.ChronoField.YEAR),
                month = accessor.get(java.time.temporal.ChronoField.MONTH_OF_YEAR),
                day   = accessor.get(java.time.temporal.ChronoField.DAY_OF_MONTH)
              )
            ).toOption.map(EdtfSingle.ExactDate(_, qualOpt))
          }
      }

    def formatMonthYear(year: Int, month: Int, dateFormat: DateFormat): String = {
      val monthStr = if (dateFormat.isPadDayMonthDigits) IsoTime.pad2(month) else month.toString
      val yearStr  = f"$year%04d"
      val sep      = dateFormat.separator
      dateFormat.firstComponent match {
        case DateFormatComponent.Year => s"$yearStr$sep$monthStr"
        case _                        => s"$monthStr$sep$yearStr"
      }
    }

    def generateMonthYearPlaceholder(dateFormat: DateFormat, ymdEn: String, ymd: String): String = {
      val sep = dateFormat.separator
      val raw = dateFormat.firstComponent match {
        case DateFormatComponent.Year => s"YYYY${sep}MM"
        case _                        => s"MM${sep}YYYY"
      }
      raw.translate(ymdEn, ymd)
    }

    def formatEditValue(value: EdtfValue, dateFormat: DateFormat): String =
      value match {
        case s: EdtfSingle =>
          val qualSuffix = s.qualifierOpt.map(_.symbol).getOrElse("")
          s match {
            case EdtfSingle.ExactDate(date, _) =>
              s"${IsoDate.formatDate(date, dateFormat)}$qualSuffix"
            case EdtfSingle.YearMonth(year, month, _) =>
              s"${formatMonthYear(year, month, dateFormat)}$qualSuffix"
            case _ =>
              s.canonicalString
          }
        case EdtfInterval(start, end) =>
          s"${start.canonicalString}/${end.canonicalString}"
      }

    def formatReadonlyValue(
      value        : EdtfValue,
      dateFormat   : DateFormat,
      approxPrefix : String,
      decadePattern: String
    ): String =
      value match {
        case s: EdtfSingle =>
          val core = s match {
            case EdtfSingle.ExactDate(date, _)        => IsoDate.formatDate(date, dateFormat)
            case EdtfSingle.YearMonth(year, month, _) => formatMonthYear(year, month, dateFormat)
            case EdtfSingle.YearOnly(year, _)         => f"$year%04d"
            case EdtfSingle.DecadeDate(startYear, _)  => decadePattern.replace("{decade}", f"$startYear%04d")
            case other                                => other.canonicalString.stripSuffix("?").stripSuffix("~").stripSuffix("%")
          }
          s.qualifierOpt match {
            case None                                   => core
            case Some(EdtfQualifier.Uncertain)          => s"$core?"
            case Some(EdtfQualifier.Approximate)        => s"$approxPrefix $core"
            case Some(EdtfQualifier.UncertainAndApprox) => s"$approxPrefix $core?"
          }
        case EdtfInterval(start, end) =>
          val left  = formatReadonlyValue(start, dateFormat, approxPrefix, decadePattern)
          val right = formatReadonlyValue(end, dateFormat, approxPrefix, decadePattern)
          s"$left – $right"
      }
  }
}

object PartialDateSupportJava {

  //@XPathFunction
  def isValidPartialDate(
    raw                 : String,
    precisionsParam     : String,
    allowQualifiersParam: String,
    allowIntervalsParam : String,
    minYearParam        : String,
    maxYearParam        : String
  ): Boolean =
    PartialDateSupport.isValidPartialDate(
      raw,
      precisionsParam,
      allowQualifiersParam,
      allowIntervalsParam,
      minYearParam,
      maxYearParam
    )

  //@XPathFunction
  def formatReadonlyModePartialDate(
    raw          : String,
    formatString : String,
    approxPrefix : String,
    decadePattern: String
  ): String =
    PartialDateSupport.formatReadonly(raw, formatString, approxPrefix, decadePattern)

  //@XPathFunction
  def generatePlaceholder(
    formatString        : String,
    precisionsParam     : String,
    allowQualifiersParam: String,
    allowIntervalsParam : String,
    ymdEn               : String,
    ymd                 : String
  ): String =
    PartialDateSupport.generatePlaceholder(
      formatString,
      precisionsParam,
      allowQualifiersParam,
      allowIntervalsParam,
      ymdEn,
      ymd
    )
}
