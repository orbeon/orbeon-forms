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


object WindowState {
  val Normal   : WindowState = new WindowState("normal")
  val Maximized: WindowState = new WindowState("maximized")
  val Minimized: WindowState = new WindowState("minimized")

  val NORMAL   : WindowState = Normal
  val MAXIMIZED: WindowState = Maximized
  val MINIMIZED: WindowState = Minimized

  def apply(windowState: javax.portlet.WindowState): WindowState =
    Option(windowState).map(s => new WindowState(s.toString)).orNull

  def apply(windowState: jakarta.portlet.WindowState): WindowState =
    Option(windowState).map(s => new WindowState(s.toString)).orNull
}

case class WindowState(name: String) {
  override val toString: String = name.toLowerCase(ju.Locale.ENGLISH)

  override def equals(other: Any): Boolean = other match {
    case that: WindowState => toString == that.toString
    case _                 => false
  }

  override def hashCode(): Int = toString.hashCode
}
