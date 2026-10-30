/**
 * Copyright (C) 2026 Orbeon, Inc.
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

import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.{PhoneNumberFormat, PhoneNumberType}

import scala.util.Try


object PhoneSupportPlatform extends PhoneSupportPlatformTrait {

  private lazy val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.getInstance()

  def countryCallingCode(regionCode: String): Int = {
    val upper = regionCode.toUpperCase
    if (phoneUtil.getSupportedRegions.contains(upper))
      Try(phoneUtil.getCountryCodeForRegion(upper)).getOrElse(0)
    else
      0
  }

  def exampleNationalNumber(regionCode: String): Option[String] =
    Try {
      val upper = regionCode.toUpperCase
      Option(phoneUtil.getExampleNumber(upper))
        .orElse(Option(phoneUtil.getExampleNumberForType(upper, PhoneNumberType.MOBILE)))
        .map(phoneUtil.format(_, PhoneNumberFormat.NATIONAL))
    }.toOption.flatten

  def parsePhoneNumber(numberToParse: String, defaultRegionOpt: Option[String]): Option[ParsedPhoneNumber] =
    Try {
      val parsed       = phoneUtil.parseAndKeepRawInput(numberToParse, defaultRegionOpt.map(_.toUpperCase).orNull)
      val hasExtension = parsed.hasExtension
      val isValid      = ! hasExtension && phoneUtil.isValidNumber(parsed)
      ParsedPhoneNumber(
        e164                = phoneUtil.format(parsed, PhoneNumberFormat.E164),
        nationalFormatted   = phoneUtil.format(parsed, PhoneNumberFormat.NATIONAL),
        internationalFormat = phoneUtil.format(parsed, PhoneNumberFormat.INTERNATIONAL),
        regionCodeOpt       = Option(phoneUtil.getRegionCodeForNumber(parsed)).map(_.toUpperCase),
        hasExtension        = hasExtension,
        isValid             = isValid,
        isValidForRegion    = region => ! hasExtension && Try(phoneUtil.isValidNumberForRegion(parsed, region.toUpperCase)).getOrElse(false)
      )
    }.toOption
}
