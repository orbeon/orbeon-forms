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
package org.orbeon.oxf.xforms

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.Logging.*
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.control.Controls.{BindingUpdater, ControlsIterator}
import org.orbeon.oxf.xforms.control.controls.{XFormsRepeatControl, XFormsRepeatIterationControl, XFormsSelect1Control}
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events.XXFormsRefreshDoneEvent
import org.orbeon.oxf.xforms.event.{Dispatch, EventCollector}
import org.orbeon.oxf.xforms.itemset.Itemset
import org.orbeon.oxf.xforms.state.ControlState


class XFormsControls(val containingDocument: XFormsContainingDocument) {

  import Private.*

  implicit val indentedLogger: IndentedLogger =
    containingDocument.getIndentedLogger("control") // Q: Not "controls"?

  def isInitialized           : Boolean = Private.initialized
  def isDirtySinceLastRequest : Boolean = Private.dirtySinceLastRequest
  def isRequireRefresh        : Boolean = Private.requireRefresh
  def isInRefresh             : Boolean = Private.inRefresh

  var openDialogs: Int = 0

  def markDirtySinceLastRequest(bindingsAffected: Boolean): Unit = {
    dirtySinceLastRequest = true
    if (bindingsAffected)
      currentControlTree.markBindingsDirty()
  }

  def requireRefresh(): Unit = {
    Private.requireRefresh = true
    markDirtySinceLastRequest(bindingsAffected = true)
  }

  def withRefresh[A](thunk: => A): A = {
    Private.requireRefresh = false
    Private.inRefresh = true
    containingDocument.getRequestStats.refreshes += 1
    containingDocument.xpathDependencies.refreshStart()
    try
      thunk
    finally {
      // TODO: Why a `finally` block here? If an exception happened, do we really need to do a `refreshDone()`?
      // 2023-12-08: Maybe ok as this cleans-up the XPath dependencies state.
      Private.inRefresh = false
      containingDocument.xpathDependencies.refreshDone()
    }
  }

  // Create the controls, whether upon initial creation of restoration of the controls.
  def createControlTree(state: Option[Map[String, ControlState]], collector: ErrorEventCollector): Unit = {

    assert(! initialized)

    if (containingDocument.staticState.topLevelPart.hasControls) {

      // NOTE: We set this first so that the tree is made available during construction to XPath functions
      // like `index()` or `case()`
      initialControlTree = new ControlTree
      currentControlTree = initialControlTree

      // Set this here so that while `initialize()` runs below, refresh events will find the flag set
      initialized = true

      currentControlTree.initialize(containingDocument, state, collector)
    } else {
      initialized = true
    }
  }

  // Adjust the controls after sending a response.
  // This makes sure that we don't keep duplicate control trees.
  def afterUpdateResponse(): Unit = {

    assert(initialized)

    if (containingDocument.staticState.topLevelPart.hasControls) {

      // Keep only one control tree
      initialControlTree = currentControlTree

      // We are now clean
      markCleanSinceLastRequest()

      // Need to make sure that `current eq initial` within controls
      ControlsIterator(containingDocument.controls.getCurrentControlTree) foreach (_.resetLocal())
    }
  }

  // Create a new repeat iteration for insertion into the current tree of controls.
  def createRepeatIterationTree(
    repeatControl  : XFormsRepeatControl,
    iterationIndex : Int, //
    collector      : ErrorEventCollector// 1..repeat size + 1
  ): XFormsRepeatIterationControl = {

    if ((initialControlTree eq currentControlTree) && containingDocument.isHandleDifferences)
      throw new OXFException("Cannot call `insertRepeatIteration()` when `initialControlTree eq currentControlTree`")

    withDebug("controls: adding iteration") {
      currentControlTree.createRepeatIterationTree(repeatControl, iterationIndex, collector)
    }
  }

  // Get the `ControlTree` computed in the `initialize()` method
  def getInitialControlTree: ControlTree = initialControlTree

  // Get the last computed `ControlTree`
  def getCurrentControlTree: ControlTree = currentControlTree

  // Clone the current controls tree if:
  //
  // 1. it hasn't yet been cloned
  // 2. we are not during the XForms engine initialization
  //
  // The rationale for #2 is that there is no controls comparison needed during initialization. Only during further
  // client requests do the controls need to be compared.
  //
  def cloneInitialStateIfNeeded(collector: ErrorEventCollector): Unit =
    if ((initialControlTree eq currentControlTree) && containingDocument.isHandleDifferences)
      withDebug("controls: cloning") {
        // NOTE: We clone "back", that is the new tree is used as the "initial" tree. This is done so that
        // if we started working with controls in the initial tree, we can keep using those references safely.
        initialControlTree = currentControlTree.getBackCopy(collector).asInstanceOf[ControlTree]
      }

  // For Java callers
  // 2018-01-05: 1 usage
  def getObjectByEffectiveId(effectiveId: String): XFormsControl =
    findObjectByEffectiveId(effectiveId).orNull

  def findObjectByEffectiveId(effectiveId: String): Option[XFormsControl] =
    currentControlTree.findControl(effectiveId)

  // Get the items for a given control id
  def getConstantItems(controlPrefixedId: String): Option[Itemset] =
      constantItems.get(controlPrefixedId)

  // Set the items for a given control id
  def setConstantItems(controlPrefixedId: String, itemset: Itemset): Unit =
    constantItems += controlPrefixedId -> itemset

  // 1 caller
  def doRefresh(): Unit = {

    if (! initialized) {
      debug("controls: skipping refresh as controls are not initialized")
      return
    }

    if (inRefresh) {
      // Ignore "nested refresh"
      // See https://github.com/orbeon/orbeon-forms/issues/1550
      debug("controls: attempt to do nested refresh")
      return
    }

    if (getCurrentControlTree.children.isEmpty) {
      debug("controls: not performing refresh because no controls are available")
      withRefresh(())
    } else {
      withDebug("controls: performing refresh") {

        // Focused control before updating bindings
        val focusedBeforeOpt = focusedControlOpt

        val resultOpt =
          EventCollector.withBufferCollector { collector =>
            withRefresh {

              // Update control bindings
              // NOTE: During this process, ideally, no events are dispatched. However, at this point, the code
              // can dispatch, upon removed repeat iterations, `xforms-disabled`, `DOMFocusOut` and possibly events
              // arising from updating the binding of nested XBL controls.
              // This unfortunately means that side effects can take place. This should be fixed, maybe by simply
              // detaching removed iterations first, and then dispatching events after all bindings have been
              // updated as part of `dispatchRefreshEvents()` below. This requires that controls are able to kind of
              // stay alive in detached mode, and then that the index is also available while these events are
              // dispatched.

              // `None` if bindings are clean
              for (updater <- updateControlBindings(collector))
                yield updater -> gatherControlsForRefresh

            }
          }

        resultOpt.foreach { case (updater, controlsEffectiveIds) =>

          EventCollector.withBufferCollector { collector =>
            currentControlTree.updateValueControls(controlsEffectiveIds, collector)
          }

          // Dispatch events
          currentControlTree.dispatchRefreshEvents(controlsEffectiveIds, isInitial = false, EventCollector.ToReview)

          // Handle focus changes
          Focus.updateFocusWithEvents(focusedBeforeOpt, updater.partialFocusRepeat)(containingDocument)

          // Dispatch to the root control
          getCurrentControlTree.rootOpt foreach { root =>
            Dispatch.dispatchEvent(new XXFormsRefreshDoneEvent(root), EventCollector.ToReview)
          }
        }

        // xxx if resultOpt.isEmpty && collector.events.nonEmpty
      }
    }
  }

  // Do a refresh of a subtree of controls starting at the given container control.
  // This is used by `xf:switch` and `xxf:dialog` as of 2021-04-14.
  def doPartialRefresh(containerControl: XFormsContainerControl, collector: ErrorEventCollector): Unit = {

    val focusedBeforeOpt = getFocusedControl

    // Update bindings starting at the container control
    val updater = updateSubtreeBindings(containerControl, collector)

    val controlIds = gatherControlsForRefresh(containerControl)
    currentControlTree.updateValueControls(controlIds, collector)
    currentControlTree.dispatchRefreshEvents(controlIds, isInitial = false, collector)

    Focus.updateFocusWithEvents(focusedBeforeOpt, updater.partialFocusRepeat)(containingDocument)
  }

  def getFocusedControl: Option[XFormsControl] =
    Private.focusedControlOpt

  def setFocusedControl(focusedControl: Option[XFormsControl]): Unit =
    Private.focusedControlOpt = focusedControl

  def eagerlyEvaluateProperties(collector: ErrorEventCollector): Unit =
    ControlsIterator(containingDocument.controls.getCurrentControlTree)
      .filter(_.isRelevant)
      .foreach {
        case control: XFormsSelect1Control =>
          control.getItemset(collector)
          control.getExternalValue(collector)
          control.getFormattedValue(collector)
          control.eagerlyEvaluateLhha(collector)
        case control: XFormsValueControl =>
          control.getExternalValue(collector)
          control.getFormattedValue(collector)
          control.eagerlyEvaluateLhha(collector)
        case control: ControlLHHASupport =>
          control.eagerlyEvaluateLhha(collector)
        case _ =>
      }

  private object Private {

    var initialized = false
    var initialControlTree = new ControlTree
    var currentControlTree = initialControlTree

    // Crude flag to indicate that something might have changed since the last request. This caches simples cases where
    // an incoming change on the document does not cause any change to the data or controls. In that case, the control
    // trees need not be compared. A general mechanism detecting mutations in the proper places would be better.
    var dirtySinceLastRequest = false

    // Whether we currently require a UI refresh
    var requireRefresh = false

    // Whether we are currently in a refresh
    var inRefresh = false

    var constantItems = Map[String, Itemset]()

    // Remember which control owns focus if any
    var focusedControlOpt: Option[XFormsControl] = None

    def markCleanSinceLastRequest(): Unit = {
      dirtySinceLastRequest = false
      currentControlTree.markBindingsClean()
    }

    // Update all the control bindings.
    //
    // Return `None` if control bindings are not dirty. Otherwise, control bindings are
    // updated and the `BindingUpdater` is returned.
    //
    def updateControlBindings(collector: ErrorEventCollector): Option[BindingUpdater] = {

      assert(initialized)

      currentControlTree.bindingsDirty option {

        // Clone if needed
        cloneInitialStateIfNeeded(collector)

        // Visit all controls and update their bindings
        val updater =
          withDebug("controls: updating bindings") {
            Controls.updateBindings(containingDocument, collector) |!>
              (updater => debugResults(updaterDebugResults(updater)))
          }

        // Controls are clean
        initialControlTree.markBindingsClean()
        currentControlTree.markBindingsClean()

        updater
      }
    }

    // Update the bindings of a container control and its descendants.
    // This is used by `xf:switch` and `xxf:dialog` as of 2021-04-14.
    def updateSubtreeBindings(containerControl: XFormsContainerControl, collector: ErrorEventCollector): BindingUpdater = {

      cloneInitialStateIfNeeded(collector)

      withDebug("controls: updating bindings", List("container" -> containerControl.effectiveId)) {
        Controls.updateBindings(containerControl, collector) |!>
          (updater => debugResults(updaterDebugResults(updater)))
      }
    }

    private def updaterDebugResults(updater: BindingUpdater) =
      List(
        "controls visited"   -> updater.visitedCount.toString,
        "bindings evaluated" -> updater.updatedCount.toString,
        "bindings optimized" -> updater.optimizedCount.toString
      )

    def gatherControlsForRefresh: List[String] =
      ControlsIterator(containingDocument.controls.getCurrentControlTree)
        .filter(XFormsControl.controlSupportsRefreshEvents)
        .map(_.effectiveId)
        .toList

    def gatherControlsForRefresh(containerControl: XFormsContainerControl): List[String] =
      ControlsIterator(containerControl, includeSelf = true)
        .filter(XFormsControl.controlSupportsRefreshEvents)
        .map(_.effectiveId)
        .toList
   }
}
