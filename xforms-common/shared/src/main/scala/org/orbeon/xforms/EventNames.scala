/**
 * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.xforms


object EventNames {

  private val Prefix = "xxforms-upload-"

  val XXFormsUploadStart       = Prefix + "start"
  val XXFormsUploadProgress    = Prefix + "progress"
  val XXFormsUploadCancel      = Prefix + "cancel"
  val XXFormsUploadDone        = Prefix + "done"
  val XXFormsUploadError       = Prefix + "error"

  val XXFormsAllEventsRequired = "xxforms-all-events-required"
  val XXFormsSessionHeartbeat  = "xxforms-session-heartbeat"
  val XXFormsServerEvents      = "xxforms-server-events"
  val XXFormsPoll              = "xxforms-poll"
  val XXFormsValue             = "xxforms-value"
  val XXFormsRpcRequest        = "xxforms-rpc-request"
  val XXFormsDnD               = "xxforms-dnd"

  val XFormsFocus              = "xforms-focus"

  val Change                   = "change"
  val KeyPress                 = "keypress"
  val KeyDown                  = "keydown"
  val KeyUp                    = "keyup"
  val TouchStart               = "touchstart"
  val FocusIn                  = "focusin"
  val FocusOut                 = "focusout"
  val DOMContentLoaded         = "DOMContentLoaded"
  val DOMActivate              = "DOMActivate"

  val KeyTextPropertyName      = "text"
  val KeyModifiersPropertyName = "modifiers"

  val InteractiveReadyState = "interactive"
  val CompleteReadyState = "complete"

  val EventsWithoutTargetId: Set[String] = Set(XXFormsAllEventsRequired, XXFormsServerEvents, XXFormsSessionHeartbeat)
  val EventsWithoutSequence: Set[String] = Set(XXFormsUploadProgress, XXFormsSessionHeartbeat)
  val KeyboardEvents       : Set[String] = Set(KeyPress, KeyDown, KeyUp)
}
