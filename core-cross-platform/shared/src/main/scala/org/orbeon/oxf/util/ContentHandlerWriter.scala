package org.orbeon.oxf.util

import java.io.Writer

import org.xml.sax.ContentHandler

/**
 * A `Writer` that converts the characters written into it into characters written to a `ContentHandler`.
 */
class ContentHandlerWriter(
  contentHandler : ContentHandler,
  supportFlush   : Boolean = false
) extends Writer {

  private val singleChar = new Array[Char](1)

  def close(): Unit = ()

  def flush(): Unit =
    if (supportFlush)
      contentHandler.processingInstruction("orbeon-serializer", "flush")

  override def write(c: Int): Unit = {
    singleChar(0) = c.toChar
    contentHandler.characters(singleChar, 0, 1)
  }

  override def write(cbuf: Array[Char]): Unit =
    contentHandler.characters(cbuf, 0, cbuf.length)

  override def write(str: String): Unit = {
    val len = str.length
    val c = new Array[Char](len)
    str.getChars(0, str.length, c, 0)
    contentHandler.characters(c, 0, len)
  }

  override def write(str: String, off: Int, len: Int): Unit = {
    val c = new Array[Char](len)
    str.getChars(off, off + len, c, 0)
    contentHandler.characters(c, 0, len)
  }

  override def write(cbuf: Array[Char], off: Int, len: Int): Unit =
    contentHandler.characters(cbuf, off, len)
}