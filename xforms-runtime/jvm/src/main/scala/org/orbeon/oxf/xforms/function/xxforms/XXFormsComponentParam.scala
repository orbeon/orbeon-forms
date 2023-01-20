/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.Property
import org.orbeon.saxon.value.AtomicValue


class XXFormsComponentParam extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): AtomicValue = {

    val paramName = getQNameFromExpression(argument.head)(xpathContext)

    // TODO: 2023-01-20: move common code to `ComponentParamSupport`
    ComponentParamSupport.findSourceComponent(None)(XFormsFunction.context) flatMap { sourceComponent =>

      val staticControl   = sourceComponent.staticControl
      val concreteBinding = staticControl.bindingOrThrow

      // NOTE: In the future, we would like constant values to be available right away, and
      // AVTs to support dependencies. Those should probably be stored lazily at the control
      // level.
      val attrValue =
        ComponentParamSupport.fromElemAlsoTryAvt(
          concreteBinding.boundElementAtts.lift,
          sourceComponent.evaluateAvt,
          paramName
        )

      attrValue orElse
        ComponentParamSupport.fromProperties(
          paramName,
          Nil,
          staticControl.commonBinding.directName,
          Property.property
        )
    } orNull
  }
}
