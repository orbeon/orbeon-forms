/* ====================================================================
   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
package org.orbeon.oxf.fr.excel

import java.math.{BigDecimal, BigInteger}
import java.{lang => jl}


/**
 * Represents a transformation of a 64 bit IEEE double quantity having a decimal exponent and a
 * fixed point (15 decimal digit) significand.  Some quirks of Excel's calculation behaviour are
 * simpler to reproduce with numeric quantities in this format.  This class is currently used to
 * help:
 *
 * - Comparison operations
 * - Conversions to text
 *
 * This class does not handle negative numbers or zero.
 *
 * The value of a `NormalisedDecimal` is given by
 *
 *     significand × 10<sup>decimalExponent</sup>
 *
 * where:
 *
 *     `significand` = wholePart + fractionalPart / 2<sup>24</sup>
 */
object NormalisedDecimal {

  // Number of powers of ten contained in the significand
  private val ExponentOffset = 14

  private val Big2Pow24 = new BigDecimal(BigInteger.ONE.shiftLeft(24))

  // log<sub>10</sub>(2) × 2<sup>20</sup>
  private val LogBase10Of2Times2Pow20 = 315653 // 315652.8287

  // 2<sup>19</sup>
  private val C2Pow19 = 1 << 19

  // The value of `_fractionalPart` that represents 0.5
  private val FracHalf = 0x800000

  // 10<sup>15</sup>
  private val MaxRepWholePart = 0x38D7EA4C68000L

  def apply(frac: BigInteger, binaryExponent: Int): NormalisedDecimal = {
    // estimate pow2&pow10 first, perform optional mulShift, then normalize
    var pow10 =
      if (binaryExponent > 49 || binaryExponent < 46) {
        // working with ints (left shifted 20) instead of doubles
        // x = 14.5 - binaryExponent * log10(2);
        var x = (29 << 19) - binaryExponent * LogBase10Of2Times2Pow20
        x += C2Pow19 // round
        -(x >> 20)
      } else
        0

    var cc = FPNumber(frac, binaryExponent)
    if (pow10 != 0)
      cc = cc.multiplyByPowerOfTen(-pow10)

    cc.get64BitNormalisedExponent match {
      case 46 if cc.isAboveMinRep =>
      case 44 | 45 | 46 =>
        cc = cc.multiplyByPowerOfTen(1)
        pow10 -= 1
      case 47 | 48 =>
      case 49 if cc.isBelowMaxRep =>
      case 49 | 50 =>
        cc = cc.multiplyByPowerOfTen(-1)
        pow10 += 1
      case _ =>
        throw new IllegalStateException("Bad binary exp " + cc.get64BitNormalisedExponent + ".")
    }
    cc = cc.normalise64bit
    cc.createNormalisedDecimal(pow10)
  }
}

case class NormalisedDecimal(
  wholePart               : Long, // whole part of the significand (typically 15 digits)
                                  // 47-50 bits long (MSB may be anywhere from bit 46 to 49)
                                  // LSB is units bit
  fractionalPart          : Int,  // fractional part of the significand.
                                  // 24 bits (only top 14-17 bits significant): a value between 0x000000 and 0xFFFF80
  relativeDecimalExponent : Int   // decimal exponent increased by one less than the digit count of `wholePart`
) {

  // Rounds at the digit with value 10<sup>decimalExponent</sup>
  def roundUnits: NormalisedDecimal = {

    var wp = wholePart
    if (fractionalPart >= NormalisedDecimal.FracHalf)
      wp += 1

    val de = relativeDecimalExponent
    if (wp < NormalisedDecimal.MaxRepWholePart)
      NormalisedDecimal(wp, 0, de)
    else
      NormalisedDecimal(wp / 10, 0, de + 1)
  }

  /**
   * Convert to an equivalent `ExpandedDouble` representation (binary frac and exponent).
   * The resulting transformed object is easily converted to a 64 bit IEEE double:
   *
   * - bits 2-53 of the `significand` become the 52 bit 'fraction'.
   * - `binaryExponent` is biased by 1023 to give the 'exponent'.
   *
   * The sign bit must be obtained from somewhere else.
   *
   * @return a new `NormalisedDecimal` normalised to base 2 representation.
   */
  def normaliseBaseTwo: ExpandedDouble = {
    var cc = new FPNumber(composeFrac, 39)
    cc = cc.multiplyByPowerOfTen(relativeDecimalExponent)
    cc = cc.normalise64bit
    cc.createExpandedDouble
  }

  // Return the significand as a fixed point number (with 24 fraction bits and 47-50 whole bits)
  private def composeFrac: BigInteger = {
    val wp = wholePart
    val fp = fractionalPart
    new BigInteger(
      Array[Byte](
        (wp >> 56).toByte, // N.B. assuming sign bit is zero
        (wp >> 48).toByte,
        (wp >> 40).toByte,
        (wp >> 32).toByte,
        (wp >> 24).toByte,
        (wp >> 16).toByte,
        (wp >> 8).toByte,
        (wp >> 0).toByte,
        (fp >> 16).toByte,
        (fp >> 8).toByte,
        (fp >> 0).toByte
      )
    )
  }

  def getSignificantDecimalDigits: String = wholePart.toString

  /**
   * Rounds the first whole digit position (considers only units digit, not fractional part).
   * Caller should check total digit count of result to see whether the rounding operation caused
   * a carry out of the most significant digit
   */
  def getSignificantDecimalDigitsLastDigitRounded: String = {
    val wp = wholePart + 5 // rounds last digit
    val sb = new jl.StringBuilder(24)
    sb.append(wp)
    sb.setCharAt(sb.length - 1, '0')
    sb.toString
  }

  // Return the number of powers of 10 which have been extracted from the significand and
  // binary exponent.
  def getDecimalExponent: Int =
    relativeDecimalExponent + NormalisedDecimal.ExponentOffset

  // Assumes both this and other are normalised
  def compareNormalised(other: NormalisedDecimal): Int = {
    val cmp = relativeDecimalExponent - other.relativeDecimalExponent
    if (cmp != 0)
      cmp
    else if (wholePart > other.wholePart)
      1
    else if (wholePart < other.wholePart)
      -1
    else
      fractionalPart - other.fractionalPart
  }

  def getFractionalPart: BigDecimal =
    new BigDecimal(fractionalPart).divide(NormalisedDecimal.Big2Pow24)

  private def getFractionalDigits: String =
    if (fractionalPart == 0)
      "0"
    else
      getFractionalPart.toString.substring(2)

  override def toString: String = {
    val sb = new jl.StringBuilder
    sb.append(getClass.getName)
    sb.append(" [")
    val ws = String.valueOf(wholePart)
    sb.append(ws.charAt(0))
    sb.append('.')
    sb.append(ws.substring(1))
    sb.append(' ')
    sb.append(getFractionalDigits)
    sb.append("E")
    sb.append(getDecimalExponent)
    sb.append("]")
    sb.toString
  }
}