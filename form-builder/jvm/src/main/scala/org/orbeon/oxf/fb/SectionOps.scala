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
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

trait SectionOps extends ContainerOps {

  self: GridOps ⇒ // funky dependency, to resolve at some point

  def canDeleteSection(section: NodeInfo): Boolean =
    canDeleteContainer(section)

  def deleteSectionById(sectionId: String)(implicit ctx: FormBuilderDocContext): Option[UndoAction] =
    deleteContainerById(canDeleteSection, sectionId)

  def moveSection(container: NodeInfo, direction: Direction)(implicit ctx: FormBuilderDocContext): Some[UndoAction] = {

    val sectionId = container.id
    val position  = FormBuilder.containerPosition(sectionId)

    direction match {
      case Direction.Up    ⇒ moveSectionUp(container)
      case Direction.Down  ⇒ moveSectionDown(container)
      case Direction.Left  ⇒ moveSectionLeft(container)
      case Direction.Right ⇒ moveSectionRight(container)
    }

    Some(MoveContainer(sectionId, direction, position))
  }

  // Move the section up if possible
  def moveSectionUp(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canMoveUp(container))
      moveContainer(container, container precedingSibling * filter IsContainer head, moveElementBefore)

  // Move the section down if possible
  def moveSectionDown(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canMoveDown(container))
      moveContainer(container, container followingSibling * filter IsContainer head, moveElementAfter)

  // Move the section right if possible
  def moveSectionRight(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canMoveRight(container)) {

      val otherContainer = precedingSection(container).get
      val destIsRepeat   = isRepeat(otherContainer)

      // If the destination is a repeat and is not the container itself (which doesn't have a nested iteration
      // element), move into the first child instead.
      def moveCheckIteration(source: NodeInfo, dest: NodeInfo) =
        moveElementIntoAsLast(source, if (destIsRepeat && dest != otherContainer) dest child * head else dest)

      moveContainer(container, otherContainer, moveCheckIteration)
    }

  // Move the section left if possible
  def moveSectionLeft(container: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    if (canMoveLeft(container))
      moveContainer(container, findAncestorContainersLeafToRoot(container).head, moveElementAfter)

  // Find the section name given a descendant node
  def findSectionName(descendant: NodeInfo): String =
    (descendant ancestor "*:section" flatMap getControlNameOpt).head

  // Find the section name for a given control name
  def findSectionName(doc: NodeInfo, controlName: String): Option[String] =
    findControlByName(doc, controlName) map findSectionName

  // Whether the given container can be moved up
  def canMoveUp(container: NodeInfo): Boolean =
    container precedingSibling * exists IsContainer

  // Whether the given container can be moved down
  def canMoveDown(container: NodeInfo): Boolean =
    container followingSibling * exists IsContainer

  // Whether the given container can be moved to the right
  def canMoveRight(container: NodeInfo): Boolean =
    precedingSection(container) exists canMoveInto

  // Whether the given container can be moved to the left
  def canMoveLeft(container: NodeInfo): Boolean =
    canDeleteSection(container) && findAncestorContainersLeafToRoot(container).size >= 2

  val DirectionCheck = List(
    "up"    → canMoveUp _,
    "right" → canMoveRight _,
    "down"  → canMoveDown _,
    "left"  → canMoveLeft _
  )

  private def precedingSection(container: NodeInfo) =
    container precedingSibling "*:section" headOption
}