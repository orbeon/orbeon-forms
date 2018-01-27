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
import org.orbeon.builder.BlockCache.Block
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.Direction
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.jquery.Offset
import org.orbeon.oxf.fr.Cell
import org.orbeon.xbl.{Dragula, DragulaOptions}
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.scalajs.jquery.JQuery

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

// Allows form authors to move the grid cell walls to resize cells.
object GridWallDnD {

  private implicit class CellBlockOps(val cell: Block) extends AnyVal {
    def x         : Int = cell.el.attr("data-fr-x").get.toInt
    def w         : Int = cell.el.attr("data-fr-w").toOption.map(_.toInt).getOrElse(1)
    def underlying: html.Element = cell.el.get(0).asInstanceOf[html.Element]
  }

  locally {

    val WallContainerClass = "fb-grid-dnd-wall-container"
    val WallHandleClass    = "fb-grid-dnd-wall-handle"
    val WallShadowClass    = "fb-grid-dnd-wall-shadow"
    val WallDropClass      = "fb-grid-dnd-wall-drop"
    val WallVeilClass      = "fb-grid-dnd-wall-veil"

    case class CurrentCell(block: Block, draggedWall: Option[Direction])
    case class DraggedWall(side: Direction)

    var currentCellOpt: Option[CurrentCell] = None

    // Keep track of the current cell and show draggable walls
    locally {
      Position.currentContainerChanged(
        containerCache = BlockCache.cellCache,
        wasCurrent     = (_   : Block) ⇒
          if (! DndShadow.isDragging) {
            currentCellOpt = None
            DndWall.hideAll()
          },
        becomesCurrent = (cell: Block) ⇒
          if (! DndShadow.isDragging) {
            currentCellOpt = Some(CurrentCell(cell, None))
            val walls      = Cell.movableWalls(cell.underlying)
            val x          = cell.x
            val w          = cell.w
            walls.foreach {
              case Direction.Left  ⇒ DndWall.show(x - 1    , Some(Direction.Left))
              case Direction.Right ⇒ DndWall.show(x + w - 1, Some(Direction.Right))
            }
          }
      )
    }

    // Blocks signaling where cell borders can be dragged from and to.
    object DndWall {

      private val walls                      = new mutable.ListBuffer[JQuery]
      private var dropTarget: Option[JQuery] = None

      def show(index: Int, side: Option[Direction]): Unit = {
        currentCellOpt.foreach { (currentCell) ⇒
          val frGridBody   = currentCell.block.el.parents(".fr-grid-body").first()
          val frGrid       = frGridBody.parent
          val dndContainer = $(s"""<div class="$WallContainerClass" data-index="$index">""")
          val dndHandle    = $(s"""<div class="$WallHandleClass">""")
          dndContainer.append(dndHandle)
          frGrid.append(dndContainer)
          walls.append(dndContainer)
          val wallSlide = side match {
            case Some(Direction.Right) ⇒ dndContainer.width()
            case Some(Direction.Left)  ⇒ 0
            case None                  ⇒ dndContainer.width() / 2
          }
          val wallLeft =
            Offset(frGridBody).left +
            frGridBody.width() / 12 * index -
            wallSlide
          Offset.offset(dndContainer, Offset(
            left = wallLeft,
            top  = currentCell.block.top
          ))
          dndContainer.height(currentCell.block.height)
          dndContainer.on("mouseenter", ControlEditor.mask _)
          dndContainer.on("mouseleave", ControlEditor.unmask _)
        }
      }

      def hideAll(): Unit = {
        walls.foreach(_.remove())
        walls.clear()
      }

      def markClosestAsDropTarget(left: Double): Unit = {

        val closestContainer: Option[JQuery] = {
          def distance(container: JQuery) = Math.abs(Offset(container).left - left)
          case class BestContainer(container: JQuery, distance: Double)
          walls.foldLeft(None: Option[BestContainer]) { (bestOpt, container) ⇒
            val currentDistance = distance(container)
            val keepBest = bestOpt.exists(_.distance <= currentDistance)
            if (keepBest) bestOpt else Some(BestContainer(container, currentDistance))
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
        dndShadowOpt.foreach(_.detach())
        dndShadowOpt = None
      }

      Position.onUnderPointerChange {
        dndShadowOpt.foreach { (dndShadow) ⇒
          val newLeft = Position.pointerPos.left - (dndShadow.width() / 2)
          val newShadowOffset = Offset(dndShadow).copy(left = newLeft)
          Offset.offset(dndShadow, newShadowOffset)
          DndWall.markClosestAsDropTarget(newLeft)
        }
      }
    }

    // Handle D&D
    locally {
      val drake = Dragula(
        js.Array(),
        new DragulaOptions {
          override def isContainer(el: html.Element): Boolean = {
            el.classList.contains(WallContainerClass)
          }
          override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): Boolean = {
            handle.classList.contains(WallHandleClass)
          }
          override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): Boolean =
            target.classList.contains(WallContainerClass)
        }
      )

      drake.onDrag { (el: html.Element, source: html.Element) ⇒
        currentCellOpt.foreach { currentCell ⇒
          val startSide = {
            val wallIndex = source.getAttribute("data-index").toInt
            if (wallIndex == currentCell.block.x - 1) Direction.Left else Direction.Right
          }
          currentCellOpt = Some(currentCell.copy(draggedWall = Some(startSide)))
          val possibleTargets = Cell.cellWallPossibleDropTargets(currentCell.block.underlying, startSide)
          ControlEditor.mask()
          DndShadow.show(source)
          DndWall.hideAll()
          possibleTargets.foreach(DndWall.show(_, None))
        }
      }

      drake.onDragend {(el: html.Element) ⇒
        ControlEditor.unmask()
        DndShadow.hide()
        DndWall.getDropTarget.foreach { (dropTarget) ⇒
          currentCellOpt match {
            case Some(CurrentCell(block, Some(startSide))) ⇒
              val targetIndex  = dropTarget.attr("data-index").get.toInt
              val cellId       = block.el.attr("id").get
              RpcClient[FormBuilderRpcApi].moveWall(cellId, startSide, targetIndex).call()
          }
        }
      }
    }
  }
}
