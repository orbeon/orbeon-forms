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

import org.apache.log4j.Level
import org.orbeon.oxf.pipeline.api.ExternalContext
import org.orbeon.oxf.xml.{XMLUtils, XMLParsing}
import java.io._

import scala.collection.JavaConverters._
import ScalaUtils._
import java.lang.{Long ⇒ JLong}
import java.util.{Map ⇒ JMap, List ⇒ JList}
import java.net.URLConnection

class ConnectionResult(val resourceURI: String) extends Logging {

    var dontHandleResponse = false
    def setDontHandleResponseJava() = dontHandleResponse = true

    var statusCode: Int = 0
    def setStatusCodeJava(statusCode: Int) = this.statusCode = statusCode

    private var _responseMediaType: Option[String] = None
    private var _responseContentType: Option[String] = None
    private var _originalResponseContentType: Option[String] = None
    private var _lastModified: Option[Long] = None

    private var _responseHeaders: Iterable[(String, List[String])] = Iterable.empty

    private var _didLogResponseDetails = false

    private var _responseInputStream: Option[InputStream] = None
    private var _hasContent = false

    def getResponseInputStream = _responseInputStream.orNull

    def hasContent = _hasContent

    def setResponseInputStream(responseInputStream: InputStream): Unit = {

        val bis = if (responseInputStream.markSupported) responseInputStream else new BufferedInputStream(responseInputStream)

        def hasContent(bis: InputStream) = {
            bis.mark(1)
            val result = bis.read != -1
            bis.reset()
            result
        }

        this._hasContent = hasContent(bis)
        this._responseInputStream = Some(bis)
    }

    def responseHeaders = _responseHeaders
    def jResponseHeaders: JMap[String, JList[String]] =
        responseHeaders.toMap.map{ case (k, v) ⇒ k → v.asJava }.asJava

    def responseHeaders_= (headers: JMap[String, JList[String]]): Unit =
        _responseHeaders = headers.asScala.toMap map { case (k, v) ⇒ k → v.asScala.to[List] }

    def setResponseContentType(responseContentType: String): Unit =
        setResponseContentType(responseContentType, null)

    def setResponseContentType(responseContentType: String, defaultContentType: String): Unit = {
        val responseContentTypeOpt = Option(responseContentType)
        this._originalResponseContentType = responseContentTypeOpt
        this._responseContentType         = responseContentTypeOpt orElse Option(defaultContentType)
        this._responseMediaType           = this._responseContentType map NetUtils.getContentTypeMediaType
    }

    def getResponseContentType         = _responseContentType.orNull
    def getOriginalResponseContentType = _originalResponseContentType.orNull
    def getResponseMediaType           = _responseMediaType.orNull

    def getLastModifiedJava: JLong     = _lastModified map (_.asInstanceOf[JLong]) orNull

    def setLastModified(urlConnection: URLConnection): Unit = {
        val connectionLastModified = NetUtils.getLastModified(urlConnection)
        // Zero and negative values often have a special meaning, make sure to normalize here
        this._lastModified = if (connectionLastModified <= 0) None else Some(connectionLastModified)
    }

    def forwardHeaders(response: ExternalContext.Response): Unit =
        for {
            (headerName, headerValues) ← Headers.filterHeaders(responseHeaders, out = false)
            headerValue ← headerValues
        } locally {
            response.addHeader(headerName, headerValue)
        }

    /**
     * Close the result once everybody is done with it.
     *
     * This can be overridden by specific subclasses.
     */
    def close(): Unit =
        useAndClose(getResponseInputStream)(identity)

    // Return the response body as text, null if not a text or XML result.
    def getTextResponseBody =
        if (XMLUtils.isTextOrJSONContentType(getResponseMediaType)) {
            // Text mediatype (including text/xml), read stream into String
            val charset = NetUtils.getTextCharsetFromContentType(getResponseContentType)
            useAndClose(new InputStreamReader(getResponseInputStream, charset)) { reader ⇒
                NetUtils.readStreamAsString(reader)
            }
        } else if (XMLUtils.isXMLMediatype(getResponseMediaType)) {
            // XML mediatype other than text/xml
            useAndClose(XMLParsing.getReaderFromXMLInputStream(getResponseInputStream)) { reader ⇒
                NetUtils.readStreamAsString(reader)
            }
        } else
            null

    /**
     * Log response details if not already logged.
     *
     * @param indentedLogger    logger
     * @param logLevel          log level
     * @param logType           log type string
     */
    def logResponseDetailsIfNeeded(indentedLogger: IndentedLogger, logLevel: Level, logType: String): Unit = {
        implicit val logger = indentedLogger
        if (! _didLogResponseDetails) {
            log(logLevel, "response", Seq("status code" → statusCode.toString))
            if (responseHeaders.nonEmpty) {

                val headersToLog =
                    for ((name, values) ← responseHeaders; value ← values)
                        yield name → value

                log(logLevel, "response headers", headersToLog.toList)
            }
            if (getOriginalResponseContentType eq null)
                log(logLevel, "received null response Content-Type", Seq("default Content-Type" → getResponseContentType))
            _didLogResponseDetails = true
        }
    }

    /**
     * Log response body.
     *
     * @param indentedLogger    logger
     * @param logLevel          log level
     * @param logType           log type string
     * @param logBody           whether to actually log the body
     */
    def logResponseBody(indentedLogger: IndentedLogger, logLevel: Level, logType: String, logBody: Boolean): Unit = {
        implicit val logger = indentedLogger
        if (hasContent) {
            log(logLevel, "response has content")
            if (logBody) {
                val tempResponse =
                    useAndClose(getResponseInputStream) { is ⇒
                        NetUtils.inputStreamToByteArray(is)
                    }

                setResponseInputStream(new ByteArrayInputStream(tempResponse))
                val responseBody = getTextResponseBody
                if (responseBody ne null) {
                    log(logLevel, "response body", Seq("body" → responseBody))
                    setResponseInputStream(new ByteArrayInputStream(tempResponse))
                } else {
                    log(logLevel, "binary response body")
                }
            }
        } else {
            log(logLevel, "response has no content")
        }
    }
}