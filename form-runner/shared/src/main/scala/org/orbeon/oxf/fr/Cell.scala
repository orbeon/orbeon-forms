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
package org.orbeon.oxf.fr

import org.orbeon.oxf.util.CoreUtils._

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

case class Cell[Underlying](u: Option[Underlying], x: Int, y: Int, h: Int, w: Int, missing: Boolean) {
  def td = u getOrElse (throw new NoSuchElementException)
}

trait CellOps[Underlying] {

  def updateH(u: Underlying, rowspan: Int): Unit

  def x(u: Underlying): Option[Int]
  def y(u: Underlying): Option[Int]
  def w(u: Underlying): Option[Int]
  def h(u: Underlying): Option[Int]

  def attValueOpt(u: Underlying, name: String): Option[String]
  def children   (u: Underlying, name: String): List[Underlying]
}

//trait ClientOps[Repr, E] {
//
//  def findHoles(r: Repr): List[E]
//
//  def canMove(r: Repr, from: Coordinate, to: Coordinate): Boolean
//  def canExpand(r: Repr, from: Coordinate, direction: Direction): Boolean
//
//}

object Cell {

  val StandardGridWidth = 12

  private def getNormalizedSpan[Underlying : CellOps](td: Underlying, name: String): Int =
    implicitly[CellOps[Underlying]].attValueOpt(td, name) map (_.toInt) getOrElse 1

  private def getNormalizedRowspan[Underlying : CellOps](td: Underlying): Int = getNormalizedSpan(td, "rowspan")
  private def getNormalizedColspan[Underlying : CellOps](td: Underlying): Int = getNormalizedSpan(td, "colspan")

  // For the previous row of prepared cells, and a new row of tds, return the new row of prepared cells
  def newCellsRow[Underlying : CellOps](previousRow: List[Cell[Underlying]], tds: List[Underlying]): List[Cell[Underlying]] =
    previousRow match {

      case Nil ⇒
        // First row: start with initial rowspans
        tds flatMap { td ⇒

          val colspan = getNormalizedColspan(td)
          val rowspan = getNormalizedRowspan(td)

          val firstCell =
            Cell(Some(td), -1, -1, rowspan, colspan, missing = false)

          val spanCells =
            if (colspan == 1)
              Nil
            else
              (1 until colspan).reverse map { index ⇒
                Cell(Some(td), -1, -1, rowspan, colspan - index, missing = true)
              } toList

          firstCell :: spanCells
        }
      case _ ⇒
        // Subsequent rows

        // Assumption is that all rows have the same width
        // TODO: Handle error conditions. Throw if error is found.

        val previousRowIt = previousRow.toIterator
        val tdsIt         = tds.toIterator

        val result = ListBuffer[Cell[Underlying]]()

        previousRowIt foreach {
          // Previous row's cell does NOT span over this current row
          case Cell(_, -1, -1, 1, colspan, _) ⇒

            val td = tdsIt.next()

            val colspan = getNormalizedColspan(td)
            val rowspan = getNormalizedRowspan(td)

            // Always at least one cell
            result += Cell(Some(td), -1, -1, rowspan, colspan, missing = false)

            // Add cells for remaining colspans
            (1 until colspan).reverse foreach { remainingColspan ⇒
              result += Cell(Some(td), -1, -1, rowspan, remainingColspan, missing = true)
              // Advance the previous row iterator explicitly!
              previousRowIt.next()
            }

          // Previous row's cell DOES span over this current row
          case Cell(td, -1, -1, rowspan, colspan, _) ⇒
            // Create a hole
            result += Cell(td, -1, -1, rowspan - 1, colspan, missing = true)
        }

        result.result()
    }

  // Get cell/rowspan information for all the rows in the grid
  def getAllRowCells[Underlying : CellOps](grid: Underlying): List[List[Cell[Underlying]]] = {

    val ops  = implicitly[CellOps[Underlying]]
    val rows = ops.children(grid, "*:tr") map (ops.children(_, "*:td"))

    // Accumulate the result for each row as we go
    val result = mutable.ListBuffer[List[Cell[Underlying]]]()

    rows.foldLeft[List[Cell[Underlying]]](Nil) { (previousRow, tds) ⇒
      val newRow = newCellsRow(previousRow, tds)
      result += newRow
      newRow
    }

    result.result()
  }


  // Create representation, filling holes if needed
//  def fooXxx[Underlying : CellOps](grid: Underlying, mergeHoles: Boolean) = {
//
//    val ops = implicitly[CellOps[Underlying]]
//    val cs  = ops.children(grid, "*:c")
//
//    def getIntValue(u: Underlying, name: String): Int =
//      ops.attValueOpt(u, name) map (_.toInt) getOrElse 1
//
//    val gridHeight = cs map (c ⇒ getIntValue(c, "y") + getIntValue(c, "h") - 1) max
//
//    val grouped = cs groupBy (getIntValue(_, "y"))
//
//    val rows = 1 to gridHeight flatMap grouped.get
//
//    rows.foldLeft[List[Cell[Underlying]]](Nil) { (previousRow, tds) ⇒
//
//      val currentCells =
//        tds.foldLeft[List[Cell[Underlying]]](Nil) {
//          case (Nil, td)          ⇒
//            val xPos = getIntValue(td, "x")
//
//            // TODO switch to `Option`
//            (xPos > 1 list Cell(null: Underlying, 1, xPos - 1, missing = false))                :::
//              Cell(td, getIntValue(td, "rowspan"), getIntValue(td, "colspan"), missing = false) ::
//              Nil
//
//          case (head :: _, td) ⇒
//
//            val previousExtentX = head.
//
//        }
//
//
//
//    }

    def analyze12ColumnGridAndFillHoles[Underlying : CellOps](
      grid       : Underlying,
      mergeHoles : Boolean,
      simplify   : Boolean
    ): List[List[Cell[Underlying]]] = {

      val ops = implicitly[CellOps[Underlying]]
      val cs  = ops.children(grid, "*:c")

      // TODO: can it be 0?
      val gridHeight = if (cs.nonEmpty) cs map (c ⇒ ops.y(c).getOrElse(1) + ops.h(c).getOrElse(1) - 1) max else 0

      val xy = Array.fill[Cell[Underlying]](gridHeight, StandardGridWidth)(null)

      // Mark cells
      cs foreach { c ⇒

        val x = ops.x(c).getOrElse(1)
        val y = ops.y(c).getOrElse(1)
        val w = ops.w(c).getOrElse(1)
        val h = ops.h(c).getOrElse(1)

        val newCell = Cell(Some(c), x, y, h, w, missing = false)

        for {
          iy ← (y - 1) until (y + h - 1)
          ix ← (x - 1) until (x + w - 1)
          isFirstCell = ix == x - 1 && iy == y - 1
        } locally {
          xy(iy)(ix) =
            if (isFirstCell)
              newCell
            else
              newCell.copy(x = ix + 1, y = iy + 1, h = h - iy + y - 1, w = w - ix + x - 1, missing = true)
        }
      }

      // Fill holes with cells that can span columns but not rows
      for ((row, iy) ← xy.zipWithIndex) {

        def findStart(from: Int) =
          row.indexWhere(_ eq null, from)

        def findEnd(from: Int) =
          row.indexWhere(_ ne null, from)

        def findRun(from: Int): Option[(Int, Int)] = {
          val x1 = findStart(from)
          (x1 != -1) option {
            val x2 = findEnd(x1 + 1)
            x1 → (if (x2 == -1) StandardGridWidth - 1 else x2 - 1)
          }
        }

        var ix = 0
        while (ix < StandardGridWidth) {
          findRun(ix) match {
            case Some((start, end)) ⇒

              val newCell = Cell[Underlying](None, start + 1, iy + 1, 1, end - start + 1, missing = false)

              for {
                ix2 ← start to end
                isFirstCell = ix2 == start
              } locally {
                xy(iy)(ix2) =
                  if (isFirstCell)
                    newCell
                  else
                    newCell.copy(x = ix2 + 1, w = end - ix2 + 1, missing = true)
              }

              ix = end + 1
            case None ⇒
              ix = StandardGridWidth // break
          }
        }

      }

      def xyToList =
        xy map (_.toList) toList

      // Adjust factors
      if (simplify) {
        val gcd = {

          val Divisors = List(6, 4, 3, 2)

          def gcd2(v1: Int, v2: Int): Int =
            Divisors find (d ⇒ v1 % d == 0 && v2 % d == 0) getOrElse 1

          val distinctValues =
            xy.iterator   flatMap
            (_.iterator)  filter
            (! _.missing) flatMap
            (c ⇒ Iterator(c.x - 1, c.w)) toSet

          distinctValues.fold(Divisors.head)(gcd2)
        }

        if (gcd > 1)
          xy map (_ map (c ⇒ c.copy(x = (c.x - 1) / gcd + 1, w = c.w / gcd)) toList) toList
        else
          xyToList
      } else
          xyToList
  }

  def find[Underlying](td: Underlying, cells: List[List[Cell[Underlying]]]): Option[Cell[Underlying]] =
    cells.iterator.flatten find (_.td == td)

  def findCoordinates[Underlying](td: Underlying, cells: List[List[Cell[Underlying]]]): Option[(Int, Int)] =
     find(td, cells) map (c ⇒ c.x → c.y)


}