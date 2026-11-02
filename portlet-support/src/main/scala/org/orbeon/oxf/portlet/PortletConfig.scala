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


object PortletConfig {
  def apply(portletConfig: javax.portlet.PortletConfig): PortletConfig     = new JavaxPortletConfig(portletConfig)
  def apply(portletConfig: jakarta.portlet.PortletConfig): PortletConfig = new JakartaPortletConfig(portletConfig)
}

trait PortletConfig {
  // javax/jakarta.portlet.PortletConfig
  def getNativePortletConfig: AnyRef

  def getDefaultNamespace: String
  def getInitParameter(name: String): String
  def getInitParameterNames: ju.Enumeration[String]
  def getPortletContext: PortletContext
  def getPortletName: String
  def getResourceBundle(locale: ju.Locale): ju.ResourceBundle
}

class JavaxPortletConfig(portletConfig: javax.portlet.PortletConfig) extends PortletConfig {
  override def getNativePortletConfig: javax.portlet.PortletConfig = portletConfig

  override def getDefaultNamespace: String = portletConfig.getDefaultNamespace
  override def getInitParameter(name: String): String = portletConfig.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = portletConfig.getInitParameterNames
  override def getPortletContext: PortletContext = PortletContext(portletConfig.getPortletContext)
  override def getPortletName: String = portletConfig.getPortletName
  override def getResourceBundle(locale: ju.Locale): ju.ResourceBundle = portletConfig.getResourceBundle(locale)
}

class JakartaPortletConfig(portletConfig: jakarta.portlet.PortletConfig) extends PortletConfig {
  override def getNativePortletConfig: jakarta.portlet.PortletConfig = portletConfig

  override def getDefaultNamespace: String = portletConfig.getDefaultNamespace
  override def getInitParameter(name: String): String = portletConfig.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = portletConfig.getInitParameterNames
  override def getPortletContext: PortletContext = PortletContext(portletConfig.getPortletContext)
  override def getPortletName: String = portletConfig.getPortletName
  override def getResourceBundle(locale: ju.Locale): ju.ResourceBundle = portletConfig.getResourceBundle(locale)
}
