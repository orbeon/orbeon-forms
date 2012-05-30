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

import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.oxf.fb.ControlOps._
import org.orbeon.oxf.fb.GridOps._
import org.orbeon.oxf.fb.ContainerOps._

/*
 * Form Builder: toolbox operations.
 */
object ToolboxOps {

    private val lhhaTemplate: NodeInfo =
        <template xmlns:xforms="http://www.w3.org/2002/xforms">
            <xforms:label ref=""/>
            <xforms:hint ref=""/>
            <xforms:help ref=""/>
            <xforms:alert ref="$fr-resources/detail/labels/alert"/>
        </template>

    // Insert a new control in a cell
    def insertNewControl(doc: NodeInfo, binding: NodeInfo) = {

        ensureEmptyTd(doc) match {
            case Some(gridTd) ⇒

                val newControlName = controlName(nextId(doc, "control"))

                // Insert control template
                val newControlElement: NodeInfo =
                    viewTemplate(binding) match {
                        case Some(viewTemplate) ⇒
                            // There is a specific template available
                            insert(into = gridTd, origin = viewTemplate).head
                        case _ ⇒
                            // No specific, create simple element with LHHA

                            // Try to find the binding QName (for now takes the first CSS rule and assume the form foo|bar)
                            val firstElementCSSName = (binding \@ "element" stringValue) split "," head
                            val elementQName = firstElementCSSName.replace('|', ':')

                            val controlElement = insert(into = gridTd, origin = elementInfo(resolveQName(binding, elementQName))).head
                            insert(into = controlElement, origin = lhhaTemplate \ *)
                            controlElement
                    }

                // Set default pointer to resources if there is an xforms:alert
                setvalue(newControlElement \ "*:alert" \@ "ref", "$fr-resources/detail/labels/alert")

                // Adjust bindings on newly inserted control
                renameControlByElement(newControlElement, newControlName)

                // Get control type
                // TODO: for now assume a literal 'xs:' prefix (should resolve namespace)
                val controlType = binding \ "*:metadata" \ "*:datatype" match {
                    case Seq(datatype, _*) ⇒ datatype.stringValue
                    case _ ⇒ "xs:string"
                }

                // Data holder may contain file attributes
                val instanceTemplate = binding \ "*:metadata" \ "*:templates" \ "*:instance"
                val dataHolder =
                    if (! instanceTemplate.isEmpty)
                        elementInfo(newControlName, (instanceTemplate.head \@ @*) ++ (instanceTemplate \ *))
                    else
                        elementInfo(newControlName)

                val lhhaNames = newControlElement \ * filter (e ⇒ Set("label", "help", "hint", "alert")(localname(e))) map (localname(_))
                val resourceHolder = elementInfo(newControlName, lhhaNames map (elementInfo(_)))

                // Insert data and resource holders
                insertHolders(
                    newControlElement,
                    dataHolder,
                    resourceHolder,
                    precedingControlNameInSectionForControl(newControlElement)
                )

                // Insert the bind element
                val bind = ensureBinds(doc, findContainerNames(gridTd) :+ newControlName)

                // Make sure there is a @bind instead of a @ref on the control
                delete(newControlElement \@ "ref")
                ensureAttribute(newControlElement, "bind", bind \@ "id" stringValue)

                // Set bind type if needed
                if (controlType != "xs:string")
                    insert(into = bind, origin = attributeInfo("type", controlType))

                debugDumpDocument("insert new control", doc)

                Some(newControlName)

            case _ ⇒
                // no empty td found/created so NOP
                None
        }
    }

    private def findGridInsertionPoint(inDoc: NodeInfo) =
        findSelectedTd(inDoc) match {
            case Some(currentTd) ⇒ // A td is selected

                val containers = findAncestorContainers(currentTd)

                // We must always have a parent (grid) and grandparent (possibly fr:body) container
                assert(containers.size >= 2)

                val parentContainer = containers.headOption
                val grandParentContainer = containers.tail.head

                // NOTE: At some point we could allow any grid bound and so with a name/id and bind
                //val newGridName = "grid-" + nextId(doc, "grid")

                (grandParentContainer, parentContainer, parentContainer)

            case _ ⇒ // No td is selected, add top-level grid
                val frBody = findFRBodyElement(inDoc)
                (frBody, childrenContainers(frBody) lastOption, None)
        }

    private def findSectionInsertionPoint(inDoc: NodeInfo) =
        findSelectedTd(inDoc) match {
            case Some(currentTd) ⇒ // A td is selected

                val containers = findAncestorContainers(currentTd)

                // We must always have a parent (grid) and grandparent (possibly fr:body) container
                assert(containers.size >= 2)

                // Idea: section is inserted after current section/tabview, NOT within current section. If there is no
                // current section/tabview, the section is inserted after the current grid.
                val grandParentContainer = containers.tail.head // section/tab, body
                val greatGrandParentContainerOption = containers.tail.tail.headOption

                greatGrandParentContainerOption match {
                    case Some(greatGrandParentContainer) ⇒ (greatGrandParentContainer, Some(grandParentContainer))
                    case None ⇒ (grandParentContainer, grandParentContainer \ * headOption)
                }

            case _ ⇒ // No td is selected, add top-level section
                val frBody = findFRBodyElement(inDoc)
                (frBody, childrenContainers(frBody) lastOption)
        }

    // Whether a section can be inserted
    def canInsertSection(inDoc: NodeInfo) =
        inDoc ne null

    // Whether a grid can be inserted
    def canInsertGrid(inDoc: NodeInfo) =
        (inDoc ne null) && findSelectedTd(inDoc).isDefined

    // Whether a control can be inserted
    def canInsertControl(inDoc: NodeInfo) =
        (inDoc ne null) && willEnsureEmptyTdSucceed(inDoc)

    // Insert a new grid
    def insertNewGrid(inDoc: NodeInfo) {

        val (into, after, _) = findGridInsertionPoint(inDoc)

        // The grid template
        val gridTemplate: NodeInfo =
            <fr:grid edit-ref="" id={nextId(inDoc, "tmp")}
                     xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                     xmlns:xhtml="http://www.w3.org/1999/xhtml">
                <xhtml:tr>
                    <xhtml:td id={nextId(inDoc, "tmp", useInstance = false)}/>
                </xhtml:tr>
            </fr:grid>

        // Insert after current level 2 if found, otherwise into level 1
        val newGrid = insert(into = into, after = after.toSeq, origin = gridTemplate)

        // Select first grid cell
        selectTd(newGrid \\ "*:td" head)

        debugDumpDocument("insert new grid", inDoc)
    }

    // Insert a new section with optionally a nested grid
    def insertNewSection(inDoc: NodeInfo, withGrid: Boolean) = {

        val (into, after) = findSectionInsertionPoint(inDoc)

        val newSectionName = controlName(nextId(inDoc, "section"))
        val precedingSectionName = after flatMap (getControlNameOption(_))

        // NOTE: use xxforms:update="full" so that xxf:dynamic can better update top-level XBL controls
        val sectionTemplate: NodeInfo =
            <fb:section id={sectionId(newSectionName)} bind={bindId(newSectionName)} edit-ref="" xxforms:update="full"
                        xmlns:xhtml="http://www.w3.org/1999/xhtml"
                        xmlns:xforms="http://www.w3.org/2002/xforms"
                        xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                        xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                <xforms:label ref={"$form-resources/" + newSectionName + "/label"}/>
                <xforms:help ref={"$form-resources/" + newSectionName + "/help"}/>{
                if (withGrid)
                    <fr:grid edit-ref="" id={nextId(inDoc, "tmp")}>
                        <xhtml:tr>
                            <xhtml:td id={nextId(inDoc, "tmp", useInstance = false)}/>
                        </xhtml:tr>
                    </fr:grid>
            }</fb:section>

        val newSectionElement = insert(into = into, after = after.toSeq, origin = sectionTemplate).head

        // Create and insert holders
        val resourceHolder = {

            val findUntitledSectionMessage = {
                val fbResources = asNodeInfo(model("fr-resources-model").get.getVariable("fr-form-resources"))
                evalOne(fbResources, "template/untitled-section/string()")
            }

            val elementContent = Seq(elementInfo("label", findUntitledSectionMessage), elementInfo("help"))
            elementInfo(newSectionName, elementContent)
        }

        insertHolders(newSectionElement, elementInfo(newSectionName), resourceHolder, precedingSectionName)

        // Insert the bind element
        ensureBinds(inDoc, findContainerNames(newSectionElement) :+ newSectionName)

        // Select first grid cell
        if (withGrid)
            selectTd(newSectionElement \\ "*:td" head)

        // TODO: Open label editor for newly inserted section

        debugDumpDocument("insert new section", inDoc)

        Some(newSectionElement)
    }

    // Insert a new repeat
    def insertNewRepeat(inDoc: NodeInfo) = {

        val (into, after, grid) = findGridInsertionPoint(inDoc)

        val newGridName = controlName(nextId(inDoc, "grid"))
        val templateInstanceId = templateId(newGridName)

        // Handle data template
        val modelElement = findModelElement(inDoc)
        modelElement \ "*:instance" filter (hasIdValue(_, templateInstanceId)) headOption match {
            case Some(templateInstance) ⇒
                // clear existing template instance content
                delete(templateInstance \ *)
                insert(into = templateInstance , origin = <dummy/>.copy(label = newGridName))

            case None ⇒
                // Insert template instance if not present
                val template: NodeInfo = <xforms:instance xmlns:xforms="http://www.w3.org/2002/xforms"
                                                          xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                                                          id={templateInstanceId} fb:readonly="true">{<dummy/>.copy(label = newGridName)}</xforms:instance>

                insert(into = modelElement, after = modelElement \ "*:instance" takeRight 1, origin = template)
        }

        // The grid template
        val gridTemplate: NodeInfo =
            <fr:grid edit-ref="" id={gridId(newGridName)} repeat="true" bind={bindId(newGridName)} origin={makeInstanceExpression(templateInstanceId)}
                     xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                     xmlns:xhtml="http://www.w3.org/1999/xhtml">
                <xhtml:tr>
                    <xhtml:td id={nextId(inDoc, "tmp", useInstance = false)}/>
                </xhtml:tr>
            </fr:grid>

        // Insert grid
        val newGridElement = insert(into = into, after = after.toSeq, origin = gridTemplate).head

        // Insert instance holder (but no resource holders)
        insertHolders(
            newGridElement,
            elementInfo(newGridName),
            Seq(),
            grid flatMap (precedingControlNameInSectionForGrid(_, true))
        )

        // Make sure binds are created
        ensureBinds(inDoc, findContainerNames(newGridElement) :+ newGridName)

        // Select new td
        selectTd(newGridElement \\ "*:td" head)

        debugDumpDocument("insert new repeat", inDoc)

        Some(newGridName)
    }

    // Insert a new section template
    def insertNewSectionTemplate(inDoc: NodeInfo, binding: NodeInfo) =
        // Insert new section first
        insertNewSection(inDoc, withGrid = false) foreach { section ⇒

            val selector = binding \@ "element" stringValue

            val model = findModelElement(inDoc)
            val xbl = model followingSibling (XBL → "xbl")
            val existingBindings = xbl child (XBL → "binding")

            // Insert binding into form if needed
            if (! (existingBindings \@ "element" === selector))
                insert(after = Seq(model) ++ xbl, origin = binding parent * )

            // Insert template into section
            val template = binding child (FB → "metadata") child (FB → "template") child *

            insert(into = section, after = section \ *, origin = template)
        }

    // Copy control to the clipboard
    def copyToClipboard(td: NodeInfo) {
        val name = getControlName(td \ * head)
        val xvc = asNodeInfo(model("fr-form-model").get.getVariable("xcv"))

        findControlByName(td, name) foreach { controlElement ⇒

            // Create <resource xml:lang="..."> containers
            val resourcesWithLang = findResourceHoldersWithLang(name) map {
                case (lang, holder) ⇒ elementInfo("resource", attributeInfo("xml:lang", lang) ++ holder)
            }

            // Clear and insert each clipboard element
            Map[String, Seq[NodeInfo]](
                "control"   → controlElement,
                "holder"    → findDataHolder(td, name).toSeq,
                "resources" → resourcesWithLang,
                "bind"      → findBindByName(td, name).toSeq) foreach {

                case (elementName, content) ⇒
                    delete(xvc \ elementName \ *)
                    insert(into = xvc \ elementName, origin = content)
            }
        }
    }

    // Cut control to the clipboard
    def cutToClipboard(td: NodeInfo) {
        copyToClipboard(td)
        deleteCellContent(td)
    }

    // Paste control from the clipboard
    def pasteFromClipboard(td: NodeInfo) {
        ensureEmptyTd(td) foreach { gridTd ⇒

            val xvc = asNodeInfo(model("fr-form-model").get.getVariable("xcv"))

            (xvc \ "control" \ * headOption) foreach { control ⇒
                val name = {
                    val requestedName = getControlName(control)

                    // Check if id is already in use
                    if (findControlById(td, controlId(requestedName)).isDefined) {
                        // If so create new id
                        val newName = controlName(nextId(td, "control"))

                        // Rename everything
                        // TODO: don't rename in place
                        renameControlByElement(control, newName)

                        (xvc \ "holder" \ *) ++ (xvc \ "resources" \ "resource" \ *) foreach
                            (rename(_, requestedName, newName))

                        (xvc \ "bind" \ * headOption) foreach
                            (renameBindByElement(_, newName))

                        newName
                    } else
                        requestedName
                }

                // Insert control and holders
                val newControlElement = insert(into = gridTd, origin = control).head
                insertHolders(
                    newControlElement,
                    xvc \ "holder" \ * head,
                    xvc \ "resources" \ "resource" map (r ⇒ (r \@ "*:lang" stringValue, r \ * head)),
                    precedingControlNameInSectionForControl(newControlElement)
                )

                // Create the bind and copy all attributes and content
                val bind = ensureBinds(gridTd, findContainerNames(gridTd) :+ name)
                (xvc \ "bind" \ * headOption) foreach { xvcBind ⇒
                    insert(into = bind, origin = (xvcBind \@ @*) ++ (xvcBind \ *))
                }
            }
        }
    }
}