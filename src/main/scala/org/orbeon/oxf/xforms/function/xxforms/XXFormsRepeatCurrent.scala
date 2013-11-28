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

import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.function.{FunctionSupport, MatchSimpleAnalysis, XFormsFunction}
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr._

/**
 * Return the current node of one of the enclosing xf:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xf:repeat.
 */
class XXFormsRepeatCurrent extends XFormsFunction with MatchSimpleAnalysis with FunctionSupport {

    override def evaluateItem(xpathContext: XPathContext) = {
        implicit val ctx = xpathContext
        bindingContext.enclosingRepeatIterationBindingContext(stringArgumentOpt(0)).getSingleItem
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet): PathMapNodeSet = {

        // Match on context expression
        argument.headOption match {
            case Some(repeatIdExpression: StringLiteral) ⇒
                // Argument is literal and we have a context to ask
                pathMap.getPathMapContext match {
                    case context: SimpleElementAnalysis#SimplePathMapContext ⇒
                        // Get PathMap for context id
                        matchSimpleAnalysis(pathMap, context.getInScopeContexts.get(repeatIdExpression.getStringValue))
                    case _ ⇒ throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
                }
            case None ⇒
                // Argument is not specified, ask PathMap for the result
                pathMap.getPathMapContext match {
                    case context: SimpleElementAnalysis#SimplePathMapContext ⇒
                        // Get PathMap for context id
                        matchSimpleAnalysis(pathMap, context.getInScopeRepeat)
                    case _ ⇒ throw new IllegalStateException("Can't process PathMap because context is not of expected type.")
                }
            case _ ⇒
                // Argument is not literal so we can't figure it out
                pathMap.setInvalidated(true)
                null
        }
    }
}
