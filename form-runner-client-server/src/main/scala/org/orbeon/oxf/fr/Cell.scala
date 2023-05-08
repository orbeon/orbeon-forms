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

import enumeratum._
import org.orbeon.datatypes.{Direction, Orientation}
import org.orbeon.oxf.util.CoreUtils._

import scala.util.Try


case class Cell[Underlying](u: Option[Underlying], origin: Option[Cell[Underlying]], x: Int, y: Int, h: Int, w: Int)(maxGridWidth: Int) {

  require(x > 0 && y > 0 && h > 0 && w > 0,                 s"`$x`, `$y`, `$h`, `$w`")
  require(x <= maxGridWidth && (x + w - 1) <= maxGridWidth, s"`$x`, `$y`, `$h`, `$w`")

  def td           = u getOrElse (throw new NoSuchElementException)
  def missing      = origin.isDefined
}

case class GridModel[Underlying](cells: List[List[Cell[Underlying]]])

sealed trait WallPosition extends EnumEntry
object WallPosition extends Enum[WallPosition] {
  val values = findValues
  case object Middle extends WallPosition
  case object Side   extends WallPosition
}

trait CellOps[Underlying] {

  def attValueOpt    (u: Underlying, name: String): Option[String]
  def children       (u: Underlying, name: String): List[Underlying]
  def parent         (u: Underlying)              : Underlying
  def hasChildElement(u: Underlying)              : Boolean
  def cellsForGrid   (u: Underlying)              : List[Underlying]
  def gridForCell    (u: Underlying)              : Underlying
  def maxGridWidth   (u: Underlying)              : Int

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
  def analyzeTrTdGrid[Underlying: CellOps](
    grid     : Underlying,
    simplify : Boolean
  ): (Int, GridModel[Underlying]) = {

    val ops  = implicitly[CellOps[Underlying]]
    val rows = ops.children(grid, TrTestName)

    // TODO: can it be 0?
    val xyGridHeight = rows.size
    val xyGridWidth  = StandardGridWidth

    val xy = Array.fill[Cell[Underlying]](xyGridHeight, xyGridWidth)(null)

    // Mark cells
    rows.iterator.zipWithIndex foreach { case (tr, iy) =>

      var ix = 0

      ops.children(tr, TdTestName) foreach { td =>

        findStart(xy(iy), ix) match {
          case Some(start) =>

            val w = getNormalizedSizeAtt(td, "colspan")
            val h = getNormalizedSizeAtt(td, "rowspan")

            val newCell = Cell(Some(td), None, start + 1, iy + 1, h, w)(xyGridWidth)

            for {
              iy1 <- iy until ((iy + h) min xyGridHeight)
              ix1 <- start until ((start + w) min xyGridWidth)
              isOriginCell = ix1 == start && iy1 == iy
            } locally {
              xy(iy1)(ix1) =
                if (isOriginCell)
                  newCell
                else
                  newCell.copy(origin = Some(newCell), x = ix1 + 1, y = iy1 + 1, h = h + iy - iy1, w = w + start - ix1)(xyGridWidth)
            }

            ix = start + w

            case None =>
              ix = xyGridWidth // break
        }
      }
    }

    // The resulting grid so far might take, for example, only 2, 3, or 4 columns. So we trim the
    // right side of the grid if needed. We could do the converse and adjust everything on a 12-column
    // basis.

    val widthToTruncate = {

      // NOTE: Checking the first row should be enough if the grid is well-formed.
      val actualGridWidth = {
        val widths = xy.iterator map (_.iterator filter (c => (c ne null) && ! c.missing) map (_.w) sum)
        if (widths.nonEmpty) widths.max else 0
      }

      if (simplify && actualGridWidth != 0 && xyGridWidth % actualGridWidth == 0)
        actualGridWidth
      else if (simplify && actualGridWidth == 5)
        6
      else
        xyGridWidth
    }

    // In most cases, there won't be holes as `<xh:tr>`/`<xh:td>` grids are supposed to be fully populated.
    fillHoles(xy, widthToTruncate)
    widthToTruncate -> xyToList(xy, widthToTruncate)
  }

  def analyze12ColumnGridAndFillHoles[Underlying : CellOps](
    grid       : Underlying,
    simplify   : Boolean,
    transpose  : Boolean
  ): GridModel[Underlying] = {

    val ops          = implicitly[CellOps[Underlying]]
    val cs           = ops.cellsForGrid(grid)
    val maxGridWidth = ops.maxGridWidth(grid)

    // TODO: can it be 0?
    val gridHeight = if (cs.nonEmpty) cs map (c => ops.y(c).getOrElse(1) + ops.h(c).getOrElse(1) - 1) max else 0

    val xy = Array.fill[Cell[Underlying]](gridHeight, maxGridWidth)(null)

    // Mark cells
    cs foreach { c =>

      val x = ops.x(c).getOrElse(1)
      val y = ops.y(c).getOrElse(1)
      val w = ops.w(c).getOrElse(1)
      val h = ops.h(c).getOrElse(1)

      val newCell = Cell(Some(c), None, x, y, h, w)(maxGridWidth)

      for {
        iy <- (y - 1) until (y + h - 1)
        ix <- (x - 1) until (x + w - 1)
        isOriginCell = ix == x - 1 && iy == y - 1
      } locally {
        xy(iy)(ix) =
          if (isOriginCell)
            newCell
          else
            newCell.copy(origin = Some(newCell), x = ix + 1, y = iy + 1, h = h - iy + y - 1, w = w - ix + x - 1)(maxGridWidth)
      }
    }

    fillHoles(xy, maxGridWidth)

    val simplified =
      if (simplify)
        Private.simplify(xy, maxGridWidth)
      else
        xyToList(xy, maxGridWidth)

    if (transpose)
      Private.transpose(simplified)
    else
      simplified
  }

  def originCells[Underlying](gridModel: GridModel[Underlying]): Iterator[Cell[Underlying]] =
    gridModel.cells.iterator.flatten collect {
      case c @ Cell(Some(_), None, _, _, _, _) => c
    }

  def findOriginCell[Underlying](gridModel: GridModel[Underlying], u: Underlying): Option[Cell[Underlying]] =
    gridModel.cells.iterator.flatten collectFirst {
      case c @ Cell(Some(`u`), None, _, _, _, _) => c
    }

  def selfCellOrOrigin[Underlying](cell: Cell[Underlying]): Cell[Underlying] = cell match {
    case c @ Cell(_, None, _, _, _, _)     => c
    case Cell(_, Some(origin), _, _, _, _) => origin
  }

  // Finds the neighbors on the given side of the given cell
  def findOriginNeighbors[Underlying: CellOps](
    originCell : Cell[Underlying],
    side       : Direction,
    gridModel  : GridModel[Underlying]
  ): List[Cell[Underlying]] = {

    val ops  = implicitly[CellOps[Underlying]]
    val grid = ops.gridForCell(originCell.u.get)

    val (neighborX, neighborY) = side match {
      case Direction.Left  => (originCell.x - 1           , originCell.y               )
      case Direction.Right => (originCell.x + originCell.w, originCell.y               )
      case Direction.Down  => (originCell.x               , originCell.y + originCell.h)
      case Direction.Up    => (originCell.x               , originCell.y - 1           )
    }
    val isCoordinateValid =
      neighborX >= 1 &&
      neighborX <= ops.maxGridWidth(grid) &&
      neighborY >= 1 &&
      neighborY <= gridModel.cells.length
    isCoordinateValid.flatList {
      val (xs, ys) = side match {
        case Direction.Left | Direction.Right => List(neighborX) -> (originCell.y until originCell.y + originCell.h).toList
        case Direction.Up   | Direction.Down  => (originCell.x until originCell.x + originCell.w).toList -> List(neighborY)
      }
      val neighborCells = xs.flatMap(x => ys.map(y => gridModel.cells(y - 1)(x - 1)))
      neighborCells.map(selfCellOrOrigin).distinct
    }
  }

  def nonOverflowingNeighbors[Underlying: CellOps](
    gridModel  : GridModel[Underlying],
    originCell : Cell[Underlying],
    direction  : Direction
  ): List[Cell[Underlying]] = {
    def overflowsOriginCell(c: Cell[Underlying]) = direction match {
      case Direction.Left | Direction.Right => c.y < originCell.y || c.y + c.h > originCell.y + originCell.h
      case Direction.Up   | Direction.Down  => c.x < originCell.x || c.x + c.w > originCell.x + originCell.w
    }
    val neighborCells       = findOriginNeighbors(originCell, direction, gridModel)
    val originNeighborCells = neighborCells.map(selfCellOrOrigin).distinct
    val directionOK         = ! originNeighborCells.exists(overflowsOriginCell)
    if (directionOK) originNeighborCells else Nil
  }

  def findSmallestNeighbor[Underlying](
    side      : Direction,
    neighbors : List[Cell[Underlying]]
  ): Option[Cell[Underlying]] = {
    neighbors match {
      case Nil => None
      case _   => Some(neighbors.minBy { neighbor =>
        Orientation.fromDirection(side) match {
          case Orientation.Horizontal => neighbor.w
          case Orientation.Vertical => neighbor.h
        }
      })
    }
  }

  // For a given cell, returns the walls that can be moved by D&D
  def movableWalls[Underlying: CellOps](
    gridModel : GridModel[Underlying],
    cellElem  : Underlying
  ): List[(Direction, WallPosition)] =
    findOriginCell(gridModel, cellElem).toList.flatMap { originCell =>
      Direction.values.flatMap { direction =>
        val neighbors = nonOverflowingNeighbors(gridModel, originCell, direction)
        val smallestNeighborOpt = findSmallestNeighbor(direction, neighbors)
        val directionWallPosition = smallestNeighborOpt.flatMap { smallestNeighbor =>
          val directionOK =
            Orientation.fromDirection(direction) match {
              case Orientation.Horizontal => originCell.w > 1 || smallestNeighbor.w > 1
              case Orientation.Vertical   => originCell.h > 1 || smallestNeighbor.h > 1
            }
          directionOK.option({
            val wallMiddle =
              Orientation.fromDirection(direction) match {
                case Orientation.Horizontal => originCell.h == smallestNeighbor.h
                case Orientation.Vertical   => originCell.w == smallestNeighbor.w
              }
            if (wallMiddle) WallPosition.Middle else WallPosition.Side
          })
        }
        directionWallPosition.map(direction -> _)
      }
    }

  def canChangeSize[Underlying: CellOps](cellElem: Underlying): List[Direction] = {

    val ops  = implicitly[CellOps[Underlying]]
    val grid = ops.gridForCell(cellElem)

    val cells = analyze12ColumnGridAndFillHoles(grid, simplify = false, transpose = false)
    findOriginCell(cells, cellElem).toList.flatMap { originCell =>
      val merge =
        List(Direction.Right, Direction.Down) flatMap { direction =>
          val neighbors = nonOverflowingNeighbors(cells, originCell, direction)
          val canExpand = neighbors.lengthCompare(1) == 0 && neighbors.head.u.exists(! ops.hasChildElement(_))
          canExpand.list(direction)
        }
      val splitX = (originCell.w > 1).list(Direction.Left)
      val splitY = (originCell.h > 1).list(Direction.Up  )
      merge ::: splitX ::: splitY
    }
  }

  // When dragging a given wall of a given cell, returns where this wall can go
  case class PossibleDropTargets(statusQuo: Int, all: List[Int])

  def cellWallPossibleDropTargets[Underlying: CellOps](
    cellElem  : Underlying,
    startSide : Direction
  ): Option[PossibleDropTargets] = {

    val ops  = implicitly[CellOps[Underlying]]
    val grid = ops.gridForCell(cellElem)

    val cells = analyze12ColumnGridAndFillHoles(grid, simplify = false, transpose = false)
    findOriginCell(cells, cellElem).map { originCell =>

      def insideCellPositions(cell: Cell[Underlying]): List[Int] =
        Orientation.fromDirection(startSide) match {
          case Orientation.Horizontal => (cell.x until cell.x + cell.w - 1).toList
          case Orientation.Vertical   => (cell.y until cell.y + cell.h - 1).toList
        }

      val statusQuoPosition = startSide match {
        case Direction.Left  => originCell.x - 1
        case Direction.Right => originCell.x + originCell.w - 1
        case Direction.Up    => originCell.y - 1
        case Direction.Down  => originCell.y + originCell.h - 1
      }

      val neighbors = findOriginNeighbors(originCell, startSide, cells)
      PossibleDropTargets(
        statusQuo = statusQuoPosition,
        all =
          statusQuoPosition                 ::
            insideCellPositions(originCell) :::
            findSmallestNeighbor(startSide, neighbors).toList.flatMap(insideCellPositions)
      )
    }
  }

  // Returns for each row (horizontal orientation) or each column (vertical orientation) if there is a grid gap after that row or column
  def gapsInGrid[Underlying](
    gridModel   : GridModel[Underlying],
    orientation : Orientation
  ): List[Boolean] = {
    val all = gridModel.cells.flatten
    val withoutFirstRowColumn = all.filter(c => c.x != 1 && c.y != 1)
    val indexGaps = withoutFirstRowColumn.map(c => orientation match {
      case Orientation.Horizontal => c.y -> ! c.missing
      case Orientation.Vertical   => c.x -> ! c.missing
    }).groupBy(_._1)
    indexGaps.map { case (index, hasGap) =>
      index -> hasGap.exists(_._2)
    }.toList.sortBy(_._1).map(_._2)
  }

  private object Private {

    // Try to get a positive attribute value, returning 1 if that fails for any reason
    def getNormalizedSizeAtt[Underlying : CellOps](u: Underlying, attName: String): Int =
      implicitly[CellOps[Underlying]].attValueOpt(u, attName) flatMap (s => Try(s.toInt).toOption) filter (_ > 0) getOrElse 1

    def findStart[Underlying <: AnyRef](row: Seq[Underlying], from: Int): Option[Int] = {
      val index = row.indexWhere(_ eq null, from)
      index != -1 option index
    }

    def findEnd[Underlying <: AnyRef](row: Seq[Underlying], from: Int): Option[Int] = {
      val index = row.indexWhere(_ ne null, from)
      index != -1 option index
    }

    def xyToList[Underlying](xy: Array[Array[Cell[Underlying]]], width: Int): GridModel[Underlying] =
      GridModel(xy map (_ take width toList) toList)

     // Fill holes with cells that can span columns (but not rows, to make things simpler)
    def fillHoles[Underlying](xy: Array[Array[Cell[Underlying]]], width: Int): Unit =
      for {
        (rowFullLength, iy) <- xy.zipWithIndex
        row = rowFullLength take width
      } locally {

        def findRun(from: Int): Option[(Int, Int)] = {
          findStart(row, from) map { x =>
            x -> (findEnd(row, x + 1) map (_ - 1) getOrElse (width - 1))
          }
        }

        var ix = 0
        while (ix < width) {
          findRun(ix) match {
            case Some((start, endInclusive)) =>

              val newCell = Cell[Underlying](None, None, start + 1, iy + 1, 1, endInclusive - start + 1)(width)

              for {
                ix1 <- start to endInclusive
                isOriginCell = ix1 == start
              } locally {
                xy(iy)(ix1) =
                  if (isOriginCell)
                    newCell
                  else
                    newCell.copy(origin = Some(newCell), x = ix1 + 1, w = endInclusive - ix1 + 1)(width)
              }

              ix = endInclusive + 1
            case None =>
              ix = width // break
          }
        }
      }

    // Create a simplified grid if positions and widths have a gcd
    // NOTE: The resulting grid will then not necessarily have `StandardGridWidth` cells on each row,
    // but 1, 2, 3, 4, 6, or 12 cells.
    def simplify[Underlying](xy: Array[Array[Cell[Underlying]]], maxGridWidth: Int): GridModel[Underlying] = {

      val gcd = {

        val Divisors = List(24, 12, 8, 6, 4, 3, 2)

        def gcd2(v1: Int, v2: Int): Int =
          Divisors find (d => v1 % d == 0 && v2 % d == 0) getOrElse 1

        val distinctValues =
          xy.iterator   flatMap
          (_.iterator)  filter
          (! _.missing) flatMap
          (c => Iterator(c.x - 1, c.w)) toSet

        if (distinctValues.size == 1)
          distinctValues.head
        else
          distinctValues.reduce(gcd2)
      }

      if (gcd > 1)
        GridModel(xy map (_ grouped gcd map (x => x.head) map (c => c.copy(x = (c.x - 1) / gcd + 1, w = c.w / gcd)(maxGridWidth)) toList) toList)
      else
        xyToList(xy, maxGridWidth)
    }

    // Create a transposed grid, i.e. a grid with the same cells but with rows and columns swapped
    def transpose[Underlying](grid: GridModel[Underlying]): GridModel[Underlying] = {
      val cells = grid.cells.flatten
      val columns = cells.map(_.x).distinct.sorted

      val transposedCells = columns.map { column =>
        cells.filter(_.x == column).sortBy(_.y)
      }

      grid.copy(cells = transposedCells)
    }
  }

  // For tests
  def makeASCII[Underlying](
    gridModel       : GridModel[Underlying],
    existingMapping : Map[Underlying, Char] = Map.empty[Underlying, Char]
  ): (String, Map[Underlying, Char]) = {

    val usToLetters = {

      val existingKeys   = existingMapping.keySet
      val existingValues = existingMapping.values.toSet

      val distinctUs  = (gridModel.cells.flatten collect { case Cell(Some(u), None, _, _, _, _) => u } distinct) filterNot existingKeys
      val lettersIt   = Iterator.tabulate(26)('A'.toInt + _ toChar)                          filterNot existingValues

      existingMapping ++ (distinctUs.iterator zip lettersIt)
    }

    val charGrid =
      gridModel.cells map { row =>
        row map {
          case Cell(Some(u), None,    _, _, _, _) => usToLetters(u)
          case Cell(Some(u), Some(_), _, _, _, _) => usToLetters(u).toLower
          case Cell(None,    _,       _, _, _, _) => ' '
        }
      }

    (charGrid map (_.mkString) mkString "\n", usToLetters)
  }
}