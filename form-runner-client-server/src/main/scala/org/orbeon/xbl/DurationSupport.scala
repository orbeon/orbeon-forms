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

import org.orbeon.oxf.util.StringUtils.*

import scala.util.Try
import scala.util.matching.Regex


object DurationSupport {

  sealed trait DurationUnit {
    def name: String
  }

  object DurationUnit {
    case object Days    extends DurationUnit { val name = "days"    }
    case object Hours   extends DurationUnit { val name = "hours"   }
    case object Minutes extends DurationUnit { val name = "minutes" }
    case object Seconds extends DurationUnit { val name = "seconds" }

    val AllInOrder: List[DurationUnit] = List(Days, Hours, Minutes, Seconds)

    def fromNameOpt(name: String): Option[DurationUnit] =
      AllInOrder.find(_.name == name.toLowerCase)
  }

  import DurationUnit.*

  val DefaultFieldsParam: String             = "hours minutes"
  val DefaultFields     : List[DurationUnit] = List(Hours, Minutes)

  private val SecondsPerDay   : BigDecimal = BigDecimal(86400)
  private val SecondsPerHour  : BigDecimal = BigDecimal(3600)
  private val SecondsPerMinute: BigDecimal = BigDecimal(60)

  private val IsoDayTimeDurationRegex: Regex =
    """^([+-])?P(?:([0-9]+)D)?(?:T(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9]+(?:\.[0-9]+)?)S)?)?$""".r

  case class ParsedDuration(
    negative: Boolean,
    days    : BigInt,
    hours   : BigInt,
    minutes : BigInt,
    seconds : BigDecimal
  ) {
    def totalSeconds: BigDecimal =
      BigDecimal(days) * SecondsPerDay +
        BigDecimal(hours) * SecondsPerHour +
        BigDecimal(minutes) * SecondsPerMinute +
        seconds

    def isZero: Boolean =
      totalSeconds == 0
  }

  case class DurationFieldValues(
    days   : String,
    hours  : String,
    minutes: String,
    seconds: String
  ) {
    def get(unit: DurationUnit): String = unit match {
      case Days    => days
      case Hours   => hours
      case Minutes => minutes
      case Seconds => seconds
    }

    def updated(unit: DurationUnit, value: String): DurationFieldValues = unit match {
      case Days    => copy(days    = value)
      case Hours   => copy(hours   = value)
      case Minutes => copy(minutes = value)
      case Seconds => copy(seconds = value)
    }
  }

  object DurationFieldValues {
    val Empty: DurationFieldValues = DurationFieldValues("", "", "", "")
  }

  def parseFields(fieldsParam: String): List[DurationUnit] = {
    val tokens =
      Option(fieldsParam)
        .map(_.splitTo[Set]().map(_.toLowerCase))
        .getOrElse(Set.empty)
    val parsed = AllInOrder.filter(u => tokens.contains(u.name))
    if (parsed.nonEmpty) parsed else DefaultFields
  }

  def parseIsoDuration(raw: String): Option[ParsedDuration] =
    Option(raw).flatMap(_.trimAllToOpt).flatMap { trimmed =>
      trimmed match {
        case IsoDayTimeDurationRegex(signOpt, dOpt, hOpt, mOpt, sOpt)
          if (dOpt ne null) || (hOpt ne null) || (mOpt ne null) || (sOpt ne null) =>
          for {
            d <- Option(dOpt).fold[Option[BigInt]](Some(BigInt(0)))(s => Try(BigInt(s)).toOption)
            h <- Option(hOpt).fold[Option[BigInt]](Some(BigInt(0)))(s => Try(BigInt(s)).toOption)
            m <- Option(mOpt).fold[Option[BigInt]](Some(BigInt(0)))(s => Try(BigInt(s)).toOption)
            s <- Option(sOpt).fold[Option[BigDecimal]](Some(BigDecimal(0)))(s => Try(BigDecimal(s)).toOption)
          } yield ParsedDuration(
            negative = signOpt == "-",
            days     = d,
            hours    = h,
            minutes  = m,
            seconds  = s
          )
        case _ =>
          None
      }
    }

  def toPlainDecimalString(bd: BigDecimal): String =
    if (bd == 0)
      "0"
    else
      bd.bigDecimal.stripTrailingZeros.toPlainString

  def decomposeSeconds(
    totalSeconds: BigDecimal,
    fields      : List[DurationUnit]
  ): (BigInt, BigInt, BigInt, BigDecimal) = {
    val active = if (fields.nonEmpty) fields.toSet else DefaultFields.toSet
    var rem    = totalSeconds.abs

    val d =
      if (active.contains(Days)) {
        val v = (rem / SecondsPerDay).toBigInt
        rem -= BigDecimal(v) * SecondsPerDay
        v
      } else BigInt(0)

    val h =
      if (active.contains(Hours)) {
        val v = (rem / SecondsPerHour).toBigInt
        rem -= BigDecimal(v) * SecondsPerHour
        v
      } else BigInt(0)

    val m =
      if (active.contains(Minutes)) {
        val v = (rem / SecondsPerMinute).toBigInt
        rem -= BigDecimal(v) * SecondsPerMinute
        v
      } else BigInt(0)

    val s =
      if (active.contains(Seconds))
        rem
      else
        BigDecimal(0)

    (d, h, m, s)
  }

  def toFieldValues(raw: String, fields: List[DurationUnit]): DurationFieldValues = {
    val effectiveFields = if (fields.nonEmpty) fields else DefaultFields
    val activeSet       = effectiveFields.toSet

    parseIsoDuration(raw) match {
      case None =>
        DurationFieldValues.Empty
      case Some(parsed) =>
        val (d, h, m, s) = decomposeSeconds(parsed.totalSeconds, effectiveFields)
        DurationFieldValues(
          days    = if (activeSet.contains(Days))    d.toString              else "",
          hours   = if (activeSet.contains(Hours))   h.toString              else "",
          minutes = if (activeSet.contains(Minutes)) m.toString              else "",
          seconds = if (activeSet.contains(Seconds)) toPlainDecimalString(s) else ""
        )
    }
  }

  private def parseNonNegativeBigIntOpt(raw: String): Option[Option[BigInt]] =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None    => Some(None)
      case Some(s) =>
        if (s.forall(_.isDigit))
          Try(BigInt(s)).toOption.filter(_ >= 0).map(Some(_))
        else
          None
    }

  private def parseNonNegativeDecimalOpt(raw: String): Option[Option[BigDecimal]] =
    Option(raw).flatMap(_.trimAllToOpt) match {
      case None    => Some(None)
      case Some(s) =>
        Try(BigDecimal(s)).toOption.filter(_ >= 0).map(Some(_))
    }

  def fromFieldValues(values: DurationFieldValues, fields: List[DurationUnit]): String = {
    val effectiveFields = if (fields.nonEmpty) fields else DefaultFields
    val activeSet       = effectiveFields.toSet

    val dParsedOpt = if (activeSet.contains(Days))    parseNonNegativeBigIntOpt(values.days)       else Some(None)
    val hParsedOpt = if (activeSet.contains(Hours))   parseNonNegativeBigIntOpt(values.hours)      else Some(None)
    val mParsedOpt = if (activeSet.contains(Minutes)) parseNonNegativeBigIntOpt(values.minutes)    else Some(None)
    val sParsedOpt = if (activeSet.contains(Seconds)) parseNonNegativeDecimalOpt(values.seconds)   else Some(None)

    (dParsedOpt, hParsedOpt, mParsedOpt, sParsedOpt) match {
      case (Some(None), Some(None), Some(None), Some(None)) =>
        ""
      case (Some(dOpt), Some(hOpt), Some(mOpt), Some(sOpt)) =>
        val totalSeconds =
          BigDecimal(dOpt.getOrElse(BigInt(0))) * SecondsPerDay +
            BigDecimal(hOpt.getOrElse(BigInt(0))) * SecondsPerHour +
            BigDecimal(mOpt.getOrElse(BigInt(0))) * SecondsPerMinute +
            sOpt.getOrElse(BigDecimal(0))

        val (d, h, m, s) = decomposeSeconds(totalSeconds, effectiveFields)
        formatIsoDuration(negative = false, d, h, m, s, effectiveFields)
      case _ =>
        "INVALID_DURATION"
    }
  }

  def formatIsoDuration(
    negative: Boolean,
    days    : BigInt,
    hours   : BigInt,
    minutes : BigInt,
    seconds : BigDecimal,
    fields  : List[DurationUnit]
  ): String = {
    val effectiveFields = if (fields.nonEmpty) fields else DefaultFields
    val signPrefix      = if (negative && (days > 0 || hours > 0 || minutes > 0 || seconds > 0)) "-" else ""

    if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
      effectiveFields.lastOption.getOrElse(Minutes) match {
        case Days    => "P0D"
        case Hours   => "PT0H"
        case Minutes => "PT0M"
        case Seconds => "PT0S"
      }
    } else {
      val dayPart  = if (days > 0) s"${days}D" else ""
      val hPart    = if (hours > 0) s"${hours}H" else ""
      val mPart    = if (minutes > 0) s"${minutes}M" else ""
      val sPart    = if (seconds > 0) s"${toPlainDecimalString(seconds)}S" else ""
      val timePart = if (hPart.nonEmpty || mPart.nonEmpty || sPart.nonEmpty) s"T$hPart$mPart$sPart" else ""
      s"${signPrefix}P$dayPart$timePart"
    }
  }

  def stepUnitValue(currentFieldVal: String, delta: Int): String = {
    val current =
      Option(currentFieldVal)
        .flatMap(_.trimAllToOpt)
        .flatMap(s => Try(BigInt(s)).toOption)
        .filter(_ >= 0)
        .getOrElse(BigInt(0))
    val next = (current + BigInt(delta)).max(BigInt(0))
    next.toString
  }

  def formatReadonly(
    valueRaw   : String,
    fieldsParam: String,
    daysUnit   : String,
    hoursUnit  : String,
    minutesUnit: String,
    secondsUnit: String
  ): String =
    Option(valueRaw).flatMap(_.trimAllToOpt) match {
      case None =>
        ""
      case Some(trimmed) =>
        parseIsoDuration(trimmed) match {
          case None =>
            trimmed
          case Some(parsed) =>
            val configuredFields = parseFields(fieldsParam)
            // Ensure any non-zero units from the stored ISO value are also represented
            val effectiveFields = AllInOrder.filter {
              case Days    => configuredFields.contains(Days)    || (parsed.days > 0 && ! configuredFields.contains(Hours) && ! configuredFields.contains(Minutes) && ! configuredFields.contains(Seconds))
              case Hours   => configuredFields.contains(Hours)   || (parsed.hours > 0 && ! configuredFields.contains(Minutes) && ! configuredFields.contains(Seconds))
              case Minutes => configuredFields.contains(Minutes) || (parsed.minutes > 0 && ! configuredFields.contains(Seconds))
              case Seconds => configuredFields.contains(Seconds) || parsed.seconds > 0 || (parsed.totalSeconds. remainder(SecondsPerMinute) != 0)
            }
            val finalFields  = if (effectiveFields.nonEmpty) effectiveFields else configuredFields
            val (d, h, m, s) = decomposeSeconds(parsed.totalSeconds, finalFields)

            def unitLabel(u: DurationUnit): String = u match {
              case Days    => Option(daysUnit).flatMap(_.trimAllToOpt).getOrElse("d")
              case Hours   => Option(hoursUnit).flatMap(_.trimAllToOpt).getOrElse("h")
              case Minutes => Option(minutesUnit).flatMap(_.trimAllToOpt).getOrElse("min")
              case Seconds => Option(secondsUnit).flatMap(_.trimAllToOpt).getOrElse("sec")
            }

            val nonZeroParts = List(
              if (d > 0) Some(s"$d ${unitLabel(Days)}") else None,
              if (h > 0) Some(s"$h ${unitLabel(Hours)}") else None,
              if (m > 0) Some(s"$m ${unitLabel(Minutes)}") else None,
              if (s > 0) Some(s"${toPlainDecimalString(s)} ${unitLabel(Seconds)}") else None
            ).flatten

            val body =
              if (nonZeroParts.nonEmpty)
                nonZeroParts.mkString(" ")
              else {
                val smallestUnit = configuredFields.lastOption.getOrElse(Minutes)
                s"0 ${unitLabel(smallestUnit)}"
              }

            if (parsed.negative && nonZeroParts.nonEmpty) s"-$body" else body
        }
    }
}

object DurationSupportJava {

  //@XPathFunction
  def formatReadonly(
    valueRaw   : String,
    fieldsParam: String,
    daysUnit   : String,
    hoursUnit  : String,
    minutesUnit: String,
    secondsUnit: String
  ): String =
    DurationSupport.formatReadonly(
      valueRaw    = valueRaw,
      fieldsParam = fieldsParam,
      daysUnit    = daysUnit,
      hoursUnit   = hoursUnit,
      minutesUnit = minutesUnit,
      secondsUnit = secondsUnit
    )
}
