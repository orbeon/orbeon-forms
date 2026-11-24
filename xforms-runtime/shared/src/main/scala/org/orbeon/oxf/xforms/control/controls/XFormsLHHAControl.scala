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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.analysis.controls.LHHA.Alert
import org.orbeon.oxf.xforms.analysis.controls.{LHHA, LHHAAnalysis, ValueControl}
import org.orbeon.oxf.xforms.control.*
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.state.ControlState
import org.orbeon.oxf.xforms.xbl.XBLContainer
import org.orbeon.oxf.xforms.{BindingContext, XFormsContextStack, XFormsContextStackSupport}
import org.orbeon.oxf.xml.SaxSupport.*
import org.orbeon.xforms.analysis.model.ValidationLevel
import org.xml.sax.helpers.AttributesImpl
import shapeless.syntax.typeable.typeableOps


//
// Special "control" which represents an LHHA value. This is used only when the LHHA element is not
// local, that is that it has a `for` attribute.
//
// A side effect of this being an `XFormsValueControl` is that it will dispatch value change events, etc.
// This should probably be changed?
//
class XFormsLHHAControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  _effectiveId: String
) extends XFormsSingleNodeControl(container, parent, _effectiveId)
  with XFormsValueControl
  with NoLHHATrait {

  selfControl =>

  override type Control <: LHHAAnalysis & ValueControl // Q: How can this compile? We have a class and an abstract class!

  override def storeExternalValue(externalValue: String, collector: ErrorEventCollector): Unit =
    throw new OXFException("operation not allowed")

  // Allow the client telling us that an external LHHA has the focus, for instance in the case of an `<xf:help>`
  // rendered as a `<button>` in the headings of a repeated grid.
  override def isDirectlyFocusableMaybeWithToggle: Boolean = true

  // https://github.com/orbeon/orbeon-forms/issues/6645
  private var _parentBindingContext: BindingContext = null

  // Set as a side effect of evaluating bindings/values.
  // This is not great, but we need to store this instead of computing it in `addAjaxAttributes()`, as cannot resolve
  // controls in the copy of the tree.
  private var _associatedControlsLevel   : Option[ValidationLevel] = None
  private var _associatedControlsVisited : Boolean                 = false
  private var _associatedControlsReadonly: Boolean                 = false
  private var _associatedControlsRequired: Boolean                 = false
  private var _associatedControlsEmpty   : Option[Boolean]         = None

  def associatedControlsLevel   : Option[ValidationLevel] = _associatedControlsLevel
  def associatedControlsVisited : Boolean                 = _associatedControlsVisited
  def associatedControlsReadonly: Boolean                 = _associatedControlsReadonly
  def associatedControlsRequired: Boolean                 = _associatedControlsRequired
  def associatedControlsEmpty   : Option[Boolean]         = _associatedControlsEmpty

  private def updateAssociatedControlsState(collector: ErrorEventCollector): Unit = {
    if (staticControl.lhhaType == LHHA.Alert) {
      val associated = associatedControls
      _associatedControlsLevel   = associated.flatMap(_.alertLevel).maxOption
      _associatedControlsVisited = associated.exists(_.visited)
    } else if (staticControl.lhhaType == LHHA.Label && ! staticControl.isForRepeat) {
      val associated = associatedControls.filter(_.isRelevant)
      val required   = associated.exists(_.isRequired)
      _associatedControlsReadonly = associated.nonEmpty && associated.forall(_.isReadonly)
      _associatedControlsRequired = required
      _associatedControlsEmpty    =
        if (required)
          associated.collectFirst { case vc: XFormsValueControl => vc.isEmptyValue(collector) }
        else
          None
    }
  }

  final override def evaluateBindingAndValues(
    parentContext : BindingContext,
    update        : Boolean,
    restoreState  : Boolean,
    state         : Option[ControlState],
    collector     : ErrorEventCollector
  ): Unit = {
    _parentBindingContext = parentContext
    super.evaluateBindingAndValues(parentContext, update, restoreState, state, collector)
  }

  override def onCreate(
    restoreState: Boolean,
    state       : Option[ControlState],
    update      : Boolean,
    collector   : ErrorEventCollector
  ): Unit = {
    super.onCreate(restoreState, state, update, collector)
    updateAssociatedControlsState(collector)
  }

  override def onBindingUpdate(
    oldBinding: BindingContext,
    newBinding: BindingContext,
    collector : ErrorEventCollector
  ): Unit = {
    super.onBindingUpdate(oldBinding, newBinding, collector)
    updateAssociatedControlsState(collector)
  }

  override def onDestroy(update: Boolean): Unit = {
    super.onDestroy(update)
    _associatedControlsLevel    = None
    _associatedControlsVisited  = false
    _associatedControlsReadonly = false
    _associatedControlsRequired = false
    _associatedControlsEmpty    = None
  }

  final override def refreshBindingAndValues(
    parentContext: BindingContext,
    collector    : ErrorEventCollector
  ): Unit = {
    _parentBindingContext = parentContext
    super.refreshBindingAndValues(parentContext, collector)
  }

  final def reevaluateForAssociatedControl(collector: ErrorEventCollector): Unit =
    super.refreshBindingAndValues(_parentBindingContext, collector)

  override def bindingContextForFollowing: BindingContext = _parentBindingContext

  private def associatedControls: List[XFormsSingleNodeControl] =
    Controls.resolveControlsById(
      containingDocument,
      effectiveId,
      staticControl.lhhaPlacementType.directTargetControl.staticId,
      followIndexes = false
    )
    .flatMap(_.cast[XFormsSingleNodeControl])

  private def isActive: Boolean =
    staticControl.lhhaType != LHHA.Alert || {
      associatedControls.exists { associatedControl =>
        associatedControl.alertsForValidation(staticControl.forValidationId, staticControl.forLevels).nonEmpty
      }
    }

  override def computeRelevant: Boolean =
    super.computeRelevant && (associatedControls.isEmpty || associatedControls.exists(_.isRelevant)) && isActive

  // Special evaluation function, as in the case of LHHA, the nested content of the element is a way to evaluate
  // the value.
  override def computeValue(collector: ErrorEventCollector): String = {

    val resultOpt = {

      implicit val contextStack: XFormsContextStack = selfControl.getContextStack

      // NOTE: The `expr` is computed using the `ref`, `bind`, or `value` attributes on the element
      // itself if present. So we must evaluate the value in the context that excludes the binding.
      // TODO: This is not efficient, since the control's binding is first evaluated during refresh,
      //   and then it is re-evaluated if there is a `ref` or `bind`.
      if (staticControl.hasBinding)
        contextStack.setBinding(selfControl.bindingContext.parent)
      else
        contextStack.setBinding(selfControl.bindingContext)

      XFormsContextStackSupport.evaluateExpressionOrConstant(
        childElem           = staticControl,
        parentEffectiveId   = selfControl.effectiveId,
        pushContextAndModel = staticControl.hasBinding,
        eventTarget         = selfControl,
        collector           = collector
      )
    }

    updateAssociatedControlsState(collector)

    resultOpt getOrElse ""
  }

  protected override def getRelevantEscapedExternalValue(collector: ErrorEventCollector): String =
    if (mediatype contains "text/html")
      XFormsControl.getEscapedHTMLValue(getExternalValue(collector))
    else
      getExternalValue(collector)

  override def supportAjaxUpdates: Boolean = ! staticControl.isPlaceholder

  override def addAjaxAttributes(
    attributesImpl    : AttributesImpl,
    previousControlOpt: Option[XFormsControl],
    collector         : ErrorEventCollector
  ): Boolean = {

    val control1Opt = previousControlOpt.asInstanceOf[Option[XFormsLHHAControl]] // FIXME
    val control2    = this

    var added = super.addAjaxAttributes(attributesImpl, previousControlOpt, collector)

    val alertLevels1 = control1Opt.flatMap(_._associatedControlsLevel)
    val alertLevels2 = control2._associatedControlsLevel

    if (alertLevels1.isDefined != alertLevels2.isDefined) {
      alertLevels1.foreach(_ => attributesImpl.appendToClassAttribute(s"-xforms-active"))
      alertLevels2.foreach(_ => attributesImpl.appendToClassAttribute(s"+xforms-active"))
      added |= true
    }

    if (alertLevels1 != alertLevels2) {
      alertLevels1.foreach(l => attributesImpl.appendToClassAttribute(s"-xforms-${l.entryName}"))
      alertLevels2.foreach(l => attributesImpl.appendToClassAttribute(s"+xforms-${l.entryName}"))
      added |= true
    }

    val visited1 = control1Opt.exists(_._associatedControlsVisited)
    val visited2 = control2._associatedControlsVisited

    // Visited
    if (visited1 != visited2) {
      attributesImpl.addOrReplace("visited", visited2.toString)
      added |= true
    }

    val readonly1 = control1Opt.exists(_._associatedControlsReadonly)
    val readonly2 = control2._associatedControlsReadonly

    if (readonly1 != readonly2) {
      attributesImpl.appendToClassAttribute(if (readonly2) "+xforms-readonly" else "-xforms-readonly")
      added |= true
    }

    val required1 = control1Opt.exists(_._associatedControlsRequired)
    val required2 = control2._associatedControlsRequired

    if (required1 != required2) {
      attributesImpl.appendToClassAttribute(if (required2) "+xforms-required" else "-xforms-required")
      added |= true
    }

    val empty1 = control1Opt.flatMap(_._associatedControlsEmpty)
    val empty2 = control2._associatedControlsEmpty

    if (empty1 != empty2) {
      empty1.foreach(e => attributesImpl.appendToClassAttribute(if (e) "-xforms-empty" else "-xforms-filled"))
      empty2.foreach(e => attributesImpl.appendToClassAttribute(if (e) "+xforms-empty" else "+xforms-filled"))
      added |= true
    }

    added
  }
}
