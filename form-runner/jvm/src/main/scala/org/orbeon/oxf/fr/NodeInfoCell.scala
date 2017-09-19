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

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.{ArrayFunctions, MapFunctions}
import org.orbeon.saxon.om.{Item, NodeInfo, SequenceIterator, ValueRepresentation}
import org.orbeon.saxon.value.{AtomicValue, EmptySequence, SequenceExtent, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.mutable.ListBuffer

object NodeInfoCell {

  implicit object NodeInfoCellOps extends CellOps[NodeInfo] {

    def children(u: NodeInfo, name: String) = (u / name).to[List]
    def attValueOpt(cell: NodeInfo, name: String) = cell attValueOpt name

    def underlyingRowspan(u: NodeInfo): Int = getNormalizedSpan(u, "rowspan")
    def underlyingRowspan_(u: NodeInfo, rowspan: Int): Unit = toggleAttribute(u, "rowspan", rowspan.toString, rowspan > 1)
  }

  def getNormalizedSpan(td: NodeInfo, name: String) =  td attValueOpt name map (_.toInt) getOrElse 1

  // Get cell/rowspan information for the given grid row
  def getRowCells(tr: NodeInfo): Seq[Cell[NodeInfo]] = {

    // All trs up to and including the current tr
    val trs = (tr precedingSibling "*:tr").reverse.to[List] ::: tr :: Nil

    // For each row, the Seq of tds
    val rows = trs map (_ / "*:td" toList)

    // Return the final row of prepared cells
    rows.foldLeft[List[Cell[NodeInfo]]](Nil)(Cell.newCellsRow)
  }

  // Get the x/y position of a td given Cell information
  def tdCoordinates(td: NodeInfo, cells: List[List[Cell[NodeInfo]]]): (Int, Int) = {

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
  def findTdsWithPositionAndSize(grid: NodeInfo): SequenceIterator = {

    val allRowCells = Cell.getAllRowCells(grid)

    val gridWidth = allRowCells map (_ filterNot (_.missing) map (_.w) sum) max

    val result = ListBuffer[Item]()

    result += gridWidth
    result += allRowCells.size

    // Maybe refine this condition
    def isEmptyRow(row: List[Cell[NodeInfo]]) =
      (row filter (cell ⇒ cell.h == 1 && ! cell.missing && ! cell.td.hasChildElement) map (_.w) sum) == gridWidth

    if (12 % gridWidth == 0) {

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
            result += cell.h
            result += cell.w * ratio
          }
        }
    }

    result
  }

  //
  //@XPathFunction
  def analyze12ColumnGridAndFillHoles(grid: NodeInfo): Item = { // `array(map(xs:string, *)*)`
    ArrayFunctions.createValue(
      Cell.analyze12ColumnGridAndFillHoles(grid, mergeHoles = true).to[Vector] map { row ⇒
        new SequenceExtent(
          row collect {
            case Cell(uOpt, x, y, h, w, false) ⇒
              MapFunctions.createValue(
                Map[AtomicValue, ValueRepresentation](
                  (SaxonUtils.fixStringValue("c"), uOpt getOrElse EmptySequence.getInstance),
                  (SaxonUtils.fixStringValue("x"), x),
                  (SaxonUtils.fixStringValue("y"), y),
                  (SaxonUtils.fixStringValue("w"), w),
                  (SaxonUtils.fixStringValue("h"), h)
                )
              )
          }
        )
      }
    )
  }


  trait ClientOps[Repr, E] {

    def findHoles(r: Repr): List[E]

    def canMove(r: Repr, from: Coordinate, to: Coordinate): Boolean
    def canExpand(r: Repr, from: Coordinate, direction: Direction): Boolean

  }

}