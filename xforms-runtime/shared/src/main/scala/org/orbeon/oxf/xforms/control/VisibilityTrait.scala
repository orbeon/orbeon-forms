/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.control

import org.orbeon.oxf.xforms.BindingContext
import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.event.Dispatch
import org.orbeon.oxf.xforms.event.EventCollector.ErrorEventCollector
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.state.ControlState

// https://github.com/orbeon/orbeon-forms/issues/3494
trait VisibilityTrait extends XFormsControl {

  self =>

  private var _visible    = false
  private var _wasVisible = false

  def locallyVisible: Boolean = true

  def visible: Boolean = _visible

  private def computeVisible: Boolean = {
    val ancestorVisibleOpt =
      new AncestorOrSelfIterator(parent) collectFirst {
        case v: VisibilityTrait => v.visible
      }

    locallyVisible && ! (ancestorVisibleOpt contains false)
  }

  override def onCreate(
    restoreState: Boolean,
    state       : Option[ControlState],
    update      : Boolean,
    collector   : ErrorEventCollector
  ): Unit = {
    super.onCreate(restoreState, state, update, collector)
    _visible = computeVisible
  }

  override def onBindingUpdate(
    oldBinding: BindingContext,
    newBinding: BindingContext,
    collector : ErrorEventCollector
  ): Unit = {
    super.onBindingUpdate(oldBinding, newBinding, collector)
    _visible = computeVisible
  }

  final def wasVisibleCommit(): Boolean = {
    val result = _wasVisible
    _wasVisible = _visible
    result
  }

  override def commitCurrentUIState(): Unit = {
    super.commitCurrentUIState()
    wasVisibleCommit()
  }

  override def dispatchCreationEvents(collector: ErrorEventCollector): Unit = {
    super.dispatchCreationEvents(collector)
    if (visible)
      Dispatch.dispatchEvent(new XXFormsVisibleEvent(this), collector)
  }

  override def dispatchChangeEvents(collector: ErrorEventCollector): Unit = {
    // Gather change first for consistency with XFormsSingleNodeControl
    val visibleChanged = wasVisibleCommit() != visible

    // Dispatch other events
    super.dispatchChangeEvents(collector)

    // Dispatch our events
    if (visibleChanged)
      Dispatch.dispatchEvent(if (visible) new XXFormsVisibleEvent(self) else new XXFormsHiddenEvent(self), collector)
  }

  override def dispatchDestructionEvents(collector: ErrorEventCollector): Unit = {
    if (visible)
      Dispatch.dispatchEvent(new XXFormsHiddenEvent(this), collector)
    super.dispatchDestructionEvents(collector)
  }
}
