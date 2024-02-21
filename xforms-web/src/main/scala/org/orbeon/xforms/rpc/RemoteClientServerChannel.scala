/**
 * Copyright (C) 2020 Orbeon, Inc.
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
package org.orbeon.xforms.rpc

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.http.StatusCode.{LoginTimeOut, ServiceUnavailable}
import org.orbeon.oxf.util.ContentTypes
import org.orbeon.xforms
import org.orbeon.xforms.AjaxClient.handleFailure
import org.orbeon.xforms._
import org.scalajs.dom
import org.scalajs.dom.FormData
import org.scalajs.dom.experimental.AbortController
import org.scalajs.macrotaskexecutor.MacrotaskExecutor.Implicits._

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.{timers, |}
import scala.util.{Failure, Success}


object RemoteClientServerChannel extends ClientServerChannel {

  import Private._

  def sendEvents(
    requestForm      : xforms.Form,
    eventsToSend     : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt: Option[Int],
    showProgress     : Boolean,
    ignoreErrors     : Boolean
  ): Future[dom.Document] = {

    requestTryCount = 0

    Page.loadingIndicator().requestStarted(showProgress, requestForm.configuration)

    asyncAjaxRequestWithRetry(
      requestForm,
      AjaxRequest.buildXmlRequest(requestForm, eventsToSend, sequenceNumberOpt),
      showProgress,
      ignoreErrors
    )
  }

  def addFile(
    upload: Upload,
    file  : dom.raw.File,
    wait  : FiniteDuration
  ): Future[Unit] =
    Future.successful(UploaderClient.addFile(upload, file, wait))

  def cancel(
    doAbort  : Boolean,
    eventName: String
  ): Unit =
    UploaderClient.cancel(doAbort, eventName)

  object Private {

    var requestTryCount: Int = 0 // attempts to run the current Ajax request done so far

    def asyncAjaxRequestWithRetry(
      requestForm : xforms.Form,
      requestBody : String | FormData,
      showProgress: Boolean,
      ignoreErrors: Boolean
    ): Future[dom.Document] = {

      requestTryCount += 1

      // Timeout support using `AbortController`
      val controller = new AbortController
      js.timers.setTimeout(requestForm.configuration.delayBeforeAjaxTimeout.millis) {
        controller.abort()
      }

      val promise = Promise[dom.Document]()

      Support.fetchText(
        url         = requestForm.xformsServerPath,
        requestBody = requestBody,
        contentType = ContentTypes.XmlContentType.some,
        acceptLang  = None,
        transform   = requestForm.transform,
        abortSignal = controller.signal.some
      ).onComplete { response =>

        // Ignore response if for a form we don't have anymore on the page
        if (dom.document.body.contains(requestForm.elem)) {
          response match {
            case Success((_, _, Some(responseXml))) if Support.getLocalName(responseXml.documentElement) == "event-response" =>
              // We ignore HTTP status and just check that we have a well-formed response document
              Page.loadingIndicator().requestEnded(showProgress)
              promise.success(responseXml)
            case Success((LoginTimeOut, _, _)) =>
              // https://github.com/orbeon/orbeon-forms/issues/5678
              AjaxClient.showLoginDetectedDialog(requestForm.namespacedFormId)
              Page.loadingIndicator().requestEnded(showProgress)
              // The `Failure` is ignored by the caller of `sendEvents()`
              promise.failure(new Throwable) // TODO: It would be good to return another error type.
            case Success((ServiceUnavailable, _, _)) =>
              // The server returns an explicit 503 when the Ajax server is still busy
              retryRequestAfterDelay(requestForm, () =>
                asyncAjaxRequestWithRetry(requestForm, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                  promise.complete
              )
            case Success((_, responseText, responseXmlOpt)) =>
              // Retry if we DON'T have an explicit error doc or a login
              if (! handleFailure(responseXmlOpt.toRight(responseText), requestForm.namespacedFormId, ignoreErrors)) {
                retryRequestAfterDelay(requestForm, () =>
                  asyncAjaxRequestWithRetry(requestForm, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                    promise.complete
                )
              } else {
                Page.loadingIndicator().requestEnded(showProgress)
                // This was handled by showing a dialog or login
                //   2023-08-21: What does the above comment mean?
                // The `Failure` is ignored by the caller of `sendEvents()`
                promise.failure(new Throwable) // TODO: It would be good to return another error type.
              }
            case Failure(_) =>
              retryRequestAfterDelay(requestForm, () =>
                asyncAjaxRequestWithRetry(requestForm, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                  promise.complete
              )
          }
        } else {
            Page.loadingIndicator().requestEnded(showProgress)
        }
      }

      promise.future
    }

    // Retry after a certain delay which increases with the number of consecutive failed request, but which never exceeds
    // a maximum delay.
    def retryRequestAfterDelay(requestForm: xforms.Form, requestFunction: () => Unit): Unit = {
      val delay = Math.min(requestForm.configuration.retryDelayIncrement * (requestTryCount - 1), requestForm.configuration.retryMaxDelay)
      if (delay == 0)
        requestFunction()
      else
        timers.setTimeout(delay.millis)(requestFunction())
    }
  }
}
