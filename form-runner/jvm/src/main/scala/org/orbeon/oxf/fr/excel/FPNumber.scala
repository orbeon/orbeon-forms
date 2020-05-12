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


private object FPNumber {

  // TODO - what about values between (10<sup>14</sup>-0.5) and (10<sup>14</sup>-0.05) ?
  /**
   * The minimum value in 'Base-10 normalised form'.
   * When `_binaryExponent` == 46 this is the the minimum `_frac` value
   *
   * (10<sup>14</sup>-0.05) * 2^17
   *
   * Values between (10<sup>14</sup>-0.05) and 10<sup>14</sup> will be represented as '1'
   * followed by 14 zeros.
   *
   * Values less than (10<sup>14</sup>-0.05) will get shifted by one more power of 10
   *
   * This frac value rounds to '1' followed by fourteen zeros with an incremented decimal exponent
   */
  val BigMinBase = new BigInteger("0B5E620F47FFFE666", 16)

  /**
   * For 'Base-10 normalised form'
   * The maximum `_frac` value when `_binaryExponent` == 49
   * (10^15-0.5) * 2^14
   */
  val BigMaxBase = new BigInteger("0E35FA9319FFFE000", 16)

  // Width of a long
  val C64 = 64

  // Minimum precision after discarding whole 32-bit words from the significand
  val MinPrecision = 72

  object Rounder {

    private val HalfBits: Array[BigInteger] = {
      val bis = new Array[BigInteger](33)
      var acc = 1
      for (i <- 1 until bis.length) {
        bis(i) = BigInteger.valueOf(acc)
        acc <<= 1
      }
      bis
    }

    /**
     * @param nBits number of bits to shift right
     */
    def round(bi: BigInteger, nBits: Int): BigInteger =
      if (nBits < 1)
        bi
      else
        bi.add(HalfBits(nBits))
  }

  // Holds values for quick multiplication and division by 10
  object TenPower {

    private val Five = new BigInteger("5")
    private val _cache = new Array[TenPower](350)

    def apply(index: Int): TenPower = {
      var result = _cache(index)
      if (result == null) {
        result = applyNoCache(index)
        _cache(index) = result
      }
      result
    }

    private def applyNoCache(index: Int): TenPower = {

      val fivePowIndex = Five.pow(index)

      var bitsDueToFiveFactors = fivePowIndex.bitLength
      val px      = 80 + bitsDueToFiveFactors
      val fx      = BigInteger.ONE.shiftLeft(px).divide(fivePowIndex)
      val adj     = fx.bitLength - 80
      val divisor = fx.shiftRight(adj)

      bitsDueToFiveFactors -= adj

      val divisorShift = -(bitsDueToFiveFactors + index + 80)

      val sc = fivePowIndex.bitLength - 68
      if (sc > 0)
        TenPower(
          fivePowIndex.shiftRight(sc),
          divisor,
          divisorShift,
          index + sc
        )
      else
        TenPower(
          fivePowIndex,
          divisor,
          divisorShift,
          index
        )
    }
  }

  case class TenPower(
    multiplicand    : BigInteger,
    divisor         : BigInteger,
    divisorShift    : Int,
    multiplierShift : Int
  )
}

case class FPNumber(significand: BigInteger, binaryExponent: Int) {

  import FPNumber._

  def normalise64bit: FPNumber = {

    var newSignificand    = significand
    var newBinaryExponent = binaryExponent

    var oldBitLen = newSignificand.bitLength
    var sc = oldBitLen - C64

    if (sc == 0) {
      this
    } else if (sc < 0) {
      throw new IllegalStateException("Not enough precision")
    } else {
      newBinaryExponent += sc
      if (sc > 32) {
        val highShift = (sc - 1) & 0xFFFFE0
        newSignificand = newSignificand.shiftRight(highShift)
        sc -= highShift
        oldBitLen -= highShift
      }
      if (sc < 1)
        throw new IllegalStateException
      newSignificand = Rounder.round(newSignificand, sc)
      if (newSignificand.bitLength > oldBitLen) {
        sc += 1
        newBinaryExponent += 1
      }
      newSignificand = newSignificand.shiftRight(sc)

      FPNumber(newSignificand, newBinaryExponent)
    }
  }

  def get64BitNormalisedExponent: Int =
    binaryExponent + significand.bitLength - C64

  def isBelowMaxRep: Boolean = {
    val sc = significand.bitLength - C64
    significand.compareTo(BigMaxBase.shiftLeft(sc)) < 0
  }

  def isAboveMinRep: Boolean = {
    val sc = significand.bitLength - C64
    significand.compareTo(BigMinBase.shiftLeft(sc)) > 0
  }

  def createNormalisedDecimal(pow10: Int): NormalisedDecimal = {
    // missingUnderBits is (0..3)
    val missingUnderBits = binaryExponent - 39
    val fracPart = (significand.intValue << missingUnderBits) & 0xFFFF80
    val wholePart = significand.shiftRight(C64 - binaryExponent - 1).longValue
    new NormalisedDecimal(wholePart, fracPart, pow10)
  }

  def multiplyByPowerOfTen(pow10: Int): FPNumber = {
    val tp = TenPower(Math.abs(pow10))
    if (pow10 < 0)
      mulShift(tp.divisor, tp.divisorShift)
    else
      mulShift(tp.multiplicand, tp.multiplierShift)
  }

  def createExpandedDouble: ExpandedDouble =
    ExpandedDouble(significand, binaryExponent)

  private def mulShift(multiplicand: BigInteger, multiplierShift: Int): FPNumber = {

    var newSignificand    = significand
    var newBinaryExponent = binaryExponent

    newSignificand = newSignificand.multiply(multiplicand)
    newBinaryExponent += multiplierShift
    // check for too much precision
    val sc = (newSignificand.bitLength - MinPrecision) & 0xFFFFFFE0
    // mask makes multiples of 32 which optimises BigInteger.shiftRight
    if (sc > 0) { // no need to round because we have at least 8 bits of extra precision
      newSignificand = newSignificand.shiftRight(sc)
      newBinaryExponent += sc
    }

    FPNumber(newSignificand, newBinaryExponent)
  }
}