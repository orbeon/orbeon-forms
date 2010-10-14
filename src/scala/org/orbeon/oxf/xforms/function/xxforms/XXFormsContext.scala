/**
 *  Copyright (C) 2010 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._
import org.orbeon.oxf.xforms.function.{MatchSimpleAnalysis, XFormsFunction}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

/**
 * The xxforms:context() function allows you to obtain the single-node binding for an enclosing xforms:group,
 * xforms:repeat, or xforms:switch. It takes one mandatory string parameter containing the id of an enclosing grouping
 * XForms control. For xforms:repeat, the context returned is the context of the current iteration.
 */
class XXFormsContext extends XFormsFunction with MatchSimpleAnalysis {

    override def iterate(xpathContext: XPathContext): SequenceIterator = {
        // Match on context expression
        getContextIdExpression match {
            case Some(contextIdExpression) =>
                // Get context id by evaluating expression
                val contextStaticId = contextIdExpression.evaluateAsString(xpathContext).toString
                // Get context item for context id
                getContextStack(xpathContext).getContextForId(contextStaticId) match {
                    case null => EmptyIterator.getInstance
                    case contextItem => SingletonIterator.makeIterator(contextItem)
                }
            case None =>
                // No context id expression
                EmptyIterator.getInstance
        }
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMap.PathMapNodeSet): PathMap.PathMapNodeSet = {
        // Match on context expression
        getContextIdExpression match {
            case Some(contextIdExpression: StringLiteral) =>
                // Argument is literal and we have a context to ask
                pathMap.getPathMapContext match {
                    case context: SimpleElementAnalysis#SimplePathMapContext =>
                        // Get static context id
                        val contextStaticId = contextIdExpression.getStringValue
                        // Handle context
                        matchSimpleAnalysis(pathMap, context.getInScopeContexts.get(contextStaticId))
                    case _ => throw new OXFException("Can't process PathMap because context is not of expected type.")
                }
            case _ =>
                // Argument is not literal so we can't figure it out
                pathMap.setInvalidated(true)
                null
        }
    }

    private def getContextIdExpression = if (argument.length == 0) None else Some(argument(0))
}
