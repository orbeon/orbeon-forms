package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.value.StringValue


class XXFormsInnerText extends XFormsFunction {

  override def evaluateItem(xpathContext: XPathContext): StringValue =
    itemArgumentOrContextOpt(0)(xpathContext) map
      (item => new StringValue(XXFormsTextSupport.innerText(item))) orNull
}
