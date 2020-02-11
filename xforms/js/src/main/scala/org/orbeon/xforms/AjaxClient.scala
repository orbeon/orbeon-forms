/**
 * Copyright (C) 2019 Orbeon, Inc.
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

import java.{lang => jl}

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.liferay.LiferaySupport
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.FutureUtils
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.xforms.EventNames.{XXFormsUploadProgress, XXFormsValue}
import org.orbeon.xforms.facade.{AjaxServer, Events, Properties, Utils}
import org.scalajs.dom
import org.scalajs.dom.experimental.AbortController
import org.scalajs.dom.ext._
import org.scalajs.dom.{FormData, html}
import org.scalajs.jquery.{JQueryCallback, JQueryEventObject}

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.{timers, |}
import scala.util.{Failure, Success}


object AjaxClient {

  import Private._

  // Callback with parameters `(ev: AjaxEvent, updateProps: js.Function1[js.Dictionary[js.Any], Unit])`
  lazy val beforeSendingEvent: JQueryCallback = $.Callbacks()

  // Callback with parameters `()`
  lazy val ajaxResponseReceived: JQueryCallback = $.Callbacks()

  // Used by `OrbeonClientTest`
  // TODO: Check if `ajaxResponseProcessedForCurrentEventQueueF` should/could be used instead.
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.ajaxResponseReceivedForTests")
  def ajaxResponseReceivedForTests(): js.Promise[Unit] = {

    val result = Promise[Unit]()

    lazy val callback: js.Function = () => {
      ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(callback) // because has `removed`
      result.success(())
    }

    ajaxResponseReceived.add(callback)

    result.future.toJSPromise
  }

  // TODO: `formId` is not used.
  def ajaxResponseProcessedForCurrentEventQueueF(formId: String): Future[Unit] = {

    val result = Promise[Unit]()

    // When there is a request in progress, we need to wait for the response after the next response processed
    var skipNext = Globals.requestInProgress

    lazy val callback: js.Function = () => {
      if (skipNext) {
        skipNext = false
      } else {
        Events.ajaxResponseProcessedEvent.unsubscribe(callback)
        result.success(())
      }
    }

    Events.ajaxResponseProcessedEvent.subscribe(callback)

    result.future
  }

  // Public for `UploaderClient`
  def handleResponseAjax(responseXML: dom.Document, formId: String, isResponseToBackgroundUpload: Boolean, ignoreErrors: Boolean) {

    if (! isResponseToBackgroundUpload)
        AjaxClient.ajaxResponseReceived.fire()

    // If neither of these two conditions is met, hide the modal progress panel:
    //      a) There is another Ajax request in the queue, which could be the one that triggered the
    //         display of the modal progress panel, so we don't want to hide before that request ran.
    //      b) The server tells us to do a submission or load, so we don't want to remove it otherwise
    //         users could start interacting with a page which is going to be replaced shortly.
    // We remove the modal progress panel before handling DOM response, as script actions may dispatch
    // events and we don't want them to be filtered. If there are server events, we don't remove the
    // panel until they have been processed, i.e. the request sending the server events returns.
    def mustHideProgressDialog: Boolean = {

      // `exists((//xxf:submission, //xxf:load)[empty(@target) and empty(@show-progress)])`
      val serverSaysToKeepModelProgressPanelDisplayed =
        responseXML.getElementsByTagNameNS(Namespaces.XXF, "submission").iterator ++
          responseXML.getElementsByTagNameNS(Namespaces.XXF, "load").iterator exists
          (e => ! e.hasAttribute("target") && ! e.hasAttribute("show-progress"))

      ! (eventQueueHasShowProgressEvent || serverSaysToKeepModelProgressPanelDisplayed)
    }

    if (mustHideProgressDialog)
        Utils.hideModalProgressPanel()

    AjaxServer.handleResponseDom(responseXML, isResponseToBackgroundUpload, formId, ignoreErrors)
    // Reset changes, as changes are included in this bach of events
    Globals.changedIdsRequest = js.Dictionary.empty
    // Notify listeners that we are done processing this request
    Events.ajaxResponseProcessedEvent.fire(formId)
    // Go ahead with next request, if any
    Globals.executeEventFunctionQueued += 1
    executeNextRequest(bypassRequestQueue = false)
  }

  // Unless we get a clear indication from the server that an error occurred, we retry to send the request to
  // the AjaxServer.
  //
  // Browsers behaviors (might be out of date as of 2019-12-09):
  //
  // - On Safari, when o.status == 0, it might not be an error. Instead, it can be happen when users click on a
  //   link to download a file, and Safari stops the current Ajax request before it knows that new page is loaded
  //   (vs. a file being downloaded). With the current core, we assimilate this to a communication error, and
  //   we'll retry to send the Ajax request to the AjaxServer.
  //
  // - On Firefox, when users navigate to another page while an Ajax request is in progress,
  //   we receive an error here, which we don't want to display. We don't have a good way of knowing if we get
  //   this error because there was really a communication failure or if this is because the user is
  //   going to another page. We handle this as a communication failure, and resend the request to the server,
  //   This  doesn't hurt as the server knows that it must not execute the request more than once.
  //
  // - 2015-01-26: Firefox (and others) return a <parsererror> document if the XML doesn't parse. So if there is an
  //   XML Content-Type header but an empty body, for example, we get <parsererror>. See:
  //
  //   https://github.com/orbeon/orbeon-forms/issues/2074
  //
  // Public for `UploaderClient`
  def handleFailure(
    response     : String Either dom.Document,
    formId       : String,
    requestBody  : String | FormData,
    ignoreErrors : Boolean
  ): Boolean = {

    object LoginRegexpMatcher {
      def unapply(s: String): Boolean = {
        val loginRegexp = Properties.loginPageDetectionRegexp.get()
        loginRegexp.nonEmpty && new js.RegExp(loginRegexp).test(s)
      }
    }

    response match {
      case Right(responseXml) if Support.getLocalName(responseXml.documentElement) == "error" =>
        // If we get an error document as follows, we consider this to be a permanent error, we don't retry, and
        // we show an error to users.
        //
        //   <error>
        //       <title>...</title>
        //       <body>...</body>
        //   </error>

        val title = responseXml.getElementsByTagName("title") map (_.textContent) mkString ""
        val body  = responseXml.getElementsByTagName("body")  map (_.textContent) mkString ""

        showError(title, body, formId, ignoreErrors)

        true

      case Left(LoginRegexpMatcher()) =>

         // It seems we got a login page back, so display dialog and reload form
        val dialogEl = $(s"$$$formId .xforms-login-detected-dialog")

        def getUniqueId(prefix: String): String = {
          var i = 0
          var r: String = null
          do {
            r = prefix + i
            i += 1
          } while (dom.document.getElementById(r) ne null)
          r
        }

        // Link dialog with title for ARIA
        val title = dialogEl.find("h4")
        if (title.attr("id").isEmpty) {
            val titleId = getUniqueId("xf-aria-dialog-title-")
            title.attr("id", titleId)
            dialogEl.attr("aria-labelledby", titleId)
        }

        dialogEl.find("button").one("click.xf", ((_: JQueryEventObject) => {
          // Reloading the page will redirect us to the login page if necessary
          dom.window.location.href = dom.window.location.href
        }): js.Function1[JQueryEventObject, js.Any])
        dialogEl.asInstanceOf[js.Dynamic].modal(new js.Object {
          val backdrop = "static" // Click on the background doesn't hide dialog
          val keyboard = false    // Can't use esc to close the dialog
        })

        true

      case _ =>
        false
    }
  }

  // Create a timer which after the specified delay will fire a server event
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.createDelayedServerEvent")
  def createDelayedServerEvent(
    encodedEvent : String,
    delay        : Double, // for JavaScript caller
    showProgress : Boolean,
    discardable  : Boolean,
    formId       : String
  ): Unit = {

    val form = Page.getForm(formId)

    val timerId = timers.setTimeout(delay) {
      fireEvents(
        js.Array(
          new AjaxEvent(
            js.Dictionary[js.Any](
              "form"         -> form.elem,
              "value"        -> encodedEvent,
              "eventName"    -> EventNames.XXFormsServerEvents,
              "showProgress" -> showProgress
            )
          )
        ),
        incremental = false
      )
    }

    if (discardable)
      form.addDiscardableTimerId(timerId)
  }

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.hasEventsToProcess")
  def hasEventsToProcess(): Boolean =
    Globals.requestInProgress || Globals.eventQueue.nonEmpty

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.fireEvents")
  def fireEvents(events: js.Array[AjaxEvent], incremental: Boolean): Unit = {

    // https://github.com/orbeon/orbeon-forms/issues/4023
    LiferaySupport.extendSession()

    // We do not filter events when the modal progress panel is shown.
    //      It is tempting to filter all the events that happen when the modal progress panel is shown.
    //      However, if we do so we would loose the delayed events that become mature when the modal
    //      progress panel is shown. So we either need to make sure that it is not possible for the
    //      browser to generate events while the modal progress panel is up, or we need to filter those
    //      event before this method is called.

    // Store the time of the first event to be sent in the queue
    val currentTime = new js.Date().getTime()
    if (Globals.eventQueue.isEmpty)
      Globals.eventsFirstEventTime = currentTime

    // Store events to fire
    events foreach { event =>
      if (! event.targetIdOpt.contains("")) // Q: Why do we check this? We expect `None` or `Some(targetId)`
        Globals.eventQueue.push(event)
    }

    // Fire them with a delay to give us a change to aggregate events together
    Globals.executeEventFunctionQueued += 1
    if (incremental && !(currentTime - Globals.eventsFirstEventTime > Properties.delayBeforeIncrementalRequest.get())) {
      // After a delay (e.g. 500 ms), run `executeNextRequest()` and send queued events to server
      // if there are no other `executeNextRequest()` that have been added to the queue after this
      // request.
      timers.setTimeout(Properties.delayBeforeIncrementalRequest.get().millis) {
        executeNextRequest(bypassRequestQueue = false)
      }
    } else {
      // After a very short delay (e.g. 20 ms), run `executeNextRequest()` and force queued events
      // to be sent to the server, even if there are other `executeNextRequest()` queued.
      // The small delay is here so we don't send multiple requests to the server when the
      // browser gives us a sequence of events (e.g. focus out, change, focus in).
      timers.setTimeout(Properties.internalShortDelay.get().millis) {
        executeNextRequest(bypassRequestQueue = true)
      }
    }
    // Used by heartbeat
    Globals.lastEventSentTime = new js.Date().getTime()
  }

  // When an exception happens while we communicate with the server, we catch it and show an error in the UI.
  // This is to prevent the UI from becoming totally unusable after an error.
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.logAndShowError")
  def logAndShowError(e: AnyRef, formId: String, ignoreErrors: Boolean): Unit = {

    // Because we catch errors in JavaScript right now in AjaxServer.js, and in JavaScript any object can be thrown,
    // we receive an `AnyRef` here.
    val message = e match {
      case t: Throwable => t.getMessage
      case r => r.toString
    }

    // Q: We used to log the JavaScript exception to the console here. In which cases can we do that? How does it help?
    dom.console.log("JavaScript error", message)

    val sb = new StringBuilder("Exception in client-side code.")

    // We used to log `fileName` and `lineNumber` as well, but those are strictly Firefox features
    Option(message) foreach { m =>
      sb append "<ul><li>Message: "
      sb append m
      sb append "</li></ul>"
    }

    showError("Exception in client-side code", sb.toString, formId, ignoreErrors)
  }

  // Display the error panel and shows the specified detailed message in the detail section of the panel.
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.showError")
  def showError(titleString: String, detailsString: String, formId: String, ignoreErrors: Boolean): Unit = {
    Events.errorEvent.fire(
      new js.Object {
        val title   = titleString
        val details = detailsString
      }
    )
    if (! ignoreErrors && Properties.showErrorDialog.get())
      ErrorPanel.showError(formId, detailsString)
  }

  private object Private {

    val Indent: String = " " * 4

    def debugEventQueue(): Unit =
      println(s"Event queue: ${Globals.eventQueue mkString ", "}")

    // Retry after a certain delay which increases with the number of consecutive failed request, but which never exceeds
    // a maximum delay.
    def retryRequestAfterDelay(requestFunction: () => Unit): Unit = {
      val delay = Math.min(Properties.retryDelayIncrement.get() * (Globals.requestTryCount - 1), Properties.retryMaxDelay.get())
      if (delay == 0)
        requestFunction()
      else
        timers.setTimeout(delay.millis)(requestFunction())
    }

    def eventQueueHasShowProgressEvent(): Boolean =
      Globals.eventQueue exists (_.showProgress)

    def executeNextRequest(bypassRequestQueue: Boolean): Unit = {
      org.scalajs.dom.console.log("executeNextRequest, eventQueue", Globals.eventQueue)

      Globals.executeEventFunctionQueued -= 1

      if (! Globals.requestInProgress && Globals.eventQueue.nonEmpty && (bypassRequestQueue || Globals.executeEventFunctionQueued == 0)) {
        val x = findEventsToProcess
        org.scalajs.dom.console.log("events to process", x.toJSArray)
        x match {
          case Some((currentForm, eventsForCurrentForm, eventsForOtherForms)) =>
            // Remove from this list of ids that changed the id of controls for
            // which we have received the keyup corresponding to the keydown.
            // Use `filter`/`filterNot` which makes a copy so we don't have to worry about deleting keys being iterated upon
            // TODO: check where this is used!
            Globals.changedIdsRequest = (Globals.changedIdsRequest filterNot (_._2 == 0)).dict
            Globals.eventQueue        = eventsForOtherForms.toJSArray
            processEvents(currentForm, eventsForCurrentForm)
          case None =>
            Globals.eventQueue = js.Array()
        }
      }
    }

    def asyncAjaxRequest(requestFormId: String, requestBody: String | FormData, ignoreErrors: Boolean): Unit = {

      Globals.requestTryCount += 1

      // Timeout support using `AbortController`
      val controller = new AbortController
      js.timers.setTimeout(Properties.delayBeforeAjaxTimeout.get().millis) {
        controller.abort()
      }

      FutureUtils.withFutureSideEffects(
        before = {
          Globals.requestInProgress = true
          Page.loadingIndicator.requestStarted()
        },
        after  = {
          Globals.requestInProgress = false
          Page.loadingIndicator.requestEnded()
        }
      ) {
        Support.fetchText(
          url         = Page.getForm(requestFormId).xformsServerPath,
          requestBody = requestBody,
          contentType = "application/xml".some,
          formId      = requestFormId,
          abortSignal = controller.signal.some
        )
      } onComplete {
        case Success((_, _, Some(responseXml))) if Support.getLocalName(responseXml.documentElement) == "event-response" =>
          // We ignore HTTP status and just check that we have a well-formed response document
          handleResponseAjax(responseXml, requestFormId, isResponseToBackgroundUpload = false, ignoreErrors)
        case Success((503, _, _)) =>
          // We return an explicit 503 when the Ajax server is still busy
          retryRequestAfterDelay(() => asyncAjaxRequest(requestFormId, requestBody, ignoreErrors))
        case Success((_, responseText, responseXmlOpt)) =>
          if (! handleFailure(responseXmlOpt.toRight(responseText), requestFormId, requestBody, ignoreErrors))
            retryRequestAfterDelay(() => asyncAjaxRequest(requestFormId, requestBody, ignoreErrors))
        case Failure(_) =>
          logAndShowError(_, requestFormId, ignoreErrors)
          retryRequestAfterDelay(() => asyncAjaxRequest(requestFormId, requestBody, ignoreErrors))
      }
    }

    def findEventsToProcess: Option[(html.Form, NonEmptyList[AjaxEvent], List[AjaxEvent])] = {

      // Ignore events for form that are no longer part of the document
      def eventsForFormsInDocument(events: NonEmptyList[AjaxEvent]): Option[NonEmptyList[AjaxEvent]] =
        NonEmptyList.fromList(events.filter((event) => dom.document.body.contains(event.form))) // IE11 doesn't support `document.contains`

      // Coalesce value events for a given `targetId`, but only between boundaries of other events. We used to do this, more
      // or less, between those boundaries, but also including `XXFormsUploadProgress`, and allowing interleaving of `targetId`
      // within a block. Here, we do something simpler: just find a string of `eventName`/`targetId` that match and keep only
      // the last event of such a string. This should be enough, as there shouldn't be many cases where value events between,
      // say, two controls are interleaved without boundary events in between.
      def coalesceValueEvents(events: NonEmptyList[AjaxEvent]): NonEmptyList[AjaxEvent] = {

        def processBlock(l: NonEmptyList[AjaxEvent]): NonEmptyList[AjaxEvent] = {

          val (single, remaining) =
            l.head.eventName match {
              case eventName @ XXFormsValue =>

                val targetIdOpt            = l.head.targetIdOpt
                val (blockTail, remaining) = l.tail.span(e => e.eventName == eventName && e.targetIdOpt == targetIdOpt)
                val block                  = l.head :: blockTail

                (block.last, remaining)
              case _ =>
                (l.head, l.tail)
            }

          NonEmptyList(single, NonEmptyList.fromList(remaining).toList flatMap (r => processBlock(r).toList))
        }

        processBlock(events)
      }

      // Keep only the last `XXFormsUploadProgress`. This makes sense because as of 2019-11-25, we only handle a
      // single upload at a time.
      def coalescedProgressEvents(events: NonEmptyList[AjaxEvent]): Option[NonEmptyList[AjaxEvent]] =
        events collect { case e if e.eventName == XXFormsUploadProgress => e } lastOption match {
          case Some(lastProgressEvent) =>
            NonEmptyList.fromList(
              events collect {
                case e if e.eventName != XXFormsUploadProgress => e
                case e if e.eventName == XXFormsUploadProgress && (e eq lastProgressEvent) => e
              }
            )
          case None =>
            events.some
        }

      def eventsForOldestEventForm(events: NonEmptyList[AjaxEvent]): (html.Form, NonEmptyList[AjaxEvent], List[AjaxEvent]) = {

        val oldestEvent = events.head
        val currentForm = oldestEvent.form

        val (eventsToSend, eventsForOtherForms) =
          events.toList partition (event => event.form.isSameNode(currentForm))

        (currentForm, NonEmptyList(oldestEvent, eventsToSend.tail), eventsForOtherForms)
      }

      for {
        originalEvents           <- NonEmptyList.fromList(Globals.eventQueue.toList)
        eventsForFormsInDocument <- eventsForFormsInDocument(originalEvents)
        coalescedEvents          <- coalescedProgressEvents(coalesceValueEvents(eventsForFormsInDocument))
      } yield
        eventsForOldestEventForm(coalescedEvents)
    }

    def processEvents(currentForm: html.Form, events: NonEmptyList[AjaxEvent]): Unit = {

      events.toList foreach { event =>

        // 2019-11-22: called by `LabelEditor`
        val updateProps: js.Function1[js.Dictionary[js.Any], Unit] =
          properties => event.properties = properties

        // 2019-11-22: `beforeSendingEvent` is undocumented but used
        beforeSendingEvent.fire(event, updateProps)
      }

      // Only remember the last value for a given `targetId`. Notes:
      //
      // 1. We could also not bother about taking the last one and just call `ServerValueStore.set()` on all of them, since all that
      //    does right now is update a `Map`.
      //
      // 2. We used to do some special handling for `.xforms-upload`. It seems that we can get `XXFormsValue` events for
      //    `.xforms-upload`, but only with an empty string value, when clearing the field. It doesn't seem that we need to handle
      //    those differently in this case.
      //
      // 3. We used to compare the value of the `ServerValueStore` and filter out values if it was the same. It's unclear which
      //    scenario this was covering or if it was correctly implemented. If the only events we have are value changes, then it
      //    might make sense, if the last value is the same as the server value, not to include that event. However, if there are
      //    other events, and/or multiple sequences of value change events separated by boundaries, this becomes less clear. I
      //    think it is more conservative to not do this check unless we can fully determine that the behavior will be correct.
      //
      locally {

        val valueEventsGroupedByTargetId =
          events collect { case e if e.eventName == XXFormsValue => e } groupByKeepOrder (_.targetIdOpt)

        valueEventsGroupedByTargetId foreach {
          case (Some(targetId), events) => ServerValueStore.set(targetId, events.last.properties.get("value").get.asInstanceOf[String])
          case _ =>
        }
      }

      val currentFormId = currentForm.id

      Globals.requestTryCount = 0

      val foundEventOtherThanHeartBeat = events exists (_.eventName != EventNames.XXFormsSessionHeartbeat)
      val showProgress                 = events exists (_.showProgress)

      // Since we are sending a request, throw out all the discardable timers.
      // But only do this if we are not just sending a heartbeat event, which is handled in a more efficient
      // way by the server, skipping the "normal" processing which includes checking if there are
      // any discardable events waiting to be executed.
      if (foundEventOtherThanHeartBeat)
        Page.getForm(currentFormId).clearDiscardableTimerIds()

      // Tell the loading indicator whether to display itself and what the progress message on the next Ajax request
      Page.loadingIndicator.setNextConnectShow(showProgress)

      asyncAjaxRequest(currentFormId, buildXmlRequest(currentFormId, events), ! (events exists (_.ignoreErrors)))
    }

    // NOTE: Later we can switch this to an automatically-generated protocol
    def buildXmlRequest(currentFormId: String, eventsToSend: NonEmptyList[AjaxEvent]): String = {

      val requestDocumentString = new jl.StringBuilder

      def newLine(): Unit = requestDocumentString.append('\n')
      def indent(l: Int): Unit = for (_ <- 0 to l) requestDocumentString.append(Indent)

      // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
      // The "unnecessary" concatenation is done to prevent IntelliJ from wrongly interpreting this
      requestDocumentString.append("""<!DOCTYPE xxf:event-request [<!ENTITY nbsp "&#160;">]>""")
      newLine()

      // Start request
      requestDocumentString.append("""<xxf:event-request xmlns:xxf="http://orbeon.org/oxf/xml/xforms">""")
      newLine()

      // Add form UUID
      indent(1)
      requestDocumentString.append("<xxf:uuid>")
      requestDocumentString.append(StateHandling.getFormUuid(currentFormId))
      requestDocumentString.append("</xxf:uuid>")
      newLine()

      val mustIncludeSequence =
        eventsToSend exists { event =>
          event.eventName != XXFormsUploadProgress && event.eventName != EventNames.XXFormsSessionHeartbeat
        }

      // Still send the element name even if empty as this is what the schema and server-side code expects
      indent(1)
      requestDocumentString.append("<xxf:sequence>")
      if (mustIncludeSequence) {

        val currentSequenceNumber = StateHandling.getSequence(currentFormId)
        requestDocumentString.append(currentSequenceNumber)

        lazy val incrementSequenceNumberCallback: js.Function = () => {
          // Increment sequence number, now that we know the server processed our request
          // If we were to do this after the request was processed, we might fail to increment the sequence
          // if we were unable to process the response (i.e. JS error). Doing this here, before the
          // response is processed, we incur the risk of incrementing the counter while the response is
          // garbage and in fact maybe wasn't even sent back by the server, but by a front-end.
          StateHandling.updateSequence(currentFormId, currentSequenceNumber.toInt + 1)
          AjaxClient.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(incrementSequenceNumberCallback) // because has `removed`
        }

        AjaxClient.ajaxResponseReceived.add(incrementSequenceNumberCallback)
      }
      requestDocumentString.append("</xxf:sequence>")
      newLine()

      // Keep track of the events we have handled, so we can later remove them from the queue

      // Start action
      indent(1)
      requestDocumentString.append("<xxf:action>")
      newLine()

      // Add events
      eventsToSend.toList foreach { event =>

        // Create `<xxf:event>` element
        indent(2)
        requestDocumentString.append("<xxf:event")
        requestDocumentString.append(s""" name="${event.eventName}"""")
        event.targetIdOpt. foreach { targetId =>
          requestDocumentString.append(s""" source-control-id="${Page.deNamespaceIdIfNeeded(currentFormId, targetId)}"""")
        }
        requestDocumentString.append(">")

        if (event.properties.nonEmpty) {
          // Only add properties when we don"t have a value (in the future, the value should be
          // sent in a sub-element, so both a value and properties can be sent for the same event)
          newLine()
          event.properties foreach { case (key, value) =>

            val stringValue = value.toString // support number and boolean

            indent(3)
            requestDocumentString.append(s"""<xxf:property name="${key.escapeXmlForAttribute}">""")
            requestDocumentString.append(stringValue.escapeXmlMinimal.removeInvalidXmlCharacters)
            requestDocumentString.append("</xxf:property>")
            newLine()
          }
          indent(2)
        }
        requestDocumentString.append("</xxf:event>")
        newLine()
      }

      // End action
      indent(1)
      requestDocumentString.append("</xxf:action>")
      newLine()

      // End request
      requestDocumentString.append("</xxf:event-request>")

      requestDocumentString.toString
    }
  }
}
