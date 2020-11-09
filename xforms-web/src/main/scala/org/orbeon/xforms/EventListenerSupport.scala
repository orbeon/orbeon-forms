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

import scala.scalajs.js


trait EventListenerSupport {

  private var listeners: List[(dom.raw.EventTarget, String, js.Function1[_, _])] = Nil

  def addListener[E <: dom.raw.Event](target: dom.raw.EventTarget, name: String, fn: E => Unit): Unit =
    addListener(target, name, fn: js.Function1[E, Unit])

  def addListener[E <: dom.raw.Event](target: dom.raw.EventTarget, name: String, jsFn: js.Function1[E, Unit]): Unit = {
    target.addEventListener(name, jsFn)
    listeners ::= (target, name, jsFn)
  }

  def clearAllListeners(): Unit = {
    listeners foreach { case (target, name, jsFn) => target.removeEventListener(name, jsFn) }
    listeners = Nil
  }
}

object GlobalEventListenerSupport extends EventListenerSupport
