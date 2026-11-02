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


object PortletSession {
  val ApplicationScope: Int = 0x01
  val PortletScope    : Int = 0x02

  val APPLICATION_SCOPE: Int = ApplicationScope
  val PORTLET_SCOPE    : Int = PortletScope

  def apply(portletSession: javax.portlet.PortletSession): PortletSession     = new JavaxPortletSession(portletSession)
  def apply(portletSession: jakarta.portlet.PortletSession): PortletSession = new JakartaPortletSession(portletSession)
}

trait PortletSession {
  // javax/jakarta.portlet.PortletSession
  def getNativePortletSession: AnyRef

  def getAttribute(name: String): AnyRef
  def getAttribute(name: String, scope: Int): AnyRef
  def getAttributeMap: ju.Map[String, AnyRef]
  def getAttributeMap(scope: Int): ju.Map[String, AnyRef]
  def getAttributeNames: ju.Enumeration[String]
  def getAttributeNames(scope: Int): ju.Enumeration[String]
  def getCreationTime: Long
  def getId: String
  def getLastAccessedTime: Long
  def getMaxInactiveInterval: Int
  def getPortletContext: PortletContext
  def invalidate(): Unit
  def isNew: Boolean
  def removeAttribute(name: String): Unit
  def removeAttribute(name: String, scope: Int): Unit
  def setAttribute(name: String, value: AnyRef): Unit
  def setAttribute(name: String, value: AnyRef, scope: Int): Unit
  def setMaxInactiveInterval(interval: Int): Unit
}

class JavaxPortletSession(portletSession: javax.portlet.PortletSession) extends PortletSession {
  override def getNativePortletSession: javax.portlet.PortletSession = portletSession

  override def getAttribute(name: String): AnyRef = portletSession.getAttribute(name)
  override def getAttribute(name: String, scope: Int): AnyRef = portletSession.getAttribute(name, scope)
  override def getAttributeMap: ju.Map[String, AnyRef] = portletSession.getAttributeMap
  override def getAttributeMap(scope: Int): ju.Map[String, AnyRef] = portletSession.getAttributeMap(scope)
  override def getAttributeNames: ju.Enumeration[String] = portletSession.getAttributeNames
  override def getAttributeNames(scope: Int): ju.Enumeration[String] = portletSession.getAttributeNames(scope)
  override def getCreationTime: Long = portletSession.getCreationTime
  override def getId: String = portletSession.getId
  override def getLastAccessedTime: Long = portletSession.getLastAccessedTime
  override def getMaxInactiveInterval: Int = portletSession.getMaxInactiveInterval
  override def getPortletContext: PortletContext = PortletContext(portletSession.getPortletContext)
  override def invalidate(): Unit = portletSession.invalidate()
  override def isNew: Boolean = portletSession.isNew
  override def removeAttribute(name: String): Unit = portletSession.removeAttribute(name)
  override def removeAttribute(name: String, scope: Int): Unit = portletSession.removeAttribute(name, scope)
  override def setAttribute(name: String, value: AnyRef): Unit = portletSession.setAttribute(name, value)
  override def setAttribute(name: String, value: AnyRef, scope: Int): Unit = portletSession.setAttribute(name, value, scope)
  override def setMaxInactiveInterval(interval: Int): Unit = portletSession.setMaxInactiveInterval(interval)
}

class JakartaPortletSession(portletSession: jakarta.portlet.PortletSession) extends PortletSession {
  override def getNativePortletSession: jakarta.portlet.PortletSession = portletSession

  override def getAttribute(name: String): AnyRef = portletSession.getAttribute(name)
  override def getAttribute(name: String, scope: Int): AnyRef = portletSession.getAttribute(name, scope)
  override def getAttributeMap: ju.Map[String, AnyRef] = portletSession.getAttributeMap
  override def getAttributeMap(scope: Int): ju.Map[String, AnyRef] = portletSession.getAttributeMap(scope)
  override def getAttributeNames: ju.Enumeration[String] = portletSession.getAttributeNames
  override def getAttributeNames(scope: Int): ju.Enumeration[String] = portletSession.getAttributeNames(scope)
  override def getCreationTime: Long = portletSession.getCreationTime
  override def getId: String = portletSession.getId
  override def getLastAccessedTime: Long = portletSession.getLastAccessedTime
  override def getMaxInactiveInterval: Int = portletSession.getMaxInactiveInterval
  override def getPortletContext: PortletContext = PortletContext(portletSession.getPortletContext)
  override def invalidate(): Unit = portletSession.invalidate()
  override def isNew: Boolean = portletSession.isNew
  override def removeAttribute(name: String): Unit = portletSession.removeAttribute(name)
  override def removeAttribute(name: String, scope: Int): Unit = portletSession.removeAttribute(name, scope)
  override def setAttribute(name: String, value: AnyRef): Unit = portletSession.setAttribute(name, value)
  override def setAttribute(name: String, value: AnyRef, scope: Int): Unit = portletSession.setAttribute(name, value, scope)
  override def setMaxInactiveInterval(interval: Int): Unit = portletSession.setMaxInactiveInterval(interval)
}
