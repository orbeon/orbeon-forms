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
import org.orbeon.fr.DropIndicatorDnD.*
import org.orbeon.fr.FormRunnerUtils
import org.orbeon.oxf.fr.ControlOps
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms.{XFormsId, XFormsUI}
import org.scalajs.dom
import org.scalajs.dom.html


object ContainerDnD {

  import Private.*

  def isDragging: Boolean = controller.isDragging

  locally {
    dom.document.addEventListener(
      "pointerdown",
      (event: dom.PointerEvent) => {
        if (event.button == 0 && ! controller.isActiveOrPending) {
          val handleSourceOpt: Option[(DragSource, html.Element)] =
            event.targetOpt
              .flatMap(_.closestOpt(".fb-container-move-handle"))
              .flatMap { handleEl =>
                handleEl.dataset.get("orbeonContainerId")
                  .flatMap(dom.document.getElementByIdOpt)
                  .flatMap(el => BlockCache.sectionGridCache.elems.find(_.el == el).orElse(Some(Block(el))))
                  .orElse(SectionGridEditor.currentSectionGridOpt)
                  .map { currentBlock =>
                    event.preventDefault()
                    (DragSource.Canvas(currentBlock), handleEl)
                  }
              }

          val titleBarSourceOpt: Option[(DragSource, html.Element)] =
            if (handleSourceOpt.isEmpty && ! event.altKey && ! event.ctrlKey && ! event.metaKey && ! event.shiftKey)
              for {
                interceptorEl <- event.targetOpt.flatMap(_.closestOpt(".fb-section-label-editor-click-interceptor"))
                if ! FormRunnerUtils.isViewMode(interceptorEl)
                containerId   <- interceptorEl.dataset.get("orbeonContainerId")
                containerEl   <- dom.document.getElementByIdOpt(containerId)
                if SectionGridEditor.canSectionOrGridMove(containerEl)
                block         <- BlockCache.sectionGridCache.elems.find(_.el == containerEl).orElse(Some(Block(containerEl)))
              } yield (DragSource.Canvas(block), interceptorEl)
            else
              None

          val canvasSourceOpt = handleSourceOpt.orElse(titleBarSourceOpt)

          val outlineSourceOpt: Option[(DragSource, html.Element)] =
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
                    .map(row => (DragSource.Outline(row), rowEl))
                }
            else
              None

          canvasSourceOpt.orElse(outlineSourceOpt).foreach {
            case (source, captureEl) =>
              controller.beginPending(
                event        = event,
                captureElOpt = Some(captureEl),
                state        = source
              )
          }
        }
      }
    )
  }

  private object Private {

    val AutoScrollEdgePx        = 50.0
    val AutoScrollStepPx        = 12.0
    val OutlineAutoScrollEdgePx = 36.0
    val OutlineAutoScrollStepPx = 8.0
    val OutlineIndentStepPx     = 14.0
    val TreeClusterGapPx        = 2.0
    val TreeClusterSpreadStepPx = 4.5

    sealed trait DragSource {
      def anchorEl: html.Element
    }
    object DragSource {
      case class Canvas(block: Block)  extends DragSource { def anchorEl: html.Element = block.el }
      case class Outline(row: TreeRow) extends DragSource { def anchorEl: html.Element = row.el   }
    }

    case class DropTarget(
      intoSectionOpt     : Option[html.Element],
      afterContainerOpt  : Option[html.Element],
      boundaryY          : Double,
      left               : Double,
      width              : Double,
      isOriginalPosition : Boolean = false
    ) {
      def toIndicatorLine: IndicatorLine =
        IndicatorLine(boundaryY, left, width, isOriginalPosition)
    }

    case class TreeRow(
      el          : html.Element,
      id          : String,
      parentIdOpt : Option[String],
      isContainer : Boolean,
      canMoveInto : Boolean,
      level       : Int
    )

    case class TreeDropTarget(
      intoContainerOpt   : Option[TreeRow],
      afterIdOpt         : Option[String],
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

    var dropTargets             : List[DropTarget]       = Nil
    var activeDropTargetOpt     : Option[DropTarget]     = None
    var treeDropTargets         : List[TreeDropTarget]   = Nil
    var activeTreeDropTargetOpt : Option[TreeDropTarget] = None
    var ghostElOpt              : Option[html.Element]   = None
    var highlightedSectionOpt   : Option[html.Element]   = None
    var highlightedTreeRowOpt   : Option[html.Element]   = None
    var dimmedTreeRows          : List[html.Element]     = Nil

    val controller = new PointerDragController[DragSource](
      overlayAnchorEl     = _.anchorEl,
      scrollContainerOpt  = {
        case DragSource.Canvas(_)  => dom.document.querySelectorOpt(".fb-main")
        case DragSource.Outline(_) => dom.document.querySelectorOpt(".fb-outline-body")
      },
      autoScrollEdgePx    = {
        case DragSource.Canvas(_) => AutoScrollEdgePx
        case _                    => OutlineAutoScrollEdgePx
      },
      autoScrollStepPx    = {
        case DragSource.Canvas(_) => AutoScrollStepPx
        case _                    => OutlineAutoScrollStepPx
      },
      suppressClickOnDrop = true,
      onStartDrag         = startDragging,
      onDragMove          = updateGhostAndIndicator,
      onScrolled          = refreshTargetsOnScroll,
      onDrop              = performDrop,
      onCleanup           = cleanupDrag
    )

    def parentSectionOpt(el: html.Element): Option[html.Element] =
      el.ancestorOrSelfElem(BlockCache.SectionSelector, includeSelf = false).nextOption()

    def isSectionOpen(sectionEl: html.Element): Boolean =
      sectionEl
        .childrenT(".fr-section-container")
        .headOption
        .flatMap(_.querySelectorOpt(".fr-section-content"))
        .exists(el => el.isVisible && ! el.hasClass(XFormsUI.CaseCollapsingClass))

    def isSectionTemplate(sectionEl: html.Element): Boolean =
      sectionEl
        .childrenT(".fr-section-container")
        .headOption
        .exists(_.queryNestedElems[html.Element](".fr-section-component", includeSelf = false).nonEmpty)

    def editableContainersInForm(): List[html.Element] =
      dom.document
        .querySelectorAllT(s".fb-body ${BlockCache.SectionSelector}, .fb-body ${BlockCache.GridSelector}")
        .iterator
        .filter { el =>
          el.isVisible &&
          ! FormRunnerUtils.isViewMode(el) &&
          ! BlockCache.isElemInCollapsingCase(el) &&
          el.ancestorOrSelfElem(".fr-section-component", includeSelf = false).nextOption().isEmpty
        }
        .toList
        .distinctBy(el => XFormsId.getStaticIdFromId(el.id))

    def computeDropTargets(draggedEl: html.Element): List[DropTarget] = {
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
              val rect = firstChild.getBoundingClientRect()
              (rect.left, rect.width)
            case None =>
              val contentEl =
                intoOpt
                  .flatMap(_.querySelectorOpt(".fr-section-content"))
                  .getOrElse(dom.document.querySelectorT(".fb-body"))
              val rect = contentEl.getBoundingClientRect()
              (rect.left + 10.0, Math.max(0.0, rect.width - 20.0))
          }

        val firstSlotOpt = {
          val firstTop =
            children.headOption match {
              case Some(firstChild) =>
                firstChild.getBoundingClientRect().top
              case None =>
                val contentEl =
                  intoOpt
                    .flatMap(_.querySelectorOpt(".fr-section-content"))
                    .getOrElse(dom.document.querySelectorT(".fb-body"))
                contentEl.getBoundingClientRect().top + 10.0
            }
          Some(
            DropTarget(
              intoSectionOpt     = intoOpt,
              afterContainerOpt  = None,
              boundaryY          = firstTop,
              left               = slotLeft,
              width              = slotWidth,
              isOriginalPosition = isSameParent && draggedIdx == 0
            )
          )
        }

        val afterSlots =
          children.indices.map { idx =>
            val child = children(idx)
            val boxEl =
              if (child.matches(BlockCache.SectionSelector))
                child.childrenT(".fr-section-container").headOption.getOrElse(child)
              else
                child
            val boxBottom = boxEl.getBoundingClientRect().bottom
            val afterTop =
              if (idx + 1 < children.length)
                (boxBottom + children(idx + 1).getBoundingClientRect().top) / 2.0
              else
                boxBottom + 2.0

            DropTarget(
              intoSectionOpt     = intoOpt,
              afterContainerOpt  = Some(child),
              boundaryY          = afterTop,
              left               = slotLeft,
              width              = slotWidth,
              isOriginalPosition = isSameParent && (idx == draggedIdx || idx == draggedIdx - 1)
            )
          }

        firstSlotOpt.toList ++ afterSlots
      }
    }

    def parseOutlineRowLevel(el: html.Element): Int =
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

    def outlineRowsInPanel(): List[TreeRow] = {
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

    def computeTreeDropTargets(
      draggedRow     : TreeRow,
      allRows        : List[TreeRow],
      indentStepPx   : Double,
      canBeTopLevel  : Boolean,
      canLeaveParent : Boolean
    ): List[TreeDropTarget] = {
      val rowById  = allRows.map(r => r.id -> r).toMap
      val parentOf = (id: String) => rowById.get(id).flatMap(_.parentIdOpt)

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
                   ! isDescendantOrSelf(sec.id, draggedRow.id, parentOf) =>
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

          val refRectOpt = intoOpt.orElse(allRows.headOption).map(_.el.getBoundingClientRect())
          val slotLeft   = refRectOpt.map(r => r.left + (targetLevel - 1) * indentStepPx).getOrElse(0.0)
          val slotRight  = refRectOpt.map(_.right).getOrElse(0.0)
          val slotWidth  = Math.max(20.0, slotRight - slotLeft)

          val firstSlotOpt = {
            val firstBoundaryY =
              intoOpt match {
                case Some(parentContainer) =>
                  val parentBottom = parentContainer.el.getBoundingClientRect().bottom
                  children.headOption match {
                    case Some(firstChild) => (parentBottom + firstChild.el.getBoundingClientRect().top) / 2.0
                    case None             => parentBottom
                  }
                case None =>
                  allRows.headOption.map(_.el.getBoundingClientRect().top).getOrElse(0.0)
              }

            Some(
              TreeDropTarget(
                intoContainerOpt   = intoOpt,
                afterIdOpt         = None,
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
              val child         = children(idx)
              val lastRow       = lastDescendantOrSelf(child, allRows, (r: TreeRow) => r.id, parentOf)
              val lastRowBottom = lastRow.el.getBoundingClientRect().bottom
              val nextRowOpt    = allRows.dropWhile(_.id != lastRow.id).drop(1).headOption
              val boundaryY =
                nextRowOpt match {
                  case Some(nextRow) => (lastRowBottom + nextRow.el.getBoundingClientRect().top) / 2.0
                  case None          => lastRowBottom + 1.0
                }

              TreeDropTarget(
                intoContainerOpt   = intoOpt,
                afterIdOpt         = Some(child.id),
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
      spreadClusterAnchors[TreeDropTarget](
        sorted       = sorted,
        boundaryY    = _.boundaryY,
        withAnchorY  = (t, y) => t.copy(anchorY = y),
        clusterGapPx = TreeClusterGapPx,
        maxStepPx    = TreeClusterSpreadStepPx
      )
    }

    def containerGhostLabel(el: html.Element): (String, String) = {
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

    def outlineGhostLabel(rowEl: html.Element): (String, String) = {
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

    def refreshTargetsOnScroll(source: DragSource): Unit =
      source match {
        case DragSource.Canvas(sourceBlock) =>
          dropTargets = computeDropTargets(sourceBlock.el)

        case DragSource.Outline(sourceRow) =>
          val allRows      = outlineRowsInPanel()
          val refreshedRow = allRows.find(_.id == sourceRow.id).getOrElse(sourceRow)
          treeDropTargets = computeTreeDropTargets(
            draggedRow     = refreshedRow,
            allRows        = allRows,
            indentStepPx   = OutlineIndentStepPx,
            canBeTopLevel  = refreshedRow.isContainer,
            canLeaveParent = allRows.count(_.parentIdOpt == refreshedRow.parentIdOpt) > 1
          )
      }

    def startDragging(source: DragSource): Boolean = {
      val hasValidTargets =
        source match {
          case DragSource.Canvas(sourceBlock) =>
            dropTargets = computeDropTargets(sourceBlock.el)
            dropTargets.exists(! _.isOriginalPosition)

          case DragSource.Outline(sourceRow) =>
            val allRows      = outlineRowsInPanel()
            val refreshedRow = allRows.find(_.id == sourceRow.id).getOrElse(sourceRow)
            treeDropTargets = computeTreeDropTargets(
              draggedRow     = refreshedRow,
              allRows        = allRows,
              indentStepPx   = OutlineIndentStepPx,
              canBeTopLevel  = refreshedRow.isContainer,
              canLeaveParent = allRows.count(_.parentIdOpt == refreshedRow.parentIdOpt) > 1
            )
            if (treeDropTargets.exists(! _.isOriginalPosition)) {
              val rowById  = allRows.map(r => r.id -> r).toMap
              val parentOf = (id: String) => rowById.get(id).flatMap(_.parentIdOpt)
              dimmedTreeRows = allRows.filter(r => isDescendantOrSelf(r.id, refreshedRow.id, parentOf)).map(_.el)
              true
            } else {
              false
            }
        }

      if (! hasValidTargets) {
        false
      } else {
        ControlEditor.mask()
        SectionGridEditor.sectionGridEditorContainer.hide()
        SectionGridEditor.gridMoveHandleContainer.hide()
        SectionLabelEditor.cancelHoverAndHint()
        dom.document.querySelectorOpt(".fb-row-editor").foreach(_.hide())

        val (iconClass, labelText) =
          source match {
            case DragSource.Canvas(sourceBlock) =>
              if (sourceBlock.el.matches(BlockCache.GridSelector))
                sourceBlock.el.childrenT(".fr-grid").foreach { gridEl =>
                  gridEl.classList.remove("fb-hover")
                  gridEl.classList.remove("fb-grid-dnd-hover")
                }
              sourceBlock.el.classList.add(SourceClass)
              containerGhostLabel(sourceBlock.el)

            case DragSource.Outline(sourceRow) =>
              dimmedTreeRows.foreach(_.classList.add(SourceClass))
              outlineGhostLabel(sourceRow.el)
          }

        ghostElOpt = Some(createPillGhost(overlayHostEl(source.anchorEl), iconClass, labelText))
        true
      }
    }

    def updateGhostAndIndicator(source: DragSource, clientX: Double, clientY: Double): Option[IndicatorLine] = {
      ghostElOpt.foreach(updatePillGhost(_, clientX, clientY))

      source match {
        case DragSource.Canvas(_) =>
          activeDropTargetOpt = dropTargets.minByOption(t => Math.abs(clientY - t.boundaryY))

          highlightedSectionOpt.foreach { sec =>
            sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
          }
          highlightedSectionOpt = activeDropTargetOpt.filterNot(_.isOriginalPosition).flatMap(_.intoSectionOpt)
          highlightedSectionOpt.foreach { sec =>
            sec.childrenT(".fr-section-container").foreach(_.classList.add("fb-dnd-drop-into"))
          }

          activeDropTargetOpt.map(_.toIndicatorLine)

        case DragSource.Outline(_) =>
          activeTreeDropTargetOpt =
            treeDropTargets.minByOption { t =>
              Math.abs(clientY - t.anchorY) + Math.abs(clientX - (t.left + 16.0)) * 0.15
            }

          highlightedTreeRowOpt.foreach { el =>
            el.classList.remove("fb-outline-dnd-drop-into")
            el.classList.remove("fb-dnd-drop-into")
          }
          highlightedTreeRowOpt = activeTreeDropTargetOpt.filterNot(_.isOriginalPosition).flatMap(_.intoContainerOpt).map(_.el)
          highlightedTreeRowOpt.foreach { el =>
            if (el.hasClass("fb-outline-row"))
              el.classList.add("fb-outline-dnd-drop-into")
            else
              el.classList.add("fb-dnd-drop-into")
          }

          activeTreeDropTargetOpt.map(_.toIndicatorLine)
      }
    }

    def performDrop(source: DragSource): Unit =
      source match {
        case DragSource.Canvas(sourceBlock) =>
          activeDropTargetOpt.filterNot(_.isOriginalPosition).foreach { target =>
            FormBuilderRpcClient.api.containerMove(
              containerId         = sourceBlock.el.id,
              intoSectionIdOpt    = target.intoSectionOpt.map(_.id),
              afterContainerIdOpt = target.afterContainerOpt.map(_.id)
            )
          }

        case DragSource.Outline(sourceRow) =>
          activeTreeDropTargetOpt.filterNot(_.isOriginalPosition).foreach { target =>
            FormBuilderRpcClient.api.containerMove(
              containerId         = sourceRow.id,
              intoSectionIdOpt    = target.intoContainerOpt.map(_.id),
              afterContainerIdOpt = target.afterIdOpt
            )
          }
      }

    def cleanupDrag(source: DragSource, wasDragging: Boolean): Unit = {
      if (wasDragging) {
        ControlEditor.unmask()
        Position.notifyOffsetMayHaveChanged()
      }

      source match {
        case DragSource.Canvas(sourceBlock) => sourceBlock.el.classList.remove(SourceClass)
        case DragSource.Outline(_)          =>
      }
      dimmedTreeRows.foreach(_.classList.remove(SourceClass))

      highlightedSectionOpt.foreach { sec =>
        sec.childrenT(".fr-section-container").foreach(_.classList.remove("fb-dnd-drop-into"))
      }
      highlightedTreeRowOpt.foreach { el =>
        el.classList.remove("fb-outline-dnd-drop-into")
        el.classList.remove("fb-dnd-drop-into")
      }

      ghostElOpt.foreach(el => Option(el.parentNode).foreach(_.removeChild(el)))

      dropTargets             = Nil
      activeDropTargetOpt     = None
      treeDropTargets         = Nil
      activeTreeDropTargetOpt = None
      ghostElOpt              = None
      highlightedSectionOpt   = None
      highlightedTreeRowOpt   = None
      dimmedTreeRows          = Nil
    }
  }
}
