package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.{DefaultFunctionSupport, RuntimeDependentFunction}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.scaxon.Implicits.*


class XXFEvaluateInContext extends DefaultFunctionSupport with RuntimeDependentFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {

    implicit val xpc: XPathContext = xpathContext
    implicit val xfc: XFormsFunction.Context = XFormsFunction.context

    EvaluateSupport.evaluateInContext(stringArgument(0), stringArgument(1))
  }
}