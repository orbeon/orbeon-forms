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

import org.orbeon.saxon.expr._
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.oxf.xforms.function.{MatchSimpleAnalysis, XFormsFunction}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis
import org.orbeon.oxf.xforms.XFormsContextStack

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */
class XXFormsRepeatCurrent extends XFormsFunction with MatchSimpleAnalysis {

    override def evaluateItem(xpathContext: XPathContext) =
        getRepeatCurrentSingleNode(getContextStack(xpathContext),
            argument.headOption map (_.evaluateAsString(xpathContext).toString))

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet): PathMapNodeSet = {

        // Match on context expression
        argument.headOption match {
            case Some(repeatIdExpression: StringLiteral) =>
                // Argument is literal and we have a context to ask
                pathMap.getPathMapContext match {
                    case context: SimpleElementAnalysis#SimplePathMapContext =>
                        // Get PathMap for context id
                        matchSimpleAnalysis(pathMap, context.getInScopeContexts.get(repeatIdExpression.getStringValue))
                    case _ => throw new OXFException("Can't process PathMap because context is not of expected type.")
                }
            case None =>
                // Argument is not specified, ask PathMap for the result
                pathMap.getPathMapContext match {
                    case context: SimpleElementAnalysis#SimplePathMapContext =>
                        // Get PathMap for context id
                        matchSimpleAnalysis(pathMap, context.getInScopeRepeat)
                    case _ => throw new OXFException("Can't process PathMap because context is not of expected type.")
                }
            case _ =>
                // Argument is not literal so we can't figure it out
                pathMap.setInvalidated(true)
                null
        }
    }

    /**
     * Return the single item associated with the iteration of the repeat specified. If a null
     * repeat id is passed, return the single item associated with the closest enclosing repeat
     * iteration.
     */
    private def getRepeatCurrentSingleNode(contextStack: XFormsContextStack, repeatId: Option[String]) =
        XXFormsRepeatFunctions.getEnclosingRepeatIterationBindingContext(contextStack, repeatId).getSingleItem
}
