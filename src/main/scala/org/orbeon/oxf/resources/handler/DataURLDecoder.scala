/**
 * Copyright (C) 2013 Orbeon, Inc.
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
package org.orbeon.oxf.resources.handler

import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.net.URLCodec
import org.orbeon.oxf.util.NetUtils

// Decode as per https://tools.ietf.org/html/rfc2397
object DataURLDecoder {

    private val DefaultMediatype = "text/plain"
    private val DefaultCharset = "US-ASCII"

    def decode(url: String) = {

        require(url.startsWith("data"))

        val comma      = url.indexOf(',')
        val beforeData = url.substring("data:".length, comma)
        val data       = url.substring(comma + 1).getBytes(DefaultCharset)

        val mediatype = Option(NetUtils.getContentTypeMediaType(beforeData)) getOrElse DefaultMediatype
        val params    = parseContentTypeParameters(beforeData)

        val isBase64 = params.contains("base64")

        val charset =
            if (mediatype.startsWith("text/"))
                params.get("charset").flatten.orElse(Some(DefaultCharset))
            else
                None

        // See: https://github.com/orbeon/orbeon-forms/issues/1065
        val decodedData =
            if (isBase64)
                Base64.decodeBase64(data)
            else
                URLCodec.decodeUrl(data)

        DecodedDataURL(decodedData, mediatype, charset)
    }

    // Support missing attribute values so we can collect ";base64" as well
    private def parseContentTypeParameters(s: String) = {

        def parseParameter(p: String) = {
            val parts = p.split('=')
            (parts(0), parts.lift(1))
        }

        s.split(';') drop 1 map parseParameter toMap
    }
}

case class DecodedDataURL(bytes: Array[Byte], mediatype: String, charset: Option[String]) {
    def contentType = mediatype + (charset map (";" + _) getOrElse "")
    def asString    = charset map (new String(bytes, _))
}