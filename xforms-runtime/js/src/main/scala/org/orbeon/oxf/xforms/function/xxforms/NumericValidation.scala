package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.saxon.model.ValidationFailure
import org.orbeon.saxon.value._


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
        BigDecimalValue.makeDecimalValue(value, validate = true) match {
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