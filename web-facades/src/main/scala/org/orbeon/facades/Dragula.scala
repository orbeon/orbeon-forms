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
package org.orbeon.facades

import org.scalajs.dom.html
import org.scalajs.dom.html.Element

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}

// Simple facade for Dragula. See https://github.com/bevacqua/dragula.
object Dragula {
  def apply(initialContainers: js.Array[Element], options: DragulaOptions): Drake =
    g.dragula(initialContainers, options).asInstanceOf[Drake]
}

// See: https://www.scala-js.org/news/2016/12/21/announcing-scalajs-0.6.14/
abstract class DragulaOptions extends js.Object {

  val direction                : js.UndefOr[String]       = js.undefined // "vertical"
  val copySortSource           : js.UndefOr[Boolean]      = js.undefined // false
  val revertOnSpill            : js.UndefOr[Boolean]      = js.undefined // false
  val removeOnSpill            : js.UndefOr[Boolean]      = js.undefined // false
  val mirrorContainer          : js.UndefOr[html.Element] = js.undefined // dom.document.body
  val ignoreInputTextSelection : js.UndefOr[Boolean]      = js.undefined // true

  def isContainer (el: Element)                                                    : js.UndefOr[Boolean] = js.undefined // false
  def moves       (el: Element, source: Element, handle: Element, sibling: Element): js.UndefOr[Boolean] = js.undefined // true
  def accepts     (el: Element, target: Element, source: Element, sibling: Element): js.UndefOr[Boolean] = js.undefined // true
  def invalid     (el: Element, handle: Element)                                   : js.UndefOr[Boolean] = js.undefined // false
  def copy        (el: Element, source: Element)                                   : js.UndefOr[Boolean] = js.undefined // false
}

@js.native
trait Drake extends js.Any {
  def on(eventName: String, callback: js.Function): Unit = js.native
  def destroy(): Unit                                    = js.native
  val dragging: Boolean                                  = js.native
}

object Drake {
  implicit class DrakeOps(private val drake: Drake) extends AnyVal {
    def onDrag   (callback: (Element, Element)                   => Any): Unit = drake.on("drag",    callback)
    def onDragend(callback: (Element)                            => Any): Unit = drake.on("dragend", callback)
    def onDrop   (callback: (Element, Element, Element, Element) => Any): Unit = drake.on("drop",    callback)
  }
}