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
import org.orbeon.web.DomSupport.*
import org.orbeon.xforms
import org.orbeon.xforms.EventNames.*
import org.orbeon.xforms.controls.Upload.*
import org.scalajs.dom
import org.scalajs.dom.{FileList, html}
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits.*

import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.scalajs.js.annotation.JSExport


object Upload {

  private val logger: Logger = LoggerFactory.createLogger("org.orbeon.xforms.AjaxClient")

  private val ClickEvent     = s"click"

  logger.debug("init object")

  Page.registerControlConstructor(() => new Upload, (e: html.Element) => e.classList.contains("xforms-upload"))

  // Sequence the sending of a list of files, see:
  // https://github.com/orbeon/orbeon-forms/issues/6167
  def processFileList(fileList: FileList, upload: Upload): Future[List[dom.File]] = {

    val delay = Page.getXFormsFormFromHtmlElemOrThrow(upload.container).configuration.delayBeforeIncrementalRequest.millis

    def processOne(file: dom.File): Future[dom.File] =
      XFormsApp.clientServerChannel.addFile(
        upload,
        file,
        delay
      ).map(_ => file)

    def processRemaining(files: List[dom.File]): Future[List[dom.File]] =
      files match {
        case Nil =>
          Future.successful(Nil)
        case file :: rest =>
          processOne(file).flatMap { file =>
            processRemaining(rest).map(file :: _)
          }
      }

    processRemaining((0 until fileList.length).map(fileList(_)).toList)
  }
}

class Upload {

  self =>

  import Upload.*

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
        cancelAnchor.addEventListener(ClickEvent, self.cancelButtonActivated _)
      }
    }
  }

  // The change event corresponds to a file being selected. This will queue an event to submit files in the
  // background as soon as possible.
  @JSExport
  def change(): Unit = {
    logger.debug("change -> queueing")
    processFileList(getInput.files, self)
  }

  // This method is called when the server sends us a progress update for this upload control. If the upload was
  // interrupted we resume it and otherwise update the progress indicator to reflect the new value we got from the
  // server.
  def progress(state: String, received: Long, expected: Long): Unit =
    state match {
      case "interrupted"                     =>
        XFormsApp.clientServerChannel.cancel(doAbort = true, XXFormsUploadError)
        logger.debug("cancel")
      case _ =>
        findProgressBar foreach { bar =>
          val pctString = Support.computePercentStringToOneDecimal(received, expected)
          logger.debug(s"update progress $pctString%")
          bar.style.width = s"$pctString%"
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
        val pctString = Support.computePercentStringToOneDecimal(1, 1000)
        _.style.width = s"$pctString%" // https://github.com/orbeon/orbeon-forms/issues/6666
      }
  }

  // Clears the upload field by recreating it.
  def clear(): Unit = {
    logger.debug("clear")
    getInput.value = "" // this should now work from IE11 up
  }

  def getAncestorForm: xforms.Form =
    Page.findXFormsFormFromHtmlElem(_container)
      .getOrElse(throw new IllegalStateException)

  def getInput: html.Input =
    findDescendantElem(UploadSelectClass) map
      (_.asInstanceOf[html.Input])        getOrElse
      (throw new IllegalStateException)

  // When users press on the cancel link, we cancel the upload, delegating this to the UploadServer.
  private def cancelButtonActivated(event: dom.Event): Unit = {
    logger.debug("cancel button activated")
    event.preventDefault()
    XFormsApp.clientServerChannel.cancel(doAbort = true, XXFormsUploadCancel)
  }

  private def findDescendantElem(className: String): Option[html.Element] =
    _container.querySelectorOpt(s".$className")

  private def findProgressBar: Option[html.Element] =
    findDescendantElem(s"$UploadProgressClass-bar") map {
      _.querySelectorT(".bar")
    }
}