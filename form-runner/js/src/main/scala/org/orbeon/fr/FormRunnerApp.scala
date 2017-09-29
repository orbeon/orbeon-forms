/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.fr

import org.orbeon.{xbl, xforms}
import org.orbeon.xforms.$

// Scala.js starting point for Form Runner
object FormRunnerApp {

  def main(args: Array[String]): Unit = {

    def initializeOnDomReady(): Unit = {
      xbl.DndRepeat
      xbl.TreeSelect1
      xbl.WPaint
      xbl.HrefButton
      xbl.Grid
      xforms.XFormsApp.main(Array.empty)
    }

    $(initializeOnDomReady _)
  }
}
