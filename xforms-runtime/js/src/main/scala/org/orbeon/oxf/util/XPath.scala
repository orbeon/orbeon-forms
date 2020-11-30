package org.orbeon.oxf.util

import java.{util => ju}

import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, VariableResolver}
import org.orbeon.oxf.xml.ShareableXPathStaticContext
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.saxon.expr.XPathContextMajor
import org.orbeon.saxon.expr.parser.OptimizerOptions
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.om.{Item, NodeInfo, SequenceTool}
import org.orbeon.saxon.sxpath.XPathExpression
import org.orbeon.saxon.utils.Configuration
import org.orbeon.saxon.value.AtomicValue
import org.orbeon.xml.NamespaceMapping

import scala.util.control.NonFatal


object XPath extends XPathTrait {

  val GlobalConfiguration: StaticXPath.SaxonConfiguration = new Configuration {

    super.setNamePool(StaticXPath.GlobalNamePool)
    super.setDocumentNumberAllocator(StaticXPath.GlobalDocumentNumberAllocator)
    optimizerOptions = new OptimizerOptions("vmt") // FIXME: temporarily remove the "l" option which fails

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
  ): Any =
    withEvaluation(compiledExpression) { xpathExpression =>

      val (contextItem, position) =
        if (contextPosition > 0 && contextPosition <= contextItems.size)
          (contextItems.get(contextPosition - 1), contextPosition)
        else
          (null, 0)

      val dynamicContext = xpathExpression.createDynamicContext(contextItem) // XXX FIXME: `, position`?
      val xpathContext   = dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor]

      xpathContext.getController.setUserData(
        classOf[ShareableXPathStaticContext].getName,
        "variableResolver",
        variableResolver
      )

      withFunctionContext(functionContext) {
        val iterator = xpathExpression.iterate(dynamicContext)
        iterator.next() match {
          case atomicValue: AtomicValue => SequenceTool.convertToJava(atomicValue)
          case nodeInfo: NodeInfo       => nodeInfo
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
  ): Boolean = ???
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
      case NonFatal(t) =>
        throw handleXPathException(t, expression.string, "evaluating XPath expression", expression.locationData)
    }

  def handleXPathException(t: Throwable, xpathString: String, description: String, locationData: LocationData): ValidationException = {

    val validationException =
      OrbeonLocationException.wrapException(
        t,
        XmlExtendedLocationData(locationData, Option(description), List("expression" -> xpathString))
      )

    // Details of ExtendedLocationData passed are discarded by the constructor for ExtendedLocationData above,
    // so we need to explicitly add them.
    if (locationData.isInstanceOf[ExtendedLocationData])
      validationException.addLocationData(locationData)

    validationException
  }
}
