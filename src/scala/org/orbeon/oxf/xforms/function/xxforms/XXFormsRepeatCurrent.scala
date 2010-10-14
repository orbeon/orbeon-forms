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


import org.orbeon.oxf.xforms.XFormsControls
import org.orbeon.saxon.expr._
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms.function.{MatchSimpleAnalysis, XFormsFunction}
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.analysis.SimpleElementAnalysis

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */
class XXFormsRepeatCurrent extends XFormsFunction with MatchSimpleAnalysis {

    override def evaluateItem(xpathContext: XPathContext): Item = {

        // Note that this is deprecated. Move to warning later?
        val indentedLogger = getContainingDocument(xpathContext).getIndentedLogger(XFormsControls.LOGGING_CATEGORY)
        if (indentedLogger.isDebugEnabled)
            indentedLogger.logDebug("xxforms:repeat-current()", "function is deprecated, use context() or xxforms:context() instead")

        getContextStack(xpathContext).getRepeatCurrentSingleNode(getRepeatIdExpression match {
            case Some(repeatIdExpression) =>
                // Argument passed => get context id by evaluating expression
                repeatIdExpression.evaluateAsString(xpathContext).toString
            case None =>
                // No argument passed => pass null
                null
        })
    }

    override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet): PathMapNodeSet = {

        // Match on context expression
        getRepeatIdExpression match {
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

    private def getRepeatIdExpression = if (argument.length == 0) None else Some(argument(0))
}
