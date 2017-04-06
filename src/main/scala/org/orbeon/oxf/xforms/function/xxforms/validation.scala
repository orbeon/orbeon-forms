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

import org.orbeon.oxf.util.{IndentedLogger, XPath}
import org.orbeon.oxf.xforms.XFormsConstants._
import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.oxf.xml.{NamespaceMapping, ShareableXPathStaticContext}
import org.orbeon.saxon.`type`.ValidationFailure
import org.orbeon.saxon.expr.PathMap.PathMapNodeSet
import org.orbeon.saxon.expr._
import org.orbeon.saxon.functions.FunctionLibrary
import org.orbeon.saxon.value._

import scala.collection.JavaConverters._
import scala.util.Try

trait ValidationFunction[T] extends XFormsFunction {

  def propertyName: String

  def argumentOpt(implicit xpathContext: XPathContext): Option[T]
  def evaluate(value: String, constraintOpt: Option[T]): Boolean

  override def evaluateItem(xpathContext: XPathContext): BooleanValue = {

    implicit val ctx = xpathContext

    val valueOpt      = Option(xpathContext.getContextItem) map (_.getStringValue)
    val constraintOpt = argumentOpt

    val propertyStringOpt = constraintOpt map (_.toString) orElse Some("true")

    setProperty(propertyName, propertyStringOpt)

    valueOpt match {
      case Some(item) ⇒ evaluate(item, constraintOpt)
      case None       ⇒ true
    }
  }

  override def getIntrinsicDependencies =
    StaticProperty.DEPENDS_ON_CONTEXT_ITEM

  override def addToPathMap(
    pathMap        : PathMap,
    pathMapNodeSet : PathMapNodeSet
  ): PathMapNodeSet  = {

    val attachmentPoint = pathMapAttachmentPoint(pathMap, pathMapNodeSet)

    // For dependency on context
    if (attachmentPoint ne null)
      attachmentPoint.setAtomized()

    val result = new PathMapNodeSet
    iterateSubExpressions.asScala.asInstanceOf[Iterator[Expression]] foreach { child ⇒
      result.addNodeSet(child.addToPathMap(pathMap, attachmentPoint))
    }

    null
  }
}

trait LongValidationFunction   extends ValidationFunction[Long] {
  def argumentOpt(implicit xpathContext: XPathContext): Option[Long] = longArgumentOpt(0)
}

trait StringValidationFunction extends ValidationFunction[String] {
  def argumentOpt(implicit xpathContext: XPathContext): Option[String] = stringArgumentOpt(0)
}


// NOTE: This should probably be scope in the Form Builder module.
object ValidationFunction {

  private val BasicNamespaceMapping =
    new NamespaceMapping(Map(
      XFORMS_PREFIX        → XFORMS_NAMESPACE_URI,
      XFORMS_SHORT_PREFIX  → XFORMS_NAMESPACE_URI,
      XXFORMS_PREFIX       → XXFORMS_NAMESPACE_URI,
      XXFORMS_SHORT_PREFIX → XXFORMS_NAMESPACE_URI
    ).asJava)

  def analyzeKnownConstraint(
    xpathString     : String,
    functionLibrary : FunctionLibrary)(implicit
    logger          : IndentedLogger
  ): Option[(String, Option[String])] = {

    def tryCompile =
      Try(
        XPath.compileExpressionMinimal(
          staticContext = new ShareableXPathStaticContext(
            XPath.GlobalConfiguration,
            BasicNamespaceMapping, // TODO: use node namespaces
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

class MaxLengthValidation extends LongValidationFunction {

  val propertyName = "max-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) ⇒ org.orbeon.saxon.value.StringValue.getStringLength(value) <= constraint
    case None             ⇒ true
  }
}

class MinLengthValidation extends LongValidationFunction {

  val propertyName = "min-length"

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) ⇒ org.orbeon.saxon.value.StringValue.getStringLength(value) >= constraint
    case None             ⇒ true
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
    case Left(long)        ⇒ long.signum
    case Right(bigDecimal) ⇒ bigDecimal.signum
  }

  // Don't use Long.parseLong or similar as they throw exceptions, and we can have invalid data in many cases.
  // With this:
  //
  // - "small" integers (15 or 16 digits?) don't throw if invalid
  // - "large" integers throw if invalid (Saxon uses `Long.parseLong` and `new BigInteger`)
  // - decimals don't throw if invalid (Saxon uses a regex to validate first)
  //   - NOTE: Since Saxon 9.2, Saxon does throw instead! Handle this when upgrading to Saxon >= 9.2.
  def tryParseAsLongOrBigDecimal(value: String): Option[Either[Long, BigDecimal]] =
    IntegerValue.stringToInteger(value) match {
      case v: Int64Value        ⇒ Some(Left(v.longValue))
      case v: BigIntegerValue   ⇒ Some(Right(v.asDecimal))
      case v: ValidationFailure ⇒
        DecimalValue.makeDecimalValue(value, true) match {
          case v: DecimalValue      ⇒ Some(Right(v.getDecimalValue))
          case v: ValidationFailure ⇒ None
        }
    }

  def tryParseAsLong(value: String): Option[Long] =
    IntegerValue.stringToInteger(value) match {
      case v: Int64Value        ⇒ Some(v.longValue)
      case _                    ⇒ None
    }
}

class MaxFractionDigitsValidation extends LongValidationFunction {

  val propertyName = "fraction-digits"

  // Operate at the lexical level
  def countDigitsAfterDecimalSeparator(value: String) = {

    var beforeDecimalSeparator      = true
    var digitsAfterDecimalSeparator = 0
    var trailingZeros               = 0

    for (c ← value) {
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
    case Some(constraint) ⇒ countDigitsAfterDecimalSeparator(value) <= constraint
    case None             ⇒ true
  }
}

object UploadMaxSizeValidation {
  val PropertyName = "upload-max-size"
}

class UploadMaxSizeValidation extends LongValidationFunction {

  val propertyName = UploadMaxSizeValidation.PropertyName

  def evaluate(value: String, constraintOpt: Option[Long]) = constraintOpt match {
    case Some(constraint) ⇒ true // for now, don't actually validate, see #2956
    case None             ⇒ true
  }

}

object UploadMediatypesValidation {
  val PropertyName = "upload-mediatypes"
}

class UploadMediatypesValidation extends StringValidationFunction {

  val propertyName = UploadMediatypesValidation.PropertyName

  def evaluate(value: String, constraintOpt: Option[String]) = constraintOpt match {
    case Some(constraint) ⇒ true // for now, don't actually validate, see #3015
    case None             ⇒ true
  }

}