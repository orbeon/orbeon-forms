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
package org.orbeon.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal


@js.native
@JSGlobal("libphonenumber")
object LibPhoneNumber extends js.Object {

  val PhoneNumberUtil  : PhoneNumberUtilCompanion = js.native
  val PhoneNumberFormat: PhoneNumberFormatObj     = js.native
  val PhoneNumberType  : PhoneNumberTypeObj       = js.native

  @js.native
  trait PhoneNumberUtilCompanion extends js.Object {
    def getInstance(): PhoneNumberUtil = js.native
  }

  @js.native
  trait PhoneNumberUtil extends js.Object {
    def getCountryCodeForRegion(regionCode: String): Int                                     = js.native
    def parse(numberToParse: String, defaultRegion: String): PhoneNumber                     = js.native
    def parseAndKeepRawInput(numberToParse: String, defaultRegion: String): PhoneNumber      = js.native
    def isValidNumber(number: PhoneNumber): Boolean                                          = js.native
    def isValidNumberForRegion(number: PhoneNumber, regionCode: String): Boolean             = js.native
    def getRegionCodeForNumber(number: PhoneNumber): String                                  = js.native
    def format(number: PhoneNumber, numberFormat: Int): String                               = js.native
    def getExampleNumber(regionCode: String): PhoneNumber                                    = js.native
    def getExampleNumberForType(regionCode: String, numberType: Int): PhoneNumber            = js.native
  }

  @js.native
  trait PhoneNumber extends js.Object {
    def hasExtension(): Boolean = js.native
  }

  @js.native
  trait PhoneNumberFormatObj extends js.Object {
    val E164         : Int = js.native
    val INTERNATIONAL: Int = js.native
    val NATIONAL     : Int = js.native
    val RFC3966      : Int = js.native
  }

  @js.native
  trait PhoneNumberTypeObj extends js.Object {
    val FIXED_LINE          : Int = js.native
    val MOBILE              : Int = js.native
    val FIXED_LINE_OR_MOBILE: Int = js.native
  }
}
