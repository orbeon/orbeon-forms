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
import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.control.{XFormsComponentControl, XFormsControl, XFormsValueControl}
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.itemset.Itemset
import org.orbeon.saxon.expr.{ExpressionTool, XPathContext}
import org.orbeon.saxon.om.Item
import org.orbeon.scaxon.Implicits._
import shapeless.syntax.typeable._

class XXFormsItemset extends XFormsFunction {
  override def evaluateItem(xpathContext: XPathContext): Item = {

    implicit val ctx = xpathContext

    val jsonOrXMLOpt =
      for {
        control                   ← relevantControl(0)
        valueControl              ← control.cast[XFormsValueControl]
        (select1Control, itemset) ← XXFormsItemset.itemsetFromControl(valueControl)
      } yield {

        val format   = stringArgument(1)
        val selected = argument.lift(2) exists (e ⇒ ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext)))

        val controlValueForSelection = if (selected) select1Control.getValue else null

        if (format == "json")
          // Return a string
          itemset.asJSON(controlValueForSelection, select1Control.mustEncodeValues, control.getLocationData): Item
        else
          // Return an XML document
          itemset.asXML(xpathContext.getConfiguration, controlValueForSelection, control.getLocationData): Item
      }

    jsonOrXMLOpt.orNull
  }
}

object XXFormsItemset {

  def itemsetFromControl(control: XFormsControl): Option[(XFormsSelect1Control, Itemset)] =
    control match {
      case c: XFormsSelect1Control ⇒
        Option(c.getItemset) map (c → _)
      case c: XFormsComponentControl if c.staticControl.bindingOrThrow.abstractBinding.modeSelection ⇒
        // Not the ideal solution, see https://github.com/orbeon/orbeon-forms/issues/1856
        for {
          select1Control ← ControlsIterator(c, includeSelf = false) collectFirst { case c: XFormsSelect1Control ⇒ c }
          itemset ← Option(select1Control.getItemset)
        } yield
          select1Control → itemset
      case _ ⇒
        None
    }
}