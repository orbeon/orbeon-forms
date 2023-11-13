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
import org.orbeon.xforms
import org.orbeon.xforms.facade.AjaxServer
import org.scalajs.dom
import org.scalajs.dom.experimental._
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import scala.scalajs.js
import scala.util.{Failure, Success}


object UploaderClient {

  import Private._

  def addFile(upload: Upload, file: dom.raw.File, wait: FiniteDuration): Unit =
    uploadEventQueue.add(
      event    = UploadEvent(upload, file),
      wait     = wait,
      waitType = ExecutionWait.MinWait
    )

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

    case class UploadEvent(upload: Upload, file: dom.raw.File)

    // While an upload is in progress:
    var currentEventOpt          : Option[UploadEvent]     = None // event for the field being uploaded
    var remainingEvents          : List[UploadEvent]       = Nil  // events for the fields that are left to be uploaded
    var currentAbortControllerOpt: Option[AbortController] = None

    var executionQueuePromiseOpt : Option[Promise[Unit]] = None // promise to complete when we are done processing all events

    val uploadEventQueue = new ExecutionQueue[UploadEvent](
      (events: NonEmptyList[UploadEvent]) => {
        val promise = Promise[Unit]()
        executionQueuePromiseOpt = promise.some
        asyncUploadRequest(events)
        promise.future
      }
    )

    // Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
    // the upload can be done.
    private def asyncUploadRequest(events: NonEmptyList[UploadEvent]): Unit = {

      val currentEvent = events.head

      val formData = extractFormData(currentEvent)

      val currentForm = currentEvent.upload.getAncestorForm

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
          url         = currentForm.xformsServerUploadPath,
          requestBody = formData,
          contentType = None,
          acceptLang  = Language.getLang().some, // this language can be used for messages returned by a file scanner
          transform   = (content, _) => content,
          abortSignal = controller.signal.some
        )

      askForProgressUpdate(currentForm)

      responseF.onComplete {
        case Success((_, _, Some(responseXml))) if Support.getLocalName(responseXml.documentElement) == "event-response" =>
          // Clear upload field we just uploaded, otherwise subsequent uploads will upload the same data again
          currentEvent.upload.clear()
          // The Ajax response only contains "server events"
          AjaxServer.handleResponseDom(responseXml, currentForm.namespacedFormId, ignoreErrors = false)
          // Are we done, or do we still need to handle events for other forms?
          continueWithRemainingEvents()
        case Success((_, responseText, responseXmlOpt)) =>
          // Here we can at least get 413, 409, and 500 status codes at least as those are explicitly set by the server
          cancel(doAbort = false, EventNames.XXFormsUploadError)
          AjaxClient.handleFailure(responseXmlOpt.toRight(responseText), currentForm.namespacedFormId, ignoreErrors = false)
        case Failure(_) =>
          // NOTE: can be an `AbortError` (to verify)
          cancel(doAbort = false, EventNames.XXFormsUploadError)
          AjaxClient.logAndShowError(_, currentForm.namespacedFormId, ignoreErrors = false)
      }
    }

    private def extractFormData(uploadEvent: UploadEvent): dom.FormData =
      new dom.FormData |!> (
        _.append(
          Constants.UuidFieldName,
          uploadEvent.upload.getAncestorForm.uuid
        )
      ) |!> (
        _.append(
          uploadEvent.upload.getInput.name,
          uploadEvent.file
        )
      )

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
    private def askForProgressUpdate(currentForm: xforms.Form): Unit =
      // Keep asking for progress update at regular interval until there is no upload in progress
      js.timers.setTimeout(currentForm.configuration.delayBeforeUploadProgressRefresh) {
        currentEventOpt foreach { processingEvent =>
          AjaxClient.fireEvent(
            AjaxEvent(
              eventName    = EventNames.XXFormsUploadProgress,
              targetId     = processingEvent.upload.container.id,
              showProgress = false
            )
          )
          askForProgressUpdate(currentForm)
        }
      }
  }
}
