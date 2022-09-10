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

import org.orbeon.xforms.facade.Events
import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.UIEvent

import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

//
// This tracks changes to fields so that user-modified fields do not get overwritten by
// server responses.
//
// See https://github.com/orbeon/orbeon-forms/issues/1732
//
@JSExportTopLevel("OrbeonAjaxFieldChangeTracker")
object AjaxFieldChangeTracker {

  def initialize(): Unit =
    GlobalEventListenerSupport.addListener(
      dom.document,
      "input",
      // "The `input` event fires when the value of an `<input>`, `<select>`, or `<textarea>` element has
      // been changed. [...] The `input` event is fired every time the value of the element changes. This
      // is unlike the change event, which only fires when the value is committed, such as by pressing the
      // enter key, selecting a value from a list of options, and the like."
      //
      // 2020-05-29: This event appears to work with `<input>` and `<textarea>` with all modern browsers.
      (ev: UIEvent) =>
        Option(Events._findParentXFormsControl(ev.target)) foreach { control =>
          Page.getFormFromElemOrThrow(control).ajaxFieldChangeTracker.onInput(control.id)
        }
    )

  @JSExport
  def hasChangedIdsRequest(control: html.Element): Boolean =
    Page.getFormFromElemOrThrow(control).ajaxFieldChangeTracker.hasChangedIdsRequest(control.id)
}

class AjaxFieldChangeTracker {

  private var changedControlIds = Set.empty[String]

  def onInput(controlId: String): Unit =
    changedControlIds += controlId

  def beforeRequestSent(events: List[AjaxEvent]): Unit =
    changedControlIds = Set.empty

  def afterResponseProcessed(): Unit =
    changedControlIds = Set.empty

  def hasChangedIdsRequest(controlId: String): Boolean =
    changedControlIds.contains(controlId)
}