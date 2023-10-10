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
import org.scalajs.dom.experimental.{RequestInfo, RequestInit, Response}

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

// 2020-10-06: Fetch API is not supported by JSDOM out of the box. We use `node-fetch` as
// implementation.
@js.native
@JSImport("node-fetch", JSImport.Namespace)
object NodeFetch extends js.Function2[RequestInfo, RequestInit, js.Promise[Response]] {
  def apply(arg1: RequestInfo, arg2: RequestInit): js.Promise[Response] = js.native
}

//@js.native
//trait DocumentTrait extends js.Object {
//
//  def getValue(
//    controlIdOrElem : String | html.Element,
//    formElem        : js.UndefOr[html.Element] = js.undefined
//  ): js.UndefOr[String] = js.native
//
//  // Set the value of an XForms control
//  def setValue(
//    controlIdOrElem : String | html.Element,
//    newValue        : String | Double | Boolean,
//    formElem        : js.UndefOr[html.Element] = js.undefined
//  ): Unit = js.native
//}

@js.native
trait AjaxServerTrait extends js.Object {
  def allEventsProcessedP(): js.Promise[Unit] = js.native
}