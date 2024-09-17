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

import java.io.{ByteArrayOutputStream, OutputStream, PrintWriter}
import java.util as ju


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
  def sendError(sc: Int): Unit
  def sendError(sc: Int, msg: String): Unit
  def sendRedirect(location: String): Unit
  def setBufferSize(size: Int): Unit
  def setDateHeader(name: String, date: Long): Unit
  def setHeader(name: String, value: String): Unit
  def setIntHeader(name: String, value: Int): Unit
  def setLocale(loc: ju.Locale): Unit
  def setStatus(sc: Int): Unit
  def getStatus: Int

  // javax/jakarta.servlet.ServletOutputStream
  def servletOutputStream(byteArrayOutputStream: ByteArrayOutputStream): OutputStream

  // javax/jakarta.servlet.http.HttpServletResponseWrapper
  def wrappedWith(wrapper: HttpServletResponseWrapper): AnyRef
}

class JavaxHttpServletResponse(httpServletResponse: javax.servlet.http.HttpServletResponse)
  extends JavaxServletResponse(httpServletResponse)
    with HttpServletResponse {

  override def getNativeServletResponse: javax.servlet.http.HttpServletResponse = httpServletResponse

  override def addDateHeader(name: String, date: Long): Unit = httpServletResponse.addDateHeader(name, date)
  override def addHeader(name: String, value: String): Unit = httpServletResponse.addHeader(name, value)
  override def addIntHeader(name: String, value: Int): Unit = httpServletResponse.addIntHeader(name, value)
  override def flushBuffer(): Unit = httpServletResponse.flushBuffer()
  override def getBufferSize: Int = httpServletResponse.getBufferSize
  override def sendError(sc: Int): Unit = httpServletResponse.sendError(sc)
  override def sendError(sc: Int, msg: String): Unit = httpServletResponse.sendError(sc, msg)
  override def sendRedirect(location: String): Unit = httpServletResponse.sendRedirect(location)
  override def setBufferSize(size: Int): Unit = httpServletResponse.setBufferSize(size)
  override def setDateHeader(name: String, date: Long): Unit = httpServletResponse.setDateHeader(name, date)
  override def setHeader(name: String, value: String): Unit = httpServletResponse.setHeader(name, value)
  override def setIntHeader(name: String, value: Int): Unit = httpServletResponse.setIntHeader(name, value)
  override def setLocale(loc: ju.Locale): Unit = httpServletResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = httpServletResponse.setStatus(sc)
  override def getStatus: Int = httpServletResponse.getStatus

  override def servletOutputStream(byteArrayOutputStream: ByteArrayOutputStream): javax.servlet.ServletOutputStream =
    new javax.servlet.ServletOutputStream {
      def write(i: Int)                                                      = byteArrayOutputStream.write(i)
      def isReady: Boolean                                                   = true
      def setWriteListener(writeListener: javax.servlet.WriteListener): Unit = ()
    }

  override def wrappedWith(wrapper: HttpServletResponseWrapper): javax.servlet.http.HttpServletResponseWrapper =
    new javax.servlet.http.HttpServletResponseWrapper(httpServletResponse) {
      // Handle mutable behavior
      override def setResponse(servletResponse: javax.servlet.ServletResponse): Unit = {
        super.setResponse(servletResponse)
        servletResponse match {
          case httpServletResponse: javax.servlet.http.HttpServletResponse =>
            wrapper.response = HttpServletResponse(httpServletResponse)
        }
      }

      override def addDateHeader(name: String, date: Long): Unit = wrapper.addDateHeader(name, date)
      override def addHeader(name: String, value: String): Unit = wrapper.addHeader(name, value)
      override def addIntHeader(name: String, value: Int): Unit = wrapper.addIntHeader(name, value)
      override def flushBuffer(): Unit = wrapper.flushBuffer()
      override def getBufferSize: Int = wrapper.getBufferSize
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentType: String = wrapper.getContentType
      override def getLocale: ju.Locale = wrapper.getLocale
      // See comment in ServletResponse trait
      override def getOutputStream: javax.servlet.ServletOutputStream = wrapper.getOutputStream.asInstanceOf[javax.servlet.ServletOutputStream]
      override def getWriter: PrintWriter = wrapper.getWriter
      override def isCommitted: Boolean = wrapper.isCommitted
      override def reset(): Unit = wrapper.reset()
      override def resetBuffer(): Unit = wrapper.resetBuffer()
      override def sendError(sc: Int): Unit = wrapper.sendError(sc)
      override def sendError(sc: Int, msg: String): Unit = wrapper.sendError(sc, msg)
      override def sendRedirect(location: String): Unit = wrapper.sendRedirect(location)
      override def setBufferSize(size: Int): Unit = wrapper.setBufferSize(size)
      override def setCharacterEncoding(charset: String): Unit = wrapper.setCharacterEncoding(charset)
      override def setContentLength(len: Int): Unit = wrapper.setContentLength(len)
      override def setContentType(`type`: String): Unit = wrapper.setContentType(`type`)
      override def setDateHeader(name: String, date: Long): Unit = wrapper.setDateHeader(name, date)
      override def setHeader(name: String, value: String): Unit = wrapper.setHeader(name, value)
      override def setIntHeader(name: String, value: Int): Unit = wrapper.setIntHeader(name, value)
      override def setLocale(loc: ju.Locale): Unit = wrapper.setLocale(loc)
      override def setStatus(sc: Int): Unit = wrapper.setStatus(sc)
      override def getStatus: Int = wrapper.getStatus
    }
}

class JakartaHttpServletResponse(httpServletResponse: jakarta.servlet.http.HttpServletResponse)
  extends JakartaServletResponse(httpServletResponse)
    with HttpServletResponse {

  override def getNativeServletResponse: jakarta.servlet.http.HttpServletResponse = httpServletResponse

  override def addDateHeader(name: String, date: Long): Unit = httpServletResponse.addDateHeader(name, date)
  override def addHeader(name: String, value: String): Unit = httpServletResponse.addHeader(name, value)
  override def addIntHeader(name: String, value: Int): Unit = httpServletResponse.addIntHeader(name, value)
  override def flushBuffer(): Unit = httpServletResponse.flushBuffer()
  override def getBufferSize: Int = httpServletResponse.getBufferSize
  override def sendError(sc: Int): Unit = httpServletResponse.sendError(sc)
  override def sendError(sc: Int, msg: String): Unit = httpServletResponse.sendError(sc, msg)
  override def sendRedirect(location: String): Unit = httpServletResponse.sendRedirect(location)
  override def setBufferSize(size: Int): Unit = httpServletResponse.setBufferSize(size)
  override def setDateHeader(name: String, date: Long): Unit = httpServletResponse.setDateHeader(name, date)
  override def setHeader(name: String, value: String): Unit = httpServletResponse.setHeader(name, value)
  override def setIntHeader(name: String, value: Int): Unit = httpServletResponse.setIntHeader(name, value)
  override def setLocale(loc: ju.Locale): Unit = httpServletResponse.setLocale(loc)
  override def setStatus(sc: Int): Unit = httpServletResponse.setStatus(sc)
  override def getStatus: Int = httpServletResponse.getStatus

  override def servletOutputStream(byteArrayOutputStream: ByteArrayOutputStream): jakarta.servlet.ServletOutputStream =
    new jakarta.servlet.ServletOutputStream {
      def write(i: Int)                                                        = byteArrayOutputStream.write(i)
      def isReady: Boolean                                                     = true
      def setWriteListener(writeListener: jakarta.servlet.WriteListener): Unit = ()
    }

  override def wrappedWith(wrapper: HttpServletResponseWrapper): jakarta.servlet.http.HttpServletResponseWrapper =
    new jakarta.servlet.http.HttpServletResponseWrapper(httpServletResponse) {
      // Handle mutable behavior
      override def setResponse(servletResponse: jakarta.servlet.ServletResponse): Unit = {
        super.setResponse(servletResponse)
        servletResponse match {
          case httpServletResponse: jakarta.servlet.http.HttpServletResponse =>
            wrapper.response = HttpServletResponse(httpServletResponse)
        }
      }

      override def addDateHeader(name: String, date: Long): Unit = wrapper.addDateHeader(name, date)
      override def addHeader(name: String, value: String): Unit = wrapper.addHeader(name, value)
      override def addIntHeader(name: String, value: Int): Unit = wrapper.addIntHeader(name, value)
      override def flushBuffer(): Unit = wrapper.flushBuffer()
      override def getBufferSize: Int = wrapper.getBufferSize
      override def getCharacterEncoding: String = wrapper.getCharacterEncoding
      override def getContentType: String = wrapper.getContentType
      override def getLocale: ju.Locale = wrapper.getLocale
      // See comment in ServletResponse trait
      override def getOutputStream: jakarta.servlet.ServletOutputStream = wrapper.getOutputStream.asInstanceOf[jakarta.servlet.ServletOutputStream]
      override def getWriter: PrintWriter = wrapper.getWriter
      override def isCommitted: Boolean = wrapper.isCommitted
      override def reset(): Unit = wrapper.reset()
      override def resetBuffer(): Unit = wrapper.resetBuffer()
      override def sendError(sc: Int): Unit = wrapper.sendError(sc)
      override def sendError(sc: Int, msg: String): Unit = wrapper.sendError(sc, msg)
      override def sendRedirect(location: String): Unit = wrapper.sendRedirect(location)
      override def setBufferSize(size: Int): Unit = wrapper.setBufferSize(size)
      override def setCharacterEncoding(charset: String): Unit = wrapper.setCharacterEncoding(charset)
      override def setContentLength(len: Int): Unit = wrapper.setContentLength(len)
      override def setContentType(`type`: String): Unit = wrapper.setContentType(`type`)
      override def setDateHeader(name: String, date: Long): Unit = wrapper.setDateHeader(name, date)
      override def setHeader(name: String, value: String): Unit = wrapper.setHeader(name, value)
      override def setIntHeader(name: String, value: Int): Unit = wrapper.setIntHeader(name, value)
      override def setLocale(loc: ju.Locale): Unit = wrapper.setLocale(loc)
      override def setStatus(sc: Int): Unit = wrapper.setStatus(sc)
      override def getStatus: Int = wrapper.getStatus
    }
}
