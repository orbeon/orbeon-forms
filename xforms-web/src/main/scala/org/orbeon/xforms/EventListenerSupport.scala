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

import org.scalajs.dom
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js


trait EventListenerSupport {

  private var listeners: List[(dom.raw.EventTarget, String, js.Function1[_, _], Boolean)] = Nil
  private var jQueryListeners: List[(dom.raw.EventTarget, String, js.Function1[JQueryEventObject, js.Any])] = Nil

  def addListener[E <: dom.raw.Event](target: dom.raw.EventTarget, name: String, fn: E => Unit, useCapture: Boolean = false): Unit =
    addJsListener(target, name, fn: js.Function1[E, Unit], useCapture)

  def addJsListener[E <: dom.raw.Event](target: dom.raw.EventTarget, name: String, jsFn: js.Function1[E, Unit], useCapture: Boolean = false): Unit = {
    target.addEventListener(name, jsFn, useCapture)
    listeners ::= (target, name, jsFn, useCapture)
  }

  def addJQueryListener[E <: dom.raw.Event](target: dom.raw.EventTarget, name: String, fn: E => Unit): Unit =
    addJsJQueryListener(target, name, e => fn(e.asInstanceOf[E]))

  def addJsJQueryListener(target: dom.raw.EventTarget, name: String, jsFn: js.Function1[JQueryEventObject, js.Any]): Unit = {
    $(target).on(name, null: js.Any, null: js.Any, jsFn)
    jQueryListeners ::= (target, name, jsFn)
  }

  def clearAllListeners(): Unit = {
    listeners foreach { case (target, name, jsFn, useCapture) => target.removeEventListener(name, jsFn, useCapture) }
    listeners = Nil

    jQueryListeners foreach { case (target, name, jsFn) => $(target).off(name, null: js.Any, jsFn) }
    jQueryListeners = Nil
  }
}

// This must be used only for listeners that are actually global, that is shared between forms
object GlobalEventListenerSupport extends EventListenerSupport
