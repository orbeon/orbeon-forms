/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.io

import java.io.{FilterInputStream, InputStream}

import org.orbeon.datatypes.MaximumSize
import org.orbeon.datatypes.MaximumSize._

class LimiterInputStream(
  is           : InputStream,
  var maxBytes : MaximumSize,
  error        : (Long, Long) => Unit
) extends FilterInputStream(is) {

  private var bytesReadSoFar = 0L

  private def checkLimit(): Unit = maxBytes match {
    case LimitedSize(maxSize) if bytesReadSoFar > maxSize => error(maxSize, bytesReadSoFar)
    case  _ =>
  }

  override def read: Int = {
    val dataByteReadOrMinusOne = super.read()
    if (dataByteReadOrMinusOne != -1) {
      bytesReadSoFar += 1
      checkLimit()
    }
    dataByteReadOrMinusOne
  }

  override def read(b: Array[Byte], off: Int, len: Int): Int = {
    val numberOfBytesReadOrMinusOne = super.read(b, off, len)
    if (numberOfBytesReadOrMinusOne > 0) {
      bytesReadSoFar += numberOfBytesReadOrMinusOne
      checkLimit()
    }
    numberOfBytesReadOrMinusOne
  }
}
