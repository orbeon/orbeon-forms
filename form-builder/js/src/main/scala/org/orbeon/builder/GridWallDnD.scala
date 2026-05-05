/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.builder

import autowire.*
import org.orbeon.builder.HtmlElementCell.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.{Direction, Orientation}
import org.orbeon.facades.{Dragula, DragulaOptions}
import org.orbeon.oxf.fr.{Cell, GridModel, WallPosition}
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*

import scala.collection.mutable
import scala.scalajs.js
import scala.util.chaining.*


// Allows form authors to move the grid cell walls to resize cells
object GridWallDnD {

  private val DatasetIndexKey = "index"

  private implicit class CellBlockOps(private val cell: Block) extends AnyVal {
    def x          : Int          = cell.el.dataset.get("frX").get.toInt
    def y          : Int          = cell.el.dataset.get("frY").get.toInt
    def w          : Int          = cell.el.dataset.get("frW").map(_.toInt).getOrElse(1)
    def h          : Int          = cell.el.dataset.get("frH").map(_.toInt).getOrElse(1)
    def underlying : html.Element = cell.el
  }

  locally {

    val WallContainerClass  = "fb-grid-dnd-wall-container"
    val WallVerticalClass   = "fb-grid-dnd-wall-vertical"
    val WallHorizontalClass = "fb-grid-dnd-wall-horizontal"
    val WallHandleClass     = "fb-grid-dnd-wall-handle"
    val WallShadowClass     = "fb-grid-dnd-wall-shadow"
    val WallDropClass       = "fb-grid-dnd-wall-drop"
    val WallVeilClass       = "fb-grid-dnd-wall-veil"

    case class StartCell(block: Block, startSide: Direction, startIndex: Int)

    var currentCellOpt : Option[Block]     = None
    var startCellOpt   : Option[StartCell] = None

    // Keep track of the current cell and show draggable walls
    locally {
      Position.currentContainerChanged(
        containerCache = BlockCache.cellCache,
        wasCurrent     = (_   : Block) => {
          currentCellOpt = None
          if (! DndShadow.isDragging)
            DndWall.hideAll()
        },
        becomesCurrent = (cell: Block) => {
          currentCellOpt = Some(cell)
          val isViewMode = FormRunnerUtils.isViewMode(cell.el)
          if (! DndShadow.isDragging && ! isViewMode)
            DndSides.show(cell)
        }
      )
    }

    object DndSides {

      def show(cell: Block): Unit = {
        val gridBody  = HtmlElementCellOps.gridForCell(cell.underlying)
        val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridBody, simplify = false, transpose = false)
        val walls     = Cell.movableWalls(gridModel, cell.underlying)
        walls.foreach { case (direction, wallPosition) =>
          val wallOrientation = DndWall.wallOrientation(direction)
          val index = direction match {
            case Direction.Left  => cell.x          - 1
            case Direction.Right => cell.x + cell.w - 1
            case Direction.Up    => cell.y          - 1
            case Direction.Down  => cell.y + cell.h - 1
          }
          val side = wallPosition match {
            case WallPosition.Side   => Some(direction)
            case WallPosition.Middle => None
          }
          DndWall.show(gridModel, index, wallOrientation, side)
        }
      }
    }

    // Blocks signaling where cell borders can be dragged from and to
    object DndWall {

      private val walls                            = new mutable.ListBuffer[html.Element]
      private var dropTarget: Option[html.Element] = None
      private var mouseOnWall                      = false

      def show[Underlying](
        gridModel   : GridModel[Underlying],
        index       : Int,
        orientation : Orientation,
        side        : Option[Direction]
      ): Unit = {
        currentCellOpt.foreach { currentCell =>

          val orientationClass =
            orientation match {
              case Orientation.Vertical   => WallVerticalClass
              case Orientation.Horizontal => WallHorizontalClass
            }

          val frGridBody = currentCell.el.ancestorOrSelfElem(BlockCache.GridBodySelector, includeSelf = false).nextOption().get
          val frGrid     = frGridBody.parentElement

          val dndHandleElem =
            dom.document.createElementT("div")
              .tap(_.classList.add(WallHandleClass))

          val dndContainerElem =
            dom.document.createElementT("div")
              .tap(_.classList.add(WallContainerClass))
              .tap(_.classList.add(orientationClass))
              .tap(_.dataset += DatasetIndexKey -> index.toString)
              .tap(_.appendChild(dndHandleElem))

          frGrid.append(dndContainerElem)
          walls.append(dndContainerElem)

          val sideSlide = {
            val wallThickness =
              orientation match {
                case Orientation.Vertical   => dndContainerElem.contentWidthOrZero
                case Orientation.Horizontal => dndContainerElem.contentHeightOrZero
              }
            side match {
              case Some(Direction.Left  | Direction.Up  ) => 0
              case Some(Direction.Right | Direction.Down) => wallThickness
              case None                                   => wallThickness / 2
            }
          }

          def trackOffset(orientation: Orientation): Double = {
            val gridSlide = orientation match {
              case Orientation.Horizontal => frGridBody.getOffset.top
              case Orientation.Vertical   => frGridBody.getOffset.left
            }
            val tracksSlide = Position.tracksWidth(frGridBody, orientation).take(index).sum
            val gapSlide = {
              val rowGapSize  = DomSupport.parseDoubleIgnoreTail(dom.window.getComputedStyle(frGridBody).getPropertyValue("row-gap")).getOrElse(0d)
              index * rowGapSize
            }
            gridSlide + tracksSlide - sideSlide + gapSlide
          }

          dndContainerElem.setOffset(DomSupport.Offset(
            left = orientation match {
              case Orientation.Vertical =>
                trackOffset(orientation)
              case Orientation.Horizontal =>
                currentCell.left
            },
            top  = orientation match {
              case Orientation.Vertical =>
                currentCell.top - Position.scrollTop
              case Orientation.Horizontal =>
                trackOffset(orientation)
            }
          ))
          orientation match {
            case Orientation.Vertical   => dndContainerElem.setHeight(currentCell.height)
            case Orientation.Horizontal => dndContainerElem.setWidth (currentCell.width)
          }
          dndContainerElem.addEventListener("mouseenter", (_: dom.Event) => {
            mouseOnWall = true
            ControlEditor.mask()
          })
          dndContainerElem.addEventListener("mouseleave", (_: dom.Event) => {
            mouseOnWall = false
            if (! DndShadow.isDragging)
              ControlEditor.unmask()
          })
        }
      }

      def hideAll(): Unit = {
        walls.foreach(_.remove())
        walls.clear()
        mouseOnWall = false
        ControlEditor.unmask()
      }

      def wallOrientation(direction: Direction): Orientation =
        direction match {
          case Direction.Left | Direction.Right => Orientation.Vertical
          case Direction.Up   | Direction.Down  => Orientation.Horizontal
        }

      def markClosestAsDropTarget(pointerPosition: Double, position: html.Element => Double): Unit = {

        val closestContainer: Option[html.Element] = {
          def distance(container: html.Element): Double = Math.abs(position(container) - pointerPosition)
          case class BestContainer(container: html.Element, distance: Double)
          walls.foldLeft(None: Option[BestContainer]) { (bestSoFarOpt, container) =>
            val currentDistance = distance(container)
            val keepBestSoFar = bestSoFarOpt.exists(_.distance <= currentDistance)
            if (keepBestSoFar) bestSoFarOpt else Some(BestContainer(container, currentDistance))
          }.map(_.container)
        }

        dropTarget.foreach(_.classList.remove(WallDropClass))
        dropTarget = closestContainer
        dropTarget.foreach(_.classList.add(WallDropClass))
      }

      def getDropTarget: Option[html.Element] = dropTarget
    }

    // Block showing during the dragging operation
    object DndShadow {

      var dndVeilOpt   : Option[html.Element] = None
      var dndShadowOpt : Option[html.Element] = None

      def isDragging: Boolean = dndShadowOpt.isDefined

      def show(containerElem: html.Element): Unit = {

        val frGridElem = containerElem.parentElement

        // Create veil
        val dndVeilElem =
          dom.document.createElementT("div")
            .tap(_.classList.add(WallVeilClass))

        dndVeilOpt = Some(dndVeilElem)
        frGridElem.appendChild(dndVeilElem)

        dndVeilElem.setOffset(frGridElem.getOffset)
        dndVeilElem.setWidth(frGridElem.contentWidthOrZero)
        dndVeilElem.setHeight(frGridElem.contentHeightOrZero)

        // Create shadow element, position and size the shadow over source container
        val dndShadowElem =
          dom.document.createElementT("div")
            .tap(_.classList.add(WallShadowClass))

        dndShadowOpt = Some(dndShadowElem)
        frGridElem.appendChild(dndShadowElem)
        dndShadowElem.setOffset(containerElem.getOffset)
        dndShadowElem.setWidth(containerElem.contentWidthOrZero)
        dndShadowElem.setHeight(containerElem.contentHeightOrZero)
      }

      def hide(): Unit = {
        dndVeilOpt.foreach(_.remove())
        dndVeilOpt = None
        dndShadowOpt.foreach(_.remove())
        dndShadowOpt = None
      }

      Position.onUnderPointerChange(() =>
        dndShadowOpt.foreach { dndShadowElem =>
          startCellOpt.foreach { case StartCell(_, startSide, _) =>
            DndWall.wallOrientation(startSide) match {
              case Orientation.Vertical =>
                val newLeft = Position.pointerPos.left - (dndShadowElem.contentWidthOrZero / 2)
                val newShadowOffset = dndShadowElem.getOffset.copy(left = newLeft)
                dndShadowElem.setOffset(newShadowOffset)
                DndWall.markClosestAsDropTarget(newLeft, _.getOffset.left)
              case Orientation.Horizontal =>
                val newTop = Position.pointerPos.top - (dndShadowElem.contentHeightOrZero / 2)
                val newShadowOffset = dndShadowElem.getOffset.copy(top = newTop)
                dndShadowElem.setOffset(newShadowOffset)
                DndWall.markClosestAsDropTarget(newTop, _.getOffset.top)
            }
          }
        }
      )
    }

    // Handle D&D
    locally {
      val drake = Dragula(
        js.Array(),
        new DragulaOptions {
          override def isContainer(el: html.Element): js.UndefOr[Boolean] =
            el.hasClass(WallContainerClass)

          override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): js.UndefOr[Boolean] =
            handle.hasClass(WallHandleClass)

          override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): js.UndefOr[Boolean] =
            target.hasClass(WallContainerClass)
        }
      )

      drake.onDrag { (_, source) =>
        currentCellOpt.foreach { currentCell =>
          val startSide = {
            val wallIndex = source.dataset.get(DatasetIndexKey).get.toInt
            val isVertical = source.hasClass(WallVerticalClass)
            if (isVertical) if (wallIndex == currentCell.x - 1) Direction.Left else Direction.Right
            else            if (wallIndex == currentCell.y - 1) Direction.Up   else Direction.Down
          }
          Cell.cellWallPossibleDropTargets(currentCell.underlying, startSide).foreach { possibleTargets =>
            startCellOpt = Some(StartCell(currentCell, startSide, possibleTargets.statusQuo))
            DndShadow.show(source)
            DndWall.hideAll()
            ControlEditor.mask()
            val gridBody  = HtmlElementCellOps.gridForCell(currentCell.underlying)
            val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridBody, simplify = false, transpose = false)
            val wallOrientation = DndWall.wallOrientation(startSide)
            possibleTargets.all.foreach(DndWall.show(gridModel, _, wallOrientation, None))
          }
        }
      }

      drake.onDragend { _ =>
        DndWall.getDropTarget.foreach { dropTarget =>
          startCellOpt.foreach { case StartCell(block, startSide, startIndex) =>
            val targetIndex  = dropTarget.dataset.get(DatasetIndexKey).get.toInt
            if (targetIndex != startIndex)
              RpcClient[FormBuilderRpcApi].moveWall(block.el.id, startSide, targetIndex).call()
          }
        }
        startCellOpt = None
        DndShadow.hide()
        DndWall.hideAll()
        currentCellOpt.foreach(DndSides.show)
      }
    }
  }
}
