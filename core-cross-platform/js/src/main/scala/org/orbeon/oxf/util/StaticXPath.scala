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

import org.orbeon.datatypes.LocationData
import org.orbeon.dom
import org.orbeon.dom.io.SAXWriter
import org.orbeon.io.StringBuilderWriter
import org.orbeon.saxon.tree.util.DocumentNumberAllocator
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.xml.dom.LocationDocumentResult
import org.orbeon.oxf.xml.{ForwardingXMLReceiver, ShareableXPathStaticContext, XMLReceiver}
import org.orbeon.saxon.expr.{XPathContext, parser}
import org.orbeon.saxon.expr.parser.OptimizerOptions
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.orbeon.saxon.model.{BuiltInAtomicType, ItemType}
import org.orbeon.saxon.om
import org.orbeon.saxon.sxpath.{XPathEvaluator, XPathExpression, XPathStaticContext}
import org.orbeon.saxon.tree.wrapper.VirtualNode
import org.orbeon.saxon.utils.Configuration

import java.lang as jl
import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult


object StaticXPath extends StaticXPathTrait {

  type SaxonConfiguration      = Configuration
  type DocumentNodeInfoType    = om.NodeInfo // TODO: check usages that had `DocumentInfo`
  type VirtualNodeType         = VirtualNode
  type ValueRepresentationType = om.GroundedValue
  type AxisType                = Int
  type PathMapType             = parser.PathMap
  type SchemaTypeType          = ItemType

  def PrecedingSiblingAxisType: AxisType = om.AxisInfo.PRECEDING_SIBLING
  def NamespaceAxisType: AxisType = om.AxisInfo.NAMESPACE
  def IntegerType: SchemaTypeType = BuiltInAtomicType.INTEGER

  type VariableResolver = (om.StructuredQName, XPathContext) => ValueRepresentationType

  val GlobalNamePool = new om.NamePool
  protected[util] val GlobalDocumentNumberAllocator = new DocumentNumberAllocator

  val GlobalConfiguration: SaxonConfiguration = new Configuration {
    super.setNamePool(GlobalNamePool)
    super.setDocumentNumberAllocator(GlobalDocumentNumberAllocator)
    optimizerOptions = new OptimizerOptions("vmt") // FIXME: temporarily remove the "l" option which fails
    setDefaultRegexEngine("J") // the "S" (Saxon) engine is broken at this time

    // TODO
  }

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

  def orbeonDomToTinyTree(doc: dom.Document): DocumentNodeInfoType = {

    val treeBuilder = om.TreeModel.TINY_TREE.makeBuilder(GlobalConfiguration.makePipelineConfiguration)

    val handler =
      new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    val writer =
      new SAXWriter                  |!>
      (_.contentHandler = handler) |!>
      (_.lexicalHandler = handler) |!>
      (_. setDTDHandler(handler))

    writer.write(doc)

    // Q: What if it's not a document but an element?
    treeBuilder.getCurrentRoot
  }

  def tinyTreeToOrbeonDom(nodeInfo: om.NodeInfo): dom.Document = {
    val identity = new SaxonTransformerFactory(GlobalConfiguration).newTransformer
    val documentResult = new LocationDocumentResult
    identity.transform(nodeInfo, documentResult)
    documentResult.getDocument
  }

  def tinyTreeToString(nodeInfo: om.NodeInfo): String = {
    val identity = new SaxonTransformerFactory(GlobalConfiguration).newTransformer
    identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
    val writer = new StringBuilderWriter(new jl.StringBuilder)
    identity.transform(nodeInfo, new StreamResult(writer))
    writer.result
  }

  def newTinyTreeReceiver: (XMLReceiver, () => DocumentNodeInfoType) = {

    val treeBuilder = om.TreeModel.TINY_TREE.makeBuilder(GlobalConfiguration.makePipelineConfiguration)

    val handler =
      new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    (
      new ForwardingXMLReceiver(handler, handler),
      () => treeBuilder.getCurrentRoot
    )
  }

  val EmptyDocument: DocumentNodeInfoType = {

    val treeBuilder = om.TreeModel.TINY_TREE.makeBuilder(GlobalConfiguration.makePipelineConfiguration)

    val handler =
      new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    handler.startDocument()
    handler.endDocument()

    // Q: What if it's not a document but an element?
    treeBuilder.getCurrentRoot
  }

  def compileExpressionWithStaticContext(
    staticContext : XPathStaticContext,
    xpathString   : String,
    avt           : Boolean
  ): XPathExpression = {
    val evaluator = new XPathEvaluator(GlobalConfiguration)
    evaluator.setStaticContext(staticContext)
    if (avt)
      evaluator.createValueTemplateExpression(xpathString)
    else
      evaluator.createExpression(xpathString)
  }

  def expressionType(xpe: XPathExpression): SchemaTypeType =
    xpe.getInternalExpression.getItemType
}