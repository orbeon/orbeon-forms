/**
 *  Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import org.orbeon.datatypes.LocationData
import org.orbeon.oxf.xml.ShareableXPathStaticContext
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om._
import org.orbeon.saxon.sxpath.XPathExpression
import org.orbeon.xml.NamespaceMapping

trait XPathTrait {

  type SaxonConfiguration

  // Marker for XPath function context
  trait FunctionContext

  // To report timing information
  type Reporter = (String, Long) => Unit

  // To resolve a variable
  // Used by `ShareableXPathStaticContext`
//  type VariableResolver

  // Context accessible during XPath evaluation
  // 2015-05-27: We use a ThreadLocal for this. Ideally we should pass this with the XPath dynamic context, via the Controller
  // for example. One issue is that we have native Java/Scala functions called via XPath which need access to FunctionContext
  // but don't have access to the XPath dynamic context anymore. This could be fixed if we implement these native functions as
  // Saxon functions, possibly generally via https://github.com/orbeon/orbeon-forms/issues/2214.
  private val xpathContextDyn = new DynamicVariable[FunctionContext]

  def withFunctionContext[T](functionContext: FunctionContext)(thunk: => T): T = {
    xpathContextDyn.withValue(functionContext) {
      thunk
    }
  }

  // Return the currently scoped function context if any
  def functionContext: Option[FunctionContext] = xpathContextDyn.value

  // Compiled expression with source information
  case class CompiledExpression(expression: XPathExpression, string: String, locationData: LocationData)

  def makeStringExpression(expression: String): String =  "string((" + expression + ")[1])"
  def makeBooleanExpression(expression: String): String =  "boolean(" + expression + ")"

  val GlobalConfiguration: SaxonConfiguration

  // New mutable configuration sharing the same name pool and converters, for use by mutating callers
//  def newConfiguration: SaxonConfiguration

  // Create and compile an expression
  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression
}
