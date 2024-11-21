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

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("OrbeonGlobals")
@JSExportAll
object Globals {

  // TODO: Most of these should be removed
  var maskFocusEvents             : Boolean                   = _ // avoid catching focus event when we do call setfocus upon server request
  var currentFocusControlId       : String                    = _ // id of the control that got the focus last
  var currentFocusControlElement  : js.Object                 = _ // element for the control that got the focus last

  var activeControl               : js.Object                 = _ // the currently active control, used to disable hint
  var hintTooltipForControl       : js.Dictionary[js.Any]     = _ // map from element id -> YUI tooltip or true, that tells us if we have already created a Tooltip for an element
  var alertTooltipForControl      : js.Dictionary[js.Any]     = _ // map from element id -> YUI alert or true, that tells us if we have already created a Tooltip for an element
  var helpTooltipForControl       : js.Dictionary[js.Any]     = _ // map from element id -> YUI help or true, that tells us if we have already created a Tooltip for an element
  var lastDialogZIndex            : Int                       = _ // zIndex of the last dialog displayed; gets incremented so the last dialog is always on top of everything else; initial value set to Bootstrap's @zindexModal

  // Reset all values upon initialization
  reset()

  def reset(): Unit = {
    maskFocusEvents            = false
    currentFocusControlId      = null
    currentFocusControlElement = null

    activeControl              = null
    hintTooltipForControl      = js.Dictionary.empty
    alertTooltipForControl     = js.Dictionary.empty
    helpTooltipForControl      = js.Dictionary.empty
    lastDialogZIndex           = 1050
  }
}
