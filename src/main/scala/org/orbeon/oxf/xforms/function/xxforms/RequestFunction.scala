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
import org.orbeon.saxon.value.StringValue

// xxf:get-request-method() as xs:string
class XXFormsGetRequestMethod extends XFormsFunction with FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
        NetUtils.getExternalContext.getRequest.getMethod
}

// xxf:get-portlet-mode() as xs:string
class XXFormsGetPortletMode extends XFormsFunction with FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
        NetUtils.getExternalContext.getRequest.getPortletMode
}

// xxf:get-window-state() as xs:string
class XXFormsGetWindowState extends XFormsFunction with FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): StringValue =
        NetUtils.getExternalContext.getRequest.getWindowState
}

// xxf:get-request-parameter($a as xs:string) as xs:string*
class XXFormsGetRequestParameter extends RequestFunction {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
        containingDocument.getRequestParameters.get(name)

    def fromRequest(request: Request, name: String) =
        Option(request.getParameterMap.get(name)) map StringConversions.objectArrayToStringArray map (_.toList)
}

// xxf:get-request-header($a as xs:string) as xs:string*
class XXFormsGetRequestHeader extends RequestFunction {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String) =
        containingDocument.getRequestHeaders.get(name.toLowerCase)

    def fromRequest(request: Request, name: String) =
        Option(NetUtils.getExternalContext.getRequest.getHeaderValuesMap.get(name.toLowerCase)) map (_.toList)
}

trait RequestFunction extends XFormsFunction with FunctionSupport {

    def fromDocument(containingDocument: XFormsContainingDocument, name: String): Option[List[String]]
    def fromRequest(request: Request, name: String): Option[List[String]]

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