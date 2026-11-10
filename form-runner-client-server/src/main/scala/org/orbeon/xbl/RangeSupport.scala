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


object RangeSupport {

  val DefaultStart: BigDecimal = BigDecimal(0)
  val DefaultEnd  : BigDecimal = BigDecimal(100)
  val DefaultStep : BigDecimal = BigDecimal(1)

  case class RangeParams(
    start   : BigDecimal,
    end     : BigDecimal,
    stepAttr: String
  ) {
    def startAttr: String = toPlainDecimalString(start)
    def endAttr  : String = toPlainDecimalString(end)
  }

  def parseDecimalOpt(raw: String): Option[BigDecimal] =
    Option(raw)
      .flatMap(_.trimAllToOpt)
      .flatMap(s => Try(BigDecimal(s)).toOption)

  def toPlainDecimalString(bd: BigDecimal): String =
    if (bd == 0)
      "0"
    else
      bd.bigDecimal.stripTrailingZeros.toPlainString

  def effectiveRangeParams(startRaw: String, endRaw: String, stepRaw: String): RangeParams = {
    val start     = parseDecimalOpt(startRaw).getOrElse(DefaultStart)
    val parsedEnd = parseDecimalOpt(endRaw).getOrElse(DefaultEnd)
    val end       = if (parsedEnd < start) start else parsedEnd
    val stepAttr  =
      if (Option(stepRaw).flatMap(_.trimAllToOpt).exists(_.equalsIgnoreCase("any")))
        "any"
      else
        toPlainDecimalString(parseDecimalOpt(stepRaw).filter(_ > 0).getOrElse(DefaultStep))

    RangeParams(
      start    = start,
      end      = end,
      stepAttr = stepAttr
    )
  }

  def effectiveStart(startRaw: String): String =
    toPlainDecimalString(parseDecimalOpt(startRaw).getOrElse(DefaultStart))

  def effectiveEnd(startRaw: String, endRaw: String): String =
    effectiveRangeParams(startRaw, endRaw, "").endAttr

  def effectiveStep(stepRaw: String): String =
    effectiveRangeParams("", "", stepRaw).stepAttr

  def initialSliderValue(valueRaw: String, startRaw: String, endRaw: String): String = {
    val params = effectiveRangeParams(startRaw, endRaw, "")
    parseDecimalOpt(valueRaw) match {
      case Some(v) =>
        val clamped = v.max(params.start).min(params.end)
        toPlainDecimalString(clamped)
      case None =>
        params.startAttr
    }
  }

  def isValueInRange(valueRaw: String, startRaw: String, endRaw: String): Boolean =
    parseDecimalOpt(valueRaw) match {
      case None =>
        true
      case Some(v) =>
        val params = effectiveRangeParams(startRaw, endRaw, "")
        v >= params.start && v <= params.end
    }

  def progressPercent(valueRaw: String, startRaw: String, endRaw: String): Option[Double] =
    parseDecimalOpt(valueRaw).map { v =>
      val params = effectiveRangeParams(startRaw, endRaw, "")
      if (params.end <= params.start)
        0.0
      else
        (((v - params.start) / (params.end - params.start)).toDouble * 100.0).max(0.0).min(100.0)
    }

  def progressCssPercent(valueRaw: String, startRaw: String, endRaw: String): String =
    progressPercent(valueRaw, startRaw, endRaw) match {
      case None =>
        "0%"
      case Some(pct) =>
        val rounded = BigDecimal(math.round(pct * 10000.0)) / BigDecimal(10000)
        s"${toPlainDecimalString(rounded)}%"
    }

  def formatNumberPlain(valueRaw: String): Option[String] =
    Option(valueRaw)
      .flatMap(_.trimAllToOpt)
      .map { s =>
        Try(BigDecimal(s)).toOption.map(toPlainDecimalString).getOrElse(s)
      }

  def formatDisplayValue(valueRaw: String, prefixRaw: String, suffixRaw: String): String =
    formatNumberPlain(valueRaw) match {
      case None =>
        ""
      case Some(numStr) =>
        List(
          Option(prefixRaw).flatMap(_.trimAllToOpt),
          Some(numStr),
          Option(suffixRaw).flatMap(_.trimAllToOpt)
        ).flatten.mkString(" ")
    }
}

object RangeSupportJava {

  //@XPathFunction
  def effectiveStart(startRaw: String): String =
    RangeSupport.effectiveStart(startRaw)

  //@XPathFunction
  def effectiveEnd(startRaw: String, endRaw: String): String =
    RangeSupport.effectiveEnd(startRaw, endRaw)

  //@XPathFunction
  def effectiveStep(stepRaw: String): String =
    RangeSupport.effectiveStep(stepRaw)

  //@XPathFunction
  def initialSliderValue(valueRaw: String, startRaw: String, endRaw: String): String =
    RangeSupport.initialSliderValue(valueRaw, startRaw, endRaw)

  //@XPathFunction
  def isValueInRange(valueRaw: String, startRaw: String, endRaw: String): Boolean =
    RangeSupport.isValueInRange(valueRaw, startRaw, endRaw)

  //@XPathFunction
  def progressCssPercent(valueRaw: String, startRaw: String, endRaw: String): String =
    RangeSupport.progressCssPercent(valueRaw, startRaw, endRaw)

  //@XPathFunction
  def formatDisplayValue(valueRaw: String, prefixRaw: String, suffixRaw: String): String =
    RangeSupport.formatDisplayValue(valueRaw, prefixRaw, suffixRaw)
}
