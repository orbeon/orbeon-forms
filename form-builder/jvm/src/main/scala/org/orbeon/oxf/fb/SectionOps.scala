/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.fb

import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fb.UndoAction.MoveContainer
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerDocContext
import org.orbeon.oxf.fr.NodeInfoCell.CellTest
import org.orbeon.oxf.fr.XMLNames.FRSectionTest
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.action.actions.XFormsDispatchAction
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.XFormsSwitchControl
import org.orbeon.oxf.xforms.event.{EventCollector, XFormsEvent}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath.*

trait SectionOps extends ContainerOps {

  self: GridOps => // funky dependency, to resolve at some point

  def deleteSectionByIdIfPossible(sectionId: String, force: Boolean = false)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    findContainerById(sectionId) flatMap
      (_ => deleteContainerById(e => force || canDeleteContainer(e), sectionId))

  def moveSection(container: NodeInfo, direction: Direction)(implicit ctx: FormBuilderDocContext): Some[UndoAction] = {

    val sectionId = container.id
    val position  = FormBuilder.containerPosition(sectionId)

    direction match {
      case Direction.Up    => moveSectionUp(container)
      case Direction.Down  => moveSectionDown(container)
      case Direction.Left  => moveSectionLeft(container)
      case Direction.Right => moveSectionRight(container)
    }

    Some(MoveContainer(sectionId, position))
  }

  // Move the section up if possible
  def moveSectionUp(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canContainerMove(container, Direction.Up))
      moveContainer(container, container precedingSibling * filter IsContainer head, moveElementBefore)

  // Move the section down if possible
  def moveSectionDown(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canContainerMove(container, Direction.Down))
      moveContainer(container, container followingSibling * filter IsContainer head, moveElementAfter)

  // Move the section right if possible
  def moveSectionRight(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canContainerMove(container, Direction.Right)) {

      val otherContainer = firstPrecedingContainerToMoveInto(container).get
      val destIsRepeat   = isRepeat(otherContainer)

      // If the destination is a repeat and is not the container itself (which doesn't have a nested iteration
      // element), move into the first child instead.
      def moveCheckIteration(source: NodeInfo, dest: NodeInfo) =
        moveElementIntoAsLast(source, if (destIsRepeat && dest != otherContainer) dest child * head else dest)

      moveContainer(container, otherContainer, moveCheckIteration)
    }

  // Move the section left if possible
  def moveSectionLeft(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canContainerMove(container, Direction.Left))
      moveContainer(container, findAncestorContainersLeafToRoot(container).head, moveElementAfter)

  // Find the section name given a descendant node
  def findSectionName(descendant: NodeInfo): String =
    (descendant ancestor "*:section" flatMap getControlNameOpt).head

  // Find the section name for a given control name
  // 2022-02-02: Unused except by test.
  def findSectionName(controlName: String)(implicit ctx: FormRunnerDocContext): Option[String] =
    findControlByName(controlName) map findSectionName

  def toggleOtherSections(sectionId: String)(implicit ctx: FormBuilderDocContext): Unit =
    findContainerById(sectionId).filter(IsSection).foreach { targetSectionElem =>

      val allSections = (ctx.bodyElem descendant FRSectionTest).toList

      val activeSectionIds =
        (
          (targetSectionElem ancestor FRSectionTest) ++
          List(targetSectionElem)                    ++
          (targetSectionElem descendant FRSectionTest)
        ).map(_.id).toSet

      val otherSections = allSections.filterNot(s => activeSectionIds(s.id))

      val targetAndAncestorsOpen =
        ((targetSectionElem ancestor FRSectionTest) :+ targetSectionElem).forall(isSectionOpen)

      val otherSectionsClosed =
        otherSections.forall(s => ! isSectionOpen(s))

      if (otherSections.nonEmpty && targetAndAncestorsOpen && otherSectionsClosed) {
        // Already soloed -> expand all sections in document order (top-down so parent sections open before subsections)
        allSections.foreach(setSectionOpen(_, open = true))
      } else {
        // Solo target section -> expand target hierarchy in document order, collapse all other sections
        allSections.filter(s => activeSectionIds(s.id)).foreach(setSectionOpen(_, open = true))
        otherSections.foreach(setSectionOpen(_, open = false))
        selectFirstCellInSectionIfNeeded(targetSectionElem)
      }
    }

  def expandAndSelectSection(sectionId: String)(implicit ctx: FormBuilderDocContext): Unit =
    findContainerById(sectionId).filter(IsSection).foreach { targetSectionElem =>
      val targetWasOpen = isSectionOpen(targetSectionElem)
      val sectionsToOpen =
        ((targetSectionElem ancestor FRSectionTest).reverse :+ targetSectionElem) ++
        (if (! targetWasOpen) (targetSectionElem descendant FRSectionTest).toList else Nil)

      sectionsToOpen.foreach(setSectionOpen(_, open = true))
      selectFirstCellInSectionIfNeeded(targetSectionElem)
    }

  def isSectionOpen(sectionElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Boolean = {
    val controls = findSectionControls(sectionElem.id).filter(_.isRelevant)
    controls.nonEmpty && controls.forall(isSectionControlOpen)
  }

  private def selectFirstCellInSectionIfNeeded(sectionElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (! findSelectedCell.exists(c => c.hasChildElement && (c ancestor * exists (_ isSameNodeInfo sectionElem)))) {
      val cells = sectionElem descendant CellTest
      cells.find(_.hasChildElement).orElse(cells.headOption).foreach { cell =>
        selectCell(cell)
        selectStartCell(cell)
      }
    }

  private def findSectionControls(staticId: String)(implicit ctx: FormBuilderDocContext): Iterable[XFormsControl] =
    inScopeContainingDocumentOpt.toList.flatMap { doc =>
      buildFormBuilderControlEffectiveId(staticId).flatMap(doc.findControlByEffectiveId) match {
        case Some(control) => List(control)
        case None          => doc.controls.getCurrentControlTree.effectiveIdsToControls.values.filter(_.getId == staticId)
      }
    }

  private def isSectionControlOpen(sectionControl: XFormsControl): Boolean =
    ControlsIterator(sectionControl, includeSelf = false)
      .collectFirst { case s: XFormsSwitchControl if s.getId == "switch" => s }
      .flatMap(_.selectedCaseIfRelevantOpt)
      .exists(_.getId == "case-open")

  private def setSectionOpen(sectionElem: NodeInfo, open: Boolean)(implicit ctx: FormBuilderDocContext): Unit = {
    val eventName = if (open) "fr-expand" else "fr-collapse"
    findSectionControls(sectionElem.id).foreach { control =>
      if (control.isRelevant && isSectionControlOpen(control) != open)
        XFormsDispatchAction.dispatch(
          eventName       = eventName,
          target          = control,
          bubbles         = true,
          cancelable      = true,
          properties      = XFormsEvent.EmptyGetter,
          delayOpt        = None,
          showProgress    = true,
          allowDuplicates = false,
          collector       = EventCollector.Throw
        )
    }
  }
}