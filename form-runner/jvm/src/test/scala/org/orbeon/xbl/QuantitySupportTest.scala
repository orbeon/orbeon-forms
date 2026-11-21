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

import org.scalatest.funspec.AnyFunSpec


class QuantitySupportTest extends AnyFunSpec {

  describe("QuantitySupport unit presets and custom units") {

    it("must resolve built-in presets and subset filtering") {
      val massAll = QuantitySupport.unitsForConfig(Some("mass"), None, None)
      assert(massAll.map(_._1) == List("mg", "g", "kg", "t", "oz", "lb", "st", "ton"))

      val massSubset = QuantitySupport.unitsForConfig(Some("mass"), Some("lb oz kg g"), None)
      assert(massSubset.map(_._1) == List("g", "kg", "oz", "lb"))

      val tempAll = QuantitySupport.unitsForConfig(Some("temperature"), None, None)
      assert(tempAll == List("C" -> "°C", "F" -> "°F", "K" -> "K"))

      val dataAll = QuantitySupport.unitsForConfig(Some("data"), None, None)
      assert(dataAll.map(_._1) == List("B", "KB", "MB", "GB", "TB"))
    }

    it("must parse custom units with optional display symbols") {
      assert(
        QuantitySupport.unitsForConfig(Some("custom"), None, Some("cpi=CPI, dpi=DPI, ppm")) ==
          List("cpi" -> "CPI", "dpi" -> "DPI", "ppm" -> "ppm")
      )
      assert(QuantitySupport.symbolForUnit("cpi", Some("cpi=CPI, dpi=DPI")) == "CPI")
      assert(QuantitySupport.symbolForUnit("m2", None) == "m²")
    }
  }

  describe("QuantitySupport exact unit conversions") {

    it("must convert mass, length, area, volume, speed, and data units accurately") {
      assert(QuantitySupport.convertValue("1", "lb", "g", None).contains("453.59237"))
      assert(QuantitySupport.convertValue("1", "kg", "g", None).contains("1000"))
      assert(QuantitySupport.convertValue("14.2", "lb", "kg", Some(2)).contains("6.44"))
      assert(QuantitySupport.convertValue("1", "in", "cm", None).contains("2.54"))
      assert(QuantitySupport.convertValue("1", "ft", "in", None).contains("12"))
      assert(QuantitySupport.convertValue("1", "km2", "ha", None).contains("100"))
      assert(QuantitySupport.convertValue("1", "L", "mL", None).contains("1000"))
      assert(QuantitySupport.convertValue("1", "GB", "MB", None).contains("1000"))
      assert(QuantitySupport.convertValue("36", "km/h", "m/s", Some(2)).contains("10.00"))
    }

    it("must handle affine temperature conversions") {
      assert(QuantitySupport.convertValue("0", "C", "F", Some(2)).contains("32.00"))
      assert(QuantitySupport.convertValue("100", "C", "F", Some(2)).contains("212.00"))
      assert(QuantitySupport.convertValue("32", "F", "C", Some(2)).contains("0.00"))
      assert(QuantitySupport.convertValue("0", "C", "K", Some(2)).contains("273.15"))
      assert(QuantitySupport.convertValue("273.15", "K", "C", Some(2)).contains("0.00"))
    }

    it("must return None for incompatible or unknown units") {
      assert(QuantitySupport.convert(BigDecimal(10), "lb", "m").isEmpty)
      assert(QuantitySupport.convert(BigDecimal(10), "lb", "unknown").isEmpty)
      assert(QuantitySupport.convertValue("not-a-number", "lb", "kg", None).isEmpty)
    }
  }

  describe("QuantitySupport readonly formatting") {

    it("must format numeric value with unit symbol and return empty for blank") {
      assert(QuantitySupport.formatReadonly("19.80", "lb", None) == "19.80 lb")
      assert(QuantitySupport.formatReadonly("21", "C", None) == "21 °C")
      assert(QuantitySupport.formatReadonly("10", "cpi", Some("cpi=CPI")) == "10 CPI")
      assert(QuantitySupport.formatReadonly("", "lb", None) == "")
    }
  }
}
