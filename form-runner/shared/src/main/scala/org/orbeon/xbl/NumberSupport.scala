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
package org.orbeon.xbl

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale

import org.orbeon.dom
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.oxf.xforms.function.xxforms.NumericValidation
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om.{Item, NodeInfo}
import org.orbeon.saxon.value.{DecimalValue, IntegerValue}

import scala.util.{Failure, Success}


case class NumberConfig(
  decimalSeparator    : Char,
  groupingSeparator   : Option[Char],
  prefix              : String,
  digitsAfterDecimal  : Option[Int],
  roundWhenFormatting : Boolean,
  roundWhenStoring    : Boolean
) {
  val decimalSeparatorString  : String         = decimalSeparator.toString
  val groupingSeparatorString : Option[String] = groupingSeparator map (_.toString)
}

// Trait abstracts over the binding type to help with unit tests
trait NumberSupport[Binding] {

  import Private._

  def getStringValue (binding: Binding): String
  def getDatatypeOpt (binding: Binding): Option[dom.QName]
  def getCustomMipOpt(binding: Binding, name: String): Option[String]

  // NOTE: Also return `None` if the `fraction-digits` custom MIP is read and is not an `Int`.
  def validationFractionDigitsOpt(binding: Binding): Option[Int] =
    fractionDigitsIfInteger(binding) orElse (getCustomMipOpt(binding, "fraction-digits") flatMap (_.toIntOpt))

  def editValue(binding: String)(implicit params: NumberConfig): String = {

    val raw = binding

    val rawTrimmed = raw.trimAllToEmpty

    // There shouldn't be a prefix because that's stored out of band,  but we remove it for historical reasons
    val prefixRemoved =
      if (rawTrimmed.startsWith(params.prefix))
        rawTrimmed.substring(params.prefix.size).trimAllToEmpty
      else
        rawTrimmed

    val normalized =
      tryParseAndPrintDecimalValue(prefixRemoved) getOrElse raw

    // Be sure to replace `decimal-separator` before `grouping-separator` because `grouping-separator`
    // can be blank: https://github.com/orbeon/orbeon-forms/issues/587
    normalized.translate(".", params.decimalSeparatorString).translate(PeriodEncodedString, ".")
  }

  def displayValue(binding: Binding)(implicit params: NumberConfig): String = {

    val fractionDigitsOpt =
      fractionDigitsFromValidationOrProp(binding)

    val precisionForRoundingOpt =
      if (params.roundWhenFormatting) fractionDigitsOpt else None

    def picture(precision: Int): String =
      pictureString(
        precision = precision,
        group     = true,
        zeroes    = true
      )

    def format(decimal: Long Either scala.BigDecimal, pictureString: String): String =
      formatNumber(decimal, pictureString)
        .translate(".,", params.decimalSeparatorString + params.groupingSeparatorString.getOrElse(""))

    (NumericValidation.tryParseAsLongOrBigDecimal(getStringValue(binding)), precisionForRoundingOpt) match {
      case (Some(sl @ Left(l)),  _      ) => format(sl,                           picture(fractionDigitsOpt getOrElse 0))
      case (Some(Right(d)) ,     Some(p)) => format(Right(roundBigDecimal(d, p)), picture(p))
      case (Some(sd @ Right(d)), None   ) => format(sd,                           picture(significantFractionalDigits(d).max(fractionDigitsOpt getOrElse 0)))
      case (None,                _      ) => editValue(getStringValue(binding))
    }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/3226
  def roundBigDecimal(value: scala.BigDecimal, precision: Int): scala.BigDecimal =
    value.signum match {
      case -1 => value.setScale(precision, scala.BigDecimal.RoundingMode.HALF_DOWN)
      case  0 => value
      case  1 => value.setScale(precision, scala.BigDecimal.RoundingMode.HALF_UP)
      case  _ => throw new IllegalStateException
    }

  def storageValue(value: String, binding: Binding)(implicit params: NumberConfig): String = {

    // If the `.` is used for something we know to be an invalid character, replace it right away by `≣`.
    // We keep this behavior for now, but it isn't strictly needed, e.g. in the `1.2.3` → `1≣2≣3` example
    // below, we could keep `1.2.3` in the data, as it isn't a valid number. If we decide not to do this
    // encoding anymore, we need to make sure we're still doing the `≣` → `.` when reading the data, for
    // backward compatibility with data saved with earlier versions of Orbeon Forms.
    //
    // - With Polish formatting (1 234,56)
    //     - `1.2.3`        → `1≣2≣3` (invalid `.`)
    //     - `1.2` or `1,2` → `1.2`   (still access `.`, as done by number field on iOS & Android)
    // - With French formatting (1.234,56)
    //     - `1.2`          → `12`

    val withInvalidPeriodEncoded =
      if (params.groupingSeparator.contains('.') || params.decimalSeparator == '.' ||
        (! value.contains(params.decimalSeparator) && value.count(_ == '.') == 1))
        value
      else
        value.translate(".", PeriodEncodedString)

    val withSeparatorsReplaced =
      withInvalidPeriodEncoded.translate(params.decimalSeparatorString + params.groupingSeparatorString.getOrElse(""), ".")

    val precisionForRoundingOpt =
      if (params.roundWhenStoring) fractionDigitsFromValidationOrProp(binding) else None

    (NumericValidation.tryParseAsLongOrBigDecimal(withSeparatorsReplaced), precisionForRoundingOpt) match {
      case (Some(Left(l)),  _      ) => l.toString // no decimal fraction to round
      case (Some(Right(d)), Some(p)) => formatNumber(Right(roundBigDecimal(d, p)), pictureString(p,        group = false, zeroes = false))
      case (Some(Right(d)), None   ) => formatNumber(Right(d),                     pictureString(d.scale , group = false, zeroes = false))
      case (None,           _      ) => withInvalidPeriodEncoded
    }
  }

  private object Private {

    // Character used to represent a `.` entered by users, when that character isn't a valid decimal separator.
    val PeriodEncoded = '\u2261'
    val PeriodEncodedString = PeriodEncoded.toString

    val IntegerPictureWithoutGrouping = "##0"
    val IntegerPictureWithGrouping    = "#,##0"

    val DefaultDecimalFormatSymbols = {
      val symbols = new DecimalFormatSymbols(Locale.ENGLISH)
      symbols.setDecimalSeparator('.')
      symbols.setGroupingSeparator(',')
      symbols
    }

    def fractionDigitsIfInteger(binding: Binding): Option[Int] =
      (getDatatypeOpt(binding) exists (_.localName == "integer")) option 0

    def propertyFractionDigitsOpt(binding: Binding)(implicit params: NumberConfig): Option[Int] =
      fractionDigitsIfInteger(binding) orElse params.digitsAfterDecimal

    def fractionDigitsFromValidationOrProp(binding: Binding)(implicit params: NumberConfig): Option[Int] =
      validationFractionDigitsOpt(binding) orElse propertyFractionDigitsOpt(binding)

    def formatNumber(decimal: Long Either scala.BigDecimal, pictureString: String): String = {
      val decimalFormat = new DecimalFormat(pictureString, DefaultDecimalFormatSymbols)
      decimal match {
        case Left(decimalLong)        =>
          decimalFormat.format(decimalLong)
        case Right(decimalBigDecimal) =>
          // Pass a `java.math.BigDecimal` to `java/text/DecimalFormat.java`
          decimalFormat.format(decimalBigDecimal.bigDecimal)
      }
    }

    def pictureString(precision: Int, group: Boolean, zeroes: Boolean): String =
      (if (group) IntegerPictureWithGrouping else IntegerPictureWithoutGrouping) + {
        if (precision <= 0)
          ""
        else
          "." + ((if (zeroes) "0" else "#") * precision)
      }

    def tryParseAndPrintDecimalValue(value: String): Option[String] =
      NumericValidation.tryParseAsLongOrBigDecimal(value) map printDecimalValue

    def printDecimalValue(value: Long Either scala.BigDecimal): String =
      value.fold(_.toString, _.bigDecimal.toPlainString)

    def significantFractionalDigits(decimal: scala.BigDecimal): Int =
      decimal.bigDecimal.stripTrailingZeros.scale.max(0)
  }
}

object NumberSupportJava extends NumberSupport[Item] {

  import io.circe.generic.auto._

  def getStringValue(binding: Item): String =
    binding.getStringValue

  def getDatatypeOpt(binding: Item): Option[dom.QName] =
    binding match {
      case v: NodeInfo     => Option(InstanceData.getType(v))
      case v: IntegerValue => Some(XMLConstants.XS_INTEGER_QNAME)
      case v: DecimalValue => Some(XMLConstants.XS_DECIMAL_QNAME)
      case _               => None
    }

  def getCustomMipOpt(binding: Item, name: String): Option[String] =
    binding match {
      case v: NodeInfo => InstanceData.findCustomMip(v, name)
      case _           => None
    }

  //@XPathFunction
  def getDisplayValueJava(
    binding             : Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    displayValue(binding)
  }

  //@XPathFunction
  def serializeExternalValueJava(
    binding             : Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    import io.circe.syntax._

    NumberExternalValue(
      displayValue(binding),
      editValue(binding.getStringValue),
      params.decimalSeparator
    ).asJson.noSpaces
  }

  //@XPathFunction
  def deserializeExternalValueJava(
    externalValue       : String,
    binding             : Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    import io.circe.parser

    val editValueFromClient =
      parser.decode[NumberExternalValue](externalValue).fold(Failure.apply, Success.apply) map (_.editValue) getOrElse ""

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    storageValue(editValueFromClient, binding)
  }

  //@XPathFunction
  def isZeroValidationFractionDigitsJava(binding: Item): Boolean =
    validationFractionDigitsOpt(binding) contains 0
}