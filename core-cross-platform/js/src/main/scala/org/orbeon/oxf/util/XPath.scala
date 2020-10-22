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
import org.orbeon.saxon.expr.{Literal, XPathContext}
import org.orbeon.xml.NamespaceMapping
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om.{GroundedValue, StructuredQName, TreeInfo}
import org.orbeon.saxon.tree.wrapper.VirtualNode
import org.orbeon.saxon.utils.Configuration

object StaticXPath extends StaticXPathTrait {

  type SaxonConfiguration   = Configuration
  type DocumentNodeInfoType = TreeInfo
  type VirtualNodeType      = VirtualNode

  type VariableResolver = (StructuredQName, XPathContext) => GroundedValue

  val GlobalConfiguration: SaxonConfiguration = ???

  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression = ???

  def orbeonDomToTinyTree(doc: Document): DocumentNodeInfoType = ???
}