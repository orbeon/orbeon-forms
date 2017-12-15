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
package org.orbeon.oxf.xforms.function.exforms

import org.orbeon.oxf.xforms.function.xxforms.XXFormsSort
import org.orbeon.oxf.xml.DependsOnContextItem
import org.orbeon.saxon.expr.ExpressionVisitor
import org.orbeon.saxon.expr.StaticProperty
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator

/**
 * exf:sort() function
 */
class EXFormsSort extends XXFormsSort with DependsOnContextItem {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    val sequenceToSortExpression = argument(0)
    val selectExpression         = argument(1)

    val (sortKeyContext, sortKeyExpression) = {
      val pooledExpression = prepareExpression(xpathContext, selectExpression, isAVT = false)

      val (dynamicContext, xpathContextMajor) = pooledExpression.newDynamicAndMajorContexts
      pooledExpression.prepareDynamicContext(xpathContextMajor)
      (dynamicContext.getXPathContextObject, pooledExpression.internalExpression)
    }
    sort(xpathContext, sortKeyContext, sequenceToSortExpression, sortKeyExpression)
  }

  // Needed by prepareExpression()
  override def checkArguments(visitor: ExpressionVisitor): Unit =
    copyStaticContextIfNeeded(visitor)
}