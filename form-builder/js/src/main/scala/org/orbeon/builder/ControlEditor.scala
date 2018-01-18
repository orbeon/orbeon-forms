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
import org.orbeon.fr.Grid
import org.orbeon.jquery.Offset
import org.orbeon.oxf.fr.ClientNames._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.scalajs.jquery.JQuery

import scala.concurrent.ExecutionContext.Implicits.global

object ControlEditor {

  def resizeCell(cell: Block, direction: Direction): Unit = {
    val cellId = cell.el.attr("id").get
    direction match {
      case Direction.Up    ⇒ RpcClient[FormBuilderRpcApi].shrinkDown (cellId).call()
      case Direction.Right ⇒ RpcClient[FormBuilderRpcApi].expandRight(cellId).call()
      case Direction.Down  ⇒ RpcClient[FormBuilderRpcApi].expandDown (cellId).call()
      case Direction.Left  ⇒ RpcClient[FormBuilderRpcApi].shrinkRight(cellId).call()
    }
  }

  private val ControlActionNames             = List("delete", "edit-details", "edit-items")
  private var currentCellOpt: Option[Block]  = None
  private lazy val controlEditorLeft         = $(".fb-control-editor-left")
  private lazy val controlEditorRight        = $(".fb-control-editor-right")
  private var previousCellOpt: Option[Block] = None
  private var masking: Boolean               = false

  // Show/hide editor
  Position.currentContainerChanged(
    containerCache = BlockCache.cellCache,
    wasCurrent     = (_) ⇒ {
      previousCellOpt = None
      hideEditors()
    },
    becomesCurrent = (cell: Block) ⇒ {
      if (! masking)
        showEditors(cell)
    }
  )

  def mask(): Unit = {
    masking = true
    previousCellOpt = currentCellOpt
    hideEditors()
  }

  def unmask(): Unit = {
    masking = false
    previousCellOpt.foreach(showEditors)
    previousCellOpt = None
  }

  private def showEditors(cell: Block): Unit = {

    currentCellOpt = Some(cell)

    // Position editors
    def positionEditor(editor: JQuery, offsetLeft: Double): Unit = {
      editor.show()
      Offset.offset(editor, Offset(
        left = cell.left + offsetLeft,
        top  = cell.top - Position.scrollTop()
      ))
    }
    val cellContent = cell.el.children()
    val controlElOpt = (cellContent.length > 0).option(cellContent.first())
    controlElOpt.foreach((controlEl) ⇒ {
      // Control editor is only show when the cell isn't empty
      controlEl.append(controlEditorRight)
      positionEditor(controlEditorRight, cell.width - controlEditorRight.outerWidth())
      // Show/hide itemset icon
      val itemsetIcon = controlEditorRight.find(".fb-control-edit-items")
      itemsetIcon.toggleClass("xforms-disabled", ! controlEl.is(".fb-itemset"))
    })
    controlElOpt.getOrElse(cell.el).append(controlEditorLeft)
    positionEditor(controlEditorLeft, 0)

    // Enable/disable arrow icons
    for (direction ← Direction.values) {
      val cellEl = cell.el.get(0).asInstanceOf[html.Element]
      val directionName = direction.entryName
      val disableIcon =
        direction match {
          case Direction.Right | Direction.Down ⇒
            Grid.spaceToExtendCell(cellEl, direction) == 0
          case Direction.Left ⇒
            (cell.el.attr(AttW) map (_.toInt) getOrElse 1) <= 1
          case Direction.Up ⇒
            (cell.el.attr(AttH) map (_.toInt) getOrElse 1) <= 1
        }
      val icon = controlEditorLeft.find(s".fb-arrow-$directionName")
      icon.toggleClass("disabled", disableIcon)
    }
  }

  private def hideEditors(): Unit = {
    currentCellOpt = None
    controlEditorLeft.hide()
    controlEditorRight.hide()
    controlEditorLeft.detach()
    controlEditorRight.detach()
  }

  // Control actions
  ControlActionNames.foreach((actionName) ⇒ {
    val actionEl = controlEditorRight.find(s".fb-control-$actionName")
    actionEl.on("click.orbeon.builder.control-editor", () ⇒ asUnit {
      currentCellOpt.foreach((currentCell) ⇒ {

        val controlId = currentCell.el.children().attr("id").get

        actionName match {
          case "delete"       ⇒ RpcClient[FormBuilderRpcApi].controlDelete     (controlId = controlId).call()
          case "edit-details" ⇒ RpcClient[FormBuilderRpcApi].controlEditDetails(controlId = controlId).call()
          case "edit-items"   ⇒ RpcClient[FormBuilderRpcApi].controlEditItems  (controlId = controlId).call()
        }
      })
    })
  })

  // Expand/shrink actions
  for (direction ← Direction.values) {
    val directionName = direction.entryName
    val className = s"fb-arrow-$directionName"
    val iconEl = controlEditorLeft.find(s".$className")

    iconEl.on("click.orbeon.builder.control-editor", () ⇒ asUnit {
      if (! iconEl.is(".disabled"))
        currentCellOpt.foreach(resizeCell(_, direction))
    })
  }
}
