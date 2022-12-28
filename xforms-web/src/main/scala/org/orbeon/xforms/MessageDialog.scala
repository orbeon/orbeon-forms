/**
 * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.xforms.facade.Utils
import org.scalajs.dom.raw

import scala.collection.mutable
import org.scalajs.dom.document

import scala.scalajs.js
import scala.scalajs.js.Dynamic.{global => g}
import scala.scalajs.js.Dynamic.{newInstance => jsnew}
import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}


@JSExportTopLevel("OrbeonMessage")
@JSExportAll
object MessageDialog {

  private val messageQueue                            = new mutable.Queue[String]
  private var yuiMessageDialogOpt: Option[js.Dynamic] = None

  // Called on page load with list of messages to show
  def showMessages(messages: js.Array[String]): Unit = {
    messageQueue ++= messages
    yuiMessageDialog()
    g.YAHOO.util.Event.onAvailable("xforms-message-dialog", showMessage _)
  }

  // Called when handling Ajax response, with the `<xxf:message>` from the Ajax response
  def execute(element: raw.Element): Unit = {
    if (element.getAttribute("level") == "modal") {
      val message = element.textContent
      messageQueue.enqueue(message)
      if (messageQueue.length == 1)
        showMessage()
    }
  }

  // Initializes the YUI dialog if it hasn't already been done, and returns it
  private def yuiMessageDialog(): js.Dynamic = {
    yuiMessageDialogOpt.getOrElse {

      // Prevent SimpleDialog from registering itself on the form
      val noop: js.Function = () => ()
      g.YAHOO.widget.SimpleDialog.prototype.registerForm = noop

      // Create one single instance of the YUI dialog used for xf:message
      val messageDialog = jsnew(g.YAHOO.widget.SimpleDialog)("xforms-message-dialog", new js.Object {
        val width               = "30em"
        val fixedcenter         = true
        val constraintoviewport = true
        val modal               = true
        val close               = false
        val visible             = false
        val draggable           = false
        val usearia             = true
        val role                = "" // See bug 315634 http://goo.gl/54vzd
        val buttons             = js.Array(
          new js.Object {
            val text = "Close"
            val isDefault = false
            val handler = new js.Object {
              val fn: js.Function = onClose _
            }
          }
        )
      })

      messageDialog.setHeader("Message")
      messageDialog.render(document.body)
      Utils.overlayUseDisplayHidden(messageDialog)

      val dialogElem = document.getElementById("xforms-message-dialog")
      dialogElem.setAttribute("aria-live", "polite") // so JAWS reads the content of the dialog (otherwise it just reads the button)
      dialogElem.classList.add("xforms-message-dialog")

      yuiMessageDialogOpt = Some(messageDialog)
      messageDialog
    }
  }

  // Called when users press the "close" button in the dialog
  private def onClose() = {
    yuiMessageDialog().hide()
    messageQueue.dequeue()
    if (messageQueue.nonEmpty)
      showMessage()
  }

  // Show the next message in the queue
  private def showMessage() = {
    // Create a span, otherwise setBody() assume the parameters is HTML, while we want it to be text
    val span = document.createElement("span")
    span.textContent = messageQueue.head
    yuiMessageDialog().setBody(span)
    yuiMessageDialog().show()
  }
}
