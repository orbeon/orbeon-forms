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

import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.SimplePath._

trait SectionOps extends ContainerOps {

  self: GridOps ⇒ // funky dependency, to resolve at some point

  def canDeleteSection(section: NodeInfo): Boolean =
    canDeleteContainer(section)

  def deleteSectionById(sectionId: String): Unit =
    deleteContainerById(canDeleteSection, sectionId)

  // Move the section up if possible
  def moveSectionUp(container: NodeInfo) =
    if (canMoveUp(container))
      moveContainer(container, container precedingSibling * filter IsContainer head, moveElementBefore)

  // Move the section down if possible
  def moveSectionDown(container: NodeInfo) =
    if (canMoveDown(container))
      moveContainer(container, container followingSibling * filter IsContainer head, moveElementAfter)

  // Move the section right if possible
  def moveSectionRight(container: NodeInfo) =
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
  def moveSectionLeft(container: NodeInfo) =
    if (canMoveLeft(container))
      moveContainer(container, findAncestorContainersLeafToRoot(container).head, moveElementAfter)

  // Find the section name given a descendant node
  def findSectionName(descendant: NodeInfo): String =
    (descendant ancestor "*:section" map getControlNameOpt flatten).head

  // Find the section name for a given control name
  def findSectionName(doc: NodeInfo, controlName: String): Option[String] =
    findControlByName(doc, controlName) map findSectionName

  // Whether the given container can be moved up
  def canMoveUp(container: NodeInfo) =
    container precedingSibling * filter IsContainer nonEmpty

  // Whether the given container can be moved down
  def canMoveDown(container: NodeInfo) =
    container followingSibling * filter IsContainer nonEmpty

  // Whether the given container can be moved to the right
  def canMoveRight(container: NodeInfo) =
    precedingSection(container) exists canMoveInto

  // Whether the given container can be moved to the left
  def canMoveLeft(container: NodeInfo) =
    canDeleteSection(container) && findAncestorContainersLeafToRoot(container).size >= 2

  private val DirectionCheck = List(
    "up"    → canMoveUp _,
    "right" → canMoveRight _,
    "down"  → canMoveDown _,
    "left"  → canMoveLeft _
  )

  // Return all classes that need to be added to an editable section
  def sectionCanDoClasses(container: NodeInfo): Seq[String] = {
    val directionClasses =
      DirectionCheck collect { case (direction, check) if check(container) ⇒ "fb-can-move-" + direction }

    val deleteClasses =
      canDeleteSection(container) list "fb-can-delete"

    "fr-section-container" :: deleteClasses ::: directionClasses
  }

  private def precedingSection(container: NodeInfo) =
    container precedingSibling "*:section" headOption
}