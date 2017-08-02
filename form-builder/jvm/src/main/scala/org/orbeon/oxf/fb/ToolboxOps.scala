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
import org.orbeon.oxf.fr.{FormRunnerLang, FormRunnerResourcesOps}
import org.orbeon.oxf.xforms.NodeInfoFactory._
import org.orbeon.oxf.xforms.action.XFormsAPI.{insert, _}
import org.orbeon.oxf.xml.XMLConstants.XML_URI
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

    ensureEmptyTd(doc) match {
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
                Seq.empty
              }

            val resourceEls = lhhaResourceEls ++ xblResourceEls ++ itemsResourceEls
            formLang → elementInfo(newControlName, resourceEls)
          }
        }

        // Insert data and resource holders
        insertHolders(
          controlElement       = newControlElement,
          dataHolder           = dataHolder,
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
  def canInsertGrid   (inDoc: NodeInfo) = (inDoc ne null) && findSelectedTd(inDoc).isDefined
  //@XPathFunction
  def canInsertControl(inDoc: NodeInfo) = (inDoc ne null) && willEnsureEmptyTdSucceed(inDoc)

  // Insert a new grid
  //@XPathFunction
  def insertNewGrid(inDoc: NodeInfo): Unit = {

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
    val newGridElement = insert(into = into, after = after.toSeq, origin = gridTemplate).head

    // This can impact templates
    updateTemplatesCheckContainers(inDoc, findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Select first grid cell
    selectTd(newGridElement descendant "*:td" head)

    debugDumpDocumentForGrids("insert new grid", inDoc)
  }

  // Insert a new section with optionally a nested grid
  //@XPathFunction
  def insertNewSection(inDoc: NodeInfo, withGrid: Boolean): Some[NodeInfo] = {

    val (into, after) = findSectionInsertionPoint(inDoc)

    val newSectionName = controlNameFromId(nextId(inDoc, "section"))
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
      selectTd(newSectionElement descendant "*:td" head)

    // TODO: Open label editor for newly inserted section

    debugDumpDocumentForGrids("insert new section", inDoc)

    Some(newSectionElement)
  }

  // Insert a new repeat
  //@XPathFunction
  def insertNewRepeatedGrid(inDoc: NodeInfo): Some[String] = {

    val (into, after, grid) = findGridInsertionPoint(inDoc)
    val newGridName         = controlNameFromId(nextId(inDoc, "grid"))

    // The grid template
    val gridTemplate: NodeInfo =
      <fr:grid edit-ref=""
           id={gridId(newGridName)}
           bind={bindId(newGridName)}
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
      controlElement       = newGridElement,
      dataHolder           = elementInfo(newGridName),
      resourceHolders      = Seq.empty,
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
    selectTd(newGridElement descendant "*:td" head)

    debugDumpDocumentForGrids("insert new repeat", inDoc)

    Some(newGridName)
  }

  // Insert a new section template
  //@XPathFunction
  def insertNewSectionTemplate(inDoc: NodeInfo, binding: NodeInfo): Unit =
    // Insert new section first
    insertNewSection(inDoc, withGrid = false) foreach { section ⇒

      val selector = binding /@ "element" stringValue

      val model = findModelElement(inDoc)
      val xbl = model followingSibling (XBL → "xbl")
      val existingBindings = xbl child (XBL → "binding")

      // Insert binding into form if needed
      if (! (existingBindings /@ "element" === selector))
        insert(after = Seq(model) ++ xbl, origin = binding parent * )

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

  sealed abstract class XvcEntry extends EnumEntry with Lowercase
  object XvcEntry extends Enum[XvcEntry] {
    val values = findValues
    case object Control   extends XvcEntry
    case object Holder    extends XvcEntry
    case object Resources extends XvcEntry
    case object Bind      extends XvcEntry
  }

  private def controlElementsInCellToXvc(td: NodeInfo): Option[NodeInfo] = {

    val doc  = td.getDocumentRoot
    val name = getControlName(td / * head)

    findControlByName(doc, name).map { controlElement ⇒

      // Create <resource xml:lang="..."> containers
      val resourcesWithLang = FormRunnerResourcesOps.findResourceHoldersWithLang(name, resourcesRoot) map {
        case (lang, holder) ⇒ elementInfo("resource", attributeInfo(XML_URI → "lang", lang) ++ holder)
      }

      val xvcContent =
        Map(
          XvcEntry.Control   → List(controlElement),
          XvcEntry.Holder    → findDataHolders(doc, name),
          XvcEntry.Resources → resourcesWithLang,
          XvcEntry.Bind      → findBindByName(doc, name).toList
        ).map { case (xvcEntry, content) ⇒
          elementInfo(xvcEntry.entryName, content)
        }.toList
      elementInfo("xvc", xvcContent)
    }
  }

  // Copy control to the clipboard
  //@XPathFunction
  def copyToClipboard(td: NodeInfo): Unit =
    controlElementsInCellToXvc(td)
      .foreach(writeXvcToClipboard)

  // Cut control to the clipboard
  //@XPathFunction
  def cutToClipboard(td: NodeInfo): Unit = {
    copyToClipboard(td)
    deleteCellContent(td, updateTemplates = true)
  }

  private def clipboardXvc: NodeInfo =
    topLevelModel("fr-form-model").get.unsafeGetVariableAsNodeInfo("xcv")

  def readXvcFromClipboard: NodeInfo = {
    val clipboard = clipboardXvc
    val clone = elementInfo("xvc", Nil)
    insert(into = clone, origin = clipboard / *)
    clone
  }

  def writeXvcToClipboard(xvc: NodeInfo): Unit = {
    val clipboard = clipboardXvc
    XvcEntry.values
        .map(_.entryName)
        .foreach(entryName ⇒ delete(clipboard / entryName))
    insert(into = clipboard, origin = xvc/ *)
  }

  //@XPathFunction
  def dndControl(source: NodeInfo, target: NodeInfo, copy: Boolean): Unit = {
    controlElementsInCellToXvc(source)
      .foreach(pasteFromXvc(target, _))
    if (! copy)
      deleteCellContent(source, updateTemplates = true)
  }

  // Paste control from the clipboard
  //@XPathFunction
  def pasteFromClipboard(td: NodeInfo): Unit =
    pasteFromXvc(td, readXvcFromClipboard)

  private def pasteFromXvc(td: NodeInfo, xvc: NodeInfo): Unit = {
    ensureEmptyTd(td) foreach { gridTd ⇒

      (xvc / "control" / * headOption) foreach { control ⇒

        def holders   = xvc / "holder" / *
        def resources = xvc / "resources" / "resource" / *

        val name = {
          val requestedName = getControlName(control)

          // Check if id is already in use
          if (findInViewTryIndex(td, controlId(requestedName)).isDefined) {
            // If so create new id
            val newName = controlNameFromId(nextId(td, "control"))

            // Rename everything
            renameControlByElement(control, newName, resources / * map (_.localname) toSet)

            holders ++ resources foreach
              (rename(_, newName))

            (xvc / "bind" / * headOption) foreach
              (renameBindElement(_, newName))

            newName
          } else
            requestedName
        }

        // Insert control and holders
        val newControlElement = insert(into = gridTd, origin = control).head
        insertHolders(
          newControlElement,
          holders.head,
          xvc / "resources" / "resource" map (r ⇒ (r attValue "*:lang", r / * head)),
          precedingControlNameInSectionForControl(newControlElement)
        )

        // Create the bind and copy all attributes and content
        val bind = ensureBinds(gridTd, findContainerNamesForModel(gridTd) :+ name)
        (xvc / "bind" / * headOption) foreach { xvcBind ⇒
          insert(into = bind, origin = (xvcBind /@ @*) ++ (xvcBind / *))
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
          nestedElemsWithId map (_._2) zip nextIds(td, Validation, nestedElemsWithId.size) toMap

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
        updateTemplatesCheckContainers(td, findAncestorRepeatNames(td).to[Set])
      }
    }
  }

  private object Private {

    val LHHAResourceNamesToInsert = LHHANames - "alert"

    // NOTE: Help is added when needed
    val lhhaTemplate: NodeInfo =
      <template xmlns:xf="http://www.w3.org/2002/xforms">
        <xf:label ref=""/>
        <xf:hint ref=""/>
        <xf:alert ref=""/>
      </template>

    def findGridInsertionPoint(inDoc: NodeInfo) =
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

    def findSectionInsertionPoint(inDoc: NodeInfo) =
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
            case None ⇒ (grandParentContainer, grandParentContainer / * headOption)
          }

        case _ ⇒ // No td is selected, add top-level section
          val frBody = findFRBodyElement(inDoc)
          (frBody, childrenContainers(frBody) lastOption)
      }
  }
}