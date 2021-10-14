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

import java.{lang => jl}
import javax.xml.transform.stream.StreamResult
import javax.xml.transform.{OutputKeys, Result, Source}
import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.io.SAXWriter
import org.orbeon.io.StringBuilderWriter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.xml.dom.LocationDocumentResult
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, ShareableXPathStaticContext, XMLReceiver}
import org.orbeon.saxon.`type`.{BuiltInAtomicType, ItemType, Type}
import org.orbeon.saxon.event.{ComplexContentOutputter, NamespaceReducer, Sender}
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.{FunctionLibrary, JavaExtensionLibrary}
import org.orbeon.saxon.style.AttributeValueTemplate
import org.orbeon.saxon.sxpath.{XPathEvaluator, XPathExpression, XPathStaticContext}
import org.orbeon.saxon.tinytree.TinyBuilder
import org.orbeon.saxon.trans.XPathException
import org.orbeon.saxon.{Configuration, Controller, TransformerFactoryImpl, om}
import org.orbeon.xml.NamespaceMapping
import org.xml.sax.{SAXParseException, XMLReader}


object StaticXPath extends StaticXPathTrait {

  type SaxonConfiguration      = Configuration
  type DocumentNodeInfoType    = om.DocumentInfo
  type VirtualNodeType         = om.VirtualNode
  type ValueRepresentationType = om.ValueRepresentation
  type AxisType                = Byte
  type PathMapType             = PathMap
  type SchemaTypeType          = ItemType

  def PrecedingSiblingAxisType: AxisType = om.Axis.PRECEDING_SIBLING
  def NamespaceAxisType: AxisType = om.Axis.NAMESPACE
  def IntegerType: SchemaTypeType = BuiltInAtomicType.INTEGER

  type VariableResolver = (om.StructuredQName, XPathContext) => ValueRepresentationType

  // Context accessible during XPath evaluation
  // 2015-05-27: We use a ThreadLocal for this. Ideally we should pass this with the XPath dynamic context, via the Controller
  // for example. One issue is that we have native Java/Scala functions called via XPath which need access to FunctionContext
  // but don't have access to the XPath dynamic context anymore. This could be fixed if we implement these native functions as
  // Saxon functions, possibly generally via https://github.com/orbeon/orbeon-forms/issues/2214.
  val GlobalNamePool = new om.NamePool
  protected[util] val GlobalDocumentNumberAllocator = new om.DocumentNumberAllocator

  // Global Saxon configuration with a global name pool
  // This configuration doesn't support creating parsers. It is only used for compiling expressions.
  val GlobalConfiguration = new Configuration {

    super.setNamePool(GlobalNamePool)
    super.setDocumentNumberAllocator(GlobalDocumentNumberAllocator)
    super.getExtensionBinder("java").asInstanceOf[JavaExtensionLibrary]
      .declareJavaClass("http://www.w3.org/2005/xpath-functions/math", classOf[org.orbeon.saxon.exslt.Math])

    // See https://github.com/orbeon/orbeon-forms/issues/3468
    // We decide not to use a pool for now as creating a parser is fairly cheap
    override def getSourceParser: XMLReader = ???
    override def getStyleParser : XMLReader = ???

    // These are called if the parser came from `getSourceParser` or `getStyleParser`
    override def reuseSourceParser(parser: XMLReader)                             : Unit = ()
    override def reuseStyleParser(parser: XMLReader)                              : Unit = ()
    override def setNamePool(targetNamePool: om.NamePool)                         : Unit = throwException()
    override def setDocumentNumberAllocator(allocator: om.DocumentNumberAllocator): Unit = throwException()
    override def registerExternalObjectModel(model: om.ExternalObjectModel)       : Unit = throwException()
    override def setAllowExternalFunctions(allowExternalFunctions: Boolean)       : Unit = throwException()
    override def setConfigurationProperty(name: String, value: AnyRef)            : Unit = throwException()

    private def throwException() = throw new IllegalStateException("Global XPath configuration is read-only")
  }

  def orbeonDomToTinyTree(doc: dom.Document): DocumentNodeInfoType = {

    val (receiver, result) = newTinyTreeReceiver

    val writer =
      new SAXWriter                     |!>
        (_.setContentHandler(receiver)) |!>
        (_.setLexicalHandler(receiver))

    writer.write(doc)

    result()
  }

  def tinyTreeToOrbeonDom(nodeInfo: om.NodeInfo): dom.Document = {
    val identity = new IdentityTransformerWithFixup(GlobalConfiguration)
    val documentResult = new LocationDocumentResult
    identity.transform(nodeInfo, documentResult)
    documentResult.getDocument
  }

  def tinyTreeToString(nodeInfo: om.NodeInfo): String = {
    val identity = new IdentityTransformerWithFixup(GlobalConfiguration)
    identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    val writer = new StringBuilderWriter(new jl.StringBuilder)
    identity.transform(nodeInfo, new StreamResult(writer))
    writer.result
  }

  def newTinyTreeReceiver: (XMLReceiver, () => DocumentNodeInfoType) = {

    val treeBuilder = new TinyBuilder

    val handler =
      new TransformerFactoryImpl(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    (
      new ForwardingXMLReceiver(handler, handler),
      () => treeBuilder.getCurrentRoot.asInstanceOf[DocumentNodeInfoType]
    )
  }

  // Custom version of Saxon's IdentityTransformer which hooks up a `ComplexContentOutputter`
  private class IdentityTransformerWithFixup(config: Configuration) extends Controller(config) {

    override def transform(source: Source, result: Result): Unit =
      try {
        val pipelineConfig = makePipelineConfiguration

        val receiver =
          config.getSerializerFactory.getReceiver(result, pipelineConfig, getOutputProperties)

        // To remove duplicate namespace declarations
        val reducer = new NamespaceReducer
        reducer.setUnderlyingReceiver(receiver)
        reducer.setPipelineConfiguration(pipelineConfig)

        // To fixup namespaces
        val cco = new ComplexContentOutputter
        cco.setHostLanguage(pipelineConfig.getHostLanguage)
        cco.setPipelineConfiguration(pipelineConfig)
        cco.setReceiver(reducer)

        new Sender(pipelineConfig).send(source, cco, true)
      } catch {
        case xpe: XPathException =>
          xpe.getException match {
            case spe: SAXParseException if ! spe.getException.isInstanceOf[RuntimeException] => // NOP
            case _ => reportFatalError(xpe)
          }
          throw xpe
      }
  }

  val EmptyDocument: DocumentNodeInfoType = {

    val treeBuilder = new TinyBuilder

    val handler =
      new TransformerFactoryImpl(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    handler.startDocument()
    handler.endDocument()

    treeBuilder.getCurrentRoot.asInstanceOf[DocumentNodeInfoType]
  }

  // Create and compile an expression
  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression =
    CompiledExpression(
      compileExpressionWithStaticContext(
        new ShareableXPathStaticContext(GlobalConfiguration, namespaceMapping, functionLibrary),
        xpathString,
        avt
      ),
      xpathString,
      locationData
    )

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
  private def prepareExpressionForAVT(staticContext: XPathStaticContext, expression: Expression) = {
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

  def expressionType(xpe: XPathExpression): SchemaTypeType = {
    val internalExpr = xpe.getInternalExpression
    internalExpr.getItemType(internalExpr.getExecutable.getConfiguration.getTypeHierarchy)
  }
}
