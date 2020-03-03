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

import java.math.BigInteger


/**
 * Represents a 64 bit IEEE double quantity expressed with both decimal and binary exponents.
 * Does not handle negative numbers or zero.
 *
 * The value of an `ExpandedDouble` is given by:
 *
 * `a × 2<sup>b</sup>`
 *
 * where:
 *
 * `a` = *significand*
 * `b` = *binaryExponent* - bitLength(significand) + 1
 */
object ExpandedDouble {

  private val ExponentShift = 52

  private val FracMask: Long = 0x000FFFFFFFFFFFFFL
  private val FracAssumedHighBit: Long = 1L << ExponentShift

  private val BigFracMask           = BigInteger.valueOf(FracMask)
  private val BigFracAssumedHighBit = BigInteger.valueOf(FracAssumedHighBit)

  private def getFrac(rawBits: Long): BigInteger =
    BigInteger.valueOf(rawBits).and(BigFracMask).or(BigFracAssumedHighBit).shiftLeft(11)

  def apply(rawBits: Long): ExpandedDouble = {
    val biasedExp = Math.toIntExact(rawBits >> 52)
    if (biasedExp == 0) {
      // sub-normal numbers
      val frac = BigInteger.valueOf(rawBits).and(BigFracMask)
      val expAdj = 64 - frac.bitLength

      ExpandedDouble(
        frac.shiftLeft(expAdj),
        (biasedExp & 0x07FF) - 1023 - expAdj
      )
    } else {
      ExpandedDouble(
        getFrac(rawBits),
        (biasedExp & 0x07FF) - 1023
      )
    }
  }
}

case class ExpandedDouble(significand: BigInteger, binaryExponent: Int) {

  // Always 64 bits long (MSB, bit-63 is '1')
  if (significand.bitLength != 64)
    throw new IllegalArgumentException("bad bit length")

  /**
   * Convert to an equivalent `NormalisedDecimal` representation having 15 decimal digits
   * of precision in the non-fractional bits of the significand.
   */
  def normaliseBaseTen: NormalisedDecimal =
    NormalisedDecimal(significand, binaryExponent)
}