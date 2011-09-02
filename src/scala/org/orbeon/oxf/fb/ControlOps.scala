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
import annotation.tailrec
import org.orbeon.oxf.fb.FormBuilderFunctions._
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.analysis.model.Model
import org.orbeon.oxf.fb.GridOps._
import org.orbeon.oxf.fb.ContainerOps._
import org.orbeon.oxf.xml.NamespaceMapping
import org.orbeon.oxf.xml.dom4j.Dom4jUtils
import org.orbeon.saxon.om.{SequenceIterator, NodeInfo}

/*
 * Form Builder: operations on controls.
 */
object ControlOps {

    private val ControlName = """(.+)-(control|bind|grid|section|template|repeat)""".r // repeat for legacy FB

    private val topLevelBindTemplate: NodeInfo =
            <xforms:bind id="fr-form-binds" nodeset="instance('fr-form-instance')"
                         xmlns:xforms="http://www.w3.org/2002/xforms"/>

    // Get the control name based on the control, bind, grid, section or template id
    def controlName(controlOrBindId: String) = controlOrBindId match {
        case ControlName(name, _) => name
        case _ => null
    }

    // Find a control by name (less efficient than searching by id)
    def findControlByName(doc: NodeInfo, controlName: String) =
        Stream("control", "grid", "section", "repeat") flatMap // repeat for legacy FB
            (suffix => findControlById(doc, controlName + '-' + suffix)) headOption

    // XForms callers: find a control element by name or null (the empty sequence)
    def findControlByNameOrEmpty(doc: NodeInfo, controlName: String) =
        findControlByName(doc, controlName).orNull

    // Find a bind by name
    def findBindByName(doc: NodeInfo, name: String) = findBind(doc, isBindForName(_, name))

    // XForms callers: find a bind by name or null (the empty sequence)
    def findBindByNameOrEmpty(doc: NodeInfo, name: String) = findBindByName(doc, name).orNull

    // Find a bind by predicate
    def findBind(doc: NodeInfo, p: NodeInfo => Boolean) =
        ((findModelElement(doc) \ "*:bind" filter (hasId(_, "fr-form-binds"))) \\ "*:bind") filter (p(_)) headOption

    def isBindForName(bind: NodeInfo, name: String) =
        hasId(bind, bindId(name)) || (bind \@ "ref" ++ bind \@ "nodeset" stringValue) == name // also check ref/nodeset in case id is not present

    // Get the control's name based on the control element
    def getControlName(control: NodeInfo) = getControlNameOption(control).get

    // Get the control's name based on the control element
    def getControlNameOption(control: NodeInfo) =
        (control \@ "id" headOption) map
            (id => controlName(id.stringValue))

    def hasName(control: NodeInfo) = getControlNameOption(control).isDefined

    // Find a control (including grids and sections) element by id
    def findControlById(doc: NodeInfo, id: String) =
        findBodyElement(doc) \\ * filter (hasId(_, id)) headOption

    // Find control holder
    def findDataHolder(doc: NodeInfo, controlName: String) =
        findBindByName(doc, controlName) map { bind =>
            // From bind, infer path by looking at ancestor-or-self binds
            // Assume there is either a @ref or a @nodeset
            val bindRefs = (bind ancestorOrSelf "*:bind" map
                (b => ((b \@ "nodeset") ++ (b \@ "ref")) head)).reverse.tail

            val path = bindRefs map ("(" + _.stringValue + ")") mkString "/"

            // Assume that namespaces in scope on leaf bind apply to ancestor binds (in theory mappings could be overridden along the way!)
            val namespaces = new NamespaceMapping(Dom4jUtils.getNamespaceContextNoDefault(bind))

            // Evaluate path from instance root element
            evalOne(formInstanceRoot(doc), path, namespaces).asInstanceOf[NodeInfo]
        }

    // Find control resource holders
    def findResourceHolders(doc: NodeInfo, controlName: String): Seq[NodeInfo] =
        findResourceHoldersWithLang(doc, controlName) map (_._2)

    // Find control resource holders with their language
    def findResourceHoldersWithLang(doc: NodeInfo, controlName: String): Seq[(String, NodeInfo)] =
        for {
            resource <- formResourcesRoot(doc) \ "resource"
            lang = (resource \@ "*:lang").stringValue
            holder <- resource \ * filter (name(_) == controlName) headOption
        } yield
            (lang, holder)

    // Current resources
    def currentResources = asNodeInfo(model("fr-form-model").get.getVariable("current-resources"))

    // Find the current resource holder for the given name
    def findCurrentResourceHolder(controlName: String) = currentResources \ * filter (name(_) == controlName) headOption

    // Ensure that a tree of bind exists
    def ensureBindsByName(doc: NodeInfo, name: String) =
        findControlByName(doc, name) foreach { control =>
            ensureBinds(doc, findContainerNames(control) :+ name, isCustomInstance)
        }

    // Ensure that a tree of bind exists
    def ensureBinds(doc: NodeInfo, names: Seq[String], isCustomInstance: Boolean): NodeInfo = {

        // Insert bind container if needed
        val model = findModelElement(doc)
        val topLevelBind = model \ "*:bind" filter (hasId(_, "fr-form-binds")) match {
            case Seq(bind: NodeInfo, _*) => bind
            case _ => insert(into = model, after = model \ "*:instance" filter (hasId(_, "fr-form-instance")), origin = topLevelBindTemplate).head
        }

        // Insert a bind into one level
        @tailrec def ensureBind(container: NodeInfo, names: Iterator[String]): NodeInfo = {
            if (names.hasNext) {
                val bindName = names.next()
                val bind =  container \ "*:bind" filter (isBindForName(_, bindName)) match {
                    case Seq(bind: NodeInfo, _*) => bind
                    case _ =>

                        val newBind: Seq[NodeInfo] =
                            <xforms:bind id={bindId(bindName)}
                                         nodeset={if (isCustomInstance) "()" else bindName}
                                         name={bindName}
                                         xmlns:xforms="http://www.w3.org/2002/xforms"/>

                        insert(into = container, after = container \ "*:bind", origin = newBind).head
                }
                ensureBind(bind, names)
            } else
                container
        }

        // Start with top-level
        ensureBind(topLevelBind.asInstanceOf[NodeInfo], names.toIterator)
    }

    // Delete the controls in the given grid cell, if any
    def deleteCellContent(td: NodeInfo) =
        td \ * flatMap (controlElementsToDelete(_)) foreach (delete(_))

    // Find all associated elements to delete for a given control element
    def controlElementsToDelete(control: NodeInfo): Seq[NodeInfo] = {

        val doc = control.getDocumentRoot

        // Holders, bind, templates, resources if the control has a name
        val holders = getControlNameOption(control).toSeq flatMap { controlName =>
            Seq(findDataHolder(doc, controlName),
                findBindByName(doc, controlName),
                instanceElement(doc, templateId(controlName)),
                findTemplateHolder(control, controlName)) ++
                (findResourceHolders(doc, controlName) map (Option(_))) flatten
        }

        // Append control element
        holders :+ control
    }

    // Rename a control with its holders, binds, etc.
    def findRenameControl(doc: NodeInfo, oldName: String, newName: String) =
        if (oldName != newName) {
            findRenameHolders(doc, oldName, newName)
            findRenameBind(doc, oldName, newName)
            findControlByName(doc, oldName) foreach (renameControlByElement(_, newName))
            renameTemplate(doc, oldName, newName)
        }

    // Rename the control (but NOT its holders, binds, etc.)
    def renameControlByElement(controlElement: NodeInfo, newName: String) {

        // Set @id in any case, @ref value if present, @bind value if present
        ensureAttribute(controlElement, "id", controlId(newName))
        ensureAttribute(controlElement, "bind", bindId(newName))

        // Make the control point to its template if @origin is present
        setvalue(controlElement \@ "origin", makeInstanceExpression(templateId(newName)))

        // Set xforms:label, xforms:hint, xforms:help and xforms:alert @ref if present
        // FIXME: This code is particularly ugly!
        controlElement \ * filter (e => Set("label", "help", "hint", "alert")(localname(e))) map
            (e => (e \@ "ref", localname(e))) filter
                (_._1 nonEmpty) filter { e =>
                    val ref = e._1.stringValue
                    ref.isEmpty || ref.startsWith("$form-resources")
                } foreach { e =>
                    setvalue(e._1, "$form-resources/" + newName + '/' + e._2)
                }

        // Set xforms:itemset/@ref xforms:itemset/@nodeset value if present
        for (attName <- Seq("ref", "nodeset"))
            setvalue(controlElement \ "*:itemset" \@ attName, "$form-resources/" + newName + "/item")
    }

    // Rename a bind
    def renameBindByElement(bindElement: NodeInfo, newName: String) = {
        ensureAttribute(bindElement, "id", bindId(newName))
        ensureAttribute(bindElement, "name", newName)
        ensureAttribute(bindElement, "ref", newName)
        delete(bindElement \@ "nodeset")
    }

    // Rename a bind
    def findRenameBind(doc: NodeInfo, oldName: String, newName: String) =
        findBindByName(doc, oldName) foreach
            (renameBindByElement(_, newName))

    // Rename holders with the given name
    // TODO: don't rename data holders if (isCustomInstance)
    def findRenameHolders(doc: NodeInfo, oldName: String, newName: String) =
        findHolders(doc, oldName) foreach
            (rename(_, oldName, newName))

    // Find all data, resources, and template holders for the given name
    def findHolders(doc: NodeInfo, holderName: String): Seq[NodeInfo] =
        Seq(findDataHolder(doc, holderName)) ++
            (findResourceHolders(doc, holderName) map (Option(_))) :+
            (findControlByName(doc, holderName) flatMap (findTemplateHolder(_, holderName))) flatten

    def findResourceAndTemplateHolders(doc: NodeInfo, holderName: String): Seq[NodeInfo] =
        (findResourceHolders(doc, holderName) map (Option(_))) :+
            (findControlByName(doc, holderName) flatMap (findTemplateHolder(_, holderName))) flatten

    // Find or create a data holder for the given hierarchy of names
    def ensureDataHolder(root: NodeInfo, holders: Seq[(() => NodeInfo, Option[String])]) = {

        @tailrec def ensure(parent: NodeInfo, names: Iterator[(() => NodeInfo, Option[String])]): NodeInfo =
            if (names.hasNext) {
                val (getHolder, precedingHolderName) = names.next()
                val holder = getHolder() // not ideal: this might create a NodeInfo just to check the name of the holder
                parent \ * filter (name(_) == name(holder)) headOption match {
                    case Some(child) =>
                        // Holder already exists
                        ensure(child, names)
                    case None =>
                        // Holder doesn't exist, insert it
                        val newChild = insert(into = parent, after = parent \ * filter (name(_) == precedingHolderName.getOrElse("")), origin = holder)
                        ensure(newChild.head, names)
                }
            } else
                parent

        ensure(root, holders.toIterator)
    }

    // Insert data and resource holders for all languages
    def insertHolders(controlElement: NodeInfo, dataHolder: NodeInfo, resourceHolder: NodeInfo, precedingControlName: Option[String]) {
        // Create one holder per existing language
        val resourceHolders = (formResourcesRoot(controlElement) \ "resource" \@ "*:lang") map
            (att => (att.stringValue, resourceHolder))

        insertHolders(controlElement, dataHolder, resourceHolders, precedingControlName)
    }

    def precedingControlNameInSectionForControl(controlElement: NodeInfo) = {

        val td = controlElement.parent.get
        val grid = findAncestorContainers(td).head
        assert(localname(grid) == "grid")

        // First check within the current grid as well as the grid itself
        val preceding = td preceding "*:td"
        val precedingInGrid = preceding intersect (grid descendant "*:td")

        val nameInGridOption = precedingInGrid :+ grid flatMap
            { case td if localname(td) == "td" => td \ *; case other => other } flatMap
                (getControlNameOption(_).toSeq) headOption

        // Return that if found, otherwise find before the current grid
        nameInGridOption orElse precedingControlNameInSectionForGrid(grid, false)
    }

    def precedingControlNameInSectionForGrid(grid: NodeInfo, includeSelf: Boolean) = {

        val precedingOrSelfContainers = (if (includeSelf) Seq(grid) else Seq()) ++ (grid precedingSibling containerElementTest)

        // If a container has a name, then use that name, otherwise it must be an unnamed grid so find its last control
        // with a name (there might not be one).
        val controlsWithName =
            precedingOrSelfContainers flatMap {
                case grid if getControlNameOption(grid).isEmpty => grid \\ "*:td" \ * filter (hasName(_)) lastOption
                case other => Some(other)
            }

        // Take the first result
        controlsWithName.headOption flatMap (getControlNameOption(_))
    }

    // Insert data and resource holders for all languages
    def insertHolders(controlElement: NodeInfo, dataHolder: NodeInfo, resourceHolders: Seq[(String, NodeInfo)], precedingControlName: Option[String]) {
        val doc = controlElement.getDocumentRoot
        val containerNames = findContainerNames(controlElement)

        // Insert hierarchy of data holders
        // We pass a Seq of tuples, one part able to create missing data holders, the other one with optional previous names.
        // In practice, the ancestor holders should already exist.
        ensureDataHolder(formInstanceRoot(doc), (containerNames map (n => (() => elementInfo(n), None))) :+ (() => dataHolder, precedingControlName))

        // Insert resources placeholders for all languages
        if (resourceHolders.nonEmpty) {
            val resourceHoldersMap = resourceHolders.toMap
            for {
                resource <- formResourcesRoot(doc) \ "resource"
                lang = (resource \@ "*:lang").stringValue
                holder = resourceHoldersMap.get(lang) getOrElse resourceHolders(0)._2
            } yield
                insert(into = resource, after = resource \ * filter (name(_) == precedingControlName.getOrElse("")), origin = holder)
        }

        // Insert repeat template holder if needed
        for {
            grid <- findContainingRepeat(controlElement)
            gridName <- getControlNameOption(grid)
            root <- templateRoot(doc, gridName)
        } yield
            ensureDataHolder(root, Seq((() => dataHolder, precedingControlName)))
    }

    // Update a mip for the given control, grid or section id
    // The bind is created if needed
    def updateMip(doc: NodeInfo, controlId: String, mipName: String, mipValue: String) {

        require(Model.MIP.withName(mipName) ne null) // withName() will throw NoSuchElementException if not present
        val mipQName = Model.mipNameToAttributeQName(mipName)

        findControlById(doc, controlId) foreach { control =>

            // Get or create the bind element
            val bind = ensureBinds(doc, findContainerNames(control) :+ controlName(controlId), isCustomInstance)

            // Create/update or remove attribute
            Option(mipValue) map (_.trim) match {
                case Some(value) if value.length > 0 => ensureAttribute(bind, mipQName, value)
                case _ => delete(bind \@ mipQName)
            }
        }
    }

    def getMip(doc: NodeInfo, controlId: String, mipName: String) = {
        require(Model.MIP.withName(mipName) ne null) // withName() will throw NoSuchElementException if not present
        findBindByName(doc, controlName(controlId)) flatMap (bind => attValueOption(bind \@ Model.mipNameToAttributeQName(mipName)))
    }

    // XForms callers: find the value of a MIP or null (the empty sequence)
    def getMipOrEmpty(doc: NodeInfo, controlId: String, mipName: String) =
        getMip(doc, controlId, mipName).orNull

    // Get all control names by inspecting all elements with an id that converts to a valid name
    def getAllControlNames(doc: NodeInfo) =
        findBodyElement(doc) \\ * flatMap
            (e => attValueOption(e \@ "id")) flatMap
                (id => Option(controlName(id)))

    // For XForms callers
    def getAllControlNamesXPath(doc: NodeInfo): SequenceIterator = getAllControlNames(doc)

    // Get the control's resource holder
    def getControlResourceOrEmpty(controlId: String, resourceName: String) =
        findCurrentResourceHolder(controlName(controlId)) flatMap
            (n => n \ resourceName map (_.stringValue) headOption) getOrElse("")

    def getControlHelpOrEmpty(controlId: String) = getControlResourceOrEmpty(controlId, "help")
    def getControlAlertOrEmpty(controlId: String) = getControlResourceOrEmpty(controlId, "alert")

    // Set a control's current resource
    def setControlResource(controlId: String, resourceName: String, value: String) =
        findCurrentResourceHolder(controlName(controlId)) flatMap
            (n => n \ resourceName headOption) foreach
                (setvalue(_, value))

    def setControlHelp(controlId: String, value: String) = setControlResource(controlId, "help", value)
    def setControlAlert(controlId: String, value: String) = setControlResource(controlId, "alert", value)
}