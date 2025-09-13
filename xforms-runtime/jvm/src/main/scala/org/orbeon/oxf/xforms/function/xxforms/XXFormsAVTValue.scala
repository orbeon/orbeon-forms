/**
 * Copyright (C) 2013 Orbeon, Inc.
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

import org.orbeon.oxf.xforms.control.controls.XXFormsAttributeControl
import org.orbeon.oxf.xforms.event.EventCollector
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits.*
import shapeless.syntax.typeable.*

class XXFormsAVTValue extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    val forId   = stringArgument(0)
    val attName = stringArgument(1)
    // TODO: handle also absolute id
    for {
      forPrefixedId      <- XFormsFunction.sourceScope.prefixedIdForStaticIdOpt(forId)
      attControlAnalysis <- Option(XFormsFunction.context.container.partAnalysis.getAttributeControl(forPrefixedId, attName))
      control            <- XFormsFunction.findRelevantControls(attControlAnalysis.staticId, followIndexes = true).headOption
      attControl         <- control.cast[XXFormsAttributeControl]
      value              <- attControl.valueOpt(EventCollector.Throw)
    } yield
      value
  }


  // TODO: PathMap
}