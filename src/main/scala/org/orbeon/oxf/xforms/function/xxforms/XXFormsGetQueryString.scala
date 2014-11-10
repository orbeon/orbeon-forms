/**
 * Copyright (C) 2014 Orbeon, Inc.
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

import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue

class XXFormsGetQueryString extends XFormsFunction with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext): StringValue = {
        val containingDocument: XFormsContainingDocument = getContainingDocument(xpathContext)
        if (containingDocument == null) return StringValue.makeStringValue(NetUtils.getExternalContext.getRequest.getQueryString)
        else return StringValue.makeStringValue(buildQueryString(containingDocument.getRequestParameters))
    }

    def buildQueryString(qsMap: Map[String, List[String]]): String = {
        qsMap flatMap {
            case (key, values) ⇒ values map (key + '=' + _)
        } mkString("&")
    }
}