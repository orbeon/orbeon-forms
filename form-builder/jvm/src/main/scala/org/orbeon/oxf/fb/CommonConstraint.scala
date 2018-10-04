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
package org.orbeon.oxf.fb

import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.function.xxforms.ValidationFunction
import org.orbeon.oxf.xml.{NamespaceMapping, ShareableXPathStaticContext}
import org.orbeon.saxon.expr.{Expression, Literal}
import org.orbeon.saxon.functions.FunctionLibrary

import scala.util.Try


object CommonConstraint {

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
          xpathString   = xpathString
        )
      )

    def analyze(expr: Expression) =
      expr match {
        case e: ValidationFunction[_] ⇒
          e.arguments.headOption match {
            case Some(l: Literal) ⇒ Some(e.propertyName → Some(l.getValue.getStringValue))
            case None             ⇒ Some(e.propertyName → None)
            case other            ⇒ None
          }
        case other ⇒
          None
      }

    tryCompile.toOption flatMap analyze
  }

}