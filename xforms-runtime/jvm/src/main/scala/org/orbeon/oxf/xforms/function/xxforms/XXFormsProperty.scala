package org.orbeon.oxf.xforms.function.xxforms;


import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.{DefaultFunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.function.{AddToPathMap, Property}
import org.orbeon.saxon.value.AtomicValue;


class XXFormsProperty
  extends DefaultFunctionSupport
    with  RuntimeDependentFunction
    with  AddToPathMap { // when properties reload, we now recompute all values
  override def evaluateItem(xpathContext: XPathContext): AtomicValue = {

    implicit val xpc: XPathContext           = xpathContext
    implicit val xfc: XFormsFunction.Context = XFormsFunction.context

    Property.property(stringArgument(0), xfc.containingDocument.staticState.propertyProfileOpt).orNull
  }
}