/**
  * Copyright (C) 2017 Orbeon, Inc.
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

import org.scalajs.dom
import org.scalajs.dom.raw

import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

// Store for values as we think they are known to the server
@JSExportTopLevel("ORBEON.xforms.ServerValueStore")
object ServerValueStore {

  private case class ControlValue(controlElem: raw.Element, value: String)

  private var idToControlValue = Map[String, ControlValue]()

  // Store a value for a given control by id
  @JSExport
  def set(id: String, valueOrUndef: js.UndefOr[String]): Unit =
    for {
      controlElem <- Option(dom.document.getElementById(id)) // unclear if callers are sure the element exists
      value       <- valueOrUndef.toOption                   // some callers pass `undefined` (e.g. triggers)
    } locally {
      idToControlValue += id -> ControlValue(controlElem, value)
    }

  // Return the value of a control as known by the server or null
  @JSExport
  def get(id: String): String =
    idToControlValue.get(id) match {
      case None =>
        // We known nothing about this control
        null
      case Some(ControlValue(controlElem, value)) if controlElem eq dom.document.getElementById(id) =>
        // We have the value and it is for the right control
        value
      case Some(_) =>
        // We have a value but it is for an obsolete control
        remove(id)
        null
    }

  // Remove the value we know for a specific control
  @JSExport
  def remove(id: String): Unit = idToControlValue -= id

  // Purge controls which are no longer in the DOM
  def purgeExpired(): Unit =
    for {
      (id, ControlValue(controlElem, _)) <- idToControlValue.iterator
      if controlElem ne dom.document.getElementById(id)
    } locally {
      remove(id)
    }
}
