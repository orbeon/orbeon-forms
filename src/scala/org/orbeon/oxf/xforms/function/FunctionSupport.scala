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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xforms.XFormsObject
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.saxon.expr.{Expression, XPathContext}
import org.orbeon.saxon.om.{ListIterator, ArrayIterator}
import org.orbeon.saxon.value.StringValue
import collection.JavaConverters._

trait FunctionSupport extends XFormsFunction {

    // Resolve the relevant control by argument expression
    def relevantControl(i: Int)(implicit xpathContext: XPathContext): Option[XFormsControl] =
        relevantControl(arguments(i).evaluateAsString(xpathContext).toString)

    // Resolve a relevant control by id
    def relevantControl(staticOrAbsoluteId: String)(implicit xpathContext: XPathContext): Option[XFormsControl] =
        resolveOrFindByEffectiveId(staticOrAbsoluteId) collect
            { case control: XFormsControl if control.isRelevant ⇒ control }

    // Resolve an object by id
    def resolveOrFindByEffectiveId(staticOrAbsoluteId: String)(implicit xpathContext: XPathContext): Option[XFormsObject] =
        Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticOrAbsoluteId, null))

    def resolveEffectiveId(staticIdExpr: Option[Expression])(implicit xpathContext: XPathContext): Option[String] =
        staticIdExpr match {
            case None ⇒
                // If no argument is supplied, return the closest id (source id)
                Option(getSourceEffectiveId)
            case Some(expr) ⇒
                // Otherwise resolve the static id passed against the source id
                val staticId = expr.evaluateAsString(xpathContext).toString
                Option(context.container.resolveObjectByIdInScope(getSourceEffectiveId, staticId, null)) collect {
                    case target: XFormsObject ⇒ target.getEffectiveId
                }
        }

    def asIterator(v: Array[String]) = new ArrayIterator(v map StringValue.makeStringValue)
    def asIterator(v: Seq[String])   = new ListIterator (v map StringValue.makeStringValue asJava)
}
