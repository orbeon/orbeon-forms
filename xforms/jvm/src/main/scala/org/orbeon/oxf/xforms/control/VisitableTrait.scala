/**
 *  Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.Controls.AncestorOrSelfIterator
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.event.{Dispatch, XFormsEvent}
import org.orbeon.oxf.xforms.model.NoDefaultsStrategy
import org.orbeon.oxf.xforms.state.ControlState

trait VisitableTrait extends XFormsControl {

  // Whether the control has been visited
  private[this] var _visited = false

  // Previous values for refresh
  private[this] var _wasVisited = false

  override def visited: Boolean = _visited

  def visited_=(visited: Boolean): Unit =
    if (visited != _visited && ! (visited && isStaticReadonly)) {
      // This mutation requires a clone. We could use XFormsControlLocal but that is more complex. In addition, most
      // non-trivial forms e.g. Form Runner will require a clone anyway as other stuff takes place upon focus out,
      // such as updating the error summary. What should be implemented is a better diff mechanism, for example lazy
      // copy of control properties upon mutation, rather than the current XFormsControlLocal/full clone alternative.
      containingDocument.getControls.cloneInitialStateIfNeeded()

      // There is no dependency handling with the xxf:visited() function. So instead of requiring callers to do this,
      // as was the case at some point, we require an RR.
      bindingContext.modelOpt foreach
        (_.deferredActionContext.markRecalculateRevalidate(NoDefaultsStrategy, None))

      containingDocument.requireRefresh()

      _visited = visited
    }

  def visitWithAncestors(): Unit = {

    visited = true

    // See https://github.com/orbeon/orbeon-forms/issues/3508
    new AncestorOrSelfIterator(this) foreach {
      case v: XFormsValueComponentControl => v.visited = true
      case _ =>
    }
  }

  override def onCreate(restoreState: Boolean, state: Option[ControlState], update: Boolean): Unit = {
    super.onCreate(restoreState, state, update)
    _visited = state match {
      case Some(state) => state.visited
      case None        => false
    }
  }

  final def wasVisitedCommit(): Boolean = {
    val result = _wasVisited
    _wasVisited = _visited
    result
  }

  override def commitCurrentUIState(): Unit = {
    super.commitCurrentUIState()
    wasVisitedCommit()
  }

  override def performTargetAction(event: XFormsEvent): Unit = {
    event match {
      case _: DOMFocusOutEvent =>
        // Mark control visited upon `DOMFocusOut`. This applies to any control, including grouping controls. We
        // do this upon the event reaching the target, so that by the time a regular event listener makes use of the
        // visited property, it is up to date. This seems reasonable since `DOMFocusOut` indicates that the focus has
        // already left the control.
        // See https://github.com/orbeon/orbeon-forms/issues/3508 and https://github.com/orbeon/orbeon-forms/issues/3611
        visited = true
      case _: XXFormsBlurEvent =>
        // The client dispatches `xxforms-blur` when focus goes away from all XForms controls.
        if (containingDocument.getControls.getFocusedControl exists (_ eq this)) {
          // See https://github.com/orbeon/orbeon-forms/issues/3508 and https://github.com/orbeon/orbeon-forms/issues/3611
          visited = true
          Focus.removeFocus(containingDocument)
        }
      case _ =>
    }
    super.performTargetAction(event)
  }

  // Compare this control with another control, as far as the comparison is relevant for the external world.
  override def compareExternalUseExternalValue(
    previousExternalValue : Option[String],
    previousControlOpt    : Option[XFormsControl]
  ): Boolean =
    previousControlOpt match {
      case Some(other: VisitableTrait) =>
        visited == other.visited &&
        super.compareExternalUseExternalValue(previousExternalValue, previousControlOpt)
      case _ => false
    }

  // Dispatch change events (between the control becoming enabled and disabled)
  override def dispatchChangeEvents(): Unit = {
    // Gather change first for consistency with XFormsSingleNodeControl
    val visitedChanged = wasVisitedCommit() != visited

    // Dispatch other events
    super.dispatchChangeEvents()

    // Dispatch our events
    if (visitedChanged)
      Dispatch.dispatchEvent(if (visited) new XXFormsVisitedEvent(this) else new XXFormsUnvisitedEvent(this))
  }
}
