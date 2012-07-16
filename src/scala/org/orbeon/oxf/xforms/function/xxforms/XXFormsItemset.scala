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
import org.orbeon.oxf.xforms.control.controls.XFormsSelectControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.ExpressionTool
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.StringValue

class XXFormsItemset extends XFormsFunction {
    override def evaluateItem(xpathContext: XPathContext): Item = {

        val controlStaticId = argument(0).evaluateAsString(xpathContext).toString
        val format          = argument(1).evaluateAsString(xpathContext).toString
        val selected        = argument.lift(2) map (e ⇒ ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext))) getOrElse false

        getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), controlStaticId, null) match {
            case select1Control: XFormsSelect1Control if select1Control.isRelevant ⇒

                val itemset = select1Control.getItemset
                val controlValueForSelection = if (selected) select1Control.getValue else null
                val isMultiple = select1Control.isInstanceOf[XFormsSelectControl]

                if (format == "json")
                    // Return a string
                    StringValue.makeStringValue(itemset.getJSONTreeInfo(controlValueForSelection, isMultiple, select1Control.getLocationData))
                else
                    // Return an XML document
                    itemset.getXMLTreeInfo(xpathContext.getConfiguration, controlValueForSelection, isMultiple, select1Control.getLocationData)

            case _ ⇒ null
        }
    }
}