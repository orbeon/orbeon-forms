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

object HttpServletResponse {
  def apply(httpServletResponse: javax.servlet.http.HttpServletResponse): JavaxHttpServletResponse     = new JavaxHttpServletResponse(httpServletResponse)
  def apply(httpServletResponse: jakarta.servlet.http.HttpServletResponse): JakartaHttpServletResponse = new JakartaHttpServletResponse(httpServletResponse)
}

trait HttpServletResponse extends ServletResponse {
  def addDateHeader(name: String, date: Long): Unit
  def addHeader(name: String, value: String): Unit
  def addIntHeader(name: String, value: Int): Unit
  def flushBuffer(): Unit
  def getBufferSize: Int
  def getCharacterEncoding: String
  def getContentType: String
  def getLocale: ju.Locale
  def getOutputStream: ServletOutputStream
  def getWriter: PrintWriter
  def isCommitted: Boolean
  def reset(): Unit
  def resetBuffer(): Unit
  def sendError(sc: Int): Unit
  def sendError(sc: Int, msg: String): Unit
  def sendRedirect(location: String): Unit
  def setBufferSize(size: Int): Unit
  def setCharacterEncoding(charset: String): Unit
  def setContentLength(len: Int): Unit
  def setContentType(`type`: String): Unit
  def setDateHeader(name: String, date: Long): Unit
  def setHeader(name: String, value: String): Unit
  def setIntHeader(name: String, value: Int): Unit
  def setLocale(loc: ju.Locale): Unit
  def setStatus(sc: Int): Unit
  def setStatus(sc: Int, sm: String): Unit
}

class JavaxHttpServletResponse(httpServletResponse: javax.servlet.http.HttpServletResponse) extends HttpServletResponse {
  override def getNativeServletResponse: javax.servlet.http.HttpServletResponse = httpServletResponse

  override def addDateHeader(name: String, date: Long): Unit = httpServletResponse.addDateHeader(name, date)
  override def addHeader(name: String, value: String): Unit = httpServletResponse.addHeader(name, value)
  override def addIntHeader(name: String, value: Int): Unit = httpServletResponse.addIntHeader(name, value)
  override def flushBuffer(): Unit = httpServletResponse.flushBuffer()
  override def getBufferSize: Int = httpServletResponse.getBufferSize
  override def getCharacterEncoding: String = httpServletResponse.getCharacterEncoding
  override def getContentType: String = httpServletResponse.getContentType
  override def getLocale: ju.Locale = httpServletResponse.getLocale
  override def getOutputStream: ServletOutputStream = ServletOutputStream(httpServletResponse.getOutputStream)
  override def getWriter: PrintWriter = httpServletResponse.getWriter
  override def isCommitted: Boolean = httpServletResponse.isCommitted
  override def reset(): Unit = httpServletResponse.reset()
  override def resetBuffer(): Unit = httpServletResponse.resetBuffer()
  override def sendError(sc: Int): Unit = httpServletResponse.sendError(sc)
  override def sendError(sc: Int, msg: String): Unit = httpServletResponse.sendError(sc, msg)
  override def sendRedirect(location: String): Unit = httpServletResponse.sendRedirect(location)
  override def setBufferSize(size: Int): Unit = httpServletResponse.setBufferSize(size)
  override def setCharacterEncoding(charset: String): Unit = httpServletResponse.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = httpServletResponse.setContentLength(len)
  override def setContentType(`type`: String): Unit = httpServletResponse.setContentType(`type`)
  override def setDateHeader(name: String, date: Long): Unit = httpServletResponse.setDateHeader(name, date)
  override def setHeader(name: String, value: String): Unit = httpServletResponse.setHeader(name, value)
  override def setIntHeader(name: String, value: Int): Unit = httpServletResponse.setIntHeader(name, value)
  override def setLocale(loc: ju.Locale): Unit = httpServletResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = httpServletResponse.setStatus(sc)
  override def setStatus(sc: Int, sm: String): Unit = httpServletResponse.setStatus(sc, sm)
}

class JakartaHttpServletResponse(httpServletResponse: jakarta.servlet.http.HttpServletResponse) extends HttpServletResponse {
  override def getNativeServletResponse: jakarta.servlet.http.HttpServletResponse = httpServletResponse

  override def addDateHeader(name: String, date: Long): Unit = httpServletResponse.addDateHeader(name, date)
  override def addHeader(name: String, value: String): Unit = httpServletResponse.addHeader(name, value)
  override def addIntHeader(name: String, value: Int): Unit = httpServletResponse.addIntHeader(name, value)
  override def flushBuffer(): Unit = httpServletResponse.flushBuffer()
  override def getBufferSize: Int = httpServletResponse.getBufferSize
  override def getCharacterEncoding: String = httpServletResponse.getCharacterEncoding
  override def getContentType: String = httpServletResponse.getContentType
  override def getLocale: ju.Locale = httpServletResponse.getLocale
  override def getOutputStream: ServletOutputStream = ServletOutputStream(httpServletResponse.getOutputStream)
  override def getWriter: PrintWriter = httpServletResponse.getWriter
  override def isCommitted: Boolean = httpServletResponse.isCommitted
  override def reset(): Unit = httpServletResponse.reset()
  override def resetBuffer(): Unit = httpServletResponse.resetBuffer()
  override def sendError(sc: Int): Unit = httpServletResponse.sendError(sc)
  override def sendError(sc: Int, msg: String): Unit = httpServletResponse.sendError(sc, msg)
  override def setCharacterEncoding(charset: String): Unit = httpServletResponse.setCharacterEncoding(charset)
  override def sendRedirect(location: String): Unit = httpServletResponse.sendRedirect(location)
  override def setBufferSize(size: Int): Unit = httpServletResponse.setBufferSize(size)
  override def setContentLength(len: Int): Unit = httpServletResponse.setContentLength(len)
  override def setContentType(`type`: String): Unit = httpServletResponse.setContentType(`type`)
  override def setDateHeader(name: String, date: Long): Unit = httpServletResponse.setDateHeader(name, date)
  override def setHeader(name: String, value: String): Unit = httpServletResponse.setHeader(name, value)
  override def setIntHeader(name: String, value: Int): Unit = httpServletResponse.setIntHeader(name, value)
  override def setLocale(loc: ju.Locale): Unit = httpServletResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = httpServletResponse.setStatus(sc)
  override def setStatus(sc: Int, sm: String): Unit = httpServletResponse.setStatus(sc, sm)
}
