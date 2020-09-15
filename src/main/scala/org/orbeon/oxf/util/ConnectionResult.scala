/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.util

import java.io._
import java.{lang => jl}

import org.apache.log4j.Level
import org.orbeon.io.IOUtils
import org.orbeon.oxf.http.{DateHeaders, HttpStatusCodeException, StatusCode, StreamedContent, Headers => HttpHeaders}
import org.orbeon.oxf.xml.XMLParsing

import scala.util.Try
import scala.util.control.NonFatal

case class ConnectionResult(
  url               : String,
  statusCode        : Int,
  headers           : Map[String, List[String]],
  content           : StreamedContent,
  hasContent        : Boolean,
  dontHandleResponse: Boolean // TODO: Should be outside of ConnectionResult.
) extends Logging {

  val lastModified: Option[Long] = DateHeaders.firstDateHeaderIgnoreCase(headers, HttpHeaders.LastModified)

  def lastModifiedJava: jl.Long = lastModified map (_.asInstanceOf[jl.Long]) orNull

  def close(): Unit = content.close()

  def isSuccessResponse: Boolean = NetUtils.isSuccessCode(statusCode)

  val mediatype: Option[String] =
    content.contentType flatMap (ct => ContentTypes.getContentTypeMediaType(ct))

  val charset: Option[String] =
    content.contentType flatMap (ct => ContentTypes.getContentTypeCharset(ct))

  def mediatypeOrDefault(default: String): String =
    mediatype getOrElse default

  def charsetJava: String =
    charset.orNull

  def getHeaderIgnoreCase(name: String): List[String] = {
    val nameLowercase = name.toLowerCase
    headers collectFirst { case (k, v) if k.toLowerCase == nameLowercase => v } getOrElse Nil
  }

  def readTextResponseBody: Option[String] = mediatype collect {
    case mediatype if ContentTypes.isXMLMediatype(mediatype) =>
      // TODO: RFC 7303 says that content type charset must take precedence with any XML mediatype.
      //
      // http://tools.ietf.org/html/rfc7303:
      //
      //  The former confusion
      //  around the question of default character sets for the two text/ types
      //  no longer arises because
      //
      //     [RFC7231] changes [RFC2616] by removing the ISO-8859-1 default and
      //     not defining any default at all;
      //
      //     [RFC6657] updates [RFC2046] to remove the US-ASCII [ASCII]
      //
      // [...]
      //
      // this specification sets the priority as follows:
      //
      //    A BOM (Section 3.3) is authoritative if it is present in an XML
      //    MIME entity;
      //
      //    In the absence of a BOM (Section 3.3), the charset parameter is
      //    authoritative if it is present
      //
      IOUtils.readStreamAsStringAndClose(XMLParsing.getReaderFromXMLInputStream(content.inputStream))
    case mediatype if ContentTypes.isTextOrJSONContentType(mediatype) =>
      IOUtils.readStreamAsStringAndClose(content.inputStream, charset)
  }

  private var _didLogResponseDetails = false

  // See https://github.com/orbeon/orbeon-forms/issues/1900
  def logResponseDetailsOnce(logLevel: Level)(implicit logger: IndentedLogger): Unit = {
    if (! _didLogResponseDetails) {
      log(logLevel, "response", Seq("status code" -> statusCode.toString))
      if (headers.nonEmpty) {

        val headersToLog =
          for ((name, values) <- headers; value <- values)
            yield name -> value

        log(logLevel, "response headers", headersToLog.toList)
      }
      _didLogResponseDetails = true
    }
  }

  // See https://github.com/orbeon/orbeon-forms/issues/1900
  def logResponseBody(logLevel: Level, logBody: Boolean)(implicit logger: IndentedLogger): Unit =
    if (hasContent)
      log(logLevel, "response has content")
    else
      log(logLevel, "response has no content")
}

object ConnectionResult {

  def apply(
    url               : String,
    statusCode        : Int,
    headers           : Map[String, List[String]],
    content           : StreamedContent,
    dontHandleResponse: Boolean = false // TODO: Should be outside of ConnectionResult.
  ): ConnectionResult = {

    val (hasContent, resetInputStream) = {

      val bis =
        if (content.inputStream.markSupported)
          content.inputStream
        else
          new BufferedInputStream(content.inputStream)

      def hasContent(bis: InputStream) = {
        bis.mark(1)
        val result = bis.read != -1
        bis.reset()
        result
      }

      (hasContent(bis), bis)
    }

    ConnectionResult(
      url                = url,
      statusCode         = statusCode,
      headers            = headers,
      content            = content.copy(inputStream = resetInputStream),
      hasContent         = hasContent,
      dontHandleResponse = dontHandleResponse
    )
  }

  def withSuccessConnection[T](cxr: ConnectionResult, closeOnSuccess: Boolean)(body: InputStream => T): T =
    tryWithSuccessConnection(cxr, closeOnSuccess)(body).get

  def tryWithSuccessConnection[T](cxr: ConnectionResult, closeOnSuccess: Boolean)(body: InputStream => T): Try[T] = Try {
    try {
      cxr match {
        case ConnectionResult(_, _, _, StreamedContent(inputStream, _, _, _), _, _) if cxr.isSuccessResponse =>
          val result = body(inputStream)
          if (closeOnSuccess)
            cxr.close() // this eventually calls InputStream.close()
          result
        case ConnectionResult(_, statusCode, _, _, _, _) =>
          throw HttpStatusCodeException(if (statusCode != StatusCode.Ok) statusCode else StatusCode.InternalServerError)
      }
    } catch {
      case NonFatal(t) =>
        cxr.close()
        throw t
    }
  }
}
