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

import controls._
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms._
import analysis.ElementAnalysis
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.BindingContext
import collection.JavaConverters._
import org.orbeon.oxf.xforms.state.{ControlState, InstancesControls}
import org.orbeon.oxf.util.DynamicVariable
import org.orbeon.oxf.util.ScalaUtils._

object Controls {

    // Create the entire tree of control from the root
    def createTree(
        containingDocument : XFormsContainingDocument,
        controlIndex       : ControlIndex,
        state              : Option[Map[String, ControlState]]
    ) = {

        val bindingContext = containingDocument.getContextStack.resetBindingContext()
        val rootControl    = containingDocument.getStaticState.topLevelPart.getTopLevelControls.head

        buildTree(
            controlIndex,
            state,
            containingDocument,
            bindingContext,
            None,
            rootControl,
            Seq()
        ) |!>
            logTreeIfNeeded("after building full tree")
    }

    // Create a new repeat iteration for insertion into the current tree of controls
    def createRepeatIterationTree(
        containingDocument : XFormsContainingDocument,
        controlIndex       : ControlIndex,
        repeatControl      : XFormsRepeatControl,
        iterationIndex     : Int
    ) = {

        val idSuffix = XFormsUtils.getEffectiveIdSuffixParts(repeatControl.getEffectiveId).toSeq :+ iterationIndex

        // This is the context of the iteration
        // buildTree() does a pushBinding(), but that won't change the context (no @ref, etc. on the iteration itself)
        val container = repeatControl.container
        val bindingContext = {
            val contextStack = container.getContextStack
            contextStack.setBinding(repeatControl.bindingContext)
            contextStack.pushIteration(iterationIndex)
        }

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
                idSuffix
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
        state            : Option[Map[String, ControlState]]
    ) = {

        val idSuffix = XFormsUtils.getEffectiveIdSuffixParts(containerControl.getEffectiveId).toSeq
        val bindingContext = containerControl.bindingContextForChild

        buildTree(
            controlIndex,
            state,
            container,
            bindingContext,
            Some(containerControl),
            rootAnalysis,
            idSuffix
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
        idSuffix        : Seq[Int]
    ): Option[XFormsControl] = {

        // Determine effective id
        val effectiveId =
            if (idSuffix.isEmpty)
                staticElement.prefixedId
            else
                staticElement.prefixedId + REPEAT_SEPARATOR + (idSuffix mkString REPEAT_INDEX_SEPARATOR_STRING)

        // Instantiate the control
        // TODO LATER: controls must take ElementAnalysis, not Element

        // NOTE: If we are unable to create a control (case of Model at least), this has no effect
        XFormsControlFactory.createXFormsControl(container, parentOption.orNull, staticElement, effectiveId) map {
            control ⇒
                // Index the new control
                // NOTE: We used to do this after evaluating the binding. In general it shouldn't hurt to do it here.
                // The reason to move indexing before is that
                controlIndex.indexControl(control)

                // Determine binding
                control.evaluateBindingAndValues(
                    parentContext = bindingContext,
                    update        = false,
                    restoreState  = state.isDefined,
                    state         = state flatMap (_.get(effectiveId))
                )

                // Build the control's children if any
                control.buildChildren(buildTree(controlIndex, state, _, _, Some(control), _, _), idSuffix)

                control
        }
    }

    // Build children controls if any, delegating the actual construction to the given `buildTree` function
    def buildChildren(
        control   : XFormsControl,
        children  : ⇒ Iterable[ElementAnalysis],
        buildTree : (XBLContainer, BindingContext, ElementAnalysis, Seq[Int]) ⇒ Option[XFormsControl],
        idSuffix  : Seq[Int]
    ): Unit = {
        // Start with the context within the current control
        var newBindingContext = control.bindingContextForChild
        // Build each child
        children foreach { childElement ⇒
            buildTree(control.container, newBindingContext, childElement, idSuffix) foreach { newChildControl ⇒
                // Update the context based on the just created control
                newBindingContext = newChildControl.bindingContextForFollowing
            }
        }
    }

    def findRepeatedControlsForTarget(
        containingDocument       : XFormsContainingDocument,
        sourceControlEffectiveId : String,
        targetStaticId           : String
    ) = (
        resolveObjectById(containingDocument, sourceControlEffectiveId, targetStaticId).toIterator
        flatMap XFormsRepeatControl.findAllRepeatedControls
    )

    def resolveObjectById(
        containingDocument       : XFormsContainingDocument,
        sourceControlEffectiveId : String,
        targetStaticId           : String
    ): Option[XFormsControl] = {

        val sourcePrefixedId = XFormsUtils.getPrefixedId(sourceControlEffectiveId)
        val scope            = containingDocument.getStaticOps.scopeForPrefixedId(sourcePrefixedId)
        val targetPrefixedId = scope.prefixedIdForStaticId(targetStaticId)

        val effectiveControlId =
            Option(
                findEffectiveControlId(
                    containingDocument.getStaticOps,
                    containingDocument.getControls.getCurrentControlTree,
                    sourceControlEffectiveId,
                    targetPrefixedId
                )
            )

        effectiveControlId map containingDocument.getControls.getObjectByEffectiveId
    }

    def resolveObjectByIdJava(
        containingDocument       : XFormsContainingDocument,
        sourceControlEffectiveId : String,
        targetStaticId           : String
    ) = resolveObjectById(
            containingDocument,
            sourceControlEffectiveId,
            targetStaticId
        ).orNull

    /**
     * Find an effective control id based on a source and a control static id, following XBL scoping and the repeat
     * structure.
     *
     * @param sourceEffectiveId  reference to source control, e.g. "list$age.3"
     * @param targetPrefixedId   reference to target control, e.g. "list$xf-10"
     * @return effective control id, or null if not found
     */
    def findEffectiveControlId(
        ops               : StaticStateGlobalOps,
        tree              : ControlTree,
        sourceEffectiveId : String,
        targetPrefixedId  : String
    ): String = {
        
        // Don't do anything if there are no controls
        if (tree.getChildren.isEmpty)
            return null
        
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
        
        // 1: Check preconditions
        require(sourceEffectiveId ne null, "Source effective id is required.")
        
        // 3: Implement XForms 1.1 "4.7.1 References to Elements within a repeat Element" algorithm
        
        // Find closest common ancestor repeat

        val sourcePrefixedId = XFormsUtils.getPrefixedId(sourceEffectiveId)
        val sourceParts = XFormsUtils.getEffectiveIdSuffixParts(sourceEffectiveId)

        val targetIndexBuilder = new StringBuilder

        def appendIterationToSuffix(iteration: Int) {
            if (targetIndexBuilder.isEmpty)
                targetIndexBuilder.append(REPEAT_SEPARATOR)
            else if (targetIndexBuilder.length != 1)
                targetIndexBuilder.append(REPEAT_INDEX_SEPARATOR)

            targetIndexBuilder.append(iteration.toString)
        }

        val ancestorRepeatPrefixedId = ops.findClosestCommonAncestorRepeat(sourcePrefixedId, targetPrefixedId)

        ancestorRepeatPrefixedId foreach { ancestorRepeatPrefixedId ⇒
            // There is a common ancestor repeat, use the current common iteration as starting point
            for (i ← 0 to ops.getAncestorRepeatIds(ancestorRepeatPrefixedId).size)
                appendIterationToSuffix(sourceParts(i))
        }

        // Find list of ancestor repeats for destination WITHOUT including the closest ancestor repeat if any
        // NOTE: make a copy because the source might be an immutable wrapped Scala collection which we can't reverse
        val targetAncestorRepeats = ops.getAncestorRepeatIds(targetPrefixedId, ancestorRepeatPrefixedId).reverse

        // Follow repeat indexes towards target
        for (repeatPrefixedId ← targetAncestorRepeats) {
            val repeatControl =
                tree.getControl(repeatPrefixedId + targetIndexBuilder.toString).asInstanceOf[XFormsRepeatControl]
            // Control might not exist
            if (repeatControl eq null)
                return null
            // Update iteration suffix
            appendIterationToSuffix(repeatControl.getIndex)
        }

        // Return target
        targetPrefixedId + targetIndexBuilder.toString
    }

    // Update the container's and all its descendants' bindings
    def updateBindings(control: XFormsContainerControl) = {
        val xpathDependencies = control.containingDocument.getXPathDependencies
        xpathDependencies.bindingUpdateStart()

        val startBindingContext =
            control.preceding map (_.bindingContextForFollowing) getOrElse control.parent.bindingContextForChild

        val updater = new BindingUpdater(control.containingDocument, startBindingContext)
        visitControls(control, updater, includeCurrent = true)
        xpathDependencies.bindingUpdateDone()

        Option(control) foreach logTreeIfNeeded("after subtree update")

        updater
    }

    // Update the bindings for the entire tree of controls
    def updateBindings(containingDocument: XFormsContainingDocument) = {
        val updater = new BindingUpdater(containingDocument, containingDocument.getContextStack.resetBindingContext())
        visitAllControls(containingDocument, updater)

        Option(containingDocument.getControls.getCurrentControlTree.getRoot) foreach
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
        private val xpathDependencies = containingDocument.getXPathDependencies

        private var level = 0
        private var relevanceChangeLevel = -1

        private var _visitedCount = 0
        def visitedCount = _visitedCount
        
        private var _updatedCount = 0
        def updatedCount = _updatedCount
        
        private var _optimizedCount = 0
        def optimizedCount = _optimizedCount
        
        var _partialFocusRepeatOption: Option[XFormsRepeatControl] = None
        def partialFocusRepeat = _partialFocusRepeatOption

        def startVisitControl(control: XFormsControl): Boolean = {

            // If this is a new iteration, don't recurse into it
            if (newIterationsIds.nonEmpty && control.isInstanceOf[XFormsRepeatIterationControl] && newIterationsIds(control.effectiveId))
                return false

            level += 1
            _visitedCount += 1

            // Value of relevance of content before messing with the binding
            val wasContentRelevant = control.wasContentRelevant

            // Update is required if:
            //
            // - we are within a container whose content relevance has changed
            // - or dependencies tell us an update is required
            // - or the control has a @model attribute (TODO TEMP HACK: because that causes model variable evaluation!)
            def mustReEvaluateBinding =
                (relevanceChangeLevel != -1 && level > relevanceChangeLevel)   ||
                xpathDependencies.requireBindingUpdate(control.prefixedId) ||
                (control.staticControl.element.attribute(XFormsConstants.MODEL_QNAME) ne null)

            // Only update the binding if needed
            if (mustReEvaluateBinding) {
                control match {
                    case repeatControl: XFormsRepeatControl ⇒
                        // Update iterations
                        val oldRepeatSeq = control.bindingContext.nodeset.asScala

                        control.evaluateBindingAndValues(
                            parentContext = bindingContext,
                            update        = true,
                            restoreState  = false,
                            state         = None
                        )

                        val (newIterations, partialFocusRepeatOption) =
                            repeatControl.updateIterations(oldRepeatSeq, null, isInsertDelete = false)

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
                    case control ⇒
                        // Simply set new binding
                        control.evaluateBindingAndValues(
                            parentContext = bindingContext,
                            update        = true,
                            restoreState  = false,
                            state         = None
                        )
                }
                _updatedCount += 1
            } else {
                control.refreshBindingAndValues(bindingContext)
                _optimizedCount += 1
            }

            // Update context for children controls
            bindingContext = control.bindingContextForChild

            // Remember whether we are in a container whose content relevance has changed
            // NOTE: The correct logic at this time is to force binding re-evaluation if container relevance has
            // changed. Doing this only when content becomes relevant is not enough as shown with the following bug:
            // https://github.com/orbeon/orbeon-forms/issues/939
            if (relevanceChangeLevel == -1 && control.isInstanceOf[XFormsContainerControl] && wasContentRelevant != control.contentRelevant)
                relevanceChangeLevel = level // entering level of containing

            true
        }

        def endVisitControl(control: XFormsControl) = {

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
        def hasNext = _next ne null
        def next() = {
            val result = _next
            _next = _next.parent
            result
        }
    }

    trait XFormsControlVisitorListener {
        def startVisitControl(control: XFormsControl): Boolean
        def endVisitControl(control: XFormsControl)
    }

    class XFormsControlVisitorAdapter extends XFormsControlVisitorListener {
        def startVisitControl(control: XFormsControl) = true
        def endVisitControl(control: XFormsControl) = ()
    }

    // Visit all the controls
    def visitAllControls(containingDocument: XFormsContainingDocument, listener: XFormsControlVisitorListener): Unit =
        visitAllControls(containingDocument.getControls.getCurrentControlTree, listener)

    // Visit all the controls
    def visitAllControls(tree: ControlTree, listener: XFormsControlVisitorListener): Unit =
        visitSiblings(listener, tree.getChildren.asScala)

    // Iterator over the given control and its descendants
    case class ControlsIterator( // Q: Should this be a case class? Probably not.
        private val start       : XFormsControl,
        private val includeSelf : Boolean
    ) extends Iterator[XFormsControl] {

        private val children = start match {
            case containerControl: XFormsContainerControl ⇒ containerControl.children.iterator
            case control                                  ⇒ Iterator.empty
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

        def next() = {
            val result = current
            current = findNext()
            result
        }

        def hasNext = current ne null
    }

    // Evaluate the body with InstancesControls in scope
    def withDynamicStateToRestore[T](instancesControls: InstancesControls, topLevel: Boolean = false)(body: ⇒ T) =
        instancesControlsToRestore.withValue((instancesControls, topLevel))(body)

    // Evaluate the body with InstancesControls in scope (Java callers)
    def withDynamicStateToRestoreJava(instancesControls: InstancesControls, runnable: Runnable) =
        withDynamicStateToRestore(instancesControls, topLevel = true)(runnable.run())

    // Get state to restore
    private def restoringDynamicState = instancesControlsToRestore.value
    def restoringInstanceControls     = restoringDynamicState map (_._1)
    def restoringControls             = restoringInstanceControls map (_.controls)
    def restoringInstancesJava        = restoringInstanceControls map (_.instancesJava) orNull

    // Whether we are restoring state
    def isRestoringDynamicState = restoringDynamicState exists (_._2)

    // ThreadLocal for dynamic state restoration
    private val instancesControlsToRestore = new DynamicVariable[(InstancesControls, Boolean)]

    // Visit all the descendant controls of the given container control
    // FIXME: Use ControlsIterator instead and then remove this when done
    def visitControls(control: XFormsControl, listener: XFormsControlVisitorListener, includeCurrent: Boolean): Unit =
        control match {
            case containerControl: XFormsContainerControl ⇒
                // Container itself
                if (includeCurrent)
                    if (! listener.startVisitControl(containerControl))
                        return

                // Children
                visitSiblings(listener, containerControl.children)

                // Container itself
                if (includeCurrent)
                    listener.endVisitControl(containerControl)
            case control ⇒
                if (includeCurrent) {
                    listener.startVisitControl(control)
                    listener.endVisitControl(control)
                }
        }

    private def visitSiblings(listener: XFormsControlVisitorListener, children: Seq[XFormsControl]): Unit =
        for (currentControl ← children) {
            if (listener.startVisitControl(currentControl)) {
                currentControl match {
                    case container: XFormsContainerControl ⇒
                        visitSiblings(listener, container.children)
                    case nonContainer ⇒
                        // NOTE: Unfortunately we handle children actions of non container controls a bit differently
                        val childrenActions = nonContainer.childrenActions
                        if (childrenActions.nonEmpty)
                            visitSiblings(listener, childrenActions)
                }

                listener.endVisitControl(currentControl)
            }
        }

    // Log a subtree of controls as XML
    private def logTreeIfNeeded(message: String)(control: XFormsControl): Unit =
        if (XFormsProperties.getDebugLogging.contains("control-tree"))
            control.containingDocument.getControls.getIndentedLogger.logDebug(message, control.toXMLString)
}