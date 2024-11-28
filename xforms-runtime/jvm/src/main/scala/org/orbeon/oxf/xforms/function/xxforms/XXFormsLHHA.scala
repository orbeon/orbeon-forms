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

import org.orbeon.oxf.xforms.analysis.controls.LHHA
import org.orbeon.oxf.xforms.function
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.RuntimeDependentFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue
import org.orbeon.scaxon.Implicits.*


class XXFormsLHHA extends XFormsFunction with RuntimeDependentFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx: XPathContext                    = xpathContext
    implicit val xfc: function.XFormsFunction.Context = XFormsFunction.context

    val lhha =
      operation match {
        case 0 => LHHA.Label
        case 1 => LHHA.Help
        case 2 => LHHA.Hint
        case 3 => LHHA.Alert
      }

    LHHAFunctionSupport.directOrByLocalLhhaValue(arguments.head.evaluateAsString(ctx).toString, lhha)
  }
}

class XXFLabelAppearance extends XFormsFunction with RuntimeDependentFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    LHHAFunctionSupport.labelHintAppearance(arguments.head.evaluateAsString(ctx).toString, LHHA.Label)
  }
}

class XXFHintAppearance extends XFormsFunction with RuntimeDependentFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext
    implicit val xfc = XFormsFunction.context

    LHHAFunctionSupport.labelHintAppearance(arguments.head.evaluateAsString(ctx).toString, LHHA.Hint)
  }
}