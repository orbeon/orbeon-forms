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


object PortletMode {
  val View: PortletMode = new PortletMode("view")
  val Edit: PortletMode = new PortletMode("edit")
  val Help: PortletMode = new PortletMode("help")

  val VIEW: PortletMode = View
  val EDIT: PortletMode = Edit
  val HELP: PortletMode = Help

  def apply(portletMode: javax.portlet.PortletMode): PortletMode =
    Option(portletMode).map(m => new PortletMode(m.toString)).orNull

  def apply(portletMode: jakarta.portlet.PortletMode): PortletMode =
    Option(portletMode).map(m => new PortletMode(m.toString)).orNull
}

case class PortletMode(name: String) {
  override val toString: String = name.toLowerCase(ju.Locale.ENGLISH)

  override def equals(other: Any): Boolean = other match {
    case that: PortletMode => toString == that.toString
    case _                 => false
  }

  override def hashCode(): Int = toString.hashCode
}
