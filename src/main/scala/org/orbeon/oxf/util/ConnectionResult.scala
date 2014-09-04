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
import java.lang.{Long ⇒ JLong}
import java.util.{List ⇒ JList, Map ⇒ JMap}

import org.apache.log4j.Level
import org.orbeon.oxf.http.{Headers, StreamedContent}
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.webapp.HttpStatusCodeException
import org.orbeon.oxf.xml.{XMLParsing, XMLUtils}

import scala.collection.JavaConverters._
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

    val lastModified     = Headers.firstDateHeaderIgnoreCase(headers, Headers.LastModified)
    def lastModifiedJava = lastModified map (_.asInstanceOf[JLong]) orNull
        
    def close() = content.close()
        
    val mediaType =
        content.contentType map NetUtils.getContentTypeMediaType

    def contentTypeOrDefault(default: String) =
        content.contentType getOrElse default

    def mediatypeOrDefault(default: String) =
        mediaType getOrElse default
    
    def contentTypeCharsetOrDefault(defaultMediatype: String) =
        NetUtils.getTextCharsetFromContentTypeOrDefault(mediatypeOrDefault(defaultMediatype))

    def jHeaders: JMap[String, JList[String]] =
        headers mapValues (_.asJava) asJava

    def readTextResponseBody(defaultMediatype: String) = Some(content) collect {
        case content if XMLUtils.isTextOrJSONContentType(mediatypeOrDefault(defaultMediatype)) ⇒
            // Text mediatype (including text/xml), read stream into String
            useAndClose(new InputStreamReader(content.inputStream, contentTypeCharsetOrDefault(defaultMediatype))) { reader ⇒
                NetUtils.readStreamAsString(reader)
            }
        case content if XMLUtils.isXMLMediatype(mediatypeOrDefault(defaultMediatype)) ⇒ 
            // XML mediatype other than text/xml
            useAndClose(XMLParsing.getReaderFromXMLInputStream(content.inputStream)) { reader ⇒
                NetUtils.readStreamAsString(reader)
            }
    }
    
    private var _didLogResponseDetails = false

    // See https://github.com/orbeon/orbeon-forms/issues/1900
    def logResponseDetailsOnce(logLevel: Level)(implicit logger: IndentedLogger): Unit = {
        if (! _didLogResponseDetails) {
            log(logLevel, "response", Seq("status code" → statusCode.toString))
            if (headers.nonEmpty) {

                val headersToLog =
                    for ((name, values) ← headers; value ← values)
                        yield name → value

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

    def withSuccessConnection[T](cxr: ConnectionResult, closeOnSuccess: Boolean)(body: InputStream ⇒ T): T =
        tryWithSuccessConnection(cxr, closeOnSuccess)(body).get

    def tryWithSuccessConnection[T](cxr: ConnectionResult, closeOnSuccess: Boolean)(body: InputStream ⇒ T): Try[T] = Try {
        try {
            cxr match {
                case ConnectionResult(_, statusCode, _, StreamedContent(inputStream, _, _, _), _, _) if NetUtils.isSuccessCode(statusCode) ⇒
                    val result = body(inputStream)
                    if (closeOnSuccess)
                        cxr.close() // this eventually calls InputStream.close()
                    result
                case ConnectionResult(_, statusCode, _, _, _, _) ⇒
                    throw new HttpStatusCodeException(if (statusCode != 200) statusCode else 500)
            }
        } catch {
            case NonFatal(t) ⇒
                cxr.close()
                throw t
        }
    }
}
