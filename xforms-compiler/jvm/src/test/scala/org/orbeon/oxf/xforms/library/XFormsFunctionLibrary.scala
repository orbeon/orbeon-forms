package org.orbeon.oxf.xforms.library

import org.orbeon.saxon.expr.{Expression, StaticContext}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.StructuredQName


// Placeholder for tests
object XFormsFunctionLibrary extends FunctionLibrary {
  def isAvailable(structuredQName: StructuredQName, i: Int): Boolean = false
  def bind(structuredQName: StructuredQName, expressions: Array[Expression], staticContext: StaticContext): Expression = null
  def copy(): FunctionLibrary = this
}
