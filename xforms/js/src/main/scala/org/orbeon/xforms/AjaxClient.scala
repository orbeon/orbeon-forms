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

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.liferay.LiferaySupport
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.CoreUtils._
import org.orbeon.xforms.EventNames.{XXFormsUploadProgress, XXFormsValue}
import org.orbeon.xforms.facade.{AjaxServer, Events, Properties}
import org.orbeon.xforms.rpc.LightClientServerChannel
import org.scalajs.dom
import org.scalajs.dom.ext._
import org.scalajs.dom.html
import org.scalajs.jquery.JQueryEventObject

import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel
import scala.scalajs.js.timers


object AjaxClient {

  import Private._

  class AjaxResponseDetails(
    val responseXML : dom.Document,
    val formId      : String
  )

  val beforeSendingEvent    = new CallbackList[(AjaxEvent, js.Function1[js.Dictionary[js.Any], Unit])]()
  val ajaxResponseReceived  = new CallbackList[AjaxResponseDetails]()
  val ajaxResponseProcessed = new CallbackList[AjaxResponseDetails]()

  // Used by `OrbeonClientTest`
  // This uses a JavaScript `Promise` as the API is used across Scala.js compilation contexts and Scala
  // classes cannot go through that boundary.
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.allEventsProcessedP")
  def allEventsProcessedP(): js.Promise[Unit] =
    allEventsProcessedF("response processed as `js.Promise`").map(_ => ()).toJSPromise

  // 2020-05-05: Used by dialog centering only.
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.currentAjaxResponseProcessedOrImmediatelyP")
  def currentAjaxResponseProcessedOrImmediatelyP(): js.Promise[Unit] =
    if (EventQueue.ajaxRequestInProgress)
      callbackF(ajaxResponseProcessed, forCurrentEventQueue = false, "current response processed as `js.Promise`").map(_ => ()).toJSPromise
    else
      Future.unit.toJSPromise

  def allEventsProcessedF(debugName: String): Future[Unit] =
    if (EventQueue.ajaxRequestInProgress || ! EventQueue.isEmpty)
      callbackF(ajaxResponseProcessed, forCurrentEventQueue = ! EventQueue.isEmpty, debugName).map(_ => ())
    else
      Future.unit

  // 2020-04-28: Only used by legacy autocomplete
  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.addAjaxResponseProcessed")
  def addAjaxResponseProcessed(fn: js.Function0[js.Any]): Unit =
    ajaxResponseProcessed.add(_ => fn.apply())

  // FIXME: This is not done per form. There used to be a `formId` but it was unused.
  def ajaxResponseReceivedForCurrentEventQueueF(debugName: String): Future[AjaxResponseDetails] =
    callbackF(ajaxResponseReceived, forCurrentEventQueue = true, debugName)

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
        // This will cause a retry for event requests (but not uploads)
        false
    }
  }

  // Create a timer which after the specified delay will fire a server event
  // 2020-07-21: Only for upload response
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
      fireEvent(
        new AjaxEvent(
          js.Dictionary[js.Any](
            "form"         -> form.elem,
            "value"        -> encodedEvent,
            "eventName"    -> EventNames.XXFormsServerEvents,
            "showProgress" -> showProgress
          )
        )
      )
    }

    if (discardable)
      form.addDiscardableTimerId(timerId)
  }

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.createDelayedPollEvent")
  def createDelayedPollEvent(
    delay  : js.UndefOr[Double],
    formId : String
  ): Unit =
    timers.setTimeout(delay.getOrElse(0.0)) {
      fireEvent(
        AjaxEvent(
          eventName = EventNames.XXFormsPoll,
          targetId  = Constants.DocumentId,
          form      = Page.getForm(formId).elem.some
        )
      )
    }

  def hasShowProgressEvent: Boolean =
    EventQueue.eventsReversed exists (_.showProgress)

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.fireEvent")
  def fireEvent(event: AjaxEvent): Unit = {

    // - `event.incremental == true` for `focus` and `keyup` event processing only.
    // - We do not filter events when the modal progress panel is shown.
    //   It is tempting to filter all the events that happen when the modal progress panel is shown.
    //   However, if we do so we would loose the delayed events that become mature when the modal
    //   progress panel is shown. So we either need to make sure that it is not possible for the
    //   browser to generate events while the modal progress panel is up, or we need to filter those
    //   event before this method is called.

    // https://github.com/orbeon/orbeon-forms/issues/4023
    LiferaySupport.extendSession()
    EventQueue.addEventAndUpdateQueueSchedule(event, event.incremental)
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
        val title  : String = titleString
        val details: String = detailsString
      }
    )
    if (! ignoreErrors && Properties.showErrorDialog.get())
      ErrorPanel.showError(formId, detailsString)
  }

  // Sending a heartbeat event if no event has been sent to server in the last time interval
  // determined by the `session-heartbeat-delay` property.
  def sendHeartBeatIfNeeded(heartBeatDelay: Int): Unit =
    if ((System.currentTimeMillis() - EventQueue.newestEventTime) >= heartBeatDelay)
      AjaxClient.fireEvent(
        AjaxEvent(
          eventName = EventNames.XXFormsSessionHeartbeat,
          form      = Support.getFirstForm
        )
      )

  private object EventQueue extends AjaxEventQueue[AjaxEvent] {

    def eventsReady(eventsReversed: NonEmptyList[AjaxEvent]): Unit =
      findEventsToProcess(eventsReversed) foreach {
        case (currentForm, eventsForCurrentForm, _) =>
          processEvents(currentForm, eventsForCurrentForm.reverse)
      }

    def canSendEvents: Boolean = ! EventQueue.ajaxRequestInProgress

    val shortDelay                                  : FiniteDuration          = Properties.internalShortDelay.get().toInt.millis
    val incrementalDelay                            : FiniteDuration          = Properties.delayBeforeIncrementalRequest.get().millis

    var ajaxRequestInProgress : Boolean = false              // actual Ajax request has started and not yet successfully completed including response processing
  }

  private object Private {

    def callbackF[T](cb: CallbackList[T], forCurrentEventQueue: Boolean, debugName: String): Future[T] = {

      scribe.debug(s"creating callback future for `$debugName`")

      val result = Promise[T]()

      // When there is a request in progress, we need to wait for the response after the next response processed
      var skipNext = forCurrentEventQueue && EventQueue.ajaxRequestInProgress

      lazy val callback: T => Unit =
        (v: T) => {
          if (skipNext) {
            scribe.debug(s"skipping callback future until next for `$debugName`")
            skipNext = false
          } else {
            cb.remove(callback)
            scribe.debug(s"completing callback future for `$debugName`")
            result.success(v)
          }
        }

      cb.add(callback)

      result.future
    }

    def handleResponse(
      responseXML        : dom.Document,
      formId             : String,
      requestSequenceOpt : Option[Int],
      showProgress       : Boolean,
      ignoreErrors       : Boolean
    ): Unit = {

      // This is a little tricky. Some code registers callbacks or `Future`s for the Ajax response received.
      // However, scheduling futures in the JavaScript contact is done via a global queue. We want to make sure
      // that those callbacks or `Future`s run *before* we process the response, otherwise it's pointless. So
      // we schedule processing the response as a `Future` as well, with the guarantee that it will run last
      // since the execution context is an ordered queue. It would be nice if there was a cleaner, less
      // error-prone way of doing this!

      callbackF(ajaxResponseReceived, forCurrentEventQueue = false, "handleResponseDom") foreach { details =>

        requestSequenceOpt foreach { requestSequence =>
          StateHandling.updateSequence(formId, requestSequence + 1)
        }

        scribe.debug("before `handleResponseDom`")
        AjaxServer.handleResponseDom(responseXML, formId, ignoreErrors)
        scribe.debug("after `handleResponseDom`")

        // Reset changes, as changes are included in this batch of events
        AjaxFieldChangeTracker.afterResponseProcessed()
        ServerValueStore.purgeExpired()

        // `require(EventQueue.ajaxRequestInProgress == false)`

        EventQueue.ajaxRequestInProgress = false

        // Notify listeners that we are done processing this request
        ajaxResponseProcessed.fire(details)

        // Schedule next requests as needed
        EventQueue.updateQueueSchedule()
      }

      // And then we fire the callback, which triggers both direct callbacks and `Future`s
      ajaxResponseReceived.fire(new AjaxResponseDetails(responseXML, formId))
    }

    def findEventsToProcess(originalEvents: NonEmptyList[AjaxEvent]): Option[(html.Form, NonEmptyList[AjaxEvent], List[AjaxEvent])] = {

      // Ignore events for form that are no longer part of the document
      def eventsForFormsInDocument(events: NonEmptyList[AjaxEvent]): Option[NonEmptyList[AjaxEvent]] =
        NonEmptyList.fromList(events.filter(event => dom.document.body.contains(event.form))) // IE11 doesn't support `document.contains`

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

                (block.head, remaining)
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
        events find (_.eventName == XXFormsUploadProgress) match {
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

        val oldestEvent = events.last
        val currentForm = oldestEvent.form

        val (eventsToSend, eventsForOtherForms) =
          events.toList partition (event => event.form.isSameNode(currentForm))

        (currentForm, NonEmptyList.ofInitLast(eventsToSend.init, oldestEvent), eventsForOtherForms)
      }

      for {
        eventsForFormsInDocument <- eventsForFormsInDocument(originalEvents)
        coalescedEvents          <- coalescedProgressEvents(coalesceValueEvents(eventsForFormsInDocument))
      } yield
        eventsForOldestEventForm(coalescedEvents)
    }

    def processEvents(currentForm: html.Form, events: NonEmptyList[AjaxEvent]): Unit = {

      val eventsAsList = events.toList

      AjaxFieldChangeTracker.beforeRequestSent(eventsAsList)

      eventsAsList foreach { event =>

        // 2019-11-22: called by `LabelEditor`
        val updateProps: js.Function1[js.Dictionary[js.Any], Unit] =
          properties => event.properties = properties

        beforeSendingEvent.fire((event, updateProps))
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
          case (Some(targetId), events) => ServerValueStore.set(targetId, events.head.properties.get("value").get.asInstanceOf[String])
          case _ =>
        }
      }

      val currentFormId = currentForm.id

      val foundEventOtherThanHeartBeat = events exists (_.eventName != EventNames.XXFormsSessionHeartbeat)
      val showProgress                 = events exists (_.showProgress)

      // Since we are sending a request, throw out all the discardable timers.
      // But only do this if we are not just sending a heartbeat event, which is handled in a more efficient
      // way by the server, skipping the "normal" processing which includes checking if there are
      // any discardable events waiting to be executed.
      if (foundEventOtherThanHeartBeat)
        Page.getForm(currentFormId).clearDiscardableTimerIds()

      // Don't ignore errors if *any* of the events tell us not to ignore errors.
      // (Corollary: We only ignore errors if *all* of the events tell us to ignore errors.)
      val ignoreErrors = events.forall(_.ignoreErrors)

      // This is set here, and cleared only once `handleResponse` completes
      EventQueue.ajaxRequestInProgress = true

      val eventsToSend = events map (_.toWireAjaxEvent)

      val mustIncludeSequence =
        eventsToSend exists { event =>
          ! EventNames.EventsWithoutSequence(event.eventName)
        }

      val sequenceNumberOpt = mustIncludeSequence option StateHandling.getSequence(currentFormId).toInt

      LightClientServerChannel.sendEvents(
        requestFormId     = currentFormId,
        eventsToSend      = eventsToSend,
        sequenceNumberOpt = sequenceNumberOpt,
        showProgress      = showProgress,
        ignoreErrors      = ignoreErrors
      ) foreach { responseXml =>
        handleResponse(
          responseXml,
          currentFormId,
          sequenceNumberOpt,
          showProgress,
          ignoreErrors
        )
      }
    }
  }
}
