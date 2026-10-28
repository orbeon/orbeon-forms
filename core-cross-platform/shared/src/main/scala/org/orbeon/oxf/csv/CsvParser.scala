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
package org.orbeon.oxf.csv

object CsvParser {

  def parse(csv: String): List[List[String]] = {
    if (csv.isEmpty) return Nil

    val rows         = List.newBuilder[List[String]]
    val currentRow   = List.newBuilder[String]
    val currentField = new java.lang.StringBuilder()

    var inQuotes     = false
    var hasQuotes    = false
    var rowHadQuotes = false
    var i            = 0
    val len          = csv.length

    def finishField(): Unit = {
      val raw = currentField.toString
      val fieldVal = if (hasQuotes) raw else raw.trim
      currentRow += fieldVal
      currentField.setLength(0)
      if (hasQuotes) rowHadQuotes = true
      hasQuotes = false
    }

    def finishRow(): Unit = {
      finishField()
      val row = currentRow.result()
      currentRow.clear()
      if (row != List("") || rowHadQuotes) {
        rows += row
      }
      rowHadQuotes = false
    }

    def isOnlyWhitespace(sb: java.lang.StringBuilder): Boolean = {
      var j = 0
      while (j < sb.length()) {
        if (!sb.charAt(j).isWhitespace) return false
        j += 1
      }
      true
    }

    while (i < len) {
      val c = csv.charAt(i)
      if (inQuotes) {
        if (c == '"') {
          if (i + 1 < len && csv.charAt(i + 1) == '"') {
            currentField.append('"')
            i += 1
          } else {
            inQuotes = false
          }
        } else {
          currentField.append(c)
        }
      } else {
        c match {
          case '"' =>
            if (isOnlyWhitespace(currentField)) {
              currentField.setLength(0)
            }
            inQuotes  = true
            hasQuotes = true
          case ',' =>
            finishField()
          case '\r' =>
            if (i + 1 < len && csv.charAt(i + 1) == '\n') {
              i += 1
            }
            finishRow()
          case '\n' =>
            finishRow()
          case other =>
            if (!hasQuotes || !other.isWhitespace) {
              currentField.append(other)
            }
        }
      }
      i += 1
    }

    if (currentField.length() > 0 || hasQuotes || currentRow.result().nonEmpty) {
      finishField()
      val row = currentRow.result()
      if (row != List("") || rowHadQuotes) {
        rows += row
      }
    }

    rows.result()
  }
}
