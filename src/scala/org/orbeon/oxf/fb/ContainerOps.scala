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
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.GridOps._

object ContainerOps {

    // Hardcoded list of FB controls we know can contain others
    val containerElementNames = Set("section", "grid", "body") // TODO: "tab" is special because within "tabview"

    // XForms callers: get the name for a section or grid element or null (the empty sequence)
    def getContainerNameOrEmpty(elem: NodeInfo) = getControlNameOption(elem).orNull

    // Find ancestor sections and grids (including non-repeated grids) from leaf to root
    def findAncestorContainers(descendant: NodeInfo, includeSelf: Boolean = false) =
        if (includeSelf) descendant ancestorOrSelf * else descendant ancestor * filter
            (e => containerElementNames(localname(e)))

    // Find ancestor section and grid names from leaf to root
    def findContainerNames(descendant: NodeInfo): Seq[String] =
        findAncestorContainers(descendant) map (getControlNameOption(_)) flatten

    // Delete the entire container and contained controls
    def deleteContainer(container: NodeInfo) {
        // Go depth-first so we delete containers after all their content has been deleted
        childrenContainers(container) foreach (deleteContainer(_))

        // Delete all controls directly in the grid
        // NOTE: lack of object-orientation is ugly here
        if (localname(container) == "grid")
            container \\ "*:tr" \\ "*:td" foreach (deleteControl(_))

        // Delete container itself
        deleteContainerOnly(container)
    }

    def childrenContainers(container: NodeInfo) =
        container \ * filter (e => containerElementNames(localname(e)))

    def deleteContainerOnly(container: NodeInfo) = {

        val doc = container.getDocumentRoot

        getControlNameOption(container) foreach { containerName =>

            // Delete: holders, bind, templates, resources
            val toDelete =
                Seq(findDataHolder(doc, containerName),
                    findBindByName(doc, containerName),
                    instanceElement(doc, templateId(containerName)),
                    findTemplateHolder(container, containerName)) ++
                    (findResourceHolders(doc, containerName) map (Option(_))) flatten

            toDelete foreach (delete(_))
        }

        // Delete whole container element
        delete(container)
    }

    // Find all siblings of the given element with the given name, excepting the given element
    def findSiblingsWithName(element: NodeInfo, siblingName: String) =
        element.parent.get \ * filter
            (name(_) == siblingName) filterNot
                (_ isSameNodeInfo element)

    // Move a container based on a move function (typically up or down)
    def moveContainer(container: NodeInfo, otherContainer: NodeInfo, move: (NodeInfo, NodeInfo) => NodeInfo) {

        // Get names before moving the container
        val nameOption = getControlNameOption(container)
        val otherNameOption = getControlNameOption(otherContainer)

        val doc = container.getDocumentRoot

        // Move container control itself
        move(container, otherContainer)

        // Try to move based on name of other element
        (nameOption, otherNameOption) match {
            case (Some(name), Some(otherName)) =>

                // Move data holder only
                for {
                    holder <- findDataHolder(doc, name)
                    otherHolder <- findDataHolder(doc, otherName)
                } yield
                    move(holder, otherHolder)

                // Move bind
                for {
                    bind <- findBindByName(doc, name)
                    otherBind <- findBindByName(doc, otherName)
                } yield
                    move(bind, otherBind)

                // Try to move resource and template elements to a good place
                // TODO: We move the container resource holder, but we should also move together the contained controls' resource holders
                def firstControl(s: Seq[NodeInfo]) =
                    s filter (getControlNameOption(_).isDefined) headOption

                def tryToMoveHolders(siblingName: String, moveOp: (NodeInfo, NodeInfo) => NodeInfo) =
                    findResourceAndTemplateHolders(doc, name) foreach { holder =>
                        findSiblingsWithName(holder, siblingName).headOption foreach
                            (moveOp(holder, _))
                    }

                val movedContainer = findControlById(doc, container \@ "id").get // must get new reference

                (firstControl(movedContainer preceding *), firstControl(movedContainer following *)) match {
                    case (Some(preceding), _) => tryToMoveHolders(getControlName(preceding), moveElementAfter)
                    case (_, Some(following)) => tryToMoveHolders(getControlName(following), moveElementBefore)
                    case _ =>
                }

            case _ =>
        }
    }
}