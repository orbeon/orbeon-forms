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

@JSExportTopLevel("ORBEON.xforms.Globals")
@JSExportAll
object Globals {

  var maskFocusEvents             : Boolean                   = false               // avoid catching focus event when we do call setfocus upon server request
  var maskDialogCloseEvents       : Boolean                   = false               // avoid catching a dialog close event received from the server, so we don't sent it back to the server
  var currentFocusControlId       : String                    = null                // id of the control that got the focus last
  var currentFocusControlElement  : js.Object                 = null                // element for the control that got the focus last
  var yuiCalendar                 : js.Object                 = null                // reusable calendar widget
  var tooltipLibraryInitialized   : Boolean                   = false

  var loadingOtherPage            : Boolean                   = false               // flag set when loading other page that prevents the loading indicator to disappear
  var activeControl               : js.Object                 = null                // the currently active control, used to disable hint
  var dialogs                     : js.Dictionary[js.Dynamic] = js.Dictionary.empty // map for dialogs: id -> YUI dialog object
  var hintTooltipForControl       : js.Dictionary[js.Any]     = js.Dictionary.empty // map from element id -> YUI tooltip or true, that tells us if we have already created a Tooltip for an element
  var alertTooltipForControl      : js.Dictionary[js.Any]     = js.Dictionary.empty // map from element id -> YUI alert or true, that tells us if we have already created a Tooltip for an element
  var helpTooltipForControl       : js.Dictionary[js.Any]     = js.Dictionary.empty // map from element id -> YUI help or true, that tells us if we have already created a Tooltip for an element
  var sliderYui                   : js.Dictionary[js.Any]     = js.Dictionary.empty // maps slider id to the YUI object for that slider
  var lastDialogZIndex            : Int                       = 1050                // zIndex of the last dialog displayed; gets incremented so the last dialog is always on top of everything else; initial value set to Bootstrap's @zindexModal

  var topLevelListenerRegistered  : Boolean                   = false               // have we already registered the listeners on the top-level elements, which never change
}
