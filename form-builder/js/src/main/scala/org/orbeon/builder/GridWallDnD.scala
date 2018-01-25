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

import org.orbeon.builder.BlockCache.Block
import org.orbeon.datatypes.Direction
import org.orbeon.fr.HtmlElementCell._
import org.orbeon.jquery.Offset
import org.orbeon.oxf.fr.Cell
import org.orbeon.xbl.{Dragula, DragulaOptions}
import org.orbeon.xforms._
import org.scalajs.dom.html
import org.scalajs.jquery.JQuery

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

// Allows form authors to move the grid cell walls to resize cells.
object GridWallDnD {

  if (false)
  locally {

    val WallContainerClass = "fb-grid-dnd-wall-container"
    val WallHandleClass    = "fb-grid-dnd-wall-handle"
    val WallShadowClass    = "fb-grid-dnd-wall-shadow"
    val WallDropClass      = "fb-grid-dnd-wall-drop"
    val WallVeilClass      = "fb-grid-dnd-wall-veil"

    // Initialize listeners for current cell
    CurrentCell

    // Keep track of the current cell
    // Instruct containers to show when appropriate
    object CurrentCell {

      var currentOpt: Option[Block] = None

      locally {
        Position.currentContainerChanged(
          containerCache = BlockCache.cellCache,
          wasCurrent     = (_   : Block) ⇒
            if (! DndShadow.isDragging) {
              currentOpt = None
              DndContainers.hideAll()
            },
          becomesCurrent = (cell: Block) ⇒
            if (! DndShadow.isDragging) {
              currentOpt = Some(cell)
              val cellEl = cell.el.get(0).asInstanceOf[html.Element]
              val walls = Cell.movableWalls(cellEl)
              println(walls)
              val x = CurrentCell.x(cell)
              val w = CurrentCell.w(cell)
              // Left
              if (x > 1 || w > 1)
                DndContainers.show(x - 1)
              // Right
              if (x + w < 12 || w > 1)
                DndContainers.show(x + w - 1)
            }
        )
      }

      def x(cell: Block): Int = cell.el.attr("data-fr-x").get.toInt
      def w(cell: Block): Int = cell.el.attr("data-fr-w").toOption.map(_.toInt).getOrElse(1)
    }

    // Blocks signaling where cell borders can be dragged from and to.
    object DndContainers {

      private val containers                 = new mutable.ListBuffer[JQuery]
      private var dropTarget: Option[JQuery] = None

      def show(index: Int): Unit = {
        CurrentCell.currentOpt.foreach { (currentCell) ⇒
          val frGridBody   = currentCell.el.parents(".fr-grid-body").first()
          var frGrid       = frGridBody.parent
          val dndContainer = $(s"""<div class="$WallContainerClass" data-index="$index">""")
          val dndHandle    = $(s"""<div class="$WallHandleClass">""")
          dndContainer.append(dndHandle)
          frGrid.append(dndContainer)
          containers.append(dndContainer)
          Offset.offset(dndContainer, Offset(
            left = Offset(frGridBody).left + (frGridBody.width() - dndContainer.width()) / 12 * index,
            top  = currentCell.top
          ))
          dndContainer.height(currentCell.height)
          dndContainer.on("mouseenter", ControlEditor.mask _)
          dndContainer.on("mouseleave", ControlEditor.unmask _)
        }
      }

      def hideAll(): Unit = {
        containers.foreach(_.remove())
        containers.clear()
      }

      def markClosestAsDropTarget(left: Double): Unit = {

        val closestContainer: Option[JQuery] = {
          def distance(container: JQuery) = Math.abs(Offset(container).left - left)
          case class BestContainer(container: JQuery, distance: Double)
          containers.foldLeft(None: Option[BestContainer]) { (bestOpt, container) ⇒
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
          DndContainers.markClosestAsDropTarget(newLeft)
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
        ControlEditor.mask()
        DndShadow.show(source)
        DndContainers.hideAll()

        for (i ← 1 to 11)
          DndContainers.show(i)
      }

      drake.onDragend {(el: html.Element) ⇒
        ControlEditor.unmask()
        DndShadow.hide()
        CurrentCell.currentOpt.foreach { (currentCell) ⇒
          DndContainers.getDropTarget.foreach { (dropTarget) ⇒
            val sourceIndex  = CurrentCell.x(currentCell) + CurrentCell.w(currentCell) - 1
            val targetIndex  = dropTarget.attr("data-index").get.toInt
            val direction = if (sourceIndex < targetIndex) Direction.Right else Direction.Left
            for (i ← 1 to Math.abs(targetIndex - sourceIndex))
              ControlEditor.resizeCell(currentCell, direction)
          }
        }
      }
    }
  }
}
