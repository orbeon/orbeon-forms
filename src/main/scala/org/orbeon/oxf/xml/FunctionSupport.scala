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

import org.orbeon.saxon.expr.{ExpressionVisitor, _}
import org.orbeon.saxon.functions.SystemFunction
import org.orbeon.saxon.om._
import org.orbeon.saxon.value.{BooleanValue, Int64Value, IntegerValue, StringValue}
import org.orbeon.scaxon.XML._

import scala.collection.JavaConverters._

trait RuntimeDependentFunction extends SystemFunction {
  override def getIntrinsicDependencies =
    super.getIntrinsicDependencies | StaticProperty.DEPENDS_ON_RUNTIME_ENVIRONMENT

  // Suppress compile-time evaluation by doing nothing
  override def preEvaluate(visitor: ExpressionVisitor): Expression = this
}

// Mix-in for functions which use the context if single optional argument is missing
trait DependsOnContextItemIfSingleArgumentMissing extends FunctionSupport {
  override def getIntrinsicDependencies =
    super.getIntrinsicDependencies | (if (arguments.isEmpty) StaticProperty.DEPENDS_ON_CONTEXT_ITEM else 0)
}

abstract class FunctionSupport extends SystemFunction {

  // Public accessor for Scala traits
  def arguments: Seq[Expression] = argument
  def functionOperation: Int = operation

  def stringArgument(i: Int)(implicit xpathContext: XPathContext): String =
    arguments(i).evaluateAsString(xpathContext).toString

  def stringArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[String] =
    arguments.lift(i) map (_.evaluateAsString(xpathContext).toString)

  def stringValueArgumentOpt(i: Int)(implicit xpathContext: XPathContext): Option[String] =
    itemArgumentOpt(i) map (_.getStringValue)

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
      case v: Int64Value   ⇒ Some(v.longValue)
      case v: IntegerValue ⇒ throw new IllegalArgumentException("integer value out of range for Long")
      case v               ⇒ None
    }

  def asIterator(v: Array[String]) = new ArrayIterator(v map StringValue.makeStringValue)
  def asIterator(v: Seq[String])   = new ListIterator (v map StringValue.makeStringValue asJava)

  implicit def stringIteratorToSequenceIterator(i: Iterator[String]) : SequenceIterator = i map stringToStringValue

  implicit def itemSeqOptToSequenceIterator(v: Option[Seq[Item]])    : SequenceIterator = v map (s ⇒ new ListIterator(s.asJava)) getOrElse EmptyIterator.getInstance
  implicit def stringSeqOptToSequenceIterator(v: Option[Seq[String]]): SequenceIterator = v map asIterator getOrElse EmptyIterator.getInstance

  implicit def stringToStringValue(v: String)                        : StringValue      = StringValue.makeStringValue(v)
  implicit def booleanToBooleanValue(v: Boolean)                     : BooleanValue     = BooleanValue.get(v)
  implicit def intToIntegerValue(v: Int)                             : IntegerValue     = Int64Value.makeIntegerValue(v)

  implicit def stringOptToStringValue(v: Option[String])             : StringValue      = v map stringToStringValue orNull
  implicit def booleanOptToBooleanValue(v: Option[Boolean])          : BooleanValue     = v map booleanToBooleanValue orNull
}
