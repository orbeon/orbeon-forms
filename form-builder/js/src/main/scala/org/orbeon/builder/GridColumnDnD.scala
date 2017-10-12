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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js

object GridColumnDnD {

  if (false)
  locally {

    val FbMainClass          = "fb-main"
    val ColumnContainerClass = "fb-grid-dnd-column-container"
    val ColumnHandleClass    = "fb-grid-dnd-column-handle"
    val ColumnShadowClass    = "fb-grid-dnd-column-shadow"
    val FbMain               = $(s".$FbMainClass")

    Cell.listenOnChange()

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
              scala.scalajs.js.Dynamic.global.console.log("new cell", cell.el.attr("id").get)
              currentOpt = Some(cell)
              DndContainers.show(Cell.x(cell) + Cell.w(cell) - 1)
            }
        )
      }

      def x(cell: Block) = cell.el.attr("data-fr-x").get.toInt
      def w(cell: Block) = cell.el.attr("data-fr-w").get.toInt
    }

    // Blocks signaling where cell borders can be dragged from and to
    object DndContainers {

      def show(index: Int): Unit = {
        Cell.currentOpt.foreach { (currentCell) ⇒
          val gridBody     = currentCell.el.parents(".fr-grid-body").first()
          val dndContainer = $(s"""<div class="$ColumnContainerClass" data-index="$index">""")
          val dndHandle    = $(s"""<div class="$ColumnHandleClass">""")
          dndContainer.append(dndHandle)
          FbMain.append(dndContainer)
          Offset.offset(dndContainer, Offset(
            left = Offset(gridBody).left + (gridBody.width() - dndContainer.width()) / 12 * index,
            top  = currentCell.top
          ))
          dndContainer.height(currentCell.height)
        }
      }

      def hideAll(): Unit =
        FbMain.find(s".$ColumnContainerClass").remove()
    }

    // Block showing during the dragging operation
    object DndShadow {

      var dndShadowOpt: Option[JQuery] = None

      def isDragging: Boolean = dndShadowOpt.isDefined

      def show(containerEl: html.Element): Unit = {

        // Create shadow element
        val dndShadow = $(s"""<div class="$ColumnShadowClass">""")
        FbMain.append(dndShadow)
        dndShadowOpt = Some(dndShadow)

        // Position and size  the shadow over source container
        val container = $(containerEl)
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
          val newShadowOffset = Offset(dndShadow).copy(left = Position.pointerPos.left)
          Offset.offset(dndShadow, newShadowOffset)
        }
      }
    }

    val drake = Dragula(
      js.Array(),
      new DragulaOptions {
        override def isContainer(el: html.Element): Boolean = {
          el.classList.contains(ColumnContainerClass)
        }
        override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): Boolean = {
          handle.classList.contains(ColumnHandleClass)
        }
        override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): Boolean =
          target.classList.contains(ColumnContainerClass)
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
