package org.orbeon.oxf.xforms.state

import java.io._

import org.orbeon.oxf.util.WhitelistObjectInputStream
import org.orbeon.oxf.xml.SAXStore
import sbinary._


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

  implicit object SAXStoreFormat extends SerializableFormat[SAXStore] {
    def allowedClass: Class[SAXStore] = classOf[SAXStore]
  }
}