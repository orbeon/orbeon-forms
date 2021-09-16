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

import org.orbeon.datatypes.Coordinate1
import org.orbeon.dom.QName
import org.orbeon.oxf.fb.UndoAction._
import org.orbeon.oxf.fb.XMLNames._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.NodeInfoCell._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.fr.{FormRunner, FormRunnerDocContext, FormRunnerTemplatesOps, Names}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath
import org.orbeon.oxf.xforms.NodeInfoFactory._
import org.orbeon.xforms.XFormsNames._
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.model.ModelDefs.MIP
import org.orbeon.oxf.xforms.analysis.model.{DependencyAnalyzer, ModelDefs}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xml.SaxonUtils
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.Constants.ComponentSeparator
import org.orbeon.xforms.XFormsId
import org.orbeon.xml.NamespaceMapping

import scala.annotation.tailrec
import scala.collection.compat._
import scala.collection.mutable


/*
 * Form Builder: operations on controls.
 */
trait ControlOps extends ResourcesOps {

  self: GridOps => // funky dependency, to resolve at some point

  private val MIPsToRewrite = ModelDefs.AllMIPs - ModelDefs.Type - ModelDefs.Required - ModelDefs.Whitespace
  private val RewrittenMIPs = MIPsToRewrite map (mip => mip -> QName(mip.name, XMLNames.FBPrefix, XMLNames.FB)) toMap

  private val FRResourceElemLocalNamesToQNames = List(FRTextQName, FRIterationLabelQName) map (v => v.localName -> v) toMap

  private val PossibleResourcePointerNames: Set[String] =
    (LHHA.values map (_.entryName)).to(Set) ++
      FRResourceElemLocalNamesToQNames.keys +
      "itemset"

  def controlLHHATQName(lhhaName: String): QName =
    FRResourceElemLocalNamesToQNames.get(lhhaName)         orElse
    (LHHA.withNameOption(lhhaName) map LHHA.QNameForValue) getOrElse
    (throw new IllegalArgumentException(lhhaName))

  private val TopLevelBindTemplate: NodeInfo =
    <xf:bind
      id={Names.FormBinds}
      ref="instance('fr-form-instance')"
      xmlns:xf="http://www.w3.org/2002/xforms"/>

  def precedingBoundControlNameInSectionForControl(controlElem: NodeInfo): Option[String] = {

    val cell = controlElem parent CellTest head
    val grid = findAncestorContainersLeafToRoot(cell).head

    assert(cell.localname == "c")
    assert(grid.localname == "grid")

    val precedingCellsInGrid = cell precedingSibling CellTest

    def fromPrecedingNamesInGrid = precedingCellsInGrid flatMap (_ firstChildOpt * flatMap getControlNameOpt) headOption

    def fromPrecedingGrids =
      if (grid.hasAtt(BIND_QNAME))
        None
      else
        precedingBoundControlNameInSectionForGrid(grid, includeSelf = false)

    fromPrecedingNamesInGrid orElse fromPrecedingGrids
  }

  def precedingBoundControlNameInSectionForGrid(gridElem: NodeInfo, includeSelf: Boolean): Option[String] = {

    // If a container has a `bind`, then use its name, otherwise it is an unbound grid so find its last control
    // with a name (there might not be one).
    val boundControls =
      precedingSiblingOrSelfContainers(gridElem, includeSelf) flatMap {
        case grid if ! grid.hasAtt(BIND_QNAME) => grid descendant CellTest child * filter hasName lastOption
        case boundSectionOrGrid                => Some(boundSectionOrGrid)
      }

    // Take the first result
    boundControls.headOption flatMap getControlNameOpt
  }

  // Ensure that a tree of bind exists
  def ensureBinds(names: Seq[String])(implicit ctx: FormBuilderDocContext): NodeInfo = {

    // Insert bind container if needed
    val topLevelBind = ctx.topLevelBindElem match {
      case Some(bind) =>
        bind
      case None =>
        insert(
          into   = ctx.modelElem,
          after  = ctx.dataInstanceElem,
          origin = TopLevelBindTemplate
        ).head
    }

    // Insert a bind into one level
    @tailrec def ensureBind(containerElem: NodeInfo, names: Iterator[String]): NodeInfo = {
      if (names.hasNext) {
        val bindName = names.next()
        val bind = containerElem / XFBindTest filter (isBindForName(_, bindName)) match {
          case Seq(bind: NodeInfo, _*) => bind
          case _ =>

            val newBind: Seq[NodeInfo] =
              <xf:bind
                id={bindId(bindName)}
                ref={bindName}
                name={bindName}
                xmlns:xf="http://www.w3.org/2002/xforms"/>

            insert(into = containerElem, after = containerElem / XFBindTest, origin = newBind).head
        }
        ensureBind(bind, names)
      } else
        containerElem
    }

    // Start with top-level
    ensureBind(topLevelBind, names.iterator)
  }

  // Iterate over the given bind followed by all of its descendants, depth-first
  def iterateSelfAndDescendantBinds(rootBind: NodeInfo): Iterator[NodeInfo] =
    rootBind descendantOrSelf XFBindTest iterator

  def iterateSelfAndDescendantBindsReversed(rootBind: NodeInfo): Iterator[NodeInfo] =
    (rootBind descendantOrSelf XFBindTest).reverseIterator

  // Iterate over the given holder and descendants in reverse depth-first order
  def iterateSelfAndDescendantHoldersReversed(rootHolder: NodeInfo): Iterator[NodeInfo] =
    (rootHolder descendantOrSelf *).reverseIterator

  def controlPosition(controlElem: NodeInfo): ControlPosition = {

    val cellElem = controlElem.parentUnsafe

    ControlPosition(
      gridName   = findAncestorContainersLeafToRoot(controlElem, includeSelf = false).headOption flatMap getControlNameOpt get,
      coordinate = Coordinate1(NodeInfoCellOps.x(cellElem).get, NodeInfoCellOps.y(cellElem).get)
    )
  }

  def deleteControlWithinCell(
    cellElem        : NodeInfo,
    updateTemplates : Boolean = false)(implicit
    ctx             : FormBuilderDocContext
  ): Option[UndoAction] =
    cellElem firstChildOpt * flatMap { controlElem =>

      val undo =
        ToolboxOps.controlOrContainerElemToXcv(controlElem) map
          (DeleteControl(controlPosition(controlElem), _))

      controlElementsToDelete(controlElem) foreach (delete(_))

      if (updateTemplates)
        self.updateTemplatesCheckContainers(findAncestorRepeatNames(cellElem).to(Set))(FormBuilderDocContext())

      undo
    }

  // Find all associated elements to delete for a given control element
  def controlElementsToDelete(controlElem: NodeInfo)(implicit ctx: FormBuilderDocContext): List[NodeInfo] = {

    // Holders, bind, templates, resources if the control has a name
    val holders = getControlNameOpt(controlElem).toList flatMap { controlName =>

      val buffer = mutable.ListBuffer[NodeInfo]()

      buffer ++=
        findDataHolders     (controlName) ++=
        findBindByName      (ctx.formDefinitionRootElem, controlName) ++=
        findTemplateInstance(ctx.formDefinitionRootElem, controlName) ++=
        findResourceHolders (controlName)

      buffer.result()
    }

    // Prepend control element
    controlElem :: holders
  }

  // Rename a control with its holders, binds, etc. but *not* its nested iteration if any
  def renameControlIfNeeded(oldName: String, newName: String)(implicit ctx: FormBuilderDocContext): Option[Rename] =
    oldName != newName option {

      require(! newName.endsWith(DefaultIterationSuffix), s"control cannot end with `$DefaultIterationSuffix` (#3359)")

      // Maybe rename section template content
      findControlByName(ctx.formDefinitionRootElem, oldName)
        .filter(FormRunner.isSectionWithTemplateContent)
        .foreach( _ => renameControlIfNeeded(oldName + TemplateContentSuffix, newName + TemplateContentSuffix))

      findDataHolders(oldName)     foreach (rename(_, newName))
      findResourceHolders(oldName) foreach (rename(_, newName))

      renameBinds   (oldName, newName)
      renameControl (oldName, newName)
      renameTemplate(oldName, newName)

      findControlByName(ctx.formDefinitionRootElem, newName) foreach { newControl =>
        updateTemplatesCheckContainers(findAncestorRepeatNames(newControl).to(Set))
      }

      renameControlReferences(oldName, newName)

      Rename(oldName, newName)
    }

  def renameControlIterationIfNeeded(
    oldControlName      : String,
    newControlName      : String,
    oldChildElementName : Option[String],
    newChildElementName : Option[String])(implicit
    ctx                 : FormBuilderDocContext
  ): Unit = {

    if (findControlByName(ctx.formDefinitionRootElem, oldControlName) exists controlRequiresNestedIterationElement) {

      val oldName = oldChildElementName getOrElse defaultIterationName(oldControlName)
      val newName = newChildElementName getOrElse defaultIterationName(newControlName)

      if (oldName != newName) {
        findDataHolders(oldName) foreach (rename(_, newName))
        renameBinds(oldName, newName)
        FormRunnerTemplatesOps.updateTemplates(None, ctx.componentBindings)
      }
    }
  }

  def renameControl(oldName: String, newName: String)(implicit ctx: FormBuilderDocContext): Unit =
    findControlByName(ctx.formDefinitionRootElem, oldName) foreach
      (renameControlByElement(_, newName))

  // Rename the control (but NOT its holders, binds, etc.)
  def renameControlByElement(
    controlElement : NodeInfo,
    newName        : String)(implicit
    ctx            : FormBuilderDocContext
  ): Unit = {

    // Produce `section-` and `grid-` for sections and grids
    val newControlId =
      if (IsSection(controlElement))
        sectionId(newName)
      else if (IsGrid(controlElement))
        gridId(newName)
      else
        controlId(newName)

    // Set @id in any case, @ref value if present, @bind value if present
    ensureAttribute(controlElement, "id", newControlId)
    if (! IsGrid(controlElement) || controlElement.hasAtt(BIND_QNAME))
      ensureAttribute(controlElement, BIND_QNAME.localName, bindId(newName))

    // Make the control point to its template if @template (or legacy @origin) is present
    for (attName <- List("template", "origin"))
      setvalue(controlElement /@ attName, makeInstanceExpression(templateId(newName)))

    // Set `xf:label`, `xf:hint`, `xf:help` and `xf:alert` `@ref` if present.
    // NOTE: Just after an insert, for example of `fr:explanation`, we have `<fr:text ref=""/>`, so we must handle
    // the case where the `ref` is blank.
    for {
      childElem        <- controlElement child *
      childName        = childElem.localname
      if PossibleResourcePointerNames(childName)
      ref              <- childElem.att("ref").headOption
      explicitIndexOpt <- FormBuilder.findZeroBasedIndexFromAlertRefHandleBlankRef(ref.stringValue, childName)
    } locally {
      setvalue(List(ref), FormBuilder.buildResourcePointer(newName, childName, explicitIndexOpt))
    }

    // If using a static itemset editor, set `xf:itemset/@ref` value
    // TODO: Does this work if the itemset points to the data?
    if (hasEditor(controlElement, "static-itemset"))
      setvalue(controlElement / "*:itemset" /@ "ref", FormBuilder.buildResourcePointer(newName, "item", None))
  }

  // Rename a bind
  def renameBindElement(bindElement: NodeInfo, newName: String): Unit = {
    ensureAttribute(bindElement, "id",   bindId(newName))
    ensureAttribute(bindElement, "name", newName)
    ensureAttribute(bindElement, "ref",  newName)
  }

  // Rename a bind
  def renameBinds(oldName: String, newName: String)(implicit ctx: FormBuilderDocContext): Unit =
    findBindByName(ctx.formDefinitionRootElem, oldName) foreach (renameBindElement(_, newName))

  // Find or create a data holder for the given hierarchy of names
  private def ensureContainers(rootElem: NodeInfo, holders: Seq[(() => NodeInfo, Option[String])]) = {

    @tailrec def ensure(parents: Seq[NodeInfo], names: Iterator[(() => NodeInfo, Option[String])]): Seq[NodeInfo] =
      if (names.hasNext) {
        val (getHolder, precedingHolderName) = names.next()
        val holder = getHolder() // not ideal: this might create a NodeInfo just to check the name of the holder

        val children =
          for {
            parent <- parents
          } yield
            parent / * filter (_.name == holder.name) match {
              case Seq() =>
                // No holder exists so insert one
                insert(
                  into   = parent,
                  after  = parent / * filter (_.name == precedingHolderName.getOrElse("")),
                  origin = holder
                )
              case existing =>
                // At least one holder exists (can be more than one for repeats)
                existing
            }

        ensure(children.flatten, names)
      } else
        parents

    ensure(List(rootElem), holders.iterator)
  }

  // Insert data and resource holders for all languages
  def insertHolderForAllLang(
    controlElement       : NodeInfo,
    dataHolder           : NodeInfo,
    resourceHolder       : NodeInfo,
    precedingControlName : Option[String])(implicit
    ctx                  : FormBuilderDocContext
  ): Unit = {

    // Create one holder per existing language
    val resourceHolders = (allResources(ctx.resourcesRootElem) attValue XMLLangQName) map (_ -> List(resourceHolder))
    insertHolders(controlElement, List(dataHolder), resourceHolders, precedingControlName)
  }

  // Insert data and resource holders for all languages
  def insertHolders(
    controlElement       : NodeInfo,
    dataHolders          : Iterable[NodeInfo],
    resourceHolders      : Seq[(String, Seq[NodeInfo])],
    precedingControlName : Option[String])(implicit
    ctx                  : FormBuilderDocContext
  ): Unit = {

    // First we ensure all the containers
    val containers =
      ensureContainers(
        ctx.dataRootElem,
        findContainerNamesForModel(controlElement) map (name => (() => elementInfo(name), None))
      )

    // Then we create the holders within the containers
    // The idea is that we try to fill with the provided holders. If we don't have enough holders
    // provided, then we use the first holder. This also handles the case where we have only one
    // template holder, of course.
    // See https://github.com/orbeon/orbeon-forms/issues/3781
    val holdersIt =
      dataHolders.iterator ++ Iterator.continually(dataHolders.head)

    containers foreach { container =>
      insert(
        into   = container,
        after  = container / * filter (_.name == precedingControlName.getOrElse("")),
        origin = holdersIt.next()
      )
    }

    // Insert resources placeholders for all languages
    if (resourceHolders.nonEmpty) {
      val resourceHoldersMap = resourceHolders.toMap
      allResources(ctx.resourcesRootElem) foreach { resource =>
        val lang    = resource attValue XMLLangQName
        val holders = resourceHoldersMap.getOrElse(lang, resourceHolders.head._2)
        insert(
          into   = resource,
          after  = resource / * filter (_.name == precedingControlName.getOrElse("")),
          origin = holders
        )
      }
    }
  }

  // Update a mip for the given control, grid or section id
  // The bind is created if needed
  def writeAndNormalizeMip(
    controlNameOpt : Option[String],
    mip            : MIP, // `CalculateMIP | ValidateMIP` depending on caller
    mipValue       : String)(implicit
    ctx            : FormBuilderDocContext
  ): Unit = {

    val bindElemOpt =
      controlNameOpt match {
        case Some(controlName) =>
          findControlByName(ctx.formDefinitionRootElem, controlName) map { control =>
            ensureBinds(findContainerNamesForModel(control) :+ controlName)
          }
        case None =>
          FormRunner.findInBindsTryIndex(ctx.formDefinitionRootElem, Names.FormBinds)
      }

    bindElemOpt foreach {bindElem =>
      val valueOpt =
        normalizeMipValue(
          mip          = mip,
          mipValue     = mipValue,
          hasCalculate = hasCalculate(bindElem),
          isTypeString = isTypeStringUpdateNsIfNeeded(bindElem, _)
        )

      toggleAttribute(bindElem, mipToFBMIPQNames(mip)._1, valueOpt)
    }
  }

  // Return `(attQName, elemQName)`
  def mipToFBMIPQNames(mip: ModelDefs.MIP): (QName, QName) =
    RewrittenMIPs.get(mip) match {
      case Some(qn) => qn        -> qn
      case None     => mip.aName -> mip.eName
    }

  def getAllNamesInUse(implicit ctx: FormBuilderDocContext): Set[String] =
    iterateNamesInUse(ctx.explicitFormDefinitionInstance.toRight(ctx.formDefinitionInstance.get)).to(Set)

  // Finds if a control uses a particular type of editor (say "static-itemset")
  def hasEditor(controlElement: NodeInfo, editor: String)(implicit ctx: FormBuilderDocContext): Boolean =
    FormBuilder.controlElementHasEditor(controlElement: NodeInfo, editor: String, ctx.componentBindings)

  // Find the control by name (resolved from the top-level form model `fr-form-model`)
  def findConcreteControlByName(controlName: String)(implicit ctx: FormBuilderDocContext): Option[XFormsControl] = {
    val model = getFormModel
    for {
      controlId <- findControlIdByName(ctx.formDefinitionRootElem, controlName)
      control   <- model.container.resolveObjectByIdInScope(model.getEffectiveId, controlId) map (_.asInstanceOf[XFormsControl])
    } yield
      control
  }

  def setControlLabelHintHelpOrText(
    controlName : String,
    lhht        : String,
    value       : String,
    params      : Option[Seq[NodeInfo]],
    isHTML      : Boolean)(implicit
    ctx         : FormBuilderDocContext
  ): Boolean = {
    val resourceChanged  = setControlResource(controlName, lhht, value.trimAllToEmpty)
    val mediatypeChanged = setControlLhhatMediatype(controlName, lhht, isHTML)
    val paramsChanged    = params exists (setControlLHHATParams(controlName, lhht, _))

    resourceChanged || mediatypeChanged || paramsChanged
  }

  def lhhatChildrenParams(lhhatNodes: Seq[NodeInfo]): Seq[NodeInfo] =
    lhhatNodes child FRParamTest

  private def setControlLHHATParams(
    controlName : String,
    lhha        : String,
    params      : Seq[NodeInfo])(implicit
    ctx         : FormBuilderDocContext
  ): Boolean = {

    val lhhaNodes      = getControlLhhat(controlName, lhha)
    val existingParams = lhhatChildrenParams(lhhaNodes)

    val changed = {

      val sizeChanged = params.lengthCompare(existingParams.size) != 0
      val bothEmpty   = ! sizeChanged && params.isEmpty && existingParams.isEmpty

      sizeChanged || ! bothEmpty && {

        val someNode = params.headOption orElse existingParams.headOption get
        val config   = someNode.getConfiguration

        ! SaxonUtils.deepCompare(config, params.iterator, existingParams.iterator, excludeWhitespaceTextNodes = false)
      }
    }

    if (changed) {
      XFormsAPI.delete(ref = existingParams)
      XFormsAPI.insert(into = lhhaNodes, after = lhhaNodes / *, origin = params)
    }

    changed
  }

  // Find a control's LHHAT (there can be more than one for alerts)
  def getControlLhhat(controlName: String, lhhaName: String)(implicit ctx: FormBuilderDocContext): Seq[NodeInfo] =
    findControlByName(ctx.formDefinitionRootElem, controlName).toList child controlLHHATQName(lhhaName)

  // For a given control and LHHAT type, whether the mediatype on the LHHAT is HTML
  def isControlLhhatHtmlMediatype(controlName: String, lhha: String)(implicit ctx: FormBuilderDocContext): Boolean =
    hasHTMLMediatype(getControlLhhat(controlName, lhha))

  // For a given control and LHHA type, set the mediatype on the LHHA to be HTML or plain text
  def setControlLhhatMediatype(controlName: String, lhha: String, isHTML: Boolean)(implicit ctx: FormBuilderDocContext): Boolean = {

    val changed = isHTML != isControlLhhatHtmlMediatype(controlName, lhha)

    if (changed)
      setHTMLMediatype(getControlLhhat(controlName, lhha), isHTML)

    changed
  }

  // For a given control, whether the mediatype on itemset labels is HTML
  def isItemsetHTMLMediatype(controlName: String)(implicit ctx: FormBuilderDocContext): Boolean =
    hasHTMLMediatype(findControlByName(ctx.formDefinitionRootElem, controlName).toList child "itemset" child "label")

  def setHTMLMediatype(lhhaElems: Iterable[NodeInfo], isHTML: Boolean): Unit =
    lhhaElems foreach { lhhaElem =>
      if (isHTML)
        insert(into = lhhaElem, origin = attributeInfo("mediatype", "text/html"))
      else
        delete(lhhaElem /@ "mediatype")
    }

  def ensureCleanLHHAElements(
    controlName : String,
    lhhaName    : String,
    count       : Int,
    replace     : Boolean)(implicit
    ctx         : FormBuilderDocContext
  ): Seq[NodeInfo] = {

    val inDoc = ctx.formDefinitionRootElem

    val control  = findControlByName(inDoc, controlName).get
    val existing = getControlLhhat(controlName, lhhaName)
    val params   = lhhatChildrenParams(existing)

    val lhhaQName = controlLHHATQName(lhhaName)

    if (replace)
      delete(existing)

    // Try to insert in the right position wrt other LHHA elements. If none, will be inserted as first
    // element.

    if (count > 0) {

      val newTemplates =
        if (count == 1) {
          List(
            elementInfo(
              lhhaQName,
              attributeInfo("ref", FormBuilder.buildResourcePointer(controlName, lhhaName, None)) +: params
            )
          )
        } else {

          def newTemplate(zeroBasedIndex: Int) =
            elementInfo(
              lhhaQName,
              attributeInfo("ref", FormBuilder.buildResourcePointer(controlName, lhhaName, Some(zeroBasedIndex))) +: params
            )

          0 until count map newTemplate
        }

      insertElementsImposeOrder(into = control, origin = newTemplates, LHHAInOrder)

    } else
      Nil
  }

  // Build an namespaced id for a given static id or return null (the empty sequence)
  def buildFormBuilderControlNamespacedIdOrEmpty(staticId: String)(implicit ctx: FormBuilderDocContext): String = {
    val effectiveId  = buildFormBuilderControlEffectiveId(staticId)
    val namespacedId = effectiveId.map(XFormsAPI.inScopeContainingDocument.namespaceId)
    namespacedId.orNull
  }

  def buildFormBuilderControlAbsoluteIdOrEmpty(staticId: String)(implicit ctx: FormBuilderDocContext): String =
    buildFormBuilderControlEffectiveId(staticId) map XFormsId.effectiveIdToAbsoluteId orNull

  def buildFormBuilderControlEffectiveId(staticId: String)(implicit ctx: FormBuilderDocContext): Option[String] =
    findInViewTryIndex(ctx.formDefinitionRootElem, staticId) map (DynamicControlId + ComponentSeparator + buildControlEffectiveId(_))

  // Set the control's items for all languages
  def setControlItems(controlName: String, items: NodeInfo)(implicit ctx: FormBuilderDocContext): Unit = {

    val addHints = FormBuilder.hasItemHintEditor(controlName)

    for ((lang, holder) <- FormRunner.findResourceHoldersWithLang(controlName, resourcesRoot)) {

      delete(holder / "item")

      val newItemElems =
        for (item <- items / "item")
        yield {
          <item>
            <label>{item / "label" filter (_.attValue("lang") == lang) stringValue}</label>
            {
              if (addHints)
                <hint>{ item / "hint"  filter (_.attValue("lang") == lang) stringValue}</hint>
            }
            <value>{item / "value" stringValue}</value>
          </item>
        }

      insert(into = holder, after = holder / *, origin = newItemElems map elemToNodeInfo toList)
    }
  }

  // Build an effective id for a given static id
  //
  // This assumes a certain hierarchy:
  //
  // - zero or more `*:section` containers
  // - zero or more `fr:grid` containers
  // - the only repeats are containers
  // - all containers must have stable (not automatically-generated by XForms) ids
  //
  def buildControlEffectiveId(control: NodeInfo): String = {
    val staticId = control.id

    // Ancestors from root to leaf except fb-body group if present
    val ancestorContainers =
      findAncestorContainersLeafToRoot(control, includeSelf = false).reverse filterNot isFBBody

    val containerIds = ancestorContainers map (_.id)
    val repeatDepth  = ancestorContainers count isRepeat

    XFormsId(staticId, containerIds.toList, 1 to repeatDepth map (_ => 1) toList).toEffectiveId
  }

  def renameControlReferences(oldName: String, newName: String)(implicit ctx: FormBuilderDocContext): Unit = {

    def updateNode(newValue: String)(node: NodeInfo): Unit =
      XFormsAPI.setvalue(node, newValue)

    def findNewActions: Seq[NodeInfo] =
      ctx.modelElem child FRActionTest

    def findLegacyActions: Seq[NodeInfo] =
      ctx.modelElem child FBActionTest filter (_.id.endsWith("-binding"))

    // Replace formulas in binds
    ctx.topLevelBindElem.toList descendantOrSelf * att XMLNames.FormulaTest foreach { att =>

      val xpathString = att.stringValue

      val compiledExpr =
        XPath.compileExpression(
          xpathString      = xpathString,
          namespaceMapping = NamespaceMapping(att.parentUnsafe.namespaceMappings.toMap),
          locationData     = null,
          functionLibrary  = inScopeContainingDocument.partAnalysis.functionLibrary,
          avt              = false
        )

      val expr = compiledExpr.expression.getInternalExpression

      if (xpathString.contains(s"$$$oldName") && DependencyAnalyzer.containsVariableReference(expr, oldName))
        updateNode(xpathString.replace(s"$$$oldName", s"$$$newName"))(att)
    }

    // Replace references in templates, including email templates
    List(ctx.bodyElem, ctx.metadataRootElem) descendant FRParamTest filter
      (_.attValue(TYPE_QNAME) == "ControlValueParam")               child
      FRControlNameTest                                             filter
      (_.stringValue == oldName)                                    foreach
      updateNode(newName)

    // Update new actions

    // `fr:action//(@control | @repeat)`
    findNewActions descendant *   att
      (ControlTest || RepeatTest) filter
      (_.stringValue == oldName)  foreach
      updateNode(newName)

    // `fr:listener/@controls`
    ctx.modelElem child FRListenerTest att ControlsTest foreach { att =>

      val newTokens = att.stringValue.splitTo[List]() map {
        case `oldName` => newName
        case other     => other
      }

      updateNode(newTokens mkString " ")(att)
    }

    // Update legacy actions
    val legacyActions = findLegacyActions

    findControlIdByName(ctx.bodyElem, oldName) foreach { oldControlId =>

      val newControlId = newName + oldControlId.substringAfter(oldName)

      // `<xf:action event="xforms-value-changed" ev:observer="foo-control" if="true()">`
      legacyActions                     child
        XFActionTest                    att
        "observer"                      filter // when using a String we match on any namespace (for better or for worse)
        (_.stringValue == oldControlId) foreach
        updateNode(newControlId)
    }

    // `<xf:var name="control-name" value="'foo'"/>`
    legacyActions                            descendant
      XFORMS_VAR_QNAME                       filter
      (_.attValue("name") == "control-name") att
      VALUE_QNAME                            filter
      (_.stringValue == s"'$oldName'")       foreach
      updateNode(s"'$newName'")
  }
}
