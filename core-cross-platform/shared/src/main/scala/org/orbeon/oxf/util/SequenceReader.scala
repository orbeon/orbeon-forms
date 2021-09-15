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

import java.io.Reader
import java.{util => ju}

import org.orbeon.oxf.common.OXFException


// Only used by `XMLParsing`
class SequenceReader(var iterator: ju.Iterator[Reader]) extends Reader {

  // Initialize upon construction
  nextReader()

  private var reader: Reader = null

  override def read: Int =
    if (reader eq null)
      -1
    else {
      val c = reader.read
      if (c == -1) {
        nextReader()
        read
      } else
        c
    }

  def read(b: Array[Char], off: Int, len: Int): Int =
    if (reader eq null)
      -1
    else if (b eq null)
      throw new NullPointerException
    else if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) > b.length) || ((off + len) < 0))
      throw new IndexOutOfBoundsException
    else if (len == 0)
      0
    else {
      val n = reader.read(b, off, len)
      if (n <= 0) {
        nextReader()
        return read(b, off, len)
      }
      n
    }

  def close(): Unit = {
    do nextReader()
    while (reader ne null)
  }

  private def nextReader(): Unit = {
    if (reader ne null)
      reader.close()
    if (iterator.hasNext) {
      reader = iterator.next()
      if (reader eq null)
        throw new OXFException("Null reader passed to `SequenceReader`")
    } else
      reader = null
  }
}