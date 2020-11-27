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
import org.orbeon.xforms.AjaxClient.{handleFailure, logAndShowError}
import org.orbeon.xforms.facade.Properties
import org.orbeon.xforms.{AjaxRequest, Page, Support}
import org.scalajs.dom
import org.scalajs.dom.FormData
import org.scalajs.dom.experimental.AbortController

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.{timers, |}
import scala.util.{Failure, Success}


object RemoteClientServerChannel extends ClientServerChannel[dom.Document] {

  import Private._

  def sendEvents(
    requestFormId     : String,
    eventsToSend      : NonEmptyList[WireAjaxEvent],
    sequenceNumberOpt : Option[Int],
    showProgress      : Boolean,
    ignoreErrors      : Boolean
  ): Future[dom.Document] = {

    requestTryCount = 0

  Page.loadingIndicator().requestStarted(showProgress)
    asyncAjaxRequestWithRetry(
      requestFormId,
      AjaxRequest.buildXmlRequest(requestFormId, eventsToSend, sequenceNumberOpt),
      showProgress,
      ignoreErrors
    )
  }

  object Private {

    var requestTryCount: Int = 0 // attempts to run the current Ajax request done so far

    def asyncAjaxRequestWithRetry(
        requestFormId : String,
        requestBody   : String | FormData,
        showProgress  : Boolean,
        ignoreErrors  : Boolean
      ): Future[dom.Document] = {

        val requestForm = Page.getForm(requestFormId)
        requestTryCount += 1

        // Timeout support using `AbortController`
        val controller = new AbortController
        js.timers.setTimeout(Properties.delayBeforeAjaxTimeout.get().millis) {
          controller.abort()
        }

        val promise = Promise[dom.Document]()

        Support.fetchText(
          url         = requestForm.xformsServerPath,
          requestBody = requestBody,
          contentType = "application/xml".some,
          formId      = requestFormId,
          abortSignal = controller.signal.some
        ).onComplete { response =>

          // Ignore response if for a form we don't have anymore on the page
          if (dom.document.body.contains(requestForm.elem)) {
            response match {
              case Success((_, _, Some(responseXml))) if Support.getLocalName(responseXml.documentElement) == "event-response" =>
                // We ignore HTTP status and just check that we have a well-formed response document
                Page.loadingIndicator().requestEnded(showProgress)
                promise.success(responseXml)
              case Success((503, _, _)) =>
                // The server returns an explicit 503 when the Ajax server is still busy
                retryRequestAfterDelay(() =>
                  asyncAjaxRequestWithRetry(requestFormId, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                    promise.complete
                )
              case Success((_, responseText, responseXmlOpt)) =>
                // Retry if we DON'T have an explicit error doc or a login
                if (! handleFailure(responseXmlOpt.toRight(responseText), requestFormId, ignoreErrors))
                  retryRequestAfterDelay(() =>
                    asyncAjaxRequestWithRetry(requestFormId, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                      promise.complete
                  )
                else {
                  Page.loadingIndicator().requestEnded(showProgress)
                  // This was handled by showing a dialog or login
                  promise.failure(new Throwable) // TODO: It would be good to return another error type.
                }
              case Failure(_) =>
                logAndShowError(_, requestFormId, ignoreErrors)
                retryRequestAfterDelay(() =>
                  asyncAjaxRequestWithRetry(requestFormId, requestBody, showProgress, ignoreErrors = ignoreErrors) onComplete
                    promise.complete
                )
            }
          }
        }

        promise.future
      }

    // Retry after a certain delay which increases with the number of consecutive failed request, but which never exceeds
    // a maximum delay.
    def retryRequestAfterDelay(requestFunction: () => Unit): Unit = {
      val delay = Math.min(Properties.retryDelayIncrement.get() * (requestTryCount - 1), Properties.retryMaxDelay.get())
      if (delay == 0)
        requestFunction()
      else
        timers.setTimeout(delay.millis)(requestFunction())
    }
  }
}
