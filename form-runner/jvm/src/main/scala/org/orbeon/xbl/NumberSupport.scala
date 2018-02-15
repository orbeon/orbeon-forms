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
import org.orbeon.saxon.om.NodeInfo

case class NumberConfig(
  decimalSeparator    : Char,
  groupingSeparator   : Char,
  prefix              : String,
  digitsAfterDecimal  : Option[Int],
  roundWhenFormatting : Boolean,
  roundWhenStoring    : Boolean
) {
  val decimalSeparatorString  = decimalSeparator.toString
  val groupingSeparatorString = groupingSeparator.toString
}

// Trait abstracts over the binding type to help with unit tests
trait NumberSupport[Binding] {

  import Private._

  def getStringValue(binding: Binding): String
  def getDatatypeOpt(binding: Binding): Option[dom.QName]
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

    def picture(precision: Int): String =
      pictureString(
        precision = precision,
        group     = true,
        zeroes    = true
      )

    val precisionForRoundingOpt =
      if (params.roundWhenFormatting) fractionDigitsOpt else None

    val formatted =
      (NumericValidation.tryParseAsLongOrBigDecimal(getStringValue(binding)), precisionForRoundingOpt) match {
        case (Some(sl @ Left(l)),  _      ) ⇒ formatNumber(sl,                           picture(fractionDigitsOpt getOrElse 0))
        case (Some(Right(d)) ,     Some(p)) ⇒ formatNumber(Right(roundBigDecimal(d, p)), picture(p))
        case (Some(sd @ Right(d)), None   ) ⇒ formatNumber(sd,                           picture(significantFractionalDigits(d).max(fractionDigitsOpt getOrElse 0)))
        case (None,                _      ) ⇒ editValue(getStringValue(binding))
      }

    formatted.translate(".,", params.decimalSeparatorString + params.groupingSeparator)
  }

  // See https://github.com/orbeon/orbeon-forms/issues/3226
  def roundBigDecimal(value: scala.BigDecimal, precision: Int): scala.BigDecimal =
    value.signum match {
      case -1 ⇒ value.setScale(precision, scala.BigDecimal.RoundingMode.HALF_DOWN)
      case  0 ⇒ value
      case  1 ⇒ value.setScale(precision, scala.BigDecimal.RoundingMode.HALF_UP)
      case  _ ⇒ throw new IllegalStateException
    }

  def storageValue(value: String, binding: Binding)(implicit params: NumberConfig): String = {

    val withPeriodEncoded =
      if (params.groupingSeparator == '.' || params.decimalSeparator == '.')
        value
      else
        value.translate(".", PeriodEncodedString)

    val withSeparatorsReplaced =
      withPeriodEncoded.translate(params.decimalSeparatorString + params.groupingSeparatorString, ".")

    val precisionForRoundingOpt =
      if (params.roundWhenStoring) fractionDigitsFromValidationOrProp(binding) else None

    (NumericValidation.tryParseAsLongOrBigDecimal(withSeparatorsReplaced), precisionForRoundingOpt) match {
      case (Some(Left(l)),  _      ) ⇒ l.toString // no decimal fraction to round
      case (Some(Right(d)), Some(p)) ⇒ formatNumber(Right(roundBigDecimal(d, p)), pictureString(p,        group = false, zeroes = false))
      case (Some(Right(d)), None   ) ⇒ formatNumber(Right(d),                     pictureString(d.scale , group = false, zeroes = false))
      case (None,           _      ) ⇒ withPeriodEncoded // Q: Unclear. Should just store `value`?
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
      (getDatatypeOpt(binding) exists (_.name == "integer")) option 0

    def propertyFractionDigitsOpt(binding: Binding)(implicit params: NumberConfig): Option[Int] =
      fractionDigitsIfInteger(binding) orElse params.digitsAfterDecimal

    def fractionDigitsFromValidationOrProp(binding: Binding)(implicit params: NumberConfig): Option[Int] =
      validationFractionDigitsOpt(binding) orElse propertyFractionDigitsOpt(binding)

    def formatNumber(decimal: Long Either scala.BigDecimal, pictureString: String): String =
      new DecimalFormat(pictureString, DefaultDecimalFormatSymbols).format(decimal.fold(identity, identity))

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

object NumberSupportJava extends NumberSupport[NodeInfo] {

  def getStringValue(binding: NodeInfo): String =
    binding.getStringValue

  def getDatatypeOpt(binding: NodeInfo): Option[dom.QName] =
    Option(InstanceData.getType(binding))

  def getCustomMipOpt(binding: NodeInfo, name: String): Option[String] =
    Option(InstanceData.collectAllCustomMIPs(binding)) flatMap (_ get name)

  //@XPathFunction
  def getDisplayValueJava(
    binding             : NodeInfo,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption  getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption getOrElse ',',
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    displayValue(binding)
  }

  //@XPathFunction
  def getStorageValueJava(
    value               : String,
    binding             : NodeInfo,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption  getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption getOrElse ',',
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    storageValue(value, binding)
  }

  //@XPathFunction
  def getEditValueJava(
    binding             : NodeInfo,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption  getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption getOrElse ',',
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOpt,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring
    )

    editValue(binding.getStringValue)
  }

  //@XPathFunction
  def isZeroValidationFractionDigitsJava(binding: NodeInfo): Boolean =
    validationFractionDigitsOpt(binding) contains 0

}