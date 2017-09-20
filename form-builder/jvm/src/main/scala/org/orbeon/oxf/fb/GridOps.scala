/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.oxf.fr.Cell._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.NodeInfoCell._
import org.orbeon.oxf.fr.{Cell, NodeInfoCell}
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

/*
 * Form Builder: operations on grids.
 */
trait GridOps extends ContainerOps {

//  import NodeInfoCell.NodeInfoCellOps

  // Get the first enclosing repeated grid or legacy repeat
  def getContainingGrid(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
    findAncestorContainers(descendantOrSelf, includeSelf) filter IsGrid head

  // Width of the grid in columns
  // TODO: FIX: Old layout.
  def getGridSize(grid: NodeInfo): Int = (grid / "*:tr").headOption.toList / "*:td" size

  def newTdElement(grid: NodeInfo, id: String, rowspan: Option[Int] = None): NodeInfo = rowspan match {
    case Some(rowspan) ⇒
      <xh:td xmlns:xh="http://www.w3.org/1999/xhtml" id={id} rowspan={rowspan.toString}/>
    case _ ⇒
      <xh:td xmlns:xh="http://www.w3.org/1999/xhtml" id={id}/>
  }

  // TODO: FIX: Old layout.
  private def trAtRowPos(gridId: String, rowPos: Int): NodeInfo = {
    val grid = containerById(gridId)
    val trs = grid / "*:tr"
    // Reason for the modulo: the value of rowPos sent by client is on the flatten iterations / rows;
    // it is not just row position in the first iteration.
    trs(rowPos % trs.length)
  }

  // Insert a row below
  def insertRowBelow(gridId: String, rowPos: Int): NodeInfo = insertRowBelow(trAtRowPos(gridId, rowPos))
  def insertRowBelow(tr: NodeInfo): NodeInfo = {

    // NOTE: This algorithm expands rowspans that span over the current row, but not rowspans that end with the
    // current row.
    val grid = getContainingGrid(tr)
    val rowCells = getRowCells(tr)

    // Number of cells that end at the current row
    val newCellCount = rowCells count (cell ⇒ cell.h == 1)

    // Increment rowspan of cells that don't end at the current row
    rowCells foreach { cell ⇒
      if (cell.h > 1)
        NodeInfoCellOps.underlyingRowspan_(cell.td, NodeInfoCellOps.underlyingRowspan(cell.td) + 1)
    }

    // Insert the new row
    val result = insert(into = grid, after = tr, origin = newRow(grid, newCellCount)).headOption
    debugDumpDocumentForGrids("insert row below", grid)
    result orNull // bad, but insert() is not always able to return the inserted item at this time
  }

  // Insert a row above
  def insertRowAbove(gridId: String, rowPos: Int): NodeInfo = insertRowAbove(trAtRowPos(gridId, rowPos))
  // TODO: FIX: Old layout.
  def insertRowAbove(tr: NodeInfo): NodeInfo =
    tr precedingSibling "*:tr" headOption match {
      case Some(prevRow) ⇒
        // Do as if this was an insert below the previous row
        // This makes things simpler as we can reuse insertRowBelow, but maybe another logic could make sense too
        insertRowBelow(prevRow)
      case None ⇒
        // Insert as first row of the table
        val grid = getContainingGrid(tr)
        val result = insert(into = grid, before = tr, origin = newRow(grid, getGridSize(grid))).head
        debugDumpDocumentForGrids("insert row above", grid)
        result
    }

  private def newRow(grid: NodeInfo, size: Int): NodeInfo = {
    // Get as many fresh ids as there are tds
    val ids = nextIds(grid, "tmp", size).toIterator

    <xh:tr xmlns:xh="http://www.w3.org/1999/xhtml">{
      1 to size map (_ ⇒ <xh:td id={ids.next()}/>)
    }</xh:tr>
  }

  // Delete a row and contained controls

  def deleteRow(gridId: String, rowPos: Int): Unit =
    deleteRow(trAtRowPos(gridId, rowPos))

  def deleteRow(tr: NodeInfo): Unit = {

    val doc = tr.getDocumentRoot

    val allRowCells  = getAllRowCells(getContainingGrid(tr))

    // TODO: FIX: Old layout.
    val posy = tr precedingSibling "*:tr" size
    val rowCells = allRowCells(posy)
    val nextRowCells = if (allRowCells.size > posy + 1) Some(allRowCells(posy + 1)) else None

    // Find all tds to delete
    val tdsToDelete = tr descendant "*:td"

    // Find the new td to select if we are removing the currently selected td
    val newTdToSelect = findNewTdToSelect(tr, tdsToDelete)

    // Decrement rowspans if needed
    rowCells.zipWithIndex foreach {
      case (cell, posx) ⇒
        if (NodeInfoCellOps.underlyingRowspan(cell.td) > 1) {
          if (cell.missing) {
            // This cell is part of a rowspan that starts in a previous row, so decrement
            NodeInfoCellOps.underlyingRowspan_(cell.td, NodeInfoCellOps.underlyingRowspan(cell.td) - 1)
          } else if (nextRowCells.isDefined) {
            // This cell is the start of a rowspan, and we are deleting it, so add a td in the next row
            // TODO XXX: issue: as we insert tds, we can't rely on Cell info unless it is updated ⇒
          }
        }
    }

    // Delete all controls in the row
    tdsToDelete foreach (deleteCellContent(_))

    // Delete row and its content
    delete(tr)

    // Update templates
    updateTemplatesCheckContainers(doc, findAncestorRepeatNames(tr).to[Set])

    // Adjust selected td if needed
    newTdToSelect foreach selectTd

    debugDumpDocumentForGrids("delete row", tr)
  }

  // Whether this is the last grid in the section
  // NOTE: Use this until we implement the new selection system allowing moving stuff around freely
  def isLastGridInSection(grid: NodeInfo) = childrenGrids(findAncestorContainers(grid).head).size == 1

  // TODO: FIX: Old layout.
  private def tdAtColPosOpt(gridId: String, colPos: Int): Option[NodeInfo] = {

    require(colPos >= 0)

    val grid        = containerById(gridId)
    val firstRowOpt = (grid / "*:tr").headOption
    val tds         = firstRowOpt.toList / "*:td"

    colPos < tds.size option tds(colPos)
  }

  def maxGridColumns = Properties.instance.getPropertySet.getInteger("oxf.fb.grid.max-columns", 4)

  // Insert a column to the right if possible
  //@XPathFunction
  def insertColRight(gridId: String, colPos: Int): Unit =
    tdAtColPosOpt(gridId, colPos) foreach insertColRight

  def insertColRight(firstRowTd: NodeInfo): Unit = {
    val grid = getContainingGrid(firstRowTd)
    if (getGridSize(grid) < maxGridColumns) {

      val allRowCells = getAllRowCells(grid)
      val pos = firstRowTd precedingSibling "*:td" size

      val ids = nextIds(grid, "tmp", allRowCells.size).toIterator

      allRowCells foreach { cells ⇒
        val cell = cells(pos)

        // For now insert same rowspans as previous column, but could also insert full column as an option
        if (! cell.missing) {
          insert(into = cell.td parent *, after = cell.td, origin = newTdElement(grid, ids.next(), if (cell.h > 1) Some(cell.h) else None))
        }
      }

      debugDumpDocumentForGrids("insert col right", grid)
    }
  }

  // Insert a column to the left if possible
  //@XPathFunction
  def insertColLeft(gridId: String, colPos: Int): Unit =
    tdAtColPosOpt(gridId, colPos) foreach insertColLeft

  def insertColLeft(firstRowTd: NodeInfo): Unit = {
    val grid = getContainingGrid(firstRowTd)
    if (getGridSize(grid) < maxGridColumns) {
      val pos = firstRowTd precedingSibling "*:td" size

      if (pos > 0) {
        // Do as if this was an insert to the right of the previous column
        // This makes things simpler as we can reuse insertColRight, but maybe another logic could make sense too
        insertColRight(firstRowTd precedingSibling "*:td" head)
      } else {
        // First column: just insert plain tds as the first row
        // TODO: FIX: Old layout.
        val trs = grid / "*:tr"
        val ids = nextIds(grid, "tmp", trs.size).toIterator

        trs foreach { tr ⇒
          insert(into = tr, origin = newTdElement(grid, ids.next()))
        }

        debugDumpDocumentForGrids("insert col left", grid)
      }
    }
  }

  // Find a column's tds
  // TODO: FIX: Old layout.
  def getColTds(td: NodeInfo) = {
    val rows = getContainingGrid(td) / "*:tr"
    val (x, _) = tdCoordinates(td)

    rows map (row ⇒ (row / "*:td")(x))
  }

  // Delete a column and contained controls if possible
  //@XPathFunction
  def deleteCol(gridId: String, colPos: Int): Unit =
    tdAtColPosOpt(gridId, colPos) foreach deleteCol

  def deleteCol(firstRowTd: NodeInfo): Unit = {

    val doc = firstRowTd.getDocumentRoot

    val grid = getContainingGrid(firstRowTd)
    val allRowCells = getAllRowCells(grid)
    val pos = firstRowTd precedingSibling "*:td" size

    // Find all tds to delete
    val tdsToDelete = allRowCells map (_(pos)) filterNot (_.missing) map (_.td)

    // Find the new td to select if we are removing the currently selected td
    val newTdToSelect = findNewTdToSelect(firstRowTd, tdsToDelete)

    // Delete the concrete td at this column position in each row
    tdsToDelete foreach { td ⇒
      deleteCellContent(td)
      delete(td)
    }

    // Update templates
    updateTemplatesCheckContainers(doc, findAncestorRepeatNames(firstRowTd).to[Set])

    // Adjust selected td if needed
    newTdToSelect foreach selectTd

    debugDumpDocumentForGrids("delete col", grid)
  }

  //@XPathFunction
  def controlsInCol(gridId: String, colPos: Int): Int =
    tdAtColPosOpt(gridId, colPos) map controlsInCol getOrElse 0

  def controlsInCol(firstRowTd: NodeInfo): Int = {
    val grid = getContainingGrid(firstRowTd)
    val allRowCells = getAllRowCells(grid)

    val (x, _) = NodeInfoCell.tdCoordinates(firstRowTd: NodeInfo, allRowCells).get

    allRowCells map (_(x)) filterNot (_.missing) count (cell ⇒ cell.td.hasChildElement)
  }

  def controlsInRow(gridId: String, rowPos: Int): Int = {
    val row = trAtRowPos(gridId, rowPos)
    (row / "*:td" / *).length
  }

  private def selectedCellVar =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("selected-cell")

  // Find the currently selected grid td if any
  def findSelectedTd(inDoc: NodeInfo) =
    findInViewTryIndex(inDoc, selectedCellVar.stringValue)

  //@XPathFunction
  def selectTdForControlId(inDoc: NodeInfo, controlId: String): Unit =
    findControlByName(inDoc, controlNameFromId(controlId)).to[List] flatMap (_ parent "*:td") foreach selectTd

  // Make the given grid td selected
  def selectTd(newTd: NodeInfo): Unit =
    setvalue(selectedCellVar, newTd.id)

  // Whether a call to ensureEmptyTd() will succeed
  // For now say we'll always succeed as we'll fill gaps and insert a row as needed.
  // TODO: Remove once no longer needed.
  def willEnsureEmptyTdSucceed(inDoc: NodeInfo): Boolean =
    findSelectedTd(inDoc).isDefined

  // Try to ensure that there is an empty td after the current location, inserting a new row if possible
  def ensureEmptyTd(inDoc: NodeInfo): Option[NodeInfo] =
    findSelectedTd(inDoc) flatMap { currentCellNode ⇒
      if (currentCellNode.hasChildElement) {
        // There is an element in the current td, figure out what to do

        // - start with `currentCell`
        // - find closest following available space with width `MinimumWidth`
        //   - could be size of `currentCell`, or a fixed value, or dependent on control type
        // - if found, then insert new cell at the right spot
        // - if none, then insert new row below OR at end of grid

        val grid         = getContainingGrid(currentCellNode)
        val cells        = Cell.analyze12ColumnGridAndFillHoles(grid, mergeHoles = true, simplify = false)
        val currentCell  = Cell.find(currentCellNode, cells).get
        val MinimumWidth = currentCell.w

        val availableCellOpt = cells.iterator.flatten collectFirst {
          case c @ Cell(None, _, _, _, w, _) if w >= MinimumWidth ⇒ c
        }

        val newCell =
          availableCellOpt match {
            case Some(availableCell) ⇒

              val precedingCellOpt =
                cells.iterator.flatten filter
                (! _.missing)          takeWhile
                (_ != availableCell)   lastOption()

              precedingCellOpt flatMap (_.u) flatMap { precedingCellNode ⇒

                val newCellNode: NodeInfo =
                  <fr:c
                    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                    id={nextId(grid, "tmp")}
                    x={availableCell.x.toString}
                    y={availableCell.y.toString}
                    w={MinimumWidth.toString}/>

                insert(into = Nil, after = precedingCellNode, origin = newCellNode).headOption
              }
            case None ⇒
              val newCellNode: NodeInfo =
                <fr:c
                  xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                  id={nextId(grid, "tmp")}
                  x="1"
                  y={cells.size + 1 toString}
                  w={MinimumWidth.toString}/>

              insert(into = Nil, after = cells.last.last.u.toList, origin = newCellNode).headOption
          }

         newCell |!> selectTd
      } else
        Some(currentCellNode)
    }

  // @min/@max can be simple AVTs, i.e. AVTs which cover the whole attribute, e.g. "{my-min}"
  // The main reason to do this instead of making @min/@max plain XPath expressions is that @max also supports the
  // literal "none" (and "unbounded" for backward compatibility).
  // NOTE: This doesn't check that the expression is syntactically correct, in particular it doesn't check that
  // curly brackets are absent or escaped within the AVT.
  private val SimpleAVTRegex = """^\{(.+)\}$""".r

  private def trimSimpleAVT(s: String) = s match {
    case SimpleAVTRegex(v) ⇒ v.replaceAllLiterally("{{", "{").replaceAllLiterally("}}", "}")
    case v                 ⇒ v
  }

  // NOTE: Value can be a simple AVT
  def getNormalizedMin(doc: NodeInfo, gridName: String) =
    findControlByName(doc, gridName) flatMap (_ attValueOpt "min") map trimSimpleAVT getOrElse "0"

  private val NoMaximum = Set("none", "unbounded")

  // NOTE: Value can be a simple AVT
  def getNormalizedMax(doc: NodeInfo, gridName: String) =
    findControlByName(doc, gridName) flatMap (_ attValueOpt "max") filterNot NoMaximum map trimSimpleAVT

  // XForms callers: get the grid's normalized max attribute, the empty sequence if no maximum
  def getNormalizedMaxOrEmpty(doc: NodeInfo, gridName: String) =
    getNormalizedMax(doc, gridName).orNull

  // Convert a min/max value to a value suitable to be written to the @min/@max attributes.
  //
  // - blank value                → None
  // - non-positive integer value → None
  // - positive integer value     → Some(int: String)
  // - any other value            → Some("{expression}")
  def minMaxForAttribute(s: String) = s.trimAllToOpt flatMap { value ⇒
    try {
      val int = value.toInt
      int > 0 option int.toString
    } catch {
      case _: NumberFormatException ⇒
        val escaped = value.replaceAllLiterally("{", "{{").replaceAllLiterally("}", "}}")
        Some(s"{$escaped}")
    }
  }

  // Get the x/y position of a td given Cell information
  def tdCoordinates(td: NodeInfo): (Int, Int) =
    NodeInfoCell.tdCoordinates(td, getAllRowCells(getContainingGrid(td))).get

  private def canExpandCell(td: NodeInfo): Boolean = {
    val allRowCells = getAllRowCells(getContainingGrid(td))
    val (x, y) = NodeInfoCell.tdCoordinates(td, allRowCells).get
    val cell = allRowCells(y)(x)

    (y + cell.h) < allRowCells.size
  }

  private def canShrinkCell(td: NodeInfo): Boolean = {
    val allRowCells = getAllRowCells(getContainingGrid(td))
    val (x, y) = NodeInfoCell.tdCoordinates(td, allRowCells).get
    val cell = allRowCells(y)(x)

    cell.h > 1
  }

  // Whether there will be controls to delete if the cell is expanded
  // @XPathFunction
  def expandCellTouchesControl(td: NodeInfo): Boolean =
    canExpandCell(td) && { // https://github.com/orbeon/orbeon-forms/issues/3282

    debugDumpDocumentForGrids("expandCellTouchesControl", td)

    val allRowCells = getAllRowCells(getContainingGrid(td))
    val (x, y) = NodeInfoCell.tdCoordinates(td, allRowCells).get

    val cell = allRowCells(y)(x)

    allRowCells(y + cell.h)(x).td.hasChildElement
  }


  // Vertically expand the given cell
  //@XPathFunction
  def expandCell(td: NodeInfo): Unit =
    if (canExpandCell(td)) {

      debugDumpDocumentForGrids("expandCell before", td)

      val allRowCells = getAllRowCells(getContainingGrid(td))
      val (x, y) = NodeInfoCell.tdCoordinates(td, allRowCells).get

      val cell = allRowCells(y)(x)
      val cellBelow = allRowCells(y + cell.h)(x)

      // Increment rowspan
      NodeInfoCellOps.underlyingRowspan_(cell.td, NodeInfoCellOps.underlyingRowspan(cell.td) + NodeInfoCellOps.underlyingRowspan(cellBelow.td))

      // Delete cell below
      delete(cellBelow.td)

      debugDumpDocumentForGrids("expandCell after", td)
    }

  // Vertically shrink the given cell
  //@XPathFunction
  def shrinkCell(td: NodeInfo): Unit =
    if (canShrinkCell(td)) {
      debugDumpDocumentForGrids("shrinkCell before", td)

      val grid = getContainingGrid(td)
      val allRowCells  = getAllRowCells(grid)

      val (x, y) = NodeInfoCell.tdCoordinates(td, allRowCells).get

      val cell = allRowCells(y)(x)

      // Decrement rowspan attribute
      NodeInfoCellOps.underlyingRowspan_(cell.td, NodeInfoCellOps.underlyingRowspan(cell.td) - 1)

      // Insert new td
      val posyToInsertInto = y + cell.h - 1
      val rowBelow = allRowCells(posyToInsertInto)

      // TODO: FIX: Old layout.
      val trToInsertInto = grid / "*:tr" apply posyToInsertInto
      val tdToInsertAfter = rowBelow.slice(0, x).reverse find (! _.missing) map (_.td) toSeq

      insert(into = trToInsertInto, after = tdToInsertAfter, origin = newTdElement(grid, nextId(grid, "tmp")))

      debugDumpDocumentForGrids("shrinkCell after", td)
    }

  def initializeGrids(doc: NodeInfo): Unit = {
    // 1. Annotate all the grid tds of the given document with unique ids, if they don't have them already
    // We do this so that ids are stable as we move things around, otherwise if the XForms document is recreated
    // new automatic ids are generated for objects without id.

    def annotate(token: String, elements: Seq[NodeInfo]): Unit = {
      // Get as many fresh ids as there are tds
      val ids = nextIds(doc, token, elements.size).toIterator

      // Add the missing ids
      elements foreach (ensureAttribute(_, "id", ids.next()))
    }

    // All grids and grid tds with no existing id
    val bodyElement = findFRBodyElement(doc)
    val grids       = bodyElement descendant "*:grid"

    annotate("tmp", grids descendant ("*:c" || "*:td") filterNot (_.hasId)) // remove `*:td` annotation once we are sure we only need `*:c`
    annotate("tmp", grids filterNot (_.hasId))

    // 2. Select the first td if any
    bodyElement descendant "*:grid" descendant "*:c" take 1 foreach selectTd
  }

  def deleteGridById(gridId: String) =
    deleteContainerById(canDeleteGrid, gridId)

  // TODO: FIX: Old layout.
  def canDeleteGrid(grid: NodeInfo): Boolean = canDeleteContainer(grid)
  def canDeleteRow (grid: NodeInfo): Boolean = (grid / "*:tr").size > 1
  def canDeleteCol (grid: NodeInfo): Boolean = ((grid / "*:tr").headOption.toList / "*:td").size > 1

  private val DeleteTests = List(
    "grid" → canDeleteGrid _,
    "row"  → canDeleteRow  _,
    "col"  → canDeleteCol  _
  )

  // Return all classes that need to be added to an editable grid
  def gridCanDoClasses(gridId: String): Seq[String] = {

    val grid = containerById(gridId)

    val deleteClasses = DeleteTests collect { case (what, test) if test(grid) ⇒ "fb-can-delete-" + what }
    val insertClasses = getGridSize(grid) < maxGridColumns list "fb-can-add-col"

    "fr-editable" :: deleteClasses ::: insertClasses
  }

  // Find the new td to select if we are removing the currently selected td
  def findNewTdToSelect(inDoc: NodeInfo, tdsToDelete: Seq[NodeInfo]) =
    findSelectedTd(inDoc) match {
      case Some(selectedTd) if tdsToDelete contains selectedTd ⇒
        // Prefer trying following before preceding, as things move up and left when deleting
        // NOTE: Could improve this by favoring things "at same level", e.g. stay in grid if possible, then
        // stay in section, etc.
        (followingTd(selectedTd) filterNot (tdsToDelete contains _) headOption) orElse
          (precedingTds(selectedTd) filterNot (tdsToDelete contains _) headOption)
      case _ ⇒
        None
    }

  // Return a td's preceding tds in the hierarchy of containers
  def precedingTds(td: NodeInfo) = {
    val preceding = td preceding "*:td"
    preceding intersect (findAncestorContainers(td).last descendant "*:td")
  }

  // Return a td's following tds in the hierarchy of containers
  def followingTd(td: NodeInfo) = {
    val following = td following "*:td"
    following intersect (findAncestorContainers(td).last descendant "*:td")
  }
}