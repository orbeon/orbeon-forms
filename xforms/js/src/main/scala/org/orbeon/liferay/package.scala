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

import scala.scalajs.js

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
  }
}
