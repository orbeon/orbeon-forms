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

trait HttpSessionListener {
  def sessionCreated(se: HttpSessionEvent): Unit
  def sessionDestroyed(se: HttpSessionEvent): Unit
}

class JavaxHttpSessionListener(httpSessionListener: HttpSessionListener) extends javax.servlet.http.HttpSessionListener {
  override def sessionCreated(se: javax.servlet.http.HttpSessionEvent): Unit = httpSessionListener.sessionCreated(HttpSessionEvent(se))
  override def sessionDestroyed(se: javax.servlet.http.HttpSessionEvent): Unit = httpSessionListener.sessionDestroyed(HttpSessionEvent(se))
}

class JakartaHttpSessionListener(httpSessionListener: HttpSessionListener) extends jakarta.servlet.http.HttpSessionListener {
  override def sessionCreated(se: jakarta.servlet.http.HttpSessionEvent): Unit = httpSessionListener.sessionCreated(HttpSessionEvent(se))
  override def sessionDestroyed(se: jakarta.servlet.http.HttpSessionEvent): Unit = httpSessionListener.sessionDestroyed(HttpSessionEvent(se))
}
