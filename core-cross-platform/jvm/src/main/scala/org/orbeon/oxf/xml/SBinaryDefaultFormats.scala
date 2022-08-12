package org.orbeon.oxf.xml

import sbinary.Operations.{read, write}
import sbinary._

import java.io.UTFDataFormatException


object SBinaryDefaultFormats extends StandardTypes with StandardPrimitives {

  private def getUTFLength(s: String): Long = {

    val length = s.length
    var result = 0L

    var i = 0
    while (i < length) {
      val c = s.charAt(i)
      result += (
        if ((c >= 0x0001) && (c <= 0x007F)) 1
        else if (c > 0x07FF) 3
        else 2)
      i += 1
    }

    result
  }

  // NOTE: This used to use a `ThreadLocal`, but we don't want lingering `ThreadLocals` around so for now we create the
  // buffers every time.

  // Modified version of sbinary JavaUTF to support reading/writing longer strings
  // See also https://github.com/orbeon/orbeon-forms/issues/5404
  implicit object StringFormat extends Format[String] {
    def reads(input: Input): String = {
      // Read 4-byte size header (ObjectInputStream uses 2 or 8)
      val utfLength = read[Int](input)
      val (cbuffer, bbuffer) = (new Array[Char](utfLength * 2), new Array[Byte](utfLength * 2))

      input.readFully(bbuffer, 0, utfLength)

      var count, charCount, c, char2, char3 = 0

      def malformed(index: Int) = throw new UTFDataFormatException("Malformed input around byte " + index)
      def partial = throw new UTFDataFormatException("Malformed input: Partial character at end")

      while ((count < utfLength) && { c = bbuffer(count) & 0xff; c <= 127 }) {
        cbuffer(charCount) = c.toChar
        charCount += 1
        count += 1
      }

      while (count < utfLength) {
        c = bbuffer(count).toInt & 0xFF
        cbuffer(charCount) = (c >> 4 match {
          case 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 =>
            count += 1
            c
          case 12 | 13 =>
            count += 2
            if (count > utfLength) partial

            char2 = bbuffer(count - 1)
            if ((char2 & 0xC0) != 0x80) malformed(count)
            ((c & 0x1F) << 6) | (char2 & 0x3F)
          case 14 =>
            count += 3
            char2 = bbuffer(count - 2)
            char3 = bbuffer(count - 1)
            if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
              malformed(count - 1)

            ((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0)
          case _ => malformed(count)
        }).toChar
        charCount += 1
      }

      new String(cbuffer, 0, charCount)
    }

    def writes(output: Output, value: String): Unit = {

      // Write 4-byte size header (ObjectOutputStream uses 2 or 8)
      val utfLength = getUTFLength(value).toInt
      write(output, utfLength)

      val bbuffer = new Array[Byte](utfLength * 2)
      var count = 0
      def append(value: Int): Unit = {
        bbuffer(count) = value.toByte
        count += 1
      }

      var i = 0
      def c = value.charAt(i)

      while ((i < value.length) && ((c >= 0x0001) && (c <= 0x007F))) {
        bbuffer(count) = c.toByte
        count += 1
        i += 1
      }

      while (i < value.length) {
        if ((c >= 0x0001) && (c <= 0x007F)) {
          append(c)
        } else if (c > 0x07FF) {
          append(0xE0 | ((c >> 12) & 0x0F))
          append(0x80 | ((c >> 6) & 0x3F))
          append(0x80 | ((c >> 0) & 0x3F))
        } else {
          append(0xC0 | ((c >> 6) & 0x1F))
          append(0x80 | ((c >> 0) & 0x3F))
        }

        i += 1
      }

      output.writeAll(bbuffer, 0, utfLength)
    }
  }
}