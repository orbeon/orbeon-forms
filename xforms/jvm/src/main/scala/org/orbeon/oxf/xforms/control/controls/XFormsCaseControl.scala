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
package org.orbeon.oxf.xforms.control.controls

import org.orbeon.dom.Element
import org.orbeon.oxf.xforms.analysis.ControlAnalysisFactory.CaseControl
import org.orbeon.oxf.xforms.control.{VisibilityTrait, XFormsControl, XFormsNoSingleNodeContainerControl}
import org.orbeon.oxf.xforms.xbl.XBLContainer

/**
 * Represents an xf:case pseudo-control.
 *
 * NOTE: This doesn't keep the "currently selected flag". Instead, the parent xf:switch holds this information.
 */
class XFormsCaseControl(container: XBLContainer, parent: XFormsControl, element: Element, effectiveId: String)
    extends XFormsNoSingleNodeContainerControl(container, parent, element, effectiveId)
       with VisibilityTrait {

  override type Control <: CaseControl

  require(parent.isInstanceOf[XFormsSwitchControl])

  // We are relevant only if we are selected
  override def computeRelevant: Boolean =
    super.computeRelevant && (! getSwitch.isXForms11Switch || isSelected)

  // Whether this is the currently selected case within the current switch.
  def isSelected: Boolean = getEffectiveId == getSwitch.getSelectedCaseEffectiveId

  // Whether to show this case.
  def isCaseVisible: Boolean = isSelected || getSwitch.isStaticReadonly

  override def locallyVisible: Boolean = isCaseVisible

  // Toggle to this case and dispatch events if this causes a change in selected cases.
  def toggle(): Unit = {
    // There are dependencies on toggled cases for:
    //
    // - case()
    // - case content relevance when XForms 1.1-behavior is enabled
    //
    // Ideally, XPath dependencies should make this smarter.
    //
    getSwitch.setSelectedCase(this)
  }

  def getSwitch: XFormsSwitchControl = parent.asInstanceOf[XFormsSwitchControl]
}
