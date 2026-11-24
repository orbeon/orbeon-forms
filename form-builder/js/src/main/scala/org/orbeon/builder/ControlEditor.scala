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

import org.orbeon.builder.HtmlElementCell.*
import org.orbeon.builder.rpc.FormBuilderRpcClient
import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fr.{Cell, ControlOps}
import org.scalajs.dom.html
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsUI
import org.scalajs.dom
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.util.chaining.*


object ControlEditor {

  private val SplitMergeCssClassDirectionOps = List(
    "fb-x-merge" -> Direction.Right -> ((cellId: String) => FormBuilderRpcClient.api.mergeRight(cellId)),
    "fb-x-split" -> Direction.Left  -> ((cellId: String) => FormBuilderRpcClient.api.splitX    (cellId)),
    "fb-y-merge" -> Direction.Down  -> ((cellId: String) => FormBuilderRpcClient.api.mergeDown (cellId)),
    "fb-y-split" -> Direction.Up    -> ((cellId: String) => FormBuilderRpcClient.api.splitY    (cellId))
  )

  private val ControlActionNames             = List("delete", "edit-details", "edit-items", "label-left", "label-top")
  private var currentCellOpt : Option[Block] = None

  private val controlEditorLeft : () => html.Element = memoizeIfFound(".fb-control-editor-left")
  private val controlEditorRight: () => html.Element = memoizeIfFound(".fb-control-editor-right")
  private val controlEditorTop  : () => html.Element = memoizeIfFound(".fb-control-editor-top")

  private def controlEditors: List[html.Element] = controlEditorLeft() :: controlEditorRight() :: controlEditorTop() :: Nil

  def isSeparateLabelElem(elem: html.Element): Boolean =
    elem.hasClass("xforms-label") &&
      ! elem.hasClass("xforms-control") &&
      ! elem.hasClass("xbl-component")  &&
      elem.hasAttribute("id")           &&
      ControlOps.controlNameFromSeparateLabelIdOpt(elem.id).isDefined

  def isCellEmptyOfControlOrLabel(cellEl: html.Element): Boolean =
    cellEl.childrenT.forall(elem =>
      controlEditors.contains(elem)          ||
      elem.hasClass("gu-transit")            ||
      elem.hasClass("gu-mirror")             ||
      elem.hasClass("fb-control-cell-badges")
    )

  def gridHasSeparateLabelFor(gridEl: html.Element, controlName: String): Boolean =
    gridEl
      .querySelectorAllT(".fr-grid-td > .xforms-label")
      .exists(el => isSeparateLabelElem(el) && ControlOps.controlNameFromSeparateLabelIdOpt(el.id).contains(controlName))

  def canCellMoveLabelLeft(cellEl: html.Element): Boolean = {
    val controlElemOpt =
      cellEl.childrenT.find(elem =>
        ! controlEditors.contains(elem)          &&
        ! elem.hasClass("gu-transit")            &&
        ! elem.hasClass("fb-control-cell-badges") &&
        elem.hasAttribute("id")                  &&
        ControlOps.controlNameFromIdOpt(elem.id).isDefined
      )

    controlElemOpt.exists { controlEl =>
      val controlName = ControlOps.controlNameFromIdOpt(controlEl.id).get
      canControlMoveLabelLeft(cellEl, controlEl, controlName)
    }
  }

  private def canControlMoveLabelLeft(cellEl: html.Element, controlEl: html.Element, controlName: String): Boolean =
    cellEl.closestOpt(".fr-grid").exists { gridEl =>
      ! gridEl.hasClass("fr-repeat-single-row")                         &&
      ! isSeparateLabelElem(controlEl)                                  &&
      ! gridHasSeparateLabelFor(gridEl, controlName)                    &&
      ! controlEl.hasClass("xforms-trigger")                            &&
      ! controlEl.hasClass("xbl-fr-hyperlink")                          &&
      controlEl.querySelectorOpt(".xforms-label, label.checkbox").isDefined &&
      (
        HtmlElementCellOps.w(cellEl).getOrElse(1) >= 2 || {
          cellEl.closestOpt(".fr-grid-body").exists { gridBodyEl =>
            val gridModel = Cell.analyze12ColumnGridAndFillHoles(gridBodyEl, simplify = false, transpose = false)
            Cell.findOriginCell(gridModel, cellEl).exists { originCell =>
              val neighbors = Cell.nonOverflowingNeighbors(gridModel, originCell, Direction.Left)
              neighbors.size == 1 && {
                val leftCell = neighbors.head
                leftCell.y == originCell.y &&
                leftCell.h == originCell.h &&
                leftCell.u.exists(isCellEmptyOfControlOrLabel)
              }
            }
          }
        }
      )
    }

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

  def refreshEditors(): Unit =
    if (! masked)
      currentCellOpt.foreach(showEditors)

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

    val controlElemWithNames: collection.Seq[(html.Element, String, Boolean)] =
      for {
        elem <- cell.el.childrenT
        if ! controlEditors.contains(elem)
        if ! elem.hasClass("gu-transit")
        if ! elem.hasClass("fb-control-cell-badges")
        if elem.hasAttribute("id")
        isSepLabel = isSeparateLabelElem(elem)
        name <-
          if (isSepLabel)
            ControlOps.controlNameFromSeparateLabelIdOpt(elem.id)
          else
            ControlOps.controlNameFromIdOpt(elem.id)
      } yield
        (elem, name, isSepLabel)

    val firstControlElemWithNameOpt = controlElemWithNames.headOption
    val isViewMode                  = FormRunnerUtils.isViewMode(cell.el)

    // Control/right editor is only shown when the cell isn't empty
    firstControlElemWithNameOpt.foreach { case (controlEl, controlName, isSepLabel) =>

      // Right editor: must be appended inside `controlEl` so Dragula sees `.fb-control-handle` as a descendant of `controlEl`
      controlEl.append(controlEditorRight())

      val gridElOpt        = cell.el.closestOpt(".fr-grid")
      val hasSeparateLabel = ! isSepLabel && gridElOpt.exists(gridHasSeparateLabelFor(_, controlName))
      val canMoveLeft      = ! isSepLabel && ! isViewMode && canControlMoveLabelLeft(cell.el, controlEl, controlName)
      val canMoveTop       = ! isViewMode && (isSepLabel || hasSeparateLabel)

      // Show/hide itemset icon
      controlEditorRight()
        .querySelectorOpt(".fb-control-edit-items")
        .foreach(_.toggleClass("xforms-disabled", isSepLabel || ! controlEl.hasClass("fb-itemset")))

      // Show/hide label placement icons
      controlEditorRight()
        .querySelectorOpt(".fb-control-label-left")
        .foreach(_.toggleClass("xforms-disabled", ! canMoveLeft))

      controlEditorRight()
        .querySelectorOpt(".fb-control-label-top")
        .foreach(_.toggleClass("xforms-disabled", ! canMoveTop))

      // Hide destructive delete icon on a separate label cell (`.fb-control-label-top` moves it back to top)
      controlEditorRight()
        .querySelectorOpt(".fb-control-delete")
        .foreach(el => if (isViewMode || isSepLabel) el.hide() else el.show())

      controlEditorRight()
        .querySelectorOpt(".fb-control-handle")
        .foreach(el => if (isViewMode) el.hide() else el.show())

      showAndPositionLeftOrRightEditor(controlEditorRight(), cell.width - controlEditorRight().outerWidth)

      // Top editor: append to `cell.el` when `isSepLabel` so badge text never pollutes `<label>`'s `innerText`/`innerHTML`
      (if (isSepLabel) cell.el else controlEl).append(controlEditorTop())
      controlEditorTop().toggleClass("fb-is-separate-label", isSepLabel)
      showAndPositionTopEditor(controlEditorTop(), 0)

      val badgeText =
        if (isSepLabel) {
          val suffix =
            controlEditorTop()
              .querySelectorOpt(".fb-separate-label-suffix")
              .flatMap(XFormsUI.getCurrentValue(_).toOption)
              .filter(_.nonEmpty)
              .getOrElse("label")
          s"$controlName ($suffix)"
        } else
          controlName

      controlEditorTop().childrenT.head.textContent = badgeText
    }

    // Cell/left editor
    if (! isViewMode) {
      val leftHost =
        firstControlElemWithNameOpt match {
          case Some((el, _, false)) => el
          case _                    => cell.el
        }
      leftHost.append(controlEditorLeft())
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

  def detachRightEditorIfInside(elem: html.Element): Unit =
    if (elem.contains(controlEditorRight())) {
      controlEditorRight().hide()
      elem.parentElementOpt.foreach(_.append(controlEditorRight()))
    }

  // Control actions
  ControlActionNames.foreach { actionName =>
    val actionEl = controlEditorRight().querySelectorOpt(s".fb-control-$actionName")
    actionEl.foreach(_.addEventListener("click", (event: dom.Event) => {
      event.preventDefault()
      event.stopPropagation()
      currentCellOpt.foreach { currentCell =>
        val targetElemOpt =
          currentCell.el.childrenT.find(elem =>
            ! controlEditors.contains(elem)          &&
            ! elem.hasClass("gu-transit")            &&
            ! elem.hasClass("fb-control-cell-badges") &&
            elem.hasAttribute("id")
          )

        targetElemOpt.foreach { targetElem =>
          val controlId = targetElem.id

          actionName match {
            case "delete"       => FormBuilderRpcClient.api.controlDelete       (controlId = controlId)
            case "edit-details" => FormBuilderRpcClient.api.controlEditDetails  (controlId = controlId)
            case "edit-items"   => FormBuilderRpcClient.api.controlEditItems    (controlId = controlId)
            case "label-left"   => FormBuilderRpcClient.api.controlMoveLabelLeft(controlId = controlId)
            case "label-top"    => FormBuilderRpcClient.api.controlMoveLabelTop (controlId = controlId)
          }
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
