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
import org.orbeon.xforms.facade.{Events, Properties}
import org.scalajs.dom.html
import org.scalajs.dom.html.Element
import org.scalajs.jquery.JQueryEventObject

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport
import scala.concurrent.ExecutionContext.Implicits.global

object Upload {

  private val ListenerSuffix = ".orbeon.upload"
  private val ClickEvent     = s"click$ListenerSuffix"

  scribe.debug("init object")

  Page.registerControlConstructor(() => new Upload, (e: html.Element) => $(e).hasClass("xforms-upload"))
}

// Converted from JavaScript/CoffeeScript so as of 2017-03-09 is still fairly JavaScript-like.
class Upload {

  self =>

  import Upload._

  private var _yuiProgressBar: ProgressBar = null

  private var _container: html.Element = null
  def container: Element = self._container

  // Creates markup for loading progress indicator element, if necessary
  def init(container: html.Element): Unit = {

    self._container = container

    scribe.debug("init class")

    if (findDescendantElem(UploadProgressClass).isEmpty) {

      // Add markup to the DOM
      // TODO: i18n of "Cancel" link
      val innerHtml = s"""<span class="$UploadProgressClass-bar"></span><a href="#" class="$UploadCancelClass">Cancel</a>"""
      $(s"""<span class="$UploadProgressClass">$innerHtml</span>""").insertAfter(findDescendantElem(UploadSelectClass))

      // Register listener on the cancel link
      val cancelAnchor = findDescendantElem(UploadCancelClass)

      $(cancelAnchor).on(ClickEvent, self.cancelButtonActivated _)
    }
  }

  // The change event corresponds to a file being selected. This will queue an event to submit this file in the
  // background  as soon as possible (pseudo-Ajax request).
  @JSExport
  def change(): Unit = {
    scribe.debug("change -> queueing")
    UploaderClient.uploadEventQueue.add(
      UploadEvent(getAncestorForm, self),
      Properties.delayBeforeIncrementalRequest.get().millis,
      ExecutionWait.MinWait
    )
  }

  // This method is called when the server sends us a progress update for this upload control. If the upload was
  // interrupted we resume it and otherwise update the progress indicator to reflect the new value we got from the
  // server.
  // This is called from JavaScript directly in AjaxServer.js.
  @JSExport
  def progress(state: String, received: Int, expected: Int): Unit =
    state match {
      case "interrupted"                     =>
        UploaderClient.cancel(doAbort = true, XXFormsUploadError)
        scribe.debug("cancel")
      case _ if self._yuiProgressBar ne null =>
        self._yuiProgressBar.set("value", 10 + 110 * received / expected)
        scribe.debug(s"update progress ${100 * received / expected}")
      case _ =>
    }

  // Called by UploadServer when the upload for this control is finished.
  def uploadDone(): Unit = {

    scribe.debug("done")

    // After the file is uploaded, in general at the next Ajax response, we get the file name
    // NOTE: Not (always?) the case, see: https://github.com/orbeon/orbeon-forms/issues/2318
    AjaxClient.ajaxResponseProcessedForCurrentEventQueueF foreach { _ =>
      // If progress indicator is still shown, this means some XForms reset the file name
      // NOTE: This is incorrect, see: https://github.com/orbeon/orbeon-forms/issues/2318
      if ($(_container).hasClass(StateClassPrefix + "progress"))
        setState("empty") // switch back to the file selector, as we won't get a file name anymore
    }
  }

  // Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
  // (file is being uploaded), or "file" (a file has been uploaded).
  @JSExport
  def setState(state: String): Unit = {

    scribe.debug(s"setState $state")

    require(States(state), throw new IllegalArgumentException(s"Invalid state: `$state`"))

    // Switch class
    for (s <- States)
      $(_container).removeClass(StateClassPrefix + s)

    $(_container).addClass(StateClassPrefix + state)

    if (state == "progress") {
      // Create or recreate progress bar
      val progressBarSpan = findDescendantElem(s"$UploadProgressClass-bar").get
      progressBarSpan.innerHTML = ""

      self._yuiProgressBar = new ProgressBar(
        new js.Object {
          val width    = 100
          val height   = 10
          val value    = 0
          val minValue = 0
          val maxValue = 110
          val anim     = true
        }
      )

      self._yuiProgressBar.get("anim").duration = Properties.delayBeforeUploadProgressRefresh.get() / 1000 * 1.5
      self._yuiProgressBar.render(progressBarSpan)
      self._yuiProgressBar.set("value", 10)
    }
  }

  // Clears the upload field by recreating it.
  def clear(): Unit = {

    scribe.debug("clear")

    val oldInputElement = findDescendantElem(UploadSelectClass).get.asInstanceOf[html.Input]

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

  // When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
  private def cancelButtonActivated(event: JQueryEventObject): Unit = {
    scribe.debug("cancel button activated")
    event.preventDefault()
    UploaderClient.cancel(doAbort = true, XXFormsUploadCancel)
  }

  private def findDescendantElem(className: String): js.UndefOr[html.Element] =
    $(_container).find(s".$className")(0)

  private def getAncestorForm: html.Form =
    $(_container).parents("form")(0).asInstanceOf[html.Form]
}
