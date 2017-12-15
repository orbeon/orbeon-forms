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
package org.orbeon.oxf.xforms.function

import org.orbeon.oxf.xml.DependsOnContextItem
import org.orbeon.saxon.expr.{PathMap, XPathContext}
import org.orbeon.saxon.om.Item

/**
  * XForms 1.1 current() function.
  *
  * "7.10.2 Returns the context node used to initialize the evaluation of the containing XPath expression."
  */
class Current extends XFormsFunction with DependsOnContextItem {

  override def evaluateItem(xpathContext: XPathContext): Item = {
    // Go up the stack to find the top-level context
    var currentContext = xpathContext
    while (currentContext.getCaller ne null)
      currentContext = currentContext.getCaller
    currentContext.getContextItem
  }

  // TODO: something smart
  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet =
    super.addToPathMap(pathMap, pathMapNodeSet)
}