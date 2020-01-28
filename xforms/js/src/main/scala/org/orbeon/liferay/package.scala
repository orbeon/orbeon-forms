/**
 * Copyright (C) 2018 Orbeon, Inc.
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
package org.orbeon

import org.scalajs.dom

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

package object liferay {

  @js.native
  trait LiferayWindow extends dom.Window {
    def Liferay: js.UndefOr[Liferay] = js.native
  }

  implicit def windowToLiferayWindow(window: dom.Window): LiferayWindow =
    window.asInstanceOf[LiferayWindow]

  @js.native
  trait Liferay extends js.Object {
    def on(event: String, listener: js.Function): Unit = js.native
    val Session: js.UndefOr[Session] = js.native // `Session` *might* not be present so let's be conservative and use `UndefOr`
  }

  @js.native
  trait Session extends js.Object {
    def extend(): Unit = js.native
  }

  implicit class LiferayOps(private val liferay: Liferay) extends AnyVal {

    def allPortletsReadyF: Future[Unit] = {
      val promise = Promise[Unit]()
      liferay.on("allPortletsReady", () => promise.success(()))
      promise.future
    }

    def extendSession(): Unit =
      liferay.Session.toOption foreach (_.extend())
  }

  object LiferaySupport {
    def extendSession(): Unit =
      dom.window.Liferay.toOption foreach (_.extendSession())
  }
}
