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

import enumeratum.EnumEntry.Lowercase
import enumeratum.{Enum, EnumEntry}
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{Cell, FormRunnerLang, FormRunnerResourcesOps}
import org.orbeon.oxf.xforms.NodeInfoFactory._
import org.orbeon.oxf.xforms.action.XFormsAPI.{insert, _}
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

/*
 * Form Builder: toolbox operations.
 */
object ToolboxOps {

  import Private._

  // Insert a new control in a cell
  //@XPathFunction
  def insertNewControl(doc: NodeInfo, binding: NodeInfo): Option[String] = {

    ensureEmptyCell(doc) match {
      case Some(gridTd) ⇒

        val newControlName = controlNameFromId(nextId(doc, "control"))

        // Insert control template
        val newControlElement: NodeInfo =
          findViewTemplate(binding) match {
            case Some(viewTemplate) ⇒
              // There is a specific template available
              val controlElement = insert(into = gridTd, origin = viewTemplate).head
              // xf:help might be in the template, but we don't need it as it is created on demand
              delete(controlElement / "help")
              controlElement
            case _ ⇒
              // No specific, create simple element with LHHA
              val controlElement =
                insert(
                  into   = gridTd,
                  origin = elementInfo(bindingFirstURIQualifiedName(binding))
                ).head

              insert(
                into   = controlElement,
                origin = lhhaTemplate / *
              )

              controlElement
          }

        // Set default pointer to resources if there is an xf:alert
        setvalue(newControlElement / "*:alert" /@ "ref", OldStandardAlertRef)

        // Data holder may contain file attributes
        val dataHolder = newDataHolder(newControlName, binding)

        // Create resource holder for all form languages
        val resourceHolders = {
          val formLanguages = FormRunnerResourcesOps.allLangs(formResourcesRoot)
          formLanguages map { formLang ⇒

            // Elements for LHHA resources, only keeping those referenced from the view (e.g. a button has no hint)
            val lhhaResourceEls = {
              val lhhaNames = newControlElement / * map (_.localname) filter LHHAResourceNamesToInsert
              lhhaNames map (elementInfo(_))
            }

            // Resource holders from XBL metadata
            val xblResourceEls = binding / "*:metadata" / "*:templates" / "*:resources" / *

            // Template items, if needed
            val itemsResourceEls =
              if (hasEditor(newControlElement, "static-itemset")) {
                val fbResourceInFormLang = FormRunnerLang.formResourcesInLang(formLang)
                val originalTemplateItems = fbResourceInFormLang / "template" / "items" / "item"
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
                Nil
              }

            val resourceEls = lhhaResourceEls ++ xblResourceEls ++ itemsResourceEls
            formLang → List(elementInfo(newControlName, resourceEls))
          }
        }

        // Insert data and resource holders
        insertHolders(
          controlElement       = newControlElement,
          dataHolderOpt        = Some(dataHolder),
          resourceHolders      = resourceHolders,
          precedingControlName = precedingControlNameInSectionForControl(newControlElement)
        )

        // Adjust bindings on newly inserted control, done after the control is added as
        // renameControlByElement() expects resources to be present
        renameControlByElement(newControlElement, newControlName, resourceNamesInUseForControl(newControlName))

        // Insert the bind element
        val bind = ensureBinds(doc, findContainerNamesForModel(gridTd) :+ newControlName)

        // Make sure there is a @bind instead of a @ref on the control
        delete(newControlElement /@ "ref")
        ensureAttribute(newControlElement, "bind", bind /@ "id" stringValue)

        // Set bind attributes if any
        insert(into = bind, origin = findBindAttributesTemplate(binding))

        // This can impact templates
        updateTemplatesCheckContainers(doc, findAncestorRepeatNames(gridTd).to[Set])

        debugDumpDocumentForGrids("insert new control", doc)

        Some(newControlName)

      case _ ⇒
        // no empty td found/created so NOP
        None
    }
  }

  //@XPathFunction
  def canInsertSection(inDoc: NodeInfo) = inDoc ne null
  //@XPathFunction
  def canInsertGrid   (inDoc: NodeInfo) = (inDoc ne null) && findSelectedCell(inDoc).isDefined
  //@XPathFunction
  def canInsertControl(inDoc: NodeInfo) = (inDoc ne null) && willEnsureEmptyCellSucceed(inDoc)

  // Insert a new grid
  //@XPathFunction
  def insertNewGrid(inDoc: NodeInfo): Unit = {

    val (into, after, _) = findGridInsertionPoint(inDoc)

    // Obtain ids first
    val ids = nextIds(inDoc, "tmp", 3).toIterator

    // The grid template
    val gridTemplate: NodeInfo =
      <fr:grid edit-ref="" id={ids.next()} xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <fr:c id={ids.next()} x="1" y="1" w="6"/><fr:c id={ids.next()} x="7" y="1" w="6"/>
      </fr:grid>

    // Insert after current level 2 if found, otherwise into level 1
    val newGridElement = insert(into = into, after = after.toList, origin = gridTemplate).head

    // This can impact templates
    updateTemplatesCheckContainers(inDoc, findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Select first grid cell
    selectFirstCellInContainer(newGridElement)

    debugDumpDocumentForGrids("insert new grid", inDoc)
  }

  // Insert a new section with optionally a nested grid
  //@XPathFunction
  def insertNewSection(inDoc: NodeInfo, withGrid: Boolean): Some[NodeInfo] = {

    val (into, after) = findSectionInsertionPoint(inDoc)

    val newSectionName = controlNameFromId(nextId(inDoc, "section"))
    val precedingSectionName = after flatMap getControlNameOpt

    // Obtain ids first
    val ids = nextIds(inDoc, "tmp", 3).toIterator

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
            <fr:c id={ids.next()} x="1" y="1" w="6"/><fr:c id={ids.next()} x="7" y="1" w="6"/>
          </fr:grid>
      }</fr:section>

    val newSectionElement = insert(into = into, after = after.toList, origin = sectionTemplate).head

    // Create and insert holders
    val resourceHolder = {
      val elementContent = List(elementInfo("label"), elementInfo("help"))
      elementInfo(newSectionName, elementContent)
    }

    insertHolderForAllLang(
      controlElement       = newSectionElement,
      dataHolder           = elementInfo(newSectionName),
      resourceHolder       = resourceHolder,
      precedingControlName = precedingSectionName
    )

    // Insert the bind element
    ensureBinds(inDoc, findContainerNamesForModel(newSectionElement, includeSelf = true))

    // This can impact templates
    updateTemplatesCheckContainers(inDoc, findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Select first grid cell
    if (withGrid)
      selectFirstCellInContainer(newSectionElement)

    // TODO: Open label editor for newly inserted section

    debugDumpDocumentForGrids("insert new section", inDoc)

    Some(newSectionElement)
  }

  // Insert a new repeat
  //@XPathFunction
  def insertNewRepeatedGrid(inDoc: NodeInfo): Some[String] = {

    val (into, after, grid) = findGridInsertionPoint(inDoc)
    val newGridName         = controlNameFromId(nextId(inDoc, "grid"))

    val ids = nextIds(inDoc, "tmp", 2).toIterator

    // The grid template
    val gridTemplate: NodeInfo =
      <fr:grid
         edit-ref=""
         id={gridId(newGridName)}
         bind={bindId(newGridName)}
         xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <fr:c id={ids.next()} x="1" y="1" w="6"/><fr:c id={ids.next()} x="7" y="1" w="6"/>
      </fr:grid>

    // Insert grid
    val newGridElement = insert(into = into, after = after.toList, origin = gridTemplate).head

    // Insert instance holder (but no resource holders)
    insertHolders(
      controlElement       = newGridElement,
      dataHolderOpt        = Some(elementInfo(newGridName)),
      resourceHolders      = Nil,
      precedingControlName = grid flatMap (precedingControlNameInSectionForGrid(_, includeSelf = true))
    )

    // Make sure binds are created
    ensureBinds(inDoc, findContainerNamesForModel(newGridElement, includeSelf = true))

    // This takes care of all the repeat-related items
    setRepeatProperties(
      inDoc                = inDoc,
      controlName          = newGridName,
      repeat               = true,
      min                  = "1",
      max                  = "",
      iterationNameOrEmpty = "",
      applyDefaults        = true,
      initialIterations    = "first"
    )

    // Select new td
    selectFirstCellInContainer(newGridElement)

    debugDumpDocumentForGrids("insert new repeat", inDoc)

    Some(newGridName)
  }

  private def selectFirstCellInContainer(container: NodeInfo): Unit =
    (container descendant Cell.CellTestName headOption) foreach selectCell

  // Insert a new section template
  //@XPathFunction
  def insertNewSectionTemplate(inDoc: NodeInfo, binding: NodeInfo): Unit =
    // Insert new section first
    insertNewSection(inDoc, withGrid = false) foreach { section ⇒

      val selector = binding /@ "element" stringValue

      val model = findModelElem(inDoc)
      val xbl = model followingSibling (XBL → "xbl")
      val existingBindings = xbl child (XBL → "binding")

      // Insert binding into form if needed
      if (! (existingBindings /@ "element" === selector))
        insert(after = model +: xbl, origin = binding parent * )

      // Insert template into section
      findViewTemplate(binding) foreach
        (template ⇒ insert(into = section, after = section / *, origin = template))
    }

  /* Example layout:
  <xcv>
    <control>
      <xf:input id="control-1-control" bind="control-1-bind">
        <xf:label ref="$form-resources/control-1/label"/>
        <xf:hint ref="$form-resources/control-1/hint"/>
        <xf:alert ref="$fr-resources/detail/labels/alert"/>
      </xf:input>
    </control>
    <holder>
      <control-1/>
    </holder>
    <resources>
      <resource xml:lang="en">
        <control-1>
          <label>My label</label>
          <hint/>
          <alert/>
        </control-1>
      </resource>
    </resources>
    <bind>
      <xf:bind id="control-1-bind" name="control-1" ref="control-1"/>
    </bind>
  </xcv>
  */

  sealed abstract class XcvEntry extends EnumEntry with Lowercase
  object XcvEntry extends Enum[XcvEntry] {
    val values = findValues
    case object Control   extends XcvEntry
    case object Holder    extends XcvEntry
    case object Resources extends XcvEntry
    case object Bind      extends XcvEntry
  }

  def controlOrContainerElemToXcv(containerElem: NodeInfo): NodeInfo = {

    val inDoc   = containerElem.getDocumentRoot
    val nameOpt = getControlNameOpt(containerElem) // non-repeated grids don't have a name

    // Create <resource xml:lang="..."> containers
    val resourcesWithLang =
      for {
        controlName       ← nameOpt.toList
        rootBind          ← findBindByName(inDoc, controlName).toList
        resourcesRootElem = resourcesRoot
        lang              ← FormRunnerResourcesOps.allLangs(resourcesRootElem)
      } yield
        elementInfo(
          "resource",
          attributeInfo(XMLLangQName, lang) ++
            FormBuilder.iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resourcesRootElem)
        )

    val xcvContent =
      Map(
        XcvEntry.Control   → List(containerElem),
        XcvEntry.Holder    → (nameOpt.toList flatMap (findDataHolders(inDoc, _))),
        XcvEntry.Resources → resourcesWithLang,
        XcvEntry.Bind      → (nameOpt.toList flatMap (findBindByName(inDoc, _)))
      ) map { case (xcvEntry, content) ⇒
        elementInfo(xcvEntry.entryName, content)
      } toList

    elementInfo("xcv", xcvContent)
  }

  private def controlElementsInCellToXcv(cellElem: NodeInfo): Option[NodeInfo] = {

    val inDoc = cellElem.getDocumentRoot
    val name  = getControlName(cellElem / * head)

    findControlByName(inDoc, name) map controlOrContainerElemToXcv
  }

  // Copy control to the clipboard
  //@XPathFunction
  def copyToClipboard(cellElem: NodeInfo): Unit =
    controlElementsInCellToXcv(cellElem)
      .foreach(writeXcvToClipboard)

  // Cut control to the clipboard
  //@XPathFunction
  def cutToClipboard(cellElem: NodeInfo): Unit = {
    copyToClipboard(cellElem)
    deleteControlWithinCell(cellElem, updateTemplates = true)
  }

  private def clipboardXcvRootElem: NodeInfo =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("xcv")

  def readXcvFromClipboard: NodeInfo = {
    val clipboard = clipboardXcvRootElem
    val clone = elementInfo("xcv", Nil)
    insert(into = clone, origin = clipboard / *)
    clone
  }

  def writeXcvToClipboard(xcv: NodeInfo): Unit = {
    val clipboard = clipboardXcvRootElem
    XcvEntry.values
        .map(_.entryName)
        .foreach(entryName ⇒ delete(clipboard / entryName))
    insert(into = clipboard, origin = xcv / *)
  }

  def dndControl(sourceCellElem: NodeInfo, targetCellElem: NodeInfo, copy: Boolean): Unit = {
    val xcv = controlElementsInCellToXcv(sourceCellElem)

    if (! copy)
      deleteControlWithinCell(sourceCellElem, updateTemplates = true)

    selectCell(targetCellElem)
    xcv foreach (pasteFromXcv(targetCellElem, _))
  }

  // Paste control from the clipboard
  //@XPathFunction
  def pasteFromClipboard(targetCellElem: NodeInfo): Unit = {

    val inDoc       = FormBuilder.fbFormInstance.root
    val controlElem = clipboardXcvRootElem / XcvEntry.Control.entryName / * head

    if (FormBuilder.IsGrid(controlElem) || FormBuilder.IsSection(controlElem)) {

      val (into, after) =
        if (FormBuilder.IsGrid(controlElem)) {
          val (into, after, _) = findGridInsertionPoint(inDoc)
          (into, after)
        } else {
          findSectionInsertionPoint(inDoc)
        }

      val precedingContainerName = after flatMap getControlNameOpt

      val newContainerElement = insert(into = into, after = after.toList, origin = controlElem).head

      val resourceHolders =
        for {
          resourceElem ← clipboardXcvRootElem / XcvEntry.Resources.entryName / "resource"
          lang = resourceElem attValue "*:lang"
        } yield
          lang → (resourceElem / *)

      // Insert holders
      insertHolders(
        controlElement       = newContainerElement,
        dataHolderOpt        = clipboardXcvRootElem / XcvEntry.Holder.entryName / * headOption,
        resourceHolders      = resourceHolders,
        precedingControlName = precedingContainerName
      )

      // Insert the bind element
      val newBind = ensureBinds(inDoc, findContainerNamesForModel(newContainerElement, includeSelf = true))
      insert(after = newBind, origin = clipboardXcvRootElem / XcvEntry.Bind.entryName / *)
      delete(newBind)

      // This can impact templates
      updateTemplatesCheckContainers(inDoc, findAncestorRepeatNames(into, includeSelf = true).to[Set])

      // Select first grid cell
      selectFirstCellInContainer(newContainerElement)
    } else
      pasteFromXcv(targetCellElem, readXcvFromClipboard)
  }

  private def pasteFromXcv(targetCellElem: NodeInfo, xcv: NodeInfo): Unit = {
    ensureEmptyCell(targetCellElem) foreach { gridCellElem ⇒

      (xcv / XcvEntry.Control.entryName / * headOption) foreach { control ⇒

        def holders   = xcv / XcvEntry.Holder.entryName / *
        def resources = xcv / XcvEntry.Resources.entryName / "resource" / *

        val name = {
          val requestedName = getControlName(control)

          // Check if id is already in use
          if (findInViewTryIndex(targetCellElem, controlId(requestedName)).isDefined) {
            // If so create new id
            val newName = controlNameFromId(nextId(targetCellElem, XcvEntry.Control.entryName))

            // Rename everything
            renameControlByElement(control, newName, resources / * map (_.localname) toSet)

            holders ++ resources foreach
              (rename(_, newName))

            (xcv / XcvEntry.Bind.entryName / * headOption) foreach
              (renameBindElement(_, newName))

            newName
          } else
            requestedName
        }

        // Insert control and holders
        val newControlElement = insert(into = gridCellElem, origin = control).head

        insertHolders(
          controlElement       = newControlElement,
          dataHolderOpt        = holders.headOption,
          resourceHolders      = xcv / XcvEntry.Resources.entryName / "resource" map (r ⇒ (r attValue "*:lang", (r / * headOption).toList)),
          precedingControlName = precedingControlNameInSectionForControl(newControlElement)
        )

        // Create the bind and copy all attributes and content
        val bind = ensureBinds(gridCellElem, findContainerNamesForModel(gridCellElem) :+ name)
        (xcv / XcvEntry.Bind.entryName / * headOption) foreach { xcvBind ⇒
          insert(into = bind, origin = (xcvBind /@ @*) ++ (xcvBind / *))
        }

        import org.orbeon.oxf.fr.Names._

        // Rename nested element ids and alert ids
        val nestedElemsWithId =
          for {
            nestedElem ← bind descendant *
            id         ← nestedElem.idOpt
          } yield
            nestedElem → id

        val oldIdToNewId =
          nestedElemsWithId map (_._2) zip nextIds(targetCellElem, Validation, nestedElemsWithId.size) toMap

        // Update nested element ids, in particular xf:constraint/@id
        nestedElemsWithId foreach { case (nestedElem, oldId) ⇒
          setvalue(nestedElem att "id", oldIdToNewId(oldId))
        }

        val alertsWithValidationId =
          for {
            alertElem    ← newControlElement / (XF → "alert")
            validationId ← alertElem attValueOpt Validation
          } yield
            alertElem → validationId

        // Update xf:alert/@validation and xf:constraint/@id
        alertsWithValidationId foreach { case (alertWithValidation, oldValidationId) ⇒

          val newValidationIdOpt = oldIdToNewId.get(oldValidationId)

          newValidationIdOpt foreach { newValidationId ⇒
            setvalue(alertWithValidation att Validation, newValidationId)
          }
        }

        // This can impact templates
        updateTemplatesCheckContainers(targetCellElem, findAncestorRepeatNames(targetCellElem).to[Set])
      }
    }
  }

  private object Private {

    val LHHAResourceNamesToInsert = LHHANames - "alert"

    // NOTE: Help is added when needed
    val lhhaTemplate: NodeInfo =
      <template xmlns:xf="http://www.w3.org/2002/xforms">
        <xf:label ref=""/>
        <xf:hint  ref=""/>
        <xf:alert ref=""/>
      </template>

    def findGridInsertionPoint(inDoc: NodeInfo): (NodeInfo, Option[NodeInfo], Option[NodeInfo]) =
      findSelectedCell(inDoc) match {
        case Some(currentCellElem) ⇒ // A cell is selected

          val containers = findAncestorContainersLeafToRoot(currentCellElem)

          // We must always have a parent (grid) and grandparent (possibly fr:body) container
          assert(containers.size >= 2)

          val parentContainer      = containers.headOption
          val grandParentContainer = containers.tail.head

          // NOTE: At some point we could allow any grid bound and so with a name/id and bind
          //val newGridName = "grid-" + nextId(doc, "grid")

          (grandParentContainer, parentContainer, parentContainer)

        case _ ⇒ // No cell is selected, add top-level grid
          val frBody = findFRBodyElem(inDoc)
          (frBody, childrenContainers(frBody) lastOption, None)
      }

    def findSectionInsertionPoint(inDoc: NodeInfo): (NodeInfo, Option[NodeInfo]) =
      findSelectedCell(inDoc) match {
        case Some(currentCellElem) ⇒ // A cell is selected

          val containers = findAncestorContainersLeafToRoot(currentCellElem)

          // We must always have a parent (grid) and grandparent (possibly fr:body) container
          assert(containers.size >= 2)

          // Idea: section is inserted after current section/tabview, NOT within current section. If there is no
          // current section/tabview, the section is inserted after the current grid.
          val grandParentContainer            = containers.tail.head // section/tab, body
          val greatGrandParentContainerOption = containers.tail.tail.headOption

          greatGrandParentContainerOption match {
            case Some(greatGrandParentContainer) ⇒ (greatGrandParentContainer, Some(grandParentContainer))
            case None                            ⇒ (grandParentContainer, grandParentContainer / * headOption)
          }

        case _ ⇒ // No cell is selected, add top-level section
          val frBody = findFRBodyElem(inDoc)
          (frBody, childrenContainers(frBody) lastOption)
      }
  }
}