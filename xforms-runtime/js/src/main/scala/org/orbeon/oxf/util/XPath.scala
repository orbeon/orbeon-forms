package org.orbeon.oxf.util

import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.dom.saxon.TypedNodeWrapper
import org.orbeon.dom.saxon.TypedNodeWrapper.TypedValueException
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.ShareableXPathStaticContext
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.expr.parser.{ExpressionTool, OptimizerOptions}
import org.orbeon.saxon.expr.{Expression, XPathContextMajor, XPathContextMinor}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.sxpath.{XPathDynamicContext, XPathExpression, XPathStaticContext}
import org.orbeon.saxon.tree.iter.ManualIterator
import org.orbeon.saxon.utils.Configuration
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.xforms.XFormsCrossPlatformSupport
import org.orbeon.xml.NamespaceMapping

import java.{util => ju}
import scala.util.Try
import scala.util.control.NonFatal


object XPath extends XPathTrait {

  private val DebugExplainExpressions = false
  private val LogAndExplainErrors     = false

  val GlobalConfiguration: StaticXPath.SaxonConfiguration = new Configuration {

    super.setNamePool(StaticXPath.GlobalNamePool)
    super.setDocumentNumberAllocator(StaticXPath.GlobalDocumentNumberAllocator)
    optimizerOptions = new OptimizerOptions("vmt") // FIXME: temporarily remove the "l" option which fails
    setDefaultRegexEngine("J") // the "S" (Saxon) engine is broken at this time

    // TODO
  }

  def evaluateSingle(
    contextItems        : ju.List[om.Item],
    contextPosition     : Int,
    compiledExpression  : CompiledExpression,
    functionContext     : FunctionContext,
    variableResolver    : VariableResolver)(implicit
    reporter            : Reporter
  ): Any =
    withEvaluation(compiledExpression) { xpathExpression =>

      Logger.debug(s"xxxx evaluateSingle for `${compiledExpression.string}`")

      val (contextItem, contextPos) = adjustContextItem(contextItems, contextPosition)

      val (dynamicContext, xpathContext) =
        newDynamicAndMajorContexts(xpathExpression, contextItem, contextPos, contextItems.size())

      xpathContext.getController.setUserData(
        classOf[ShareableXPathStaticContext].getName,
        "variableResolver",
        variableResolver
      )

      if (DebugExplainExpressions) {
        import org.orbeon.saxon.lib.StandardLogger

        import _root_.java.io.PrintStream

        xpathExpression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
      }

//      if (compiledExpression.string.contains("""oxf.fr.detail.pdf.disable-if-invalid""")) {
//        println(s"xxxx evaluateSingle for `$compiledExpression`")
//
//        val r = variableResolver(new om.StructuredQName("", "", "app"), xpathContext)
//
//        println(s"xxxx result is $r, ${r.getClass.getName}")
//      }

      withFunctionContext(functionContext) {
        val iterator = xpathExpression.iterate(dynamicContext)
        iterator.next() match {
          case atomicValue: AtomicValue => om.SequenceTool.convertToJava(atomicValue)
          case nodeInfo: om.NodeInfo    => nodeInfo
          case null                     => null
          case _                        => throw new IllegalStateException // Saxon guarantees that an Item is either AtomicValue or NodeInfo
        }
      }
    }

  def isXPath2ExpressionOrValueTemplate(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): Boolean =
    (avt || xpathString.nonAllBlank) &&
    Try(
      compileExpressionMinimal(
        staticContext = new ShareableXPathStaticContext(
          XPath.GlobalConfiguration,
          namespaceMapping,
          functionLibrary
        ),
        xpathString   = xpathString,
        avt           = avt
      )
    ).isSuccess

  def compileExpressionMinimal(
    staticContext : XPathStaticContext,
    xpathString   : String,
    avt           : Boolean
  ): Expression =
    if (avt)
      AttributeValueTemplate.make(xpathString, staticContext)
    else
      ExpressionTool.make(xpathString, staticContext, 0, -1, null)

  def adjustContextItem(contextItems: ju.List[om.Item], contextPosition: Int): (om.Item, Int) =
    if (contextPosition > 0 && contextPosition <= contextItems.size)
      (contextItems.get(contextPosition - 1), contextPosition)
    else
      (null, 0)

  def newDynamicAndMajorContexts(
    expression      : XPathExpression,
    contextItem     : om.Item,
    contextPosition : Int,
    contextSize     : Int
  ): (XPathDynamicContext, XPathContextMajor) = {

    val dynamicContext = expression.createDynamicContext(contextItem)
    val xpcm           = dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor]

    if (contextItem != null) {
      xpcm.currentIterator = new ManualIterator(contextItem, contextPosition)
      xpcm.last           = new XPathContextMinor.LastValue(contextSize)
    }

    (dynamicContext, xpcm)
  }

  private def withEvaluation[T](expression: CompiledExpression)(body: XPathExpression => T)(implicit reporter: Reporter): T =
    try {
      if (reporter ne null) {
        val startTime = System.nanoTime
        val result = body(expression.expression)
        val totalTimeMicroSeconds = (System.nanoTime - startTime) / 1000 // never smaller than 1000 ns on OS X
        if (totalTimeMicroSeconds > 0)
          reporter(expression.string, totalTimeMicroSeconds)

        result
      } else
        body(expression.expression)
    } catch {
      case t if XFormsCrossPlatformSupport.getRootThrowable(t).isInstanceOf[TypedValueException] =>
        throw handleXPathException(t, expression.string, "evaluating XPath expression", expression.locationData)
      case NonFatal(t) =>

        if (LogAndExplainErrors) {
          import org.orbeon.saxon.lib.StandardLogger
          import _root_.java.io.PrintStream
          expression.expression.getInternalExpression.explain(new StandardLogger(new PrintStream(System.out)))
        }

        throw handleXPathException(t, expression.string, "evaluating XPath expression", expression.locationData)
    }

  private def handleXPathException(
    throwable    : Throwable,
    xpathString  : String,
    description  : String,
    locationData : LocationData
  ): ValidationException = {

    val validationException =
      OrbeonLocationException.wrapException(
        throwable,
        XmlExtendedLocationData(locationData, Option(description), List("expression" -> xpathString))
      )

    // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
    // so we need to explicitly add them.
    if (locationData.isInstanceOf[ExtendedLocationData])
      validationException.addLocationData(locationData)

    validationException
  }
}
