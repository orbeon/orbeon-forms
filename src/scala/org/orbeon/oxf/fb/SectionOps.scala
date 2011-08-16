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

import org.orbeon.saxon.om.NodeInfo
import org.orbeon.oxf.fb.ContainerOps._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.fb.ControlOps._

object SectionOps {

    // Delete the entire section and contained controls
    def deleteSection(section: NodeInfo) = deleteContainer(section)

    // Move the section up if possible
    def moveSectionUp(container: NodeInfo) =
        container.precedingElement foreach
            (moveContainer(container, _, moveElementBefore))

    // Move the section down if possible
    def moveSectionDown(container: NodeInfo) =
        container.followingElement foreach
            (moveContainer(container, _, moveElementAfter))

    // Move the section right if possible
    def moveSectionRight(container: NodeInfo) =
        precedingSection(container) foreach
            (moveContainer(container, _, moveElementIntoAsLast))

    // Move the section left if possible
    def moveSectionLeft(container: NodeInfo) =
        findAncestorContainers(container).headOption foreach
            (moveContainer(container, _, moveElementAfter))

    // TODO: Could also move into fr:tabview/fr:tab?
    def precedingSection(container: NodeInfo) = container precedingSibling "*:section" headOption

    // Find the section name given a descendant node
    def findSectionName(descendant: NodeInfo): String  =
        (descendant ancestor "*:section" map (getControlNameOption(_)) flatten).head

    // Find the section name for a given control name
    def findSectionName(doc: NodeInfo, controlName: String): Option[String] =
        findControlByName(doc, controlName) map (findSectionName(_))
}