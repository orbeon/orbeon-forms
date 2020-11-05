/**
*  Copyright (C) 2012 Orbeon, Inc.
*
*  This program is free software; you can redistribute it and/or modify it under the terms of the
*  GNU Lesser General Public License as published by the Free Software Foundation; either version
*  2.1 of the License, or (at your option) any later version.
*
*  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
*  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
*  See the GNU Lesser General Public License for more details.
*
*  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
*/
package org.orbeon.oxf.xforms.state

import java.io._

import javax.xml.transform.OutputKeys
import javax.xml.transform.stream.StreamResult
import org.orbeon.dom.{Document, Namespace, QName}
import org.orbeon.oxf.util.PathMatcher
import org.orbeon.oxf.xforms.model.InstanceCaching
import org.orbeon.oxf.xforms.state.XFormsCommonBinaryFormats._
import org.orbeon.oxf.xml.TransformerUtils
import org.orbeon.oxf.xml.dom4j.LocationDocumentSource
import org.orbeon.xforms.DelayedEvent
import sbinary.Operations._
import sbinary._


// TODO: Almost none of this should be needed, as case classes, etc. should be handled automatically.
object XFormsOperations {

  // NOTE: We use immutable.Seq instead of Array to indicate immutability

  def toByteSeq[T: Writes](t: T): Seq[Byte] =
    toByteArray(t).toSeq // actually a `WrappedArray`

  def fromByteSeq[T: Reads](bytes: Seq[Byte]): T =
    fromByteArray(bytes.toArray) // TODO: inefficient copy to array -> implement Input instead
}

object XFormsProtocols extends StandardTypes with StandardPrimitives with JavaLongUTF {

  implicit object DynamicStateFormat extends SerializableFormat[DynamicState] {
    def allowedClass: Class[DynamicState] = classOf[DynamicState]
  }

  implicit object Dom4jFormat extends Format[Document] {
    def writes(output: Output, document: Document): Unit = {
      val identity = TransformerUtils.getXMLIdentityTransformer
      identity.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
      identity.transform(new LocationDocumentSource(document), new StreamResult(new JavaOutputStream(output)))
    }

    def reads(input: Input): Document =
      TransformerUtils.readDom4j(new JavaInputStream(input), null, false, false)
  }

  implicit object ControlFormat extends Format[ControlState] {

    def writes(output: Output, control: ControlState): Unit = {
      write(output, control.effectiveId)
      write(output, control.visited)
      write(output, control.keyValues)
    }

    def reads(input: Input): ControlState =
      ControlState(read[String](input), read[Boolean](input), read[Map[String, String]](input))
  }

  implicit object DelayedEventFormat extends Format[DelayedEvent] {

    def writes(output: Output, delayedEvent: DelayedEvent): Unit = {
      write(output, delayedEvent.eventName)
      write(output, delayedEvent.targetEffectiveId)
      write(output, delayedEvent.bubbles)
      write(output, delayedEvent.cancelable)
      write(output, delayedEvent.time)
      write(output, delayedEvent.showProgress)
      write(output, delayedEvent.browserTarget)
      write(output, delayedEvent.isResponseResourceType)
    }

    def reads(input: Input): DelayedEvent =
      DelayedEvent(
        read[String](input),
        read[String](input),
        read[Boolean](input),
        read[Boolean](input),
        read[Option[Long]](input),
        read[Boolean](input),
        read[Option[String]](input),
        read[Boolean](input)
      )
  }

  implicit object InstanceCachingFormat extends Format[InstanceCaching] {

    def writes(output: Output, instance: InstanceCaching): Unit = {
      write(output, instance.timeToLive)
      write(output, instance.handleXInclude)
      write(output, instance.pathOrAbsoluteURI)
      write(output, instance.requestBodyHash)
    }

    def reads(in: Input): InstanceCaching =
      InstanceCaching(
        read[Long](in),
        read[Boolean](in),
        read[String](in),
        read[Option[String]](in)
      )
  }

  implicit object InstanceFormat extends Format[InstanceState] {

    def writes(output: Output, instance: InstanceState): Unit = {
      write(output, instance.effectiveId)
      write(output, instance.modelEffectiveId)
      instance.cachingOrContent match {
        case Left(caching)  => write[Byte](output, 0); write(output, caching)
        case Right(content) => write[Byte](output, 1); write(output, content)
      }
      write(output, instance.readonly)
      write(output, instance.modified)
      write(output, instance.valid)
    }

    def reads(in: Input): InstanceState = {

      def readCachingOrContent = read[Byte](in) match {
        case 0 => Left(read[InstanceCaching](in))
        case 1 => Right(read[String](in))
      }

      InstanceState(
        read[String](in),
        read[String](in),
        readCachingOrContent,
        read[Boolean](in),
        read[Boolean](in),
        read[Boolean](in)
      )
    }
  }

  implicit object QNameFormat extends Format[QName] {
    def writes(out: Output, value: QName): Unit = {
      write(out, value.localName)
      write(out, value.namespace.prefix)
      write(out, value.namespace.uri)
      write(out, value.qualifiedName)

    }

    def reads(in: Input): QName =
      QName(
        read[String](in),
        Namespace(read[String](in), read[String](in)),
        read[String](in)
      )
  }

  implicit object PathMatcherFormat extends Format[PathMatcher] {
    def writes(output: Output, value: PathMatcher): Unit = {
      write(output, value.regexp)
      write(output, Option(value.mimeType))
      write(output, value.versioned)
    }

    def reads(in: Input): PathMatcher =
      PathMatcher(
        read[String](in),
        read[Option[String]](in).orNull,
        read[Boolean](in)
      )
  }
}

// Modified version of sbinary JavaUTF to support reading/writing longer strings
trait JavaLongUTF extends CoreProtocol {

  private def getUTFLength(s: String) = {

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

  // NOTE: This used to use a ThreadLocal, but we don't want lingering ThreadLocals around so for now we create the
  // buffers every time.

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
