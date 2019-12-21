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

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.facade.{AjaxServer, Properties}
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.dom.ext._
import org.scalajs.dom.html

import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.util.{Failure, Success}
import scala.collection.compat._

case class UploadEvent(form: html.Form, upload: Upload)

// - Converted from JavaScript/CoffeeScript so as of 2017-03-09 is still fairly JavaScript-like.
// - Other enhancements are listed here: https://github.com/orbeon/orbeon-forms/issues/3150
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

    NonEmptyList.fromList(remainingEvents) match {
      case Some(nel) =>
        asyncUploadRequest(nel)
      case None =>
        executionQueuePromiseOpt.foreach (_.success(()))
        executionQueuePromiseOpt = None
    }
  }

  // While there is a file upload going, this method runs at a regular interval and keeps asking the server for
  // the status of the upload. Initially, it is called by `UploadServer` when it sends the file. Then, it is called
  // by the upload control, when it receives a progress update, as we only want to ask for an updated progress after
  // we get an answer from the server.
  def askForProgressUpdate(): Unit =
    // Keep asking for progress update at regular interval until there is no upload in progress
    js.timers.setTimeout(Properties.delayBeforeUploadProgressRefresh.get()) {
      currentEventOpt foreach { processingEvent =>
        AjaxClient.fireEvent(
          AjaxEvent(
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

    currentEventOpt foreach { processingEvent =>
      AjaxClient.fireEvent(
          AjaxEvent(
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

    def asyncUploadRequestSetPromise(events: NonEmptyList[UploadEvent]): Future[Unit] = {
      val promise = Promise[Unit]()
      executionQueuePromiseOpt = promise.some
      asyncUploadRequest(events)
      promise.future
    }

    // Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
    // the upload can be done.
    def asyncUploadRequest(events: NonEmptyList[UploadEvent]): Unit = {

      val currentEvent = events.head

      // If we can't extract form data, we don't attempt to do anything. That *might* happen if, between the time the
      // event is queued and the time it is processed, the event is gone from the DOM (repeat iteration removed, wizard
      // page toggled?). But we don't have a formal proof it can happen.

      extractFormData(currentEvent.form, currentEvent.upload) foreach { formData =>

        val requestFormId = currentEvent.form.id

        currentEventOpt = currentEvent.some
        remainingEvents = events.tail

        val controller = new AbortController
        currentAbortControllerOpt = controller.some

        // Switch the upload to progress state, so users can't change the file and know the upload is in progress
        currentEvent.upload.setState("progress")

        // Tell server we're starting uploads
        AjaxClient.fireEvent(
          AjaxEvent(
            eventName    = EventNames.XXFormsUploadStart,
            targetId     = currentEvent.upload.container.id,
            showProgress = false
          )
        )

        val responseF =
          Support.fetchText(
            url         = Page.getForm(requestFormId).xformsServerUploadPath,
            requestBody = formData,
            contentType = None,
            formId      = requestFormId,
            abortSignal = controller.signal.some
          )

        askForProgressUpdate()

        responseF.onComplete {
          case Success((_, _, Some(responseXml))) if Support.getLocalName(responseXml.documentElement) == "event-response" =>
            // Clear upload field we just uploaded, otherwise subsequent uploads will upload the same data again
            currentEvent.upload.clear()
            // The Ajax response only contains "server events"
            AjaxServer.handleResponseDom(responseXml, requestFormId, ignoreErrors = false)
            // Are we done, or do we still need to handle events for other forms?
            continueWithRemainingEvents()
          case Success((_, responseText, responseXmlOpt)) =>
            // Here we can at least get 413, 409, and 500 status codes at least as those are explicity set by the server
            cancel(doAbort = false, EventNames.XXFormsUploadError)
            AjaxClient.handleFailure(responseXmlOpt.toRight(responseText), requestFormId, formData, ignoreErrors = false)
          case Failure(_) =>
            // NOTE: can be an `AbortError` (to verify)
            cancel(doAbort = false, EventNames.XXFormsUploadError)
            AjaxClient.logAndShowError(_, requestFormId, ignoreErrors = false)
        }
      }
    }

    // This is a lot of work just to get two form fields and their values!
    private def extractFormData(form: html.Form, upload: Upload): Option[dom.FormData] = {

      object UuidExtractor {
        def unapply(e: html.Input): Option[(String, String)] =
          e.name == Constants.UuidFieldName option (e.name, e.value)
      }

      object FileExtractor {
        def unapply(e: html.Input): Option[(String, dom.FileList)] =
          (
            $(e).hasClass(controls.Upload.UploadSelectClass) &&
            $.contains(upload.container, e)     &&
            e.files.length > 0
          ) option (e.name, e.files)
      }

      val it =
        form.elements.iterator collect[(String, js.Any)] {
          case UuidExtractor(name, value) => name -> value
          case FileExtractor(name, value) => name -> value(0) // for now take the first file only
        }

      it.to(List) match {
        case l @ List(_, _) => // exactly two items
          val d = new dom.FormData
          l foreach { case (name, value) => d.append(name, value) }
          d.some
        case _ =>
          None
      }
    }
  }
}
