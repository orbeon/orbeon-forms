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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.xforms.action.{DynamicActionContext, XFormsAPI, XFormsAction}
import org.orbeon.oxf.xforms.control.Focus
import org.orbeon.oxf.xforms.control.controls.XFormsCaseControl
import org.orbeon.xforms.XFormsNames.XXFORMS_TOGGLE_ANCESTORS_QNAME


/**
 * 9.2.3 The toggle Element
 */
class XFormsToggleAction extends XFormsAction {
  override def execute(actionContext: DynamicActionContext)(implicit logger: IndentedLogger): Unit = {

    val interpreter = actionContext.interpreter

    // Find case control
    resolveControlAvt("case")(actionContext) match {
      case Some(caseControl: XFormsCaseControl) =>
        // Perform the actual toggle action
        XFormsToggleAction.toggle(
          caseControl                  = caseControl,
          mustHonorDeferredUpdateFlags = interpreter.mustHonorDeferredUpdateFlags(actionContext.analysis),
          mustToggleAncestors          = ! actionContext.analysis.element.attributeValueOpt(XXFORMS_TOGGLE_ANCESTORS_QNAME).contains("false")
        )
      case _ =>
        // "If there is a null search result for the target object and the source object is an XForms action such as
        // dispatch, send, setfocus, setindex or toggle, then the action is terminated with no effect."
        debug(
          "xf:toggle: case does not refer to an existing xf:case element, ignoring action",
          List("case id" -> actionContext.element.attributeValue("case"))
        )
    }
  }
}

object XFormsToggleAction {

  def toggle(
    caseControl                  : XFormsCaseControl,
    mustHonorDeferredUpdateFlags : Boolean = true,
    mustToggleAncestors          : Boolean = true
  ): Unit = {

    val doc = XFormsAPI.inScopeContainingDocument

    // "This XForms Action begins by invoking the deferred update behavior."
    if (mustHonorDeferredUpdateFlags)
      doc.synchronizeAndRefresh()

    // NOTE: The logic below doesn't handle showing ancestor `xxf:xforms11-switch="true"` cases yet.
    if (caseControl.getSwitch.isRelevant) { // the `xf:switch` and consequently all ancestors are relevant

      val ancestorOrSelfHiddenCasesIt = Focus.ancestorOrSelfHiddenCases(caseControl)
      val focusedBeforeOpt            = ancestorOrSelfHiddenCasesIt.nonEmpty option doc.controls.getFocusedControl

      // Q: Should we instead toggle from root to leaf?
      val casesToToggle =
        if (mustToggleAncestors)
          ancestorOrSelfHiddenCasesIt
        else
          ! caseControl.isCaseVisible iterator caseControl

      casesToToggle foreach (_.toggle())

      // Update the focus only if at least one `xf:case` requires toggling
      focusedBeforeOpt foreach
        (Focus.updateFocusWithEvents(_, None)(doc))
    }
  }
}