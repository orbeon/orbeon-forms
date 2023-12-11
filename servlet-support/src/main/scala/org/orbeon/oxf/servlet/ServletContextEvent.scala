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

object ServletContextEvent {
  def apply(servletContextEvent: javax.servlet.ServletContextEvent): JavaxServletContextEvent     = new JavaxServletContextEvent(servletContextEvent)
  def apply(servletContextEvent: jakarta.servlet.ServletContextEvent): JakartaServletContextEvent = new JakartaServletContextEvent(servletContextEvent)
}

trait ServletContextEvent {
  def getServletContext: ServletContext
}

class JavaxServletContextEvent(servletContextEvent: javax.servlet.ServletContextEvent) extends ServletContextEvent {
  override def getServletContext: ServletContext = ServletContext(servletContextEvent.getServletContext)
}

class JakartaServletContextEvent(servletContextEvent: jakarta.servlet.ServletContextEvent) extends ServletContextEvent {
  override def getServletContext: ServletContext = ServletContext(servletContextEvent.getServletContext)
}
