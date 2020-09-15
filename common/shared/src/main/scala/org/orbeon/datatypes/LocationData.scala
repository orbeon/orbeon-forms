/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.datatypes

import java.{lang => jl}


trait LocationData {
  val file : String
  val line : Int
  val col  : Int
}

object LocationData {

  def asString(ld: LocationData): String = {
    val sb = new jl.StringBuilder

    val hasLine =
      if (ld.line > 0) {
        sb.append("line ")
        sb.append(ld.line.toString)
        true
      } else {
        false
      }

    val hasColumn =
      if (ld.col > 0) {
        if (hasLine)
          sb.append(", ")
        sb.append("column ")
        sb.append(ld.col.toString)
        true
      } else {
        false
      }

    if (ld.file ne null) {
      if (hasLine || hasColumn)
        sb.append(" of ")

      sb.append(ld.file)
    }

    sb.toString
  }

}

case class BasicLocationData(file: String, line: Int, col: Int) extends LocationData {
  override def toString: String =
    LocationData.asString(this)
}
