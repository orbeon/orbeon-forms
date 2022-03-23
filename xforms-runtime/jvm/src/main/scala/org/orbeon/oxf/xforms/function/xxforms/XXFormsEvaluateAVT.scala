package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om.SequenceIterator


class XXFormsEvaluateAVT extends XFormsFunction {

  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    val (avtExpression, newXPathContext) = prepareExpressionSaxonNoPool(xpathContext, argument(0), isAVT = true)
    avtExpression.iterate(newXPathContext)
  }

  // Needed by prepareExpression()
  override def checkArguments(visitor: ExpressionVisitor): Unit =
    copyStaticContextIfNeeded(visitor)

  override def getIntrinsicDependencies: Int = StaticProperty.DEPENDS_ON_FOCUS
}