/**
  * Copyright (C) 2017 Orbeon, Inc.
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
package org.orbeon.xforms

import org.orbeon.xforms.EventNames._
import org.orbeon.xforms.controls.Upload._
import org.orbeon.xforms.facade.{Control, Events, Properties}
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.annotation.ScalaJSDefined

object Upload {

  def log(s: String) = () // println(s"Upload: $s")

  log(s"init object")
  Page.registerControlConstructor(() ⇒ new Upload, (e: html.Element) ⇒ $(e).hasClass("xforms-upload"))
}

// Converted from JavaScript/CoffeeScript so as of 2017-03-09 is still fairly JavaScript-like.
@ScalaJSDefined
class Upload extends Control {

  self ⇒

  import Upload._

  private var yuiProgressBar: ProgressBar = null

  // Creates markup for loading progress indicator element, if necessary
  override def init(container: html.Element): Unit = {

    super.init(container)

    log("init class")

    if (getElementByClassName(UploadProgressClass).isEmpty) {

      // Add markup to the DOM
      // TODO: i18n of "Cancel" link
      val innerHtml = s"""<span class="$UploadProgressClass-bar"></span><a href="#" class="$UploadCancelClass">Cancel</a>"""
      $(s"""<span class="$UploadProgressClass">$innerHtml</span>""").insertAfter(getElementByClassName(UploadSelectClass))

      // Register listener on the cancel link
      val cancelAnchor = getElementByClassName(UploadCancelClass)

      $(cancelAnchor).on("click.orbeon.upload", self.cancel _)
    }
  }

  // The change event corresponds to a file being selected. This will queue an event to submit this file in the
  // background  as soon as possible (pseudo-Ajax request).
  override def change(): Unit = {
    log("change → queueing")
    UploaderClient.uploadEventQueue.add(
      UploadEvent(self.getForm(), self),
      Properties.delayBeforeIncrementalRequest.get(),
      ExecutionWait.MinWait
    )
  }

  // This method is called when the server sends us a progress update for this upload control. If the upload was
  // interrupted we resume it and otherwise update the progress indicator to reflect the new value we got from the
  // server.
  def progress(state: String, received: Int, expected: Int): Unit =
    state match {
      case "interrupted"                    ⇒ UploaderClient.cancel(doAbort = true, XXFormsUploadError); log("cancel")
      case _ if self.yuiProgressBar ne null ⇒ self.yuiProgressBar.set("value", 10 + 110 * received / expected); log(s"update progress ${100 * received / expected}")
      case _ ⇒
    }

  // Called by UploadServer when the upload for this control is finished.
  def uploadDone(): Unit = {

    log("done")

    lazy val ajaxResponseProcessed: js.Function = () ⇒ {
      log("removing listener for ajaxResponseProcessed")
        Events.ajaxResponseProcessedEvent.unsubscribe(ajaxResponseProcessed)
        // If progress indicator is still shown, this means some XForms reset the file name
        // NOTE: This is incorrect, see: https://github.com/orbeon/orbeon-forms/issues/2318
        if ($(self.container).hasClass(StateClassPrefix + "progress"))
          setState("empty") // switch back to the file selector, as we won't get a file name anymore
    }

    // After the file is uploaded, in general at the next Ajax response, we get the file name
    // NOTE: Not (always?) the case, see: https://github.com/orbeon/orbeon-forms/issues/2318
    Events.ajaxResponseProcessedEvent.subscribe(ajaxResponseProcessed)
    log("adding listener for ajaxResponseProcessed")
  }

  // When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
  def cancel(event: js.Object): Unit = {
    log("cancel")
    Event.preventDefault(event)
    UploaderClient.cancel(doAbort = true, XXFormsUploadCancel)
  }

  // Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
  // (file is being uploaded), or "file" (a file has been uploaded).
  def setState(state: String): Unit = {

    log(s"setState $state")

    require(States(state), throw new IllegalArgumentException(s"Invalid state: `$state`"))

    // Switch class
    for (s ← States)
      $(self.container).removeClass(StateClassPrefix + s)

    $(self.container).addClass(StateClassPrefix + state)

    if (state == "progress") {
      // Create or recreate progress bar
      val progressBarSpan = getElementByClassName(s"$UploadProgressClass-bar").get
      progressBarSpan.innerHTML = ""

      self.yuiProgressBar = new ProgressBar(
        new js.Object {
          val width    = 100
          val height   = 10
          val value    = 0
          val minValue = 0
          val maxValue = 110
          val anim     = true
        }
      )

      self.yuiProgressBar.get("anim").duration = Properties.delayBeforeUploadProgressRefresh.get() / 1000 * 1.5
      self.yuiProgressBar.render(progressBarSpan)
      self.yuiProgressBar.set("value", 10)
    }
  }

  // Clears the upload field by recreating it.
  def clear(): Unit = {

    log(s"clear")

    val oldInputElement = getElementByClassName(UploadSelectClass).get.asInstanceOf[html.Input]

    // TODO: Would be good to copy attributes generically.
    val newInputElement =
      $(
        s"""
          |<input
          |  class="${oldInputElement.className}"
          |  id="${oldInputElement.id}"
          |  type="${oldInputElement.`type`}"
          |  name="${oldInputElement.name}"
          |  size="${oldInputElement.size}"
          |  accept="${oldInputElement.accept}"
          |  unselectable="on"
          |>
        """.stripMargin
      )

    $(oldInputElement).replaceWith(newInputElement)
  }
}
