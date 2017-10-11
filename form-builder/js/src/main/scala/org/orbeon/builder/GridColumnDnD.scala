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
import org.orbeon.jquery.Offset
import org.orbeon.xbl.{Dragula, DragulaOptions}
import org.orbeon.xforms._
import org.orbeon.xforms.facade.JQueryTooltip
import org.scalajs.dom.{document, html}
import org.scalajs.jquery.JQuery

import scala.scalajs.js

object GridColumnDnD {

  locally {

    val FbMainClass          = "fb-main"
    val ColumnContainerClass = "fb-grid-dnd-column-container"
    val ColumnHandleClass    = "fb-grid-dnd-column-handle"
    val ColumnShadowClass    = "fb-grid-dnd-column-shadow"
    val FbMain               = $(s".$FbMainClass")

    var dndShadowOpt: Option[JQuery] = None

    Position.currentContainerChanged(
      containerCache = BlockCache.cellCache,
      wasCurrent     = (_   : Block) ⇒ hideDndContainers(),
      becomesCurrent = (cell: Block) ⇒ showDndContainers(cell)
    )

    def showDndContainers(cell: Block): Unit = {

      val gridBody   = cell.el.parents(".fr-grid-body").first()

      def addContainer(index: Int): Unit = {
        val dndContainer = $(s"""<div class="$ColumnContainerClass">""")
        val dndHandle    = $(s"""<div class="$ColumnHandleClass">""")
        dndContainer.append(dndHandle)
        FbMain.append(dndContainer)
        Offset.offset(dndContainer, Offset(
          left = Offset(gridBody).left + (gridBody.width() - dndContainer.width()) / 12 * index,
          top  = cell.top
        ))
        dndContainer.height(cell.height)
      }

      for (i ← 6 to 12)
        addContainer(i)
    }

    def hideDndContainers(): Unit = {
      FbMain.find(s".$ColumnContainerClass").remove()
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

      // Create shadow element
      val dndShadow = $(s"""<div class="$ColumnShadowClass">""")
      FbMain.append(dndShadow)
      dndShadowOpt = Some(dndShadow)

      // Position shadow over source container
      val container = $(source)
      Offset.offset(dndShadow, Offset(container))
      dndShadow.width(container.width())
      dndShadow.height(container.height())

      scala.scalajs.js.Dynamic.global.console.log("drag", dndShadow)
    }

    Position.onUnderPointerChange {
      scala.scalajs.js.Dynamic.global.console.log("pointer change")
      dndShadowOpt.foreach { (dndShadow) ⇒
        val newShadowOffset = Offset(dndShadow).copy(left = Position.pointerPos.left)
        Offset.offset(dndShadow, newShadowOffset)
        scala.scalajs.js.Dynamic.global.console.log("adjusting left", Position.pointerPos.left)
      }
    }

    drake.onDrop((el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) ⇒ {
      scala.scalajs.js.Dynamic.global.console.log("Drop")
    })

    drake.onDragend((el: html.Element) ⇒
      dndShadowOpt = None
    )
  }
}
