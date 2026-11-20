/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventNames, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.util.Try


object Grid {

  private val DragThresholdPx            = 4.0
  private val AutoScrollEdgePx           = 50.0
  private val AutoScrollStepPx           = 10.0
  private val IsIterationSelector        = ".fr-grid-repeat-iteration:not(.fr-grid-dnd-mirror):not(.xforms-disabled)"
  private val IsMovableIterationSelector = ".fr-grid-repeat-iteration:not(.fr-grid-dnd-mirror):not(.xforms-disabled):is(.can-move-up, .can-move-down)"
  private val IsRepeatBeginEndSelector   = ".xforms-repeat-begin-end"

  XFormsXbl.declareCompanion("fr|grid-multiple", js.constructorOf[GridCompanion])

  private class GridCompanion(containerElem: html.Element) extends XBLCompanion {

    private case class DragState(
      repeatGridElem : html.Element,
      draggedEl      : html.Element,
      handleEl       : html.Element,
      pointerId      : Double,
      startIndex     : Int,
      startClientX   : Double,
      startClientY   : Double,
      grabOffsetY    : Double
    )

    private case class DropTarget(
      boundaryY      : Double,
      left           : Double,
      width          : Double,
      targetIndexOpt : Option[Int]
    ) {
      def isOriginalPosition: Boolean = targetIndexOpt.isEmpty
    }

    private var repeatGridElemOpt   : Option[html.Element] = None
    private var dragState           : Option[DragState]    = None
    private var isDragging          : Boolean              = false
    private var lastClientY         : Double               = 0.0
    private var dropTargets         : List[DropTarget]     = Nil
    private var activeDropTargetOpt : Option[DropTarget]   = None
    private var mirrorElOpt         : Option[html.Element] = None
    private var indicatorElOpt      : Option[html.Element] = None
    private var autoScrollDelta     : Double               = 0.0
    private var autoScrollRafOpt    : Option[Int]          = None

    private val onPointerDownFn        : js.Function1[dom.PointerEvent, Unit]  = onPointerDown(_)
    private val onPointerMoveFn        : js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
    private val onPointerUpFn          : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
    private val onLostPointerCaptureFn : js.Function1[dom.PointerEvent, Unit]  = onLostPointerCapture(_)
    private val onKeyDownFn            : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

    override def init(): Unit = {
      GridMenus // initialize menus once

      containerElem.querySelectorOpt(":scope > .fr-grid.fr-repeat").foreach { repeatGridElem =>
        repeatGridElemOpt = Some(repeatGridElem)
        repeatGridElem.addEventListener("pointerdown", onPointerDownFn)
      }
    }

    override def destroy(): Unit = {
      cleanupDrag()
      repeatGridElemOpt.foreach(_.removeEventListener("pointerdown", onPointerDownFn))
      repeatGridElemOpt = None
    }

    private def orbeonRootEl: html.Element =
      dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

    private def onPointerDown(event: dom.PointerEvent): Unit =
      if (event.button == 0 && dragState.isEmpty) {
        for {
          repeatGridElem <- repeatGridElemOpt
          target         <- event.targetOpt
          if target.closestOpt(".fr-grid-dropdown-menu").isEmpty
          handleEl       <- target.closestOpt(".fr-repeat-column-left")
          rowEl          <- handleEl.closestOpt(".fr-grid-repeat-iteration")
          if rowEl.matches(IsMovableIterationSelector)
          if rowEl.parentElementOpt.contains(repeatGridElem)
        } {
          val iterations = repeatGridElem.childrenT(IsIterationSelector).toList
          val startIndex = iterations.indexOf(rowEl)
          if (startIndex >= 0) {
            val rowRect = rowEl.getBoundingClientRect()
            dragState = Some(
              DragState(
                repeatGridElem = repeatGridElem,
                draggedEl      = rowEl,
                handleEl       = handleEl,
                pointerId      = event.pointerId,
                startIndex     = startIndex,
                startClientX   = event.clientX,
                startClientY   = event.clientY,
                grabOffsetY    = event.clientY - rowRect.top
              )
            )
            lastClientY = event.clientY

            dom.document.addEventListener("pointermove",   onPointerMoveFn)
            dom.document.addEventListener("pointerup",     onPointerUpFn)
            dom.document.addEventListener("pointercancel", onPointerUpFn)
            dom.document.addEventListener("keydown",       onKeyDownFn, useCapture = true)
          }
        }
      }

    private def computeDropTargets(repeatGridElem: html.Element, draggedIndex: Int): List[DropTarget] = {
      val iterations     = repeatGridElem.childrenT(IsIterationSelector).toList
      val movableIndices = iterations.indices.filter(i => iterations(i).matches(IsMovableIterationSelector))

      if (movableIndices.size < 2) {
        Nil
      } else {
        val firstMovable = movableIndices.head
        val lastMovable  = movableIndices.last
        val gridRect     = repeatGridElem.getBoundingClientRect()
        val rects        = iterations.map(_.getBoundingClientRect())

        (firstMovable to (lastMovable + 1)).map { boundaryPos =>
          val boundaryY =
            if (boundaryPos == firstMovable)
              rects(firstMovable).top
            else if (boundaryPos == lastMovable + 1)
              rects(lastMovable).bottom
            else
              (rects(boundaryPos - 1).bottom + rects(boundaryPos).top) / 2.0

          val targetIndexOpt =
            if (boundaryPos == draggedIndex || boundaryPos == draggedIndex + 1)
              None
            else if (boundaryPos < draggedIndex)
              Some(boundaryPos)
            else
              Some(boundaryPos - 1)

          DropTarget(
            boundaryY      = boundaryY,
            left           = gridRect.left,
            width          = gridRect.width,
            targetIndexOpt = targetIndexOpt
          )
        }.toList
      }
    }

    private def cloneRowForMirror(rowEl: html.Element): html.Element = {
      val clone = rowEl.cloneNode(deep = true).asInstanceOf[html.Element]
      clone.removeAttribute("id")
      clone.querySelectorAllT("[id]").foreach(_.removeAttribute("id"))

      val origInputs  = rowEl.querySelectorAllT("input")
      val cloneInputs = clone.querySelectorAllT("input")
      origInputs.zip(cloneInputs).foreach {
        case (orig: html.Input, copy: html.Input) =>
          copy.value   = orig.value
          copy.checked = orig.checked
        case _ =>
      }

      val origTextareas  = rowEl.querySelectorAllT("textarea")
      val cloneTextareas = clone.querySelectorAllT("textarea")
      origTextareas.zip(cloneTextareas).foreach {
        case (orig: html.TextArea, copy: html.TextArea) =>
          copy.value = orig.value
        case _ =>
      }

      val origSelects  = rowEl.querySelectorAllT("select")
      val cloneSelects = clone.querySelectorAllT("select")
      origSelects.zip(cloneSelects).foreach {
        case (orig: html.Select, copy: html.Select) =>
          copy.selectedIndex = orig.selectedIndex
        case _ =>
      }

      clone
    }

    private def startDragging(state: DragState): Unit = {
      dropTargets = computeDropTargets(state.repeatGridElem, state.startIndex)
      if (! dropTargets.exists(! _.isOriginalPosition)) {
        cleanupDrag()
      } else {
        isDragging = true
        GridMenus.onDragStart()

        Try(state.handleEl.setPointerCapture(state.pointerId))
        state.handleEl.addEventListener("lostpointercapture", onLostPointerCaptureFn)

        orbeonRootEl.classList.add("fr-grid-dragging")
        state.draggedEl.classList.add("fr-grid-dnd-source")

        val rowRect = state.draggedEl.getBoundingClientRect()
        val mirror  = cloneRowForMirror(state.draggedEl)
        mirror.classList.remove("fr-grid-dnd-source")
        mirror.classList.add("fr-grid-dnd-mirror")
        mirror.style.width  = s"${rowRect.width}px"
        mirror.style.height = s"${rowRect.height}px"
        mirror.style.left   = s"${rowRect.left}px"
        mirror.style.top    = s"${lastClientY - state.grabOffsetY}px"
        state.repeatGridElem.appendChild(mirror)
        mirrorElOpt = Some(mirror)

        val indicator       = dom.document.createDivElement
        indicator.className = "fr-grid-drop-indicator"
        indicator.hide()
        orbeonRootEl.appendChild(indicator)
        indicatorElOpt = Some(indicator)
      }
    }

    private def updateMirrorAndIndicator(state: DragState, clientY: Double): Unit = {
      val rowRect = state.draggedEl.getBoundingClientRect()
      mirrorElOpt.foreach { mirrorEl =>
        mirrorEl.style.left = s"${rowRect.left}px"
        mirrorEl.style.top  = s"${clientY - state.grabOffsetY}px"
      }

      activeDropTargetOpt = dropTargets.minByOption(t => Math.abs(clientY - t.boundaryY))

      indicatorElOpt.foreach { indicatorEl =>
        activeDropTargetOpt match {
          case Some(target) =>
            indicatorEl.show()
            indicatorEl.style.left  = s"${target.left}px"
            indicatorEl.style.top   = s"${target.boundaryY - 1.5}px"
            indicatorEl.style.width = s"${target.width}px"
            indicatorEl.classList.toggle("fr-grid-drop-indicator-noop", target.isOriginalPosition)
          case None =>
            indicatorEl.hide()
        }
      }
    }

    private def findScrollContainerOpt(repeatGridElem: html.Element): Option[html.Element] =
      repeatGridElem
        .ancestorOrSelfElem(includeSelf = false)
        .takeWhile(el => (el ne dom.document.body) && (el ne dom.document.documentElement))
        .find { el =>
          el.scrollHeight > el.clientHeight + 1 && {
            val overflowY = dom.window.getComputedStyle(el).overflowY
            overflowY == "auto" || overflowY == "scroll"
          }
        }

    private def updateAutoScroll(clientY: Double): Unit =
      dragState match {
        case Some(state) =>
          val (topEdge, bottomEdge) =
            findScrollContainerOpt(state.repeatGridElem) match {
              case Some(scrollEl) =>
                val rect = scrollEl.getBoundingClientRect()
                (rect.top, rect.bottom)
              case None =>
                (0.0, dom.window.innerHeight)
            }

          autoScrollDelta =
            if (clientY < topEdge + AutoScrollEdgePx)
              -AutoScrollStepPx
            else if (clientY > bottomEdge - AutoScrollEdgePx)
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
        if (isDragging && autoScrollDelta != 0.0) {
          dragState.foreach { state =>
            val scrolled =
              findScrollContainerOpt(state.repeatGridElem) match {
                case Some(scrollEl) =>
                  val prev = scrollEl.scrollTop
                  scrollEl.scrollTop = prev + autoScrollDelta
                  scrollEl.scrollTop != prev
                case None =>
                  val prev = dom.window.pageYOffset
                  dom.window.scrollBy(0, autoScrollDelta.toInt)
                  dom.window.pageYOffset != prev
              }

            if (scrolled) {
              dropTargets = computeDropTargets(state.repeatGridElem, state.startIndex)
              updateMirrorAndIndicator(state, lastClientY)
            }
          }
          if (isDragging && autoScrollDelta != 0.0)
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
      dragState.filter(_.pointerId == event.pointerId).foreach { state =>
        lastClientY = event.clientY

        if (! isDragging) {
          val dist = Math.hypot(event.clientX - state.startClientX, event.clientY - state.startClientY)
          if (dist >= DragThresholdPx)
            startDragging(state)
        }

        if (isDragging) {
          event.preventDefault()
          updateAutoScroll(event.clientY)
          updateMirrorAndIndicator(state, event.clientY)
        }
      }

    private def onPointerUp(event: dom.PointerEvent): Unit =
      if (dragState.exists(_.pointerId == event.pointerId)) {
        val wasDragging = isDragging
        val stateOpt    = dragState
        val targetOpt   = activeDropTargetOpt

        cleanupDrag()

        if (wasDragging && event.`type` == "pointerup") {
          for {
            state  <- stateOpt
            target <- targetOpt
            dndEnd <- target.targetIndexOpt
          } {
            val repeatId =
              state.draggedEl.previousElementSiblings(IsRepeatBeginEndSelector)
                .nextOption()
                .map(_.id.substring("repeat-begin-".length))
                .getOrElse(throw new IllegalStateException)

            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = EventNames.XXFormsDnD,
                targetId   = repeatId,
                form       = Some(getXFormsFormOrThrow.elem),
                properties = Map(
                  "dnd-start" -> (state.startIndex + 1),
                  "dnd-end"   -> (dndEnd + 1)
                )
              )
            )
          }
        }
      }

    private def onLostPointerCapture(event: dom.PointerEvent): Unit =
      if (dragState.exists(_.pointerId == event.pointerId))
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

      dragState.foreach { state =>
        state.handleEl.removeEventListener("lostpointercapture", onLostPointerCaptureFn)
        Try {
          if (state.handleEl.hasPointerCapture(state.pointerId))
            state.handleEl.releasePointerCapture(state.pointerId)
        }
        state.draggedEl.classList.remove("fr-grid-dnd-source")
      }

      if (isDragging) {
        orbeonRootEl.classList.remove("fr-grid-dragging")
        GridMenus.onDragEnd()
      }

      mirrorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
      indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

      isDragging          = false
      dragState           = None
      dropTargets         = Nil
      activeDropTargetOpt = None
      mirrorElOpt         = None
      indicatorElOpt      = None
    }
  }
}

object GridMenus extends GridSectionMenus {
  override def componentName = "grid"
}