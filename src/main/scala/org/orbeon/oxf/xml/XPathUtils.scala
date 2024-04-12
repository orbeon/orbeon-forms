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
package org.orbeon.oxf.xml

import java.{lang => jl, util => ju}

import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.{FunctionContext, StaticXPath, XPath, XPathCache}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om
import org.orbeon.saxon.value._
import org.orbeon.xml.NamespaceMapping
import org.orbeon.{dom => odom}

import scala.jdk.CollectionConverters._
import scala.collection.compat._

// These are older XPath utilities used by XPL. Don't expand on this as it's kept mostly for legacy purposes.
object XPathUtils {

  private def itemToJavaUnwrap: PartialFunction[om.Item, AnyRef] = {
    case v: AtomicValue                                                          => Value.convertToJava(v)
    case n: om.VirtualNode if ! n.getUnderlyingNode.isInstanceOf[odom.Namespace] => n.getUnderlyingNode.asInstanceOf[odom.Node]
  }

  private def itemToNodeUnwrap: PartialFunction[om.Item, odom.Node] = {
    case _: AtomicValue                                                          => throw new IllegalArgumentException
    case n: om.VirtualNode if ! n.getUnderlyingNode.isInstanceOf[odom.Namespace] => n.getUnderlyingNode.asInstanceOf[odom.Node]
  }

  private val EmptyVariables  = ju.Collections.emptyMap[String, om.ValueRepresentation]()
  private val EmptyNamespaces = ju.Collections.emptyMap[String, String]()

  // 29 usages
  def selectNodeIterator(contextNode: odom.Node, expr: String): ju.Iterator[odom.Node] =
    selectNodeIterator(contextNode, expr, EmptyNamespaces)

  // 1 external usage
  def selectNodeIterator(contextNode: odom.Node, expr: String, prefixes: ju.Map[String, String]): ju.Iterator[odom.Node] = {

    val resultWithItemsIt =
      selectIteratorImpl(
        contextNode     = contextNode,
        expr            = expr,
        prefixes        = prefixes,
        functionLibrary = null,
        functionContext = null
      )

    resultWithItemsIt collect itemToNodeUnwrap asJava
  }

  // 2 external usages from SQL interpreter
  def selectNodeIterator(
    contextNode     : odom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): ju.Iterator[odom.Node] = {

    val resultWithItemsIt =
      selectIteratorImpl(
        contextNode,
        expr,
        prefixes,
        functionLibrary,
        functionContext
      )

    resultWithItemsIt collect itemToNodeUnwrap asJava
  }

  // NOTE: Return `null` if there is no result.
  // 12 usages
  def selectSingleNode(node: odom.Node, expr: String): odom.Node = {
    val it = selectNodeIterator(node, expr)
    if (it.hasNext)
      it.next()
    else
      null
  }

  // 21 usages
  def selectStringValue(node: odom.Node, expr: String): String =
    selectStringValueOrNull(node, expr, EmptyNamespaces, null, null)

  // 128 usages
  def selectStringValueNormalize(node: odom.Node, expr: String): String =
    trimAllToNull(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null))

  def selectStringValueNormalizeOpt(node: odom.Node, expr: String): Option[String] =
    trimAllToOpt(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null))

  def selectStringValueNormalize(node: odom.Node, expr: String, default: String): String =
    selectStringValueNormalizeOpt(node, expr).getOrElse(default)

  // 3 external usages from SQL interpreter
  // Expects: List<Node>, Node, Number (Float, Double, Long, any other?), String.
  def selectObjectValue(
    contextNode     : odom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): AnyRef = {

    val resultWithItemsIt =
      selectIteratorImpl(
        contextNode,
        expr,
        prefixes,
        functionLibrary,
        functionContext
      )

    val resultWithJava =
      resultWithItemsIt.collect(itemToJavaUnwrap).to(List)

    if (resultWithJava.isEmpty)
      null
    else if (resultWithJava.size == 1)
      resultWithJava.head
    else
      resultWithJava collect { case v: odom.Node => v } asJava
  }

  // 1 external usage
  def selectObjectValue(contextNode: odom.Node, expr: String): AnyRef =
    selectObjectValue(contextNode, expr, EmptyNamespaces, null, null)

  // 3 external usages from SQL interpreter
  // IMPORTANT: If the XPath expressions select an empty node set, return `null`!
  def selectStringValueOrNull(
    contextNode     : odom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext): String = {

    val resultWithItemsIt =
      selectIteratorImpl(
        contextNode,
        expr,
        prefixes,
        functionLibrary,
        functionContext
      )

    val resultWithJavaIt =
      resultWithItemsIt collect itemToJavaUnwrap

    if (resultWithJavaIt.hasNext)
      resultWithJavaIt.next() match {
        case v: odom.Node => v.getStringValue
        case v            => v.toString // covers String, Boolean, and number values
      }
    else
      null
  }

  // IMPORTANT: If the XPath expressions select an empty node set, return `null`!
  // 23 usages
  def selectIntegerValue(node: odom.Node, expr: String): Integer =
    Option(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null)) map jl.Integer.valueOf orNull

  def selectIntegerValue(node: odom.Node, expr: String, default: Int): Int =
    Option(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null)).map(_.toInt).getOrElse(default)

  // 1 caller from SQL interpreter, implication is that expressions must return xs:boolean
  def selectBooleanValue(
    contextNode     : odom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): Boolean =
    selectIteratorImpl(
      contextNode     = contextNode,
      expr            = StaticXPath.makeBooleanExpression(expr),
      prefixes        = prefixes,
      functionLibrary = functionLibrary,
      functionContext = functionContext
    ).next().asInstanceOf[BooleanValue].effectiveBooleanValue

  private def selectIteratorImpl(
    contextNode     : odom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): Iterator[om.Item] = {

    assert(contextNode ne null)

    val dw = new DocumentWrapper(contextNode.getDocument, null, XPath.GlobalConfiguration)

    val resultWithItems =
        XPathCache.evaluateKeepItems( // NOTE: XPathCache API should have support for `Iterator`!
          contextItems        = ju.Collections.singletonList(dw.wrap(contextNode)),
          contextPosition     = 1,
          xpathString         = expr,
          namespaceMapping    = if ((prefixes eq null) || prefixes.isEmpty) null else NamespaceMapping(prefixes),
          variableToValueMap  = EmptyVariables,
          functionLibrary     = functionLibrary,
          functionContext     = functionContext,
          baseURI             = null,
          locationData        = null,
          reporter            = null
        )

    resultWithItems.iterator
  }
}