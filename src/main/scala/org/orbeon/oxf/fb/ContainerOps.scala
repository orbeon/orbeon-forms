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
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.fr.FormRunner._

trait ContainerOps extends ControlOps {

    self: GridOps ⇒ // funky dependency, to resolve at some point

    def containerById(containerId: String): NodeInfo = {
        // Support effective id, to make it easier to use from XForms (i.e. no need to call XFormsUtils.getStaticIdFromId every time)
        val staticId = XFormsUtils.getStaticIdFromId(containerId)
        byId(fbFormInstance, staticId) filter IsContainer head
    }

    def controlsInContainer(containerId: String): Int = (containerById(containerId) \\ "*:td" \ *).length

    // Find all siblings of the given element with the given name, excepting the given element
    def findSiblingsWithName(element: NodeInfo, siblingName: String) =
        element parent * child * filter
                (_.name == siblingName) filterNot
                (_ == element)

    // Return all the container controls in the view
    def getAllContainerControlsWithIds(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filter IsContainer

    def getAllContainerControls(inDoc: NodeInfo) = findFRBodyElement(inDoc) descendant * filter IsContainer

    // Various counts
    def countSections(inDoc: NodeInfo)         = getAllControlsWithIds(inDoc)          count IsSection
    def countAllGrids(inDoc: NodeInfo)         = findFRBodyElement(inDoc) descendant * count IsGrid
    def countRepeats(inDoc: NodeInfo)          = getAllControlsWithIds(inDoc)          count IsRepeat
    def countSectionTemplates(inDoc: NodeInfo) = findFRBodyElement(inDoc) descendant * count IsSectionTemplateContent

    def countGrids(inDoc: NodeInfo)            = countAllGrids(inDoc) - countRepeats(inDoc)
    def countAllNonContainers(inDoc: NodeInfo) = getAllControlsWithIds(inDoc) filterNot IsContainer size
    def countAllContainers(inDoc: NodeInfo)    = getAllContainerControls(inDoc).size
    def countAllControls(inDoc: NodeInfo)      = countAllContainers(inDoc) + countAllNonContainers(inDoc) + countSectionTemplates(inDoc)

    // Delete the entire container and contained controls
    def deleteContainerById(canDelete: NodeInfo ⇒ Boolean, containerId: String): Unit = {
        val container = containerById(containerId)
        if (canDelete(container))
            deleteContainer(container)
    }

    def deleteContainer(container: NodeInfo) = {

        val doc = container.getDocumentRoot

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

        // Update templates
        // NOTE: Could skip if top-level repeat
        updateTemplates(doc)

        // Adjust selected td if needed
        newTdToSelect foreach selectTd
    }

    // Move a container based on a move function
    def moveContainer(container: NodeInfo, otherContainer: NodeInfo, move: (NodeInfo, NodeInfo) ⇒ NodeInfo) {

        // Get names before moving the container
        val nameOption      = getControlNameOpt(container)
        val otherNameOption = getControlNameOpt(otherContainer)

        val doc = container.getDocumentRoot

        // Move container control itself
        move(container, otherContainer)

        // Try to move holders and binds based on name of other element
        (nameOption, otherNameOption) match {
            case (Some(name), Some(otherName)) ⇒

                // Move data holder only
                for {
                    holder      ← findDataHolders(doc, name)
                    otherHolder ← findDataHolders(doc, otherName)
                } yield
                    move(holder, otherHolder)

                // Move bind
                for {
                    bind      ← findBindByName(doc, name)
                    otherBind ← findBindByName(doc, otherName)
                } yield
                    move(bind, otherBind)

                // Try to move resource elements to a good place
                // TODO: We move the container resource holder, but we should also move together the contained controls' resource holders
                def firstControl(s: Seq[NodeInfo]) =
                    s find (getControlNameOpt(_).isDefined)

                def tryToMoveHolders(siblingName: String, moveOp: (NodeInfo, NodeInfo) ⇒ NodeInfo) =
                    findResourceHolders(name) foreach {
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

                // Moving sections can impact templates
                updateTemplates(doc)

            case _ ⇒
        }
    }

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
                controlName ← getControlNameOpt(section).toList
                holder      ← findDataHolders(inDoc, controlName)
            } yield
                holder

        // Delete all elements underneath those holders
        holders foreach { holder ⇒
            delete(holder \ *)
        }
    }

    def setRepeatProperties(inDoc: NodeInfo, controlName: String, repeat: Boolean, min: Int, max: Int): Unit = {

        val controlOpt     = findControlByName(inDoc, controlName)
        val wasRepeat      = controlOpt exists (_.attValue("repeat") == "true")

        // Update control attributes first
        controlOpt foreach { control ⇒
            // A missing or invalid value is taken as the default value: 0 for min, unbounded for max. In both cases, we
            // don't set the attribute value. This means that in the end we only set positive integer values.
            toggleAttribute(control, "repeat",   "true",       repeat)
            toggleAttribute(control, "template", makeInstanceExpression(templateId(controlName)), repeat)
            toggleAttribute(control, "min",      min.toString, repeat && min > 0)
            toggleAttribute(control, "max",      max.toString, repeat && max > 0)

            if (! wasRepeat && repeat) {
                // Insert new bind and template

                // Insert nested iteration bind
                val oldNestedBinds = findBindByName(inDoc, controlName).toList / *
                delete(oldNestedBinds)
                ensureBindsByName(inDoc, controlName) // uses the new `repeat="true"` attribute which implies the nested iteration element
                val controlBind   = findBindByName(inDoc, controlName)
                val iterationBind = controlBind.toList / *
                insert(into = iterationBind, origin = oldNestedBinds)

                // Insert nested iteration data holders
                // NOTE: There can be multiple existing data holders due to enclosing repeats
                findDataHolders(inDoc, controlName, withIterations = false) foreach { holder ⇒
                    val nestedHolders = holder / *
                    delete(nestedHolders)
                    insert(into = holder, origin = elementInfo(iterationName(controlName), nestedHolders))
                }

                // Update existing templates
                // NOTE: Could skip if top-level repeat
                updateTemplates(inDoc)

                // Ensure new template rooted at iteration
                ensureTemplateReplaceContent(inDoc, controlName, createTemplateContentFromBind(iterationBind.head))

            } else if (wasRepeat && ! repeat) {
                // Remove bind, holders and template

                // Move bind up
                val controlBind = findBindByName(inDoc, controlName).toList
                val oldNestedBinds = controlBind / * / *
                delete(controlBind / *)
                insert(into = controlBind, origin = oldNestedBinds)

                // Mover data holders up and keep only the first iteration
                findDataHolders(inDoc, controlName, withIterations = false) foreach { holder ⇒
                    val nestedHolders = holder / * take 1 child *
                    delete(holder / *)
                    insert(into = holder, origin = nestedHolders)
                }

                // Remove template
                findTemplateInstance(inDoc, controlName) foreach (delete(_))
                
                // Update existing templates
                updateTemplates(inDoc)
                
            } else if (repeat) {
                // Template should already exists an should have already been renamed if needed
                // MAYBE: Ensure template just in case.
            } else if (! repeat) {
                // Template should not exist
                // MAYBE: Delete template just in case.
            }
        }
    }

    def renameTemplate(doc: NodeInfo, oldName: String, newName: String): Unit =
        for {
            root     ← templateRoot(doc, oldName)
            instance ← root.parentOption
        } locally {
            ensureAttribute(instance, "id", templateId(newName))
        }

    def findTemplateInstance(doc: NodeInfo, controlName: String) =
        instanceElement(doc, templateId(controlName))

    def ensureTemplateReplaceContent(inDoc: NodeInfo, controlName: String, content: NodeInfo): Unit = {

        val templateInstanceId = templateId(controlName)
        val modelElement = findModelElement(inDoc)
        modelElement \ "*:instance" find (hasIdValue(_, templateInstanceId)) match {
            case Some(templateInstance) ⇒
                // clear existing template instance content
                delete(templateInstance \ *)
                insert(into = templateInstance , origin = content)

            case None ⇒
                // Insert template instance if not present
                val template: NodeInfo =
                    <xf:instance xmlns:xf="http://www.w3.org/2002/xforms"
                                 xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                                 id={templateInstanceId}
                                 fb:readonly="true">{nodeInfoToElem(content)}</xf:instance>

                insert(into = modelElement, after = modelElement \ "*:instance" takeRight 1, origin = template)
        }
    }

    // Create an instance template based on a hierarchy of binds rooted at the given bind
    def createTemplateContentFromBind(bind: NodeInfo): NodeInfo = {
        val e = elementInfo(findBindName(bind))
        // TODO: Insert instance template as is done in insertNewControl
        insert(into = e, origin = bind / "*:bind" map createTemplateContentFromBind)
        e
    }

    // Make sure all template instances reflect the current bind structure
    def updateTemplates(inDoc: NodeInfo): Unit =
        for {
            templateInstance ← templateInstanceElements(inDoc: NodeInfo)
            name             = controlName(templateInstance attValue "id")
            control          ← findControlByName(inDoc, name)
            repeatedSection  = IsRepeat(control) && IsSection(control)
            bind             ← findBindByName(inDoc, if (repeatedSection) iterationName(name) else name)
        } locally {
            ensureTemplateReplaceContent(inDoc, name, createTemplateContentFromBind(bind))
        }
}