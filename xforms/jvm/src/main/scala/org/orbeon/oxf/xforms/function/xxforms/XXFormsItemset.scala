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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.control.Controls.ControlsIterator
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{ExpressionTool, XPathContext}
import org.orbeon.saxon.om.Item

class XXFormsItemset extends XFormsFunction {
  override def evaluateItem(xpathContext: XPathContext): Item = {

    implicit val ctx = xpathContext

    def fromSelectionControl(control: XFormsSelect1Control): Item = {

      val format   = stringArgument(1)
      val selected = argument.lift(2) exists (e ⇒ ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext)))

      val itemset = control.getItemset
      val controlValueForSelection = if (selected) control.getValue else null

      if (format == "json")
        // Return a string
        itemset.asJSON(controlValueForSelection, control.mustEncodeValues, control.getLocationData)
      else
        // Return an XML document
        itemset.asXML(xpathContext.getConfiguration, controlValueForSelection, control.getLocationData)
    }

    // Not the ideal solution, see https://github.com/orbeon/orbeon-forms/issues/1856
    def fromComponentControl(control: XFormsComponentControl): Item = (
      ControlsIterator(control, includeSelf = false)
      collectFirst { case c: XFormsSelect1Control ⇒ c }
      map fromSelectionControl
      orNull
    )

    relevantControl(0) match {
      case Some(control: XFormsSelect1Control)   ⇒ fromSelectionControl(control)
      case Some(control: XFormsComponentControl) ⇒ fromComponentControl(control)
      case _                                     ⇒ null
    }
  }
}