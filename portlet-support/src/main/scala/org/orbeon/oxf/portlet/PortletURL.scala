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

import java.io.Writer
import java.util as ju
import scala.annotation.nowarn


trait BaseURL {
  // javax/jakarta.portlet.BaseURL
  def getNativeBaseURL: AnyRef

  def addProperty(key: String, value: String): Unit
  def getParameterMap: ju.Map[String, Array[String]]
  def setParameter(name: String, value: String): Unit
  def setParameter(name: String, values: Array[String]): Unit
  def setParameters(parameters: ju.Map[String, Array[String]]): Unit
  def setProperty(key: String, value: String): Unit
  def setSecure(secure: Boolean): Unit
  def write(out: Writer): Unit
  def write(out: Writer, escapeXML: Boolean): Unit
}

object PortletURL {
  def apply(portletURL: javax.portlet.PortletURL): PortletURL     = new JavaxPortletURL(portletURL)
  def apply(portletURL: jakarta.portlet.PortletURL): PortletURL = new JakartaPortletURL(portletURL)
}

trait PortletURL extends BaseURL {
  override def getNativeBaseURL: AnyRef
  def getPortletMode: PortletMode
  def getWindowState: WindowState
  def removePublicRenderParameter(name: String): Unit
  def setPortletMode(portletMode: PortletMode): Unit
  def setWindowState(windowState: WindowState): Unit
}

object ResourceURL {
  val Full   : String = "cacheLevelFull"
  val Portlet: String = "cacheLevelPortlet"
  val Page   : String = "cacheLevelPage"
  val Shared : String = "javax.portlet.shared"

  val FULL   : String = Full
  val PORTLET: String = Portlet
  val PAGE   : String = Page
  val SHARED : String = Shared

  def apply(resourceURL: javax.portlet.ResourceURL): ResourceURL     = new JavaxResourceURL(resourceURL)
  def apply(resourceURL: jakarta.portlet.ResourceURL): ResourceURL = new JakartaResourceURL(resourceURL)
}

trait ResourceURL extends BaseURL {
  override def getNativeBaseURL: AnyRef
  def getCacheability: String
  def setCacheability(cacheLevel: String): Unit
  def setResourceID(resourceID: String): Unit
}

@nowarn("cat=deprecation")
class JavaxBaseURL(baseURL: javax.portlet.BaseURL) extends BaseURL {
  override def getNativeBaseURL: javax.portlet.BaseURL = baseURL

  override def addProperty(key: String, value: String): Unit = baseURL.addProperty(key, value)
  override def getParameterMap: ju.Map[String, Array[String]] = baseURL.getParameterMap
  override def setParameter(name: String, value: String): Unit = baseURL.setParameter(name, value)
  override def setParameter(name: String, values: Array[String]): Unit = baseURL.setParameter(name, values*)
  override def setParameters(parameters: ju.Map[String, Array[String]]): Unit = baseURL.setParameters(parameters)
  override def setProperty(key: String, value: String): Unit = baseURL.setProperty(key, value)
  override def setSecure(secure: Boolean): Unit = baseURL.setSecure(secure)
  override def write(out: Writer): Unit = baseURL.write(out)
  override def write(out: Writer, escapeXML: Boolean): Unit = baseURL.write(out, escapeXML)
  override def toString: String = baseURL.toString
}

@nowarn("cat=deprecation")
class JakartaBaseURL(baseURL: jakarta.portlet.BaseURL) extends BaseURL {
  override def getNativeBaseURL: jakarta.portlet.BaseURL = baseURL

  override def addProperty(key: String, value: String): Unit = baseURL.addProperty(key, value)
  override def getParameterMap: ju.Map[String, Array[String]] = baseURL.getParameterMap
  override def setParameter(name: String, value: String): Unit = baseURL.setParameter(name, value)
  override def setParameter(name: String, values: Array[String]): Unit = baseURL.setParameter(name, values*)
  override def setParameters(parameters: ju.Map[String, Array[String]]): Unit = baseURL.setParameters(parameters)
  override def setProperty(key: String, value: String): Unit = baseURL.setProperty(key, value)
  override def setSecure(secure: Boolean): Unit = baseURL.setSecure(secure)
  override def write(out: Writer): Unit = baseURL.write(out)
  override def write(out: Writer, escapeXML: Boolean): Unit = baseURL.write(out, escapeXML)
  override def toString: String = baseURL.toString
}

@nowarn("cat=deprecation")
class JavaxPortletURL(portletURL: javax.portlet.PortletURL) extends JavaxBaseURL(portletURL) with PortletURL {
  override def getNativeBaseURL: javax.portlet.PortletURL = portletURL

  override def getPortletMode: PortletMode = PortletMode(portletURL.getPortletMode)
  override def getWindowState: WindowState = WindowState(portletURL.getWindowState)
  override def removePublicRenderParameter(name: String): Unit = portletURL.removePublicRenderParameter(name)
  override def setPortletMode(portletMode: PortletMode): Unit =
    portletURL.setPortletMode(Option(portletMode).map(m => new javax.portlet.PortletMode(m.toString)).orNull)
  override def setWindowState(windowState: WindowState): Unit =
    portletURL.setWindowState(Option(windowState).map(s => new javax.portlet.WindowState(s.toString)).orNull)
}

@nowarn("cat=deprecation")
class JakartaPortletURL(portletURL: jakarta.portlet.PortletURL) extends JakartaBaseURL(portletURL) with PortletURL {
  override def getNativeBaseURL: jakarta.portlet.PortletURL = portletURL

  override def getPortletMode: PortletMode = PortletMode(portletURL.getPortletMode)
  override def getWindowState: WindowState = WindowState(portletURL.getWindowState)
  override def removePublicRenderParameter(name: String): Unit = portletURL.removePublicRenderParameter(name)
  override def setPortletMode(portletMode: PortletMode): Unit =
    portletURL.setPortletMode(Option(portletMode).map(m => new jakarta.portlet.PortletMode(m.toString)).orNull)
  override def setWindowState(windowState: WindowState): Unit =
    portletURL.setWindowState(Option(windowState).map(s => new jakarta.portlet.WindowState(s.toString)).orNull)
}

class JavaxResourceURL(resourceURL: javax.portlet.ResourceURL) extends JavaxBaseURL(resourceURL) with ResourceURL {
  override def getNativeBaseURL: javax.portlet.ResourceURL = resourceURL

  override def getCacheability: String = resourceURL.getCacheability
  override def setCacheability(cacheLevel: String): Unit = resourceURL.setCacheability(cacheLevel)
  override def setResourceID(resourceID: String): Unit = resourceURL.setResourceID(resourceID)
}

class JakartaResourceURL(resourceURL: jakarta.portlet.ResourceURL) extends JakartaBaseURL(resourceURL) with ResourceURL {
  override def getNativeBaseURL: jakarta.portlet.ResourceURL = resourceURL

  override def getCacheability: String = resourceURL.getCacheability
  override def setCacheability(cacheLevel: String): Unit = resourceURL.setCacheability(cacheLevel)
  override def setResourceID(resourceID: String): Unit = resourceURL.setResourceID(resourceID)
}
