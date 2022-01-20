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

import org.orbeon.dom.Element
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.{XFormsContextStack, XFormsContextStackSupport}
import org.orbeon.oxf.xforms.analysis.controls.{LHHAAnalysis, ValueControl}
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.xbl.XBLContainer


//
// Special "control" which represents an LHHA value. This is used only when the LHHA element is not
// local, that is that it has a `for` attribute.
//
// A side-effect of this being an `XFormsValueControl` is that it will dispatch value change events, etc.
// This should probably be changed?
//
class XFormsLHHAControl(
  container   : XBLContainer,
  parent      : XFormsControl,
  element     : Element,
  effectiveId : String
) extends XFormsSingleNodeControl(container, parent, element, effectiveId)
  with XFormsValueControl
  with NoLHHATrait {

  selfControl =>

  override type Control <: LHHAAnalysis with ValueControl // Q: How can this compile? We have a class and an abstract class!

  override def storeExternalValue(externalValue: String): Unit =
    throw new OXFException("operation not allowed")

  // Allow the client telling us that an external LHHA has the focus, for instance in the case of an `<xf:help>`
  // rendered as a `<button>` in the headings of a repeated grid.
  override def isDirectlyFocusableMaybeWithToggle: Boolean = true

  // Special evaluation function, as in the case of LHHA, the nested content of the element is a way to evaluate
  // the value.
  override def computeValue: String = {

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
        pushContextAndModel = staticControl.hasBinding
      )
    }

    resultOpt getOrElse ""
  }

  override def getRelevantEscapedExternalValue: String =
    if (mediatype contains "text/html")
      XFormsControl.getEscapedHTMLValue(getLocationData, getExternalValue)
    else
      getExternalValue
}