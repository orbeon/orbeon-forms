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
import org.orbeon.jquery.Offset
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

  if (false)
  locally {

    val WallContainerClass = "fb-grid-dnd-wall-container"
    val WallHandleClass    = "fb-grid-dnd-wall-handle"
    val WallShadowClass    = "fb-grid-dnd-wall-shadow"
    val WallVeilClass      = "fb-grid-dnd-wall-veil"

    Cell.listenOnChange()

    // Keeps track of the current cell (if any), and provides utility functions on cells.
    object Cell {

      var currentOpt: Option[Block] = None

      def listenOnChange(): Unit = {
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
              val x = Cell.x(cell)
              val w = Cell.w(cell)
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
      def w(cell: Block): Int = cell.el.attr("data-fr-w").get.toInt
    }

    // Blocks signaling where cell borders can be dragged from and to.
    object DndContainers {

      private val existingContainers = new mutable.ListBuffer[JQuery]

      def show(index: Int): Unit = {
        Cell.currentOpt.foreach { (currentCell) ⇒
          val frGridBody   = currentCell.el.parents(".fr-grid-body").first()
          var frGrid       = frGridBody.parent
          val dndContainer = $(s"""<div class="$WallContainerClass" data-index="$index">""")
          val dndHandle    = $(s"""<div class="$WallHandleClass">""")
          dndContainer.append(dndHandle)
          frGrid.append(dndContainer)
          existingContainers.append(dndContainer)
          Offset.offset(dndContainer, Offset(
            left = Offset(frGridBody).left + (frGridBody.width() - dndContainer.width()) / 12 * index,
            top  = currentCell.top
          ))
          dndContainer.height(currentCell.height)
        }
      }

      def hideAll(): Unit = {
        existingContainers.foreach(_.remove())
        existingContainers.clear()
      }
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
        }
      }
    }

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
      scala.scalajs.js.Dynamic.global.console.log("drag, storing shadow")
      DndShadow.show(source)
      DndContainers.hideAll()
      for (i ← (1 to 5) ++ (6 to 11))
        DndContainers.show(i)
    }

    drake.onDrop {(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) ⇒
      Cell.currentOpt.foreach { (currentCell) ⇒
        val sourceIndex  = Cell.x(currentCell) + Cell.w(currentCell) - 1
        val targetIndex  = target.getAttribute("data-index").toInt
        val direction = if (sourceIndex < targetIndex) Direction.Right else Direction.Left
        scala.scalajs.js.Dynamic.global.console.log(sourceIndex, targetIndex, direction.toString)
        for (i ← 1 to Math.abs(targetIndex - sourceIndex))
          ControlEditor.resizeCell(currentCell, direction)
      }
    }

    drake.onDragend {(el: html.Element) ⇒
      scala.scalajs.js.Dynamic.global.console.log("drag end")
      DndShadow.hide()
    }
  }
}
