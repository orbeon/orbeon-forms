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
import org.orbeon.oxf.xforms.analysis.TopLevelPartAnalysis
import org.orbeon.oxf.xforms.state.AnnotatedTemplate
import org.orbeon.saxon.functions.FunctionLibrary

trait XFormsStaticState {

  def getIndentedLogger                       : IndentedLogger

  // State
  def digest                                  : String
  def encodedState                            : String

  // Static representation
  def topLevelPart                            : TopLevelPartAnalysis
  def template                                : Option[AnnotatedTemplate]

  // Must always return `true` as of 2020-10-29 or handlers will throw an error
  def isHTMLDocument                          : Boolean

  // Other configurations
  def functionLibrary                         : FunctionLibrary
  def assets                                  : XFormsAssets

  // General configuration via properties
  def isClientStateHandling                   : Boolean
  def isServerStateHandling                   : Boolean

  def isXPathAnalysis                         : Boolean
  def isCalculateDependencies                 : Boolean

  def sanitizeInput                           : String => String
  def isInlineResources                       : Boolean

  def uploadMaxSize                           : MaximumSize
  def uploadMaxSizeAggregate                  : MaximumSize
  def uploadMaxSizeAggregateExpression        : Option[CompiledExpression]

  def staticProperty       (name: String)     : Any
  def staticStringProperty (name: String)     : String
  def staticBooleanProperty(name: String)     : Boolean
  def staticIntProperty    (name: String)     : Int

  def propertyMaybeAsExpression(name: String) : Either[Any, CompiledExpression]
  def clientNonDefaultProperties              : Map[String, Any]

  // Probably obsolete
  def allowedExternalEvents                   : Set[String]
}
