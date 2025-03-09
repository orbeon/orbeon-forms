/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.orbeon.oxf.util

import java.math.{BigDecimal, BigInteger, RoundingMode}

object ByteSizeUtils {

  def byteCountToCompactDisplaySize(size: Long): String = {
    val sizeBI = BigInteger.valueOf(size)
    if (sizeBI.divide(ONE_EB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${formatSize(sizeBI, ONE_EB_BI, 5)} EB"
    else if (sizeBI.divide(ONE_PB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${formatSize(sizeBI, ONE_PB_BI, 4)} PB"
    else if (sizeBI.divide(ONE_TB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${formatSize(sizeBI, ONE_TB_BI, 3)} TB"
    else if (sizeBI.divide(ONE_GB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${formatSize(sizeBI, ONE_GB_BI, 2)} GB"
    else if (sizeBI.divide(ONE_MB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${formatSize(sizeBI, ONE_MB_BI, 1)} MB"
    else if (sizeBI.divide(ONE_KB_BI).compareTo(BigInteger.ZERO) > 0)
      s"${sizeBI.divide(ONE_KB_BI)} KB"
    else
      s"${size} B"
  }

  def byteCountToFullDisplaySize(size: Long): String = {
    val compactSize = byteCountToCompactDisplaySize(size)
    val sizeBI = BigInteger.valueOf(size)
    if (sizeBI.divide(ONE_KB_BI).compareTo(BigInteger.ZERO) > 0)
      s"$compactSize (${size} B)"
    else
      compactSize
  }

  private def formatSize(size: BigInteger, divisor: BigInteger, decimalPlaces: Int): String =
    new BigDecimal(size)
      .divide(new BigDecimal(divisor), decimalPlaces, RoundingMode.HALF_UP)
      .stripTrailingZeros()
      .toPlainString

  private val ONE_KB_BI : BigInteger = BigInteger.valueOf(1024L)
  private val ONE_MB_BI : BigInteger = ONE_KB_BI.multiply(ONE_KB_BI)
  private val ONE_GB_BI : BigInteger = ONE_KB_BI.multiply(ONE_MB_BI)
  private val ONE_TB_BI : BigInteger = ONE_KB_BI.multiply(ONE_GB_BI)
  private val ONE_PB_BI : BigInteger = ONE_KB_BI.multiply(ONE_TB_BI)
  private val ONE_EB_BI : BigInteger = ONE_KB_BI.multiply(ONE_PB_BI)
}
