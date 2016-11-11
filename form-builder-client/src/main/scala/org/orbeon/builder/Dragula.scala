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
package org.orbeon.builder

import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global ⇒ g}
import scala.scalajs.js.annotation.ScalaJSDefined

object Dragula {
  def apply(initialContainers: js.Array[html.Element], options: DragulaOptions): Drake =
    g.dragula(initialContainers, options).asInstanceOf[Drake]
}

@ScalaJSDefined
trait DragulaOptions extends js.Object {
  def isContainer(el: html.Element)                                                                : Boolean
  def moves  (el: html.Element, source: html.Element, handle: html.Element, sibling: html.Element) : Boolean
  def accepts(el: html.Element, target: html.Element, source: html.Element, sibling: html.Element) : Boolean
  def invalid(el: html.Element, handle: html.Element)                                              : Boolean
  def direction                                                                                    : String
  def copy                                                                                         : Boolean
  def copySortSource                                                                               : Boolean
  def revertOnSpill                                                                                : Boolean
  def removeOnSpill                                                                                : Boolean
  def mirrorContainer                                                                              : html.Element
  def ignoreInputTextSelection                                                                     : Boolean

}

@js.native
trait Drake extends js.Any {
  def on(eventName: String, callback: js.Function): Unit = js.native
  def destroy(): Unit = js.native
}

object Drake {
  implicit class DrakeOps(val drake: Drake) extends AnyVal {
    def onDrag   (callback: (html.Element, html.Element)                             ⇒ Any): Unit = drake.on("drag",    callback)
    def onDragend(callback: (html.Element)                                           ⇒ Any): Unit = drake.on("dragend", callback)
    def onDrop   (callback: (html.Element, html.Element, html.Element, html.Element) ⇒ Any): Unit = drake.on("drag",    callback)
  }
}