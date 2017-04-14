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

import org.orbeon.oxf.util.NetUtils
import org.orbeon.oxf.xml.FunctionSupport
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator

/**
  * xxf:get-request-attribute($a as xs:string) document-node()?
  *
  * Return the value of the given request attribute.
  */
class XXFormsGetRequestAttribute extends FunctionSupport {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val ctx = xpathContext

    val attributeName = stringArgument(0)

    ScopeFunctionSupport.convertAttributeValue(
      Option(NetUtils.getExternalContext.getRequest.getAttributesMap.get(attributeName)),
      stringArgumentOpt(1),
      attributeName
    )
  }
}