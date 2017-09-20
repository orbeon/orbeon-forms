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
import org.orbeon.saxon.om.{Item, NodeInfo, ValueRepresentation}
import org.orbeon.saxon.value.{AtomicValue, EmptySequence, SequenceExtent}
import org.orbeon.saxon.{ArrayFunctions, MapFunctions}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

object NodeInfoCell {

  implicit object NodeInfoCellOps extends CellOps[NodeInfo] {

    def children   (u: NodeInfo, name: String): List[NodeInfo] = (u / name).to[List]
    def attValueOpt(u: NodeInfo, name: String): Option[String] = u attValueOpt name

    def underlyingRowspan (u: NodeInfo): Int                = getNormalizedSpan(u, "rowspan")
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
  def tdCoordinates(td: NodeInfo, cells: List[List[Cell[NodeInfo]]]): Option[(Int, Int)] =
    cells.iterator.flatten find (_.td == td) map (c ⇒ c.x → c.y)

  // Find all grid cells which are not empty and return for each:
  //
  // - the cell element
  // - the row-position
  // - the column-position
  // - the row-span
  // - the column-span
  //
  //@XPathFunction
  def findTdsWithPositionAndSize(grid: NodeInfo): Item = { // `map(*)`

    val allRowCells = Cell.getAllRowCells(grid)

    val gridWidth = allRowCells map (_ filterNot (_.missing) map (_.w) sum) max

    // Maybe refine this condition
    def isEmptyRow(row: List[Cell[NodeInfo]]) =
      (row filter (cell ⇒ cell.h == 1 && ! cell.missing && ! cell.td.hasChildElement) map (_.w) sum) == gridWidth

    import org.orbeon.oxf.util.CoreUtils._

    val itOpt =
      12 % gridWidth == 0 option {

        val ratio = 12 / gridWidth

        val it =
          for {
            (row, rowStart) ← allRowCells.iterator.zipWithIndex
          } yield {
            if (isEmptyRow(row)) {
              Iterator(
                Map[AtomicValue, ValueRepresentation](
                  (SaxonUtils.fixStringValue("c"), row.head.td),
                  (SaxonUtils.fixStringValue("x"), 1),
                  (SaxonUtils.fixStringValue("y"), rowStart + 1),
                  (SaxonUtils.fixStringValue("w"), 12),
                  (SaxonUtils.fixStringValue("h"), 1)
                )
              )
            } else
              for {
                (cell, columnStart) ← row.zipWithIndex.iterator
                if ! cell.missing && cell.td.hasChildElement
              } yield {
                Map[AtomicValue, ValueRepresentation](
                  (SaxonUtils.fixStringValue("c"), cell.td),
                  (SaxonUtils.fixStringValue("x"), columnStart * ratio + 1),
                  (SaxonUtils.fixStringValue("y"), rowStart + 1),
                  (SaxonUtils.fixStringValue("w"), cell.w * ratio),
                  (SaxonUtils.fixStringValue("h"), cell.h)
                )
            }
          }

        it.flatten
      }

    // NOTE: Conversions have too much boilerplate, we should improve this at some point.
    val cells =
      itOpt                                          map
      (_ map (MapFunctions.createValue(_)) toVector) map
      (ArrayFunctions.createValue(_))                getOrElse
      EmptySequence.getInstance

    MapFunctions.createValue(
      Map[AtomicValue, ValueRepresentation](
        (SaxonUtils.fixStringValue("width"),  gridWidth),        // xs:integer
        (SaxonUtils.fixStringValue("height"), allRowCells.size), // xs:integer
        (SaxonUtils.fixStringValue("cells"),  cells)             // array(map(*))?
      )
    )
  }


  /* How I would like to write the function below with explicit conversions for less boilerplate:

  def testAnalyze12ColumnGridAndFillHoles(grid: NodeInfo): Item = { // `array(map(xs:string, *)*)`
    Cell.analyze12ColumnGridAndFillHoles(grid, mergeHoles = true) map { row ⇒
      row collect {
        case Cell(uOpt, x, y, h, w, false) ⇒
          Map(
            "c" → uOpt getOrElse EmptySequence.getInstance,
            "x" → x,
            "y" → y,
            "w" → w,
            "h" → h
          ).asXPath // or `toXPath`?
      } asXPathSeq  // or `toXPathSeq`?
    } asXPathArray  // or `toXPathArray`?
  }
  */

  //@XPathFunction
  def analyze12ColumnGridAndFillHoles(grid: NodeInfo): Item = { // `array(map(xs:string, *)*)`
    ArrayFunctions.createValue(
      Cell.analyze12ColumnGridAndFillHoles(grid, mergeHoles = true, simplify = true).to[Vector] map { row ⇒
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