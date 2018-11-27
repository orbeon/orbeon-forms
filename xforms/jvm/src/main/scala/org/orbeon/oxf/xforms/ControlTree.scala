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

import java.{lang ⇒ jl, util ⇒ ju}

import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{CollectionUtils, IndentedLogger}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.control.{Controls, XFormsContainerControl, XFormsControl}
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer

import scala.collection.JavaConverters._
import scala.collection.{mutable ⇒ m}

private class ControlIndex {

  // Index of all controls in the tree by effective id
  // Order is desired so we iterate controls in the order they were added
  private val _effectiveIdsToControls = new ju.LinkedHashMap[String, XFormsControl]

  // HashMap[Type = String, LinkedHashMap[EffectiveId = String, Control = XFormsControl]]
  // No need for order here
  private val _controlTypes = new ju.HashMap[String, ju.LinkedHashMap[String, XFormsControl]]

  private def mustMapControl(control: XFormsControl): Boolean = control match {
    case _: XFormsUploadControl | _: XFormsRepeatControl | _: XXFormsDialogControl ⇒ true
    case s: XFormsSelectControl if s.isFullAppearance                              ⇒ true
    case _                                                                         ⇒ false
  }

  // Index a single controls
  def indexControl(control: XFormsControl): Unit = {
    // Remember by effective id
    _effectiveIdsToControls.put(control.getEffectiveId, control)
    // Also index children actions
    for (actionControl ← control.childrenActions)
      _effectiveIdsToControls.put(actionControl.getEffectiveId, actionControl)

    // Remember by control type (for certain controls we know we need)
    if (mustMapControl(control)) {
      var controlsMap = _controlTypes.get(control.localName)
      if (controlsMap eq null) {
        controlsMap = new ju.LinkedHashMap[String, XFormsControl] // need for order here!
        _controlTypes.put(control.localName, controlsMap)
      }
      controlsMap.put(control.getEffectiveId, control)
    }
  }

  // Deindex a single control
  def deindexControl(control: XFormsControl): Unit = {

    // Remove by effective id
    _effectiveIdsToControls.remove(control.getEffectiveId)
    // Also remove children actions
    for (actionControl ← control.childrenActions)
      _effectiveIdsToControls.remove(actionControl.getEffectiveId)

    // Remove by control type (for certain controls we know we need)
    if (mustMapControl(control)) {
      val controlsMap = _controlTypes.get(control.localName)
      if (controlsMap ne null)
        controlsMap.remove(control.getEffectiveId)
    }
  }

  def effectiveIdsToControls: collection.Map[String, XFormsControl] = _effectiveIdsToControls.asScala

  // WARNING: Caller must ensure consistency of `name` and `T`!
  def controlsOfName[T <: XFormsControl](name: String): Iterable[T] = {
    val result = _controlTypes.get(name)
    if (result ne null) result.asScala.values.asInstanceOf[Iterable[T]] else Nil
  }
}

// Represent a tree of XForms controls
class ControlTree(private implicit val indentedLogger: IndentedLogger) extends Cloneable {

  // Top-level controls
  private var _root: Option[XFormsContainerControl] = None

  def rootOpt: Option[XFormsContainerControl] = _root

  def setRoot(root: XFormsContainerControl): Unit =
    this._root = Some(root ensuring (_ ne null))

  // Index of controls
  private var _controlIndex = new ControlIndex

  // Repeat indexes for Ajax updates only
  private var _initialRepeatIndexes = m.LinkedHashMap.empty[String, Int]

  // Only for initial tree after getBackCopy has been called
  def initialRepeatIndexes: m.LinkedHashMap[String, Int] = _initialRepeatIndexes

  // Whether the bindings must be reevaluated
  var bindingsDirty = false

  def markBindingsClean(): Unit = bindingsDirty = false
  def markBindingsDirty(): Unit = bindingsDirty = true

  // Build the entire tree of controls and associated information.
  def initialize(containingDocument: XFormsContainingDocument, state: Option[Map[String, ControlState]]): Unit =
    withDebug("building controls") {
      // Visit the static tree of controls to create the actual tree of controls
      Controls.createTree(containingDocument, _controlIndex, state)
      // Evaluate all controls
      // Dispatch initialization events for all controls created in index
      val allControls = _controlIndex.effectiveIdsToControls.values
      if (state.isEmpty) {
        // Copy list because it can be modified concurrently as events are being dispatched and handled
        dispatchRefreshEvents(_controlIndex.effectiveIdsToControls.keysIterator.to[List])
      } else {
        // Make sure all control state such as relevance, value changed, etc. does not mark a difference
        for (control ← allControls)
          control.commitCurrentUIState()
      }

      debugResults(List("controls created" → allControls.size.toString))
    }

  def dispatchRefreshEvents(controlsEffectiveIds: Iterable[String]): Unit = {
    withDebug("dispatching refresh events") {

      def dispatchRefreshEvents(control: XFormsControl): Unit =
        if (XFormsControl.controlSupportsRefreshEvents(control)) {
          val oldRelevantState = control.wasRelevantCommit()
          val newRelevantState = control.isRelevant
          if (newRelevantState && ! oldRelevantState) {
            // Control has become relevant
            control.dispatchCreationEvents()
          } else if (! newRelevantState && oldRelevantState) {
            // Control has become non-relevant
            control.dispatchDestructionEvents()
          } else if (newRelevantState) {
            // Control was and is relevant
            control.dispatchChangeEvents()
          }
        }

      for (controlEffectiveId ← controlsEffectiveIds)
        findControl(controlEffectiveId) foreach dispatchRefreshEvents
    }
  }

  def dispatchDestructionEventsForRemovedRepeatIteration(
    removedControl: XFormsContainerControl,
    includeCurrent: Boolean
  ): Unit =
    withDebug("dispatching destruction events") {
      // Gather ids of controls to handle

      val controlsEffectiveIds = m.ListBuffer[String]()

      // NOTE: In order to replace this with `ControlsIterator` we would need an option to `ControlsIterator` to
      // report container controls after their children.
      Controls.visitControls(
        removedControl,
        new Controls.XFormsControlVisitorListener {

          def startVisitControl(control: XFormsControl): Boolean = {
            // Don't handle container controls here
            if (! control.isInstanceOf[XFormsContainerControl])
              controlsEffectiveIds += control.getEffectiveId

            true
          }

          def endVisitControl(control: XFormsControl): Unit = {
            // Add container control after all its children have been added
            if (control.isInstanceOf[XFormsContainerControl])
              controlsEffectiveIds += control.getEffectiveId
          }
        },
        includeCurrent
      )
      // Dispatch events
      for {
        effectiveId ← controlsEffectiveIds
        control     ← effectiveIdsToControls.get(effectiveId)
        if XFormsControl.controlSupportsRefreshEvents(control)
      } locally {
        // Directly call destruction events as we know the iteration is going away
        control.dispatchDestructionEvents()
      }
      debugResults(List("controls" → controlsEffectiveIds.size.toString))
    }

  def getBackCopy: Any = {
    // Clone this
    val cloned = super.clone.asInstanceOf[ControlTree]

    _root match {
      case Some(root) ⇒
        // Gather repeat indexes if any
        // Do this before cloning controls so that initial/current locals are still different
        cloned._initialRepeatIndexes = XFormsRepeatControl.initialIndexes(root.containingDocument)
        // Clone children if any
        cloned._root = Some(root.getBackCopy.asInstanceOf[XFormsContainerControl])
      case None ⇒
    }
    // NOTE: The cloned tree does not make use of this so we clear it
    cloned._controlIndex = null
    cloned.bindingsDirty = false

    cloned
  }

  // Create a new repeat iteration for insertion into the current tree of controls
  def createRepeatIterationTree(
    containingDocument : XFormsContainingDocument,
    repeatControl      : XFormsRepeatControl,
    iterationIndex     : Int                       // new iteration to repeat (1..repeat size + 1)
  ): XFormsRepeatIterationControl = {
    // NOTE: We used to create a separate index, but this caused this bug:
    // [ #316177 ] When new repeat iteration is created upon repeat update, controls are not immediately accessible by id
    Controls.createRepeatIterationTree(containingDocument, _controlIndex, repeatControl, iterationIndex)
  }

  def initializeSubTree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    dispatchRefreshEvents(ControlsIterator(containerControl, includeCurrent).map(_.getEffectiveId).to[List])

  def createAndInitializeDynamicSubTree(
    container        : XBLContainer,
    containerControl : XFormsContainerControl,
    elementAnalysis  : ElementAnalysis,
    state            : Option[Map[String, ControlState]],
    dispatchEvents   : Boolean
  ): Unit = {

    Controls.createSubTree(container, _controlIndex, containerControl, elementAnalysis, state)

    if (dispatchEvents) {
      // NOTE: We dispatch refresh events for the subtree right away, by consistency with repeat iterations. But we
      // don't really have to do this, we could wait for the following refresh.
      initializeSubTree(containerControl, includeCurrent = false)
    }
  }

  // Index a subtree of controls. Also handle special relevance binding events
  def indexSubtree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    ControlsIterator(containerControl, includeCurrent) foreach _controlIndex.indexControl

  // Deindex a subtree of controls. Also handle special relevance binding events
  def deindexSubtree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    ControlsIterator(containerControl, includeCurrent) foreach _controlIndex.deindexControl

  def children: Seq[XFormsControl] = _root match {
    case Some(root) ⇒ root.children
    case None       ⇒ Nil
  }

  def effectiveIdsToControls: collection.Map[String, XFormsControl] = _controlIndex.effectiveIdsToControls
  def findControl(effectiveId: String): Option[XFormsControl] = effectiveIdsToControls.get(effectiveId)

  def findRepeatControl(effectiveId: String): Option[XFormsRepeatControl] =
    findControl(effectiveId) flatMap CollectionUtils.collectByErasedType[XFormsRepeatControl]

  def getUploadControlsJava : jl.Iterable[XFormsUploadControl] = getUploadControls.asJava
  def getUploadControls     : Iterable[XFormsUploadControl]    = _controlIndex.controlsOfName[XFormsUploadControl](UPLOAD_NAME)
  def getRepeatControls     : Iterable[XFormsRepeatControl]    = _controlIndex.controlsOfName[XFormsRepeatControl](REPEAT_NAME)
  def getDialogControls     : Iterable[XXFormsDialogControl]   = _controlIndex.controlsOfName[XXFormsDialogControl](XXFORMS_DIALOG_NAME)
}