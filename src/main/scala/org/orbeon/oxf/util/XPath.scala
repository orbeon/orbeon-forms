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

import java.util.{List ⇒ JList}
import javax.xml.transform.sax.SAXSource
import javax.xml.transform.{Result, Source, TransformerException, URIResolver}

import org.apache.commons.lang3.StringUtils._
import org.orbeon.oxf.common.OrbeonLocationException
import org.orbeon.oxf.resources.URLFactory
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xml.dom4j.{ExtendedLocationData, LocationData}
import org.orbeon.oxf.xml.{NamespaceMapping, ShareableXPathStaticContext, XMLParsing}
import org.orbeon.saxon.Configuration
import org.orbeon.saxon.`type`.{AnyItemType, Type}
import org.orbeon.saxon.event.{PipelineConfiguration, Receiver}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om._
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.sxpath.{XPathEvaluator, XPathExpression, XPathStaticContext}
import org.orbeon.saxon.value.{AtomicValue, SequenceExtent, Value}
import org.orbeon.scaxon.XML
import org.xml.sax.InputSource

import scala.util.Try
import scala.util.control.NonFatal

object XPath {

    // Marker for XPath function context
    trait FunctionContext

    // To report timing information
    type Reporter = (String, Long) ⇒ Unit

    // To resolve a variable
    type VariableResolver = (StructuredQName, XPathContext) ⇒ ValueRepresentation

    // Context accessible during XPath evaluation
    private val xpathContextDyn = new DynamicVariable[FunctionContext]

    def withFunctionContext[T](functionContext: FunctionContext)(thunk: ⇒ T): T = {
        xpathContextDyn.withValue(functionContext) {
            thunk
        }
    }

    // Compiled expression with source information
    case class CompiledExpression(expression: XPathExpression, string: String, locationData: LocationData)

    def makeStringExpression(expression: String)  =  "string((" + expression + ")[1])"
    def makeBooleanExpression(expression: String) =  "boolean(" + expression + ")"

    private val GlobalNamePool = new NamePool

    // HACK: We can't register new converters directly, so we register an external object model, even though this is
    // not going to be used by Saxon as such. But Saxon tests for external object model when looking for a JPConverter,
    // so it will find and use this for converting types from Java/Scala.
    private val GlobalDataConverter = new ExternalObjectModel {
        def getIdentifyingURI = "http://scala-lang.org/"
        def sendSource(source: Source, receiver: Receiver, pipe: PipelineConfiguration) = false
        def getDocumentBuilder(result: Result) = null
        def getNodeListCreator(node: scala.Any) = null
        def unravel(source: Source, config: Configuration) = null

        val SupportedScalaToSaxonClasses = List(classOf[Traversable[_]], classOf[Option[_]], classOf[Iterator[_]])
        val SupportedSaxonToScalaClasses = List(classOf[List[_]], classOf[Option[_]], classOf[Iterator[_]])

        val ScalaToSaxonConverter = new JPConverter {

            private def anyToItem(any: Any, context: XPathContext) =
                Option(Value.asItem(JPConverter.allocate(any.getClass, context.getConfiguration).convert(any, context)))

            def convert(any: Any, context: XPathContext): ValueRepresentation = any match {
                case v: Traversable[_] ⇒ new SequenceExtent(v flatMap (anyToItem(_, context)) toArray)
                case v: Option[_]      ⇒ convert(v.toList, context)
                case v: Iterator[_]    ⇒ convert(v.toList, context) // we have to return a ValueRepresentation
            }

            def getItemType = AnyItemType.getInstance
        }

        val SaxonToScalaConverter = new PJConverter {

            // NOTE: Because of Java erasure, we cannot statically know whether we have e.g. Option[DocumentInfo] or
            // Option[dom4j.Document]. So we have to decide whether to leave the contained nodes wrapped or not. We
            // decide to leave them unwrapped, so that a Scala method can be defined as:
            //
            //  def dataMaybeMigratedTo(data: DocumentInfo, metadata: Option[DocumentInfo])
            //
            private def itemToAny(item: Item, context: XPathContext) = item match {
                case v: AtomicValue ⇒
                    val config = context.getConfiguration
                    val th     = config.getTypeHierarchy

                    val pj = PJConverter.allocate(config, v.getItemType(th), StaticProperty.EXACTLY_ONE, classOf[AnyRef])
                    pj.convert(v, classOf[AnyRef], context)
                case v              ⇒ v
            }

            def convert(value: ValueRepresentation, targetClass: Class[_], context: XPathContext): AnyRef =
                if (targetClass.isAssignableFrom(classOf[List[_]])) {

                    val values =
                        for (item ← XML.asScalaIterator(Value.asIterator(value)))
                        yield itemToAny(item, context)

                    values.toList
                } else if (targetClass.isAssignableFrom(classOf[Option[_]])) {
                    XML.asScalaIterator(Value.asIterator(value)).nextOption() map (itemToAny(_, context))
                } else if (targetClass.isAssignableFrom(classOf[Iterator[_]])) {
                    XML.asScalaIterator(Value.asIterator(value)) map (itemToAny(_, context))
                } else {
                    throw new IllegalStateException(targetClass.getName)
                }
        }

        def getJPConverter(targetClass: Class[_]) =
            if (SupportedScalaToSaxonClasses exists(_.isAssignableFrom(targetClass)))
                ScalaToSaxonConverter
            else
                null

        def getPJConverter(targetClass: Class[_]) =
            if (SupportedSaxonToScalaClasses exists(_.isAssignableFrom(targetClass)))
                SaxonToScalaConverter
            else
                null
    }

    // Global Saxon configuration with a global name pool
    val GlobalConfiguration = new Configuration {

        setNamePool(GlobalNamePool)
        registerExternalObjectModel(GlobalDataConverter)

        override def setAllowExternalFunctions(allowExternalFunctions: Boolean): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")

        override def setConfigurationProperty(name: String, value: AnyRef): Unit =
            throw new IllegalStateException("Global XPath configuration is read-only")
    }

    // New mutable configuration sharing the same name pool and converters, for use by mutating callers
    def newConfiguration =
        new Configuration {
            setNamePool(GlobalNamePool)
            setDocumentNumberAllocator(GlobalConfiguration.getDocumentNumberAllocator)
            registerExternalObjectModel(GlobalDataConverter)
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
            case literal: Literal ⇒ Some(literal)
            case _                ⇒ None
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
        val staticContext = new ShareableXPathStaticContext(GlobalConfiguration, namespaceMapping, functionLibrary)
        CompiledExpression(compileExpressionWithStaticContext(staticContext, xpathString, avt), xpathString, locationData)
    }

    // Create and compile an expression
    def compileExpressionWithStaticContext(staticContext: XPathStaticContext, xpathString: String, avt: Boolean): XPathExpression =
        if (avt) {
            val tempExpression = AttributeValueTemplate.make(xpathString, -1, staticContext)
            prepareExpressionForAVT(staticContext, tempExpression)
        } else {
            val evaluator = new XPathEvaluator(GlobalConfiguration)
            evaluator.setStaticContext(staticContext)
            evaluator.createExpression(xpathString)
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

    // Return the currently scoped function context if any
    def functionContext = xpathContextDyn.value

    // Return either a NodeInfo for nodes, a native Java value for atomic values, or null
    def evaluateSingle(
            contextItems: JList[Item],
            contextPosition: Int,
            compiledExpression: CompiledExpression,
            functionContext: FunctionContext,
            variableResolver: VariableResolver)
            (implicit reporter: Reporter) = {

        withEvaluation(compiledExpression) { xpathExpression ⇒

            val (contextItem, position) =
                if (contextPosition > 0 && contextPosition <= contextItems.size)
                    (contextItems.get(contextPosition - 1), contextPosition)
                else
                    (null, 0)

            val dynamicContext = xpathExpression.createDynamicContext(contextItem, position)
            val xpathContext = dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor]

            xpathContext.getController.setUserData(classOf[ShareableXPathStaticContext].getName, "variableResolver", variableResolver)

            withFunctionContext(functionContext) {
                val iterator = xpathExpression.iterate(dynamicContext)
                iterator.next() match {
                    case atomicValue: AtomicValue ⇒ Value.convertToJava(atomicValue)
                    case nodeInfo: NodeInfo       ⇒ nodeInfo
                    case null                     ⇒ null
                    case _                        ⇒ throw new IllegalStateException // Saxon guarantees that an Item is either AtomicValue or NodeInfo
                }
            }
        }
    }

    // Return a string, or null if the expression returned an empty sequence
    // TODO: Should always return a string!
    // TODO: Check what we do upon NodeInfo
    // NOTE: callers tend to use string(foo), so issue of null/NodeInfo should not occur
    def evaluateAsString(
            contextItems: JList[Item],
            contextPosition: Int,
            compiledExpression: CompiledExpression,
            functionContext: FunctionContext,
            variableResolver: VariableResolver)
            (implicit reporter: Reporter) =
        Option(evaluateSingle(contextItems, contextPosition, compiledExpression, functionContext, variableResolver)) map (_.toString) orNull

    private def withEvaluation[T](expression: CompiledExpression)(body: XPathExpression ⇒ T)(implicit reporter: Reporter): T =
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
            case NonFatal(t) ⇒ throw handleXPathException(t, expression.string, "evaluating XPath expression", expression.locationData)
        }

    def handleXPathException(t: Throwable, xpathString: String, description: String, locationData: LocationData) = {
        val validationException =
            OrbeonLocationException.wrapException(t, new ExtendedLocationData(locationData, Option(description), List("expression" → xpathString)))

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
                new SAXSource(XMLParsing.newXMLReader(XMLParsing.ParserConfiguration.PLAIN), new InputSource(url.openStream))
            } catch {
                case NonFatal(t) ⇒ throw new TransformerException(t)
            }
    }

    // Whether the given string contains a well-formed XPath 2.0 expression.
    // NOTE: Ideally we would like the parser to not throw as this is time-consuming, but not sure how to achieve that
    // NOTE: We should probably just do the parse and typeCheck parts and skip simplify and a few smaller operations
    def isXPath2Expression(xpathString: String, namespaceMapping: NamespaceMapping, locationData: LocationData)(implicit logger: IndentedLogger) =
        isNotBlank(xpathString) &&
        Try(compileExpression(xpathString, namespaceMapping, locationData, XFormsContainingDocument.getFunctionLibrary, avt = false)).isSuccess
}
