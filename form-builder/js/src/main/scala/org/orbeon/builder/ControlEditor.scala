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
import org.orbeon.fr.Grid
import org.orbeon.xforms._
import org.scalajs.dom.{document, html}

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
      controlEditorLeft.detach()
      controlEditorRight.detach()
    },

    becomesCurrent = (cell: Block) ⇒ {

      currentCellOpt = Some(cell)

      if (cell.el.children().length > 0) {
        // Control editor is only show when the cell isn't empty
        cell.el.append(controlEditorRight)
        controlEditorRight.show()
        Position.offset(controlEditorRight, new Position.Offset {
          override val left = cell.left + cell.width - controlEditorRight.outerWidth()
          override val top  = cell.top
        })
      }
      // Position editors
      cell.el.append(controlEditorLeft)
      controlEditorLeft.show()
      Position.offset(controlEditorLeft, new Position.Offset {
        override val left = cell.left
        override val top  = cell.top
      })

      // Enable/disable arrow icons
      for (direction ← Direction.values) {
        val cellEl = cell.el.get(0).asInstanceOf[html.Element]
        val directionName = direction.entryName
        val disableIcon =
          direction match {
            case Direction.Right | Direction.Down ⇒
              Grid.spaceToExtendCell(cellEl, direction) == 0
            case Direction.Left ⇒
              (cell.el.attr("data-w") map (_.toInt) getOrElse 1) <= 1
            case Direction.Up ⇒
              (cell.el.attr("data-h") map (_.toInt) getOrElse 1) <= 1
          }
        val icon = controlEditorLeft.find(s".icon-arrow-$directionName")
        icon.toggleClass("disabled", disableIcon)
      }
    }
  )

  // Register listener on editor icons
  $(document).ready(() ⇒ {

    // Control actions
    ControlActionNames.foreach((actionName) ⇒ {
      val classEventName =  s"fb-control-$actionName"
      val actionEl = controlEditorRight.find(s".$classEventName")
      actionEl.on("click", () ⇒ {
        currentCellOpt.foreach((currentCell) ⇒ {
          val controlEl = currentCell.el.children()
          DocumentAPI.dispatchEvent(
            targetId   = controlEl.attr("id").get,
            eventName  = classEventName
          )
        })
      })
    })

    // Expand/shrink actions
    for (direction ← Direction.values) {
      val directionName = direction.entryName
      val className = s"icon-arrow-$directionName"
      val iconEl = controlEditorLeft.find(s".$className")
      val eventName = direction match {
        case Direction.Up    ⇒ "fb-shrink-down"
        case Direction.Right ⇒ "fb-expand-right"
        case Direction.Down  ⇒ "fb-expand-down"
        case Direction.Left  ⇒ "fb-shrink-right"
      }
      iconEl.on("click", () ⇒ {
        if (! iconEl.is(".disabled"))
          for (currentCell ← currentCellOpt)
            DocumentAPI.dispatchEvent(
              targetId   = currentCell.el.attr("id").get,
              eventName  = eventName
            )
      })

    }
  })

}
