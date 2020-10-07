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

import java.text.MessageFormat
import java.util.Locale
import java.util.regex.Matcher

import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xml.{DefaultFunctionSupport, DependsOnContextItemIfSingleArgumentMissing}
import org.orbeon.saxon.{Configuration, MapFunctions}
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{BooleanValue, StringValue, Value}
import org.orbeon.scaxon.Implicits._
import org.orbeon.oxf.util.CollectionUtils._
import scala.collection.compat._

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

          ProcessTemplate.processTemplateWithNames(resourceOrTemplate, javaNamedParamsIt.to(List), Configuration.getLocale(langArgument))

        case None =>
          resourceOrTemplate
      }

    stringToStringValue(processResourceString(templateArgument))
  }

  // TODO: PathMap
}

object ProcessTemplate {

  // Ideally should be the same as a non-qualified XPath variable name
  private val MatchTemplateKey = """\{\s*\$([A-Za-z0-9\-_]+)\s*""".r

  // Template processing
  //
  // - See https://github.com/orbeon/orbeon-forms/issues/3078
  // - For now, we only support template values like `{$foo}`.
  // - Whitespace is allowed between the brackets: `{ $foo }`.
  //
  // In the future, we would like to extend the syntax with full nested XPath expressions, which would mean
  // compiling the template to an XPath value template.

  def processTemplateWithNames(
    templateWithNames : String,
    javaNamedParams   : List[(String, Any)],
    currentLocale     : Locale
  ): String = {

    // TODO
    def formatValue(v: Any) = v match {
      case null       => ""
      case v: Byte    => v
      case v: Short   => v
      case v: Int     => v
      case v: Long    => v
      case v: Float   => v
      case v: Double  => v
      case v: Boolean => v
      case other      => other.toString
    }

    val nameToPos = javaNamedParams.iterator.map(_._1).zipWithIndex.toMap

    val templateWithPositions =
      MatchTemplateKey.replaceAllIn(templateWithNames, m => {
        Matcher.quoteReplacement("{" + nameToPos(m.group(1)).toString)
      }).replaceAllLiterally("'", "''")

    new MessageFormat(templateWithPositions, currentLocale)
      .format(javaNamedParams.map(v => formatValue(v._2)).to(Array))
  }
}