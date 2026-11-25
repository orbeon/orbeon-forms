/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.xbl

import org.orbeon.fr.DropIndicatorDnD.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, AjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object ProcessEditorDnD {

  import Private.*

  def isDragging: Boolean = controller.isDragging

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0 && ! controller.isActiveOrPending) {
          for {
            handleEl     <- event.targetOpt.flatMap(_.closestOpt(GrabHandleSelector))
            rowEl        <- handleEl.closestOpt(".fr-process-iteration")
            listEl       <- rowEl.closestOpt(".fr-process-steps-list")
            editorEl     <- listEl.closestOpt(".xbl-fr-dialog-process-editor, .xbl-fr-process-editor")
            processIndex <- listEl.attValueOpt("data-orbeon-process-index").flatMap(_.trimAllToOpt)
            allRows       = rowsInList(listEl)
            draggedRow   <- allRows.find(_.el == rowEl)
            if canRowLeaveParent(draggedRow, allRows)
          } {
            event.preventDefault()
            controller.beginPending(
              event        = event,
              captureElOpt = Some(handleEl),
              state        = DragContext(editorEl, listEl, processIndex, draggedRow.rowIndex)
            )
          }
        }
      }
    )
  }

  private object Private {

    val AutoScrollEdgePx   = 40.0
    val AutoScrollStepPx   = 8.0
    val IndentStepPx       = 20.0
    val ClusterGapPx       = 10.0
    val ClusterSpreadStep  = 7.0
    val GrabHandleSelector = ".fr-process-steps-list .fr-process-iteration.fr-process-can-move .fr-process-grab"
    val ContainerStepTypes = Set("group", "condition", "else")

    case class DragContext(
      editorEl       : html.Element,
      listEl         : html.Element,
      processIndex   : String,
      sourceRowIndex : Int
    )

    case class ProcessRow(
      el               : html.Element,
      mainEl           : html.Element,
      contentEl        : html.Element,
      rowIndex         : Int,
      stepType         : String,
      level            : Int,
      canMove          : Boolean,
      treeParentRowOpt : Option[Int]
    )

    case class ProcessDropTarget(
      intoOpt            : Option[ProcessRow],
      afterRowIndexOpt   : Option[Int],
      level              : Int,
      boundaryY          : Double,
      anchorY            : Double,
      left               : Double,
      width              : Double,
      isOriginalPosition : Boolean = false
    ) {
      def toIndicatorLine: IndicatorLine =
        IndicatorLine(boundaryY, left, width, isOriginalPosition)
    }

    var dropTargets         : List[ProcessDropTarget]   = Nil
    var activeDropTargetOpt : Option[ProcessDropTarget] = None
    var ghostElOpt          : Option[html.Element]      = None
    var highlightedRowOpt   : Option[html.Element]      = None
    var dimmedElements      : List[html.Element]        = Nil

    val controller = new PointerDragController[DragContext](
      overlayAnchorEl     = _.listEl,
      scrollContainerOpt  = ctx => findScrollContainerOpt(ctx),
      autoScrollEdgePx    = _ => AutoScrollEdgePx,
      autoScrollStepPx    = _ => AutoScrollStepPx,
      suppressClickOnDrop = true,
      onStartDrag         = startDragging,
      onDragMove          = updateGhostAndIndicator,
      onScrolled          = refreshDropTargets,
      onDrop              = performDrop,
      onCleanup           = cleanupDrag
    )

    def canRowLeaveParent(row: ProcessRow, allRows: List[ProcessRow]): Boolean =
      row.stepType != "else" &&
      row.canMove            &&
      allRows.count(r => r.stepType != "else" && r.treeParentRowOpt == row.treeParentRowOpt) > 1

    def rowsInList(listEl: html.Element): List[ProcessRow] = {
      val rawElems =
        listEl
          .querySelectorAllT(".fr-process-iteration")
          .iterator
          .filter(_.isVisible)
          .toList

      var containerHeaderByLevel = Map.empty[Int, Int]
      var conditionHeaderByLevel = Map.empty[Int, Int]

      rawElems.flatMap { el =>
        for {
          rowIndex  <- el.attValueOpt("data-orbeon-row-index").flatMap(_.toIntOption)
          mainEl    <- el.querySelectorOpt(".fr-process-row-main")
          contentEl <- mainEl.querySelectorOpt(".fr-process-row-content")
        } yield {
          val stepType = el.attValueOpt("data-orbeon-step-type").flatMap(_.trimAllToOpt).getOrElse("action")
          val level    = el.attValueOpt("data-orbeon-level").flatMap(_.toIntOption).getOrElse(0)
          val canMove  = el.booleanAttValueOpt("data-orbeon-can-move").getOrElse(false)

          val treeParentRowOpt =
            if (stepType == "else")
              conditionHeaderByLevel.get(level)
            else if (level > 0)
              containerHeaderByLevel.get(level - 1)
            else
              None

          stepType match {
            case "condition" =>
              containerHeaderByLevel = containerHeaderByLevel.filter { case (lvl, _) => lvl < level } + (level -> rowIndex)
              conditionHeaderByLevel = conditionHeaderByLevel.filter { case (lvl, _) => lvl < level } + (level -> rowIndex)
            case "group" =>
              containerHeaderByLevel = containerHeaderByLevel.filter { case (lvl, _) => lvl < level } + (level -> rowIndex)
              conditionHeaderByLevel = conditionHeaderByLevel.filter { case (lvl, _) => lvl < level }
            case "else" =>
              containerHeaderByLevel = containerHeaderByLevel.filter { case (lvl, _) => lvl < level } + (level -> rowIndex)
              conditionHeaderByLevel = conditionHeaderByLevel.filter { case (lvl, _) => lvl < level }
            case _ =>
              containerHeaderByLevel = containerHeaderByLevel.filter { case (lvl, _) => lvl < level }
              conditionHeaderByLevel = conditionHeaderByLevel.filter { case (lvl, _) => lvl < level }
          }

          ProcessRow(
            el               = el,
            mainEl           = mainEl,
            contentEl        = contentEl,
            rowIndex         = rowIndex,
            stepType         = stepType,
            level            = level,
            canMove          = canMove,
            treeParentRowOpt = treeParentRowOpt
          )
        }
      }
    }

    def computeDropTargets(
      draggedRow : ProcessRow,
      allRows    : List[ProcessRow]
    ): List[ProcessDropTarget] =
      if (! canRowLeaveParent(draggedRow, allRows)) {
        Nil
      } else {
        val rowByIndex = allRows.map(r => r.rowIndex -> r).toMap
        val parentOf   = (idx: Int) => rowByIndex.get(idx).flatMap(_.treeParentRowOpt)

        val eligibleParents: List[Option[ProcessRow]] =
          None :: allRows.collect {
            case r
              if ContainerStepTypes(r.stepType) &&
                 ! isDescendantOrSelf(r.rowIndex, draggedRow.rowIndex, parentOf) =>
              Some(r)
          }

        val refRowOpt = allRows.headOption

        val rawTargets =
          eligibleParents.flatMap { intoOpt =>
            val intoIdxOpt   = intoOpt.map(_.rowIndex)
            val targetLevel  = intoOpt.map(_.level + 1).getOrElse(0)
            val children     = allRows.filter(r => r.stepType != "else" && r.treeParentRowOpt == intoIdxOpt)
            val isSameParent = intoIdxOpt == draggedRow.treeParentRowOpt
            val draggedIdx   = if (isSameParent) children.indexWhere(_.rowIndex == draggedRow.rowIndex) else -1

            val (slotLeft, slotWidth) =
              intoOpt.orElse(refRowOpt) match {
                case Some(refRow) =>
                  val mainRect = refRow.mainEl.getBoundingClientRect()
                  val left     = mainRect.left + targetLevel * IndentStepPx
                  val width    = Math.max(40.0, mainRect.width - 2.0 * targetLevel * IndentStepPx)
                  (left, width)
                case None =>
                  (0.0, 0.0)
              }

            val firstSlotOpt = {
              val firstBoundaryY =
                intoOpt match {
                  case Some(headerRow) =>
                    children
                      .headOption
                      .map(_.mainEl.getBoundingClientRect().top)
                      .getOrElse(headerRow.mainEl.getBoundingClientRect().bottom)
                  case None =>
                    allRows
                      .headOption
                      .map(_.mainEl.getBoundingClientRect().top)
                      .getOrElse(0.0)
                }

              Some(
                ProcessDropTarget(
                  intoOpt            = intoOpt,
                  afterRowIndexOpt   = None,
                  level              = targetLevel,
                  boundaryY          = firstBoundaryY,
                  anchorY            = firstBoundaryY,
                  left               = slotLeft,
                  width              = slotWidth,
                  isOriginalPosition = isSameParent && draggedIdx == 0
                )
              )
            }

            val afterSlots =
              children.indices.map { idx =>
                val child   = children(idx)
                val lastRow = lastDescendantOrSelf(child, allRows, (r: ProcessRow) => r.rowIndex, parentOf)
                val boundaryY =
                  if (idx + 1 < children.length) {
                    children(idx + 1).mainEl.getBoundingClientRect().top
                  } else if (targetLevel == 0) {
                    lastRow.mainEl.getBoundingClientRect().bottom + 2.0
                  } else {
                    lastRow
                      .mainEl
                      .querySelectorOpt(s".fr-process-box-slice[data-orbeon-body-level='$targetLevel']")
                      .map(_.getBoundingClientRect().bottom)
                      .getOrElse(lastRow.mainEl.getBoundingClientRect().bottom)
                  }

                ProcessDropTarget(
                  intoOpt            = intoOpt,
                  afterRowIndexOpt   = Some(child.rowIndex),
                  level              = targetLevel,
                  boundaryY          = boundaryY,
                  anchorY            = boundaryY,
                  left               = slotLeft,
                  width              = slotWidth,
                  isOriginalPosition = isSameParent && (idx == draggedIdx || idx == draggedIdx - 1)
                )
              }

            firstSlotOpt.toList ++ afterSlots
          }

        val sorted = rawTargets.sortBy(t => (t.boundaryY, -t.level))
        spreadClusterAnchors[ProcessDropTarget](
          sorted       = sorted,
          boundaryY    = _.boundaryY,
          withAnchorY  = (t, y) => t.copy(anchorY = y),
          clusterGapPx = ClusterGapPx,
          maxStepPx    = ClusterSpreadStep
        )
      }

    def stepGhostLabel(row: ProcessRow): (String, String) =
      row.stepType match {
        case "group" =>
          ("fa-object-group", "group")

        case "condition" =>
          val condOpt =
            row.el
              .querySelectorOpt(".fr-process-condition-input input")
              .collect { case inp: html.Input => inp.value }
              .flatMap(_.trimAllToOpt)
              .map(s => if (s.length > 32) s"${s.take(31)}…" else s)
          val label = condOpt.map(c => s"if ($c)").getOrElse("if")
          ("fa-code-fork", label)

        case _ =>
          val actionLabel =
            row.el
              .querySelectorOpt(".fr-process-action-select1 select")
              .collect {
                case sel: html.Select if sel.selectedIndex >= 0 && sel.selectedIndex < sel.options.length =>
                  sel.options(sel.selectedIndex).text
              }
              .flatMap(_.trimAllToOpt)
              .getOrElse("action")
          ("fa-cog", actionLabel)
      }

    def collectSubtreeDimmedElements(
      draggedRow : ProcessRow,
      allRows    : List[ProcessRow]
    ): List[html.Element] = {
      val rowByIndex  = allRows.map(r => r.rowIndex -> r).toMap
      val parentOf    = (idx: Int) => rowByIndex.get(idx).flatMap(_.treeParentRowOpt)
      val subtreeRows = allRows.filter(r => isDescendantOrSelf(r.rowIndex, draggedRow.rowIndex, parentOf))

      subtreeRows.flatMap { r =>
        val buttonsOpt  = r.el.querySelectorOpt(".fr-process-row-buttons")
        val innerSlices =
          r.mainEl
            .childrenT(".fr-process-box-slice")
            .filter(_.attValueOpt("data-orbeon-box-depth").flatMap(_.toIntOption).exists(_ >= draggedRow.level))
        r.contentEl :: buttonsOpt.toList ++ innerSlices
      }
    }

    def findScrollContainerOpt(ctx: DragContext): Option[html.Element] = {
      val hostParent = Option(overlayHostEl(ctx.listEl).parentElement)
      ctx.listEl
        .ancestorOrSelfElem(includeSelf = false)
        .takeWhile(el => ! hostParent.contains(el) && (el ne dom.document.body))
        .find(el => el.scrollHeight > el.clientHeight + 1)
    }

    def refreshDropTargets(ctx: DragContext): Unit = {
      val allRows = rowsInList(ctx.listEl)
      allRows.find(_.rowIndex == ctx.sourceRowIndex).foreach { draggedRow =>
        dropTargets = computeDropTargets(draggedRow, allRows)
      }
    }

    def startDragging(ctx: DragContext): Boolean = {
      val allRows = rowsInList(ctx.listEl)
      allRows.find(_.rowIndex == ctx.sourceRowIndex) match {
        case Some(draggedRow) =>
          dropTargets = computeDropTargets(draggedRow, allRows)
          if (! dropTargets.exists(! _.isOriginalPosition)) {
            false
          } else {
            dimmedElements = collectSubtreeDimmedElements(draggedRow, allRows)
            dimmedElements.foreach(_.classList.add(SourceClass))

            val (iconClass, labelText) = stepGhostLabel(draggedRow)
            ghostElOpt = Some(createPillGhost(overlayHostEl(ctx.listEl), iconClass, labelText))
            true
          }
        case None =>
          false
      }
    }

    def updateGhostAndIndicator(ctx: DragContext, clientX: Double, clientY: Double): Option[IndicatorLine] = {
      ghostElOpt.foreach(updatePillGhost(_, clientX, clientY))

      activeDropTargetOpt = dropTargets.minByOption(t => Math.abs(clientY - t.anchorY))

      highlightedRowOpt.foreach(_.classList.remove("fr-process-dnd-drop-into"))
      highlightedRowOpt = activeDropTargetOpt.filterNot(_.isOriginalPosition).flatMap(_.intoOpt).map(_.el)
      highlightedRowOpt.foreach(_.classList.add("fr-process-dnd-drop-into"))

      activeDropTargetOpt.map(_.toIndicatorLine)
    }

    def performDrop(ctx: DragContext): Unit =
      for {
        target <- activeDropTargetOpt
        if ! target.isOriginalPosition
      } {
        val intoIndex  = target.intoOpt.map(_.rowIndex.toString).getOrElse("")
        val afterIndex = target.afterRowIndexOpt.map(_.toString).getOrElse("")

        AjaxClient.fireEvent(
          AjaxEvent(
            eventName  = "fr-move-process-step",
            targetId   = ctx.editorEl.id,
            properties = Map[String, js.Any](
              "process-index" -> ctx.processIndex,
              "source-index"  -> ctx.sourceRowIndex.toString,
              "into-index"    -> intoIndex,
              "after-index"   -> afterIndex
            )
          )
        )
      }

    def cleanupDrag(ctx: DragContext, wasDragging: Boolean): Unit = {
      dimmedElements.foreach(_.classList.remove(SourceClass))
      highlightedRowOpt.foreach(_.classList.remove("fr-process-dnd-drop-into"))
      ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

      dropTargets         = Nil
      activeDropTargetOpt = None
      ghostElOpt          = None
      highlightedRowOpt   = None
      dimmedElements      = Nil
    }
  }
}
