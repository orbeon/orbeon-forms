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

import org.orbeon.dom.QName
import org.orbeon.oxf.util.Logging._
import org.orbeon.oxf.util.{CollectionUtils, IndentedLogger}
import org.orbeon.oxf.xforms.analysis.ElementAnalysis
import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.xforms.XFormsNames._

import java.{util => ju}
import scala.collection.{mutable => m}
import scala.jdk.CollectionConverters._


private class ControlIndex {

  // Index of all controls in the tree by effective id
  // Order is desired so we iterate controls in the order they were added
  private val _effectiveIdsToControls = new ju.LinkedHashMap[String, XFormsControl]

  // HashMap[Type = String, LinkedHashMap[EffectiveId = String, Control = XFormsControl]]
  // No need for order here
  private val _controlTypes            = new ju.HashMap[QName, ju.LinkedHashMap[String, XFormsControl]]
  private val _sectionTemplateControls = new ju.LinkedHashMap[String, XFormsComponentControl]()

  private def collectSectionContentControl(control: XFormsControl): Option[XFormsComponentControl] = control match {
    case s: XFormsComponentControl
      if s.staticControl.element.getNamespaceURI.startsWith(Controls.SectionTemplateUriPrefix) => Some(s)
    case _                                                                                     => None
  }

  private def mustMapControl(control: XFormsControl): Boolean = control match {
    case _: XFormsUploadControl | _: XFormsRepeatControl | _: XXFormsDialogControl => true
    case s: XFormsSelectControl if s.isFullAppearance                              => true
    case _                                                                         => false
  }

  // Index a single controls
  def indexControl(control: XFormsControl): Unit = {
    // Remember by effective id
    _effectiveIdsToControls.put(control.effectiveId, control)
    // Also index children actions
    for (actionControl <- control.childrenActions)
      _effectiveIdsToControls.put(actionControl.effectiveId, actionControl)

    // Remember by control type (for certain controls we know we need)
    if (mustMapControl(control)) {
      val controlQName = control.staticControl.element.getQName
      var controlsMap = _controlTypes.get(controlQName)
      if (controlsMap eq null) {
        controlsMap = new ju.LinkedHashMap[String, XFormsControl] // need for order here!
        _controlTypes.put(controlQName, controlsMap)
      }
      controlsMap.put(control.effectiveId, control)
    } else {
      collectSectionContentControl(control) foreach { componentControl =>
        _sectionTemplateControls.put(componentControl.effectiveId, componentControl)
      }
    }
  }

  // Deindex a single control
  def deindexControl(control: XFormsControl): Unit = {

    // Remove by effective id
    _effectiveIdsToControls.remove(control.effectiveId)
    // Also remove children actions
    for (actionControl <- control.childrenActions)
      _effectiveIdsToControls.remove(actionControl.effectiveId)

    // Remove by control type (for certain controls we know we need)
    if (mustMapControl(control)) {
      val controlQName = control.staticControl.element.getQName
      val controlsMap = _controlTypes.get(controlQName)
      if (controlsMap ne null)
        controlsMap.remove(control.effectiveId)
    } else {
      collectSectionContentControl(control) foreach { componentControl =>
        _sectionTemplateControls.remove(componentControl.effectiveId)
      }
    }
  }

  def effectiveIdsToControls: collection.Map[String, XFormsControl] = _effectiveIdsToControls.asScala

  // WARNING: Caller must ensure consistency of `name` and `T`!
  def controlsOfName[T <: XFormsControl](name: QName): Iterable[T] = {
    val result = _controlTypes.get(name)
    if (result ne null) result.asScala.values.asInstanceOf[Iterable[T]] else Nil
  }

  def sectionControls: ju.Collection[XFormsComponentControl] =
    _sectionTemplateControls.values()
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
  def initialize(
    containingDocument: XFormsContainingDocument,
    state             : Option[Map[String, ControlState]],
    collector         : ErrorEventCollector
  ): Unit =
    withDebug("building controls") {
      // Visit the static tree of controls to create the actual tree of controls
      Controls.createTree(containingDocument, _controlIndex, state, collector)
      // Evaluate all controls
      // Dispatch initialization events for all controls created in index
      val allControls = _controlIndex.effectiveIdsToControls.values
      if (state.isEmpty) {
        // Copy list because it can be modified concurrently as events are being dispatched and handled
        val controlIds = _controlIndex.effectiveIdsToControls.keysIterator.toList
        updateValueControls(controlIds, collector)
        dispatchRefreshEvents(controlIds, isInitial = true, collector)
      } else {
        // Make sure all control state such as relevance, value changed, etc. does not mark a difference
        for (control <- allControls) {
          updateValueControl(control, collector)
          control.commitCurrentUIState()
        }
      }

      debugResults(List("controls created" -> allControls.size.toString))
    }

  def updateValueControls(controlsEffectiveIds: List[String], collector: ErrorEventCollector): Unit =
    controlsEffectiveIds.iterator.flatMap(findControl).foreach(updateValueControl(_, collector))

  private def updateValueControl(control: XFormsControl, collector: ErrorEventCollector): Unit =
    control match {
      case vc: XFormsValueControl if vc.isRelevant => vc.getValue(collector)
      case _ =>
    }

  def dispatchRefreshEvents(controlsEffectiveIds: List[String], isInitial: Boolean, collector: ErrorEventCollector): Unit =
    withDebug("dispatching refresh events") {

      def doDispatchRefreshEvents(control: XFormsControl): Unit =
        if (XFormsControl.controlSupportsRefreshEvents(control)) {
          val oldRelevantState = control.wasRelevantCommit()
          val newRelevantState = control.isRelevant
          if (newRelevantState && ! oldRelevantState) {
            // Control has become relevant
            control.dispatchCreationEvents(collector)
          } else if (! newRelevantState && oldRelevantState) {
            // Control has become non-relevant
            control.dispatchDestructionEvents(collector)
          } else if (newRelevantState) {
            // Control was and is relevant
            control.dispatchChangeEvents(collector)
          } else if (isInitial) {
            // Initialization and control is non-relevant
            // 2018-12-20: Since we don't actually have a need for this, we are not dispatching this
            // event at this time. If the need arises we can uncomment the code below. See #3829.
            //Dispatch.dispatchEvent(new XXFormsInitiallyDisabledEvent(control))
          }
        }

      for (controlEffectiveId <- controlsEffectiveIds)
        findControl(controlEffectiveId).foreach(doDispatchRefreshEvents)
    }

  def dispatchDestructionEventsForRemovedRepeatIteration(
    removedControl: XFormsContainerControl,
    includeCurrent: Boolean,
    collector     : ErrorEventCollector
  ): Unit =
    withDebug("dispatching destruction events") {
      // Gather ids of controls to handle

      val controlsEffectiveIds = m.ListBuffer[String]()

      // NOTE: In order to replace this with `ControlsIterator` we would need an option to `ControlsIterator` to
      // report container controls after their children.
      Controls.visitControls(
        removedControl,
        new Controls.XFormsControlVisitorListener {

          def startVisitControl(control: XFormsControl, collector: ErrorEventCollector): Boolean = {
            // Don't handle container controls here
            if (! control.isInstanceOf[XFormsContainerControl])
              controlsEffectiveIds += control.effectiveId

            true
          }

          def endVisitControl(control: XFormsControl): Unit = {
            // Add container control after all its children have been added
            if (control.isInstanceOf[XFormsContainerControl])
              controlsEffectiveIds += control.effectiveId
          }
        },
        includeCurrent,
        collector
      )
      // Dispatch events
      for {
        effectiveId <- controlsEffectiveIds
        control     <- effectiveIdsToControls.get(effectiveId)
        if XFormsControl.controlSupportsRefreshEvents(control)
      } locally {
        // Directly call destruction events as we know the iteration is going away
        control.dispatchDestructionEvents(collector)
      }
      debugResults(List("controls" -> controlsEffectiveIds.size.toString))
    }

  def getBackCopy(collector: ErrorEventCollector): Any = {
    // Clone this
    val cloned = super.clone.asInstanceOf[ControlTree]

    _root match {
      case Some(root) =>
        // Gather repeat indexes if any
        // Do this before cloning controls so that initial/current locals are still different
        cloned._initialRepeatIndexes = XFormsRepeatControl.initialIndexes(root.containingDocument)
        // Clone children if any
        cloned._root = Some(root.getBackCopy(collector).asInstanceOf[XFormsContainerControl])
      case None =>
    }
    // NOTE: The cloned tree does not make use of this so we clear it
    cloned._controlIndex = null
    cloned.bindingsDirty = false

    cloned
  }

  // Create a new repeat iteration for insertion into the current tree of controls
  def createRepeatIterationTree(
    repeatControl : XFormsRepeatControl,
    iterationIndex: Int,                 // new iteration to repeat (1..repeat size + 1)
    collector     : ErrorEventCollector
  ): XFormsRepeatIterationControl =
    // NOTE: We used to create a separate index, but this caused this bug:
    // [ #316177 ] When new repeat iteration is created upon repeat update, controls are not immediately accessible by id
    Controls.createRepeatIterationTree(_controlIndex, repeatControl, iterationIndex, collector)

  def createAndInitializeDynamicSubTree(
    container        : XBLContainer,
    containerControl : XFormsContainerControl,
    elementAnalysis  : ElementAnalysis,
    state            : Option[Map[String, ControlState]],
    collector        : ErrorEventCollector
  ): Unit =
    Controls.createSubTree(container, _controlIndex, containerControl, elementAnalysis, state, collector)

  // Index a subtree of controls. Also handle special relevance binding events
  def indexSubtree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    ControlsIterator(containerControl, includeCurrent) foreach _controlIndex.indexControl

  // Deindex a subtree of controls. Also handle special relevance binding events
  def deindexSubtree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    ControlsIterator(containerControl, includeCurrent) foreach _controlIndex.deindexControl

  def destroySubtree(containerControl: XFormsContainerControl, includeCurrent: Boolean): Unit =
    ControlsIterator(containerControl, includeCurrent) filter (_.isRelevant) foreach
      (_.onDestroy(update = true))

  def children: Seq[XFormsControl] = _root match {
    case Some(root) => root.children
    case None       => Nil
  }

  def effectiveIdsToControls: collection.Map[String, XFormsControl] = _controlIndex.effectiveIdsToControls
  def findControl(effectiveId: String): Option[XFormsControl] = effectiveIdsToControls.get(effectiveId)

  def findRepeatControl(effectiveId: String): Option[XFormsRepeatControl] =
    findControl(effectiveId) flatMap CollectionUtils.collectByErasedType[XFormsRepeatControl]

  def getUploadControls          : Iterable[XFormsUploadControl]    = _controlIndex.controlsOfName[XFormsUploadControl](XFORMS_UPLOAD_QNAME)
  def getDialogControls          : Iterable[XXFormsDialogControl]   = _controlIndex.controlsOfName[XXFormsDialogControl](XXFORMS_DIALOG_QNAME)
  def getSectionTemplateControls : Iterable[XFormsComponentControl] = _controlIndex.sectionControls.asScala
}