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
package org.orbeon.xforms.facade

import org.scalajs.dom.html
import org.scalajs.dom.raw.XMLHttpRequest
import org.scalajs.jquery.JQueryCallback

import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.|

@js.native
trait DocumentTrait extends js.Object {

  def getValue(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): js.UndefOr[String] = js.native

  // Set the value of an XForms control
  def setValue(
    controlIdOrElem : String | html.Element,
    newValue        : String | Double | Boolean,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = js.native

  def focus(
    controlIdOrElem : String | html.Element,
    formElem        : js.UndefOr[html.Element] = js.undefined
  ): Unit = js.native

}

@js.native
@JSGlobal("ORBEON.xforms.Document")
object Document extends DocumentTrait

@js.native
trait AjaxServerTrait extends js.Object {
  def handleResponseAjax(o: XMLHttpRequest): Unit           = js.native
  def ajaxResponseReceived                 : JQueryCallback = js.native
}

@js.native
@JSGlobal("ORBEON.xforms.server.AjaxServer")
object AjaxServer extends AjaxServerTrait

object AjaxServerOps {

  implicit class AjaxServerOps(val ajaxServer: AjaxServerTrait) extends AnyVal {
    def ajaxResponseReceivedF: Future[Unit] = {

      val result = Promise[Unit]()

      var callback: js.Function = null

      callback = () ⇒ {
        ajaxServer.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(callback) // because has `removed`
        result.success(())
      }

      ajaxServer.ajaxResponseReceived.add(callback)

      result.future
    }
  }

}
