/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.oxf.fr

import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunction
import org.orbeon.oxf.xml.ShareableXPathStaticContext
import org.orbeon.saxon.expr.{Expression, Literal}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.value._
import org.orbeon.xml.NamespaceMapping

import scala.util.Try


object FormRunnerCommonConstraint {

  def analyzeKnownConstraint(
    xpathString      : String,
    namespaceMapping : NamespaceMapping,
    functionLibrary  : FunctionLibrary
  )(implicit
    logger          : IndentedLogger
  ): Option[(String, Option[String])] = {

    def tryCompile =
      Try(
        XPath.compileExpressionMinimal(
          staticContext = new ShareableXPathStaticContext(
            XPath.GlobalConfiguration,
            namespaceMapping,
            functionLibrary
          ),
          xpathString   = xpathString,
          avt           = false
        )
      )

    def contentWithinNestedBrackets(s: String): Option[String] = {

      val start = s.indexOf("(")
      val end   = s.lastIndexOf(")")

      start != -1 && end > start option {
        s.substring(start + 1, end)
      }
    }

    def removeEnclosingBracketsIfPresent(s: String): String =
      if (s.head == '(' && s.last == ')')
        s.tail.init
      else
        s

    def findExpressionArgument(s: String): String =
      contentWithinNestedBrackets(s)     map
        (_.trimAllToEmpty)               map
        removeEnclosingBracketsIfPresent getOrElse // expected to have an extra level of brackets but if not we ignore it for compatibility
        (throw new IllegalArgumentException)

    // NOTE: In the future, we could handle more types of literals, possibly all of the `AtomicValue` literals. In this case,
    // we should return the type of the literal, and a correctly-serialized string value of the string. We will also have to
    // support extracting a sequence of items, in particular a sequence of `xs:date`.
    def isSupportedLiteral(l: Literal): Boolean =
      l.getValue match {
        case _: StringValue | _: IntegerValue | _: DecimalValue | _: BooleanValue => true
        case _ => false
      }

    def analyze(expr: Expression) =
      expr match {
        case e: ValidationFunction[_] =>
          e.arguments.headOption match {
            case Some(l: Literal) if isSupportedLiteral(l) => Some(e.propertyName -> Some(l.getValue.getStringValue))
            case Some(_)                                   => Some(e.propertyName -> Some(findExpressionArgument(xpathString)))
            case None                                      => Some(e.propertyName -> None)
            case _                                         => None
          }
        case _ =>
          None
      }

    tryCompile.toOption flatMap analyze
  }

}