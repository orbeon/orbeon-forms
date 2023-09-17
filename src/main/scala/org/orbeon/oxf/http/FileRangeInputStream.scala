package org.orbeon.oxf.http

import java.io.{File, FileInputStream, IOException, InputStream}

case class FileRangeInputStream(file: File, httpRange: HttpRange) extends InputStream {
  private val fis            = new FileInputStream(file)
  private val start: Long    = httpRange.start
  private val end: Long      = httpRange.end.map(_ + 1).getOrElse(file.length()) // End is exclusive
  private var position: Long = fis.skip(start)

  assert(position == start, s"Initial skip failed: $position != $start")

  override def read: Int =
    if (position >= end) {
      -1
    } else {
      val byte = fis.read()
      if (byte >= 0) {
        position += 1
      }
      byte
    }

  override def read(b: Array[Byte]): Int =
    read(b, 0, b.length)

  override def read(b: Array[Byte], off: Int, len: Int): Int =
    if (position >= end) {
      -1
    } else {
      val bytesToRead = Math.min(len, available)
      val bytesRead   = fis.read(b, off, bytesToRead)
      if (bytesRead >= 0) {
        position += bytesRead
      }
      bytesRead
    }

  override def skip(n: Long): Long = {
    val bytesToSkip  = Math.min(n, available)
    val bytesSkipped = fis.skip(bytesToSkip)
    position += bytesSkipped
    bytesSkipped
  }

  override def available: Int =
    Math.min(Int.MaxValue, end - position).toInt

  override def close(): Unit =
    fis.close()

  override def mark(readlimit: Int): Unit = throw new IOException("mark/reset not supported")
  override def reset(): Unit              = throw new IOException("mark/reset not supported")
  override def markSupported: Boolean     = false
}
