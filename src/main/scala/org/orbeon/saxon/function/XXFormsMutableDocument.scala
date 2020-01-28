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
package org.orbeon.saxon.function

import org.orbeon.oxf.xml.{DefaultFunctionSupport, TransformerUtils}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{DocumentInfo, NodeInfo}

/**
  * `xxf:mutable-document()` takes a document as input and produces a mutable document as output, i.g. a document on
  * which you can use `xf:setvalue`, for example.
  */
class XXFormsMutableDocument extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): DocumentInfo =
    itemArgument(0)(xpathContext) match {
      case n: NodeInfo => TransformerUtils.extractAsMutableDocument(n)
      case _           => null
    }
}