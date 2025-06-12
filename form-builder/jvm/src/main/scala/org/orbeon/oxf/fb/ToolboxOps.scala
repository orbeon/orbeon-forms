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
import org.orbeon.connection.ConnectionResult
import org.orbeon.datatypes.Coordinate1
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.externalcontext.{ExternalContext, SafeRequestContext, UrlRewriteMode}
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.UndoAction.*
import org.orbeon.oxf.fb.XMLNames.*
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.*
import org.orbeon.oxf.fr.FormRunnerCommon.*
import org.orbeon.oxf.fr.NodeInfoCell.*
import org.orbeon.oxf.fr.Version.OrbeonFormDefinitionVersion
import org.orbeon.oxf.fr.XMLNames.*
import org.orbeon.oxf.http.HttpMethod.GET
import org.orbeon.oxf.pipeline.Transform
import org.orbeon.oxf.processor.XPLConstants
import org.orbeon.oxf.util.*
import org.orbeon.oxf.util.CollectionUtils.*
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.PathUtils.*
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.NodeInfoFactory.*
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.control.controls.XFormsUploadControl
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.{CLASS_QNAME, ID_QNAME}
import org.orbeon.xforms.{Namespaces, XFormsNames}

import java.net.URI
import scala.collection.mutable
import scala.util.Try


object ToolboxOps {

  import Private.*

  // Insert a new control in a cell
  //@XPathFunction
  def insertNewControl(bindingElem: NodeInfo): Option[String] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    import ctx.bindingIndex

    ensureEmptyCell() match {
      case Some(gridTd) =>
        withDebugGridOperation("insert new control") {
          val newControlName = controlNameFromId(nextId("control"))

          // Insert control template
          val newControlElem: NodeInfo =
            findViewTemplate(bindingElem, forEnclosingSection = false) match {
              case Some(viewTemplate) =>
                // There is a specific template available
                val controlElem = insert(into = gridTd, origin = viewTemplate).head
                // xf:help might be in the template, but we don't need it as it is created on demand
                delete(controlElem / "help")

                // https://github.com/orbeon/orbeon-forms/issues/6725
                controlElem.attOpt(CLASS_QNAME).foreach { classAtt =>
                  insert(into = controlElem, origin = attributeInfo(FBClass, classAtt.stringValue))
                  delete(classAtt)
                }

                controlElem
              case _ =>
                // No specific, create simple element with LHHA
                val controlElem =
                  insert(
                    into   = gridTd,
                    origin = elementInfo(bindingFirstURIQualifiedName(bindingElem))
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
          val dataHolder = FormRunnerTemplatesOps.newDataHolder(newControlName, bindingElem)

          // Create resource holder for all form languages
          val resourceHolders = {
            val formLanguages = FormRunnerResourcesOps.allLangs(ctx.resourcesRootElem)
            formLanguages map { formLang =>

              // Resource holders from XBL metadata
              val xblResourceEls     = bindingElem / FBMetadataTest / FBTemplatesTest / FBResourcesTest / *
              val xblResourceElNames = xblResourceEls map (_.localname) toSet

              // Elements for LHHA resources, only keeping those referenced from the view (e.g. a button has no hint)
              val lhhaResourceEls = {
                newControlElem.child(*)
                val lhhaNames = newControlElem.child(*)
                  .map(_.localname)
                  .filter(LHHAResourceNamesToInsert)
                  // Give priority to resources provided by XBL author
                  .filterNot(xblResourceElNames)
                lhhaNames map (elementInfo(_))
              }

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
                    originalTemplateItems map { item =>
                      val newLHHA = (item / *) filter (_.localname != "hint")
                      elementInfo("item", newLHHA)
                    }
                  }
                } else {
                  Nil
                }

              val resourceEls = lhhaResourceEls ++ xblResourceEls ++ itemsResourceEls
              formLang -> List(elementInfo(newControlName, resourceEls))
            }
          }

          // Insert data and resource holders
          insertHolders(
            controlElement       = newControlElem,
            dataHolders          = List(dataHolder),
            resourceHolders      = resourceHolders,
            precedingControlName = precedingBoundControlNameInSectionForControl(newControlElem)
          )

          // Adjust bindings on newly inserted control, done after the control is added as
          // renameControlByElement() expects resources to be present
          renameControlByElement(newControlElem, newControlName)

          // Insert the bind element
          val bind = ensureBinds(findContainerNamesForModel(gridTd) :+ newControlName)

          // Make sure there is a @bind instead of a @ref on the control
          delete(newControlElem /@ "ref")
          ensureAttribute(newControlElem, XFormsNames.BIND_QNAME.localName, bind.id)

          // Set bind attributes if any
          insert(into = bind, origin = findBindAttributesTemplate(bindingElem, forEnclosingSection = false))

          // This can impact templates
          updateTemplatesCheckContainers(findAncestorRepeatNames(gridTd).to(Set))

          Undo.pushUserUndoAction(InsertControl(newControlElem.id))

          Some(newControlName)
        }

      case _ =>
        // no empty td found/created so NOP
        None
    }
  }

  // TODO: Review these. They are probably not needed as of 2017-10-12.
  //@XPathFunction
  def canInsertSection(inDoc: NodeInfo): Boolean = inDoc ne null

  //@XPathFunction
  def canInsertGrid   (inDoc: NodeInfo): Boolean = (inDoc ne null) && findSelectedCell(FormBuilderDocContext(inDoc)).isDefined

  //@XPathFunction
  def canInsertControl(inDoc: NodeInfo): Boolean = (inDoc ne null) && willEnsureEmptyCellSucceed(FormBuilderDocContext(inDoc))

  //@XPathFunction
  def insertNewSection(withGrid: Boolean, suggestedNameOrNull: String): Some[(NodeInfo, NodeInfo)] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    withDebugGridOperation("insert new section") {

      val (into, after) = findSectionInsertionPoint

      val newSectionName    = Option(suggestedNameOrNull).getOrElse(controlNameFromId(nextId("section")))
      lazy val newGridName  = controlNameFromId(nextId("grid"))
      lazy val newCellIdsIt = nextTmpIds(count = 2).iterator

      val precedingSectionName = after flatMap getControlNameOpt

      // NOTE: use xxf:update="full" so that xxf:dynamic can better update top-level XBL controls
      val sectionTemplate: NodeInfo =
        <fr:section id={sectionId(newSectionName)} bind={bindId(newSectionName)} edit-ref="" xxf:update="full"
              xmlns:xh="http://www.w3.org/1999/xhtml"
              xmlns:xf={Namespaces.XF}
              xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
              xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
              xmlns:fr="http://orbeon.org/oxf/xml/form-runner">{
          if (withGrid)
            <fr:grid
              edit-ref=""
              id={gridId(newGridName)}
              bind={bindId(newGridName)}>
              <fr:c id={newCellIdsIt.next()} x="1" y="1" w="6"/><fr:c id={newCellIdsIt.next()} x="7" y="1" w="6"/>
            </fr:grid>
        }</fr:section>

      val newSectionElem       = insert(into = into, after = after.toList, origin = sectionTemplate).head
      val newNestedGridElemOpt = newSectionElem child FRGridTest headOption

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
      val sectionBind = ensureBinds(findContainerNamesForModel(newSectionElem, includeSelf = true))

      newNestedGridElemOpt foreach { newNestedGridElem =>
        insertHolders(
          controlElement       = newNestedGridElem,
          dataHolders          = List(elementInfo(newGridName)),
          resourceHolders      = Nil,
          precedingControlName = None
        )

        // Insert the bind element
        ensureBinds(findContainerNamesForModel(newNestedGridElem, includeSelf = true))
      }

      // This can impact templates
      updateTemplatesCheckContainers(findAncestorRepeatNames(into, includeSelf = true).to(Set))

      // Select first grid cell
      if (withGrid)
        selectFirstCellInContainer(newSectionElem)

      // TODO: Open label editor for newly inserted section

      Undo.pushUserUndoAction(InsertSection(newSectionElem.id))

      Some((newSectionElem, sectionBind))
    }
  }

  //@XPathFunction
  def insertNewGrid(repeated: Boolean): Some[String] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    withDebugGridOperation(s"insert new ${if (repeated) "repeated " else ""}grid") {

      val (into, after, grid) = findGridInsertionPoint

      val newGridName  = controlNameFromId(nextId("grid"))
      val newCellIdsIt = nextTmpIds(count = 2).iterator

      // The grid template
      val gridTemplate: NodeInfo =
        <fr:grid
           edit-ref=""
           id={gridId(newGridName)}
           bind={bindId(newGridName)}
           xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
          <fr:c id={newCellIdsIt.next()} x="1" y="1" w="6"/><fr:c id={newCellIdsIt.next()} x="7" y="1" w="6"/>
        </fr:grid>

      // Insert grid
      val newGridElem = insert(into = into, after = after.toList, origin = gridTemplate).head

      // Insert instance holder (but no resource holders)
      insertHolders(
        controlElement       = newGridElem,
        dataHolders          = List(elementInfo(newGridName)),
        resourceHolders      = Nil,
        precedingControlName = grid flatMap (precedingBoundControlNameInSectionForGrid(_, includeSelf = true))
      )

      // Make sure binds are created
      ensureBinds(findContainerNamesForModel(newGridElem, includeSelf = true))

      // This takes care of all the repeat-related items
      if (repeated)
        setRepeatProperties(
          controlName          = newGridName,
          repeat               = true,
          userCanAddRemove     = true,
          numberRows           = false,
          usePaging            = false,
          min                  = "1",
          max                  = "",
          freeze               = "",
          iterationNameOrEmpty = "",
          applyDefaults        = true,
          initialIterations    = "first"
        )

      // Select new td
      selectFirstCellInContainer(newGridElem)

      Undo.pushUserUndoAction(InsertGrid(newGridElem.id))

      Some(newGridName)
    }
  }

  def selectFirstCellInContainer(containerElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    (containerElem descendant Cell.CellTestName headOption) foreach selectCell

  // Insert a new section template
  //@XPathFunction
  def insertNewSectionTemplate(bindingElem: NodeInfo): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val xblSectionName = bindingFirstURIQualifiedName(bindingElem).localName

    val suggestedSectionName = {

      val allSectionNamesInUse = {

        val (allSections, _) =
          findNestedContainers(ctx.bodyElem) partition IsSection

        allSections flatMap getControlNameOpt toSet
      }

      (! allSectionNamesInUse(xblSectionName)) option xblSectionName
    }

    // Insert new section first
    insertNewSection(withGrid = false,  suggestedSectionName.orNull) map { case (section, bind) =>

      val formSectionName =
        ControlOps.controlNameFromIdOpt(section.id)
          .getOrElse(throw new IllegalStateException)

      // Insert template into section
      findViewTemplate(bindingElem, forEnclosingSection = false) foreach { template =>
        val control     = insert(into = section, after = section / *, origin = template)
        val contentName = formSectionName + TemplateContentSuffix
        val idAttribute = NodeInfoFactory.attributeInfo(ID_QNAME, controlId(contentName))
        insert(into = control, origin = idAttribute)
      }

      findViewTemplate(bindingElem, forEnclosingSection = true) foreach { template =>
        // Propagate `class` from the template to the control
        insert(into = section, origin = template /@ "class")

        // Propagate label and help `mediatype` from the template to the control
        for (lhhaTest <- List(XFLabelTest, XFHelpTest))
          if (hasHTMLMediatype(template / lhhaTest))
            setHTMLMediatype(section / lhhaTest, isHTML = true)
      }

      // Propagate label and help values
      val xblResourcesRootElem =
        (findXblInstance(bindingElem, Names.FormResources).toList / *)
          .headOption
          .getOrElse(throw new IllegalStateException)

      val triples =
        for {
          (lang, xblResource) <- allLangsWithResources(xblResourcesRootElem)
          resourceName        <- List("label", "help")
          xblHolder           <- (xblResource / xblSectionName / resourceName).headOption
        } yield
          (resourceName, lang, xblHolder.stringValue)

      triples.toList.groupByKeepOrder(_._1).foreach { case (resourceName, list) =>
        setControlResourcesWithLang(formSectionName, resourceName, list.map(t => (t._2, List(t._3))))
      }

      // Propagate bind attributes to the `bind` element if present
      insert(into = bind, origin = findBindAttributesTemplate(bindingElem, forEnclosingSection = true))

      UndoAction.InsertSectionTemplate(section.id)
    }
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

  sealed trait XcvEntry extends EnumEntry with Lowercase
  object XcvEntry extends Enum[XcvEntry] {
    val values = findValues
    case object Control   extends XcvEntry
    case object Holder    extends XcvEntry
    case object Resources extends XcvEntry
    case object Bind      extends XcvEntry
  }

  def controlOrContainerElemToXcv(
    controlOrContainerElem : NodeInfo)(implicit
    ctx                    : FormBuilderDocContext
  ): Option[NodeInfo] = { // returns an `<xcv>` root element

    val resourcesRootElem = resourcesRoot

    val controlDetailsOpt =
      searchControlBindPathHoldersInDoc(
        controlElems   = List(controlOrContainerElem),
        contextItemOpt = Some(ctx.dataRootElem),
        predicate      = _ => true
      ).headOption

    // In the context of Form Builder, we *should* always find the information. But there are some cases in tests where
    // the controls to delete don't have ids and binds.
    controlDetailsOpt collect {
      case ControlBindPathHoldersResources(control, bind, _, holders, _) =>

        val bindAsList = List(bind)

        // Handle resources separately since unlike holders and binds, they are not nested
        val resourcesWithLang =
          for {
            rootBind <- bindAsList
            lang     <- FormRunnerResourcesOps.allLangs(resourcesRootElem)
          } yield
            elementInfo(
              "resource",
              attributeInfo(XMLLangQName, lang) ++
                FormBuilder.iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resourcesRootElem)
            )

        val xcvContent =
          XcvEntry.values map {
            case e @ XcvEntry.Control   => e -> List(control)
            case e @ XcvEntry.Holder    => e -> holders.toList.flatten
            case e @ XcvEntry.Resources => e -> resourcesWithLang
            case e @ XcvEntry.Bind      => e -> bindAsList
          }

        val result =
          elementInfo(
            "xcv",
            xcvContent map { case (xcvEntry, content) => elementInfo(xcvEntry.entryName, content) }
          )

        // Remove all `tmp-*-tmp` attributes as they are transient and, instead of renaming them upon paste,
        // we just re-annotate at that time
        result descendant (FRGridTest || NodeInfoCell.CellTest) att "id" filter
          (a => a.stringValue.startsWith("tmp-") && a.stringValue.endsWith("-tmp")) foreach (delete(_))

        result
    }
  }

  def controlElementsInCellToXcv(cellElem: NodeInfo)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] = {
    val name  = getControlName(cellElem / * head)
    findControlByName(name) flatMap controlOrContainerElemToXcv
  }

  // Copy control to the clipboard
  //@XPathFunction
  def copyToClipboard(cellElem: NodeInfo): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    controlElementsInCellToXcv(cellElem)
      .foreach(writeXcvToClipboard)
  }

  // Cut control to the clipboard
  //@XPathFunction
  def cutToClipboard(cellElem: NodeInfo): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    copyToClipboard(cellElem)
    deleteControlWithinCell(cellElem, updateTemplates = true) foreach Undo.pushUserUndoAction
  }

  private val FormBuilderClipboardSessionAttributeName = "orbeon-form-builder-clipboard"

  def readXcvFromClipboard(implicit ctx: FormBuilderDocContext): Option[NodeInfo] =
    Option(NetUtils.getExternalContext) flatMap
      (_.getRequest.getSession(true).getAttribute(FormBuilderClipboardSessionAttributeName)) collect {
        case s: String => TransformerUtils.stringToTinyTree(XPath.GlobalConfiguration, s, false, false).rootElement
      }

  // Returns an `<xcv>` root elementBaseOps.scala
  def readXcvFromClipboardAndClone(implicit ctx: FormBuilderDocContext): Option[NodeInfo] = {

    val xcvElemAsList = readXcvFromClipboard.toList

    (xcvElemAsList / XcvEntry.Control.entryName / * nonEmpty) option {
      val clone = elementInfo("xcv", Nil)
      insert(into = clone, origin = (xcvElemAsList /@ @*) ++ (xcvElemAsList / *))
      clone
    }
  }

  // `xcv` must be an `<xcv>` root element
  def writeXcvToClipboard(xcv: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit =
    Option(NetUtils.getExternalContext) foreach { ec =>
      ec.getRequest.getSession(true).setAttribute(
        FormBuilderClipboardSessionAttributeName,
        TransformerUtils.tinyTreeToString(xcv)
      )
    }

  def dndControl(
    sourceCellElem  : NodeInfo,
    targetCellElem  : NodeInfo,
    copy            : Boolean)(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Option[UndoAction] = {

    val xcvElemOpt = controlElementsInCellToXcv(sourceCellElem)

    val undoDeleteControlOpt =
      withDebugGridOperation("dnd delete") {
        if (! copy)
          deleteControlWithinCell(sourceCellElem, updateTemplates = true)
        else
          None
      }

    val undoInsertControlOpt =
      withDebugGridOperation("dnd paste") {
        selectCell(targetCellElem)
        xcvElemOpt flatMap (pasteSingleControlFromXcv(_, None, copyAttachments = false))
      }

    undoDeleteControlOpt match {
      case Some(undoDeleteControl) => undoInsertControlOpt map (MoveControl(_, undoDeleteControl))
      case None                    => undoInsertControlOpt
    }
  }

  def namesToRenameForMergingSectionTemplate(
    containerId : String,
    prefix      : String,
    suffix      : String)(implicit
    ctx         : FormBuilderDocContext
  ): Option[Seq[(String, String, Boolean)]] =
    for {
      xcvElem     <- xcvFromSectionWithTemplate(containerId)
      sectionElem <- xcvElem / XcvEntry.Control.entryName firstChildOpt *
      sectionName <- getControlNameOpt(sectionElem)
    } yield
      namesToRenameForPaste(xcvElem, prefix, suffix, Set(sectionName))

  def namesToRenameForClipboard(
    prefix      : String,
    suffix      : String)(implicit
    ctx         : FormBuilderDocContext
  ): Option[Seq[(String, String, Boolean)]] =
    readXcvFromClipboardAndClone map
      (namesToRenameForPaste(_, prefix, suffix, Set.empty))

  private def namesToRenameForPaste(
    xcvElem : NodeInfo,
    prefix  : String,
    suffix  : String,
    ignore  : Set[String])(implicit
    ctx     : FormBuilderDocContext
  ): Seq[(String, String, Boolean)] = {

    require(xcvElem.isElement)

    // Remove the `ignore` set. This is for the case where we don't want to rename the enclosing section.
    val xcvNamesInUse =
      (mutable.LinkedHashSet() ++ iterateNamesInUse(Right(xcvElem))).diff(ignore).toList

    def toNameWithPrefixSuffix(name: String) = prefix + name + suffix

    val newControlNamesWithAutomaticIdsMap = {

      val xcvNamesWithPrefixSuffix   = xcvNamesInUse map toNameWithPrefixSuffix
      val allNamesInUse              = getAllNamesInUse
      val needRenameWithAutomaticIds = mutable.LinkedHashSet() ++ xcvNamesWithPrefixSuffix intersect allNamesInUse

      // These names are both subsets of `allNamesInUse`
      val (allSectionNamesInUse, allGridNamesInUse, templateContentNamesToSectionNames) = {

        val (allSections, allGrids) =
          findNestedContainers(ctx.bodyElem) partition IsSection

        (
          allSections.flatMap(getControlNameOpt).toSet,
          allGrids   .flatMap(getControlNameOpt).toSet,
          (
            for {
              sectionElem <- allSections
              sectionName <- getControlNameOpt(sectionElem)
              contentElem <- FormRunner.findComponentElemForSection(sectionElem)
              contentName <- getControlNameOpt(contentElem)
            } yield
              contentName -> sectionName
          ).toMap
        )
      }

      // We do not want to generate a new id that matches the `ignore`d id, so we add `ignore` here.
      // The caller has already removed the section template section from the document so we explicitly
      // make sure here that the enclosing section name is not allowed.
      val namesInUseForNewIds = xcvNamesInUse ++ ignore

      val newControlNamesIt = nextIds("control", needRenameWithAutomaticIds.size, namesInUseForNewIds).iterator map controlNameFromId
      val newSectionNamesIt = nextIds("section", allSectionNamesInUse      .size, namesInUseForNewIds).iterator map controlNameFromId
      val newGridNamesIt    = nextIds("grid",    allGridNamesInUse         .size, namesInUseForNewIds).iterator map controlNameFromId

      // Produce `section-` and `grid-` for sections and grids
      def newName(name: String) =
        if (allSectionNamesInUse(name))
          newSectionNamesIt.next()
        else if (allGridNamesInUse(name))
          newGridNamesIt.next()
        else
          newControlNamesIt.next()

      val resultWithoutSyncedTemplateContent =
        needRenameWithAutomaticIds
          .iterator
          .map(name => name -> newName(name))
          .toMap

      // Try to sync the template content names with the section names
      val newTemplateContentNames =
        needRenameWithAutomaticIds
          .iterator
          .flatMap(name => templateContentNamesToSectionNames.get(name).map(name -> _))
          .flatMap { case (contentName, oldSectionName) =>
            resultWithoutSyncedTemplateContent.get(oldSectionName).flatMap { newSectionName =>
              val newName = s"$newSectionName$TemplateContentSuffix"

              (! resultWithoutSyncedTemplateContent.contains(newName) && ! allNamesInUse.contains(newName))
                .option(contentName -> newName)
            }
          }

      resultWithoutSyncedTemplateContent ++ newTemplateContentNames
    }

    xcvNamesInUse map { xcvName =>

      val withPrefixSuffix = toNameWithPrefixSuffix(xcvName)
      val automaticIdOpt   = newControlNamesWithAutomaticIdsMap.get(withPrefixSuffix)

      (xcvName, automaticIdOpt getOrElse withPrefixSuffix, automaticIdOpt.isDefined)
    }
  }

  def xcvFromSectionWithTemplate(containerId: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] = {

    val containerElem = containerById(containerId)

    // Check this is a section template section
    if (isSectionWithTemplateContent(containerElem)) {

      val head = ctx.formDefinitionRootElem / XHHeadTest head

      xblBindingForSection(head, containerElem) map { bindingDoc =>

        val bindingElem = bindingDoc.rootElement

        val containerName       = getControlName(containerElem)
        val sectionTemplateName = bindingFirstURIQualifiedName(bindingElem).localName

        val resourcesWithLangElems = {

          val sectionLangResources    = findResourceHoldersWithLang(containerName, resourcesRoot)
          val sectionLangResourcesMap = sectionLangResources.toMap

          val nestedResources = {

            val resourcesInstanceElem = findXblInstance(bindingElem, Names.FormResources)
            val resourceElems         = resourcesInstanceElem.toList flatMap (_ child * child *)

            // NOTE: For some reason, there is a resource with the name of the section template. That should
            // be fixed, but in the meanwhile we need to remove those.
            XFormsAPI.delete(resourceElems / * filter (_.localname == sectionTemplateName))

            resourceElems map (e => (e attValue XMLLangQName) -> (e / *))
          }

          val nestedResourcesMap = nestedResources.toMap

          allResources(ctx.resourcesRootElem) map { resource =>

            val lang = resource attValue XMLLangQName

            val sectionHolderForLang = sectionLangResourcesMap.getOrElse(lang, sectionLangResources.head._2)
            val otherHoldersForLang  = nestedResourcesMap.getOrElse(lang, nestedResources.head._2)

            elementInfo(
              "resource",
              attributeInfo(XMLLangQName, lang) ++ sectionHolderForLang ++ otherHoldersForLang
            )
          }
        }

        val newSectionControlElem = {

          val nestedContainers = bindingDoc.rootElement / XBLTemplateTest / * / * filter IsContainer

          val newElem = TransformerUtils.extractAsMutableDocument(containerElem).rootElement

          // NOTE: This duplicates some annotations done in `annotate.xpl`.
          nestedContainers ++ newElem foreach { containerElem =>
            XFormsAPI.ensureAttribute(containerElem, "edit-ref", "")
            if (IsSection(containerElem))
              XFormsAPI.ensureAttribute(containerElem, XXF -> "update", "full")
          }

          XFormsAPI.delete(newElem / * filter isSectionTemplateContent)
          XFormsAPI.insert(into = newElem, after = newElem / *, origin = nestedContainers)
          newElem
        }

        val newDataHolderElem = {

          val dataTemplateInstanceElem = findXblInstance(bindingElem, Names.FormTemplate)
          val nestedDataHolderElems    = dataTemplateInstanceElem.toList flatMap (_ child * take 1 child *)

          val newElem = elementInfo(containerName)
          XFormsAPI.insert(into = newElem, origin = nestedDataHolderElems)
          newElem
        }

        val newBindElem = {

          val nestedBindElems = findXblBinds(bindingElem)

          val newElem = TransformerUtils.extractAsMutableDocument(findBindByName(containerName).get).rootElement
          XFormsAPI.delete(newElem / *)
          XFormsAPI.insert(into = newElem, origin = nestedBindElems)
          newElem
        }

        val xcvContent =
          XcvEntry.values map {
            case e @ XcvEntry.Control   => e -> List(newSectionControlElem)
            case e @ XcvEntry.Holder    => e -> List(newDataHolderElem)
            case e @ XcvEntry.Resources => e -> resourcesWithLangElems
            case e @ XcvEntry.Bind      => e -> List(newBindElem)
          }

        new DocumentWrapper(
          Transform.transformDocument(
            Transform.FileReadDocument("/forms/orbeon/builder/form/annotate-xcv.xsl"),
            Some(
              Transform.InlineReadDocument(
                "",
                TransformerUtils.tinyTreeToOrbeonDom(
                  elementInfo("xcv", xcvContent map { case (xcvEntry, content) => elementInfo(xcvEntry.entryName, content) })
                ),
                0L
              )
            ),
            XPLConstants.UNSAFE_XSLT_PROCESSOR_QNAME
          ),
          null,
          XPath.GlobalConfiguration
        ).rootElement

      }
    } else
      None
  }

  def containerMerge(
    containerId     : String,
    prefix          : String,
    suffix          : String)(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Option[UndoAction] =
    xcvFromSectionWithTemplate(containerId) flatMap { xcvElem =>

      val undoOpt =
        controlOrContainerElemToXcv(containerById(containerId)) map { xcvElem2 =>
          MergeSectionTemplate(
            containerId,
            TransformerUtils.extractAsMutableDocument(xcvElem2).rootElement,
            prefix,
            suffix
          )
        }

      // https://github.com/orbeon/orbeon-forms/issues/6541
      // Find the position before deleting the container
      val insertPosition = {
        val container = findContainerById(containerId)
        ContainerPosition(
          container.flatMap(_.parentOption.filter(IsContainer)).flatMap(getControlNameOpt),
          container.flatMap(_.precedingSibling(*).find(IsContainer)).flatMap(getControlNameOpt)
        )
      }

      deleteSectionByIdIfPossible(
        containerId,
        force = true // https://github.com/orbeon/orbeon-forms/issues/6541
      )

      // Also copy attachments when merging https://github.com/orbeon/orbeon-forms/issues/
      pasteSectionGridFromXcv(
        xcvElem,
        prefix,
        suffix,
        Some(insertPosition),
        Set(controlNameFromId(containerId)),
        copyAttachments = true
      )

      undoOpt
    }

  // Paste control from the clipboard
  //@XPathFunction
  def pasteFromClipboard(): Unit = {
    implicit val ctx             : FormBuilderDocContext = FormBuilderDocContext()
    implicit val formRunnerParams: FormRunnerParams      = FormRunnerParams()
    pasteFromClipboardImpl()
  }

  def pasteFromClipboardImpl()(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Unit =
    readXcvFromClipboardAndClone flatMap { xcvElem =>

      val controlElem = xcvElem / XcvEntry.Control.entryName / * head

      if (IsGrid(controlElem) || IsSection(controlElem)) {
        if (namesToRenameForPaste(xcvElem, "", "", Set.empty) forall (! _._3)) {
          pasteSectionGridFromXcv(xcvElem, "", "", None, Set.empty, copyAttachments = true)
        } else {
          XFormsAPI.dispatch(
            name       = "fb-show-dialog",
            targetId   = "dialog-ids",
            properties = Map("container-id" -> Some(controlElem.id), "action" -> Some("paste"))
          )
          None
        }
      } else
        pasteSingleControlFromXcv(xcvElem, None, copyAttachments = true)
    } foreach
      Undo.pushUserUndoAction

  def pasteSectionGridFromXcv(
    xcvElem         : NodeInfo,
    prefix          : String,
    suffix          : String,
    insertPosition  : Option[ContainerPosition],
    ignore          : Set[String],
    copyAttachments : Boolean
  )(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Option[UndoAction] = {

    require(xcvElem.isElement)

    val containerControlElem = xcvElem / XcvEntry.Control.entryName / * head

    // Handle attachments if needed
    if (copyAttachments) {
      implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext
      updateUnpublishedAttachment(xcvElem / XcvEntry.Holder.entryName / *)
    }

    // Rename control names if needed
    locally {

      val oldToNewNames =
        namesToRenameForPaste(xcvElem, prefix, suffix, ignore) collect {
          case (oldName, newName, _) if oldName != newName => oldName -> newName
        } toMap

      if (oldToNewNames.nonEmpty) {

        // Rename self control, nested sections and grids, and nested controls
        (getControlNameOpt(containerControlElem).isDefined iterator containerControlElem) ++
          findComponentElemForSection(containerControlElem).iterator                      ++
          findNestedContainers(containerControlElem).iterator                             ++
          findNestedControls(containerControlElem).iterator foreach { controlElem =>

          val oldName = controlNameFromId(controlElem.id)

          oldToNewNames.get(oldName) foreach { newName =>
            import ctx.bindingIndex
            renameControlByElement(controlElem, newName)
          }
        }

        // Rename holders
        val holders =
          xcvElem / XcvEntry.Holder.entryName / *

        holders.iterator flatMap iterateSelfAndDescendantHoldersReversed foreach { holderElem =>

          val oldName = holderElem.localname

          val isDefaultIterationName = oldName.endsWith(DefaultIterationSuffix)

          val oldNameAdjusted =
            if (isDefaultIterationName)
              oldName.substring(0, oldName.size - DefaultIterationSuffix.size)
            else
              oldName

          oldToNewNames.get(oldNameAdjusted) foreach { newName =>
            rename(holderElem, if (isDefaultIterationName) newName + DefaultIterationSuffix else newName)
          }
        }

        // Rename resources
        val resourceHolderContainers =
          xcvElem / XcvEntry.Resources.entryName / "resource"

        resourceHolderContainers / * foreach { holderElem =>

          val oldName = holderElem.localname

          oldToNewNames.get(oldName) foreach { newName =>
            rename(holderElem, newName)
          }
        }

        // Rename binds
        (xcvElem / XcvEntry.Bind.entryName / *).iterator flatMap iterateSelfAndDescendantBindsReversed foreach { bindElem =>

          val oldName = findBindName(bindElem).get

          val isDefaultIterationName = oldName.endsWith(DefaultIterationSuffix)

          val oldNameAdjusted =
            if (isDefaultIterationName)
              oldName.substring(0, oldName.size - DefaultIterationSuffix.size)
            else
              oldName

          oldToNewNames.get(oldNameAdjusted) foreach { newName =>
            renameBindElement(bindElem, if (isDefaultIterationName) newName + DefaultIterationSuffix else newName)
          }
        }
      }
    }

    // Rename validation ids if needed
    // NOTE: These are not names so do not really need to be stable.
    locally {

      // Rename nested element ids and alert ids
      val nestedBindElemsWithValidationId =
        for {
          nestedElem   <- xcvElem / XcvEntry.Bind.entryName descendant XFBindTest child NestedBindElemTest
          validationId <- nestedElem.idOpt
        } yield
          nestedElem -> validationId

      val oldIdToNewId =
        nestedBindElemsWithValidationId map (_._2) zip nextTmpIds(token = Names.Validation, count = nestedBindElemsWithValidationId.size) toMap

      // Update nested element ids, in particular xf:constraint/@id
      nestedBindElemsWithValidationId foreach { case (nestedElem, oldId) =>
        setvalue(nestedElem att "id", oldIdToNewId(oldId))
      }

      val alertsWithValidationId =
        for {
          alertElem    <- xcvElem / XcvEntry.Control.entryName descendant (XF -> "alert")
          validationId <- alertElem attValueOpt Names.Validation
        } yield
          alertElem -> validationId

      // Update xf:alert/@validation and xf:constraint/@id
      alertsWithValidationId foreach { case (alertWithValidation, oldValidationId) =>

        val newValidationIdOpt = oldIdToNewId.get(oldValidationId)

        newValidationIdOpt foreach { newValidationId =>
          setvalue(alertWithValidation att Names.Validation, newValidationId)
        }
      }
    }

    val (intoContainerElem, afterElemOpt) =
      insertPosition match {
        case Some(ContainerPosition(into, after)) =>

          val intoContainerElem     = into  flatMap findControlByName
          val afterContainerElemOpt = after flatMap findControlByName

          // Tricky: Within the `fb-body` top-level container, we need to insert after the `<xf:var>`.
          val afterElemOpt =
            if (intoContainerElem.isEmpty)
              afterContainerElemOpt orElse (ctx.bodyElem lastChildOpt "*:var")
            else
              afterContainerElemOpt

          (intoContainerElem getOrElse ctx.bodyElem, afterElemOpt)
        case None if IsGrid(containerControlElem) =>
          val (into, after, _) = findGridInsertionPoint
          (into, after)
        case None =>
          findSectionInsertionPoint
      }

    // NOTE: Now non-repeated grids also have a control name.
    val precedingContainerNameOpt = afterElemOpt flatMap getControlNameOpt

    val newContainerElem =
      insert(
        into   = intoContainerElem,
        after  = afterElemOpt.toList,
        origin = containerControlElem
      ).head

    def resourceHolders(resourceElems: collection.Seq[NodeInfo]): collection.Seq[(String, collection.Seq[NodeInfo])] =
      for {
        resourceElem <- resourceElems
        lang = resourceElem attValue "*:lang"
      } yield
        lang -> (resourceElem / *)

      insertHolders(
        controlElement       = newContainerElem, // in order to find containers
        dataHolders          = xcvElem / XcvEntry.Holder.entryName / *,
        resourceHolders      = resourceHolders(xcvElem / XcvEntry.Resources.entryName / "resource"),
        precedingControlName = precedingContainerNameOpt
      )

    val xcvBinds = xcvElem / XcvEntry.Bind.entryName / *

    if (newContainerElem.hasAtt(XFormsNames.BIND_QNAME.localName)) {
      // Insert the bind element for the container and descendants
      val tmpBind = ensureBinds(findContainerNamesForModel(newContainerElem, includeSelf = true))
      insert(after = tmpBind, origin = xcvBinds)
      delete(tmpBind)
    }

    // Insert template for repeated grids/sections
    (getControlNameOpt(containerControlElem).isDefined iterator containerControlElem) ++
      findNestedContainers(containerControlElem).iterator filter isRepeat foreach  { containerElem =>

      val newControlName = getControlName(containerElem)
      val bindElem       = findBindByName(newControlName).get

      import ctx.bindingIndex

      FormRunnerTemplatesOps.ensureTemplateReplaceContent(
        controlName = newControlName,
        content     = FormRunnerTemplatesOps.createTemplateContentFromBind(bindElem firstChildOpt * head)
      )
    }

    // Update ancestor templates if any
    updateTemplatesCheckContainers(findAncestorRepeatNames(intoContainerElem, includeSelf = true).to(Set))

    // Make sure new grids and cells are annotated
    annotateGridsAndCells(newContainerElem)

    // Select first grid cell
    selectFirstCellInContainer(newContainerElem)

    Some(
      if (IsGrid(newContainerElem))
        InsertGrid(newContainerElem.id)
      else
        InsertSection(newContainerElem.id)
    )
  }

  def pasteSingleControlFromXcv(
    xcvElem         : NodeInfo,
    insertPosition  : Option[ControlPosition],
    copyAttachments : Boolean)(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Option[UndoAction] = {

    val insertCellElemOpt =
      insertPosition match {
        case Some(ControlPosition(gridName, Coordinate1(x, y))) =>
          findControlByName(gridName).toList descendant CellTest collectFirst {
            case cell if NodeInfoCellOps.x(cell).contains(x) && NodeInfoCellOps.y(cell).contains(y) => cell
          }

        case None => ensureEmptyCell()
      }

    insertCellElemOpt map { insertCellElem =>

      val controlElem = xcvElem / XcvEntry.Control.entryName / * head

      def dataHolders = xcvElem / XcvEntry.Holder.entryName / *
      def resources   = xcvElem / XcvEntry.Resources.entryName / "resource" / *

      if (copyAttachments) {
        implicit val ec: ExternalContext = CoreCrossPlatformSupport.externalContext
        updateUnpublishedAttachment(dataHolders)
      }

      val name = {
        val requestedName = getControlName(controlElem)

        // Check if name is already in use
        if (findInViewTryIndex(controlId(requestedName)).isDefined) {
          // If so create new name
          val newName = controlNameFromId(nextId(XcvEntry.Control.entryName))

          // Rename everything
          import ctx.bindingIndex
          renameControlByElement(controlElem, newName)

          dataHolders ++ resources foreach
            (rename(_, newName))

          (xcvElem / XcvEntry.Bind.entryName / * headOption) foreach
            (renameBindElement(_, newName))

          newName
        } else
          requestedName
      }

      // Insert control and holders
      val newControlElem = insert(into = insertCellElem, origin = controlElem).head

      insertHolders(
        controlElement       = newControlElem,
        dataHolders          = dataHolders,
        resourceHolders      = xcvElem / XcvEntry.Resources.entryName / "resource" map (r => (r attValue "*:lang", (r / * headOption).toList)),
        precedingControlName = precedingBoundControlNameInSectionForControl(newControlElem)
      )

      // Create the bind and copy all attributes and content
      val bind = ensureBinds(findContainerNamesForModel(insertCellElem) :+ name)
      (xcvElem / XcvEntry.Bind.entryName / * headOption) foreach { xcvBind =>
        insert(into = bind, origin = (xcvBind /@ @*) ++ (xcvBind / *))
      }

      // Rename nested element ids and alert ids
      val nestedElemsWithId =
        for {
          nestedElem <- bind descendant *
          id         <- nestedElem.idOpt
        } yield
          nestedElem -> id

      val oldIdToNewId =
        nestedElemsWithId map (_._2) zip nextTmpIds(token = Names.Validation, count = nestedElemsWithId.size) toMap

      // Update nested element ids, in particular xf:constraint/@id
      nestedElemsWithId foreach { case (nestedElem, oldId) =>
        setvalue(nestedElem att "id", oldIdToNewId(oldId))
      }

      val alertsWithValidationId =
        for {
          alertElem    <- newControlElem / (XF -> "alert")
          validationId <- alertElem attValueOpt Names.Validation
        } yield
          alertElem -> validationId

      // Update xf:alert/@validation and xf:constraint/@id
      alertsWithValidationId foreach { case (alertWithValidation, oldValidationId) =>

        val newValidationIdOpt = oldIdToNewId.get(oldValidationId)

        newValidationIdOpt foreach { newValidationId =>
          setvalue(alertWithValidation att Names.Validation, newValidationId)
        }
      }

      // This can impact templates
      updateTemplatesCheckContainers(findAncestorRepeatNames(insertCellElem).toSet)

      InsertControl(newControlElem.id)
    }
  }

  private object Private {

    // Unneeded for reading unpublished attachments, which are temporary files
    private implicit val resourceResolver: Option[ResourceResolver] = None

    // This is for `insertNewControl`
    val LHHAResourceNamesToInsert: Set[String] = (LHHA.values.toSet - LHHA.Alert) map (_.entryName)

    // NOTE: Help is added when needed
    val lhhaTemplate: NodeInfo =
      <template xmlns:xf={Namespaces.XF}>
        <xf:label ref=""/>
        <xf:hint  ref=""/>
        <xf:alert ref=""/>
      </template>

    def findGridInsertionPoint(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[NodeInfo], Option[NodeInfo]) =
      findSelectedCell match {
        case Some(currentCellElem) => // A cell is selected

          val containers = findAncestorContainersLeafToRoot(currentCellElem)

          // We must always have a parent (grid) and grandparent (possibly fr:body) container
          assert(containers.size >= 2)

          val parentContainer      = containers.headOption
          val grandParentContainer = containers.tail.head

          // NOTE: At some point we could allow any grid bound and so with a name/id and bind
          //val newGridName = "grid-" + nextId(doc, "grid")

          (grandParentContainer, parentContainer, parentContainer)

        case _ => // No cell is selected, add top-level grid
          (ctx.bodyElem, childrenContainers(ctx.bodyElem) lastOption, None)
      }

    def findSectionInsertionPoint(implicit ctx: FormBuilderDocContext): (NodeInfo, Option[NodeInfo]) =
      findSelectedCell match {
        case Some(currentCellElem) => // A cell is selected

          val containers = findAncestorContainersLeafToRoot(currentCellElem)

          // We must always have a parent (grid) and grandparent (possibly fr:body) container
          assert(containers.size >= 2)

          // Idea: section is inserted after current section/tabview, NOT within current section. If there is no
          // current section/tabview, the section is inserted after the current grid.
          val grandParentContainer            = containers.tail.head // section/tab, body
          val greatGrandParentContainerOption = containers.tail.tail.headOption

          greatGrandParentContainerOption match {
            case Some(greatGrandParentContainer) => (greatGrandParentContainer, Some(grandParentContainer))
            case None                            => (grandParentContainer, grandParentContainer / * headOption)
          }

        case _ => // No cell is selected, add top-level section
          (ctx.bodyElem, childrenContainers(ctx.bodyElem) lastOption)
      }

    private def readUnpublishedAttachment(
      sourceUrl      : String
    )(implicit
      logger         : IndentedLogger,
      externalContext: ExternalContext
    ): Try[(URI, Long)] = {

      // TODO: Check duplication from `FormRunnerCompiler`.
      def connect(path: String): ConnectionResult = {

          val resolvedUri =
            URI.create(
              URLRewriterUtils.rewriteServiceURL(
                externalContext.getRequest,
                path,
                UrlRewriteMode.Absolute
              )
            )

          implicit val safeRequestCtx: SafeRequestContext = SafeRequestContext(externalContext)

          val allHeaders =
            Connection.buildConnectionHeadersCapitalizedIfNeeded(
              url              = resolvedUri,
              hasCredentials   = false,
              customHeaders    = Map(OrbeonFormDefinitionVersion -> List(1.toString)), // Form Builder version is always 1
              headersToForward = Set.empty,
              cookiesToForward = Connection.cookiesToForwardFromProperty,
              getHeader        = Connection.getHeaderFromRequest(externalContext.getRequest)
            )

          Connection.connectNow(
            method      = GET,
            url         = resolvedUri,
            credentials = None,
            content     = None,
            headers     = allHeaders,
            loadState   = true,
            saveState   = true,
            logBody     = false
          )
        }

      ConnectionResult.tryWithSuccessConnection(connect(sourceUrl), closeOnSuccess = true) { is =>
        FileItemSupport.inputStreamToAnyURI(is, ExpirationScope.Session)
      }
    }

    private def collectUnpublishedAttachments(holderElem: NodeInfo)(implicit params: FormRunnerParams): List[AttachmentWithHolder] =
      collectUnsavedAttachments(
        data            = holderElem,
        attachmentMatch =
          AttachmentMatch.BasePaths(
            includes = List(createFormDataBasePath(params.app, params.form, params.isDraft.contains(true), "").appendSlash),
            excludes = Nil
          )
      )

    def updateUnpublishedAttachment(
      holders        : Iterable[NodeInfo]
    )(implicit
      params         : FormRunnerParams,
      logger         : IndentedLogger,
      externalContext: ExternalContext
    ): Unit =
      holders foreach { holderElem =>
        collectUnpublishedAttachments(holderElem) foreach { case AttachmentWithHolder(fromPath, holder) =>

          debug(s"about to update unpublished attachment upon paste for path `$fromPath`")

          readUnpublishedAttachment(fromPath) foreach { case (tmpFileUrl, _) =>

            // The following attempts to preserve file upload metadata if possible. It's just one way of doing it.
            // There is in fact not much benefit in keeping the metadata as temporary file URL parameters, as the values
            // are supposed to be present in the element's attributes already. The HMAC is the more important part.
            val newTmpFileUrl =
              if (isUploadedFileURL(fromPath)) {

                val fromPathParams =
                  PathUtils.splitQueryDecodeParams(fromPath)._2

                XFormsUploadControl.hmacURL(
                  url = tmpFileUrl.toString,
                  fromPathParams.collectFirst { case ("filename",  value) => value },
                  fromPathParams.collectFirst { case ("mediatype", value) => value },
                  fromPathParams.collectFirst { case ("size",      value) => value },
                )
              } else {
                XFormsUploadControl.hmacURL(
                  url = tmpFileUrl.toString,
                  holder.attValueOpt("filename"),
                  holder.attValueOpt("mediatype"),
                  holder.attValueOpt("size"),
                )
              }

            debug(s"setting new attachment URL to `$newTmpFileUrl`")

            setvalue(holder, newTmpFileUrl)
          }
        }
      }
  }
}