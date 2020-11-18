package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.xml.NamespaceMapping


trait XPathTrait {

  // Marker for XPath function context
  trait FunctionContext

  // To report timing information
  type Reporter = (String, Long) => Unit

  // Context accessible during XPath evaluation
  // 2015-05-27: We use a ThreadLocal for this. Ideally we should pass this with the XPath dynamic context, via the Controller
  // for example. One issue is that we have native Java/Scala functions called via XPath which need access to FunctionContext
  // but don't have access to the XPath dynamic context anymore. This could be fixed if we implement these native functions as
  // Saxon functions, possibly generally via https://github.com/orbeon/orbeon-forms/issues/2214.
  private val xpathContextDyn = new DynamicVariable[FunctionContext]

  def withFunctionContext[T](functionContext: FunctionContext)(thunk: => T): T = {
    xpathContextDyn.withValue(functionContext) {
      thunk
    }
  }

  // Return the currently scoped function context if any
  def functionContext: Option[FunctionContext] = xpathContextDyn.value

  val GlobalConfiguration: StaticXPath.SaxonConfiguration

  def evaluateAsString(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : StaticXPath.CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : StaticXPath.VariableResolver)(implicit
    reporter            : Reporter
  ): String

  def evaluateSingle(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : StaticXPath.CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : StaticXPath.VariableResolver)(implicit
    reporter            : Reporter
  ): AnyRef

  def isXPath2ExpressionOrValueTemplate(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): Boolean
}
