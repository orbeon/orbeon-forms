/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.DateUtils
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.DependsOnContextItem
import org.orbeon.saxon.`type`.ValidationFailure
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr._
import org.orbeon.saxon.value._
import org.orbeon.scaxon.Implicits
import org.orbeon.scaxon.Implicits._

import scala.collection.JavaConverters._

trait ValidationFunction[T] extends XFormsFunction with DependsOnContextItem {

  def propertyName: String

  def argumentOpt(implicit xpathContext: XPathContext): Option[T]
  def evaluate(value: String, constraintOpt: Option[T]): Boolean

  def toMipValue(constraintOpt: Option[T]): Option[String] =
    constraintOpt map (_.toString) orElse Some("true") // XXX check why "true"?

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {

    implicit val ctx = xpathContext

    val valueOpt      = Option(xpathContext.getContextItem) map (_.getStringValue)
    val constraintOpt = argumentOpt

    setProperty(propertyName, toMipValue(constraintOpt))

    valueOpt match {
      case Some(itemValue) => evaluate(itemValue, constraintOpt)
      case None            => true
    }
  }

  override def addToPathMap(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet  = {

    val attachmentPoint = pathMapAttachmentPoint(pathMap, pathMapNodeSet)

    // For dependency on context
    if (attachmentPoint ne null)
      attachmentPoint.setAtomized()

    val result = new PathMapNodeSet
    iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] foreach { child =>
      result.addNodeSet(child.addToPathMap(pathMap, attachmentPoint))
    }

    null
  }
}

trait LongValidationFunction extends ValidationFunction[Long] {
  def argumentOpt(implicit xpathContext: XPathContext): Option[Long] = longArgumentOpt(0)
}

trait StringValidationFunction extends ValidationFunction[String] {
  def argumentOpt(implicit xpathContext: XPathContext): Option[String] = stringArgumentOpt(0)
}

trait DateSeqValidationFunction extends ValidationFunction[Seq[DateValue]] {

  override def toMipValue(constraintOpt: Option[Seq[DateValue]]): Option[String] =
    constraintOpt map (_ map (_.getStringValueCS) mkString " ")

  def argumentOpt(implicit xpathContext: XPathContext): Option[Seq[DateValue]] =
    itemsArgumentOpt(0) map { itemsIt =>

      val datesIt =
        Implicits.asScalaIterator(itemsIt) flatMap {
          case v: DateValue  => Some(v)
          case _             => None
        }

      datesIt.toList
    }
}

class MaxLengthValidation extends LongValidationFunction {

  val propertyName = "max-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) => org.orbeon.saxon.value.StringValue.getStringLength(value) <= constraint
    case None             => true
  }
}

class MinLengthValidation extends LongValidationFunction {

  val propertyName = "min-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) => org.orbeon.saxon.value.StringValue.getStringLength(value) >= constraint
    case None             => true
  }
}

class NonNegativeValidation extends LongValidationFunction {

  val propertyName = "non-negative"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.trySignum(value) exists (_ != -1)
}

class NegativeValidation extends LongValidationFunction {

  val propertyName = "negative"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.trySignum(value) contains -1
}

class NonPositiveValidation extends LongValidationFunction {

  val propertyName = "non-positive"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.trySignum(value) exists (_ != 1)
}

class PositiveValidation extends LongValidationFunction {

  val propertyName = "positive"

  def evaluate(value: String, constraintOpt: Option[Long]) =
    NumericValidation.trySignum(value) contains 1
}

object NumericValidation {

  def trySignum(value: String): Option[Int] = tryParseAsLongOrBigDecimal(value) map {
    case Left(long)        => long.signum
    case Right(bigDecimal) => bigDecimal.signum
  }

  // Don't use `Long.parseLong` or similar as they throw exceptions, and we can have invalid data in many cases.
  // With this:
  //
  // - "small" integers (15 or 16 digits?) don't throw if invalid
  // - "large" integers throw if invalid (Saxon uses `Long.parseLong` and `new BigInteger`)
  // - decimals don't throw if invalid (Saxon uses a regex to validate first)
  //   - NOTE: Since Saxon 9.2, Saxon does throw instead! Handle this when upgrading to Saxon >= 9.2.
  def tryParseAsLongOrBigDecimal(value: String): Option[Either[Long, BigDecimal]] =
    IntegerValue.stringToInteger(value) match {
      case v: Int64Value        => Some(Left(v.longValue))
      case v: BigIntegerValue   => Some(Right(v.asDecimal))
      case _: ValidationFailure =>
        DecimalValue.makeDecimalValue(value, true) match {
          case v: DecimalValue      => Some(Right(v.getDecimalValue))
          case _: ValidationFailure => None
        }
    }

  def tryParseAsLong(value: String): Option[Long] =
    IntegerValue.stringToInteger(value) match {
      case v: Int64Value        => Some(v.longValue)
      case _                    => None
    }
}

class MaxFractionDigitsValidation extends LongValidationFunction {

  val propertyName = "fraction-digits"

  // Operate at the lexical level
  def countDigitsAfterDecimalSeparator(value: String) = {

    var beforeDecimalSeparator      = true
    var digitsAfterDecimalSeparator = 0
    var trailingZeros               = 0

    for (c <- value) {
      if (beforeDecimalSeparator) {
        if (c == '.')
          beforeDecimalSeparator = false
      } else {
        if (c == '0')
          trailingZeros += 1
        else
          trailingZeros = 0

        if ('0' <= c && c <= '9')
          digitsAfterDecimalSeparator += 1
      }
    }

    digitsAfterDecimalSeparator - trailingZeros
  }

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) => countDigitsAfterDecimalSeparator(value) <= constraint
    case None             => true
  }
}

object UploadMaxSizeValidation {
  val PropertyName = "upload-max-size"
}

class UploadMaxSizeValidation extends LongValidationFunction {

  val propertyName = UploadMaxSizeValidation.PropertyName

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) => true // for now, don't actually validate, see #2956
    case None             => true
  }

}

object UploadMediatypesValidation {
  val PropertyName = "upload-mediatypes"
}

class UploadMediatypesValidation extends StringValidationFunction {

  val propertyName = UploadMediatypesValidation.PropertyName

  def evaluate(value: String, constraintOpt: Option[String]) = constraintOpt match {
    case Some(constraint) => true // for now, don't actually validate, see #3015
    case None             => true
  }

}

// Passes if:
//
// - the current value is a date
// - AND that date is NOT part of the list of excluded dates
//
// We use timestamps/instants to do the comparison, assuming that the function is passed `Date` objects
// which reflect actual date values.
//
class ExcludedDatesValidation extends DateSeqValidationFunction {

  val propertyName = ExcludedDatesValidation.PropertyName

  def evaluate(value: String, constraintOpt: Option[Seq[DateValue]]): Boolean =
    DateUtils.tryParseISODate(value, DateUtils.TimeZone.UTC) exists { dateInstant =>
       // NOTE: `getCalendar` assumes UTC if the date doesn't have a timezone!
      ! (constraintOpt.iterator.flatten exists (_.getCalendar.getTimeInMillis == dateInstant))
    }
}

object ExcludedDatesValidation {
  val PropertyName = "excluded-dates"
}