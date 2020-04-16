/**
  * Copyright (C) 2017 Orbeon, Inc.
  *
  * This program is free software you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation either version
  *  2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.facades

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalajs.dom.raw.HTMLLinkElement

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSGlobal, JSGlobalScope}

@js.native
trait Mousetrap extends js.Object {
  def bind(command: String, callback: js.Function, mode: String = "keydown", action: js.UndefOr[String] = js.undefined): Unit = js.native
}

@js.native
@JSGlobal("Mousetrap")
object Mousetrap extends Mousetrap {
  def apply(elem: html.Element): Mousetrap = js.native
}

@js.native
@JSGlobal("bowser")
object Bowser extends js.Object {
  val msie    : js.UndefOr[Boolean] = js.native
  val msedge  : js.UndefOr[Boolean] = js.native
  val ios     : js.UndefOr[Boolean] = js.native
  val mobile  : js.UndefOr[Boolean] = js.native
  val version : String              = js.native
  val name    : String              = js.native
}

object HTMLFacades {

  implicit class HTMLLinkElementOps(private val link: HTMLLinkElement) extends AnyVal {
    def onloadF: Future[Unit] = {
      val promise = Promise[Unit]()
      link.asInstanceOf[js.Dynamic].onload = () => promise.success(())
      promise.future
    }
  }
}

@JSGlobalScope
@js.native
private object ResizeObserverGlobalScope extends js.Object {
  val ResizeObserver: js.UndefOr[js.Any] = js.native
}

object ResizeObserver {
  def isDefined: Boolean = ResizeObserverGlobalScope.ResizeObserver.isDefined
}

@js.native
@JSGlobal
class ResizeObserver(observer: js.Function0[Unit]) extends js.Object {
  def observe  (element: dom.Element): Unit = js.native
  def unobserve(element: dom.Element): Unit = js.native
}
