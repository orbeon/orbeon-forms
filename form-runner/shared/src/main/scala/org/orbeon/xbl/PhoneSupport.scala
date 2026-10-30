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

import org.orbeon.oxf.util.StringUtils.*

import scala.util.matching.Regex


case class ParsedPhoneNumber(
  e164               : String,
  nationalFormatted  : String,
  internationalFormat: String,
  regionCodeOpt      : Option[String],
  hasExtension       : Boolean,
  isValid            : Boolean,
  isValidForRegion   : String => Boolean
)

trait PhoneSupportPlatformTrait {
  def countryCallingCode(regionCode: String): Int
  def exampleNationalNumber(regionCode: String): Option[String]
  def parsePhoneNumber(numberToParse: String, defaultRegionOpt: Option[String]): Option[ParsedPhoneNumber]
}

object PhoneSupportJava {

  private val E164Regex: Regex = """^\+[1-9]\d+$""".r
  private val FallbackCountry  = "US"

  private def isInternationalInput(s: String): Boolean =
    s.startsWith("+") || (s.startsWith("00") && s.length > 2 && s.charAt(2).isDigit && s.charAt(2) != '0')

  private def normalizeInternationalPrefix(s: String): String =
    if (s.startsWith("00") && s.length > 2 && s.charAt(2).isDigit && s.charAt(2) != '0')
      "+" + s.substring(2)
    else
      s

  private def isValidForSelectedRegion(p: ParsedPhoneNumber, selected: String): Boolean =
    p.isValid && (p.isValidForRegion(selected) || p.regionCodeOpt.contains(selected))

  //@XPathFunction
  def countryCallingCode(regionCode: String): Int =
    regionCode.trimAllToOpt
      .map(_.toUpperCase)
      .map(PhoneSupportPlatform.countryCallingCode)
      .getOrElse(0)

  //@XPathFunction
  def isValidSavedPhone(value: String): Boolean = {
    val trimmed = value.trimAllToEmpty
    trimmed.isEmpty || (
      E164Regex.matches(trimmed) &&
      PhoneSupportPlatform.parsePhoneNumber(trimmed, None).exists(p => p.isValid && p.e164 == trimmed)
    )
  }

  //@XPathFunction
  def formatReadonlyPhone(value: String, defaultCountry: String): String = {
    val trimmed = value.trimAllToEmpty
    if (trimmed.isEmpty) {
      ""
    } else {
      val defaultRegionOpt = defaultCountry.trimAllToOpt.map(_.toUpperCase).orElse(Some(FallbackCountry))
      PhoneSupportPlatform
        .parsePhoneNumber(normalizeInternationalPrefix(trimmed), defaultRegionOpt)
        .filter(_.isValid)
        .map(_.internationalFormat)
        .getOrElse(value)
    }
  }

  //@XPathFunction
  def exampleNationalNumber(regionCode: String): String =
    regionCode.trimAllToOpt
      .map(_.toUpperCase)
      .flatMap(PhoneSupportPlatform.exampleNationalNumber)
      .map(_.trimAllToEmpty)
      .getOrElse("")

  //@XPathFunction
  def extractCountry(
    value             : String,
    currentCountry    : String,
    defaultCountry    : String,
    availableCountries: String
  ): String = {
    val available = availableCountries.splitTo[List]().map(_.toUpperCase)
    val fallback =
      currentCountry.trimAllToOpt.map(_.toUpperCase).filter(available.contains)
        .orElse(defaultCountry.trimAllToOpt.map(_.toUpperCase).filter(available.contains))
        .orElse(Option(FallbackCountry).filter(available.contains))
        .orElse(available.headOption)
        .getOrElse(FallbackCountry)

    val trimmed = value.trimAllToEmpty
    if (trimmed.isEmpty || ! isInternationalInput(trimmed)) {
      fallback
    } else {
      PhoneSupportPlatform.parsePhoneNumber(normalizeInternationalPrefix(trimmed), Some(fallback)) match {
        case Some(p) if p.isValid =>
          if (available.contains(fallback) && isValidForSelectedRegion(p, fallback))
            fallback
          else
            p.regionCodeOpt.filter(available.contains).getOrElse(fallback)
        case _ =>
          fallback
      }
    }
  }

  //@XPathFunction
  def resolveCountryForInput(
    rawNumber         : String,
    selectedCountry   : String,
    availableCountries: String
  ): String = {
    val available = availableCountries.splitTo[List]().map(_.toUpperCase)
    val selected =
      selectedCountry.trimAllToOpt.map(_.toUpperCase).filter(available.contains)
        .orElse(available.headOption)
        .getOrElse(FallbackCountry)

    val trimmed = rawNumber.trimAllToEmpty
    if (trimmed.isEmpty || ! isInternationalInput(trimmed)) {
      selected
    } else {
      PhoneSupportPlatform.parsePhoneNumber(normalizeInternationalPrefix(trimmed), Some(selected)) match {
        case Some(p) if p.isValid =>
          if (available.contains(selected) && isValidForSelectedRegion(p, selected))
            selected
          else
            p.regionCodeOpt.filter(available.contains).getOrElse(selected)
        case _ =>
          selected
      }
    }
  }

  //@XPathFunction
  def computeStoredValue(
    rawNumber         : String,
    selectedCountry   : String,
    availableCountries: String
  ): String = {
    val available = availableCountries.splitTo[List]().map(_.toUpperCase)
    val selected  = selectedCountry.trimAllToOpt.map(_.toUpperCase).getOrElse(FallbackCountry)
    val trimmed   = rawNumber.trimAllToEmpty

    if (trimmed.isEmpty) {
      ""
    } else {
      val intl = isInternationalInput(trimmed)
      PhoneSupportPlatform.parsePhoneNumber(normalizeInternationalPrefix(trimmed), Some(selected)) match {
        case Some(p) if p.isValid =>
          val isValidAndAllowed =
            (available.contains(selected) && isValidForSelectedRegion(p, selected)) ||
            (intl && p.regionCodeOpt.exists(available.contains))
          if (isValidAndAllowed) {
            p.e164
          } else if (! E164Regex.matches(trimmed)) {
            trimmed
          } else if (p.internationalFormat.contains(" ")) {
            p.internationalFormat
          } else {
            s"${p.e164.take(2)} ${p.e164.drop(2)}"
          }
        case _ =>
          if (E164Regex.matches(trimmed))
            s"${trimmed.take(2)} ${trimmed.drop(2)}"
          else
            trimmed
      }
    }
  }

  //@XPathFunction
  def formatForEdit(
    value             : String,
    selectedCountry   : String,
    availableCountries: String
  ): String = {
    val available = availableCountries.splitTo[List]().map(_.toUpperCase)
    val selected  = selectedCountry.trimAllToOpt.map(_.toUpperCase).getOrElse(FallbackCountry)
    val trimmed   = value.trimAllToEmpty

    if (trimmed.isEmpty) {
      ""
    } else {
      PhoneSupportPlatform.parsePhoneNumber(normalizeInternationalPrefix(trimmed), Some(selected)) match {
        case Some(p) if available.contains(selected) && isValidForSelectedRegion(p, selected) =>
          p.nationalFormatted
        case _ =>
          trimmed
      }
    }
  }
}
