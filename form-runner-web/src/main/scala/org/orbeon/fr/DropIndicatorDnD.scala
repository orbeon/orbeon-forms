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
package org.orbeon.fr

import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{AjaxClient, AjaxEvent, EventNames}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import scala.util.Try


object DropIndicatorDnD {

  import Private.*

  val DragThresholdPx         = 4.0
  val DefaultAutoScrollEdgePx = 50.0
  val DefaultAutoScrollStepPx = 10.0

  val DraggingRootClass       = "fr-dnd-dragging"
  val DropIndicatorClass      = "fr-dnd-drop-indicator-line"
  val DropIndicatorNoopClass  = "fr-dnd-drop-indicator-noop"
  val GhostClass              = "fr-dnd-ghost"
  val SourceClass             = "fr-dnd-source"
  val MirrorClass             = "fr-dnd-mirror"
  val RepeatBeginEndSelector  = ".xforms-repeat-begin-end"

  case class IndicatorLine(
    boundaryY : Double,
    left      : Double,
    width     : Double,
    isNoop    : Boolean
  )

  case class LinearDropTarget(
    boundaryY      : Double,
    left           : Double,
    width          : Double,
    targetIndexOpt : Option[Int]
  ) {
    def isOriginalPosition: Boolean       = targetIndexOpt.isEmpty
    def toIndicatorLine   : IndicatorLine = IndicatorLine(boundaryY, left, width, isOriginalPosition)
  }

  def computeLinearDropTargetsFromRects(
    containerLeft      : Double,
    containerWidth     : Double,
    itemTopsAndBottoms : IndexedSeq[(Double, Double)],
    movableIndices     : IndexedSeq[Int],
    draggedIndex       : Int
  ): List[LinearDropTarget] =
    if (movableIndices.size < 2 || ! itemTopsAndBottoms.indices.contains(draggedIndex)) {
      Nil
    } else {
      val firstMovable = movableIndices.head
      val lastMovable  = movableIndices.last

      (firstMovable to (lastMovable + 1)).map { boundaryPos =>
        val boundaryY =
          if (boundaryPos == firstMovable)
            itemTopsAndBottoms(firstMovable)._1
          else if (boundaryPos == lastMovable + 1)
            itemTopsAndBottoms(lastMovable)._2
          else
            (itemTopsAndBottoms(boundaryPos - 1)._2 + itemTopsAndBottoms(boundaryPos)._1) / 2.0

        val targetIndexOpt =
          if (boundaryPos == draggedIndex || boundaryPos == draggedIndex + 1)
            None
          else if (boundaryPos < draggedIndex)
            Some(boundaryPos)
          else
            Some(boundaryPos - 1)

        LinearDropTarget(
          boundaryY      = boundaryY,
          left           = containerLeft,
          width          = containerWidth,
          targetIndexOpt = targetIndexOpt
        )
      }.toList
    }

  def computeLinearDropTargets(
    containerEl   : html.Element,
    iterations    : IndexedSeq[html.Element],
    isItemMovable : html.Element => Boolean,
    draggedIndex  : Int
  ): List[LinearDropTarget] = {
    val movableIndices = iterations.indices.filter(i => isItemMovable(iterations(i)))
    if (movableIndices.size < 2) {
      Nil
    } else {
      val containerRect = containerEl.getBoundingClientRect()
      val bounds        = iterations.map { el =>
        val r = el.getBoundingClientRect()
        (r.top, r.bottom)
      }
      computeLinearDropTargetsFromRects(
        containerLeft      = containerRect.left,
        containerWidth     = containerRect.width,
        itemTopsAndBottoms = bounds,
        movableIndices     = movableIndices,
        draggedIndex       = draggedIndex
      )
    }
  }

  def isDescendantOrSelf[Id](
    candidateId : Id,
    ancestorId  : Id,
    parentOf    : Id => Option[Id]
  ): Boolean = {
    @tailrec
    def loop(currId: Id): Boolean =
      if (currId == ancestorId)
        true
      else
        parentOf(currId) match {
          case Some(parentId) => loop(parentId)
          case None           => false
        }

    loop(candidateId)
  }

  def lastDescendantOrSelf[R, Id](
    row      : R,
    allRows  : List[R],
    idOf     : R => Id,
    parentOf : Id => Option[Id]
  ): R = {
    val rootId = idOf(row)
    allRows
      .filter(candidate => isDescendantOrSelf(idOf(candidate), rootId, parentOf))
      .lastOption
      .getOrElse(row)
  }

  // Spreads clustered drop-target anchor Y coordinates (e.g. at the bottom boundary of nested containers)
  // from innermost to outermost without colliding with the next cluster below.
  def spreadClusterAnchors[T](
    sorted       : List[T],
    boundaryY    : T => Double,
    withAnchorY  : (T, Double) => T,
    clusterGapPx : Double,
    maxStepPx    : Double
  ): List[T] =
    if (sorted.size <= 1) {
      sorted
    } else {
      val clusters = ListBuffer.empty[List[T]]
      var current  = List(sorted.head)

      sorted.tail.foreach { target =>
        if (boundaryY(target) - boundaryY(current.last) < clusterGapPx)
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
          val firstY = boundaryY(cluster.head)
          val step =
            clusterList.lift(cIdx + 1) match {
              case Some(nextCluster) =>
                val maxSpan = Math.max(0.0, boundaryY(nextCluster.head) - 8.0 - firstY)
                Math.min(maxStepPx, maxSpan / (cluster.size - 1))
              case None =>
                maxStepPx
            }

          cluster.zipWithIndex.map { case (target, idx) =>
            withAnchorY(target, firstY + idx * step)
          }
        }
      }
    }

  def orbeonRootEl: html.Element =
    dom.document.querySelectorOpt(".orbeon").getOrElse(dom.document.body)

  // Modal `<dialog>` elements render in the browser's top layer, so fixed-position overlays must be appended
  // inside the open `<dialog>` rather than `.orbeon` / `<body>` to paint above the dialog and its backdrop.
  def overlayHostEl(anchorEl: html.Element): html.Element =
    anchorEl.closestOpt("dialog, .xforms-dialog").getOrElse(orbeonRootEl)

  def findVerticalScrollContainerOpt(
    fromEl        : html.Element,
    stopBeforeOpt : Option[html.Element] = None
  ): Option[html.Element] =
    fromEl
      .ancestorOrSelfElem(includeSelf = false)
      .takeWhile { el =>
        (el ne dom.document.body)            &&
        (el ne dom.document.documentElement) &&
        ! stopBeforeOpt.contains(el)
      }
      .find { el =>
        el.scrollHeight > el.clientHeight + 1 && {
          val overflowY = dom.window.getComputedStyle(el).overflowY
          overflowY == "auto" || overflowY == "scroll"
        }
      }

  def createDropIndicator(hostEl: html.Element): html.Element = {
    val indicator       = dom.document.createDivElement
    indicator.className = DropIndicatorClass
    indicator.hide()
    hostEl.appendChild(indicator)
    indicator
  }

  def updateDropIndicator(indicatorEl: html.Element, lineOpt: Option[IndicatorLine]): Unit =
    lineOpt match {
      case Some(line) =>
        indicatorEl.show()
        indicatorEl.style.left  = s"${line.left}px"
        indicatorEl.style.top   = s"${line.boundaryY - 1.5}px"
        indicatorEl.style.width = s"${line.width}px"
        indicatorEl.classList.toggle(DropIndicatorNoopClass, line.isNoop)
      case None =>
        indicatorEl.hide()
    }

  def createPillGhost(hostEl: html.Element, iconClass: String, labelText: String): html.Element = {
    val ghostEl        = dom.document.createDivElement
    ghostEl.className  = GhostClass
    val iconEl         = dom.document.createElementT("i")
    iconEl.className   = s"fa fa-fw $iconClass"
    val textEl         = dom.document.createElementT("span")
    textEl.textContent = labelText
    ghostEl.appendChild(iconEl)
    ghostEl.appendChild(textEl)
    hostEl.appendChild(ghostEl)
    ghostEl
  }

  def updatePillGhost(ghostEl: html.Element, clientX: Double, clientY: Double): Unit = {
    ghostEl.style.left = s"${clientX + 14.0}px"
    ghostEl.style.top  = s"${clientY + 14.0}px"
  }

  def cloneElementForMirror(
    sourceEl       : html.Element,
    customizeClone : html.Element => Unit = _ => ()
  ): html.Element = {
    val clone = sourceEl.cloneNode(deep = true).asInstanceOf[html.Element]
    clone.removeAttribute("id")
    clone.querySelectorAllT("[id]").foreach(_.removeAttribute("id"))

    sourceEl.querySelectorAllT("input").zip(clone.querySelectorAllT("input")).foreach {
      case (orig: html.Input, copy: html.Input) =>
        copy.value   = orig.value
        copy.checked = orig.checked
      case _ =>
    }

    sourceEl.querySelectorAllT("textarea").zip(clone.querySelectorAllT("textarea")).foreach {
      case (orig: html.TextArea, copy: html.TextArea) =>
        copy.value = orig.value
      case _ =>
    }

    sourceEl.querySelectorAllT("select").zip(clone.querySelectorAllT("select")).foreach {
      case (orig: html.Select, copy: html.Select) =>
        copy.selectedIndex = orig.selectedIndex
      case _ =>
    }

    customizeClone(clone)
    clone
  }

  def suppressFollowingClick(): Unit = {
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

  def fireRepeatDndEvent(
    draggedEl  : html.Element,
    formElem   : html.Form,
    startIndex : Int,
    endIndex   : Int
  ): Unit = {
    val repeatId =
      draggedEl
        .previousElementSiblings(RepeatBeginEndSelector)
        .nextOption()
        .map(_.id.substring("repeat-begin-".length))
        .getOrElse(throw new IllegalStateException)

    AjaxClient.fireEvent(
      AjaxEvent(
        eventName  = EventNames.XXFormsDnD,
        targetId   = repeatId,
        form       = Some(formElem),
        properties = Map(
          "dnd-start" -> (startIndex + 1),
          "dnd-end"   -> (endIndex + 1)
        )
      )
    )
  }

  // Reusable pointer-drag session controller with 4px threshold, pointer capture, Escape cancellation,
  // requestAnimationFrame edge auto-scrolling, manual scroll sync, and top-layer drop-indicator management.
  final class PointerDragController[S](
    overlayAnchorEl     : S => html.Element,
    scrollContainerOpt  : S => Option[html.Element],
    allowWindowScroll   : S => Boolean              = (_: S) => false,
    autoScrollEdgePx    : S => Double               = (_: S) => DefaultAutoScrollEdgePx,
    autoScrollStepPx    : S => Double               = (_: S) => DefaultAutoScrollStepPx,
    suppressClickOnDrop : Boolean                   = false,
    onStartDrag         : S => Boolean,
    onDragMove          : (S, Double, Double) => Option[IndicatorLine],
    onScrolled          : S => Unit                 = (_: S) => (),
    onDrop              : S => Unit,
    onCleanup           : (S, Boolean) => Unit      = (_: S, _: Boolean) => ()
  ) {

    private var activePointerId     : Option[Double]       = None
    private var capturedHandleOpt   : Option[html.Element] = None
    private var pendingStateOpt     : Option[S]            = None
    private var _isDragging         : Boolean              = false
    private var startClientX        : Double               = 0.0
    private var startClientY        : Double               = 0.0
    private var lastClientX         : Double               = 0.0
    private var lastClientY         : Double               = 0.0
    private var indicatorElOpt      : Option[html.Element] = None
    private var autoScrollDelta     : Double               = 0.0
    private var autoScrollRafOpt    : Option[Int]          = None
    private var inProgrammaticScroll: Boolean              = false

    private val onPointerMoveFn        : js.Function1[dom.PointerEvent, Unit]  = handlePointerMove(_)
    private val onPointerUpFn          : js.Function1[dom.PointerEvent, Unit]  = handlePointerUp(_)
    private val onLostPointerCaptureFn : js.Function1[dom.PointerEvent, Unit]  = handleLostPointerCapture(_)
    private val onKeyDownFn            : js.Function1[dom.KeyboardEvent, Unit] = handleKeyDown(_)
    private val onScrollFn             : js.Function1[dom.Event, Unit]         = handleScroll(_)

    def isDragging: Boolean = _isDragging
    def isActiveOrPending: Boolean = pendingStateOpt.isDefined

    def beginPending(
      event        : dom.PointerEvent,
      captureElOpt : Option[html.Element],
      state        : S
    ): Unit =
      if (event.button == 0 && pendingStateOpt.isEmpty) {
        activePointerId   = Some(event.pointerId)
        capturedHandleOpt = captureElOpt
        pendingStateOpt   = Some(state)
        startClientX      = event.clientX
        startClientY      = event.clientY
        lastClientX       = event.clientX
        lastClientY       = event.clientY

        dom.document.addEventListener("pointermove",   onPointerMoveFn)
        dom.document.addEventListener("pointerup",     onPointerUpFn)
        dom.document.addEventListener("pointercancel", onPointerUpFn)
        dom.document.addEventListener("keydown",       onKeyDownFn, useCapture = true)
      }

    def cancel(): Unit =
      pendingStateOpt.foreach { state =>
        val wasDragging = _isDragging
        teardownInternal()
        onCleanup(state, wasDragging)
      }

    private def handlePointerMove(event: dom.PointerEvent): Unit =
      if (activePointerId.contains(event.pointerId)) {
        pendingStateOpt.foreach { state =>
          lastClientX = event.clientX
          lastClientY = event.clientY

          if (! _isDragging) {
            val dist = Math.hypot(event.clientX - startClientX, event.clientY - startClientY)
            if (dist >= DragThresholdPx) {
              if (! onStartDrag(state)) {
                cancel()
              } else {
                _isDragging = true
                for {
                  handleEl  <- capturedHandleOpt
                  pointerId <- activePointerId
                } {
                  Try(handleEl.setPointerCapture(pointerId))
                  handleEl.addEventListener("lostpointercapture", onLostPointerCaptureFn)
                }
                orbeonRootEl.classList.add(DraggingRootClass)
                indicatorElOpt = Some(createDropIndicator(overlayHostEl(overlayAnchorEl(state))))
                dom.document.addEventListener("scroll", onScrollFn, useCapture = true)
              }
            }
          }

          if (_isDragging) {
            event.preventDefault()
            updateAutoScroll(state, event.clientY)
            refreshDragPosition(state, event.clientX, event.clientY)
          }
        }
      }

    private def refreshDragPosition(state: S, clientX: Double, clientY: Double): Unit = {
      val lineOpt = onDragMove(state, clientX, clientY)
      indicatorElOpt.foreach(updateDropIndicator(_, lineOpt))
    }

    private def handleScroll(event: dom.Event): Unit =
      if (_isDragging && ! inProgrammaticScroll) {
        pendingStateOpt.foreach { state =>
          onScrolled(state)
          refreshDragPosition(state, lastClientX, lastClientY)
        }
      }

    private def updateAutoScroll(state: S, clientY: Double): Unit = {
      val edgePx = autoScrollEdgePx(state)
      val stepPx = autoScrollStepPx(state)

      val boundsOpt: Option[(Double, Double)] =
        scrollContainerOpt(state) match {
          case Some(scrollEl) =>
            val rect = scrollEl.getBoundingClientRect()
            Some((rect.top, rect.bottom))
          case None if allowWindowScroll(state) =>
            Some((0.0, dom.window.innerHeight))
          case None =>
            None
        }

      autoScrollDelta =
        boundsOpt match {
          case Some((topEdge, bottomEdge)) =>
            if (clientY < topEdge + edgePx)
              -stepPx
            else if (clientY > bottomEdge - edgePx)
              stepPx
            else
              0.0
          case None =>
            0.0
        }

      if (autoScrollDelta != 0.0) {
        if (autoScrollRafOpt.isEmpty)
          scheduleAutoScrollStep()
      } else {
        stopAutoScroll()
      }
    }

    private def scheduleAutoScrollStep(): Unit = {
      val rafId = dom.window.requestAnimationFrame { _ =>
        autoScrollRafOpt = None
        if (_isDragging && autoScrollDelta != 0.0) {
          pendingStateOpt.foreach { state =>
            inProgrammaticScroll = true
            val scrolled =
              try {
                scrollContainerOpt(state) match {
                  case Some(scrollEl) =>
                    val prev = scrollEl.scrollTop
                    scrollEl.scrollTop = prev + autoScrollDelta
                    scrollEl.scrollTop != prev
                  case None if allowWindowScroll(state) =>
                    val prev = dom.window.pageYOffset
                    dom.window.scrollBy(0, autoScrollDelta.toInt)
                    dom.window.pageYOffset != prev
                  case None =>
                    false
                }
              } finally {
                inProgrammaticScroll = false
              }

            if (scrolled) {
              onScrolled(state)
              refreshDragPosition(state, lastClientX, lastClientY)
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

    private def handlePointerUp(event: dom.PointerEvent): Unit =
      if (activePointerId.contains(event.pointerId)) {
        val wasDragging = _isDragging
        val stateOpt    = pendingStateOpt
        val isDrop      = wasDragging && event.`type` == "pointerup"

        teardownInternal()

        stateOpt.foreach { state =>
          try {
            if (isDrop) {
              if (suppressClickOnDrop)
                suppressFollowingClick()
              onDrop(state)
            }
          } finally {
            onCleanup(state, wasDragging)
          }
        }
      }

    private def handleLostPointerCapture(event: dom.PointerEvent): Unit =
      if (activePointerId.contains(event.pointerId))
        cancel()

    private def handleKeyDown(event: dom.KeyboardEvent): Unit =
      if (event.key == "Escape") {
        event.preventDefault()
        event.stopPropagation()
        cancel()
      }

    private def teardownInternal(): Unit = {
      stopAutoScroll()

      dom.document.removeEventListener("pointermove",   onPointerMoveFn)
      dom.document.removeEventListener("pointerup",     onPointerUpFn)
      dom.document.removeEventListener("pointercancel", onPointerUpFn)
      dom.document.removeEventListener("keydown",       onKeyDownFn, useCapture = true)
      dom.document.removeEventListener("scroll",        onScrollFn,  useCapture = true)

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

      if (_isDragging)
        orbeonRootEl.classList.remove(DraggingRootClass)

      indicatorElOpt.foreach(removeElement)

      _isDragging       = false
      activePointerId   = None
      capturedHandleOpt = None
      pendingStateOpt   = None
      indicatorElOpt    = None
    }
  }

  // Shared 1D vertical repeat drag-and-drop implementation used by `fr:dnd-repeat` and repeated `fr:grid`.
  final class RepeatDropIndicatorDnD(
    repeatContainerOpt : () => Option[html.Element],
    formElem           : () => html.Form,
    itemSelector       : String,
    isItemMovable      : html.Element => Boolean,
    resolveHandleOpt   : (html.Element, html.Element) => Option[html.Element],
    customizeMirror    : (html.Element, html.Element) => Unit = (_, _) => (),
    onDragStartHook    : () => Unit                           = () => (),
    onDragEndHook      : () => Unit                           = () => ()
  ) {

    private case class RepeatDragState(
      containerEl         : html.Element,
      draggedEl           : html.Element,
      startIndex          : Int,
      grabOffsetY         : Double,
      var dropTargets     : List[LinearDropTarget]     = Nil,
      var activeTargetOpt : Option[LinearDropTarget]   = None,
      var mirrorElOpt     : Option[html.Element]       = None
    )

    private val onPointerDownFn: js.Function1[dom.PointerEvent, Unit] = handlePointerDown(_)

    private val controller = new PointerDragController[RepeatDragState](
      overlayAnchorEl    = _.containerEl,
      scrollContainerOpt = s => findVerticalScrollContainerOpt(s.containerEl),
      allowWindowScroll  = _ => true,
      onStartDrag        = startRepeatDrag,
      onDragMove         = updateRepeatDrag,
      onScrolled         = s => s.dropTargets = computeTargets(s),
      onDrop             = performRepeatDrop,
      onCleanup          = cleanupRepeatDrag
    )

    def attach(elem: html.Element): Unit =
      elem.addEventListener("pointerdown", onPointerDownFn)

    def detach(elem: html.Element): Unit = {
      controller.cancel()
      elem.removeEventListener("pointerdown", onPointerDownFn)
    }

    private def handlePointerDown(event: dom.PointerEvent): Unit =
      if (event.button == 0 && ! controller.isActiveOrPending) {
        for {
          containerEl <- repeatContainerOpt()
          target      <- event.targetOpt
          itemEl      <- target.closestOpt(itemSelector)
          if isItemMovable(itemEl)
          if itemEl.parentElementOpt.contains(containerEl)
          handleEl    <- resolveHandleOpt(target, itemEl)
        } {
          val iterations = containerEl.childrenT(itemSelector).toVector
          val startIndex = iterations.indexOf(itemEl)
          if (startIndex >= 0) {
            val itemRect = itemEl.getBoundingClientRect()
            controller.beginPending(
              event        = event,
              captureElOpt = Some(handleEl),
              state        = RepeatDragState(
                containerEl = containerEl,
                draggedEl   = itemEl,
                startIndex  = startIndex,
                grabOffsetY = event.clientY - itemRect.top
              )
            )
          }
        }
      }

    private def computeTargets(state: RepeatDragState): List[LinearDropTarget] =
      computeLinearDropTargets(
        containerEl   = state.containerEl,
        iterations    = state.containerEl.childrenT(itemSelector).toVector,
        isItemMovable = isItemMovable,
        draggedIndex  = state.startIndex
      )

    private def startRepeatDrag(state: RepeatDragState): Boolean = {
      state.dropTargets = computeTargets(state)
      if (! state.dropTargets.exists(! _.isOriginalPosition)) {
        false
      } else {
        onDragStartHook()
        state.draggedEl.classList.add(SourceClass)

        val itemRect = state.draggedEl.getBoundingClientRect()
        val mirror   = cloneElementForMirror(state.draggedEl, clone => customizeMirror(state.draggedEl, clone))
        mirror.classList.remove(SourceClass)
        mirror.classList.add(MirrorClass)
        mirror.style.width  = s"${itemRect.width}px"
        mirror.style.height = s"${itemRect.height}px"
        mirror.style.left   = s"${itemRect.left}px"
        mirror.style.top    = s"${itemRect.top}px"
        state.containerEl.appendChild(mirror)
        state.mirrorElOpt = Some(mirror)
        true
      }
    }

    private def updateRepeatDrag(state: RepeatDragState, clientX: Double, clientY: Double): Option[IndicatorLine] = {
      val itemRect = state.draggedEl.getBoundingClientRect()
      state.mirrorElOpt.foreach { mirrorEl =>
        mirrorEl.style.left = s"${itemRect.left}px"
        mirrorEl.style.top  = s"${clientY - state.grabOffsetY}px"
      }

      state.activeTargetOpt = state.dropTargets.minByOption(t => Math.abs(clientY - t.boundaryY))
      state.activeTargetOpt.map(_.toIndicatorLine)
    }

    private def performRepeatDrop(state: RepeatDragState): Unit =
      for {
        target <- state.activeTargetOpt
        dndEnd <- target.targetIndexOpt
      } {
        fireRepeatDndEvent(
          draggedEl  = state.draggedEl,
          formElem   = formElem(),
          startIndex = state.startIndex,
          endIndex   = dndEnd
        )
      }

    private def cleanupRepeatDrag(state: RepeatDragState, wasDragging: Boolean): Unit = {
      state.draggedEl.classList.remove(SourceClass)
      state.mirrorElOpt.foreach(removeElement)
      state.mirrorElOpt     = None
      state.dropTargets     = Nil
      state.activeTargetOpt = None
      if (wasDragging)
        onDragEndHook()
    }
  }

  private object Private {
    def removeElement(el: html.Element): Unit =
      Option(el.parentNode).foreach(_.removeChild(el))
  }
}
