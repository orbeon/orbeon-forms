/**
 *  Copyright (C) 2007 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om._
import org.orbeon.oxf.xforms.analysis.controls.SimpleAnalysis

/**
 * The xxforms:context() function allows you to obtain the single-node binding for an enclosing xforms:group,
 * xforms:repeat, or xforms:switch. It takes one mandatory string parameter containing the id of an enclosing grouping
 * XForms control. For xforms:repeat, the context returned is the context of the current iteration.
 */
class XXFormsContext extends XFormsFunction {

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
                    case context: SimpleAnalysis#SimplePathMapContext =>
                        // Get context id by evaluating expression
                        val contextStaticId = contextIdExpression.getStringValue
                        // Get PathMap for context id
                        context.getInScopeContexts.get(contextStaticId) match {
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
                }
            case _ =>
                // Argument is not literal so we can't figure it out
                pathMap.setInvalidated(true)
                null
        }
    }

    private def getContextIdExpression = if (argument.length == 0) None else Some(argument(0))
}
