/**
 * Copyright (C) 2026 Orbeon, Inc.
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
package org.orbeon.oxf.portlet

import java.util as ju


object FilterConfig {
  def apply(filterConfig: javax.portlet.filter.FilterConfig): FilterConfig     = new JavaxFilterConfig(filterConfig)
  def apply(filterConfig: jakarta.portlet.filter.FilterConfig): FilterConfig = new JakartaFilterConfig(filterConfig)
}

trait FilterConfig {
  def getFilterName: String
  def getInitParameter(name: String): String
  def getInitParameterNames: ju.Enumeration[String]
  def getPortletContext: PortletContext
}

class JavaxFilterConfig(filterConfig: javax.portlet.filter.FilterConfig) extends FilterConfig {
  override def getFilterName: String = filterConfig.getFilterName
  override def getInitParameter(name: String): String = filterConfig.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = filterConfig.getInitParameterNames
  override def getPortletContext: PortletContext = PortletContext(filterConfig.getPortletContext)
}

class JakartaFilterConfig(filterConfig: jakarta.portlet.filter.FilterConfig) extends FilterConfig {
  override def getFilterName: String = filterConfig.getFilterName
  override def getInitParameter(name: String): String = filterConfig.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = filterConfig.getInitParameterNames
  override def getPortletContext: PortletContext = PortletContext(filterConfig.getPortletContext)
}
