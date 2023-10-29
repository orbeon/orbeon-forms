package org.orbeon.oxf.xforms.function.xxforms


import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.value.Int64Value
import org.orbeon.scaxon.Implicits._


class XXFormsRepeatPositions extends XFormsFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator =
    bindingContext.repeatPositions.map(new Int64Value(_)).toList // https://github.com/orbeon/orbeon-forms/issues/6016
}
