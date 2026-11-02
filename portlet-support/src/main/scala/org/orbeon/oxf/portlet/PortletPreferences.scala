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


object PortletPreferences {
  def apply(portletPreferences: javax.portlet.PortletPreferences): PortletPreferences     = new JavaxPortletPreferences(portletPreferences)
  def apply(portletPreferences: jakarta.portlet.PortletPreferences): PortletPreferences = new JakartaPortletPreferences(portletPreferences)
}

trait PortletPreferences {
  // javax/jakarta.portlet.PortletPreferences
  def getNativePortletPreferences: AnyRef

  def getMap: ju.Map[String, Array[String]]
  def getNames: ju.Enumeration[String]
  def getValue(key: String, `def`: String): String
  def getValues(key: String, `def`: Array[String]): Array[String]
  def isReadOnly(key: String): Boolean
  def reset(key: String): Unit
  def setValue(key: String, value: String): Unit
  def setValues(key: String, values: Array[String]): Unit
  def store(): Unit
}

class JavaxPortletPreferences(portletPreferences: javax.portlet.PortletPreferences) extends PortletPreferences {
  override def getNativePortletPreferences: javax.portlet.PortletPreferences = portletPreferences

  override def getMap: ju.Map[String, Array[String]] = portletPreferences.getMap
  override def getNames: ju.Enumeration[String] = portletPreferences.getNames
  override def getValue(key: String, `def`: String): String = portletPreferences.getValue(key, `def`)
  override def getValues(key: String, `def`: Array[String]): Array[String] = portletPreferences.getValues(key, `def`)
  override def isReadOnly(key: String): Boolean = portletPreferences.isReadOnly(key)
  override def reset(key: String): Unit = portletPreferences.reset(key)
  override def setValue(key: String, value: String): Unit = portletPreferences.setValue(key, value)
  override def setValues(key: String, values: Array[String]): Unit = portletPreferences.setValues(key, values*)
  override def store(): Unit = portletPreferences.store()
}

class JakartaPortletPreferences(portletPreferences: jakarta.portlet.PortletPreferences) extends PortletPreferences {
  override def getNativePortletPreferences: jakarta.portlet.PortletPreferences = portletPreferences

  override def getMap: ju.Map[String, Array[String]] = portletPreferences.getMap
  override def getNames: ju.Enumeration[String] = portletPreferences.getNames
  override def getValue(key: String, `def`: String): String = portletPreferences.getValue(key, `def`)
  override def getValues(key: String, `def`: Array[String]): Array[String] = portletPreferences.getValues(key, `def`)
  override def isReadOnly(key: String): Boolean = portletPreferences.isReadOnly(key)
  override def reset(key: String): Unit = portletPreferences.reset(key)
  override def setValue(key: String, value: String): Unit = portletPreferences.setValue(key, value)
  override def setValues(key: String, values: Array[String]): Unit = portletPreferences.setValues(key, values*)
  override def store(): Unit = portletPreferences.store()
}
