/**
  * Copyright (C) 2018 Orbeon, Inc.
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

import org.orbeon.xforms.$
import org.scalajs.dom
import org.scalajs.jquery.JQueryEventObject

import scala.scalajs.js
import scala.scalajs.js.Dynamic.global
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}
import dom.experimental.domparser.DOMParser
import dom.experimental.domparser.SupportedType
import org.scalajs.dom.raw.HTMLFormElement

@JSExportTopLevel("ORBEON.fr.private.API")
@JSExportAll
object FormRunnerPrivateAPI {

  private val ListenerSuffix = ".orbeon-beforeunload"
  private val ListenerEvents = s"beforeunload$ListenerSuffix"

  // 2018-05-07: Some browsers, including Firefox and Chrome, no longer use the message provided here.
  private val Message = "You may lose some unsaved changes."

  def setDataStatus(safe: Boolean): Unit =
    if (safe)
      $(global).off(
        ListenerEvents
      )
    else
      $(global).on(
        ListenerEvents,
        ((_: JQueryEventObject) ⇒ Message): js.Function
      )

  def submitLogin(
    username: String,
    password: String
  ): Unit = {

    val formElem =
      <form action="/orbeon/j_security_check" method="post">
        <input type="hidden" name="j_username" value={username}/>
        <input type="hidden" name="j_password" value={password}/>
      </form>

    val domParser    = new DOMParser
    val formDocument = domParser.parseFromString(formElem.toString, SupportedType.`text/html`)
    val formElement  = formDocument.querySelector("form").asInstanceOf[HTMLFormElement]
    dom.document.body.appendChild(formElement)
    formElement.submit()
  }
}
