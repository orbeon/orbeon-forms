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

import org.orbeon.datatypes.Direction
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._

import scala.util.Try

case class Cell[Underlying](u: Option[Underlying], origin: Option[Cell[Underlying]], x: Int, y: Int, h: Int, w: Int) {

  require(x > 0 && y > 0 && h > 0 && w > 0,                                     s"`$x`, `$y`, `$h`, `$w`")
  require(x <= Cell.StandardGridWidth && (x + w - 1) <= Cell.StandardGridWidth, s"`$x`, `$y`, `$h`, `$w`")

  def td      = u getOrElse (throw new NoSuchElementException)
  def missing = origin.isDefined
}

// TODO: Pass this around and add operations
case class GridModel[Underlying](cells: List[Cell[Underlying]])

trait CellOps[Underlying] {

  def attValueOpt    (u: Underlying, name: String): Option[String]
  def children       (u: Underlying, name: String): List[Underlying]
  def parent         (u: Underlying)              : Underlying
  def hasChildElement(u: Underlying)              : Boolean

  def x(u: Underlying): Option[Int]
  def y(u: Underlying): Option[Int]
  def w(u: Underlying): Option[Int]
  def h(u: Underlying): Option[Int]

  def updateX(u: Underlying, x: Int): Unit
  def updateY(u: Underlying, y: Int): Unit
  def updateH(u: Underlying, h: Int): Unit
  def updateW(u: Underlying, w: Int): Unit
}

object Cell {

  import Private._

  val StandardGridWidth = 12

  val GridTestName = "*:grid"
  val TrTestName   = "*:tr"
  val TdTestName   = "*:td"
  val CellTestName = "*:c"

  // This function is used to migrate grids from the older `<xh:tr>`/`<xh:td>` format to the new `<fr:c>` format.
  // The cells returned can have a width which is less than `StandardGridWidth`.
  def analyzeTrTdGrid[Underlying : CellOps](
    grid     : Underlying,
    simplify : Boolean
  ): (Int, List[List[Cell[Underlying]]]) = {

    val ops  = implicitly[CellOps[Underlying]]
    val rows = ops.children(grid, TrTestName)

    // TODO: can it be 0?
    val gridHeight = rows.size

    val xy = Array.fill[Cell[Underlying]](gridHeight, StandardGridWidth)(null)

    // Mark cells
    rows.iterator.zipWithIndex foreach { case (tr, iy) ⇒

      var ix = 0

      ops.children(tr, TdTestName) foreach { td ⇒

        findStart(xy(iy), ix) match {
          case Some(start) ⇒

            val w = getNormalizedSizeAtt(td, "colspan")
            val h = getNormalizedSizeAtt(td, "rowspan")

            val newCell = Cell(Some(td), None, start + 1, iy + 1, h, w)

            for {
              iy1 ← iy until iy + h
              ix1 ← start until start + w
              isOriginCell = ix1 == start && iy1 == iy
            } locally {
              xy(iy1)(ix1) =
                if (isOriginCell)
                  newCell
                else
                  newCell.copy(origin = Some(newCell), x = ix1 + 1, y = iy1 + 1, h = h + iy - iy1, w = w + start - ix1)
            }

            ix = start + w

            case None ⇒
              ix = StandardGridWidth // break
        }
      }
    }

    // The resulting grid so far might take, for example, only 2, 3, or 4 columns. So we trim the
    // right side of the grid if needed. We could do the converse and adjust everything on a 12-column
    // basis.

    // NOTE: Checking the first row should be enough if the grid is well-formed.
    val gridWidth = {
      val widths = xy.iterator map (_.iterator filter (c ⇒ (c ne null) && ! c.missing) map (_.w) sum)
      if (widths.nonEmpty) widths.max else 0
    }

    val widthToTruncate =
      if (simplify && gridWidth != 0 && Cell.StandardGridWidth % gridWidth == 0)
        gridWidth
      else
        StandardGridWidth

    // In most cases, there won't be holes as `<xh:tr>`/`<xh:td>` grids are supposed to be fully populated.
    fillHoles(xy, widthToTruncate)
    widthToTruncate → xyToList(xy, widthToTruncate)
  }

  def analyze12ColumnGridAndFillHoles[Underlying : CellOps](
    grid       : Underlying,
    simplify   : Boolean
  ): List[List[Cell[Underlying]]] = {

    val ops = implicitly[CellOps[Underlying]]
    val cs  = ops.children(grid, CellTestName)

    // TODO: can it be 0?
    val gridHeight = if (cs.nonEmpty) cs map (c ⇒ ops.y(c).getOrElse(1) + ops.h(c).getOrElse(1) - 1) max else 0

    val xy = Array.fill[Cell[Underlying]](gridHeight, StandardGridWidth)(null)

    // Mark cells
    cs foreach { c ⇒

      val x = ops.x(c).getOrElse(1)
      val y = ops.y(c).getOrElse(1)
      val w = ops.w(c).getOrElse(1)
      val h = ops.h(c).getOrElse(1)

      val newCell = Cell(Some(c), None, x, y, h, w)

      for {
        iy ← (y - 1) until (y + h - 1)
        ix ← (x - 1) until (x + w - 1)
        isOriginCell = ix == x - 1 && iy == y - 1
      } locally {
        xy(iy)(ix) =
          if (isOriginCell)
            newCell
          else
            newCell.copy(origin = Some(newCell), x = ix + 1, y = iy + 1, h = h - iy + y - 1, w = w - ix + x - 1)
      }
    }

    fillHoles(xy, StandardGridWidth)

    if (simplify)
      Private.simplify(xy)
    else
      xyToList(xy, StandardGridWidth)
  }

  def findOriginCell[Underlying](cells: List[List[Cell[Underlying]]], u: Underlying): Option[Cell[Underlying]] =
    cells.iterator.flatten collectFirst {
      case c @ Cell(Some(`u`), None, _, _, _, _) ⇒ c
    }

//  def findCoordinates[Underlying](cells: List[List[Cell[Underlying]]], u: Underlying): Option[(Int, Int)] =
//    findOriginCell(cells, u) map (c ⇒ c.x → c.y)

  def selfCellOrOrigin[Underlying](cell: Cell[Underlying]): Cell[Underlying] = cell match {
    case c @ Cell(_, None, _, _, _, _)     ⇒ c
    case Cell(_, Some(origin), _, _, _, _) ⇒ origin
  }

  def findDistinctOriginCellsToTheRight[Underlying : CellOps](
    cells     : List[List[Cell[Underlying]]],
    cellElem  : Underlying
  ): List[Cell[Underlying]] =
    findOriginCell(cells, cellElem).toList flatMap { originCell ⇒

      val cellToTheRightX = originCell.x + originCell.w

      if (cellToTheRightX <= Cell.StandardGridWidth)
        originCell.y until (originCell.y + originCell.h) map
          (iy ⇒ cells(iy - 1)(cellToTheRightX - 1))      map
          selfCellOrOrigin                               keepDistinctBy
          (_.u)
      else
        Nil
    }

  def findDistinctOriginCellsBelow[Underlying : CellOps](
    cells     : List[List[Cell[Underlying]]],
    cellElem  : Underlying
  ): List[Cell[Underlying]] =
    findOriginCell(cells, cellElem).toList flatMap { originCell ⇒

      val cellBelowY = originCell.y + originCell.h

      if (cellBelowY <= cells.size)
        originCell.x until (originCell.x + originCell.w) map
          (ix ⇒ cells(cellBelowY - 1)(ix - 1))           map
          selfCellOrOrigin                               keepDistinctBy
          (_.u)
      else
        Nil
    }

  def spaceToExtendCell[Underlying : CellOps](
    cells     : List[List[Cell[Underlying]]],
    cellElem  : Underlying,
    direction : Direction
  ): Int =
    findOriginCell(cells, cellElem) flatMap { originCell ⇒

      val ops = implicitly[CellOps[Underlying]]

      direction match {
        case Direction.Right ⇒

          //val currentCellHasContent = originCell.u exists ops.hasChildElement

          val cellsToTheRight = findDistinctOriginCellsToTheRight(cells, cellElem)

          cellsToTheRight.nonEmpty option {

            // For now, simplification: we can only expand to the right if:
            //
            // - all the cells to the right are empty
            // - and they don't start or end over the height of the expanding cell
            //
            // In the future, something smarter can be done.

            cellsToTheRight.iterator map { cellToTheRight ⇒

              val originRightCell = selfCellOrOrigin(cellToTheRight)

              val hasContent   = originRightCell.u exists ops.hasChildElement
              val startsBefore = originRightCell.y < originCell.y
              val endsAfter    = originRightCell.y + originRightCell.h > originCell.y + originCell.h

              if (hasContent || startsBefore || endsAfter)
                0
              else
                cellToTheRight.w

            } min
          }

        case Direction.Down ⇒

          val cellsBelow = findDistinctOriginCellsBelow(cells, cellElem)

          cellsBelow.nonEmpty option {

            cellsBelow.iterator map { cellBelow ⇒

              val originBelowCell = selfCellOrOrigin(cellBelow)

              val hasContent   = originBelowCell.u exists ops.hasChildElement
              val startsBefore = originBelowCell.x < originCell.x
              val endsAfter    = originBelowCell.x + originBelowCell.w > originCell.x + originCell.w

              if (hasContent || startsBefore || endsAfter)
                0
              else
                cellBelow.h

            } min
          }

        case Direction.Left ⇒ Some(0) // TODO
        case Direction.Up   ⇒ Some(0) // TODO
      }
    } getOrElse 0

  private object Private {

    // Try to get a positive attribute value, returning 1 if that fails for any reason
    def getNormalizedSizeAtt[Underlying : CellOps](u: Underlying, attName: String): Int =
      implicitly[CellOps[Underlying]].attValueOpt(u, attName) flatMap (s ⇒ Try(s.toInt).toOption) filter (_ > 0) getOrElse 1

    def findStart[Underlying <: AnyRef](row: Seq[Underlying], from: Int): Option[Int] = {
      val index = row.indexWhere(_ eq null, from)
      index != -1 option index
    }

    def findEnd[Underlying <: AnyRef](row: Seq[Underlying], from: Int): Option[Int] = {
      val index = row.indexWhere(_ ne null, from)
      index != -1 option index
    }

    def xyToList[Underlying](xy: Array[Array[Cell[Underlying]]], width: Int): List[List[Cell[Underlying]]] =
      xy map (_ take width toList) toList

     // Fill holes with cells that can span columns (but not rows, to make things simpler)
    def fillHoles[Underlying](xy: Array[Array[Cell[Underlying]]], width: Int): Unit =
      for {
        (rowFullLength, iy) ← xy.zipWithIndex
        row = rowFullLength take width
      } locally {

        def findRun(from: Int): Option[(Int, Int)] = {
          findStart(row, from) map { x ⇒
            x → (findEnd(row, x + 1) map (_ - 1) getOrElse (width - 1))
          }
        }

        var ix = 0
        while (ix < width) {
          findRun(ix) match {
            case Some((start, endInclusive)) ⇒

              val newCell = Cell[Underlying](None, None, start + 1, iy + 1, 1, endInclusive - start + 1)

              for {
                ix1 ← start to endInclusive
                isOriginCell = ix1 == start
              } locally {
                xy(iy)(ix1) =
                  if (isOriginCell)
                    newCell
                  else
                    newCell.copy(origin = Some(newCell), x = ix1 + 1, w = endInclusive - ix1 + 1)
              }

              ix = endInclusive + 1
            case None ⇒
              ix = width // break
          }
        }
      }

    // Create a simplified grid if positions and widths have a gcd
    // NOTE: The resulting grid will then not necessarily have `StandardGridWidth` cells on each row,
    // but 1, 2, 3, 4, 6, or 12 cells.
    def simplify[Underlying](xy: Array[Array[Cell[Underlying]]]): List[List[Cell[Underlying]]] = {

      val gcd = {

        val Divisors = List(12, 6, 4, 3, 2)

        def gcd2(v1: Int, v2: Int): Int =
          Divisors find (d ⇒ v1 % d == 0 && v2 % d == 0) getOrElse 1

        val distinctValues =
          xy.iterator   flatMap
          (_.iterator)  filter
          (! _.missing) flatMap
          (c ⇒ Iterator(c.x - 1, c.w)) toSet

        if (distinctValues.size == 1)
          distinctValues.head
        else
          distinctValues.reduce(gcd2)
      }

      if (gcd > 1)
        xy map (_ grouped gcd map (x ⇒ x.head) map (c ⇒ c.copy(x = (c.x - 1) / gcd + 1, w = c.w / gcd)) toList) toList
      else
        xyToList(xy, StandardGridWidth)
    }
  }

  def makeASCII[Underlying](
    cells           : List[List[Cell[Underlying]]],
    existingMapping : Map[Underlying, Char] = Map.empty[Underlying, Char]
  ): (String, Map[Underlying, Char]) = {

    val usToLetters = {

      val existingKeys   = existingMapping.keySet
      val existingValues = existingMapping.values.toSet

      val distinctUs  = (cells.flatten collect { case Cell(Some(u), None, _, _, _, _) ⇒ u } distinct) filterNot existingKeys
      val lettersIt   = Iterator.tabulate(26)('A'.toInt + _ toChar)                          filterNot existingValues

      existingMapping ++ (distinctUs.iterator zip lettersIt)
    }

    val charGrid =
      cells map { row ⇒
        row map {
          case Cell(Some(u), None,    _, _, _, _) ⇒ usToLetters(u)
          case Cell(Some(u), Some(_), _, _, _, _) ⇒ usToLetters(u).toLower
          case Cell(None,    _,       _, _, _, _) ⇒ ' '
        }
      }

    (charGrid map (_.mkString) mkString "\n", usToLetters)
  }
}