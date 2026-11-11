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
package org.orbeon.builder

import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, AjaxEvent}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.annotation.tailrec
import scala.scalajs.js
import scala.util.Try


object ProcessEditorDnD {

  private val DragThresholdPx    = 4.0
  private val AutoScrollEdgePx   = 40.0
  private val AutoScrollStepPx   = 8.0
  private val IndentStepPx       = 20.0
  private val ClusterGapPx       = 10.0
  private val ClusterSpreadStep  = 7.0
  private val GrabHandleSelector = ".fb-dialog-process-editor .fb-process-iteration.fb-process-can-move .fb-process-grab"
  private val ContainerStepTypes = Set("group", "condition", "else")

  private case class DragContext(
    dialogEl       : html.Element,
    listEl         : html.Element,
    processIndex   : String,
    sourceRowIndex : Int
  )

  private case class ProcessRow(
    el               : html.Element,
    mainEl           : html.Element,
    contentEl        : html.Element,
    rowIndex         : Int,
    stepType         : String,
    level            : Int,
    canMove          : Boolean,
    treeParentRowOpt : Option[Int]
  )

  private case class ProcessDropTarget(
    intoOpt          : Option[ProcessRow],
    afterRowIndexOpt : Option[Int],
    level            : Int,
    boundaryY        : Double,
    anchorY          : Double,
    left             : Double,
    width            : Double
  )

  private var _isDragging: Boolean = false
  def isDragging: Boolean = _isDragging

  private var activePointerId     : Option[Double]            = None
  private var capturedHandleOpt   : Option[html.Element]      = None
  private var dragContextOpt      : Option[DragContext]       = None
  private var startClientX        : Double                    = 0.0
  private var startClientY        : Double                    = 0.0
  private var lastClientX         : Double                    = 0.0
  private var lastClientY         : Double                    = 0.0
  private var dropTargets         : List[ProcessDropTarget]   = Nil
  private var activeDropTargetOpt : Option[ProcessDropTarget] = None
  private var ghostElOpt          : Option[html.Element]      = None
  private var indicatorElOpt      : Option[html.Element]      = None
  private var highlightedRowOpt   : Option[html.Element]      = None
  private var dimmedElements      : List[html.Element]        = Nil
  private var autoScrollDelta     : Double                    = 0.0
  private var autoScrollRafOpt    : Option[Int]               = None

  private val onPointerMoveFn        : js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
  private val onPointerUpFn          : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
  private val onLostPointerCaptureFn : js.Function1[dom.PointerEvent, Unit]  = onLostPointerCapture(_)
  private val onKeyDownFn            : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0 && activePointerId.isEmpty) {
          for {
            handleEl     <- event.targetOpt.flatMap(_.closestOpt(GrabHandleSelector))
            rowEl        <- handleEl.closestOpt(".fb-process-iteration")
            listEl       <- rowEl.closestOpt(".fb-process-steps-list")
            dialogEl     <- listEl.closestOpt(".xforms-dialog.fb-dialog-process-editor")
            processIndex <- listEl.attValueOpt("data-orbeon-process-index").flatMap(_.trimAllToOpt)
            allRows       = rowsInList(listEl)
            draggedRow   <- allRows.find(_.el == rowEl)
            if canRowLeaveParent(draggedRow, allRows)
          } {
            event.preventDefault()

            activePointerId   = Some(event.pointerId)
            capturedHandleOpt = Some(handleEl)
            dragContextOpt    = Some(DragContext(dialogEl, listEl, processIndex, draggedRow.rowIndex))
            startClientX      = event.clientX
            startClientY      = event.clientY
            lastClientX       = event.clientX
            lastClientY       = event.clientY

            Try(handleEl.setPointerCapture(event.pointerId))

            dom.document.addEventListener("pointermove",   onPointerMoveFn)
            dom.document.addEventListener("pointerup",     onPointerUpFn)
            dom.document.addEventListener("pointercancel", onPointerUpFn)
            handleEl.addEventListener("lostpointercapture", onLostPointerCaptureFn)
            dom.document.addEventListener("keydown", onKeyDownFn, useCapture = true)
          }
        }
      }
    )
  }

  private def canRowLeaveParent(row: ProcessRow, allRows: List[ProcessRow]): Boolean =
    row.stepType != "else" &&
    row.canMove            &&
    allRows.count(r => r.stepType != "else" && r.treeParentRowOpt == row.treeParentRowOpt) > 1

  private def rowsInList(listEl: html.Element): List[ProcessRow] = {
    val rawElems =
      listEl
        .querySelectorAllT(".fb-process-iteration")
        .iterator
        .filter(_.isVisible)
        .toList

    var containerHeaderByLevel = Map.empty[Int, Int]
    var conditionHeaderByLevel = Map.empty[Int, Int]

    rawElems.flatMap { el =>
      for {
        rowIndex  <- el.attValueOpt("data-orbeon-row-index").flatMap(_.toIntOption)
        mainEl    <- el.querySelectorOpt(".fb-process-row-main")
        contentEl <- mainEl.querySelectorOpt(".fb-process-row-content")
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

  private def isDescendantOrSelf(
    candidateRowIndex : Int,
    ancestorRowIndex  : Int,
    rowByIndex        : Map[Int, ProcessRow]
  ): Boolean = {
    @tailrec
    def loop(currIdx: Int): Boolean =
      if (currIdx == ancestorRowIndex)
        true
      else
        rowByIndex.get(currIdx).flatMap(_.treeParentRowOpt) match {
          case Some(parentIdx) => loop(parentIdx)
          case None            => false
        }

    loop(candidateRowIndex)
  }

  private def computeDropTargets(
    draggedRow : ProcessRow,
    allRows    : List[ProcessRow]
  ): List[ProcessDropTarget] =
    if (! canRowLeaveParent(draggedRow, allRows)) {
      Nil
    } else {
      val rowByIndex = allRows.map(r => r.rowIndex -> r).toMap

      def lastDescendantOrSelfRow(row: ProcessRow): ProcessRow =
        allRows
          .filter(c => isDescendantOrSelf(c.rowIndex, row.rowIndex, rowByIndex))
          .lastOption
          .getOrElse(row)

      val eligibleParents: List[Option[ProcessRow]] =
        None :: allRows.collect {
          case r
            if ContainerStepTypes(r.stepType) &&
               ! isDescendantOrSelf(r.rowIndex, draggedRow.rowIndex, rowByIndex) =>
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

          val firstSlotOpt =
            if (isSameParent && draggedIdx == 0)
              None
            else {
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
                  intoOpt          = intoOpt,
                  afterRowIndexOpt = None,
                  level            = targetLevel,
                  boundaryY        = firstBoundaryY,
                  anchorY          = firstBoundaryY,
                  left             = slotLeft,
                  width            = slotWidth
                )
              )
            }

          val afterSlots =
            children.indices.flatMap { idx =>
              if (isSameParent && (idx == draggedIdx || idx == draggedIdx - 1)) {
                None
              } else {
                val child   = children(idx)
                val lastRow = lastDescendantOrSelfRow(child)
                val boundaryY =
                  if (idx + 1 < children.length) {
                    children(idx + 1).mainEl.getBoundingClientRect().top
                  } else if (targetLevel == 0) {
                    lastRow.mainEl.getBoundingClientRect().bottom + 2.0
                  } else {
                    lastRow
                      .mainEl
                      .querySelectorOpt(s".fb-process-box-slice[data-orbeon-body-level='$targetLevel']")
                      .map(_.getBoundingClientRect().bottom)
                      .getOrElse(lastRow.mainEl.getBoundingClientRect().bottom)
                  }

                Some(
                  ProcessDropTarget(
                    intoOpt          = intoOpt,
                    afterRowIndexOpt = Some(child.rowIndex),
                    level            = targetLevel,
                    boundaryY        = boundaryY,
                    anchorY          = boundaryY,
                    left             = slotLeft,
                    width            = slotWidth
                  )
                )
              }
            }

          firstSlotOpt.toList ++ afterSlots
        }

      val sorted = rawTargets.sortBy(t => (t.boundaryY, -t.level))
      spreadClusterAnchors(sorted)
    }

  private def spreadClusterAnchors(sorted: List[ProcessDropTarget]): List[ProcessDropTarget] =
    if (sorted.size <= 1) {
      sorted
    } else {
      val clusters = scala.collection.mutable.ListBuffer.empty[List[ProcessDropTarget]]
      var current  = List(sorted.head)

      sorted.tail.foreach { target =>
        if (target.boundaryY - current.last.boundaryY < ClusterGapPx)
          current = current :+ target
        else {
          clusters += current
          current = List(target)
        }
      }
      clusters += current

      val clusterList = clusters.toList
      clusterList.zipWithIndex.flatMap { case (cluster, cIdx) =>
        if (cluster.size <= 1) {
          cluster
        } else {
          val firstY = cluster.head.boundaryY
          val step =
            clusterList.lift(cIdx + 1) match {
              case Some(nextCluster) =>
                val maxSpan = Math.max(0.0, nextCluster.head.boundaryY - 8.0 - firstY)
                Math.min(ClusterSpreadStep, maxSpan / (cluster.size - 1))
              case None =>
                ClusterSpreadStep
            }

          cluster.zipWithIndex.map { case (target, idx) =>
            target.copy(anchorY = firstY + idx * step)
          }
        }
      }
    }

  private def stepGhostLabel(row: ProcessRow): (String, String) =
    row.stepType match {
      case "group" =>
        ("fa-object-group", "group")

      case "condition" =>
        val condOpt =
          row.el
            .querySelectorOpt(".fb-process-condition-input input")
            .collect { case inp: html.Input => inp.value }
            .flatMap(_.trimAllToOpt)
            .map { s =>
              if (s.length > 32) s"${s.take(31)}…" else s
            }
        val label = condOpt.map(c => s"if ($c)").getOrElse("if")
        ("fa-code-fork", label)

      case _ =>
        val actionLabel =
          row.el
            .querySelectorOpt(".fb-process-action-select1 select")
            .collect {
              case sel: html.Select if sel.selectedIndex >= 0 && sel.selectedIndex < sel.options.length =>
                sel.options(sel.selectedIndex).text
            }
            .flatMap(_.trimAllToOpt)
            .getOrElse("action")
        ("fa-cog", actionLabel)
    }

  private def orbeonRootEl: html.Element =
    dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

  private def collectSubtreeDimmedElements(
    draggedRow : ProcessRow,
    allRows    : List[ProcessRow]
  ): List[html.Element] = {
    val rowByIndex  = allRows.map(r => r.rowIndex -> r).toMap
    val subtreeRows = allRows.filter(r => isDescendantOrSelf(r.rowIndex, draggedRow.rowIndex, rowByIndex))

    subtreeRows.flatMap { r =>
      val buttonsOpt  = r.el.querySelectorOpt(".fb-process-row-buttons")
      val innerSlices =
        r.mainEl
          .childrenT(".fb-process-box-slice")
          .filter(_.attValueOpt("data-orbeon-box-depth").flatMap(_.toIntOption).exists(_ >= draggedRow.level))
      r.contentEl :: buttonsOpt.toList ++ innerSlices
    }
  }

  private def startDragging(ctx: DragContext): Unit = {
    val allRows = rowsInList(ctx.listEl)
    allRows.find(_.rowIndex == ctx.sourceRowIndex) match {
      case Some(draggedRow) =>
        dropTargets = computeDropTargets(draggedRow, allRows)
        if (dropTargets.isEmpty) {
          cleanupDrag()
        } else {
          _isDragging = true
          orbeonRootEl.classList.add("fb-dragging-container")

          dimmedElements = collectSubtreeDimmedElements(draggedRow, allRows)
          dimmedElements.foreach(_.classList.add("fb-dnd-source"))

          val (iconClass, labelText) = stepGhostLabel(draggedRow)

          val ghostEl        = dom.document.createDivElement
          ghostEl.className  = "fb-container-dnd-ghost"
          val iconEl         = dom.document.createElementT("i")
          iconEl.className   = s"fa fa-fw $iconClass"
          val textEl         = dom.document.createElementT("span")
          textEl.textContent = labelText
          ghostEl.appendChild(iconEl)
          ghostEl.appendChild(textEl)
          ctx.dialogEl.appendChild(ghostEl)
          ghostElOpt = Some(ghostEl)

          val indicatorEl       = dom.document.createDivElement
          indicatorEl.className = "fb-container-drop-indicator fb-process-drop-indicator"
          indicatorEl.hide()
          ctx.dialogEl.appendChild(indicatorEl)
          indicatorElOpt = Some(indicatorEl)
        }

      case None =>
        cleanupDrag()
    }
  }

  private def updateGhostAndIndicator(clientX: Double, clientY: Double): Unit = {
    ghostElOpt.foreach { ghostEl =>
      ghostEl.style.left = s"${clientX + 14.0}px"
      ghostEl.style.top  = s"${clientY + 14.0}px"
    }

    activeDropTargetOpt = dropTargets.minByOption(t => Math.abs(clientY - t.anchorY))

    highlightedRowOpt.foreach(_.classList.remove("fb-process-dnd-drop-into"))
    highlightedRowOpt = activeDropTargetOpt.flatMap(_.intoOpt).map(_.el)
    highlightedRowOpt.foreach(_.classList.add("fb-process-dnd-drop-into"))

    indicatorElOpt.foreach { indicatorEl =>
      activeDropTargetOpt match {
        case Some(target) =>
          indicatorEl.show()
          indicatorEl.style.left  = s"${target.left}px"
          indicatorEl.style.top   = s"${target.boundaryY - 1.5}px"
          indicatorEl.style.width = s"${target.width}px"
        case None =>
          indicatorEl.hide()
      }
    }
  }

  private def findScrollContainerOpt(ctx: DragContext): Option[html.Element] =
    ctx.listEl
      .ancestorOrSelfElem(includeSelf = false)
      .takeWhile(el => (el ne ctx.dialogEl.parentElement) && (el ne dom.document.body))
      .find(el => el.scrollHeight > el.clientHeight + 1)

  private def updateAutoScroll(clientY: Double): Unit =
    dragContextOpt.flatMap(findScrollContainerOpt) match {
      case Some(scrollEl) =>
        val rect = scrollEl.getBoundingClientRect()
        autoScrollDelta =
          if (clientY < rect.top + AutoScrollEdgePx)
            -AutoScrollStepPx
          else if (clientY > rect.bottom - AutoScrollEdgePx)
            AutoScrollStepPx
          else
            0.0

        if (autoScrollDelta != 0.0) {
          if (autoScrollRafOpt.isEmpty)
            scheduleAutoScrollStep()
        } else {
          stopAutoScroll()
        }

      case None =>
        stopAutoScroll()
    }

  private def scheduleAutoScrollStep(): Unit = {
    val rafId = dom.window.requestAnimationFrame { _ =>
      autoScrollRafOpt = None
      if (_isDragging && autoScrollDelta != 0.0) {
        dragContextOpt.foreach { ctx =>
          findScrollContainerOpt(ctx).foreach { scrollEl =>
            val prevScrollTop = scrollEl.scrollTop
            scrollEl.scrollTop = prevScrollTop + autoScrollDelta
            if (scrollEl.scrollTop != prevScrollTop) {
              val allRows = rowsInList(ctx.listEl)
              allRows.find(_.rowIndex == ctx.sourceRowIndex).foreach { draggedRow =>
                dropTargets = computeDropTargets(draggedRow, allRows)
                updateGhostAndIndicator(lastClientX, lastClientY)
              }
            }
          }
        }
        if (_isDragging && autoScrollDelta != 0.0)
          scheduleAutoScrollStep()
      }
    }
    autoScrollRafOpt = Some(rafId)
  }

  private def stopAutoScroll(): Unit = {
    autoScrollDelta = 0.0
    autoScrollRafOpt.foreach(dom.window.cancelAnimationFrame)
    autoScrollRafOpt = None
  }

  private def onPointerMove(event: dom.PointerEvent): Unit =
    if (activePointerId.contains(event.pointerId)) {
      dragContextOpt.foreach { ctx =>
        lastClientX = event.clientX
        lastClientY = event.clientY

        if (! _isDragging) {
          val dist = Math.hypot(event.clientX - startClientX, event.clientY - startClientY)
          if (dist >= DragThresholdPx)
            startDragging(ctx)
        }

        if (_isDragging) {
          event.preventDefault()
          updateAutoScroll(event.clientY)
          updateGhostAndIndicator(event.clientX, event.clientY)
        }
      }
    }

  private def suppressFollowingClick(): Unit = {
    lazy val listener: js.Function1[dom.MouseEvent, Unit] = (e: dom.MouseEvent) => {
      e.stopPropagation()
      e.preventDefault()
      dom.window.removeEventListener("click", listener, useCapture = true)
    }
    dom.window.addEventListener("click", listener, useCapture = true)
    dom.window.setTimeout(
      () => dom.window.removeEventListener("click", listener, useCapture = true),
      0
    )
  }

  private def onPointerUp(event: dom.PointerEvent): Unit =
    if (activePointerId.contains(event.pointerId)) {
      val wasDragging = _isDragging
      val ctxOpt      = dragContextOpt
      val targetOpt   = activeDropTargetOpt

      cleanupDrag()

      if (wasDragging && event.`type` == "pointerup") {
        suppressFollowingClick()

        for {
          ctx    <- ctxOpt
          target <- targetOpt
        } {
          val targetId =
            ctx.dialogEl
              .closestOpt(".xbl-fb-dialog-process-editor")
              .map(_.id)
              .filter(_.nonEmpty)
              .getOrElse(ctx.dialogEl.id.stripSuffix("≡dialog"))

          val intoIndex  = target.intoOpt.map(_.rowIndex.toString).getOrElse("")
          val afterIndex = target.afterRowIndexOpt.map(_.toString).getOrElse("")

          AjaxClient.fireEvent(
            AjaxEvent(
              eventName  = "fb-move-process-step",
              targetId   = targetId,
              properties = Map[String, js.Any](
                "process-index" -> ctx.processIndex,
                "source-index"  -> ctx.sourceRowIndex.toString,
                "into-index"    -> intoIndex,
                "after-index"   -> afterIndex
              )
            )
          )
        }
      }
    }

  private def onLostPointerCapture(event: dom.PointerEvent): Unit =
    if (activePointerId.contains(event.pointerId))
      cleanupDrag()

  private def onKeyDown(event: dom.KeyboardEvent): Unit =
    if (event.key == "Escape") {
      event.preventDefault()
      event.stopPropagation()
      cleanupDrag()
    }

  private def cleanupDrag(): Unit = {
    stopAutoScroll()

    dom.document.removeEventListener("pointermove",   onPointerMoveFn)
    dom.document.removeEventListener("pointerup",     onPointerUpFn)
    dom.document.removeEventListener("pointercancel", onPointerUpFn)
    dom.document.removeEventListener("keydown",       onKeyDownFn, useCapture = true)

    for {
      handleEl  <- capturedHandleOpt
      pointerId <- activePointerId
    } {
      handleEl.removeEventListener("lostpointercapture", onLostPointerCaptureFn)
      Try {
        if (handleEl.hasPointerCapture(pointerId))
          handleEl.releasePointerCapture(pointerId)
      }
    }

    _isDragging = false

    orbeonRootEl.classList.remove("fb-dragging-container")
    dimmedElements.foreach(_.classList.remove("fb-dnd-source"))
    highlightedRowOpt.foreach(_.classList.remove("fb-process-dnd-drop-into"))

    ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
    indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

    activePointerId     = None
    capturedHandleOpt   = None
    dragContextOpt      = None
    dropTargets         = Nil
    activeDropTargetOpt = None
    ghostElOpt          = None
    indicatorElOpt      = None
    highlightedRowOpt   = None
    dimmedElements      = Nil
  }
}
