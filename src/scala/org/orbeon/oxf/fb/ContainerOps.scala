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

object ContainerOps {

    // Get the name for a section or grid element
    def getContainerName(elem: NodeInfo) =
        (elem \@ "bind" headOption) map
            (bind => controlName(bind.getStringValue))

    // XForms callers: get the name for a section or grid element or null (the empty sequence)
    def getContainerNameOrEmpty(elem: NodeInfo) = getContainerName(elem).orNull

    // Find ancestor sections and grids (including non-repeated grids) from leaf to root
    def findContainers(descendant: NodeInfo) =
        descendant ancestor * filter (e => Set("section", "grid")(localname(e)))

    def findContainersOrSelf(descendantOrSelf: NodeInfo) =
        descendantOrSelf ancestorOrSelf  * filter (e => Set("section", "grid")(localname(e)))

    // Find ancestor section and grid names from leaf to root
    def findContainerNames(descendant: NodeInfo): Seq[String] =
        findContainers(descendant) map (getContainerName(_)) flatten

    // Delete the entire container and contained controls
    def deleteContainer(container: NodeInfo) {

        // Delete all descendant controls
        container \\ "*:tr" \\ "*:td" foreach (deleteControl(_))

        def deleteOne(f: String => Option[NodeInfo]) =
            getContainerName(container) flatMap
                (gridName => f(gridName)) foreach
                    (delete(_))

        // Delete data holder, bind and repeat template if present
        deleteOne(containerName => findDataHolder(container, containerName))
        deleteOne(containerName => findBindByName(container, containerName))
        deleteOne(containerName => findModelElement(container) \ "*:instance" filter (hasId(_, templateId(containerName))) headOption)

        // Delete whole container element
        delete(container)
    }

    // Move a container based on a move function (typically up or down). The container remains at the same hierarchical level.
    def moveContainer(container: NodeInfo, otherContainer: NodeInfo, move: (NodeInfo, NodeInfo) => Unit) {

        // Find all siblings of the given element with the given name, excepting the given element
        def findSiblingsWithName(element: NodeInfo, siblingName: String) =
            element.parent.get \ * filter
                (name(_) == siblingName) filterNot
                    (_ isSameNodeInfo element)

        // Get names before moving the container
        val nameOption = getContainerName(container)
        val otherNameOption = getContainerName(otherContainer)

        val doc = container.getDocumentRoot

        // Move container itself
        move(container, otherContainer)

        // Try to move based on name of other element
        tupleOption(nameOption, otherNameOption) foreach {
            case (name, otherName) =>

                // Move data, resources, and template holders
                for {
                    holder <- findHolders(doc, name)
                    sibling <- findSiblingsWithName(holder, otherName) take 1
                } yield
                    move(holder, sibling)

                // Move bind
                for {
                    bind <- findBindByName(doc, name)
                    otherBind <- findBindByName(doc, otherName)
                    if bind.parent.get isSameNodeInfo otherBind.parent.get // what if not?
                } yield
                    move(bind, otherBind)
        }
    }

    def moveContainerLR(container: NodeInfo, otherContainer: NodeInfo, move: (NodeInfo, NodeInfo) => Unit) {

        // Get names before moving the container
        val nameOption = getContainerName(container)
        val otherNameOption = getContainerName(otherContainer)

        val doc = container.getDocumentRoot

        // Move container itself
        move(container, otherContainer)

        // Try to move based on name of other element
        tupleOption(nameOption, otherNameOption) foreach {
            case (name, otherName) =>

                // Move data holder only
                for {
                    holder <- findDataHolder(doc, name)
                    otherHolder <- findDataHolder(doc, otherName)
                } yield
                    move(holder, otherHolder)

                // TODO: adjust resource holders for cosmetics

                // Move bind
                for {
                    bind <- findBindByName(doc, name)
                    otherBind <- findBindByName(doc, otherName)
                } yield
                    move(bind, otherBind)
        }
    }
}