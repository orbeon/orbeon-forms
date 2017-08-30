/**
  * Copyright (C) 2007 Orbeon, Inc.
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
package org.orbeon.fr

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("jsdom", JSImport.Namespace)
object JSDOM extends js.Object {
  def env(config: js.Object) : js.Any    = js.native
  def createVirtualConsole() : js.Object = js.native
  def createCookieJar()      : js.Object = js.native
}
