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

import java.{util => ju}

object ServletRegistration {
  def apply(servletRegistration: javax.servlet.ServletRegistration): ServletRegistration   = new JavaxServletRegistration(servletRegistration)
  def apply(servletRegistration: jakarta.servlet.ServletRegistration): ServletRegistration = new JakartaServletRegistration(servletRegistration)
}

trait ServletRegistration {
  def addMapping(urlPatterns: String*): ju.Set[String]
  def setInitParameter(name: String, value: String): Boolean
}

class JavaxServletRegistration(servletRegistration: javax.servlet.ServletRegistration) extends ServletRegistration {
  override def addMapping(urlPatterns: String*): ju.Set[String] = servletRegistration.addMapping(urlPatterns: _*)
  override def setInitParameter(name: String, value: String): Boolean = servletRegistration.setInitParameter(name, value)
}

class JakartaServletRegistration(servletRegistration: jakarta.servlet.ServletRegistration) extends ServletRegistration {
  override def addMapping(urlPatterns: String*): ju.Set[String] = servletRegistration.addMapping(urlPatterns: _*)
  override def setInitParameter(name: String, value: String): Boolean = servletRegistration.setInitParameter(name, value)
}