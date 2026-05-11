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

import autowire.*
import org.orbeon.builder.HtmlElementCell.*
import org.orbeon.builder.rpc.FormBuilderRpcApi
import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fr.{Cell, ControlOps}
import org.orbeon.xforms.rpc.RpcClient
import org.scalajs.dom.html
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.util.chaining.*


object ControlEditor {

  private val SplitMergeCssClassDirectionOps = List(
    "fb-x-merge" -> Direction.Right -> ((cellId: String) => RpcClient[FormBuilderRpcApi].mergeRight(cellId).call()),
    "fb-x-split" -> Direction.Left  -> ((cellId: String) => RpcClient[FormBuilderRpcApi].splitX    (cellId).call()),
    "fb-y-merge" -> Direction.Down  -> ((cellId: String) => RpcClient[FormBuilderRpcApi].mergeDown (cellId).call()),
    "fb-y-split" -> Direction.Up    -> ((cellId: String) => RpcClient[FormBuilderRpcApi].splitY    (cellId).call())
  )

  private val ControlActionNames             = List("delete", "edit-details", "edit-items")
  private var currentCellOpt : Option[Block] = None

  private val controlEditorLeft : () => html.Element = memoizeIfFound(".fb-control-editor-left")
  private val controlEditorRight: () => html.Element = memoizeIfFound(".fb-control-editor-right")
  private val controlEditorTop  : () => html.Element = memoizeIfFound(".fb-control-editor-top")

  private def controlEditors: List[html.Element] = controlEditorLeft() :: controlEditorRight() :: controlEditorTop() :: Nil

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

    def showAndPositionLeftOrRightEditor(
      editor    : html.Element,
      offsetLeft: => Double // by name so that this can be computed after `show()` (#7669)
    ): Unit = {
      editor.show()
      editor.setOffset(DomSupport.Offset(
        left = cell.left + offsetLeft,
        top  = cell.top - Position.scrollTop
      ))
    }

    def showAndPositionTopEditor(
      editor    : html.Element,
      offsetLeft: => Double // by name so that this can be computed after `show()` (#7669)
    ): Unit = {
      editor.show()
      editor.setOffset(DomSupport.Offset(
        left = cell.left + offsetLeft,
        top  = cell.top - Position.scrollTop - editor.contentHeightOrZero
      ))
    }

    val controlElemWithNames: collection.Seq[(html.Element, String)] =
      for {
        elem <- cell.el.childrenT
        if ! controlEditors.contains(elem)
        if ! elem.hasClass("gu-transit")
        if elem.hasAttribute("id")
        name <- ControlOps.controlNameFromIdOpt(elem.id)
      } yield
        elem -> name

    val firstControlElemWithNameOpt = controlElemWithNames.headOption
    val isViewMode                  = FormRunnerUtils.isViewMode(cell.el)

    // Control/right editor is only show when the cell isn't empty
    firstControlElemWithNameOpt.foreach { case (controlEl, controlName) =>

      // Right editor
      controlEl.append(controlEditorRight())
      showAndPositionLeftOrRightEditor(controlEditorRight(), cell.width - controlEditorRight().outerWidth)

      // Show/hide itemset icon
      controlEditorRight()
        .querySelectorOpt(".fb-control-edit-items")
        .foreach(_.toggleClass("xforms-disabled", ! controlEl.hasClass("fb-itemset")))

      if (isViewMode)
        controlEditorRight()
          .querySelectorOpt(".fb-control-delete, .fb-control-handle")
          .foreach(_.hide())

      // Top editor
      controlEl.append(controlEditorTop())
      showAndPositionTopEditor(controlEditorTop(), 0)

      controlEditorTop().childrenT.head.textContent = controlName
    }

    // Cell/left editor
    if (! isViewMode) {
      firstControlElemWithNameOpt.map(e => e._1).getOrElse(cell.el).append(controlEditorLeft())
      showAndPositionLeftOrRightEditor(controlEditorLeft(), 0)

      // Enable/disable split/merge icons
      val allowedDirections = {
        Cell.canChangeSize(cell.el)
      }
      for (((cssClass, direction), _) <- SplitMergeCssClassDirectionOps) {
        val disableIcon = ! allowedDirections.contains(direction)
        val icon = controlEditorLeft().querySelectorT(s".$cssClass")
        icon.toggleClass("disabled", disableIcon)
      }
    }
  }

  private def hideEditors(): Unit = {
    controlEditorLeft().hide()
    controlEditorRight().hide()
    controlEditorTop().hide()
  }

  // Control actions
  ControlActionNames.foreach { actionName =>
    val actionEl = controlEditorRight().querySelectorOpt(s".fb-control-$actionName")
    actionEl.foreach(_.addEventListener("click", (_: dom.Event) => {
      currentCellOpt.foreach { currentCell =>

        val controlId = currentCell.el.childrenT.head.id

        actionName match {
          case "delete"       => RpcClient[FormBuilderRpcApi].controlDelete     (controlId = controlId).call()
          case "edit-details" => RpcClient[FormBuilderRpcApi].controlEditDetails(controlId = controlId).call()
          case "edit-items"   => RpcClient[FormBuilderRpcApi].controlEditItems  (controlId = controlId).call()
        }
      }
    }))
  }

  // Expand/shrink actions
  for (((cssClass, _), ops) <- SplitMergeCssClassDirectionOps) {
    val iconElOpt = controlEditorLeft().querySelectorOpt(s".$cssClass")
    iconElOpt.foreach { iconEl =>
      iconEl.addEventListener("click", (_: dom.Event) => {
        if (! iconEl.hasClass("disabled"))
          currentCellOpt.foreach { currentCell =>
            ops(currentCell.el.id)
          }
      }
      )
    }
  }

  // Keep a reference when found, as elements can be removed from the DOM, for instance by full updates
  private def memoizeIfFound(selector: String): () => html.Element = {
    var memoOpt: Option[html.Element] = None
    () =>
      memoOpt match {
        case Some(memo) => memo
        case _          => dom.document.querySelectorT(selector).ensuring(_ ne null).tap(r => memoOpt = Some(r))
      }
  }
}
