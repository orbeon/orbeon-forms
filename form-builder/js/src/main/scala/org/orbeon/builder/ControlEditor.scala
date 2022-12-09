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
import org.orbeon.builder.HtmlElementCell._
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.Direction
import org.orbeon.jquery.Offset
import org.orbeon.oxf.fr.{Cell, ControlOps}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms._
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.scalajs.jquery.JQuery
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._


object ControlEditor {

  private val SplitMergeCssClassDirectionOps = List(
    "fb-x-merge" -> Direction.Right -> ((cellId: String) => RpcClient[FormBuilderRpcApi].mergeRight(cellId).call()),
    "fb-x-split" -> Direction.Left  -> ((cellId: String) => RpcClient[FormBuilderRpcApi].splitX    (cellId).call()),
    "fb-y-merge" -> Direction.Down  -> ((cellId: String) => RpcClient[FormBuilderRpcApi].mergeDown (cellId).call()),
    "fb-y-split" -> Direction.Up    -> ((cellId: String) => RpcClient[FormBuilderRpcApi].splitY    (cellId).call())
  )

  private val ControlActionNames             = List("delete", "edit-details", "edit-items")
  private var currentCellOpt : Option[Block] = None
  private lazy val controlEditorLeft         = $(".fb-control-editor-left")
  private lazy val controlEditorRight        = $(".fb-control-editor-right")
  private lazy val controlEditorTop          = $(".fb-control-editor-top")
  private lazy val controlEditors            = controlEditorLeft.get ++ controlEditorRight.get ++ controlEditorTop.get
  private var masked: Boolean                = false

  // Show/hide editor
  Position.currentContainerChanged(
    containerCache = BlockCache.cellCache,
    wasCurrent     = _ => {
      currentCellOpt = None
      hideEditors()
    },
    becomesCurrent = (cell: Block) => {
      currentCellOpt = Some(cell)
      if (! masked)
        showEditors(cell)
    }
  )

  def mask(): Unit = {
    masked = true
    hideEditors()
  }

  def unmask(): Unit = {
    masked = false
    currentCellOpt.foreach(showEditors)
  }

  private def showEditors(cell: Block): Unit = {

    def positionEditor(editor: JQuery, offsetLeft: Double): Unit = {
      editor.show()
      Offset.offset(editor, Offset(
        left = cell.left + offsetLeft,
        top  = cell.top - Position.scrollTop()
      ))
    }

    def positionTopEditor(editor: JQuery, offsetLeft: Double): Unit = {
      editor.show()
      Offset.offset(editor, Offset(
        left = cell.left + offsetLeft,
        top  = cell.top - Position.scrollTop() - editor.height()
      ))
    }

    val controlElemWithNames =
      for {
        elem <- cell.el.children().get
        if ! controlEditors.contains(elem)
        if ! elem.classList.contains("gu-transit")
        if elem.hasAttribute("id")
        name <- ControlOps.controlNameFromIdOpt(elem.id)
      } yield
        elem -> name

    val firstControlElemWithNameOpt = controlElemWithNames.headOption

    // Control editor is only show when the cell isn't empty
    firstControlElemWithNameOpt.foreach { case (controlEl, controlName) =>

      val jControlEl = $(controlEl)

      // Right editor
      jControlEl.append(controlEditorRight)
      positionEditor(controlEditorRight, cell.width - controlEditorRight.outerWidth())

      // Show/hide itemset icon
      val itemsetIcon = controlEditorRight.find(".fb-control-edit-items")
      itemsetIcon.toggleClass("xforms-disabled", ! controlEl.classList.contains("fb-itemset"))

      // Top editor
      jControlEl.append(controlEditorTop)
      positionTopEditor(controlEditorTop, 0)

      controlEditorTop.children().get(0).textContent = controlName
    }
    firstControlElemWithNameOpt.map(e => $(e._1)).getOrElse(cell.el).append(controlEditorLeft)
    positionEditor(controlEditorLeft, 0)

    // Enable/disable split/merge icons
    val allowedDirections = {
      val cellEl = cell.el.get(0).asInstanceOf[html.Element]
      Cell.canChangeSize(cellEl)
    }
    for (((cssClass, direction), _) <- SplitMergeCssClassDirectionOps) {
      val disableIcon = ! allowedDirections.contains(direction)
      val icon = controlEditorLeft.find(s".$cssClass")
      icon.toggleClass("disabled", disableIcon)
    }
  }

  private def hideEditors(): Unit = {
    controlEditorLeft.hide()
    controlEditorRight.hide()
    controlEditorTop.hide()
    controlEditorLeft.detach()
    controlEditorRight.detach()
    controlEditorTop.detach()
  }

  // Control actions
  ControlActionNames.foreach { actionName =>
    val actionEl = controlEditorRight.find(s".fb-control-$actionName")
    actionEl.on("click.orbeon.builder.control-editor", () => asUnit {
      currentCellOpt.foreach { currentCell =>

        val controlId = currentCell.el.children().attr("id").get

        actionName match {
          case "delete"       => RpcClient[FormBuilderRpcApi].controlDelete     (controlId = controlId).call()
          case "edit-details" => RpcClient[FormBuilderRpcApi].controlEditDetails(controlId = controlId).call()
          case "edit-items"   => RpcClient[FormBuilderRpcApi].controlEditItems  (controlId = controlId).call()
        }
      }
    })
  }

  // Expand/shrink actions
  for (((cssClass, _), ops) <- SplitMergeCssClassDirectionOps) {
    val iconEl = controlEditorLeft.find(s".$cssClass")
    iconEl.on("click.orbeon.builder.control-editor", () => asUnit {
      if (! iconEl.is(".disabled"))
        currentCellOpt.foreach { currentCell =>
          val cellId = currentCell.el.attr("id").get
          ops(cellId)
        }
    })
  }
}
