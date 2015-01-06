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

import collection.mutable
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._

/*
 * Form Builder: operations on grids.
 */
trait GridOps extends ContainerOps {

    case class Cell(td: NodeInfo, rowspan: Int, missing: Boolean) {
        def originalRowspan = getNormalizedRowspan(td)
        def originalRowspan_= (newRowSpan: Int): Unit = ensureAttribute(td, "rowspan", newRowSpan.toString)
    }

    // Get the first enclosing repeated grid or legacy repeat
    def getContainingGrid(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
        findAncestorContainers(descendantOrSelf, includeSelf) filter IsGrid head

    // Extract the rowspan of a td (default is 1 if there is no attribute)
    private def getNormalizedRowspan(td: NodeInfo) =  td attValueOpt "rowspan" map (_.toInt) getOrElse 1

    // For the previous row of prepared cells, and a new row of tds, return the new row of prepared cells
    private def newCellsRow(previousRow: Seq[Cell], tds: Seq[NodeInfo]): Seq[Cell] = previousRow match {
        case Seq() ⇒
            // First row: start with initial rowspans
            tds map (td ⇒ Cell(td, getNormalizedRowspan(td), missing = false))
        case _ ⇒
            // Subsequent rows
            val tdsIterator = tds.toIterator
            previousRow map {
                case Cell(_, 1, _) ⇒
                    val td = tdsIterator.next()
                    Cell(td, getNormalizedRowspan(td), missing = false)
                case Cell(td, rowspan, _) ⇒
                    Cell(td, rowspan - 1, missing = true)
            }
    }

    // Get cell/rowspan information for the given grid row
    def getRowCells(tr: NodeInfo): Seq[Cell] = {

        // All trs up to and including the current tr
        val trs = (tr precedingSibling "*:tr").reverse :+ tr
        // For each row, the Seq of tds
        val rows = trs map (_ \ "*:td")

        // Return the final row of prepared cells
        rows.foldLeft(Seq[Cell]())(newCellsRow)
    }

    // Get cell/rowspan information for all the rows in the grid
    def getAllRowCells(grid: NodeInfo): Seq[Seq[Cell]] = {

        // All trs up to and including the current tr
        val trs = grid \ "*:tr"
        // For each row, the Seq of tds
        val rows = trs map (_ \ "*:td")

        // Accumulate the result for each row as we go
        val result = mutable.Buffer[Seq[Cell]]()

        rows.foldLeft(Seq[Cell]()) { (previousRow, tds) ⇒
            val newRow = newCellsRow(previousRow, tds)
            result += newRow
            newRow
        }

        result
    }

    // Width of the grid in columns
    def getGridSize(grid: NodeInfo) = (grid \ "*:tr")(0) \ "*:td" size

    def newTdElement(grid: NodeInfo, id: String, rowspan: Option[Int] = None): NodeInfo = rowspan match {
        case Some(rowspan) ⇒
            <xh:td xmlns:xh="http://www.w3.org/1999/xhtml" id={id} rowspan={rowspan.toString}/>
        case _ ⇒
            <xh:td xmlns:xh="http://www.w3.org/1999/xhtml" id={id}/>
    }

    private def trAtRowPos(gridId: String, rowPos: Int): NodeInfo = {
        val grid = containerById(gridId)
        val trs = grid \ "*:tr"
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
        val newCellCount = rowCells count (cell ⇒ cell.rowspan == 1)

        // Increment rowspan of cells that don't end at the current row
        rowCells foreach { cell ⇒
            if (cell.rowspan > 1)
                cell.originalRowspan += 1
        }

        // Insert the new row
        val result = insert(into = grid, after = tr, origin = newRow(grid, newCellCount)).headOption
        debugDumpDocumentForGrids("insert row below", grid)
        result orNull // bad, but insert() is not always able to return the inserted item at this time
    }

    // Insert a row above
    def insertRowAbove(gridId: String, rowPos: Int): NodeInfo = insertRowAbove(trAtRowPos(gridId, rowPos))
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

        val posy = tr precedingSibling "*:tr" size
        val rowCells = allRowCells(posy)
        val nextRowCells = if (allRowCells.size > posy + 1) Some(allRowCells(posy + 1)) else None

        // Find all tds to delete
        val tdsToDelete = tr \\ "*:td"

        // Find the new td to select if we are removing the currently selected td
        val newTdToSelect = findNewTdToSelect(tr, tdsToDelete)

        // Decrement rowspans if needed
        rowCells.zipWithIndex foreach {
            case (cell, posx) ⇒
                if (cell.originalRowspan > 1) {
                    if (cell.missing) {
                        // This cell is part of a rowspan that starts in a previous row, so decrement
                        cell.originalRowspan -= 1
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
        updateTemplates(doc)

        // Adjust selected td if needed
        newTdToSelect foreach selectTd

        debugDumpDocumentForGrids("delete row", tr)
    }

    // Whether this is the last grid in the section
    // NOTE: Use this until we implement the new selection system allowing moving stuff around freely
    def isLastGridInSection(grid: NodeInfo) = childrenGrids(findAncestorContainers(grid).head).size == 1

    private def tdAtColPos(gridId: String, colPos: Int): NodeInfo = {
        val grid = containerById(gridId)
        val firstRow = (grid \ "*:tr").head
        (firstRow \ "*:td")(colPos)
    }

    def maxGridColumns = Properties.instance.getPropertySet.getInteger("oxf.fb.grid.max-columns", 4)

    // Insert a column to the right
    def insertColRight(gridId: String, colPos: Int): Unit = insertColRight(tdAtColPos(gridId, colPos))
    def insertColRight(firstRowTd: NodeInfo) {
        val grid = getContainingGrid(firstRowTd)
        if (getGridSize(grid) < maxGridColumns) {

            val allRowCells = getAllRowCells(grid)
            val pos = firstRowTd precedingSibling "*:td" size

            val ids = nextIds(grid, "tmp", allRowCells.size).toIterator

            allRowCells foreach { cells ⇒
                val cell = cells(pos)

                // For now insert same rowspans as previous column, but could also insert full column as an option
                if (! cell.missing) {
                    insert(into = cell.td parent *, after = cell.td, origin = newTdElement(grid, ids.next(), if (cell.rowspan > 1) Some(cell.rowspan) else None))
                }
            }

            debugDumpDocumentForGrids("insert col right", grid)
        }
    }

    // Insert a column to the left
    def insertColLeft(gridId: String, colPos: Int): Unit = insertColLeft(tdAtColPos(gridId, colPos))
    def insertColLeft(firstRowTd: NodeInfo) {
        val grid = getContainingGrid(firstRowTd)
        if (getGridSize(grid) < maxGridColumns) {
            val pos = firstRowTd precedingSibling "*:td" size

            if (pos > 0) {
                // Do as if this was an insert to the right of the previous column
                // This makes things simpler as we can reuse insertColRight, but maybe another logic could make sense too
                insertColRight(firstRowTd precedingSibling "*:td" head)
            } else {
                // First column: just insert plain tds as the first row
                val trs = grid \ "*:tr"
                val ids = nextIds(grid, "tmp", trs.size).toIterator

                trs foreach { tr ⇒
                    insert(into = tr, origin = newTdElement(grid, ids.next()))
                }

                debugDumpDocumentForGrids("insert col left", grid)
            }
        }
    }

    // Find a column's tds
    def getColTds(td: NodeInfo) = {
        val rows = getContainingGrid(td) \ "*:tr"
        val (x, _) = tdCoordinates(td)

        rows map (row ⇒ (row \ "*:td")(x))
    }

    // Insert a column and contained controls
    def deleteCol(gridId: String, colPos: Int): Unit =
        deleteCol(tdAtColPos(gridId, colPos))

    def deleteCol(firstRowTd: NodeInfo) {

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
        updateTemplates(doc)

        // Adjust selected td if needed
        newTdToSelect foreach selectTd

        debugDumpDocumentForGrids("delete col", grid)
    }

    def controlsInCol(gridId: String, colPos: Int): Int = controlsInCol(tdAtColPos(gridId, colPos))
    def controlsInCol(firstRowTd: NodeInfo): Int = {
        val grid = getContainingGrid(firstRowTd)
        val allRowCells = getAllRowCells(grid)

        val (x, _) = tdCoordinates(firstRowTd: NodeInfo, allRowCells: Seq[Seq[Cell]])

        allRowCells map (_(x)) filterNot (_.missing) count (cell ⇒ hasChildren(cell.td))
    }

    def controlsInRow(gridId: String, rowPos: Int): Int = {
        val row = trAtRowPos(gridId, rowPos)
        (row \ "*:td" \ *).length
    }

    private def selectedCellVar =
        asNodeInfo(topLevelModel("fr-form-model").get.getVariable("selected-cell"))

    // Find the currently selected grid td if any
    def findSelectedTd(inDoc: NodeInfo) =
        findInViewTryIndex(inDoc, selectedCellVar.stringValue)

    // Make the given grid td selected
    def selectTd(newTd: NodeInfo): Unit =
        setvalue(selectedCellVar, newTd \@ "id" stringValue)

    // Whether a call to ensureEmptyTd() will succeed
    def willEnsureEmptyTdSucceed(inDoc: NodeInfo): Boolean =
        findSelectedTd(inDoc) match {
            case Some(currentTd) ⇒
                if (currentTd \ * nonEmpty)
                    currentTd followingSibling "*:td" match {
                        case Seq(followingTd, _*) if followingTd \ * nonEmpty  ⇒ false
                        case _ ⇒ true
                    }
                else
                    true
            case None ⇒ false
        }

    // Try to ensure that there is an empty td after the current location, inserting a new row if possible
    def ensureEmptyTd(inDoc: NodeInfo): Option[NodeInfo] = {

        findSelectedTd(inDoc) flatMap { currentTd ⇒

            if (currentTd \ * nonEmpty) {
                // There is an element in the current td, figure out what to do

                currentTd followingSibling "*:td" match {
                    case Seq(followingTd, _*) if followingTd \ * isEmpty  ⇒
                        // Next td exists is empty: move to that one
                        selectTd(followingTd)
                        Some(followingTd)
                    case Seq(followingTd, _*) ⇒
                        // Next td exists but is not empty: NOP for now
                        None
                    case _ ⇒
                        // We are the last cell of the row
                        val nextTr = currentTd.getParent followingSibling "*:tr" take 1
                        val nextTrFirstTd = nextTr \ "*:td" take 1

                        val newTd =
                            if (nextTr.isEmpty || (nextTrFirstTd \ *).nonEmpty)
                                // The first cell of the next row is occupied, or there is no next row: insert new row
                                insertRowBelow(currentTd.getParent) \ "*:td" head
                            else
                                // There is a next row, and its first cell is empty: move to that one
                                nextTrFirstTd.head

                        selectTd(newTd)
                        Some(newTd)
                }
            } else
                Some(currentTd)
        }
    }

    // @mi/@max can be simple AVTs, i.e. AVTs which cover the whole attribute, e.g. "{my-min}"
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
    def minMaxForAttribute(s: String) = nonEmptyOrNone(s) flatMap { value ⇒
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
    private def tdCoordinates(td: NodeInfo, cells: Seq[Seq[Cell]]): (Int, Int) = {

        // Search rows first, then cols
        // Another solution would be to store the position directly into Cell

        val y = td parent * precedingSibling "*:tr" size
        val x = cells(y) indexWhere (_.td == td)

        (x, y)
    }

    // Get the x/y position of a td given Cell information
    def tdCoordinates(td: NodeInfo): (Int, Int) =
        tdCoordinates(td, getAllRowCells(getContainingGrid(td)))

    // Whether there will be controls to delete if the cell is expanded
    def expandCellTouchesControl(td: NodeInfo): Boolean = {
        val allRowCells = getAllRowCells(getContainingGrid(td))
        val (x, y) = tdCoordinates(td, allRowCells)

        val cell = allRowCells(y)(x)

        hasChildren(allRowCells(y + cell.rowspan)(x).td)
    }

    // Vertically expand the given cell
    def expandCell(td: NodeInfo) {
        val allRowCells  = getAllRowCells(getContainingGrid(td))
        val (x, y) = tdCoordinates(td, allRowCells)

        val cell = allRowCells(y)(x)
        val cellBelow = allRowCells(y + cell.rowspan)(x)

        // Increment rowspan
        cell.originalRowspan += cellBelow.originalRowspan

        // Delete cell below
        delete(cellBelow.td)
    }

    // Vertically shrink the given cell
    def shrinkCell(td: NodeInfo) {

        val grid = getContainingGrid(td)
        val allRowCells  = getAllRowCells(grid)

        val (x, y) = tdCoordinates(td, allRowCells)

        val cell = allRowCells(y)(x)

        // Decrement rowspan attribute
        cell.originalRowspan -= 1

        // Insert new td
        val posyToInsertInto = y + cell.rowspan - 1
        val rowBelow = allRowCells(posyToInsertInto)

        val trToInsertInto = grid \ "*:tr" apply posyToInsertInto
        val tdToInsertAfter = rowBelow.slice(0, x).reverse find (! _.missing) map (_.td) toSeq

        insert(into = trToInsertInto, after = tdToInsertAfter, origin = newTdElement(grid, nextId(grid, "tmp")))
    }

    def initializeGrids(doc: NodeInfo) {
        // 1. Annotate all the grid tds of the given document with unique ids, if they don't have them already
        // We do this so that ids are stable as we move things around, otherwise if the XForms document is recreated
        // new automatic ids are generated for objects without id.

        def annotate(token: String, elements: Seq[NodeInfo]) = {
            // Get as many fresh ids as there are tds
            val ids = nextIds(doc, token, elements.size).toIterator

            // Add the missing ids
            elements foreach (ensureAttribute(_, "id", ids.next()))
        }

        // All grids and grid tds with no existing id
        val bodyElement = findFRBodyElement(doc)
        annotate("tmp", bodyElement \\ "*:grid" \\ "*:td" filterNot hasId)
        annotate("tmp", bodyElement \\ "*:grid" filterNot hasId)

        // 2. Select the first td if any
        bodyElement \\ "*:grid" \\ "*:td" take 1 foreach selectTd
    }

    def canDeleteGrid(grid: NodeInfo): Boolean =
        canDeleteContainer(grid)

    def deleteGridById(gridId: String) =
        deleteContainerById(canDeleteGrid, gridId)

    def canDeleteRow(grid: NodeInfo): Boolean = (grid \ "*:tr").length > 1
    def canDeleteCol(grid: NodeInfo): Boolean = ((grid \ "*:tr").head \ "*:td").length > 1

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