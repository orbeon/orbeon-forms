/**
 * Copyright (C) 2020 Orbeon, Inc.
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

import scala.scalajs.js.annotation.JSExportTopLevel


//
// This tracks `keydown`/`keyup` events by fields.
//
// See https://github.com/orbeon/orbeon-forms/issues/1732
//
object AjaxKeyboardEventTracker {

  private var changedIdsRequest = Map.empty[String, Int]

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.hasChangedIdsRequest")
  def hasChangedIdsRequest(controlId: String): Boolean =
    changedIdsRequest.contains(controlId)

  // Verified 2020-05-28 on Chrome: when we move out from a field, we might get `keydown`, `change`,
  // then only `keyup`. So we reset here the count for `keydown` without `keyup` for that field upon
  // `change`. When the `keyup` comes, it will not decrement IFF `processEvents()` has been called,
  // as that calls `keepOnlyNonBalanced()`.
  //
  // BUG: If there is a pending Ajax request, then `processEvents()` will only be called much later,
  // and we will decrement to `-1`!
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.clearChangedIdsRequestIfPresentForChange")
  def clearChangedIdsRequestIfPresentForChange(controlId: String): Unit =
    changedIdsRequest.get(controlId) foreach { _ =>
      changedIdsRequest += controlId -> 0
    }

  // Called upon `keydown` if it's a "changing key"
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.setOrIncrementChangedIdsRequestForKeyDown")
  def setOrIncrementChangedIdsRequestForKeyDown(controlId: String): Unit =
    changedIdsRequest += controlId -> (changedIdsRequest.getOrElse(controlId, 0) + 1)

  // Called upon `keyup` if it's a "changing key"
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.decrementChangedIdsRequestIfPresentForKeyUp")
  def decrementChangedIdsRequestIfPresentForKeyUp(controlId: String): Unit = {
    changedIdsRequest.get(controlId) foreach { v =>
      changedIdsRequest += controlId -> (v - 1)
    }
  }

  // Remove from this list of ids that changed the id of controls for which we have received the `keyup`
  // corresponding to the `keydown`.
  // Q: Should we do this only for the controls in the form we are currently processing?
  def keepOnlyNonBalanced(): Unit =
    changedIdsRequest = changedIdsRequest filter (_._2 != 0)

  def clear(): Unit =
    changedIdsRequest = Map.empty
}
