/**
  * Copyright (C) 2019 Orbeon, Inc.
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

import scala.util.Try


case class NumberExternalValue(
  displayValue     : String,
  editValue        : String,
  decimalSeparator : Char, // Q: Would prevent characters beyond the basic plane; change?
)

object NumberStepperSupport {

  val DefaultStep: BigDecimal = BigDecimal(1)

  def parseDecimal(s: String, decimalSeparator: Char): Option[BigDecimal] = {
    val trimmed = s.trim
    if (trimmed.isEmpty)
      None
    else {
      val normalized = if (decimalSeparator == '.') trimmed else trimmed.replace(decimalSeparator, '.')
      Try(BigDecimal(normalized)).toOption
    }
  }

  def parseParamDecimal(s: String): Option[BigDecimal] =
    parseDecimal(s, '.')

  def effectiveStep(stepAttr: String): BigDecimal =
    parseParamDecimal(stepAttr).filter(_ > 0).getOrElse(DefaultStep)

  def clamp(value: BigDecimal, minOpt: Option[BigDecimal], maxOpt: Option[BigDecimal]): BigDecimal = {
    val afterMin = minOpt.fold(value)(value.max)
    maxOpt.fold(afterMin)(afterMin.min)
  }

  def canStep(
    currentEditValue: String,
    decimalSeparator: Char,
    direction       : Int,
    minOpt          : Option[BigDecimal],
    maxOpt          : Option[BigDecimal]
  ): Boolean =
    parseDecimal(currentEditValue, decimalSeparator) match {
      case Some(v) if direction < 0 => minOpt.forall(v > _)
      case Some(v) if direction > 0 => maxOpt.forall(v < _)
      case _                        => true
    }

  def formatEditDecimal(value: BigDecimal, decimalSeparator: Char): String = {
    val bd    = if (value == BigDecimal(0)) java.math.BigDecimal.ZERO else value.bigDecimal.stripTrailingZeros
    val plain = bd.toPlainString
    if (decimalSeparator == '.') plain else plain.replace('.', decimalSeparator)
  }

  def stepEditValue(
    currentEditValue: String,
    decimalSeparator: Char,
    direction       : Int,
    step            : BigDecimal,
    minOpt          : Option[BigDecimal],
    maxOpt          : Option[BigDecimal]
  ): Option[String] =
    if (direction == 0)
      None
    else
      parseDecimal(currentEditValue, decimalSeparator) match {
        case Some(current) =>
          val candidate = clamp(current + (step * direction), minOpt, maxOpt)
          if (candidate == current)
            None
          else
            Some(formatEditDecimal(candidate, decimalSeparator))
        case None =>
          val initial =
            if (direction > 0 && minOpt.exists(_ > 0))
              minOpt.get
            else if (direction < 0 && maxOpt.exists(_ < 0))
              maxOpt.get
            else
              clamp(BigDecimal(0) + (step * direction), minOpt, maxOpt)
          Some(formatEditDecimal(initial, decimalSeparator))
      }
}
