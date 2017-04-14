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

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.value.StringValue

/**
  * xxf:get-request-path() xs:string
  *
  * Return the value of the request path.
  */
class XXFormsGetRequestPath extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): Item =
    functionOperation == 1 option getContainingDocument(xpathContext) match {
      case Some(containingDocument) ⇒ StringValue.makeStringValue(containingDocument.getRequestPath)
      case None                     ⇒ StringValue.makeStringValue(NetUtils.getExternalContext.getRequest.getRequestPath)
    }
}