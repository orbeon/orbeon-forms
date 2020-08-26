/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XFormsRepeatIterationControl}
import org.orbeon.oxf.xforms.control.{Controls, XFormsComponentControl, XFormsContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.events.XFormsModelDestructEvent
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEventFactory}
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.xforms.Constants.ComponentSeparator
import org.orbeon.xforms.XFormsId
import org.orbeon.xforms.xbl.Scope

import scala.collection.JavaConverters._
import scala.collection.{immutable, mutable}

/**
 * Represent an XBL container of models and controls.
 *
 * - this is used at the top-level (XFormsContainingDocument) and by XBL components
 * - there is no nested component tree (a single components tree is handled by XFormsControls)
 *
 * There is a double purpose for this class, which we should fix:
 *
 * - as a container for models
 * - as a boundary for components
 *
 * In the future we want flexible model placement, so models should get out of this class.
 */
class XBLContainer(
  private var _effectiveId : String,        // effective id of the control containing this container, e.g. "#document" for root container, "my-stuff$my-foo-bar.1-2", etc.
  val prefixedId           : String,        // prefixed id of the control containing this container, e.g. "#document" for root container, "my-stuff$my-foo-bar", etc.
  val fullPrefix           : String,        // prefix of controls and models within this container, e.g. "" for the root container, "my-stuff$my-foo-bar$", etc.
  val parentXBLContainer   : Option[XBLContainer],
  val associatedControlOpt : Option[XFormsControl], // `None` for root container
  val innerScope           : Scope
) extends ModelContainer
     with RefreshSupport
     with ContainerResolver {

  self =>

  protected def this(
    associatedControl  : XFormsControl,
    parentXBLContainer : XBLContainer,
    innerScope         : Scope
  ) = this(
      associatedControl.getEffectiveId,
      XFormsId.getPrefixedId(associatedControl.getEffectiveId),
      XFormsId.getPrefixedId(associatedControl.getEffectiveId) + ComponentSeparator,
      Some(parentXBLContainer ensuring (_ ne null)),
      Some(associatedControl  ensuring (_ ne null)),
      innerScope
    )

  // Tell parent it has a child
  parentXBLContainer foreach
    (_.addChild(self))

  def ancestorsIterator: Iterator[XBLContainer] =
    Iterator.iterateOpt(self)(_.parentXBLContainer)

  val containingDocument = ancestorsIterator collectFirst { case cd: XFormsContainingDocument => cd } get

  val contextStack = new XFormsContextStack(self)

  private var _childrenXBLContainers: mutable.Buffer[XBLContainer] = mutable.ArrayBuffer()
  def childrenXBLContainers = _childrenXBLContainers.iterator

  def effectiveId = _effectiveId
  def partAnalysis: PartAnalysis = parentXBLContainer map (_.partAnalysis) orNull

  // Legacy getters/setters
  def getEffectiveId        = _effectiveId
//  def getPrefixedId         = prefixedId
  def getFullPrefix         = fullPrefix
  def getContainingDocument = containingDocument
  def getContextStack       = contextStack
  final def getPartAnalysis = partAnalysis

  // Create a new container child of the given control
  // NOTE: Require passing the `ConcreteBinding` to help ensure that the control does have it.
  def createChildContainer(associatedControl: XFormsComponentControl, concreteBinding: ConcreteBinding): XBLContainer = {
    require(associatedControl.staticControl.bindingOpt exists (_ eq concreteBinding))
    new XBLContainer(associatedControl, self, concreteBinding.innerScope)
  }

  def createChildContainer(associatedControl: XFormsControl, childPartAnalysis: PartAnalysis): XBLContainer =
    new XBLContainer(associatedControl, self, childPartAnalysis.startScope) {
      // Start with specific part
      override def partAnalysis = childPartAnalysis
    }

  // Update the effective id when repeat iterations change.
  def updateEffectiveId(effectiveId: String): Unit = {
    parentXBLContainer foreach
      (_.removeChild(self))

    _effectiveId = effectiveId

    parentXBLContainer foreach
      (_.addChild(self))

    for (currentModel <- models) {

      val newModelEffectiveId =
        XFormsId.getPrefixedId(currentModel.getEffectiveId) +
          XFormsId.getEffectiveIdSuffixWithSeparator(effectiveId)

      currentModel.updateEffectiveId(newModelEffectiveId)
    }
  }

  // Remove container and destroy models when a repeat iteration is removed.
  def destroy(): Unit = {
    parentXBLContainer foreach
      (_.removeChild(self))

    destroyModels()
  }

  private def addChild(container: XBLContainer): Unit =
    _childrenXBLContainers += container

  // Only remove if there is an identity match, in case another object with same effective id was added in the
  // meanwhile, which is possible with repeat iteration updates.
  private def removeChild(container: XBLContainer): Unit =
    _childrenXBLContainers  = _childrenXBLContainers filterNot (_ eq container)

  // Find the root container for the given prefixed id, starting with the current container.
  // This is the container which has the given scope as inner scope.
  // The prefixed id must be within the current container.
  def findScopeRoot(prefixedId: String): XBLContainer = {
    val scope = partAnalysis.scopeForPrefixedId(prefixedId)
    if (scope eq null)
      throw new IllegalArgumentException("Prefixed id not found in current part: " + prefixedId)
    findScopeRoot(scope)
  }

  // Find the root container for the given scope, starting with the current container.
  // This is the container which has the given scope as inner scope.
  def findScopeRoot(scope: Scope): XBLContainer =
    ancestorsIterator find (_.innerScope == scope) getOrElse
      (throw new OXFException("XBL resolution scope not found for scope id: " + scope.scopeId))

  // Whether this container is relevant, i.e. either is a top-level container OR is within a relevant container
  // control componentControl will be null if we are at the top-level
  def isRelevant: Boolean = ! (associatedControlOpt exists (! _.isRelevant))
}

trait ModelContainer {

  self: XBLContainer =>

  private var _models = Seq.empty[XFormsModel]
  def models = _models

  // Create and index models corresponding to this container's scope
  def addAllModels(): Unit =
    _models =
      for {
        model <- partAnalysis.getModelsForScope(innerScope)
        modelEffectiveId = model.prefixedId + XFormsId.getEffectiveIdSuffixWithSeparator(effectiveId)
      } yield
        new XFormsModel(self, modelEffectiveId, model)

  def initializeModels(eventsToDispatch: immutable.Seq[String]): Unit =
    for (eventName <- eventsToDispatch){
      if (eventName == XFORMS_READY) {
        initializeNestedControls()
        requireRefresh()
      }
      for (model <- _models)
        Dispatch.dispatchEvent(XFormsEventFactory.createEvent(eventName, model))
    }

  def destroyModels(): Unit =
    for (model <- models)
      Dispatch.dispatchEvent(new XFormsModelDestructEvent(model, Map()))

  def defaultModel: Option[XFormsModel] = _models.headOption

  // Get a list of all the relevant models in this container and all sub-containers
  def allModels: Iterator[XFormsModel] =
    if (isRelevant)
      models.iterator ++ (childrenXBLContainers flatMap (_.allModels))
    else
      Iterator.empty

  def searchContainedModelsInScope(sourceEffectiveId: String, staticId: String, contextItemOpt: Option[Item]): Option[XFormsObject] = {

    val sourcePrefixedId         = XFormsId.getPrefixedId(sourceEffectiveId): String
    val resolutionScopeContainer = findScopeRoot(sourcePrefixedId)

    resolutionScopeContainer.searchContainedModels(staticId, contextItemOpt)
  }

  // Performance: for some reason, with Scala 2.9.2 at least, using for (model <- models) { ... return ... } is much
  // slower than using an Iterator (profiler).
  def searchContainedModels(staticId: String, contextItemOpt: Option[Item]): Option[XFormsObject] =
    isRelevant && models.nonEmpty flatOption {
      models.iterator flatMap (_.findObjectById(staticId, contextItemOpt)) nextOption()
    }

  def restoreModelsState(deferRRR: Boolean): Unit = {
    // Handle this container only

    // 1: Restore all instances
    for (model <- _models)
      model.restoreInstances()

    // 2: Restore everything else
    // NOTE: It's important to do this as a separate step, because variables which might refer to other models'
    // instances.
    for (model <- _models)
      model.restoreState(deferRRR)
  }
}

trait RefreshSupport {

  self: XBLContainer =>

  // Below we split RRR and Refresh in order to reduce the number of refreshes performed

  // This is fun. Say you have a single model requiring RRRR and you get here the first time:
  //
  // - Model's rebuildRecalculateRevalidateIfNeeded() runs
  // - Its rebuild runs
  // - That dispatches xforms-rebuild as an outer event
  // - The current method is then called again recursively
  // - So RRR runs, recursively, then comes back here
  //
  // We do not want to run refresh just after coming back from rebuildRecalculateRevalidateIfNeeded(), otherwise
  // because of recursion you might have RRR, then refresh, refresh again, instead of RRR, refresh, RRR, refresh,
  // etc. So:
  //
  // - We first exhaust the possibility for RRR globally
  // - Then we run refresh if possible
  // - Then we check again, as refresh events might have changed things
  //
  // TODO: We might want to implement some code to detect excessive loops/recursion
  def synchronizeAndRefresh(): Unit =
    if (! containingDocument.controls.isInRefresh) // see https://github.com/orbeon/orbeon-forms/issues/1550
      while (needRebuildRecalculateRevalidate || containingDocument.controls.isRequireRefresh) {

        while (needRebuildRecalculateRevalidate)
          rebuildRecalculateRevalidateIfNeeded()

        if (containingDocument.controls.isRequireRefresh)
          containingDocument.controls.doRefresh()
      }

  def needRebuildRecalculateRevalidate: Boolean =
    allModels exists (_.needRebuildRecalculateRevalidate)

  // NOTE: It used to be (Java implementation) that childrenContainers could be modified down the line and cause a
  // ConcurrentModificationException. This should no longer happen as once we obtain a reference to
  // childrenXBLContainers, that collection doesn't change.
  def rebuildRecalculateRevalidateIfNeeded(): Unit =
    allModels foreach (_.rebuildRecalculateRevalidateIfNeeded)

  def requireRefresh(): Unit = {
    // Note that we don't recurse into children container as for now refresh is global
    val controls = containingDocument.controls
    if (controls.isInitialized)
      // Controls exist, otherwise there is no point in doing anything controls-related
      controls.requireRefresh()
  }

  def setDeferredFlagsForSetindex(): Unit =
    // XForms 1.1: "This action affects deferred updates by performing deferred update in its initialization and by
    // setting the deferred update flags for recalculate, revalidate and refresh."
    for (model <- models)
      // NOTE: We used to do this, following XForms 1.0, but XForms 1.1 has changed the behavior
      //currentModel.getBinds.rebuild()
      model.markValueChange(null, false)
}

trait ContainerResolver {

  self: XBLContainer =>

  // Return the namespace mappings associated with the given element. The element must be part of this container.
  def getNamespaceMappings(element: Element): NamespaceMapping =
    partAnalysis.getNamespaceMapping(fullPrefix, element)

  // Check whether if the bind id can resolve in this scope
  def containsBind(bindId: String): Boolean =
    partAnalysis.getModelsForScope(innerScope) exists (_.containsBind(bindId))

  // Get object with the effective id specified within this container or descendant containers
  final def getObjectByEffectiveId(effectiveId: String): XFormsObject =
    findObjectByEffectiveId(effectiveId).orNull

  def findObjectByEffectiveId(effectiveId: String): Option[XFormsObject] =
    allModels flatMap (_.findObjectByEffectiveId(effectiveId)) nextOption()

  /**
   * Return the current repeat index for the given xf:repeat id, -1 if the id is not found.
   *
   * The repeat must be in the scope of this container.
   *
   * @param sourceEffectiveId effective id of the source (control, model, instance, submission, ...), or null
   * @param repeatStaticId    static id of the target
   * @return                  repeat index, -1 if repeat is not found
   */
  def getRepeatIndex(sourceEffectiveId: String, repeatStaticId: String): Int = {

    def fromConcreteRepeat = {

      val repeatControl =
        resolveObjectByIdInScope(sourceEffectiveId, repeatStaticId) collect {
          case repeat: XFormsRepeatControl             => repeat
          case iteration: XFormsRepeatIterationControl => iteration.repeat
        }

      repeatControl map (_.getIndex)
    }

    def fromStaticRepeat = {
      // Make sure to use prefixed id, e.g. my-stuff$my-foo-bar$my-repeat
      val sourcePrefixedId = XFormsId.getPrefixedId(sourceEffectiveId)
      val scope = partAnalysis.scopeForPrefixedId(sourcePrefixedId)
      val repeatPrefixedId = scope.prefixedIdForStaticId(repeatStaticId)

      getPartAnalysis.findControlAnalysis(repeatPrefixedId) match {
        case Some(_: RepeatControl) => Some(0)
        case _                      => None
      }
    }

    fromConcreteRepeat orElse fromStaticRepeat getOrElse -1
  }

  def resolveObjectByIdInScope(
    sourceEffectiveId  : String,
    staticOrAbsoluteId : String,
    contextItemOpt     : Option[Item] = None
  ): Option[XFormsObject] = {
    val sourcePrefixedId = XFormsId.getPrefixedId(sourceEffectiveId)
    val resolutionScopeContainer = findScopeRoot(sourcePrefixedId)

    Option(resolutionScopeContainer.resolveObjectById(sourceEffectiveId, staticOrAbsoluteId, contextItemOpt))
  }

  /**
   * Resolve an object in the scope of this container.
   *
   * @param sourceEffectiveId   effective id of the source (control, model, instance, submission, ...) (can be null
   *                            only for absolute ids)
   * @param staticOrAbsoluteId  static or absolute id of the object
   * @param contextItemOpt      context item, or null (used for bind resolution only)
   * @return                    object, or null if not found
   */
  def resolveObjectById(
    sourceEffectiveId  : String,
    staticOrAbsoluteId : String,
    contextItemOpt     : Option[Item]
  ): XFormsObject =
    resolveObjectsById(sourceEffectiveId, staticOrAbsoluteId, contextItemOpt, followIndexes = true).headOption.orNull

  def resolveObjectsById(
    sourceEffectiveId  : String,
    staticOrAbsoluteId : String,
    contextItemOpt     : Option[Item],
    followIndexes      : Boolean
  ): List[XFormsObject] = {

    // Handle "absolute ids"
    // NOTE: Experimental, definitive format TBD
    if (XFormsId.isAbsoluteId(staticOrAbsoluteId))
      return Option(containingDocument.getObjectByEffectiveId(XFormsId.absoluteIdToEffectiveId(staticOrAbsoluteId))).toList

    // Make sure the static id passed is actually a static id
    require(XFormsId.isStaticId(staticOrAbsoluteId), "Target id must be static id: " + staticOrAbsoluteId)

    require(sourceEffectiveId ne null, "Source id must be specified.")

    // 1. Check if requesting the binding id. If so, we interpret this as requesting the bound element
    //    and return the control associated with the bound element.
    // TODO: should this use sourceControlEffectiveId?

    // TODO: Handle https://github.com/orbeon/orbeon-forms/issues/3853. Unclear. For `xxf:dynamic`, `self.prefixedId`
    // is the prefixed id of the `xxf:dynamic` in the outer scope. So we can't just use `getPartAnalysis`. Use parent
    // part if any?
    val bindingIdOpt = containingDocument.staticOps.getBinding(self.prefixedId) map (_.bindingId)
    if (bindingIdOpt.contains(staticOrAbsoluteId))
      return containingDocument.findControlByEffectiveId(effectiveId).toList

    // 2. Search in directly contained models
    val resultModelObject = searchContainedModels(staticOrAbsoluteId, contextItemOpt)
    if (resultModelObject.isDefined)
      return resultModelObject.toList

    // Check that source is resolvable within this container
    if (! isEffectiveIdResolvableByThisContainer(sourceEffectiveId))
      throw new OXFException("Source not resolvable in container: " + sourceEffectiveId)

    // 3. Search in controls
    // NOTE: in the future, sub-tree of components might be rooted in this class
    resolveControlById(sourceEffectiveId, staticOrAbsoluteId, contextItemOpt, followIndexes)
  }

  private def resolveControlById(
    sourceEffectiveId  : String,
    staticOrAbsoluteId : String,
    contextItemOpt     : Option[Item],
    followIndexes      : Boolean
  ): List[XFormsControl] =
    findSourceControlEffectiveId(sourceEffectiveId, contextItemOpt).toList flatMap { sourceControlEffectiveId =>

      // Resolve on controls
      val controls =
        Controls.resolveControlsById(
          containingDocument,
          sourceControlEffectiveId,
          staticOrAbsoluteId,
          followIndexes
        )

      controls find (c => ! isEffectiveIdResolvableByThisContainer(c.getEffectiveId)) foreach { c =>
        // This should not happen!
        throw new OXFException(s"Resulting control is not in proper scope: `${c.getEffectiveId}`")
      }

      controls
    }

  private def isEffectiveIdResolvableByThisContainer(effectiveId: String): Boolean =
    self eq findScopeRoot(XFormsId.getPrefixedId(effectiveId))

  private def findSourceControlEffectiveId(
    sourceEffectiveId : String,
    contextItemOpt    : Option[Item]
  ): Option[String] =
    searchContainedModels(XFormsId.getStaticIdFromId(sourceEffectiveId), contextItemOpt) match {
      case Some(_) =>
        // Source is a model object, so find first control instead
        findFirstControlEffectiveId
      case None =>
        // Assume the source is a control
        Some(sourceEffectiveId)
    }

  // For Java callers
  def getInstanceForNode(nodeInfo: NodeInfo): XFormsInstance =
    instanceForNodeOpt(nodeInfo).orNull

  // Recursively find the instance containing the specified node
  def instanceForNodeOpt(nodeInfo: NodeInfo): Option[XFormsInstance] =
    isRelevant flatOption {
      allModels flatMap (_.findInstanceForNode(nodeInfo)) nextOption()
    }

  // Locally find the instance with the specified id, searching in any relevant model
  def findInstance(instanceStaticId: String): Option[XFormsInstance] =
    isRelevant && models.nonEmpty flatOption {
      models.iterator flatMap (_.findInstance(instanceStaticId)) nextOption()
    }

  // Find the instance in this or descendant containers
  def findInstanceInDescendantOrSelf(instanceStaticId: String): Option[XFormsInstance] =
    isRelevant && models.nonEmpty flatOption {
      allModels flatMap (_.instancesIterator) find (_.getId == instanceStaticId)
    }

  // For Java callers
  def findInstanceOrNull(instanceId: String) = findInstance(instanceId).orNull

  protected def initializeNestedControls(): Unit = ()

  def findFirstControlEffectiveId: Option[String] =
    // We currently don't have a real notion of a "root" control, so we resolve against the first control if any
    getChildrenControls(containingDocument.controls).headOption map (_.getEffectiveId)

  def getChildrenControls(controls: XFormsControls): Seq[XFormsControl] =
    associatedControlOpt match {
      case Some(container: XFormsContainerControl) => container.children
      case _                                       => Nil
    }
}