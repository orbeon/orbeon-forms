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
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.FormRunnerDocContext
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

trait SectionOps extends ContainerOps {

  self: GridOps => // funky dependency, to resolve at some point

  def deleteSectionByIdIfPossible(sectionId: String)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    findContainerById(sectionId) flatMap
      (_ => deleteContainerById(canDeleteContainer, sectionId))

  def moveSection(container: NodeInfo, direction: Direction)(implicit ctx: FormBuilderDocContext): Some[UndoAction] = {

    val sectionId = container.id
    val position  = FormBuilder.containerPosition(sectionId)

    direction match {
      case Direction.Up    => moveSectionUp(container)
      case Direction.Down  => moveSectionDown(container)
      case Direction.Left  => moveSectionLeft(container)
      case Direction.Right => moveSectionRight(container)
    }

    Some(MoveContainer(sectionId, direction, position))
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
}