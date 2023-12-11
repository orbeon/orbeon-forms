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

object HttpSession {
  def apply(httpSession: javax.servlet.http.HttpSession): JavaxHttpSession     = new JavaxHttpSession(httpSession)
  def apply(httpSession: jakarta.servlet.http.HttpSession): JakartaHttpSession = new JakartaHttpSession(httpSession)
}

sealed trait HttpSession {
  def getAttribute(name: String): AnyRef
  def getAttributeNames: ju.Enumeration[String]
  def getCreationTime: Long
  def getId: String
  def getLastAccessedTime: Long
  def getMaxInactiveInterval: Int
  def getServletContext: ServletContext
  def invalidate(): Unit
  def isNew: Boolean
  def removeAttribute(name: String): Unit
  def setAttribute(name: String, value: AnyRef): Unit
  def setMaxInactiveInterval(interval: Int): Unit
}

class JavaxHttpSession(httpSession: javax.servlet.http.HttpSession) extends HttpSession {
  override def getAttribute(name: String): AnyRef = httpSession.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = httpSession.getAttributeNames
  override def getCreationTime: Long = httpSession.getCreationTime
  override def getId: String = httpSession.getId
  override def getLastAccessedTime: Long = httpSession.getLastAccessedTime
  override def getMaxInactiveInterval: Int = httpSession.getMaxInactiveInterval
  override def getServletContext: ServletContext = ServletContext(httpSession.getServletContext)
  override def invalidate(): Unit = httpSession.invalidate()
  override def isNew: Boolean = httpSession.isNew
  override def removeAttribute(name: String): Unit = httpSession.removeAttribute(name)
  override def setAttribute(name: String, value: AnyRef): Unit = httpSession.setAttribute(name, value)
  override def setMaxInactiveInterval(interval: Int): Unit = httpSession.setMaxInactiveInterval(interval)
}

class JakartaHttpSession(httpSession: jakarta.servlet.http.HttpSession) extends HttpSession {
  override def getAttribute(name: String): AnyRef = httpSession.getAttribute(name)
  override def getAttributeNames: ju.Enumeration[String] = httpSession.getAttributeNames
  override def getCreationTime: Long = httpSession.getCreationTime
  override def getId: String = httpSession.getId
  override def getLastAccessedTime: Long = httpSession.getLastAccessedTime
  override def getMaxInactiveInterval: Int = httpSession.getMaxInactiveInterval
  override def getServletContext: ServletContext = ServletContext(httpSession.getServletContext)
  override def invalidate(): Unit = httpSession.invalidate()
  override def isNew: Boolean = httpSession.isNew
  override def removeAttribute(name: String): Unit = httpSession.removeAttribute(name)
  override def setAttribute(name: String, value: AnyRef): Unit = httpSession.setAttribute(name, value)
  override def setMaxInactiveInterval(interval: Int): Unit = httpSession.setMaxInactiveInterval(interval)
}
