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

object HttpSessionEvent {
  def apply(httpSessionEvent: javax.servlet.http.HttpSessionEvent): JavaxHttpSessionEvent     = new JavaxHttpSessionEvent(httpSessionEvent)
  def apply(httpSessionEvent: jakarta.servlet.http.HttpSessionEvent): JakartaHttpSessionEvent = new JakartaHttpSessionEvent(httpSessionEvent)
}

trait HttpSessionEvent {
  def getSession: HttpSession
}

class JavaxHttpSessionEvent(httpSessionEvent: javax.servlet.http.HttpSessionEvent) extends HttpSessionEvent {
  override def getSession: HttpSession = HttpSession(httpSessionEvent.getSession)
}

class JakartaHttpSessionEvent(httpSessionEvent: jakarta.servlet.http.HttpSessionEvent) extends HttpSessionEvent {
  override def getSession: HttpSession = HttpSession(httpSessionEvent.getSession)
}
