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

import annotation.tailrec
import org.orbeon.oxf.xml.XMLConstants.XS_STRING_QNAME
import org.orbeon.oxf.xforms.analysis.model.Model
import Model._
import org.orbeon.oxf.xml.NamespaceMapping

import org.orbeon.oxf.xforms.control.{XFormsSingleNodeControl, XFormsControl}
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.XML._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.action.XFormsAPI._
import collection.mutable
import org.orbeon.saxon.value.StringValue
import org.apache.commons.lang3.StringUtils._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.util.ScalaUtils._
import scala.collection.JavaConverters._

/*
 * Form Builder: operations on controls.
 */
trait ControlOps extends SchemaOps with ResourcesOps {

    self: GridOps ⇒ // funky dependency, to resolve at some point

    val FB = "http://orbeon.org/oxf/xml/form-builder"

    private val MIPsToRewrite = AllMIPs - Type
    private val RewrittenMIPs = MIPsToRewrite map (mip ⇒ mip.name → toQName(FB → ("fb:" + mip.name))) toMap
    
    val BindElementTest: Test = XF → "bind"

    private val topLevelBindTemplate: NodeInfo =
        <xf:bind id="fr-form-binds" ref="instance('fr-form-instance')"
                     xmlns:xf="http://www.w3.org/2002/xforms"/>

    private val HelpRefMatcher = """\$form-resources/([^/]+)/help""".r

    // Find data holders (there can be more than one with repeats)
    def findDataHolders(inDoc: NodeInfo, controlName: String): Seq[NodeInfo] =
        findDataHoldersPathStatically(inDoc, controlName) map { case (bind, path) ⇒
            // From bind, infer path by looking at ancestor-or-self binds
            val bindRefs = (bind ancestorOrSelf BindElementTest flatMap bindRefOrNodeset).reverse.tail

            val path = bindRefs map ("(" + _ + ")") mkString "/"

            // Assume that namespaces in scope on leaf bind apply to ancestor binds (in theory mappings could be
            // overridden along the way!)
            val namespaces = new NamespaceMapping(bind.namespaceMappings.toMap.asJava)

            // Evaluate path from instance root element
            eval(
                item       = formInstanceRoot(inDoc),
                expr       = path,
                namespaces = namespaces,
                variables  = null,
                reporter   = containingDocument.getRequestStats.addXPathStat
            ).asInstanceOf[Seq[NodeInfo]]
        } getOrElse
            Seq.empty

    def findDataHoldersPathStatically(inDoc: NodeInfo, controlName: String): Option[(NodeInfo, String)] = {
        findBindByName(inDoc, controlName) map { bind ⇒

            val bindRefsFromRootExcluded =
                (bind ancestorOrSelf BindElementTest flatMap bindRefOrNodeset).reverse.tail

            (bind, bindRefsFromRootExcluded mkString "/")
        }
    }

    def precedingControlNameInSectionForControl(controlElement: NodeInfo) = {

        val td = controlElement parent * head
        val grid = findAncestorContainers(td).head
        assert(grid.localname == "grid")

        // First check within the current grid as well as the grid itself
        val preceding = td preceding "*:td"
        val precedingInGrid = preceding intersect (grid descendant "*:td")

        val nameInGridOption = precedingInGrid :+ grid flatMap
            { case td if td.localname == "td" ⇒ td \ *; case other ⇒ other } flatMap
                (getControlNameOpt(_).toSeq) headOption

        // Return that if found, otherwise find before the current grid
        nameInGridOption orElse precedingControlNameInSectionForGrid(grid, includeSelf = false)
    }

    def precedingControlNameInSectionForGrid(grid: NodeInfo, includeSelf: Boolean) = {

        val precedingOrSelfContainers =
            (if (includeSelf) Seq(grid) else Seq.empty) ++ (grid precedingSibling * filter IsContainer)

        // If a container has a name, then use that name, otherwise it must be an unnamed grid so find its last control
        // with a name (there might not be one).
        val controlsWithName =
            precedingOrSelfContainers flatMap {
                case grid if getControlNameOpt(grid).isEmpty ⇒ grid \\ "*:td" \ * filter hasName lastOption
                case other ⇒ Some(other)
            }

        // Take the first result
        controlsWithName.headOption flatMap getControlNameOpt
    }

    // Ensure that a tree of bind exists
    def ensureBinds(inDoc: NodeInfo, names: Seq[String]): NodeInfo = {

        // Insert bind container if needed
        val model = findModelElement(inDoc)
        val topLevelBind = findTopLevelBind(inDoc).headOption match {
            case Some(bind) ⇒
                bind
            case None ⇒
                insert(
                    into   = model,
                    after  = model \ "*:instance" filter (hasIdValue(_, "fr-form-instance")),
                    origin = topLevelBindTemplate
                ).head
        }

        // Insert a bind into one level
        @tailrec def ensureBind(container: NodeInfo, names: Iterator[String]): NodeInfo = {
            if (names.hasNext) {
                val bindName = names.next()
                val bind = container \ "*:bind" filter (isBindForName(_, bindName)) match {
                    case Seq(bind: NodeInfo, _*) ⇒ bind
                    case _ ⇒

                        val newBind: Seq[NodeInfo] =
                            <xf:bind id={bindId(bindName)}
                                         ref={bindName}
                                         name={bindName}
                                         xmlns:xf="http://www.w3.org/2002/xforms"/>

                        insert(into = container, after = container \ "*:bind", origin = newBind).head
                }
                ensureBind(bind, names)
            } else
                container
        }

        // Start with top-level
        ensureBind(topLevelBind, names.toIterator)
    }

    // Iterate over the given bind followed by all of its descendants, depth-first
    def iterateSelfAndDescendantBinds(rootBind: NodeInfo): Iterator[NodeInfo] =
        rootBind descendantOrSelf "*:bind" iterator

    // Delete the controls in the given grid cell, if any
    def deleteCellContent(td: NodeInfo, updateTemplates: Boolean = false): Unit = {
        td \ * flatMap controlElementsToDelete foreach (delete(_))
        if (updateTemplates)
            self.updateTemplates(td)
    }

    // Find all associated elements to delete for a given control element
    def controlElementsToDelete(control: NodeInfo): List[NodeInfo] = {

        val doc = control.getDocumentRoot

        // Holders, bind, templates, resources if the control has a name
        val holders = getControlNameOpt(control).toList flatMap { controlName ⇒

            val result = mutable.Buffer[NodeInfo]()

            result ++=
                findDataHolders     (doc, controlName) ++=
                findBindByName      (doc, controlName) ++=
                findTemplateInstance(doc, controlName) ++=
                findResourceHolders  (controlName)

            result.toList
        }

        // Prepend control element
        control :: holders
    }

    // Rename a control with its holders, binds, etc. but *not* its nested iteration if any
    def renameControlIfNeeded(inDoc: NodeInfo, oldName: String, newName: String): Unit =
        if (oldName != newName) {

            findDataHolders(inDoc, oldName) foreach (rename(_, newName))
            findResourceHolders(oldName)    foreach (rename(_, newName))

            renameBinds   (inDoc, oldName, newName)
            renameControl (inDoc, oldName, newName)
            renameTemplate(inDoc, oldName, newName)

            updateTemplates(inDoc)
        }

    // Rename a control's nested iteration if any
    def renameControlIterationIfNeeded(
        inDoc                      : NodeInfo,
        oldControlName             : String,
        newControlName             : String,
        oldChildElementNameOrBlank : String,
        newChildElementNameOrBlank : String
    ): Unit =
        if (findControlByName(inDoc, oldControlName) exists controlRequiresNestedIterationElement) {

            val oldName = nonEmptyOrNone(oldChildElementNameOrBlank) getOrElse defaultIterationName(oldControlName)
            val newName = nonEmptyOrNone(newChildElementNameOrBlank) getOrElse defaultIterationName(newControlName)

            if (oldName != newName) {
                findDataHolders(inDoc, oldName) foreach (rename(_, newName))
                renameBinds(inDoc, oldName, newName)
                updateTemplates(inDoc)
            }
        }

    def renameControl(inDoc: NodeInfo, oldName: String, newName: String): Unit =
        findControlByName(inDoc, oldName) foreach
            (renameControlByElement(_, newName, resourceNamesInUseForControl(newName)))

    def resourceNamesInUseForControl(controlName: String) =
        currentResources.child(controlName).child(*).map(_.localname).to[Set]

    // Rename the control (but NOT its holders, binds, etc.)
    def renameControlByElement(controlElement: NodeInfo, newName: String, resourcesNames: Set[String]): Unit = {

        // Set @id in any case, @ref value if present, @bind value if present
        ensureAttribute(controlElement, "id", controlId(newName))
        ensureAttribute(controlElement, "bind", bindId(newName))

        // Make the control point to its template if @template (or legacy @origin) is present
        for (attName ← List("template", "origin"))
            setvalue(controlElement \@ attName, makeInstanceExpression(templateId(newName)))

        // Set xf:label, xf:hint, xf:help and xf:alert @ref if present
        for {
            resourcePointer ← controlElement.child(*)
            resourceName = resourcePointer.localname
            if resourcesNames(resourceName)                             // We have a resource for this sub-element
            ref = resourcePointer.att("ref").headOption
            if ref.nonEmpty                                             // If no ref, value might be inline
            refVal = ref.head.stringValue
            if refVal.isEmpty || refVal.startsWith("$form-resources")   // Don't overwrite ref pointing somewhere else
        } locally {
            setvalue(ref.toSeq, s"$$form-resources/$newName/$resourceName")
        }

        // If using a static itemset editor, set xf:itemset/@ref xf:itemset/@nodeset value
        if (hasEditor(controlElement, "static-itemset"))
            for (attName ← Seq("ref", "nodeset"))
                setvalue(controlElement \ "*:itemset" \@ attName, s"$$form-resources/$newName/item")
    }

    // Rename a bind
    def renameBindElement(bindElement: NodeInfo, newName: String) = {
        ensureAttribute(bindElement, "id",   bindId(newName))
        ensureAttribute(bindElement, "name", newName)
        ensureAttribute(bindElement, "ref",  newName)
        delete(bindElement \@ "nodeset") // sanitize
    }

    // Rename a bind
    def renameBinds(inDoc: NodeInfo, oldName: String, newName: String): Unit =
        findBindByName(inDoc, oldName) foreach (renameBindElement(_, newName))

    // Find or create a data holder for the given hierarchy of names
    private def ensureDataHolder(root: NodeInfo, holders: Seq[(() ⇒ NodeInfo, Option[String])]) = {

        @tailrec def ensure(parents: Seq[NodeInfo], names: Iterator[(() ⇒ NodeInfo, Option[String])]): Seq[NodeInfo] =
            if (names.hasNext) {
                val (getHolder, precedingHolderName) = names.next()
                val holder = getHolder() // not ideal: this might create a NodeInfo just to check the name of the holder

                val children =
                    for {
                        parent ← parents
                    } yield
                        parent \ * filter (_.name == holder.name) match {
                            case Seq() ⇒
                                // No holder exists so insert one
                                insert(
                                    into   = parent,
                                    after  = parent \ * filter (_.name == precedingHolderName.getOrElse("")),
                                    origin = holder
                                )
                            case existing ⇒
                                // At least one holder exists (can be more than one for repeats)
                                existing

                        }

                ensure(children.flatten, names)
            } else
                parents

        ensure(Seq(root), holders.toIterator)
    }

    // Insert data and resource holders for all languages
    def insertHolderForAllLang(
        controlElement       : NodeInfo,
        dataHolder           : NodeInfo,
        resourceHolder       : NodeInfo,
        precedingControlName : Option[String]
    ): Unit = {

        // Create one holder per existing language
        val resourceHolders = (formResourcesRoot \ "resource" \@ "*:lang") map (_.stringValue → resourceHolder)
        insertHolders(controlElement, dataHolder, resourceHolders, precedingControlName)
    }

    // Insert data and resource holders for all languages
    def insertHolders(
        controlElement       : NodeInfo,
        dataHolder           : NodeInfo,
        resourceHolders      : Seq[(String, NodeInfo)],
        precedingControlName : Option[String]
    ): Unit = {
        val doc = controlElement.getDocumentRoot

        // Insert hierarchy of data holders
        // We pass a Seq of tuples, one part able to create missing data holders, the other one with optional previous
        // names. In practice, the ancestor holders should already exist.
        locally {
            val containerNames = findContainerNames(controlElement)
            ensureDataHolder(
                formInstanceRoot(doc),
                (containerNames map (n ⇒ (() ⇒ elementInfo(n), None))) :+ (() ⇒ dataHolder, precedingControlName)
            )
        }

        // Insert resources placeholders for all languages
        if (resourceHolders.nonEmpty) {
            val resourceHoldersMap = resourceHolders.toMap
            formResourcesRoot \ "resource" foreach (resource ⇒ {
                val lang = (resource \@ "*:lang").stringValue
                val holder = resourceHoldersMap.getOrElse(lang, resourceHolders(0)._2)
                insert(
                    into   = resource,
                    after  = resource \ * filter (_.name == precedingControlName.getOrElse("")),
                    origin = holder
                )
            })
        }
    }

    // Update a mip for the given control, grid or section id
    // The bind is created if needed
    def updateMip(inDoc: NodeInfo, controlName: String, mipName: String, mipValue: String): Unit = {

        require(Model.AllMIPNames(mipName))
        val mipQName = mipNameToFBMIPQname(mipName)

        findControlByName(inDoc, controlName) foreach { control ⇒

            // Get or create the bind element
            val bind = ensureBinds(inDoc, findContainerNames(control) :+ controlName)

            // NOTE: It's hard to remove the namespace mapping once it's there, as in theory lots of
            // expressions and types could use it. So for now the mapping is never garbage collected.
            def isTypeString(value: String) =
                mipName == Type.name &&
                valueNamespaceMappingScopeIfNeeded(bind, value).isDefined &&
                Set(XS_STRING_QNAME, XFORMS_STRING_QNAME)(bind.resolveQName(value))

            def isRequiredFalse(value: String) =
                mipName == Required.name && value == "false()"

            def mustRemoveAttribute(value: String) =
                isTypeString(value) || isRequiredFalse(value)

            nonEmptyOrNone(mipValue) match {
                case Some(normalizedMipValue) if ! mustRemoveAttribute(normalizedMipValue) ⇒
                    ensureAttribute(bind, mipQName, normalizedMipValue)
                case _ ⇒
                    delete(bind /@ mipQName)
            }
        }
    }

    // Return None if no namespace mapping is required OR none can be created
    def valueNamespaceMappingScopeIfNeeded(bind: NodeInfo, qNameValue: String): Option[(String, String)] = {

        val (prefix, _) = parseQName(qNameValue)

        def existingNSMapping =
            bind.namespaceMappings.toMap.get(prefix) map (prefix →)

        def newNSMapping = {
            // If there is no mapping and the schema prefix matches the prefix and a uri is found for the
            // schema, then insert a new mapping. We place it on the top-level bind so we don't have to insert
            // it repeatedly.
            val newURI =
                if (findSchemaPrefix(bind) == Some(prefix))
                    findSchemaNamespace(bind)
                else
                    None

            newURI map { uri ⇒
                insert(into = findTopLevelBind(bind).toList, origin = namespaceInfo(prefix, uri))
                prefix → uri
            }
        }

        if (prefix == "")
            None
        else
            existingNSMapping orElse newNSMapping
    }

    // Get the value of a MIP attribute if present
    def getMip(inDoc: NodeInfo, controlName: String, mipName: String) = {
        require(Model.AllMIPNames(mipName))
        val mipQName = mipNameToFBMIPQname(mipName)

        findBindByName(inDoc, controlName) flatMap (_ attValueOpt mipQName)
    }

    def mipNameToFBMIPQname(mipName: String) =
        RewrittenMIPs.get(mipName)                 orElse
        (AllMIPsByName.get(mipName) map (_.aName)) getOrElse
        (throw new IllegalArgumentException)

    // XForms callers: find the value of a MIP or null (the empty sequence)
    def getMipOrEmpty(inDoc: NodeInfo, controlName: String, mipName: String) =
        getMip(inDoc, controlName, mipName).orNull

    // Get all control names by inspecting all elements with an id that converts to a valid name
    def getAllControlNames(inDoc: NodeInfo) =
        fbFormInstance.idsIterator filter isIdForControl map controlNameFromId toSet

    // For XForms callers
    def getAllControlNamesXPath(inDoc: NodeInfo): SequenceIterator =
        getAllControlNames(inDoc).iterator map StringValue.makeStringValue

    // Return all the controls in the view
    def getAllControlsWithIds(inDoc: NodeInfo) =
        findFRBodyElement(inDoc) \\ * filter
            (e ⇒ isIdForControl(e.id))

    // From a control element (say <fr:autocomplete>), returns the corresponding <xbl:binding>
    // For XPath callers
    // TODO: Is there a better way, so we don't have to keep defining alternate functions? Maybe define a
    // Option → List function?
    def bindingOrEmpty(controlElement: NodeInfo) =
        FormBuilder.bindingForControlElement(controlElement, componentBindings).orNull

    // Finds if a control uses a particular type of editor (say "static-itemset")
    def hasEditor(controlElement: NodeInfo, editor: String) =
        FormBuilder.controlElementHasEditor(controlElement: NodeInfo, editor: String, componentBindings)

    // Find a given static control by name
    def findStaticControlByName(controlName: String) = {
        val model = getFormModel
        val part = model.getStaticModel.part
        for {
            controlId ← findControlIdByName(getFormDoc, controlName)
            prefixedId = part.startScope.prefixedIdForStaticId(controlId)
            control ← Option(part.getControlAnalysis(prefixedId))
        } yield
            control
    }

    // Find the control by name (resolved from the top-level form model `fr-form-model`)
    def findConcreteControlByName(controlName: String) = {
        val model = getFormModel
        for {
            controlId ← findControlIdByName(getFormDoc, controlName)
            control   ← model.container.resolveObjectByIdInScope(model.getEffectiveId, controlId) map (_.asInstanceOf[XFormsControl])
        } yield
            control
    }

    // Find the control's bound item if any (resolved from the top-level form model `fr-form-model`)
    def findControlBoundNodeByName(controlName: String) = (
        findConcreteControlByName(controlName)
        collect { case c: XFormsSingleNodeControl ⇒ c }
        flatMap (_.boundNode)
    )

    // Find a control's LHHA (there can be more than one for alerts)
    def getControlLHHA(inDoc: NodeInfo, controlName: String, lhha: String) =
        findControlByName(inDoc, controlName).toList child (XF → lhha)

    // Set the control help and add/remove help element and placeholders as needed
    def setControlHelp(controlName: String,  value: String) = {

        setControlResource(controlName, "help", trimToEmpty(value))

        val inDoc = getFormDoc

        if (hasBlankOrMissingLHHAForAllLangsUseDoc(inDoc, controlName, "help"))
            removeLHHAElementAndResources(inDoc, controlName, "help")
        else
            ensureCleanLHHAElements(inDoc, controlName, "help")
    }

    // For a given control and LHHA type, whether the mediatype on the LHHA is HTML
    def isControlLHHAHTMLMediatype(inDoc: NodeInfo, controlName: String, lhha: String) =
        hasHTMLMediatype(getControlLHHA(inDoc, controlName, lhha))

    // For a given control and LHHA type, set the mediatype on the LHHA to be HTML or plain text
    def setControlLHHAMediatype(inDoc: NodeInfo, controlName: String, lhha: String, isHTML: Boolean): Unit =
        if (isHTML != isControlLHHAHTMLMediatype(inDoc, controlName, lhha))
            setHTMLMediatype(getControlLHHA(inDoc, controlName, lhha), isHTML)

    // For a given control, whether the mediatype on itemset labels is HTML
    def isItemsetHTMLMediatype(inDoc: NodeInfo, controlName: String) =
        hasHTMLMediatype(findControlByName(inDoc, controlName).toList child "itemset" child "label")

    // For a given control, set the mediatype on the itemset labels to be HTML or plain text
    def setItemsetHTMLMediatype(inDoc: NodeInfo, controlName: String, isHTML: Boolean): Unit =
        if (isHTML != isItemsetHTMLMediatype(inDoc, controlName)) {
            val itemsetEl = findControlByName(inDoc, controlName).toList child "itemset"
            val labelHintEls = Seq("label", "hint") flatMap (itemsetEl.child(_))
            setHTMLMediatype(labelHintEls, isHTML)
        }

    private def setHTMLMediatype(nodes: Seq[NodeInfo], isHTML: Boolean): Unit =
        nodes foreach { lhhaElement ⇒
            if (isHTML)
                insert(into = lhhaElement, origin = attributeInfo("mediatype", "text/html"))
            else
                delete(lhhaElement \@ "mediatype")
        }

    def ensureCleanLHHAElements(
        inDoc       : NodeInfo,
        controlName : String,
        lhha        : String,
        count       : Int     = 1,
        replace     : Boolean = true
    ): Seq[NodeInfo] = {

        val control  = findControlByName(inDoc, controlName).get
        val existing = getControlLHHA(inDoc, controlName, lhha)

        if (replace)
            delete(existing)

        // Try to insert in the right position wrt other LHHA elements. If none, will be inserted as first
        // element.

        if (count > 0) {
            val newTemplates =
                if (count == 1) {
                    def newTemplate: NodeInfo =
                        <xf:lhha xmlns:xf="http://www.w3.org/2002/xforms"
                                 ref={s"$$form-resources/$controlName/$lhha"}/>.copy(label = lhha)

                    Seq(newTemplate)
                } else {
                    def newTemplate(index: Int): NodeInfo =
                        <xf:lhha xmlns:xf="http://www.w3.org/2002/xforms"
                                 ref={s"$$form-resources/$controlName/$lhha[$index]"}/>.copy(label = lhha)

                    1 to count map newTemplate
                }

            insertElementsImposeOrder(into = control, origin = newTemplates, LHHAInOrder)
        } else
            Seq.empty
    }

    private def removeLHHAElementAndResources(inDoc: NodeInfo, controlName: String, lhha: String) = {
        val control = findControlByName(inDoc, controlName).get

        val removedHolders = delete(lhhaHoldersForAllLangsUseDoc(inDoc, controlName, lhha))
        val removedLHHA    = delete(control child (XF → lhha))

        removedHolders ++ removedLHHA
    }

    // XForms callers: build an effective id for a given static id or return null (the empty sequence)
    def buildFormBuilderControlAbsoluteIdOrEmpty(inDoc: NodeInfo, staticId: String) =
        buildFormBuilderControlEffectiveId(inDoc, staticId) map effectiveIdToAbsoluteId orNull

    def buildFormBuilderControlEffectiveIdOrEmpty(inDoc: NodeInfo, staticId: String) =
        buildFormBuilderControlEffectiveId(inDoc, staticId).orNull

    private def buildFormBuilderControlEffectiveId(inDoc: NodeInfo, staticId: String) =
        findInViewTryIndex(inDoc, staticId) map (DynamicControlId + COMPONENT_SEPARATOR + buildControlEffectiveId(_))

    // Build an effective id for a given static id
    //
    // This assumes a certain hierarchy:
    //
    // - zero or more *:section containers
    // - zero or more fr:grid containers
    // - the only repeats are containers
    // - all containers must have stable (not automatically-generated by XForms) ids
    def buildControlEffectiveId(control: NodeInfo) = {
        val staticId = control.id

        // Ancestors from root to leaf except fb-body group if present
        val ancestorContainers = findAncestorContainers(control, includeSelf = false).reverse filterNot isFBBody

        val containerIds = ancestorContainers map (_.id)
        val repeatDepth  = ancestorContainers count isRepeat

        def suffix = 1 to repeatDepth map (_ ⇒ 1) mkString REPEAT_INDEX_SEPARATOR_STRING
        val prefixedId = containerIds :+ staticId mkString XF_COMPONENT_SEPARATOR_STRING

        prefixedId + (if (repeatDepth == 0) "" else REPEAT_SEPARATOR + suffix)
    }

    // Find all resource holders and elements which are unneeded because the resources are blank
    def findBlankLHHAHoldersAndElements(inDoc: NodeInfo, lhha: String) = {

        val allHelpElements =
            inDoc.root \\ (XF → lhha) map
            (lhhaElement ⇒ lhhaElement → lhhaElement.attValue("ref")) collect
            { case (lhhaElement, HelpRefMatcher(controlName)) ⇒ lhhaElement → controlName }

        val allUnneededHolders =
            allHelpElements collect {
                case (lhhaElement, controlName) if hasBlankOrMissingLHHAForAllLangsUseDoc(inDoc, controlName, lhha) ⇒
                   lhhaHoldersForAllLangsUseDoc(inDoc, controlName, lhha) :+ lhhaElement
            }

        allUnneededHolders.flatten
    }
}