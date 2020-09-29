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
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.control.XFormsComponentControl
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.Property
import org.orbeon.saxon.value.{AtomicValue, StringValue}
import org.orbeon.scaxon.Implicits._
import org.orbeon.xforms.{XFormsNames, XFormsId}
import shapeless.syntax.typeable._

class XXFormsComponentParam extends XFormsFunction {

  import XXFormsComponentParam._

  override def evaluateItem(xpathContext: XPathContext): AtomicValue = {

    val paramName = getQNameFromExpression(argument.head)(xpathContext)

    findSourceComponent(XFormsFunction.context) flatMap { sourceComponent =>

      val staticControl   = sourceComponent.staticControl
      val concreteBinding = staticControl.bindingOrThrow

      // NOTE: In the future, we would like constant values to be available right away, and
      // AVTs to support dependencies. Those should probably be stored lazily at the control
      // level.
      val attrValue =
        fromElemAlsoTryAvt(
          concreteBinding.boundElementAtts.lift,
          sourceComponent.evaluateAvt,
          paramName
        )

      attrValue orElse
        fromProperties(
          paramName,
          Nil,
          staticControl.commonBinding.directName
        )
    } orNull
  }
}

object XXFormsComponentParam {

  val XblLocalName = XFormsNames.XBL_XBL_QNAME.localName

  def findSourceComponent(xformsFunctionContext: XFormsFunction.Context): Option[XFormsComponentControl] = {

    val prefixedId              = XFormsId.getPrefixedId(xformsFunctionContext.sourceEffectiveId)
    val containerForSourceScope = xformsFunctionContext.container.findScopeRoot(prefixedId)

    containerForSourceScope.associatedControlOpt flatMap (_.narrowTo[XFormsComponentControl])
  }

  // NOTE: In the future, we would like constant values to be available right away, and
  // AVTs to support dependencies. Those should probably be stored lazily at the control
  // level.
  def fromElemAlsoTryAvt(
     atts            : QName => Option[String],
     evaluateAvt     : String => String,
     paramName       : QName
   ): Option[StringValue] =
     atts(paramName) map evaluateAvt map stringToStringValue

  def fromElem(
    atts            : QName => Option[String],
    paramName       : QName
  ): Option[AtomicValue] =
    atts(paramName) map stringToStringValue

  def fromProperties(
    paramName       : QName,
    paramSuffix     : List[String],
    directNameOpt   : Option[QName]
  ): Option[AtomicValue] = {

    val propertyNameOpt =
      findPropertyParts(directNameOpt, paramName) map { parts =>
        "oxf" :: "xforms" :: parts ::: paramSuffix mkString "."
      }

    // NOTE: We currently don't have a way, besides removing a property entirely, to indicate that a property is
    // `null` or `None`. For properties like `number.digits-after-decimal`, `number.prefix`, etc., we do need
    // such a way. So if the value is a blank string (which means the value is actually a blank `xs:string` or maybe
    // `xs:anyURI`), consider the property missing. We could revise this in the future to make a distinction between
    // a blank or empty string and a missing property.
    propertyNameOpt flatMap Property.property filter (_.getStringValue.nonAllBlank)
  }

  // For example `xbl.fr.number.decimal-separator`
  private def findPropertyParts(directNameOpt: Option[QName], paramName: QName): Option[List[String]] =
    directNameOpt map { qName =>
      XblLocalName :: qName.namespace.prefix :: qName.localName :: paramName.localName :: Nil
    }
}