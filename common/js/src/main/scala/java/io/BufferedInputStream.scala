/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package java.io


class BufferedInputStream(private var is: InputStream, size: Int) extends FilterInputStream(is) {

  if (size <= 0)
    throw new IllegalArgumentException("size must be > 0")

  protected var buf: Array[Byte] = new Array[Byte](size)
  protected var count = 0
  protected var marklimit = 0
  protected var markpos: Int = -1
  protected var pos = 0

  def this(in: InputStream) =
    this(in, 8192)

  override def available: Int = {
    val localIn = is // 'in' could be invalidated by close()
    if (buf == null || localIn == null)
      throw new IOException("Stream is closed")
    count - pos + localIn.available
  }

  override def close(): Unit = {
    buf = null
    val localIn = is
    is = null
    if (localIn != null)
      localIn.close()
  }

  private def fillbuf(localIn: InputStream, _localBuf: Array[Byte]): Int = {
    var localBuf = _localBuf
    if (markpos == -1 || (pos - markpos >= marklimit)) {
      // Mark position not set or exceeded readlimit
      val result = localIn.read(localBuf)
      if (result > 0) {
        markpos = -1
        pos = 0
        count = if (result == -1) 0 else result
      }
      return result
    }
    if (markpos == 0 && marklimit > localBuf.length) {
      // Increase buffer size to accommodate the readlimit
      var newLength = localBuf.length * 2
      if (newLength > marklimit)
        newLength = marklimit
      val newbuf = new Array[Byte](newLength)
      System.arraycopy(localBuf, 0, newbuf, 0, localBuf.length)
      // Reassign buf, which will invalidate any local references
      // FIXME: what if buf was null?
      localBuf = newbuf
      buf = newbuf
    } else if (markpos > 0)
      System.arraycopy(localBuf, markpos, localBuf, 0, localBuf.length - markpos)
    // Set the new position and mark position
    pos -= markpos
    count = 0
    markpos = 0
    val bytesread = localIn.read(localBuf, pos, localBuf.length - pos)
    count = if (bytesread <= 0) pos else pos + bytesread
    bytesread
  }

  override def mark(readlimit: Int): Unit = {
    marklimit = readlimit
    markpos = pos
  }

  override def markSupported = true

  override def read: Int = {
    // Use local refs since buf and in may be invalidated by an unsynchronized close()
    var localBuf = buf
    val localIn = is
    if (localBuf == null || localIn == null)
      throw new IOException("Stream is closed")
    // Are there buffered bytes available?
    if (pos >= count && fillbuf(localIn, localBuf) == -1)
      return -1
    // no, fill buffer
    // localBuf may have been invalidated by fillbuf
    if (localBuf ne buf) {
      localBuf = buf
      if (localBuf == null)
        throw new IOException("Stream is closed")
    }
    // Did filling the buffer fail with -1 (EOF)?
    if (count - pos > 0)
      return localBuf({pos += 1; pos - 1}) & 0xFF
    -1
  }

  override def read(buffer: Array[Byte], _offset: Int, length: Int): Int = {

    var offset = _offset

    // Use local ref since buf may be invalidated by an unsynchronized close()
    var localBuf = buf
    if (localBuf == null)
      throw new IOException("Stream is closed")
    // avoid int overflow
    if (offset > buffer.length - length || offset < 0 || length < 0)
      throw new IndexOutOfBoundsException
    if (length == 0)
      return 0
    val localIn = is
    if (localIn == null)
      throw new IOException("Stream is closed")

    var required =
      if (pos < count) {
        // There are bytes available in the buffer.
        val copylength = if (count - pos >= length) length else count - pos
        System.arraycopy(localBuf, pos, buffer, offset, copylength)
        pos += copylength
        if (copylength == length || localIn.available == 0)
          return copylength
        offset += copylength
        length - copylength
      } else
        length

    while (true) {
      var read = 0
      /*
       * If we're not marked and the required size is greater than the
       * buffer, simply read the bytes directly bypassing the buffer.
       */
      if (markpos == -1 && required >= localBuf.length) {
        read = localIn.read(buffer, offset, required)
        if (read == -1)
          return if (required == length) -1 else length - required
      } else {
        if (fillbuf(localIn, localBuf) == -1)
          return if (required == length) -1 else length - required
        if (localBuf ne buf) {
          localBuf = buf
          if (localBuf == null)
            throw new IOException("Stream is closed")
        }
        read = if (count - pos >= required) required else count - pos
        System.arraycopy(localBuf, pos, buffer, offset, read)
        pos += read
      }
      required -= read
      if (required == 0)
        return length
      if (localIn.available == 0)
        return length - required
      offset += read
    }
    -1
  }

  override def reset(): Unit = {
    if (buf == null)
      throw new IOException("Stream is closed")
    if (-1 == markpos)
      throw new IOException("Mark has been invalidated.")
    pos = markpos
  }

  override def skip(amount: Long): Long = {
    val localBuf = buf
    val localIn = is
    if (localBuf == null)
      throw new IOException("Stream is closed")
    if (amount < 1)
      return 0
    if (localIn == null)
      throw new IOException("Stream is closed")
    if (count - pos >= amount) {
      pos += amount.toInt // WARNING: `Long` to `Int` conversion!
      return amount
    }
    var read = count.toLong - pos
    pos = count
    if (markpos != -1)
      if (amount <= marklimit) {
        if (fillbuf(localIn, localBuf) == -1)
          return read
        if (count - pos >= amount - read) {
          pos += (amount - read).toInt // WARNING: `Long` to `Int` conversion!
          return amount
        }
        // Couldn't get all the bytes, skip what we read
        read += (count - pos)
        pos = count
        return read
      }
    read + localIn.skip(amount - read)
  }
}