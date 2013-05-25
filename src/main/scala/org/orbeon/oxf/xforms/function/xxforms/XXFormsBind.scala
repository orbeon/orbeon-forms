/**
 * Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.RuntimeBind
import org.orbeon.saxon.om.{EmptyIterator, ListIterator, SequenceIterator}

/**
 * The xxf:bind() function.
 */
class XXFormsBind extends XFormsFunction with FunctionSupport {

    override def iterate(xpathContext: XPathContext): SequenceIterator = {

        implicit val ctx = xpathContext

        // Get bind id
        val bindId = stringArgument(0)(xpathContext)

        // Get bind nodeset
        context.container.resolveObjectByIdInScope(getSourceEffectiveId, bindId, context.contextStack.getCurrentSingleItem) match {
            case bind: RuntimeBind ⇒ new ListIterator(bind.nodeset)
            case _ ⇒ EmptyIterator.getInstance
        }
    }
}

