/**
 * Copyright (C) 2011 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.xforms

import org.orbeon.datatypes.MaximumSize
import org.orbeon.oxf.util.IndentedLogger
import org.orbeon.oxf.util.StaticXPath.CompiledExpression
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.oxf.xforms.xbl.XBLSupport
import org.orbeon.oxf.xml.XMLReceiver
import org.orbeon.saxon.functions.FunctionLibrary

trait XFormsStaticState {

  def getIndentedLogger                       : IndentedLogger

  def digest                                  : String
  def encodedState                            : String
  def allowedExternalEvents                   : Set[String]
  def template                                : Option[AnnotatedTemplate]

  def topLevelPart                            : PartAnalysis

  def isClientStateHandling                   : Boolean
  def isServerStateHandling                   : Boolean
  def isHTMLDocument                          : Boolean

  def isXPathAnalysis                         : Boolean
  def isCalculateDependencies                 : Boolean

  def functionLibrary                         : FunctionLibrary
  def xblSupport                              : Option[XBLSupport]
  def sanitizeInput                           : String => String
  def isInlineResources                       : Boolean
  def assets                                  : XFormsAssets
  def uploadMaxSize                           : MaximumSize
  def uploadMaxSizeAggregate                  : MaximumSize
  def uploadMaxSizeAggregateExpression        : Option[CompiledExpression]

  def staticProperty       (name: String)     : Any
  def staticStringProperty (name: String)     : String
  def staticBooleanProperty(name: String)     : Boolean
  def staticIntProperty    (name: String)     : Int

  def propertyMaybeAsExpression(name: String) : Either[Any, CompiledExpression]
  def clientNonDefaultProperties              : Map[String, Any]

  def writeAnalysis(implicit receiver: XMLReceiver): Unit
}
