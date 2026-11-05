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

  private val DragThresholdPx   = 4.0
  private val AutoScrollEdgePx  = 50.0
  private val AutoScrollStepPx  = 12.0

  private case class DropTarget(
    intoSectionOpt    : Option[html.Element],
    afterContainerOpt : Option[html.Element],
    top               : Double,
    left              : Double,
    width             : Double
  )

  private var _isDragging: Boolean = false
  def isDragging: Boolean = _isDragging

  private var sourceBlockOpt       : Option[Block]        = None
  private var startClientX         : Double               = 0.0
  private var startClientY         : Double               = 0.0
  private var lastClientX          : Double               = 0.0
  private var lastClientY          : Double               = 0.0
  private var dropTargets          : List[DropTarget]     = Nil
  private var activeDropTargetOpt  : Option[DropTarget]   = None
  private var ghostElOpt           : Option[html.Element] = None
  private var indicatorElOpt       : Option[html.Element] = None
  private var highlightedSectionOpt: Option[html.Element] = None
  private var autoScrollTimerOpt   : Option[Int]          = None

  private val onPointerMoveFn: js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
  private val onPointerUpFn  : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
  private val onKeyDownFn    : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0) {
          event.targetOpt
            .flatMap(_.closestOpt(".fb-container-move-handle"))
            .foreach { _ =>
              SectionGridEditor.currentSectionGridOpt.foreach { currentBlock =>
                event.preventDefault()
                sourceBlockOpt = Some(currentBlock)
                startClientX   = event.clientX
                startClientY   = event.clientY
                lastClientX    = event.clientX
                lastClientY    = event.clientY

                dom.document.addEventListener("pointermove",   onPointerMoveFn)
                dom.document.addEventListener("pointerup",     onPointerUpFn)
                dom.document.addEventListener("pointercancel", onPointerUpFn)
                dom.document.addEventListener("keydown",       onKeyDownFn)
              }
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

  private def orbeonRootEl: html.Element =
    dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

  private def startDragging(sourceBlock: Block): Unit = {
    _isDragging = true
    ControlEditor.mask()
    SectionGridEditor.sectionGridEditorContainer.hide()
    dom.document.querySelectorOpt(".fb-row-editor").foreach(_.hide())
    if (sourceBlock.el.matches(BlockCache.GridSelector))
      sourceBlock.el.childrenT(".fr-grid").foreach(_.classList.remove("fb-hover"))

    val rootEl = orbeonRootEl
    rootEl.classList.add("fb-dragging-container")
    sourceBlock.el.classList.add("fb-dnd-source")

    val (iconClass, labelText) = containerGhostLabel(sourceBlock.el)
    val ghostEl                = dom.document.createDivElement
    ghostEl.className          = "fb-container-dnd-ghost"
    val iconEl                 = dom.document.createElementT("i")
    iconEl.className           = s"fa fa-fw $iconClass"
    val textEl                 = dom.document.createElementT("span")
    textEl.textContent         = labelText
    ghostEl.appendChild(iconEl)
    ghostEl.appendChild(textEl)
    rootEl.appendChild(ghostEl)
    ghostElOpt = Some(ghostEl)

    val indicatorEl       = dom.document.createDivElement
    indicatorEl.className = "fb-container-drop-indicator"
    indicatorEl.hide()
    rootEl.appendChild(indicatorEl)
    indicatorElOpt = Some(indicatorEl)

    dropTargets = computeDropTargets(sourceBlock.el)
  }

  private def updateGhostAndIndicator(clientX: Double, clientY: Double): Unit = {
    ghostElOpt.foreach { ghostEl =>
      ghostEl.style.left = s"${clientX + 14.0}px"
      ghostEl.style.top  = s"${clientY + 14.0}px"
    }

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
  }

  private def updateAutoScroll(clientY: Double): Unit = {
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
                  sourceBlockOpt.foreach(b => dropTargets = computeDropTargets(b.el))
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
  }

  private def stopAutoScroll(): Unit = {
    autoScrollTimerOpt.foreach(dom.window.clearInterval)
    autoScrollTimerOpt = None
  }

  private def onPointerMove(event: dom.PointerEvent): Unit =
    sourceBlockOpt.foreach { sourceBlock =>
      lastClientX = event.clientX
      lastClientY = event.clientY

      if (! _isDragging) {
        val dist = Math.hypot(event.clientX - startClientX, event.clientY - startClientY)
        if (dist >= DragThresholdPx)
          startDragging(sourceBlock)
      }

      if (_isDragging) {
        event.preventDefault()
        updateAutoScroll(event.clientY)
        updateGhostAndIndicator(event.clientX, event.clientY)
      }
    }

  private def onPointerUp(event: dom.PointerEvent): Unit = {
    val wasDragging   = _isDragging
    val sourceOpt     = sourceBlockOpt
    val dropTargetOpt = activeDropTargetOpt

    cleanupDrag()

    if (wasDragging && event.`type` == "pointerup") {
      for {
        sourceBlock <- sourceOpt
        target      <- dropTargetOpt
      } locally {
        FormBuilderRpcClient.api.containerMove(
          containerId         = sourceBlock.el.id,
          intoSectionIdOpt    = target.intoSectionOpt.map(_.id),
          afterContainerIdOpt = target.afterContainerOpt.map(_.id)
        )
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
    sourceBlockOpt.foreach(_.el.classList.remove("fb-dnd-source"))
    highlightedSectionOpt.foreach { sec =>
      sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
    }

    ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
    indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

    sourceBlockOpt        = None
    dropTargets           = Nil
    activeDropTargetOpt   = None
    ghostElOpt            = None
    indicatorElOpt        = None
    highlightedSectionOpt = None
  }
}
