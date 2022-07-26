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

import org.log4s.Logger
import org.orbeon.oxf.util.LoggerFactory
import org.orbeon.xforms.EventNames._
import org.orbeon.xforms.controls.Upload._
import org.orbeon.xforms.facade.Properties
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.duration._
import scala.scalajs.js.annotation.JSExport

object Upload {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.AjaxClient")

  private val ListenerSuffix = ".orbeon.upload"
  private val ClickEvent     = s"click$ListenerSuffix"

  logger.debug("init object")

  Page.registerControlConstructor(() => new Upload, (e: html.Element) => e.classList.contains("xforms-upload"))
}

class Upload {

  self =>

  import Upload._

  private var _container: html.Element = null
  def container: html.Element = self._container

  // Creates markup for loading progress indicator element, if necessary
  def init(container: html.Element): Unit = {

    self._container = container

    logger.debug("init class")

    if (findDescendantElem(UploadProgressClass).isEmpty) {

      // Add markup to the DOM
      // TODO: i18n of "Cancel" link

      val markup =
        s"""
           |<span class="$UploadProgressClass">
           |  <span class="$UploadProgressClass-bar">
           |    <div class="progress progress-striped active">
           |      <div class="bar"></div>
           |    </div>
           |  </span>
           |  <a href="#" class="$UploadCancelClass">Cancel</a>
           |</span>""".stripMargin

      $(markup).insertAfter(getInput)

      // Register listener on the cancel link
      findDescendantElem(UploadCancelClass) foreach { cancelAnchor =>
        $(cancelAnchor).on(ClickEvent, self.cancelButtonActivated _)
      }
    }
  }

  // The change event corresponds to a file being selected. This will queue an event to submit files in the
  // background as soon as possible.
  @JSExport
  def change(): Unit = {
    logger.debug("change -> queueing")
    val files = getInput.files
    for (i <- 0 until files.length) {
      UploaderClient.uploadEventQueue.add(
        event    = UploadEvent(self, files(i)),
        wait     = Properties.delayBeforeIncrementalRequest.get().millis,
        waitType = ExecutionWait.MinWait
      )
    }
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
        logger.debug("cancel")
      case _ =>
        findProgressBar foreach { bar =>
          val pct = 100 * received / expected max 10
          logger.debug(s"update progress $pct%")
          bar.style.width = s"$pct%"
        }
    }

  // Called by UploadServer when the upload for this control is finished.
  def uploadDone(): Unit = {

    logger.debug("done")

    // After the file is uploaded, in general at the next Ajax response, we get the file name
    // NOTE: Not (always?) the case, see: https://github.com/orbeon/orbeon-forms/issues/2318
    AjaxClient.allEventsProcessedF("upload") foreach { _ =>
      // If progress indicator is still shown, this means some XForms reset the file name
      // NOTE: This is incorrect, see: https://github.com/orbeon/orbeon-forms/issues/2318
      if (_container.classList.contains(StateClassPrefix + "progress"))
        setState("empty") // switch back to the file selector, as we won't get a file name anymore
    }
  }

  // Sets the state of the control to either "empty" (no file selected, or upload hasn't started yet), "progress"
  // (file is being uploaded), or "file" (a file has been uploaded).
  @JSExport
  def setState(state: String): Unit = {

    logger.debug(s"setState $state")

    require(States(state), throw new IllegalArgumentException(s"Invalid state: `$state`"))

    // Switch class
    for (s <- States)
      $(_container).removeClass(StateClassPrefix + s)

    $(_container).addClass(StateClassPrefix + state)

    if (state == "progress")
      findProgressBar foreach {
        _.style.width = s"10%"
      }
  }

  // Clears the upload field by recreating it.
  def clear(): Unit = {
    logger.debug("clear")
    getInput.value = "" // this should now work from IE11 up
  }

  def getAncestorForm: html.Form =
    $(_container).parents("form")(0).asInstanceOf[html.Form]

  def getInput: html.Input =
    findDescendantElem(UploadSelectClass) map
      (_.asInstanceOf[html.Input])        getOrElse
      (throw new IllegalStateException)

  // When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
  private def cancelButtonActivated(event: JQueryEventObject): Unit = {
    logger.debug("cancel button activated")
    event.preventDefault()
    UploaderClient.cancel(doAbort = true, XXFormsUploadCancel)
  }

  private def findDescendantElem(className: String): Option[html.Element] =
    Option(_container.querySelector(s".$className")) map (_.asInstanceOf[html.Element])

  private def findProgressBar: Option[html.Element] =
    findDescendantElem(s"$UploadProgressClass-bar") map {
      _.querySelector(".bar").asInstanceOf[html.Element]
    }
}