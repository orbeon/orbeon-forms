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

import org.orbeon.builder.rpc.FormBuilderRpcClient
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.oxf.fr.ControlOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.XFormsId
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js


object ContainerDnD {

  private val DragThresholdPx          = 4.0
  private val AutoScrollEdgePx         = 50.0
  private val AutoScrollStepPx         = 12.0
  private val OutlineAutoScrollEdgePx  = 36.0
  private val OutlineAutoScrollStepPx  = 8.0
  private val OutlineIndentStepPx      = 14.0

  private sealed trait DragSource
  private object DragSource {
    case class Canvas(block: Block)     extends DragSource
    case class Outline(row: OutlineRow) extends DragSource
  }

  private case class DropTarget(
    intoSectionOpt    : Option[html.Element],
    afterContainerOpt : Option[html.Element],
    top               : Double,
    left              : Double,
    width             : Double
  )

  private case class OutlineRow(
    el          : html.Element,
    containerId : String,
    parentIdOpt : Option[String],
    isSection   : Boolean,
    canMoveInto : Boolean,
    level       : Int
  )

  private case class OutlineDropTarget(
    intoSectionOpt      : Option[OutlineRow],
    afterContainerIdOpt : Option[String],
    level               : Int,
    boundaryTop         : Double,
    anchorTop           : Double,
    left                : Double,
    width               : Double
  )

  private var _isDragging: Boolean = false
  def isDragging: Boolean = _isDragging

  private var dragSourceOpt              : Option[DragSource]        = None
  private var startClientX               : Double                    = 0.0
  private var startClientY               : Double                    = 0.0
  private var lastClientX                : Double                    = 0.0
  private var lastClientY                : Double                    = 0.0
  private var dropTargets                : List[DropTarget]          = Nil
  private var activeDropTargetOpt        : Option[DropTarget]        = None
  private var outlineDropTargets         : List[OutlineDropTarget]   = Nil
  private var activeOutlineDropTargetOpt : Option[OutlineDropTarget] = None
  private var ghostElOpt                 : Option[html.Element]      = None
  private var indicatorElOpt             : Option[html.Element]      = None
  private var highlightedSectionOpt      : Option[html.Element]      = None
  private var highlightedOutlineRowOpt   : Option[html.Element]      = None
  private var dimmedOutlineRows          : List[html.Element]        = Nil
  private var autoScrollTimerOpt         : Option[Int]               = None

  private val onPointerMoveFn: js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
  private val onPointerUpFn  : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
  private val onKeyDownFn    : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0) {
          val canvasSourceOpt =
            event.targetOpt
              .flatMap(_.closestOpt(".fb-container-move-handle"))
              .flatMap(_ => SectionGridEditor.currentSectionGridOpt)
              .map { currentBlock =>
                event.preventDefault()
                DragSource.Canvas(currentBlock)
              }

          val outlineSourceOpt =
            if (canvasSourceOpt.isEmpty)
              event.targetOpt
                .flatMap(_.closestOpt(".fb-outline-body .fb-outline-row"))
                .filter(el => ! FormRunnerUtils.isViewMode(el))
                .flatMap { rowEl =>
                  val allRows = outlineRowsInPanel()
                  allRows
                    .find(_.el == rowEl)
                    .filter { row =>
                      row.el.getAttribute("data-orbeon-can-move") != "false" &&
                      allRows.count(_.parentIdOpt == row.parentIdOpt) > 1
                    }
                }
                .map(DragSource.Outline.apply)
            else
              None

          canvasSourceOpt.orElse(outlineSourceOpt).foreach { source =>
            dragSourceOpt = Some(source)
            startClientX  = event.clientX
            startClientY  = event.clientY
            lastClientX   = event.clientX
            lastClientY   = event.clientY

            dom.document.addEventListener("pointermove",   onPointerMoveFn)
            dom.document.addEventListener("pointerup",     onPointerUpFn)
            dom.document.addEventListener("pointercancel", onPointerUpFn)
            dom.document.addEventListener("keydown",       onKeyDownFn)
          }
        }
      }
    )
  }

  private def parentSectionOpt(el: html.Element): Option[html.Element] =
    el.ancestorOrSelfElem(BlockCache.SectionSelector, includeSelf = false).nextOption()

  private def isSectionOpen(sectionEl: html.Element): Boolean =
    sectionEl
      .childrenT(".fr-section-container")
      .headOption
      .flatMap(_.querySelectorOpt(".fr-section-content"))
      .exists(_.isVisible)

  private def isSectionTemplate(sectionEl: html.Element): Boolean =
    sectionEl
      .childrenT(".fr-section-container")
      .headOption
      .exists(_.queryNestedElems[html.Element](".fr-section-component", includeSelf = false).nonEmpty)

  private def editableContainersInForm(): List[html.Element] =
    dom.document
      .querySelectorAllT(s".fb-body ${BlockCache.SectionSelector}, .fb-body ${BlockCache.GridSelector}")
      .iterator
      .filter { el =>
        el.isVisible &&
        ! FormRunnerUtils.isViewMode(el) &&
        el.ancestorOrSelfElem(".fr-section-component", includeSelf = false).nextOption().isEmpty
      }
      .toList
      .distinctBy(el => XFormsId.getStaticIdFromId(el.id))

  private def computeDropTargets(draggedEl: html.Element): List[DropTarget] = {
    val isDraggingSection = draggedEl.matches(BlockCache.SectionSelector)
    val innerContainerOpt =
      if (isDraggingSection)
        draggedEl.childrenT(".fr-section-container").headOption
      else
        draggedEl.childrenT(".fr-grid").headOption

    val canLeaveCurrentParent = innerContainerOpt.exists(_.hasClass("fb-can-delete"))
    val sourceParentOpt       = parentSectionOpt(draggedEl)
    val allContainers         = editableContainersInForm()

    val eligibleParents: List[Option[html.Element]] =
      if (! canLeaveCurrentParent) {
        List(sourceParentOpt)
      } else {
        val topLevelOpt = if (isDraggingSection) List(None) else Nil
        val sectionParents =
          allContainers.collect {
            case sec
              if sec.matches(BlockCache.SectionSelector) &&
                 isSectionOpen(sec) &&
                 ! isSectionTemplate(sec) &&
                 ! sec.ancestorOrSelfElem(BlockCache.SectionSelector, includeSelf = true).contains(draggedEl) =>
              Some(sec)
          }
        topLevelOpt ++ sectionParents
      }

    eligibleParents.flatMap { intoOpt =>
      val children     = allContainers.filter(c => parentSectionOpt(c).map(_.id) == intoOpt.map(_.id))
      val isSameParent = intoOpt.map(_.id) == sourceParentOpt.map(_.id)
      val draggedIdx   = if (isSameParent) children.indexWhere(_.id == draggedEl.id) else -1

      val (slotLeft, slotWidth) =
        children.headOption match {
          case Some(firstChild) =>
            (Position.adjustedOffset(firstChild).left, firstChild.outerWidth)
          case None =>
            val contentEl =
              intoOpt
                .flatMap(_.querySelectorOpt(".fr-section-content"))
                .getOrElse(dom.document.querySelectorT(".fb-body"))
            (Position.adjustedOffset(contentEl).left + 10.0, Math.max(0.0, contentEl.outerWidth - 20.0))
        }

      val firstSlotOpt =
        if (isSameParent && draggedIdx == 0)
          None
        else {
          val firstTop =
            children.headOption match {
              case Some(firstChild) =>
                Position.adjustedOffset(firstChild).top
              case None =>
                val contentEl =
                  intoOpt
                    .flatMap(_.querySelectorOpt(".fr-section-content"))
                    .getOrElse(dom.document.querySelectorT(".fb-body"))
                Position.adjustedOffset(contentEl).top + 10.0
            }
          Some(DropTarget(intoOpt, None, firstTop, slotLeft, slotWidth))
        }

      val afterSlots =
        children.indices.flatMap { idx =>
          if (isSameParent && (idx == draggedIdx || idx == draggedIdx - 1)) {
            None
          } else {
            val child = children(idx)
            val boxEl =
              if (child.matches(BlockCache.SectionSelector))
                child.childrenT(".fr-section-container").headOption.getOrElse(child)
              else
                child
            val boxBottom = Position.adjustedOffset(boxEl).top + boxEl.outerHeight
            val afterTop =
              if (idx + 1 < children.length) {
                val nextChild = children(idx + 1)
                (boxBottom + Position.adjustedOffset(nextChild).top) / 2.0
              } else {
                boxBottom + 2.0
              }
            Some(DropTarget(intoOpt, Some(child), afterTop, slotLeft, slotWidth))
          }
        }

      firstSlotOpt.toList ++ afterSlots
    }
  }

  private def parseOutlineRowLevel(el: html.Element): Int =
    Option(el.getAttribute("data-orbeon-level"))
      .flatMap(_.toIntOption)
      .orElse {
        Option(el.getAttribute("class"))
          .toList
          .flatMap(_.splitTo[List]())
          .collectFirst { case s if s.startsWith("fb-outline-level-") => s.stripPrefix("fb-outline-level-").toIntOption }
          .flatten
      }
      .getOrElse(1)

  private def outlineRowsInPanel(): List[OutlineRow] = {
    val rawElems =
      dom.document
        .querySelectorAllT(".fb-outline-body .fb-outline-row")
        .iterator
        .filter(el => el.isVisible && ! FormRunnerUtils.isViewMode(el))
        .toList

    var sectionByLevel = Map.empty[Int, String]

    rawElems.flatMap { el =>
      Option(el.getAttribute("data-orbeon-container-id"))
        .flatMap(_.trimAllToOpt)
        .map { containerId =>
          val level       = parseOutlineRowLevel(el)
          val isSection   = el.hasClass("fb-outline-section") || el.getAttribute("data-orbeon-container-type") == "section"
          val canMoveInto = el.getAttribute("data-orbeon-can-move-into") == "true"
          val parentIdOpt =
            if (level > 1)
              sectionByLevel.get(level - 1).orElse(Option(el.getAttribute("data-orbeon-parent-id")).flatMap(_.trimAllToOpt))
            else
              None

          if (isSection)
            sectionByLevel = sectionByLevel.filter { case (lvl, _) => lvl < level } + (level -> containerId)

          OutlineRow(
            el          = el,
            containerId = containerId,
            parentIdOpt = parentIdOpt,
            isSection   = isSection,
            canMoveInto = canMoveInto,
            level       = level
          )
        }
    }
  }

  private def isOutlineDescendantOrSelf(
    candidateId : String,
    ancestorId  : String,
    rowById     : Map[String, OutlineRow]
  ): Boolean = {
    @scala.annotation.tailrec
    def loop(currId: String): Boolean =
      if (currId == ancestorId)
        true
      else
        rowById.get(currId).flatMap(_.parentIdOpt) match {
          case Some(parentId) => loop(parentId)
          case None           => false
        }

    loop(candidateId)
  }

  private def computeOutlineDropTargets(
    draggedRow : OutlineRow,
    allRows    : List[OutlineRow]
  ): List[OutlineDropTarget] = {
    val rowById               = allRows.map(r => r.containerId -> r).toMap
    val canLeaveCurrentParent =
      draggedRow.el.getAttribute("data-orbeon-can-move") != "false" &&
      allRows.count(_.parentIdOpt == draggedRow.parentIdOpt) > 1

    def lastDescendantOrSelfRow(row: OutlineRow): OutlineRow =
      allRows
        .filter(c => isOutlineDescendantOrSelf(c.containerId, row.containerId, rowById))
        .lastOption
        .getOrElse(row)

    val eligibleParents: List[Option[OutlineRow]] =
      if (! canLeaveCurrentParent) {
        List(draggedRow.parentIdOpt.flatMap(rowById.get))
      } else {
        val topLevelOpt = if (draggedRow.isSection) List(None) else Nil
        val sectionParents =
          allRows.collect {
            case sec
              if sec.isSection &&
                 sec.canMoveInto &&
                 ! isOutlineDescendantOrSelf(sec.containerId, draggedRow.containerId, rowById) =>
              Some(sec)
          }
        topLevelOpt ++ sectionParents
      }

    val rawTargets =
      eligibleParents.flatMap { intoOpt =>
        val intoIdOpt    = intoOpt.map(_.containerId)
        val targetLevel  = intoOpt.map(_.level + 1).getOrElse(1)
        val children     = allRows.filter(_.parentIdOpt == intoIdOpt)
        val isSameParent = intoIdOpt == draggedRow.parentIdOpt
        val draggedIdx   = if (isSameParent) children.indexWhere(_.containerId == draggedRow.containerId) else -1

        val refRowElOpt = intoOpt.orElse(allRows.headOption).map(_.el)
        val slotLeft =
          refRowElOpt
            .map(el => el.getOffset.left + (targetLevel - 1) * OutlineIndentStepPx)
            .getOrElse(0.0)
        val slotRight =
          refRowElOpt
            .map(el => el.getOffset.left + el.outerWidth)
            .getOrElse(0.0)
        val slotWidth = Math.max(20.0, slotRight - slotLeft)

        val firstSlotOpt =
          if (isSameParent && draggedIdx == 0)
            None
          else {
            val firstBoundaryTop =
              intoOpt match {
                case Some(parentSec) =>
                  val parentBottom = parentSec.el.getOffset.top + parentSec.el.outerHeight
                  children.headOption match {
                    case Some(firstChild) => (parentBottom + firstChild.el.getOffset.top) / 2.0
                    case None             => parentBottom
                  }
                case None =>
                  allRows.headOption.map(_.el.getOffset.top).getOrElse(0.0)
              }

            Some(
              OutlineDropTarget(
                intoSectionOpt      = intoOpt,
                afterContainerIdOpt = None,
                level               = targetLevel,
                boundaryTop         = firstBoundaryTop,
                anchorTop           = firstBoundaryTop,
                left                = slotLeft,
                width               = slotWidth
              )
            )
          }

        val afterSlots =
          children.indices.flatMap { idx =>
            if (isSameParent && (idx == draggedIdx || idx == draggedIdx - 1)) {
              None
            } else {
              val child         = children(idx)
              val lastRow       = lastDescendantOrSelfRow(child)
              val lastRowBottom = lastRow.el.getOffset.top + lastRow.el.outerHeight
              val nextRowOpt    = allRows.dropWhile(_.containerId != lastRow.containerId).drop(1).headOption
              val boundaryTop =
                nextRowOpt match {
                  case Some(nextRow) => (lastRowBottom + nextRow.el.getOffset.top) / 2.0
                  case None          => lastRowBottom + 1.0
                }

              Some(
                OutlineDropTarget(
                  intoSectionOpt      = intoOpt,
                  afterContainerIdOpt = Some(child.containerId),
                  level               = targetLevel,
                  boundaryTop         = boundaryTop,
                  anchorTop           = boundaryTop,
                  left                = slotLeft,
                  width               = slotWidth
                )
              )
            }
          }

        firstSlotOpt.toList ++ afterSlots
      }

    // When multiple targets share the same vertical boundary (e.g. at the end of a nested section),
    // spread their anchor Y coordinates from innermost (deepest level) to outermost (shallowest level).
    rawTargets
      .groupBy(t => Math.round(t.boundaryTop))
      .values
      .flatMap { group =>
        val sorted = group.sortBy(-_.level)
        if (sorted.size <= 1) {
          sorted
        } else {
          val spreadStep = 4.5
          sorted.zipWithIndex.map { case (target, idx) =>
            val offset = (idx - (sorted.size - 1) / 2.0) * spreadStep
            target.copy(anchorTop = target.boundaryTop + offset)
          }
        }
      }
      .toList
      .sortBy(t => (t.boundaryTop, -t.level))
  }

  private def containerGhostLabel(el: html.Element): (String, String) = {
    val isSection   = el.matches(BlockCache.SectionSelector)
    val controlName = ControlOps.controlNameFromIdOpt(el.id).getOrElse(el.id)
    if (isSection) {
      val titleOpt =
        el.querySelectorOpt(".fr-section-title .fr-section-label")
          .flatMap(labelEl => Option(labelEl.textContent))
          .flatMap(_.trimAllToOpt)
      val display = titleOpt.map(t => s"$t ($controlName)").getOrElse(controlName)
      ("fa-folder-open-o", display)
    } else {
      ("fa-th", controlName)
    }
  }

  private def outlineGhostLabel(rowEl: html.Element): (String, String) = {
    val iconClass =
      rowEl
        .querySelectorOpt(".fb-outline-item-icon")
        .flatMap { iconEl =>
          List("fa-puzzle-piece", "fa-repeat", "fa-columns", "fa-table").find(iconEl.classList.contains)
        }
        .getOrElse("fa-columns")

    val labelText =
      rowEl
        .querySelectorOpt(".fb-outline-item-content")
        .flatMap(el => Option(el.getAttribute("title")).flatMap(_.trimAllToOpt))
        .orElse(Option(rowEl.textContent).flatMap(_.trimAllToOpt))
        .getOrElse("")

    (iconClass, labelText)
  }

  private def orbeonRootEl: html.Element =
    dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

  private def startDragging(source: DragSource): Unit = {
    val hasValidTargets =
      source match {
        case DragSource.Canvas(sourceBlock) =>
          dropTargets = computeDropTargets(sourceBlock.el)
          dropTargets.nonEmpty

        case DragSource.Outline(sourceRow) =>
          val allRows      = outlineRowsInPanel()
          val refreshedRow = allRows.find(_.containerId == sourceRow.containerId).getOrElse(sourceRow)
          outlineDropTargets = computeOutlineDropTargets(refreshedRow, allRows)
          if (outlineDropTargets.nonEmpty) {
            val rowById = allRows.map(r => r.containerId -> r).toMap
            dimmedOutlineRows =
              allRows
                .filter(r => isOutlineDescendantOrSelf(r.containerId, refreshedRow.containerId, rowById))
                .map(_.el)
            true
          } else {
            false
          }
      }

    if (! hasValidTargets) {
      cleanupDrag()
    } else {
      _isDragging = true
      ControlEditor.mask()
      SectionGridEditor.sectionGridEditorContainer.hide()
      dom.document.querySelectorOpt(".fb-row-editor").foreach(_.hide())

      val rootEl = orbeonRootEl
      rootEl.classList.add("fb-dragging-container")

      val (iconClass, labelText) =
        source match {
          case DragSource.Canvas(sourceBlock) =>
            if (sourceBlock.el.matches(BlockCache.GridSelector))
              sourceBlock.el.childrenT(".fr-grid").foreach(_.classList.remove("fb-hover"))
            sourceBlock.el.classList.add("fb-dnd-source")
            containerGhostLabel(sourceBlock.el)

          case DragSource.Outline(sourceRow) =>
            dimmedOutlineRows.foreach(_.classList.add("fb-dnd-source"))
            outlineGhostLabel(sourceRow.el)
        }

      val ghostEl        = dom.document.createDivElement
      ghostEl.className  = "fb-container-dnd-ghost"
      val iconEl         = dom.document.createElementT("i")
      iconEl.className   = s"fa fa-fw $iconClass"
      val textEl         = dom.document.createElementT("span")
      textEl.textContent = labelText
      ghostEl.appendChild(iconEl)
      ghostEl.appendChild(textEl)
      rootEl.appendChild(ghostEl)
      ghostElOpt = Some(ghostEl)

      val indicatorEl       = dom.document.createDivElement
      indicatorEl.className = "fb-container-drop-indicator"
      indicatorEl.hide()
      rootEl.appendChild(indicatorEl)
      indicatorElOpt = Some(indicatorEl)
    }
  }

  private def updateGhostAndIndicator(clientX: Double, clientY: Double): Unit = {
    ghostElOpt.foreach { ghostEl =>
      ghostEl.style.left = s"${clientX + 14.0}px"
      ghostEl.style.top  = s"${clientY + 14.0}px"
    }

    dragSourceOpt match {
      case Some(DragSource.Canvas(_)) =>
        val pointerAdjustedTop = clientY + dom.window.pageYOffset + Position.scrollTop
        activeDropTargetOpt =
          if (dropTargets.isEmpty)
            None
          else
            Some(dropTargets.minBy(t => Math.abs(pointerAdjustedTop - t.top)))

        highlightedSectionOpt.foreach { sec =>
          sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
        }
        highlightedSectionOpt = activeDropTargetOpt.flatMap(_.intoSectionOpt)
        highlightedSectionOpt.foreach { sec =>
          sec.childrenT(".fr-section-container").foreach(_.classList.add("fb-dnd-drop-into"))
        }

        indicatorElOpt.foreach { indicatorEl =>
          activeDropTargetOpt match {
            case Some(target) =>
              indicatorEl.show()
              indicatorEl.setOffset(
                DomSupport.Offset(
                  left = target.left - Position.scrollLeft,
                  top  = target.top  - Position.scrollTop - 1.5
                )
              )
              indicatorEl.setWidth(target.width)
            case None =>
              indicatorEl.hide()
          }
        }

      case Some(DragSource.Outline(_)) =>
        val pointerDocX = clientX + dom.window.pageXOffset
        val pointerDocY = clientY + dom.window.pageYOffset

        activeOutlineDropTargetOpt =
          outlineDropTargets.minByOption { t =>
            Math.abs(pointerDocY - t.anchorTop) + Math.abs(pointerDocX - (t.left + 16.0)) * 0.15
          }

        highlightedOutlineRowOpt.foreach(_.classList.remove("fb-outline-dnd-drop-into"))
        highlightedOutlineRowOpt = activeOutlineDropTargetOpt.flatMap(_.intoSectionOpt).map(_.el)
        highlightedOutlineRowOpt.foreach(_.classList.add("fb-outline-dnd-drop-into"))

        indicatorElOpt.foreach { indicatorEl =>
          activeOutlineDropTargetOpt match {
            case Some(target) =>
              indicatorEl.show()
              indicatorEl.setOffset(
                DomSupport.Offset(
                  left = target.left,
                  top  = target.boundaryTop - 1.5
                )
              )
              indicatorEl.setWidth(target.width)
            case None =>
              indicatorEl.hide()
          }
        }

      case None =>
    }
  }

  private def updateAutoScroll(clientY: Double): Unit =
    dragSourceOpt match {
      case Some(DragSource.Canvas(sourceBlock)) =>
        dom.document.querySelectorOpt(".fb-main").foreach { fbMain =>
          val rect = fbMain.getBoundingClientRect()
          val delta =
            if (clientY < rect.top + AutoScrollEdgePx)
              -AutoScrollStepPx
            else if (clientY > rect.bottom - AutoScrollEdgePx)
              AutoScrollStepPx
            else
              0.0

          if (delta != 0.0) {
            if (autoScrollTimerOpt.isEmpty) {
              val timerId = dom.window.setInterval(
                () => {
                  dom.document.querySelectorOpt(".fb-main").foreach { mainEl =>
                    val prevScrollTop = mainEl.scrollTop
                    mainEl.scrollTop = prevScrollTop + delta
                    if (mainEl.scrollTop != prevScrollTop) {
                      dropTargets = computeDropTargets(sourceBlock.el)
                      updateGhostAndIndicator(lastClientX, lastClientY)
                    }
                  }
                },
                16.0
              )
              autoScrollTimerOpt = Some(timerId)
            }
          } else {
            stopAutoScroll()
          }
        }

      case Some(DragSource.Outline(sourceRow)) =>
        dom.document.querySelectorOpt(".fb-outline-body").foreach { outlineBody =>
          val rect = outlineBody.getBoundingClientRect()
          val delta =
            if (clientY < rect.top + OutlineAutoScrollEdgePx)
              -OutlineAutoScrollStepPx
            else if (clientY > rect.bottom - OutlineAutoScrollEdgePx)
              OutlineAutoScrollStepPx
            else
              0.0

          if (delta != 0.0) {
            if (autoScrollTimerOpt.isEmpty) {
              val timerId = dom.window.setInterval(
                () => {
                  dom.document.querySelectorOpt(".fb-outline-body").foreach { bodyEl =>
                    val prevScrollTop = bodyEl.scrollTop
                    bodyEl.scrollTop = prevScrollTop + delta
                    if (bodyEl.scrollTop != prevScrollTop) {
                      outlineDropTargets = computeOutlineDropTargets(sourceRow, outlineRowsInPanel())
                      updateGhostAndIndicator(lastClientX, lastClientY)
                    }
                  }
                },
                16.0
              )
              autoScrollTimerOpt = Some(timerId)
            }
          } else {
            stopAutoScroll()
          }
        }

      case None =>
        stopAutoScroll()
    }

  private def stopAutoScroll(): Unit = {
    autoScrollTimerOpt.foreach(dom.window.clearInterval)
    autoScrollTimerOpt = None
  }

  private def onPointerMove(event: dom.PointerEvent): Unit =
    dragSourceOpt.foreach { source =>
      lastClientX = event.clientX
      lastClientY = event.clientY

      if (! _isDragging) {
        val dist = Math.hypot(event.clientX - startClientX, event.clientY - startClientY)
        if (dist >= DragThresholdPx)
          startDragging(source)
      }

      if (_isDragging) {
        event.preventDefault()
        updateAutoScroll(event.clientY)
        updateGhostAndIndicator(event.clientX, event.clientY)
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

  private def onPointerUp(event: dom.PointerEvent): Unit = {
    val wasDragging          = _isDragging
    val sourceOpt            = dragSourceOpt
    val canvasDropTargetOpt  = activeDropTargetOpt
    val outlineDropTargetOpt = activeOutlineDropTargetOpt

    cleanupDrag()

    if (wasDragging && event.`type` == "pointerup") {
      suppressFollowingClick()

      sourceOpt match {
        case Some(DragSource.Canvas(sourceBlock)) =>
          canvasDropTargetOpt.foreach { target =>
            FormBuilderRpcClient.api.containerMove(
              containerId         = sourceBlock.el.id,
              intoSectionIdOpt    = target.intoSectionOpt.map(_.id),
              afterContainerIdOpt = target.afterContainerOpt.map(_.id)
            )
          }

        case Some(DragSource.Outline(sourceRow)) =>
          outlineDropTargetOpt.foreach { target =>
            FormBuilderRpcClient.api.containerMove(
              containerId         = sourceRow.containerId,
              intoSectionIdOpt    = target.intoSectionOpt.map(_.containerId),
              afterContainerIdOpt = target.afterContainerIdOpt
            )
          }

        case None =>
      }
    }
  }

  private def onKeyDown(event: dom.KeyboardEvent): Unit =
    if (event.key == "Escape") {
      event.preventDefault()
      cleanupDrag()
    }

  private def cleanupDrag(): Unit = {
    stopAutoScroll()

    dom.document.removeEventListener("pointermove",   onPointerMoveFn)
    dom.document.removeEventListener("pointerup",     onPointerUpFn)
    dom.document.removeEventListener("pointercancel", onPointerUpFn)
    dom.document.removeEventListener("keydown",       onKeyDownFn)

    if (_isDragging) {
      _isDragging = false
      ControlEditor.unmask()
    }

    orbeonRootEl.classList.remove("fb-dragging-container")
    dragSourceOpt.foreach {
      case DragSource.Canvas(sourceBlock) => sourceBlock.el.classList.remove("fb-dnd-source")
      case DragSource.Outline(_)          =>
    }
    dimmedOutlineRows.foreach(_.classList.remove("fb-dnd-source"))
    highlightedSectionOpt.foreach { sec =>
      sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
    }
    highlightedOutlineRowOpt.foreach(_.classList.remove("fb-outline-dnd-drop-into"))

    ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
    indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

    dragSourceOpt              = None
    dropTargets                = Nil
    activeDropTargetOpt        = None
    outlineDropTargets         = Nil
    activeOutlineDropTargetOpt = None
    ghostElOpt                 = None
    indicatorElOpt             = None
    highlightedSectionOpt      = None
    highlightedOutlineRowOpt   = None
    dimmedOutlineRows          = Nil
  }
}
