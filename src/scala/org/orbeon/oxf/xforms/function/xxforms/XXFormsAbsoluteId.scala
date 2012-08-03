/**
 * Copyright (C) 2012 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.{Expression, XPathContext}
import org.orbeon.saxon.value.StringValue
import org.orbeon.oxf.xforms.event.XFormsEventTarget

class XXFormsAbsoluteId extends XFormsFunction with ResolveIds {
    override def evaluateItem(xpathContext: XPathContext) =
        resolveEffectiveId(argument.lift(0), xpathContext) match {
            case Some(effectiveId) ⇒
                StringValue.makeStringValue(XFormsUtils.effectiveIdToAbsoluteId(effectiveId))
            case None ⇒ null
        }
}

trait ResolveIds extends XFormsFunction {
    def resolveEffectiveId(staticIdExpr: Option[Expression], xpathContext: XPathContext) = staticIdExpr match {
        case None ⇒
            // If no argument is supplied, return the closest id (source id)
            Option(getSourceEffectiveId(xpathContext))
        case Some(expr) ⇒
            // Otherwise resolve the static id passed against the source id
            val staticId = expr.evaluateAsString(xpathContext).toString
            Option(getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), staticId, null)) collect
                { case target: XFormsEventTarget ⇒ target.getEffectiveId }
    }
}