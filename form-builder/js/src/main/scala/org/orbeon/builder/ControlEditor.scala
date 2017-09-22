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
import org.orbeon.xforms._
import org.scalajs.dom.document

import scala.scalajs.js

object ControlEditor {

  val ControlActionNames            = List("delete", "edit-details", "edit-items")
  var currentCellOpt: Option[Block] = None
  lazy val controlEditorLeft        = $(".fb-control-editor-left")
  lazy val controlEditorRight       = $(".fb-control-editor-right")

  // Show/hide editor
  Position.currentContainerChanged(
    containerCache = BlockCache.cellCache,
    wasCurrent = (cell: Block) ⇒ {
      currentCellOpt = None
      controlEditorLeft.hide()
      controlEditorRight.hide()
    },

    becomesCurrent = (cell: Block) ⇒ {

      currentCellOpt = Some(cell)

      // Position editors
      controlEditorLeft.show()
      Position.offset(controlEditorLeft, new Position.Offset {
        override val left = cell.left
        override val top  = cell.top
      })
      controlEditorRight.show()
      Position.offset(controlEditorRight, new Position.Offset {
        override val left = cell.left + cell.width - controlEditorRight.outerWidth()
        override val top  = cell.top
      })
    }
  )

  // Register listener on editor icons
  $(document).ready(() ⇒ {
    scala.scalajs.js.Dynamic.global.console.log("register listeners")
    ControlActionNames.foreach((actionName) ⇒ {
      val className =  s".fb-control-$actionName"
      val actionEl = controlEditorRight.find(className)
      scala.scalajs.js.Dynamic.global.console.log("on", actionEl)
      actionEl.on("click", () ⇒ {
        scala.scalajs.js.Dynamic.global.console.log("click", currentCellOpt.asInstanceOf[js.Any])
        currentCellOpt.foreach((currentCell) ⇒ {
          val gridEl = currentCell.el.closest(".xbl-fr-grid")
          val gridId = gridEl.attr("id").get
          DocumentAPI.dispatchEvent(
            targetId   = gridId,
            eventName  = s"fb-control-$actionName",
            properties = js.Dictionary(
              "fb-cell-x" → currentCell.el.attr("data-x").get,
              "fb-cell-y" → currentCell.el.attr("data-y").get
            )
          )
        })
      })
    })
  })

}
