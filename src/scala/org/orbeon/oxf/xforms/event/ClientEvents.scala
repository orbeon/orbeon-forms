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

import org.dom4j.Element
import org.orbeon.oxf.pipeline.api.PipelineContext
import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils
import org.orbeon.oxf.xforms.control.controls.XFormsTriggerControl
import org.orbeon.oxf.xml.dom4j.Dom4jUtils

import scala.collection.JavaConversions._
import java.util.{List => JList, Set => JSet}
import org.orbeon.oxf.common.OXFException

object ClientEvents {

    private val EVENT_PARAMETERS = List("dnd-start", "dnd-end", "modifiers", "text", "file", "filename", "content-type", "content-length")

    private val DUMMY_EVENT = List(new LocalEvent(Dom4jUtils.createElement("dummy"), false))

    private case class LocalEvent(element: Element, trusted: Boolean) {
        val name = element.attributeValue("name")
        val targetEffectiveId = element.attributeValue("source-control-id")
        val bubbles = element.attributeValue("bubbles") != "false" // default is true
        val cancelable = element.attributeValue("cancelable") != "false" // default is true
        val otherTargetEffectiveId = element.attributeValue("other-control-id")
        val value = element.getText()
    }

    def createAndDispatchEvents(pipelineContext: PipelineContext, containingDocument: XFormsContainingDocument,
                                clientEvents: JList[Element], serverEvents: JList[Element],
                                valueChangeControlIds: JSet[String]) = {

        // Decode encrypted server events
        def decodeServerEvents(element: Element) = {
            val document = XFormsUtils.decodeXML(pipelineContext, element.getStringValue)
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
                (clientEvents flatMap (_ match {
                    case element if element.attributeValue("name") == XFormsEvents.XXFORMS_SERVER_EVENTS =>
                        decodeServerEvents(element) map (LocalEvent(_, true))
                    case element => List(LocalEvent(element, false))
                }))

        var hasAllEvents = false

        // Slide over the events so we can filter and compress them
        // NOTE: Don't use Iterator.toSeq as that returns a Stream, which evaluates lazily. This would be great, except
        // that we *must* first create all events, then dispatch them, so that references to XFormsTarget are obtained
        // beforehand.
        val filteredEvents: Seq[XFormsEvent] = (allClientAndServerEvents ++ DUMMY_EVENT).sliding(2).toList flatMap (_ match {
            case Seq(a, _) if a.name == XFormsEvents.XXFORMS_ALL_EVENTS_REQUIRED =>
                // Just remember we got the "all events" event
                hasAllEvents = true
                None
            case Seq(a, _) if (a.name eq null) && (a.targetEffectiveId eq null) =>
                throw new OXFException("<event> element must either have source-control-id and name attributes, or no attribute.")
            case Seq(a, _) if a.name != XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE =>
                // Non-value change event
                safelyCreateEvent(containingDocument, a)
            case Seq(a, b) if new EventGroupingKey(a) != new EventGroupingKey(b) =>
                // Only process last value change event received
                assert(a.name == XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)
                valueChangeControlIds.add(a.targetEffectiveId)
                safelyCreateEvent(containingDocument, a)
            case Seq(a, b) =>
                // Nothing to do here: we are compressing value change events
                assert(a.name == XFormsEvents.XXFORMS_VALUE_CHANGE_WITH_FOCUS_CHANGE)
                assert(new EventGroupingKey(a) == new EventGroupingKey(b))
                None
        })

        // Dispatch all filtered events
        for (event <- filteredEvents)
            containingDocument.handleExternalEvent(pipelineContext, event)

        hasAllEvents
    }

    private def safelyCreateEvent(containingDocument: XFormsContainingDocument, event: LocalEvent): Option[XFormsEvent] = {

        val indentedLogger = containingDocument.getIndentedLogger(XFormsEvents.LOGGING_CATEGORY)

        // Get event target
        val eventTarget = containingDocument.getObjectByEffectiveId(XFormsUtils.deNamespaceId(containingDocument, event.targetEffectiveId)) match {
            case eventTarget: XFormsEventTarget => eventTarget
            case _ =>
                if (indentedLogger.isDebugEnabled)
                    indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "ignoring client event with invalid target id", "target id", event.targetEffectiveId, "event name", event.name)
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

        // Check the event is allowed on target
        if (event.trusted) {
            // Event is trusted, don't check if it is allowed
            if (indentedLogger.isDebugEnabled)
                indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "processing trusted event", "target id", eventTarget.getEffectiveId, "event name", event.name)
        } else if (!containingDocument.checkAllowedExternalEvents(indentedLogger, event.name, eventTarget))
            return None // event is not trusted and is not allowed

        // Get other event target
        val otherEventTarget = event.otherTargetEffectiveId match {
            case otherTargetEffectiveId: String =>
                containingDocument.getObjectByEffectiveId(XFormsUtils.deNamespaceId(containingDocument, otherTargetEffectiveId)) match {
                    case eventTarget: XFormsEventTarget => eventTarget
                    case _ =>
                        if (indentedLogger.isDebugEnabled)
                            indentedLogger.logDebug(XFormsContainingDocument.EVENT_LOG_TYPE, "ignoring client event with invalid second target id", "target id", event.otherTargetEffectiveId, "event name", event.name)
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
        Some(XFormsEventFactory.createEvent(containingDocument, newEventName, eventTarget, otherEventTarget, true,
            event.bubbles, event.cancelable, event.value, gatherParameters(event)))
    }
}