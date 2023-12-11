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

import java.io.OutputStream

trait WriteListener {
  def onError(t: Throwable): Unit
  def onWritePossible(): Unit

  private val outer = this

  def asJavax: javax.servlet.WriteListener = new javax.servlet.WriteListener {
    override def onError(t: Throwable): Unit = outer.onError(t)
    override def onWritePossible(): Unit = outer.onWritePossible()
  }

  def asJakarta: jakarta.servlet.WriteListener = new jakarta.servlet.WriteListener {
    override def onError(t: Throwable): Unit = outer.onError(t)
    override def onWritePossible(): Unit = outer.onWritePossible()
  }
}

object ServletOutputStream {
  def apply(servletOutputStream: javax.servlet.ServletOutputStream): JavaxServletOutputStream     = new JavaxServletOutputStream(servletOutputStream)
  def apply(servletOutputStream: jakarta.servlet.ServletOutputStream): JakartaServletOutputStream = new JakartaServletOutputStream(servletOutputStream)
}

trait ServletOutputStream extends OutputStream {
  override def flush(): Unit = ()
  def isReady: Boolean
  def setWriteListener(writeListener: WriteListener): Unit
  def write(i: Int): Unit
}

class JavaxServletOutputStream(servletOutputStream: javax.servlet.ServletOutputStream) extends ServletOutputStream {
  override def flush(): Unit = servletOutputStream.flush()
  override def isReady: Boolean = servletOutputStream.isReady
  override def setWriteListener(writeListener: WriteListener): Unit = servletOutputStream.setWriteListener(writeListener.asJavax)
  override def write(i: Int): Unit = servletOutputStream.write(i)
}

class JakartaServletOutputStream(servletOutputStream: jakarta.servlet.ServletOutputStream) extends ServletOutputStream {
  override def flush(): Unit = servletOutputStream.flush()
  override def isReady: Boolean = servletOutputStream.isReady
  override def setWriteListener(writeListener: WriteListener): Unit = servletOutputStream.setWriteListener(writeListener.asJakarta)
  override def write(i: Int): Unit = servletOutputStream.write(i)
}
