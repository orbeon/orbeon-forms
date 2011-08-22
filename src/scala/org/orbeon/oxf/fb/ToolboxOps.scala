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
    def insertNewControl(doc: NodeInfo, binding: NodeInfo) {

        ensureEmptyTd(doc) match {
            case Some(gridTd) =>

                val newControlId = nextId(doc, "control")
                val newControlName = "control-" + newControlId

                // Insert control template
                val newControlElement: NodeInfo =
                    binding \ "*:metadata" \ "*:template" \ * match {
                        case Seq(template, _*) =>
                            // There is a specific template available
                            insert(into = gridTd, origin = template).head
                        case _ =>
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
                    case Seq(datatype, _*) => datatype.stringValue
                    case _ => "xs:string"
                }

                // Data holder may contain file attributes
                val dataHolder =
                    if (Set("xs:anyURI", "xforms:anyURI")(controlType))
                        elementInfo(newControlName, Seq("filename", "mediatype", "size") map (attributeInfo(_)))
                    else
                        elementInfo(newControlName)

                val lhhaNames = newControlElement \ * filter (e => Set("label", "help", "hint", "alert")(localname(e))) map (localname(_))
                val resourceHolder = elementInfo(newControlName, lhhaNames map (elementInfo(_)))

                // Insert data and resource holders
                insertHolders(
                    newControlElement,
                    dataHolder,
                    resourceHolder,
                    precedingControlNameInSectionForControl(newControlElement)
                )

                // Insert the bind element
                val bind = ensureBinds(doc, findContainerNames(gridTd) :+ newControlName, isCustomInstance)

                // Make sure there is a @bind instead of a @ref on the control
                delete(newControlElement \@ "ref")
                ensureAttribute(newControlElement, "bind", bind \@ "id" stringValue)

                // Set bind type if needed
                if (controlType != "xs:string")
                    insert(into = bind, origin = attributeInfo("type", controlType))

                debugDumpDocument("insert new control", doc)

                // Open label editor for newly inserted control -->
                // TODO
//                <xforms:toggle case="fr-inplace-fb-control-label-control-edit"/>
//                <xforms:setfocus control="fr-inplace-fb-control-label-control-input"/>

            case _ => // no empty td found/created so NOP
        }
    }

    // Insert a new grid
    def insertNewGrid(doc: NodeInfo) {

        findSelectedTd(doc) match {
            case Some(currentTd) =>

                val containers = findAncestorContainers(currentTd)

                // We must always have a parent (grid) and grandparent (possibly fr:body) container
                assert(containers.size >= 2)

                val parentContainer = containers.head
                val grandParentContainer = containers.tail.head

                // NOTE: At some point we could allow any grid bound and so with a name/id and bind
                //val newGridName = "grid-" + nextId(doc, "grid")

                // The grid template
                val gridTemplate: NodeInfo =
                    <fr:grid edit-ref=""
                             xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                             xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xhtml:tr>
                            <xhtml:td id={tdId("td-" + nextId(doc, "td"))}/>
                        </xhtml:tr>
                    </fr:grid>

                // Insert after current level 2 if found, otherwise into level 1
                val newGrid = insert(into = grandParentContainer, after = parentContainer, origin = gridTemplate)

                // Select first grid cell
                selectTd(newGrid \\ "*:td" head)

                debugDumpDocument("insert new grid", doc)

            case _ => // NOP
        }
    }

    // Insert a new section
    def insertNewSection(doc: NodeInfo) {

        findSelectedTd(doc) match {
            case Some(currentTd) => // A td is selected

                val containers = findAncestorContainers(currentTd)

                // We must always have a parent (grid) and grandparent (possibly fr:body) container
                assert(containers.size >= 2)

                // Idea: section is inserted after current section/tabview, NOT within current section. If there is no
                // current section/tabview, the section is inserted after the current grid.
                val grandParentContainer = containers.tail.head // section/tab, body
                val greatGrandParentContainerOption = containers.tail.tail.headOption

                val (into, after) =
                    greatGrandParentContainerOption match {
                        case Some(greatGrandParentContainer) => (greatGrandParentContainer, Some(grandParentContainer))
                        case None => (grandParentContainer, grandParentContainer \ * headOption)
                    }

                val newSectionName = "section-" + nextId(doc, "section")
                val precedingSectionName = after flatMap (getControlNameOption(_))

                val sectionTemplate: NodeInfo =
                    <fb:section id={sectionId(newSectionName)} bind={bindId(newSectionName)} edit-ref=""
                                xmlns:xhtml="http://www.w3.org/1999/xhtml"
                                xmlns:xforms="http://www.w3.org/2002/xforms"
                                xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                                xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                        <xforms:label ref={"$form-resources/" + newSectionName + "/label"}/>
                        <xforms:help ref={"$form-resources/" + newSectionName + "/help"}/>
                        <fr:grid edit-ref="">
                            <xhtml:tr>
                                <xhtml:td id={tdId("td-" + nextId(doc, "td"))}/>
                            </xhtml:tr>
                        </fr:grid>
                    </fb:section>

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
                ensureBinds(doc, findContainerNames(newSectionElement) :+ newSectionName, isCustomInstance)

                // Select first grid cell
                selectTd(newSectionElement \\ "*:td" head)

                // TODO: Open label editor for newly inserted section

                debugDumpDocument("insert new section", doc)

            case _ => // No td is selected, add top-level section
                // TODO
        }
    }

    // Insert a new repeat
    def insertNewRepeat(doc: NodeInfo) {

        findSelectedTd(doc) match {
            case Some(currentTd) =>

                val currentTdContainers = findAncestorContainers(currentTd)

                // We must always have a parent (grid) and grandparent (possibly fr:body) container
                assert(currentTdContainers.size >= 2)

                val currentTdGrid = currentTdContainers.head // grid
                val currentTdGrandParentContainer = currentTdContainers.tail.head // section/tab, body

                val newGridName = "grid-" + nextId(doc, "grid")
                val templateInstanceId = templateId(newGridName)

                // Handle data template
                val modelElement = findModelElement(doc)
                modelElement \ "*:instance" filter (hasId(_, templateInstanceId)) headOption match {
                    case Some(templateInstance) =>
                        // clear existing template instance
                        delete(templateInstance \ *)
                        insert(into = templateInstance , origin = <dummy/>.copy(label = newGridName))

                    case None =>
                        // Insert template instance if not present
                        val template: NodeInfo = <xforms:instance xmlns:xforms="http://www.w3.org/2002/xforms"
                                                                  xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
                                                                  id={templateInstanceId} xxforms:readonly="true">{<dummy/>.copy(label = newGridName)}</xforms:instance>

                        insert(into = modelElement, after = modelElement \ "*:instance" takeRight 1, origin = template)
                }

                // The grid template
                val gridTemplate: NodeInfo =
                    <fr:grid edit-ref="" id={gridId(newGridName)} repeat="true" bind={bindId(newGridName)} origin={makeInstanceExpression(templateInstanceId)}
                             xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                             xmlns:xhtml="http://www.w3.org/1999/xhtml">
                        <xhtml:tr>
                            <xhtml:td id={tdId("td-" + nextId(currentTd, "td"))}/>
                        </xhtml:tr>
                    </fr:grid>

                // Insert grid
                val newGridElement = insert(into = currentTdGrandParentContainer, after = currentTdGrid, origin = gridTemplate).head

                // Insert instance holder (but no resource holders)
                insertHolders(
                    newGridElement,
                    elementInfo(newGridName),
                    Seq(),
                    precedingControlNameInSectionForGrid(currentTdGrid, true)
                )

                // Make sure binds are created
                ensureBinds(doc, findContainerNames(newGridElement) :+ newGridName, isCustomInstance)

                // Select new td
                selectTd(newGridElement \\ "*:td" head)

                debugDumpDocument("insert new repeat", doc)

            case _ => // NOP
        }
    }

    // Copy control to the clipboard
    def copyToClipboard(td: NodeInfo) {
        val name = getControlName(td \ * head)
        val xvc = asNodeInfo(model("fr-form-model").get.getVariable("xcv"))

        findControlByName(td, name) foreach { controlElement =>

            // Create <resource xml:lang="..."> containers
            val resourcesWithLang = findResourceHoldersWithLang(td, name) map {
                case (lang, holder) => elementInfo("resource", attributeInfo("xml:lang", lang) ++ holder)
            }

            // Clear and insert each clipboard element
            Map[String, Seq[NodeInfo]](
                "control"   -> controlElement,
                "holder"    -> findDataHolder(td, name).toSeq,
                "resources" -> resourcesWithLang,
                "bind"      -> findBindByName(td, name).toSeq) foreach {

                case (elementName, content) =>
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
        ensureEmptyTd(td) foreach { gridTd =>

            val xvc = asNodeInfo(model("fr-form-model").get.getVariable("xcv"))

            (xvc \ "control" \ * headOption) foreach { control =>
                val controlName = {
                    val requestedName = getControlName(control)

                    // Check if id is already in use
                    if (findControlById(td, controlId(requestedName)).isDefined) {
                        // If so create new id
                        val newName = "control-" + nextId(td, "control")

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
                    xvc \ "resources" \ "resource" map (r => (r \@ "*:lang" stringValue, r \ * head)),
                    precedingControlNameInSectionForControl(newControlElement)
                )

                // Create the bind and copy all attributes and content
                val bind = ensureBinds(gridTd, findContainerNames(gridTd) :+ controlName, isCustomInstance)
                (xvc \ "bind" \ * headOption) foreach { xvcBind =>
                    insert(into = bind, origin = (xvcBind \@ @*) ++ (xvcBind \ *))
                }
            }
        }
    }
}