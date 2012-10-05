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

import collection.JavaConverters._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.fb.ContainerOps._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.xforms.XFormsUtils

object SectionOps {

    def canDeleteSection(section: NodeInfo): Boolean = {
        val isSubSection = (section ancestor "*:section").nonEmpty        // We can always delete a sub-section
        val hasSiblingSection = (section sibling "*:section").nonEmpty    // We don't want to delete the last top-level section
        isSubSection || hasSiblingSection
    }

    def deleteSectionById(sectionId: String): Unit = deleteContainerById(canDeleteSection, sectionId)

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
        if (canMoveRight(container))
            moveContainer(container, precedingSection(container).get, moveElementIntoAsLast)

    // Move the section left if possible
    def moveSectionLeft(container: NodeInfo) =
        if (canMoveLeft(container))
            moveContainer(container, findAncestorContainers(container).head, moveElementAfter)

    // Find the section name given a descendant node
    def findSectionName(descendant: NodeInfo): String  =
        (descendant ancestor "*:section" map (getControlNameOption(_)) flatten).head

    // Find the section name for a given control name
    def findSectionName(doc: NodeInfo, controlName: String): Option[String] =
        findControlByName(doc, controlName) map (findSectionName(_))

    // Whether the given container can be moved up
    def canMoveUp(container: NodeInfo) =
        container precedingSibling * filter IsContainer nonEmpty

    // Whether the given container can be moved down
    def canMoveDown(container: NodeInfo) =
        container followingSibling * filter IsContainer nonEmpty

    // Whether the given container can be moved to the right
    def canMoveRight(container: NodeInfo) =
        precedingSection(container) exists (canMoveInto(_))

    // Whether the given container can be moved to the left
    def canMoveLeft(container: NodeInfo) =
        findAncestorContainers(container).size >= 2

    def canDoClasses(container: NodeInfo): java.util.List[String] = {
        val directionClasses = {
            val directionCheck = Map("up" → (canMoveUp _), "right" → (canMoveRight _), "down" → (canMoveDown _),  "left" → (canMoveLeft _))
            val canDirections = directionCheck filter { case (_, check) ⇒ check(container) } map { case (direction, _) ⇒ direction }
            canDirections map ( "fb-can-move-" ++ _)
        }
        val deleteClass = if (canDeleteSection(container)) Some("fb-can-delete") else None
        (directionClasses ++ deleteClass).toList.asJava
    }

    private def precedingSection(container: NodeInfo) =
        container precedingSibling "*:section" headOption
}