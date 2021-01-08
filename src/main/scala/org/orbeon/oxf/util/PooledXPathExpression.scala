/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.{util => ju}

import org.apache.commons.pool.ObjectPool
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.util.XPath._
import org.orbeon.saxon.expr.{Expression, XPathContextMajor}
import org.orbeon.saxon.om.{Item, SequenceIterator, ValueRepresentation}
import org.orbeon.saxon.sxpath.{XPathDynamicContext, XPathExpression, XPathVariable}
import org.orbeon.saxon.value.{AtomicValue, ObjectValue, SequenceExtent, Value}
import org.orbeon.scaxon.Implicits

import scala.jdk.CollectionConverters._
import scala.collection.mutable
import scala.util.control.NonFatal
import scala.collection.compat._

class PooledXPathExpression(
  expression : XPathExpression,
  pool       : ObjectPool[PooledXPathExpression],
  variables  : List[(String, XPathVariable)]
) {

  // FIXME: This shouldn't be mutable state
  private var variableToValueMap: ju.Map[String, ValueRepresentation] = null
  private var contextItem: Item = null
  private var contextPosition: Int = 0

  /**
   * Set context node-set and initial position.
   *
   * @param contextItems          List of Item
   * @param contextPosition       1-based current position
   */
  def setContextItems(contextItems: ju.List[Item], contextPosition: Int): Unit =
    if (contextPosition > 0 && contextPosition <= contextItems.size)
      setContextItem(contextItems.get(contextPosition - 1), contextPosition)
    else
      setContextItem(null, 0)

  def setContextItem(contextItem: Item, contextPosition: Int): Unit = {
    this.contextItem = contextItem
    this.contextPosition = contextPosition
  }

  def setVariables(variableToValueMap: ju.Map[String, ValueRepresentation]): Unit =
    this.variableToValueMap = variableToValueMap

  /**
   * This *must* be called in a finally block to return the expression to the pool.
   */
  def returnToPool(): Unit = {
    variableToValueMap = null
    contextItem = null
    if (pool ne null)
      pool.returnObject(this)
  }

  def internalExpression: Expression = expression.getInternalExpression

  /**
   * Evaluate and return a List of native Java objects, but keep NodeInfo objects.
   *
   * NOTE: Used by XPathCache.
   */
  def evaluateKeepNodeInfo(functionContext: FunctionContext): ju.List[Any] =
    withFunctionContext(functionContext) {
      scalaIteratorToJavaList(Implicits.asScalaIterator(evaluate()) map itemToJavaKeepNodeInfoOrNull)
    }

  /**
   * Evaluate and return a List of Item objects.
   *
   * NOTE: Used by XPathCache.
   */
  def evaluateKeepItemsJava(functionContext: FunctionContext): ju.List[Item] =
    withFunctionContext(functionContext) {
      scalaIteratorToJavaList(Implicits.asScalaIterator(evaluate()))
    }

  // NOTE: Used by XPathCache.
  def evaluateKeepItems(functionContext: FunctionContext): List[Item] =
    withFunctionContext(functionContext) {
      Implicits.asScalaIterator(evaluate()).toList
    }

  /**
   * Evaluate and return a List of Item objects.
   *
   * NOTE: Used by XPathCache.
   */
  def evaluateSingleKeepItemOrNull(functionContext: FunctionContext): Item =
    withFunctionContext(functionContext) {
      evaluate().next()
    }

  /**
   * Evaluate the expression as a variable value usable by Saxon in further XPath expressions.
   *
   * NOTE: Used by XPathCache.
   */
  def evaluateAsExtent(functionContext: FunctionContext): SequenceExtent =
    withFunctionContext(functionContext) {
      new SequenceExtent(evaluate())
    }

  /**
   * Evaluate and return a single native Java object, but keep NodeInfo objects. Return null if the evaluation
   * doesn't return any item.
   *
   * NOTE: Used by XPathCache.
   */
  def evaluateSingleKeepNodeInfoOrNull(functionContext: FunctionContext): AnyRef =
    withFunctionContext(functionContext) {
      singleItemToJavaKeepNodeInfoOrNull(evaluate().next())
    }

  private def scalaIteratorToJavaList[T](i: Iterator[T]): ju.List[T] =
    new ju.ArrayList(i.to(mutable.ArrayBuffer).asJava)

  private def itemToJavaKeepNodeInfoOrNull(item: Item) = item match {
    case v: ObjectValue => v // don't convert for `Array` and `Map` types
    case v: AtomicValue => Value.convertToJava(v)
    case v              => v
  }

  private def singleItemToJavaOrNull(item: Item) = item match {
    case null => null
    case item => Value.convertToJava(item)
  }

  private def singleItemToJavaKeepNodeInfoOrNull(item: Item) = item match {
    case null => null
    case item => itemToJavaKeepNodeInfoOrNull(item)
  }

  /**
   * Evaluate and return an iterator over native Java objects, including underlying wrapped nodes.
   *
   * NOTE: Used by legacy Java code only.
   */
  def iterateReturnJavaObjects: ju.Iterator[AnyRef] =
    Implicits.asScalaIterator(expression.iterate(expression.createDynamicContext(contextItem))) map Value.convertToJava asJava

  /**
   * Evaluate and return a List of native Java objects, including underlying wrapped nodes.
   *
   * NOTE: Used by legacy Java code only.
   */
  def evaluateToJavaReturnToPool: ju.List[AnyRef] =
    try scalaIteratorToJavaList(Implicits.asScalaIterator(evaluate()) map Value.convertToJava)
    finally returnToPool()

  /**
   * Evaluate and return a single native Java object, including underlying wrapped nodes. Return null if the
   * evaluation doesn't return any item.
   *
   * NOTE: Used by legacy Java code only.
   */
  def evaluateSingleToJavaReturnToPoolOrNull: AnyRef =
    try singleItemToJavaOrNull(evaluate().next())
    catch { case NonFatal(e) => throw new OXFException(e) } // so Java callers can catch a RuntimeException
    finally returnToPool()

  private def evaluate(): SequenceIterator = {
    val (dynamicContext, xpathContext) = newDynamicAndMajorContexts
    prepareDynamicContext(xpathContext)
    expression.iterate(dynamicContext)
  }

  // Called from exf:sort(), indirectly from xxf:evaluate-avt(), and evaluate()
  def prepareDynamicContext(xpathContext: XPathContextMajor): Unit =
    if (variableToValueMap ne null) {
      for ((name, variable) <- variables) {
        val value = variableToValueMap.get(name)
        if (value ne null) // FIXME: this should never happen, right?
          xpathContext.setLocalVariable(variable.getLocalSlotNumber, value)
      }
    }

  // Called from `exf:sort()` and `evaluate()`
  def newDynamicAndMajorContexts: (XPathDynamicContext, XPathContextMajor) = {
    val dynamicContext = expression.createDynamicContext(contextItem, contextPosition)
    (dynamicContext, dynamicContext.getXPathContextObject.asInstanceOf[XPathContextMajor])
  }
}
