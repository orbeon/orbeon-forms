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

import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits._

class XXFormsLHHA extends XFormsFunction with RuntimeDependentFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    def evaluateControlItem(f: XFormsControl => String) =
      relevantControl(0) map f

    evaluateControlItem(operation match {
      case 0 => _.getLabel
      case 1 => _.getHelp
      case 2 => _.getHint
      case 3 => _.getAlert
      case _ => throw new UnsupportedOperationException
    })
  }
}
