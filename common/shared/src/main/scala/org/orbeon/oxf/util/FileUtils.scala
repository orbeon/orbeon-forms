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

import java.math.BigInteger


object FileUtils {

  def byteCountToDisplaySize(size: Long): String =
    byteCountToDisplaySize(BigInteger.valueOf(size))

  def byteCountToDisplaySize(size: BigInteger): String =
    if (size.divide(ONE_EB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_EB_BI)) + " EB"
    else if (size.divide(ONE_PB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_PB_BI)) + " PB"
    else if (size.divide(ONE_TB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_TB_BI)) + " TB"
    else if (size.divide(ONE_GB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_GB_BI)) + " GB"
    else if (size.divide(ONE_MB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_MB_BI)) + " MB"
    else if (size.divide(ONE_KB_BI).compareTo(BigInteger.ZERO) > 0)
      String.valueOf(size.divide(ONE_KB_BI)) + " KB"
    else
      String.valueOf(size) + " bytes"

  val ONE_KB    : Long       = 1024L
  val ONE_KB_BI : BigInteger = BigInteger.valueOf(ONE_KB)
  val ONE_MB    : Long       = ONE_KB * ONE_KB
  val ONE_MB_BI : BigInteger = ONE_KB_BI.multiply(ONE_KB_BI)
  val ONE_GB    : Long       = ONE_KB * ONE_MB
  val ONE_GB_BI : BigInteger = ONE_KB_BI.multiply(ONE_MB_BI)
  val ONE_TB    : Long       = ONE_KB * ONE_GB
  val ONE_TB_BI : BigInteger = ONE_KB_BI.multiply(ONE_GB_BI)
  val ONE_PB    : Long       = ONE_KB * ONE_TB
  val ONE_PB_BI : BigInteger = ONE_KB_BI.multiply(ONE_TB_BI)
  val ONE_EB    : Long       = ONE_KB * ONE_PB
  val ONE_EB_BI : BigInteger = ONE_KB_BI.multiply(ONE_PB_BI)
  val ONE_ZB    : BigInteger = BigInteger.valueOf(ONE_KB).multiply(BigInteger.valueOf(ONE_EB))
  val ONE_YB    : BigInteger = ONE_KB_BI.multiply(ONE_ZB)
}
