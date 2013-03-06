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

import org.orbeon.oxf.pipeline.api.ExternalContext.Request
import org.orbeon.oxf.util.{StringConversions, NetUtils}
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.function.{FunctionSupport, XFormsFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{EmptyIterator, SequenceIterator}

// xxf:get-request-parameter($a as xs:string) xs:string*
class XXFormsGetRequestParameter extends RequestFunction {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
        containingDocument.getRequestParameters.get(name)

    def fromRequest(request: Request, name: String) =
        Option(request.getParameterMap.get(name)) map StringConversions.objectArrayToStringArray
}

// xxf:get-request-header($a as xs:string) xs:string*
class XXFormsGetRequestHeader extends RequestFunction {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
        containingDocument.getRequestHeaders.get(name)

    def fromRequest(request: Request, name: String) =
        Option(NetUtils.getExternalContext.getRequest.getHeaderValuesMap.get(name))
}

trait RequestFunction extends XFormsFunction with FunctionSupport {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String): Option[Array[String]]
    def fromRequest(request: Request, name: String): Option[Array[String]]

    override def iterate(xpathContext: XPathContext): SequenceIterator = {

        val name = stringArgument(0)(xpathContext)

        // Ask XForms document if present, request otherwise
        val values =
            Option(getContainingDocument(xpathContext)) map
            (fromDocument(_, name)) getOrElse
            fromRequest(NetUtils.getExternalContext.getRequest, name)

        values map asIterator getOrElse EmptyIterator.getInstance
    }
}