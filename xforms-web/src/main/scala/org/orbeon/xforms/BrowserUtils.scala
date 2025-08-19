/**
  * Copyright (C) 2019 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xforms

import enumeratum.{Enum, EnumEntry}
import org.scalajs.dom

import scala.scalajs.js


object BrowserUtils {

  sealed abstract class NavigationType extends EnumEntry
  object NavigationType extends Enum[NavigationType] {

    val values = findValues

    case object Navigate    extends NavigationType
    case object Reload      extends NavigationType
    case object BackForward extends NavigationType
    case object Reserved    extends NavigationType
  }

  // 2025-08-19:
  // - `dom.window.performance.navigation` is deprecated.
  // - Moving to `dom.window.performance.getEntriesByType()`.
  // - Neither `dom.window.performance.getEntriesByType()` nor `dom.window.performance.navigation` seem to
  //   work with JSDOM.
  // - https://developer.mozilla.org/en-US/docs/Web/API/PerformanceNavigationTiming/type#navigate
  // - https://stackoverflow.com/questions/5004978/check-if-page-gets-reloaded-or-refreshed-in-javascript/53307588#53307588
  // - https://www.w3.org/TR/navigation-timing/
  // - https://www.w3.org/TR/resource-timing-2/
  def getNavigationType: NavigationType =
    dom.window.performance.getEntriesByType("navigation").head.asInstanceOf[js.Dynamic].`type`.asInstanceOf[String] match {
      case "navigate"     => NavigationType.Navigate
      case "reload"       => NavigationType.Reload
      case "back_forward" => NavigationType.BackForward
      case _              => NavigationType.Reserved
    }
}
