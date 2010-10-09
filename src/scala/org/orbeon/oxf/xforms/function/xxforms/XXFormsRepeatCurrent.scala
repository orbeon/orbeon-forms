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
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.om.Item
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */

class XXFormsRepeatCurrent extends XFormsFunction {
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

        def matchSimpleAnalysis(analysisOption: Option[SimpleAnalysis]): PathMapNodeSet = analysisOption match {
            // TODO: This is the same match as in XXFormsContext
            case Some(simpleAnalysis) if simpleAnalysis.getBindingAnalysis != null && simpleAnalysis.getBindingAnalysis.figuredOutDependencies =>
                // Clone the PathMap first because the nodes returned must belong to this PathMap
                val clonedContextPathMap = simpleAnalysis.getBindingAnalysis.pathmap.clone
                pathMap.addRoots(clonedContextPathMap.getPathMapRoots)
                clonedContextPathMap.findFinalNodes
            case None =>
                // Probably the id passed doesn't match any ancestor id
                pathMap.setInvalidated(true)
                null
        }

        // Match on context expression
        getRepeatIdExpression match {
            case Some(repeatIdExpression: StringLiteral) =>
                // Argument is literal and we have a context to ask
                pathMap.getPathMapContext match {
                    case context: SimpleAnalysis#SimplePathMapContext =>
                        // Get PathMap for context id
                        matchSimpleAnalysis(context.getInScopeContexts.get(repeatIdExpression.getStringValue))
                }
            case None =>
                // Argument is not specified, ask PathMap for the result
                pathMap.getPathMapContext match {
                    case context: SimpleAnalysis#SimplePathMapContext =>
                        // Get PathMap for context id
                        matchSimpleAnalysis(context.getInScopeRepeat)
                }
            case _ =>
                // Argument is not literal so we can't figure it out
                pathMap.setInvalidated(true)
                null
        }
    }

    private def getRepeatIdExpression = if (argument.length == 0) None else Some(argument(0))
}

