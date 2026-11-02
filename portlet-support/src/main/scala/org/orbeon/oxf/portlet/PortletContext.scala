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

import java.io.InputStream
import java.net.URL
import java.util as ju


object PortletContext {
  def apply(portletContext: javax.portlet.PortletContext): PortletContext     = new JavaxPortletContext(portletContext)
  def apply(portletContext: jakarta.portlet.PortletContext): PortletContext = new JakartaPortletContext(portletContext)
}

trait PortletContext {
  // javax/jakarta.portlet.PortletContext
  def getNativePortletContext: AnyRef

  def getAttribute(name: String): AnyRef
  def getAttributeNames: ju.Enumeration[String]
  def getContextPath: String
  def getInitParameter(name: String): String
  def getInitParameterNames: ju.Enumeration[String]
  def getRealPath(path: String): String
  def getResource(path: String): URL
  def getResourceAsStream(path: String): InputStream
  def log(msg: String): Unit
  def log(message: String, throwable: Throwable): Unit
  def removeAttribute(name: String): Unit
  def setAttribute(name: String, `object`: AnyRef): Unit
}

class JavaxPortletContext(portletContext: javax.portlet.PortletContext) extends PortletContext {
  override def getNativePortletContext: javax.portlet.PortletContext = portletContext

  override def getAttribute(name: String): AnyRef = portletContext.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = portletContext.getAttributeNames
  override def getContextPath: String = portletContext.getContextPath
  override def getInitParameter(name: String): String = portletContext.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = portletContext.getInitParameterNames
  override def getRealPath(path: String): String = portletContext.getRealPath(path)
  override def getResource(path: String): URL = portletContext.getResource(path)
  override def getResourceAsStream(path: String): InputStream = portletContext.getResourceAsStream(path)
  override def log(msg: String): Unit = portletContext.log(msg)
  override def log(message: String, throwable: Throwable): Unit = portletContext.log(message, throwable)
  override def removeAttribute(name: String): Unit = portletContext.removeAttribute(name)
  override def setAttribute(name: String, `object`: AnyRef): Unit = portletContext.setAttribute(name, `object`)
}

class JakartaPortletContext(portletContext: jakarta.portlet.PortletContext) extends PortletContext {
  override def getNativePortletContext: jakarta.portlet.PortletContext = portletContext

  override def getAttribute(name: String): AnyRef = portletContext.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = portletContext.getAttributeNames
  override def getContextPath: String = portletContext.getContextPath
  override def getInitParameter(name: String): String = portletContext.getInitParameter(name)
  override def getInitParameterNames: ju.Enumeration[String] = portletContext.getInitParameterNames
  override def getRealPath(path: String): String = portletContext.getRealPath(path)
  override def getResource(path: String): URL = portletContext.getResource(path)
  override def getResourceAsStream(path: String): InputStream = portletContext.getResourceAsStream(path)
  override def log(msg: String): Unit = portletContext.log(msg)
  override def log(message: String, throwable: Throwable): Unit = portletContext.log(message, throwable)
  override def removeAttribute(name: String): Unit = portletContext.removeAttribute(name)
  override def setAttribute(name: String, `object`: AnyRef): Unit = portletContext.setAttribute(name, `object`)
}
