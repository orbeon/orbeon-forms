/**
 *  Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.util

import java.io._

object ScalaUtils {

    private val COPY_BUFFER_SIZE = 8192

    // Copy an InputStream to an OutputStream, with optional progress information
    def copyStream(in: InputStream, out: OutputStream, closeOut: Boolean = true, progress: (Long) => Unit = _ => ()) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in =>
            useAndClose(out) { out =>
                val buffer = new Array[Byte](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read =>
                    progress(read)
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    // Copy a Reader to a writer, with optional progress information
    def copyReader(in: Reader, out: Writer, closeOut: Boolean = true, progress: (Long) => Unit = _ => ()) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in =>
            useAndClose(out) { out =>
                val buffer = new Array[Char](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read =>
                    progress(read)
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    // Use a closable item and make sure an attempt to close it is done after use
    def useAndClose[T <: {def close() : Unit}](closable: T)(block: T => Unit) =
        try {
            block(closable)
        } finally {
            if (closable ne null)
                runQuietly(closable.close())
        }

    // Run a block and swallow any exception. Use only for things like close().
    def runQuietly(block: => Unit) =
        try {
            block
        } catch {
            case _ => // NOP
        }
}