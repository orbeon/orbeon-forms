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

import org.orbeon.oxf.util.StaticXPath.ValueRepresentationType
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.{AtomicValue, EmptySequence, SequenceExtent}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.SimplePath._

import scala.collection.compat._

// This contains grid/cell operations acting on `NodeInfo`, which is on the source of the form definition
// as seen in Form Builder.
object NodeInfoCell {

  val GridTest: Test = Cell.GridTestName
  val TrTest  : Test = Cell.TrTestName
  val TdTest  : Test = Cell.TdTestName
  val CellTest: Test = Cell.CellTestName

  implicit object NodeInfoCellOps extends CellOps[NodeInfo] {

    def attValueOpt    (u: NodeInfo, name: String): Option[String] = u attValueOpt name
    def children       (u: NodeInfo, name: String): List[NodeInfo] = (u / name).to(List)
    def parent         (u: NodeInfo)              : NodeInfo       = u.parentUnsafe
    def hasChildElement(u: NodeInfo)              : Boolean        = u.hasChildElement

    def cellsForGrid   (u: NodeInfo)              : List[NodeInfo] = (u / CellTest).to(List)
    def gridForCell    (u: NodeInfo)              : NodeInfo       = parent(u)

    def maxGridWidth(u: NodeInfo): Int =
      if (u.attValueOpt("columns") contains "24") 24 else 12

    def x(u: NodeInfo): Option[Int] = attValueOpt(u, "x") map (_.toInt)
    def y(u: NodeInfo): Option[Int] = attValueOpt(u, "y") map (_.toInt)
    def w(u: NodeInfo): Option[Int] = attValueOpt(u, "w") map (_.toInt)
    def h(u: NodeInfo): Option[Int] = attValueOpt(u, "h") map (_.toInt)

    import org.orbeon.oxf.xforms.action.XFormsAPI

    def updateX(u: NodeInfo, x: Int): Unit = XFormsAPI.ensureAttribute(u, "x", x.toString)
    def updateY(u: NodeInfo, y: Int): Unit = XFormsAPI.ensureAttribute(u, "y", y.toString)
    def updateH(u: NodeInfo, h: Int): Unit = XFormsAPI.toggleAttribute(u, "h", h.toString, h > 1)
    def updateW(u: NodeInfo, w: Int): Unit = XFormsAPI.ensureAttribute(u, "w", w.toString)
  }

  // This function is used to migrate grids from the older `<xh:tr>`/`<xh:td>` format to the new `<fr:c>` format.
  //
  // Return `array(map(xs:string, *)*)`.
  //
  //@XPathFunction
  def analyzeTrTdGridAndFillHoles(grid: NodeInfo, forMigration: Boolean): Item = {

    val (gridWidth, allRowCells) = Cell.analyzeTrTdGrid(grid, simplify = true)

    val ratio =
      if (forMigration && Cell.StandardGridWidth % gridWidth == 0)
        Cell.StandardGridWidth / gridWidth
      else
        1

    SaxonUtils.newArrayItem(
      allRowCells.cells.to(Vector) map { row =>
        new SequenceExtent(
            row collect {
              case Cell(u, None, x, y, h, w) =>
                SaxonUtils.newMapItem(
                  Map[AtomicValue, ValueRepresentationType](
                    (SaxonUtils.fixStringValue("c"), u getOrElse EmptySequence.getInstance),
                    (SaxonUtils.fixStringValue("x"), (x - 1) * ratio + 1),
                    (SaxonUtils.fixStringValue("y"), y),
                    (SaxonUtils.fixStringValue("w"), w * ratio),
                    (SaxonUtils.fixStringValue("h"), h)
                  )
                )
            }
        )
      }
    )
  }

  //
  // This function is used to analyze a grid in `<fr:c>` format. It i used by `grid.xbl` at runtime and by tests.
  //
  // Return `array(map(xs:string, *)*)`.
  //
  //@XPathFunction
  def analyze12ColumnGridAndFillHoles(grid: NodeInfo, simplify: Boolean): Item =
    SaxonUtils.newArrayItem(
      Cell.analyze12ColumnGridAndFillHoles(grid, simplify).cells.to(Vector) map { row =>
        new SequenceExtent(
          row collect {
            case Cell(u, None, x, y, h, w) =>
              SaxonUtils.newMapItem(
                Map[AtomicValue, ValueRepresentationType](
                  (SaxonUtils.fixStringValue("c"), u getOrElse EmptySequence.getInstance),
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


  /* How I would like to write the function above with conversions for less boilerplate:

  def testAnalyze12ColumnGridAndFillHoles(grid: NodeInfo): Item =
    Cell.analyze12ColumnGridAndFillHoles(grid, mergeHoles = true) map { row =>
      row collect {
        case Cell(uOpt, x, y, h, w, false) =>
          Map(
            "c" -> uOpt getOrElse EmptySequence.getInstance,
            "x" -> x,
            "y" -> y,
            "w" -> w,
            "h" -> h
          ) asXPath // or `toXPath`?
      } asXPathSeq  // or `toXPathSeq`?
    } asXPathArray  // or `toXPathArray`?
  */
}