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
import org.orbeon.oxf.fb.FormBuilder.{findNestedContainers, _}
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr._
import org.orbeon.oxf.util.CoreUtils._
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

    implicit val ctx = FormBuilderDocContext()

    ensureEmptyCell() match {
      case Some(gridTd) ⇒

        val newControlName = controlNameFromId(nextId("control"))

        // Insert control template
        val newControlElem: NodeInfo =
          findViewTemplate(binding) match {
            case Some(viewTemplate) ⇒
              // There is a specific template available
              val controlElem = insert(into = gridTd, origin = viewTemplate).head
              // xf:help might be in the template, but we don't need it as it is created on demand
              delete(controlElem / "help")
              controlElem
            case _ ⇒
              // No specific, create simple element with LHHA
              val controlElem =
                insert(
                  into   = gridTd,
                  origin = elementInfo(bindingFirstURIQualifiedName(binding))
                ).head

              insert(
                into   = controlElem,
                origin = lhhaTemplate / *
              )

              controlElem
          }

        // Set default pointer to resources if there is an xf:alert
        setvalue(newControlElem / "*:alert" /@ "ref", OldStandardAlertRef)

        // Data holder may contain file attributes
        val dataHolder = newDataHolder(newControlName, binding)

        // Create resource holder for all form languages
        val resourceHolders = {
          val formLanguages = FormRunnerResourcesOps.allLangs(formResourcesRoot)
          formLanguages map { formLang ⇒

            // Elements for LHHA resources, only keeping those referenced from the view (e.g. a button has no hint)
            val lhhaResourceEls = {
              val lhhaNames = newControlElem / * map (_.localname) filter LHHAResourceNamesToInsert
              lhhaNames map (elementInfo(_))
            }

            // Resource holders from XBL metadata
            val xblResourceEls = binding / "*:metadata" / "*:templates" / "*:resources" / *

            // Template items, if needed
            val itemsResourceEls =
              if (hasEditor(newControlElem, "static-itemset")) {
                val fbResourceInFormLang = FormRunnerLang.formResourcesInLang(formLang)
                val originalTemplateItems = fbResourceInFormLang / "template" / "items" / "item"
                if (hasEditor(newControlElem, "item-hint")) {
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
          controlElement       = newControlElem,
          dataHolders          = List(dataHolder),
          resourceHolders      = resourceHolders,
          precedingControlName = precedingControlNameInSectionForControl(newControlElem)
        )

        // Adjust bindings on newly inserted control, done after the control is added as
        // renameControlByElement() expects resources to be present
        renameControlByElement(newControlElem, newControlName, resourceNamesInUseForControl(newControlName))

        // Insert the bind element
        val bind = ensureBinds(doc, findContainerNamesForModel(gridTd) :+ newControlName)

        // Make sure there is a @bind instead of a @ref on the control
        delete(newControlElem /@ "ref")
        ensureAttribute(newControlElem, "bind", bind /@ "id" stringValue)

        // Set bind attributes if any
        insert(into = bind, origin = findBindAttributesTemplate(binding))

        // This can impact templates
        updateTemplatesCheckContainers(findAncestorRepeatNames(gridTd).to[Set])

        debugDumpDocumentForGrids("insert new control")

        Some(newControlName)

      case _ ⇒
        // no empty td found/created so NOP
        None
    }
  }

  //@XPathFunction
  def canInsertSection(inDoc: NodeInfo) = inDoc ne null
  //@XPathFunction
  def canInsertGrid   (inDoc: NodeInfo) = (inDoc ne null) && findSelectedCell(FormBuilderDocContext(inDoc)).isDefined
  //@XPathFunction
  def canInsertControl(inDoc: NodeInfo) = (inDoc ne null) && willEnsureEmptyCellSucceed(FormBuilderDocContext(inDoc))

  // Insert a new grid
  //@XPathFunction
  def insertNewGrid(inDoc: NodeInfo): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val (into, after, _) = findGridInsertionPoint

    // Obtain ids first
    val ids = nextIds("tmp", 3).toIterator

    // The grid template
    val gridTemplate: NodeInfo =
      <fr:grid edit-ref="" id={ids.next()} xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <fr:c id={ids.next()} x="1" y="1" w="6"/><fr:c id={ids.next()} x="7" y="1" w="6"/>
      </fr:grid>

    // Insert after current level 2 if found, otherwise into level 1
    val newGridElem = insert(into = into, after = after.toList, origin = gridTemplate).head

    // This can impact templates
    updateTemplatesCheckContainers(findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Select first grid cell
    selectFirstCellInContainer(newGridElem)

    debugDumpDocumentForGrids("insert new grid")
  }

  // Insert a new section with optionally a nested grid
  //@XPathFunction
  def insertNewSection(inDoc: NodeInfo, withGrid: Boolean): Some[NodeInfo] = {

    implicit val ctx = FormBuilderDocContext()

    val (into, after) = findSectionInsertionPoint

    val newSectionName = controlNameFromId(nextId("section"))
    val precedingSectionName = after flatMap getControlNameOpt

    // Obtain ids first
    val ids = nextIds("tmp", 3).toIterator

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

    val newSectionElem = insert(into = into, after = after.toList, origin = sectionTemplate).head

    // Create and insert holders
    val resourceHolder = {
      val elemContent = List(elementInfo("label"), elementInfo("help"))
      elementInfo(newSectionName, elemContent)
    }

    insertHolderForAllLang(
      controlElement       = newSectionElem,
      dataHolder           = elementInfo(newSectionName),
      resourceHolder       = resourceHolder,
      precedingControlName = precedingSectionName
    )

    // Insert the bind element
    ensureBinds(inDoc, findContainerNamesForModel(newSectionElem, includeSelf = true))

    // This can impact templates
    updateTemplatesCheckContainers(findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Select first grid cell
    if (withGrid)
      selectFirstCellInContainer(newSectionElem)

    // TODO: Open label editor for newly inserted section

    debugDumpDocumentForGrids("insert new section")

    Some(newSectionElem)
  }

  // Insert a new repeat
  //@XPathFunction
  def insertNewRepeatedGrid(inDoc: NodeInfo): Some[String] = {

    implicit val ctx = FormBuilderDocContext()

    val (into, after, grid) = findGridInsertionPoint
    val newGridName         = controlNameFromId(nextId("grid"))

    val ids = nextIds("tmp", 2).toIterator

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
    val newGridElem = insert(into = into, after = after.toList, origin = gridTemplate).head

    // Insert instance holder (but no resource holders)
    insertHolders(
      controlElement       = newGridElem,
      dataHolders          = List(elementInfo(newGridName)),
      resourceHolders      = Nil,
      precedingControlName = grid flatMap (precedingControlNameInSectionForGrid(_, includeSelf = true))
    )

    // Make sure binds are created
    ensureBinds(inDoc, findContainerNamesForModel(newGridElem, includeSelf = true))

    // This takes care of all the repeat-related items
    setRepeatProperties(
      controlName          = newGridName,
      repeat               = true,
      min                  = "1",
      max                  = "",
      iterationNameOrEmpty = "",
      applyDefaults        = true,
      initialIterations    = "first"
    )

    // Select new td
    selectFirstCellInContainer(newGridElem)

    debugDumpDocumentForGrids("insert new repeat")

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

    val inDoc             = containerElem.getDocumentRoot
    val resourcesRootElem = resourcesRoot

    val controlDetailsOpt =
      searchControlBindPathHoldersInDoc(
        controlElems   = List(containerElem),
        inDoc          = inDoc,
        contextItemOpt = Some(formInstanceRoot(inDoc)),
        predicate      = _ ⇒ true
      ).headOption

    val xcvContent =
      controlDetailsOpt match {
        case Some(ControlBindPathHoldersResources(control, bind, _, holders, _)) ⇒
          // The control has a name and a bind

          val bindAsList = List(bind)

          // Handle resources separately since unlike holders and binds, they are not nested
          val resourcesWithLang =
            for {
              rootBind ← bindAsList
              lang     ← FormRunnerResourcesOps.allLangs(resourcesRootElem)
            } yield
              elementInfo(
                "resource",
                attributeInfo(XMLLangQName, lang) ++
                  FormBuilder.iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resourcesRootElem)
              )

          // LATER: handle repetitions, for now keep only the first data holder
          val firstHolderOpt = holders flatMap (_.headOption)

          XcvEntry.values map {
            case e @ XcvEntry.Control   ⇒ e → List(control)
            case e @ XcvEntry.Holder    ⇒ e → firstHolderOpt.toList
            case e @ XcvEntry.Resources ⇒ e → resourcesWithLang
            case e @ XcvEntry.Bind      ⇒ e → bindAsList
          }

        case None ⇒
          // Non-repeated grids don't have a name or a bind.
          // In this case, we use the grid control as a source of truth and find the nested controls.

          val nestedControlDetails = searchControlBindPathHoldersInDoc(
            controlElems   = findNestedControls(containerElem),
            inDoc          = inDoc,
            contextItemOpt = Some(formInstanceRoot(inDoc)),
            predicate      = _ ⇒ true
          )

          val resourcesWithLang = nestedControlDetails flatMap (_.resources) groupBy (_._1) map {
            case (lang, langsAndElems) ⇒
              elementInfo(
                "resource",
                attributeInfo(XMLLangQName, lang) ++ (langsAndElems map (_._2))
              )
          }

          XcvEntry.values map {
            case e @ XcvEntry.Control   ⇒ e → List(containerElem)
            case e @ XcvEntry.Holder    ⇒ e → (nestedControlDetails flatMap (_.holders flatMap (_.headOption)))
            case e @ XcvEntry.Resources ⇒ e → resourcesWithLang.toList
            case e @ XcvEntry.Bind      ⇒ e → (nestedControlDetails map (_.bind))
          }
      }

    val result = elementInfo("xcv", xcvContent map { case (xcvEntry, content) ⇒ elementInfo(xcvEntry.entryName, content) })

    // Remove all `tmp-*-tmp` attributes as they are transient and, instead of renaming them upon paste,
    // we just re-annotate at that time
    result descendant (FRGridTest || NodeInfoCell.CellTest) att "id" filter
      (a ⇒ a.stringValue.startsWith("tmp-") && a.stringValue.endsWith("-tmp")) foreach (delete(_))

    result
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

    implicit val ctx = FormBuilderDocContext()

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

  def clearClipboard(): Unit =
    XcvEntry.values
      .map(_.entryName)
      .foreach(entryName ⇒ delete(clipboardXcvRootElem / entryName))

  def writeXcvToClipboard(xcv: NodeInfo): Unit = {
    clearClipboard()
    insert(into = clipboardXcvRootElem, origin = xcv / *)
  }

  def dndControl(
    sourceCellElem : NodeInfo,
    targetCellElem : NodeInfo,
    copy           : Boolean)(implicit
    ctx            : FormBuilderDocContext
  ): Unit = {

    val xcv = controlElementsInCellToXcv(sourceCellElem)

    if (! copy)
      deleteControlWithinCell(sourceCellElem, updateTemplates = true)

    selectCell(targetCellElem)
    xcv foreach (pasteSingleControlFromXcv(targetCellElem, _))
  }

  // Paste control from the clipboard
  //@XPathFunction
  def pasteFromClipboard(targetCellElem: NodeInfo): Unit = {

    implicit val ctx = FormBuilderDocContext()

    val xcvElem     = readXcvFromClipboard
    val controlElem = xcvElem / XcvEntry.Control.entryName / * head

    if (FormBuilder.IsGrid(controlElem) || FormBuilder.IsSection(controlElem))
      pasteSectionGridFromXcv(targetCellElem, xcvElem)
    else
      pasteSingleControlFromXcv(targetCellElem, xcvElem)
  }

  private def pasteSectionGridFromXcv(
    targetCellElem : NodeInfo,
    xcvElem        : NodeInfo)(implicit
    ctx            : FormBuilderDocContext): Unit = {

    // TODO: Remove once `ctx` is used everywhere
    val inDoc                = ctx.rootElem
    val containerControlElem = xcvElem / XcvEntry.Control.entryName / * head

    // Rename if needed
    locally {

      def findXcvNames           = xcvElem / XcvEntry.Bind.entryName descendant XFBindTest flatMap findBindName
      def existingUniqueNamesSet = getAllControlNames

      val needRename = collection.mutable.LinkedHashSet() ++ findXcvNames intersect existingUniqueNamesSet

      if (needRename.nonEmpty) {

        val newControlNames = nextIds(XcvEntry.Control.entryName, needRename.size) map controlNameFromId
        val oldToNewNames   = needRename.iterator.zip(newControlNames.iterator).toMap

        // Rename self control, nested sections and grids, and nested controls

        (getControlNameOpt(containerControlElem).isDefined iterator containerControlElem) ++
          findNestedContainers(containerControlElem).iterator                             ++
          findNestedControls(containerControlElem).iterator foreach { controlElem ⇒

          val oldName = controlNameFromId(controlElem.id)

          oldToNewNames.get(oldName) foreach { newName ⇒
            renameControlByElement(controlElem, newName, Set("label", "help", "hint", "alert", "itemset"))
          }
        }

        // Rename holders
        (xcvElem / XcvEntry.Holder.entryName / *).iterator flatMap iterateSelfAndDescendantHoldersReversed foreach { holderElem ⇒

          val oldName = holderElem.localname

          val isDefaultIterationName = oldName.endsWith(DefaultIterationSuffix)

          val oldNameAdjusted =
            if (isDefaultIterationName)
              oldName.substring(0, oldName.size - DefaultIterationSuffix.size)
            else
              oldName

          oldToNewNames.get(oldNameAdjusted) foreach { newName ⇒
            rename(holderElem, if (isDefaultIterationName) newName + DefaultIterationSuffix else newName)
          }
        }

        // Rename resources
        val resourceHolders = xcvElem / XcvEntry.Resources.entryName / "resource" / *

        resourceHolders foreach { holderElem ⇒

          val oldName = holderElem.localname

          oldToNewNames.get(oldName) foreach { newName ⇒
            rename(holderElem, newName)
          }
        }

        // Rename binds
        (xcvElem / XcvEntry.Bind.entryName / *).iterator flatMap iterateSelfAndDescendantBindsReversed foreach { bindElem ⇒

          val oldName = findBindName(bindElem).get

          val isDefaultIterationName = oldName.endsWith(DefaultIterationSuffix)

          val oldNameAdjusted =
            if (isDefaultIterationName)
              oldName.substring(0, oldName.size - DefaultIterationSuffix.size)
            else
              oldName

          oldToNewNames.get(oldNameAdjusted) foreach { newName ⇒
            renameBindElement(bindElem, if (isDefaultIterationName) newName + DefaultIterationSuffix else newName)
          }
        }
      }
    }

    val (into, after) =
      if (FormBuilder.IsGrid(containerControlElem)) {
        val (into, after, _) = findGridInsertionPoint
        (into, after)
      } else {
        findSectionInsertionPoint
      }

    // TODO: What if pasting after a non-repeated grid without name? We must try to keep the order of things!
    val precedingContainerName = after flatMap getControlNameOpt

    val newContainerElem =
      insert(into = into, after = after.toList, origin = containerControlElem).head

    val resourceHolders =
      for {
        resourceElem ← xcvElem / XcvEntry.Resources.entryName / "resource"
        lang = resourceElem attValue "*:lang"
      } yield
        lang → (resourceElem / *)

    // Insert holders
    insertHolders(
      controlElement       = newContainerElem, // in order to find containers
      dataHolders          = xcvElem / XcvEntry.Holder.entryName / *,
      resourceHolders      = resourceHolders,
      precedingControlName = precedingContainerName
    )

    // Insert the bind element
    val newBindOrNot = ensureBinds(inDoc, findContainerNamesForModel(newContainerElem, includeSelf = true))

    val newBindElem =
      if (newContainerElem attValueOpt "bind" nonEmpty) {
        // Element has a `bind` so the bind is newly-created
        val result = insert(after = newBindOrNot, origin = xcvElem / XcvEntry.Bind.entryName / *)
        delete(newBindOrNot)
        result.head
      } else {
        // Element doesn't have a `bind` so the bind was already there
        insert(into = newBindOrNot, origin = xcvElem / XcvEntry.Bind.entryName / *).head
      }

    // Insert template for repeated grids/sections
    (getControlNameOpt(containerControlElem).isDefined iterator containerControlElem) ++
      findNestedContainers(containerControlElem).iterator filter isRepeat foreach  { containerElem ⇒

      val newControlName = getControlName(containerElem)
      val bindElem       = findBindByName(inDoc, newControlName).get

      ensureTemplateReplaceContent(
        controlName = newControlName,
        content     = createTemplateContentFromBind(bindElem firstChild * head, componentBindings))
    }

    // Update ancestor templates if any
    updateTemplatesCheckContainers(findAncestorRepeatNames(into, includeSelf = true).to[Set])

    // Make sure new grids and cells are annotated
    annotateGridsAndCells(newContainerElem)

    // Select first grid cell
    selectFirstCellInContainer(newContainerElem)
  }

  private def pasteSingleControlFromXcv(
    targetCellElem : NodeInfo,
    xcvElem        : NodeInfo)(implicit
    ctx            : FormBuilderDocContext
  ): Unit =
    ensureEmptyCell() foreach { gridCellElem ⇒

      implicit val ctx = FormBuilderDocContext()

      val controlElem = xcvElem / XcvEntry.Control.entryName / * head

      def dataHolders = xcvElem / XcvEntry.Holder.entryName / * take 1
      def resources   = xcvElem / XcvEntry.Resources.entryName / "resource" / *

      val name = {
        val requestedName = getControlName(controlElem)

        // Check if id is already in use
        if (findInViewTryIndex(targetCellElem, controlId(requestedName)).isDefined) {
          // If so create new id
          val newName = controlNameFromId(nextId(XcvEntry.Control.entryName))

          // Rename everything
          renameControlByElement(controlElem, newName, resources / * map (_.localname) toSet)

          dataHolders ++ resources foreach
            (rename(_, newName))

          (xcvElem / XcvEntry.Bind.entryName / * headOption) foreach
            (renameBindElement(_, newName))

          newName
        } else
          requestedName
      }

      // Insert control and holders
      val newControlElem = insert(into = gridCellElem, origin = controlElem).head

      insertHolders(
        controlElement       = newControlElem,
        dataHolders          = dataHolders,
        resourceHolders      = xcvElem / XcvEntry.Resources.entryName / "resource" map (r ⇒ (r attValue "*:lang", (r / * headOption).toList)),
        precedingControlName = precedingControlNameInSectionForControl(newControlElem)
      )

      // Create the bind and copy all attributes and content
      val bind = ensureBinds(gridCellElem, findContainerNamesForModel(gridCellElem) :+ name)
      (xcvElem / XcvEntry.Bind.entryName / * headOption) foreach { xcvBind ⇒
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
        nestedElemsWithId map (_._2) zip nextIds(Validation, nestedElemsWithId.size) toMap

      // Update nested element ids, in particular xf:constraint/@id
      nestedElemsWithId foreach { case (nestedElem, oldId) ⇒
        setvalue(nestedElem att "id", oldIdToNewId(oldId))
      }

      val alertsWithValidationId =
        for {
          alertElem    ← newControlElem / (XF → "alert")
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
      updateTemplatesCheckContainers(findAncestorRepeatNames(targetCellElem).to[Set])
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

    def findGridInsertionPoint(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[NodeInfo], Option[NodeInfo]) =
      findSelectedCell match {
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
          (ctx.bodyElem, childrenContainers(ctx.bodyElem) lastOption, None)
      }

    def findSectionInsertionPoint(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[NodeInfo]) =
      findSelectedCell match {
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
          (ctx.bodyElem, childrenContainers(ctx.bodyElem) lastOption)
      }
  }
}