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
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.sxpath.XPathExpression
import org.orbeon.xml.NamespaceMapping

// TODO: Check what really belongs to the form compilation and split out the rest.
trait StaticXPathTrait {

  // These types are temporary indirections while we have two incompatible versions of Saxon (9.1 and 10)
  type SaxonConfiguration
  type DocumentNodeInfoType
  type VirtualNodeType
  type ValueRepresentationType
  type AxisType

  // Used by `ShareableXPathStaticContext`
  type VariableResolver

  // Compiled expression with source information
  case class CompiledExpression(expression: XPathExpression, string: String, locationData: LocationData)

  def makeStringExpression(expression: String): String =  "string((" + expression + ")[1])"
  def makeBooleanExpression(expression: String): String =  "boolean(" + expression + ")"

  val GlobalConfiguration: SaxonConfiguration

  // Create and compile an expression
  def compileExpression(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    locationData     : LocationData,
    functionLibrary  : FunctionLibrary,
    avt              : Boolean)(implicit
    logger           : IndentedLogger
  ): CompiledExpression

  def orbeonDomToTinyTree(doc: Document): DocumentNodeInfoType
  def tinyTreeToOrbeonDom(nodeInfo: om.NodeInfo): Document
  val EmptyDocument: DocumentNodeInfoType
}
