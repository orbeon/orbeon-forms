/**
 * Copyright (C) 2004 Orbeon, Inc.
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

import java.io.{IOException, Reader}

import org.orbeon.oxf.common.OXFException

class SequenceReader(var iterator: Iterator[_]) extends Reader {

  try nextReader()
  catch {
    case _: IOException =>
      throw new OXFException("Invalid state")
  }
  private var reader: Reader = null

  @throws[IOException]
  override def read: Int = {
    if (reader == null) return -1
    val c = reader.read
    if (c == -1) {
      nextReader()
      return read
    }
    c
  }

  @throws[IOException]
  override def read(b: Array[Char], off: Int, len: Int): Int = {
    if (reader == null) return -1
    else if (b == null) throw new NullPointerException
    else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0)) throw new IndexOutOfBoundsException
    else if (len == 0) return 0
    val n = reader.read(b, off, len)
    if (n <= 0) {
      nextReader()
      return read(b, off, len)
    }
    n
  }

  @throws[IOException]
  override def close() {
    do nextReader() while ( {
      reader != null
    })
  }

  @throws[IOException]
  private[util] def nextReader() {
    if (reader != null) reader.close()
    if (iterator.hasNext) {
      reader = iterator.next.asInstanceOf[Reader]
      if (reader == null) throw new OXFException("Null reader passed to " + getClass.getName)
    }
    else reader = null
  }
}