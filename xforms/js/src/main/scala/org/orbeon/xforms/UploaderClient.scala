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

import cats.syntax.option._
import org.orbeon.xforms.facade.{AjaxServer, Properties}
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.ext._
import org.scalajs.dom.html

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}

case class UploadEvent(form: html.Form, upload: Upload)

// - Converted from JavaScript/CoffeeScript so as of 2017-03-09 is still fairly JavaScript-like.
// - We should move away from YUI's Connect for Ajax. Other enhancements are listed here:
//   https://github.com/orbeon/orbeon-forms/issues/3150
object UploaderClient {

  import Private._

  // Used by `Upload`
  val uploadEventQueue = new ExecutionQueue[UploadEvent](asyncUploadRequestSetPromise)

  // Once we are done processing the events (either because the uploads have been completed or canceled), handle the
  // remaining events.
  def continueWithRemainingEvents(): Unit = {
    currentEventOpt foreach (_.upload.uploadDone())
    currentEventOpt = None
    currentAbortControllerOpt = None
    if (remainingEvents.isEmpty) {
      executionQueuePromiseOpt.foreach (_.success(()))
      executionQueuePromiseOpt = None
    } else
      asyncUploadRequest(remainingEvents)
  }

  // While there is a file upload going, this method runs at a regular interval and keeps asking the server for
  // the status of the upload. Initially, it is called by `UploadServer` when it sends the file. Then, it is called
  // by the upload control, when it receives a progress update, as we only want to ask for an updated progress after
  // we get an answer from the server.
  def askForProgressUpdate(): Unit =
    // Keep asking for progress update at regular interval until there is no upload in progress
    js.timers.setTimeout(Properties.delayBeforeUploadProgressRefresh.get()) {
      currentEventOpt foreach { processingEvent ⇒
        AjaxServerEvent.dispatchEvent(
          AjaxServerEvent(
            eventName    = EventNames.XXFormsUploadProgress,
            targetId     = processingEvent.upload.container.id,
            showProgress = false
          )
        )
        askForProgressUpdate()
      }
    }

  // Cancel the uploads currently in process. This can be called by the control, which delegates canceling to
  // `UploadServer` as it can't know about other controls being "uploaded" at the same time. Indeed, we can have
  // uploads for multiple files at the same time, and for each one of the them, we want to clear the upload field,
  // and switch back to the empty state so users can again select a file to upload.
  def cancel(doAbort: Boolean, eventName: String): Unit = {

    if (doAbort)
      currentAbortControllerOpt foreach (_.abort())

    currentEventOpt foreach { processingEvent ⇒
      AjaxServerEvent.dispatchEvent(
          AjaxServerEvent(
            eventName = eventName,
            targetId  = processingEvent.upload.container.id
          )
      )
      processingEvent.upload.clear()
      processingEvent.upload.setState("empty")
    }

    continueWithRemainingEvents()
  }

  private object Private {

    // While an upload is in progress:
    var currentEventOpt          : Option[UploadEvent]     = None // event for the field being uploaded
    var remainingEvents          : List[UploadEvent]       = Nil  // events for the fields that are left to be uploaded
    var currentAbortControllerOpt: Option[AbortController] = None

    var executionQueuePromiseOpt : Option[Promise[Unit]] = None // promise to complete when we are done processing all events

    def asyncUploadRequestSetPromise(events: List[UploadEvent]): Future[Unit] = {
      val promise = Promise[Unit]()
      executionQueuePromiseOpt = promise.some
      asyncUploadRequest(events)
      promise.future
    }

    // Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
    // the upload can be done.
    // TODO: use NEL
    def asyncUploadRequest(events: List[UploadEvent]): Unit = {

      val currentEvent = events.head

      currentEventOpt = currentEvent.some
      remainingEvents = events.tail
      val controller = new AbortController
      currentAbortControllerOpt = controller.some

      // Switch the upload to progress state, so users can't change the file and know the upload is in progress
      currentEvent.upload.setState("progress")

      // Tell server we're starting uploads
      AjaxServerEvent.dispatchEvent(
        AjaxServerEvent(
          eventName    = EventNames.XXFormsUploadStart,
          targetId     = currentEvent.upload.container.id,
          showProgress = false
        )
      )

      def isUuidInput (e: html.Input) = e.name == "$uuid"
      def isThisUpload(e: html.Input) = $(e).hasClass(controls.Upload.UploadSelectClass) && $.contains(currentEvent.upload.container, e)

      val uuidInput =
        currentEvent.form.elements.iterator collect { case e: html.Input ⇒ e } filter isUuidInput toList

      val uploadElem =
        currentEvent.form.elements.iterator collect { case e: html.Input ⇒ e } filter isThisUpload toList

      val data = new dom.FormData
      data.append(uuidInput.head.name,   uuidInput.head.value)
      data.append(uploadElem.head.name,  uploadElem.head.files(0))

      val fetchPromise =
        Fetch.fetch(
          Page.getForm(currentEvent.form.id).xformsServerUploadPath,
          new RequestInit {
            var method         : js.UndefOr[HttpMethod]         = HttpMethod.POST
            var body           : js.UndefOr[BodyInit]           = data
            var headers        : js.UndefOr[HeadersInit]        = js.undefined // set automatically via the `FormData`
            var referrer       : js.UndefOr[String]             = js.undefined
            var referrerPolicy : js.UndefOr[ReferrerPolicy]     = js.undefined
            var mode           : js.UndefOr[RequestMode]        = js.undefined
            var credentials    : js.UndefOr[RequestCredentials] = js.undefined
            var cache          : js.UndefOr[RequestCache]       = js.undefined
            var redirect       : js.UndefOr[RequestRedirect]    = RequestRedirect.follow // only one supported with the polyfill
            var integrity      : js.UndefOr[String]             = js.undefined
            var keepalive      : js.UndefOr[Boolean]            = js.undefined
            var signal         : js.UndefOr[AbortSignal]        = controller.signal
            var window         : js.UndefOr[Null]               = null
          }
        )

      val responseF =
        for {
          response ← fetchPromise.toFuture
          text     ← response.text().toFuture
        } yield
          (
            response.status,
            text,
            Support.stringToDom(text)
          )

      askForProgressUpdate()

      // TODO: Determine whether we should call `handleFailureAjax` or `exceptionWhenTalkingToServer` when the `Future` fails.
      // TODO: Check `status`.
      responseF.onComplete {
        case Success((status, responseText, responseXml)) ⇒ // includes 404 or 500 etc.
          if ((responseText ne null) && responseText.startsWith("<xxf:event-response")) {
            // Clear upload field we just uploaded, otherwise subsequent uploads will upload the same data again
            currentEvent.upload.clear()
            // The Ajax response typically contains information about each file (name, size, etc)
            AjaxServer.handleResponseAjax(responseText, responseXml, currentEvent.form.id, isResponseToBackgroundUpload = true)
            // Are we done, or do we still need to handle events for other forms?
            continueWithRemainingEvents()
          }
        case Failure(_) ⇒ // network failure/anything preventing the request from completing
          cancel(doAbort = false, EventNames.XXFormsUploadError)
          AjaxServer.handleFailureAjax(js.undefined, js.undefined, js.undefined, currentEvent.form.id)
      }
    }
  }
}
