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

import org.orbeon.oxf.xforms.XFormsConstants
import org.orbeon.oxf.xforms.XFormsContainingDocument
import org.orbeon.oxf.xforms.XFormsUtils
import scala.collection.JavaConversions._
import org.orbeon.oxf.common.OXFException
import org.orbeon.oxf.xforms.control.XFormsControl
import org.orbeon.oxf.xforms.control.controls.{XFormsSelectControl, XFormsTriggerControl}
import org.orbeon.oxf.xml._
import org.orbeon.oxf.util.IndentedLogger
import java.util.{ArrayList, List => JList, Set => JSet}
import dom4j.{LocationSAXContentHandler, Dom4jUtils}
import org.orbeon.oxf.pipeline.api._
import org.orbeon.oxf.util.Multipart
import org.dom4j.{Document, Element}
import org.orbeon.oxf.xforms.state.XFormsStateManager

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

        // Process events for noscript mode if needed
        val clientEventsAfterNoscript =
            if (containingDocument.getStaticState.isNoscript)
                reorderNoscriptEvents(clientEvents, containingDocument)
            else
                clientEvents.toSeq

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
                (clientEventsAfterNoscript flatMap (_ match {
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
                valueChangeControlIds += a.targetEffectiveId
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

    def reorderNoscriptEvents(eventElements: JList[Element], containingDocument: XFormsContainingDocument): Seq[Element] = {

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
                    case element if containingDocument.getStaticState.isValueControl(sourceControlId) => Category.ValueChange
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

            val selectFullControls = containingDocument.getControls.getCurrentControlTree.getSelectFullControls

            // Find all relevant and non-readonly select controls for which no value change event arrived. For each such
            // control, create a new event that will blank its value.
            selectFullControls.keySet -- getValueChangeIds map
                (selectFullControls.get(_).asInstanceOf[XFormsSelectControl]) filter
                    (control => control.isRelevant && !control.isReadonly) map
                        (createBlankingEvent(_)) toSeq
        }

        // Return all events by category in the order we defined the categories
        Category.values.toSeq flatMap ((groups ++ Map(Category.SelectBlank -> blankEvents)).get(_)) flatten
    }

    def doQuickReturnEvents(xmlReceiver: XMLReceiver, request: ExternalContext.Request, requestDocument: Document, indentedLogger: IndentedLogger,
               logRequestResponse: Boolean, clientEvents: JList[Element], session: ExternalContext.Session): Boolean = {

        val eventElement = clientEvents.get(0)

        // Hook-up debug content handler if we must log the response document
        def getReceiver =
            if (logRequestResponse) {
                val receivers = new ArrayList[XMLReceiver]
                receivers.add(xmlReceiver)
                val debugContentHandler = new LocationSAXContentHandler
                receivers.add(debugContentHandler)

                (new TeeXMLReceiver(receivers), debugContentHandler)
            } else
                (xmlReceiver, null)

        // Helper to make it easier to output simple Ajax responses
        def eventResponse(messageType: String, message: String)(block: ContentHandlerHelper => Unit): Boolean = {
            indentedLogger.startHandleOperation("ajax response", "handling regular Ajax response")

            val (responseReceiver, debugContentHandler) = getReceiver

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
                    val progress = Multipart.getUploadProgressJava(request, XFormsStateManager.getRequestUUID(requestDocument))
                    if (progress != null) {
                        helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "action")
                        helper.startElement("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control-values")
                        helper.element("xxf", XFormsConstants.XXFORMS_NAMESPACE_URI, "control",
                            Array[String]("id", eventElement.attributeValue("source-control-id"),
                                "progress-received", progress.receivedSize.toString,
                                "progress-expected", progress.expectedSize match {case Some(long) => long.toString; case _ => null}))
                        helper.endElement()
                        helper.endElement()
                    }
                }
            case _ => false
        }
    }
}