/**
  * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.xbl

import org.scalajs.dom
import org.scalajs.dom.html.Element

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.annotation.ScalaJSDefined

// Simple facade for Dragula. See https://github.com/bevacqua/dragula.
object Dragula {
  def apply(initialContainers: js.Array[Element], options: DragulaOptions): Drake =
    g.dragula(initialContainers, options).asInstanceOf[Drake]
}

// TODO: Consider moving to trait with `js.UndefOr[Boolean] = js.undefined`, `val`s, see:
// https://www.scala-js.org/news/2016/12/21/announcing-scalajs-0.6.14/
@ScalaJSDefined
abstract class DragulaOptions extends js.Object {
  def isContainer(el: Element)                                                 = false
  def moves  (el: Element, source: Element, handle: Element, sibling: Element) = true
  def accepts(el: Element, target: Element, source: Element, sibling: Element) = true
  def invalid(el: Element, handle: Element)                                    = false
  def direction                                                                = "vertical"
  def copy                                                                     = false
  def copySortSource                                                           = false
  def revertOnSpill                                                            = false
  def removeOnSpill                                                            = false
  def mirrorContainer                                                          = dom.document.body
  def ignoreInputTextSelection                                                 = true
}

@js.native
trait Drake extends js.Any {
  def on(eventName: String, callback: js.Function): Unit = js.native
  def destroy(): Unit = js.native
}

object Drake {
  implicit class DrakeOps(val drake: Drake) extends AnyVal {
    def onDrag   (callback: (Element, Element)                   ⇒ Any): Unit = drake.on("drag",    callback)
    def onDragend(callback: (Element)                            ⇒ Any): Unit = drake.on("dragend", callback)
    def onDrop   (callback: (Element, Element, Element, Element) ⇒ Any): Unit = drake.on("drop",    callback)
  }
}