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
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.GridOps._
import FormBuilderFunctions._
import org.orbeon.oxf.xforms.XFormsUtils

object ContainerOps {

    // Node tests
    private val GridElementTest: Test    = FR → "grid"
    private val SectionElementTest: Test = FR → "section"
    private val GroupElementTest: Test   = XF → "group"
    private val ContainerElementTest     = SectionElementTest || GridElementTest

    // Predicates
    val IsGrid: NodeInfo ⇒ Boolean = _ self GridElementTest
    val IsSection: NodeInfo ⇒ Boolean = _ self SectionElementTest

    val IsContainer: NodeInfo ⇒ Boolean =
        node ⇒ (node self ContainerElementTest) || ((node self GroupElementTest) && node.attClasses("fb-body"))

    // Namespace URL a section template component must match
    private val ComponentURI = """^http://orbeon.org/oxf/xml/form-builder/component/([^/]+)/([^/]+)$""".r

    val IsSectionTemplateContent: NodeInfo ⇒ Boolean =
        container ⇒ (container parent * exists IsSection) && ComponentURI.findFirstIn(namespaceURI(container)).nonEmpty

    def containerById(containerId: String): NodeInfo = {
        // Support effective id, to make it easier to use from XForms (i.e. no need to call XFormsUtils.getStaticIdFromId every time)
        val staticId = XFormsUtils.getStaticIdFromId(containerId)
        byId(fbFormInstance, staticId) filter IsContainer head
    }

    def controlsInContainer(containerId: String): Int = (containerById(containerId) \\ "*:td" \ *).length

    // XForms callers: get the name for a section or grid element or null (the empty sequence)
    def getContainerNameOrEmpty(elem: NodeInfo) = getControlNameOption(elem).orNull

    // Find ancestor sections and grids (including non-repeated grids) from leaf to root
    def findAncestorContainers(descendant: NodeInfo, includeSelf: Boolean = false) =
        (if (includeSelf) descendant ancestorOrSelf * else descendant ancestor *) filter IsContainer

    // Find ancestor section and grid names from root to leaf
    def findContainerNames(descendant: NodeInfo): Seq[String] =
        findAncestorContainers(descendant).reverse map (getControlNameOption(_)) flatten

    // Find the new td to select if we are removing the currently selected td
    def findNewTdToSelect(inDoc: NodeInfo, tdsToDelete: Seq[NodeInfo]) =
        findSelectedTd(inDoc) match {
            case Some(selectedTd) if tdsToDelete contains selectedTd ⇒
                // Prefer trying following before preceding, as things move up and left when deleting
                // NOTE: Could improve this by favoring things "at same level", e.g. stay in grid if possible, then
                // stay in section, etc.
                (followingTd(selectedTd) filterNot (tdsToDelete contains _) headOption) orElse
                        (precedingTds(selectedTd) filterNot (tdsToDelete contains _) headOption)
            case _ ⇒
                None
        }

    // A container's children containers
    def childrenContainers(container: NodeInfo) =
        container \ * filter IsContainer

    // A container's children grids (including repeated grids)
    def childrenGrids(container: NodeInfo) =
        container \ * filter IsGrid

    // Delete the entire container and contained controls
    def deleteContainerById(canDelete: NodeInfo ⇒ Boolean, containerId: String): Unit = {
        val container = containerById(containerId)
        if (canDelete(container))
            deleteContainer(container)
    }

    def deleteContainer(container: NodeInfo) = {

        // Find the new td to select if we are removing the currently selected td
        val newTdToSelect = findNewTdToSelect(container, container \\ "*:td")

        def recurse(container: NodeInfo): Seq[NodeInfo] = {

            // NOTE: Deleting is tricky because NodeInfo is mutation-averse as it keeps a node's index, among others.
            // So deleting a node under a given NodeInfo can cause the position of following siblings to be out of date
            // and cause errors. So we delete from back to front. But a safer solution should be found.

            // Go depth-first so we delete containers after all their content has been deleted
            // NOTE: Use toList to make sure we are not lazy, otherwise items might be deleted as we go!
            val children = childrenContainers(container).reverse.toList flatMap recurse

            val gridContent =
                if (IsGrid(container))
                    container \\ "*:tr" \\ "*:td" \ * filter IsControl reverse
                else
                    Seq()

            children ++ gridContent :+ container
        }

        // Start with top-level container
        val controls = recurse(container)

        //  Delete all controls in order
        controls flatMap controlElementsToDelete foreach (delete(_))

        // Adjust selected td if needed
        newTdToSelect foreach selectTd
    }

    // Find all siblings of the given element with the given name, excepting the given element
    def findSiblingsWithName(element: NodeInfo, siblingName: String) =
        element parent * child * filter
                (name(_) == siblingName) filterNot
                (_ == element)

    // Move a container based on a move function (typically up or down)
    def moveContainer(container: NodeInfo, otherContainer: NodeInfo, move: (NodeInfo, NodeInfo) ⇒ NodeInfo) {

        // Get names before moving the container
        val nameOption = getControlNameOption(container)
        val otherNameOption = getControlNameOption(otherContainer)

        val doc = container.getDocumentRoot

        // Move container control itself
        move(container, otherContainer)

        // Try to move based on name of other element
        (nameOption, otherNameOption) match {
            case (Some(name), Some(otherName)) ⇒

                // Move data holder only
                for {
                    holder ← findDataHolders(doc, name)
                    otherHolder ← findDataHolders(doc, otherName)
                } yield
                    move(holder, otherHolder)

                // Move bind
                for {
                    bind ← findBindByName(doc, name)
                    otherBind ← findBindByName(doc, otherName)
                } yield
                    move(bind, otherBind)

                // Try to move resource and template elements to a good place
                // TODO: We move the container resource holder, but we should also move together the contained controls' resource holders
                def firstControl(s: Seq[NodeInfo]) =
                    s filter (getControlNameOption(_).isDefined) headOption

                def tryToMoveHolders(siblingName: String, moveOp: (NodeInfo, NodeInfo) ⇒ NodeInfo) =
                    findResourceAndTemplateHolders(doc, name) foreach {
                        holder ⇒
                            findSiblingsWithName(holder, siblingName).headOption foreach
                                    (moveOp(holder, _))
                    }

                val movedContainer = byId(doc, container \@ "id").get // must get new reference

                (firstControl(movedContainer preceding *), firstControl(movedContainer following *)) match {
                    case (Some(preceding), _) ⇒ tryToMoveHolders(getControlName(preceding), moveElementAfter)
                    case (_, Some(following)) ⇒ tryToMoveHolders(getControlName(following), moveElementBefore)
                    case _ ⇒
                }

            case _ ⇒
        }
    }

    // Return a td's preceding tds in the hierarchy of containers
    def precedingTds(td: NodeInfo) = {
        val preceding = td preceding "*:td"
        preceding intersect (findAncestorContainers(td).last descendant "*:td")
    }

    // Return a td's following tds in the hierarchy of containers
    def followingTd(td: NodeInfo) = {
        val following = td following "*:td"
        following intersect (findAncestorContainers(td).last descendant "*:td")
    }

    // Return all the container controls in the view
    def getAllContainerControlsWithIds(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filter IsContainer

    def getAllContainerControls(inDoc: NodeInfo) = findFRBodyElement(inDoc) descendant * filter IsContainer

    // Various counts
    def countSections(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filter IsSection size

    def countAllGrids(inDoc: NodeInfo) = findFRBodyElement(inDoc) descendant * filter IsGrid size

    // includes repeated grids
    def countRepeats(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filter IsRepeat size

    def countGrids(inDoc: NodeInfo) = countAllGrids(inDoc) - countRepeats(inDoc)

    // non-repeated grids
    def countSectionTemplates(inDoc: NodeInfo) = findFRBodyElement(inDoc) descendant * filter IsSectionTemplateContent size

    def countAllNonContainers(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filterNot IsContainer size

    def countAllContainers(inDoc: NodeInfo) = getAllContainerControls(inDoc).size

    def countAllControls(inDoc: NodeInfo) = countAllContainers(inDoc) + countAllNonContainers(inDoc) + countSectionTemplates(inDoc)

    // Whether it is possible to move an item into the given container
    // Currently: must be a section without section template content
    // Later: fr:tab (maybe fr:tabview), wizard
    def canMoveInto(container: NodeInfo) =
        IsSection(container) && ! (container \ * exists IsSectionTemplateContent)

    def sectionsWithTemplates(inDoc: NodeInfo) =
        findFRBodyElement(inDoc) descendant * filter IsSection filter (_ \ * exists IsSectionTemplateContent)

    // See: https://github.com/orbeon/orbeon-forms/issues/633
    def deleteSectionTemplateContentHolders(inDoc: NodeInfo) = {

        // Find data holders for all section templates
        val holders =
            for {
                section     ← sectionsWithTemplates(inDoc)
                controlName ← getControlNameOption(section).toList
                holder      ← findDataHolders(inDoc, controlName)
            } yield
                holder

        // Delete all elements underneath those holders
        holders foreach { holder ⇒
            delete(holder \ *)
        }
    }
}