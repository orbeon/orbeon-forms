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

import java.net.{URLConnection, URL, URLStreamHandler}

import java.io.ByteArrayInputStream

object DataHandler extends URLStreamHandler {
    def openConnection(url: URL): URLConnection = new DataURLConnection(url)

    class DataURLConnection(url: URL) extends URLConnection(url) {

        require(url.getProtocol == "data")

        private val decoded = DataURLDecoder.decode(url.toExternalForm)

        def connect() = ()
        override val getContentType = decoded.contentType
        override def getInputStream = new ByteArrayInputStream(decoded.bytes)
    }
}
