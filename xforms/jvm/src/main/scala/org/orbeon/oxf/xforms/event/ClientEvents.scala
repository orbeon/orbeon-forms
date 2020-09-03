/**
 * Copyright (C) 2011 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.event

import java.{util => ju}

import org.orbeon.dom.io.XMLWriter
import org.orbeon.oxf.externalcontext.ExternalContext
import org.orbeon.oxf.http.SessionExpiredException
import org.orbeon.oxf.logging.LifecycleLogger
import org.orbeon.oxf.util._
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils._
import org.orbeon.oxf.xforms.analysis.controls.RepeatControl
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.event.XFormsEvent._
import org.orbeon.oxf.xforms.event.XFormsEvents._
import org.orbeon.oxf.xforms.event.events._
import org.orbeon.oxf.xforms.upload.UploaderServer
import org.orbeon.oxf.xml._
import org.orbeon.oxf.xml.dom.LocationSAXContentHandler
import org.orbeon.xforms.XFormsNames._
import org.orbeon.xforms.rpc.{WireAjaxEvent, WireAjaxEventWithTarget}
import org.orbeon.xforms.{EventNames, XFormsId}

// Process events sent by the client, including sorting, filtering, and security
object ClientEvents extends Logging with XMLReceiverSupport {

  import Private._

  case class EventsFindings(
    valueChangeControlIdsAndValues : Map[String, String],
    clientFocusControlIdOpt        : Option[Option[String]]
  )

  val EmptyEventsFindings = EventsFindings(Map.empty, None)

  // Entry point called by the server: process a sequence of incoming client events.
  def processEvents(
    doc                      : XFormsContainingDocument,
    allClientAndServerEvents : List[(WireAjaxEventWithTarget, Boolean)]
  ): EventsFindings = {

    def filterEvents(events: List[(WireAjaxEventWithTarget, Boolean)]) = events filter {
      case (e, _) if e.eventName == EventNames.XXFormsAllEventsRequired => false
      case (e, _) if e.eventName eq null =>
        debug("ignoring invalid client event", List("event name" -> e.eventName))(doc.indentedLogger)
        false
      case _ => true
    }

    def combineValueEvents(events: List[(WireAjaxEventWithTarget, Boolean)]): List[XFormsEvent] = events match {
      case Nil                => Nil
      case List((e, trusted)) => safelyCreateAndMapEvent(doc, e, trusted).toList
      case _                  =>

        // Grouping key for value change events
        case class EventGroupingKey(name: String, targetId: String) {
          def this(localEvent: WireAjaxEventWithTarget) =
            this(localEvent.eventName, localEvent.targetId)
        }

        // Slide over the events so we can filter and compress them
        // NOTE: Don't use Iterator.toSeq as that returns a Stream, which evaluates lazily. This would be great, except
        // that we *must* first create all events, then dispatch them, so that references to XFormsTarget are obtained
        // beforehand.
        (events ++ DummyEvent).sliding(2).toList flatMap {
          case List((a, trusted), (b, _)) =>
            if (a.eventName != EventNames.XXFormsValue || new EventGroupingKey(a) != new EventGroupingKey(b))
              safelyCreateAndMapEvent(doc, a, trusted)
            else
              None
        }
    }

    // Combine and process events
    for (event <- combineValueEvents(filterEvents(allClientAndServerEvents)))
      processEvent(doc, event)

    // Gather some metadata about the events received to help with the response to the client

    // Set of all control ids for which we got value events
    val valueChangeControlIdsAndValues = allClientAndServerEvents collect {
      case (e, _) if e.eventName == EventNames.XXFormsValue => e.targetId -> e.valueOpt.get
    }

    // Last focus/blur event received from the client
    // This ignores server events, see: https://github.com/orbeon/orbeon-forms/issues/2567
    val clientFocusControlIdOpt = allClientAndServerEvents.reverse filterNot (_._2) collectFirst {
      case (e, _) if e.eventName == XFORMS_FOCUS => Some(e.targetId)
      case (e, _) if e.eventName == XXFORMS_BLUR => None
    }

    EventsFindings(valueChangeControlIdsAndValues.toMap, clientFocusControlIdOpt)
  }

  // Incoming ids can have the form `my-repeat⊙1` in order to target a repeat iteration. This is ambiguous without
  // knowing that `my-repeat` refers to a repeat and without knowing the repeat hierarchy, so we should change it
  // in the future, but in the meanwhile we map this id to `my-repeat~iteration⊙1` based on static information.
  // NOTE: Leave public for unit tests
  // TODO: Handle https://github.com/orbeon/orbeon-forms/issues/3853.
  def adjustIdForRepeatIteration(doc: XFormsContainingDocument, effectiveId: String): String =
    doc.staticOps.getControlAnalysis(XFormsId.getPrefixedId(effectiveId)) match {
      case repeat: RepeatControl if repeat.ancestorRepeatsAcrossParts.size == XFormsId.getEffectiveIdSuffixParts(effectiveId).size - 1 =>
        XFormsId.getRelatedEffectiveId(effectiveId, repeat.iteration.get.staticId)
      case _ =>
        effectiveId
    }

  // Send an error document
  def errorDocument(message: String, code: Int)(implicit receiver: XMLReceiver): Unit =
    withDocument {
      processingInstruction("orbeon-serializer", List("status-code" -> code.toString))
      withElement("error") {
        element("title", text = message)
      }
    }

  // Send an error response consisting of just a status code
  def errorResponse(code: Int)(implicit receiver: XMLReceiver): Unit =
    withDocument {
      processingInstruction("orbeon-serializer", List("status-code" -> code.toString))
    }

  def assertSessionExists(): Unit =
    Option(NetUtils.getSession(false)) getOrElse
      (throw SessionExpiredException("Session has expired. Unable to process incoming request."))

  // Check for and handle events that don't need access to the document but can return an Ajax response rapidly
  def handleQuickReturnEvents(
    xmlReceiver         : XMLReceiver,
    requestUuid         : String,
    request             : ExternalContext.Request,
    logRequestResponse  : Boolean,
    clientEvents        : List[WireAjaxEvent])(implicit
    indentedLogger      : IndentedLogger
  ): List[WireAjaxEvent] = {

    val eventsWithSequence = clientEvents collect {
      case e if ! EventNames.EventsWithoutSequence(e.eventName) => e
    }

    // Helper to make it easier to output simple Ajax responses
    def eventResponse(message: String)(block: XMLReceiverHelper => Unit): Boolean = {
      withDebug(message) {
        // Hook-up debug content handler if we must log the response document
        val (responseReceiver, debugContentHandler) =
          if (logRequestResponse) {
            val receivers = new ju.ArrayList[XMLReceiver]
            receivers.add(xmlReceiver)
            val debugContentHandler = new LocationSAXContentHandler
            receivers.add(debugContentHandler)

            (new TeeXMLReceiver(receivers), Some(debugContentHandler))
          } else
            (xmlReceiver, None)

        val helper = new XMLReceiverHelper(responseReceiver)
        helper.startDocument()
        helper.startPrefixMapping("xxf", XXFORMS_NAMESPACE_URI)
        helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "event-response")

        block(helper)

        helper.endElement()
        helper.endPrefixMapping("xxf")
        helper.endDocument()

        debugContentHandler foreach
          (ch => debugResults(Seq("ajax response" -> ch.getDocument.getRootElement.serializeToString(XMLWriter.PrettyFormat))))
      }

      true
    }

    def logEvent(message: String): Unit =
      LifecycleLogger.eventAssumingRequest("xforms", message, List("uuid" -> requestUuid))

    if (eventsWithSequence.nonEmpty) {
      // Return other events only
      logEvent("ajax with update events")
      eventsWithSequence
    } else {

      val uploadProgressEvents = clientEvents collect {
        case e @ WireAjaxEventWithTarget(EventNames.XXFormsUploadProgress, _ ,_) => e
      }

      uploadProgressEvents match {
        case uploadProgressEventsNel @ _ :: _ =>
          // Directly output progress information for all controls found
          eventResponse("ajax response: handling quick upload progress Ajax response") { helper =>

            logEvent("ajax upload progress")

            val ids         = uploadProgressEventsNel map (_.targetId)
            val allProgress = ids flatMap (id => UploaderServer.getUploadProgress(request, requestUuid, id).toList)

            if (allProgress.nonEmpty) {

              helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "action")
              helper.startElement("xxf", XXFORMS_NAMESPACE_URI, "control-values")

              allProgress foreach {
                progress =>
                  helper.element(
                    "xxf", XXFORMS_NAMESPACE_URI, "control",
                    Array[String]("id", request.getContainerNamespace + progress.fieldName,
                      "progress-state",    progress.state.name,
                      "progress-received", progress.receivedSize.toString,
                      "progress-expected", progress.expectedSize map (_.toString) orNull
                    )
                  )
              }

              helper.endElement()
              helper.endElement()
            }
          }
        case Nil =>
          if (clientEvents exists (_.eventName == EventNames.XXFormsSessionHeartbeat)) {
            // Output empty Ajax response
            logEvent("ajax heartbeat")
            eventResponse("ajax response: handling quick heartbeat Ajax response")(_ => ())
          } else {
            // No events to process
            logEvent("ajax empty")
            eventResponse("ajax response: handling quick empty response")(_ => ())
          }
      }
      Nil
    }
  }

  // Process an incoming client event. Preprocessing for encrypted events is assumed to have taken place.
  // This handles checking for stale controls, relevance, readonly, and special cases like `xf:output`.
  // NOTE: Leave public for unit tests
  def processEvent(doc: XFormsContainingDocument, event: XFormsEvent): Unit = {

    // Check whether an event can be be dispatched to the given object. This only checks:
    // - the the target is still live
    // - that the target is not a non-relevant or readonly control
    def checkEventTarget(event: XFormsEvent): Boolean = {
      val eventTarget = event.targetObject
      val newReference = doc.getObjectByEffectiveId(eventTarget.getEffectiveId)

      def warn(condition: String) = {
        debug("ignoring invalid client event on " + condition, Seq(
          "control id" -> eventTarget.getEffectiveId,
          "event name" -> event.name)
        )(doc.indentedLogger)
        false
      }

      if (eventTarget ne newReference) {

        // Here, we check that the event's target is still a valid object. For example, a couple of events from the
        // UI could target controls. The first event is processed, which causes a change in the controls tree. The
        // second event would then refer to a control which no longer exist. In this case, we don't dispatch it.

        // We used to check simply by effective id, but this is not enough in some cases. We want to handle
        // controls that just "move" in a repeat. Scenario:
        //
        // - repeat with 2 iterations has xf:input and xf:trigger
        // - assume repeat is sorted on input value
        // - use changes value in input and clicks trigger
        // - client sends 2 events to server
        // - client processes value change and sets new value
        // - refresh takes place and causes reordering of rows
        // - client processes DOMActivate on trigger, which now has moved position, e.g. row 2 to row 1
        // - DOMActivate is dispatched to proper control (i.e. same as input was on)
        //
        // On the other hand, if the repeat iteration has disappeared, or was removed and recreated, the event is
        // not dispatched.

        warn("ghost target")

      } else {

        def allowFocusEvent(c: XFormsControl, e: XFormsEvent) =
          c.directlyFocusableControls.nonEmpty && (
            e.isInstanceOf[XFormsFocusEvent] ||
              e.isInstanceOf[XXFormsBlurEvent] && (doc.controls.getFocusedControl exists (_ eq c))
          )

        (eventTarget, event) match {
          // Controls accept event only if they are relevant
          case (c: XFormsControl, _) if ! c.isRelevant =>
            warn("non-relevant control")

          // These controls can accept focus events
          case (c: XFormsControl, e @ (_: XFormsFocusEvent | _: XXFormsBlurEvent)) if allowFocusEvent(c, e) =>
            true

          // Other readonly single node controls accept events only if they are not readonly
          case (c: XFormsSingleNodeControl, _) if c.isReadonly =>
            warn("read-only control")

          // Disallow focus/blur if the control is not focusable
          // Relevance and read-only above are already caught. This catches hidden controls, which must not be
          // focusable from the client.
          case (c: XFormsControl, e @ (_: XFormsFocusEvent | _: XXFormsBlurEvent)) if ! allowFocusEvent(c, e) =>
            warn(s"non-focusable control for `${e.name}`")

          case _ =>
            true
        }
      }
    }

    def dispatchEventCheckTarget(event: XFormsEvent): Unit =
      if (checkEventTarget(event))
        Dispatch.dispatchEvent(event)

    implicit val CurrentLogger = doc.getIndentedLogger(LOGGING_CATEGORY)

    val target            = event.targetObject
    val targetEffectiveId = target.getEffectiveId
    val eventName         = event.name

    withDebug("handling external event", Seq("target id" -> targetEffectiveId, "event name" -> eventName)) {

      // Optimize case where a value change event won't change the control value to actually change
      (event, target) match {
        case (valueChange: XXFormsValueEvent, target: XFormsValueControl) if target.getExternalValue == valueChange.value =>
          // We completely ignore the event if the value in the instance is the same.
          // This also saves dispatching xxforms-repeat-activate below.
          debug("ignoring value change event as value is the same", Seq(
            "control id" -> targetEffectiveId,
            "event name" -> eventName,
            "value" -> target.getExternalValue)
          )
          return
        case _ =>
      }

      // NOTES:

      // 1. We used to dispatch xforms-focus here, but now we don't anymore: we assume that the client provides
      //    xforms-focus before value changes as needed. Also, value changes can occur without focus changes, in
      //    particular when the JavaScript API is used.

      // 2. We also used to handle value controls here, but it makes more sense to do it via events.

      // 3. Recalculate, revalidate and refresh are handled with the automatic deferred updates.

      // 4. We used to do special handling for xf:output: upon click on xf:output, the client would send
      //    xforms-focus. We would translate that into DOMActivate. As of 2012-03-09 there doesn't seem to be a
      //    need for this so we are removing this behavior.

      // Each event is within its own start/end outermost action handler
      doc.withOutermostActionHandler {

        // Handle repeat iteration if the event target is in a repeat
        if (XFormsId.hasEffectiveIdSuffix(targetEffectiveId))
          dispatchEventCheckTarget(new XXFormsRepeatActivateEvent(target, EmptyGetter))

        // Interpret event
        dispatchEventCheckTarget(event)
      }
    }
  }

  private object Private {

    val DummyEvent = List(WireAjaxEventWithTarget("dummy", "#dummy", Map.empty) -> false)

    def safelyCreateAndMapEvent(
      doc     : XFormsContainingDocument,
      event   : WireAjaxEventWithTarget,
      trusted : Boolean
    ): Option[XFormsEvent] = {

      implicit val CurrentLogger = doc.getIndentedLogger(LOGGING_CATEGORY)

      // Get event target
      val eventTarget =
        doc.getObjectByEffectiveId(deNamespaceId(doc, adjustIdForRepeatIteration(doc, event.targetId))) match {
          case eventTarget: XFormsEventTarget => eventTarget
          case _ =>
            debug(
              "ignoring client event with invalid target id",
              List("target id" -> event.targetId, "event name" -> event.eventName)
            )
            return None
        }

      // Check whether the external event is allowed on the given target.
      def checkAllowedExternalEvents = {

        // Whether an external event name is explicitly allowed by the configuration.
        def isExplicitlyAllowedExternalEvent = {
          val externalEventsSet = doc.staticState.allowedExternalEvents
          ! XFormsEventFactory.isBuiltInEvent(event.eventName) && externalEventsSet(event.eventName)
        }

        // This is also a security measure that also ensures that somebody is not able to change values in an instance
        // by hacking external events.
        isExplicitlyAllowedExternalEvent || {
          val explicitlyAllowed = eventTarget.allowExternalEvent(event.eventName)
          if (! explicitlyAllowed)
            debug(
              "ignoring invalid client event on target",
              List("id" -> eventTarget.getEffectiveId, "event name" -> event.eventName)
            )
          explicitlyAllowed
        }
      }

      // Check the event is allowed on target
      if (trusted)
        // Event is trusted, don't check if it is allowed
        debug(
          "processing trusted event",
          List("target id" -> eventTarget.getEffectiveId, "event name" -> event.eventName)
        )
      else if (! checkAllowedExternalEvents)
        return None // event is not trusted and is not allowed

      Some(
        XFormsEventFactory.createEvent(
          eventName  = event.eventName,
          target     = eventTarget,
          properties = event.properties mapValues Some.apply,
          bubbles    = true, // the default; we don't allow the client to pass those
          cancelable = true  // the default; we don't allow the client to pass those
        )
      )
    }
  }
}