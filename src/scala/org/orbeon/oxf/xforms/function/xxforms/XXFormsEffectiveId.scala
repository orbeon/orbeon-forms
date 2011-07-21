/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.xforms.event.XFormsEventTarget

class XXFormsEffectiveId extends XFormsFunction {
    override def evaluateItem(xpathContext: XPathContext) = argument match {
        case Array() =>
            // If no argument is supplied, return the closest id (source id)
            StringValue.makeStringValue(getSourceEffectiveId(xpathContext))
        case Array(expr) =>
            // Otherwise resolve the static id passed against the source id
            val staticId = expr.evaluateAsString(xpathContext).toString
            Option(getXBLContainer(xpathContext).resolveObjectById(getSourceEffectiveId(xpathContext), staticId, null)) match {
                case Some(o: XFormsEventTarget) => StringValue.makeStringValue(o.getEffectiveId)
                case None => null
            }
    }
}