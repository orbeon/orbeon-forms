/**
 * Copyright (C) 2004 Orbeon, Inc.
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

import org.orbeon.oxf.resources.ResourceManagerWrapper

import java.io.InputStream
import java.net.{URL, URLConnection, URLStreamHandler}


object OXFHandler {
  val Protocol = "oxf"
}

class OXFHandler extends URLStreamHandler {

  import OXFHandler.Protocol

  protected def openConnection(url: URL): URLConnection =
    new URLConnection(url) {

      require(url.getProtocol == Protocol, s"Orbeon Forms URL must start with `$Protocol:`")

      private lazy val key: String =
        url.toExternalForm.substring(Protocol.length + 1)

      def connect(): Unit = () // NOP

      override def getInputStream: InputStream =
        ResourceManagerWrapper.instance.getContentAsStream(key)

      override def getOutputStream = throw new UnsupportedOperationException

      override def getLastModified: Long =
        ResourceManagerWrapper.instance.lastModified(key, false)

      override def getContentLength: Int =
        ResourceManagerWrapper.instance.length(key)
    }

  // 2021-12-15: Not sure why we do this.
  override protected def setURL(
    u         : URL,
    protocol  : String,
    host      : String,
    port      : Int,
    authority : String,
    userInfo  : String,
    path      : String,
    query     : String,
    ref       : String
  ): Unit =
    super.setURL(u, protocol, null, port, authority, userInfo, host + path, query, ref)
}