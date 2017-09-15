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
package org.orbeon.oxf.fb

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Cell(td: NodeInfo, rowspan: Int, colspan: Int, missing: Boolean) {

  import Cell._

  def underlyingRowspan = getNormalizedRowspan(td)
  def underlyingRowspan_= (newRowSpan: Int): Unit = toggleAttribute(td, "rowspan", newRowSpan.toString, newRowSpan > 1)
}

object Cell {


  private def getNormalizedSpan(td: NodeInfo, name: String) =  td attValueOpt name map (_.toInt) getOrElse 1

  private def getNormalizedRowspan(td: NodeInfo) =  getNormalizedSpan(td, "rowspan")
  private def getNormalizedColspan(td: NodeInfo) =  getNormalizedSpan(td, "colspan")

  // For the previous row of prepared cells, and a new row of tds, return the new row of prepared cells
  private def newCellsRow(previousRow: List[Cell], tds: List[NodeInfo]): List[Cell] = previousRow match {

    case Nil ⇒
      // First row: start with initial rowspans
      tds flatMap { td ⇒

        val colspan = getNormalizedColspan(td)
        val rowspan = getNormalizedRowspan(td)

        val firstCell =
          Cell(td, rowspan, colspan, missing = false)

        val spanCells =
          if (colspan == 1)
            Nil
          else
            (1 until colspan).reverse map { index ⇒
              Cell(td, rowspan, colspan - index, missing = true)
            } toList

        firstCell :: spanCells
      }
    case _ ⇒
      // Subsequent rows

      // Assumption is that all rows have the same width
      // TODO: Handle error conditions. Throw if error is found.

      val previousRowIt = previousRow.toIterator
      val tdsIt         = tds.toIterator

      val result = ListBuffer[Cell]()

      previousRowIt foreach {
        // Previous row's cell does NOT span over this current row
        case Cell(_, 1, colspan, _) ⇒

          val td = tdsIt.next()

          val colspan = getNormalizedColspan(td)
          val rowspan = getNormalizedRowspan(td)

          // Always at least one cell
          result += Cell(td, rowspan, colspan, missing = false)

          // Add cells for remaining colspans
          (1 until colspan).reverse foreach { remainingColspan ⇒
            result += Cell(td, rowspan, remainingColspan, missing = true)
            // Advance the previous row iterator explicitly!
            previousRowIt.next()
          }

        // Previous row's cell DOES span over this current row
        case Cell(td, rowspan, colspan, _) ⇒
          // Create a hole
          result += Cell(td, rowspan - 1, colspan, missing = true)
      }

      result.result()
  }

  // Get cell/rowspan information for all the rows in the grid
  def getAllRowCells(grid: NodeInfo): List[List[Cell]] = {

    val trs  = grid / "*:tr" toList
    val rows = trs map (_ / "*:td" toList)

    // Accumulate the result for each row as we go
    val result = mutable.ListBuffer[List[Cell]]()

    rows.foldLeft[List[Cell]](Nil) { (previousRow, tds) ⇒
      val newRow = newCellsRow(previousRow, tds)
      result += newRow
      newRow
    }

    result.result()
  }

  // Get cell/rowspan information for the given grid row
  def getRowCells(tr: NodeInfo): Seq[Cell] = {

    // All trs up to and including the current tr
    val trs = (tr precedingSibling "*:tr").reverse.to[List] ::: tr :: Nil

    // For each row, the Seq of tds
    val rows = trs map (_ / "*:td" toList)

    // Return the final row of prepared cells
    rows.foldLeft[List[Cell]](Nil)(newCellsRow)
  }

  // Get the x/y position of a td given Cell information
  def tdCoordinates(td: NodeInfo, cells: List[List[Cell]]): (Int, Int) = {

    // Search rows first, then cols
    // Another solution would be to store the position directly into Cell

    val y = td parent * precedingSibling "*:tr" size
    val x = cells(y) indexWhere (_.td == td)

    (x, y)
  }

  // Find all grid cells which are not empty and return for each, in order:
  //
  // - the cell element
  // - the row-position
  // - the column-position
  // - the row-span
  // - the column-span
  //
  //@XPathFunction
  def findTdsWithPositionAndSize(grid: NodeInfo): List[Item] = {

    val allRowCells = getAllRowCells(grid)

    val gridWidth = allRowCells map (_ filterNot (_.missing) map (_.colspan) sum) max

    val result = ListBuffer[Item]()

    result += gridWidth
    result += allRowCells.size

    // Maybe refine this condition
    def isEmptyRow(row: List[Cell]) =
      (row filter (cell ⇒ cell.rowspan == 1 && ! cell.missing && ! cell.td.hasChildElement) map (_.colspan) sum) == gridWidth

    if (12 % gridWidth == 0){

      val ratio = 12 / gridWidth

      for {
        (row, rowStart) ← allRowCells.iterator.zipWithIndex
      } locally {
        if (isEmptyRow(row)) {
          result += row.head.td
          result += rowStart + 1
          result += 1
          result += 1
          result += 12
        } else
          for {
            (cell, columnStart) ← row.zipWithIndex.iterator
            if ! cell.missing && cell.td.hasChildElement
          } locally {
            result += cell.td
            result += rowStart + 1
            result += columnStart * ratio + 1
            result += cell.rowspan
            result += cell.colspan * ratio
          }
        }
    }

    result.result()
  }
}