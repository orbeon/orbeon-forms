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

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.ContainerOps._
import collection.mutable.Buffer
import org.orbeon.saxon.om.NodeInfo

/*
 * Form Builder: operations on grids.
 */
object GridOps {

    case class Cell(td: NodeInfo, rowspan: Int, missing: Boolean) {
        def originalRowspan = getRowspan(td)
        def originalRowspan_= (newRowSpan: Int): Unit = ensureAttribute(td, "rowspan", newRowSpan.toString)
    }

    // Find a grid element
    def findGridElement(doc: NodeInfo, gridName: String) =
        findControlById(doc, gridId(gridName))

    // XForms callers: find a grid element by name or null (the empty sequence)
    def findGridElementOrEmpty(doc: NodeInfo, gridName: String) =
        findGridElement(doc, gridName).orNull

    def isGridOrRepeat(nodeInfo: NodeInfo) = nodeInfo self ("*:grid" || "*:repeat") nonEmpty
    def isRepeat(nodeInfo: NodeInfo) = ((nodeInfo self "*:grid" nonEmpty) && nodeInfo \@ "repeat" === "true") || (nodeInfo self "*:repeat" nonEmpty)

    // Find the first enclosing repeated grid or legacy repeat if any
    def findContainingRepeat(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
        findAncestorContainers(descendantOrSelf, includeSelf) filter (isRepeat(_)) headOption

    // Get the first enclosing repeated grid or legacy repeat
    def getContainingGridOrRepeat(descendantOrSelf: NodeInfo, includeSelf: Boolean = false) =
        findAncestorContainers(descendantOrSelf, includeSelf) filter (isGridOrRepeat(_)) head

    // Extract the rowspan of a td (default is 1 if there is no attribute)
    private def getRowspan(td: NodeInfo) =  attValueOption(td \@ "rowspan") map (_.toInt) getOrElse 1

    // For the previous row of prepared cells, and a new row of tds, return the new row of prepared cells
    private def newCellsRow(previousRow: Seq[Cell], tds: Seq[NodeInfo]): Seq[Cell] = previousRow match {
        case Seq() =>
            // First row: start with initial rowspans
            tds map (td => Cell(td, getRowspan(td), false))
        case _ =>
            // Subsequent rows
            val tdsIterator = tds.toIterator
            previousRow map {
                case Cell(_, 1, _) =>
                    val td = tdsIterator.next()
                    Cell(td, getRowspan(td), false)
                case Cell(td, rowspan, _) =>
                    Cell(td, rowspan - 1, true)
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
        val result = Buffer[Seq[Cell]]()

        rows.foldLeft(Seq[Cell]()) { (previousRow, tds) =>
            val newRow = newCellsRow(previousRow, tds)
            result += newRow
            newRow
        }

        result
    }

    def getGridSize(grid: NodeInfo) = (grid \ "*:tr")(0) \ "*:td" size

    def newTdElement(grid: NodeInfo, id: String, rowspan: Option[Int] = None): NodeInfo = rowspan match {
        case Some(rowspan) =>
            <xhtml:td xmlns:xhtml="http://www.w3.org/1999/xhtml" id={id} rowspan={rowspan.toString}/>
        case _ =>
            <xhtml:td xmlns:xhtml="http://www.w3.org/1999/xhtml" id={id}/>
    }

    // Insert a row below
    def insertRowBelow(tr: NodeInfo): NodeInfo = {

        val grid = getContainingGridOrRepeat(tr)
        val rowCells = getRowCells(tr)
        val newCellCount = rowCells count (! _.missing)

        // Increment rowspans if needed
        rowCells foreach { cell =>
            if (cell.rowspan > 1)
                cell.originalRowspan += 1
        }

        val result = insert(into = grid, after = tr, origin = newRow(grid, newCellCount)).head
        debugDumpDocument("insert row below", grid)
        result
    }

    // Insert a row above
    def insertRowAbove(tr: NodeInfo): NodeInfo =
        tr precedingSibling "*:tr" headOption match {
            case Some(prevRow) =>
                // Do as if this was an insert below the previous row
                // This makes things simpler as we can reuse insertRowBelow, but maybe another logic could make sense too
                insertRowBelow(prevRow)
            case None =>
                // Insert as first row of the table
                val grid = getContainingGridOrRepeat(tr)
                val result = insert(into = grid, before = tr, origin = newRow(grid, getGridSize(grid))).head
                debugDumpDocument("insert row above", grid)
                result
        }

    private def newRow(grid: NodeInfo, size: Int): NodeInfo = {
        // Get as many fresh ids as there are tds
        val ids = nextIds(grid, "td", size).toIterator

        <xhtml:tr xmlns:xhtml="http://www.w3.org/1999/xhtml">{
            (1 to size) map (_ => <xhtml:td id={tdId("td-" + ids.next())}/>)
        }</xhtml:tr>
    }

    // Delete a row and contained controls
    def deleteRow(tr: NodeInfo) {

        val allRowCells  = getAllRowCells(getContainingGridOrRepeat(tr))

        val posy = tr precedingSibling "*:tr" size
        val rowCells = allRowCells(posy)
//        val nextRow = tr followingSibling "*:tr" headOption
        val nextRowCells = if (allRowCells.size > posy + 1) Some(allRowCells(posy + 1)) else None

        // Decrement rowspans if needed
        rowCells.zipWithIndex foreach {
            case (cell, posx) =>
                if (cell.originalRowspan > 1) {
                    if (cell.missing) {
                        // This cell is part of a rowspan that starts in a previous row, so decrement
                        cell.originalRowspan -= 1
                    } else if (nextRowCells.isDefined) {
                        // This cell is the start of a rowspan, and we are deleting it, so add a td in the next row
                        // TODO XXX: issue: as we insert tds, we can't rely on Cell info unless it is updated => 
                    }
                }
        }

        // Delete all controls in the row
        tr \ "*:td" foreach (deleteCellContent(_))

        // Delete row and its content
        delete(tr)

        debugDumpDocument("delete row", tr)
    }

    // Delete the entire grid and contained controls
    def deleteGrid(grid: NodeInfo) = deleteContainer(grid)

    // Insert a column to the right
    def insertColRight(firstRowTd: NodeInfo) {

        val grid = getContainingGridOrRepeat(firstRowTd)
        val allRowCells = getAllRowCells(grid)
        val pos = firstRowTd precedingSibling "*:td" size

        val ids = nextIds(grid, "td", allRowCells.size).toIterator

        allRowCells foreach { cells =>
            val cell = cells(pos)

            // For now insert same rowspans as previous column, but could also insert full column as an option
            if (! cell.missing) {
                insert(into = cell.td.parent.get, after = cell.td, origin = newTdElement(grid, tdId("td-" + ids.next()), if (cell.rowspan > 1) Some(cell.rowspan) else None))
            }
        }

        debugDumpDocument("insert col right", grid)
    }

    // Insert a column to the left
    def insertColLeft(firstRowTd: NodeInfo) {

        val grid = getContainingGridOrRepeat(firstRowTd)
        val pos = firstRowTd precedingSibling "*:td" size

        if (pos > 0) {
            // Do as if this was an insert to the right of the previous column
            // This makes things simpler as we can reuse insertColRight, but maybe another logic could make sense too
            insertColRight(firstRowTd precedingSibling "*:td" head)
        } else {
            // First column: just insert plain tds as the first row
            val trs = grid \ "*:tr"
            val ids = nextIds(grid, "td", trs.size).toIterator

            trs foreach { tr =>
                insert(into = tr, origin = newTdElement(grid, tdId("td-" + ids.next())))
            }

            debugDumpDocument("insert col left", grid)
        }
    }

    // Insert a column and contained controls
    def deleteCol(firstRowTd: NodeInfo) {

        val grid = getContainingGridOrRepeat(firstRowTd)
        val allRowCells = getAllRowCells(grid)
        val pos = firstRowTd precedingSibling "*:td" size

        // Delete the concrete td at this column position in each row

        allRowCells map (_(pos)) filterNot (_.missing) foreach { cell =>
            deleteCellContent(cell.td)
            delete(cell.td)
        }

        debugDumpDocument("delete col", grid)
    }

    def controlsInCol(firstRowTd: NodeInfo) = {
        val grid = getContainingGridOrRepeat(firstRowTd)
        val allRowCells = getAllRowCells(grid)
        val pos = firstRowTd precedingSibling "*:td" size

        allRowCells map (_(pos)) filterNot (_.missing) filter (cell => hasChildren(cell.td)) size
    }

    private def selectedCellId =
        Option(asNodeInfo(model("fr-form-model").get.getVariable("selected-cell")))

    private def legacySelectedCell =
        Option(asNodeInfo(model("fr-form-model").get.getVariable("current-td")))

    // Find the currently selected grid td if any
    def findSelectedTd(doc: NodeInfo) = selectedCellId match {
        case Some(selectedCell) =>
            val tdId = selectedCell.stringValue
            findBodyElement(doc) \\ "*:grid" \\ "*:td" filter (_ \@ "id" === tdId) headOption
        case _ => // legacy FB
            legacySelectedCell
    }

    // Make the given grid td selected
    def selectTd(newTd: NodeInfo) = selectedCellId match {
        case Some(selectedCell) =>
            setvalue(selectedCell, newTd \@ "id" stringValue)
        case _ => // legacy FB
    }

    // Try to ensure that there is an empty td after the current location, inserting a new row if possible
    def ensureEmptyTd(doc: NodeInfo): Option[NodeInfo] = {

        findSelectedTd(doc) flatMap { currentTd =>

            if (currentTd \ * nonEmpty) {
                // There is an element in the current td, figure out what to do

                currentTd followingSibling "*:td" match {
                    case Seq(followingTd, _*) if followingTd \ * isEmpty  =>
                        // Next td exists is empty: move to that one
                        selectTd(followingTd)
                        Some(followingTd)   
                    case Seq(followingTd, _*) =>
                        // Next td exists but is not empty: NOP for now
                        None
                    case _ =>
                        // We are the last cell of the row
                        val nextTr = currentTd.getParent followingSibling "*:tr" take 1
                        val nextTrFirstTd = nextTr \ "*:td" take 1

                        val newTd =
                            if (nextTr.isEmpty || (nextTrFirstTd \ *).isEmpty)
                                // The first cell of the next row is occupied, or there is no next row: insert new row
                                GridOps.insertRowBelow(currentTd.getParent) \ "*:td" head
                            else
                                // There is a next row, and its first cell is empty, move to that one
                                nextTrFirstTd.head

                        selectTd(newTd)
                        Some(newTd)
                }
            } else
                Some(currentTd)
        }
    }

    def getMin(doc: NodeInfo, gridName: String) =
        findControlById(doc, gridId(gridName)) flatMap (grid => attValueOption(grid \@ "min")) map (_.toInt) getOrElse 0

    def getMax(doc: NodeInfo, gridName: String) =
        findControlById(doc, gridId(gridName)) flatMap (grid => attValueOption(grid \@ "max")) map (_.toInt)

    // XForms callers: get the grid's max attribute or null (the empty sequence)
    def getMaxOrEmpty(doc: NodeInfo, gridName: String) = getMax(doc, gridName) map (_.toString) orNull

    def setMinMax(doc: NodeInfo, gridName: String, min: Int, max: Int) = {

        // A missing or invalid value is taken as the default value: 0 for min, unbounded for max. In both cases, we
        // don't set the attribute value. This means that in the end we only set positive integer values.
        def set(name: String, value: Int) =
            findControlById(doc, gridId(gridName)) foreach { control =>
                if (value > 0)
                    ensureAttribute(control, name, value.toString)
                else
                    delete(control \@ name)
            }

        set("min", min)
        set("max", max)
    }

    // Find template holder
    def findTemplateHolder(descendantOrSelf: NodeInfo, controlName: String) =
        for {
            grid <- findContainingRepeat(descendantOrSelf, true)
            gridName <- getControlNameOption(grid)
            root <- templateRoot(descendantOrSelf, gridName)
            holder <- root descendantOrSelf * filter (name(_) == controlName) headOption
        } yield
            holder

    // Rename a bind
    def renameTemplate(doc: NodeInfo, oldName: String, newName: String) =
        templateRoot(doc, oldName) flatMap(_.parent) foreach
            (template => ensureAttribute(template, "id", templateId(newName)))

    // Get the x/y position of a td given Cell information
    private def getTdPosition(td: NodeInfo, cells: Seq[Seq[Cell]]) = {

        // Search rows first, then cols
        // Another solution would be to store the position directly into Cell

        val posy = td.parent.get precedingSibling "*:tr" size
        val posx = cells(posy) indexWhere (_.td isSameNodeInfo td)

        (posx, posy)
    }

    // Whether there will be controls to delete if the cell is expanded
    def expandCellTouchesControl(td: NodeInfo): Boolean = {
        val allRowCells = getAllRowCells(getContainingGridOrRepeat(td))
        val (posx, posy) = getTdPosition(td, allRowCells)

        val cell = allRowCells(posy)(posx)

        hasChildren(allRowCells(posy + cell.rowspan)(posx).td)
    }

    // Vertically expand the given cell
    def expandCell(td: NodeInfo) {
        val allRowCells  = getAllRowCells(getContainingGridOrRepeat(td))
        val (posx, posy) = getTdPosition(td, allRowCells)

        val cell = allRowCells(posy)(posx)
        val cellBelow = allRowCells(posy + cell.rowspan)(posx)

        // Increment rowspan
        cell.originalRowspan += cellBelow.originalRowspan

        // Delete cell below
        delete(cellBelow.td)
    }

    // Vertically shrink the given cell
    def shrinkCell(td: NodeInfo) {

        val grid = getContainingGridOrRepeat(td)
        val allRowCells  = getAllRowCells(grid)

        val (posx, posy) = getTdPosition(td, allRowCells)

        val cell = allRowCells(posy)(posx)

        // Decrement rowspan attribute
        cell.originalRowspan -= 1

        // Insert new td
        val posyToInsertInto = posy + cell.rowspan - 1
        val rowBelow = allRowCells(posyToInsertInto)

        val trToInsertInto = grid \ "*:tr" apply posyToInsertInto
        val tdToInsertAfter = rowBelow.slice(0, posx).reverse find (! _.missing) map (_.td) toSeq
        
        insert(into = trToInsertInto, after = tdToInsertAfter, origin = newTdElement(grid, tdId("td-" + nextId(grid, "td"))))
    }

    def initializeGrids(doc: NodeInfo) {
        // 1. Annotate all the grid tds of the given document with unique ids, if they don't have them already

        // All grid tds with no existing id
        val gridTds = findBodyElement(doc) \\ "*:grid" \\ "*:td" filterNot (td => exists(td \@ "id"))

        // Get as many fresh ids as there are tds
        val ids = nextIds(doc, "td", gridTds.size).toIterator

        // Add the missing ids
        gridTds foreach (ensureAttribute(_, "id", tdId("td-" + ids.next())))

        // 2. Select the first td if any
        findBodyElement(doc) \\ "*:grid" \\ "*:td" take 1 foreach (selectTd(_))
    }

    def moveRowUp(td: NodeInfo) {

    }

    def moveRowDown(td: NodeInfo) {

    }

    def moveColLeft(td: NodeInfo) {

    }

    def moveColRight(td: NodeInfo) {

    }
}