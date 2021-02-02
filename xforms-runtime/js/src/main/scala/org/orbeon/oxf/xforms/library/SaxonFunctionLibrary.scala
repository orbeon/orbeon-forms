package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsId


// This is for backward compatibility so that we have `saxon:evaluate()`
object SaxonFunctionLibrary extends OrbeonFunctionLibrary {

  lazy val namespaces = List("http://saxon.sf.net/" -> "saxon")

  @XPathFunction
  def evaluate(expr: String)(implicit xpc: XPathContext, xfc: XFormsFunction.Context): Iterable[om.Item] =
    XXFormsFunctionLibrary.evaluateImpl(expr)
}
