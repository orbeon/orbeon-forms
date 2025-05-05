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

import org.orbeon.oxf.xforms.function.XFormsFunction.getPathMapContext
import org.orbeon.oxf.xforms.function.{MatchSimpleAnalysis, XFormsFunction}
import org.orbeon.saxon.expr.*
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet


/**
 * Return the current node of one of the enclosing `xf:repeat` iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an `xf:repeat`.
 */
class XXFormsRepeatCurrent extends XFormsFunction with MatchSimpleAnalysis {

  override def evaluateItem(xpathContext: XPathContext) = {
    implicit val ctx = xpathContext
    bindingContext.enclosingRepeatIterationBindingContext(stringArgumentOpt(0)).getSingleItemOrNull
  }

  override def addToPathMap(pathMap: PathMap, pathMapNodeSet: PathMapNodeSet): PathMapNodeSet =
    argument.headOption match {
      case Some(repeatIdExpression: StringLiteral) =>
        // Argument is literal and we have a context to ask
        val context = getPathMapContext(pathMap)
        // Get PathMap for context id
        matchSimpleAnalysis(pathMap, context.getInScopeContexts.get(repeatIdExpression.getStringValue))
      case None =>
        // Argument is not specified, ask `PathMap` for the result
        val context = getPathMapContext(pathMap)
        matchSimpleAnalysis(pathMap, context.getInScopeRepeat)
      case _ =>
        // Argument is not literal so we can't figure it out
        pathMap.setInvalidated(true)
        null
    }
}
