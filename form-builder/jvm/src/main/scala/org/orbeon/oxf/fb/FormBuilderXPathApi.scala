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

import org.orbeon.datatypes.MediatypeRange
import org.orbeon.oxf.fb.FormBuilder._
import org.orbeon.oxf.fr.FormRunner
import org.orbeon.oxf.fr.FormRunner.{controlNameFromId, createFormDataBasePath, createFormDefinitionBasePath, getControlName, putWithAttachments, _}
import org.orbeon.oxf.fr.Names.FormInstance
import org.orbeon.oxf.fr.NodeInfoCell._
import org.orbeon.oxf.fr.XMLNames.{FR, XF}
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.PathUtils.encodeSimpleQuery
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{NetUtils, UserAgent}
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.control.XFormsSingleNodeControl
import org.orbeon.saxon.function.Property
import org.orbeon.saxon.om.{NodeInfo, SequenceIterator}
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._

import scala.util.control.NonFatal

object FormBuilderXPathApi {

  // This is called when the user adds/removes an iteration, as we want to update the templates in this case in order
  // to adjust the default number of iterations. See https://github.com/orbeon/orbeon-forms/issues/2379
  //@XPathFunction
  def updateTemplatesFromDynamicIterationChange(controlName: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    FormRunner.findControlByName(ctx.rootElem, controlNameFromId(controlName)) foreach { controlElem ⇒
      assert(isRepeat(controlElem))
      updateTemplatesCheckContainers(findAncestorRepeatNames(controlElem).to[Set])
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
  def renameControlIfNeeded(oldName: String, newName: String): Unit =
    FormBuilder.renameControlIfNeeded(oldName, newName)(FormBuilderDocContext())

  // Find the value of a MIP or null (the empty sequence)
  //@XPathFunction
  def readMipAsAttributeOnlyOrEmpty(inDoc: NodeInfo, controlName: String, mipName: String): String =
    FormBuilder.readMipAsAttributeOnly(inDoc, controlName, mipName).orNull

  //@XPathFunction
  def updateMipAsAttributeOnly(controlName: String, mipName: String, mipValue: String): Unit =
    FormBuilder.updateMipAsAttributeOnly(controlName, mipName, mipValue)(FormBuilderDocContext())

  //@XPathFunction
  def setRepeatProperties(
    controlName          : String,
    repeat               : Boolean,
    min                  : String,
    max                  : String,
    iterationNameOrEmpty : String,
    applyDefaults        : Boolean,
    initialIterations    : String
  ): Unit =
    FormBuilder.setRepeatProperties(
      controlName          = controlName,
      repeat               = repeat,
      min                  = min,
      max                  = max,
      iterationNameOrEmpty = iterationNameOrEmpty,
      applyDefaults        = applyDefaults,
      initialIterations    = initialIterations
    )(FormBuilderDocContext())

  //@XPathFunction
  def selectCellForControlId(controlId: String): Unit = {

    implicit val ctx = FormBuilderDocContext()

    FormRunner.findControlByName(ctx.rootElem, controlNameFromId(controlId)).to[List] flatMap
      (_ parent CellTest) foreach selectCell
  }

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
      validationElems
    )(FormBuilderDocContext())

  // Set the control help and add/remove help element and placeholders as needed
  //@XPathFunction
  def setControlHelp(controlName: String,  value: String): Seq[NodeInfo] = {

    implicit val ctx = FormBuilderDocContext()

    FormBuilder.setControlResource(controlName, "help", value.trimAllToEmpty)

    if (hasBlankOrMissingLHHAForAllLangsUseDoc(controlName, "help"))
      FormBuilder.removeLHHAElementAndResources(controlName, "help")
    else
      FormBuilder.ensureCleanLHHAElements(controlName, "help")
  }

  //@XPathFunction
  def setControlLHHAMediatype(controlName: String, lhha: String, isHTML: Boolean): Unit =
    FormBuilder.setControlLHHAMediatype(controlName, lhha, isHTML)(FormBuilderDocContext())

  // Set the control's items for all languages
  //@XPathFunction
  def setControlItems(controlName: String, items: NodeInfo): Unit =
    FormBuilder.setControlItems(controlName, items)

  // For a given control, set the mediatype on the itemset labels to be HTML or plain text
  //@XPathFunction
  def setItemsetHTMLMediatype(controlName: String, isHTML: Boolean): Unit = {

    implicit val ctx = FormBuilderDocContext()

    if (isHTML != FormBuilder.isItemsetHTMLMediatype(controlName)) {
      val itemsetEl = FormRunner.findControlByName(ctx.rootElem, controlName).toList child "itemset"
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
    implicit val ctx = FormBuilderDocContext(doc)

    // 1. Annotate all the grid and grid cells of the given document with unique ids,
    // if they don't have them already. We do this so that ids are stable as we move
    // things around, otherwise if the XForms document is recreated new automatic ids
    // are generated for objects without id.

    FormBuilder.annotateGridsAndCells(ctx.bodyElem)

    // 2. Select the first td if any
    ctx.bodyElem descendant GridTest descendant CellTest take 1 foreach selectCell
  }

  // Create template content from a bind name
  // FIXME: Saxon can pass null as `bindings`.
  //@XPathFunction
  def createTemplateContentFromBindName(inDoc: NodeInfo, name: String, bindings: List[NodeInfo]): Option[NodeInfo] =
    FormBuilder.createTemplateContentFromBindName(name, Option(bindings) getOrElse Nil)(FormBuilderDocContext(inDoc))

  // See: https://github.com/orbeon/orbeon-forms/issues/633
  //@XPathFunction
  def deleteSectionTemplateContentHolders(inDoc: NodeInfo): Unit = {

    // Create context explicitly based on the document passed, as the node might not be
    // in the main Form Builder instance yet.
    implicit val ctx = FormBuilderDocContext(inDoc)

    // Find data holders for all section templates
    val holders =
      for {
        section     ← findSectionsWithTemplates(ctx.bodyElem)
        controlName ← getControlNameOpt(section).toList
        holder      ← findDataHolders(controlName)
      } yield
        holder

    // Delete all elements underneath those holders
    holders foreach { holder ⇒
      delete(holder / *)
    }
  }

  // Publish a form and its attachments
  //@XPathFunction
  def publish(xhtml: NodeInfo, app: String, form: String, document: String, formVersion: String): Unit = {

    try {
      val (beforeURLs, _, publishedVersion) =
        putWithAttachments(
          data              = xhtml.root,
          toBaseURI         = "", // local publish
          fromBasePath      = createFormDataBasePath("orbeon", "builder", isDraft = false, document),
          toBasePath        = createFormDefinitionBasePath(app, form),
          filename          = "form.xhtml",
          commonQueryString = encodeSimpleQuery(List("document" → document)),
          forceAttachments  = false,
          // Using "next" for attachments works as attachments are saved first, and the persistence layer
          // uses the latest version of the published forms (not attachments) to figure what the next
          // version is
          formVersion       = formVersion.trimAllToOpt
        )
      setvalue(instanceRoot("fb-publish-instance").get / "published-attachments", beforeURLs.size.toString)
      setvalue(instanceRoot("fb-publish-instance").get / "published-version",     publishedVersion.toString)
      toggle("fb-publish-dialog-success")
    } catch {
      case NonFatal(t) ⇒
        toggle("fb-publish-dialog-error")
    }

    setfocus("fb-publish-dialog", includes = Set.empty, excludes = Set.empty)
  }

  // Find all resource holders and elements which are unneeded because the resources are blank
  //@XPathFunction
  def findBlankLHHAHoldersAndElements(inDoc: NodeInfo, lhha: String): Seq[NodeInfo] = {

    // Create context explicitly based on the document passed, as the node might not be
    // in the main Form Builder instance yet.
    implicit val ctx = FormBuilderDocContext(inDoc)

    val allHelpElements =
      ctx.rootElem.root descendant ((if (lhha=="text") FR else XF) → lhha) map
      (lhhaElement ⇒ lhhaElement → lhhaElement.attValue("ref")) collect
      { case (lhhaElement, HelpRefMatcher(controlName)) ⇒ lhhaElement → controlName }

    val allUnneededHolders =
      allHelpElements collect {
        case (lhhaElement, controlName) if hasBlankOrMissingLHHAForAllLangsUseDoc(controlName, lhha) ⇒
           lhhaHoldersForAllLangsUseDoc(controlName, lhha) :+ lhhaElement
      }

    allUnneededHolders.flatten
  }

  //@XPathFunction
  def nextIds(inDoc: NodeInfo, token: String, count: Int): Seq[String] =
    FormBuilder.nextIds(token, count)(FormBuilderDocContext(inDoc))

  // Canonical way: use the `name` attribute
  //@XPathFunction
  def getBindNameOrEmpty(bind: NodeInfo): String =
    FormRunner.getBindNameOrEmpty(bind)

  //@XPathFunction
  def defaultIterationName(repeatName: String): String =
    FormRunner.defaultIterationName(repeatName)

  //@XPathFunction
  def hasCustomIterationName(controlName: String): Boolean = {
    implicit val ctx = FormBuilderDocContext()
    FormRunner.findRepeatIterationName(ctx.rootElem, controlName) exists (isCustomIterationName(controlName, _))
  }

  // NOTE: Value can be a simple AVT
  //@XPathFunction
  def getNormalizedMin(doc: NodeInfo, gridName: String): String =
    FormBuilder.getNormalizedMin(doc, gridName)

  // Get the grid's normalized max attribute, the empty sequence if no maximum
  //@XPathFunction
  def getNormalizedMaxOrEmpty(doc: NodeInfo, gridName: String): String =
    FormBuilder.getNormalizedMax(doc, gridName).orNull

  //@XPathFunction
  def getAllControlNames: SequenceIterator = {
    implicit val ctx = FormBuilderDocContext()
    FormBuilder.getAllControlNames.iterator map stringToStringValue
  }

  //@XPathFunction
  def findNextContainer(controlName: String, previousOrNext: String): Option[NodeInfo] = {

    implicit val ctx = FormBuilderDocContext()

    val allContainersWithSettings = getAllContainerControlsWithIds(ctx.rootElem) filter hasContainerSettings

    previousOrNext match {
      case "previous" ⇒ allContainersWithSettings takeWhile (n ⇒ getControlName(n) != controlName) lastOption
      case "next"     ⇒ allContainersWithSettings dropWhile (n ⇒ getControlName(n) != controlName) drop 1 headOption
    }
  }

  // From a control element (say `<fr:autocomplete>`), returns the corresponding `<xbl:binding>`
  //@XPathFunction
  def bindingForControlElementOrEmpty(controlElement: NodeInfo): NodeInfo =
    FormBuilder.bindingForControlElement(controlElement, FormBuilder.componentBindings).orNull

  //@XPathFunction
  def containerById(containerId: String): NodeInfo =
    FormBuilder.containerById(containerId)(FormBuilderDocContext())

  // Return the first default alert for the given control, or a blank template if none exists
  //@XPathFunction
  def readDefaultAlertAsXML(controlName: String): NodeInfo = (
    AlertDetails.fromForm(controlName)(FormBuilderDocContext())
    find      (_.default)
    getOrElse AlertDetails(None, List(FormBuilder.currentLang → ""), global = true)
    toXML     FormBuilder.currentLang
  )

  // Return all validations as XML for the given control
  //@XPathFunction
  def readValidationsAsXML(controlName: String): Array[NodeInfo] =
    FormBuilder.readValidationsAsXML(controlName)(FormBuilderDocContext()).toArray

  //@XPathFunction
  def getControlHelpOrEmpty(controlName: String): String =
    FormBuilder.getControlResourceOrEmpty(controlName, "help")

  //@XPathFunction
  def isControlLHHAHTMLMediatype(controlName: String, lhha: String): Boolean =
    FormBuilder.isControlLHHAHTMLMediatype(controlName, lhha)(FormBuilderDocContext())

  //@XPathFunction
  def findNextControlId(controlName: String, previousOrNext: String): Option[String] = {

    implicit val ctx = FormBuilderDocContext()

    FormRunner.findControlByName(ctx.rootElem, controlName) flatMap { control ⇒

      val currentCell = control parent CellTest

      val cells =
        previousOrNext match {
          case "previous" ⇒ currentCell preceding CellTest
          case "next"     ⇒ currentCell following CellTest
        }

      val cellWithChild = cells find (_.hasChildElement)

      cellWithChild flatMap (_ child * map (_.id) headOption)
    }
  }

  //@XPathFunction
  def formInstanceRoot(inDoc: NodeInfo): NodeInfo =
    FormRunner.inlineInstanceRootElem(inDoc, FormInstance).get

  // Find data holders (there can be more than one with repeats)
  //@XPathFunction
  def findDataHolders(controlName: String): List[NodeInfo] =
    FormBuilder.findDataHolders(controlName)(FormBuilderDocContext())

  //@XPathFunction
  def possibleAppearancesByControlNameAsXML(
    controlName       : String,
    isInitialLoad     : Boolean,
    builtinDatatype   : String,
    desiredAppearance : String    // relevant only if isInitialLoad == false
  ): Array[NodeInfo] =
    FormBuilder.possibleAppearancesByControlNameAsXML(
      controlName,
      isInitialLoad,
      builtinDatatype,
      desiredAppearance
    )(FormBuilderDocContext()).to[Array]

  //@XPathFunction
  def isValidListOfMediatypeRanges(s: String): Boolean = {

    val mediatypeRanges =
      s.splitTo[List](" ,") flatMap { token ⇒
        token.trimAllToOpt
      } map { trimmed ⇒
          MediatypeRange.unapply(trimmed).isDefined
      }

    mediatypeRanges forall identity
  }

  //@XPathFunction
  def findSchemaOrEmpty(inDoc: NodeInfo) =
    findSchema(inDoc).orNull

  //@XPathFunction
  def findSchemaPrefixOrEmpty(inDoc: NodeInfo) =
    findSchemaPrefix(inDoc).orNull

  // Various counts
  //@XPathFunction
  def countSections        (inDoc: NodeInfo): Int = getAllControlsWithIds(inDoc)       count IsSection
  def countAllGrids        (inDoc: NodeInfo): Int = findFRBodyElem(inDoc) descendant * count IsGrid
  def countRepeats         (inDoc: NodeInfo): Int = getAllControlsWithIds(inDoc)       count isRepeat
  def countSectionTemplates(inDoc: NodeInfo): Int = findFRBodyElem(inDoc) descendant * count isSectionTemplateContent

  def countGrids           (inDoc: NodeInfo): Int = countAllGrids(inDoc) - countRepeats(inDoc)
  def countAllNonContainers(inDoc: NodeInfo): Int = getAllControlsWithIds(inDoc) filterNot IsContainer size
  def countAllContainers   (inDoc: NodeInfo): Int = getAllContainerControls(inDoc).size
  def countAllControls     (inDoc: NodeInfo): Int = countAllContainers(inDoc) + countAllNonContainers(inDoc) + countSectionTemplates(inDoc)

  // Find the control's bound item if any (resolved from the top-level form model `fr-form-model`)
  //@XPathFunction
  def findControlBoundNodeByName(controlName: String): Option[NodeInfo] = (
    findConcreteControlByName(controlName)
    collect { case c: XFormsSingleNodeControl ⇒ c }
    flatMap (_.boundNode)
  )

  //@XPathFunction
  def currentLang: String =
    FormBuilder.currentLang

  //@XPathFunction
  def getControlItemsGroupedByValue(controlName: String): Seq[NodeInfo] =
    FormBuilder.getControlItemsGroupedByValue(controlName)

  //@XPathFunction
  def resourcesRoot: NodeInfo =
    FormBuilder.resourcesRoot

  //@XPathFunction
  def iterateSelfAndDescendantBindsResourceHolders(
    rootBind          : NodeInfo,
    lang              : String,
    resourcesRootElem : NodeInfo
  ): SequenceIterator =
    FormBuilder.iterateSelfAndDescendantBindsResourceHolders(rootBind, lang, resourcesRootElem)

   // Return all classes that need to be added to an editable grid
  // TODO: Consider whether the client can test for grid deletion directly so we don't have to place CSS classes.
  //@XPathFunction
  def gridCanDoClasses(gridId: String): List[String] = {
    implicit val ctx = FormBuilderDocContext()
    "fr-editable" :: (canDeleteGrid(containerById(gridId)) list "fb-can-delete-grid")
  }

  //@XPathFunction
  def hasEditor(controlElement: NodeInfo, editor: String): Boolean =
    FormBuilder.hasEditor(controlElement, editor)

  //@XPathExpression
  def MinimalIEVersion: Int =
    FormBuilder.MinimalIEVersion

  // Whether the browser is supported
  // Concretely, we only return false if the browser is an "old" version of IE
  //@XPathFunction
  def isBrowserSupported: Boolean = {
    val request = NetUtils.getExternalContext.getRequest
    ! UserAgent.isUserAgentIE(request) || UserAgent.getMSIEVersion(request) >= MinimalIEVersion
  }

  //@XPathExpression
  def alwaysShowRoles: List[String] = {

    import spray.json.DefaultJsonProtocol._
    import spray.json._

    val rolesJsonOpt = Property.propertyAsString("oxf.fb.permissions.role.always-show")
    rolesJsonOpt.to[List].flatMap(_.parseJson.convertTo[List[String]])
  }

  // Return all classes that need to be added to an editable section
  //@XPathFunction
  def sectionCanDoClasses(container: NodeInfo): Seq[String] = {
    val directionClasses =
      DirectionCheck collect { case (direction, check) if check(container) ⇒ "fb-can-move-" + direction }

    val deleteClasses =
      canDeleteSection(container) list "fb-can-delete"

    "fr-section-container" :: deleteClasses ::: directionClasses
  }

  //@XPathFunction
  def buildFormBuilderControlEffectiveIdOrEmpty(staticId: String): String =
    FormBuilder.buildFormBuilderControlEffectiveId(staticId)(FormBuilderDocContext()).orNull
}
