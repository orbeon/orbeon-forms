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

import org.scalajs.dom.html
import org.scalajs.dom.raw.XMLHttpRequest

import scala.concurrent.{Future, Promise}
import scala.scalajs.js

case class UploadEvent(form: html.Form, upload: Upload)

// NOTES:
//
// - This got converted from JavaScript/CoffeeScript.
// - There is no retry, see [#315398](https://goo.gl/pPByq).
// - There is a bit too much state to our taste.
object UploadServer {

  val uploadEventQueue = new ExecutionQueue(asyncUploadRequestSetPromise)

  // While an upload is in progress:
  var currentEventOpt          : Option[UploadEvent]   = None // event for the field being uploaded
  var remainingEvents          : List[UploadEvent]     = Nil  // events for the fields that are left to be uploaded
  var yuiConnectionOpt         : Option[js.Object]     = None // connection object

  var executionQueuePromiseOpt : Option[Promise[Unit]] = None // promise to complete when we are done processing all events

  // Method called by YUI when the upload ends successfully.
  private def uploadSuccess(xhr: XMLHttpRequest) = {

    // uploadSuccess can be called:
    // - on all browsers when the file is too large, and the server returns an error HTML page, instead of a
    //   xxf:event-response wrapped in an <html><body>
    // - on IE 7 and IE 8 in IE 7 mode when the upload is interrupted, say because of a connection issue (in that
    //   case o.responseText is undefined or null)
    // In both cases, we don't want to process the body as an Ajax response, as this could lead to errors.

    (xhr.responseText: js.UndefOr[String]).toOption match {
      case Some(text) if (text ne null) && text.startsWith("&lt;xxf:event-response") ⇒
        // Clear upload field we just uploaded, otherwise subsequent uploads will upload the same data again
        currentEventOpt foreach (_.upload.clear())
        // The Ajax response typically contains information about each file (name, size, etc)
        AjaxServer.handleResponseAjax(xhr)
        // Are we done, or do we still need to handle events for other forms?
        continueWithRemainingEvents()
      case _ ⇒
        cancel(doAbort = false, EventNames.XXFormsUploadError)
    }
  }

  /**
   * Once we are done processing the events (either because the uploads have been completed or canceled), handle the
   * remaining events.
   */
  def continueWithRemainingEvents(): Unit = {
    currentEventOpt foreach (_.upload.uploadDone())
    yuiConnectionOpt = None
    currentEventOpt = None
    if (remainingEvents.isEmpty) {
      executionQueuePromiseOpt.foreach (_.success(()))
      executionQueuePromiseOpt = None
    } else
      asyncUploadRequest(remainingEvents)
  }

  private def asyncUploadRequestSetPromise(events: List[UploadEvent]): Future[Unit] = {
    val promise = Promise[Unit]()
    executionQueuePromiseOpt = Some(promise)
    asyncUploadRequest(events)
    promise.future
  }

  /**
   * Run background form post do to the upload. This method is called by the ExecutionQueue when it determines that
   * the upload can be done.
   */
  private def asyncUploadRequest(events: List[UploadEvent]): Unit = events match {

    case Nil ⇒ throw new IllegalArgumentException
    case currentEvent :: tail ⇒

      currentEventOpt = Some(currentEvent)
      remainingEvents = tail

      // Switch the upload to progress state, so users can't change the file and know the upload is in progress
      currentEvent.upload.setState("progress")

      // Tell server we're starting uploads
      AjaxServer.fireEvents(
        js.Array(
          new AjaxServer.Event(
            new js.Object {
              val targetId     = currentEvent.upload.container.id
              val eventName    = EventNames.XXFormsUploadStart
              val showProgress = false
            }
          )
        ),
        incremental = false
      )

      import org.scalajs.dom.ext._

      val newlyDisabledElems = {

        def isUuidInput (e: html.Input) = e.name == "$uuid"                   // `id` might be prefixed when embedding
        def isFieldset  (e: html.Input) = e.tagName.toLowerCase == "fieldset" // disabling fieldsets disables nested controls
        def isThisUpload(e: html.Input) = $(e).hasClass(controls.Upload.UploadSelectClass) && $.contains(currentEvent.upload.container, e)

        currentEvent.form.elements.iterator collect {
          case e: html.Input ⇒ e
        } filterNot { e ⇒
          e.disabled.toOption.contains(true) || isUuidInput(e) || isFieldset(e) || isThisUpload(e)
        } toList
      }

      newlyDisabledElems foreach (_.disabled = true)

      // Trigger actual upload through a form POST and start asking server for progress
      YUIConnect.setForm(currentEvent.form, isUpload = true, secureUri = true)

      yuiConnectionOpt =
        Some(
          YUIConnect.asyncRequest(
            "POST",
            Globals.xformsServerUploadURL(currentEvent.form.id),
            new YUICallback {
              val upload: js.Function = uploadSuccess _
              // Failure isn't called; instead we detect if an upload is interrupted through
              // `progress-state="interrupted"` in the Ajax response.
              val failure: js.Function = () ⇒ ()
              val argument = new js.Object {
                val isUpload = true
                val formId   = currentEvent.form.id
              }
            }
          )
        )

      askForProgressUpdate()

      newlyDisabledElems foreach (_.disabled = false)
  }

  // While there is a file upload going, this method runs at a regular interval and keeps asking the server for
  // the status of the upload. Initially, it is called by `UploadServer` when it sends the file. Then, it is called
  // by the upload control, when it receives a progress update, as we only want to ask for an updated progress after
  // we get an answer from the server.
  def askForProgressUpdate(): Unit =
    // Keep asking for progress update at regular interval until there is no upload in progress
    js.timers.setTimeout(Properties.delayBeforeUploadProgressRefresh.get()) {
      currentEventOpt foreach { processingEvent ⇒
        AjaxServer.fireEvents(
          js.Array(
            new AjaxServer.Event(
              new js.Object {
                val targetId     = processingEvent.upload.container.id
                val eventName    = EventNames.XXFormsUploadProgress
                val showProgress = false
              }
            )
          ),
          incremental = false
        )
        askForProgressUpdate()
      }
    }

  // Cancel the uploads currently in process. This can be called by the control, which delegates canceling to
  // `UploadServer` as it can't know about other controls being "uploaded" at the same time. Indeed, we can have
  // uploads for multiple files at the same time, and for each one of the them, we want to clear the upload field,
  // and switch back to the empty state so users can again select a file to upload.
  def cancel(doAbort: Boolean, event: String): Unit = {

    if (doAbort)
      yuiConnectionOpt foreach (YUIConnect.abort(_))

    currentEventOpt foreach { processingEvent ⇒

      AjaxServer.fireEvents(
        js.Array(
          new AjaxServer.Event(
            new js.Object {
              val targetId  = processingEvent.upload.container.id
              val eventName = event
            }
          )
        ),
        incremental = false
      )

      processingEvent.upload.clear()
      processingEvent.upload.setState("empty")
    }

    continueWithRemainingEvents()
  }
}
