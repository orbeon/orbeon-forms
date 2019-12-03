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

import org.orbeon.facades.Bowser
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

@JSExportTopLevel("ORBEON.xforms.Globals")
@JSExportAll
object Globals {

  lazy val isRenderingEngineTrident: Boolean                   = Bowser.msie.getOrElse(false) // one usage left to check as of 2019-01-04

  var eventQueue                  : js.Array[AjaxServerEvent] = js.Array()                   // events to be sent to the server
  var eventsFirstEventTime        : Double                    = 0                            // time when the first event in the queue was added
  var requestForm                 : html.Form                 = null                         // hTML for the request currently in progress
  var requestIgnoreErrors         : Boolean                   = false                        // should we ignore errors that result from running this request
  var requestInProgress           : Boolean                   = false                        // indicates whether an Ajax request is currently in process
  var requestDocument             : String                    = ""                           // the last Ajax request, so we can resend it if necessary
  var requestTryCount             : Int                       = 0                            // how many attempts to run the current Ajax request we have done so far
  var executeEventFunctionQueued  : Int                       = 0                            // number of ORBEON.xforms.server.AjaxServer.executeNextRequest waiting to be executed
  var maskFocusEvents             : Boolean                   = false                        // avoid catching focus event when we do call setfocus upon server request
  var maskDialogCloseEvents       : Boolean                   = false                        // avoid catching a dialog close event received from the server, so we don't sent it back to the server
  var currentFocusControlId       : js.Object                 = null                         // id of the control that got the focus last
  var currentFocusControlElement  : js.Object                 = null                         // element for the control that got the focus last
  var yuiCalendar                 : js.Object                 = null                         // reusable calendar widget
  var tooltipLibraryInitialized   : Boolean                   = false
  var changedIdsRequest           : js.Dictionary[Int]        = js.Dictionary.empty          // id of controls that have been touched by user since the last response was received
  var loadingOtherPage            : Boolean                   = false                        // flag set when loading other page that prevents the loading indicator to disappear
  var activeControl               : js.Object                 = null                         // the currently active control, used to disable hint
  var dialogs                     : js.Dictionary[js.Dynamic] = js.Dictionary.empty          // map for dialogs: id -> YUI dialog object
  var hintTooltipForControl       : js.Dictionary[js.Any]     = js.Dictionary.empty          // map from element id -> YUI tooltip or true, that tells us if we have already created a Tooltip for an element
  var alertTooltipForControl      : js.Dictionary[js.Any]     = js.Dictionary.empty          // map from element id -> YUI alert or true, that tells us if we have already created a Tooltip for an element
  var helpTooltipForControl       : js.Dictionary[js.Any]     = js.Dictionary.empty          // map from element id -> YUI help or true, that tells us if we have already created a Tooltip for an element
  var lastEventSentTime           : Double                    = new js.Date().getTime()      // timestamp when the last event was sent to server
  var sliderYui                   : js.Dictionary[js.Any]     = js.Dictionary.empty          // maps slider id to the YUI object for that slider
  var lastDialogZIndex            : Int                       = 1050                         // zIndex of the last dialog displayed; gets incremented so the last dialog is always on top of everything else; initial value set to Bootstrap's @zindexModal

  var modalProgressPanel          : js.Object                 = null                         // overlay modal panel for displaying progress bar
  var modalProgressPanelTimerId   : js.Any                    = null                         // timer id for modal progress panels shown asynchronously (iOS)

  var changeListeners             : js.Dictionary[js.Any]     = js.Dictionary.empty          // maps control id to DOM element for which we have registered a change listener
  var topLevelListenerRegistered  : Boolean                   = false                        // have we already registered the listeners on the top-level elements, which never change
}
