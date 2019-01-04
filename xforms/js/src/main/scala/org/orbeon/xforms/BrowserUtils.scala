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

import enumeratum.values.{IntEnum, IntEnumEntry}
import org.scalajs.dom


object BrowserUtils {

  sealed abstract class NavigationType(val value: Int) extends IntEnumEntry
  object NavigationType extends IntEnum[NavigationType] {

    val values = findValues

    case object Navigate    extends NavigationType(value = 0)
    case object Reload      extends NavigationType(value = 1)
    case object BackForward extends NavigationType(value = 2)
    case object Reserved    extends NavigationType(value = 255)
  }

  // https://stackoverflow.com/questions/5004978/check-if-page-gets-reloaded-or-refreshed-in-javascript/53307588#53307588
  // https://www.w3.org/TR/navigation-timing/
  // https://www.w3.org/TR/resource-timing-2/
  def getNavigationType: NavigationType =
    NavigationType.withValue(dom.window.performance.navigation.`type`)
}
