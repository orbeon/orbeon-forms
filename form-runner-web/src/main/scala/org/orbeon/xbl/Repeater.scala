/**
  * Copyright (C) 2018 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.orbeon.xforms.facade.{XBL, XBLCompanion}


object Repeater {

  XBL.declareCompanion(
    "fr|repeater",
    new XBLCompanion {
      override def init()   : Unit = RepeaterMenus // initialize menus once
      override def destroy(): Unit = ()
    }
  )
}

object RepeaterMenus extends GridSectionMenus {
  override def componentName = "repeater"
}