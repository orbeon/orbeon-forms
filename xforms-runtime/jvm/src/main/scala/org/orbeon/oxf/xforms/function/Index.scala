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

import org.orbeon.datatypes.BasicLocationData
import org.orbeon.oxf.common.ValidationException
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.Int64Value

/**
 * XForms index() function.
 *
 * 7.8.5 The index() Function
 */
class Index extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): Int64Value =
    findIndexForRepeatId(stringArgument(0)(xpathContext))

  protected def findIndexForRepeatId(repeatStaticId: String): Int64Value =
    XFormsFunction.context.container.getRepeatIndex(XFormsFunction.getSourceEffectiveId(XFormsFunction.context), repeatStaticId) match {
      case Some(index) =>
        new Int64Value(index)
      case None =>
        throw new ValidationException(
          s"Function index uses repeat id `$repeatStaticId` which is not in scope",
          BasicLocationData(getSystemId, getLineNumber, getColumnNumber)
        )
    }
}