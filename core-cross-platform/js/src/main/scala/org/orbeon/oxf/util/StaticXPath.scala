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
import org.orbeon.dom.Document
import org.orbeon.dom.io.SAXWriter
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.jaxp.SaxonTransformerFactory
import org.orbeon.saxon.om
import org.orbeon.saxon.tree.wrapper.VirtualNode
import org.orbeon.saxon.utils.Configuration

object StaticXPath extends StaticXPathTrait {

  type SaxonConfiguration      = Configuration
  type DocumentNodeInfoType    = om.TreeInfo
  type VirtualNodeType         = VirtualNode
  type ValueRepresentationType = om.GroundedValue
  type AxisType                = om.AxisInfo.type

  type VariableResolver = (om.StructuredQName, XPathContext) => ValueRepresentationType

  val GlobalConfiguration: SaxonConfiguration = ???

  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression = ???

  def orbeonDomToTinyTree(doc: Document): DocumentNodeInfoType = {

    val treeBuilder = om.TreeModel.TINY_TREE.makeBuilder(GlobalConfiguration.makePipelineConfiguration)

    val handler =
      new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    val writer =
      new SAXWriter                  |!>
      (_.setContentHandler(handler)) |!>
      (_.setLexicalHandler(handler)) |!>
      (_.setDTDHandler(handler))

    writer.write(doc)

    // Q: What if it's not a document but an element?
    treeBuilder.getCurrentRoot.asInstanceOf[DocumentNodeInfoType]
  }

  val EmptyDocument: DocumentNodeInfoType = {

    val treeBuilder = om.TreeModel.TINY_TREE.makeBuilder(GlobalConfiguration.makePipelineConfiguration)

    val handler =
      new SaxonTransformerFactory(GlobalConfiguration).newTransformerHandler |!>
        (_.setResult(treeBuilder))

    handler.startDocument()
    handler.endDocument()

    // Q: What if it's not a document but an element?
    treeBuilder.getCurrentRoot.asInstanceOf[DocumentNodeInfoType]
  }
}