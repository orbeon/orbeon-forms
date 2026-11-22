/**
  * Copyright (C) 2016 Orbeon, Inc.
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

import org.orbeon.facades.{Dragula, DragulaOptions, Drake}
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.facade.XBLCompanion
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventNames, XFormsXbl}
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.scalajs.js
import scala.scalajs.js.UndefOr
import scala.util.Try


// Companion for `fr:dnd-repeat`
object DndRepeat {

  val IsRepeatBeginEndSelector  = ".xforms-repeat-begin-end"
  val IsRepeatDelimiterSelector = ".xforms-repeat-delimiter"

  val IsNotGuSelector           = ":not(.gu-transit):not(.gu-mirror)"
  val IsDndItemSelector         = ".xforms-dnd-item"
  val IsDndMovesSelector        = ".xforms-dnd-moves"
  val IsDndLevelPrefix          = "xforms-dnd-level-"

  val FindDndLevelRe            = """^xforms-dnd-level-(\d+)$""".r

  private val DragThresholdPx            = 4.0
  private val AutoScrollEdgePx           = 50.0
  private val AutoScrollStepPx           = 10.0
  private val IsDropIndicatorClass       = "fr-dnd-drop-indicator"
  private val IsActiveDndItemSelector    = ".xforms-dnd-item:not(.xforms-dnd-mirror):not(.xforms-disabled)"
  private val IsMovableDndItemSelector   = ".xforms-dnd-item.xforms-dnd-moves:not(.xforms-dnd-mirror):not(.xforms-disabled)"
  private val InvalidInteractiveSelector = ".fr-attachment-remove, .fr-attachment-zoom, a, button, input, select, textarea"

  XFormsXbl.declareCompanion("fr|dnd-repeat", js.constructorOf[DndRepeatCompanion])

  private class DndRepeatCompanion(containerElem: html.Element) extends XBLCompanion {

    // --- Legacy Dragula mode state ---
    case class DragState(
      currentDragStartPrev     : dom.Element,
      currentDragStartPosition : Int,
      excludedTargets          : List[dom.Element]
    )

    private var dragState : Option[DragState] = None
    private var drake     : Option[Drake]     = None

    // --- Drop-indicator line mode state (matches Grid.scala / Form Builder blue bar) ---
    private case class LineDragState(
      draggedEl    : html.Element,
      handleEl     : html.Element,
      pointerId    : Double,
      startIndex   : Int,
      startClientX : Double,
      startClientY : Double,
      grabOffsetY  : Double
    )

    private case class DropTarget(
      boundaryY      : Double,
      left           : Double,
      width          : Double,
      targetIndexOpt : Option[Int]
    ) {
      def isOriginalPosition: Boolean = targetIndexOpt.isEmpty
    }

    private var useDropIndicator    : Boolean               = false
    private var lineDragState       : Option[LineDragState] = None
    private var isLineDragging      : Boolean               = false
    private var lastClientY         : Double                = 0.0
    private var dropTargets         : List[DropTarget]      = Nil
    private var activeDropTargetOpt : Option[DropTarget]    = None
    private var mirrorElOpt         : Option[html.Element]  = None
    private var indicatorElOpt      : Option[html.Element]  = None
    private var autoScrollDelta     : Double                = 0.0
    private var autoScrollRafOpt    : Option[Int]           = None

    private val onPointerDownFn        : js.Function1[dom.PointerEvent, Unit]  = onPointerDown(_)
    private val onPointerMoveFn        : js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
    private val onPointerUpFn          : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
    private val onLostPointerCaptureFn : js.Function1[dom.PointerEvent, Unit]  = onLostPointerCapture(_)
    private val onKeyDownFn            : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

    override def init(): Unit =
      if (containerElem.hasClass(IsDropIndicatorClass)) {
        useDropIndicator = true
        containerElem.addEventListener("pointerdown", onPointerDownFn)
      } else {
        useDropIndicator = false
        initDragula()
      }

    override def destroy(): Unit =
      if (useDropIndicator) {
        cleanupLineDrag()
        containerElem.removeEventListener("pointerdown", onPointerDownFn)
      } else {
        drake foreach (_.destroy())
        drake = None
      }

    private def orbeonRootEl: html.Element =
      dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

    private def onPointerDown(event: dom.PointerEvent): Unit =
      if (event.button == 0 && lineDragState.isEmpty) {
        for {
          target   <- event.targetOpt
          if target.closestOpt(InvalidInteractiveSelector).isEmpty
          itemEl   <- target.closestOpt(IsMovableDndItemSelector)
          if itemEl.parentElementOpt.contains(containerElem)
          handleEl <-
            if (itemEl.querySelectorOpt(".xforms-dnd-handle").isDefined)
              target.closestOpt(".xforms-dnd-handle").filter(h => itemEl.contains(h))
            else
              Some(itemEl)
        } {
          val iterations = containerElem.childrenT(IsActiveDndItemSelector).toList
          val startIndex = iterations.indexOf(itemEl)
          if (startIndex >= 0) {
            val itemRect = itemEl.getBoundingClientRect()
            lineDragState = Some(
              LineDragState(
                draggedEl    = itemEl,
                handleEl     = handleEl,
                pointerId    = event.pointerId,
                startIndex   = startIndex,
                startClientX = event.clientX,
                startClientY = event.clientY,
                grabOffsetY  = event.clientY - itemRect.top
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

    private def computeDropTargets(draggedIndex: Int): List[DropTarget] = {
      val iterations     = containerElem.childrenT(IsActiveDndItemSelector).toList
      val movableIndices = iterations.indices.filter(i => iterations(i).matches(IsMovableDndItemSelector))

      if (movableIndices.size < 2) {
        Nil
      } else {
        val firstMovable  = movableIndices.head
        val lastMovable   = movableIndices.last
        val containerRect = containerElem.getBoundingClientRect()
        val rects         = iterations.map(_.getBoundingClientRect())

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
            left           = containerRect.left,
            width          = containerRect.width,
            targetIndexOpt = targetIndexOpt
          )
        }.toList
      }
    }

    private def cloneItemForMirror(itemEl: html.Element): html.Element = {
      val clone = itemEl.cloneNode(deep = true).asInstanceOf[html.Element]
      clone.removeAttribute("id")
      clone.querySelectorAllT("[id]").foreach(_.removeAttribute("id"))

      val origInputs  = itemEl.querySelectorAllT("input")
      val cloneInputs = clone.querySelectorAllT("input")
      origInputs.zip(cloneInputs).foreach {
        case (orig: html.Input, copy: html.Input) =>
          copy.value   = orig.value
          copy.checked = orig.checked
        case _ =>
      }

      val origTextareas  = itemEl.querySelectorAllT("textarea")
      val cloneTextareas = clone.querySelectorAllT("textarea")
      origTextareas.zip(cloneTextareas).foreach {
        case (orig: html.TextArea, copy: html.TextArea) =>
          copy.value = orig.value
        case _ =>
      }

      val origSelects  = itemEl.querySelectorAllT("select")
      val cloneSelects = clone.querySelectorAllT("select")
      origSelects.zip(cloneSelects).foreach {
        case (orig: html.Select, copy: html.Select) =>
          copy.selectedIndex = orig.selectedIndex
        case _ =>
      }

      clone
    }

    private def startLineDragging(state: LineDragState): Unit = {
      dropTargets = computeDropTargets(state.startIndex)
      if (! dropTargets.exists(! _.isOriginalPosition)) {
        cleanupLineDrag()
      } else {
        isLineDragging = true

        Try(state.handleEl.setPointerCapture(state.pointerId))
        state.handleEl.addEventListener("lostpointercapture", onLostPointerCaptureFn)

        orbeonRootEl.classList.add("fr-dnd-dragging")
        state.draggedEl.classList.add("xforms-dnd-source")

        val itemRect = state.draggedEl.getBoundingClientRect()
        val mirror   = cloneItemForMirror(state.draggedEl)
        mirror.classList.remove("xforms-dnd-source")
        mirror.classList.add("xforms-dnd-mirror")
        mirror.style.width  = s"${itemRect.width}px"
        mirror.style.height = s"${itemRect.height}px"
        mirror.style.left   = s"${itemRect.left}px"
        mirror.style.top    = s"${lastClientY - state.grabOffsetY}px"
        containerElem.appendChild(mirror)
        mirrorElOpt = Some(mirror)

        val indicator       = dom.document.createDivElement
        indicator.className = "fr-dnd-drop-indicator-line"
        indicator.hide()
        orbeonRootEl.appendChild(indicator)
        indicatorElOpt = Some(indicator)
      }
    }

    private def updateMirrorAndIndicator(state: LineDragState, clientY: Double): Unit = {
      val itemRect = state.draggedEl.getBoundingClientRect()
      mirrorElOpt.foreach { mirrorEl =>
        mirrorEl.style.left = s"${itemRect.left}px"
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
            indicatorEl.classList.toggle("fr-dnd-drop-indicator-noop", target.isOriginalPosition)
          case None =>
            indicatorEl.hide()
        }
      }
    }

    private def findScrollContainerOpt: Option[html.Element] =
      containerElem
        .ancestorOrSelfElem(includeSelf = false)
        .takeWhile(el => (el ne dom.document.body) && (el ne dom.document.documentElement))
        .find { el =>
          el.scrollHeight > el.clientHeight + 1 && {
            val overflowY = dom.window.getComputedStyle(el).overflowY
            overflowY == "auto" || overflowY == "scroll"
          }
        }

    private def updateAutoScroll(clientY: Double): Unit =
      if (lineDragState.isDefined) {
        val (topEdge, bottomEdge) =
          findScrollContainerOpt match {
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
      } else {
        stopAutoScroll()
      }

    private def scheduleAutoScrollStep(): Unit = {
      val rafId = dom.window.requestAnimationFrame { _ =>
        autoScrollRafOpt = None
        if (isLineDragging && autoScrollDelta != 0.0) {
          lineDragState.foreach { state =>
            val scrolled =
              findScrollContainerOpt match {
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
              dropTargets = computeDropTargets(state.startIndex)
              updateMirrorAndIndicator(state, lastClientY)
            }
          }
          if (isLineDragging && autoScrollDelta != 0.0)
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
      lineDragState.filter(_.pointerId == event.pointerId).foreach { state =>
        lastClientY = event.clientY

        if (! isLineDragging) {
          val dist = Math.hypot(event.clientX - state.startClientX, event.clientY - state.startClientY)
          if (dist >= DragThresholdPx)
            startLineDragging(state)
        }

        if (isLineDragging) {
          event.preventDefault()
          updateAutoScroll(event.clientY)
          updateMirrorAndIndicator(state, event.clientY)
        }
      }

    private def onPointerUp(event: dom.PointerEvent): Unit =
      if (lineDragState.exists(_.pointerId == event.pointerId)) {
        val wasDragging = isLineDragging
        val stateOpt    = lineDragState
        val targetOpt   = activeDropTargetOpt

        cleanupLineDrag()

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
      if (lineDragState.exists(_.pointerId == event.pointerId))
        cleanupLineDrag()

    private def onKeyDown(event: dom.KeyboardEvent): Unit =
      if (event.key == "Escape") {
        event.preventDefault()
        event.stopPropagation()
        cleanupLineDrag()
      }

    private def cleanupLineDrag(): Unit = {
      stopAutoScroll()

      dom.document.removeEventListener("pointermove",   onPointerMoveFn)
      dom.document.removeEventListener("pointerup",     onPointerUpFn)
      dom.document.removeEventListener("pointercancel", onPointerUpFn)
      dom.document.removeEventListener("keydown",       onKeyDownFn, useCapture = true)

      lineDragState.foreach { state =>
        state.handleEl.removeEventListener("lostpointercapture", onLostPointerCaptureFn)
        Try {
          if (state.handleEl.hasPointerCapture(state.pointerId))
            state.handleEl.releasePointerCapture(state.pointerId)
        }
        state.draggedEl.classList.remove("xforms-dnd-source")
      }

      if (isLineDragging)
        orbeonRootEl.classList.remove("fr-dnd-dragging")

      mirrorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
      indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

      isLineDragging      = false
      lineDragState       = None
      dropTargets         = Nil
      activeDropTargetOpt = None
      mirrorElOpt         = None
      indicatorElOpt      = None
    }

    private def initDragula(): Unit = {
      val newDrake =
        Dragula(
          js.Array(),
          new DragulaOptions {

            override val mirrorContainer: UndefOr[html.Element] = containerElem

            override val direction: UndefOr[String] =
              if (
                containerElem.closestOpt(".fr-attachment-image-preview, .fr-dnd-horizontal").isDefined ||
                Option(containerElem.getAttribute("dnd-direction")).contains("horizontal")
              )
                "horizontal"
              else
                js.undefined

            override def isContainer(el: html.Element): UndefOr[Boolean] =
              el eq containerElem

            override def moves(el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element): UndefOr[Boolean] =
              el.matches(IsDndMovesSelector) && (
                el.querySelectorOpt(".xforms-dnd-handle").isEmpty ||
                ((handle ne null) && handle.closestOpt(".xforms-dnd-handle").isDefined)
              )

            override def invalid(el: html.Element, handle: html.Element): UndefOr[Boolean] =
              (handle ne null) && handle.closestOpt(InvalidInteractiveSelector).isDefined

            override def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element): UndefOr[Boolean] =
              (sibling ne null)                  &&
                sibling.matches(IsNotGuSelector) && (
                  sibling.matches(IsDndItemSelector)         || // regular case
                  sibling.matches(IsRepeatDelimiterSelector) || // between repeat items when dragging past midpoint
                  sibling.matches(IsRepeatBeginEndSelector)     // at the end of the repeat see https://github.com/orbeon/orbeon-forms/issues/6000
                )                                &&
                (! dragState.exists(_.excludedTargets.exists(_ eq sibling)))
          }
        )

      newDrake.onDrag((el: dom.Element, source: html.Element) => {

        def findElemLevel(el: dom.Element): Option[Int] =
          el.classList collectFirst { case FindDndLevelRe(level) => level.toInt }

        val startLevelOpt = findElemLevel(el)

        val nextDndItemIt = el.nextElementSiblings(IsDndItemSelector).iterator

        val excludedTargets = startLevelOpt match {
          case Some(startLevel) => nextDndItemIt.takeWhile(e => findElemLevel(e).exists(_ > startLevel)).toList
          case None             => Nil
        }

        dragState = Some(
          DragState(
            currentDragStartPrev     = el.previousElementSiblingT,
            currentDragStartPosition = el.previousElementSiblings(IsDndItemSelector).length,
            excludedTargets          = excludedTargets
          )
        )
      })

      newDrake.onDragend((_: html.Element) => {
        dragState = None
      })

      newDrake.onDrop((el: html.Element, _: html.Element, _: html.Element, _: html.Element) => {
        dragState foreach { dragState =>

          val dndEnd   = el.previousElementSiblings(IsDndItemSelector).length
          val repeatId = el.previousElementSiblings(IsRepeatBeginEndSelector).next().id.substring("repeat-begin-".length)

          val beforeEl = dragState.currentDragStartPrev
          val dndStart = dragState.currentDragStartPosition

          if (dndStart != dndEnd) {

            AjaxClient.ajaxResponseReceivedForCurrentEventQueueF("dndrepeat") foreach { _ =>
              beforeEl.insertAdjacentElement("afterend", el)
            }

            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = EventNames.XXFormsDnD,
                targetId   = repeatId,
                form       = Some(getXFormsFormOrThrow.elem),
                properties = Map(
                  "dnd-start" -> (dndStart + 1),
                  "dnd-end"   -> (dndEnd + 1)
                )
              )
            )
          }
        }
      })

      drake = Some(newDrake)
    }
  }
}
