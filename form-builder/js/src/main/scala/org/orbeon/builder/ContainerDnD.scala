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
import org.orbeon.xforms.{AjaxClient, AjaxEvent, XFormsId}
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
  private val ActionStepIndentStepPx   = 18.0

  private sealed trait DragSource
  private object DragSource {
    case class Canvas(block: Block)     extends DragSource
    case class Outline(row: TreeRow)    extends DragSource
    case class ActionStep(row: TreeRow) extends DragSource
  }

  private case class DropTarget(
    intoSectionOpt    : Option[html.Element],
    afterContainerOpt : Option[html.Element],
    top               : Double,
    left              : Double,
    width             : Double
  )

  private case class TreeRow(
    el          : html.Element,
    id          : String,
    parentIdOpt : Option[String],
    isContainer : Boolean,
    canMoveInto : Boolean,
    level       : Int
  )

  private case class TreeDropTarget(
    intoContainerOpt : Option[TreeRow],
    afterIdOpt      : Option[String],
    level            : Int,
    boundaryTop      : Double,
    anchorTop        : Double,
    left             : Double,
    width            : Double
  )

  private var _isDragging: Boolean = false
  def isDragging: Boolean = _isDragging

  private var dragSourceOpt           : Option[DragSource]     = None
  private var startClientX            : Double                 = 0.0
  private var startClientY            : Double                 = 0.0
  private var lastClientX             : Double                 = 0.0
  private var lastClientY             : Double                 = 0.0
  private var dropTargets             : List[DropTarget]       = Nil
  private var activeDropTargetOpt     : Option[DropTarget]     = None
  private var treeDropTargets         : List[TreeDropTarget]   = Nil
  private var activeTreeDropTargetOpt : Option[TreeDropTarget] = None
  private var ghostElOpt              : Option[html.Element]   = None
  private var indicatorElOpt          : Option[html.Element]   = None
  private var highlightedSectionOpt   : Option[html.Element]   = None
  private var highlightedTreeRowOpt   : Option[html.Element]   = None
  private var dimmedTreeRows          : List[html.Element]     = Nil
  private var autoScrollTimerOpt      : Option[Int]            = None

  private val onPointerMoveFn: js.Function1[dom.PointerEvent, Unit]  = onPointerMove(_)
  private val onPointerUpFn  : js.Function1[dom.PointerEvent, Unit]  = onPointerUp(_)
  private val onKeyDownFn    : js.Function1[dom.KeyboardEvent, Unit] = onKeyDown(_)

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0) {
          val handleSourceOpt =
            event.targetOpt
              .flatMap(_.closestOpt(".fb-container-move-handle"))
              .flatMap { handleEl =>
                handleEl.dataset.get("orbeonContainerId")
                  .flatMap(dom.document.getElementByIdOpt)
                  .flatMap(el => BlockCache.sectionGridCache.elems.find(_.el == el).orElse(Some(Block(el))))
                  .orElse(SectionGridEditor.currentSectionGridOpt)
              }
              .map { currentBlock =>
                event.preventDefault()
                DragSource.Canvas(currentBlock)
              }

          val titleBarSourceOpt =
            if (handleSourceOpt.isEmpty && ! event.altKey && ! event.ctrlKey && ! event.metaKey && ! event.shiftKey)
              event.targetOpt
                .flatMap(_.closestOpt(".fb-section-label-editor-click-interceptor"))
                .filter(el => ! FormRunnerUtils.isViewMode(el))
                .flatMap(_.dataset.get("orbeonContainerId"))
                .flatMap(dom.document.getElementByIdOpt)
                .filter(SectionGridEditor.canSectionOrGridMove)
                .flatMap(el => BlockCache.sectionGridCache.elems.find(_.el == el).orElse(Some(Block(el))))
                .map(DragSource.Canvas.apply)
            else
              None

          val canvasSourceOpt = handleSourceOpt.orElse(titleBarSourceOpt)

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

          val actionStepSourceOpt =
            if (canvasSourceOpt.isEmpty && outlineSourceOpt.isEmpty)
              event.targetOpt
                .flatMap(_.closestOpt(".fb-action-dialog .fb-action-step-grab, #fb-action-dialog .fb-action-step-grab"))
                .flatMap { grabEl =>
                  event.preventDefault()
                  val rowElOpt = grabEl.closestOpt(".fb-action-step-iteration")
                  val allRows  = actionStepRowsInDialog()
                  rowElOpt.flatMap(rowEl => allRows.find(_.el == rowEl)).filter { _ =>
                    allRows.length > 1
                  }
                }
                .map(DragSource.ActionStep.apply)
            else
              None

          canvasSourceOpt.orElse(outlineSourceOpt).orElse(actionStepSourceOpt).foreach { source =>
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

  private def outlineRowsInPanel(): List[TreeRow] = {
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

          TreeRow(
            el          = el,
            id          = containerId,
            parentIdOpt = parentIdOpt,
            isContainer = isSection,
            canMoveInto = canMoveInto,
            level       = level
          )
        }
    }
  }

  private def actionStepRowsInDialog(): List[TreeRow] = {
    val rawElems =
      dom.document
        .querySelectorAllT(".fb-action-dialog .fb-action-step-iteration, #fb-action-dialog .fb-action-step-iteration")
        .iterator
        .filter(el => el.isVisible && ! FormRunnerUtils.isViewMode(el))
        .toList

    var containerByLevel = Map.empty[Int, String]

    rawElems.zipWithIndex.flatMap { case (el, idx) =>
      val rowId =
        Option(el.getAttribute("data-orbeon-index"))
          .flatMap(_.trimAllToOpt)
          .getOrElse((idx + 1).toString)

      val level =
        Option(el.getAttribute("data-orbeon-level"))
          .flatMap(_.toIntOption)
          .orElse {
            Option(el.getAttribute("class"))
              .toList
              .flatMap(_.splitTo[List]())
              .collectFirst { case s if s.startsWith("xforms-dnd-level-") => s.stripPrefix("xforms-dnd-level-").toIntOption.map(_ + 1) }
              .flatten
          }
          .getOrElse(1)

      val isContainer =
        el.getAttribute("data-orbeon-can-move-into") == "true" ||
        el.getAttribute("data-orbeon-is-container") == "true" ||
        Option(el.getAttribute("data-orbeon-step-type")).exists(t => t == "if" || t == "data-iterate") ||
        el.querySelectorOpt(".fb-action-step-options select option[value='if']:checked, .fb-action-step-options select option[value='data-iterate']:checked").isDefined

      val parentIdOpt =
        if (level > 1)
          containerByLevel.get(level - 1)
        else
          None

      if (isContainer)
        containerByLevel = containerByLevel.filter { case (lvl, _) => lvl < level } + (level -> rowId)

      Some(
        TreeRow(
          el          = el,
          id          = rowId,
          parentIdOpt = parentIdOpt,
          isContainer = isContainer,
          canMoveInto = isContainer,
          level       = level
        )
      )
    }
  }

  private def isTreeDescendantOrSelf(
    candidateId : String,
    ancestorId  : String,
    rowById     : Map[String, TreeRow]
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

  private def computeTreeDropTargets(
    draggedRow     : TreeRow,
    allRows        : List[TreeRow],
    indentStepPx   : Double,
    canBeTopLevel  : Boolean,
    canLeaveParent : Boolean
  ): List[TreeDropTarget] = {
    val rowById = allRows.map(r => r.id -> r).toMap

    def lastDescendantOrSelfRow(row: TreeRow): TreeRow =
      allRows
        .filter(c => isTreeDescendantOrSelf(c.id, row.id, rowById))
        .lastOption
        .getOrElse(row)

    val eligibleParents: List[Option[TreeRow]] =
      if (! canLeaveParent) {
        List(draggedRow.parentIdOpt.flatMap(rowById.get))
      } else {
        val topLevelOpt = if (canBeTopLevel) List(None) else Nil
        val containerParents =
          allRows.collect {
            case sec
              if sec.isContainer &&
                 sec.canMoveInto &&
                 ! isTreeDescendantOrSelf(sec.id, draggedRow.id, rowById) =>
              Some(sec)
          }
        topLevelOpt ++ containerParents
      }

    val rawTargets =
      eligibleParents.flatMap { intoOpt =>
        val intoIdOpt    = intoOpt.map(_.id)
        val targetLevel  = intoOpt.map(_.level + 1).getOrElse(1)
        val children     = allRows.filter(_.parentIdOpt == intoIdOpt)
        val isSameParent = intoIdOpt == draggedRow.parentIdOpt
        val draggedIdx   = if (isSameParent) children.indexWhere(_.id == draggedRow.id) else -1

        val refRowElOpt = intoOpt.orElse(allRows.headOption).map(_.el)
        val slotLeft =
          refRowElOpt
            .map(el => el.getOffset.left + (targetLevel - 1) * indentStepPx)
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
                case Some(parentContainer) =>
                  val parentBottom = parentContainer.el.getOffset.top + parentContainer.el.outerHeight
                  children.headOption match {
                    case Some(firstChild) => (parentBottom + firstChild.el.getOffset.top) / 2.0
                    case None             => parentBottom
                  }
                case None =>
                  allRows.headOption.map(_.el.getOffset.top).getOrElse(0.0)
              }

            Some(
              TreeDropTarget(
                intoContainerOpt = intoOpt,
                afterIdOpt      = None,
                level            = targetLevel,
                boundaryTop      = firstBoundaryTop,
                anchorTop        = firstBoundaryTop,
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
              val child         = children(idx)
              val lastRow       = lastDescendantOrSelfRow(child)
              val lastRowBottom = lastRow.el.getOffset.top + lastRow.el.outerHeight
              val nextRowOpt    = allRows.dropWhile(_.id != lastRow.id).drop(1).headOption
              val boundaryTop =
                nextRowOpt match {
                  case Some(nextRow) => (lastRowBottom + nextRow.el.getOffset.top) / 2.0
                  case None          => lastRowBottom + 1.0
                }

              Some(
                TreeDropTarget(
                  intoContainerOpt = intoOpt,
                  afterIdOpt      = Some(child.id),
                  level            = targetLevel,
                  boundaryTop      = boundaryTop,
                  anchorTop        = boundaryTop,
                  left             = slotLeft,
                  width            = slotWidth
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

  private def actionStepGhostLabel(rowEl: html.Element): (String, String) = {
    val (iconClass, fallbackLabel) = Option(rowEl.getAttribute("data-orbeon-step-type")) match {
      case Some("service-call")            => ("fa-exchange", "Call Service")
      case Some("if")                      => ("fa-code-fork", "Condition")
      case Some("data-iterate")           => ("fa-repeat", "Repeat for Each Item")
      case Some("control-setvalue")        => ("fa-pencil", "Set Control Value")
      case Some("control-clear")           => ("fa-eraser", "Clear Control Value")
      case Some("control-setitems")        => ("fa-list", "Set Control Items")
      case Some("control-setfocus")        => ("fa-crosshairs", "Set Control Focus")
      case Some("control-setvisited")      => ("fa-check-square-o", "Set Control Visited")
      case Some("alert")                   => ("fa-exclamation-triangle", "Show Message")
      case Some("navigate")                => ("fa-external-link", "Navigate")
      case Some("process-call")            => ("fa-cogs", "Call Process")
      case Some("repeat-add-iteration")    => ("fa-plus-circle", "Add Iteration")
      case Some("repeat-remove-iteration") => ("fa-minus-circle", "Remove Iteration")
      case Some("repeat-clear")            => ("fa-trash-o", "Clear Iterations")
      case _                               => ("fa-bolt", "Step")
    }

    val labelText =
      rowEl.querySelectorOpt(".fb-action-step-options select option:checked")
        .flatMap(el => Option(el.textContent))
        .orElse(rowEl.querySelectorOpt(".select2-selection__rendered").flatMap(el => Option(el.textContent)))
        .orElse(rowEl.querySelectorOpt(".xforms-select1-entry-selected").flatMap(el => Option(el.textContent)))
        .flatMap(_.trimAllToOpt)
        .getOrElse(fallbackLabel)

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
          val refreshedRow = allRows.find(_.id == sourceRow.id).getOrElse(sourceRow)
          treeDropTargets  = computeTreeDropTargets(
            draggedRow     = refreshedRow,
            allRows        = allRows,
            indentStepPx   = OutlineIndentStepPx,
            canBeTopLevel  = refreshedRow.isContainer,
            canLeaveParent = allRows.count(_.parentIdOpt == refreshedRow.parentIdOpt) > 1
          )
          if (treeDropTargets.nonEmpty) {
            val rowById = allRows.map(r => r.id -> r).toMap
            dimmedTreeRows =
              allRows
                .filter(r => isTreeDescendantOrSelf(r.id, refreshedRow.id, rowById))
                .map(_.el)
            true
          } else {
            false
          }

        case DragSource.ActionStep(sourceRow) =>
          val allRows      = actionStepRowsInDialog()
          val refreshedRow = allRows.find(_.id == sourceRow.id).getOrElse(sourceRow)
          treeDropTargets  = computeTreeDropTargets(
            draggedRow     = refreshedRow,
            allRows        = allRows,
            indentStepPx   = ActionStepIndentStepPx,
            canBeTopLevel  = true,
            canLeaveParent = true
          )
          if (treeDropTargets.nonEmpty) {
            val rowById = allRows.map(r => r.id -> r).toMap
            dimmedTreeRows =
              allRows
                .filter(r => isTreeDescendantOrSelf(r.id, refreshedRow.id, rowById))
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
      SectionGridEditor.gridMoveHandleContainer.hide()
      SectionLabelEditor.cancelHoverAndHint()
      dom.document.querySelectorOpt(".fb-row-editor").foreach(_.hide())

      val targetDialogOpt =
        source match {
          case DragSource.ActionStep(_) =>
            dom.document.querySelectorOpt(".fb-action-dialog, #fb-action-dialog")
          case _ =>
            None
        }
      val rootEl = targetDialogOpt.getOrElse(orbeonRootEl)
      orbeonRootEl.classList.add("fb-dragging-container")
      targetDialogOpt.foreach(_.classList.add("fb-dragging-container"))

      val (iconClass, labelText) =
        source match {
          case DragSource.Canvas(sourceBlock) =>
            if (sourceBlock.el.matches(BlockCache.GridSelector))
              sourceBlock.el.childrenT(".fr-grid").foreach { gridEl =>
                gridEl.classList.remove("fb-hover")
                gridEl.classList.remove("fb-grid-dnd-hover")
              }
            sourceBlock.el.classList.add("fb-dnd-source")
            containerGhostLabel(sourceBlock.el)

          case DragSource.Outline(sourceRow) =>
            dimmedTreeRows.foreach(_.classList.add("fb-dnd-source"))
            outlineGhostLabel(sourceRow.el)

          case DragSource.ActionStep(sourceRow) =>
            dimmedTreeRows.foreach(_.classList.add("fb-dnd-source"))
            actionStepGhostLabel(sourceRow.el)
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

      case Some(DragSource.Outline(_) | DragSource.ActionStep(_)) =>
        val pointerDocX = clientX + dom.window.pageXOffset
        val pointerDocY = clientY + dom.window.pageYOffset

        activeTreeDropTargetOpt =
          treeDropTargets.minByOption { t =>
            Math.abs(pointerDocY - t.anchorTop) + Math.abs(pointerDocX - (t.left + 16.0)) * 0.15
          }

        highlightedTreeRowOpt.foreach { el =>
          el.classList.remove("fb-outline-dnd-drop-into")
          el.classList.remove("fb-dnd-drop-into")
        }
        highlightedTreeRowOpt = activeTreeDropTargetOpt.flatMap(_.intoContainerOpt).map(_.el)
        highlightedTreeRowOpt.foreach { el =>
          if (el.hasClass("fb-outline-row"))
            el.classList.add("fb-outline-dnd-drop-into")
          else
            el.classList.add("fb-dnd-drop-into")
        }

        indicatorElOpt.foreach { indicatorEl =>
          activeTreeDropTargetOpt match {
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

  private def autoScrollContainer(
    scrollElemOpt : Option[html.Element],
    clientY       : Double,
    edgePx        : Double,
    stepPx        : Double,
    onScrolled    : () => Unit
  ): Unit =
    scrollElemOpt.foreach { scrollElem =>
      val rect = scrollElem.getBoundingClientRect()
      val delta =
        if (clientY < rect.top + edgePx)
          -stepPx
        else if (clientY > rect.bottom - edgePx)
          stepPx
        else
          0.0

      if (delta != 0.0) {
        if (autoScrollTimerOpt.isEmpty) {
          val timerId = dom.window.setInterval(
            () => {
              scrollElemOpt.foreach { elem =>
                val prevScrollTop = elem.scrollTop
                elem.scrollTop = prevScrollTop + delta
                if (elem.scrollTop != prevScrollTop)
                  onScrolled()
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

  private def updateAutoScroll(clientY: Double): Unit =
    dragSourceOpt match {
      case Some(DragSource.Canvas(sourceBlock)) =>
        autoScrollContainer(
          scrollElemOpt = dom.document.querySelectorOpt(".fb-main"),
          clientY       = clientY,
          edgePx        = AutoScrollEdgePx,
          stepPx        = AutoScrollStepPx,
          onScrolled    = () => {
            dropTargets = computeDropTargets(sourceBlock.el)
            updateGhostAndIndicator(lastClientX, lastClientY)
          }
        )

      case Some(DragSource.Outline(sourceRow)) =>
        autoScrollContainer(
          scrollElemOpt = dom.document.querySelectorOpt(".fb-outline-body"),
          clientY       = clientY,
          edgePx        = OutlineAutoScrollEdgePx,
          stepPx        = OutlineAutoScrollStepPx,
          onScrolled    = () => {
            treeDropTargets = computeTreeDropTargets(
              draggedRow     = sourceRow,
              allRows        = outlineRowsInPanel(),
              indentStepPx   = OutlineIndentStepPx,
              canBeTopLevel  = sourceRow.isContainer,
              canLeaveParent = true
            )
            updateGhostAndIndicator(lastClientX, lastClientY)
          }
        )

      case Some(DragSource.ActionStep(sourceRow)) =>
        autoScrollContainer(
          scrollElemOpt = dom.document.querySelectorOpt(".fb-action-dialog .fb-dialog-body, #fb-action-dialog .fb-dialog-body"),
          clientY       = clientY,
          edgePx        = OutlineAutoScrollEdgePx,
          stepPx        = OutlineAutoScrollStepPx,
          onScrolled    = () => {
            treeDropTargets = computeTreeDropTargets(
              draggedRow     = sourceRow,
              allRows        = actionStepRowsInDialog(),
              indentStepPx   = ActionStepIndentStepPx,
              canBeTopLevel  = true,
              canLeaveParent = true
            )
            updateGhostAndIndicator(lastClientX, lastClientY)
          }
        )

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
    val wasDragging         = _isDragging
    val sourceOpt           = dragSourceOpt
    val canvasDropTargetOpt = activeDropTargetOpt
    val treeDropTargetOpt   = activeTreeDropTargetOpt

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
          treeDropTargetOpt.foreach { target =>
            FormBuilderRpcClient.api.containerMove(
              containerId         = sourceRow.id,
              intoSectionIdOpt    = target.intoContainerOpt.map(_.id),
              afterContainerIdOpt = target.afterIdOpt
            )
          }

        case Some(DragSource.ActionStep(sourceRow)) =>
          treeDropTargetOpt.foreach { target =>
            val intoId  = target.intoContainerOpt.fold("")(_.id)
            val afterId = target.afterIdOpt.getOrElse("")
            AjaxClient.fireEvent(
              AjaxEvent(
                eventName  = "fb-action-step-move",
                targetId   = "fb-action-dialog",
                properties = Map[String, js.Any](
                  "source-index" -> sourceRow.id,
                  "into-index"   -> intoId,
                  "after-index"  -> afterId
                )
              )
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
      Position.notifyOffsetMayHaveChanged()
    }

    orbeonRootEl.classList.remove("fb-dragging-container")
    dom.document.querySelectorOpt(".fb-action-dialog, #fb-action-dialog").foreach(_.classList.remove("fb-dragging-container"))
    dragSourceOpt.foreach {
      case DragSource.Canvas(sourceBlock) => sourceBlock.el.classList.remove("fb-dnd-source")
      case DragSource.Outline(_)          =>
      case DragSource.ActionStep(_)       =>
    }
    dimmedTreeRows.foreach(_.classList.remove("fb-dnd-source"))
    highlightedSectionOpt.foreach { sec =>
      sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
    }
    highlightedTreeRowOpt.foreach { el =>
      el.classList.remove("fb-outline-dnd-drop-into")
      el.classList.remove("fb-dnd-drop-into")
    }

    ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))
    indicatorElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

    dragSourceOpt           = None
    dropTargets             = Nil
    activeDropTargetOpt     = None
    treeDropTargets         = Nil
    activeTreeDropTargetOpt = None
    ghostElOpt              = None
    indicatorElOpt          = None
    highlightedSectionOpt   = None
    highlightedTreeRowOpt   = None
    dimmedTreeRows          = Nil
  }
}
