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
import org.orbeon.oxf.xforms.model.XFormsModel
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.{EmptyIterator, ListIterator}
import org.orbeon.scaxon.Implicits._

import scala.collection.JavaConverters._

class XXFormsListVariables extends XFormsFunction {

  override def iterate(xpathContext: XPathContext) = {

    implicit val ctx = xpathContext

    val modelEffectiveId = stringArgument(0)(xpathContext)

    XFormsFunction.context.containingDocument.getObjectByEffectiveId(modelEffectiveId) match {
      case model: XFormsModel =>
        val variables = model.getTopLevelVariables

        if (variables.size() > 0)
          new ListIterator(variables.asScala.map { case (name, _) => stringToStringValue(name) }.toList.asJava)
        else
          EmptyIterator.getInstance

      case _ => EmptyIterator.getInstance
    }
  }
}