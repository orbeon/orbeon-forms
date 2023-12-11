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

import java.io.InputStream

trait ReadListener {
  def onDataAvailable(): Unit
  def onAllDataRead(): Unit
  def onError(t: Throwable): Unit

  private val outer = this

  def asJavax: javax.servlet.ReadListener = new javax.servlet.ReadListener {
    override def onDataAvailable(): Unit = outer.onDataAvailable()
    override def onAllDataRead(): Unit = outer.onAllDataRead()
    override def onError(t: Throwable): Unit = outer.onError(t)
  }

  def asJakarta: jakarta.servlet.ReadListener = new jakarta.servlet.ReadListener {
    override def onDataAvailable(): Unit = outer.onDataAvailable()
    override def onAllDataRead(): Unit = outer.onAllDataRead()
    override def onError(t: Throwable): Unit = outer.onError(t)
  }
}

object ServletInputStream {
  def apply(servletInputStream: javax.servlet.ServletInputStream): JavaxServletInputStream     = new JavaxServletInputStream(servletInputStream)
  def apply(servletInputStream: jakarta.servlet.ServletInputStream): JakartaServletInputStream = new JakartaServletInputStream(servletInputStream)
}

trait ServletInputStream extends InputStream {
  override def read(): Int
  def isFinished: Boolean
  def isReady: Boolean
  def setReadListener(readListener: ReadListener): Unit
}

class JavaxServletInputStream(servletInputStream: javax.servlet.ServletInputStream) extends ServletInputStream {
  override def read(): Int = servletInputStream.read()
  override def isFinished: Boolean = servletInputStream.isFinished
  override def isReady: Boolean = servletInputStream.isReady
  override def setReadListener(readListener: ReadListener): Unit = servletInputStream.setReadListener(readListener.asJavax)
}

class JakartaServletInputStream(servletInputStream: jakarta.servlet.ServletInputStream) extends ServletInputStream {
  override def read(): Int = servletInputStream.read()
  override def isFinished: Boolean = servletInputStream.isFinished
  override def isReady: Boolean = servletInputStream.isReady
  override def setReadListener(readListener: ReadListener): Unit = servletInputStream.setReadListener(readListener.asJakarta)
}
