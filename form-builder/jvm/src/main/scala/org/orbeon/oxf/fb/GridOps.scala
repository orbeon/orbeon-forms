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

import org.orbeon.datatypes.{AboveBelow, Direction}
import org.orbeon.oxf.fb.UndoAction.{DeleteRow, InsertRow}
import org.orbeon.oxf.fr.Cell.{findOriginCell, nonOverflowingNeighbors}
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.NodeInfoCell._
import org.orbeon.oxf.fr.{Cell, FormRunner}
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

  def annotateGridsAndCells(startElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit = {

    // 1. Annotate all the grid and grid cells of the given document with unique ids,
    // if they don't have them already. We do this so that ids are stable as we move
    // things around, otherwise if the XForms document is recreated new automatic ids
    // are generated for objects without id.

    val grids = startElem descendantOrSelf GridTest

    // Annotate cells
    locally {
      val toAnnotate = grids descendant CellTest filterNot (_.hasId)
      val ids        = nextTmpIds(count = toAnnotate.size).toIterator
      toAnnotate foreach (ensureAttribute(_, "id", ids.next()))
    }

    // Annotate grids
    locally {
      val toAnnotate = grids filterNot (_.hasId)
      val ids        = nextIds("grid", toAnnotate.size).toIterator
      toAnnotate foreach (ensureAttribute(_, "id", ids.next()))
    }
  }

  // Get the first enclosing repeated grid or legacy repeat
  def getContainingGrid(descendantOrSelf: NodeInfo, includeSelf: Boolean = false): NodeInfo =
    findAncestorContainersLeafToRoot(descendantOrSelf, includeSelf) filter IsGrid head

  def rowInsertBelow(gridElem: NodeInfo, rowPos: Int)(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[UndoAction]) =
    withDebugGridOperation("insert row below") {

      val allCells       = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false)
      val adjustedRowPos = rowPos % allCells.size // modulo as index sent by client can be in repeated grid

      // Increment height of origin cells that don't end at the current row
      collectDistinctOriginCellsSpanningAfter(allCells, adjustedRowPos) foreach { cell ⇒
        NodeInfoCellOps.updateH(cell.td, cell.h + 1)
      }

      // Increment the position of all cells on subsequent rows
      allCells.view.slice(adjustedRowPos + 1, allCells.size).flatten foreach {
        case Cell(Some(u), None, _, y, _, _) ⇒ NodeInfoCellOps.updateY(u, y + 1)
        case _ ⇒
      }

      // Insert a cell in the new row
      // NOTE: This is not very efficient. We should be able to just iterate back in the grid.
      val precedingCellOpt = allCells.slice(0, adjustedRowPos + 1).flatten.reverse collectFirst {
        case Cell(Some(u), None, _, _, _, _) ⇒ u
      }

      // Cells that end at the current row
      val distinctCellsEndingAtCurrentRow =
        allCells(adjustedRowPos) collect {
          case c @ Cell(Some(u), _, _, _, 1, _) ⇒ c
        } keepDistinctBy (_.u)

      val idsIt =
        nextTmpIds(count = distinctCellsEndingAtCurrentRow.size).iterator

      val newCells =
        distinctCellsEndingAtCurrentRow map { cell ⇒
          <fr:c
            xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
            id={idsIt.next()}
            x={cell.x.toString}
            y={adjustedRowPos + 2 toString}
            w={cell.w.toString}/>: NodeInfo
        }

      val insertedNode =
        insert(into = Nil, after = precedingCellOpt.toList, origin = newCells).headOption.orNull
      // `.orNull` is bad, but `insert()` is not always able to return the inserted item at this time

      (insertedNode, Some(InsertRow(gridElem.id, adjustedRowPos, AboveBelow.Below)))
    }

  def rowInsertAbove(gridElem: NodeInfo, rowPos: Int)(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[UndoAction]) = {

    val allCells       = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false)
    val adjustedRowPos = rowPos % allCells.size // modulo as index sent by client can be in repeated grid

    if (adjustedRowPos >= 1)
      rowInsertBelow(gridElem, rowPos - 1)
    else
      withDebugGridOperation("insert first row above") {

        // Move all cells down one notch
        allCells.iterator.flatten foreach {
          case Cell(Some(u), None, _, y, _, _) ⇒ NodeInfoCellOps.updateY(u, y + 1)
          case _ ⇒
        }

        // Insert new first row
        val cellsStartingOnFirstRow =
          allCells.head collect {
            case c @ Cell(Some(u), None, _, _, _, _) ⇒ c
          }

        val idsIt =
          nextTmpIds(count = cellsStartingOnFirstRow.size).iterator

        val newCells =
          cellsStartingOnFirstRow map { cell ⇒
            <fr:c
              xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
              id={idsIt.next()}
              x={cell.x.toString}
              y="1"
              w={cell.w.toString}/>: NodeInfo
          }

        val firstCellOpt = allCells.iterator.flatten.nextOption() flatMap (_.u)

        val insertedNode =
          insert(into = List(gridElem), before = firstCellOpt.toList, origin = newCells).headOption.orNull
          // `.orNull` is bad, but `insert()` is not always able to return the inserted item at this time

        (insertedNode, Some(InsertRow(gridElem.id, adjustedRowPos, AboveBelow.Above)))
      }
  }

  private def collectDistinctOriginCellsSpanningAfter[Underlying](cells: List[List[Cell[Underlying]]], rowPos: Int): List[Cell[Underlying]] =
    cells(rowPos) collect {
      case c @ Cell(Some(u), _, _, _, h, _) if h > 1 ⇒ c
    } keepDistinctBy (_.u)

  private def collectDistinctOriginCellsSpanningBefore[Underlying](cells: List[List[Cell[Underlying]]], rowPos: Int): List[Underlying] =
    cells(rowPos) collect {
      case Cell(Some(u), Some(origin), _, y, _, _) if origin.y < y ⇒ u
    } distinct

  def canDeleteRow(gridId: String, rowPos: Int)(implicit ctx: FormBuilderDocContext): Boolean = {

    require(rowPos >= 0)

    val gridElem       = containerById(gridId)
    val allCells       = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false)
    val adjustedRowPos = rowPos % allCells.size

    allCells.lengthCompare(1) > 0 && adjustedRowPos < allCells.size
  }

  def rowDelete(gridId: String, rowPos: Int)(implicit ctx: FormBuilderDocContext): Option[UndoAction] = {

    require(rowPos >= 0)

    val gridElem       = containerById(gridId)
    val allCells       = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false)
    val adjustedRowPos = rowPos % allCells.size

    (allCells.lengthCompare(1) > 0 && adjustedRowPos < allCells.size) option
      withDebugGridOperation("delete row") {

        val undo = DeleteRow(gridId, ToolboxOps.controlOrContainerElemToXcv(gridElem), adjustedRowPos)

        // Reduce height of cells which start on a previous row
        val distinctOriginCellsSpanning = collectDistinctOriginCellsSpanningBefore(allCells, adjustedRowPos)

        distinctOriginCellsSpanning foreach (cell ⇒ NodeInfoCellOps.updateH(cell, NodeInfoCellOps.h(cell).getOrElse(1) - 1))

        // Decrement the position of all cells on subsequent rows
        allCells.view.slice(adjustedRowPos + 1, allCells.size).flatten foreach {
          case Cell(Some(u), None, _, y, _, _) ⇒ NodeInfoCellOps.updateY(u, y - 1)
          case _ ⇒
        }

        val cellsToDelete =
          allCells(adjustedRowPos) collect {
            case Cell(Some(u), None, _, _, _, _) ⇒ u
          }

        // Find the new cell to select if we are removing the currently selected cell
        val newCellToSelect = findNewCellToSelect(cellsToDelete)

        // Delete all controls in the row
        cellsToDelete foreach (deleteControlWithinCell(_))

        // Delete row and its content
        delete(cellsToDelete)

        // Update templates
        updateTemplatesCheckContainers(findAncestorRepeatNames(gridElem, includeSelf = true).to[Set])

        // Adjust selected cell if needed
        newCellToSelect foreach selectCell

        undo
      }
  }

  // Whether this is the last grid in the section
  // NOTE: Use this until we implement the new selection system allowing moving stuff around freely
  def isLastGridInSection(grid: NodeInfo): Boolean =
    childrenGrids(findAncestorContainersLeafToRoot(grid).head).lengthCompare(1) == 0

  private def selectedCellVar(implicit ctx: FormBuilderDocContext) =
    ctx.formBuilderModel.get.unsafeGetVariableAsNodeInfo("selected-cell")

  // Find the currently selected grid cell if any
  def findSelectedCell(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    findInViewTryIndex(ctx.formDefinitionRootElem, selectedCellVar.stringValue)

  // Make the given grid cell selected
  def selectCell(newCellElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    setvalue(selectedCellVar, newCellElem.id)

  // Whether a call to ensureEmptyCell() will succeed
  // For now say we'll always succeed as we'll fill gaps and insert a row as needed.
  // TODO: Remove once no longer needed.
  def willEnsureEmptyCellSucceed(implicit ctx: FormBuilderDocContext): Boolean =
    findSelectedCell.isDefined

  // Try to ensure that there is an empty cell after the current location, inserting a new row if possible
  def ensureEmptyCell()(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    findSelectedCell flatMap { currentCellNode ⇒
      if (currentCellNode.hasChildElement) {
        // There is an element in the current cell, figure out what to do

        // - start with `currentCell`
        // - find closest following available space with width `MinimumWidth`
        //   - could be size of `currentCell`, or a fixed value, or dependent on control type
        // - if found, then insert new cell at the right spot
        // - if none, then insert new row below OR at end of grid

        val gridElem     = getContainingGrid(currentCellNode)
        val cells        = Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false)
        val currentCell  = Cell.findOriginCell(cells, currentCellNode).get
        val MinimumWidth = currentCell.w

        val availableCellOpt =
          cells.iterator.flatten dropWhile (_ != currentCell) drop 1 collect {
            case c @ Cell(Some(cellNode), None, _, _, _, _) if ! cellNode.hasChildElement ⇒ c
          } nextOption()

        val newCell =
          availableCellOpt match {
            case Some(Cell(Some(cellNode), _, _, _, _, _)) ⇒ Some(cellNode)
            case _                                         ⇒ Option(rowInsertBelow(gridElem, currentCell.y - 1)._1)
          }

         newCell |!> selectCell
      } else
        Some(currentCellNode)
    }

  // @min/@max can be simple AVTs, i.e. AVTs which cover the whole attribute, e.g. "{my-min}"
  // The main reason to do this instead of making @min/@max plain XPath expressions is that @max also supports the
  // literal "none" (and "unbounded" for backward compatibility).
  // NOTE: This doesn't check that the expression is syntactically correct, in particular it doesn't check that
  // curly brackets are absent or escaped within the AVT.
  private val SimpleAVTRegex = """^\{(.+)\}$""".r

  def trimSimpleAVT(s: String): String = s match {
    case SimpleAVTRegex(v) ⇒ v.replaceAllLiterally("{{", "{").replaceAllLiterally("}}", "}")
    case v                 ⇒ v
  }

  private val NoMaximum = Set("none", "unbounded")

  // NOTE: Value can be a simple AVT
  def getNormalizedMax(doc: NodeInfo, gridName: String): Option[String] =
    findControlByName(doc, gridName) flatMap (_ attValueOpt "max") filterNot NoMaximum map trimSimpleAVT

  def getNormalizedMin(doc: NodeInfo, gridName: String): String =
    FormRunner.findControlByName(doc, gridName) flatMap (_ attValueOpt "min") map trimSimpleAVT getOrElse "0"

  // Convert a min/max value to a value suitable to be written to the @min/@max attributes.
  //
  // - blank value                → None
  // - non-positive integer value → None
  // - positive integer value     → Some(int: String)
  // - any other value            → Some("{expression}")
  //
  def minMaxForAttribute(s: String): Option[String] = s.trimAllToOpt flatMap { value ⇒
    try {
      val int = value.toInt
      int > 0 option int.toString
    } catch {
      case _: NumberFormatException ⇒
        val escaped = value.replaceAllLiterally("{", "{{").replaceAllLiterally("}", "}}")
        Some(s"{$escaped}")
    }
  }

  def merge(
    cellElem     : NodeInfo,
    direction    : Direction)(
    implicit ctx : FormBuilderDocContext
  ): Unit = {
    if (Cell.canChangeSize(cellElem).contains(direction)) {
      val cells = Cell.analyze12ColumnGridAndFillHoles(getContainingGrid(cellElem) , simplify = false)
      findOriginCell(cells, cellElem).foreach { originCell ⇒
        val neighbors = nonOverflowingNeighbors(cells, originCell, direction)
        direction match {
          case Direction.Right ⇒ NodeInfoCellOps.updateW(cellElem, originCell.w + neighbors.head.w)
          case Direction.Down  ⇒ NodeInfoCellOps.updateH(cellElem, originCell.h + neighbors.head.h)
          case _               ⇒ throw new IllegalStateException
        }
        neighbors.foreach(_.u.foreach { neighborCellElem ⇒
          deleteControlWithinCell(neighborCellElem)
          delete(neighborCellElem)
        })
      }
    }
  }

  def split(
    cellElem     : NodeInfo,
    direction    : Direction)(
    implicit ctx : FormBuilderDocContext
  ): Unit = {
    if (Cell.canChangeSize(cellElem).contains(direction)) {
      val cells = Cell.analyze12ColumnGridAndFillHoles(getContainingGrid(cellElem) , simplify = false)
      findOriginCell(cells, cellElem).foreach { originCell ⇒
        direction match {
          case Direction.Left ⇒
            val newCellW = (originCell.w + 1) / 2
            NodeInfoCellOps.updateW(cellElem, newCellW)
            insertCellAtBestPosition(cells, nextTmpId(), originCell.x + newCellW, originCell.y, originCell.w - newCellW, originCell.h)
          case Direction.Up ⇒
            val newCellH = (originCell.h + 1) / 2
            NodeInfoCellOps.updateH(cellElem, newCellH)
            insertCellAtBestPosition(cells, nextTmpId(), originCell.x, originCell.y + newCellH, originCell.w, originCell.h - newCellH)
          case _ ⇒
            throw new IllegalStateException
        }
      }
    }
  }

  def moveWall(
    cellElem     : NodeInfo,
    startSide    : Direction,
    target       : Int)(
    implicit ctx : FormBuilderDocContext
  ): Unit = {
    val cells = Cell.analyze12ColumnGridAndFillHoles(getContainingGrid(cellElem) , simplify = false)
    Cell.findOriginCell(cells, cellElem) foreach {  originCell ⇒

      def moveSingleCell(cell: Cell[NodeInfo], side: Direction, target: Int): Unit = {
        cell match {
          case Cell(Some(u), _, x, y, h, w) ⇒
            val (newX, newW) = side match {
              case Direction.Left  ⇒ (target + 1, w - (target + 1 - x))
              case Direction.Right ⇒ (x         , target + 1 - x)
            }
            if (x != newX) NodeInfoCellOps.updateX(u, newX)
            if (w != newW) NodeInfoCellOps.updateW(u, newW)
          case _ ⇒ throw new IllegalStateException
        }
      }

      val neighbors = Cell.findOriginNeighbors(originCell, startSide, cells)
      startSide match {
        case Direction.Left ⇒
          moveSingleCell(originCell, Direction.Left, target)
          neighbors.foreach(moveSingleCell(_, Direction.Right, target))
        case Direction.Right ⇒
          moveSingleCell(originCell, Direction.Right, target)
          neighbors.foreach(moveSingleCell(_, Direction.Left, target))
      }
    }
  }

  def deleteGridByIdIfPossible(gridId: String)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    findContainerById(gridId) flatMap
      (_ ⇒ deleteContainerById(canDeleteGrid, gridId))

  def canDeleteGrid(gridElem: NodeInfo): Boolean =
    canDeleteContainer(gridElem)

  def canDeleteRow(gridElem: NodeInfo): Boolean =
    Cell.analyze12ColumnGridAndFillHoles(gridElem, simplify = false).lengthCompare(1) > 0

  // Find the new td to select if we are removing the currently selected td
  def findNewCellToSelect(cellsToDelete: Seq[NodeInfo])(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    findSelectedCell match {
      case Some(selectedCell) if cellsToDelete contains selectedCell ⇒

        def findCells(find: Test ⇒ Seq[NodeInfo], selectedCell: NodeInfo) =
          find(CellTest)                                                            intersect
          (findAncestorContainersLeafToRoot(selectedCell).last descendant CellTest) filterNot
          (cellsToDelete contains _)                                                headOption

        // Prefer trying following before preceding, as things move up and left when deleting
        // NOTE: Could improve this by favoring things "at same level", e.g. stay in grid if possible, then
        // stay in section, etc.
        findCells(selectedCell following _, selectedCell) orElse
          findCells(selectedCell preceding  _, selectedCell)
      case _ ⇒
        None
    }

  private def insertCellAtBestPosition(
    cells : List[List[Cell[NodeInfo]]],
    id    : String,
    x     : Int,
    y     : Int,
    w     : Int,
    h     : Int
  ): Option[NodeInfo] = {

    val originCells = Cell.originCells(cells)

    require(originCells.nonEmpty)

    def lt(x1: Int, y1: Int, x2: Int, y2: Int) =
      y1 < y2 || (y1 == y2 && x1 < x2)

    val newCell: NodeInfo =
      <fr:c
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        id={id}
        x={x.toString}
        y={y.toString}
        w={w.toString}
        h={h.toString}/>

    val firstCell = originCells.head
    val lastCell  = originCells.last

    if (lt(x, y, firstCell.x, firstCell.y)) {
      // Insert before the first cell
      insert(into = Nil, before = firstCell.u.toList, origin = newCell).headOption
    } else if (lt(lastCell.x, lastCell.y, x, y)) {
      // Insert after the last cell
      insert(into = Nil, after = lastCell.u.toList, origin = newCell).headOption
    } else {
      // Insert between two cells

      // If we get here and the size is 1, it means that `firstCell.x == x && firstCell.y == y`, which
      // is not allowed as there cannot be two cells originating at the same coordinate.
      require(originCells.lengthCompare(1) > 0)

      val afterCellOpt =
        originCells.sliding(2) collectFirst {
          case Seq(c1, c2)  if lt(c1.x, c1.y, x, y) && lt(x, y, c2.x, c2.y) ⇒ c1
        }

      insert(into = Nil, after = (afterCellOpt flatMap (_.u)).toList, origin = newCell).headOption
    }
  }
}