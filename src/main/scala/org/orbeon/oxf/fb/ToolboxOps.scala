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
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.xml.XMLConstants.XML_URI
import org.orbeon.oxf.xforms.xbl.BindingDescriptor._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.fr.FormRunner

/*
 * Form Builder: toolbox operations.
 */
object ToolboxOps {

    val LHHAResourceNamesToInsert = LHHANames - "alert"

    // NOTE: Help is added when needed
    private val lhhaTemplate: NodeInfo =
        <template xmlns:xf="http://www.w3.org/2002/xforms">
            <xf:label ref=""/>
            <xf:hint ref=""/>
            <xf:alert ref=""/>
        </template>

    // Insert a new control in a cell
    def insertNewControl(doc: NodeInfo, binding: NodeInfo) = {

        ensureEmptyTd(doc) match {
            case Some(gridTd) ⇒

                val newControlName = controlName(nextId(doc, "control"))

                // Insert control template
                val newControlElement: NodeInfo =
                    findViewTemplate(binding) match {
                        case Some(viewTemplate) ⇒
                            // There is a specific template available
                            val controlElement = insert(into = gridTd, origin = viewTemplate).head
                            // xf:help might be in the template, but we don't need it as it is created on demand
                            delete(controlElement \ "help")
                            controlElement
                        case _ ⇒
                            // No specific, create simple element with LHHA

                            // Try to find the binding QName (for now takes the first CSS rule and assume the form foo|bar)
                            val firstElementCSSName = (binding \@ "element" stringValue) split "," head
                            val elementQName = firstElementCSSName.replace('|', ':')

                            val controlElement = insert(into = gridTd, origin = elementInfo(binding.resolveQName(elementQName))).head
                            insert(into = controlElement, origin = lhhaTemplate \ *)
                            controlElement
                    }

                // Set default pointer to resources if there is an xf:alert
                setvalue(newControlElement \ "*:alert" \@ "ref", OldStandardAlertRef)

                // Adjust bindings on newly inserted control
                renameControlByElement(newControlElement, newControlName)

                // Data holder may contain file attributes
                val dataHolder = newDataHolder(newControlName, binding)

                val formLanguages = allLangs(formResourcesRoot)
                val resourceHolders = formLanguages map { formLang ⇒
                    // Elements for LHHA resources, only keeping those referenced from the view (e.g. a button has no hint)
                    val lhhaResourceEls = {
                        val lhhaNames = newControlElement \ * map (_.localname) filter LHHAResourceNamesToInsert
                        lhhaNames map (elementInfo(_))
                    }

                    // Resource holders from XBL metadata
                    val xblResourceEls = binding / "*:metadata" / "*:templates" / "*:resources" / *

                    // Template items, if needed
                    val itemsResourceEls =
                        if (hasEditor(newControlElement, "static-itemset")) {
                            val fbResourceInFormLang = FormRunner.formResourcesInLang(formLang)
                            val originalTemplateItems = fbResourceInFormLang \ "template" \ "items" \ "item"
                            if (hasEditor(newControlElement, "item-hint")) {
                                // Supports hint: keep hint we have in the resources.xml
                                originalTemplateItems
                            }  else {
                                // Hint not supported: <hint> in each <item>
                                originalTemplateItems map { item ⇒
                                    val newLHHA = (item / *) filter (_.localname != "hint")
                                    elementInfo("item", newLHHA)
                                }
                            }
                        } else {
                            Seq.empty
                        }

                    val resourceEls = lhhaResourceEls ++ xblResourceEls ++ itemsResourceEls
                    formLang → elementInfo(newControlName, resourceEls)
                }

                // Insert data and resource holders
                insertHolders(
                    newControlElement,
                    dataHolder,
                    resourceHolders,
                    precedingControlNameInSectionForControl(newControlElement)
                )

                // Insert the bind element
                val bind = ensureBinds(doc, findContainerNames(gridTd) :+ newControlName)

                // Make sure there is a @bind instead of a @ref on the control
                delete(newControlElement \@ "ref")
                ensureAttribute(newControlElement, "bind", bind \@ "id" stringValue)

                // Set bind attributes if any
                insert(into = bind, origin = findBindAttributesTemplate(binding))

                // This can impact templates
                updateTemplates(doc)

                debugDumpDocumentForGrids("insert new control", doc)

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

        // Obtain ids first
        val ids = nextIds(inDoc, "tmp", 2).toIterator

        // The grid template
        val gridTemplate: NodeInfo =
            <fr:grid edit-ref="" id={ids.next()}
                     xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                     xmlns:xh="http://www.w3.org/1999/xhtml">
                <xh:tr>
                    <xh:td id={ids.next()}/>
                </xh:tr>
            </fr:grid>

        // Insert after current level 2 if found, otherwise into level 1
        val newGrid = insert(into = into, after = after.toSeq, origin = gridTemplate)

        // This can impact templates
        updateTemplates(inDoc)

        // Select first grid cell
        selectTd(newGrid \\ "*:td" head)

        debugDumpDocumentForGrids("insert new grid", inDoc)
    }

    // Insert a new section with optionally a nested grid
    def insertNewSection(inDoc: NodeInfo, withGrid: Boolean) = {

        val (into, after) = findSectionInsertionPoint(inDoc)

        val newSectionName = controlName(nextId(inDoc, "section"))
        val precedingSectionName = after flatMap getControlNameOpt

        // Obtain ids first
        val ids = nextIds(inDoc, "tmp", 2).toIterator

        // NOTE: use xxf:update="full" so that xxf:dynamic can better update top-level XBL controls
        val sectionTemplate: NodeInfo =
            <fr:section id={controlId(newSectionName)} bind={bindId(newSectionName)} edit-ref="" xxf:update="full"
                        xmlns:xh="http://www.w3.org/1999/xhtml"
                        xmlns:xf="http://www.w3.org/2002/xforms"
                        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
                        xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                        xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
                <xf:label ref={s"$$form-resources/$newSectionName/label"}/>{
                if (withGrid)
                    <fr:grid edit-ref="" id={ids.next()}>
                        <xh:tr>
                            <xh:td id={ids.next()}/>
                        </xh:tr>
                    </fr:grid>
            }</fr:section>

        val newSectionElement = insert(into = into, after = after.toSeq, origin = sectionTemplate).head

        // Create and insert holders
        val resourceHolder = {
            val elementContent = Seq(elementInfo("label"), elementInfo("help"))
            elementInfo(newSectionName, elementContent)
        }

        insertHolderForAllLang(newSectionElement, elementInfo(newSectionName), resourceHolder, precedingSectionName)

        // Insert the bind element
        ensureBinds(inDoc, findContainerNames(newSectionElement, includeSelf = true))

        // This can impact templates
        updateTemplates(inDoc)

        // Select first grid cell
        if (withGrid)
            selectTd(newSectionElement \\ "*:td" head)

        // TODO: Open label editor for newly inserted section

        debugDumpDocumentForGrids("insert new section", inDoc)

        Some(newSectionElement)
    }

    // Insert a new repeat
    def insertNewRepeat(inDoc: NodeInfo) = {

        val (into, after, grid) = findGridInsertionPoint(inDoc)

        val newGridName = controlName(nextId(inDoc, "grid"))
        val templateInstanceId = templateId(newGridName)

        // Handle data template
        ensureTemplateReplaceContent(inDoc, newGridName, <dummy/>.copy(label = newGridName))

        val modelElement = findModelElement(inDoc)
        modelElement \ "*:instance" find (hasIdValue(_, templateInstanceId)) match {
            case Some(templateInstance) ⇒
                // clear existing template instance content
                delete(templateInstance \ *)
                insert(into = templateInstance , origin = <dummy/>.copy(label = newGridName): NodeInfo)

            case None ⇒
                // Insert template instance if not present
                val template: NodeInfo = <xf:instance xmlns:xf="http://www.w3.org/2002/xforms"
                                                          xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
                                                          id={templateInstanceId} fb:readonly="true">{<dummy/>.copy(label = newGridName)}</xf:instance>

                insert(into = modelElement, after = modelElement \ "*:instance" takeRight 1, origin = template)
        }

        // The grid template
        val gridTemplate: NodeInfo =
            <fr:grid edit-ref=""
                     id={gridId(newGridName)}
                     repeat="true"
                     bind={bindId(newGridName)}
                     template={makeInstanceExpression(templateInstanceId)}
                     min="1"
                     xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
                     xmlns:xh="http://www.w3.org/1999/xhtml">
                <xh:tr>
                    <xh:td id={nextId(inDoc, "tmp")}/>
                </xh:tr>
            </fr:grid>

        // Insert grid
        val newGridElement = insert(into = into, after = after.toSeq, origin = gridTemplate).head

        // Insert instance holder (but no resource holders)
        insertHolders(
            newGridElement,
            elementInfo(newGridName),
            Seq.empty,
            grid flatMap (precedingControlNameInSectionForGrid(_, includeSelf = true))
        )

        // Make sure binds are created
        ensureBinds(inDoc, findContainerNames(newGridElement, includeSelf = true))

        // This can impact templates
        updateTemplates(inDoc)

        // Select new td
        selectTd(newGridElement \\ "*:td" head)

        debugDumpDocumentForGrids("insert new repeat", inDoc)

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
            findViewTemplate(binding) foreach
                (template ⇒ insert(into = section, after = section \ *, origin = template))
        }

    // Copy control to the clipboard
    def copyToClipboard(td: NodeInfo): Unit = {

        val doc = td.getDocumentRoot

        val name = getControlName(td \ * head)
        val xvc = asNodeInfo(topLevelModel("fr-form-model").get.getVariable("xcv"))

        findControlByName(doc, name) foreach { controlElement ⇒

            // Create <resource xml:lang="..."> containers
            val resourcesWithLang = findResourceHoldersWithLang(name, resourcesRoot) map {
                case (lang, holder) ⇒ elementInfo("resource", attributeInfo(XML_URI → "lang", lang) ++ holder)
            }

            // Clear and insert each clipboard element
            Map[String, Seq[NodeInfo]](
                "control"   → List(controlElement),
                "holder"    → findDataHolders(doc, name),
                "resources" → resourcesWithLang,
                "bind"      → findBindByName(doc, name).toList) foreach {

                case (elementName, content) ⇒
                    delete(xvc \ elementName \ *)
                    insert(into = xvc \ elementName, origin = content)
            }
        }
    }

    // Cut control to the clipboard
    def cutToClipboard(td: NodeInfo): Unit = {
        copyToClipboard(td)
        deleteCellContent(td, updateTemplates = true)
    }

    // Paste control from the clipboard
    def pasteFromClipboard(td: NodeInfo): Unit = {
        ensureEmptyTd(td) foreach { gridTd ⇒

            val xvc = asNodeInfo(topLevelModel("fr-form-model").get.getVariable("xcv"))

            (xvc \ "control" \ * headOption) foreach { control ⇒
                val name = {
                    val requestedName = getControlName(control)

                    // Check if id is already in use
                    if (byId(td, controlId(requestedName)).isDefined) {
                        // If so create new id
                        val newName = controlName(nextId(td, "control"))

                        // Rename everything
                        // TODO: don't rename in place
                        renameControlByElement(control, newName)

                        (xvc \ "holder" \ *) ++ (xvc \ "resources" \ "resource" \ *) foreach
                            (rename(_, newName))

                        (xvc \ "bind" \ * headOption) foreach
                            (renameBindElement(_, newName))

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

                    // If we copied a bind with @nodeset, keep things that way and remove the @ref created by ensureBinds
                    if (bind \@ "nodeset" nonEmpty)
                        delete(bind \@ "ref")
                }

                // This can impact templates
                updateTemplates(td)
            }
        }
    }
}