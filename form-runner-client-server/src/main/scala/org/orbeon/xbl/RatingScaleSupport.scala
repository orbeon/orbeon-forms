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


object RatingScaleSupport {

  val DefaultRatingMax  : Int = 5
  val MinRatingMax      : Int = 2
  val MaxRatingMax      : Int = 10
  val MaxRenderedStars  : Int = 20

  val DefaultScaleStart : Int = 1
  val DefaultScaleEnd   : Int = 5
  val MaxScaleItems     : Int = 21

  private def parseBigIntOpt(raw: String): Option[BigInt] =
    Option(raw)
      .flatMap(_.trimAllToOpt)
      .flatMap(s => Try(BigInt(s)).toOption)

  private def parseClampedIntOpt(raw: String): Option[Int] =
    parseBigIntOpt(raw).map { bi =>
      if (bi < BigInt(Int.MinValue))
        Int.MinValue
      else if (bi > BigInt(Int.MaxValue))
        Int.MaxValue
      else
        bi.toInt
    }

  def effectiveRatingMax(maxRaw: String): Int =
    parseClampedIntOpt(maxRaw)
      .filter(_ >= 1)
      .map(m => math.min(m, MaxRenderedStars))
      .getOrElse(DefaultRatingMax)

  def isRatingValueInRange(valueRaw: String, maxRaw: String): Boolean =
    Option(valueRaw).flatMap(_.trimAllToOpt) match {
      case None    => true
      case Some(v) =>
        Try(BigInt(v)).toOption.exists { bi =>
          val maxBound = parseBigIntOpt(maxRaw).filter(_ >= 1).getOrElse(BigInt(DefaultRatingMax))
          bi >= 1 && bi <= maxBound
        }
    }

  def formatRatingDisplayValue(valueRaw: String, maxRaw: String): String =
    Option(valueRaw).flatMap(_.trimAllToOpt) match {
      case None    => ""
      case Some(v) =>
        Try(BigInt(v)).toOption match {
          case Some(bi) => s"$bi / ${effectiveRatingMax(maxRaw)}"
          case None     => v
        }
    }

  def effectiveScaleBounds(startRaw: String, endRaw: String): (Int, Int) = {
    val start  = parseClampedIntOpt(startRaw).getOrElse(DefaultScaleStart)
    val rawEnd = parseClampedIntOpt(endRaw).getOrElse(math.max(start, DefaultScaleEnd))
    val end    = math.min(math.max(start, rawEnd), start + MaxScaleItems - 1)
    (start, end)
  }

  def effectiveScaleStart(startRaw: String): Int =
    effectiveScaleBounds(startRaw, "")._1

  def effectiveScaleEnd(startRaw: String, endRaw: String): Int =
    effectiveScaleBounds(startRaw, endRaw)._2

  def isScaleValueInRange(valueRaw: String, startRaw: String, endRaw: String): Boolean =
    Option(valueRaw).flatMap(_.trimAllToOpt) match {
      case None    => true
      case Some(v) =>
        Try(BigInt(v)).toOption.exists { bi =>
          val startBound = parseBigIntOpt(startRaw).getOrElse(BigInt(DefaultScaleStart))
          val rawEnd     = parseBigIntOpt(endRaw).getOrElse(startBound.max(BigInt(DefaultScaleEnd)))
          val endBound   = startBound.max(rawEnd)
          bi >= startBound && bi <= endBound
        }
    }

  def formatScaleDisplayValue(valueRaw: String, startRaw: String, endRaw: String): String =
    Option(valueRaw).flatMap(_.trimAllToOpt) match {
      case None    => ""
      case Some(v) =>
        Try(BigInt(v)).toOption match {
          case Some(bi) =>
            val (start, end) = effectiveScaleBounds(startRaw, endRaw)
            if (start >= 0)
              s"$bi / $end"
            else
              bi.toString
          case None =>
            v
        }
    }
}

object RatingScaleSupportJava {

  //@XPathFunction
  def effectiveRatingMax(maxRaw: String): Int =
    RatingScaleSupport.effectiveRatingMax(maxRaw)

  //@XPathFunction
  def isRatingValueInRange(valueRaw: String, maxRaw: String): Boolean =
    RatingScaleSupport.isRatingValueInRange(valueRaw, maxRaw)

  //@XPathFunction
  def formatRatingDisplayValue(valueRaw: String, maxRaw: String): String =
    RatingScaleSupport.formatRatingDisplayValue(valueRaw, maxRaw)

  //@XPathFunction
  def effectiveScaleStart(startRaw: String): Int =
    RatingScaleSupport.effectiveScaleStart(startRaw)

  //@XPathFunction
  def effectiveScaleEnd(startRaw: String, endRaw: String): Int =
    RatingScaleSupport.effectiveScaleEnd(startRaw, endRaw)

  //@XPathFunction
  def isScaleValueInRange(valueRaw: String, startRaw: String, endRaw: String): Boolean =
    RatingScaleSupport.isScaleValueInRange(valueRaw, startRaw, endRaw)

  //@XPathFunction
  def formatScaleDisplayValue(valueRaw: String, startRaw: String, endRaw: String): String =
    RatingScaleSupport.formatScaleDisplayValue(valueRaw, startRaw, endRaw)
}
