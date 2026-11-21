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

import java.math.MathContext
import scala.util.Try


object QuantitySupport {

  case class UnitDef(
    code    : String,
    symbol  : String,
    preset  : String,
    toBase  : BigDecimal => BigDecimal,
    fromBase: BigDecimal => BigDecimal
  )

  private val Mc = MathContext.DECIMAL64

  private def linear(code: String, symbol: String, preset: String, factorToBase: BigDecimal): UnitDef =
    UnitDef(
      code     = code,
      symbol   = symbol,
      preset   = preset,
      toBase   = v => (v * factorToBase).round(Mc),
      fromBase = b => BigDecimal(b.bigDecimal.divide(factorToBase.bigDecimal, Mc))
    )

  val PresetNames: List[String] =
    List("mass", "length", "area", "volume", "temperature", "speed", "data", "custom")

  val BuiltinUnitsByPreset: Map[String, List[UnitDef]] = Map(
    // Base: gram (g)
    "mass" -> List(
      linear("mg",  "mg",  "mass", BigDecimal("0.001")),
      linear("g",   "g",   "mass", BigDecimal("1")),
      linear("kg",  "kg",  "mass", BigDecimal("1000")),
      linear("t",   "t",   "mass", BigDecimal("1000000")),
      linear("oz",  "oz",  "mass", BigDecimal("28.349523125")),
      linear("lb",  "lb",  "mass", BigDecimal("453.59237")),
      linear("st",  "st",  "mass", BigDecimal("6350.29318")),
      linear("ton", "ton", "mass", BigDecimal("907184.74"))
    ),
    // Base: meter (m)
    "length" -> List(
      linear("mm", "mm", "length", BigDecimal("0.001")),
      linear("cm", "cm", "length", BigDecimal("0.01")),
      linear("m",  "m",  "length", BigDecimal("1")),
      linear("km", "km", "length", BigDecimal("1000")),
      linear("in", "in", "length", BigDecimal("0.0254")),
      linear("ft", "ft", "length", BigDecimal("0.3048")),
      linear("yd", "yd", "length", BigDecimal("0.9144")),
      linear("mi", "mi", "length", BigDecimal("1609.344"))
    ),
    // Base: square meter (m2)
    "area" -> List(
      linear("mm2", "mm²", "area", BigDecimal("0.000001")),
      linear("cm2", "cm²", "area", BigDecimal("0.0001")),
      linear("m2",  "m²",  "area", BigDecimal("1")),
      linear("ha",  "ha",  "area", BigDecimal("10000")),
      linear("km2", "km²", "area", BigDecimal("1000000")),
      linear("in2", "in²", "area", BigDecimal("0.00064516")),
      linear("ft2", "ft²", "area", BigDecimal("0.09290304")),
      linear("ac",  "ac",  "area", BigDecimal("4046.8564224")),
      linear("mi2", "mi²", "area", BigDecimal("2589988.110336"))
    ),
    // Base: milliliter (mL)
    "volume" -> List(
      linear("mL",    "mL",    "volume", BigDecimal("1")),
      linear("cL",    "cL",    "volume", BigDecimal("10")),
      linear("L",     "L",     "volume", BigDecimal("1000")),
      linear("m3",    "m³",    "volume", BigDecimal("1000000")),
      linear("fl-oz", "fl oz", "volume", BigDecimal("29.5735295625")),
      linear("cup",   "cup",   "volume", BigDecimal("236.5882365")),
      linear("pt",    "pt",    "volume", BigDecimal("473.176473")),
      linear("qt",    "qt",    "volume", BigDecimal("946.352946")),
      linear("gal",   "gal",   "volume", BigDecimal("3785.411784"))
    ),
    // Base: Celsius (C) — affine conversions
    "temperature" -> List(
      UnitDef(
        code     = "C",
        symbol   = "°C",
        preset   = "temperature",
        toBase   = identity,
        fromBase = identity
      ),
      UnitDef(
        code     = "F",
        symbol   = "°F",
        preset   = "temperature",
        toBase   = f => BigDecimal(((f - BigDecimal(32)) * BigDecimal(5)).bigDecimal.divide(BigDecimal(9).bigDecimal, Mc)),
        fromBase = c => BigDecimal((c * BigDecimal(9)).bigDecimal.divide(BigDecimal(5).bigDecimal, Mc)) + BigDecimal(32)
      ),
      UnitDef(
        code     = "K",
        symbol   = "K",
        preset   = "temperature",
        toBase   = k => k - BigDecimal("273.15"),
        fromBase = c => c + BigDecimal("273.15")
      )
    ),
    // Base: meter per second (m/s)
    "speed" -> List(
      linear("m/s",  "m/s",  "speed", BigDecimal("1")),
      linear("km/h", "km/h", "speed", BigDecimal(1000) / BigDecimal(3600)),
      linear("mph",  "mph",  "speed", BigDecimal("0.44704")),
      linear("kn",   "kn",   "speed", BigDecimal("1852") / BigDecimal(3600))
    ),
    // Base: byte (B) — decimal SI (1000-based)
    "data" -> List(
      linear("B",  "B",  "data", BigDecimal("1")),
      linear("KB", "KB", "data", BigDecimal("1000")),
      linear("MB", "MB", "data", BigDecimal("1000000")),
      linear("GB", "GB", "data", BigDecimal("1000000000")),
      linear("TB", "TB", "data", BigDecimal("1000000000000"))
    )
  )

  val AllBuiltinUnitsByCode: Map[String, UnitDef] =
    BuiltinUnitsByPreset.values.flatten.map(u => u.code -> u).toMap

  def parseCustomUnits(customUnitsOpt: Option[String]): List[(String, String)] =
    customUnitsOpt
      .flatMap(_.trimAllToOpt)
      .toList
      .flatMap(_.split("[,\\r\\n]+").toList)
      .flatMap(_.trimAllToOpt)
      .flatMap { entry =>
        entry.indexOf('=') match {
          case idx if idx > 0 =>
            val code   = entry.substring(0, idx).trimAllToEmpty
            val symbol = entry.substring(idx + 1).trimAllToOpt.getOrElse(code)
            if (code.nonEmpty) Some(code -> symbol) else None
          case _ =>
            Some(entry -> entry)
        }
      }
      .distinctBy(_._1)

  def unitsForConfig(
    presetOpt      : Option[String],
    allowedUnitsOpt: Option[String],
    customUnitsOpt : Option[String]
  ): List[(String, String)] = {
    val preset = presetOpt.flatMap(_.trimAllToOpt).getOrElse("mass")
    if (preset == "custom") {
      parseCustomUnits(customUnitsOpt)
    } else {
      val presetUnits = BuiltinUnitsByPreset.getOrElse(preset, BuiltinUnitsByPreset("mass"))
      val allowedSet  = allowedUnitsOpt.flatMap(_.trimAllToOpt).map(_.splitTo[Set]()).getOrElse(Set.empty)
      val filtered    = if (allowedSet.nonEmpty) presetUnits.filter(u => allowedSet.contains(u.code)) else presetUnits
      val effective   = if (filtered.nonEmpty) filtered else presetUnits
      effective.map(u => u.code -> u.symbol)
    }
  }

  def symbolForUnit(unitCode: String, customUnitsOpt: Option[String]): String = {
    val trimmed = Option(unitCode).flatMap(_.trimAllToOpt).getOrElse("")
    if (trimmed.isEmpty) {
      ""
    } else {
      parseCustomUnits(customUnitsOpt)
        .collectFirst { case (code, symbol) if code == trimmed => symbol }
        .orElse(AllBuiltinUnitsByCode.get(trimmed).map(_.symbol))
        .getOrElse(trimmed)
    }
  }

  def convert(value: BigDecimal, fromUnit: String, toUnit: String): Option[BigDecimal] =
    for {
      from <- AllBuiltinUnitsByCode.get(fromUnit.trimAllToEmpty)
      to   <- AllBuiltinUnitsByCode.get(toUnit.trimAllToEmpty)
      if from.preset == to.preset
    } yield {
      if (from.code == to.code)
        value
      else
        to.fromBase(from.toBase(value))
    }

  def formatBigDecimalPlain(value: BigDecimal, digitsAfterDecimalOpt: Option[Int] = None): String = {
    val scaled = digitsAfterDecimalOpt match {
      case Some(digits) if digits >= 0 =>
        value.setScale(digits, BigDecimal.RoundingMode.HALF_UP)
      case _ =>
        // Avoid noisy floating-point tails like 6.441011654000000 while keeping precision
        val stripped = BigDecimal(value.bigDecimal.stripTrailingZeros())
        if (stripped.scale < 0) stripped.setScale(0) else stripped
    }
    scaled.bigDecimal.toPlainString
  }

  def convertValue(
    rawValue             : String,
    fromUnit             : String,
    toUnit               : String,
    digitsAfterDecimalOpt: Option[Int]
  ): Option[String] =
    for {
      raw   <- Option(rawValue).flatMap(_.trimAllToOpt)
      bd    <- Try(BigDecimal(raw)).toOption
      conv  <- convert(bd, fromUnit, toUnit)
    } yield
      formatBigDecimalPlain(conv, digitsAfterDecimalOpt)

  def formatReadonly(
    displayNumber : String,
    unitCode      : String,
    customUnitsOpt: Option[String]
  ): String = {
    val numOpt = Option(displayNumber).flatMap(_.trimAllToOpt)
    numOpt match {
      case None => ""
      case Some(num) =>
        val sym = symbolForUnit(unitCode, customUnitsOpt)
        if (sym.nonEmpty) s"$num $sym" else num
    }
  }
}

object QuantitySupportJava {

  //@XPathFunction
  def unitCodes(
    preset      : String,
    allowedUnits: String,
    customUnits : String
  ): Array[String] =
    QuantitySupport
      .unitsForConfig(
        presetOpt       = Option(preset),
        allowedUnitsOpt = Option(allowedUnits),
        customUnitsOpt  = Option(customUnits)
      )
      .map(_._1)
      .toArray

  //@XPathFunction
  def symbolForUnit(
    unitCode   : String,
    customUnits: String
  ): String =
    QuantitySupport.symbolForUnit(unitCode, Option(customUnits))

  //@XPathFunction
  def convertValue(
    rawValue          : String,
    fromUnit          : String,
    toUnit            : String,
    digitsAfterDecimal: String
  ): String = {
    val digitsOpt = Option(digitsAfterDecimal).flatMap(_.trimAllToOpt).flatMap(s => Try(s.toInt).toOption)
    QuantitySupport
      .convertValue(rawValue, fromUnit, toUnit, digitsOpt)
      .getOrElse(Option(rawValue).getOrElse(""))
  }

  //@XPathFunction
  def formatReadonly(
    displayNumber: String,
    unitCode     : String,
    customUnits  : String
  ): String =
    QuantitySupport.formatReadonly(displayNumber, unitCode, Option(customUnits))
}
