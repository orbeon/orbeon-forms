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

import events._
import org.orbeon.oxf.xforms.control._
import org.orbeon.oxf.xforms.control.controls._
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils
import scala.collection.JavaConversions._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xml._
import org.orbeon.oxf.util.IndentedLogger
import java.util.{ArrayList, List => JList, Set => JSet}
import dom4j.{LocationSAXContentHandler, Dom4jUtils}
import org.orbeon.oxf.pipeline.api._
import org.orbeon.oxf.util.Multipart
import org.dom4j.{Document, Element}
import org.orbeon.oxf.xforms.state.XFormsStateManager

object ClientEvents {

    private val EVENT_LOG_TYPE = "executeExternalEvent"
    private val EVENT_PARAMETERS = List("dnd-start", "dnd-end", "modifiers", "text", "file", "filename", "content-type", "content-length")
    private val DUMMY_EVENT = List(new LocalEvent(Dom4jUtils.createElement("dummy"), false))

    private case class LocalEvent(element: Element, trusted: Boolean) {
        val name = element.attributeValue("name")
        val targetEffectiveId = element.attributeValue("source-control-id")
        val bubbles = element.attributeValue("bubbles") != "false" // default is true
        val cancelable = element.attributeValue("cancelable") != "false" // default is true
        val otherTargetEffectiveId = element.attributeValue("other-control-id")
        val value = element.getText
    }

    /**
     * Process a sequence of incoming client events.
     */
    def processEvents(pipelineContext: PipelineContext, document: XFormsContainingDocument, clientEvents: JList[Element],
                      serverEvents: JList[Element], valueChangeControlIds: JSet[String]) = {

        // Process events for noscript mode if needed
        val clientEventsAfterNoscript =
            if (document.getStaticState.isNoscript)
                reorderNoscriptEvents(clientEvents, document)
            else
                clientEvents.toSeq

        // Decode encrypted server events
        def decodeServerEvents(element: Element) = {
            val document = XFormsUtils.decodeXML(element.getStringValue)
            Dom4jUtils.elements(document.getRootElement, XFormsConstants.XXFORMS_EVENT_QNAME)
        }

        // Grouping key for value change events
        case class EventGroupingKey(name: String, targetId: String, otherTargetId: String) {
            def this(localEvent: LocalEvent) =
                this(localEvent.name, localEvent.targetEffectiveId, localEvent.otherTargetEffectiveId)
        }

        // Decode global server events
        val globalServerEvents: Seq[LocalEvent] = serverEvents flatMap (decodeServerEvents(_)) map (LocalEvent(_, true))

        // Gather all events including decoding action server events
        val allClientAndServerEvents: Seq[LocalEvent] =
            globalServerEvents ++
                (clientEventsAfterNoscript flatMap {
                    case element if element.attributeValue("name") == XFormsEvents.XXFORMS_SERVER_EVENTS =>
                        decodeServerEvents(element) map (LocalEvent(_, true))
                    case element => List(LocalEvent(element, false))
                })

        var hasAllEvents = false

        if (allClientAndServerEvents.size > 0 ) {
            // Slide over the events so we can filter and compress them
            // NOTE: Don't use Iterator.toSeq as that returns a Stream, which evaluates lazily. This would be great, except
            // that we *must* first create all events, then dispatch them, so that references to XFormsTarget are obtained
            // beforehand.
            val filteredEvents: Seq[XFormsEvent] = (allClientAndServerEvents ++ DUMMY_EVENT).sliding(2).toList flatMap {
                case Seq(a, _) if a.name == XFormsEvents.XXFORMS_ALL_EVENTS_REQUIRED =>
                    // Just remember we got the "all events" event
                    hasAllEvents = true
                    None
                case Seq(a, _) if (a.name eq null) && (a.targetEffectiveId eq null) =>
                    throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.")
                case Seq(a, _) if a.name != XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE =>
                    // Non-value change event
                    safelyCreateEvent(document, a)
                case Seq(a, b) if new EventGroupingKey(a) != new EventGroupingKey(b) =>
                    // Only process last value change event received
                    assert(a.name == XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)
                    valueChangeControlIds += a.targetEffectiveId
                    safelyCreateEvent(document, a)
                case Seq(a, b) =>
                    // Nothing to do here: we are compressing value change events
                    assert(a.name == XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)
                    assert(new EventGroupingKey(a) == new EventGroupingKey(b))
                    None
            }

            // Process all filtered events
            for (event <- filteredEvents)
                processEvent(document, event)
        }

        hasAllEvents
    }

    def reorderNoscriptEvents(eventElements: Seq[Element], document: XFormsContainingDocument): Seq[Element] = {

        // Event categories, in the order we will want them
        object Category extends Enumeration {
            val Other = Value
            val ValueChange = Value
            val SelectBlank = Value
            val Activation = Value
        }

        // Group events in 3 categories
        def getEventCategory(element: Element) = element match {
            // Special event for noscript mode
            case element if element.attributeValue("name") == XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE =>
                val sourceControlId = element.attributeValue("source-control-id")
                element match {
                    // This is a value event
                    case element if document.getStaticState.isValueControl(sourceControlId) => Category.ValueChange
                    // This is most likely a trigger or submit which will translate into a DOMActivate. We will move it
                    // to the end so that value change events are committed to instance data before that.
                    case _ => Category.Activation
                }
            case _ => Category.Other
        }

        // NOTE: map keys are not in predictable order, but map values preserve the order
        val groups = eventElements groupBy (getEventCategory(_))

        // Special handling of checkboxes blanking in noscript mode
        val blankEvents = {

            // Get set of all value change events effective ids
            def getValueChangeIds = groups.get(Category.ValueChange).flatten map (_.attributeValue("source-control-id")) toSet

            // Create <xxf:event name="xxforms-value-or-activate" source-control-id="my-effective-id"/>
            def createBlankingEvent(control: XFormsControl) = {
                val newEventElement = Dom4jUtils.createElement(XFormsConstants.XXFORMS_EVENT_QNAME)
                newEventElement.addAttribute("name", XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE)
                newEventElement.addAttribute("source-control-id", control.getEffectiveId)
                newEventElement
            }

            val selectFullControls = document.getControls.getCurrentControlTree.getSelectFullControls

            // Find all relevant and non-readonly select controls for which no value change event arrived. For each such
            // control, create a new event that will blank its value.
            selectFullControls.keySet -- getValueChangeIds map
                (selectFullControls.get(_).asInstanceOf[XFormsSelectControl]) filter
                    (control => control.isRelevant && !control.isReadonly) map
                        (createBlankingEvent(_)) toSeq
        }

        // Return all events by category in the order we defined the categories
        Category.values.toSeq flatMap ((groups + (Category.SelectBlank -> blankEvents)).get(_)) flatten
    }

    private def safelyCreateEvent(document: XFormsContainingDocument, event: LocalEvent): Option[XFormsEvent] = {

        val indentedLogger = document.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)

        // Get event target
        val eventTarget = document.getObjectByEffectiveId(XFormsUtils.deNamespaceId(document, event.targetEffectiveId)) match {
            case eventTarget: XFormsEventTarget => eventTarget
            case _ =>
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring client event with invalid target id", "target id", event.targetEffectiveId, "event name", event.name)
                return None
        }

        val newEventName = event.name match {
            // Rewrite event type. This is special handling of xxforms-value-or-activate for noscript mode.
            // NOTE: We do this here, because we need to know the actual type of the target. Could do this statically if
            // the static state kept type information for each control.
            case XFormsEvents.XXFORMS_VALUE_OR_ACTIVATE =>
                eventTarget match {
                    // Handler produces:
                    //   <button type="submit" name="foobar" value="activate">...
                    //   <input type="submit" name="foobar" value="Hi There">...
                    //   <input type="image" name="foobar" value="Hi There" src="...">...

                    // IE 6/7 are terminally broken: they don't send the value back, but the contents of the label. So
                    // we must test for any empty content here instead of "!activate".equals(valueString). (Note that
                    // this means that empty labels won't work.) Further, with IE 6, all buttons are present when
                    // using <button>, so we use <input> instead, either with type="submit" or type="image". Bleh.
                    case triggerControl: XFormsTriggerControl if event.value.isEmpty => return None
                    // Triggers get a DOM activation
                    case triggerControl: XFormsTriggerControl => XFormsEvents.DOM_ACTIVATE
                    // Other controls get a value change
                    case _ => XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE
                }
             case eventName => eventName
        }

        // Check whether the external event is allowed on the given target.
        def checkAllowedExternalEvents = {

            // Whether an external event name is explicitly allowed by the configuration.
            def isExplicitlyAllowedExternalEvent = {
                val externalEventsMap = document.getStaticState.getAllowedExternalEvents
                !XFormsEventFactory.isBuiltInEvent(event.name) && externalEventsMap.contains(event.name)
            }

            // This is also a security measure that also ensures that somebody is not able to change values in an instance
            // by hacking external events.
            isExplicitlyAllowedExternalEvent || eventTarget.allowExternalEvent(indentedLogger, EVENT_LOG_TYPE, event.name)
        }

        // Check the event is allowed on target
        if (event.trusted) {
            // Event is trusted, don't check if it is allowed
            if (indentedLogger.isDebugEnabled)
                indentedLogger.logDebug(EVENT_LOG_TYPE, "processing trusted event", "target id", eventTarget.getEffectiveId, "event name", event.name)
        } else if (!checkAllowedExternalEvents)
            return None // event is not trusted and is not allowed

        // Get other event target
        val otherEventTarget = event.otherTargetEffectiveId match {
            case otherTargetEffectiveId: String =>
                document.getObjectByEffectiveId(XFormsUtils.deNamespaceId(document, otherTargetEffectiveId)) match {
                    case eventTarget: XFormsEventTarget => eventTarget
                    case _ =>
                        if (indentedLogger.isDebugEnabled)
                            indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring client event with invalid second target id", "target id", event.otherTargetEffectiveId, "event name", event.name)
                        return None
                }
            case _ => null
        }

        def gatherParameters(event: LocalEvent) =
            Map((for {
                    attributeName <- EVENT_PARAMETERS
                    attributeValue = event.element.attributeValue(attributeName)
                    if attributeValue ne null
                } yield (attributeName -> attributeValue)): _*)

        // Create event
        Some(XFormsEventFactory.createEvent(document, newEventName, eventTarget, otherEventTarget, true,
            event.bubbles, event.cancelable, event.value, gatherParameters(event)))
    }

    /**
     * Check for and handle events that don't need access to the document but can return an Ajax response rapidly.
     */
    def doQuickReturnEvents(xmlReceiver: XMLReceiver, request: ExternalContext.Request, requestDocument: Document, indentedLogger: IndentedLogger,
               logRequestResponse: Boolean, clientEvents: JList[Element], session: ExternalContext.Session): Boolean = {

        val eventElement = clientEvents(0)

        // Helper to make it easier to output simple Ajax responses
        def eventResponse(messageType: String, message: String)(block: ContentHandlerHelper => Unit): Boolean = {
            indentedLogger.startHandleOperation(messageType, message)

            // Hook-up debug content handler if we must log the response document
            val (responseReceiver, debugContentHandler) =
                if (logRequestResponse) {
                    val receivers = new ArrayList[XMLReceiver]
                    receivers.add(xmlReceiver)
                    val debugContentHandler = new LocationSAXContentHandler
                    receivers.add(debugContentHandler)

                    (new TeeXMLReceiver(receivers), debugContentHandler)
                } else
                    (xmlReceiver, null)

            val helper = new ContentHandlerHelper(responseReceiver)
            helper.startDocument()
            helper.startPrefixMapping("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI)
            helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "event-response")

            block(helper)

            helper.endElement()
            helper.endPrefixMapping("xxf")
            helper.endDocument()

            indentedLogger.endHandleOperation("ajax response", if (debugContentHandler ne null) Dom4jUtils.domToPrettyString(debugContentHandler.getDocument) else null)

            true
        }

        eventElement.attributeValue("name") match {
            // Quick response for heartbeat
            case XFormsEvents.XXFORMS_SESSION_HEARTBEAT =>

                if (indentedLogger.isDebugEnabled) {
                    if (session != null)
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client for session: " + session.getId)
                    else
                        indentedLogger.logDebug("heartbeat", "received heartbeat from client (no session available).")
                }

                // Output empty Ajax response
                eventResponse("ajax response", "handling quick heartbeat Ajax response")(helper => ())

            // Quick response for upload progress
            case XFormsEvents.XXFORMS_UPLOAD_PROGRESS =>

                // Output simple resulting document
                eventResponse("ajax response", "handling quick upload progress Ajax response") { helper =>
                    val sourceControlId = eventElement.attributeValue("source-control-id")
                    Multipart.getUploadProgress(request, XFormsStateManager.getRequestUUID(requestDocument), sourceControlId) match {
                        case Some(progress) =>

                            helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action")
                            helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values")
                            helper.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control",
                                Array[String]("id", sourceControlId,
                                    "progress-state", progress.state.toString.toLowerCase,
                                    "progress-received", progress.receivedSize.toString,
                                    "progress-expected", progress.expectedSize match {case Some(long) => long.toString; case _ => null}))
                            helper.endElement()
                            helper.endElement()

                        case _ =>
                    }
                }
            case _ => false
        }
    }

    /**
     * Process an incoming client event. Preprocessing for Noscript and encrypted events is assumed to have taken place.
     *
     * This handles checking for stale controls, relevance, readonly, and special cases like xf:output.
     *
     * @param event             event to dispatch
     */
    def processEvent(document: XFormsContainingDocument, event: XFormsEvent): Unit = {

        // Check whether an event can be be dispatched to the given object. This only checks:
        // o the the target is still live
        // o that the target is not a non-relevant or readonly control
        def checkEventTarget(event: XFormsEvent): Boolean = {
            val eventTarget = event.getTargetObject
            val newReference = document.getObjectByEffectiveId(eventTarget.getEffectiveId)
            if (eventTarget ne newReference) {

                // Here, we check that the event's target is still a valid object. For example, a couple of events from the
                // UI could target controls. The first event is processed, which causes a change in the controls tree. The
                // second event would then refer to a control which no longer exist. In this case, we don't dispatch it.

                // We used to check simply by effective id, but this is not enough in some cases. We want to handle
                // controls that just "move" in a repeat. Scenario:
                //
                // o repeat with 2 iterations has xforms:input and xforms:trigger
                // o assume repeat is sorted on input value
                // o use changes value in input and clicks trigger
                // o client sends 2 events to server
                // o client processes value change and sets new value
                // o refresh takes place and causes reordering of rows
                // o client processes DOMActivate on trigger, which now has moved position, e.g. row 2 to row 1
                // o DOMActivate is dispatched to proper control (i.e. same as input was on)
                //
                // On the other hand, if the repeat iteration has disappeared, or was removed and recreated, the event is
                // not dispatched.

                if (document.getIndentedLogger.isDebugEnabled)
                    document.getIndentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on ghost target", "control id", eventTarget.getEffectiveId, "event name", event.getName)

                false

            } else eventTarget match {
                // Controls accept event only if they are relevant
                case control: XFormsControl if !control.isRelevant =>
                    if (document.getIndentedLogger.isDebugEnabled)
                        document.getIndentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on non-relevant control", "control id", eventTarget.getEffectiveId, "event name", event.getName)

                    false

                // Output control not subject to readonly condition below
                case outputControl: XFormsOutputControl => true

                // Single node controls accept event only if they are not readonly
                case singleNodeControl: XFormsSingleNodeControl if singleNodeControl.isReadonly =>

                    if (document.getIndentedLogger.isDebugEnabled)
                        document.getIndentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring invalid client event on read-only control", "control id", eventTarget.getEffectiveId, "event name", event.getName)

                    false

                case _ => true
            }
        }

        def dispatchEventCheckTarget(event: XFormsEvent) =
            if (checkEventTarget(event))
                document.dispatchEvent(event)

        val indentedLogger = document.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)
        val eventTarget = event.getTargetObject
        val eventTargetEffectiveId = eventTarget.getEffectiveId
        val eventName = event.getName

        indentedLogger.startHandleOperation(EVENT_LOG_TYPE, "handling external event", "target id", eventTargetEffectiveId, "event name", eventName)
        try {
            // Each event is within its own start/end outermost action handler
            document.startOutermostActionHandler()

            // Check if the value to set will be different from the current value
            if (eventTarget.isInstanceOf[XFormsValueControl] && event.isInstanceOf[XXFormsValueChangeWithFocusChangeEvent]) {
                val valueChangeWithFocusChangeEvent = event.asInstanceOf[XXFormsValueChangeWithFocusChangeEvent]
                if (valueChangeWithFocusChangeEvent.getOtherTargetObject eq null) {
                    // We only get a value change with this event
                    val currentExternalValue = (eventTarget.asInstanceOf[XFormsValueControl]).getExternalValue
                    if (currentExternalValue ne null) {
                        // We completely ignore the event if the value in the instance is the same. This also saves dispatching xxforms-repeat-focus below.
                        val isIgnoreValueChangeEvent = currentExternalValue.equals(valueChangeWithFocusChangeEvent.getNewValue)
                        if (isIgnoreValueChangeEvent) {
                            indentedLogger.logDebug(EVENT_LOG_TYPE, "ignoring value change event as value is the same", "control id", eventTargetEffectiveId, "event name", eventName, "value", currentExternalValue)
                            // Ensure deferred event handling
                            // NOTE: Here this will do nothing, but out of consistency we better have matching startOutermostActionHandler/endOutermostActionHandler
                            document.endOutermostActionHandler()
                            return
                        }
                    } else {
                        // shouldn't happen really, but just in case let's log this
                        indentedLogger.logDebug(EVENT_LOG_TYPE, "got null currentExternalValue", "control id", eventTargetEffectiveId, "event name", eventName)
                    }
                } else {
                    // There will be a focus event too, so don't ignore the event!
                }
            }

            // Handle repeat focus. Don't dispatch event on DOMFocusOut however.
            if (eventTargetEffectiveId.indexOf(XFormsConstants.REPEAT_HIERARCHY_SEPARATOR_1) != -1 && !(event.isInstanceOf[DOMFocusOutEvent])) {
                // The event target is in a repeated structure, so make sure it gets repeat focus
                dispatchEventCheckTarget(new XXFormsRepeatFocusEvent(document, eventTarget))
            }

            // Interpret event
            eventTarget match {
                // Special xforms:output case
                case xformsOutputControl: XFormsOutputControl =>
                    event match {
                        case event: DOMFocusInEvent =>
                            // First, dispatch DOMFocusIn
                            dispatchEventCheckTarget(event)
                            // Then, dispatch DOMActivate unless the control is read-only
                            if (!xformsOutputControl.isReadonly)
                                dispatchEventCheckTarget(new DOMActivateEvent(document, xformsOutputControl))
                        case event if !xformsOutputControl.isIgnoredExternalEvent(eventName) =>
                            // Dispatch any other event
                            dispatchEventCheckTarget(event)
                        case _ =>
                    }
                // All other targets
                case _ =>
                    event match {
                        // Special case of value change with focus change
                        case valueChangeWithFocusChangeEvent: XXFormsValueChangeWithFocusChangeEvent =>
                            // 4.6.7 Sequence: Value Change

                            // TODO: Not sure if this comment makes sense anymore.
                            // What we want to do here is set the value on the initial controls tree, as the value has already been
                            // changed on the client. This means that this event(s) must be the first to come!

                            if (checkEventTarget(event)) {
                                // Store value into instance data through the control
                                val valueXFormsControl = eventTarget.asInstanceOf[XFormsValueControl]
                                valueXFormsControl.storeExternalValue(valueChangeWithFocusChangeEvent.getNewValue, null)
                            }

                            // NOTE: Recalculate and revalidate are done with the automatic deferred updates

                            // Handle focus change DOMFocusOut / DOMFocusIn
                            if (valueChangeWithFocusChangeEvent.getOtherTargetObject ne null) {

                                // We have a focus change (otherwise, the focus is assumed to remain the same)

                                // Dispatch DOMFocusOut
                                // NOTE: storeExternalValue() above may cause e.g. xforms-select / xforms-deselect events to be
                                // dispatched, so we get the control again to have a fresh reference

                                dispatchEventCheckTarget(new DOMFocusOutEvent(document, eventTarget))

                                // Dispatch DOMFocusIn
                                dispatchEventCheckTarget(new DOMFocusInEvent(document, valueChangeWithFocusChangeEvent.getOtherTargetObject))
                            }

                            // NOTE: Refresh is done with the automatic deferred updates

                        // Dispatch any other event
                        case _ =>
                            dispatchEventCheckTarget(event)
                    }
            }

            // Each event is within its own start/end outermost action handler
            document.endOutermostActionHandler()
        } finally {
            indentedLogger.endHandleOperation()
        }
    }
}