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

import java.{lang ⇒ jl}

import cats.data.NonEmptyList
import cats.syntax.option._
import org.orbeon.oxf.util.CollectionUtils._
import org.orbeon.oxf.util.MarkupUtils._
import org.orbeon.oxf.util.StringUtils._
import org.orbeon.xforms.EventNames.{XXFormsUploadProgress, XXFormsValue, DOMActivate}
import org.orbeon.xforms.facade.{AjaxServer, Globals, Properties}
import org.scalajs.dom
import org.scalajs.dom.html

import scala.scalajs.js
import scala.scalajs.js.JSConverters._
import scala.scalajs.js.annotation.JSExportTopLevel

object AjaxClient {

  private lazy val EventsToFilterOut: Set[String] = Properties.clientEventsFilter.get().splitTo[Set]()
  private val Indent = " " * 4

  @JSExportTopLevel("ORBEON.xforms.server.AjaxServer.executeNextRequest")
  def executeNextRequest(bypassRequestQueue: Boolean): Unit = {

    Globals.executeEventFunctionQueued -= 1

    if (! Globals.requestInProgress && Globals.eventQueue.length > 0 && (bypassRequestQueue || Globals.executeEventFunctionQueued == 0)) {

      // Filter events (typically used for xforms-focus/xxforms-blur events)
      def  filterEvents(events: NonEmptyList[AjaxServerEvent]): Option[NonEmptyList[AjaxServerEvent]] =
        if (EventsToFilterOut.nonEmpty)
          NonEmptyList.fromList(events filterNot (e ⇒ EventsToFilterOut(e.eventName)))
        else
          events.some

      def foundActivatingEvent(events: NonEmptyList[AjaxServerEvent]): Boolean =
        if (Properties.clientEventMode.get() == "deferred")
          hasActivationEventForDeferredLegacy(events.toList)
        else
          true // every event is an activating event

      // Coalesce value events for a given `targetId`, but only between boundaries of other events. We used to do this, more
      // or less, between those boundaries, but also including `XXFormsUploadProgress`, and allowing interleaving of `targetId`
      // within a block. Here, we do something simpler: just find a string of `eventName`/`targetId` that match and keep only
      // the last event of such a string. This should be enough, as there shouldn't be many cases where value events between,
      // say, two controls are interleaved without boundary events in between.
      def coalesceValueEvents(events: NonEmptyList[AjaxServerEvent]): NonEmptyList[AjaxServerEvent] = {

        def processBlock(l: NonEmptyList[AjaxServerEvent]): NonEmptyList[AjaxServerEvent] = {

          val (single, remaining) =
            l.head.eventName match {
              case eventName @ XXFormsValue ⇒

                val targetIdOpt            = l.head.targetIdOpt
                val (blockTail, remaining) = l.tail.span(e ⇒ e.eventName == eventName && e.targetIdOpt == targetIdOpt)
                val block                  = l.head :: blockTail

                (block.last, remaining)
              case _ ⇒
                (l.head, l.tail)
            }

          NonEmptyList(single, NonEmptyList.fromList(remaining).toList flatMap (r ⇒ processBlock(r).toList))
        }

        processBlock(events)
      }

      // Keep only the last `XXFormsUploadProgress`. This makes sense because as of 2019-11-25, we only handle a
      // single upload at a time.
      def coalescedProgressEvents(events: NonEmptyList[AjaxServerEvent]): Option[NonEmptyList[AjaxServerEvent]] =
        events collect { case e if e.eventName == XXFormsUploadProgress ⇒ e } lastOption match {
          case Some(lastProgressEvent) ⇒
            NonEmptyList.fromList(
              events collect {
                case e if e.eventName != XXFormsUploadProgress ⇒ e
                case e if e.eventName == XXFormsUploadProgress && (e eq lastProgressEvent) ⇒ e
              }
            )
          case None ⇒
            events.some
        }

      def eventsForFirstForm(events: NonEmptyList[AjaxServerEvent]): (NonEmptyList[AjaxServerEvent], html.Form, List[AjaxServerEvent]) = {

        val currentForm = events.head.form // process all events for the form associated with the first event

        val (eventsToSend, remainingEvents) =
          events.toList partition (event ⇒ event.form.isSameNode(currentForm))

        (NonEmptyList(events.head, eventsToSend.tail), currentForm, remainingEvents)
      }

      val stuffToProcessOpt =
        for {
          originalEvents  ← NonEmptyList.fromList(Globals.eventQueue.toList)
          filteredEvents  ← filterEvents(originalEvents)
          if foundActivatingEvent(filteredEvents)
          coalescedEvents ← coalescedProgressEvents(coalesceValueEvents(filteredEvents))
        } yield
          eventsForFirstForm(coalescedEvents)

      // Remove from this list of ids that changed the id of controls for
      // which we have received the keyup corresponding to the keydown.
      // Use `filter`/`filterNot` which makes a copy so we don't have to worry about deleting keys being iterated upon
      // TODO: check where this is used!
      Globals.changedIdsRequest = (Globals.changedIdsRequest filterNot (_._2 == 0)).dict

      stuffToProcessOpt match {
        case Some((events, currentForm, remainingEvents)) ⇒

          events.toList foreach { event ⇒

            // 2019-11-22: called by `LabelEditor`
            val updateProps: js.Function1[js.Dictionary[js.Any], Unit] =
              properties ⇒ event.properties = properties

            // 2019-11-22: `beforeSendingEvent` is undocumented but used
            AjaxServer.beforeSendingEvent.fire(event, updateProps)
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
              events collect { case e if e.eventName == XXFormsValue ⇒ e } groupByKeepOrder (_.targetIdOpt)

            valueEventsGroupedByTargetId foreach {
              case (Some(targetId), events) ⇒ ServerValueStore.set(targetId, events.last.properties.get("value").get.asInstanceOf[String])
              case _ ⇒
            }
          }

          val currentFormId = currentForm.id

          Globals.requestIgnoreErrors = ! (events exists (_.ignoreErrors))
          Globals.eventQueue          = remainingEvents.toJSArray
          Globals.requestForm         = currentForm
          Globals.requestInProgress   = true
          Globals.requestDocument     = buildXmlRequest(currentFormId, events)
          Globals.requestTryCount     = 0

          val foundEventOtherThanHeartBeat = events exists (_.eventName != EventNames.XXFormsSessionHeartbeat)
          val showProgress                 = events exists (_.showProgress)

          // Since we are sending a request, throw out all the discardable timers.
          // But only do this if we are not just sending a heartbeat event, which is handled in a more efficient
          // way by the server, skipping the "normal" processing which includes checking if there are
          // any discardable events waiting to be executed.
          if (foundEventOtherThanHeartBeat)
            Page.getForm(currentFormId).clearDiscardableTimerIds()

          // Tell the loading indicator whether to display itself and what the progress message on the next Ajax request
          Page.getForm(currentFormId).loadingIndicator.setNextConnectShow(showProgress)

          AjaxServer.asyncAjaxRequest()
        case None ⇒
          Globals.eventQueue = js.Array()
      }
    }
  }

  // NOTE: Later we can switch this to an automatically-generated protocol
  private def buildXmlRequest(currentFormId: String, eventsToSend: NonEmptyList[AjaxServerEvent]): String = {

    val requestDocumentString = new jl.StringBuilder

    def newLine(): Unit = requestDocumentString.append('\n')
    def indent(l: Int): Unit = for (_ ← 0 to l) requestDocumentString.append(Indent)

    // Add entity declaration for nbsp. We are adding this as this entity is generated by the FCK editor.
    // The "unnecessary" concatenation is done to prevent IntelliJ from wrongly interpreting this
    requestDocumentString.append("""<!DOCTYPE xxf:event-request [<!ENTITY nbsp "&#160;">]>""")
    newLine()

    // Start request
    requestDocumentString.append("""<xxf:event-request xmlns:xxf="http://orbeon.org/oxf/xml/xforms">""");
    newLine()

    // Add form UUID
    indent(1)
    requestDocumentString.append("<xxf:uuid>")
    requestDocumentString.append(StateHandling.getFormUuid(currentFormId))
    requestDocumentString.append("</xxf:uuid>")
    newLine()

    val mustIncludeSequence =
      eventsToSend exists { event ⇒
        event.eventName != XXFormsUploadProgress && event.eventName != EventNames.XXFormsSessionHeartbeat
      }

    // Still send the element name even if empty as this is what the schema and server-side code expects
    indent(1)
    requestDocumentString.append("<xxf:sequence>")
    if (mustIncludeSequence) {

      val currentSequenceNumber = StateHandling.getSequence(currentFormId)
      requestDocumentString.append(currentSequenceNumber)

      lazy val incrementSequenceNumberCallback: js.Function = () ⇒ {
        // Increment sequence number, now that we know the server processed our request
        // If we were to do this after the request was processed, we might fail to increment the sequence
        // if we were unable to process the response (i.e. JS error). Doing this here, before the
        // response is processed, we incur the risk of incrementing the counter while the response is
        // garbage and in fact maybe wasn't even sent back by the server, but by a front-end.
        StateHandling.updateSequence(currentFormId, currentSequenceNumber.toInt + 1)
        AjaxServer.ajaxResponseReceived.asInstanceOf[js.Dynamic].remove(incrementSequenceNumberCallback) // because has `removed`
      }

      AjaxServer.ajaxResponseReceived.add(incrementSequenceNumberCallback)
    }
    requestDocumentString.append("</xxf:sequence>")
    newLine()

    // Keep track of the events we have handled, so we can later remove them from the queue

    // Start action
    indent(1)
    requestDocumentString.append("<xxf:action>")
    newLine()

    // Add events
    eventsToSend.toList foreach { event ⇒

      // Create `<xxf:event>` element
      indent(2)
      requestDocumentString.append("<xxf:event")
      requestDocumentString.append(s""" name="${event.eventName}"""")
      event.targetIdOpt. foreach { targetId ⇒
        requestDocumentString.append(s""" source-control-id="${Page.deNamespaceIdIfNeeded(currentFormId, targetId)}"""")
      }
      requestDocumentString.append(">")

      if (event.properties.nonEmpty) {
        // Only add properties when we don"t have a value (in the future, the value should be
        // sent in a sub-element, so both a value and properties can be sent for the same event)
        newLine()
        event.properties foreach { case (key, value) ⇒

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

  // TODO: Review and simplify this algorithm.
  private def hasActivationEventForDeferredLegacy(events: List[AjaxServerEvent]): Boolean = {

    val ElementType = 1

    // Element with class `xxforms-events-mode-default` which is the parent of a target
    var parentWithDefaultClass: dom.Node = null
    // Set to true when we find a target which is not under and element with the default class
    var foundTargetWithNoParentWithDefaultClass = false

    // Look for events that we need to send to the server when deferred mode is enabled
    for (event ← events) {

      // DOMActivate is considered to be an "activating" event
      if (event.eventName == DOMActivate)
        return true

      // Check if we find a class on the target that tells us this is an activating event
      // Do NOT consider a filtered event as an activating event
      event.targetIdOpt foreach { targetId ⇒
        val target = dom.document.getElementById(targetId)
        if (target == null) {
          // Target is not on the client. For most use cases, assume event should be dispatched right away.
          return true
        } else {
          // Target is on the client

          if ($(target).is(".xxforms-events-mode-default"))
            return true

          // Look for parent with the default class
          var parent = target.parentNode
          var foundParentWithDefaultClass = false
          while (parent ne null) {
            // Found a parent with the default class
            if (parent.nodeType == ElementType && $(parent).is(".xxforms-events-mode-default")) {
              foundParentWithDefaultClass = true
              if (foundTargetWithNoParentWithDefaultClass) {
                // And there is another target which is outside of a parent with a default class
                return true
              }
              if (parentWithDefaultClass eq null) {
                parentWithDefaultClass = parent
              } else if (parentWithDefaultClass != parent) {
                // And there is another target which is under another parent with a default class
                return true
              }
              parent = null // break
            } else {
              parent = parent.parentNode
            }
          }

          // Record the fact
          if (! foundParentWithDefaultClass) {
            foundTargetWithNoParentWithDefaultClass = true
            if (parentWithDefaultClass ne null)
              return true
          }
        }
      }
    }

    false
  }
}
