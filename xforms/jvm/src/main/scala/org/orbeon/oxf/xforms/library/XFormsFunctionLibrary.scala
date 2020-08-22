/**
 *  Copyright (C) 2007 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms.library

import org.orbeon.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.function.Last
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.functions._
import org.orbeon.saxon.om.NamespaceConstant
import org.orbeon.saxon.value._
import org.orbeon.saxon._

/**
 * Function library for XPath expressions in XForms.
 */
object XFormsFunctionLibrary extends {
  // Namespace the functions. We wish we had trait parameters, see:
  // http://docs.scala-lang.org/sips/pending/trait-parameters.html
  val XFormsIndependentFunctionsNS  = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
  val XFormsEnvFunctionsNS          = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
  val XFormsXXFormsEnvFunctionsNS   = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI, XXFORMS_NAMESPACE_URI)
  val XFormsFunnyFunctionsNS        = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
  val CryptoFunctionsNS             = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
  val PureUriFunctionsNS            = Seq(NamespaceConstant.FN, XFORMS_NAMESPACE_URI)
  val IndependentFunctionsNS        = Seq(XXFORMS_NAMESPACE_URI)
  val XXFormsEnvFunctionsNS         = Seq(XXFORMS_NAMESPACE_URI)
  val EXFormsFunctionsNS            = Seq(EXFORMS_NAMESPACE_URI)
  val XSLTFunctionsNS               = Seq(NamespaceConstant.FN)
}
  with OrbeonFunctionLibrary
  with XFormsEnvFunctions
  with XFormsXXFormsEnvFunctions
  with XFormsFunnyFunctions
  with XFormsDeprecatedFunctions
  with CryptoFunctions
  with PureUriFunctions
  with IndependentFunctions
  with XXFormsEnvFunctions
  with MapFunctions
  with ArrayFunctions
  with EXFormsFunctions
  with XSLTFunctions {

  // Saxon's last() function doesn't do what we need
  Fun("last", classOf[Last], op = 0, min = 0, INTEGER, EXACTLY_ONE)

  // Forward these to our own implementation so we can handle PathMap
  Fun("count", classOf[org.orbeon.oxf.xforms.function.Aggregate], op = Aggregate.COUNT, min = 1, INTEGER, EXACTLY_ONE,
    Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE, IntegerValue.ZERO)
  )

  Fun("avg", classOf[org.orbeon.oxf.xforms.function.Aggregate], op = Aggregate.AVG, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
    Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_MORE, EmptySequence.getInstance())
  )

  Fun("sum", classOf[org.orbeon.oxf.xforms.function.Aggregate], op = Aggregate.SUM, min = 1, ANY_ATOMIC, ALLOWS_ZERO_OR_ONE,
    Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_MORE),
    Arg(ANY_ATOMIC, ALLOWS_ZERO_OR_ONE)
  )
}