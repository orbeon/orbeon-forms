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
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.common.ValidationException
import org.orbeon.oxf.xforms.XFormsContextStack
import org.orbeon.saxon.value.Int64Value

/**
 * Return the current node of one of the enclosing xforms:repeat iteration, either the closest
 * iteration if no argument is passed, or the iteration for the repeat id passed.
 *
 * This function must be called from within an xforms:repeat.
 */
class XXFormsRepeatPosition extends XFormsFunction {

    override def evaluateItem(xpathContext: XPathContext) =
        new Int64Value(getRepeatCurrentPosition(getContextStack(xpathContext),
            argument.headOption map (_.evaluateAsString(xpathContext).toString)))

    private def getRepeatCurrentPosition(contextStack: XFormsContextStack, repeatId: Option[String]) =
        XXFormsRepeatFunctions.getEnclosingRepeatIterationBindingContext(contextStack, repeatId).position
}

object XXFormsRepeatFunctions {

    def getEnclosingRepeatIterationBindingContext(contextStack: XFormsContextStack, repeatId: Option[String]): XFormsContextStack.BindingContext = {

        val initialBindingContext = contextStack.getCurrentBindingContext
        var currentBindingContext = initialBindingContext
        do {
            if (contextStack.isRepeatIterationBindingContext(currentBindingContext)
                && (repeatId.isEmpty || (currentBindingContext.parent.elementId == repeatId.get))) {
                return currentBindingContext
            }
            currentBindingContext = currentBindingContext.parent
        } while (currentBindingContext ne null)

        repeatId match {
            case Some(id) => throw new ValidationException("No enclosing xforms:repeat found for repeat id: " + id, initialBindingContext.getLocationData)
            case None =>throw new ValidationException("No enclosing xforms:repeat found.", initialBindingContext.getLocationData)
        }
    }
}