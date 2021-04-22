/**
  * Copyright (C) 2007 Orbeon, Inc.
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

import java.io._
import java.{lang => jl}

import org.orbeon.oxf.common.Defaults

import scala.util.control.NonFatal


object IOUtils {

  private val CopyBufferSize = 8192

  def copyStreamAndClose(in: InputStream, out: OutputStream, progress: Long => Unit = _ => (), doCloseOut: Boolean = true): Unit = {

    require(in ne null)
    require(out ne null)

    useAndClose(in) { in =>
      useAndClose(out, doCloseOut) { out =>
        val buffer = new Array[Byte](CopyBufferSize)
        Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read =>
          progress(read)
          out.write(buffer, 0, read)
        }
        out.flush()
      }
    }
  }

  def copyReaderAndClose(in: Reader, out: Writer, progress: Long => Unit = _ => (), doCloseOut: Boolean = true): Unit = {

    require(in ne null)
    require(out ne null)

    useAndClose(in) { in =>
      useAndClose(out, doCloseOut) { out =>
        val buffer = new Array[Char](CopyBufferSize)
        Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read =>
          progress(read)
          out.write(buffer, 0, read)
        }
      }
    }
  }

  // Scala 2.13 has `scala.util.Using`. Switch to that when possible.
  // Use a closable item and make sure an attempt to close it is done after use
  def useAndClose[T <: {def close(): Unit}, U](closable: T, doClose: Boolean = true)(block: T => U): U =
    try block(closable)
    finally {
      if (doClose && (closable ne null))
        runQuietly(closable.close())
    }

  // Run a block and swallow any exception. Use only for things like close().
  def runQuietly(block: => Unit): Unit =
    try block
    catch {
      case NonFatal(_) => // NOP
    }

  def readStreamAsStringAndClose(reader: Reader): String = {
    val writer = new StringBuilderWriter(new jl.StringBuilder)
    copyReaderAndClose(reader, writer)
    writer.result
  }

  // - JSON: "JSON text SHALL be encoded in Unicode.  The default encoding is UTF-8."
  //   http://www.ietf.org/rfc/rfc4627.txt
  // - other: we pick UTF-8 anyway (2014-09-18)
  def readStreamAsStringAndClose(is: InputStream, charset: Option[String]): String =
    useAndClose(new InputStreamReader(is, charset getOrElse Defaults.DefaultEncodingForModernUse)) { reader =>
      readStreamAsStringAndClose(reader)
    }
}
