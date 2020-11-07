package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.saxon.om

object XPath extends XPathTrait {

  val GlobalConfiguration: StaticXPath.SaxonConfiguration = ???

  def evaluateAsString(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : VariableResolver)(implicit
    reporter            : Reporter
  ): String = ???


}
