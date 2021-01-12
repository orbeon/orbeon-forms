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

import org.orbeon.oxf.xforms.control.XFormsValueControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.itemset.ItemsetSupport
import org.orbeon.oxf.xml.SaxonUtils
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
        select1Control <- ItemsetSupport.findSelectionControl(valueControl)
        itemset        = select1Control.getItemset
      } yield {

        val format   = stringArgument(1)
        val selected = argument.lift(2) exists (e => ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext)))

        val controlValueForSelection =
          if (selected)
            select1Control.boundItemOpt map select1Control.getCurrentItemValueFromData map { v =>
              (v, SaxonUtils.attCompare(select1Control.boundNodeOpt, _))
            }
          else
            None

        if (format == "json")
          // Return a string
          ItemsetSupport.asJSON(
            itemset                    = itemset,
            controlValue               = controlValueForSelection,
            encode                     = select1Control.mustEncodeValues,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          ): om.Item
        else {
          // Return an XML document
          ItemsetSupport.asXML(
            itemset                    = itemset,
            configuration              = ctx.getConfiguration,
            controlValue               = controlValueForSelection,
            excludeWhitespaceTextNodes = select1Control.staticControl.excludeWhitespaceTextNodesForCopy,
            locationData               = control.getLocationData
          ): om.Item
        }
      }

    jsonOrXMLOpt.orNull
  }
}
