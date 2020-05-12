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

import java.{lang => jl}


/**
 * Excel converts numbers to text with different rules to those of java, so
 * `Double.toString(value)` won't do.
 *
 * - No more than 15 significant figures are output (java does 18).
 * - The sign char for the exponent is included even if positive
 * - Special values (`NaN` and `Infinity`) get rendered like the ordinary
 *   number that the bit pattern represents.
 * - Denormalized values (between ±2<sup>-1074</sup> and ±2<sup>-1022</sup>
 *   are displayed as "0"
 *
 * See tests for IEEE 64-bit Double Rendering Comparison.
 *
 * *Note*:
 *
 * Excel has inconsistent rules for the following numeric operations:
 *
 * - Conversion to string (as handled here)
 * - Rendering numerical quantities in the cell grid.
 * - Conversion from text
 * - General arithmetic
 *
 * Excel's text to number conversion is not a true *inverse* of this operation.  The
 * allowable ranges are different.  Some numbers that don't correctly convert to text actually
 * __do__ get handled properly when used in arithmetic evaluations.
 */
object NumberToTextConverter {

  import Private._

  /**
   * Converts the supplied <tt>value</tt> to the text representation that Excel would give if
   * the value were to appear in an unformatted cell, or as a literal number in a formula.<br>
   * Note - the results from this method differ slightly from those of <tt>Double.toString()</tt>
   * In some special cases Excel behaves quite differently.  This function attempts to reproduce
   * those results.
   */
  def toText(value: Double): String =
    rawDoubleBitsToText(java.lang.Double.doubleToLongBits(value))

  def rawDoubleBitsToText(pRawBits: Long): String = {

    var rawBits = pRawBits

    var isNegative = rawBits < 0 // sign bit is in the same place for long and double
    if (isNegative)
      rawBits &= 0x7FFFFFFFFFFFFFFFL

    if (rawBits == 0)
      return if (isNegative) "-0" else "0"

    val ed = ExpandedDouble(rawBits)
    if (ed.binaryExponent < -1022) {
      // value is 'denormalised' which means it is less than 2^-1022
      // excel displays all these numbers as zero, even though calculations work OK
      return if (isNegative) "-0" else "0"
    }

    if (ed.binaryExponent == 1024) {
      // Special number NaN /Infinity
      // Normally one would not create HybridDecimal objects from these values
      // except in these cases Excel really tries to render them as if they were normal numbers
      if (rawBits == ExcelNaNBits)
        return "3.484840871308E+308"
      // This is where excel really gets it wrong
      // Special numbers like Infinity and NaN are interpreted according to
      // the standard rules below.
      isNegative = false // except that the sign bit is ignored
    }

    val nd = ed.normaliseBaseTen
    val sb = new jl.StringBuilder(MaxTextLen + 1)
    if (isNegative)
      sb.append('-')
    convertToText(sb, nd)
    sb.toString
  }

  private object Private {

     val ExcelNaNBits = 0xFFFF0420003C0000L
     val MaxTextLen   = 20

     def convertToText(sb: jl.StringBuilder, pnd: NormalisedDecimal): Unit = {

      val rnd = pnd.roundUnits
      var decExponent = rnd.getDecimalExponent

      val decimalDigits =
        if (Math.abs(decExponent) > 98) {
          val r = rnd.getSignificantDecimalDigitsLastDigitRounded
          if (r.length == 16) {
            // rounding caused carry
            decExponent += 1
          }
          r
        } else
          rnd.getSignificantDecimalDigits

      val countSigDigits = countSignificantDigits(decimalDigits)
      if (decExponent < 0)
        formatLessThanOne(sb, decimalDigits, decExponent, countSigDigits)
      else
        formatGreaterThanOne(sb, decimalDigits, decExponent, countSigDigits)
    }

     def formatLessThanOne(sb: jl.StringBuilder, decimalDigits: String, decExponent: Int, countSigDigits: Int): Unit = {

      val nLeadingZeros = -decExponent - 1
      val normalLength = 2 + nLeadingZeros + countSigDigits // 2 == "0.".length()

      if (needsScientificNotation(normalLength)) {
        sb.append(decimalDigits.charAt(0))
        if (countSigDigits > 1) {
          sb.append('.')
          sb.append(decimalDigits.subSequence(1, countSigDigits))
        }
        sb.append("E-")
        appendExp(sb, -decExponent)
        return
      }
      sb.append("0.")
      for (_ <- nLeadingZeros until 0 by -1)
        sb.append('0')
      sb.append(decimalDigits.subSequence(0, countSigDigits))
    }

     def formatGreaterThanOne(sb: jl.StringBuilder, decimalDigits: String, decExponent: Int, countSigDigits: Int): Unit = {
      if (decExponent > 19) {
        // scientific notation
        sb.append(decimalDigits.charAt(0))
        if (countSigDigits > 1) {
          sb.append('.')
          sb.append(decimalDigits.subSequence(1, countSigDigits))
        }
        sb.append("E+")
        appendExp(sb, decExponent)
        return
      }
      val nFractionalDigits = countSigDigits - decExponent - 1
      if (nFractionalDigits > 0) {
        sb.append(decimalDigits.subSequence(0, decExponent + 1))
        sb.append('.')
        sb.append(decimalDigits.subSequence(decExponent + 1, countSigDigits))
        return
      }
      sb.append(decimalDigits.subSequence(0, countSigDigits))
      for (_ <- -nFractionalDigits until 0 by -1)
        sb.append('0')
    }

     def needsScientificNotation(nDigits: Int): Boolean =
      nDigits > MaxTextLen

     def countSignificantDigits(sb: String): Int = {
      var result = sb.length - 1
      while (sb.charAt(result) == '0') {
        result -= 1
        if (result < 0)
          throw new RuntimeException("No non-zero digits found")
      }
      result + 1
    }

     def appendExp(sb: jl.StringBuilder, v: Int): Unit =
      if (v < 10) {
        sb.append('0')
        sb.append(('0' + v).toChar)
      } else {
        sb.append(v)
      }
  }
}
