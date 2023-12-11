/**
 * Copyright (C) 2023 Orbeon, Inc.
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
package org.orbeon.oxf.servlet

import java.io.PrintWriter
import java.{util => ju}

class HttpServletResponseWrapper(response: HttpServletResponse) extends HttpServletResponse {
  override def getNativeServletResponse: AnyRef = response.getNativeServletResponse
  override def addDateHeader(name: String, date: Long): Unit = response.addDateHeader(name, date)
  override def addHeader(name: String, value: String): Unit = response.addHeader(name, value)
  override def addIntHeader(name: String, value: Int): Unit = response.addIntHeader(name, value)
  override def flushBuffer(): Unit = response.flushBuffer()
  override def getBufferSize: Int = response.getBufferSize
  override def getCharacterEncoding: String = response.getCharacterEncoding
  override def getContentType: String = response.getContentType
  override def getLocale: ju.Locale = response.getLocale
  override def getOutputStream: ServletOutputStream = response.getOutputStream
  override def getWriter: PrintWriter = response.getWriter
  override def isCommitted: Boolean = response.isCommitted
  override def reset(): Unit = response.reset()
  override def resetBuffer(): Unit = response.resetBuffer()
  override def sendError(sc: Int): Unit = response.sendError(sc)
  override def sendError(sc: Int, msg: String): Unit = response.sendError(sc, msg)
  override def sendRedirect(location: String): Unit = response.sendRedirect(location)
  override def setBufferSize(size: Int): Unit = response.setBufferSize(size)
  override def setCharacterEncoding(charset: String): Unit = response.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = response.setContentLength(len)
  override def setContentType(`type`: String): Unit = response.setContentType(`type`)
  override def setDateHeader(name: String, date: Long): Unit = response.setDateHeader(name, date)
  override def setHeader(name: String, value: String): Unit = response.setHeader(name, value)
  override def setIntHeader(name: String, value: Int): Unit = response.setIntHeader(name, value)
  override def setLocale(loc: ju.Locale): Unit = response.setLocale(loc)
  override def setStatus(sc: Int): Unit = response.setStatus(sc)
  override def setStatus(sc: Int, sm: String): Unit = response.setStatus(sc, sm)
}
