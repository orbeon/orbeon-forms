package org.orbeon.oxf.util

import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}

import java.util as ju
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.om.Item
import org.orbeon.xml.NamespaceMapping

import java.util.List as JList


// Marker for XPath function context
trait FunctionContext

trait XPathTrait {

  val Logger = LoggerFactory.createLogger("org.orbeon.xpath")

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

  // Return a string, or null if the expression returned an empty sequence
  // TODO: Should always return a string!
  // TODO: Check what we do upon NodeInfo
  // NOTE: callers tend to use string(foo), so issue of null/NodeInfo should not occur
  // 2 XForms usages
  def evaluateAsString(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : StaticXPath.CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : StaticXPath.VariableResolver
  )(implicit
    reporter            : Reporter
  ): String =
    Option(
      evaluateSingle(
        contextItems,
        contextPosition,
        compiledExpression,
        functionContext,
        variableResolver
      )
    ).map(_.toString).orNull

  def evaluateSingle(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : StaticXPath.CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : StaticXPath.VariableResolver
  )(implicit
    reporter            : Reporter
  ): Any

  def evaluateKeepItems(
    contextItems        : JList[Item],
    contextPosition     : Int,
    compiledExpression  : CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : VariableResolver
  )(implicit
    reporter            : Reporter
  ): LazyList[Item]

  def isXPath2ExpressionOrValueTemplate(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean
  )(implicit
    logger           : IndentedLogger
  ): Boolean

  def adjustContextItem(contextItems: ju.List[om.Item], contextPosition: Int): (om.Item, Int) =
    if (contextPosition > 0 && contextPosition <= contextItems.size)
      (contextItems.get(contextPosition - 1), contextPosition)
    else
      (null, 0)
}
