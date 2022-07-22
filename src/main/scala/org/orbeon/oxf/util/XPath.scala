/**
 *  Copyright (C) 2013 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import java.util.{List => JList}

import javax.xml.transform._
import javax.xml.transform.sax.SAXSource
import org.orbeon.datatypes.{ExtendedLocationData, LocationData}
import org.orbeon.dom.saxon.OrbeonDOMObjectModel
import org.orbeon.oxf.common.{OrbeonLocationException, ValidationException}
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.StaticXPath.{CompiledExpression, GlobalDocumentNumberAllocator}
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.dom.XmlExtendedLocationData
import org.orbeon.oxf.xml.{ParserConfiguration, ShareableXPathStaticContext, XMLParsing}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.{AnyItemType, Type}
import org.orbeon.saxon.event.{PipelineConfiguration, Receiver}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.{FunctionLibrary, JavaExtensionLibrary}
import org.orbeon.saxon.om._
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.sxpath.{XPathEvaluator, XPathExpression, XPathStaticContext}
import org.orbeon.saxon.value.{AtomicValue, SequenceExtent, Value}
import org.orbeon.scaxon.Implicits
import org.orbeon.xml.NamespaceMapping
import org.xml.sax.{InputSource, XMLReader}

import scala.util.Try
import scala.util.control.NonFatal

import scala.collection.compat._


object XPath extends XPathTrait {

  type SaxonConfiguration = Configuration
  type VariableResolver = (StructuredQName, XPathContext) => ValueRepresentation

  // Context accessible during XPath evaluation
  // 2015-05-27: We use a ThreadLocal for this. Ideally we should pass this with the XPath dynamic context, via the Controller
  // for example. One issue is that we have native Java/Scala functions called via XPath which need access to FunctionContext
  // but don't have access to the XPath dynamic context anymore. This could be fixed if we implement these native functions as
  // Saxon functions, possibly generally via https://github.com/orbeon/orbeon-forms/issues/2214.
  private val GlobalNamePool = StaticXPath.GlobalNamePool

  // HACK: We can't register new converters directly, so we register an external object model, even though this is
  // not going to be used by Saxon as such. But Saxon tests for external object model when looking for a JPConverter,
  // so it will find and use this for converting types from Java/Scala.
  private val GlobalDataConverter = new ExternalObjectModel {

    def getIdentifyingURI = "http://scala-lang.org/"
    def sendSource(source: Source, receiver: Receiver, pipe: PipelineConfiguration) = false
    def getDocumentBuilder(result: Result) = null
    def getNodeListCreator(node: scala.Any) = null
    def unravel(source: Source, config: Configuration) = null

    val SupportedScalaToSaxonClasses = List(classOf[Iterable[_]], classOf[Option[_]], classOf[Iterator[_]])
    val SupportedSaxonToScalaClasses = List(classOf[List[_]], classOf[Option[_]], classOf[Iterator[_]])

    val ScalaToSaxonConverter = new JPConverter {

      private def anyToItem(any: Any, context: XPathContext) =
        Option(Value.asItem(JPConverter.allocate(any.getClass, context.getConfiguration).convert(any, context)))

      def convert(any: Any, context: XPathContext): ValueRepresentation = any match {
        case v: Iterable[_] => new SequenceExtent(v flatMap (anyToItem(_, context)) toArray)
        case v: Option[_]   => convert(v.toList, context)
        case v: Iterator[_] => convert(v.toList, context) // we have to return a ValueRepresentation
      }

      def getItemType = AnyItemType.getInstance
    }

    val SaxonToScalaConverter = new PJConverter {

      // NOTE: Because of Java erasure, we cannot statically know whether we have e.g. Option[DocumentInfo] or
      // Option[dom.Document]. So we have to decide whether to leave the contained nodes wrapped or not. We
      // decide to leave them unwrapped, so that a Scala method can be defined as:
      //
      //  def dataMaybeMigratedTo(data: DocumentInfo, metadata: Option[DocumentInfo])
      //
      private def itemToAny(item: Item, context: XPathContext) = item match {
        case v: AtomicValue =>
          val config = context.getConfiguration
          val th     = config.getTypeHierarchy

          val pj = PJConverter.allocate(config, v.getItemType(th), StaticProperty.EXACTLY_ONE, classOf[AnyRef])
          pj.convert(v, classOf[AnyRef], context)
        case v              => v
      }

      def convert(value: ValueRepresentation, targetClass: Class[_], context: XPathContext): AnyRef =
        if (targetClass.isAssignableFrom(classOf[List[_]])) {

          val values =
            for (item <- Implicits.asScalaIterator(Value.asIterator(value)))
            yield itemToAny(item, context)

          values.toList
        } else if (targetClass.isAssignableFrom(classOf[Option[_]])) {
          Implicits.asScalaIterator(Value.asIterator(value)).nextOption() map (itemToAny(_, context))
        } else if (targetClass.isAssignableFrom(classOf[Iterator[_]])) {
          Implicits.asScalaIterator(Value.asIterator(value)) map (itemToAny(_, context))
        } else {
          throw new IllegalStateException(targetClass.getName)
        }
    }

    def getJPConverter(targetClass: Class[_]) =
      if (SupportedScalaToSaxonClasses exists (_.isAssignableFrom(targetClass)))
        ScalaToSaxonConverter
      else
        null

    def getPJConverter(targetClass: Class[_]) =
      if (SupportedSaxonToScalaClasses exists (_.isAssignableFrom(targetClass)))
        SaxonToScalaConverter
      else
        null
  }

  // Global Saxon configuration with a global name pool
  val GlobalConfiguration = new Configuration {

    super.setNamePool(GlobalNamePool)
    super.setDocumentNumberAllocator(GlobalDocumentNumberAllocator)
    super.registerExternalObjectModel(GlobalDataConverter)
    super.registerExternalObjectModel(OrbeonDOMObjectModel)
    super.getExtensionBinder("java").asInstanceOf[JavaExtensionLibrary]
      .declareJavaClass("http://www.w3.org/2005/xpath-functions/math", classOf[ExsltWithPiAndPowFunctions])

    // See https://github.com/orbeon/orbeon-forms/issues/3468
    // We decide not to use a pool for now as creating a parser is fairly cheap
    override def getSourceParser: XMLReader = getParser
    override def getStyleParser : XMLReader = getParser

    private def getParser =
      XMLParsing.newSAXParser(ParserConfiguration.Plain).getXMLReader

    // These are called if the parser came from `getSourceParser` or `getStyleParser`
    override def reuseSourceParser(parser: XMLReader)                          : Unit = ()
    override def reuseStyleParser(parser: XMLReader)                           : Unit = ()
    override def setNamePool(targetNamePool: NamePool)                         : Unit = throwException()
    override def setDocumentNumberAllocator(allocator: DocumentNumberAllocator): Unit = throwException()
    override def registerExternalObjectModel(model: ExternalObjectModel)       : Unit = throwException()
    override def setAllowExternalFunctions(allowExternalFunctions: Boolean)    : Unit = throwException()
    override def setConfigurationProperty(name: String, value: AnyRef)         : Unit = throwException()

    private def throwException() = throw new IllegalStateException("Global XPath configuration is read-only")
  }

  // New mutable configuration sharing the same name pool and converters, for use by mutating callers
  def newConfiguration =
    new Configuration {
      setNamePool(GlobalNamePool)
      setDocumentNumberAllocator(GlobalDocumentNumberAllocator)
      registerExternalObjectModel(GlobalDataConverter)
      registerExternalObjectModel(OrbeonDOMObjectModel)
    }

  // Compile the expression and return a literal value if possible
  def evaluateAsLiteralIfPossible(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): Option[Literal] = {
    val compiled =
      compileExpression(
        xpathString,
        namespaceMapping,
        locationData,
        functionLibrary,
        avt
      )

    compiled.expression.getInternalExpression match {
      case literal: Literal => Some(literal)
      case _                => None
    }
  }

  // Create and compile an expression
  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression = {
    CompiledExpression(
      compileExpressionWithStaticContext(
        new ShareableXPathStaticContext(GlobalConfiguration, namespaceMapping, functionLibrary),
        xpathString,
        avt
      ),
      xpathString,
      locationData
    )
  }

  // Create and compile an expression
  def compileExpressionWithStaticContext(
    staticContext : XPathStaticContext,
    xpathString   : String,
    avt           : Boolean
  ): XPathExpression =
    if (avt) {
      val tempExpression = AttributeValueTemplate.make(xpathString, -1, staticContext)
      prepareExpressionForAVT(staticContext, tempExpression)
    } else {
      val evaluator = new XPathEvaluator(GlobalConfiguration)
      evaluator.setStaticContext(staticContext)
      evaluator.createExpression(xpathString)
    }

  def compileExpressionMinimal(
    staticContext : XPathStaticContext,
    xpathString   : String,
    avt           : Boolean
  ): Expression =
    if (avt) {
      val exp = AttributeValueTemplate.make(xpathString, -1, staticContext)
      exp.setContainer(staticContext)
      exp
    } else {
      val exp = ExpressionTool.make(xpathString, staticContext, 0, -1, 1, false)
      exp.setContainer(staticContext)
      exp
    }

  // Ideally: add this to Saxon XPathEvaluator
  private def prepareExpressionForAVT(staticContext: XPathStaticContext, expression: Expression): XPathExpression = {
    // Based on XPathEvaluator.createExpression()
    expression.setContainer(staticContext)
    val visitor = ExpressionVisitor.make(staticContext)
    visitor.setExecutable(staticContext.getExecutable)
    var newExpression = visitor.typeCheck(expression, Type.ITEM_TYPE)
    newExpression = visitor.optimize(newExpression, Type.ITEM_TYPE)
    val map = staticContext.getStackFrameMap
    val numberOfExternalVariables = map.getNumberOfVariables
    ExpressionTool.allocateSlots(expression, numberOfExternalVariables, map)

    // Set an evaluator as later it might be requested
    val evaluator = new XPathEvaluator(GlobalConfiguration)
    evaluator.setStaticContext(staticContext)

    // See history for comment on CustomXPathExpression vs. modifying Saxon
    new XPathExpression(evaluator, newExpression) {
      setStackFrameMap(map, numberOfExternalVariables)
    }
  }

  // Return either a NodeInfo for nodes, a native Java value for atomic values, or null
  // 4 callers
  def evaluateSingle(
    contextItems        : JList[Item],
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

      val dynamicContext = xpathExpression.createDynamicContext(contextItem, position)
      val xpathContext   = dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor]

      xpathContext.getController.setUserData(
        classOf[ShareableXPathStaticContext].getName,
        "variableResolver",
        variableResolver
      )

      withFunctionContext(functionContext) {
        val iterator = xpathExpression.iterate(dynamicContext)
        iterator.next() match {
          case atomicValue: AtomicValue => Value.convertToJava(atomicValue)
          case nodeInfo: NodeInfo       => nodeInfo
          case null                     => null
          case _                        => throw new IllegalStateException // Saxon guarantees that an Item is either AtomicValue or NodeInfo
        }
      }
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

  val URIResolver = new URIResolver {
    def resolve(href: String, base: String): Source =
      try {
        // Saxon Document.makeDoc() changes the base to "" if it is null
        // NOTE: We might use TransformerURIResolver/ExternalContext in the future (ThreadLocal)
        val url = URLFactory.createURL(if (base == "") null else base, href)
        new SAXSource(
          XMLParsing.newXMLReader(ParserConfiguration.Plain),
          new InputSource(url.openStream)
        )
      } catch {
        case NonFatal(t) => throw new TransformerException(t)
      }
  }

  // Whether the given string contains a well-formed XPath 2.0 expression.
  // NOTE: Ideally we would like the parser to not throw as this is time-consuming, but not sure how to achieve that
  // NOTE: We should probably just do the parse and typeCheck parts and skip simplify and a few smaller operations
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
}
