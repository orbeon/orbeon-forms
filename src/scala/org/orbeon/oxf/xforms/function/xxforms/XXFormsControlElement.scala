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

import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.XFormsConstants

class XXFormsControlElement extends XFormsFunction {

    override def evaluateItem(xpathContext: XPathContext) =
        Option(if (argument.length == 0) xpathContext.getContextItem else argument(0).evaluateItem(xpathContext)) map
            (item => resolveOrFindByEffectiveId(xpathContext, item.getStringValue)) flatMap {
                case control: XFormsControl =>
                    Some(getContainingDocument(xpathContext).getStaticState.documentWrapper.wrap(control.getControlElement))
                case _ =>
                    None
            } orNull

    def resolveOrFindByEffectiveId(xpathContext: XPathContext, staticOrEffectiveId: String) =
        if (isEffectiveId(staticOrEffectiveId))
            getContainingDocument(xpathContext).getObjectByEffectiveId(staticOrEffectiveId)
        else
            getXBLContainer(xpathContext).resolveObjectByIdInScope(getSourceEffectiveId(xpathContext), staticOrEffectiveId, null)

    def isEffectiveId(id: String) = id.contains(XFormsConstants.COMPONENT_SEPARATOR) || id.contains(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1)
}