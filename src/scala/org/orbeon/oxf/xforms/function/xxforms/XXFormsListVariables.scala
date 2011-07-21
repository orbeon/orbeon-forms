/**
 * Copyright (C) 2011 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.om.{EmptyIterator, ListIterator}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.oxf.xforms.XFormsModel
import collection.JavaConverters._
import org.orbeon.saxon.value.StringValue

class XXFormsListVariables extends XFormsFunction {

    override def iterate(xpathContext: XPathContext) = {

        val containingDocument = getContainingDocument(xpathContext)
        val modelEffectiveId = argument(0).evaluateAsString(xpathContext).toString

        containingDocument.getObjectByEffectiveId(modelEffectiveId) match {
            case model: XFormsModel =>
                val variables = model.getBindingContext(containingDocument).getInScopeVariables

                if (variables.size() > 0)
                    new ListIterator(variables.asScala.map(v => StringValue.makeStringValue(v._1)).toList.asJava)
                else
                    EmptyIterator.getInstance

            case _ => EmptyIterator.getInstance
        }
    }
}