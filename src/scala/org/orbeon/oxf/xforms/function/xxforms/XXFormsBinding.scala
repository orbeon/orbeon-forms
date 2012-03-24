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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.oxf.xforms.control.{XFormsNoSingleNodeContainerControl, XFormsSingleNodeControl}

class XXFormsBinding extends XFormsFunction {

    override def iterate(xpathContext: XPathContext) = {

        // Get id
        val staticIdOption = argument.lift(0) map (_.evaluateAsString(xpathContext).toString)

        staticIdOption flatMap {
            staticId ⇒
                // Find object
                getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), staticId, null) match {
                    case control: XFormsSingleNodeControl ⇒
                        // Single-node (single-item) binding
                        Option(control.getBoundItem) map (SingletonIterator.makeIterator(_))
                    case control: XFormsNoSingleNodeContainerControl ⇒
                        // Node-set (item-sequence) binding
                        Option(control.getBindingContext) filter (_.isNewBind) map (c ⇒ new ListIterator(c.nodeset))
                    case _ ⇒
                        None
                }
        } getOrElse
            EmptyIterator.getInstance
    }
}