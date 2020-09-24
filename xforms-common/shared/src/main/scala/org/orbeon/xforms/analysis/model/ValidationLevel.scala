/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms.analysis.model

import enumeratum.{Enum, EnumEntry}

import scala.collection.immutable.List
import scala.collection.compat._


sealed abstract class ValidationLevel(override val entryName: String) extends EnumEntry

object ValidationLevel extends Enum[ValidationLevel] {

  val values = findValues

  // In order of decreasing priority
  case object ErrorLevel   extends ValidationLevel("error")
  case object WarningLevel extends ValidationLevel("warning")
  case object InfoLevel    extends ValidationLevel("info")

  val LevelsByPriority: List[ValidationLevel]        = values.to(List)
  val LevelByName     : Map[String, ValidationLevel] = LevelsByPriority map (l => l.entryName -> l) toMap

  implicit object ValidationLevelOrdering extends Ordering[ValidationLevel] {
    override def compare(x: ValidationLevel, y: ValidationLevel): Int =
      if (x == y) 0
      else if (y == ErrorLevel || y == WarningLevel && x == InfoLevel) -1
      else 1
  }
}
