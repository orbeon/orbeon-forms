package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.XPath.compileExpressionWithStaticContext
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr._
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.trans.XPathException

class XXFormsEvaluateAVT extends XFormsFunction {

  override def iterate(xpathContext: XPathContext) = {
    val (avtExpression, newXPathContext) = prepareExpressionSaxonNoPool(xpathContext, argument(0), isAVT = true)
    avtExpression.iterate(newXPathContext)
  }

  // Needed by prepareExpression()
  override def checkArguments(visitor: ExpressionVisitor): Unit =
    copyStaticContextIfNeeded(visitor)

  override def getIntrinsicDependencies = StaticProperty.DEPENDS_ON_FOCUS
}