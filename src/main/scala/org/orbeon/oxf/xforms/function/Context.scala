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

import org.orbeon.saxon.expr.PathMap
import org.orbeon.saxon.expr.StaticProperty
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

/**
 * XForms 1.1 context() function.
 *
 * "7.10.4 The context() Function [...] This function returns the in-scope evaluation context node of the
 * nearest ancestor element of the node containing the XPath expression that invokes this function. The nearest
 * ancestor element may have been created dynamically as part of the run-time expansion of repeated content as
 * described in Section 4.7 Resolving ID References in XForms."
 */
class Context extends XFormsFunction with MatchSimpleAnalysis {

    override def evaluateItem(xpathContext: XPathContext) =
        bindingContext(xpathContext).contextItem

    override def getIntrinsicDependencies =
        StaticProperty.DEPENDS_ON_CONTEXT_ITEM

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet) =
        pathMap.getPathMapContext match {
            case context: SimpleElementAnalysis#SimplePathMapContext ⇒
                // Handle context
                matchSimpleAnalysis(pathMap, context.context)
            case _ ⇒ throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
        }
}