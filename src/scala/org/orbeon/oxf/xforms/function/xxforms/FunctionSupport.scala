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

import org.orbeon.saxon.expr.{Expression, XPathContext}
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.XFormsObject

trait FunctionSupport extends XFormsFunction {

    def relevantControl(i: Int)(implicit xpathContext: XPathContext): Option[XFormsControl] = {
        val staticOrAbsoluteId = arguments(i).evaluateAsString(xpathContext).toString

        Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId, null)) collect
            { case control: XFormsControl if control.isRelevant ⇒ control }
    }

    def resolveEffectiveId(staticIdExpr: Option[Expression])(implicit xpathContext: XPathContext) = staticIdExpr match {
        case None ⇒
            // If no argument is supplied, return the closest id (source id)
            Option(getSourceEffectiveId)
        case Some(expr) ⇒
            // Otherwise resolve the static id passed against the source id
            val staticId = expr.evaluateAsString(xpathContext).toString
            Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticId, null)) collect
                { case target: XFormsObject ⇒ target.getEffectiveId }
    }
}
