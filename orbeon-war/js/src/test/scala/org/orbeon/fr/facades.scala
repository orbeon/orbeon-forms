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

import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.UndefOr
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("jsdom", "JSDOM")
class JSDOM(html: String, option: js.Object) extends js.Object {
  val window        : dom.Window     = js.native
  val virtualConsole: VirtualConsole = js.native
  val cookieJar     : CookieJar      = js.native
  def serialize     : String         = js.native
}

@js.native
@JSImport("jsdom", "JSDOM")
object JSDOM extends js.Object {
  def fromURL(url: String, options: js.Object): js.Promise[JSDOM] = js.native
}

@js.native
@JSImport("jsdom", "CookieJar")
class CookieJar extends js.Object {
  def setCookieSync(cookie: String, currentUrl: String, options: js.UndefOr[js.Object] = js.undefined): Unit = js.native
  def getCookiesSync(currentUrl: String, options: js.UndefOr[js.Object] = js.undefined): js.Array[Cookie] = js.native
}

@js.native
@JSImport("jsdom", "Cookie")
class Cookie extends js.Object

@js.native
@JSImport("jsdom", "VirtualConsole")
class VirtualConsole extends js.Object {
  def sendTo(anyConsole: js.Object, options: UndefOr[Nothing] = js.undefined): VirtualConsole = js.native
}