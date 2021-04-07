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

import org.orbeon.datatypes.Direction
import org.orbeon.oxf.fb.XMLNames._
import org.orbeon.oxf.fr.FormRunner._
import org.orbeon.oxf.fr.NodeInfoCell._
import org.orbeon.oxf.fr.XMLNames._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.NodeInfoFactory
import org.orbeon.oxf.xforms.NodeInfoFactory.elementInfo
import org.orbeon.xforms.XFormsNames.APPEARANCE_QNAME
import org.orbeon.oxf.xforms.action.XFormsAPI._
import org.orbeon.oxf.xforms.xbl.BindingDescriptor._
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.scaxon.Implicits._
import org.orbeon.scaxon.NodeConversions._
import org.orbeon.scaxon.SimplePath._
import org.orbeon.xforms.XFormsId
import org.orbeon.oxf.util.CoreUtils._
import scala.collection.compat._

trait ContainerOps extends ControlOps {

  self: GridOps => // funky dependency, to resolve at some point

  def containerById(containerId: String)(implicit ctx: FormBuilderDocContext): NodeInfo =
    findContainerById(containerId).get

  def findContainerById(containerId: String)(implicit ctx: FormBuilderDocContext): Option[NodeInfo] = {
    // Support effective id, to make it easier to use from XForms (i.e. no need to call
    // XFormsUtils.getStaticIdFromId every time)
    val staticId = XFormsId.getStaticIdFromId(containerId)
    findInViewTryIndex(ctx.formDefinitionRootElem, staticId) filter IsContainer
  }

  def findNestedContainers(containerElem: NodeInfo): Seq[NodeInfo] =
    containerElem descendant FRContainerTest

  // Find all siblings of the given element with the given name, excepting the given element
  def findSiblingsWithName(element: NodeInfo, siblingName: String): Seq[NodeInfo] =
    element parent * child * filter
      (_.name == siblingName) filterNot
      (_ == element)

  def getInitialIterationsAttribute(controlElem: NodeInfo): Option[String] =
    controlElem attValueOpt FBInitialIterations flatMap trimAllToOpt

  // Return all the container controls in the view
  def getAllContainerControlsWithIds(inDoc: NodeInfo): Seq[NodeInfo] = getAllControlsWithIds(inDoc) filter IsContainer

  def getAllContainerControls(inDoc: NodeInfo): Seq[NodeInfo] = getFormRunnerBodyElem(inDoc) descendant * filter IsContainer

  // A container can be removed if it's not the last one at that level
  def canDeleteContainer(containerElem: NodeInfo): Boolean =
    containerElem sibling FRContainerTest nonEmpty

  def containerPosition(containerId: String)(implicit ctx: FormBuilderDocContext): ContainerPosition = {
    val container = containerById(containerId)
    ContainerPosition(
      findAncestorContainersLeafToRoot(container).headOption flatMap getControlNameOpt, // top-level container doesn't have a name
      precedingSiblingOrSelfContainers(container).headOption map getControlName
    )
  }

  // Delete the entire container and contained controls
  def deleteContainerById(
    canDelete   : NodeInfo => Boolean,
    containerId : String)(implicit
    ctx         : FormBuilderDocContext
  ): Option[UndoAction] = {
    val container = containerById(containerId)
    canDelete(container) flatOption {

      val undoOpt =
        ToolboxOps.controlOrContainerElemToXcv(container) map
          (UndoAction.DeleteContainer(containerPosition(containerId), _))

      deleteContainer(container)

      undoOpt
    }
  }

  def deleteContainer(containerElem: NodeInfo): Unit = {

    implicit val ctx = FormBuilderDocContext()

    // Find the new td to select if we are removing the currently selected td
    val newCellToSelectOpt = findNewCellToSelect(containerElem descendant CellTest)

    def recurse(container: NodeInfo): Seq[NodeInfo] = {

      // So we delete from back to front, but it's probably no longer needed (#3630).

      // Go depth-first so we delete containers after all their content has been deleted
      // NOTE: Use `toList` to make sure we are not lazy, otherwise items might be deleted as we go!
      val children = childrenContainers(container).reverse.toList flatMap recurse

      val gridContent =
        if (IsGrid(container))
          container descendant CellTest child * filter IsControl reverse
        else
          Nil

      children ++ gridContent :+ container
    }

    // Start with top-level container
    val controls = recurse(containerElem)

    //  Delete all controls in order
    controls flatMap controlElementsToDelete foreach (delete(_))

    // Update templates
    updateTemplatesCheckContainers(findAncestorRepeatNames(containerElem).to(Set))

    // Adjust selected td if needed
    newCellToSelectOpt foreach selectCell
  }

  // Move a container based on a move function
  def moveContainer(
    containerElem  : NodeInfo,
    otherContainer : NodeInfo,
    move           : (NodeInfo, NodeInfo) => NodeInfo)(implicit
    ctx            : FormBuilderDocContext
  ): Unit = {

    // Get names before moving the container
    val nameOption      = getControlNameOpt(containerElem)
    val otherNameOption = getControlNameOpt(otherContainer)

    val doc = containerElem.getDocumentRoot

    // Move container control itself
    move(containerElem, otherContainer)

    // Try to move holders and binds based on name of other element
    (nameOption, otherNameOption) match {
      case (Some(name), Some(otherName)) =>

        // Move data holder only
        for {
          holder      <- findDataHolders(name)
          otherHolder <- findDataHolders(otherName)
        } yield
          move(holder, otherHolder)

        // Move bind
        for {
          bind      <- findBindByName(doc, name)
          otherBind <- findBindByName(doc, otherName)
        } yield
          move(bind, otherBind)

        // Try to move resource elements to a good place
        // TODO: We move the container resource holder, but we should also move together the contained controls' resource holders
        def firstControl(s: Seq[NodeInfo]) =
          s find (getControlNameOpt(_).isDefined)

        def tryToMoveHolders(siblingName: String, moveOp: (NodeInfo, NodeInfo) => NodeInfo): Unit =
          findResourceHolders(name) foreach {
            holder =>
              findSiblingsWithName(holder, siblingName).headOption foreach
                  (moveOp(holder, _))
          }

        val movedContainer = findInViewTryIndex(doc, containerElem.id).get // must get new reference

        (firstControl(movedContainer preceding *), firstControl(movedContainer following *)) match {
          case (Some(preceding), _) => tryToMoveHolders(getControlName(preceding), moveElementAfter)
          case (_, Some(following)) => tryToMoveHolders(getControlName(following), moveElementBefore)
          case _ =>
        }

        // Moving sections can impact templates
        updateTemplates(None)

      case _ =>
    }
  }

  // Whether it is possible to move an item into the given container
  // Currently: must be a section without section template content
  // Later: fr:tab (maybe fr:tabview), wizard
  def canMoveInto(containerElem: NodeInfo): Boolean =
    IsSection(containerElem) && ! (containerElem / * exists isSectionTemplateContent)

  def firstPrecedingContainerToMoveInto(container: NodeInfo): Option[NodeInfo] =
    container precedingSibling * find canMoveInto

  def canContainerMove(container: NodeInfo, direction: Direction): Boolean =
    direction match {
      case Direction.Up    => container precedingSibling * exists IsContainer
      case Direction.Right => firstPrecedingContainerToMoveInto(container).nonEmpty
      case Direction.Down  => container followingSibling * exists IsContainer
      case Direction.Left  => canDeleteContainer(container) && findAncestorContainersLeafToRoot(container).lengthCompare(if (IsSection(container)) 2 else 3) >= 0
    }

  val ContainerDirectionCheck: List[(Direction, NodeInfo => Boolean)] = Direction.values.to(List) map { d =>
    d -> (canContainerMove(_, d))
  }

  def isCustomIterationName(controlName: String, iterationName: String): Boolean =
    defaultIterationName(controlName) != iterationName

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
    initialIterations    : String)(implicit
    ctx                  : FormBuilderDocContext
  ): Unit = {

    // TODO: Remove once `ctx` is used everywhere
    val inDoc = ctx.formDefinitionRootElem

    findControlByName(inDoc, controlName) foreach { control =>

      val wasRepeat = isRepeat(control)
      val oldInitialIterationsAttribute = getInitialIterationsAttribute(control)

      val minOpt    = minMaxFreezeForAttribute(min)
      val maxOpt    = minMaxFreezeForAttribute(max)
      val freezeOpt = minMaxFreezeForAttribute(freeze)

      val initialIterationsOpt = initialIterations.trimAllToOpt

      // Update control attributes first
      // A missing or invalid min/max value is taken as the default value: 0 for min, none for max. In both cases, we
      // don't set the attribute value. This means that in the end we only set positive integer values.
      toggleAttribute(control, "repeat",            RepeatContentToken,                              repeat)
      toggleAttribute(control, FBReadonly,          "true",                                          repeat && ! userCanAddRemove)
      toggleAttribute(control, FBPageSize,          "1",                                             repeat && ! userCanAddRemove && usePaging)
      toggleAttribute(control, "number-rows",       "true",                                          repeat && numberRows)
      toggleAttribute(control, "min",               minOpt.get,                                      repeat && minOpt.isDefined)
      toggleAttribute(control, "max",               maxOpt.get,                                      repeat && maxOpt.isDefined)
      toggleAttribute(control, "freeze",            freezeOpt.get,                                   repeat && freezeOpt.isDefined)
      toggleAttribute(control, "template",          makeInstanceExpression(templateId(controlName)), repeat)
      toggleAttribute(control, "apply-defaults",    "true",                                          repeat && applyDefaults)
      toggleAttribute(control, FBInitialIterations, initialIterationsOpt.get,                        repeat && initialIterationsOpt.isDefined)

      if (! wasRepeat && repeat) {
        // Insert new bind and template

        val iterationName = iterationNameOrEmpty.trimAllToOpt getOrElse defaultIterationName(controlName)

        // Make sure there are no nested binds
        val oldNestedBinds = findBindByName(inDoc, controlName).toList / *
        delete(oldNestedBinds)

        // Insert nested iteration bind
        findControlByName(inDoc, controlName) foreach { control =>
          ensureBinds(findContainerNamesForModel(control) :+ controlName :+ iterationName)
        }

        val controlBind   = findBindByName(inDoc, controlName)
        val iterationBind = controlBind.toList / *
        insert(into = iterationBind, origin = oldNestedBinds)

        // Insert nested iteration data holders
        // NOTE: There can be multiple existing data holders due to enclosing repeats
        findDataHolders(controlName) foreach { holder =>
          val nestedHolders = holder / *
          delete(nestedHolders)
          insert(into = holder, origin = elementInfo(iterationName, nestedHolders))
        }

        // Update existing templates
        // NOTE: Could skip if top-level repeat
        updateTemplatesCheckContainers(findAncestorRepeatNames(control).to(Set))

        // Ensure new template rooted at iteration
        ensureTemplateReplaceContent(
          controlName,
          createTemplateContentFromBind(iterationBind.head, ctx.componentBindings)
        )

      } else if (wasRepeat && ! repeat) {
        // Remove bind, holders and template

        // Move bind up
        val controlBind = findBindByName(inDoc, controlName).toList
        val oldNestedBinds = controlBind / * / *
        delete(controlBind / *)
        insert(into = controlBind, origin = oldNestedBinds)

        // Move data holders up and keep only the first iteration
        findDataHolders(controlName) foreach { holder =>
          val nestedHolders = holder / * take 1 child *
          delete(holder / *)
          insert(into = holder, origin = nestedHolders)
        }

        // Remove template
        findTemplateInstance(inDoc, controlName) foreach (delete(_))

        // Update existing templates
        updateTemplatesCheckContainers(findAncestorRepeatNames(control).to(Set))

      } else if (repeat) {
        // Template should already exists an should have already been renamed if needed
        // MAYBE: Ensure template just in case.

        val newInitialIterationsAttribute = getInitialIterationsAttribute(control)

        if (oldInitialIterationsAttribute != newInitialIterationsAttribute)
          updateTemplatesCheckContainers(findAncestorRepeatNames(control, includeSelf = true).to(Set))

      } else if (! repeat) {
        // Template should not exist
        // MAYBE: Delete template just in case.
      }
    }
  }

  def renameTemplate(oldName: String, newName: String)(implicit ctx: FormBuilderDocContext): Unit =
    for {
      root     <- findTemplateRoot(oldName)
      instance <- root.parentOption
    } locally {
      ensureAttribute(instance, "id", templateId(newName))
    }

  def findTemplateInstance(doc: NodeInfo, controlName: String): Option[NodeInfo] =
    instanceElem(doc, templateId(controlName))

  def ensureTemplateReplaceContent(
    controlName : String,
    content     : NodeInfo)(implicit
    ctx         : FormBuilderDocContext
  ): Unit = {

    val templateInstanceId = templateId(controlName)
    val modelElement = getModelElem(ctx.formDefinitionRootElem)
    modelElement / XFInstanceTest find (_.hasIdValue(templateInstanceId)) match {
      case Some(templateInstance) =>
        // clear existing template instance content
        delete(templateInstance / *)
        insert(into = templateInstance , origin = content)

      case None =>
        // Insert template instance if not present
        val template: NodeInfo =
          <xf:instance
            xmlns:xf="http://www.w3.org/2002/xforms"
            xmlns:fb="http://orbeon.org/oxf/xml/form-builder"
            xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
            id={templateInstanceId}
            fb:readonly="true"
            xxf:exclude-result-prefixes="#all">{nodeInfoToElem(content)}</xf:instance>

        insert(into = modelElement, after = modelElement / XFInstanceTest takeRight 1, origin = template)
    }
  }

  def createTemplateContentFromBindName(
    bindName : String,
    bindings : Seq[NodeInfo])(implicit
    ctx      : FormBuilderDocContext
  ): Option[NodeInfo] =
    findBindByName(ctx.formDefinitionRootElem, bindName) map (createTemplateContentFromBind(_, bindings))

  private val AttributeRe = "@(.+)".r

  // Create an instance template based on a hierarchy of binds rooted at the given bind
  // This checks each control binding in case the control specifies a custom data holder.
  def createTemplateContentFromBind(
    startBindElem : NodeInfo,
    bindings      : Seq[NodeInfo])(implicit
    ctx           : FormBuilderDocContext
  ): NodeInfo = {

    val inDoc       = startBindElem.getDocumentRoot
    val descriptors = getAllRelevantDescriptors(bindings)

    val allControlsByName = getAllControlsWithIds(inDoc) map (c => controlNameFromId(c.id) -> c) toMap

    def holderForBind(bind: NodeInfo, topLevel: Boolean): Option[NodeInfo] = {

      val controlName    = getBindNameOrEmpty(bind)
      val controlElemOpt = allControlsByName.get(controlName)

      // Handle non-standard cases, see https://github.com/orbeon/orbeon-forms/issues/2470
      def fromNonStandardRef =
        bind attValueOpt "ref" match {
          case Some(AttributeRe(att)) => Some(Some(NodeInfoFactory.attributeInfo(att)))
          case Some(".")              => Some(None)
          case _                      => None
        }

      def fromBinding =
        for {
          controlElem <- controlElemOpt
          appearances = controlElem attTokens APPEARANCE_QNAME
          descriptor  <- findMostSpecificWithoutDatatype(controlElem.uriQualifiedName, appearances, descriptors)
          binding     <- descriptor.binding
        } yield {
          Some(FormBuilder.newDataHolder(controlName, binding))
        }

      def fromPlainControlName =
        Some(Some(elementInfo(controlName)))

      val elementTemplateOpt = fromNonStandardRef orElse fromBinding orElse fromPlainControlName flatten

      elementTemplateOpt foreach { elementTemplate =>

        val iterationCount = {

          // If the current control is a repeated fr:grid or fr:section with the attribute set, find the first occurrence
          // in the data of this  repeat, and use its concrete initial number of iterations to update the template. We
          // can imagine other values for the attribute in the future, maybe an integer value (`0`, `1`, ...) setting
          // the initial number of iterations.
          // See https://github.com/orbeon/orbeon-forms/issues/2379
          def useInitialIterations(controlElem: NodeInfo) =
            ! topLevel && isRepeat(controlElem) && getInitialIterationsAttribute(controlElem).contains("first")

          controlElemOpt match {
            case Some(controlElem) if useInitialIterations(controlElem) =>

              val firstDataHolder   = findDataHolders(controlName) take 1
              val iterationsHolders = firstDataHolder / *

              iterationsHolders.size

            case _ =>
              1
          }
        }

        // Recursively insert elements in the template
        if (iterationCount > 0) {

          // If iterationCount > 1, we just duplicate the children `iterationCount` times. In practice, this means
          // multiple iteration elements:
          //
          // <repeated-section-2-iteration>
          //   ...
          // </repeated-section-2-iteration>
          // <repeated-section-2-iteration>
          //   ...
          // </repeated-section-2-iteration>
          val nested         = bind / "*:bind" flatMap (holderForBind(_, topLevel = false))
          val repeatedNested = (1 to iterationCount) flatMap (_ => nested)

          insert(into = elementTemplate, origin = repeatedNested)
        }
      }

      elementTemplateOpt
    }

    holderForBind(startBindElem, topLevel = true) getOrElse (throw new IllegalStateException)
  }

  // Make sure all template instances reflect the current bind structure

  def updateTemplates(ancestorContainerNames: Option[Set[String]])(implicit ctx: FormBuilderDocContext): Unit =
    for {
      templateInstance <- templateInstanceElements(ctx.formDefinitionRootElem)
      repeatName       = controlNameFromId(templateInstance.id)
      if ancestorContainerNames.isEmpty || ancestorContainerNames.exists(_(repeatName))
      iterationName    <- findRepeatIterationName(ctx.formDefinitionRootElem, repeatName)
      template         <- createTemplateContentFromBindName(iterationName, ctx.componentBindings)
    } locally {
      ensureTemplateReplaceContent(repeatName, template)
    }

  // Update templates but only those which might contain one of specified names
  def updateTemplatesCheckContainers(ancestorContainerNames: Set[String])(implicit ctx: FormBuilderDocContext): Unit =
    updateTemplates(Some(ancestorContainerNames))
}