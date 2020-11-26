package org.orbeon.oxf.xforms.state

import java.io._

import org.orbeon.oxf.util.WhitelistObjectInputStream
import org.orbeon.oxf.xml.SAXStore
import sbinary.DefaultProtocol._
import sbinary.Operations._
import sbinary._

import scala.jdk.CollectionConverters._


object XFormsCommonBinaryFormats {

  class JavaOutputStream(output: Output) extends OutputStream {
    def write(b: Int): Unit = { output.writeByte(b.asInstanceOf[Byte]) }
    override def write(b: Array[Byte], off: Int, len: Int): Unit = { output.writeAll(b, off, len) }
  }

  class JavaInputStream(input: Input) extends InputStream {
    def read(): Int = input.readByte
    override def read(b: Array[Byte], off: Int, len: Int): Int = input.readTo(b, off, len)
  }

  // Base trait for stuff that should be serialized via Serializable/Externalizable
  trait SerializableFormat[T <: java.io.Serializable] extends Format[T] {

    def allowedClass: Class[T]

    def writes(output: Output, o: T): Unit =
      new ObjectOutputStream(new JavaOutputStream(output)).writeObject(o)

    def reads(input: Input): T =
      WhitelistObjectInputStream(new JavaInputStream(input), allowedClass, List("org.orbeon.oxf.http.HttpMethod$")).readObject.asInstanceOf[T]
  }

  implicit object SAXStoreFormat extends Format[SAXStore] {

    def reads(in: Input): SAXStore = {

      val ss = new SAXStore // This will call `init()` and allocate the arrays. Was not the case earlier with Java and `Externalizable`.

      ss.eventBufferPosition = read[Int](in)
      ss.eventBuffer = new Array[Byte](ss.eventBufferPosition)
      for (i <- 0 until ss.eventBufferPosition)
        ss.eventBuffer(i) = read[Byte](in)
      ss.charBufferPosition = read[Int](in)
      ss.charBuffer = new Array[Char](ss.charBufferPosition)
      for (i <- 0 until ss.charBufferPosition)
        ss.charBuffer(i) = read[Char](in)
      ss.intBufferPosition = read[Int](in)
      ss.intBuffer = new Array[Int](ss.intBufferPosition)
      for (i <- 0 until ss.intBufferPosition)
        ss.intBuffer(i) = read[Int](in)
      ss.lineBufferPosition = read[Int](in)
      ss.lineBuffer = new Array[Int](ss.lineBufferPosition)
      for (i <- 0 until ss.lineBufferPosition)
        ss.lineBuffer(i) = read[Int](in)
      ss.systemIdBufferPosition = read[Int](in)
      ss.systemIdBuffer = new Array[String](ss.systemIdBufferPosition)
      for (i <- 0 until ss.systemIdBufferPosition) {
        ss.systemIdBuffer(i) = read[String](in)
        if ("" == ss.systemIdBuffer(i))
          ss.systemIdBuffer(i) = null
      }
      ss.attributeCountBufferPosition = read[Int](in)
      ss.attributeCountBuffer = new Array[Int](ss.attributeCountBufferPosition)
      for (i <- 0 until ss.attributeCountBufferPosition) {
        val count = read[Int](in)
        ss.attributeCountBuffer(i) = count
        ss.attributeCount += count
      }
      val stringBuilderSize = read[Int](in)
      for (_ <- 0 until stringBuilderSize)
        ss.stringBuilder.add(read[String](in))
      ss.hasDocumentLocator = read[Boolean](in)
      ss.publicId = read[String](in)
      if ("" == ss.publicId)
        ss.publicId = null
      val marksCount = read[Int](in)
      if (marksCount > 0)
        for (_ <- 0 until marksCount) {
          val id = read[String](in)
          val values = new Array[Int](7)
          for (j <- 0 until 7)
            values(j) = read[Int](in)
          ss.newMark(values, id)
        }

      ss
    }

    def writes(out: Output, value: SAXStore): Unit = {
      write(out, value.eventBufferPosition)
      for (i <- 0 until value.eventBufferPosition)
        write(out, value.eventBuffer(i))
      write(out, value.charBufferPosition)
      for (i <- 0 until value.charBufferPosition)
        write(out, value.charBuffer(i))
      write(out, value.intBufferPosition)
      for (i <- 0 until value.intBufferPosition)
        write(out, value.intBuffer(i))
      write(out, value.lineBufferPosition)
      for (i <- 0 until value.lineBufferPosition)
        write(out, value.lineBuffer(i))
      write(out, value.systemIdBufferPosition)
      for (i <- 0 until value.systemIdBufferPosition) {
        val systemId = value.systemIdBuffer(i)
        write(out, if (systemId == null) "" else systemId)
      }
      write(out, value.attributeCountBufferPosition)
      for (i <- 0 until value.attributeCountBufferPosition)
        write(out, value.attributeCountBuffer(i))
      write(out, value.stringBuilder.size)
      for (i <- 0 until value.stringBuilder.size)
        write(out, value.stringBuilder.get(i))
      write(out, value.hasDocumentLocator)
      write(out, if (value.publicId == null) "" else value.publicId)
      if (value.marks == null || value.marks.isEmpty)
        write(out, 0)
      else {
        write(out, value.marks.size)
        for (mark <- value.marks.asScala) {
          write(out, mark._id)
          write(out, mark.eventBufferPosition)
          write(out, mark.charBufferPosition)
          write(out, mark.intBufferPosition)
          write(out, mark.lineBufferPosition)
          write(out, mark.systemIdBufferPosition)
          write(out, mark.attributeCountBufferPosition)
          write(out, mark.stringBuilderPosition)
        }
      }
    }
  }
}
