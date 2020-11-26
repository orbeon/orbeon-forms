package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.om.Item
import org.orbeon.saxon.utils.Configuration
import org.orbeon.xml.NamespaceMapping


object XPath extends XPathTrait {

  val GlobalConfiguration: StaticXPath.SaxonConfiguration = new Configuration {

    super.setNamePool(StaticXPath.GlobalNamePool)
    super.setDocumentNumberAllocator(StaticXPath.GlobalDocumentNumberAllocator)

    // TODO
  }

  def evaluateAsString(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : VariableResolver)(implicit
    reporter            : Reporter
  ): String = ???

  def evaluateSingle(
    contextItems        : ju.List[Item],
    contextPosition     : Int,
    compiledExpression  : CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : VariableResolver)(implicit
    reporter            : Reporter
  ): AnyRef = ???

  def isXPath2ExpressionOrValueTemplate(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): Boolean = ???
}
