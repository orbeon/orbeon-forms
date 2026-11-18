/**
 * Copyright (C) 2026 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr


final case class ConsoleChartSeries(
  key       : String,
  values    : List[Double],
  chartType : String,  // "line" | "area" | "bar"
  colorSlot : Int,     // 0..6 semantic theme slot
  yAxisId   : String  = "y",
  dashed    : Boolean = false
)

final case class ConsoleChartSpec(
  kind             : String,       // "timeseries" | "hbar"
  valueUnit        : String,       // "bytes" | "rate" | "count" | "duration-ms"
  secondaryUnitOpt : Option[String] = None,
  stacked          : Boolean        = false,
  window           : String         = "1h",
  timestampsMs     : List[Long]     = Nil,
  categories       : List[String]   = Nil,
  fullCategories   : List[String]   = Nil,
  categoryMeta     : List[String]   = Nil,
  series           : List[ConsoleChartSeries] = Nil
)
