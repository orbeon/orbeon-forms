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
import org.orbeon.saxon.expr.{ExpressionTool, XPathContext}
import org.orbeon.saxon.om
import org.orbeon.scaxon.Implicits._
import shapeless.syntax.typeable._


class XXFormsItemset extends XFormsFunction {
  override def evaluateItem(xpathContext: XPathContext): om.Item = {

    implicit val ctx: XPathContext = xpathContext

    val jsonOrXMLOpt =
      for {
        control        <- relevantControl(0)
        valueControl   <- control.narrowTo[XFormsValueControl]
        select1Control <- XXFormsItemset.findSelectionControl(valueControl)
        itemset        = select1Control.getItemset
      } yield {

        val format   = stringArgument(1)
        val selected = argument.lift(2) exists (e => ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext)))

        val controlValueForSelection =
          if (selected)
            select1Control.boundItemOpt map select1Control.getCurrentItemValueFromData map { v =>
              (v, XFormsSelect1Control.attCompare(select1Control.boundNodeOpt, _))
            }
          else
            None

        if (format == "json")
          // Return a string
          itemset.asJSON(
            controlValue               = controlValueForSelection,
            encode                     = select1Control.mustEncodeValues,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          ): om.Item
        else
          // Return an XML document
          itemset.asXML(
            configuration              = ctx.getConfiguration,
            controlValue               = controlValueForSelection,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          ): om.Item
      }

    jsonOrXMLOpt.orNull
  }
}

object XXFormsItemset {

  def findSelectionControl(control: XFormsControl): Option[XFormsSelect1Control] =
    control match {
      case c: XFormsSelect1Control =>
        Some(c)
      case c: XFormsComponentControl if c.staticControl.commonBinding.modeSelection =>
        // Not the ideal solution, see https://github.com/orbeon/orbeon-forms/issues/1856
        ControlsIterator(c, includeSelf = false) collectFirst { case c: XFormsSelect1Control => c }
      case _ =>
        None
    }
}