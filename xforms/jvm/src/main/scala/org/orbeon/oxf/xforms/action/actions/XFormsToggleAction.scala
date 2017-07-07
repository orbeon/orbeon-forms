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
package org.orbeon.oxf.xforms.action.actions

import org.orbeon.oxf.xforms.action.{XFormsAPI, DynamicActionContext, XFormsAction}
import org.orbeon.oxf.xforms.control.Focus
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl

/**
 * 9.2.3 The toggle Element
 */
class XFormsToggleAction extends XFormsAction {
  override def execute(actionContext: DynamicActionContext): Unit = {

    val interpreter        = actionContext.interpreter
    val actionElement      = actionContext.element

    // Find case control
    resolveControlAvt("case")(actionContext) match {
      case Some(caseControl: XFormsCaseControl) ⇒
        // Perform the actual toggle action
        val deferred = XFormsAPI.inScopeActionInterpreter.isDeferredUpdates(actionElement)
        XFormsToggleAction.toggle(caseControl, deferred)
      case _ ⇒
        // "If there is a null search result for the target object and the source object is an XForms action such as
        // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
        val indentedLogger = interpreter.indentedLogger
        if (indentedLogger.isDebugEnabled)
          indentedLogger.logDebug("xf:toggle", "case does not refer to an existing xf:case element, ignoring action", "case id", actionContext.element.attributeValue("case"))
    }
  }
}

object XFormsToggleAction {

  def toggle(caseControl: XFormsCaseControl, deferred: Boolean = true): Unit = {
    // "This XForms Action begins by invoking the deferred update behavior."
    if (deferred)
      XFormsAPI.inScopeContainingDocument.synchronizeAndRefresh()

    if (caseControl.parent.isRelevant && ! caseControl.isSelected) {
      // This case is in a relevant switch and not currently selected
      val focusedBefore = XFormsAPI.inScopeContainingDocument.getControls.getFocusedControl

      // Actually toggle the xf:case
      caseControl.toggle()

      // Handle focus changes
      Focus.updateFocusWithEvents(focusedBefore, None)
    }
  }
}