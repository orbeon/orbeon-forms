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

import org.orbeon.dom.QName
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr.{ExpressionVisitor, _}
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om._
import org.orbeon.saxon.value._


trait DefaultFunctionSupport
  extends FunctionSupport
    with NoPathMapDependencies
    with NoPreEvaluate

trait RuntimeDependentFunction extends DefaultFunctionSupport {
  override def getIntrinsicDependencies: Int =
    super.getIntrinsicDependencies | StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT
}

trait DependsOnContextItem extends FunctionSupport {
  override def getIntrinsicDependencies: Int =
    super.getIntrinsicDependencies | StaticProperty.DEPENDS_ON_CONTEXT_ITEM
}

// Mix-in for functions which use the context if single optional argument is missing
trait DependsOnContextItemIfSingleArgumentMissing extends FunctionSupport {
  override def getIntrinsicDependencies: Int =
    super.getIntrinsicDependencies | (if (arguments.isEmpty) StaticProperty.DEPENDS_ON_CONTEXT_ITEM else 0)
}

trait NoPathMapDependencies extends SystemFunction {
  override def addToPathMap(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet = {
    pathMap.setInvalidated(true)
    null
  }
}

/**
 * preEvaluate: this method suppresses compile-time evaluation by doing nothing
 * (because the value of the expression depends on the runtime context)
 *
 * NOTE: A few functions would benefit from not having this, but it is always safe.
 */
trait NoPreEvaluate extends SystemFunction {
  override def preEvaluate(visitor: ExpressionVisitor): Expression = this
}

abstract class FunctionSupportJava extends FunctionSupport

trait FunctionSupport extends SystemFunction {

  def arguments: Seq[Expression] = getArguments

  def stringArgument(i: Int)(implicit xpathContext: XPathContext): String =
    arguments(i).evaluateAsString(xpathContext).toString

  def qNameArgument(i: Int)(implicit xpathContext: XPathContext): QName =
    arguments(i).evaluateItem(xpathContext) match {
      case v: QNameValue => QName(v.getLocalName, org.orbeon.dom.Namespace(v.getPrefix, v.getNamespaceURI))
      case _             => throw new IllegalArgumentException
    }

  def stringArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[String] =
    arguments.lift(i) map (_.evaluateAsString(xpathContext).toString)

  def stringArgumentOrContextOpt(i: Int)(implicit xpathContext: XPathContext): Option[String] =
    stringArgumentOpt(i) orElse (Option(xpathContext.getContextItem) map (_.getStringValue))

  def longArgument(i: Int, default: Long)(implicit xpathContext: XPathContext): Long =
    longArgumentOpt(i) getOrElse default

  def longArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[Long] =
    arguments.lift(i) flatMap evaluateAsLong

  def booleanArgument(i: Int, default: Boolean)(implicit xpathContext: XPathContext): Boolean =
    booleanArgumentOpt(i) getOrElse default

  def booleanArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[Boolean] =
    arguments.lift(i) map effectiveBooleanValue

  def itemsArgument(i: Int)(implicit xpathContext: XPathContext): SequenceIterator =
    arguments(i).iterate(xpathContext)

  def itemsArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[SequenceIterator] =
    arguments.lift(i) map (_.iterate(xpathContext))

  def itemArgument(i: Int)(implicit xpathContext: XPathContext): Item =
    itemsArgument(i).next()

  def itemArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[Item] =
    itemsArgumentOpt(i) map (_.next())

  def itemArgumentOrContextOpt(i: Int)(implicit xpathContext: XPathContext): Option[Item] =
    Option(itemArgumentOpt(i) getOrElse xpathContext.getContextItem)

  def itemsArgumentOrContextOpt(i: Int)(implicit xpathContext: XPathContext): SequenceIterator =
    itemsArgumentOpt(i) getOrElse SingletonIterator.makeIterator(xpathContext.getContextItem)

  def effectiveBooleanValue(e: Expression)(implicit xpathContext: XPathContext): Boolean =
    ExpressionTool.effectiveBooleanValue(e.iterate(xpathContext))

  def evaluateAsLong(e: Expression)(implicit xpathContext: XPathContext): Option[Long] =
    Option(e.evaluateItem(xpathContext)) flatMap {
      case v: Int64Value   => Some(v.longValue)
      case _: IntegerValue => throw new IllegalArgumentException("integer value out of range for Long")
      case _               => None
    }
}
