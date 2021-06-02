/**
 * Copyright (C) 2010 Orbeon, Inc.
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
package org.orbeon.saxon.function

import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.{DefaultFunctionSupport, DependsOnContextItemIfSingleArgumentMissing}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{BooleanValue, StringValue, Value}
import org.orbeon.saxon.{Configuration, MapFunctions}
import org.orbeon.scaxon.Implicits._


class IsBlank extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): BooleanValue =
    ! (stringArgumentOrContextOpt(0)(context) exists (_.trimAllToEmpty.nonEmpty))
}

class NonBlank extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): BooleanValue =
    stringArgumentOrContextOpt(0)(context) exists (_.trimAllToEmpty.nonEmpty)
}

class Split extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    val separator = stringArgumentOpt(1)(xpathContext)
    stringArgumentOrContextOpt(0)(xpathContext) map (_.splitTo[List](separator.orNull))
  }
}

class Trim extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): StringValue =
    stringArgumentOrContextOpt(0)(context) map (_.trimAllToEmpty)
}

class EscapeXmlMinimal extends DefaultFunctionSupport with DependsOnContextItemIfSingleArgumentMissing {
  override def evaluateItem(context: XPathContext): StringValue =
    stringArgumentOrContextOpt(0)(context) map (_.escapeXmlMinimal)
}

class ProcessTemplate extends DefaultFunctionSupport {

  override def evaluateItem(xpathContext: XPathContext): StringValue = {

    implicit val ctx = xpathContext

    val templateArgument  = stringArgument(0)
    val langArgument      = stringArgument(1)
    val templateParamsOpt = itemsArgumentOpt(2) map (it => MapFunctions.collectMapValues(it).next())

    def processResourceString(resourceOrTemplate: String): String =
      templateParamsOpt match {
        case Some(params) =>

          val javaNamedParamsIt = params.iterator map {
            case (key, value) =>
              val javaParamOpt = asScalaIterator(Value.asIterator(value)).map(Value.convertToJava).nextOption()
              key.getStringValue -> javaParamOpt.orNull
          }

          ProcessTemplateSupport.processTemplateWithNames(resourceOrTemplate, javaNamedParamsIt.toList)

        case None =>
          resourceOrTemplate
      }

    stringToStringValue(processResourceString(templateArgument))
  }

  // TODO: PathMap
}
