/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.processor.sql

import org.orbeon.oxf.util.XPath.FunctionContext
import org.orbeon.oxf.util.{ScalaUtils, XPath}
import org.orbeon.oxf.xml.{FunctionSupport, OrbeonFunctionLibrary}
import org.orbeon.saxon.`type`.BuiltInAtomicType._
import org.orbeon.saxon.`type`.Type
import org.orbeon.saxon.dom4j.DocumentWrapper
import org.orbeon.saxon.expr.StaticProperty._
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om.NodeInfo
import org.orbeon.saxon.value.{Int64Value, StringValue}
import org.{dom4j ⇒ d4j}

object SQLFunctionLibrary extends OrbeonFunctionLibrary {

  def instance = this

  class SQLFunctionContext(val currentNode: d4j.Node, val position: Int, val getColumn: (String, Int) ⇒ String) extends FunctionContext

  abstract class Function2Base[V1, V2, R] extends Function2[V1, V2, R]

  private def functionContextOpt =
    XPath.functionContext flatMap ScalaUtils.collectByErasedType[SQLFunctionContext]

  Namespace(SQLProcessor.SQL_NAMESPACE_URI) {
    Fun("current",    classOf[CurrentFunction],  op = 0, min = 0, Type.NODE_TYPE, ALLOWS_ZERO_OR_ONE)
    Fun("position",   classOf[PositionFunction], op = 0, min = 0, INTEGER, EXACTLY_ONE)
    Fun("get-column", classOf[PositionFunction], op = 0, min = 0, STRING, ALLOWS_ZERO_OR_ONE,
      Arg(INTEGER, EXACTLY_ONE)
    )
  }

  private class CurrentFunction extends SystemFunction {
    override def evaluateItem(xpathContext: XPathContext): NodeInfo = {

      def wrap(node: d4j.Node) =
        new DocumentWrapper(node.getDocument, null, XPath.GlobalConfiguration).wrap(node)

      functionContextOpt map (_.currentNode) map wrap orNull
    }
  }

  private class PositionFunction extends SystemFunction {
    override def evaluateItem(xpathContext: XPathContext): Int64Value =
      functionContextOpt map (_.position) map (new Int64Value(_)) orNull
  }

  private class GetColumnFunction extends FunctionSupport {
    override def evaluateItem(xpathContext: XPathContext): StringValue = {

      implicit val ctx = xpathContext

      val colName = stringArgument(0)
      val level   = arguments.lift(1) flatMap evaluateAsLong filter (_ >= 1) getOrElse 1L

      functionContextOpt map (_.getColumn(colName, level.toInt))
    }
  }

}
