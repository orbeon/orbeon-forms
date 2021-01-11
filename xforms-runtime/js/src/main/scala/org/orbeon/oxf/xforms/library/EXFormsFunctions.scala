package org.orbeon.oxf.xforms.library

import org.orbeon.macros.XPathFunction
import org.orbeon.oxf.xml.OrbeonFunctionLibrary
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames.{EXFORMS_NAMESPACE_URI, EXFORMS_PREFIX}


// Only for backward-compatibility
object EXFormsFunctions extends OrbeonFunctionLibrary {

  lazy val namespaces = List(EXFORMS_NAMESPACE_URI -> EXFORMS_PREFIX)

  @XPathFunction
  def relevant(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    XXFormsFunctionLibrary.exformsMipFunction(items, 0)

  @XPathFunction
  def readonly(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    XXFormsFunctionLibrary.exformsMipFunction(items, 1)

  @XPathFunction
  def required(items: Iterable[om.Item] = null)(implicit xpc: XPathContext): Boolean =
    XXFormsFunctionLibrary.exformsMipFunction(items, 2)

//  Fun("sort", classOf[EXFormsSort], 0, 2, Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE,
//    Arg(Type.ITEM_TYPE, ALLOWS_ZERO_OR_MORE),
//    Arg(STRING, EXACTLY_ONE),
//    Arg(STRING, ALLOWS_ZERO_OR_ONE),
//    Arg(STRING, ALLOWS_ZERO_OR_ONE),
//    Arg(STRING, ALLOWS_ZERO_OR_ONE)
//  )
}