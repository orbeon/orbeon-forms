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

import org.orbeon.oxf.xforms.control.controls.XFormsSelect1Control
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.ExpressionTool
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item

class XXFormsItemset extends XFormsFunction with FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): Item = {

        implicit val ctx = xpathContext

        val controlStaticId = stringArgument(0)(xpathContext)
        val format          = stringArgument(1)(xpathContext)
        val selected        = argument.lift(2) map (e ⇒ ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext))) getOrElse false

        context.container.resolveObjectByIdInScope(getSourceEffectiveId, controlStaticId, null) match {
            case select1Control: XFormsSelect1Control if select1Control.isRelevant ⇒

                val itemset = select1Control.getItemset
                val controlValueForSelection = if (selected) select1Control.getValue else null

                if (format == "json")
                    // Return a string
                    itemset.getJSONTreeInfo(controlValueForSelection, select1Control.getLocationData)
                else
                    // Return an XML document
                    itemset.getXMLTreeInfo(xpathContext.getConfiguration, controlValueForSelection, select1Control.getLocationData)

            case _ ⇒ null
        }
    }
}