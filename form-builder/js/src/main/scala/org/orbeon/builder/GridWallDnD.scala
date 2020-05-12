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

import autowire._
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.{Direction, Orientation}
import org.orbeon.facades.{Dragula, DragulaOptions}
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.jquery.Offset
import org.orbeon.oxf.fr.{Cell, GridModel, WallPosition}
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.scalajs.jquery.JQuery

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

// Allows form authors to move the grid cell walls to resize cells
object GridWallDnD {

  private implicit class CellBlockOps(private val cell: Block) extends AnyVal {
    def x          : Int          = cell.el.attr("data-fr-x").get.toInt
    def y          : Int          = cell.el.attr("data-fr-y").get.toInt
    def w          : Int          = cell.el.attr("data-fr-w").toOption.map(_.toInt).getOrElse(1)
    def h          : Int          = cell.el.attr("data-fr-h").toOption.map(_.toInt).getOrElse(1)
    def underlying : html.Element = cell.el.get(0).asInstanceOf[html.Element]
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
          if (! DndShadow.isDragging)
            DndSides.show(cell)
        }
      )
    }

    object DndSides {
       def show(cell: Block): Unit = {

         val gridModel = Cell.analyze12ColumnGridAndFillHoles(HtmlElementCellOps.gridForCell(cell.underlying), simplify = false)

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

      private val walls                      = new mutable.ListBuffer[JQuery]
      private var dropTarget: Option[JQuery] = None
      private var mouseOnWall                = false

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
          val frGridBody       = currentCell.el.parents(BlockCache.GridBodySelector).first()
          val frGrid           = frGridBody.parent
          val dndContainer     = $(s"""<div class="$WallContainerClass $orientationClass" data-index="$index">""")
          val dndHandle        = $(s"""<div class="$WallHandleClass">""")
          dndContainer.append(dndHandle)
          frGrid.append(dndContainer)
          walls.append(dndContainer)
          val sideSlide = {
            val wallThickness =
              orientation match {
                case Orientation.Vertical   => dndContainer.width()
                case Orientation.Horizontal => dndContainer.height()
              }
            side match {
              case Some(Direction.Left  | Direction.Up  ) => 0
              case Some(Direction.Right | Direction.Down) => wallThickness
              case None                                   => wallThickness / 2
            }
          }

          def trackOffset(orientation: Orientation) = {
            val gridSlide = orientation match {
              case Orientation.Horizontal => Offset(frGridBody).top
              case Orientation.Vertical   => Offset(frGridBody).left
            }
            val tracksSlide = Position.tracksWidth(frGridBody, orientation).take(index).sum
            val gapSlide = {
              val rowGapSize  = frGridBody.css("grid-row-gap").split("px")(0).toInt
              index * rowGapSize
            }
            gridSlide + tracksSlide - sideSlide + gapSlide
          }

          Offset.offset(dndContainer, Offset(
            left = orientation match {
              case Orientation.Vertical =>
                trackOffset(orientation)
              case Orientation.Horizontal =>
                currentCell.left
            },
            top  = orientation match {
              case Orientation.Vertical =>
                currentCell.top - Position.scrollTop()
              case Orientation.Horizontal =>
                trackOffset(orientation)
            }
          ))
          orientation match {
            case Orientation.Vertical   => dndContainer.height(currentCell.height)
            case Orientation.Horizontal => dndContainer.width (currentCell.width)
          }
          dndContainer.on("mouseenter", () => {
            mouseOnWall = true
            ControlEditor.mask()
          })
          dndContainer.on("mouseleave", () => {
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

      def markClosestAsDropTarget(pointerPosition: Double, position: JQuery => Double): Unit = {

        val closestContainer: Option[JQuery] = {
          def distance(container: JQuery) = Math.abs(position(container) - pointerPosition)
          case class BestContainer(container: JQuery, distance: Double)
          walls.foldLeft(None: Option[BestContainer]) { (bestSoFarOpt, container) =>
            val currentDistance = distance(container)
            val keepBestSoFar = bestSoFarOpt.exists(_.distance <= currentDistance)
            if (keepBestSoFar) bestSoFarOpt else Some(BestContainer(container, currentDistance))
          }.map(_.container)
        }

        dropTarget.foreach(_.removeClass(WallDropClass))
        dropTarget = closestContainer
        dropTarget.foreach(_.addClass(WallDropClass))
      }

      def getDropTarget: Option[JQuery] = dropTarget
    }

    // Block showing during the dragging operation
    object DndShadow {

      var dndVeilOpt   : Option[JQuery] = None
      var dndShadowOpt : Option[JQuery] = None

      def isDragging: Boolean = dndShadowOpt.isDefined

      def show(containerEl: html.Element): Unit = {

        val container = $(containerEl)
        val frGrid = container.parent()

        // Create veil
        val dndVeil = $(s"""<div class="$WallVeilClass">""")
        dndVeilOpt = Some(dndVeil)
        frGrid.append(dndVeil)
        Offset.offset(dndVeil, Offset(frGrid))
        dndVeil.width(frGrid.width())
        dndVeil.height(frGrid.height())

        // Create shadow element, position and size the shadow over source container
        val dndShadow = $(s"""<div class="$WallShadowClass">""")
        dndShadowOpt = Some(dndShadow)
        frGrid.append(dndShadow)
        Offset.offset(dndShadow, Offset(container))
        dndShadow.width(container.width())
        dndShadow.height(container.height())
      }

      def hide(): Unit = {
        dndVeilOpt.foreach(_.detach())
        dndVeilOpt = None
        dndShadowOpt.foreach(_.detach())
        dndShadowOpt = None
      }

      Position.onUnderPointerChange {
        dndShadowOpt.foreach { dndShadow =>
          startCellOpt.foreach { case StartCell(_, startSide, _) =>
            DndWall.wallOrientation(startSide) match {
              case Orientation.Vertical =>
                val newLeft = Position.pointerPos.left - (dndShadow.width() / 2)
                val newShadowOffset = Offset(dndShadow).copy(left = newLeft)
                Offset.offset(dndShadow, newShadowOffset)
                DndWall.markClosestAsDropTarget(newLeft, (container: JQuery) => Offset(container).left)
              case Orientation.Horizontal =>
                val newTop = Position.pointerPos.top - (dndShadow.height() / 2)
                val newShadowOffset = Offset(dndShadow).copy(top = newTop)
                Offset.offset(dndShadow, newShadowOffset)
                DndWall.markClosestAsDropTarget(newTop, (container: JQuery) => Offset(container).top)
            }
          }
        }
      }
    }

    // Handle D&D
    locally {
      val drake = Dragula(
        js.Array(),
        new DragulaOptions {
          override def isContainer(el: html.Element) =
            el.classList.contains(WallContainerClass)

          override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) =
            handle.classList.contains(WallHandleClass)

          override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) =
            target.classList.contains(WallContainerClass)
        }
      )

      drake.onDrag { (_, source) =>
        currentCellOpt.foreach { currentCell =>
          val startSide = {
            val wallIndex = source.getAttribute("data-index").toInt
            val isVertical = $(source).hasClass(WallVerticalClass)
            if (isVertical) if (wallIndex == currentCell.x - 1) Direction.Left else Direction.Right
            else            if (wallIndex == currentCell.y - 1) Direction.Up   else Direction.Down
          }
          Cell.cellWallPossibleDropTargets(currentCell.underlying, startSide).foreach { possibleTargets =>
            startCellOpt = Some(StartCell(currentCell, startSide, possibleTargets.statusQuo))
            DndShadow.show(source)
            DndWall.hideAll()
            ControlEditor.mask()
            val gridModel = Cell.analyze12ColumnGridAndFillHoles(HtmlElementCellOps.gridForCell(currentCell.underlying), simplify = false)
            val wallOrientation = DndWall.wallOrientation(startSide)
            possibleTargets.all.foreach(DndWall.show(gridModel, _, wallOrientation, None))
          }
        }
      }

      drake.onDragend { el =>
        DndWall.getDropTarget.foreach { dropTarget =>
          startCellOpt foreach { case StartCell(block, startSide, startIndex) =>
            val targetIndex  = dropTarget.attr("data-index").get.toInt
            if (targetIndex != startIndex) {
              val cellId       = block.el.attr("id").get
              RpcClient[FormBuilderRpcApi].moveWall(cellId, startSide, targetIndex).call()
            }
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
