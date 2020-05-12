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

import java.{util => ju}

import org.orbeon.dom
import org.orbeon.dom.Namespace
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.util.XPath.FunctionContext
import org.orbeon.oxf.util.{XPath, XPathCache}
import org.orbeon.oxf.xml.{dom4j => _}
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.om._
import org.orbeon.saxon.value._

import scala.collection.JavaConverters._
import scala.collection.compat._

// These are older XPath utilities used by XPL. Don't expand on this as it's kept mostly for legacy purposes.
object XPathUtils {

  private def itemToJavaUnwrap: PartialFunction[Item, AnyRef] = {
    case v: AtomicValue                                                  => Value.convertToJava(v)
    case n: VirtualNode if ! n.getUnderlyingNode.isInstanceOf[Namespace] => n.getUnderlyingNode.asInstanceOf[dom.Node]
  }

  private def itemToNodeUnwrap: PartialFunction[Item, dom.Node] = {
    case v: AtomicValue                                                  => throw new IllegalArgumentException
    case n: VirtualNode if ! n.getUnderlyingNode.isInstanceOf[Namespace] => n.getUnderlyingNode.asInstanceOf[dom.Node]
  }

  private val EmptyVariables  = ju.Collections.emptyMap[String, ValueRepresentation]()
  private val EmptyNamespaces = ju.Collections.emptyMap[String, String]()

  // 46 usages
  def selectNodeIterator(contextNode: dom.Node, expr: String): ju.Iterator[dom.Node] =
    selectNodeIterator(contextNode, expr, EmptyNamespaces)

  // 1 external usage
  def selectNodeIterator(contextNode: dom.Node, expr: String, prefixes: ju.Map[String, String]): ju.Iterator[dom.Node] = {

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
    contextNode     : dom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): ju.Iterator[dom.Node] = {

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
  // 13 usages
  def selectSingleNode(node: dom.Node, expr: String): dom.Node = {
    val it = selectNodeIterator(node, expr)
    if (it.hasNext)
      it.next()
    else
      null
  }

  // 25 usages
  def selectStringValue(node: dom.Node, expr: String): String =
    selectStringValueOrNull(node, expr, EmptyNamespaces, null, null)

  // 168 usages
  def selectStringValueNormalize(node: dom.Node, expr: String): String =
    trimAllToNull(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null))

  // 3 external usages from SQL interpreter
  // Expects: List<Node>, Node, Number (Float, Double, Long, any other?), String.
  def selectObjectValue(
    contextNode     : dom.Node,
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
      resultWithJava collect { case v: dom.Node => v } asJava
  }

  // 1 external usage
  def selectObjectValue(contextNode: dom.Node, expr: String): AnyRef =
    selectObjectValue(contextNode, expr, EmptyNamespaces, null, null)

  // 3 external usages from SQL interpreter
  // IMPORTANT: If the XPath expressions select an empty node set, return `null`!
  def selectStringValueOrNull(
    contextNode     : dom.Node,
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

    if (resultWithJavaIt.hasNext) {
      resultWithJavaIt.next() match {
        case v: dom.Node => v.getStringValue
        case v           => v.toString // covers String, Boolean, and number values
      }
    } else
      null
  }

  // IMPORTANT: If the XPath expressions select an empty node set, return `null`!
  // 26 usages
  def selectIntegerValue(node: dom.Node, expr: String): Integer =
    Option(selectStringValueOrNull(node, expr, EmptyNamespaces, null, null)) map (new java.lang.Integer(_)) orNull

  // 1 caller from SQL interpreter, implication is that expressions must return xs:boolean
  def selectBooleanValue(
    contextNode     : dom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): Boolean =
    selectIteratorImpl(
      contextNode     = contextNode,
      expr            = XPath.makeBooleanExpression(expr),
      prefixes        = prefixes,
      functionLibrary = functionLibrary,
      functionContext = functionContext
    ).next().asInstanceOf[BooleanValue].effectiveBooleanValue

  private def selectIteratorImpl(
    contextNode     : dom.Node,
    expr            : String,
    prefixes        : ju.Map[String, String],
    functionLibrary : FunctionLibrary,
    functionContext : FunctionContext
  ): Iterator[Item] = {

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