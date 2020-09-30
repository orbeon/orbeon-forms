/**
 * Copyright (C) 2004-2007 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import java.{lang => jl}

object NumberUtils {

  private val Digits = Array('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

  /**
   * Convert a byte array into a hexadecimal String representation.
   *
   * @param bytes array of bytes to convert
   * @return hexadecimal representation
   */
  def toHexString(bytes: Array[Byte]): String = {
    val sb = new jl.StringBuilder(bytes.length * 2)
    for (i <- bytes.indices) {
      sb.append(Digits((bytes(i) >> 4) & 0x0f))
      sb.append(Digits(bytes(i) & 0x0f))
    }
    sb.toString
  }

  /**
   * Convert a byte into a hexadecimal String representation.
   *
   * @param b byte to convert
   * @return hexadecimal representation
   */
  def toHexString(b: Byte): String = {
    val sb = new jl.StringBuilder(2)
    sb.append(Digits((b >> 4) & 0x0f))
    sb.append(Digits(b & 0x0f))
    sb.toString
  }

  def readIntBigEndian(bytes: Array[Byte], first: Int): Int =
    ((bytes(first + 0).toInt & 0xff) << 24)   +
      ((bytes(first + 1).toInt & 0xff) << 16) +
      ((bytes(first + 2).toInt & 0xff) << 8)  +
      (bytes(first + 3).toInt & 0xff)

  def readShortBigEndian(bytes: Array[Byte], first: Int): Short =
    (
      ((bytes(first + 0).toInt & 0xff) << 8) +
        (bytes(first + 1).toInt & 0xff)
    ).toShort

  def readShortLittleEndian(bytes: Array[Byte], first: Int): Short =
    (
      ((bytes(first + 1).toInt & 0xff) << 8) +
        (bytes(first + 0).toInt & 0xff)
    ).toShort
}