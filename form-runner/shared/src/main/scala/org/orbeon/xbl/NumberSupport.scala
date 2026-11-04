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

import org.orbeon.dom
import org.orbeon.oxf.util.CoreUtils.*
import org.orbeon.oxf.util.StringUtils.*
import org.orbeon.oxf.xforms.function.xxforms.NumericValidation
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.XMLConstants
import org.orbeon.saxon.om
import org.orbeon.saxon.value.{DecimalValue, IntegerValue}

import java.text.{DecimalFormat, DecimalFormatSymbols}
import java.util.Locale
import scala.util.{Failure, Success}


case class NumberConfig(
  decimalSeparator    : Char,
  groupingSeparator   : Option[Char],
  prefix              : String,
  digitsAfterDecimal  : Option[Int],
  roundWhenFormatting : Boolean,
  roundWhenStoring    : Boolean,
  scaleFactor         : Int = 0
) {
  val decimalSeparatorString  : String         = decimalSeparator.toString
  val groupingSeparatorString : Option[String] = groupingSeparator map (_.toString)
}

// Trait abstracts over the binding type to help with unit tests
trait NumberSupport[Binding] {

  import Private.*

  def getStringValue (binding: Binding): String
  def getDatatypeOpt (binding: Binding): Option[dom.QName]
  def getCustomMipOpt(binding: Binding, name: String): Option[String]

  // NOTE: Also return `None` if the `fraction-digits` custom MIP is read and is not an `Int`.
  def validationFractionDigitsOpt(binding: Binding): Option[Int] =
    fractionDigitsIfInteger(binding) orElse (getCustomMipOpt(binding, "fraction-digits") flatMap (_.toIntOption))

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

    (NumericValidation.tryParseAsLongOrBigDecimal(getStringValue(binding)) map (scaleParsedValue(_, params.scaleFactor)), precisionForRoundingOpt) match {
      case (Some(sl @ Left(_)),  _      ) => format(sl,                           picture(fractionDigitsOpt getOrElse 0))
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
      case (Some(Left(l)),  _      ) if params.scaleFactor == 0 =>
        l.toString // no decimal fraction to round
      case (Some(Left(l)),  _      ) =>
        val scaled = scala.BigDecimal(java.math.BigDecimal.valueOf(l).scaleByPowerOfTen(-params.scaleFactor))
        formatNumber(Right(scaled), pictureString(scaled.scale, group = false, zeroes = false))
      case (Some(Right(d)), Some(p)) =>
        val rounded = roundBigDecimal(d, p)
        val scaled  = if (params.scaleFactor == 0) rounded else scala.BigDecimal(rounded.bigDecimal.scaleByPowerOfTen(-params.scaleFactor))
        formatNumber(Right(scaled), pictureString(p + params.scaleFactor, group = false, zeroes = false))
      case (Some(Right(d)), None   ) =>
        val scaled = if (params.scaleFactor == 0) d else scala.BigDecimal(d.bigDecimal.scaleByPowerOfTen(-params.scaleFactor))
        formatNumber(Right(scaled), pictureString(scaled.scale, group = false, zeroes = false))
      case (None,           _      ) =>
        withInvalidPeriodEncoded
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
      validationFractionDigitsOpt(binding).map(d => (d - params.scaleFactor).max(0)) orElse propertyFractionDigitsOpt(binding)

    def scaleParsedValue(v: Long Either scala.BigDecimal, powerOfTen: Int): Long Either scala.BigDecimal =
      if (powerOfTen == 0) {
        v
      } else {
        val bd = v match {
          case Left(l)  => java.math.BigDecimal.valueOf(l)
          case Right(d) => d.bigDecimal
        }
        val scaled = bd.scaleByPowerOfTen(powerOfTen)
        Right(scala.BigDecimal(if (scaled.scale < 0) scaled.setScale(0) else scaled))
      }

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

    def tryParseAndPrintDecimalValue(value: String)(implicit params: NumberConfig): Option[String] =
      NumericValidation.tryParseAsLongOrBigDecimal(value) map (scaleParsedValue(_, params.scaleFactor)) map printDecimalValue

    def printDecimalValue(value: Long Either scala.BigDecimal): String =
      value.fold(_.toString, _.bigDecimal.toPlainString)

    def significantFractionalDigits(decimal: scala.BigDecimal): Int =
      decimal.bigDecimal.stripTrailingZeros.scale.max(0)
  }
}

object NumberSupportJava extends NumberSupport[om.Item] {

  import io.circe.generic.auto.*

  def getStringValue(binding: om.Item): String =
    binding.getStringValue

  def getDatatypeOpt(binding: om.Item): Option[dom.QName] =
    binding match {
      case v: om.NodeInfo  => Option(InstanceData.getType(v))
      case v: IntegerValue => Some(XMLConstants.XS_INTEGER_QNAME)
      case v: DecimalValue => Some(XMLConstants.XS_DECIMAL_QNAME)
      case _               => None
    }

  def getCustomMipOpt(binding: om.Item, name: String): Option[String] =
    binding match {
      case v: om.NodeInfo => InstanceData.findCustomMip(v, name)
      case _              => None
    }

  //@XPathFunction
  def getDisplayValueJava(
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String =
    getDisplayValueJava(
      binding             = binding,
      decimalSeparator    = decimalSeparator,
      groupingSeparator   = groupingSeparator,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = 0
    )

  //@XPathFunction
  def getDisplayValueJava(
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean,
    scaleFactor         : Int
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOption,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = scaleFactor
    )

    displayValue(binding)
  }

  //@XPathFunction
  def serializeExternalValueJava(
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String =
    serializeExternalValueJava(
      binding             = binding,
      decimalSeparator    = decimalSeparator,
      groupingSeparator   = groupingSeparator,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = 0
    )

  //@XPathFunction
  def serializeExternalValueJava(
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean,
    scaleFactor         : Int
  ): String = {

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOption,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = scaleFactor
    )

    import io.circe.syntax.*

    NumberExternalValue(
      displayValue(binding),
      editValue(binding.getStringValue),
      params.decimalSeparator
    ).asJson.noSpaces
  }

  //@XPathFunction
  def deserializeExternalValueJava(
    externalValue       : String,
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean
  ): String =
    deserializeExternalValueJava(
      externalValue       = externalValue,
      binding             = binding,
      decimalSeparator    = decimalSeparator,
      groupingSeparator   = groupingSeparator,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = 0
    )

  //@XPathFunction
  def deserializeExternalValueJava(
    externalValue       : String,
    binding             : om.Item,
    decimalSeparator    : String,
    groupingSeparator   : String,
    prefix              : String,
    digitsAfterDecimal  : String,
    roundWhenFormatting : Boolean,
    roundWhenStoring    : Boolean,
    scaleFactor         : Int
  ): String = {

    import io.circe.parser

    val editValueFromClient =
      parser.decode[NumberExternalValue](externalValue).fold(Failure.apply, Success.apply) map (_.editValue) getOrElse ""

    implicit val params = NumberConfig(
      decimalSeparator    = decimalSeparator.headOption getOrElse '.',
      groupingSeparator   = groupingSeparator.headOption,
      prefix              = prefix,
      digitsAfterDecimal  = digitsAfterDecimal.toIntOption,
      roundWhenFormatting = roundWhenFormatting,
      roundWhenStoring    = roundWhenStoring,
      scaleFactor         = scaleFactor
    )

    storageValue(editValueFromClient, binding)
  }

  //@XPathFunction
  def isZeroValidationFractionDigitsJava(binding: om.Item): Boolean =
    validationFractionDigitsOpt(binding) contains 0
}