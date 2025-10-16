/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fb

import cats.effect.IO
import io.circe.{Json, parser}
import org.orbeon.builder.rpc.FormBuilderRpcApiImpl
import org.orbeon.css.CSSSelectorParser
import org.orbeon.datatypes.{AboveBelow, Direction, MediatypeRange}
import org.orbeon.oxf.fb.FormBuilder.*
import org.orbeon.oxf.fb.Undo.UndoOrRedo
import org.orbeon.oxf.fb.UndoAction.*
import org.orbeon.oxf.fr
import org.orbeon.oxf.fr.*
import org.orbeon.oxf.fr.FormRunner.{allLangs, findControlByName, formRunnerProperty}
import org.orbeon.oxf.fr.Names.FormBinds
import org.orbeon.oxf.fr.NodeInfoCell.*
import org.orbeon.oxf.fr.XMLNames.{FRServiceCallTest, XFBindTest, XFSendTest}
import org.orbeon.oxf.fr.permission.*
import org.orbeon.oxf.fr.process.SimpleProcess.{currentXFormsDocumentId, evaluateString}
import org.orbeon.oxf.http.Headers
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.util.{ContentTypes, Mediatypes, PathUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.action.XFormsAPI
import org.orbeon.oxf.xforms.action.XFormsAPI.*
import org.orbeon.oxf.xforms.action.actions.XXFormsInvalidateInstanceAction
import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.analysis.model.MipName
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.oxf.xforms.event.XFormsEvent.PropertyValue
import org.orbeon.oxf.xforms.xbl.BindingDescriptor.*
import org.orbeon.oxf.xforms.xbl.BindingIndex.DatatypeMatch
import org.orbeon.oxf.xforms.xbl.{BindingDescriptor, BindingIndex}
import org.orbeon.oxf.xml.{SaxonUtils, TransformerUtils}
import org.orbeon.saxon.ArrayFunctions
import org.orbeon.saxon.function.Property
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.saxon.value.QNameValue
import org.orbeon.scaxon.Implicits.*
import org.orbeon.scaxon.NodeConversions
import org.orbeon.scaxon.NodeConversions.*
import org.orbeon.scaxon.SimplePath.*
import org.orbeon.xforms.XFormsNames.XFORMS_VAR_QNAME
import org.orbeon.xforms.{XFormsId, XFormsNames}
import org.orbeon.xml.NamespaceMapping

import scala.collection.immutable.SortedSet
import scala.util.{Failure, Success, Try}


object FormBuilderXPathApi {

  //@XPathFunction
  def containerMove(directionString: String): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    findSelectedCell
      .map(FormRunner.findAncestorContainersLeafToRoot(_, includeSelf = false))
      .flatMap(_.headOption)
      .foreach { container =>
        FormBuilder.moveSection(
          container,
          Direction.withName(directionString)
        ) foreach
          Undo.pushUserUndoAction
      }
  }

  //@XPathFunction
  def gridRowMove(
    currentCellElem: NodeInfo,
    direction      : String,
  ): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val ops  = implicitly[CellOps[NodeInfo]]

    val cellY      = ops.y(currentCellElem).getOrElse(1)
    val fromRowPos = cellY - 1

    FormBuilder
      .rowMove(
        gridId      = ops.gridForCell(currentCellElem).id,
        fromRowPos0 = fromRowPos,
        toRowPos0   = if (Direction.withName(direction) == Direction.Up) fromRowPos - 1 else fromRowPos + 1
      )
      .foreach(Undo.pushUserUndoAction)
  }

  //@XPathFunction
  def publishForm(
    doc                  : NodeInfo,
    documentId           : String,
    formDefinitionVersion: String,
    versionComment       : String,
    available            : Boolean,
    formName             : String
  ): Unit =
    saveThenContinue("publishForm()") {

      XFormsAPI.sendThrowOnError(
        "fb-publish-submission",
        List(
          PropertyValue("doc", Some(doc)),
          PropertyValue("document-id", Some(documentId)),
          PropertyValue("form-definition-version", Some(formDefinitionVersion)),
          PropertyValue("version-comment", Some(versionComment)),
          PropertyValue("available", Some(available))
        )
      )

      if (formName == "library")
        XXFormsInvalidateInstanceAction.doInvalidateInstance(
          resourceURI       = "/fr/service/custom/orbeon/builder/toolbox",
          handleXInclude    = None,
          ignoreQueryString = true
        )
    }

  //@XPathFunction
  def saveThenExport(exportFormat: String): Unit =
    saveThenContinue("saveThenExport()") {
      justExport(exportFormat)
    }

  //@XPathFunction
  def justExport(exportFormat: String): Unit =
    XFormsAPI.sendThrowOnError(
      "fb-export-submission",
      List(
        PropertyValue("export-format", Some(exportFormat))
      )
    )

  //@XPathFunction
  def saveAs(documentId: String): Unit =
    saveThenContinue("saveThenExport()") {
      // TODO: 2024-06-02: This is not used but should be modified if `save-as` is implemented.
      //  https://github.com/orbeon/orbeon-forms/issues/6342
      <xf:action type="javascript">
        <xf:param name="documentId" value="fr:document-id()"/>
        <xf:body>ORBEON.builder.private.API.updateLocationDocumentId(documentId)</xf:body>
      </xf:action>
      ???
    }

  private def saveThenContinue(description: String)(body: => Unit): Unit = {

    def continuation(xfcd: XFormsContainingDocument, t: Try[Any]): Either[Try[Unit], Nothing] = t match {
      case Failure(t) => Left(Failure(t))
      case Success(_) => Left(Try(body))
    }

    process.SimpleProcess.runProcessByName("oxf.fr.detail.process", "save") match {
      case Left(t)       => continuation(null /* not used in our continuation above */, t)
      case Right(future) => process.SimpleProcess.submitContinuation(s"continuation of `$description`", IO.fromFuture(IO.pure(future)), continuation)
    }
  }

  //@XPathFunction
  def buildContentDispositionHeader(doc: NodeInfo, formatName: String): String = {

    // TODO: Use namespaces from appropriate scope.
    // TODO: Ideally the expression would have access to various aspects of the form, e.g. the form title in a
    //  structured way. For now, we pass a reference to the root element of the edited form's metadata, which gives a
    //  little flexibility.
    val filenameProperty      = s"oxf.fr.detail.$formatName.filename"
    val filenamePropertyValue = formRunnerProperty(filenameProperty, AppForm.FormBuilder).flatMap(trimAllToOpt)
    val filenameFromProperty  = filenamePropertyValue.map(evaluateString(_, FormBuilderDocContext(doc).metadataRootElem)).flatMap(trimAllToOpt)
    val filename              = filenameFromProperty.getOrElse(currentXFormsDocumentId)

    val filenameWithExt =
      PathUtils.findExtension(filename) match {
        case Some(_) => filename
        case None    => s"$filename.${Mediatypes.getExtensionForMediatypeOrThrow(ContentTypes.XhtmlContentType)}"
      }

    Headers.buildContentDispositionHeader(s"$filenameWithExt")._2
  }

  // This is called when the user adds/removes an iteration, as we want to update the templates in this case in order
  // to adjust the default number of iterations. See https://github.com/orbeon/orbeon-forms/issues/2379
  //@XPathFunction
  def updateTemplatesFromDynamicIterationChange(controlName: String): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    FormRunner.findControlByName(FormRunner.controlNameFromId(controlName)) foreach { controlElem =>
      assert(FormRunner.isRepeat(controlElem))
      updateTemplatesCheckContainers(FormRunner.findAncestorRepeatNames(controlElem).to(Set))
    }
  }

  // Rename a control's nested iteration if any
  //@XPathFunction
  def renameControlIterationIfNeeded(
    oldControlName             : String,
    newControlName             : String,
    oldChildElementNameOrBlank : String,
    newChildElementNameOrBlank : String
  ): Unit =
    FormBuilder.renameControlIterationIfNeeded(
      oldControlName,
      newControlName,
      oldChildElementNameOrBlank.trimAllToOpt,
      newChildElementNameOrBlank.trimAllToOpt
    )(FormBuilderDocContext())

  //@XPathFunction
  def renameControlIfNeeded(oldName: String, newName: String, addToUndoStack: Boolean): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    FormBuilder.renameControlIfNeeded(oldName, newName) filter
      (_ => addToUndoStack) foreach
      Undo.pushUserUndoAction
  }

  //@XPathFunction
  def renameServiceIfNeeded(oldNameOrBlank: String, newName: String): Unit =
    Option(oldNameOrBlank)
      .flatMap(_.trimAllToOpt)
      .filter(_ != newName)
      .foreach { oldName =>
      implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
      FormBuilder.renameServiceReferences(oldName, newName)
    }

  // Get the normalized value of a computed MIP for the given control name and computed MIP name
  // If `controlName` is `null`, try the top-level bind (`fr-form-binds`).
  //@XPathFunction
  def readDenormalizedCalculatedMip(controlName: String, mipName: String): String =
    readDenormalizedCalculatedMip(controlName, mipName, iteration = false)

  //@XPathFunction
  def readDenormalizedCalculatedMipForIteration(controlName: String, mipName: String): String =
    readDenormalizedCalculatedMip(controlName, mipName, iteration = true)

  private def readDenormalizedCalculatedMip(controlName: String, mipName: String, iteration: Boolean): String = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val resultOpt =
      for {
        mip             <- MipName.AllComputedMipsByName.get(mipName)
        controlBindElem <-
          if (controlName ne null)
            FormRunner.findBindByName(controlName)
          else
            FormRunner.findInBindsTryIndex(FormBinds)
        bindElem        <- if (iteration) controlBindElem / XFBindTest headOption else Some(controlBindElem)
      } yield
        FormRunner.readDenormalizedCalculatedMip(bindElem, mip, mipToFBMIPQNames(mip)._1)

    resultOpt getOrElse (throw new IllegalArgumentException)
  }

  //@XPathFunction
  def writeAndNormalizeCalculatedMip(controlName: String, mipName: String, mipValue: String): Unit =
    writeAndNormalizeCalculatedMip(controlName, mipName, mipValue, iteration = false)

  //@XPathFunction
  def writeAndNormalizeCalculatedMipForIteration(controlName: String, mipName: String, mipValue: String): Unit =
    writeAndNormalizeCalculatedMip(controlName, mipName, mipValue, iteration = true)

  private def writeAndNormalizeCalculatedMip(
    controlName: String,
    mipName    : String,
    mipValue   : String,
    iteration  : Boolean
  ): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val resultOpt =
      for {
        mip <- MipName.AllComputedMipsByName.get(mipName)
      } yield
        FormBuilder.writeAndNormalizeMip(Option(controlName), mip, mipValue, iteration = iteration)

    resultOpt getOrElse (throw new IllegalArgumentException)
  }

  //@XPathFunction
  def setRepeatProperties(
    controlName          : String,
    repeat               : Boolean,
    userCanAddRemove     : Boolean,
    numberRows           : Boolean,
    usePaging            : Boolean,
    min                  : String,
    max                  : String,
    freeze               : String,
    iterationNameOrEmpty : String,
    applyDefaults        : Boolean,
    initialIterations    : String
  ): Unit =
    FormBuilder.setRepeatProperties(
      controlName          = controlName,
      repeat               = repeat,
      userCanAddRemove     = userCanAddRemove,
      numberRows           = numberRows,
      usePaging            = usePaging,
      min                  = min,
      max                  = max,
      freeze               = freeze,
      iterationNameOrEmpty = iterationNameOrEmpty,
      applyDefaults        = applyDefaults,
      initialIterations    = initialIterations
    )(FormBuilderDocContext())

  //@XPathFunction
  def selectCellForControlId(controlId: String): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    FormRunner.findControlByName(FormRunner.controlNameFromId(controlId)).toList flatMap
      (_ parent CellTest) foreach selectCell
  }

  //@XPathFunction
  def findControlIdByName(inDoc: NodeInfo, controlName: String): Option[String] =
    FormRunner.findControlIdByName(controlName )(new InDocFormRunnerDocContext(inDoc))

  //@XPathFunction
  def findRepeatIterationNameOrEmpty(inDoc: NodeInfo, controlName: String): String = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(inDoc)
    FormRunner.findRepeatIterationName(controlName) getOrElse ""
  }

  //@XPathFunction
  def getCurrentlySelectedCell: NodeInfo =
    findSelectedCell(FormBuilderDocContext()).orNull

  //@XPathFunction
  def getStartingCell: NodeInfo =
    findStartingCell(FormBuilderDocContext()).orNull

  // Write back everything
  //@XPathFunction
  def writeAlertsAndValidationsAsXML(
    controlName      : String,
    newAppearance    : String,
    defaultAlertElem : NodeInfo,
    validationElems  : Array[NodeInfo]
  ): Unit =
    FormBuilder.writeAlertsAndValidationsAsXML(
      controlName,
      newAppearance,
      defaultAlertElem,
      validationElems.to(List)
    )(FormBuilderDocContext())

  //@XPathFunction
  def setControlLHHAMediatype(controlName: String, lhha: String, isHTML: Boolean): Unit =
    FormBuilder.setControlLhhatMediatype(controlName, lhha, isHTML)(FormBuilderDocContext())

  // Do the pre/post insertions/deletions needed for optional LHHT elements
  def settingControlLabelHintHelpOrText[T](
    controlName  : String,
    lhht         : String,
    forceOptional: Boolean
  )(
    controlSetter: FormBuilderDocContext => T
  ): T = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val isOptionalLHHAT = {
      forceOptional                                          ||
      lhht == LHHA.Help.entryName                            ||
      lhht == fr.XMLNames.FRShortLabelQName.localName        ||
      lhht == fr.XMLNames.FRIterationLabelQName.localName    ||
      lhht == fr.XMLNames.FRAddIterationLabelQName.localName ||
      lhht == fr.XMLNames.FRRemoveIterationLabelQName.localName
    }

    // Make sure an optional element is present while we set content or attributes
    if (isOptionalLHHAT)
      FormBuilder.ensureCleanLHHAElements(controlName, lhht, count = 1, replace = true, keepParams = true)

    val t = controlSetter(ctx)

    // If an optional element turns out to be empty, then remove it
    if (isOptionalLHHAT) {
      val (doDelete, holders) =
        holdersToRemoveIfHasBlankOrMissingLHHAForAllLangs(controlName, FormBuilder.getControlLhhat(controlName, lhht).toList, lhht)

      if (doDelete) {
        delete(holders)
        delete(findControlByName(controlName).toList child controlLHHATQName(lhht))
      }
    }

    t
  }

  // Called by `dialog-container-settings.xbl`.
  //@XPathFunction
  def setControlLabelHintHelpOrText(
    controlName : String,
    lhht        : String,
    value       : String,
    params      : Array[NodeInfo],
    isHTML      : Boolean
  ): Unit =
    settingControlLabelHintHelpOrText(controlName, lhht, forceOptional = lhht == LHHA.Label.entryName) { implicit ctx: FormBuilderDocContext =>
      FormBuilder.setControlLabelHintHelpOrText(controlName, lhht, value, Some(params), isHTML)
    }

  //@XPathFunction
  def setControlLabelHintHelpOrTextForAllLangs(
    controlName : String,
    lhht        : String,
    values      : Array[NodeInfo],
    params      : Array[NodeInfo],
    isHTML      : Boolean,
    automatic   : String
  ): Unit = settingControlLabelHintHelpOrText(controlName, lhht, forceOptional = false) { implicit ctx: FormBuilderDocContext =>

    val langValues = values.map { nodeInfo =>
      val lang = nodeInfo.attValue("lang")
      val value = nodeInfo.getStringValue
      lang -> Seq(value)
    }

    FormBuilder.setControlResourcesWithLang(controlName, lhht, langValues)
    setControlLHHATParams(controlName, lhht, params)
    val lhhaElems = getControlLhhat(controlName, lhht)
    setHTMLMediatype(lhhaElems, isHTML)
    setAutomatic(lhhaElems, automatic.toBooleanOption)
  }

  // Set the control's items for all languages
  //@XPathFunction
  def setControlItems(controlName: String, items: NodeInfo): Unit =
    FormBuilder.setControlItems(controlName, items)(FormBuilderDocContext())

  // For a given control, set the mediatype on the itemset labels to be HTML or plain text
  //@XPathFunction
  def setItemsetHTMLMediatype(controlName: String, isHTML: Boolean): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    if (isHTML != FormBuilder.isItemsetHTMLMediatype(controlName)) {
      val itemsetEl = FormRunner.findControlByName(controlName).toList child "itemset"
      val labelHintEls = Seq("label", "hint") flatMap (itemsetEl.child(_))
      setHTMLMediatype(labelHintEls, isHTML)
    }
  }

  //@XPathFunction
  def isItemsetHTMLMediatype(controlName: String): Boolean =
    FormBuilder.isItemsetHTMLMediatype(controlName)(FormBuilderDocContext())

  //@XPathFunction
  def initializeGrids(doc: NodeInfo): Unit = {

    // Create context explicitly based on the document passed, as the node might not be
    // in the main Form Builder instance yet.
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(doc)

    // 1. Annotate all the grid and grid cells of the given document with unique ids,
    // if they don't have them already. We do this so that ids are stable as we move
    // things around, otherwise if the XForms document is recreated new automatic ids
    // are generated for objects without id.
    FormBuilder.annotateGridsAndCells(ctx.bodyElem)

    // 2. Select the first td if any
    ctx.bodyElem descendant GridTest descendant CellTest take 1 foreach selectCell
  }

  //@XPathFunction
  def findModelElem(doc: NodeInfo): NodeInfo =
    FormBuilderDocContext(doc).modelElem

  //@XPathFunction
  def findDataInstanceElem(doc: NodeInfo): NodeInfo =
    FormBuilderDocContext(doc).dataInstanceElem

  //@XPathFunction
  def findMetadataInstanceElem(doc: NodeInfo): NodeInfo =
    FormBuilderDocContext(doc).metadataInstanceElem

  // Create template content from a bind name
  // Called from XSLT in `annotate-migrate.xbl`
  // FIXME: Saxon can pass null as `bindings`.
  //@XPathFunction
  def createTemplateContentFromBindName(inDoc: NodeInfo, name: String, bindingsOrNull: List[NodeInfo]): Option[NodeInfo] = {
    val bindings = Option(bindingsOrNull) getOrElse Nil
    // Don't use `ctx.bindingIndex`, which won't work as there is no live Form Builder document yet given that this is
    // called during XSLT form migration. Instead, the caller passes an explicit list of bindings.
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(inDoc)
    implicit val index: BindingIndex[BindingDescriptor] =
      buildIndexFromBindingDescriptors(getAllRelevantDescriptors(bindings))
    FormRunnerTemplatesOps.createTemplateContentFromBindName(name)
  }

  // See: https://github.com/orbeon/orbeon-forms/issues/633
  // See: https://github.com/orbeon/orbeon-forms/issues/3073
  //@XPathFunction
  def updateSectionTemplateContentHolders(inDoc: NodeInfo): Unit = {

    // Create context explicitly based on the document passed, as the node might not be
    // in the main Form Builder instance yet.
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(inDoc)

    // Find data holders for all section templates
    val holdersWithRoots =
      for {
        sectionElem   <- FormRunner.findSectionsWithTemplates
        controlName   <- FormRunner.getControlNameOpt(sectionElem).toList
        holder        <- FormRunner.findDataHolders(controlName) // TODO: What about within repeated sections? Templates ok?
        componentElem <- FormRunner.findComponentElemForSection(sectionElem)
        bindingElem   <- FormRunner.findXblBinding(ctx.bodyElem, componentElem.uriQualifiedName)
        instance      <- FormRunner.findXblInstance(bindingElem, fr.Names.FormTemplate)
        instanceRoot  <- instance / * headOption
      } yield
        holder -> instanceRoot

    holdersWithRoots foreach { case (holder, instanceRoot) =>
      delete(holder / *)
      insert(into = holder, origin = instanceRoot / *)
    }
  }

  // Find all resource holders and elements which are unneeded because the resources are blank
  //@XPathFunction
  def findBlankHelpHoldersAndElements(inDoc: NodeInfo): Iterable[NodeInfo] = {

    // Create context explicitly based on the document passed, as the node might not be
    // in the main Form Builder instance yet.
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(inDoc)

    val lhha = LHHA.Help

    val lhhaTest: Test = LHHA.QNameForValue(lhha)

    val allHelpElementsWithControlNames =
      ctx.bodyElem
        .descendant(lhhaTest)
        .map(lhhaElem => lhhaElem -> lhhaElem.attValue("ref"))
        .collect { case (lhhaElem, HelpRefMatcher(controlName)) => lhhaElem -> controlName }

    allHelpElementsWithControlNames flatMap { case (lhhaElement, controlName) =>

      val (doDelete, holders) =
        holdersToRemoveIfHasBlankOrMissingLHHAForAllLangs(controlName, List(lhhaElement), lhha.entryName)

      if (doDelete)
        holders :+ lhhaElement
      else
        Nil
    }
  }

  //@XPathFunction
  def nextValidationIds(inDoc: NodeInfo, count: Int): Iterable[String] =
    FormBuilder.nextTmpIds(token = Names.Validation, count = count)(FormBuilderDocContext(inDoc))

  //@XPathFunction
  def hasCustomIterationName(controlName: String): Boolean = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    FormRunner.findRepeatIterationName(controlName) exists (isCustomIterationName(controlName, _))
  }

  // NOTE: Value can be a simple AVT
  //@XPathFunction
  def getNormalizedMin(doc: NodeInfo, gridName: String): String =
    FormBuilder.getNormalizedMin(doc, gridName)(new InDocFormRunnerDocContext(doc))

  //@XPathFunction
  def getNormalizedFreeze(doc: NodeInfo, gridName: String): String =
    FormBuilder.getNormalizedFreeze(doc, gridName)(new InDocFormRunnerDocContext(doc))

  // Get the grid's normalized max attribute, the empty sequence if no maximum
  //@XPathFunction
  def getNormalizedMaxOrEmpty(doc: NodeInfo, gridName: String): String =
    FormBuilder.getNormalizedMax(doc, gridName)(new InDocFormRunnerDocContext(doc)).orNull

  //@XPathFunction
  def getAllNamesInUseAsString: String = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    FormBuilder.getAllNamesInUse.mkString(" ")
  }

  //@XPathFunction
  def controlsWithSorting(currentControlName: String): String = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    val currentControlNameOpt               = Option(currentControlName)

    FormRunner
      .getAllControlsWithIdsExcludeContainers
      .filter(control => (control / "*:index" / "*:default-sort-column").nonEmpty)
      .map(FormRunner.getControlName)
      .filterNot(currentControlNameOpt.contains)
      .mkString(" ")
  }

  //@XPathFunction
  def findNextContainer(controlName: String, previousOrNext: String): Option[NodeInfo] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val allContainersWithSettings = FormBuilder.getAllContainerControlsWithIds filter FormRunner.hasContainerSettings

    previousOrNext match {
      case "previous" => allContainersWithSettings takeWhile (n => FormRunner.getControlName(n) != controlName) lastOption
      case "next"     => allContainersWithSettings dropWhile (n => FormRunner.getControlName(n) != controlName) drop 1 headOption
    }
  }

  // From a control element (say `<fr:autocomplete>`), returns the corresponding `<xbl:binding>`
  //@XPathFunction
  def bindingForControlElementOrEmpty(controlElement: NodeInfo): NodeInfo = {
    implicit val index: BindingIndex[BindingDescriptor] = FormBuilderDocContext().bindingIndex
    FormBuilder.bindingForControlElement(controlElement).orNull
  }

  //@XPathFunction
  def hasViewTemplateSupportElementFor(binding: NodeInfo, name: String): Boolean =
    FormBuilder.hasViewTemplateSupportElementFor(binding, name)

  //@XPathFunction
  def containerById(containerId: String): NodeInfo =
    FormBuilder.containerById(containerId)(FormBuilderDocContext())

  // Return the first default alert for the given control, or a blank template if none exists
  //@XPathFunction
  def readDefaultAlertAsXML(controlName: String): NodeInfo = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    AlertDetails.fromForm(controlName)(FormBuilderDocContext())              find
      (_.default)                                                            getOrElse
      AlertDetails(None, Nil, global = true, params = Nil) toXML
  }

  // Return all validations as XML for the given control
  //@XPathFunction
  def readValidationsAsXML(controlName: String): Array[NodeInfo] =
    FormBuilder.readValidationsAsXML(controlName)(FormBuilderDocContext()).toArray

  //@XPathFunction
  def getControlLhhOrEmpty(controlName: String, lhh: String): String =
    FormBuilder.getControlResourceOrEmpty(controlName, lhh)(FormBuilderDocContext())

  //@XPathFunction
  def getControlLhhValuesForAllLangs(controlName: String, lhh: String): Iterable[NodeInfo] = {

    val langs = allLangs(resourcesRoot)

    val valuesForExistingLangs = for {
      (lang, nodeInfos) <- FormBuilder.getControlResourcesWithLang(controlName, lhh, langs)(FormBuilderDocContext())
      nodeInfo <- nodeInfos.headOption
    } yield lang -> nodeInfo.getStringValue

    val existingLangs = valuesForExistingLangs.map(_._1).toSet

    val valuesForMissingLangs = langs.filterNot(existingLangs).map(lang => lang -> "")

    for {
      (lang, value) <- valuesForExistingLangs ++ valuesForMissingLangs
    } yield NodeConversions.elemToNodeInfo(<value lang={ lang }>{ value }</value>)
  }

  //@XPathFunction
  def getControlLhhtParams(controlName: String, lhh: String): Iterable[NodeInfo] =
    lhhatChildrenParams(FormBuilder.getControlLhhat(controlName, lhh)(FormBuilderDocContext()))

  //@XPathFunction
  def isControlLhhatHtmlMediatype(controlName: String, lhha: String): Boolean =
    FormBuilder.isControlLhhatHtmlMediatype(controlName, lhha)(FormBuilderDocContext())

  //@XPathFunction
  def controlLhhatAutomatic(controlName: String, lhha: String): String =
    FormBuilder.isControlLhhatAutomatic(controlName, lhha)(FormBuilderDocContext()) match {
      case Some(value) => value.toString
      case None        => "default"
    }

  //@XPathFunction
  def findNextControlId(controlName: String, leftOrRight: String): Option[String] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    FormRunner.findControlByName(controlName) flatMap { control =>

      // Make sure we don't go outside the body when looking for cells
      // https://github.com/orbeon/orbeon-forms/issues/5073
      val firstCellWithChild =
        (control parent CellTest)
          .headOption
          .flatMap(findCellsByDirection(_, None, Direction.withName(leftOrRight), allowEmptyCell = false).headOption)

      firstCellWithChild flatMap (_ child * map (_.id) headOption)
    }
  }

  //@XPathFunction
  def selectNextOrPreviousCell(
    currentCellElem: NodeInfo,
    startCellElem  : NodeInfo,
    direction      : String,
    allowEmptyCell : Boolean
  ): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    findCellsByDirection(
      currentCellElem,
      Option(startCellElem),
      Direction.withName(direction),
      allowEmptyCell
    ).headOption.foreach { td =>
      selectCell(td)
    }
  }

  //@XPathFunction
  def formInstanceRoot: NodeInfo =
    FormBuilderDocContext().dataRootElem

  // Find data holders (there can be more than one with repeats)
  //@XPathFunction
  def findDataHolders(controlName: String): List[NodeInfo] =
    FormRunner.findDataHolders(controlName)(FormBuilderDocContext())

  //@XPathFunction
  def possibleAppearancesByControlNameAsXML(
    controlName       : String,
    isInitialLoad     : Boolean,
    newBuiltinDatatype: String,
    desiredAppearance : String // relevant only if `isInitialLoad == false`
  ): Array[NodeInfo] =
    FormBuilder.possibleAppearancesByControlNameAsXML(
      controlName,
      isInitialLoad,
      newBuiltinDatatype,
      desiredAppearance
    )(FormBuilderDocContext()).to(Array)

  //@XPathFunction
  def directNameFromBinding(
    bindingElem: NodeInfo
  ): QNameValue = {

    import org.orbeon.scaxon.SimplePath.*

    val selectors =
      CSSSelectorParser.parseSelectors(bindingElem.attValue(XFormsNames.ELEMENT_QNAME))

    val bindingNs = NamespaceMapping(bindingElem.namespaceMappings.toMap)

    val directNameOpt =
      selectors collectFirst BindingDescriptor.directBindingPF(bindingNs, None) flatMap (_.elementName)

    val updatedDirectNameOpt =
      for {
        directName <- directNameOpt
        uri        =  directName.namespace.uri
        newPrefix  <- bindingNs.mapping collectFirst { case (p, `uri`) => p }
      } yield
        new QNameValue(newPrefix, uri, directName.localName)

    updatedDirectNameOpt.orNull
  }

  //@XPathFunction
  def isValidListOfMediatypeRanges(s: String): Boolean = {

    val mediatypeRanges =
      s.splitTo[List](" ,") flatMap { token =>
        token.trimAllToOpt
      } map { trimmed =>
          MediatypeRange.unapply(trimmed).isDefined
      }

    mediatypeRanges forall identity
  }

  //@XPathFunction
  def findSchemaOrEmpty(inDoc: NodeInfo) =
    SchemaOps.findSchema(inDoc).orNull

  //@XPathFunction
  def findSchemaPrefixOrEmpty(inDoc: NodeInfo) =
    SchemaOps.findSchemaPrefix(inDoc).orNull

  //@XPathFunction
  def countSections        (inDoc: NodeInfo): Int = FormRunner.getAllControlsWithIds(new InDocFormRunnerDocContext(inDoc)) count FormRunner.IsSection
  def countAllGrids        (inDoc: NodeInfo): Int = FormRunner.getFormRunnerBodyElem(inDoc) descendant *                   count FormRunner.IsGrid
  def countRepeats         (inDoc: NodeInfo): Int = FormRunner.getAllControlsWithIds(new InDocFormRunnerDocContext(inDoc)) count FormRunner.isRepeat
  def countSectionTemplates(inDoc: NodeInfo): Int = FormRunner.getFormRunnerBodyElem(inDoc) descendant *                   count FormRunner.isSectionTemplateContent

  def countGrids           (inDoc: NodeInfo): Int = countAllGrids(inDoc) - countRepeats(inDoc)
  def countAllNonContainers(inDoc: NodeInfo): Int = FormRunner.getAllControlsWithIdsExcludeContainers(new InDocFormRunnerDocContext(inDoc)).size
  def countAllContainers   (inDoc: NodeInfo): Int = getAllContainerControls(inDoc).size
  def countAllControls     (inDoc: NodeInfo): Int = countAllContainers(inDoc) + countAllNonContainers(inDoc) + countSectionTemplates(inDoc)

  //@XPathFunction
  def countImpactedActions(inDoc: NodeInfo, serviceName: String): Int = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext(inDoc)

    val newServiceCalls =
      findNewActions(Some(ctx.modelElem)) descendant FRServiceCallTest filter (_.attValue("service") == serviceName)

    val legacyServiceCalls =
      findLegacyActions(Some(ctx.modelElem)) descendant XFSendTest filter (_.attValue("submission") == s"$serviceName-submission")

    legacyServiceCalls.size + newServiceCalls.size
  }

  // Find the control's bound item if any (resolved from the top-level form model `fr-form-model`)
  //@XPathFunction
  def findControlBoundNodeByEffectiveId(controlEffectiveId: String): Option[NodeInfo] =
    findConcreteControlByEffectiveId(controlEffectiveId)
      .collect { case c: XFormsSingleNodeControl => c }
      .flatMap(_.boundNodeOpt)

  //@XPathFunction
  def currentLang: String =
    FormBuilder.currentLang(FormBuilderDocContext())

  //@XPathFunction
  def currentResources: NodeInfo =
    FormBuilderDocContext().formBuilderModel.get.unsafeGetVariableAsNodeInfo("current-resources")

  //@XPathFunction
  def getAllControlsWithIds: Iterable[NodeInfo] =
    FormRunner.getAllControlsWithIdsExcludeContainers(FormBuilderDocContext())

  //@XPathFunction
  def getControlsLabelValueItemset(): Iterable[NodeInfo] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val someResourcesMap =
      Some(FormBuilder.currentResources.child(*).map(r => r.localname -> r).toMap) // accelerate resources lookups

    FormRunner.getAllControlsWithIdsExcludeContainers.map { controlElem =>
      val controlId    = controlElem.id
      val controlName  = FormRunner.controlNameFromId(controlId)
      val controlLabel =
        FormBuilder.getControlResourceOrEmpty(
          controlName,
          "label",
          removeHtmlTags  = true,
          resourcesMapOpt = someResourcesMap
        )
      <item
        label={s"$controlLabel ($controlName)"}
        value={controlName}
        local-name={controlElem.localname}/>
    }.map(elemToNodeInfo)
  }

  //@XPathFunction
  def getControlItemsGroupedByValue(controlName: String): Iterable[NodeInfo] = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    import ctx.bindingIndex
    FormBuilder.getControlItemsGroupedByValue(controlName)
  }

  //@XPathFunction
  def resourcesRoot: NodeInfo =
    FormBuilder.resourcesRoot(FormBuilderDocContext())

  //@XPathFunction
  def iterateSelfAndDescendantBindsResourceHolders(
    rootBind          : NodeInfo,
    lang              : String,
    resourcesRootElem : NodeInfo
  ): SequenceIterator =
    FormBuilder.iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resourcesRootElem).toList // https://github.com/orbeon/orbeon-forms/issues/6016

   // Return all classes that need to be added to an editable grid
  // TODO: Consider whether the client can test for grid deletion directly so we don't have to place CSS classes.
  //@XPathFunction
  def gridCanDoClasses(gridId: String): List[String] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val container = containerById(gridId)

    val directionClasses =
      ContainerDirectionCheck collect { case (direction, check) if check(container) => "fb-can-move-" + direction.entryName }

    "fr-editable"                                          ::
      directionClasses                                     :::
      (canDeleteContainer(container) list "fb-can-delete") :::
      (canDeleteRow      (container) list "fb-can-delete-row")
  }

  // Return all classes that need to be added to an editable section
  //@XPathFunction
  def sectionCanDoClasses(container: NodeInfo): Iterable[String] = {

    val directionClasses =
      ContainerDirectionCheck collect { case (direction, check) if check(container) => "fb-can-move-" + direction.entryName }

    val deleteClasses =
      canDeleteContainer(container) list "fb-can-delete"

    "fr-section-container" :: deleteClasses ::: directionClasses
  }

  //@XPathFunction
  def hasEditor(controlElement: NodeInfo, editor: String): Boolean = {
    implicit val index: BindingIndex[BindingDescriptor] = FormBuilderDocContext().bindingIndex
    FormBuilder.hasEditor(controlElement, editor)
  }

  //@XPathExpression
  def alwaysShowRoles: List[String] =
    Property.propertyAsString("oxf.fb.permissions.role.always-show") match {
      case Some(rolesJson) =>
        parser.parse(rolesJson).flatMap(_.as[List[String]]).getOrElse(throw new IllegalArgumentException(rolesJson))
      case None =>
        Nil
    }

  // Called by `permissions.xbl` when loading `instance('fr-form-metadata')/permissions`
  // TODO: Should just create the UI XML!
  //@XPathFunction
  def normalizePermissionsHandleList(permissionsElOrNull: NodeInfo): NodeInfo =
    PermissionsXML.serialize(PermissionsXML.parse(Option(permissionsElOrNull)), normalized = true)
      .map(NodeConversions.elemToNodeInfo).orNull

  private val PermissionElemName = "permission"
  private val OperationsElemName = "operations"
  private val RoleElemName       = "role"

  //@XPathFunction
  def convertPermissionsFromUiToFormDefinitionFormat(permissionsEl: NodeInfo): NodeInfo = {

    val findAnyonePermissionElem: String => Option[NodeInfo] =
      (_: String) => (permissionsEl / PermissionElemName).headOption

    val findForPermissionElem: String => Option[NodeInfo] =
      (condition: String) => (permissionsEl / PermissionElemName).find(_.attValue("for") == condition)

    // NOTE: In `permissions.xbl`, all elements *are* present
    def findPermissionsFor(conditionOpt: Option[Condition], findElem: String => Option[NodeInfo]): Option[Permission] =
      findElem(Condition.simpleConditionToStringName(conditionOpt)) flatMap { permissionElem =>

        val specificOperations =
          Operations.parseFromStringTokensNoWildcard(permissionElem.elemValue(OperationsElemName))

        specificOperations != Operations.None option
          Permission(
            conditionOpt.toList,
            specificOperations
          )
      }

    val rawRolePermissions =
      for {
        rolePermissionElem <- (permissionsEl / PermissionElemName).toList
        nonEmptyRoleName   <- rolePermissionElem.elemValueOpt(RoleElemName).flatMap(_.trimAllToOpt)
        operations         = Operations.parseFromStringTokensNoWildcard(rolePermissionElem.elemValue(OperationsElemName))
      } yield
        Permission(List(Condition.RolesAnyOf(List(nonEmptyRoleName))), operations)

    NodeConversions.elemToNodeInfo(
      PermissionsXML.serialize(
        Permissions.normalizePermissions(
          Permissions.Defined(
            findPermissionsFor(None,                                 findAnyonePermissionElem).toList :::
            findPermissionsFor(Some(Condition.AnyoneWithToken),      findForPermissionElem).toList    :::
            findPermissionsFor(Some(Condition.AnyAuthenticatedUser), findForPermissionElem).toList    :::
            findPermissionsFor(Some(Condition.Owner),                findForPermissionElem).toList    :::
            findPermissionsFor(Some(Condition.Group),                findForPermissionElem).toList    :::
            rawRolePermissions
          )
        ),
        normalized = false
      ).getOrElse(throw new IllegalStateException)
    )
  }

  //@XPathFunction
  def buildFormBuilderControlNamespacedIdOrEmpty(staticId: String): String =
    FormBuilder.buildFormBuilderControlNamespacedIdOrEmpty(staticId)(FormBuilderDocContext())

  //@XPathFunction
  def findControlByNameOrEmpty(controlName: String): NodeInfo =
    FormRunner.findControlByNameOrEmpty(controlName)(FormBuilderDocContext())

  //@XPathFunction
  def findBindByNameOrEmpty(name: String): NodeInfo =
    FormRunner.findBindByName(name)(FormBuilderDocContext()).orNull

  //@XPathFunction
  def findNewControlBinding(
    controlName        : String,
    builtinTypeOrBlank : String,
    builtinTypeRequired: Boolean,
    schemaTypeOrBlank  : String,
    appearanceOpt      : Option[String]
  ): Option[NodeInfo] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    import ctx.bindingIndex

    val oldControlElem = findControlByNameOrEmpty(controlName)
    val oldDatatype    = FormBuilder.DatatypeValidation.fromForm(controlName).datatypeQName
    val oldAtts        = (oldControlElem /@ @*).view.map(attNode => (attNode.qName, attNode.stringValue))

    val newDatatype =
      DatatypeValidation.datatypeQNameFromStrings(
        controlName    = controlName,
        builtinTypeOpt = builtinTypeOrBlank.trimAllToOpt.map(_ -> builtinTypeRequired),
        schemaTypeOpt  = schemaTypeOrBlank.trimAllToOpt
      ).fold(_._1, identity)

    val (virtualName, _) =
      findVirtualNameAndAppearance(
        searchElemName = oldControlElem.uriQualifiedName,
        searchDatatype = oldDatatype,
        searchAtts     = oldAtts
      )

    for {
      descriptor <-
        findMostSpecificBindingUseEqualOrToken(
          searchElemName = virtualName,
          datatypeMatch  = DatatypeMatch.makeExcludeStringMatch(newDatatype),
          searchAtts     = updateAttAppearance(oldAtts, appearanceOpt)
        )
      binding    <- descriptor.binding
    } yield
      binding
  }

  private def renamingDetailsToXPath(renamingDetails: Option[Seq[(String, String, Boolean)]]): SequenceIterator =
    renamingDetails.toList.flatten map {
      case (oldId, newId, isAutomaticId) =>
        ArrayFunctions.createValue(
          Vector(
            SaxonUtils.fixStringValue(oldId),
            SaxonUtils.fixStringValue(newId),
            isAutomaticId
          )
        )
    }

  // See also `buildPDFFieldNameFromHTML`.
  //@XPathFunction
  def findPdfFieldName(controlName: String, currentControlName: String): Option[String] = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    FormRunner.findControlByName(controlName) map { controlElem =>

      val containerNames =
        FormRunner.findContainerNamesForModel(
          controlElem,
          includeSelf              = false,
          includeIterationElements = false,
          includeNonRepeatedGrids  = false // https://github.com/orbeon/orbeon-forms/issues/4099
        )

      val suffix =
        XFormsId.getEffectiveIdSuffix(buildControlEffectiveId(controlElem)).trimAllToOpt.toList

      containerNames ++: (currentControlName :: suffix) mkString FormRunner.PdfFieldSeparator
    }
  }

  //@XPathFunction
  def getExcelRangeName(currentControlName: String): String = {
    import org.orbeon.oxf.fr.importexport.ImportExportSupport
    ImportExportSupport.controlNameToNamedRangeName(currentControlName)(ImportExportSupport.DefaultExcelNameManglingConfig)
  }

  //@XPathFunction
  def namesToRenameForMergingSectionTemplate(
    containerId : String,
    prefix      : String,
    suffix      : String
  ): SequenceIterator = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    renamingDetailsToXPath(ToolboxOps.namesToRenameForMergingSectionTemplate(containerId, prefix, suffix))
  }

  //@XPathFunction
  def namesToRenameForClipboard(
    prefix      : String,
    suffix      : String
  ): SequenceIterator = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    renamingDetailsToXPath(ToolboxOps.namesToRenameForClipboard(prefix, suffix))
  }

  //@XPathFunction
  def containerMerge(
    containerId : String,
    prefix      : String,
    suffix      : String
  ): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    ToolboxOps.containerMerge(containerId, prefix, suffix) foreach Undo.pushUserUndoAction
  }

  //@XPathFunction
  def pasteSectionGridFromClipboard(
    prefix      : String,
    suffix      : String
  ): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    pasteSectionGridFromClipboardImpl(prefix, suffix)
  }

  def pasteSectionGridFromClipboardImpl(
    prefix          : String,
    suffix          : String)(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Unit =
    ToolboxOps.readXcvFromClipboardAndClone flatMap
      (ToolboxOps.pasteSectionGridFromXcv(_, prefix, suffix, None, Set.empty, copyAttachments = true)) foreach
      Undo.pushUserUndoAction

  //@XPathFunction
  def saveControlToUndoStack(oldName: String, newName: String): Unit = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    for {
      control <- FormRunner.findControlByName(oldName)
      xcv     <- ToolboxOps.controlOrContainerElemToXcv(control)
    } locally {
      Undo.pushUserUndoAction(ControlSettings(oldName, newName, xcv))
    }
  }

  //@XPathFunction
  def undoAction(): Unit =
    undoActionImpl()(FormBuilderDocContext(), FormRunnerParams())

  def undoActionImpl()(implicit ctx: FormBuilderDocContext, formRunnerParams: FormRunnerParams): Unit =
    for {
      undoAction <- Undo.popUndoAction()
      redoAction <- processUndoRedoAction(undoAction)
    } locally {
      Undo.pushAction(UndoOrRedo.Redo, redoAction, undoAction.name)
    }

  //@XPathFunction
  def redoAction(): Unit = {
    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
    implicit val formRunnerParams: FormRunnerParams = FormRunnerParams()
    for {
      redoAction <- Undo.popRedoAction()
      undoAction <- processUndoRedoAction(redoAction)
    } locally {
      Undo.pushAction(UndoOrRedo.Undo, undoAction, undoAction.name)
    }
  }

  //@XPathFunction
  def migrateGridColumns(gridElem: NodeInfo, from: Int, to: Int): Unit =
    FormBuilder.migrateGridColumns(gridElem, from, to) // foreach Undo.pushUserUndoAction

  //@XPathFunction
  def canMigrateGridColumns(gridElem: NodeInfo, from: Int, to: Int): Boolean =
    FormBuilder.findGridColumnMigrationType(gridElem, from, to).isDefined

  //@XPathFunction
  def hasUnresolvedVariableReferences(
    elemOrAtt   : NodeInfo,
    avt         : Boolean,
    originalName: String,
    newName     : String
  ): Boolean =
    if (elemOrAtt.stringValue.trimAllToOpt.nonEmpty)
      Try {
        implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()
        FormRunnerRename.findUnresolvedVariableReferences(
          elemOrAtt      = elemOrAtt,
          avt            = avt,
          validBindNames = (
            FormBuilder.iterateNamesInUseCtx
              .filterNot(_ == originalName)
              .concat(
                Iterator(
                  newName,
                  "form-resources", // Issue #5909
                  "fr-mode"         // Issue #6220
                )
              )
              .concat(              // Issue #6145
                ctx.modelElem / XFORMS_VAR_QNAME attValue "name"
              )
          ).toSet
        )
      } match {
        case Success(it) if it.nonEmpty => true
        case Success(_)                 => false
        case Failure(_)                 => false // The user expression can be incorrect in the UI. We ignore it
                                                 // here and UI validation should inform the user.
      }
    else
      false

  // Simple suggestion logic from a text label to a control name
  //@XPathFunction
  def controlNameSuggestionFromLabel(
    controlNameMaybeUpdated: String,
    updates                : String,
    label                  : String,
  ): String = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val updatesSet = updates.tokenizeToSet
    val toRemove   = updatesSet.filter(_.startsWith("-")).map(_.substring(1)).headOption
    val toAdd      = updatesSet.filter(_.startsWith("+")).map(_.substring(1)).headOption

    val formControl = findControlByName(toRemove.getOrElse(controlNameMaybeUpdated))

    // TODO: Do better than ASCII letters and numbers. Other characters are allowed.
    val cleaned    = label.replaceAll("[^a-zA-Z0-9\\-_\\s]", "_")
    val suggestion = {
      val replaced = cleaned.trim.replaceAll("\\s+", "-").toLowerCase
      if (! SaxonUtils.isValidNCName(replaced))
        s"_$replaced"
      else
        replaced
    }

    // TODO:
    //  - consider using section name, vs. numeric suffix
    //      - my-section-email
    //      - email2
    //      - email-2
    //      - if section doesn't have a nice label, what about using the section label as well? what about grid label?
    //  - maybe return multiple suggestions

    val availableControlNames =
      FormBuilder.iterateNamesInUseCtx.toList

    Iterator.iterate((suggestion, None: Option[Int])) { case (base, indexOpt) =>
      val index = indexOpt.getOrElse(0) + 1
      (base, Some(index))
    }
    .collectFirst {
      case (base, None)        if ! availableControlNames.contains(base)            => base
      case (base, Some(index)) if ! availableControlNames.contains(s"$base-$index") => s"$base-$index"
    }
    .orNull
  }

  //@XPathFunction
  def renameControlReferences(
    elemsOrAtts: SequenceIterator,
    avt        : Boolean,
    oldName    : String,
    newName    : String
  ): Int = {

    val functionLibrary = inScopeContainingDocument.partAnalysis.functionLibrary

    var countOfUpdatedValues = 0

    def updateNode(newValue: String)(node: NodeInfo): Unit =
      if (XFormsAPI.setvalue(node, newValue).exists(_._2))
        countOfUpdatedValues += 1

    elemsOrAtts filter (_.getStringValue.nonAllBlank) foreach {
      case elemOrAtt: NodeInfo =>
        Try(
          FormRunnerRename.replaceVarAndFnReferences(
            xpathString      = elemOrAtt.stringValue,
            namespaceMapping = NamespaceMapping(elemOrAtt.namespaceMappings.toMap),
            functionLibrary  = functionLibrary,
            avt              = avt,
            oldName          = oldName,
            newName          = newName
          )
        ) match {
          case Success(Some(newValue)) =>
            updateNode(newValue)(elemOrAtt)
          case Success(None) | Failure(_) =>
            // The user expression can be incorrect in the UI. We ignore it here and UI validation should inform the user.
            // We could count the number of failed expressions, but it's not really useful.
        }
      case _ =>
        throw new IllegalArgumentException
    }

    countOfUpdatedValues
  }

  //@XPathFunction
  def findPdfFields(pathOrTmpFileUri: String): NodeInfo =
    FormBuilder.findPdfFields(pathOrTmpFileUri)

  //@XPathFunction
  def controlNameCompletionsJson(text: String, limit: Int, updates: String): String = {

    implicit val ctx: FormBuilderDocContext = FormBuilderDocContext()

    val updatesSet = updates.tokenizeToSet
    val toRemove   = updatesSet.filter(_.startsWith("-")).map(_.substring(1)).headOption
    val toAdd      = updatesSet.filter(_.startsWith("+")).map(_.substring(1)).headOption

    val namesIt =
      text.trimAllToOpt match {
        case Some(t) =>
          FormBuilder.iterateNamesInUseCtx.filter(_.startsWith(t))
        case None =>
          FormBuilder.iterateNamesInUseCtx
      }

    Json.fromValues(
      namesIt
        .filterNot(toRemove.contains)
        .concat(toAdd)
        .to(SortedSet)
        .iterator
        .take(limit)
        .map { name =>
          Json.obj(
            "v" -> Json.fromString(name),
            "l" -> Json.fromString(
              FormBuilder.getControlResourceOrEmpty(
                if (toAdd.contains(name)) toRemove.head else name,
                "label",
                removeHtmlTags = true
              )
            )
          )
        }
        .toList
    ).noSpaces
  }

  private def processUndoRedoAction(
    undoAction      : UndoAction)(implicit
    ctx             : FormBuilderDocContext,
    formRunnerParams: FormRunnerParams
  ): Option[UndoAction] =
    undoAction match {
      case DeleteContainer(position, xcvElem) =>
        ToolboxOps.pasteSectionGridFromXcv(
          TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
          "",
          "",
          Some(position),
          Set.empty,
          copyAttachments = false
        )
      case DeleteControl(position, xcvElem) =>
        ToolboxOps.pasteSingleControlFromXcv(
          TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
          Some(position),
          copyAttachments = false
        )
      case DeleteRow(gridId, xcvElem, rowPos) =>

        val containerPosition = FormBuilder.containerPosition(gridId)

        FormBuilder.deleteContainerById(_ => true, gridId)

        ToolboxOps.pasteSectionGridFromXcv(
          TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
          "",
          "",
          Some(containerPosition),
          Set.empty,
          copyAttachments = false
        )

        Some(UndeleteRow(gridId, rowPos))
      case UndeleteRow(gridId, rowPos) =>
        FormBuilder.rowDelete(gridId, rowPos)
      case MoveRow(gridId, fromRowPos, toRowPos) =>
        FormBuilder.rowMove(gridId, toRowPos, fromRowPos)
      case InsertRow(gridId, rowPos, AboveBelow.Above) =>
        FormBuilder.rowDelete(gridId, rowPos)
      case InsertRow(gridId, rowPos, AboveBelow.Below) =>
        FormBuilder.rowDelete(gridId, rowPos + 1)
      case Rename(oldName, newName) =>
        FormBuilder.renameControlIfNeeded(newName, oldName)
      case InsertControl(controlId) =>
        FormRunner.findControlByName(FormRunner.controlNameFromId(controlId)) map
          (_.parentUnsafe) flatMap
          (FormBuilder.deleteControlWithinCell(_))
      case InsertSection(sectionId) =>
        FormBuilder.deleteSectionByIdIfPossible(sectionId)
      case InsertGrid(gridId) =>
        FormBuilder.deleteGridByIdIfPossible(gridId)
      case MoveControl(insert, delete) =>
        for {
          newUndoDeleteAction <- processUndoRedoAction(delete)
          newUndoInsertAction <- processUndoRedoAction(insert)
        } yield
          MoveControl(newUndoDeleteAction, newUndoInsertAction)
      case MoveContainer(sectionId, direction, position) =>

        val container = FormBuilder.containerById(sectionId)

        direction match {
          case Direction.Up    => FormBuilder.moveSection(container, Direction.Down)
          case Direction.Down  => FormBuilder.moveSection(container, Direction.Up)
          case Direction.Left  => FormBuilder.moveSection(container, Direction.Right)
          case Direction.Right => FormBuilder.moveSection(container, Direction.Left)
        }
      case InsertSectionTemplate(sectionId) =>
        FormBuilder.deleteSectionByIdIfPossible(sectionId)
      case MergeSectionTemplate(sectionId, xcvElem, prefix, suffix) =>

        val containerPosition = FormBuilder.containerPosition(sectionId)

        FormBuilder.deleteContainerById(_ => true, sectionId)

        ToolboxOps.pasteSectionGridFromXcv(
          TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
          "",
          "",
          Some(containerPosition),
          Set.empty,
          copyAttachments = false
        )

        Some(UnmergeSectionTemplate(sectionId, prefix, suffix))
      case UnmergeSectionTemplate(sectionId, prefix, suffix) =>
        ToolboxOps.containerMerge(sectionId, prefix, suffix)
      case ControlSettings(oldName, newName, xcvElem) =>
        for {
          controlElem <- FormRunner.findControlByName(newName)
          newXcvElem  <- ToolboxOps.controlOrContainerElemToXcv(controlElem)
        } yield {

          if (FormRunner.IsContainer(controlElem)) {
            // NOTE: It is a bit costly to do this for entire sections or grids. We could do
            // better and create a smaller set of information but it would be more work.
            val containerId = controlElem.id

            val containerPosition = FormBuilder.containerPosition(containerId)

            FormBuilder.deleteContainerById(_ => true, containerId)

            ToolboxOps.pasteSectionGridFromXcv(
              TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
              "",
              "",
              Some(containerPosition),
              Set.empty,
              copyAttachments = false
            )

          } else {
            val cellElem = controlElem.parentUnsafe
            val position = FormBuilder.controlPosition(controlElem)

            FormBuilder.deleteControlWithinCell(cellElem)

            ToolboxOps.pasteSingleControlFromXcv(
              TransformerUtils.extractAsMutableDocument(xcvElem).rootElement,
              Some(position),
              copyAttachments = false
            )
          }

          ControlSettings(newName, oldName, newXcvElem)
        }
      case MoveWall(cellId, startSide, target) =>
        FormBuilder.moveWall(FormBuilderRpcApiImpl.resolveId(cellId).get, startSide, target)
      case SplitCell(cellId, direction) =>
        FormBuilder.merge(FormBuilderRpcApiImpl.resolveId(cellId).get, direction)
      case MergeCell(cellId, direction, size) =>
        FormBuilder.split(FormBuilderRpcApiImpl.resolveId(cellId).get, direction, Some(size))
      case MigrateGridColumns(gridId, from, to) =>
        FormBuilder.migrateGridColumns(FormBuilderRpcApiImpl.resolveId(gridId).get, to, from)
    }
}
