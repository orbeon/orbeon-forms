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

object ServletResponse {
  def apply(servletResponse: javax.servlet.ServletResponse): ServletResponse =
    servletResponse match {
      case httpServletResponse: javax.servlet.http.HttpServletResponse =>
        new JavaxHttpServletResponse(httpServletResponse)
      case _ =>
        new JavaxServletResponse(servletResponse)
    }

  def apply(servletResponse: jakarta.servlet.ServletResponse): ServletResponse =
    servletResponse match {
      case httpServletResponse: jakarta.servlet.http.HttpServletResponse =>
        new JakartaHttpServletResponse(httpServletResponse)
      case _ =>
        new JakartaServletResponse(servletResponse)
    }
}

trait ServletResponse {
  //javax/jakarta.servlet.ServletResponse
  def getNativeServletResponse: AnyRef

  def getCharacterEncoding: String
  def getContentType: String
  def getLocale: ju.Locale
  def getOutputStream: ServletOutputStream
  def getWriter: PrintWriter
  def isCommitted: Boolean
  def reset(): Unit
  def resetBuffer(): Unit
  def setCharacterEncoding(charset: String): Unit
  def setContentLength(len: Int): Unit
  def setContentType(`type`: String): Unit
}

class JavaxServletResponse(servletResponse: javax.servlet.ServletResponse) extends ServletResponse {
  override def getNativeServletResponse: javax.servlet.ServletResponse = servletResponse

  override def getCharacterEncoding: String = servletResponse.getCharacterEncoding
  override def getContentType: String = servletResponse.getContentType
  override def getLocale: ju.Locale = servletResponse.getLocale
  override def getOutputStream: ServletOutputStream = ServletOutputStream(servletResponse.getOutputStream)
  override def getWriter: PrintWriter = servletResponse.getWriter
  override def isCommitted: Boolean = servletResponse.isCommitted
  override def reset(): Unit = servletResponse.reset()
  override def resetBuffer(): Unit = servletResponse.resetBuffer()
  override def setCharacterEncoding(charset: String): Unit = servletResponse.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = servletResponse.setContentLength(len)
  override def setContentType(`type`: String): Unit = servletResponse.setContentType(`type`)
}

class JakartaServletResponse(servletResponse: jakarta.servlet.ServletResponse) extends ServletResponse {
  override def getNativeServletResponse: jakarta.servlet.ServletResponse = servletResponse

  override def getCharacterEncoding: String = servletResponse.getCharacterEncoding
  override def getContentType: String = servletResponse.getContentType
  override def getLocale: ju.Locale = servletResponse.getLocale
  override def getOutputStream: ServletOutputStream = ServletOutputStream(servletResponse.getOutputStream)
  override def getWriter: PrintWriter = servletResponse.getWriter
  override def isCommitted: Boolean = servletResponse.isCommitted
  override def reset(): Unit = servletResponse.reset()
  override def resetBuffer(): Unit = servletResponse.resetBuffer()
  override def setCharacterEncoding(charset: String): Unit = servletResponse.setCharacterEncoding(charset)
  override def setContentLength(len: Int): Unit = servletResponse.setContentLength(len)
  override def setContentType(`type`: String): Unit = servletResponse.setContentType(`type`)
}
