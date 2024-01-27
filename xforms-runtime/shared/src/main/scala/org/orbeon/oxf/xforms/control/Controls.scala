/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.DynamicVariable
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.model.XFormsInstance
import org.orbeon.oxf.xforms.state.{ControlState, InstanceState, InstancesControls}
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.Constants.{RepeatIndexSeparatorString, RepeatSeparatorString}
import org.orbeon.xforms.XFormsId

import scala.collection.compat._
import scala.jdk.CollectionConverters._


object Controls {

  // Special for Form Runner
  // It's not ideal to have this here, but we currently don't have a pluggable way for Form Runner to indicate to
  // XForms that some controls should be indexed.
  val SectionTemplateUriPrefix = "http://orbeon.org/oxf/xml/form-builder/component/"

  // Create the entire tree of control from the root
  def createTree(
    containingDocument : XFormsContainingDocument,
    controlIndex       : ControlIndex,
    state              : Option[Map[String, ControlState]],
    collector          : ErrorEventCollector
  ): Option[XFormsControl] = {

    val bindingContext = containingDocument.getContextStack.resetBindingContext(collector)
    val rootControl    = containingDocument.staticState.topLevelPart.getTopLevelControls.head

    buildTree(
      controlIndex,
      state,
      containingDocument,
      bindingContext,
      None,
      rootControl,
      Nil,
      collector
    ) |!>
      logTreeIfNeeded("after building full tree")
  }

  // Create a new repeat iteration for insertion into the current tree of controls
  def createRepeatIterationTree(
    controlIndex  : ControlIndex,
    repeatControl : XFormsRepeatControl,
    iterationIndex: Int,
    collector     : ErrorEventCollector
  ): XFormsRepeatIterationControl = {

    val idSuffix = XFormsId.getEffectiveIdSuffixParts(repeatControl.getEffectiveId).toSeq :+ iterationIndex

    // This is the context of the iteration
    // buildTree() does a pushBinding(), but that won't change the context (no @ref, etc. on the iteration itself)
    val container = repeatControl.container
    val bindingContext =
      container.getContextStack.setBinding(repeatControl.bindingContext)

    // This has to be the case at this point, otherwise it's a bug in our code
    assert(repeatControl.staticControl.iteration.isDefined)

    val controlOpt =
      buildTree(
        controlIndex,
        None,
        container,
        bindingContext,
        Some(repeatControl),
        repeatControl.staticControl.iteration.get,
        idSuffix,
        collector
      ) |!>
        logTreeIfNeeded("after building repeat iteration tree")

    controlOpt.get.asInstanceOf[XFormsRepeatIterationControl] // we "know" this, right?
  }

  // Create a new subtree of controls (used by xxf:dynamic)
  def createSubTree(
    container        : XBLContainer,
    controlIndex     : ControlIndex,
    containerControl : XFormsContainerControl,
    rootAnalysis     : ElementAnalysis,
    state            : Option[Map[String, ControlState]],
    collector        : ErrorEventCollector
  ): Option[XFormsControl] = {

    val idSuffix = XFormsId.getEffectiveIdSuffixParts(containerControl.getEffectiveId).toSeq
    val bindingContext = containerControl.bindingContextForChildOrEmpty(collector)

    buildTree(
      controlIndex,
      state,
      container,
      bindingContext,
      Some(containerControl),
      rootAnalysis,
      idSuffix,
      collector
    ) |!>
      logTreeIfNeeded("after building subtree")
  }

  // Build a component subtree
  private def buildTree(
    controlIndex    : ControlIndex,
    state           : Option[Map[String, ControlState]],
    container       : XBLContainer,
    bindingContext  : BindingContext,
    parentOption    : Option[XFormsControl],
    staticElement   : ElementAnalysis,
    idSuffix        : Seq[Int],
    collector       : ErrorEventCollector
  ): Option[XFormsControl] = {

    // Determine effective id
    val effectiveId =
      if (idSuffix.isEmpty)
        staticElement.prefixedId
      else
        staticElement.prefixedId + RepeatSeparatorString + (idSuffix mkString RepeatIndexSeparatorString)

    // Instantiate the control
    // TODO LATER: controls must take ElementAnalysis, not Element

    val stats = container.containingDocument.getRequestStats

    // NOTE: If we are unable to create a control (case of Model at least), this has no effect
    XFormsControlFactory.createXFormsControl(container, parentOption.orNull, staticElement, effectiveId) map { control =>

      stats.controlsCreated += 1

      // Index the new control
      // NOTE: We used to do this after evaluating the binding. In general it shouldn't hurt to do it here.
      // 2018-12-21: The probable reason to move indexing before is so that variables can resolve controls indexed so far.
      controlIndex.indexControl(control)

      // Determine binding
      control.evaluateBindingAndValues(
        parentContext = bindingContext,
        update        = false,
        restoreState  = state.isDefined,
        state         = state flatMap (_.get(effectiveId)),
        collector     = collector
      )

      // Build the control's children if any
      control.buildChildren(buildTree(controlIndex, state, _, _, Some(control), _, _, _), idSuffix, collector)

      control
    }
  }

  // Build children controls if any, delegating the actual construction to the given `buildTree` function
  def buildChildren(
    control   : XFormsControl,
    children  : => Iterable[ElementAnalysis],
    buildTree : (XBLContainer, BindingContext, ElementAnalysis, Seq[Int], ErrorEventCollector) => Option[XFormsControl],
    idSuffix  : Seq[Int],
    collector : ErrorEventCollector
  ): Unit = {
    // Start with the context within the current control
    var newBindingContext = control.bindingContextForChildOrEmpty(collector)
    // Build each child
    children foreach { childElement =>
      buildTree(control.container, newBindingContext, childElement, idSuffix, collector) foreach { newChildControl =>
        // Update the context based on the just created control
        newBindingContext = newChildControl.bindingContextForFollowing
      }
    }
  }

  def iterateAllRepeatedControlsForTarget(
    containingDocument       : XFormsContainingDocument,
    sourceControlEffectiveId : String,
    targetStaticId           : String
  ): Iterator[XFormsControl] =
    resolveControlsById(
      containingDocument,
      sourceControlEffectiveId,
      targetStaticId,
      followIndexes = true
    ).iterator flatMap
      XFormsRepeatControl.findAllRepeatedControls

  def resolveControlsById(
    containingDocument       : XFormsContainingDocument,
    sourceControlEffectiveId : String,
    targetStaticId           : String,
    followIndexes            : Boolean
  ): List[XFormsControl] = {

    val sourcePrefixedId = XFormsId.getPrefixedId(sourceControlEffectiveId)
    val scope            = containingDocument.staticOps.scopeForPrefixedId(sourcePrefixedId)
    val targetPrefixedId = scope.prefixedIdForStaticId(targetStaticId)

    for {
      controls           <- Option(containingDocument.controls).toList
      effectiveControlId <-
        resolveControlsEffectiveIds(
          containingDocument.staticOps,
          controls.getCurrentControlTree,
          sourceControlEffectiveId,
          targetPrefixedId,
          followIndexes
        )
      control            <- controls.findObjectByEffectiveId(effectiveControlId)
    } yield
      control
  }

  /**
   * Find effective control ids based on a source and a control static id, following XBL scoping and the repeat
   * hierarchy.
   *
   * @return effective control ids (0 or 1 if `followIndexes == true`, 0 to n if `followIndexes = false`)
   */
  def resolveControlsEffectiveIds(
    ops               : StaticStateGlobalOps,
    tree              : ControlTree,
    sourceEffectiveId : String,   // reference to source control, e.g. "list$age.3"
    targetPrefixedId  : String,   // reference to target control, e.g. "list$xf-10"
    followIndexes     : Boolean   // whether to follow repeat indexes
  ): List[String] = {

    // Don't do anything if there are no controls
    if (tree.children.isEmpty)
      return Nil

    // NOTE: The implementation tries to do a maximum using the static state. One reason is that the source
    // control's effective id might not yet have an associated control during construction. E.g.:
    //
    // <xf:group id="my-group" ref="employee[index('employee-repeat')]">
    //
    // In that case, the XFormsGroupControl does not yet exist when its binding is evaluated. However, its
    // effective id is known and passed as source, and can be used for resolving the id passed to the index()
    // function.
    //
    // We trust the caller to pass a valid source effective id. That value is always internal, i.e. not created by a
    // form author. On the other hand, the target id cannot be trusted as it is typically specified by the form
    // author.

    val (_, commonIndexesLeafToRoot, remainingRepeatPrefixedIdsLeafToRoot) =
      getStaticRepeatDetails(ops, sourceEffectiveId, targetPrefixedId)

    def searchNextRepeatLevel(indexes: List[Int], nextRepeatPrefixedIds: List[String]): List[List[Int]] =
      nextRepeatPrefixedIds match {
        case Nil =>
          List(indexes)
        case repeatPrefixedId :: remainingPrefixedIds =>
          tree.findRepeatControl(repeatPrefixedId + buildSuffix(indexes)) match {
            case None =>
              Nil // control might not exist (but why?)
            case Some(repeatControl) if followIndexes =>
              searchNextRepeatLevel(repeatControl.getIndex :: indexes, remainingPrefixedIds)
            case Some(repeatControl) =>
              1 to repeatControl.getSize flatMap (i => searchNextRepeatLevel(i :: indexes, remainingPrefixedIds)) toList
            case _ =>
              throw new IllegalStateException
          }
      }

    val allIndexes = searchNextRepeatLevel(commonIndexesLeafToRoot, remainingRepeatPrefixedIdsLeafToRoot.reverse)

    allIndexes map (indexes => targetPrefixedId + buildSuffix(indexes))
  }

  def buildSuffix(iterations: List[Int]): String =
    if (iterations.isEmpty)
      ""
    else
      iterations.reverse map (_.toString) mkString (RepeatSeparatorString, RepeatIndexSeparatorString, "")

  def getStaticRepeatDetails(
    ops               : StaticStateGlobalOps,
    sourceEffectiveId : String,
    targetPrefixedId  : String
  ): (Option[String], List[Int], List[String]) = {

    require(sourceEffectiveId ne null, "Source effective id is required.")

    val sourcePrefixedId = XFormsId.getPrefixedId(sourceEffectiveId)
    val sourceParts      = XFormsId.getEffectiveIdSuffixParts(sourceEffectiveId)

    val ancestorRepeatPrefixedIdOpt = ops.findClosestCommonAncestorRepeat(sourcePrefixedId, targetPrefixedId)

    val commonIndexes =
      for {
        ancestorRepeatPrefixedId <- ancestorRepeatPrefixedIdOpt.to(List)
        index                    <- sourceParts.take(ops.getAncestorRepeatIds(ancestorRepeatPrefixedId).size + 1).reverse
      } yield
        index

    // Find list of ancestor repeats for destination WITHOUT including the closest ancestor repeat if any
    val remainingTargetRepeatsPrefixedIds =
      ops.getAncestorRepeatIds(targetPrefixedId, ancestorRepeatPrefixedIdOpt)

    (ancestorRepeatPrefixedIdOpt, commonIndexes, remainingTargetRepeatsPrefixedIds)
  }

  // Update the container's and all its descendants' bindings
  // This is used by `xf:switch` and `xxf:dialog` as of 2021-04-14.
  def updateBindings(control: XFormsContainerControl, collector: ErrorEventCollector): BindingUpdater = {
    val xpathDependencies = control.containingDocument.xpathDependencies
    xpathDependencies.bindingUpdateStart()

    val startBindingContext =
      control.preceding map (_.bindingContextForFollowing) getOrElse control.parent.bindingContextForChildOrEmpty(collector)

    val updater = new BindingUpdater(control.containingDocument, startBindingContext)
    visitControls(control, updater, includeCurrent = true, collector)
    xpathDependencies.bindingUpdateDone()

    Option(control) foreach logTreeIfNeeded("after subtree update")

    updater
  }

  // Update the bindings for the entire tree of controls
  def updateBindings(
    containingDocument: XFormsContainingDocument,
    collector         : ErrorEventCollector
  ): BindingUpdater = {

    val updater = new BindingUpdater(containingDocument, containingDocument.getContextStack.resetBindingContext(collector))
    visitSiblings(updater, containingDocument.controls.getCurrentControlTree.children, collector)

    containingDocument.controls.getCurrentControlTree.rootOpt foreach
      logTreeIfNeeded("after full tree update")

    updater
  }

  class BindingUpdater(
    val containingDocument  : XFormsContainingDocument,
    val startBindingContext : BindingContext
  ) extends XFormsControlVisitorListener {

    private var newIterationsIds = Set.empty[String]

    // Start with initial context
    private var bindingContext = startBindingContext
    private val xpathDependencies = containingDocument.xpathDependencies

    private var level = 0
    private var relevanceChangeLevel = -1

    private var _visitedCount = 0
    def visitedCount: Int = _visitedCount

    private var _updatedCount = 0
    def updatedCount: Int = _updatedCount

    private var _optimizedCount = 0
    def optimizedCount: Int = _optimizedCount

    var _partialFocusRepeatOption: Option[XFormsRepeatControl] = None
    def partialFocusRepeat: Option[XFormsRepeatControl] = _partialFocusRepeatOption

    val stats = containingDocument.getRequestStats

    def startVisitControl(control: XFormsControl, collector: ErrorEventCollector): Boolean = {

      // Increment before the early return as `endVisitControl` always decrements.
      // Caused https://github.com/orbeon/orbeon-forms/issues/3976
      level += 1

      // If this is a new iteration, don't recurse into it
      if (newIterationsIds.nonEmpty && control.isInstanceOf[XFormsRepeatIterationControl] && newIterationsIds(control.effectiveId))
        return false

      _visitedCount += 1

      // Value of relevance of content before messing with the binding
      val wasContentRelevant = control.wasContentRelevant

      // Update is required if:
      //
      // - we are within a container whose content relevance has changed
      // - or dependencies tell us an update is required
      // - or the control has a @model attribute (TODO TEMP HACK: because that causes model variable evaluation!)
      def mustReEvaluateBinding =
        (relevanceChangeLevel != -1 && level > relevanceChangeLevel)                       ||
        xpathDependencies.requireBindingUpdate(control.staticControl, control.effectiveId) ||
        control.staticControl.model.isDefined

      // Only update the binding if needed
      if (mustReEvaluateBinding) {

        stats.bindingsUpdated += 1

        def evaluateBindingAndValues(): Unit =
          control.evaluateBindingAndValues(
            parentContext = bindingContext,
            update        = true,
            restoreState  = false,
            state         = None,
            collector     = collector
          )

        control match {
          case repeatControl: XFormsRepeatControl =>

            // Update iterations
            val oldRepeatSeq = control.bindingContext.nodeset.asScala

            evaluateBindingAndValues()

            val (newIterations, partialFocusRepeatOption) =
              repeatControl.updateIterations(oldRepeatSeq, collector)

            // Remember partial focus out of repeat if needed
            if (this._partialFocusRepeatOption.isEmpty && partialFocusRepeatOption.isDefined)
              this._partialFocusRepeatOption = partialFocusRepeatOption

            // Remember newly created iterations so we don't recurse into them in startRepeatIteration()
            //
            // - It is not needed to recurse into them because their bindings are up to date since they have
            //   just been created
            // - However they have not yet been evaluated. They will be evaluated at the same time the other
            //   controls are evaluated
            //
            // NOTE: don't call ControlTree.initializeRepeatIterationTree() here because refresh evaluates
            // controls and dispatches events
            this.newIterationsIds = newIterations map (_.getEffectiveId) toSet

          case _ =>

            // Simply set the new binding
            evaluateBindingAndValues()
        }
        _updatedCount += 1
      } else {
        stats.bindingsRefreshed += 1
        control.refreshBindingAndValues(bindingContext, collector)
        _optimizedCount += 1
      }

      // Remember whether we are in a container whose content relevance has changed
      // NOTE: The correct logic at this time is to force binding re-evaluation if container relevance has
      // changed. Doing this only when content becomes relevant is not enough as shown with the following bug:
      // https://github.com/orbeon/orbeon-forms/issues/939
      if (relevanceChangeLevel == -1 && control.isInstanceOf[XFormsContainerControl] && wasContentRelevant != control.contentRelevant)
        relevanceChangeLevel = level // entering level of containing

      control.bindingContextForChildOpt(collector) match {
        case Some(bindingContextForChild) =>
          bindingContext = bindingContextForChild
          true
        case None =>
          bindingContext = null // should not be used
          false
      }
    }

    def endVisitControl(control: XFormsControl): Unit = {

      // Check if we are exiting the level of a container whose content relevance has changed
      if (relevanceChangeLevel == level)
        relevanceChangeLevel = -1

      // Update context for following controls
      bindingContext = control.bindingContextForFollowing

      // When we exit a repeat control, discard the list of new iterations so we don't unnecessarily test on them
      if (control.isInstanceOf[XFormsRepeatControl])
        newIterationsIds = Set.empty[String]

      level -= 1
    }
  }

  // Iterator over a control's ancestors
  class AncestorOrSelfIterator(start: XFormsControl) extends Iterator[XFormsControl] {
    private var _next = start
    def hasNext: Boolean = _next ne null
    def next(): XFormsControl = {
      val result = _next
      _next = _next.parent
      result
    }
  }

  trait XFormsControlVisitorListener {
    def startVisitControl(control: XFormsControl, collector: ErrorEventCollector): Boolean
    def endVisitControl(control: XFormsControl): Unit
  }

  // Iterator over the given control and its descendants
  class ControlsIterator(
    private val start         : XFormsControl,
    private val includeSelf   : Boolean,
    private val followVisible : Boolean
  ) extends Iterator[XFormsControl] {

    private val children = start match {
      case c: XFormsSwitchControl if followVisible => c.selectedCaseIfRelevantOpt.iterator
      case c: XFormsContainerControl               => c.children.iterator
      case _                                       => Iterator.empty
    }

    private var descendants: Iterator[XFormsControl] = Iterator.empty

    private def findNext(): XFormsControl =
      if (descendants.hasNext)
        // Descendants of current child
        descendants.next()
      else if (children.hasNext) {
        // Move to next child
        val next = children.next()
        if (next.isInstanceOf[XFormsContainerControl])
          descendants = ControlsIterator(next, includeSelf = false)
        next
      } else
        null

    private var current =
      if (includeSelf)
        start
      else
        findNext()

    def next(): XFormsControl = {
      val result = current
      current = findNext()
      result
    }

    def hasNext: Boolean = current ne null
  }

  object ControlsIterator {
    def apply(start: XFormsControl, includeSelf: Boolean, followVisible: Boolean = false): Iterator[XFormsControl] =
      new ControlsIterator(start, includeSelf, followVisible)

    def apply(start: ControlTree): Iterator[XFormsControl] =
      start.children.iterator flatMap (new ControlsIterator(_, includeSelf = true, followVisible = false))
  }

  // Evaluate the body with InstancesControls in scope
  def withDynamicStateToRestore[T](instancesControls: InstancesControls)(body: => T): T =
    instancesControlsToRestore.withValue(instancesControls)(body)

  // Get state to restore
  private def restoringDynamicState = instancesControlsToRestore.value
  def isRestoringDynamicState   : Boolean                           = restoringDynamicState.isDefined
  def restoringInstanceControls : Option[InstancesControls]         = restoringDynamicState
  def restoringControls         : Option[Map[String, ControlState]] = restoringInstanceControls map (_.controls)

  // ThreadLocal for dynamic state restoration
  private val instancesControlsToRestore = new DynamicVariable[InstancesControls]

  // Visit all the descendant controls of the given container control
  // 2018-01-04: 2 uses left:
  // - `updateBindings`
  // - `dispatchDestructionEventsForRemovedRepeatIteration`
  def visitControls(
    control       : XFormsControl,
    listener      : XFormsControlVisitorListener,
    includeCurrent: Boolean,
    collector     : ErrorEventCollector
  ): Unit =
    control match {
      case containerControl: XFormsContainerControl =>
        // Container itself
        if (includeCurrent)
          if (! listener.startVisitControl(containerControl, collector))
            return

        // Children
        visitSiblings(listener, containerControl.children, collector)

        // Container itself
        if (includeCurrent)
          listener.endVisitControl(containerControl)
      case control =>
        if (includeCurrent) {
          listener.startVisitControl(control, collector)
          listener.endVisitControl(control)
        }
    }

  // Serialize relevant controls that have data
  //
  // - Repeat, switch and dialogs controls serialize state (have been for a long time). The state of all the other
  //   controls is rebuilt from model data. This way we minimize the size of serialized controls. In the future,
  //   more information might be serialized.
  // - `VisitableTrait` controls serialize state if `visited == true`
  def iterateControlsToSerialize(startOpt: Option[XFormsControl]): Iterator[ControlState] =
    for {
      start        <- startOpt.iterator
      control      <- ControlsIterator(start, includeSelf = false)
      if control.isRelevant
      controlState <- control.controlState.iterator
    } yield
      controlState

  def iterateInstancesToSerialize(startOpt: Option[XBLContainer], p: XFormsInstance => Boolean): Iterator[InstanceState] =
    for {
      startContainer <- startOpt.iterator
      model          <- startContainer.allModels
      instance       <- model.instancesIterator
      if p(instance)
    } yield
      new InstanceState(instance)

  private def visitSiblings(
    listener : XFormsControlVisitorListener,
    children : Seq[XFormsControl],
    collector: ErrorEventCollector
  ): Unit =
    for (currentControl <- children) {
      if (listener.startVisitControl(currentControl, collector)) {
        currentControl match {
          case container: XFormsContainerControl =>
            visitSiblings(listener, container.children, collector)
          case nonContainer =>
            // NOTE: Unfortunately we handle children actions of non container controls a bit differently
            val childrenActions = nonContainer.childrenActions
            if (childrenActions.nonEmpty)
              visitSiblings(listener, childrenActions, collector)
        }
      }
      listener.endVisitControl(currentControl)
    }

  // Log a subtree of controls as XML
  private def logTreeIfNeeded(message: String)(control: XFormsControl): Unit =
    if (Loggers.isDebugEnabled("control-tree"))
      debug(s"$message\n${ControlsDebugSupport.controlTreeAsXmlString(control)}")(control.containingDocument.controls.indentedLogger)
}