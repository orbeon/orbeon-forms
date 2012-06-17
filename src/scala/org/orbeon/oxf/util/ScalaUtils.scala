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

import org.orbeon.exception._
import java.security.MessageDigest
import org.apache.log4j.Logger
import java.net.URLEncoder.{encode ⇒ encodeURL}
import java.net.URLDecoder.{decode ⇒ decodeURL}

object ScalaUtils {

    private val COPY_BUFFER_SIZE = 8192

    type Readable[T] = {
        def close()
        def read(b: Array[T]): Int
    }

    type Writable[T] = {
        def close()
        def write(cbuf: Array[T], off: Int, len: Int)
    }

    // Copy a stream, with optional progress callback
    // This fails at runtime due to: https://lampsvn.epfl.ch/trac/scala/ticket/2672
    def genericCopyStream[T : ClassManifest](in: Readable[T], out: Writable[T], progress: Long ⇒ Unit = _ ⇒ ()) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in ⇒
            useAndClose(out) { out ⇒
                val buffer = new Array[T](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
                    progress(read)
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    def copyStream(in: Readable[Byte], out: Writable[Byte], progress: Long ⇒ Unit = _ ⇒ ()) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in ⇒
            useAndClose(out) { out ⇒
                val buffer = new Array[Byte](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
                    progress(read)
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    def copyReader(in: Readable[Char], out: Writable[Char], progress: Long ⇒ Unit = _ ⇒ ()) = {

        require(in ne null)
        require(out ne null)

        useAndClose(in) { in ⇒
            useAndClose(out) { out ⇒
                val buffer = new Array[Char](COPY_BUFFER_SIZE)
                Iterator continually (in read buffer) takeWhile (_ != -1) filter (_ > 0) foreach { read ⇒
                    progress(read)
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    // Use a closable item and make sure an attempt to close it is done after use
    def useAndClose[T <: {def close()}, U](closable: T)(block: T ⇒ U): U =
        try block(closable)
        finally {
            if (closable ne null)
                runQuietly(closable.close())
        }

    // Run a block and swallow any exception. Use only for things like close().
    def runQuietly(block: ⇒ Unit) =
        try block
        catch {
            case _ ⇒ // NOP
        }

    // Run the body and log/rethrow the root cause of any Exception caught
    def withRootException[T](action: String, newException: Throwable ⇒ Exception)(body: ⇒ T)(implicit logger: Logger): T =
        try body
        catch {
            case e: Exception ⇒
                logger.error("Exception when running " + action + '\n' + OrbeonFormatter.format(e))
                throw newException(Exceptions.getRootThrowable(e))
        }

    def digest(algorithm: String, data: Traversable[String]) = {
        // Create and update digest
        val messageDigest = MessageDigest.getInstance(algorithm)
        data foreach (s ⇒ messageDigest.update(s.getBytes("utf-8")))

        // Format result
        SecureUtils.byteArrayToHex(messageDigest.digest())
    }

    def dropTrailingSlash(s: String) = if (s.size == 0 || s.last != '/') s else s.init
    def dropStartingSlash(s: String) = if (s.size == 0 || s.head != '/') s else s.tail
    def capitalizeHeader(s: String) = s split '-' map (_.capitalize) mkString "-"

    // Shortcut for "not implemented yet" (something like this is planned for Scala post-2.9.1)
    def ??? = throw new RuntimeException("NIY")

    // Semi-standard pipe operator
    class PipeOps[A](a: A) { def |>[B](f: A ⇒ B) = f(a) }
    implicit def anyToPipeOps[A](a: A) = new PipeOps(a)

    // Convert a string of tokens to a set
    def stringOptionToSet(s: Option[String]) = s match {
        case Some(list) ⇒ list split """\s+""" toSet
        case None ⇒ Set[String]()
    }

    // Split a URL's query
    def splitQuery(url: String): (String, Option[String]) = {
        val index = url.indexOf('?')
        if (index == -1)
            (url, None)
        else
            (url.substring(0, index), Some(url.substring(index + 1)))
    }

    // Decode a query string into a sequence of pairs
    // We assume that there are no spaces in the input query
    def decodeSimpleQuery(queryOption: Option[String]): Seq[(String, String)] =
        for {
            query ← queryOption.toList
            nameValue ← query.split('&').toList
            if nameValue.nonEmpty
            nameValueArray = nameValue.split('=')
            if nameValueArray.size >= 1
            encodedName = nameValueArray(0)
            if encodedName.nonEmpty
            decodedName = decodeURL(encodedName, "utf-8")
            decodedValue = decodeURL(nameValueArray.lift(1) getOrElse "", "utf-8")
        } yield
            decodedName → decodedValue

    // Get the first query parameter value for the given name
    def getFirstQueryParameter(url: String, name: String) = {
        val query = decodeSimpleQuery(splitQuery(url)._2)

        query find (_._1 == name) map { case (k, v) ⇒ v }
    }

    // Encode a sequence of pairs to a query string
    def encodeSimpleQuery(parameters: Seq[(String, String)]): String =
        parameters map { case (name, value) ⇒ encodeURL(name, "utf-8") + '=' + encodeURL(value, "utf-8") } mkString "&"
}