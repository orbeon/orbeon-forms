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

import org.orbeon.oxf.properties.Properties
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue


class XXFormsComponentParam extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    val paramName = getQNameFromExpression(argument.head)(xpathContext)

    val containerForSourceScope =
      XFormsFunction.context.container.findScopeRoot(XFormsUtils.getPrefixedId(getSourceEffectiveId))

    containerForSourceScope.associatedControlOpt collect
      { case c: XFormsComponentControl ⇒ c } flatMap { c ⇒

      val concreteBinding = c.staticControl.binding

      // NOTE: In the future, we would like constant values to be available right away, and
      // AVTs to support dependencies. Those should probably be stored lazily at the control
      // level.

      def fromElemAlsoTryAvt =
        concreteBinding.boundElementAtts.get(paramName) map c.evaluateAvt

      def propertyName =
        concreteBinding.abstractBinding.directName map { name ⇒
          List("oxf.xforms.xbl", name.getNamespacePrefix, name.getName, paramName) mkString "."
        }

      def fromProperties =
        propertyName flatMap Properties.instance.getPropertySet.getNonBlankString

      fromElemAlsoTryAvt orElse fromProperties
    }
  }
}