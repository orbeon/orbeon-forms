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

import org.orbeon.dom.QName
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xforms.xbl.AbstractBinding
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.Property
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.XFormsId

class XXFormsComponentParam extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): AtomicValue = {

    val paramName = getQNameFromExpression(argument.head)(xpathContext)

    val containerForSourceScope =
      XFormsFunction.context.container.findScopeRoot(XFormsId.getPrefixedId(getSourceEffectiveId): String)

    containerForSourceScope.associatedControlOpt collect
      { case c: XFormsComponentControl ⇒ c } flatMap { c ⇒

      val concreteBinding = c.staticControl.binding

      // NOTE: In the future, we would like constant values to be available right away, and
      // AVTs to support dependencies. Those should probably be stored lazily at the control
      // level.
      XXFormsComponentParam.evaluateUsePropertiesIfNeeded(
        concreteBinding.boundElementAtts.lift,
        c.evaluateAvt,
        paramName,
        concreteBinding.abstractBinding
      )

    } orNull
  }
}

object XXFormsComponentParam {

  def evaluateUsePropertiesIfNeeded(
    atts            : QName ⇒ Option[String],
    evaluateAvt     : String ⇒ String,
    paramName       : QName,
    abstractBinding : AbstractBinding
  ): Option[AtomicValue] = {

    // NOTE: In the future, we would like constant values to be available right away, and
    // AVTs to support dependencies. Those should probably be stored lazily at the control
    // level.

    def fromElemAlsoTryAvt =
      atts(paramName) map evaluateAvt map stringToStringValue

    def propertyName =
      abstractBinding.directName map { qName ⇒
        List("oxf.xforms.xbl", qName.namespace.prefix, qName.name, paramName) mkString "."
      }

    // NOTE: We currently don't have a way, besides removing a property entirely, to indicate that a property is
    // `null` or `None`. For properties like `number.digits-after-decimal`, `number.prefix`, etc., we do need
    // such a way. So if the value is a blank string (which means the value is actually a blank `xs:string` or maybe
    // `xs:anyURI`), consider the property missing. We could revise this in the future to make a distinction between
    // a blank or empty string and a missing property.
    def fromProperties =
      propertyName flatMap Property.property filter (_.getStringValue.nonEmpty)

    fromElemAlsoTryAvt orElse fromProperties
  }

}